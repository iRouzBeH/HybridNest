package dev.rouzbeh.hybridvpn.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for subscription-meta parsing. These exercise the real
 * [isSubscriptionInfoName] and [leadingSubscriptionInfoNames] helpers from
 * DashboardScreen.kt (internal, same package) so the detection contract is
 * locked against the production implementation, not a copy.
 *
 * Contract under test:
 *  - Volume token: "<num> <unit> <sep> <num> <unit>" (unit B/KB/MB/GB/TB,
 *    separator "/" or "|", ASCII or Persian/Arabic-Indic digits).
 *  - Time token: "<num> Day|Days|Hour|روز|ساعت" OR a bare number right after
 *    a ⏳/⌛/📅 emoji.
 *  - Only the contiguous LEADING run of info names is consumed; a real server
 *    such as "Germany 5 GB" (single volume, no pair) is never an info card.
 */
class SubscriptionMetaTest {

    // ---------- detection ----------

    @Test
    fun `info-card detection matches single name with volume and days`() {
        assertTrue(isSubscriptionInfoName("Plan | 10 GB / 100 GB | 5 Days"))
    }

    @Test
    fun `info-card detection matches volume-only names`() {
        assertTrue(isSubscriptionInfoName("Data 25.5 GB / 50 GB"))
        assertTrue(isSubscriptionInfoName("25 / 50 GB"))
        assertTrue(isSubscriptionInfoName("10 GB | 100 GB"))
        assertTrue(isSubscriptionInfoName("512 MB / 1 GB"))
    }

    @Test
    fun `info-card detection matches days-only names`() {
        assertTrue(isSubscriptionInfoName("Expires in 3 Days"))
        assertTrue(isSubscriptionInfoName("1 Day left"))
        assertTrue(isSubscriptionInfoName("12 Hour"))
        assertTrue(isSubscriptionInfoName("۳۰ روز"))
        assertTrue(isSubscriptionInfoName("24 ساعت"))
        assertTrue(isSubscriptionInfoName("⏳ 17"))
        assertTrue(isSubscriptionInfoName("📅 9"))
    }

    @Test
    fun `info-card detection matches persian-digit volume pair`() {
        assertTrue(isSubscriptionInfoName("۵۰ GB / ۱۰۰ GB"))
        assertTrue(isSubscriptionInfoName("۴۱٫۷۹ GB / ۱۰۰ GB"))
    }

    @Test
    fun `info-card detection rejects plain server names`() {
        assertFalse(isSubscriptionInfoName("VLESS TCP TLS"))
        assertFalse(isSubscriptionInfoName("Trojan WS"))
        assertFalse(isSubscriptionInfoName("🇩🇪 Germany | Premium"))
    }

    @Test
    fun `single volume without a pair is NOT an info card`() {
        // "Germany 5 GB" has one volume token but no "<v>/<v>" pair — a real server.
        assertFalse(isSubscriptionInfoName("Germany 5 GB"))
        assertFalse(isSubscriptionInfoName("Server 10 GB speed"))
    }

    // ---------- leading-scope ----------

    @Test
    fun `leading info run consumes only the contiguous top block`() {
        val names = listOf(
            "📊 10 GB / 100 GB",
            "⏳ 7 Days",
            "Germany 5 GB",       // real server — stops the run
            "20 GB / 40 GB"       // info-looking but NOT leading — ignored
        )
        val leading = leadingSubscriptionInfoNames(names)
        assertEquals(listOf("📊 10 GB / 100 GB", "⏳ 7 Days"), leading)
    }

    @Test
    fun `leading info run is empty when the first entry is a real server`() {
        val names = listOf("Germany 5 GB", "10 GB / 20 GB")
        assertTrue(leadingSubscriptionInfoNames(names).isEmpty())
    }

    @Test
    fun `leading info run stops at first non-info entry`() {
        val names = listOf("5 Days", "vless tcp", "3 Days")
        assertEquals(listOf("5 Days"), leadingSubscriptionInfoNames(names))
    }

    @Test
    fun `leading info run handles all-info and empty lists`() {
        assertEquals(2, leadingSubscriptionInfoNames(listOf("1 Day", "2 GB / 4 GB")).size)
        assertTrue(leadingSubscriptionInfoNames(emptyList()).isEmpty())
    }

    // ---------- meta parsing ----------

    @Test
    fun `parseSubscriptionMeta extracts volume and days`() {
        val meta = parseSubscriptionMeta("Plan | 41.79 GB / 100 GB | 11 Days")
        assertNotNull(meta)
        assertEquals("41.79 GB", meta!!.usedStr)
        assertEquals("100 GB", meta.totalStr)
        assertEquals(41.79, meta.usedGb!!, 0.001)
        assertEquals(100.0, meta.totalGb!!, 0.001)
        assertEquals(11, meta.daysLeft)
    }

    @Test
    fun `parseSubscriptionMeta returns null for a real server name`() {
        assertNull(parseSubscriptionMeta("Germany 5 GB"))
        assertNull(parseSubscriptionMeta("VLESS TCP TLS"))
    }

    @Test
    fun `parseSubscriptionMeta normalizes persian digits`() {
        val meta = parseSubscriptionMeta("۴۱٫۷۹ GB / ۱۰۰ GB")
        assertNotNull(meta)
        assertEquals("41.79 GB", meta!!.usedStr)
        assertEquals(41.79, meta.usedGb!!, 0.001)
    }
}
