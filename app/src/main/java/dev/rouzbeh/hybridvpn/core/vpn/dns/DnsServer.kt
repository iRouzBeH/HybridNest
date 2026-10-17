package dev.rouzbeh.hybridvpn.core.vpn.dns

/**
 * A DNS provider entry for the "DNS" connection method.
 *
 * Built-ins ship with the app and can be hidden (not edited). Custom entries
 * are created/edited/deleted by the user and persisted via
 * DnsSettingsRepository.
 *
 * [primary]/[secondary] are literal IP addresses (IPv4 or IPv6), never
 * hostnames: DNS servers are contacted before DNS resolution exists.
 */
data class DnsServer(
    /** Stable identity used for selection/persistence (built-ins use "builtin:<name>"). */
    val id: String,
    val name: String,
    val primary: String,
    val secondary: String? = null,
    val isBuiltin: Boolean = false,
    /** Last measured ping in ms, -1 = failed/unreachable, 0 = not tested. */
    val ping: Long = 0L,
    val isHidden: Boolean = false
)

/** Officially documented public DNS resolvers. Add new providers here. */
object DnsProviders {
    val BUILT_INS: List<DnsServer> = listOf(
        DnsServer("builtin:google",    "Google DNS",    "8.8.8.8",         "8.8.4.4",        isBuiltin = true),
        DnsServer("builtin:cloudflare","Cloudflare",    "1.1.1.1",         "1.0.0.1",        isBuiltin = true),
        DnsServer("builtin:quad9",     "Quad9",         "9.9.9.9",         "149.112.112.112",isBuiltin = true),
        DnsServer("builtin:opendns",   "OpenDNS",       "208.67.222.222",  "208.67.220.220", isBuiltin = true),
        DnsServer("builtin:adguard",   "AdGuard DNS",   "94.140.14.14",    "94.140.15.15",   isBuiltin = true)
    )
}

/** Literal IP validation for the add/edit form. Pure JVM. */
object DnsValidation {
    private val IPV4_OCTET = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun isValidIpv4(input: String): Boolean {
        val m = IPV4_OCTET.matchEntire(input.trim()) ?: return false
        return (1..4).all { idx ->
            m.groupValues[idx].toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    fun isValidIpv6(input: String): Boolean {
        val s = input.trim()
        if (s.isEmpty() || s.length > 45) return false
        if (s.count { it == ':' } < 2) return false
        return try {
            val addr = java.net.InetAddress.getByName(s)
            addr is java.net.Inet6Address
        } catch (_: Throwable) { false }
    }

    fun isValidIp(input: String): Boolean =
        isValidIpv4(input) || isValidIpv6(input)
}
