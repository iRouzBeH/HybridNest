package dev.rouzbeh.hybridvpn.core.vpn.wireguard

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Parsed representation of a WireGuard client configuration.
 *
 * Keeps Room's [dev.rouzbeh.hybridvpn.data.local.VpnProfile] schema untouched (version = 1).
 * Supports canonical serialization via [toUri] into a `wireguard://...` URI stored uniformly
 * in `VpnProfile.serverAddress` with `protocol = "WireGuard"`.
 */
data class WireGuardParsedConfig(
    val remark: String = "WireGuard Server",
    val secretKey: String,
    val publicKey: String,
    val preSharedKey: String? = null,
    val endpointHost: String,
    val endpointPort: Int,
    val localAddresses: List<String> = listOf("10.0.0.2/32"),
    val mtu: Int = 1420,
    val keepAlive: Int = 0,
    val reserved: List<Int>? = null,
    val dns: String? = null,
    val listenPort: Int? = null,
    val allowedIps: List<String> = listOf("0.0.0.0/0")
) {
    /**
     * Validates configuration fields and returns a list of error messages (empty if valid).
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (secretKey.isBlank()) {
            errors.add("Secret key (private key) cannot be blank")
        }
        if (publicKey.isBlank()) {
            errors.add("Public key cannot be blank")
        }
        if (endpointHost.isBlank()) {
            errors.add("Endpoint host cannot be blank")
        }
        if (endpointPort !in 1..65535) {
            errors.add("Endpoint port must be between 1 and 65535 (was $endpointPort)")
        }
        if (localAddresses.isEmpty() || localAddresses.all { it.isBlank() }) {
            errors.add("Local interface address list cannot be empty")
        }
        if (mtu !in 576..65535) {
            errors.add("MTU must be between 576 and 65535 (was $mtu)")
        }
        if (keepAlive < 0) {
            errors.add("Keepalive must be non-negative (was $keepAlive)")
        }
        if (listenPort != null && listenPort !in 1..65535) {
            errors.add("Listen port must be between 1 and 65535 (was $listenPort)")
        }
        if (reserved != null) {
            if (reserved.size != 3) {
                errors.add("Reserved must contain exactly 3 bytes (found ${reserved.size})")
            }
            if (reserved.any { it !in 0..255 }) {
                errors.add("Reserved bytes must be in range 0..255")
            }
        }
        return errors
    }

    /**
     * Asserts this configuration is valid or throws [IllegalArgumentException].
     */
    fun requireValid(): WireGuardParsedConfig {
        val errors = validate()
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException("Invalid WireGuard configuration: ${errors.joinToString("; ")}")
        }
        return this
    }

    /**
     * Serializes this parsed configuration into a canonical `wireguard://` URI.
     *
     * Format:
     * `wireguard://<url_encoded_private_key>@<host>:<port>?address=<url_encoded_addrs>&publickey=<url_encoded_pub_key>[&presharedkey=...][&mtu=...][&reserved=...][&keepalive=...][&dns=...][&listenport=...][&allowedips=...]#<url_encoded_remark>`
     */
    fun toUri(): String {
        requireValid()
        val encKey = URLEncoder.encode(secretKey, "UTF-8")
        val authority = if (endpointHost.contains(":") && !endpointHost.startsWith("[")) {
            "[$endpointHost]:$endpointPort"
        } else {
            "$endpointHost:$endpointPort"
        }

        val params = mutableListOf<String>()
        val addrs = localAddresses.filter { it.isNotBlank() }.joinToString(",")
        params.add("address=" + URLEncoder.encode(addrs, "UTF-8"))
        params.add("publickey=" + URLEncoder.encode(publicKey, "UTF-8"))

        if (!preSharedKey.isNullOrBlank()) {
            params.add("presharedkey=" + URLEncoder.encode(preSharedKey, "UTF-8"))
        }
        if (mtu > 0) {
            params.add("mtu=$mtu")
        }
        if (reserved != null && reserved.isNotEmpty()) {
            params.add("reserved=" + URLEncoder.encode(reserved.joinToString(","), "UTF-8"))
        }
        if (keepAlive > 0) {
            params.add("keepalive=$keepAlive")
        }
        if (!dns.isNullOrBlank()) {
            params.add("dns=" + URLEncoder.encode(dns, "UTF-8"))
        }
        if (listenPort != null && listenPort > 0) {
            params.add("listenport=$listenPort")
        }
        if (allowedIps.isNotEmpty()) {
            params.add("allowedips=" + URLEncoder.encode(allowedIps.filter { it.isNotBlank() }.joinToString(","), "UTF-8"))
        }

        val query = params.joinToString("&")
        val fragment = URLEncoder.encode(remark.ifBlank { "WireGuard Server" }, "UTF-8")
        return "wireguard://$encKey@$authority?$query#$fragment"
    }
}

