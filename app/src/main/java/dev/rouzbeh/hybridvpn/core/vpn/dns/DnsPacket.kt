package dev.rouzbeh.hybridvpn.core.vpn.dns

/**
 * Minimal DNS wire-format helpers for the packet-level forwarder.
 *
 * The forwarder never parses full queries: a DNS message is treated as
 * opaque except for the header fields needed to route and synthesize
 * failure responses (ID at bytes 0-1, flags at 2-3, QDCOUNT at 4-5).
 *
 * Pure JVM (no Android dependencies) so it is unit-testable.
 */
object DnsPacket {

    const val HEADER_LEN = 12

    // Header flag bits (byte offsets within the 12-byte header).
    private const val FLAG_QR = 0x80      // byte 2, bit 7: response
    private const val FLAG_TC = 0x02      // byte 2, bit 1: truncation
    private const val FLAG_RD = 0x01      // byte 2, bit 0: recursion desired
    private const val FLAG_RA = 0x80      // byte 3, bit 7: recursion available
    private const val RCODE_MASK = 0x0F   // byte 3, low nibble: response code
    private const val RCODE_SERVFAIL = 2

    /** Extracts the 16-bit transaction ID, or -1 if the buffer is too short. */
    fun id(packet: ByteArray, len: Int): Int {
        if (len < 2) return -1
        return ((packet[0].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
    }

    /** True when the QR bit is set (packet is a response, not a query). */
    fun isResponse(packet: ByteArray, len: Int): Boolean {
        if (len < HEADER_LEN) return false
        return (packet[2].toInt() and FLAG_QR) != 0
    }

    /** True when the TC bit is set (truncated response → retry over TCP). */
    fun isTruncated(packet: ByteArray, len: Int): Boolean {
        if (len < HEADER_LEN) return false
        return (packet[2].toInt() and FLAG_TC) != 0
    }

    /** QDCOUNT field (number of questions). */
    fun questionCount(packet: ByteArray, len: Int): Int {
        if (len < 6) return 0
        return ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
    }

    /**
     * Overwrites the 16-bit transaction ID. Used to translate between the
     * client's ID (inside the TUN packet) and the ID used upstream. Returns
     * false when the buffer is too small.
     */
    fun setId(packet: ByteArray, id: Int): Boolean {
        if (packet.size < 2) return false
        packet[0] = (id ushr 8).toByte()
        packet[1] = id.toByte()
        return true
    }

    /**
     * Builds a DNS response packet from a query: copies the header + question
     * section verbatim, sets QR/RA (and RD preserved from the query), and
     * attaches a single answer RR of type MX(15) or NULL(10) when given,
     * so the response has at least one answer (needed so that the OS marks
     * the DNS server as functional and does not retry or fall back to a
     * second resolver while traffic is meant to use this one).
     *
     * When [rcode] is non-zero, produces an error response with no answer
     * section (SERVFAIL etc.), which signals the resolver to try the
     * secondary server.
     *
     * Returns the serialized response packet, or null when the query is
     * malformed (too short, zero questions).
     */
    fun buildResponse(
        query: ByteArray,
        queryLen: Int,
        rcode: Int = 0,
        answerRr: ByteArray? = null,
        copySubnetOpt: Boolean = true
    ): ByteArray? {
        if (queryLen < HEADER_LEN || questionCount(query, queryLen) < 1) return null
        // Length of the question section = header + the first question.
        // We keep the whole query message up to its original length: simplest
        // valid echo is header+questions as they appeared (the query has no
        // answer section). Any additional sections in the query are dropped.
        val questionEnd = skipQuestion(query, queryLen) ?: return null

        val flags2 = FLAG_QR or (query[2].toInt() and FLAG_RD) // QR=1, keep RD, opcode/AA/TC/Z=0
        val flags3 = FLAG_RA or (rcode and RCODE_MASK)

        val hasAnswer = rcode == 0 && answerRr != null
        val buf = ByteArray(HEADER_LEN + (questionEnd - HEADER_LEN) + (if (hasAnswer) answerRr!!.size else 0))
        // Header
        buf[0] = query[0]; buf[1] = query[1]                       // ID
        buf[2] = flags2.toByte()
        buf[3] = flags3.toByte()
        buf[4] = query[4]; buf[5] = query[5]                       // QDCOUNT (unchanged)
        // QDCOUNT stays the same; ANCOUNT = 1 when answering else 0
        val ancountHi = if (hasAnswer) 0 else 0
        val ancountLo = if (hasAnswer) 1 else 0
        buf[6] = ancountHi.toByte(); buf[7] = ancountLo.toByte()
        buf[8] = 0; buf[9] = 0                                     // NSCOUNT
        buf[10] = 0; buf[11] = 0                                   // ARCOUNT (EDNS OPT stripped deliberately)
        // Question section
        System.arraycopy(query, HEADER_LEN, buf, HEADER_LEN, questionEnd - HEADER_LEN)
        if (hasAnswer) {
            System.arraycopy(answerRr!!, 0, buf, questionEnd, answerRr.size)
        }
        return buf
    }

    /**
     * Skips the first question record in [packet] and returns the offset just
     * past it, or null when malformed.
     */
    private fun skipQuestion(packet: ByteArray, len: Int): Int? {
        var pos = HEADER_LEN
        // QNAME: sequence of labels ending in a zero byte; pointers are not
        // allowed in queries but we tolerate them defensively.
        while (true) {
            if (pos >= len) return null
            val b = packet[pos].toInt() and 0xFF
            when {
                b == 0 -> { pos++; break }
                b and 0xC0 == 0xC0 -> { pos += 2; break }
                b and 0xC0 != 0 -> return null
                else -> pos += 1 + b
            }
        }
        // QTYPE + QCLASS
        pos += 4
        if (pos > len) return null
        return pos
    }
}
