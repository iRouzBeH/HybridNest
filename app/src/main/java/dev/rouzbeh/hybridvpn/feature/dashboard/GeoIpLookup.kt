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

    val PRIMARY_PUBLIC_IP_ENDPOINTS: List<String> = listOf(
        "https://ipinfo.io/json",
        "https://ipwho.is/"
    )

    val FALLBACK_PUBLIC_IP_ENDPOINTS: List<String> = listOf(
        "https://free.freeipapi.com/api/json",
        "https://api-ipv4.ip.sb/geoip",
        "https://ifconfig.co/json",
        "http://ip-api.com/json"
    )

    val PUBLIC_IP_ENDPOINTS: List<String> = PRIMARY_PUBLIC_IP_ENDPOINTS + FALLBACK_PUBLIC_IP_ENDPOINTS

    fun primaryHostLookupUrls(ip: String): List<String> = listOf(
        "https://ipinfo.io/$ip/json",
        "https://ipwho.is/$ip"
    )

    fun fallbackHostLookupUrls(ip: String): List<String> = listOf(
        "https://free.freeipapi.com/api/json/$ip",
        "https://api.ip.sb/geoip/$ip",
        "http://ip-api.com/json/$ip"
    )

    fun hostLookupUrls(ip: String): List<String> = primaryHostLookupUrls(ip) + fallbackHostLookupUrls(ip)

    fun isValidIpv4(ip: String): Boolean {
        val trimmed = ip.trim()
        if (trimmed.isEmpty() || trimmed.contains(':')) return false
        val parts = trimmed.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } && (part.toIntOrNull() ?: -1) in 0..255
        }
    }

    fun isIpv4Address(ip: String): Boolean = isValidIpv4(ip)

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

    fun resolveCountryName(countryCode: String): String {
        val code = countryCode.trim()
        if (code.length != 2 || !code.all { it.isLetter() } || code.equals("un", ignoreCase = true) || code.equals("xx", ignoreCase = true)) {
            return ""
        }
        val display = Locale("", code.uppercase(Locale.US)).getDisplayCountry(Locale.ENGLISH)
        val cleaned = cleanCountryName(display)
        return if (cleaned.isNotBlank() && !cleaned.equals(code, ignoreCase = true) && cleaned != "Unknown") {
            cleaned
        } else {
            code.uppercase(Locale.US)
        }
    }

    fun resolveCountryCode(rawCode: String, countryName: String): String {
        val code = rawCode.trim().lowercase(Locale.US)
        if (code.length == 2 && code != "un" && code != "xx" && code.all { it in 'a'..'z' }) {
            return code
        }
        if (countryName.isNotBlank() && countryName != "Unknown") {
            val match = Locale.getISOCountries().firstOrNull { iso ->
                val loc = Locale("", iso)
                loc.displayCountry.equals(countryName, ignoreCase = true) ||
                    loc.getDisplayCountry(Locale.ENGLISH).equals(countryName, ignoreCase = true)
            }
            if (match != null) return match.lowercase(Locale.US)
        }
        return "un"
    }

    fun selectConsensusResult(orderedCandidates: List<GeoIpResult>): GeoIpResult? {
        val ipv4Candidates = orderedCandidates.filter { isIpv4Address(it.ip) }
        val effectivePool = if (ipv4Candidates.isNotEmpty()) ipv4Candidates else orderedCandidates
        if (effectivePool.isEmpty()) return null
        if (effectivePool.size == 1) return effectivePool.first()

        fun isValidCode(code: String): Boolean {
            val c = code.trim().lowercase(Locale.US)
            return c.length == 2 && c != "un" && c != "xx" && c.all { it in 'a'..'z' }
        }

        val validCandidates = effectivePool.filter { isValidCode(it.countryCode) }
        if (validCandidates.isEmpty()) {
            return effectivePool.first()
        }

        val votes = mutableMapOf<String, Int>()
        for (c in validCandidates) {
            val code = c.countryCode.lowercase(Locale.US)
            votes[code] = (votes[code] ?: 0) + 1
        }

        val maxVotes = votes.values.maxOrNull() ?: 0
        if (maxVotes >= 2) {
            val winningCodes = votes.filter { it.value == maxVotes }.keys
            val matching = effectivePool.filter { it.countryCode.lowercase(Locale.US) in winningCodes }
            return matching.firstOrNull { it.countryName.isNotBlank() && it.countryName != "Unknown" }
                ?: matching.first()
        }

        return effectivePool.firstOrNull { isValidCode(it.countryCode) && it.countryName.isNotBlank() && it.countryName != "Unknown" }
            ?: effectivePool.firstOrNull { isValidCode(it.countryCode) }
            ?: effectivePool.first()
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
                url.contains("ipinfo.io") -> {
                    if (json.optBoolean("bogon", false)) return null
                    val ip = json.optString("ip", "").trim()
                    val rawCode = json.optString("country", "un")
                    val rawCountry = resolveCountryName(rawCode)
                    ParsedRaw(ip, rawCode, rawCountry)
                }
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
                else resolveCountryName(rawCode)
            )
            val countryCode = resolveCountryCode(rawCode, country)

            GeoIpResult(ip = ip, countryCode = countryCode, countryName = country)
        } catch (_: Exception) {
            null
        }
    }
}
