package dev.rouzbeh.hybridvpn.core.utils

/**
 * Producer-side redaction of secret-bearing material before it reaches logcat or
 * the UI log flows.
 *
 * Why the producer: the 2026-10-07 device logcat captured real `Set-Cookie` values
 * (webvpncontext=…, webvpn=…, webvpnc=…) because OpenConnect progress output was
 * forwarded verbatim. Redacting only in the UI would leave secrets in logcat, so
 * the app's single logging entry points call [redact] FIRST.
 *
 * What is redacted (value only; the header/parameter NAME is preserved so logs stay
 * useful for debugging):
 *  - `Set-Cookie:`, `Cookie:`, `Authorization:`, `Proxy-Authorization:` headers.
 *    The header value runs to the next KNOWN header token, a newline, or end of
 *    text — OpenConnect progress messages concatenate several headers on one line,
 *    so scanning for known header names as value terminators is the reliable way
 *    to stop at the right place without eating following headers.
 *  - Session/VPN cookie tokens such as `webvpn…=<value>` anywhere in the line.
 *  - Credential-bearing URLs: `https://user:pass@host/...` (userinfo).
 *  - `password=…` query/form parameters (case-insensitive name).
 *
 * What is NOT touched: everything else (status lines, header names, non-secret
 * header values, packet metadata). This keeps enough safe session/policy context
 * for debugging.
 *
 * Pure Kotlin with no Android dependencies, so the behavior is JVM-testable.
 */
object LogRedactor {

    // Header names whose values must never appear in logs. Case-insensitive.
    private val SECRET_HEADERS = listOf(
        "set-cookie", "cookie", "authorization", "proxy-authorization"
    )

    /**
     * All header names recognized as VALUE TERMINATORS. A secret header's value
     * ends where one of these (preceded by whitespace) begins. Includes the secret
     * headers themselves (two Set-Cookie lines in a row) plus the common CSTP
     * response headers seen in OpenConnect output.
     */
    private val ALL_KNOWN_HEADERS = SECRET_HEADERS + listOf(
        "content-type", "content-length", "x-transcend-version",
        "strict-transport-security", "x-frame-options", "x-content-type-options",
        "content-security-policy", "x-permitted-cross-domain-policies",
        "referrer-policy", "clear-site-data", "cross-origin-embedder-policy",
        "cross-origin-opener-policy", "cross-origin-resource-policy",
        "x-xss-protection", "pragma", "cache-control", "connection",
        "x-cstp-version", "x-cstp-server-name", "x-cstp-hostname", "x-cstp-dpd",
        "x-dtls-dpd", "x-dtls-port", "x-cstp-base-mtu", "x-cstp-mtu",
        "x-cstp-keepalive", "x-dtls-ciphersuite", "http-body-length",
        "tcp_info", "date", "server", "etag", "location", "host"
    )

    // Value-replacement placeholder keeps the shape of the log line.
    private const val REDACTED = "<redacted>"

    /**
     * Master switch for strict producer-side redaction.
     * When disabled, lines are passed through verbatim.
     */
    @JvmField
    @Volatile
    var strictRedactionEnabled: Boolean = true

    /**
     * Redacts a single log line. Idempotent: already-redacted lines pass through
     * unchanged, so double-redaction cannot corrupt output.
     */
    fun redact(line: String): String {
        if (!strictRedactionEnabled || line.isEmpty()) return line
        var out = line

        // 1. Secret-bearing headers: "Name: value" -> "Name: <redacted>".
        for (header in SECRET_HEADERS) {
            out = out.replaceSecretHeader(header, REDACTED)
        }

        // 2. VPN session tokens: webvpn…=<value> (value runs to ';', whitespace or end).
        out = out.replaceRegex(Regex("\\b(webvpn[A-Za-z0-9_-]*)\\s*=\\s*[^;\\s]+", RegexOption.IGNORE_CASE)) {
            "${it.groupValues[1]}=$REDACTED"
        }

        // 3. Credential-bearing URLs: scheme://user:password@host or wireguard://privatekey@endpoint
        out = out.replaceRegex(Regex("(https?://)([^/\\s:]+):([^/\\s@]+)@", RegexOption.IGNORE_CASE)) {
            "${it.groupValues[1]}${it.groupValues[2]}:$REDACTED@"
        }
        out = out.replaceRegex(Regex("(wireguard://)([^@\\s]+)@", RegexOption.IGNORE_CASE)) {
            "${it.groupValues[1]}$REDACTED@"
        }

        // 4. password=<value>, presharedkey=<value>, secretKey=<value> form/query parameters.
        out = out.replaceRegex(Regex("\\b(password|pass|secret|token|presharedkey|preshared_key|psk|secretkey|secret_key|privatekey|private_key)\\s*=\\s*[^&\\s]+", RegexOption.IGNORE_CASE)) {
            "${it.groupValues[1]}=$REDACTED"
        }

        // 5. JSON fields for keys/secrets: "secretKey": "..." / "preSharedKey": "..."
        out = out.replaceRegex(Regex("\"(secretKey|secret_key|privateKey|private_key|preSharedKey|pre_shared_key|psk)\"\\s*:\\s*\"[^\"]+\"", RegexOption.IGNORE_CASE)) {
            "\"${it.groupValues[1]}\": \"$REDACTED\""
        }

        return out
    }

