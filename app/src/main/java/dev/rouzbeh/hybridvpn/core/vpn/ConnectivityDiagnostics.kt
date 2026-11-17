package dev.rouzbeh.hybridvpn.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.net.UnknownHostException

/**
 * Bounded, cancellable connectivity diagnostics for an ESTABLISHED tunnel.
 *
 * This class deliberately produces VERDICTS, not actions. Callers (the VPN service)
 * decide what to do; a DNS or HTTP failure here must never tear down a tunnel by
 * itself — 2026-10-07 device evidence: an OpenConnect allow-mode session with live
 * DTLS traffic was killed because `cp.cloudflare.com` failed to resolve, i.e. the
 * app's own check destroyed a working tunnel.
 *
 * Stages are separated so a verdict distinguishes:
 *  1. DNS resolution failure (stage=dns) — resolver-level.
 *  2. Raw-IP TCP reachability (stage=tcp) — literal IP, no DNS involved.
 *  3. HTTP status (stage=http).
 *
 * For a public corporate/ISP-style VPN the expected healthy verdict is
 * [Verdict.Verified]. A private/corporate VPN that intentionally blocks public
 * endpoints will surface [Verdict.HttpFailure]/[Verdict.DnsFailure] — reported as
 * degraded, not fatal, per design.
 */
object ConnectivityDiagnostics {

    /** Master opt-in switch for the VERBOSE DNS/network diagnostics. */
    const val ENABLED = true

    private const val CHECK_HOST = "cp.cloudflare.com"
    private const val CHECK_URL = "http://cp.cloudflare.com/generate_204"
    // Literal IP for the raw TCP stage: 1.1.1.1:443 (Cloudflare). No DNS involved.
    private const val RAW_IP_TCP_HOST = "1.1.1.1"
    private const val RAW_IP_TCP_PORT = 443

    /** Single check attempt budget. */
    private const val ATTEMPT_TIMEOUT_MS = 5_000
    /** Max attempts within the check budget. */
    private const val MAX_ATTEMPTS = 3

    /** Outcome of a bounded check. Pure data; the service decides what happens. */
    sealed class Verdict {
        /** The public check fully succeeded. */
        data class Verified(val attempts: Int, val elapsedMs: Long) : Verdict()

        /** Resolver could not resolve the check host. */
        data class DnsFailure(val stage: String, val detail: String) : Verdict()

        /** DNS fine (or bypassed), but HTTP did not return a success code. */
        data class HttpFailure(val code: Int, val detail: String) : Verdict()

        /** Could not determine anything definitive within budget. */
        data class Inconclusive(val detail: String) : Verdict()
    }

    /**
     * Runs the bounded check. Never throws (all failures become verdicts), never
     * mutates engine state, honors coroutine cancellation between attempts.
     */
    suspend fun runCheck(
        context: Context,
        sessionId: Long,
        budgetMs: Long,
        proxy: Proxy = Proxy.NO_PROXY,
        onLog: (String) -> Unit,
    ): Verdict = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        var lastVerdict: Verdict = Verdict.Inconclusive("no attempt made")
        var attempts = 0

        if (ENABLED) logNetworkDiagnostics(context, sessionId, onLog)

        while (attempts < MAX_ATTEMPTS && (System.currentTimeMillis() - startMs) < budgetMs) {
            attempts++
            currentCoroutineContext().ensureActive()

            // Stage 1: DNS resolution.
            val dns = probeDns()
            if (dns is DnsResult.Failure) {
                lastVerdict = Verdict.DnsFailure(stage = "dns", detail = dns.detail)
                onLog("[$sessionId] check a#$attempts DNS FAILED: ${dns.detail}")
                delayBetweenAttempts()
                continue
            }
            if (ENABLED) {
                onLog("[$sessionId] check a#$attempts DNS ok: $CHECK_HOST -> ${(dns as DnsResult.Success).addresses.joinToString()} via ${dns.via}")
            }

            // Stage 2: raw-IP TCP reachability (distinguishes DNS vs transport).
            val tcp = probeRawIpTcp()
            if (!tcp.reachable) {
                lastVerdict = Verdict.HttpFailure(code = -1, detail = "raw TCP $RAW_IP_TCP_HOST:$RAW_IP_TCP_PORT unreachable: ${tcp.detail}")
                onLog("[$sessionId] check a#$attempts raw-IP TCP FAILED: ${tcp.detail}")
                delayBetweenAttempts()
                continue
            }
            if (ENABLED) onLog("[$sessionId] check a#$attempts raw-IP TCP ok (${tcp.detail})")

            // Stage 3: HTTP check.
            val http = probeHttp(proxy)
            onLog("[$sessionId] check a#$attempts HTTP $http")
            lastVerdict = classifyResponse(http)
            if (lastVerdict is Verdict.Verified) break
            delayBetweenAttempts()
        }

