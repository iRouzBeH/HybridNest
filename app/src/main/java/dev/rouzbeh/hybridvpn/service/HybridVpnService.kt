package dev.rouzbeh.hybridvpn.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.rouzbeh.hybridvpn.core.routing.RoutingRefusedException
import dev.rouzbeh.hybridvpn.core.utils.LogRedactor
import dev.rouzbeh.hybridvpn.core.vpn.ConnectivityDiagnostics
import dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2RayManager
import dev.rouzbeh.hybridvpn.core.vpn.v2ray.XrayConfigGenerator
import dev.rouzbeh.hybridvpn.data.local.AppDatabase
import dev.rouzbeh.hybridvpn.data.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import libv2ray.CoreCallbackHandler
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer

class HybridVpnService : VpnService() {

    companion object {
        const val ACTION_START_VPN = "ACTION_START_VPN"
        const val EXTRA_PROTOCOL = "EXTRA_PROTOCOL"
        const val NOTIFICATION_ID = 0x5650

        @Volatile var nativeLibLoaded = false; private set
        init {
            nativeLibLoaded = try {
                System.loadLibrary("hybridvpn")
                true
            } catch (e: Throwable) {
                android.util.Log.e("HybridVpnService", "libhybridvpn not loaded: ${e.message}")
                false
            }
        }
    }

