package dev.rouzbeh.hybridvpn.core.vpn.dns

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ThreadLocalRandom

/**
 * Measures real DNS round-trip to a server with a minimal A query.
 *
 * Reliability rules (why a single UDP sample used to read too low):
 *  - Each measurement sends a fresh query with a unique 16-bit ID and the
 *    response is required to echo that ID AND carry RCODE = NOERROR.
 *    Previously any packet with the QR bit set (a stray answer, a SERVFAIL,
 *    or a refused/edge-case reply that returns instantly) counted as a hit.
 *  - A ping is the MEDIAN of [SAMPLES] sequential queries, not one sample,
 *    so a single best-case datagram can no longer dominate the number.
 *    One failed/lost probe is tolerated (dropped before the median).
 *  - Every probe uses its own protect()ed socket — nothing is reused, so no
 *    kernel-level or socket-level caching can carry a previous answer over.
 *
 * The timer starts just before send() and stops just after a *validated*
 * response is received, so it measures pure server round-trip. protect() and
 * socket setup happen before the clock starts.
 *
 * Used for the per-server ping badge in the DNS server list AND for the
 * "Test Ping" box when connected in DNS mode. [protectUdp]/[protectTcp] are
 * the VpnService.protect() functions; with no tunnel (list test before
 * connect) pass a no-op `{ true }` so the socket goes out on the default
 * network normally.
 *
 * Returns elapsed ms >= 0 on success, -1 on timeout/failure.
 */
object DnsPing {

    private const val QUERY_HOST = "www.google.com"
    private const val TIMEOUT_MS = 2_500
    private const val SAMPLES = 3

    /**
     * Median-of-[SAMPLES] UDP ping. Each sample is a fresh protect()ed socket
     * with a unique transaction ID; only ID-matching NOERROR responses count.
     * Runs the samples sequentially so a transient loss shows up as a slower
     * median instead of being hidden by a single fast reply. Returns -1 only
     * when every sample fails.
     */
    fun pingUdp(serverIp: String, protect: (DatagramSocket) -> Boolean = { true }): Long {
        val server = parseIp(serverIp) ?: return -1L
        val rtts = ArrayList<Long>(SAMPLES)
        repeat(SAMPLES) {
            val rtt = singleUdpQuery(server, protect)
            if (rtt >= 0) rtts.add(rtt)
        }
        return medianOrMinusOne(rtts)
    }

    /**
     * Median-of-[SAMPLES] TCP ping — used only when UDP is explicitly
     * unavailable (rare). Same validation and median rules as [pingUdp].
     */
    fun pingTcp(serverIp: String, protect: (Socket) -> Boolean = { true }): Long {
        val server = parseIp(serverIp) ?: return -1L
        val rtts = ArrayList<Long>(SAMPLES)
        repeat(SAMPLES) {
            val rtt = singleTcpQuery(server, protect)
            if (rtt >= 0) rtts.add(rtt)
        }
        return medianOrMinusOne(rtts)
    }

    /**
     * One validated UDP query: fresh socket, unique ID, waits for a response
     * that echoes the ID and is RCODE NOERROR. Returns ms or -1.
     */
    private fun singleUdpQuery(server: InetAddress, protect: (DatagramSocket) -> Boolean): Long {
        val id = randomId()
        val query = buildAQuery(QUERY_HOST, id) ?: return -1L
        return try {
            DatagramSocket().use { sock ->
                if (!protect(sock)) return -1L
                sock.soTimeout = TIMEOUT_MS
                val start = System.nanoTime()
                sock.send(DatagramPacket(query, query.size, server, 53))
                val buf = ByteArray(2048)
                val p = DatagramPacket(buf, buf.size)
                sock.receive(p)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                if (isValidAnswer(buf, p.length, id)) elapsed else -1L
            }
        } catch (t: Throwable) { -1L }
    }

    /**
     * One validated TCP query: fresh socket, unique ID, same ID/NOERROR rule.
     * TCP carries a 2-byte length prefix; the timer spans connect+query+reply.
     */
    private fun singleTcpQuery(server: InetAddress, protect: (Socket) -> Boolean): Long {
        val id = randomId()
        val query = buildAQuery(QUERY_HOST, id) ?: return -1L
        return try {
            Socket().use { sock ->
                if (!protect(sock)) return -1L
                sock.soTimeout = TIMEOUT_MS
                val start = System.nanoTime()
                sock.connect(InetSocketAddress(server, 53), TIMEOUT_MS)
                val out = sock.getOutputStream()
                out.write(query.size ushr 8); out.write(query.size); out.write(query); out.flush()
                val input = sock.getInputStream()
                val l1 = input.read(); val l2 = input.read()
                if (l1 < 0 || l2 < 0) return -1L
                val len = (l1 shl 8) or l2
                var got = 0; val buf = ByteArray(len)
                while (got < len) {
                    val r = input.read(buf, got, len - got); if (r < 0) return -1L; got += r
                }
                val elapsed = (System.nanoTime() - start) / 1_000_000
                if (isValidAnswer(buf, buf.size, id)) elapsed else -1L
            }
        } catch (t: Throwable) { -1L }
    }

    /**
     * True when [packet] is a well-formed response that (a) is a response,
     * (b) echoes [expectedId], and (c) carries RCODE NOERROR. This rejects
     * stray/spoofed packets and instant SERVFAIL/REFUSED replies that would
     * otherwise read as a "0 ms" server.
     */
    private fun isValidAnswer(packet: ByteArray, len: Int, expectedId: Int): Boolean {
        if (!DnsPacket.isResponse(packet, len)) return false
        if (DnsPacket.id(packet, len) != expectedId) return false
        return rcode(packet, len) == 0
    }

    /** RCODE = low nibble of header byte 3; -1 when the packet is too short. */
    private fun rcode(packet: ByteArray, len: Int): Int {
        if (len < DnsPacket.HEADER_LEN) return -1
        return packet[3].toInt() and 0x0F
    }

    private fun medianOrMinusOne(values: List<Long>): Long {
        if (values.isEmpty()) return -1L
        val s = values.sorted()
        return s[s.size / 2]
    }

    private fun randomId(): Int = ThreadLocalRandom.current().nextInt(0x10000)

    private fun parseIp(ip: String): InetAddress? = try {
        InetAddress.getByName(ip).takeIf { it is java.net.Inet4Address || it is java.net.Inet6Address }
    } catch (_: Throwable) { null }

    /** Minimal A query (no EDNS): header + QNAME labels + QTYPE/QCLASS. */
    private fun buildAQuery(host: String, id: Int): ByteArray? {
        return try {
            val qname = host.split(".").flatMap { label ->
                listOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII).toList()
            }.toByteArray() + 0
            val buf = java.nio.ByteBuffer.allocate(12 + qname.size + 4)
            buf.putShort(id.toShort())               // unique transaction ID, validated on reply
            buf.putShort(0x0100)                    // RD=1
            buf.putShort(1); buf.putShort(0); buf.putShort(0); buf.putShort(0)
            buf.put(qname)
            buf.putShort(1); buf.putShort(1)        // QTYPE=A, QCLASS=IN
            buf.array()
        } catch (_: Throwable) { null }
    }
}
