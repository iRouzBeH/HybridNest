package dev.rouzbeh.hybridvpn.core.vpn.v2ray

import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v2rayNG DNS/mux fix.
 *
 * Two entry points are covered:
 *  - [XrayConfigGenerator.generateConfig]     -> a REAL VPN session.
 *  - [XrayConfigGenerator.generatePingConfig] -> the ping/delay-test path.
 *
 * The ping path is pinned to the exact pre-dns-fix structure (flat system DNS,
 * no "dns-module"/"domestic-dns" rules, `{"enabled": false}` mux) regardless of
 * the session toggles, so the delay test can never regress again.
 */
class XrayConfigGeneratorTest {

    private fun vlessProfile(
        link: String = "vless://00000000-0000-4000-8000-000000000000@example.com:443" +
            "?security=reality&encryption=none&pbk=testkey&fp=chrome&sni=example.com" +
            "&type=tcp&headerType=none#Test"
    ) = VpnProfile(name = "t", protocol = "v2ray", serverAddress = link)

    private fun trojanProfile() = VpnProfile(
        name = "t", protocol = "v2ray",
        serverAddress = "trojan://password@example.com:443?security=tls&sni=example.com&type=tcp#T"
    )

    private fun xhttpProfile() = vlessProfile(
        "vless://00000000-0000-4000-8000-000000000000@example.com:443" +
            "?security=reality&encryption=none&pbk=testkey&fp=chrome&sni=example.com" +
            "&type=xhttp&path=%2F#Xhttp"
    )

    private fun flowProfile() = vlessProfile(
        "vless://00000000-0000-4000-8000-000000000000@example.com:443" +
            "?security=tls&encryption=none&sni=example.com&flow=xtls-rprx-vision" +
            "&type=tcp#Flow"
    )

    private fun ipProfile() = vlessProfile(
        "vless://00000000-0000-4000-8000-000000000000@203.0.113.9:443" +
            "?security=reality&encryption=none&pbk=testkey&fp=chrome&sni=example.com" +
            "&type=tcp#Ip"
    )

    private fun generate(profile: VpnProfile = vlessProfile()) =
        JSONObject(XrayConfigGenerator.generateConfig(profile))

    private fun pingGenerate(profile: VpnProfile = vlessProfile()) =
        JSONObject(XrayConfigGenerator.generatePingConfig(profile))

    private fun proxyOutbound(root: JSONObject): JSONObject {
        val outbounds = root.getJSONArray("outbounds")
        return (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.optString("tag") == "proxy" }
    }

    private fun routingRules(root: JSONObject) = root.getJSONObject("routing").getJSONArray("rules")

    private fun inboundTagRule(root: JSONObject, tag: String): JSONObject? {
        val rules = routingRules(root)
        return (0 until rules.length())
            .map { rules.getJSONObject(it) }
            .firstOrNull {
                it.optJSONArray("inboundTag")?.let { arr ->
                    (0 until arr.length()).any { i -> arr.getString(i) == tag }
                } == true
            }
    }

    // ================================================== session DNS module

    @Test
    fun `session dns matches the active module flag`() {
        val dns = generate().getJSONObject("dns")
        if (V2raySettings.SESSION_DNS_MODULE) {
            assertEquals(V2raySettings.TAG_DNS, dns.getString("tag"))
            val servers = dns.getJSONArray("servers")
            assertEquals(V2raySettings.REMOTE_DNS, servers.getString(0))
            val domestic = (1 until servers.length())
                .map { servers.getJSONObject(it) }
                .firstOrNull { it.optString("tag") == V2raySettings.TAG_DOMESTIC_DNS }
            assertTrue("expected a domestic-dns server entry", domestic != null)
            domestic!!
            assertEquals(V2raySettings.DOMESTIC_DNS, domestic.getString("address"))
            assertTrue(domestic.getBoolean("skipFallback"))
        } else {
            assertFalse("session dns must not carry a tag yet", dns.has("tag"))
            val servers = dns.getJSONArray("servers")
            assertEquals("8.8.8.8", servers.getString(0))
            assertEquals("1.1.1.1", servers.getString(1))
        }
        assertEquals("UseIPv4", dns.getString("queryStrategy"))
    }