        val verdict = when (lastVerdict) {
            is Verdict.Verified -> Verdict.Verified(attempts, System.currentTimeMillis() - startMs)
            else -> lastVerdict
        }
        onLog("[$sessionId] connectivity verdict after $attempts attempt(s): ${verdict::class.java.simpleName}")
        verdict
    }

    /** Maps an HTTP response code to a verdict. Visible for testing. */
    fun classifyResponse(code: Int): Verdict = when (code) {
        204, 200 -> Verdict.Verified(attempts = 1, elapsedMs = 0L)
        in 300..399 -> Verdict.HttpFailure(code, "redirect (unexpected for generate_204)")
        401, 403 -> Verdict.HttpFailure(code, "blocked/captive portal response")
        in 400..499 -> Verdict.HttpFailure(code, "client error")
        in 500..599 -> Verdict.HttpFailure(code, "server error")
        else -> Verdict.Inconclusive("HTTP code $code")
    }

    private suspend fun delayBetweenAttempts() {
        kotlinx.coroutines.delay(1_000)
    }

    // ------------------------------------------------------------------ stages

    private sealed class DnsResult {
        data class Success(val addresses: List<InetAddress>, val via: String) : DnsResult()
        data class Failure(val detail: String) : DnsResult()
    }

    private data class TcpResult(val reachable: Boolean, val detail: String)

    /**
     * Ordinary app-process resolution of the check host. Note: when a VPN network
     * exists, this resolution goes through the VPN's DNS (system resolver picks the
     * VPN network for the default route). Explicit network-selected resolution is
     * covered by [logNetworkDiagnostics] when verbose diagnostics are enabled.
     */
    private fun probeDns(): DnsResult = try {
        val addresses = InetAddress.getAllByName(CHECK_HOST).toList()
        if (addresses.isEmpty()) DnsResult.Failure("empty address list for $CHECK_HOST")
        else DnsResult.Success(addresses, via = "default resolver")
    } catch (e: UnknownHostException) {
        DnsResult.Failure("unable to resolve $CHECK_HOST: ${e.message ?: "unknown host"}")
    } catch (e: Exception) {
        DnsResult.Failure("DNS error for $CHECK_HOST: ${e.message}")
    }

    private fun probeRawIpTcp(): TcpResult = try {
        val address = InetAddress.getByName(RAW_IP_TCP_HOST) // literal, no DNS lookup
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, RAW_IP_TCP_PORT), ATTEMPT_TIMEOUT_MS)
        }
        TcpResult(reachable = true, detail = "connected")
    } catch (e: Exception) {
        TcpResult(reachable = false, detail = e.message ?: e::class.java.simpleName)
    }

    /**
     * The HTTP probe. Connection resources are always closed in a finally block.
     */
    private fun probeHttp(proxy: Proxy): Int = try {
        val connection = (URL(CHECK_URL).openConnection(proxy) as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = ATTEMPT_TIMEOUT_MS
            readTimeout = ATTEMPT_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Connection", "close")
        }
        try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        -1
    }

    // -------------------------------------------------------------- diagnostics

    /**
     * One-shot verbose dump (only when [ENABLED]) of the network environment the
     * check runs in: VPN vs underlying network identity, LinkProperties of the
     * tunnel (interface, DNS servers, routes, MTU), capabilities and Private DNS
     * visibility. Uses only public Android APIs.
     */
    private fun logNetworkDiagnostics(context: Context, sessionId: Long, onLog: (String) -> Unit) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networks = cm.allNetworks // deprecated in 31; acceptable for diagnostics
            var vpnNetwork: Network? = null
            var underlying: Network? = null

            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpnNetwork = network
                    break
                }
            }

            if (vpnNetwork == null) {
                onLog("[$sessionId] diag: no TRANSPORT_VPN network visible from this process")
                return
            }

            val lp = cm.getLinkProperties(vpnNetwork)
            if (lp == null) {
                onLog("[$sessionId] diag: VPN network present but LinkProperties null")
                return
            }

            val dnsServers = lp.dnsServers.joinToString { it.hostAddress ?: it.toString() }
            val routeCount = lp.routes.size
            val iface = lp.interfaceName
            val mtu = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                lp.mtu
            } else 0
            onLog("[$sessionId] diag: tunnel iface=$iface mtu=$mtu dns=[$dnsServers] routes=$routeCount")

            // Private DNS state visible on this network's LinkProperties.
            val privateDns: String? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                runCatching { lp.privateDnsServerName }.getOrNull()
            } else null
            onLog(
                "[$sessionId] diag: privateDns=" +
                    (privateDns ?: "opportunistic/unset (pre-P)")
            )

            // Explicit resolution bound to the VPN network vs default resolution.
            try {
                val vpnResolved = vpnNetwork.getAllByName(CHECK_HOST)
                onLog("[$sessionId] diag: getAllByName on VPN network -> ${vpnResolved.joinToString()}")
            } catch (e: Exception) {
                onLog("[$sessionId] diag: getAllByName on VPN network FAILED: ${e.message}")
            }

            // Underlying (physical) network for cross-reference.
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                ) {
                    underlying = network
                    break
                }
            }
            if (underlying != null) {
                val ulp = cm.getLinkProperties(underlying)
                val udns = ulp?.dnsServers?.joinToString { it.hostAddress ?: it.toString() } ?: "?"
                onLog("[$sessionId] diag: underlying network dns=[$udns] (reference only)")
            }

            // Which network would the default HTTP check use?
            val defaultNet = cm.activeNetwork
            val usesVpn = defaultNet == vpnNetwork
            onLog("[$sessionId] diag: activeNetwork is VPN network: $usesVpn (http check uses default resolver path)")
        } catch (e: Exception) {
            onLog("[$sessionId] diag: network diagnostics unavailable: ${e.message}")
        }
    }
}
