package dev.rouzbeh.hybridvpn.feature.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotDetectorTest {

    @Test
    fun `detectHotspotOrLanIp returns a valid non-empty IPv4 address`() {
        val ip = detectHotspotOrLanIp()
        assertNotNull(ip)
        assertTrue(ip.isNotBlank())
        // Must be an IPv4 format with 4 octets
        val parts = ip.split(".")
        assertEquals(4, parts.size)
        parts.forEach { octet ->
            val num = octet.toIntOrNull()
            assertNotNull("Octet '$octet' must be numeric", num)
            assertTrue("Octet $num must be in 0..255", num!! in 0..255)
        }
        // Must never return the VPN tun IP (172.19.0.1)
        assertFalse(ip == "172.19.0.1")
    }

    private fun assertEquals(expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
