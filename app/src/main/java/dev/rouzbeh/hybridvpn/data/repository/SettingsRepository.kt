package dev.rouzbeh.hybridvpn.data.repository

import android.content.Context
import android.content.SharedPreferences
import dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy
import dev.rouzbeh.hybridvpn.core.utils.LogRedactor
import dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2raySettings
import java.net.IDN
import java.net.URLDecoder
import java.util.Locale

/**
 * Source of truth for user settings and protocol configurations.
 */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hybridvpn_settings", Context.MODE_PRIVATE)

    init {
        syncRuntimeSettings()
    }

    // --- Per-App Routing Settings (Untouched) ---

    fun isPerAppEnabled(): Boolean = prefs.getBoolean(KEY_PER_APP_ENABLED, false)
    fun setPerAppEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_PER_APP_ENABLED, enabled).apply()

    fun isBypassMode(): Boolean = prefs.getBoolean(KEY_BYPASS_MODE, false)
    fun setBypassMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_BYPASS_MODE, enabled).apply()

    fun getSelectedApps(): Set<String> =
        prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()

    fun setSelectedApps(packageNames: Set<String>) =
        prefs.edit().putStringSet(KEY_SELECTED_APPS, packageNames).apply()

    /**
     * Monotonic policy revision, bumped on every routing-affecting change.
     * Survives process restarts; establishes session identity for the policy
     * snapshot captured at each connection start.
     */
    fun getRoutingRevision(): Long = prefs.getLong(KEY_ROUTING_REVISION, 0L)

    fun snapshotRoutingPolicy(): RoutingPolicy =
        RoutingPolicy(
            enabled = isPerAppEnabled(),
            mode = if (isBypassMode()) RoutingPolicy.Mode.BYPASS else RoutingPolicy.Mode.ALLOW,
            packages = getSelectedApps(),
            revision = getRoutingRevision()
        )

    private fun bumpRevisionInternal(editor: SharedPreferences.Editor) {
        val next = getRoutingRevision() + 1
        editor.putLong(KEY_ROUTING_REVISION, next)
    }

    fun bumpRoutingRevision() {
        prefs.edit().let { bumpRevisionInternal(it); it.apply() }
    }

    fun setPerAppEnabledAndBump(enabled: Boolean) {
        prefs.edit().let { bumpRevisionInternal(it); it.putBoolean(KEY_PER_APP_ENABLED, enabled).apply() }
    }

    fun setBypassModeAndBump(enabled: Boolean) {
        prefs.edit().let { bumpRevisionInternal(it); it.putBoolean(KEY_BYPASS_MODE, enabled).apply() }
    }

    fun setSelectedAppsAndBump(packageNames: Set<String>) {
        prefs.edit().let { bumpRevisionInternal(it); it.putStringSet(KEY_SELECTED_APPS, packageNames).apply() }
    }

    // --- AnyConnect (OpenConnect) Settings ---

    fun isOpenConnectDtlsEnabled(): Boolean = prefs.getBoolean(KEY_OC_DTLS_ENABLED, true)
    fun setOpenConnectDtlsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OC_DTLS_ENABLED, enabled).apply()
        syncRuntimeSettings()
    }

    fun getOpenConnectMtu(): Int = prefs.getInt(KEY_OC_MTU, 1400).coerceIn(1280, 1500)
    fun setOpenConnectMtu(mtu: Int) {
        prefs.edit().putInt(KEY_OC_MTU, mtu.coerceIn(1280, 1500)).apply()
        syncRuntimeSettings()
    }

    fun getOpenConnectDpdTimeout(): Int = prefs.getInt(KEY_OC_DPD_TIMEOUT, 300).coerceIn(30, 600)
    fun setOpenConnectDpdTimeout(timeout: Int) {
        prefs.edit().putInt(KEY_OC_DPD_TIMEOUT, timeout.coerceIn(30, 600)).apply()
        syncRuntimeSettings()
    }

    fun getOpenConnectFallbackDns(): String = prefs.getString(KEY_OC_FALLBACK_DNS, "8.8.8.8") ?: "8.8.8.8"
    fun setOpenConnectFallbackDns(dns: String) {
        prefs.edit().putString(KEY_OC_FALLBACK_DNS, dns.trim()).apply()
        syncRuntimeSettings()
    }

    fun resetOpenConnectSettings() {
        prefs.edit()
            .remove(KEY_OC_DTLS_ENABLED)
            .remove(KEY_OC_MTU)
            .remove(KEY_OC_DPD_TIMEOUT)
            .remove(KEY_OC_FALLBACK_DNS)
            .apply()
        syncRuntimeSettings()
    }

    // --- WireGuard Settings ---

    fun getWireGuardMtuOverride(): Int {
        val v = prefs.getInt(KEY_WG_MTU_OVERRIDE, V2raySettings.DEFAULT_WG_MTU_OVERRIDE)
        return if (v <= 0) 0 else v.coerceIn(1280, 1500)
    }
    fun setWireGuardMtuOverride(mtu: Int) {
        val v = if (mtu <= 0) 0 else mtu.coerceIn(1280, 1500)
        prefs.edit().putInt(KEY_WG_MTU_OVERRIDE, v).apply()
        syncRuntimeSettings()
    }

    fun getWireGuardKeepAliveOverride(): Int {
        val v = prefs.getInt(KEY_WG_KEEPALIVE_OVERRIDE, V2raySettings.DEFAULT_WG_KEEPALIVE_OVERRIDE)
        return if (v < 0) -1 else v.coerceIn(0, 300)
    }
    fun setWireGuardKeepAliveOverride(keepAlive: Int) {
        val v = if (keepAlive < 0) -1 else keepAlive.coerceIn(0, 300)
        prefs.edit().putInt(KEY_WG_KEEPALIVE_OVERRIDE, v).apply()
        syncRuntimeSettings()
    }

    fun getWireGuardTunnelDns(): String =
        prefs.getString(KEY_WG_TUNNEL_DNS, V2raySettings.DEFAULT_WG_TUNNEL_DNS) ?: V2raySettings.DEFAULT_WG_TUNNEL_DNS
    fun setWireGuardTunnelDns(dns: String) {
        prefs.edit().putString(KEY_WG_TUNNEL_DNS, dns.trim()).apply()
        syncRuntimeSettings()
    }

    fun isWireGuardBootstrapResolveEnabled(): Boolean =
        prefs.getBoolean(KEY_WG_BOOTSTRAP_RESOLVE, V2raySettings.DEFAULT_WG_BOOTSTRAP_RESOLVE)
    fun setWireGuardBootstrapResolveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WG_BOOTSTRAP_RESOLVE, enabled).apply()
        syncRuntimeSettings()
    }

    fun resetWireGuardSettings() {
        prefs.edit()
            .remove(KEY_WG_MTU_OVERRIDE)
            .remove(KEY_WG_KEEPALIVE_OVERRIDE)
            .remove(KEY_WG_TUNNEL_DNS)
            .remove(KEY_WG_BOOTSTRAP_RESOLVE)
            .apply()
        syncRuntimeSettings()
    }

    // --- v2rayNG (Xray) Settings ---

    fun getV2rayVpnDns(): String =
        prefs.getString(KEY_V2RAY_VPN_DNS, V2raySettings.DEFAULT_VPN_DNS) ?: V2raySettings.DEFAULT_VPN_DNS
    fun setV2rayVpnDns(dns: String) {
        prefs.edit().putString(KEY_V2RAY_VPN_DNS, dns.trim()).apply()
        syncRuntimeSettings()
    }

    fun getV2rayRemoteDns(): String =
        prefs.getString(KEY_V2RAY_REMOTE_DNS, V2raySettings.DEFAULT_REMOTE_DNS) ?: V2raySettings.DEFAULT_REMOTE_DNS
    fun setV2rayRemoteDns(dns: String) {
        prefs.edit().putString(KEY_V2RAY_REMOTE_DNS, dns.trim()).apply()
        syncRuntimeSettings()
    }

    fun getV2rayDomesticDns(): String =
        prefs.getString(KEY_V2RAY_DOMESTIC_DNS, V2raySettings.DEFAULT_DOMESTIC_DNS) ?: V2raySettings.DEFAULT_DOMESTIC_DNS
    fun setV2rayDomesticDns(dns: String) {
        prefs.edit().putString(KEY_V2RAY_DOMESTIC_DNS, dns.trim()).apply()
        syncRuntimeSettings()
    }

    fun isV2raySniffingEnabled(): Boolean =
        prefs.getBoolean(KEY_V2RAY_SNIFFING_ENABLED, V2raySettings.DEFAULT_SNIFFING_ENABLED)
    fun setV2raySniffingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_V2RAY_SNIFFING_ENABLED, enabled).apply()
        syncRuntimeSettings()
    }

    fun isV2rayMuxEnabled(): Boolean =
        prefs.getBoolean(KEY_V2RAY_MUX_ENABLED, V2raySettings.DEFAULT_MUX_ENABLED)
    fun setV2rayMuxEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_V2RAY_MUX_ENABLED, enabled).apply()
        syncRuntimeSettings()
    }

    fun getV2rayMuxConcurrency(): Int =
        prefs.getInt(KEY_V2RAY_MUX_CONCURRENCY, V2raySettings.DEFAULT_MUX_CONCURRENCY).coerceIn(1, 64)
    fun setV2rayMuxConcurrency(concurrency: Int) {
        prefs.edit().putInt(KEY_V2RAY_MUX_CONCURRENCY, concurrency.coerceIn(1, 64)).apply()
        syncRuntimeSettings()
    }

    fun getV2rayMuxQuicPolicy(): String =
        prefs.getString(KEY_V2RAY_MUX_QUIC_POLICY, V2raySettings.DEFAULT_MUX_XUDP_QUIC) ?: V2raySettings.DEFAULT_MUX_XUDP_QUIC
    fun setV2rayMuxQuicPolicy(policy: String) {
        prefs.edit().putString(KEY_V2RAY_MUX_QUIC_POLICY, policy.trim()).apply()
        syncRuntimeSettings()
    }

    fun isV2rayBypassLanEnabled(): Boolean =
        prefs.getBoolean(KEY_V2RAY_BYPASS_LAN, V2raySettings.DEFAULT_BYPASS_LAN)
    fun setV2rayBypassLanEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_V2RAY_BYPASS_LAN, enabled).apply()
        syncRuntimeSettings()
    }

    fun resetV2raySettings() {
        prefs.edit()
            .remove(KEY_V2RAY_VPN_DNS)
            .remove(KEY_V2RAY_REMOTE_DNS)
            .remove(KEY_V2RAY_DOMESTIC_DNS)
            .remove(KEY_V2RAY_SNIFFING_ENABLED)
            .remove(KEY_V2RAY_MUX_ENABLED)
            .remove(KEY_V2RAY_MUX_CONCURRENCY)
            .remove(KEY_V2RAY_MUX_QUIC_POLICY)
            .remove(KEY_V2RAY_BYPASS_LAN)
            .apply()
        syncRuntimeSettings()
    }

    // --- General Features Settings ---

    fun isConnectivityCheckEnabled(): Boolean =
        prefs.getBoolean(KEY_CONNECTIVITY_CHECK_ENABLED, true)
    fun setConnectivityCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONNECTIVITY_CHECK_ENABLED, enabled).apply()
        syncRuntimeSettings()
    }

    fun getPingTestUrl(): String =
        prefs.getString(KEY_PING_TEST_URL, "https://www.google.com/generate_204") ?: "https://www.google.com/generate_204"
    fun setPingTestUrl(url: String) {
        prefs.edit().putString(KEY_PING_TEST_URL, url.trim()).apply()
        syncRuntimeSettings()
    }

    fun getSubUserAgent(): String =
        prefs.getString(KEY_SUB_USER_AGENT, "v2rayNG/1.8.23") ?: "v2rayNG/1.8.23"
    fun setSubUserAgent(userAgent: String) {
        prefs.edit().putString(KEY_SUB_USER_AGENT, userAgent.trim()).apply()
        syncRuntimeSettings()
    }

    fun isStrictLogRedactionEnabled(): Boolean =
        prefs.getBoolean(KEY_STRICT_LOG_REDACTION, true)
    fun setStrictLogRedactionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STRICT_LOG_REDACTION, enabled).apply()
        syncRuntimeSettings()
    }

    fun getBypassedDomains(): Set<String> =
        prefs.getStringSet(KEY_BYPASSED_DOMAINS, emptySet()) ?: emptySet()

    fun setBypassedDomains(domains: Set<String>) {
        val normalized = domains.flatMap { parseAndNormalizeDomainEntries(it) }
            .filter { it.isNotBlank() }
            .toSet()
        prefs.edit().putStringSet(KEY_BYPASSED_DOMAINS, normalized).apply()
        syncRuntimeSettings()
    }

    fun isAllowLanHotspotEnabled(): Boolean =
        prefs.getBoolean(KEY_ALLOW_LAN_HOTSPOT, false)

    fun setAllowLanHotspotEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_LAN_HOTSPOT, enabled).apply()
        syncRuntimeSettings()
    }

    fun resetFeatureSettings() {
        prefs.edit()
            .remove(KEY_CONNECTIVITY_CHECK_ENABLED)
            .remove(KEY_PING_TEST_URL)
            .remove(KEY_SUB_USER_AGENT)
            .remove(KEY_STRICT_LOG_REDACTION)
            .remove(KEY_BYPASSED_DOMAINS)
            .remove(KEY_ALLOW_LAN_HOTSPOT)
            .apply()
        syncRuntimeSettings()
    }

    // --- Runtime Sync Method ---

    fun syncRuntimeSettings() {
        V2raySettings.VPN_DNS = getV2rayVpnDns()
        V2raySettings.REMOTE_DNS = getV2rayRemoteDns()
        V2raySettings.DOMESTIC_DNS = getV2rayDomesticDns()
        V2raySettings.SNIFFING_ENABLED = isV2raySniffingEnabled()
        V2raySettings.MUX_ENABLED = isV2rayMuxEnabled()
        V2raySettings.MUX_CONCURRENCY = getV2rayMuxConcurrency()
        V2raySettings.MUX_XUDP_QUIC = getV2rayMuxQuicPolicy()
        V2raySettings.BYPASS_LAN = isV2rayBypassLanEnabled()
        V2raySettings.BYPASSED_DOMAINS = getBypassedDomains()
        V2raySettings.ALLOW_LAN_HOTSPOT = isAllowLanHotspotEnabled()

        V2raySettings.WG_MTU_OVERRIDE = getWireGuardMtuOverride()
        V2raySettings.WG_KEEPALIVE_OVERRIDE = getWireGuardKeepAliveOverride()
        V2raySettings.WG_TUNNEL_DNS = getWireGuardTunnelDns()
        V2raySettings.WG_BOOTSTRAP_RESOLVE = isWireGuardBootstrapResolveEnabled()

        LogRedactor.strictRedactionEnabled = isStrictLogRedactionEnabled()
    }

    companion object {
        /**
         * Parses a potentially multi-domain or complex input string (e.g. Google redirect URLs,
         * comma/space/newline separated domains) into a deduplicated list of valid normalized domains.
         */
        fun parseAndNormalizeDomainEntries(rawInput: String): List<String> {
            if (rawInput.isBlank()) return emptyList()

            val initialTokens = rawInput.trim().split(Regex("[\\s;]+")).filter { it.isNotBlank() }
            val candidateTokens = mutableListOf<String>()

            for (token in initialTokens) {
                val isComplexUrl = token.contains("://") ||
                    token.startsWith("www.google.", ignoreCase = true) ||
                    token.startsWith("google.", ignoreCase = true)

                if (isComplexUrl) {
                    val clean = token.trim().trimEnd(',')
                    if (clean.contains(",http://", ignoreCase = true) || clean.contains(",https://", ignoreCase = true)) {
                        val splitUrls = clean.split(Regex(",(?=https?://)", RegexOption.IGNORE_CASE))
                        for (u in splitUrls) {
                            val cu = u.trim().trimEnd(',')
                            if (cu.isNotBlank()) candidateTokens.add(cu)
                        }
                    } else {
                        if (clean.isNotBlank()) candidateTokens.add(clean)
                    }
                } else {
                    val subTokens = token.split(',').map { it.trim() }.filter { it.isNotBlank() }
                    candidateTokens.addAll(subTokens)
                }
            }

            return candidateTokens
                .map { normalizeDomainEntry(it) }
                .filter { it.isNotBlank() }
                .distinct()
        }

        /**
         * Normalizes a single domain or URL entry into a clean domain rule (e.g. "shad.ir" or ".ir").
         * Returns an empty string "" if the input is malformed, invalid, or cannot be resolved.
         */
        fun normalizeDomainEntry(entry: String): String {
            return normalizeDomainEntryInternal(entry, depth = 0)
        }

        private fun normalizeDomainEntryInternal(entry: String, depth: Int): String {
            if (depth > 3) return ""
            var s = entry.trim().trim('"', '\'')
            if (s.isEmpty()) return ""

            // 1. Explicit Xray routing prefixes (geosite:, domain:, full:, regexp:)
            val xrayPrefixes = listOf("geosite:", "domain:", "full:", "regexp:")
            for (prefix in xrayPrefixes) {
                if (s.startsWith(prefix, ignoreCase = true)) {
                    val ruleType = prefix.lowercase(Locale.ROOT)
                    val rest = s.substring(prefix.length).trim().lowercase(Locale.ROOT)
                    return if (rest.isNotEmpty() && !rest.any { it.isWhitespace() || it.isISOControl() }) {
                        "$ruleType$rest"
                    } else {
                        ""
                    }
                }
            }

            // 2. Google Redirect URL Extraction
            val googleCandidate = s.replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
            if (googleCandidate.contains('?')) {
                val beforeQuery = googleCandidate.substringBefore('?')
                val query = googleCandidate.substringAfter('?')
                if (beforeQuery.contains('/')) {
                    val host = beforeQuery.substringBefore('/').lowercase(Locale.ROOT)
                    val path = "/" + beforeQuery.substringAfter('/')
                    val isGoogleHost = host == "google.com" ||
                        host == "www.google.com" ||
                        host.startsWith("google.") ||
                        host.startsWith("www.google.") ||
                        host.contains(".google.")

                    if (isGoogleHost && (path == "/url" || path == "/url/")) {
                        val params = query.substringBefore('#').split('&').mapNotNull { param ->
                            val eq = param.indexOf('=')
                            if (eq != -1) {
                                val k = param.substring(0, eq).trim()
                                val v = param.substring(eq + 1).trim()
                                k to v
                            } else null
                        }
                        val rawTarget = params.firstOrNull { it.first == "url" && it.second.isNotEmpty() }?.second
                            ?: params.firstOrNull { it.first == "q" && it.second.isNotEmpty() }?.second

                        if (rawTarget.isNullOrBlank()) {
                            return "" // Reject: Google redirect link missing target URL
                        }
                        val decodedTarget = try {
                            URLDecoder.decode(rawTarget, "UTF-8")
                        } catch (_: Exception) {
                            rawTarget
                        }
                        return normalizeDomainEntryInternal(decodedTarget, depth + 1)
                    }
                }
            }

            // 3. Scheme removal
            s = s.replace(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")

            // 4. Credentials removal
            if (s.contains('@')) {
                s = s.substringAfterLast('@')
            }

            // 5. Path, query, fragment removal
            s = s.substringBefore('/').substringBefore('?').substringBefore('#')

            // 6. Port removal (and reject bracketed IPv6)
            if (s.startsWith('[') || s.endsWith(']')) return ""
            if (s.contains(':')) {
                s = s.substringBefore(':')
            }
            s = s.trim()
            if (s.isEmpty()) return ""

            // 7. Case conversion & trailing dots
            s = s.lowercase(Locale.ROOT).trimEnd('.')
            if (s.isEmpty()) return ""

            // 8. Leading dot & www. prefix handling
            val hasLeadingDot = s.startsWith('.')
            var coreDomain = s.trimStart('.')
            if (coreDomain.startsWith("www.")) {
                coreDomain = coreDomain.removePrefix("www.")
            }
            coreDomain = coreDomain.trim()
            if (coreDomain.isEmpty()) return ""

            // 9. IDN / Punycode conversion
            val asciiDomain = try {
                IDN.toASCII(coreDomain, IDN.ALLOW_UNASSIGNED).lowercase(Locale.ROOT)
            } catch (_: Exception) {
                return ""
            }

            // 10. Strict domain validation
            if (asciiDomain.isEmpty() || asciiDomain.any { it.isWhitespace() || it.isISOControl() }) {
                return ""
            }

            // Reject raw IPv4 literals (4 numeric octets)
            if (asciiDomain.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
                return ""
            }

            val labels = asciiDomain.split('.')

            // Case A: Single-label TLD rule with leading dot (e.g. .ir or .xn--mgba3a4f16a)
            if (hasLeadingDot && labels.size == 1) {
                val tld = labels[0]
                val isValidTld = tld.matches(Regex("^[a-z]{2,63}$")) || tld.matches(Regex("^xn--[a-z0-9-]{1,59}$"))
                return if (isValidTld) ".$tld" else ""
            }

            // Case B: Full domain (requires at least 2 labels)
            if (labels.size < 2) {
                return ""
            }

            for (label in labels) {
                if (label.isEmpty() || label.length > 63) return ""
                if (label.startsWith('-') || label.endsWith('-')) return ""
                if (!label.matches(Regex("^[a-z0-9-]+$"))) return ""
            }

            val tld = labels.last()
            val isValidTld = tld.matches(Regex("^[a-z]{2,63}$")) || tld.matches(Regex("^xn--[a-z0-9-]{1,59}$"))
            if (!isValidTld) return ""

            return asciiDomain
        }

        private const val KEY_PER_APP_ENABLED = "per_app_enabled"
        private const val KEY_BYPASS_MODE = "bypass_mode"
        private const val KEY_SELECTED_APPS = "selected_apps"
        private const val KEY_ROUTING_REVISION = "routing_revision"

        // AnyConnect
        private const val KEY_OC_DTLS_ENABLED = "oc_dtls_enabled"
        private const val KEY_OC_MTU = "oc_mtu"
        private const val KEY_OC_DPD_TIMEOUT = "oc_dpd_timeout"
        private const val KEY_OC_FALLBACK_DNS = "oc_fallback_dns"

        // WireGuard
        private const val KEY_WG_MTU_OVERRIDE = "wg_mtu_override"
        private const val KEY_WG_KEEPALIVE_OVERRIDE = "wg_keepalive_override"
        private const val KEY_WG_TUNNEL_DNS = "wg_tunnel_dns"
        private const val KEY_WG_BOOTSTRAP_RESOLVE = "wg_bootstrap_resolve"

        // v2rayNG
        private const val KEY_V2RAY_VPN_DNS = "v2ray_vpn_dns"
        private const val KEY_V2RAY_REMOTE_DNS = "v2ray_remote_dns"
        private const val KEY_V2RAY_DOMESTIC_DNS = "v2ray_domestic_dns"
        private const val KEY_V2RAY_SNIFFING_ENABLED = "v2ray_sniffing_enabled"
        private const val KEY_V2RAY_MUX_ENABLED = "v2ray_mux_enabled"
        private const val KEY_V2RAY_MUX_CONCURRENCY = "v2ray_mux_concurrency"
        private const val KEY_V2RAY_MUX_QUIC_POLICY = "v2ray_mux_quic_policy"
        private const val KEY_V2RAY_BYPASS_LAN = "v2ray_bypass_lan"

        // Features
        private const val KEY_CONNECTIVITY_CHECK_ENABLED = "feature_connectivity_check_enabled"
        private const val KEY_PING_TEST_URL = "feature_ping_test_url"
        private const val KEY_SUB_USER_AGENT = "feature_sub_user_agent"
        private const val KEY_STRICT_LOG_REDACTION = "feature_strict_log_redaction"
        private const val KEY_BYPASSED_DOMAINS = "feature_bypassed_domains"
        private const val KEY_ALLOW_LAN_HOTSPOT = "feature_allow_lan_hotspot"
    }
}
