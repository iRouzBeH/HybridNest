package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.feature.dashboard.ServerCardDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerCardDisplayTest {

    @Test
    fun `valid wireguard URI formats to host and port without leaking private key or scheme`() {
        val privateKey = "aW52YWxpZF9zZWNyZXRfa2V5X2Zvcl90ZXN0aW5nXzEyMzQ="
        val uri = "wireguard://$privateKey@185.10.20.30:51820?publickey=cHVibGljX2tleV9mb3JfdGVzdGluZ18xMjM0NTY3ODkwMTI=&address=10.0.0.2/32#TestServer"
        val subtitle = ServerCardDisplay.formatWireGuardSubtitle(uri)

        assertEquals("185.10.20.30:51820", subtitle)
        assertFalse(subtitle.contains(privateKey))
        assertFalse(subtitle.contains("wireguard://"))
        assertFalse(subtitle.contains("@"))
    }

    @Test
    fun `valid wireguard URI with domain endpoint formats correctly`() {
        val privateKey = "aW52YWxpZF9zZWNyZXRfa2V5X2Zvcl90ZXN0aW5nXzEyMzQ="
        val uri = "wireguard://$privateKey@wg.example.com:51820?publickey=cHVibGljX2tleV9mb3JfdGVzdGluZ18xMjM0NTY3ODkwMTI=&address=10.0.0.2/32"
        val subtitle = ServerCardDisplay.formatWireGuardSubtitle(uri)

        assertEquals("wg.example.com:51820", subtitle)
        assertFalse(subtitle.contains(privateKey))
        assertFalse(subtitle.contains("wireguard://"))
    }

    @Test
    fun `valid wireguard URI with IPv6 endpoint formats correctly with brackets`() {
        val privateKey = "aW52YWxpZF9zZWNyZXRfa2V5X2Zvcl90ZXN0aW5nXzEyMzQ="
        val uri = "wireguard://$privateKey@[2001:db8::1]:51820?publickey=cHVibGljX2tleV9mb3JfdGVzdGluZ18xMjM0NTY3ODkwMTI=&address=10.0.0.2/32"
        val subtitle = ServerCardDisplay.formatWireGuardSubtitle(uri)

        assertEquals("[2001:db8::1]:51820", subtitle)
        assertFalse(subtitle.contains(privateKey))
        assertFalse(subtitle.contains("wireguard://"))
    }

    @Test
    fun `standard INI WireGuard config string formats without leaking PrivateKey`() {
        val privateKey = "aW52YWxpZF9zZWNyZXRfa2V5X2Zvcl90ZXN0aW5nXzEyMzQ="
        val conf = """
            [Interface]
            PrivateKey = $privateKey
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = cHVibGljX2tleV9mb3JfdGVzdGluZ18xMjM0NTY3ODkwMTI=
            Endpoint = 198.51.100.1:51820
        """.trimIndent()
        val subtitle = ServerCardDisplay.formatWireGuardSubtitle(conf)

        assertEquals("198.51.100.1:51820", subtitle)
        assertFalse(subtitle.contains(privateKey))
        assertFalse(subtitle.contains("PrivateKey"))
    }

    @Test
    fun `malformed wireguard URI without endpoint never leaks private key`() {
        val secretKey = "SECRET_PRIVATE_KEY_XYZ"
        val malformedUri = "wireguard://$secretKey"
        val subtitle = ServerCardDisplay.formatWireGuardSubtitle(malformedUri)

        assertEquals("WireGuard", subtitle)
        assertFalse(subtitle.contains(secretKey))
        assertFalse(subtitle.contains("wireguard://"))
    }

    @Test
    fun `malformed wireguard URI with trailing at sign never leaks private key`() {
        val secretKey = "SECRET_PRIVATE_KEY_XYZ"
        val malformedUri = "wireguard://$secretKey@"
        val subtitle = ServerCardDisplay.formatWireGuardSubtitle(malformedUri)

        assertEquals("WireGuard", subtitle)
        assertFalse(subtitle.contains(secretKey))
        assertFalse(subtitle.contains("@"))
    }

    @Test
    fun `blank or empty input safely returns WireGuard fallback`() {
        assertEquals("WireGuard", ServerCardDisplay.formatWireGuardSubtitle(""))
        assertEquals("WireGuard", ServerCardDisplay.formatWireGuardSubtitle("   "))
    }
}
