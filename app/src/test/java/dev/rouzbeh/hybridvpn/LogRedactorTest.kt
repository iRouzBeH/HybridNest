package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.core.utils.LogRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract: secret VALUES never survive redaction; header/parameter NAMES do, so
 * logs remain debuggable. These tests use structurally-realistic but FAKE values —
 * no real credentials are reproduced here or in any patch/report.
 *
 * Line shape note: OpenConnect progress messages commonly concatenate several
 * headers on one line ("... Set-Cookie: x\nSet-Cookie: y" arrives as separate
 * messages; "Cookie: a; Authorization: b" style concatenation is what this suite
 * exercises for the multi-header scan path).
 */
class LogRedactorTest {

    @Test
    fun `set-cookie value is redacted but header name preserved`() {
        val line = "OpenConnect [2]: Set-Cookie: webvpncontext=FAKEVALUE123=; Max-Age=300; Secure; HttpOnly"
        val out = LogRedactor.redact(line)
        assertTrue(out.contains("Set-Cookie:"))
        assertFalse(out.contains("FAKEVALUE123"))
        assertFalse(out.contains("webvpncontext=FAKEVALUE123"))
        // The complete secret-bearing header value is removed; safe attributes
        // such as Secure/HttpOnly may be removed with it.
        assertTrue(out.contains("Set-Cookie: <redacted>"))
    }

    @Test
    fun `cookie and authorization headers are redacted`() {
        val out = LogRedactor.redact("Cookie: SESSION=abc; then Authorization: Bearer tok123")
        assertFalse(out.contains("SESSION=abc"))
        // Cookie is a header whose value runs to the next known header token;
        // this preserves the Authorization header name but not its value.
        assertFalse(out.contains("Bearer tok123"))
        assertTrue(out.contains("Cookie:"))
        assertTrue(out.contains("Authorization:"))
    }

    @Test
    fun `proxy-authorization is redacted`() {
        val out = LogRedactor.redact("Proxy-Authorization: Basic Zm9vOmJhcg==")
        assertFalse(out.contains("Zm9vOmJhcg=="))
        assertTrue(out.contains("Proxy-Authorization:"))
    }

    @Test
    fun `two set-cookie messages in one line are both redacted`() {
        val line = "Set-Cookie: a=1; Secure Set-Cookie: b=2; HttpOnly"
        val out = LogRedactor.redact(line)
        assertFalse(out.contains("a=1"))
        assertFalse(out.contains("b=2"))
        assertTrue(out.contains("Set-Cookie: <redacted>"))
    }

    @Test
    fun `webvpn session tokens are redacted anywhere in the line`() {
        val line = "Cookie obtained: webvpn=SECRETWEBVPN; webvpnc=bu:/&p:t&iu:1/"
        val out = LogRedactor.redact(line)
        assertFalse(out.contains("SECRETWEBVPN"))
        assertFalse(out.contains("bu:/&p:t&iu:1/"))
        assertTrue(out.contains("webvpn="))
        assertTrue(out.contains("webvpnc="))
    }

    @Test
    fun `credential-bearing url userinfo is redacted`() {
        val out = LogRedactor.redact("POST https://admin:hunter2@example.com/auth")
        assertFalse(out.contains("hunter2"))
        assertTrue(out.contains("admin:<redacted>@example.com"))
    }

    @Test
    fun `password query parameter is redacted`() {
        val out = LogRedactor.redact("GET /login?user=admin&PASSWORD=sekret")
        assertFalse(out.contains("sekret"))
        assertTrue(out.contains("PASSWORD=<redacted>"))
        assertTrue(out.contains("user=admin")) // non-secret params preserved
    }

    @Test
    fun `ordinary prose about cookies is not clobbered`() {
        val line = "Cookie obtained successfully"
        assertEquals(line, LogRedactor.redact(line))
    }

    @Test
    fun `non-secret headers pass through unchanged`() {
        val line = "OpenConnect [2]: Content-Type: text/xml"
        assertEquals(line, LogRedactor.redact(line))
    }

    @Test
    fun `realistic openconnect progress message is fully redacted`() {
        // Shape mirrors the device logcat (values are FAKE).
        val line = "OpenConnect [2]: Got HTTP response: HTTP/1.1 200 OK Set-Cookie: webvpncontext=FAKE1=; Secure; HttpOnly Content-Type: text/xml"
        val out = LogRedactor.redact(line)
        assertFalse(out.contains("FAKE1"))
        assertTrue(out.contains("Set-Cookie: <redacted>"))
        assertTrue(out.contains("Content-Type: text/xml"))
    }

    @Test
    fun `redaction is idempotent`() {
        val line = "Set-Cookie: webvpncontext=SECRET; Secure"
        val once = LogRedactor.redact(line)
        val twice = LogRedactor.redact(once)
        assertEquals(once, twice)
    }
}
