package dev.rouzbeh.hybridvpn.feature.dashboard

import org.json.JSONObject
import java.util.Locale

data class GeoIpResult(
    val ip: String,
    val countryCode: String,
    val countryName: String
) {
    val location: String get() = countryName
    val flagCode: String get() = countryCode
}

object GeoIpLookup {

    val PUBLIC_IP_ENDPOINTS: List<String> = listOf(
        "https://ipwho.is/",
        "https://api-ipv4.ip.sb/geoip",
        "https://v4.ifconfig.co/json",
        "https://api.myip.world/json",
        "https://free.freeipapi.com/api/json",
        "http://ip-api.com/json"
    )

    fun hostLookupUrls(ip: String): List<String> = listOf(
        "https://ipwho.is/$ip",
        "https://api.ip.sb/geoip/$ip",
        "http://ip-api.com/json/$ip"
    )

    fun isValidIpv4(ip: String): Boolean {
        val trimmed = ip.trim()
        if (trimmed.isEmpty() || trimmed.contains(':')) return false
        val parts = trimmed.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } && (part.toIntOrNull() ?: -1) in 0..255
        }
    }

    fun isValidIp(ip: String): Boolean {
        val trimmed = ip.trim()
        if (trimmed.isBlank() || trimmed == "0.0.0.0") return false
        return isValidIpv4(trimmed) || (trimmed.contains(':') && !trimmed.contains(' ') && !trimmed.contains('/'))
    }

    fun cleanCountryName(name: String): String {
        val trimmed = name.trim()
        val cleaned = if (trimmed.startsWith("The ", ignoreCase = true)) {
            trimmed.substring(4).trim()
        } else {
            trimmed
        }
        return if (cleaned.isNotBlank()) cleaned else "Unknown"
    }

    fun resolveCountryCode(rawCode: String, countryName: String): String {
        val code = rawCode.trim().lowercase()
        if (code.length == 2 && code != "un" && code != "xx" && code.all { it in 'a'..'z' }) {
            return code
        }
        if (countryName.isNotBlank() && countryName != "Unknown") {
            val match = Locale.getISOCountries().firstOrNull { iso ->
                val loc = Locale("", iso)
                loc.displayCountry.equals(countryName, ignoreCase = true) ||
                    loc.getDisplayCountry(Locale.ENGLISH).equals(countryName, ignoreCase = true)
            }
            if (match != null) return match.lowercase()
        }
        return "un"
    }

    private data class ParsedRaw(
        val ip: String,
        val rawCode: String,
        val country: String
    )

    fun parseResponse(url: String, responseBody: String): GeoIpResult? {
        return try {
            val trimmedBody = responseBody.trim()
            if (trimmedBody.isEmpty() || !trimmedBody.startsWith("{")) return null
            val json = JSONObject(trimmedBody)

            val rawParsed = when {
                url.contains("ipwho.is") -> {
                    if (json.has("success") && !json.optBoolean("success", true)) return null
                    val ip = json.optString("ip", "").trim()
                    val rawCode = json.optString("country_code", "un")
                    val rawCountry = json.optString("country", "")
                    ParsedRaw(ip, rawCode, rawCountry)
                }
                url.contains("ip.sb") -> {
                    val ip = json.optString("ip", "").trim()
                    val rawCode = json.optString("country_code", "un")
                    val rawCountry = json.optString("country", "")
                    ParsedRaw(ip, rawCode, rawCountry)
                }
                url.contains("ifconfig.co") -> {
                    val ip = json.optString("ip", "").trim()
                    val rawCode = json.optString("country_iso", json.optString("country_code", "un"))
                    val rawCountry = json.optString("country", "")
                    ParsedRaw(ip, rawCode, rawCountry)
                }
                url.contains("myip.world") -> {
                    val ip = json.optString("ip", "").trim()
                    val rawCode = json.optString("country_code", "un")
                    val rawCountry = json.optString("country", "")
                    ParsedRaw(ip, rawCode, rawCountry)
                }
                url.contains("freeipapi.com") -> {
                    val ip = json.optString("ipAddress", json.optString("ip", "")).trim()
                    val rawCode = json.optString("countryCode", json.optString("country_code", "un"))
                    val rawCountry = json.optString("countryName", json.optString("country", ""))
                    ParsedRaw(ip, rawCode, rawCountry)
                }
                url.contains("ip-api.com") -> {
                    if (json.optString("status", "").equals("fail", ignoreCase = true)) return null
                    val ip = json.optString("query", json.optString("ip", "")).trim()
                    val rawCode = json.optString("countryCode", json.optString("country_code", "un"))
                    val rawCountry = json.optString("country", json.optString("countryName", ""))
                    ParsedRaw(ip, rawCode, rawCountry)
                }
                else -> null
            } ?: return null

            val ip = rawParsed.ip
            if (!isValidIp(ip)) return null

            val rawCode = rawParsed.rawCode.trim()
            val country = cleanCountryName(
                if (rawParsed.country.isNotBlank()) rawParsed.country
                else if (rawCode.length == 2 && rawCode.all { it.isLetter() }) {
                    Locale("", rawCode).getDisplayCountry(Locale.ENGLISH)
                } else ""
            )
            val countryCode = resolveCountryCode(rawCode, country)

            GeoIpResult(ip = ip, countryCode = countryCode, countryName = country)
        } catch (_: Exception) {
            null
        }
    }
}
