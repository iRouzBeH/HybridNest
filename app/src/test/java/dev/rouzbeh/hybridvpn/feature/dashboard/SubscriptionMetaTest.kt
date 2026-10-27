package dev.rouzbeh.hybridvpn.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for subscription-meta parsing and leading-run merging.
 * Verifies [InfoRemarkClassifier], [mergeLeadingInfo], and backward-compatible helpers.
 */
class SubscriptionMetaTest {

    private val timeEntry = "👤VIP-6293392523-94328 | ⏳ 24"
    private val volumeEntry = "🔋5.40 GB | 🪫10.00 GB"

    @Test
    fun `time entry with user id extracts time 24 day without volume`() {
        val piece = InfoRemarkClassifier.classify("👤VIP-6293392523-94328 | ⏳ 24")
        assertNotNull(piece)
        assertEquals(emptyList<String>(), piece!!.volumeValues)
        assertEquals(24, piece.timeAmount)
        assertEquals("day", piece.timeUnit)
    }

    @Test
    fun `time entry with variation selector extracts time 24 day`() {
        val piece = InfoRemarkClassifier.classify("👤\uFE0FVIP-6293392523-94328 | ⏳\uFE0F 24")
        assertNotNull(piece)
        assertEquals(emptyList<String>(), piece!!.volumeValues)
        assertEquals(24, piece.timeAmount)
        assertEquals("day", piece.timeUnit)
    }

    @Test
    fun `time entry with bidi marker at start extracts time 24 day`() {
        val piece = InfoRemarkClassifier.classify("\u200E👤VIP-6293392523-94328 | ⏳ 24")
        assertNotNull(piece)
        assertEquals(emptyList<String>(), piece!!.volumeValues)
        assertEquals(24, piece.timeAmount)
        assertEquals("day", piece.timeUnit)
    }

    @Test
    fun `volume entry extracts volume pair without time`() {
        val piece = InfoRemarkClassifier.classify("🔋5.40 GB | 🪫10.00 GB")
        assertNotNull(piece)
        assertEquals(listOf("5.40 GB", "10.00 GB"), piece!!.volumeValues)
        assertNull(piece.timeAmount)
        assertNull(piece.timeUnit)
    }

    @Test
    fun `volume entry with variation selector extracts volume pair`() {
        val piece = InfoRemarkClassifier.classify("🔋\uFE0F5.40 GB | 🪫\uFE0F10.00 GB")
        assertNotNull(piece)
        assertEquals(listOf("5.40 GB", "10.00 GB"), piece!!.volumeValues)
        assertNull(piece.timeAmount)
        assertNull(piece.timeUnit)
    }

    @Test
    fun `volume entry with persian digits extracts normalized volume pair`() {
        val piece = InfoRemarkClassifier.classify("🔋۵٫۴۰ GB | 🪫۱۰٫۰۰ GB")
        assertNotNull(piece)
        assertEquals(listOf("5.40 GB", "10.00 GB"), piece!!.volumeValues)
        assertNull(piece.timeAmount)
        assertNull(piece.timeUnit)
    }

    @Test
    fun `single entry with volume and day extracts both`() {
        val piece = InfoRemarkClassifier.classify("47.48 GB / 100.0 GB | 1 Day")
        assertNotNull(piece)
        assertEquals(listOf("47.48 GB", "100.0 GB"), piece!!.volumeValues)
        assertEquals(1, piece.timeAmount)
        assertEquals("day", piece.timeUnit)
    }

    @Test
    fun `real server with single volume returns null`() {
        assertNull(InfoRemarkClassifier.classify("Germany 5 GB"))
        assertFalse(isSubscriptionInfoName("Germany 5 GB"))
    }

    @Test
    fun `real servers and noise return null`() {
        assertNull(InfoRemarkClassifier.classify("▪Frankfurt"))
        assertNull(InfoRemarkClassifier.classify("TUNNEL- 1/2"))
        assertNull(InfoRemarkClassifier.classify("⚡ Germany | همراه اول"))

        assertFalse(isSubscriptionInfoName("▪Frankfurt"))
        assertFalse(isSubscriptionInfoName("TUNNEL- 1/2"))
        assertFalse(isSubscriptionInfoName("⚡ Germany | همراه اول"))
    }

    @Test
    fun `mergeLeadingInfo with time then volume consumes 2 with both parts`() {
        val list = listOf(timeEntry, volumeEntry, "▪Frankfurt", "▪Berlin")
        val (consumed, piece) = mergeLeadingInfo(list)
        assertEquals(2, consumed)
        assertNotNull(piece)
        assertEquals(listOf("5.40 GB", "10.00 GB"), piece!!.volumeValues)
        assertEquals(24, piece.timeAmount)
        assertEquals("day", piece.timeUnit)

        val meta = piece.toSubscriptionMeta()
        assertEquals("5.40 GB", meta.usedStr)
        assertEquals("10.00 GB", meta.totalStr)
        assertEquals(24, meta.daysLeft)
        assertEquals("24 Days", meta.daysRaw)
    }

    @Test
    fun `mergeLeadingInfo with volume then time consumes 2 with both parts`() {
        val list = listOf(volumeEntry, timeEntry, "Germany")
        val (consumed, piece) = mergeLeadingInfo(list)
        assertEquals(2, consumed)
        assertNotNull(piece)
        assertEquals(listOf("5.40 GB", "10.00 GB"), piece!!.volumeValues)
        assertEquals(24, piece.timeAmount)
        assertEquals("day", piece.timeUnit)
    }

    @Test
    fun `mergeLeadingInfo starting with real server consumes 0`() {
        val list = listOf("Frankfurt", volumeEntry)
        val (consumed, piece) = mergeLeadingInfo(list)
        assertEquals(0, consumed)
        assertNull(piece)
    }

    @Test
    fun `parseSubscriptionMeta compatibility helper`() {
        val metaSingle = parseSubscriptionMeta("47.48 GB / 100.0 GB | 1 Day")
        assertNotNull(metaSingle)
        assertEquals("47.48 GB", metaSingle!!.usedStr)
        assertEquals("100.0 GB", metaSingle.totalStr)
        assertEquals(1, metaSingle.daysLeft)

        assertNull(parseSubscriptionMeta("Germany 5 GB"))

        val metaList = parseSubscriptionMeta(listOf(timeEntry, volumeEntry, "Frankfurt"))
        assertNotNull(metaList)
        assertEquals("5.40 GB", metaList!!.usedStr)
        assertEquals("10.00 GB", metaList.totalStr)
        assertEquals(24, metaList.daysLeft)
        assertEquals("24 Days", metaList.daysRaw)
    }
}
