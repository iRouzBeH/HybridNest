package dev.rouzbeh.hybridvpn.core.vpn.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DnsPacket] wire helpers. Pure JVM — no Android deps.
 */
class DnsPacketTest {

    /** Builds a minimal valid A query for "example.com" with the given ID. */
    private fun buildQuery(id: Int = 0x1234, host: String = "example.com"): ByteArray {
        val qname = host.split(".").flatMap { label ->
            listOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII).toList()
        }.toByteArray() + 0
        val buf = java.nio.ByteBuffer.allocate(12 + qname.size + 4)
        buf.putShort(id.toShort())
        buf.putShort(0x0100) // RD=1
        buf.putShort(1); buf.putShort(0); buf.putShort(0); buf.putShort(0)
        buf.put(qname)
        buf.putShort(1); buf.putShort(1) // A, IN
        return buf.array()
    }

    @Test
    fun `id reads the 16 bit transaction id`() {
        val q = buildQuery(id = 0xBEEF)
        assertEquals(0xBEEF, DnsPacket.id(q, q.size))
    }

    @Test
    fun `id returns -1 for short buffer`() {
        assertEquals(-1, DnsPacket.id(byteArrayOf(0x01), 1))
    }

    @Test
    fun `isResponse detects QR bit`() {
        val q = buildQuery()
        assertFalse(DnsPacket.isResponse(q, q.size))
        q[2] = (q[2].toInt() or 0x80).toByte()
        assertTrue(DnsPacket.isResponse(q, q.size))
    }

    @Test
    fun `isTruncated detects TC bit`() {
        val q = buildQuery()
        assertFalse(DnsPacket.isTruncated(q, q.size))
        q[2] = (q[2].toInt() or 0x02).toByte()
        assertTrue(DnsPacket.isTruncated(q, q.size))
    }

    @Test
    fun `questionCount reads QDCOUNT`() {
        val q = buildQuery()
        assertEquals(1, DnsPacket.questionCount(q, q.size))
    }

    @Test
    fun `setId overwrites the transaction id`() {
        val q = buildQuery(id = 0x0001)
        assertTrue(DnsPacket.setId(q, 0xABCD))
        assertEquals(0xABCD, DnsPacket.id(q, q.size))
    }

    @Test
    fun `setId returns false for short buffer`() {
        assertFalse(DnsPacket.setId(byteArrayOf(0x00), 5))
    }

    @Test
    fun `buildResponse SERVFAIL has no answers and preserves id`() {
        val q = buildQuery(id = 0x7777)
        val resp = DnsPacket.buildResponse(q, q.size, rcode = 2)
        assertNotNull(resp)
        resp!!
        // Same transaction id.
        assertEquals(0x7777, DnsPacket.id(resp, resp.size))
        // QR set.
        assertTrue(DnsPacket.isResponse(resp, resp.size))
        // RCODE = SERVFAIL (low nibble of byte 3).
        assertEquals(2, resp[3].toInt() and 0x0F)
        // ANCOUNT = 0.
        assertEquals(0, ((resp[6].toInt() and 0xFF) shl 8) or (resp[7].toInt() and 0xFF))
        // Question section is preserved verbatim.
        assertEquals(1, DnsPacket.questionCount(resp, resp.size))
    }

    @Test
    fun `buildResponse echoes the question bytes`() {
        val q = buildQuery(host = "a.b.c.d")
        val resp = DnsPacket.buildResponse(q, q.size, rcode = 2)!!
        // Question section (after 12-byte header) must match the query.
        val qQuestion = q.copyOfRange(12, q.size)
        val rQuestion = resp.copyOfRange(12, 12 + qQuestion.size)
        assertArrayEquals(qQuestion, rQuestion)
    }

    @Test
    fun `buildResponse returns null for malformed query`() {
        assertNull(DnsPacket.buildResponse(byteArrayOf(1, 2, 3), 3, rcode = 2))
        // Zero questions -> null.
        val empty = java.nio.ByteBuffer.allocate(12)
            .putShort(1).putShort(0).putShort(0).putShort(0).putShort(0).putShort(0).array()
        assertNull(DnsPacket.buildResponse(empty, empty.size, rcode = 2))
    }

    @Test
    fun `buildResponse rejects unterminated QNAME`() {
        // Header + a label length but no terminating zero -> malformed.
        val bad = byteArrayOf(
            0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x03, 'w'.code.toByte(), 'w'.code.toByte() // truncated label
        )
        assertNull(DnsPacket.buildResponse(bad, bad.size, rcode = 2))
    }
}
