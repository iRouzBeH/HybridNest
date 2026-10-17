package dev.rouzbeh.hybridvpn.core.vpn.dns

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Packet-level DNS forwarder for the "DNS" connection method.
 *
 * Traffic model: the TUN advertises a fake resolver address to the system
 * via VpnService.Builder.addDnsServer and routes ONLY that address (/32 for
 * IPv4, /128 for IPv6) into the tunnel. Everything else keeps using the
 * normal network — the user's IP does not change and no traffic is proxied.
 *
 * Android's DNS resolver (netd) sends the selected apps' lookups to the
 * fake resolver address. This class reads those IP packets off the TUN,
 * extracts the embedded DNS message, forwards it to the configured
 * upstream resolver through a protect()ed socket (bypassing the tunnel —
 * no routing loop), and writes the response back into the TUN.
 *
 * Per-query failure policy:
 *  - upstream UDP timeout (3 s) -> retry once on the same server, then
 *    fail over to the secondary server; if both fail the client receives a
 *    SERVFAIL so the OS can fall back gracefully.
 *  - upstream response with TC=1 (truncated) -> retry once over TCP on the
 *    same server (RFC 7766 behaviour).
 *  - malformed / non-DNS packets -> dropped and logged (rate-limited).
 *
 * Lifecycle: [start] spawns the reader; [stop] stops it, closes every
 * upstream socket and joins the threads. Idempotent.
 */