    /**
     * Redacts the VALUE of every `[Prefix]Name: value` occurrence for [headerName]
     * where the name appears at a message boundary (start of text, after a newline,
     * or after the OpenConnect progress prefix "OpenConnect [n]: ").
     *
     * The value runs until the next whitespace-preceded KNOWN header token, a
     * newline, or end of text — so concatenated header lines each keep their name
     * and lose only their value.
     */
    private fun String.replaceSecretHeader(headerName: String, replacement: String): String {
        val sb = StringBuilder(this)
        val needle = "$headerName:"

        // Walk occurrences left-to-right; each replacement shortens the builder, so
        // recompute positions each pass but start after the replacement just made.
        var searchFrom = 0
        while (true) {
            val idx = findBoundaryMatch(sb, needle, searchFrom) ?: break

            var valueStart = idx + needle.length
            if (valueStart < sb.length && sb[valueStart] == ' ') valueStart += 1

            // Already-redacted values must not be matched and replaced forever.
            if (sb.regionMatches(valueStart, replacement, 0, replacement.length)) {
                searchFrom = valueStart + replacement.length
                continue
            }

            val valueEnd = findValueEnd(sb, valueStart)
            sb.replace(valueStart, valueEnd, replacement)
            searchFrom = valueStart + replacement.length
        }
        return sb.toString()
    }

    /** Finds the next occurrence of [needle] at a valid header boundary, or null. */
    private fun findBoundaryMatch(sb: StringBuilder, needle: String, startIndex: Int): Int? {
        var i = startIndex.coerceAtLeast(0)
        while (i <= sb.length - needle.length) {
            if (sb.regionMatches(i, needle, 0, needle.length, ignoreCase = true) &&
                isHeaderBoundary(sb, i)
            ) {
                return i
            }
            i++
        }
        return null
    }

    private fun isHeaderBoundary(sb: StringBuilder, idx: Int): Boolean {
        if (idx == 0) return true
        if (sb[idx - 1] == '\n' || sb[idx - 1] == '\t') return true
        // Header output can be concatenated after an HTTP status/body token, e.g.
        // "... 200 OK Set-Cookie: ..." or "Cookie: ... Authorization: ...".
        // An exact known header token preceded by whitespace is sufficient; ordinary
        // prose such as "Cookie obtained" has no colon and never matches needle.
        if (sb[idx - 1] == ' ') return true
        return false
    }

    /**
     * Value end = first position at/after [from] where a KNOWN header name (with a
     * following colon) begins after whitespace, or the first newline, or text end.
     */
    private fun findValueEnd(sb: StringBuilder, from: Int): Int {
        var i = from
        while (i < sb.length) {
            val c = sb[i]
            if (c == '\n') return i
            if (c == ' ' || c == '\t') {
                // Skip whitespace, then check for "Name:" against every known header.
                var j = i
                while (j < sb.length && (sb[j] == ' ' || sb[j] == '\t')) j++
                for (h in ALL_KNOWN_HEADERS) {
                    if (j + h.length < sb.length &&
                        sb.regionMatches(j, h, 0, h.length, ignoreCase = true) &&
                        sb[j + h.length] == ':'
                    ) {
                        return i
                    }
                }
            }
            i++
        }
        return sb.length
    }

    private fun String.replaceRegex(regex: Regex, transform: (MatchResult) -> CharSequence): String =
        regex.replace(this, transform)
}
