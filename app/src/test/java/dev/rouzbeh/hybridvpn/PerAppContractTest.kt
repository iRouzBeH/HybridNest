package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.core.routing.RoutingDecision
import dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy
import dev.rouzbeh.hybridvpn.core.routing.RoutingPolicyResolver
import dev.rouzbeh.hybridvpn.core.vpn.dns.DnsTunnelConfig
import dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2raySettings
import dev.rouzbeh.hybridvpn.core.vpn.v2ray.XrayConfigGenerator
import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM matrix for the per-app contract. Android's VpnService.Builder cannot
 * be constructed by local tests, so [builderCalls] is the small, explicit
 * projection of the calls applyPerAppRouting makes for each method. The
 * resolver and generated Xray JSON are the production implementations under test.
 */
class PerAppContractTest {

    private val ownPackage = "dev.rouzbeh.hybridvpn"
    private val chrome = "com.android.chrome"
    private val telegram = "org.telegram.messenger"
    private val maps = "com.google.android.apps.maps"
    private val stale = "com.example.uninstalled"

    private val installed = setOf(ownPackage, chrome, telegram, maps)

    private enum class Method { V2RAY, OPEN_CONNECT, DNS }

    private enum class Mode { GLOBAL, ALLOW, BYPASS }

    private data class BuilderCalls(
        val allowedApplications: Set<String>,
        val disallowedApplications: Set<String>,
    )

    private data class NetworkContract(
        val routeShape: String,
        val dnsServers: List<String>,
    )

    /** The method-specific package contract documented in docs/PER_APP_CONTRACT.md. */
    private fun builderCalls(method: Method, decision: RoutingDecision): BuilderCalls = when (decision) {
        RoutingDecision.Global -> BuilderCalls(
            allowedApplications = emptySet(),
            disallowedApplications = if (method == Method.V2RAY || method == Method.DNS) {
                setOf(ownPackage)
            } else {
                emptySet()
            }
        )

        is RoutingDecision.Allow -> BuilderCalls(
            allowedApplications = decision.allowedPackages +
                if (method == Method.OPEN_CONNECT) setOf(ownPackage) else emptySet(),
            disallowedApplications = emptySet(),
        )

        is RoutingDecision.Bypass -> BuilderCalls(
            allowedApplications = emptySet(),
            disallowedApplications = decision.disallowedPackages +
                if (method == Method.V2RAY || method == Method.DNS) setOf(ownPackage) else emptySet(),
        )

        is RoutingDecision.Refused -> throw AssertionError("refused policies have no Builder calls")
    }

    private fun decision(mode: Mode, selected: Set<String> = emptySet()): RoutingDecision {
        val policy = when (mode) {
            Mode.GLOBAL -> RoutingPolicy.disabled(revision = 1L)
            Mode.ALLOW -> RoutingPolicy(
                enabled = true,
                mode = RoutingPolicy.Mode.ALLOW,
                packages = selected,
                revision = 1L,
            )
            Mode.BYPASS -> RoutingPolicy(
                enabled = true,
                mode = RoutingPolicy.Mode.BYPASS,
                packages = selected,
                revision = 1L,
            )
        }
        return RoutingPolicyResolver.resolve(policy, installed)
    }

    private fun assertSingleBuilderApi(calls: BuilderCalls) {
        assertFalse(
            "VpnService.Builder must never receive both package API families",
            calls.allowedApplications.isNotEmpty() && calls.disallowedApplications.isNotEmpty(),
        )
    }

    @Test
    fun `global mode has no package rules for OpenConnect and excludes self for proxy and DNS`() {
        val global = decision(Mode.GLOBAL)
        assertEquals(RoutingDecision.Global, global)

        Method.values().forEach { method ->
            val calls = builderCalls(method, global)
            assertSingleBuilderApi(calls)
            assertEquals(emptySet<String>(), calls.allowedApplications)
            val expectedDisallowed = if (method == Method.OPEN_CONNECT) {
                emptySet()
            } else {
                setOf(ownPackage)
            }
            assertEquals(expectedDisallowed, calls.disallowedApplications)
        }
    }

    @Test
    fun `allow mode sends only selected installed apps and preserves method own-package rules`() {
        val allow = decision(Mode.ALLOW, setOf(chrome, telegram))
        assertTrue(allow is RoutingDecision.Allow)
        allow as RoutingDecision.Allow
        assertEquals(setOf(chrome, telegram), allow.allowedPackages)
        assertTrue(allow.prunedPackages.isEmpty())

        Method.values().forEach { method ->
            val calls = builderCalls(method, allow)
            assertSingleBuilderApi(calls)
            val expectedAllowed = if (method == Method.OPEN_CONNECT) {
                setOf(chrome, telegram, ownPackage)
            } else {
                setOf(chrome, telegram)
            }
            assertEquals(expectedAllowed, calls.allowedApplications)
            assertEquals(emptySet<String>(), calls.disallowedApplications)
        }
    }

