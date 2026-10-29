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
 * The bug: app DNS was forwarded to 1.1.1.1 as plaintext UDP over the direct
 * network (ISP-poisoned) instead of being resolved by the built-in DNS module
 * through the proxy, and mux was hard-disabled. These tests pin the generated
 * Xray JSON to the v2rayNG-equivalent behaviour.
 */
class XrayConfigGeneratorTest {

    private fun vlessProfile(
        link: String = "vless://00000000-0000-4000-8000-000000000000@example.com:443" +
            "?security=reality&encryption=none&pbk=testkey&fp=chrome&sni=example.com" +
            "&type=tcp&headerType=none#Test"
    ) = VpnProfile(name = "t", protocol = "v2ray", serverAddress = link)

    private fun generate(profile: VpnProfile = vlessProfile()) =
        JSONObject(XrayConfigGenerator.generateConfig(profile))

    private fun proxyOutbound(root: JSONObject): JSONObject {
        val outbounds = root.getJSONArray("outbounds")
        return (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .first { it.optString("tag") == "proxy" }
    }

    private fun routingRules(root: JSONObject) = root.getJSONObject("routing").getJSONArray("rules")

    // ------------------------------------------------------------ DNS module

    @Test
    fun `dns module carries remote server and dns-module tag`() {
        val dns = generate().getJSONObject("dns")
        assertEquals(V2raySettings.TAG_DNS, dns.getString("tag"))
        assertEquals("UseIPv4", dns.getString("queryStrategy"))
        val servers = dns.getJSONArray("servers")
        // First (default) server is the remote resolver, queried via proxy.
        assertEquals(V2raySettings.REMOTE_DNS, servers.getString(0))
    }

    @Test
    fun `domestic dns server is tagged and skip-fallback`() {
        val servers = generate().getJSONObject("dns").getJSONArray("servers")
        val domestic = (1 until servers.length())
            .map { servers.getJSONObject(it) }
            .firstOrNull { it.optString("tag") == V2raySettings.TAG_DOMESTIC_DNS }
        assertTrue("expected a domestic-dns server entry", domestic != null)
        domestic!!
        assertEquals(V2raySettings.DOMESTIC_DNS, domestic.getString("address"))
        assertTrue(domestic.getBoolean("skipFallback"))
    }

    // ------------------------------------------------------------ routing

    @Test
    fun `dns module upstream is routed through proxy and domestic through direct`() {
        val rules = routingRules(generate())
        fun inboundTagRule(tag: String): JSONObject? = (0 until rules.length())
            .map { rules.getJSONObject(it) }
            .firstOrNull {
                it.optJSONArray("inboundTag")?.let { arr ->
                    (0 until arr.length()).any { i -> arr.getString(i) == tag }
                } == true
            }
        assertEquals("proxy", inboundTagRule(V2raySettings.TAG_DNS)?.getString("outboundTag"))
        assertEquals("direct", inboundTagRule(V2raySettings.TAG_DOMESTIC_DNS)?.getString("outboundTag"))
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

    // ------------------------------------------------------------ mux

    @Test
    fun `vless proxy outbound has mux enabled with configured concurrency`() {
        val mux = proxyOutbound(generate()).getJSONObject("mux")
        assertTrue(mux.getBoolean("enabled"))
        assertEquals(V2raySettings.MUX_CONCURRENCY, mux.getInt("concurrency"))
        assertEquals(V2raySettings.MUX_XUDP_CONCURRENCY, mux.getInt("xudpConcurrency"))
        assertEquals(V2raySettings.MUX_XUDP_QUIC, mux.getString("xudpProxyUDP443"))
    }

    @Test
    fun `vless with flow uses single-stream mux concurrency -1`() {
        val profile = vlessProfile(
            "vless://00000000-0000-4000-8000-000000000000@example.com:443" +
                "?security=tls&encryption=none&sni=example.com&flow=xtls-rprx-vision" +
                "&type=tcp#Flow"
        )
        val mux = proxyOutbound(generate(profile)).getJSONObject("mux")
        assertTrue(mux.getBoolean("enabled"))
        assertEquals(-1, mux.getInt("concurrency"))
    }

    @Test
    fun `trojan outbound forces mux off`() {
        val profile = VpnProfile(
            name = "t", protocol = "v2ray",
            serverAddress = "trojan://password@example.com:443?security=tls&sni=example.com&type=tcp#T"
        )
        val mux = proxyOutbound(generate(profile)).getJSONObject("mux")
        assertFalse(mux.getBoolean("enabled"))
        assertEquals(-1, mux.getInt("concurrency"))
    }

    @Test
    fun `xhttp transport forces mux off`() {
        val profile = vlessProfile(
            "vless://00000000-0000-4000-8000-000000000000@example.com:443" +
                "?security=reality&encryption=none&pbk=testkey&fp=chrome&sni=example.com" +
                "&type=xhttp&path=%2F#Xhttp"
        )
        val mux = proxyOutbound(generate(profile)).getJSONObject("mux")
        assertFalse(mux.getBoolean("enabled"))
    }

    // ------------------------------------------------------------ tun sniffing

    @Test
    fun `tun inbound consumes fd packets with sniffing enabled`() {
        val inbounds = generate().getJSONArray("inbounds")
        val tun = (0 until inbounds.length())
            .map { inbounds.getJSONObject(it) }
            .first { it.optString("tag") == "tun-in" }
        assertEquals("tun", tun.getString("protocol"))
        assertTrue(tun.getJSONObject("sniffing").getBoolean("enabled"))
    }
}