    private val TAG = "HybridVpnService"
    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreTunFd: java.io.FileDescriptor? = null // ADD THIS LINE
    private var tunFdClaimedByCore = false
    private lateinit var settingsRepository: SettingsRepository
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(
        Dispatchers.IO + serviceJob +
        CoroutineExceptionHandler { _, t ->
            android.util.Log.e(TAG, "Unhandled service coroutine failure", t)
            emitLog("FATAL (caught): ${t::class.java.simpleName}: ${t.message}")
            VpnStateManager.updateStateFromNative(3)
            try { stopAllEngines(reason = "unhandled coroutine failure", intentional = false) } catch (_: Throwable) {}
            stopSelf()
        }
    )
    private val stopScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() +
        CoroutineExceptionHandler { _, t ->
            Log.e(TAG, "Unhandled stopScope failure", t)
        }
    )
    private var notificationJob: Job? = null
    private var connectionJob: Job? = null
    private var checkJob: Job? = null
    private val CHANNEL_ID = "vpn_notification_channel"
    private var currentConnectedProtocol: VpnProtocol? = null

    /** DNS-mode session: the forwarder thread that relays TUN DNS packets. */
    private var dnsForwarder: dev.rouzbeh.hybridvpn.core.vpn.dns.DnsForwarder? = null

    /**
     * In-progress stop for the CURRENT session. Combined with [stoppingSessionId]
     * this makes [stopAllEngines] idempotent per session: duplicate stop requests
     * (service intent + observer failure + onRevoke/onDestroy) reuse the SAME
     * teardown instead of racing each other with duplicate native cancels.
     */
    private val stopLock = Any()
    private var stoppingSessionId: Long? = null
    /**
     * Handle to the in-flight async teardown launched by [stopAllEngines]. A new
     * session join()s this before starting so the prior session's engine stop
     * (which clears the shared V2Ray running flag / native OpenConnect state)
     * always completes before a new core is started. Without this, a late
     * teardown could leave V2RayManager.running==true, making the next
     * startCore() silently skip startLoop -> TUN up but nothing forwarded.
     */
    private var stopJob: Job? = null

    /** Diagnostic/failure reason preserved from the stop trigger through teardown. */
    @Volatile
    private var stopReason: String = "unknown"

    /** Immutable routing-policy snapshot for the CURRENT connection session. */
    @Volatile
    private var sessionRoutingPolicy: dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy? = null
    /** Session identifier; used to ignore callbacks from superseded sessions (Stage 2 foundation). */
    @Volatile
    private var sessionId: Long = 0L

    private data class PreparedXrayConfig(
        val json: String,
        val serverIp: InetAddress,
        val serverPort: Int,
        val originalHost: String
    )

    private fun android.net.VpnService.Builder.addIpv4DefaultRouteExcept(excludedAddress: Inet4Address) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addRoute("0.0.0.0", 0)
            excludeRoute(IpPrefix(excludedAddress, 32))
            return
        }
        val bytes = excludedAddress.address
        require(bytes.size == 4)
        val target = ByteBuffer.wrap(bytes).int
        for (prefixLength in 1..32) {
            val changedBit = 1 shl (32 - prefixLength)
            val sibling = target xor changedBit
            val mask = -1 shl (32 - prefixLength)
            val siblingNetwork = sibling and mask
            val networkBytes = ByteBuffer.allocate(4).putInt(siblingNetwork).array()
            val networkAddress = InetAddress.getByAddress(networkBytes).hostAddress ?: error("Failed to construct complementary route")
            addRoute(networkAddress, prefixLength)
        }
    }

    private suspend fun preparePinnedXrayConfig(
        activeProfile: dev.rouzbeh.hybridvpn.data.local.VpnProfile
    ): PreparedXrayConfig = withContext(Dispatchers.IO) {
        val root = JSONObject(dev.rouzbeh.hybridvpn.core.vpn.v2ray.XrayConfigGenerator.generateConfig(activeProfile))
        val outbounds = root.getJSONArray("outbounds")
        val proxyOutbound = (0 until outbounds.length())
            .map { outbounds.getJSONObject(it) }
            .firstOrNull { it.optString("tag") == "proxy" }
            ?: error("Generated configuration has no proxy outbound")

        val protocol = proxyOutbound.getString("protocol")
        val settings = proxyOutbound.getJSONObject("settings")

        val endpoint: JSONObject = when (protocol) {
            "vless", "vmess" -> settings.getJSONArray("vnext").getJSONObject(0)
            "trojan" -> settings.getJSONArray("servers").getJSONObject(0)
            else -> error("Unsupported outbound protocol: ${protocol}")
        }

        val originalHost = endpoint.getString("address")
        val serverPort = endpoint.getInt("port")
        val resolvedAddresses = InetAddress.getAllByName(originalHost)
        val selectedAddress = resolvedAddresses.filterIsInstance<Inet4Address>().firstOrNull()
            ?: error("The server resolved only to IPv6, but the current TUN supports only IPv4")
        val selectedIp = selectedAddress.hostAddress ?: error("Resolved server address has no textual IP")
        
        endpoint.put("address", selectedIp)
        emitLog("Pinned $protocol endpoint: $originalHost -> $selectedIp:$serverPort")

        PreparedXrayConfig(
            json = root.toString(2),
            serverIp = selectedAddress,
            serverPort = serverPort,
            originalHost = originalHost
        )
    }

    private suspend fun testDirectTcpConnection(address: InetAddress, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), 5_000)
            }
            emitLog("Direct TCP preflight succeeded: ${address.hostAddress}:$port")
            true
        } catch (t: Throwable) {
            emitLog("Direct TCP preflight failed for ${address.hostAddress}:$port: ${t.message}")
            false
        }
    }

    private fun getFlagEmoji(countryCode: String?): String {
        if (countryCode.isNullOrBlank() || countryCode.length != 2 || countryCode == "un") return "🌍"
        val first = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    private fun formatVolume(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
            java.util.Locale.US,
            "%.1f %s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    private fun emitLog(msg: String) {
        val proto = currentConnectedProtocol?.title ?: "OpenConnect"
        // Producer-side redaction: secrets (Set-Cookie/Cookie/Authorization/URL
        // credentials) must never reach logcat or the UI flow. See LogRedactor.
        val safe = LogRedactor.redact(msg)
        VpnStateManager.vpnLogs.tryEmit(Pair(proto, safe))
        Log.d(TAG, safe)
    }

    fun protectSocket(fd: Int) {
        val success = protect(fd)
        Log.d(TAG, "Protecting socket $fd: $success")
    }

    private fun createNotification(title: String, content: String): android.app.Notification {
        val stopIntent = android.content.Intent(this, HybridVpnService::class.java).apply {
            action = "ACTION_STOP_VPN"
        }
        val pendingDisconnect = android.app.PendingIntent.getService(
            this,
            0,
            stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingLaunch = launchIntent?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(dev.rouzbeh.hybridvpn.R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingLaunch)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", pendingDisconnect)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private val v2rayCallbacks = object : CoreCallbackHandler {
        override fun startup(): Long { emitLog("XRAY: startup()"); return 0L }
        override fun shutdown(): Long { emitLog("XRAY: shutdown()"); return 0L }
        override fun onEmitStatus(p0: Long, status: String?): Long {
            val msg = status ?: return 0L
            if (!msg.contains("RpcSession") && !msg.contains("IpcRuntime")) emitLog("XRAY: $msg")
            if (msg.startsWith("CORE ERROR") || msg.startsWith("START FAILED")) {
                emitLog("FATAL Xray status: $msg")
                VpnStateManager.updateStateFromNative(3)
                try { stopAllEngines(reason = "Xray core error: $msg", intentional = false) } catch (_: Throwable) {}
                stopSelf()
            }
            return 0L
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        settingsRepository = SettingsRepository(this)
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("InlinedApi")
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startForegroundCompat failed", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = when (intent?.action) {
            "STOP_SERVICE", "ACTION_STOP_VPN", "ACTION_FORCE_STOP" -> createNotification("HybridNest", "Disconnecting...")
            else -> createNotification("HybridNest", "Connecting...")
        }
        startForegroundCompat(initialNotification)

        if (intent == null) {
            val state = VpnStateManager.connectionState.value
            val isOcOrV2ray = currentConnectedProtocol == VpnProtocol.V2rayNG || currentConnectedProtocol == VpnProtocol.OpenConnect
            if (state != ConnectionState.CONNECTED || !isOcOrV2ray) {
                emitLog("Restarted with null intent but state is $state. Stopping.")
                stopAllEngines(reason = "null intent restart", intentional = true, startId = startId)
                return START_NOT_STICKY
            }
        }

        when (intent?.action) {
            "STOP_SERVICE", "ACTION_STOP_VPN", "ACTION_FORCE_STOP" -> {
                synchronized(stopLock) {
                    if (stoppingSessionId != null && stoppingSessionId != 0L) {
                        emitLog("Stop action received (${intent.action}) while teardown of session $stoppingSessionId already in progress - ignoring duplicate.")
                        return START_NOT_STICKY
                    }
                }
                emitLog("Stop action received (${intent.action}).")
                stopAllEngines(reason = "requested via ${intent.action}", intentional = true, startId = startId)
                return START_NOT_STICKY
            }
            "ACTION_REQUEST_STATE_SYNC" -> {
                replyToStateSyncRequest()
                return START_NOT_STICKY
            }
            ACTION_START_VPN -> {
                currentConnectedProtocol = when (intent.getStringExtra(EXTRA_PROTOCOL)) {
                    VpnProtocol.V2rayNG.title -> VpnProtocol.V2rayNG
                    VpnProtocol.Dns.title -> VpnProtocol.Dns
                    else -> VpnProtocol.OpenConnect
                }
            }
        }

        emitLog("VpnService started.")
        VpnStateManager.updateStateFromNative(1)
        
        startNotificationLoop()

        connectionJob?.cancel()
        connectionJob = serviceScope.launch {
            val requestedProtocol = intent?.getStringExtra(EXTRA_PROTOCOL)
            establishSession(requestedProtocol)

            // DNS mode has no Room profile — it is configured via
            // DnsSettingsRepository and dispatched directly by the intent extra.
            if (requestedProtocol == VpnProtocol.Dns.title) {
                currentConnectedProtocol = VpnProtocol.Dns
                startDnsOnly()
                return@launch
            }

            val db = AppDatabase.getDatabase(applicationContext)
            val activeProfile = db.vpnProfileDao().getActiveProfile().firstOrNull()
            if (activeProfile == null) {
                emitLog("No active profile found. Stopping.")
                VpnStateManager.updateStateFromNative(3)
                removeVpnNotification()
                stopSelf()
                return@launch
            }
            currentConnectedProtocol = when (activeProfile.protocol) {
                VpnProtocol.V2rayNG.title -> VpnProtocol.V2rayNG
                VpnProtocol.Dns.title -> VpnProtocol.Dns
                else -> VpnProtocol.OpenConnect
            }

            launch {
                // Connectivity verification is now DIAGNOSTIC ONLY (see
                // ConnectivityDiagnostics): a public-endpoint DNS/HTTP failure is
                // reported as degraded, it never tears down a ready tunnel.
                // Session-scoped: only the CURRENT session's CONNECTED triggers a
                // check, and a completed check for this session is never repeated.
                var checkedForThisSession = false
                val observedSessionId = sessionId
                VpnStateManager.connectionState.collect { state ->
                    val sessionCurrent = sessionId == observedSessionId
                    if (!sessionCurrent) {
                        android.util.Log.d(TAG, "Observer for superseded session $observedSessionId ignored state $state")
                        return@collect
                    }
                    if (state == ConnectionState.CONNECTED) {
                        currentConnectedProtocol?.let { proto ->
                            BroadcastStateManager.broadcastConnectedStateDetailed(applicationContext, proto)
                        }
                        if (!checkedForThisSession &&
                            currentConnectedProtocol != VpnProtocol.V2rayNG
                        ) {
                            checkedForThisSession = true
                            launchConnectivityCheck(observedSessionId)
                        }
                    }
                }
            }

            when (activeProfile.protocol) {
                "v2rayNG" -> startV2Ray(activeProfile)
                "DNS" -> startDnsOnly()
                else -> startOpenConnectPath(activeProfile)
            }
        }
        return START_STICKY
    }

    /**
     * DNS-only connection method.
     *
     * Unlike the proxy protocols there is no Room VpnProfile for DNS: the
     * selected upstream resolver comes from DnsSettingsRepository. The TUN is
     * configured so ONLY traffic addressed to a fake resolver IP is routed into
     * the tunnel — everything else uses the normal network, so the user's IP
     * is never changed and nothing is proxied.
     *
     * netd sends the routed apps' DNS lookups to the fake resolver address;
     * DnsForwarder reads those packets off the TUN and relays them to the
     * chosen upstream through protect()ed sockets (no routing loop).
     */
    private suspend fun startDnsOnly() {
        val server = try {
            dev.rouzbeh.hybridvpn.data.repository.DnsSettingsRepository(applicationContext).getSelectedServer()
        } catch (t: Throwable) {
            emitLog("FATAL: cannot load DNS settings: ${t.message}")
            null
        }
        if (server == null) {
            emitLog("FATAL: no DNS server selected. Stopping.")
            VpnStateManager.updateStateFromNative(3)
            removeVpnNotification()
            stopSelf()
            return
        }

        val primary = try {
            InetAddress.getByName(server.primary)
        } catch (t: Throwable) {
            emitLog("FATAL: invalid primary DNS address '${server.primary}': ${t.message}")
            null
        }
        val secondary = server.secondary?.let {
            try { InetAddress.getByName(it) } catch (_: Throwable) { null }
        }
        if (primary == null) {
            VpnStateManager.updateStateFromNative(3)
            removeVpnNotification()
            stopSelf()
            return
        }
        emitLog("DNS server selected: ${server.name} (primary=${server.primary}${server.secondary?.let { ", secondary=$it" } ?: ""})")

        emitLog("Establishing DNS-only TUN...")
        if (VpnService.prepare(this) != null) {
            emitLog("FATAL: VPN consent not granted / revoked.")
            VpnStateManager.updateStateFromNative(3)
            removeVpnNotification()
            stopSelf()
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingConfigure = android.app.PendingIntent.getActivity(
            this, 0, launchIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val cfg = dev.rouzbeh.hybridvpn.core.vpn.dns.DnsTunnelConfig
        val builder = Builder()
            .setSession("HybridNestDNS")
            .setConfigureIntent(pendingConfigure)
            .setMtu(1500)
            .addAddress(cfg.TUN_ADDR_V4, 32)
            .addAddress(cfg.TUN_ADDR_V6, 128)
            // Advertise the fake resolver so the system DNS resolver targets it.
            .addDnsServer(cfg.FAKE_DNS_V4)
            .addDnsServer(cfg.FAKE_DNS_V6)
            // Route ONLY the fake resolver addresses into the tunnel. No
            // default route: nothing but DNS packets ever enters the TUN.
            .addRoute(cfg.FAKE_DNS_V4, 32)
            .addRoute(cfg.FAKE_DNS_V6, 128)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) builder.setMetered(false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) builder.setBlocking(false)

        applyPerAppRouting(builder)

        val pfd = try {
            builder.establish()
        } catch (e: dev.rouzbeh.hybridvpn.core.routing.RoutingRefusedException) {
            emitLog("FATAL: Connection refused by routing policy: " + e.message)
            VpnStateManager.updateStateFromNative(3)
            stopSelf()
            return
        } catch (t: Throwable) {
            emitLog("FATAL: establish() threw: ${t.message}")
            null
        } ?: run {
            emitLog("FATAL: establish() returned null (no consent or another VPN owns the slot).")
            VpnStateManager.updateStateFromNative(3)
            stopSelf()
            return
        }

        // DNS mode keeps pfd ownership in the service (no dup/detach): the
        // forwarder reads/writes the TUN through this FileDescriptor and
        // stopAllEngines closes vpnInterface on teardown.
        vpnInterface = pfd
        RoutingPolicyStatus.publish(
            sessionId = sessionId,
            revision = sessionRoutingPolicy?.revision ?: -1L,
            protocol = VpnProtocol.Dns.title,
            nowMs = System.currentTimeMillis()
        )
        emitLog(
            "Routing applied: DNS tunnel established with policy r" +
                (sessionRoutingPolicy?.revision ?: -1L) + " (session $sessionId)"
        )

        val forwarder = try {
            dev.rouzbeh.hybridvpn.core.vpn.dns.DnsForwarder(
                tunFd = pfd.fileDescriptor,
                upstreamPrimary = primary,
                upstreamSecondary = secondary,
                mtu = 1500,
                protectTcp = { sock -> protect(sock) },
                protectUdp = { sock -> protect(sock) },
                log = { msg -> emitLog(msg) }
            )
        } catch (t: Throwable) {
            emitLog("FATAL: cannot create DNS forwarder: ${t.message}")
            null
        }
        if (forwarder == null) {
            VpnStateManager.updateStateFromNative(3)
            stopSelf()
            return
        }
        dnsForwarder = forwarder
        forwarder.start()

        // Tunnel + forwarder are up: mark CONNECTED immediately (tunnel-ready
        // truth). The connectivity check is diagnostic-only and exercises the
        // forwarder via a real DNS resolution through the tunnel.
        VpnStateManager.updateStateFromNative(2)
        BroadcastStateManager.broadcastConnectedStateDetailed(applicationContext, VpnProtocol.Dns)
        emitLog("DNS forwarder running; marking CONNECTED (tunnel-ready). IP is NOT changed.")
        launchConnectivityCheck(sessionId)
    }

    private fun startNotificationLoop() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch(Dispatchers.IO) {
            VpnStateManager.connectionState.collect { state ->
                if (state.isTerminal || state == ConnectionState.CANCELING) {
                    removeVpnNotification()
                    return@collect
                }

                if (state == ConnectionState.CONNECTING || state == ConnectionState.RECONNECTING) {
                    try {
                        (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                            ?.notify(NOTIFICATION_ID, createNotification("HybridNest", "Connecting..."))
                    } catch (_: Throwable) {}
                } else if (state == ConnectionState.DISCONNECTING) {
                    try {
                        (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                            ?.notify(NOTIFICATION_ID, createNotification("HybridNest", "Disconnecting..."))
                    } catch (_: Throwable) {}
                } else if (state == ConnectionState.CONNECTED) {
                    while (coroutineContext.isActive && VpnStateManager.connectionState.value == ConnectionState.CONNECTED) {
                        try {
                            val dlVolume = formatVolume(VpnStateManager.rawSpeed.value.first)
                            val ulVolume = formatVolume(VpnStateManager.rawSpeed.value.second)
                            val netInfo = VpnStateManager.networkInfo.value
                            val isDetecting = netInfo.location == "Detecting..." || netInfo.location.isBlank()
                            val title = if (isDetecting) "🌐 ${netInfo.location}" else "${getFlagEmoji(netInfo.flagCode)} ${netInfo.location}"
                            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                                ?.notify(NOTIFICATION_ID, createNotification(title, "DL ↓ $dlVolume - UL ↑ $ulVolume"))
                        } catch (_: Throwable) {}
                        delay(1000)
                    }
                }
            }
        }
    }

    /** Removes the foreground and visible notification. Safe to call multiple times. */
    private fun removeVpnNotification() {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIFICATION_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Establishes session identity: captures profile-independent session data
     * (session id, routing snapshot) at connection start. Callbacks/results from
     * superseded sessions are ignored via the session id captured in observers.
     */
    private suspend fun establishSession(protocolTitle: String?) {
        // Superseded-session hygiene: if a previous session left a TUN behind or teardown is in progress,
        // finish its teardown BEFORE binding session identity to the new one, so FDs and native engines never overlap.
        if (stoppingSessionId != null || vpnInterface != null || coreTunFd != null) {
            emitLog("New session requested while prior session teardown is in progress; waiting for teardown completion first.")
            stopAllEngines(reason = "superseded by newer session", intentional = true)
            awaitTeardownCompletion(deadlineMs = 5_000, expectSessionId = sessionId)
            VpnStateManager.clearIntentionalCancel()
        }
        // Hard serialization of stop-then-start: even if the state-based wait
        // above was skipped (handles already cleared) or timed out, the prior
        // async teardown job must finish before a new core is started. This
        // guarantees V2RayManager.stopCore() has cleared the shared `running`
        // flag so the new startCore() actually launches startLoop instead of
        // early-returning on a stale "running" flag (the connected-but-no-
        // traffic bug). join() is a no-op when no teardown is in flight.
        try {
            stopJob?.join()
        } catch (t: Throwable) {
            Log.w(TAG, "stopJob join failed", t)
        }
        stopJob = null

        sessionId = System.currentTimeMillis()
        VpnStateManager.setCurrentSessionId(sessionId)
        VpnStateManager.clearIntentionalCancel()
        val snapshot = settingsRepository.snapshotRoutingPolicy()
        sessionRoutingPolicy = snapshot

        // Snapshot/revision consistency diagnostics: the settings the user sees NOW
        // must be the snapshot we captured. A mismatch means settings were written
        // concurrently; the log makes that visible for debugging.
        val currentSettingsRevision = settingsRepository.getRoutingRevision()
        if (currentSettingsRevision != snapshot.revision) {
            emitLog(
                "Routing revision changed while snapshotting: snapshot r${snapshot.revision} vs settings r$currentSettingsRevision " +
                    "(newer edits belong to the NEXT session)."
            )
        }

        emitLog(
            "Session #" + sessionId + " started (requested protocol=" +
                (protocolTitle ?: "from profile") + "), policy r" + snapshot.revision + ": " +
                snapshot.describe()
        )
    }

    private suspend fun startV2Ray(activeProfile: dev.rouzbeh.hybridvpn.data.local.VpnProfile) {
        emitLog("Preparing Xray assets...")
        prepareXrayAssets()
        emitLog("Generating and pinning Xray configuration...")

        val preparedConfig = try {
            preparePinnedXrayConfig(activeProfile)
        } catch (t: Throwable) {
            emitLog("FATAL: Cannot prepare Xray endpoint: ${t.message}")
            VpnStateManager.updateStateFromNative(3)
            stopSelf()
            return
        }

        emitLog("Establishing TUN...")
        val fd = try {
            establishTunNative(ip = "172.19.0.1", dns = dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2raySettings.VPN_DNS, excludedServerIp = preparedConfig.serverIp)
        } catch (e: dev.rouzbeh.hybridvpn.core.routing.RoutingRefusedException) {
            emitLog("FATAL: Connection refused by routing policy: " + e.message)
            VpnStateManager.updateStateFromNative(3)
            stopSelf()
            return
        }

        if (fd < 0) {
            emitLog("FATAL: TUN establish failed.")
            VpnStateManager.updateStateFromNative(3)
            stopSelf()
            return
        }

        val transportReachable = testDirectTcpConnection(preparedConfig.serverIp, preparedConfig.serverPort)
        if (!transportReachable) {
            emitLog("FATAL: Direct TCP connection to the pinned endpoint failed.")
            VpnStateManager.updateStateFromNative(3)
            stopAllEngines(reason = "Xray endpoint unreachable (transport preflight)", intentional = false)
            stopSelf()
            return
        }

        emitLog("Starting Xray core with tun fd=$fd...")
        val ok = dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2RayManager.startCore(applicationContext, preparedConfig.json, fd, v2rayCallbacks)

        if (!ok) {
            emitLog("FATAL: startCore returned false.")
            VpnStateManager.updateStateFromNative(3)
            stopSelf()
            return
        }

        // Verification delay to allow startLoop to confirm core loop startup before marking CONNECTED
        delay(100)
        if (!dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2RayManager.isRunning() || VpnStateManager.connectionState.value == ConnectionState.ERROR) {
            emitLog("FATAL: Xray core failed immediately after startLoop.")
            VpnStateManager.updateStateFromNative(3)
            stopSelf()
            return
        }

        // Tunnel + core are up: mark CONNECTED immediately (tunnel-ready truth).
        // The HTTP check becomes diagnostic-only (degraded flag), consistent with
        // OpenConnect; the launchConnectivityCheck path skips V2Ray, so run
        // it here with the local proxy explicitly.
        VpnStateManager.updateStateFromNative(2)
        BroadcastStateManager.broadcastConnectedStateDetailed(applicationContext, VpnProtocol.V2rayNG)
        emitLog("Xray core started; marking CONNECTED (tunnel-ready). Running diagnostic connectivity check via local proxy.")
        launchConnectivityCheck(sessionId)
    }

    @androidx.annotation.Keep
    private fun establishTunNative(ip: String, dns: String): Int {
        emitLog("Establishing standard TUN for OpenConnect...")
        if (VpnService.prepare(this) != null) {
            emitLog("FATAL: VPN consent not granted / revoked.")
            return -1
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingConfigure = android.app.PendingIntent.getActivity(
            this, 0, launchIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Builder()
            .setSession("HybridNestVPN")
            .setConfigureIntent(pendingConfigure)
            .setMtu(1400)
            .addAddress(ip, 32)
            .addRoute("0.0.0.0", 0)

        // `dns` may be a comma-joined list of ALL server-assigned resolvers (native
        // layer forwards the complete X-CSTP-DNS list). Each is added individually so
        // a second advertised resolver is not silently dropped.
        val serverDnsServers = dns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        serverDnsServers.forEach { server -> builder.addDnsServer(server) }
        // Static fallback ONLY when the server assigned no resolvers.
        if (serverDnsServers.isEmpty()) builder.addDnsServer("8.8.8.8")
        emitLog("Builder DNS servers: ${serverDnsServers.ifEmpty { listOf("8.8.8.8 (fallback; server assigned none)") }}")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) builder.setMetered(false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) builder.setBlocking(false)

        // RoutingRefusedException propagates to the caller (JNI callback wrapper or
        // startV2Ray) which reports the refusal; never silently widened to global.
        applyPerAppRouting(builder)

        val pfd = try {
            builder.establish()
        } catch (e: dev.rouzbeh.hybridvpn.core.routing.RoutingRefusedException) {
            throw e // Refusals must not be masked as generic establish failures.
        } catch (t: Throwable) {
            emitLog("FATAL: establish() threw: ${t.message}")
            null
        } ?: return -1

        vpnInterface = pfd
        RoutingPolicyStatus.publish(
            sessionId = sessionId,
            revision = sessionRoutingPolicy?.revision ?: -1L,
            protocol = currentConnectedProtocol?.title ?: "OpenConnect",
            nowMs = System.currentTimeMillis()
        )
        emitLog(
            "Routing applied: tunnel established with policy r" +
                (sessionRoutingPolicy?.revision ?: -1L) + " (session $sessionId)"
        )
        return try {
            val dup = android.system.Os.dup(pfd.fileDescriptor)
            coreTunFd = dup
            tunFdClaimedByCore = true
            val f = java.io.FileDescriptor::class.java.getDeclaredField("descriptor").apply { isAccessible = true }
            f.getInt(dup)
        } catch (t: Throwable) {
            emitLog("dup() failed, falling back to detachFd(): ${t.message}")
            tunFdClaimedByCore = true
            val raw = pfd.detachFd()
            vpnInterface = null
            raw
        }
    }

    private fun establishTunNative(ip: String, dns: String, excludedServerIp: InetAddress): Int {
        if (VpnService.prepare(this) != null) {
            emitLog("FATAL: VPN consent not granted / revoked.")
            return -1
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingConfigure = android.app.PendingIntent.getActivity(
            this, 0, launchIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Builder()
            .setSession("HybridNestVPN")
            .setConfigureIntent(pendingConfigure)
            .setMtu(1400)
            .addAddress(ip, 32)

        if (excludedServerIp !is Inet4Address) {
            emitLog("FATAL: IPv6 proxy endpoints are unsupported by the current TUN.")
            return -1
        }

        // Route strategy differs per mode:
        //
        // Global / Bypass: Xray runs in-process and dials the proxy server on
        //   the underlying network. The app's UID is explicitly excluded from
        //   the TUN (addDisallowedApplication) in Global/Bypass, so Xray's
        //   transport sockets never enter the tunnel — the route table is only
        //   consulted for tunneled UIDs, and the server IP exclusion is
        //   belt-and-suspenders.
        //
        // Allow: the VPN app is NOT in the allowed set (see applyPerAppRouting),
        //   so Xray's sockets are already UID-excluded. A plain 0.0.0.0/0 default
        //   route is correct and simpler than complementary routes — the route
        //   table does not affect the app's own traffic (no loop risk). The
        //   complementary-route trick was added in 1fd3ac5 on an unverified
        //   theory that a plain default route "swallows the server IP"; that
        //   theory is wrong because Xray's transport is outside the TUN by UID.
        //
        // IPv6 is intentionally NOT added: the TUN is IPv4-only, and the
        // framework's makeLinkProperties() already installs an RTN_UNREACHABLE
        // ::/0 route, giving allowed apps fast-fallback to IPv4. Advertising a
        // v6 address + route would attract Happy Eyeballs v6 traffic into a TUN
        // whose DNS (queryStrategy=UseIPv4) returns no AAAA records, creating a
        // black-hole — the very symptom this avoids.
        val policy = sessionRoutingPolicy
        val isAllowMode = policy != null && policy.enabled &&
            policy.mode == dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy.Mode.ALLOW
        if (isAllowMode) {
            builder.addRoute("0.0.0.0", 0)
            emitLog("V2Ray Allow mode: using full default route (app excluded from tunnel; no loop risk)")
        } else {
            builder.addIpv4DefaultRouteExcept(excludedServerIp)
            emitLog("Excluded Xray server from VPN routes: ${excludedServerIp.hostAddress}")
        }

        // Xray config is pinned to IPs, so `dns` is a literal resolver address here;
        // parse defensively anyway (comma-joined list convention shared with the
        // OpenConnect path).
        val serverDnsServers = dns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        serverDnsServers.forEach { server -> builder.addDnsServer(server) }
        if (serverDnsServers.isEmpty()) builder.addDnsServer("8.8.8.8")
        emitLog("Builder DNS servers: ${serverDnsServers.ifEmpty { listOf("8.8.8.8 (fallback)") }} (IPv4 default route excluding Xray endpoint)")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) builder.setMetered(false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) builder.setBlocking(false)

        applyPerAppRouting(builder)

        val pfd = try {
            builder.establish()
        } catch (e: dev.rouzbeh.hybridvpn.core.routing.RoutingRefusedException) {
            throw e // Refusals must not be masked as generic establish failures.
        } catch (t: Throwable) {
            emitLog("FATAL: establish() threw: ${t.message}")
            null
        } ?: run {
            emitLog("FATAL: establish() returned null (no consent or another VPN owns the slot).")
            return -1
        }

        vpnInterface = pfd
        RoutingPolicyStatus.publish(
            sessionId = sessionId,
            revision = sessionRoutingPolicy?.revision ?: -1L,
            protocol = currentConnectedProtocol?.title ?: "OpenConnect",
            nowMs = System.currentTimeMillis()
        )
        emitLog(
            "Routing applied: tunnel established with policy r" +
                (sessionRoutingPolicy?.revision ?: -1L) + " (session $sessionId)"
        )
        return try {
            val dup = android.system.Os.dup(pfd.fileDescriptor)
            coreTunFd = dup // Save the duplicated FD
            tunFdClaimedByCore = true
            val f = java.io.FileDescriptor::class.java.getDeclaredField("descriptor").apply { isAccessible = true }
            f.getInt(dup)
        } catch (t: Throwable) {
            emitLog("dup() failed, falling back to detachFd(): ${t.message}")
            tunFdClaimedByCore = true
            val raw = pfd.detachFd()
            vpnInterface = null
            raw
        }
    }

    private fun applyPerAppRouting(builder: Builder) {
        // The snapshot was captured at session start (establishSession); re-read it here.
        val policy = sessionRoutingPolicy
            ?: run {
                emitLog("Routing: no session snapshot, falling back to current settings")
                settingsRepository.snapshotRoutingPolicy()
            }
        emitLog("Routing: applying policy r${policy.revision}: ${policy.describe()}")

        val decision = dev.rouzbeh.hybridvpn.core.routing.RoutingPolicyResolver.resolve(
            policy = policy,
            installedPackages = installedPackagesForRouting()
        )
        emitLog("Routing decision: ${decision.describe()}")

        when (decision) {
            is dev.rouzbeh.hybridvpn.core.routing.RoutingDecision.Global -> {
                // Global routing. Transport exception for V2Ray and DNS:
                // Xray dials the proxy server directly from this app's UID; without
                // the exclusion its outbound traffic would loop back into its TUN.
                // DNS runs the in-process forwarder whose upstream sockets must
                // never enter the TUN (they use protect(), but the explicit
                // exclusion also keeps this app's connectivity checks honest).
                // OpenConnect uses protect() on transport sockets and stays
                // INSIDE the tunnel for diagnostics.
                if (currentConnectedProtocol == dev.rouzbeh.hybridvpn.service.VpnProtocol.V2rayNG ||
                    currentConnectedProtocol == dev.rouzbeh.hybridvpn.service.VpnProtocol.Dns) {
                    addDisallow(builder, packageName)
                }
                android.util.Log.d("PerAppDebug", "Global $currentConnectedProtocol policy=${policy.describe()}")
            }

            is dev.rouzbeh.hybridvpn.core.routing.RoutingDecision.Allow -> {
                decision.prunedPackages.forEach {
                    emitLog("Routing: selected app not installed, pruned: $it")
                }
                var userPackagesAdded = 0
                decision.allowedPackages.forEach { pkg ->
                    if (addAllow(builder, pkg)) userPackagesAdded++
                }

                // Per-app Allow: the VPN app must be tunneled for OpenConnect
                // so its IP/location check (vpnNetwork.openConnection) sees the VPN exit.
                // OpenConnect transports are protected via protect() -> no loop risk.
                // For V2Ray the app must NOT be tunneled: Xray runs in this process
                // without protect(), allowing it would capture its transport -> loop.
                // V2Ray detects the exit via localhost HTTP proxy (127.0.0.1:10809).
                if (currentConnectedProtocol == dev.rouzbeh.hybridvpn.service.VpnProtocol.OpenConnect) {
                    val addedSelf = addAllow(builder, packageName)
                    if (addedSelf) {
                        emitLog("Routing: allowed self (OpenConnect Allow requires tunneled app for IP detection)")
                    }
                    android.util.Log.d("PerAppDebug", "Allow OpenConnect: userAdded=$userPackagesAdded selfAdded=$addedSelf packages=${decision.allowedPackages.sorted()} pruned=${decision.prunedPackages.sorted()}")
                } else if (currentConnectedProtocol == dev.rouzbeh.hybridvpn.service.VpnProtocol.V2rayNG) {
                    android.util.Log.d("PerAppDebug", "Allow V2Ray: userAdded=$userPackagesAdded packages=${decision.allowedPackages.sorted()} pruned=${decision.prunedPackages.sorted()} (self NOT added to avoid Xray loop)")
                } else {
                    // DNS (and any other protocol): self is NOT added — the
                    // forwarder's upstream sockets must stay outside the TUN.
                    android.util.Log.d("PerAppDebug", "Allow $currentConnectedProtocol: userAdded=$userPackagesAdded packages=${decision.allowedPackages.sorted()}")
                }

                if (userPackagesAdded == 0) {
                    // Every user selection vanished at Builder time: refuse rather
                    // than establish an empty tunnel.
                    val msg = "All user-selected apps vanished before TUN creation; refusing establishment (allow policy not widened)."
                    emitLog("Routing: REFUSED - $msg")
                    throw RoutingRefusedException(msg)
                }
            }

            is dev.rouzbeh.hybridvpn.core.routing.RoutingDecision.Bypass -> {
                decision.prunedPackages.forEach {
                    emitLog("Routing: selected app not installed, pruned: $it")
                }
                decision.disallowedPackages.forEach { addDisallow(builder, it) }
                if (currentConnectedProtocol == dev.rouzbeh.hybridvpn.service.VpnProtocol.V2rayNG ||
                    currentConnectedProtocol == dev.rouzbeh.hybridvpn.service.VpnProtocol.Dns) {
                    addDisallow(builder, packageName)
                }
                android.util.Log.d("PerAppDebug", "Bypass $currentConnectedProtocol policy=${policy.describe()} disallowed=${decision.disallowedPackages.sorted()} pruned=${decision.prunedPackages.sorted()}")
            }

            is dev.rouzbeh.hybridvpn.core.routing.RoutingDecision.Refused -> {
                // allow-mode with no usable apps: refuse loudly. Never widen to global.
                emitLog("Routing: REFUSED — ${decision.reason}")
                throw dev.rouzbeh.hybridvpn.core.routing.RoutingRefusedException(decision.reason)
            }
        }
    }

    /**
     * Adds an allow rule. Returns true when the package was actually added to the
     * Builder; false when it vanished (NameNotFoundException). Throws on API-misuse
     * (UnsupportedOperationException) so mixed allow/disallow calls are never
     * silently absorbed.
     */
    private fun addAllow(builder: Builder, pkg: String): Boolean {
        return try {
            builder.addAllowedApplication(pkg)
            emitLog("Routing: allowed app: $pkg")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            emitLog("Routing: package vanished, not added: $pkg")
            false
        } catch (e: UnsupportedOperationException) {
            // Builder API contract: a disallowed rule was already present.
            emitLog("Routing: MIXED API USAGE detected (allow after disallow): $pkg")
            throw e
        }
    }

    private fun addDisallow(builder: Builder, pkg: String) {
        try {
            builder.addDisallowedApplication(pkg)
            emitLog("Routing: disallowed app: $pkg")
        } catch (e: PackageManager.NameNotFoundException) {
            emitLog("Routing: package vanished, not added: $pkg")
        } catch (e: UnsupportedOperationException) {
            emitLog("Routing: MIXED API USAGE detected (disallow after allow): $pkg")
            throw e
        }
    }

    private fun installedPackagesForRouting(): Set<String> {
        return try {
            packageManager.getInstalledApplications(0)
                .asSequence()
                .map { it.packageName }
                .toSet()
        } catch (t: Throwable) {
            // Empty set means every selected package would be "pruned": in allow mode
            // this refuses the connection instead of widening it. Fail closed.
            emitLog("Routing: failed to enumerate installed packages (fail-closed): ${t.message}")
            emptySet()
        }
    }

    private fun prepareXrayAssets() {
        val names = listOf("geoip.dat", "geosite.dat")
        for (name in names) {
            try {
                val outFile = File(filesDir, name)
                if (outFile.exists() && outFile.length() > 0) continue
                assets.open(name).use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
                emitLog("Asset copied: ${outFile.absolutePath} (${outFile.length()} bytes)")
            } catch (t: Throwable) {
                emitLog("Asset missing or copy failed for $name: ${t.message}")
            }
        }
    }

    /**
     * Diagnostic-only connectivity check for the CURRENT session.
     *
     * Runs the bounded check via [ConnectivityDiagnostics]; a non-Verified verdict
     * is reported as DEGRADED (VpnStateManager.connectivityDegraded) — it NEVER
     * tears down the tunnel. Real engine failures reach the service through the
     * native state callback and are handled separately.
     *
     * Session-safety: [forSessionId] is captured at launch; the check job is
     * cancelled by stopAllEngines, and every state update re-checks that the
     * session is still current. A superseded check cannot stop or update a newer
     * session.
     */
    private fun launchConnectivityCheck(forSessionId: Long) {
        checkJob?.cancel()
        checkJob = serviceScope.launch {
            // Preserve the protocol captured at check start; V2Ray checks run in
            // startV2Ray with the local HTTP proxy instead.
            val proto = currentConnectedProtocol
            val isV2ray = proto == VpnProtocol.V2rayNG
            val proxy = if (isV2ray) java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 10809)) else java.net.Proxy.NO_PROXY

            emitLog("Connectivity check starting (diagnostic only; failure will NOT disconnect).")
            val verdict = ConnectivityDiagnostics.runCheck(
                context = applicationContext,
                sessionId = forSessionId,
                budgetMs = 12_000,
                proxy = proxy,
                onLog = { line -> emitLog(line) }
            )

            // Session-safety: only the owning session may publish its verdict.
            if (sessionId != forSessionId) {
                android.util.Log.d(TAG, "Connectivity verdict from superseded session $forSessionId discarded")
                return@launch
            }
            when (verdict) {
                is ConnectivityDiagnostics.Verdict.Verified -> {
                    VpnStateManager.setConnectivityDegraded(false)
                    emitLog("Connectivity VERIFIED (public endpoint reachable through tunnel).")
                }
                else -> {
                    // Degraded ≠ dead: the tunnel stays up. Preserve private/corporate
                    // VPNs that intentionally cannot reach the public check endpoint.
                    VpnStateManager.setConnectivityDegraded(true)
                    emitLog("Connectivity DEGRADED: ${verdict::class.java.simpleName} - ${verdict}" )
                    emitLog("Tunnel remains up; this is diagnostic only (private VPNs legitimately fail this check).")
                }
            }
        }
    }

    /**
     * Waits for the teardown completion signal of [expectSessionId] (or any earlier
     * session) up to [deadlineMs]. Used by establishSession to guarantee a prior
     * session's native/FD teardown truly finished before a new TUN is built.
     * Suspends (never blocks a thread); called from coroutines only.
     */
    private suspend fun awaitTeardownCompletion(deadlineMs: Long, expectSessionId: Long) {
        if (stoppingSessionId == null && vpnInterface == null && coreTunFd == null) return
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = serviceScope.launch {
            VpnActionBus.teardownComplete.collect { completedSessionId ->
                if (completedSessionId == expectSessionId) done.complete(Unit)
            }
        }
        try {
            val completed = withTimeoutOrNull(deadlineMs) { done.await() }
            if (completed == null) {
                emitLog("Teardown completion wait timed out after ${deadlineMs}ms for session $expectSessionId.")
            } else {
                emitLog("Teardown completion verified for session $expectSessionId.")
            }
        } finally {
            job.cancel()
        }
    }

    private fun stopAllEngines(reason: String = "unspecified", intentional: Boolean, startId: Int = -1) {
        val forSession = sessionId
        synchronized(stopLock) {
            if (stoppingSessionId == forSession && forSession != 0L) {
                Log.d(TAG, "stopAllEngines: teardown of session $forSession already in progress; duplicate request ignored ($reason)")
                return
            }
            stoppingSessionId = forSession
        }
        stopReason = reason
        Log.d(TAG, "Stopping all engines (session $forSession, intentional=$intentional, reason=$reason)...")

        VpnStateManager.updateState(ConnectionState.DISCONNECTING)

        connectionJob?.cancel()
        connectionJob = null
        checkJob?.cancel()
        checkJob = null
        notificationJob?.cancel()
        notificationJob = null

        // Signal the UI/ViewModel that a disconnect was requested.
        VpnActionBus.disconnectFlow.tryEmit(Unit)

        if (intentional) VpnStateManager.beginIntentionalCancel()

        tunFdClaimedByCore = false
        currentConnectedProtocol = null
        sessionRoutingPolicy = null
        RoutingPolicyStatus.clearActive(forSession, intentional, reason)

        val fdToClose = coreTunFd
        val pfdToClose = vpnInterface
        val dnsToStop = dnsForwarder
        coreTunFd = null
        vpnInterface = null
        dnsForwarder = null

        stopJob = stopScope.launch {
            try { stopOpenConnectNative() } catch (e: Throwable) { Log.e(TAG, "stopOpenConnectNative failed", e) }
            try { V2RayManager.stopCore() } catch (e: Throwable) { Log.e(TAG, "V2Ray stop failed", e) }
            // Stop the DNS packet relay before closing the TUN fd so no thread
            // is mid-read/write on it.
            try { dnsToStop?.stop() } catch (e: Throwable) { Log.e(TAG, "DNS forwarder stop failed", e) }
            try { fdToClose?.let { Os.close(it) } } catch (_: Throwable) {}
            try { pfdToClose?.close() } catch (_: Throwable) {}

            // These mutate GLOBAL process state (system proxy props, process
            // network binding, the broadcast "Ready" detailed-state). A stale
            // teardown from a SUPERSEDED session must not apply them while a
            // newer session is live -> gate by session like the stopSelf path.
            if (forSession == sessionId || sessionId == 0L) {
                try {
                    System.clearProperty("socksProxyHost"); System.clearProperty("socksProxyPort")
                    System.clearProperty("http.proxyHost"); System.clearProperty("http.proxyPort")
                    System.clearProperty("https.proxyHost"); System.clearProperty("https.proxyPort")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        (getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager)?.bindProcessToNetwork(null)
                    }
                } catch (_: Throwable) {}
                BroadcastStateManager.broadcastReadyState(applicationContext)
            }

            if (forSession == sessionId || sessionId == 0L) {
                VpnStateManager.updateStateFromNative(0)
                withContext(Dispatchers.Main) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
                    } catch (_: Throwable) {}
                    if (startId != -1) {
                        stopSelfResult(startId)
                    } else {
                        stopSelf()
                    }
                }
            }
            Log.d(TAG, "Teardown of session $forSession complete (reason=$reason).")
            synchronized(stopLock) {
                if (stoppingSessionId == forSession) {
                    stoppingSessionId = null
                }
            }
            VpnActionBus.teardownComplete.tryEmit(forSession)
        }
    }

    private fun replyToStateSyncRequest() {
        VpnStateManager.initPersist(applicationContext)
        VpnStateManager.refreshPersistedStartTime(applicationContext)
        if (vpnInterface != null || VpnStateManager.connectionState.value == ConnectionState.CONNECTED) {
            // Re-broadcast CONNECTED even if vpnInterface handle was transiently null
            // due to service restart race; authoritative state is connectionState.
            val proto = currentConnectedProtocol ?: VpnProtocol.OpenConnect
            BroadcastStateManager.broadcastConnectedStateDetailed(applicationContext, proto)
            // Ensure persisted start time is authoritative for re-entry uptime.
            if (VpnStateManager.connectionStartTime == 0L) {
                val t = VpnStateManager.connectionStartTime
                // initPersist already restored it if available; nothing else to do.
                android.util.Log.d(TAG, "replyToStateSyncRequest: CONNECTED sync, startTime=$t")
            }
        } else BroadcastStateManager.broadcastReadyState(applicationContext)
    }

    override fun onRevoke() {
        stopAllEngines(reason = "VPN revoked by system/user (onRevoke)", intentional = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopAllEngines(reason = "service destroyed", intentional = true)
        super.onDestroy()
    }

    private external fun startOpenConnectNative(serverAddress: String, username: String, password: String, vpnFileDescriptor: Int, sessionId: Long)
    private external fun stopOpenConnectNative()

    private fun startOpenConnectPath(p: dev.rouzbeh.hybridvpn.data.local.VpnProfile) {
        if (!nativeLibLoaded) { 
            emitLog("FATAL: native lib unavailable") 
            VpnStateManager.updateStateFromNative(3)
            stopSelf()
            return 
        }
        startOpenConnectNative(p.serverAddress, p.username, p.password, -1, sessionId)
    }
}