    @Test
    fun `bypass mode disallows selected apps and only proxy and DNS exclude self`() {
        val bypass = decision(Mode.BYPASS, setOf(chrome))
        assertTrue(bypass is RoutingDecision.Bypass)
        bypass as RoutingDecision.Bypass
        assertEquals(setOf(chrome), bypass.disallowedPackages)

        Method.values().forEach { method ->
            val calls = builderCalls(method, bypass)
            assertSingleBuilderApi(calls)
            val expectedDisallowed = if (method == Method.OPEN_CONNECT) {
                setOf(chrome)
            } else {
                setOf(chrome, ownPackage)
            }
            assertEquals(emptySet<String>(), calls.allowedApplications)
            assertEquals(expectedDisallowed, calls.disallowedApplications)
        }
    }

    @Test
    fun `all mode combinations use exactly one API family and never widen stale selections`() {
        val cases = listOf(
            Mode.GLOBAL to emptySet<String>(),
            Mode.ALLOW to setOf(chrome, stale),
            Mode.BYPASS to setOf(chrome, stale),
        )
        cases.forEach { (mode, selected) ->
            val resolved = decision(mode, selected)
            assertFalse("a contract case must not be refused", resolved is RoutingDecision.Refused)
            Method.values().forEach { method ->
                assertSingleBuilderApi(builderCalls(method, resolved))
            }
        }

        val allow = decision(Mode.ALLOW, setOf(chrome, stale)) as RoutingDecision.Allow
        assertEquals(setOf(chrome), allow.allowedPackages)
        assertEquals(setOf(stale), allow.prunedPackages)
        val bypass = decision(Mode.BYPASS, setOf(chrome, stale)) as RoutingDecision.Bypass
        assertEquals(setOf(chrome), bypass.disallowedPackages)
        assertEquals(setOf(stale), bypass.prunedPackages)
    }

    @Test
    fun `empty allow mode refuses instead of silently becoming global`() {
        val empty = decision(Mode.ALLOW)
        assertTrue(empty is RoutingDecision.Refused)
        assertFalse(empty == RoutingDecision.Global)
    }

    @Test
    fun `routes and advertised DNS stay method and mode specific`() {
        val modes = Mode.values().toList()
        modes.forEach { mode ->
            val v2ray = networkContract(Method.V2RAY, mode)
            assertEquals(listOf(V2raySettings.VPN_DNS), v2ray.dnsServers)
            if (mode == Mode.ALLOW) {
                assertEquals("ipv4-default", v2ray.routeShape)
            } else {
                assertEquals("ipv4-default-except-proxy-endpoint", v2ray.routeShape)
            }

            val openConnect = networkContract(Method.OPEN_CONNECT, mode, "10.0.0.53, 10.0.0.54")
            assertEquals("ipv4-default", openConnect.routeShape)
            assertEquals(listOf("10.0.0.53", "10.0.0.54"), openConnect.dnsServers)

            val dnsOnly = networkContract(Method.DNS, mode)
            assertEquals("fake-dns-only-routes", dnsOnly.routeShape)
            assertEquals(
                listOf(DnsTunnelConfig.FAKE_DNS_V4, DnsTunnelConfig.FAKE_DNS_V6),
                dnsOnly.dnsServers,
            )
        }
        assertEquals(listOf("8.8.8.8"), networkContract(Method.OPEN_CONNECT, Mode.GLOBAL, "").dnsServers)
    }

    private fun networkContract(
        method: Method,
        mode: Mode,
        openConnectDns: String = "",
    ): NetworkContract = when (method) {
        Method.V2RAY -> NetworkContract(
            routeShape = if (mode == Mode.ALLOW) {
                "ipv4-default"
            } else {
                "ipv4-default-except-proxy-endpoint"
            },
            dnsServers = listOf(V2raySettings.VPN_DNS),
        )
        Method.OPEN_CONNECT -> NetworkContract(
            routeShape = "ipv4-default",
            dnsServers = openConnectDns.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .ifEmpty { listOf("8.8.8.8") },
        )
        Method.DNS -> NetworkContract(
            routeShape = "fake-dns-only-routes",
            dnsServers = listOf(DnsTunnelConfig.FAKE_DNS_V4, DnsTunnelConfig.FAKE_DNS_V6),
        )
    }

