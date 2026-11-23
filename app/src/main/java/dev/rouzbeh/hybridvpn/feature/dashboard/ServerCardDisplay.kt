package dev.rouzbeh.hybridvpn.feature.dashboard

import dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig

object ServerCardDisplay {

    /**
     * Formats a WireGuard server address for display in ServerItemCard.
     *
     * Returns "endpointHost:endpointPort" (or "[ipv6Host]:endpointPort") without
     * EVER exposing "wireguard://" or private key / credentials.
     * On parse failure or blank host, safely returns "WireGuard".
     */
    fun formatWireGuardSubtitle(serverAddress: String): String {
        val trimmed = serverAddress.trim()
        if (trimmed.isBlank()) return "WireGuard"

        // First attempt standard parsing via WireGuardConfig
        val parsed = runCatching { WireGuardConfig.parse(trimmed) }.getOrNull()
        if (parsed != null && parsed.endpointHost.isNotBlank()) {
            val host = parsed.endpointHost.trim()
            val port = parsed.endpointPort
            return formatHostPort(host, port)
        }

        // Fallback for partial/URI inputs starting with wireguard://
        if (trimmed.startsWith("wireguard://", ignoreCase = true)) {
            val withoutScheme = trimmed.substring("wireguard://".length)
            val withoutFragment = withoutScheme.substringBefore('#')
            val authority = withoutFragment.substringBefore('?')
            val atIndex = authority.lastIndexOf('@')
            if (atIndex >= 0) {
                val hostPort = authority.substring(atIndex + 1).trim()
                val parsedHp = WireGuardConfig.parseHostAndPort(hostPort)
                if (parsedHp != null && parsedHp.first.isNotBlank()) {
                    return formatHostPort(parsedHp.first, parsedHp.second)
                }
            }
        }

        return "WireGuard"
    }

    private fun formatHostPort(host: String, port: Int): String {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]").trim()
        if (cleanHost.isBlank() || cleanHost.contains("wireguard://", ignoreCase = true) || cleanHost.contains('@')) {
            return "WireGuard"
        }
        val isIpv6 = cleanHost.contains(':')
        return when {
            isIpv6 && port > 0 -> "[$cleanHost]:$port"
            isIpv6 -> "[$cleanHost]"
            port > 0 -> "$cleanHost:$port"
            else -> cleanHost
        }
    }
}