    @Test
    fun `session routing carries dns-module rules only when module enabled`() {
        val root = generate()
        if (V2raySettings.SESSION_DNS_MODULE) {
            assertEquals("proxy", inboundTagRule(root, V2raySettings.TAG_DNS)?.getString("outboundTag"))
            assertEquals("direct", inboundTagRule(root, V2raySettings.TAG_DOMESTIC_DNS)?.getString("outboundTag"))
        } else {
            assertTrue(inboundTagRule(root, V2raySettings.TAG_DNS) == null)
            assertTrue(inboundTagRule(root, V2raySettings.TAG_DOMESTIC_DNS) == null)
        }
    }

    @Test
    fun `port 53 traffic is routed to dns-out`() {
        val rules = routingRules(generate())
        val dnsRule = (0 until rules.length())
            .map { rules.getJSONObject(it) }
            .firstOrNull { it.optString("port") == "53" }
        assertTrue("expected a port-53 routing rule", dnsRule != null)
        dnsRule!!
        assertEquals("dns-out", dnsRule.getString("outboundTag"))
        assertEquals("udp,tcp", dnsRule.getString("network"))
    }

    @Test
    fun `dns-out outbound is a dns protocol forwarder`() {
        val outbounds = generate().getJSONArray("outbounds")
        val dnsOut = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.optString("tag") == "dns-out" }
        assertEquals("dns", dnsOut.getString("protocol"))
    }

    // ================================================== server-domain bootstrap (dns-step-3)

    @Test
    fun `server domain direct rule routes the domain to direct`() {
        val rule = XrayConfigGenerator.serverDomainDirectRule("vpn.example.com")
        assertTrue("expected a direct rule for a domain", rule != null)
        rule!!
        assertEquals("direct", rule.getString("outboundTag"))
        val domains = rule.getJSONArray("domain")
        assertEquals("vpn.example.com", domains.getString(0))
    }

    @Test
    fun `server domain direct rule is null for ip literals`() {
        assertTrue(XrayConfigGenerator.serverDomainDirectRule("203.0.113.9") == null)
        assertTrue(XrayConfigGenerator.serverDomainDirectRule("::1") == null)
        assertTrue(XrayConfigGenerator.serverDomainDirectRule("  ") == null)
    }

    @Test
    fun `pin server domain resolution injects hosts pin and first direct rule`() {
        val root = generate()
        val pinned = XrayConfigGenerator.pinServerDomainResolution(
            root, "vpn.example.com", "203.0.113.9"
        )
        assertTrue("expected the domain to be pinned", pinned)

        val dns = root.getJSONObject("dns")
        assertEquals("203.0.113.9", dns.getJSONObject("hosts").getString("vpn.example.com"))

        val rules = routingRules(root)
        assertEquals("direct", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("vpn.example.com", rules.getJSONObject(0).getJSONArray("domain").getString(0))
        // The dns-module rules still follow.
        assertEquals("proxy", inboundTagRule(root, V2raySettings.TAG_DNS)?.getString("outboundTag"))
    }

    @Test
    fun `pin server domain resolution is a no-op for ip literals`() {
        val root = generate()
        assertFalse(
            XrayConfigGenerator.pinServerDomainResolution(root, "203.0.113.9", "203.0.113.9")
        )
        assertFalse(root.getJSONObject("dns").has("hosts"))
    }

    @Test
    fun `session dns module still present alongside bootstrap`() {
        // When the flag is on the module exists; the service separately pins
        // dns.hosts and inserts the direct rule at runtime.
        if (V2raySettings.SESSION_DNS_MODULE) {
            val dns = generate().getJSONObject("dns")
            assertEquals(V2raySettings.TAG_DNS, dns.getString("tag"))
            // hosts key is added by the service at connect time, not the
            // generator — assert it is absent here so the two paths stay distinct.
            assertFalse(dns.has("hosts"))
        }
    }

    // ================================================== session mux

    @Test
    fun `session mux follows the toggle for each profile type`() {
        data class Case(val profile: VpnProfile, val enabledIfOn: Boolean, val concurrency: Int)
        val cases = listOf(
            Case(vlessProfile(), true, V2raySettings.MUX_CONCURRENCY), // VLESS TCP REALITY
            Case(flowProfile(), true, -1),                           // VLESS + flow -> -1
            Case(trojanProfile(), false, -1),                        // trojan forced off
            Case(xhttpProfile(), false, -1),                         // xhttp forced off
            Case(ipProfile(), true, V2raySettings.MUX_CONCURRENCY),  // VLESS TCP REALITY (IP)
        )
        for (c in cases) {
            val mux = proxyOutbound(generate(c.profile)).getJSONObject("mux")
            if (V2raySettings.MUX_ENABLED) {
                assertEquals(c.enabledIfOn, mux.getBoolean("enabled"))
                if (c.enabledIfOn) {
                    assertEquals(c.concurrency, mux.getInt("concurrency"))
                    assertEquals(V2raySettings.MUX_XUDP_CONCURRENCY, mux.getInt("xudpConcurrency"))
                    assertEquals(V2raySettings.MUX_XUDP_QUIC, mux.getString("xudpProxyUDP443"))
                } else {
                    assertEquals(-1, mux.getInt("concurrency"))
                }
            } else {
                assertFalse("mux must be disabled until dns-step-2", mux.getBoolean("enabled"))
                assertEquals(-1, mux.getInt("concurrency"))
            }
        }
    }

    // ================================================== tun sniffing (session)

    @Test
    fun `tun inbound consumes fd packets with sniffing enabled`() {
        val inbounds = generate().getJSONArray("inbounds")
        val tun = (0 until inbounds.length())
            .map { inbounds.getJSONObject(it) }
            .first { it.optString("tag") == "tun-in" }
        assertEquals("tun", tun.getString("protocol"))
        assertTrue(tun.getJSONObject("sniffing").getBoolean("enabled"))
    }

    // ================================================== ping/test config

    @Test
    fun `ping config has no dns module or new routing rules`() {
        val root = pingGenerate()
        val dns = root.getJSONObject("dns")
        assertFalse("ping dns must not carry a tag", dns.has("tag"))
        val servers = dns.getJSONArray("servers")
        assertEquals("8.8.8.8", servers.getString(0))
        assertEquals("1.1.1.1", servers.getString(1))
        assertEquals(2, servers.length())

        val json = root.toString()
        assertFalse("no dns-module tag", json.contains(V2raySettings.TAG_DNS))
        assertFalse("no domestic-dns tag", json.contains(V2raySettings.TAG_DOMESTIC_DNS))
        assertFalse("no domestic resolver", json.contains(V2raySettings.DOMESTIC_DNS))
        assertFalse("no remote resolver", json.contains(V2raySettings.REMOTE_DNS))
    }

    @Test
    fun `ping config proxy outbound never has mux`() {
        listOf(vlessProfile(), flowProfile(), xhttpProfile(), ipProfile(), trojanProfile())
            .forEach { profile ->
                val mux = proxyOutbound(pingGenerate(profile)).getJSONObject("mux")
                assertFalse("ping mux must be disabled", mux.getBoolean("enabled"))
                assertFalse("ping mux carries only the enabled flag", mux.has("concurrency"))
                assertFalse(mux.has("xudpConcurrency"))
                assertFalse(mux.has("xudpProxyUDP443"))
            }
    }

    @Test
    fun `ping config equals the pre-dns-fix session structure`() {
        val root = pingGenerate()
        val inbounds = root.getJSONArray("inbounds")
        assertEquals(
            setOf("tun-in", "socks-in", "http-in"),
            (0 until inbounds.length()).map { inbounds.getJSONObject(it).getString("tag") }.toSet()
        )
        val outbounds = root.getJSONArray("outbounds")
        assertEquals(
            setOf("proxy", "direct", "block", "dns-out"),
            (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }.toSet()
        )
        val rules = routingRules(root)
        // Old rule set: port-53 -> dns-out, private ips -> direct, inbounds -> proxy.
        assertEquals(3, rules.length())
        assertEquals("dns-out", rules.getJSONObject(0).getString("outboundTag"))
        assertEquals("direct", rules.getJSONObject(1).getString("outboundTag"))
        assertEquals("proxy", rules.getJSONObject(2).getString("outboundTag"))
    }

    @Test
    fun `ping config preserves the proxy outbound for domain and ip servers`() {
        val domain = proxyOutbound(pingGenerate())
        assertEquals("vless", domain.getString("protocol"))
        assertEquals(
            "example.com",
            domain.getJSONObject("settings").getJSONArray("vnext")
                .getJSONObject(0).getString("address")
        )
        assertEquals(
            "203.0.113.9",
            proxyOutbound(pingGenerate(ipProfile())).getJSONObject("settings")
                .getJSONArray("vnext").getJSONObject(0).getString("address")
        )
    }
}