/**
 * Parser, validator and serializer utilities for WireGuard configurations.
 */
object WireGuardConfig {

    private val INTERFACE_REGEX = Regex("""(?im)^\s*\[\s*Interface\s*\]""")
    private val PEER_REGEX = Regex("""(?im)^\s*\[\s*Peer\s*\]""")

    /**
     * Detects if the given input is a WireGuard configuration (`wireguard://` or `[Interface]` + `[Peer]`).
     */
    fun isWireGuardConfig(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim()
        if (trimmed.startsWith("wireguard://", ignoreCase = true)) return true
        return trimmed.contains(INTERFACE_REGEX) && trimmed.contains(PEER_REGEX)
    }

    /**
     * Parses a WireGuard configuration from a `wireguard://` URI or `.conf` text.
     * Throws [IllegalArgumentException] if invalid.
     */
    fun parse(input: String, defaultRemark: String? = null): WireGuardParsedConfig {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("wireguard://", ignoreCase = true) -> parseUri(trimmed)
            trimmed.contains(INTERFACE_REGEX) || trimmed.contains(PEER_REGEX) -> parseConf(trimmed, defaultRemark)
            else -> {
                // Try URI first then Conf fallback
                try {
                    parseUri(trimmed)
                } catch (_: Exception) {
                    parseConf(trimmed, defaultRemark)
                }
            }
        }
    }

    /**
     * Alias for [parse] to support round-trip serialization tests.
     */
    fun fromConfig(input: String, defaultRemark: String? = null): WireGuardParsedConfig = parse(input, defaultRemark)

    /**
     * Parses a `wireguard://` URI.
     */
    fun parseUri(uriStr: String): WireGuardParsedConfig {
        val trimmed = uriStr.trim()
        require(trimmed.startsWith("wireguard://", ignoreCase = true)) {
            "WireGuard URI must start with 'wireguard://'"
        }

        val withoutScheme = trimmed.substring("wireguard://".length)
        val withoutFragment = withoutScheme.substringBefore('#')
        val fragment = if (withoutScheme.contains('#')) withoutScheme.substringAfter('#') else null
        val remark = fragment?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }?.ifBlank { null }
            ?: "WireGuard Server"

        val queryStr = if (withoutFragment.contains('?')) withoutFragment.substringAfter('?') else ""
        val authority = withoutFragment.substringBefore('?')

        val queryParams = mutableMapOf<String, String>()
        if (queryStr.isNotEmpty()) {
            for (part in queryStr.split('&')) {
                val eq = part.indexOf('=')
                if (eq != -1) {
                    val rawKey = part.substring(0, eq)
                    val rawVal = part.substring(eq + 1)
                    val k = runCatching { URLDecoder.decode(rawKey, "UTF-8") }.getOrDefault(rawKey).trim().lowercase(Locale.ROOT)
                    val v = runCatching { URLDecoder.decode(rawVal, "UTF-8") }.getOrDefault(rawVal).trim()
                    queryParams[k] = v
                }
            }
        }

        val atIndex = authority.lastIndexOf('@')
        val rawSecretKey = if (atIndex > 0) {
            val keyPart = authority.substring(0, atIndex)
            runCatching { URLDecoder.decode(keyPart, "UTF-8") }.getOrDefault(keyPart)
        } else {
            queryParams["privatekey"] ?: queryParams["secretkey"] ?: queryParams["privkey"] ?: queryParams["sk"]
        }

        val hostPort = if (atIndex >= 0) authority.substring(atIndex + 1) else authority
        val (host, port) = parseHostAndPort(hostPort)
            ?: error("Invalid host/port authority in WireGuard URI: '$hostPort'")

        val rawPublicKey = queryParams["publickey"]
            ?: queryParams["pbk"]
            ?: queryParams["pubkey"]
            ?: queryParams["pk"]

        val rawPreSharedKey = queryParams["presharedkey"]
            ?: queryParams["preshared_key"]
            ?: queryParams["psk"]

        val addressParam = queryParams["address"]
            ?: queryParams["ip"]
            ?: queryParams["localaddress"]

        val localAddresses = if (!addressParam.isNullOrBlank()) {
            addressParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { normalizeAddress(it) }
        } else {
            listOf("10.0.0.2/32")
        }

        val mtu = queryParams["mtu"]?.toIntOrNull() ?: 1420
        val keepAlive = (queryParams["keepalive"]
            ?: queryParams["keep_alive"]
            ?: queryParams["ka"]
            ?: queryParams["persistentkeepalive"])?.toIntOrNull() ?: 0

        val reserved = queryParams["reserved"]?.let { parseReserved(it) }
        val dns = queryParams["dns"]
        val listenPort = (queryParams["listenport"] ?: queryParams["listen_port"])?.toIntOrNull()
        val allowedIpsParam = queryParams["allowedips"] ?: queryParams["allowed_ips"]
        val allowedIps = if (!allowedIpsParam.isNullOrBlank()) {
            allowedIpsParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            listOf("0.0.0.0/0")
        }

        require(!rawSecretKey.isNullOrBlank()) { "WireGuard URI is missing private/secret key" }
        require(!rawPublicKey.isNullOrBlank()) { "WireGuard URI is missing public key" }

        return WireGuardParsedConfig(
            remark = remark,
            secretKey = normalizeKey(rawSecretKey),
            publicKey = normalizeKey(rawPublicKey),
            preSharedKey = rawPreSharedKey?.let { normalizeKey(it) },
            endpointHost = host,
            endpointPort = port,
            localAddresses = localAddresses,
            mtu = mtu,
            keepAlive = keepAlive,
            reserved = reserved,
            dns = dns,
            listenPort = listenPort,
            allowedIps = allowedIps
        ).requireValid()
    }

    /**
     * Parses standard WireGuard `.conf` INI text containing `[Interface]` and `[Peer]` blocks.
     */
    fun parseConf(confText: String, defaultRemark: String? = null): WireGuardParsedConfig {
        var currentSection = ""
        var remark: String? = null
        var privateKey: String? = null
        val addresses = mutableListOf<String>()
        var dns: String? = null
        var mtu = 1420
        var listenPort: Int? = null
        var publicKey: String? = null
        var preSharedKey: String? = null
        var endpoint: String? = null
        var keepAlive = 0
        var reserved: List<Int>? = null
        val allowedIps = mutableListOf<String>()

        val lines = confText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Comment lines (# or ;)
            if (trimmed.startsWith("#") || trimmed.startsWith(";")) {
                val commentContent = trimmed.substring(1).trim()
                if (remark == null && commentContent.isNotEmpty()) {
                    val lower = commentContent.lowercase(Locale.ROOT)
                    if (lower.startsWith("name") || lower.startsWith("remark") || lower.startsWith("title")) {
                        val candidate = commentContent.substringAfter('=').substringAfter(':').trim()
                        if (candidate.isNotEmpty()) remark = candidate
                    } else if (!commentContent.contains("=") && !commentContent.contains(":") && !commentContent.startsWith("[")) {
                        remark = commentContent
                    }
                }
                continue
            }

            // Section header
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed.substring(1, trimmed.length - 1).trim().lowercase(Locale.ROOT)
                continue
            }

            val eqIndex = trimmed.indexOf('=')
            if (eqIndex == -1) continue
            val key = trimmed.substring(0, eqIndex).trim().lowercase(Locale.ROOT)
            val value = trimmed.substring(eqIndex + 1).trim()
            if (value.isEmpty()) continue

            when (currentSection) {
                "interface" -> {
                    when (key) {
                        "privatekey" -> privateKey = normalizeKey(value)
                        "address" -> {
                            val addrs = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            addresses.addAll(addrs.map { normalizeAddress(it) })
                        }
                        "dns" -> dns = value
                        "mtu" -> mtu = value.toIntOrNull() ?: 1420
                        "listenport", "listen_port" -> listenPort = value.toIntOrNull()
                    }
                }
                "peer" -> {
                    when (key) {
                        "publickey" -> publicKey = normalizeKey(value)
                        "presharedkey" -> preSharedKey = normalizeKey(value)
                        "endpoint" -> endpoint = value
                        "persistentkeepalive", "keepalive" -> keepAlive = value.toIntOrNull() ?: 0
                        "reserved" -> reserved = parseReserved(value)
                        "allowedips", "allowed_ips" -> {
                            val ips = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            if (ips.isNotEmpty()) {
                                allowedIps.clear()
                                allowedIps.addAll(ips)
                            }
                        }
                    }
                }
            }
        }

        require(!privateKey.isNullOrBlank()) { "WireGuard config is missing PrivateKey in [Interface]" }
        require(!publicKey.isNullOrBlank()) { "WireGuard config is missing PublicKey in [Peer]" }
        require(!endpoint.isNullOrBlank()) { "WireGuard config is missing Endpoint in [Peer]" }

        val (host, port) = parseHostAndPort(endpoint)
            ?: error("Invalid Endpoint in [Peer]: '$endpoint'")

        val finalRemark = remark ?: defaultRemark?.trim()?.ifBlank { null } ?: "WireGuard Server"

        return WireGuardParsedConfig(
            remark = finalRemark,
            secretKey = privateKey,
            publicKey = publicKey,
            preSharedKey = preSharedKey,
            endpointHost = host,
            endpointPort = port,
            localAddresses = if (addresses.isNotEmpty()) addresses else listOf("10.0.0.2/32"),
            mtu = mtu,
            keepAlive = keepAlive,
            reserved = reserved,
            dns = dns,
            listenPort = listenPort,
            allowedIps = if (allowedIps.isNotEmpty()) allowedIps else listOf("0.0.0.0/0")
        ).requireValid()
    }

    /**
     * Parses host and port from a string like "1.2.3.4:51820", "[2001:db8::1]:51820", or "wg.example.com:51820".
     */
    fun parseHostAndPort(endpointStr: String): Pair<String, Int>? {
        val ep = endpointStr.trim()
        if (ep.isEmpty()) return null
        return if (ep.startsWith("[")) {
            val host = ep.substringAfter('[').substringBefore(']')
            val portStr = ep.substringAfterLast("]:", "")
            val port = portStr.toIntOrNull() ?: 51820
            Pair(host, port)
        } else if (ep.contains(':')) {
            val host = ep.substringBeforeLast(':')
            val portStr = ep.substringAfterLast(':', "")
            val port = portStr.toIntOrNull() ?: 51820
            Pair(host, port)
        } else {
            Pair(ep, 51820)
        }
    }

    /**
     * Parses the 3-byte reserved field from comma-separated integers (e.g. "12, 34, 56") or a 3-byte base64 string.
     */
    fun parseReserved(reservedStr: String): List<Int> {
        val trimmed = reservedStr.trim().removePrefix("[").removeSuffix("]").trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.contains(",")) {
            val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            require(parts.size == 3) {
                "Reserved must contain exactly 3 comma-separated bytes, found ${parts.size} in '$reservedStr'"
            }
            return parts.map { part ->
                val num = part.toIntOrNull() ?: error("Invalid reserved byte: '$part'")
                require(num in 0..255) { "Reserved byte must be in range 0..255, found $num" }
                num
            }
        }

        // Try base64 decoding 3 bytes
        try {
            val decoded = Base64Util.decode(trimmed)
            if (decoded.size == 3) {
                return decoded.map { it.toInt() and 0xFF }
            }
        } catch (_: Exception) {}

        error("Invalid reserved format: '$reservedStr'. Expected 3 comma-separated integers (e.g. 12,34,56) or 3-byte base64 string")
    }

    /**
     * Ensures an IP address string has a CIDR mask suffix (defaulting to /32 for IPv4, /128 for IPv6).
     */
    fun normalizeAddress(addr: String): String {
        val trimmed = addr.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.contains('/')) return trimmed
        return if (trimmed.contains(':')) "$trimmed/128" else "$trimmed/32"
    }

    /**
     * Normalizes a private or public key: converts 64-character hex keys into base64 if needed.
     */
    fun normalizeKey(rawKey: String): String {
        val key = rawKey.trim()
        if (key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            val bytes = ByteArray(32) { i ->
                key.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return Base64Util.encodeToString(bytes)
        }
        return key
    }

    /**
     * Self-contained cross-platform Base64 helper compatible with both JVM unit tests and Android runtimes.
     */
    internal object Base64Util {
        fun decode(input: String): ByteArray {
            val clean = input.trim()
            return try {
                java.util.Base64.getDecoder().decode(clean)
            } catch (_: Throwable) {
                android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
            }
        }

        fun encodeToString(bytes: ByteArray): String {
            return try {
                java.util.Base64.getEncoder().encodeToString(bytes)
            } catch (_: Throwable) {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP).trim()
            }
        }
    }
}