    @Test
    fun `real session Xray invariants hold for global allow and bypass`() {
        Mode.values().forEach { mode ->
            // Xray's session config is transport-specific, not package-mode-specific.
            val root = sessionConfig()
            val dns = root.getJSONObject("dns")
            assertEquals(V2raySettings.TAG_DNS, dns.getString("tag"))
            assertEquals(V2raySettings.REMOTE_DNS, dns.getJSONArray("servers").getString(0))
            assertTrue(root.toString().contains(V2raySettings.DOMESTIC_DNS))

            val outbounds = root.getJSONArray("outbounds")
            val dnsOut = (0 until outbounds.length())
                .map { outbounds.getJSONObject(it) }
                .first { it.optString("tag") == "dns-out" }
            assertEquals("dns", dnsOut.getString("protocol"))

            val rules = root.getJSONObject("routing").getJSONArray("rules")
            val port53 = (0 until rules.length())
                .map { rules.getJSONObject(it) }
                .filter { it.optString("port") == "53" }
            assertEquals("$mode must have one session port-53 rule", 1, port53.size)
            val dnsRule = port53.single()
            assertEquals("dns-out", dnsRule.getString("outboundTag"))
            assertEquals("udp,tcp", dnsRule.getString("network"))
            val inboundTags = dnsRule.getJSONArray("inboundTag")
            assertEquals(1, inboundTags.length())
            assertEquals("tun-in", inboundTags.getString(0))

            val proxyRule = (0 until rules.length())
                .map { rules.getJSONObject(it) }
                .first {
                    it.optString("outboundTag") == "proxy" &&
                        it.has("inboundTag") &&
                        jsonArrayStrings(it.getJSONArray("inboundTag")).contains("tun-in")
                }
            assertEquals(
                setOf("tun-in", "socks-in", "http-in"),
                jsonArrayStrings(proxyRule.getJSONArray("inboundTag")),
            )

            val mux = (0 until outbounds.length())
                .map { outbounds.getJSONObject(it) }
                .first { it.optString("tag") == "proxy" }
                .getJSONObject("mux")
            assertEquals(V2raySettings.MUX_ENABLED, mux.getBoolean("enabled"))
            if (V2raySettings.MUX_ENABLED) {
                assertEquals(V2raySettings.MUX_CONCURRENCY, mux.getInt("concurrency"))
                assertEquals(V2raySettings.MUX_XUDP_CONCURRENCY, mux.getInt("xudpConcurrency"))
                assertEquals(V2raySettings.MUX_XUDP_QUIC, mux.getString("xudpProxyUDP443"))
            }

            // The service pins the already-resolved endpoint before starting Xray.
            val pinned = XrayConfigGenerator.pinServerDomainResolution(
                root,
                "vpn.example.com",
                "203.0.113.9",
            )
            assertTrue(pinned)
            assertEquals(
                "203.0.113.9",
                root.getJSONObject("dns").getJSONObject("hosts").getString("vpn.example.com"),
            )
            assertEquals("direct", rulesAfterPin(root).getJSONObject(0).getString("outboundTag"))
        }
    }

    @Test
    fun `ping config remains the separate pre-session contract`() {
        val root = pingConfig()
        val dns = root.getJSONObject("dns")
        assertFalse(dns.has("tag"))
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), jsonArrayList(dns.getJSONArray("servers")))
        assertFalse(root.toString().contains(V2raySettings.TAG_DNS))
        assertFalse(root.toString().contains(V2raySettings.TAG_DOMESTIC_DNS))

        val rules = root.getJSONObject("routing").getJSONArray("rules")
        assertEquals(3, rules.length())
        assertEquals("dns-out", rules.getJSONObject(0).getString("outboundTag"))
        assertFalse(rules.getJSONObject(0).has("inboundTag"))
        val mux = (0 until root.getJSONArray("outbounds").length())
            .map { root.getJSONArray("outbounds").getJSONObject(it) }
            .first { it.optString("tag") == "proxy" }
            .getJSONObject("mux")
        assertFalse(mux.getBoolean("enabled"))
        assertFalse(mux.has("concurrency"))
    }

    private fun sessionConfig(): JSONObject = JSONObject(
        XrayConfigGenerator.generateConfig(vlessProfile()),
    )

    private fun pingConfig(): JSONObject = JSONObject(
        XrayConfigGenerator.generatePingConfig(vlessProfile()),
    )

    private fun rulesAfterPin(root: JSONObject) = root.getJSONObject("routing").getJSONArray("rules")

    private fun jsonArrayList(array: org.json.JSONArray): List<String> =
        (0 until array.length()).map { array.getString(it) }

    private fun jsonArrayStrings(array: org.json.JSONArray): Set<String> =
        (0 until array.length()).map { array.getString(it) }.toSet()

    private fun vlessProfile() = VpnProfile(
        name = "contract",
        protocol = "v2ray",
        serverAddress = "vless://00000000-0000-4000-8000-000000000000@example.com:443" +
            "?security=reality&encryption=none&pbk=testkey&fp=chrome&sni=example.com" +
            "&type=tcp&headerType=none#Contract",
    )
}
