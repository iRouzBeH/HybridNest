package dev.rouzbeh.hybridvpn.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for subscription-meta parsing. These exercise the same regexes
 * as the Compose UI but are packaged under feature/dashboard to live beside the
 * code under test. They do not touch Android APIs.
 *
 * Because the parse helpers in DashboardScreen.kt are file-private, these tests
 * duplicate the regexes to lock in the detection contract: data and time tokens
 * may appear in the same profile name OR split across separate profiles.
 */
class SubscriptionMetaTest {

    private val volumePrimary = Regex("""(\d+(?:\.\d+)?)\s*GB\s*/\s*(\d+(?:\.\d+)?)\s*GB""", RegexOption.IGNORE_CASE)
    private val volumeAlt = Regex("""(\d+(?:\.\d+)?)\s*/\s*(\d+(?:\.\d+)?)\s*GB""", RegexOption.IGNORE_CASE)
    private val days = Regex("""(\d+)\s*Days?""", RegexOption.IGNORE_CASE)

    private fun isInfoName(name: String): Boolean =
        volumePrimary.containsMatchIn(name) || volumeAlt.containsMatchIn(name) || days.containsMatchIn(name)

    @Test
    fun `info-card detection matches single profile with both volume and days`() {
        assertTrue(isInfoName("Plan | 10 GB / 100 GB | 5 Days"))
    }

    @Test
    fun `info-card detection matches volume-only name`() {
        assertTrue(isInfoName("Data remaining 25.5 GB / 50 GB"))
        assertTrue(isInfoName("25 / 50 GB"))
    }

    @Test
    fun `info-card detection matches days-only name`() {
        assertTrue(isInfoName("Expires in 3 Days"))
        assertTrue(isInfoName("1 Day left"))
    }

    @Test
    fun `info-card detection rejects plain server names`() {
        assertNull(volumePrimary.find("VLESS TCP TLS"))
        assertNull(volumeAlt.find("VLESS TCP TLS"))
        assertNull(days.find("VLESS TCP TLS"))
        assertNull(days.find("Trojan WS"))
    }

    @Test
    fun `data and time split across separate profiles are both detected`() {
        val names = listOf("Plan A 10 GB / 40 GB", "Plan A 7 Days", "Plan A vless server")
        // Simulate aggregation: first name yields volume, second yields days.
        var vol: String? = null
        var day: String? = null
        for (n in names) {
            if (vol == null) vol = (volumePrimary.find(n) ?: volumeAlt.find(n))?.value
            if (day == null) day = days.find(n)?.value
        }
        assertEquals("10 GB / 40 GB", vol)
        assertEquals("7 Days", day)
    }

    @Test
    fun `regex captures numeric groups for aggregation`() {
        val vol = volumePrimary.find("41.79 GB / 100 GB")
        assertNotNull(vol)
        assertEquals("41.79", vol!!.groupValues[1])
        assertEquals("100", vol.groupValues[2])

        val d = days.find("11 Day")
        assertNotNull(d)
        assertEquals("11", d!!.groupValues[1])
    }
}
