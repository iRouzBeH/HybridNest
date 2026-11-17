package dev.rouzbeh.hybridvpn.core.vpn.v2ray

/**
 * v2rayNG and WireGuard engine defaults and runtime-configurable settings.
 *
 * Provides defaults matching upstream v2rayNG (2dust/v2rayNG) and the
 * single source of truth for generated Xray configs, WireGuard settings,
 * and the TUN's advertised DNS.
 */
object V2raySettings {

    // --- Defaults ---
    const val DEFAULT_VPN_DNS = "9.9.9.9"
    const val DEFAULT_REMOTE_DNS = "tcp://1.1.1.1"
    const val DEFAULT_DOMESTIC_DNS = "178.22.122.100"
    const val DEFAULT_MUX_ENABLED = false
    const val DEFAULT_MUX_CONCURRENCY = 10
    const val DEFAULT_MUX_XUDP_CONCURRENCY = 13
    const val DEFAULT_MUX_XUDP_QUIC = "reject"
    const val DEFAULT_SESSION_DNS_MODULE = true
    const val DEFAULT_SNIFFING_ENABLED = true
    const val DEFAULT_BYPASS_LAN = false

    val DEFAULT_BYPASSED_DOMAINS: Set<String> = emptySet()
    const val DEFAULT_ALLOW_LAN_HOTSPOT = false

    const val DEFAULT_WG_MTU_OVERRIDE = 0 // 0 = use profile MTU; otherwise clamped 1280..1500
    const val DEFAULT_WG_KEEPALIVE_OVERRIDE = -1 // -1 = use profile keepAlive; otherwise >= 0
    const val DEFAULT_WG_TUNNEL_DNS = "9.9.9.9"
    const val DEFAULT_WG_BOOTSTRAP_RESOLVE = true

    // --- Static Tags ---
    const val TAG_DNS = "dns-module"
    const val TAG_DOMESTIC_DNS = "domestic-dns"

    // --- Runtime Configurable Properties ---
    @JvmField @Volatile var VPN_DNS: String = DEFAULT_VPN_DNS
    @JvmField @Volatile var REMOTE_DNS: String = DEFAULT_REMOTE_DNS
    @JvmField @Volatile var DOMESTIC_DNS: String = DEFAULT_DOMESTIC_DNS
    @JvmField @Volatile var MUX_ENABLED: Boolean = DEFAULT_MUX_ENABLED
    @JvmField @Volatile var MUX_CONCURRENCY: Int = DEFAULT_MUX_CONCURRENCY
    @JvmField @Volatile var MUX_XUDP_CONCURRENCY: Int = DEFAULT_MUX_XUDP_CONCURRENCY
    @JvmField @Volatile var MUX_XUDP_QUIC: String = DEFAULT_MUX_XUDP_QUIC
    @JvmField @Volatile var SESSION_DNS_MODULE: Boolean = DEFAULT_SESSION_DNS_MODULE
    @JvmField @Volatile var SNIFFING_ENABLED: Boolean = DEFAULT_SNIFFING_ENABLED
    @JvmField @Volatile var BYPASS_LAN: Boolean = DEFAULT_BYPASS_LAN
    @JvmField @Volatile var BYPASSED_DOMAINS: Set<String> = DEFAULT_BYPASSED_DOMAINS
    @JvmField @Volatile var ALLOW_LAN_HOTSPOT: Boolean = DEFAULT_ALLOW_LAN_HOTSPOT

    @JvmField @Volatile var WG_MTU_OVERRIDE: Int = DEFAULT_WG_MTU_OVERRIDE
    @JvmField @Volatile var WG_KEEPALIVE_OVERRIDE: Int = DEFAULT_WG_KEEPALIVE_OVERRIDE
    @JvmField @Volatile var WG_TUNNEL_DNS: String = DEFAULT_WG_TUNNEL_DNS
    @JvmField @Volatile var WG_BOOTSTRAP_RESOLVE: Boolean = DEFAULT_WG_BOOTSTRAP_RESOLVE

    /**
     * Resets all volatile runtime fields back to their original compile-time defaults.
     */
    fun resetToDefaults() {
        VPN_DNS = DEFAULT_VPN_DNS
        REMOTE_DNS = DEFAULT_REMOTE_DNS
        DOMESTIC_DNS = DEFAULT_DOMESTIC_DNS
        MUX_ENABLED = DEFAULT_MUX_ENABLED
        MUX_CONCURRENCY = DEFAULT_MUX_CONCURRENCY
        MUX_XUDP_CONCURRENCY = DEFAULT_MUX_XUDP_CONCURRENCY
        MUX_XUDP_QUIC = DEFAULT_MUX_XUDP_QUIC
        SESSION_DNS_MODULE = DEFAULT_SESSION_DNS_MODULE
        SNIFFING_ENABLED = DEFAULT_SNIFFING_ENABLED
        BYPASS_LAN = DEFAULT_BYPASS_LAN
        BYPASSED_DOMAINS = DEFAULT_BYPASSED_DOMAINS
        ALLOW_LAN_HOTSPOT = DEFAULT_ALLOW_LAN_HOTSPOT

        WG_MTU_OVERRIDE = DEFAULT_WG_MTU_OVERRIDE
        WG_KEEPALIVE_OVERRIDE = DEFAULT_WG_KEEPALIVE_OVERRIDE
        WG_TUNNEL_DNS = DEFAULT_WG_TUNNEL_DNS
        WG_BOOTSTRAP_RESOLVE = DEFAULT_WG_BOOTSTRAP_RESOLVE
    }
}
