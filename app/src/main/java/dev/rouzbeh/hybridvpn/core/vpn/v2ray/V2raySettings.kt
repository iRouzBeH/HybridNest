package dev.rouzbeh.hybridvpn.core.vpn.v2ray

/**
 * v2rayNG-tunnel defaults, mirroring the values the upstream v2rayNG app
 * (2dust/v2rayNG) uses and that the config author recommends for this server.
 *
 * These are NOT wired to a settings screen (none exists for v2rayNG options);
 * they are the single source of truth for the generated Xray config and the
 * TUN's advertised DNS. Keeping them here instead of scattered literals makes
 * them easy to promote to a real preference later without touching the
 * generator or the service.
 */
object V2raySettings {
    /**
     * DNS the Android system is told to use inside the TUN (addDnsServer).
     * dns-step-1 flips this from the previous 1.1.1.1 to 9.9.9.9.
     */
    const val VPN_DNS = "9.9.9.9"

    /** Remote DNS the Xray built-in DNS module forwards to THROUGH the proxy. */
    const val REMOTE_DNS = "9.9.9.9"

    /**
     * Domestic DNS the built-in module forwards to directly (skipFallback).
     * Used for domestic/direct-domain resolution; routed via the "direct"
     * outbound so it is not proxied.
     */
    const val DOMESTIC_DNS = "178.22.122.100"

    /** Mux on the proxy outbound (v2rayNG "Enable mux"). dns-step-2 flips on. */
    const val MUX_ENABLED = true
    const val MUX_CONCURRENCY = 10
    const val MUX_XUDP_CONCURRENCY = 13

    /**
     * v2rayNG "xudpProxyUDP443": how mux/XUDP treats UDP-443 (QUIC).
     * "reject" drops QUIC so browsers fall back to TCP/443 (HTTP/2), which the
     * VLESS transport relays reliably.
     */
    const val MUX_XUDP_QUIC = "reject"

    /**
     * Whether the real session config gets the built-in DNS module +
     * "dns-module"/"domestic-dns" routing rules (dns-step-3). The ping/test
     * path never uses this — see XrayConfigGenerator.generatePingConfig.
     */
    const val SESSION_DNS_MODULE = false

    // Xray routing/dns tags (mirror AppConfig TAG_DNS / TAG_DOMESTIC_DNS).
    const val TAG_DNS = "dns-module"
    const val TAG_DOMESTIC_DNS = "domestic-dns"
}