class DnsForwarder(
    private val tunFd: FileDescriptor,
    private val upstreamPrimary: InetAddress,
    private val upstreamSecondary: InetAddress?,
    private val mtu: Int,
    /** protect() from the VpnService; must return true for upstream sockets. */
    private val protectTcp: (Socket) -> Boolean,
    private val protectUdp: (DatagramSocket) -> Boolean,
    private val log: (String) -> Unit
) {
    companion object {
        const val DNS_PORT = 53
        private const val UDP_TIMEOUT_MS = 3_000
        private const val TCP_TIMEOUT_MS = 5_000
        private const val UDP_ATTEMPTS_PER_SERVER = 2
        private const val MAX_DNS_PACKET = 8_192
        private const val UDP_SESSION_IDLE_MS = 60_000L
        private const val TCP_SESSION_IDLE_MS = 30_000L
        private const val SWEEP_MS = 15_000L
    }

    private val running = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private var sweeperThread: Thread? = null

    /** One UDP query in flight upstream: keyed by (v6, clientIp, clientPort, qid). */
    private data class UdpCtx(
        val sock: DatagramSocket,
        val clientIp: ByteArray,
        val clientPort: Int,
        val isV6: Boolean,
        val clientQueryId: Int,
        @Volatile var upstreamId: Int,
        val clientQuery: ByteArray,
        @Volatile var upstream: InetAddress,
        @Volatile var attemptsLeft: Int,
        @Volatile var serverIndex: Int,
        @Volatile var lastActivity: Long
    )
    private data class SessionKey(val v6: Boolean, val addr: String, val port: Int, val qid: Int)
    private val udpSessions = ConcurrentHashMap<SessionKey, UdpCtx>()
    private val upstreamIdSeq = AtomicInteger()

    /** Per-connection TCP state so retransmits/handshake behave correctly. */
    private class TcpFlow {
        var clientSeqBase = -1L          // ISN of the client side
        var ourSeqBase = 0L              // ISN we picked for the server side
        var queryDone = false            // one DNS exchange per connection
        var buffer = ByteArray(0)        // accumulated client payload bytes
        var upstreamQuerying = false
        var lastActivity = System.currentTimeMillis()
    }
    private val tcpFlows = ConcurrentHashMap<String, TcpFlow>()

    fun start() {
        if (running.getAndSet(true)) return
        readerThread = Thread({ readerLoop() }, "dns-fwd-read").apply { isDaemon = true; start() }
        sweeperThread = Thread({ sweeperLoop() }, "dns-fwd-sweep").apply { isDaemon = true; start() }
        log("DNS forwarder started (primary=${upstreamPrimary.hostAddress}, secondary=${upstreamSecondary?.hostAddress ?: "none"})")
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        readerThread?.interrupt()
        sweeperThread?.interrupt()
        udpSessions.values.forEach { runCatching { it.sock.close() } }
        udpSessions.clear()
        tcpFlows.clear()
        try { readerThread?.join(2_000) } catch (_: InterruptedException) {}
        try { sweeperThread?.join(1_000) } catch (_: InterruptedException) {}
        log("DNS forwarder stopped")
    }

    // ------------------------------------------------------------------ read

    private fun readerLoop() {
        val input = FileInputStream(tunFd)
        val output = FileOutputStream(tunFd)
        val buf = ByteArray(mtu.coerceAtLeast(2048))
        while (running.get()) {
            val n = try { input.read(buf) } catch (e: Throwable) {
                if (running.get()) log("TUN read error: ${e.message}")
                break
            }
            if (n <= 0) continue
            try { handlePacket(buf.copyOf(n), output) }
            catch (t: Throwable) { log("packet error: ${t.message}") }
        }
    }

    private fun sweeperLoop() {
        while (running.get()) {
            try { Thread.sleep(SWEEP_MS) } catch (_: InterruptedException) { break }
            val now = System.currentTimeMillis()
            udpSessions.entries.removeIf { e ->
                if (now - e.value.lastActivity > UDP_SESSION_IDLE_MS) {
                    runCatching { e.value.sock.close() }; true
                } else false
            }
            tcpFlows.entries.removeIf { e ->
                now - e.value.lastActivity > TCP_SESSION_IDLE_MS
            }
        }
    }

    // ---------------------------------------------------------------- decode

    private fun handlePacket(ip: ByteArray, output: FileOutputStream) {
        if (ip.isEmpty()) return
        when (ip[0].toInt() ushr 4) {
            4 -> handleIpv4(ip, output)
            6 -> handleIpv6(ip, output)
            else -> log("drop: unknown IP version")
        }
    }

    private fun handleIpv4(ip: ByteArray, out: FileOutputStream) {
        if (ip.size < 20) return
        val ihl = (ip[0].toInt() and 0x0F) * 4
        if (ihl < 20 || ip.size < ihl) return
        val proto = ip[9].toInt() and 0xFF
        val src = ip.copyOfRange(12, 16)
        val dst = ip.copyOfRange(16, 20)
        val totalLen = ((ip[2].toInt() and 0xFF) shl 8) or (ip[3].toInt() and 0xFF)
        val payloadLen = (if (totalLen in ihl..ip.size) totalLen else ip.size) - ihl
        if (payloadLen <= 0) return
        val payload = ip.copyOfRange(ihl, ihl + payloadLen)
        when (proto) {
            17 -> handleUdp(src, dst, false, payload, out)
            6 -> handleTcp(src, dst, false, payload, out)
            else -> {}
        }
    }

    private fun handleIpv6(ip: ByteArray, out: FileOutputStream) {
        if (ip.size < 40) return
        val src = ip.copyOfRange(8, 24)
        val dst = ip.copyOfRange(24, 40)
        var hdr = ip[6].toInt() and 0xFF
        var off = 40
        var remaining = ip.size - 40
        // Walk extension headers we can skip: Hop-by-Hop(0), Routing(43),
        // Destination(60), Fragment(44). AH(51)/ESP(50) never carry plain DNS.
        var steps = 0
        while (steps++ < 8) {
            when (hdr) {
                0, 43, 60 -> {
                    if (remaining < 2) return
                    val extLen = ((ip[off + 1].toInt() and 0xFF) + 1) * 8
                    if (extLen <= 0 || extLen > remaining) return
                    hdr = ip[off].toInt() and 0xFF
                    off += extLen; remaining -= extLen
                }
                44 -> {
                    if (remaining < 8) return
                    hdr = ip[off].toInt() and 0xFF
                    off += 8; remaining -= 8
                }
                else -> break
            }
        }
        if (remaining <= 0) return
        val payload = ip.copyOfRange(off, off + remaining)
        when (hdr) {
            17 -> handleUdp(src, dst, true, payload, out)
            6 -> handleTcp(src, dst, true, payload, out)
            else -> {}
        }
    }

    // ------------------------------------------------------------------- UDP

    private fun handleUdp(srcIp: ByteArray, dstIp: ByteArray, isV6: Boolean,
                          udp: ByteArray, out: FileOutputStream) {
        if (udp.size < 8) return
        val srcPort = ((udp[0].toInt() and 0xFF) shl 8) or (udp[1].toInt() and 0xFF)
        val dstPort = ((udp[2].toInt() and 0xFF) shl 8) or (udp[3].toInt() and 0xFF)
        val udpLen = ((udp[4].toInt() and 0xFF) shl 8) or (udp[5].toInt() and 0xFF)
        if (udpLen < 8 || udpLen > udp.size) return
        val dns = udp.copyOfRange(8, udpLen)
        if (dstPort != DNS_PORT) return
        if (dns.size < DnsPacket.HEADER_LEN) return
        forwardUdpQuery(srcIp, srcPort, isV6, dns, out)
    }

    private fun forwardUdpQuery(clientIp: ByteArray, clientPort: Int, isV6: Boolean,
                                query: ByteArray, out: FileOutputStream) {
        val qid = DnsPacket.id(query, query.size)
        if (qid < 0) return
        val key = SessionKey(isV6, ipKey(clientIp), clientPort, qid)

        // Compute on first packet only; subsequent retries reuse the session.
        val ctx = udpSessions.compute(key) { _, old ->
            old ?: run {
                val sock = try {
                    DatagramSocket().also { s ->
                        s.soTimeout = UDP_TIMEOUT_MS
                        if (!protectUdp(s)) {
                            runCatching { s.close() }
                            log("protect() refused UDP socket")
                            return@run null
                        }
                    }
                } catch (t: Throwable) { log("UDP socket: ${t.message}"); null } ?: return@run null

                UdpCtx(
                    sock = sock, clientIp = clientIp, clientPort = clientPort,
                    isV6 = isV6, clientQueryId = qid,
                    upstreamId = nextUpstreamId(),
                    clientQuery = query,
                    upstream = upstreamPrimary,
                    attemptsLeft = UDP_ATTEMPTS_PER_SERVER,
                    serverIndex = 0,
                    lastActivity = System.currentTimeMillis()
                )
            }
        } ?: run {
            sendServfail(clientIp, isV6, query, clientPort, out); return
        }

        ctx.lastActivity = System.currentTimeMillis()
        val upstreamQuery = query.copyOf()
        DnsPacket.setId(upstreamQuery, ctx.upstreamId)
        try {
            ctx.sock.send(DatagramPacket(upstreamQuery, upstreamQuery.size, ctx.upstream, DNS_PORT))
        } catch (t: Throwable) {
            log("UDP send: ${t.message}")
            udpSessions.remove(key); runCatching { ctx.sock.close() }
            sendServfail(clientIp, isV6, query, clientPort, out)
            return
        }

        // Dedicated receiver: matches transaction id, retries timeouts,
        // fails over primary->secondary, upgrades to TCP on TC=1.
        Thread({ udpReceiveLoop(key, ctx, out) }, "dns-udp-${clientPort}-$qid")
            .apply { isDaemon = true; start() }
    }

    private fun udpReceiveLoop(key: SessionKey, ctx: UdpCtx, out: FileOutputStream) {
        val buf = ByteArray(MAX_DNS_PACKET)
        try {
            while (running.get() && udpSessions.containsKey(key)) {
                val resp: ByteArray
                try {
                    val p = DatagramPacket(buf, buf.size)
                    ctx.sock.receive(p)
                    resp = buf.copyOf(p.length)
                } catch (e: SocketTimeoutException) {
                    if (!retryOrFailoverUdp(key, ctx, out)) return
                    continue
                } catch (t: Throwable) {
                    if (running.get()) log("UDP recv: ${t.message}")
                    return
                }
                ctx.lastActivity = System.currentTimeMillis()
                if (DnsPacket.id(resp, resp.size) != ctx.upstreamId) continue
                if (DnsPacket.isTruncated(resp, resp.size)) {
                    val tcpResp = queryTcp(ctx.upstream, ctx.clientQuery)
                    if (tcpResp != null) { writeUdpResponse(ctx, tcpResp, out); return }
                    // TCP upgrade failed -> treat as a timeout (retry/failover).
                    if (!retryOrFailoverUdp(key, ctx, out)) return
                    continue
                }
                writeUdpResponse(ctx, resp, out); return
            }
        } finally {
            udpSessions.remove(key)
            runCatching { ctx.sock.close() }
        }
    }

    /**
     * Retransmits on the current server, or switches to the secondary and
     * retransmits there. Returns false when no more attempts remain (a
     * SERVFAIL has already been sent to the client).
     */
    private fun retryOrFailoverUdp(key: SessionKey, ctx: UdpCtx, out: FileOutputStream): Boolean {
        if (--ctx.attemptsLeft <= 0) {
            val targets = listOfNotNull(upstreamPrimary, upstreamSecondary)
            ctx.serverIndex++
            if (ctx.serverIndex >= targets.size) {
                sendServfail(ctx.clientIp, ctx.isV6, ctx.clientQuery, ctx.clientPort, out)
                return false
            }
            ctx.upstream = targets[ctx.serverIndex]
            ctx.attemptsLeft = UDP_ATTEMPTS_PER_SERVER
            ctx.upstreamId = nextUpstreamId()
        }
        val q = ctx.clientQuery.copyOf(); DnsPacket.setId(q, ctx.upstreamId)
        return try {
            ctx.sock.send(DatagramPacket(q, q.size, ctx.upstream, DNS_PORT)); true
        } catch (t: Throwable) {
            sendServfail(ctx.clientIp, ctx.isV6, ctx.clientQuery, ctx.clientPort, out); false
        }
    }

    // ------------------------------------------------------------------- TCP

    /**
     * Minimal TCP endpoint for DNS-over-TCP inside the TUN.
     *
     * netd opens a fresh connection per query: SYN, our SYN+ACK, ACK,
     * PSH+{len,query}, we ACK, forward upstream, reply {len,resp}+FIN.
     * Per-connection state in [tcpFlows] handles retransmission correctly:
     * duplicate SYNs/PSHs re-ack instead of re-forwarding.
     */
    private fun handleTcp(srcIp: ByteArray, dstIp: ByteArray, isV6: Boolean,
                          seg: ByteArray, out: FileOutputStream) {
        if (seg.size < 20) return
        val srcPort = ((seg[0].toInt() and 0xFF) shl 8) or (seg[1].toInt() and 0xFF)
        val dstPort = ((seg[2].toInt() and 0xFF) shl 8) or (seg[3].toInt() and 0xFF)
        if (dstPort != DNS_PORT) return
        val dataOff = ((seg[12].toInt() and 0xF0) ushr 4) * 4
        if (dataOff < 20 || seg.size < dataOff) return
        val flags = seg[13].toInt() and 0x3F
        val seq = u32(seg, 4)
        val ack = u32(seg, 8)
        val payload = seg.copyOfRange(dataOff, seg.size)

        val flowKey = (if (isV6) "6" else "4") + ":" + ipKey(srcIp) + ":" + srcPort
        val flow = tcpFlows.getOrPut(flowKey) { TcpFlow() }
        flow.lastActivity = System.currentTimeMillis()

        val syn = flags and 0x02 != 0
        val fin = flags and 0x01 != 0
        val rst = flags and 0x04 != 0
        val psh = flags and 0x08 != 0
        val ackf = flags and 0x10 != 0

        if (rst) { tcpFlows.remove(flowKey); return }

        if (syn) {
            // (re)initialize on a fresh SYN. Duplicate SYN retransmits get a
            // repeat SYN+ACK — harmless.
            flow.clientSeqBase = seq
            flow.ourSeqBase = seq xor 0x5D3A11E1L // arbitrary ISN
            flow.queryDone = false; flow.buffer = ByteArray(0); flow.upstreamQuerying = false
            sendTcp(srcIp, isV6, srcPort,
                seq = flow.ourSeqBase, ack = seq + 1, flags = 0x12, payload = ByteArray(0), out = out)
            return
        }

        if (flow.clientSeqBase < 0) return // data before SYN: drop

        val consumed = (seq - flow.clientSeqBase - 1).toInt() // bytes into the stream, after SYN
        if (consumed < 0) return

        if (payload.isNotEmpty()) {
            val need = consumed + payload.size
            if (need > flow.buffer.size) {
                // Fast path: fresh data appended at the tail; retransmissions
                // overlap and are trimmed by position.
                val newBuf = flow.buffer.copyOf(need)
                System.arraycopy(payload, 0, newBuf, consumed, payload.size)
                flow.buffer = newBuf
            }
            // ACK current tail position.
            sendTcp(srcIp, isV6, srcPort,
                seq = flow.ourSeqBase + 1, ack = flow.clientSeqBase + 1 + flow.buffer.size,
                flags = 0x10, payload = ByteArray(0), out = out)
        }

        if (!flow.queryDone && !flow.upstreamQuerying && flow.buffer.size >= 2) {
            val dnsLen = ((flow.buffer[0].toInt() and 0xFF) shl 8) or (flow.buffer[1].toInt() and 0xFF)
            if (dnsLen in 1..(MAX_DNS_PACKET - 2) && flow.buffer.size >= 2 + dnsLen) {
                flow.upstreamQuerying = true
                flow.queryDone = true
                val query = flow.buffer.copyOfRange(2, 2 + dnsLen)
                // Run the upstream exchange off the TUN reader thread so a slow
                // resolver doesn't stall other queries.
                Thread({
                    val resp = tryUpstreamTcpThenUdp(query)
                        ?: DnsPacket.buildResponse(query, query.size, rcode = 2)
                    if (resp != null && running.get()) {
                        val framed = ByteArray(2 + resp.size)
                        framed[0] = (resp.size ushr 8).toByte(); framed[1] = resp.size.toByte()
                        System.arraycopy(resp, 0, framed, 2, resp.size)
                        sendTcp(srcIp, isV6, srcPort,
                            seq = flow.ourSeqBase + 1,
                            ack = flow.clientSeqBase + 1 + flow.buffer.size,
                            flags = 0x18, payload = framed, out = out)
                        sendTcp(srcIp, isV6, srcPort,
                            seq = flow.ourSeqBase + 1 + framed.size,
                            ack = flow.clientSeqBase + 1 + flow.buffer.size,
                            flags = 0x11, payload = ByteArray(0), out = out)
                    }
                    tcpFlows.remove(flowKey)
                }, "dns-tcp-$srcPort").apply { isDaemon = true; start() }
            }
        }

        if (fin) {
            sendTcp(srcIp, isV6, srcPort,
                seq = flow.ourSeqBase + 1, ack = seq + 1, flags = 0x11, payload = ByteArray(0), out = out)
            tcpFlows.remove(flowKey)
        }
    }

    /** TCP-first upstream exchange with primary->secondary failover. */
    private fun tryUpstreamTcpThenUdp(query: ByteArray): ByteArray? {
        for (server in listOfNotNull(upstreamPrimary, upstreamSecondary)) {
            queryTcp(server, query)?.let { return it }
            repeat(UDP_ATTEMPTS_PER_SERVER) {
                tryUdpOnce(server, query)?.let { return it }
            }
        }
        return null
    }

    private fun queryTcp(server: InetAddress, query: ByteArray): ByteArray? {
        return try {
            Socket().use { sock ->
                if (!protectTcp(sock)) { log("protect() refused TCP socket"); return null }
                sock.soTimeout = TCP_TIMEOUT_MS
                sock.connect(InetSocketAddress(server, DNS_PORT), TCP_TIMEOUT_MS)
                val dos = DataOutputStream(sock.getOutputStream())
                val dis = DataInputStream(sock.getInputStream())
                dos.writeShort(query.size); dos.write(query); dos.flush()
                val len = dis.readShort().toInt() and 0xFFFF
                if (len <= 0 || len > MAX_DNS_PACKET) return null
                val buf = ByteArray(len); dis.readFully(buf)
                buf
            }
        } catch (t: Throwable) { null }
    }

    private fun tryUdpOnce(server: InetAddress, query: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { sock ->
                if (!protectUdp(sock)) return null
                sock.soTimeout = UDP_TIMEOUT_MS
                val q = query.copyOf()
                val id = nextUpstreamId(); DnsPacket.setId(q, id)
                sock.send(DatagramPacket(q, q.size, server, DNS_PORT))
                val buf = ByteArray(MAX_DNS_PACKET)
                val p = DatagramPacket(buf, buf.size)
                sock.receive(p)
                val resp = buf.copyOf(p.length)
                if (DnsPacket.id(resp, resp.size) != id) null
                else if (DnsPacket.isTruncated(resp, resp.size)) queryTcp(server, query)
                else resp
            }
        } catch (t: Throwable) { null }
    }

    // --------------------------------------------------------------- writers

    private fun writeUdpResponse(ctx: UdpCtx, upstreamResp: ByteArray, out: FileOutputStream) {
        val resp = upstreamResp.copyOf()
        DnsPacket.setId(resp, ctx.clientQueryId)
        writeIp(ctx.clientIp, ctx.isV6, 17, buildUdpDatagram(DNS_PORT, ctx.clientPort, resp, ctx.clientIp, ctx.isV6), out)
    }

    private fun sendServfail(clientIp: ByteArray, isV6: Boolean, query: ByteArray, clientPort: Int, out: FileOutputStream) {
        val resp = DnsPacket.buildResponse(query, query.size, rcode = 2) ?: return
        writeIp(clientIp, isV6, 17, buildUdpDatagram(DNS_PORT, clientPort, resp, clientIp, isV6), out)
    }

    /** Builds a UDP datagram src=DNS_PORT -> dst=clientPort carrying [dns]. */
    private fun buildUdpDatagram(srcPort: Int, dstPort: Int, dns: ByteArray,
                                 clientIp: ByteArray, isV6: Boolean): ByteArray {
        val len = 8 + dns.size
        val b = ByteBuffer.allocate(len)
        b.putShort(srcPort.toShort()); b.putShort(dstPort.toShort())
        b.putShort(len.toShort()); b.putShort(0)
        b.put(dns)
        val arr = b.array()
        // UDP checksum is optional for IPv4 but REQUIRED for IPv6.
        if (isV6) {
            val csum = udpChecksum6(fakeV6Bytes(), clientIp, arr)
            arr[6] = (csum ushr 8).toByte(); arr[7] = csum.toByte()
        } else {
            val csum = udpChecksum4(FAKE_V4_BYTES, clientIp, arr)
            arr[6] = (csum ushr 8).toByte(); arr[7] = csum.toByte()
        }
        return arr
    }

    private fun writeIp(dstIp: ByteArray, isV6: Boolean, proto: Int, payload: ByteArray, out: FileOutputStream) {
        val pkt = if (isV6) buildIpv6(dstIp, proto, payload) else buildIpv4(dstIp, proto, payload) ?: return
        synchronized(out) { try { out.write(pkt) } catch (_: Throwable) {} }
    }

    private fun buildIpv4(dstIp: ByteArray, proto: Int, payload: ByteArray): ByteArray? {
        if (dstIp.size != 4) return null
        val b = ByteBuffer.allocate(20 + payload.size)
        b.put(0x45.toByte()); b.put(0)
        b.putShort((20 + payload.size).toShort())
        b.putShort(0); b.putShort(0x4000.toShort())
        b.put(64); b.put(proto.toByte()); b.putShort(0)
        b.put(FAKE_V4_BYTES); b.put(dstIp); b.put(payload)
        val p = b.array()
        val c = checksum(p.copyOf(20))
        p[10] = (c ushr 8).toByte(); p[11] = c.toByte()
        return p
    }

    private fun buildIpv6(dstIp: ByteArray, proto: Int, payload: ByteArray): ByteArray? {
        if (dstIp.size != 16) return null
        val b = ByteBuffer.allocate(40 + payload.size)
        b.put(0x60.toByte()); b.put(0); b.put(0); b.put(0)
        b.putShort(payload.size.toShort()); b.put(proto.toByte()); b.put(64)
        b.put(fakeV6Bytes()); b.put(dstIp); b.put(payload)
        val p = b.array()
        if (proto == 6) { // TCP checksum is mandatory
            val c = tcpChecksum6(fakeV6Bytes(), dstIp, payload)
            p[40 + 16] = (c ushr 8).toByte(); p[40 + 17] = c.toByte()
        }
        return p
    }

    private fun sendTcp(dstIp: ByteArray, isV6: Boolean, dstPort: Int,
                        seq: Long, ack: Long, flags: Int, payload: ByteArray,
                        out: FileOutputStream) {
        val tcp = ByteBuffer.allocate(20 + payload.size)
        tcp.putShort(DNS_PORT.toShort()); tcp.putShort(dstPort.toShort())
        tcp.putInt(seq.toInt()); tcp.putInt(ack.toInt())
        tcp.put((5 shl 4).toByte()); tcp.put(flags.toByte())
        tcp.putShort(32767.toShort()); tcp.putShort(0); tcp.putShort(0)
        tcp.put(payload)
        writeIp(dstIp, isV6, 6, tcp.array(), out)
    }

    // -------------------------------------------------------------- checksums

    private fun udpChecksum4(src: ByteArray, dst: ByteArray, udp: ByteArray): Int {
        val pseudo = ByteBuffer.allocate(12 + udp.size)
        pseudo.put(src); pseudo.put(dst)
        pseudo.put(0); pseudo.put(17); pseudo.putShort(udp.size.toShort())
        pseudo.put(udp)
        return checksum(pseudo.array())
    }
    private fun udpChecksum6(src: ByteArray, dst: ByteArray, udp: ByteArray): Int {
        val pseudo = ByteBuffer.allocate(40 + udp.size)
        pseudo.put(src); pseudo.put(dst)
        pseudo.putInt(udp.size); pseudo.put(byteArrayOf(0, 0, 0, 17)); pseudo.put(udp)
        return checksum(pseudo.array())
    }
    private fun tcpChecksum6(src: ByteArray, dst: ByteArray, tcp: ByteArray): Int {
        val pseudo = ByteBuffer.allocate(40 + tcp.size)
        pseudo.put(src); pseudo.put(dst)
        pseudo.putInt(tcp.size); pseudo.put(byteArrayOf(0, 0, 0, 6)); pseudo.put(tcp)
        return checksum(pseudo.array())
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0L; var i = 0
        while (i + 1 < data.size) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF); i += 2
        }
        if (i < data.size) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        val r = sum.inv() and 0xFFFF
        return if (r == 0L) 0xFFFF else r.toInt()
    }

    // ---------------------------------------------------------------- helpers

    private fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
        ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    private fun ipKey(b: ByteArray) = b.joinToString(".") { (it.toInt() and 0xFF).toString() }
    private fun nextUpstreamId() = upstreamIdSeq.incrementAndGet() and 0xFFFF

    private val FAKE_V4_BYTES = byteArrayOf(10, 10, 10, 53)
    private fun fakeV6Bytes(): ByteArray = FAKE_V6_BYTES
    private val FAKE_V6_BYTES: ByteArray = try {
        (InetAddress.getByName(DnsTunnelConfig.FAKE_DNS_V6) as Inet6Address).address
    } catch (_: Throwable) { ByteArray(16) }
}

/** Fake addresses used by the DNS-only TUN session. */
object DnsTunnelConfig {
    const val TUN_ADDR_V4 = "10.10.10.1"
    const val FAKE_DNS_V4 = "10.10.10.53"
    const val TUN_ADDR_V6 = "fd00:646e:7373::1"
    const val FAKE_DNS_V6 = "fd00:646e:7373::53"
}
