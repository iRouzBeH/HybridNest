package dev.rouzbeh.hybridvpn.core.vpn.dns

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Measures real DNS round-trip to a server: sends a minimal A query for
 * a well-known domain and waits for any valid DNS response (RCODE doesn't
 * matter — we are measuring reachability, not correctness).
 *
 * Used for the per-server ping badge in the DNS server list AND for the
 * "Test Ping" box when connected in DNS mode.
 *
 * [protectUdp]/[protectTcp] are the VpnService.protect() functions; when
 * there is no tunnel (list testing before connect) pass a no-op `{ true }`
 * — the socket then goes out on the default network normally.
 *
 * Returns elapsed ms >= 0 on success, -1 on timeout/failure.
 */
object DnsPing {

    private const val QUERY_HOST = "www.google.com"
    private const val TIMEOUT_MS = 3_000

    /**
     * UDP ping first (what the forwarder uses). On TC=1 or UDP failure we
     * do NOT retry TCP here — ping is meant to measure the common path.
     */
    fun pingUdp(serverIp: String, protect: (DatagramSocket) -> Boolean = { true }): Long {
        val server = parseIp(serverIp) ?: return -1L
        val query = buildAQuery(QUERY_HOST) ?: return -1L
        val start = System.nanoTime()
        return try {
            DatagramSocket().use { sock ->
                if (!protect(sock)) return -1L
                sock.soTimeout = TIMEOUT_MS
                sock.send(DatagramPacket(query, query.size, server, 53))
                val buf = ByteArray(2048)
                val p = DatagramPacket(buf, buf.size)
                sock.receive(p)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                // Only need "a DNS response came back"; RCODE/answers don't matter.
                if (DnsPacket.isResponse(buf, p.length)) elapsed else -1L
            }
        } catch (t: Throwable) { -1L }
    }

    /** TCP variant used only when UDP is explicitly unavailable (rare). */
    fun pingTcp(serverIp: String, protect: (Socket) -> Boolean = { true }): Long {
        val server = parseIp(serverIp) ?: return -1L
        val query = buildAQuery(QUERY_HOST) ?: return -1L
        val start = System.nanoTime()
        return try {
            Socket().use { sock ->
                if (!protect(sock)) return -1L
                sock.soTimeout = TIMEOUT_MS
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
                if (DnsPacket.isResponse(buf, buf.size)) elapsed else -1L
            }
        } catch (t: Throwable) { -1L }
    }

    private fun parseIp(ip: String): InetAddress? = try {
        InetAddress.getByName(ip).takeIf { it is java.net.Inet4Address || it is java.net.Inet6Address }
    } catch (_: Throwable) { null }

    /** Minimal A query (no EDNS): header + QNAME labels + QTYPE/QCLASS. */
    private fun buildAQuery(host: String): ByteArray? {
        return try {
            val qname = host.split(".").flatMap { label ->
                listOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII).toList()
            }.toByteArray() + 0
            val buf = java.nio.ByteBuffer.allocate(12 + qname.size + 4)
            buf.putShort(0x1A2B)                    // ID (arbitrary, we only check isResponse)
            buf.putShort(0x0100)                    // RD=1
            buf.putShort(1); buf.putShort(0); buf.putShort(0); buf.putShort(0)
            buf.put(qname)
            buf.putShort(1); buf.putShort(1)        // QTYPE=A, QCLASS=IN
            buf.array()
        } catch (_: Throwable) { null }
    }
}
