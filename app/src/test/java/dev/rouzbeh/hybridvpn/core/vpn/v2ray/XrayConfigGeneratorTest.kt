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
    fun `session port 53 traffic is routed to dns-out from tun only`() {
        val rules = routingRules(generate())
        val dnsRule = (0 until rules.length())
            .map { rules.getJSONObject(it) }
            .firstOrNull { it.optString("port") == "53" }
        assertTrue("expected a port-53 routing rule", dnsRule != null)
        dnsRule!!
        assertEquals("dns-out", dnsRule.getString("outboundTag"))
        assertEquals("udp,tcp", dnsRule.getString("network"))
        val inboundTags = dnsRule.getJSONArray("inboundTag")
        assertEquals(1, inboundTags.length())
        assertEquals("tun-in", inboundTags.getString(0))
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

    // ================================================== dynamic settings & overrides

    @Test
    fun `sniffing toggles dynamically based on V2raySettings`() {
        try {
            V2raySettings.SNIFFING_ENABLED = false
            val inboundsOff = generate().getJSONArray("inbounds")
            val tunOff = (0 until inboundsOff.length()).map { inboundsOff.getJSONObject(it) }.first { it.optString("tag") == "tun-in" }
            assertFalse(tunOff.getJSONObject("sniffing").getBoolean("enabled"))

            V2raySettings.SNIFFING_ENABLED = true
            val inboundsOn = generate().getJSONArray("inbounds")
            val tunOn = (0 until inboundsOn.length()).map { inboundsOn.getJSONObject(it) }.first { it.optString("tag") == "tun-in" }
            assertTrue(tunOn.getJSONObject("sniffing").getBoolean("enabled"))
        } finally {
            V2raySettings.resetToDefaults()
        }
    }

    @Test
    fun `bypass lan rule is conditionally injected into routing rules`() {
        try {
            V2raySettings.BYPASS_LAN = false
            val rulesOff = routingRules(generate())
            val hasGeoIpOff = (0 until rulesOff.length()).any { i ->
                rulesOff.getJSONObject(i).optJSONArray("ip")?.let { arr ->
                    (0 until arr.length()).any { j -> arr.getString(j) == "geoip:private" }
                } == true
            }
            assertFalse("geoip:private rule should not be present when BYPASS_LAN is false", hasGeoIpOff)

            V2raySettings.BYPASS_LAN = true
            val rulesOn = routingRules(generate())
            val geoIpRule = (0 until rulesOn.length()).map { rulesOn.getJSONObject(it) }.firstOrNull { rule ->
                rule.optJSONArray("ip")?.let { arr ->
                    (0 until arr.length()).any { j -> arr.getString(j) == "geoip:private" }
                } == true
            }
            assertTrue("geoip:private rule should be present when BYPASS_LAN is true", geoIpRule != null)
            assertEquals("direct", geoIpRule?.getString("outboundTag"))
        } finally {
            V2raySettings.resetToDefaults()
        }
    }

    @Test
    fun `wireguard outbound respects MTU and keepalive overrides in V2raySettings`() {
        val samplePrivKey = "YKh/8+7bVzM+p0yT7v6E2Z1r0x5y8z9w1a2b3c4d5e8="
        val samplePubKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzHP0JuVo5VCa2wWg4="
        val wgUri = "wireguard://${java.net.URLEncoder.encode(samplePrivKey, "UTF-8")}@198.51.100.1:51820" +
            "?address=10.0.0.2%2F32&publickey=${java.net.URLEncoder.encode(samplePubKey, "UTF-8")}&mtu=1360&keepalive=25"
        val profile = VpnProfile(name = "wg", protocol = "wireguard", serverAddress = wgUri)

        try {
            // Default (no override: WG_MTU_OVERRIDE=0, WG_KEEPALIVE_OVERRIDE=-1)
            V2raySettings.resetToDefaults()
            val defSettings = proxyOutbound(generate(profile)).getJSONObject("settings")
            assertEquals(1360, defSettings.getInt("mtu"))
            assertEquals(25, defSettings.getJSONArray("peers").getJSONObject(0).getInt("keepAlive"))

            // Overrides applied
            V2raySettings.WG_MTU_OVERRIDE = 1420
            V2raySettings.WG_KEEPALIVE_OVERRIDE = 50
            val overriddenSettings = proxyOutbound(generate(profile)).getJSONObject("settings")
            assertEquals(1420, overriddenSettings.getInt("mtu"))
            assertEquals(50, overriddenSettings.getJSONArray("peers").getJSONObject(0).getInt("keepAlive"))
        } finally {
            V2raySettings.resetToDefaults()
        }
    }

    @Test
    fun `toXrayDomainRule correctly normalizes prefixes URLs and suffixes`() {
        assertEquals("domain:ir", XrayConfigGenerator.toXrayDomainRule(".ir"))
        assertEquals("domain:shaparak.ir", XrayConfigGenerator.toXrayDomainRule("shaparak.ir"))
        assertEquals("domain:digikala.com", XrayConfigGenerator.toXrayDomainRule("https://www.digikala.com/product/123"))
        assertEquals("geosite:ir", XrayConfigGenerator.toXrayDomainRule("geosite:ir"))
        assertEquals("domain:google.com", XrayConfigGenerator.toXrayDomainRule("domain:google.com"))
        assertEquals(null, XrayConfigGenerator.toXrayDomainRule("   "))
    }

    @Test
    fun `bypassed domains are added to domestic DNS module and direct routing rules`() {
        try {
            V2raySettings.resetToDefaults()
            V2raySettings.BYPASSED_DOMAINS = setOf(".ir", "shaparak.ir")

            val root = generate()

            // Check DNS module domestic resolver domains
            val dnsServers = root.getJSONObject("dns").getJSONArray("servers")
            val domesticServer = (0 until dnsServers.length())
                .map { dnsServers.optJSONObject(it) }
                .first { it != null && it.optString("tag") == V2raySettings.TAG_DOMESTIC_DNS }!!
            val domains = domesticServer.getJSONArray("domains")
            val domainList = (0 until domains.length()).map { domains.getString(it) }
            assertTrue(domainList.contains("geosite:private"))
            assertTrue(domainList.contains("domain:ir"))
            assertTrue(domainList.contains("domain:shaparak.ir"))

            // Check direct routing rule for bypassed domains
            val rules = routingRules(root)
            val directDomainRule = (0 until rules.length())
                .map { rules.getJSONObject(it) }
                .firstOrNull {
                    it.optString("outboundTag") == "direct" && it.has("domain")
                }
            assertTrue("Expected direct domain routing rule", directDomainRule != null)
            val routedDomains = (0 until directDomainRule!!.getJSONArray("domain").length())
                .map { directDomainRule.getJSONArray("domain").getString(it) }
            assertTrue(routedDomains.contains("domain:ir"))
            assertTrue(routedDomains.contains("domain:shaparak.ir"))
        } finally {
            V2raySettings.resetToDefaults()
        }
    }

    @Test
    fun `allow LAN hotspot switches inbounds to 0_0_0_0 and routes geoip private directly`() {
        try {
            // Default: hotspot false -> listen on 127.0.0.1
            V2raySettings.resetToDefaults()
            var root = generate()
            var inbounds = root.getJSONArray("inbounds")
            var socksIn = (0 until inbounds.length()).map { inbounds.getJSONObject(it) }.first { it.getString("tag") == "socks-in" }
            var httpIn = (0 until inbounds.length()).map { inbounds.getJSONObject(it) }.first { it.getString("tag") == "http-in" }
            assertEquals("127.0.0.1", socksIn.getString("listen"))
            assertEquals("127.0.0.1", httpIn.getString("listen"))

            // Hotspot true -> listen on 0.0.0.0 and geoip:private routed direct
            V2raySettings.ALLOW_LAN_HOTSPOT = true
            root = generate()
            inbounds = root.getJSONArray("inbounds")
            socksIn = (0 until inbounds.length()).map { inbounds.getJSONObject(it) }.first { it.getString("tag") == "socks-in" }
            httpIn = (0 until inbounds.length()).map { inbounds.getJSONObject(it) }.first { it.getString("tag") == "http-in" }
            assertEquals("0.0.0.0", socksIn.getString("listen"))
            assertEquals("0.0.0.0", httpIn.getString("listen"))

            val rules = routingRules(root)
            val directPrivateIpRule = (0 until rules.length())
                .map { rules.getJSONObject(it) }
                .firstOrNull {
                    it.optString("outboundTag") == "direct" &&
                        it.optJSONArray("ip")?.let { ipArr ->
                            (0 until ipArr.length()).any { i -> ipArr.getString(i) == "geoip:private" }
                        } == true
                }
            assertTrue("Expected geoip:private direct routing rule when hotspot is active", directPrivateIpRule != null)
        } finally {
            V2raySettings.resetToDefaults()
        }
    }

    @Test
    fun `resetToDefaults restores all original compile-time defaults`() {
        V2raySettings.VPN_DNS = "1.2.3.4"
        V2raySettings.REMOTE_DNS = "tcp://8.8.8.8"
        V2raySettings.SNIFFING_ENABLED = false
        V2raySettings.BYPASS_LAN = true
        V2raySettings.WG_MTU_OVERRIDE = 1400
        V2raySettings.BYPASSED_DOMAINS = setOf("test.ir")
        V2raySettings.ALLOW_LAN_HOTSPOT = true

        V2raySettings.resetToDefaults()

        assertEquals(V2raySettings.DEFAULT_VPN_DNS, V2raySettings.VPN_DNS)
        assertEquals(V2raySettings.DEFAULT_REMOTE_DNS, V2raySettings.REMOTE_DNS)
        assertEquals(V2raySettings.DEFAULT_SNIFFING_ENABLED, V2raySettings.SNIFFING_ENABLED)
        assertEquals(V2raySettings.DEFAULT_BYPASS_LAN, V2raySettings.BYPASS_LAN)
        assertEquals(V2raySettings.DEFAULT_WG_MTU_OVERRIDE, V2raySettings.WG_MTU_OVERRIDE)
        assertEquals(V2raySettings.DEFAULT_BYPASSED_DOMAINS, V2raySettings.BYPASSED_DOMAINS)
        assertEquals(V2raySettings.DEFAULT_ALLOW_LAN_HOTSPOT, V2raySettings.ALLOW_LAN_HOTSPOT)
    }
}
