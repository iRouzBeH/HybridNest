package dev.rouzbeh.hybridvpn.feature.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rouzbeh.hybridvpn.data.local.AppDatabase
import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2RayManager
import dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig
import dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardParsedConfig
import dev.rouzbeh.hybridvpn.core.utils.LogRedactor
import dev.rouzbeh.hybridvpn.data.model.AppInfo
import dev.rouzbeh.hybridvpn.data.repository.SettingsRepository
import dev.rouzbeh.hybridvpn.service.BroadcastStateManager
import dev.rouzbeh.hybridvpn.service.ConnectionState
import dev.rouzbeh.hybridvpn.service.DetailedState
import dev.rouzbeh.hybridvpn.service.HybridVpnService
import dev.rouzbeh.hybridvpn.service.LogStore
import dev.rouzbeh.hybridvpn.service.NetworkInfo
import dev.rouzbeh.hybridvpn.service.RoutingPolicyStatus
import dev.rouzbeh.hybridvpn.service.VpnActionBus
import dev.rouzbeh.hybridvpn.service.VpnProtocol
import dev.rouzbeh.hybridvpn.service.VpnStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).vpnProfileDao()
    private val settingsRepository = SettingsRepository(application)
    val settings: SettingsRepository get() = settingsRepository
    private var isIntentionalReconnect = false
    private var isInitialStateEmission = true
    private var logcatJob: Job? = null
    private var networkFetchJob: Job? = null
    private var postConnectCheckJob: Job? = null
    private var cancelJob: Job? = null

    private val disconnectCoordinator = DisconnectCoordinator(
        scope = viewModelScope,
        stateFlow = VpnStateManager.connectionState,
        setState = { VpnStateManager.updateState(it) },
        dispatchStopSignal = { dispatchVpnStop(getApplication()) },
        timeoutMs = 4_000L
    )

    private val _sortedV2rayTabs = MutableStateFlow<Set<String>>(emptySet())
    val sortedV2rayTabs: StateFlow<Set<String>> = _sortedV2rayTabs.asStateFlow()
    private val _v2rayPingTabs = MutableStateFlow<Set<String>>(emptySet())
    val v2rayPingTabs: StateFlow<Set<String>> = _v2rayPingTabs.asStateFlow()
    private val v2rayPingJobs = ConcurrentHashMap<String, Job>()
    private val _singleLinksPingProgress = MutableStateFlow<PingProgress?>(null)
    val singleLinksPingProgress: StateFlow<PingProgress?> = _singleLinksPingProgress.asStateFlow()
    private val _v2rayPingProgressState = MutableStateFlow(V2rayPingProgressState())
    private val _v2rayPingingProfileIds = MutableStateFlow<Set<Int>>(emptySet())
    val v2rayPingingProfileIds: StateFlow<Set<Int>> = _v2rayPingingProfileIds.asStateFlow()

    private fun updateV2rayPingProgress(transform: (V2rayPingProgressState) -> V2rayPingProgressState) {
        val newState = _v2rayPingProgressState.updateAndGet(transform)
        _v2rayPingingProfileIds.value = newState.activeProfileIds
    }

    private val _v2rayTabOrder = MutableStateFlow<List<String>>(emptyList())
    val v2rayTabOrder: StateFlow<List<String>> = _v2rayTabOrder.asStateFlow()
    private val _v2raySelectedTab = MutableStateFlow(V2rayTabOrderResolver.SINGLE_LINKS_TAB)
    val v2raySelectedTab: StateFlow<String> = _v2raySelectedTab.asStateFlow()
    private var protocolRestoreJob: Job? = null

    val allProfiles: StateFlow<List<VpnProfile>> = dao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProtocol = MutableStateFlow("OpenConnect")
    val selectedProtocol: StateFlow<String> = _selectedProtocol.asStateFlow()

    val filteredProfiles: StateFlow<List<VpnProfile>> = combine(
        allProfiles, _selectedProtocol, _sortedV2rayTabs
    ) { profiles, protocol, sortedTabs ->
        val filtered = profiles.filter { it.protocol == protocol }
        if (protocol == "v2rayNG") {
            // Keep subscription info cards pinned within their tab regardless of sort / ping.
            filtered.groupBy { if (it.username.startsWith("sub:")) it.username else "SingleLinks" }
                .values
                .flatMap { group ->
                    val groupKey = if (group.first().username.startsWith("sub:")) group.first().username else "SingleLinks"
                    val (consumed, _) = mergeLeadingInfo(group.map { it.name })
                    val infoCards = group.take(consumed)
                    val regular = group.drop(consumed)
                    infoCards + if (sortedTabs.contains(groupKey)) {
                        regular.sortedBy { if (it.ping > 0) it.ping else Long.MAX_VALUE }
                    } else {
                        regular
                    }
                }
        } else {
            filtered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile: StateFlow<VpnProfile?> = dao.getActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _pendingRestoredProfile = MutableStateFlow<VpnProfile?>(null)
    val displayedActiveProfile: StateFlow<VpnProfile?> = combine(
        selectedProtocol,
        activeProfile,
        _pendingRestoredProfile
    ) { protocol, roomProfile, pendingProfile ->
        ProtocolSwitchResolver.resolveDisplayedActiveProfile(protocol, roomProfile, pendingProfile)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ProtocolSwitchResolver.resolveDisplayedActiveProfile(
            _selectedProtocol.value,
            activeProfile.value,
            _pendingRestoredProfile.value
        )
    )

    val connectionState: StateFlow<ConnectionState> = VpnStateManager.connectionState
    val speed: StateFlow<Pair<String, String>> = VpnStateManager.speed
    val networkInfo: StateFlow<NetworkInfo> = VpnStateManager.networkInfo
    val connectivityDegraded: StateFlow<Boolean> = VpnStateManager.connectivityDegraded

    /** Live Log now backed by process-scoped LogStore so it survives re-entry. */
    val currentLogs: StateFlow<List<String>> = combine(
        selectedProtocol, LogStore.openConnectLogs, LogStore.v2rayLogs, LogStore.wireguardLogs
    ) { protocol, ocLogs, v2Logs, wgLogs ->
        when (protocol) {
            "OpenConnect" -> ocLogs
            "WireGuard" -> wgLogs
            else -> v2Logs
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearLogs(protocol: String) { LogStore.clear(protocol) }

    fun clearAllProtocolLogs() {
        LogStore.clear("OpenConnect")
        LogStore.clear("WireGuard")
        LogStore.clear("v2rayNG")
        showToast("All logs cleared")
    }

    fun appendLog(protocol: String, log: String) { LogStore.append(protocol, log) }

    private fun startLogcatCapture(protocol: String) {
        logcatJob?.cancel()
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor()
                val cmd = when (protocol) {
                    "v2rayNG", "WireGuard" ->
                        "logcat -v time HybridNestVPN:D GoLog:D libv2ray:D Go:D AndroidRuntime:E DEBUG:E libc:E System.err:W *:S"
                    else ->
                        "logcat -v time OpenConnect:D HybridNestJNI:D HybridNestVPN:D VPNService:D AndroidRuntime:E DEBUG:E libc:E *:S"
                }
                val process = Runtime.getRuntime().exec(cmd)
                val reader = process.inputStream.bufferedReader()
                while (this.isActive) {
                    val line = reader.readLine()
                    if (line != null && !line.contains("beginning of main")) {
                        val cleanLine = if (line.contains(": ")) line.substringAfter(": ") else line
                        if (cleanLine.isNotBlank() && !cleanLine.contains("No work to do") && !cleanLine.contains("DTLS packet") && !cleanLine.contains("RpcSession") && !cleanLine.contains("IpcRuntime")) {
                            appendLog(protocol, cleanLine)
                        }
                    } else {
                        delay(200)
                    }
                }
            } catch (e: Exception) {
                appendLog(protocol, "Logcat capture error: ${e.message}")
            }
        }
    }

    private val _uptime = MutableStateFlow(0L)
    val uptime: StateFlow<Long> = _uptime.asStateFlow()
    private var uptimeJob: Job? = null

    private val _pingResult = MutableStateFlow<String?>(null)
    val pingResult: StateFlow<String?> = _pingResult.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    /**
     * Subscription-update results, delivered only to the v2rayNG server list.
     * Kept off the shared [toastMessage] flow so the message doesn't surface in
     * other methods' server lists. Emitted from the update coroutine on the main
     * dispatcher; collected by the screen-local toast.
     */
    private val _subscriptionToast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val subscriptionToast = _subscriptionToast.asSharedFlow()

    private val subscriptionUpdateLock = AtomicBoolean(false)
    private val _subscriptionUpdateState = MutableStateFlow(SubscriptionUpdateState())
    val subscriptionUpdateState: StateFlow<SubscriptionUpdateState> = _subscriptionUpdateState.asStateFlow()

    private fun showToast(message: String) {
        viewModelScope.launch { _toastMessage.emit(message) }
    }

    fun showDashboardToast(message: String) {
        showToast(message)
    }

    fun notifySettingsSaved(sectionName: String) {
        settingsRepository.syncRuntimeSettings()
        val msg = if (connectionState.value == ConnectionState.CONNECTED) {
            "$sectionName saved • Reconnect VPN to apply"
        } else {
            "$sectionName saved"
        }
        showToast(msg)
    }

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isRoutingPending = MutableStateFlow(false)
    val isRoutingPending: StateFlow<Boolean> = _isRoutingPending.asStateFlow()

    private val _isPerAppEnabled = MutableStateFlow(settingsRepository.isPerAppEnabled())
    val isPerAppEnabled: StateFlow<Boolean> = _isPerAppEnabled.asStateFlow()

    private val _isBypassMode = MutableStateFlow(settingsRepository.isBypassMode())
    val isBypassMode: StateFlow<Boolean> = _isBypassMode.asStateFlow()

    private var pingJob: Job? = null

    private suspend fun activateProfileAndRemember(profile: VpnProfile) {
        settingsRepository.setLastServerId(profile.protocol, profile.id)
        dao.deactivateAll()
        dao.setActiveProfile(profile.id)
    }

    // Subscription-info detection is shared with DashboardScreen so the UI and
    // the ViewModel agree on which profiles are informational cards.

    init {
        settingsRepository.migrateLegacyTabPrefsIfNeeded(application)
        _v2rayTabOrder.value = settingsRepository.getV2rayTabOrder()
        _v2raySelectedTab.value = settingsRepository.getV2rayLastSelectedTab() ?: V2rayTabOrderResolver.SINGLE_LINKS_TAB

        VpnStateManager.initPersist(application)
        // Re-hydrate in-memory start time if the process survived but
        // VpnStateManager's singleton lost its cached value (e.g. after
        // Activity recreation the ViewModel was GC'd but prefs still hold it).
        VpnStateManager.refreshPersistedStartTime(application)
        // Cache the flag now so replay logic can distinguish a real
        // state replay (collect immediately with isConnected==true) from a
        // normal emission. Must be computed BEFORE any coroutine collects.
        val initialIsConnected = VpnStateManager.connectionState.value.isConnected
        if (initialIsConnected) {
            // Hydrate UI state without broadcasting DISCONNECTED via init block.
            _uptime.value = run {
                val t = VpnStateManager.connectionStartTime
                if (t > 0) (System.currentTimeMillis() - t) / 1000 else 0L
            }
        } else {
            VpnStateManager.updateNetworkInfo("Detecting...", "Detecting...", "un")
        }

        viewModelScope.launch {
            VpnStateManager.vpnLogs.collect { (protocol, msg) -> appendLog(protocol, msg) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            combine(activeProfile, allProfiles) { active, profiles ->
                active to profiles
            }.collect { (active, profiles) ->
                if (active != null && isSubscriptionInfoName(active.name) && profiles.isNotEmpty()) {
                    val realProfile = profiles.firstOrNull {
                        it.protocol == active.protocol &&
                        it.username == active.username &&
                        !isSubscriptionInfoName(it.name)
                    }
                    if (realProfile != null) {
                        activateProfileAndRemember(realProfile)
                    } else {
                        dao.deactivateAll()
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(selectedProtocol, activeProfile, _pendingRestoredProfile) { protocol, room, pending ->
                Triple(protocol, room, pending)
            }.collect { (protocol, room, pending) ->
                if (ProtocolSwitchResolver.shouldClearPendingProfile(protocol, room, pending)) {
                    _pendingRestoredProfile.value = null
                }
            }
        }

        viewModelScope.launch {
            // "applies after reconnect" state: recompute whenever the applied revision
            // changes (tunnel established/removed) or settings change.
            RoutingPolicyStatus.appliedRevision.collect {
                refreshRoutingPendingState()
            }
        }

        viewModelScope.launch {
            dev.rouzbeh.hybridvpn.service.VpnActionBus.disconnectFlow.collect {
                val current = VpnStateManager.connectionState.value
                if (current == ConnectionState.DISCONNECTING || current == ConnectionState.CANCELING || current.isTerminal) {
                    android.util.Log.d("DashboardViewModel", "disconnectFlow: already $current, ignoring duplicate signal")
                } else {
                    cancelConnection(getApplication())
                }
            }
        }

        viewModelScope.launch {
            // Do not infer tunnel liveness from ConnectivityManager when this app
            // may be excluded from the tunnel (Allow mode for V2Ray, or any future
            // policy). That check incorrectly reports "no VPN" and forces a bogus
            // DISCONNECTED, which resets uptime and wipes the "Detecting..." state.
            // The service is authoritative: always ask it to sync state if we are
            // currently CONNECTED, otherwise ensure Ready state.
            val isConnected = VpnStateManager.connectionState.value.isConnected
            if (isConnected) {
                try {
                    val syncIntent = Intent(getApplication(), HybridVpnService::class.java).apply {
                        action = "ACTION_REQUEST_STATE_SYNC"
                    }
                    getApplication<Application>().startService(syncIntent)
                } catch (_: Exception) {
                    // Service not running (e.g. after process death while tunnel still technically exists)
                    // Keep CONNECTED UI and let uptime hydrate from persisted START_TIME.
                }
            } else {
                // Not connected: ensure UI is in Ready state without forcing DISCONNECTED
                // over an already-terminal state (avoids clobbering isInitialStateEmission logic).
                if (VpnStateManager.connectionState.value.isTerminal) {
                    BroadcastStateManager.broadcastReadyState(application)
                } else {
                    // Transitional state (CONNECTING etc) — request sync to recover.
                    try {
                        val syncIntent = Intent(getApplication(), HybridVpnService::class.java).apply {
                            action = "ACTION_REQUEST_STATE_SYNC"
                        }
                        getApplication<Application>().startService(syncIntent)
                    } catch (_: Exception) {}
                }
            }

            val active = dao.getActiveProfile().firstOrNull()
            if (active != null && settingsRepository.getLastServerId(active.protocol) == null) {
                settingsRepository.setLastServerId(active.protocol, active.id)
            }
            if (ConnectionLockGuard.isLocked(connectionState.value) && active != null) {
                _selectedProtocol.value = active.protocol
            } else {
                val targetProtocol = settingsRepository.getLastSelectedProtocol()
                    ?: active?.protocol?.takeIf { it in setOf("OpenConnect", "WireGuard", "v2rayNG") }
                    ?: "OpenConnect"
                _selectedProtocol.value = targetProtocol
                settingsRepository.setLastSelectedProtocol(targetProtocol)
                if (active?.protocol != targetProtocol && !ConnectionLockGuard.isLocked(connectionState.value)) {
                    val savedId = settingsRepository.getLastServerId(targetProtocol)
                    if (savedId != null) {
                        val savedProfile = dao.getProfileById(savedId)
                        if (savedProfile != null && savedProfile.protocol == targetProtocol) {
                            dao.deactivateAll()
                            dao.setActiveProfile(savedId)
                        } else {
                            settingsRepository.clearLastServerId(targetProtocol)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            VpnStateManager.detailedConnectionStateFlow.collect { detailedState ->
                when (detailedState) {
                    is DetailedState.Connected -> {
                        _selectedProtocol.value = detailedState.activeProtocol.title
                        VpnStateManager.updateSelectionLockedState(true)
                    }
                    DetailedState.Ready -> {
                        VpnStateManager.updateSelectionLockedState(false)
                    }
                }
            }
        }

        loadApps()
        fetchInitialNetworkInfo()

        viewModelScope.launch {
            connectionState.collect { state ->
                if (isIntentionalReconnect) {
                    if (state == ConnectionState.CONNECTED || state == ConnectionState.ERROR || state == ConnectionState.FAILED) {
                        isIntentionalReconnect = false
                    } else {
                        VpnStateManager.updateState(ConnectionState.RECONNECTING)
                        return@collect
                    }
                }

                if (state.isConnectingState || state == ConnectionState.CONNECTED) {
                    val protocol = activeProfile.value?.protocol ?: _selectedProtocol.value
                    VpnStateManager.startEngine(getApplication(), protocol)
                } else if (state == ConnectionState.DISCONNECTING || state == ConnectionState.CANCELING) {
                    VpnStateManager.stopEngine(getApplication())
                }

                if (state == ConnectionState.CONNECTING) {
                    ++ipLookupSessionId
                    networkFetchJob?.cancel()
                    postConnectCheckJob?.cancel()
                    cancelJob?.cancel()
                    VpnStateManager.updateNetworkInfo("Detecting...", "Detecting...", "un")
                    _uptime.value = 0L // Force zero during connection phase
                    // Only clear logs when a NEW connection is about to be established,
                    // not on ViewModel recreation replay. The re-entry scenario replays
                    // CONNECTING only when a genuine new connection is starting; if we
                    // are replaying CONNECTING because the singleton still holds it from
                    // a previous session, the service's new sessionId will differ — but
                    // for correctness we gate on whether we actually transitioned INTO
                    // CONNECTING from a non-connecting state, not on replay.
                    // LogStore is process-scoped so naive clears would wipe history.
                    if (!isInitialStateEmission) {
                        clearLogs(activeProfile.value?.protocol ?: _selectedProtocol.value)
                    }
                    logcatJob?.cancel()
                    startLogcatCapture(activeProfile.value?.protocol ?: _selectedProtocol.value)
                } else if (state == ConnectionState.DISCONNECTING || state == ConnectionState.CANCELING) {
                    ++ipLookupSessionId
                    networkFetchJob?.cancel()
                    postConnectCheckJob?.cancel()
                    VpnStateManager.updateNetworkInfo("Detecting...", "Detecting...", "un")
                } else if (state.isTerminal) {
                    disconnectCoordinator.onStateChanged(state)
                    postConnectCheckJob?.cancel()
                    if (isInitialStateEmission) {
                        isInitialStateEmission = false
                    } else {
                        // Only stop things, do not reset persistent state here; VpnStateManager handles that internally
                        // when transitions actually happen natively, not just on UI reconnection.
                        VpnStateManager.stopEngine(getApplication())
                        logcatJob?.cancel()
                        stopUptime()
                        _uptime.value = 0L
                        _pingResult.value = null
                        VpnStateManager.updateSelectionLockedState(false)

                        VpnStateManager.updateNetworkInfo("Detecting...", "Detecting...", "un")
                        fetchInitialNetworkInfo(initialDelayMs = 300L)
                    }
                }

                if (state == ConnectionState.CONNECTED) {
                    stopUptime()

                    val savedStartTime = VpnStateManager.connectionStartTime
                    if (savedStartTime > 0L) {
                        _uptime.value = (System.currentTimeMillis() - savedStartTime) / 1000
                    } else {
                        _uptime.value = 0L
                    }
                    startUptime()

                    val wasInitialReplay = isInitialStateEmission
                    if (isInitialStateEmission) {
                        isInitialStateEmission = false
                    }

                    // Reset IP/location/flag to the loading state immediately when
                    // entering CONNECTED so the card never flashes the previous
                    // ISP info before the VPN exit IP lookup completes.
                    val curNet = VpnStateManager.networkInfo.value
                    if (!wasInitialReplay || curNet.location == "Detecting..." || curNet.ip == "Detecting..." || curNet.ip == "Unknown") {
                        VpnStateManager.updateNetworkInfo("Detecting...", "Detecting...", "un")
                    }

                    // Track and cancel any prior postConnectCheckJob so a rapid
                    // disconnect->reconnect within 2s never lets the old session's
                    // delayed coroutine fire early during the new session.
                    val connectedSessionId = VpnStateManager.currentSessionId
                    postConnectCheckJob?.cancel()
                    postConnectCheckJob = viewModelScope.launch {
                        delay(2000.milliseconds)
                        if (isActive &&
                            connectionState.value == ConnectionState.CONNECTED &&
                            VpnStateManager.currentSessionId == connectedSessionId
                        ) {
                            fetchCurrentNetworkInfo()
                            testPing()
                        }
                    }
                }
            }
        }
    }

    private fun startUptime() {
        uptimeJob?.cancel()
        if (connectionState.value != ConnectionState.CONNECTED) return
        uptimeJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(1000)
                _uptime.value += 1
            }
        }
    }

    private fun stopUptime() { uptimeJob?.cancel() }

    fun setProtocol(protocol: String) {
        if (protocol !in setOf("OpenConnect", "WireGuard", "v2rayNG")) return
        if (ConnectionLockGuard.isLocked(connectionState.value)) return

        val savedId = settingsRepository.getLastServerId(protocol)
        val matchedProfile = if (savedId != null) {
            allProfiles.value.firstOrNull { it.id == savedId && it.protocol == protocol }
        } else null

        _pendingRestoredProfile.value = matchedProfile
        _selectedProtocol.value = protocol
        settingsRepository.setLastSelectedProtocol(protocol)
        // Do NOT clear logs when switching protocol tabs; logs are per-protocol
        // buffers in LogStore and should persist until a new connection starts.

        protocolRestoreJob?.cancel()
        protocolRestoreJob = viewModelScope.launch(Dispatchers.IO) {
            if (ConnectionLockGuard.isLocked(connectionState.value)) return@launch
            val currentSavedId = settingsRepository.getLastServerId(protocol) ?: run {
                if (_selectedProtocol.value == protocol) {
                    _pendingRestoredProfile.value = null
                }
                return@launch
            }
            val savedProfile = dao.getProfileById(currentSavedId)
            if (savedProfile != null && savedProfile.protocol == protocol) {
                if (_selectedProtocol.value == protocol && !ConnectionLockGuard.isLocked(connectionState.value)) {
                    if (_pendingRestoredProfile.value?.id != currentSavedId && _selectedProtocol.value == protocol) {
                        _pendingRestoredProfile.value = savedProfile
                    }
                    if (dao.getActiveProfile().firstOrNull()?.id != currentSavedId) {
                        dao.deactivateAll()
                        dao.setActiveProfile(currentSavedId)
                    }
                }
            } else {
                settingsRepository.clearLastServerId(protocol)
                if (_selectedProtocol.value == protocol) {
                    _pendingRestoredProfile.value = null
                }
            }
        }
    }

    fun testPing(ipToPing: String = "8.8.8.8") {
        if (connectionState.value != ConnectionState.CONNECTED) return // Guard against early execution
        pingJob?.cancel()
        pingJob = viewModelScope.launch(Dispatchers.IO) {
            _pingResult.value = "Testing..."
            val activeProto = activeProfile.value?.protocol ?: _selectedProtocol.value
            if (activeProto == "v2rayNG" || activeProto == "WireGuard") {
                val rtt = V2RayManager.measureRealPing()
                _pingResult.value = if (rtt >= 0) "$rtt ms" else "Timeout"
                return@launch
            }
            try {
                val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val vpnNetwork = cm.allNetworks.firstOrNull { net ->
                    cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }
                if (vpnNetwork != null) {
                    val start = System.currentTimeMillis()
                    java.net.Socket().use { socket ->
                        vpnNetwork.bindSocket(socket)
                        socket.connect(java.net.InetSocketAddress("1.1.1.1", 53), 3000)
                    }
                    val rtt = System.currentTimeMillis() - start
                    _pingResult.value = "$rtt ms"
                } else {
                    val process = Runtime.getRuntime().exec("ping -c 1 -w 2 $ipToPing")
                    val reader = process.inputStream.bufferedReader()
                    var rtt = -1.0
                    reader.forEachLine { line ->
                        if (line.contains("time=")) {
                            val timeStr = line.substringAfter("time=").substringBefore(" ms")
                            rtt = timeStr.toDouble()
                        }
                    }
                    process.waitFor()
                    _pingResult.value = if (rtt >= 0) "${rtt.toInt()} ms" else "Timeout"
                }
            } catch (_: Exception) { _pingResult.value = "Error" }
        }
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = getApplication<Application>().packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val ownPackageName = getApplication<Application>().packageName
                val selectedApps = settingsRepository.getSelectedApps()

                val appInfoList = apps.mapNotNull { app ->
                    // HybridNest itself is NEVER selectable: allow-mode includes it
                    // implicitly for tunnel diagnostics, bypass-mode cannot exclude it
                    // (that would kill the app's own connectivity checks).
                    // Documented exception; see MANUAL_TEST.md.
                    if (app.packageName == ownPackageName) return@mapNotNull null
                    val isSystemFlag = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystemFlag = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    val isLaunchable = pm.getLaunchIntentForPackage(app.packageName) != null
                    AppInfo(
                        appName = app.loadLabel(pm).toString(),
                        packageName = app.packageName,
                        icon = app.loadIcon(pm),
                        isSystemApp = isSystemFlag && !isUpdatedSystemFlag && !isLaunchable,
                        isSelected = selectedApps.contains(app.packageName)
                    )
                }.sortedWith(compareByDescending<AppInfo> { it.isSelected }.thenBy { it.appName.lowercase() })
                _installedApps.value = appInfoList
                refreshRoutingPendingState()
            } catch (e: Exception) { android.util.Log.e("DashboardViewModel", LogRedactor.redact("Failed to load apps: ${e.message}")) }
        }
    }

    fun toggleAppSelection(packageName: String) {
        val currentSavedApps = settingsRepository.getSelectedApps() ?: emptySet()
        val updatedApps = currentSavedApps.toMutableSet()
        if (updatedApps.contains(packageName)) updatedApps.remove(packageName) else updatedApps.add(packageName)
        settingsRepository.setSelectedAppsAndBump(updatedApps.toSet())
        _installedApps.value = _installedApps.value.map { app ->
            if (app.packageName == packageName) app.copy(isSelected = updatedApps.contains(packageName)) else app
        }.sortedWith(compareByDescending<AppInfo> { it.isSelected }.thenBy { it.appName.lowercase() })
        refreshRoutingPendingState()
    }

    fun setPerAppEnabled(enabled: Boolean) {
        _isPerAppEnabled.value = enabled
        settingsRepository.setPerAppEnabledAndBump(enabled)
        refreshRoutingPendingState()
    }

    fun setBypassMode(enabled: Boolean) {
        _isBypassMode.value = enabled
        settingsRepository.setBypassModeAndBump(enabled)
        refreshRoutingPendingState()
    }

    fun selectAllApps() {
        val allPackages = _installedApps.value.map { it.packageName }.toSet()
        settingsRepository.setSelectedAppsAndBump(allPackages)
        _installedApps.value = _installedApps.value.map { it.copy(isSelected = true) }
        refreshRoutingPendingState()
    }

    fun deselectAllApps() {
        settingsRepository.setSelectedAppsAndBump(emptySet())
        _installedApps.value = _installedApps.value.map { it.copy(isSelected = false) }
        refreshRoutingPendingState()
    }

    /**
     * "applies after reconnect" marker. A tunnel, if any, was established with
     * RoutingPolicyStatus.appliedRevision; any change since (higher current revision)
     * is pending until the user explicitly reconnects.
     */
    private fun refreshRoutingPendingState() {
        val current = settingsRepository.getRoutingRevision()
        val applied = RoutingPolicyStatus.appliedRevision.value
        _isRoutingPending.value = applied != null && current != applied
        if (_isRoutingPending.value) {
            showToast("Routing changes apply after reconnect")
        }
    }

    @Volatile private var lastKnownRealNetworkInfo: GeoIpResult? = null
    @Volatile private var ipLookupSessionId = 0L

    private fun buildFreshHttpClient(
        proxy: java.net.Proxy = java.net.Proxy.NO_PROXY,
        vpnNetwork: android.net.Network? = null
    ): okhttp3.OkHttpClient {
        val builder = okhttp3.OkHttpClient.Builder()
            .connectTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .callTimeout(3500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .connectionPool(okhttp3.ConnectionPool(0, 1, java.util.concurrent.TimeUnit.MILLISECONDS))
            .retryOnConnectionFailure(false)
            .followRedirects(true)
            .followSslRedirects(true)
            .cache(null)
            .proxy(proxy)
        if (vpnNetwork != null && proxy == java.net.Proxy.NO_PROXY) {
            builder.socketFactory(vpnNetwork.socketFactory)
            builder.dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    return vpnNetwork.getAllByName(hostname).toList()
                }
            })
        }
        return builder.build()
    }

    private fun executeIpLookupRequest(
        client: okhttp3.OkHttpClient,
        url: String
    ): GeoIpResult? {
        val req = okhttp3.Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache, no-store")
            .header("Pragma", "no-cache")
            .header("Connection", "close")
            .build()
        val call = client.newCall(req)
        return try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) null
                else {
                    val body = resp.body?.string() ?: return null
                    parseIpResponse(url, body)
                }
            }
        } catch (ce: CancellationException) {
            try { call.cancel() } catch (_: Throwable) {}
            throw ce
        } catch (_: Throwable) {
            try { call.cancel() } catch (_: Throwable) {}
            null
        }
    }

    private suspend fun lookupPublicIpWithConsensus(
        client: okhttp3.OkHttpClient,
        rejectCandidate: (GeoIpResult) -> Boolean = { false }
    ): GeoIpResult? = coroutineScope {
        val primaryDeferred = GeoIpLookup.PRIMARY_PUBLIC_IP_ENDPOINTS.map { url ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(3500L) {
                    executeIpLookupRequest(client, url)
                }
            }
        }
        val primaryResults = primaryDeferred.awaitAll()
            .filterNotNull()
            .filterNot { rejectCandidate(it) }

        val primaryIpv4 = primaryResults.filter { isValidIpv4(it.ip) }
        val ipv6Candidates = primaryResults.filter { it.ip.contains(':') }.toMutableList()

        if (primaryIpv4.size == 2) {
            val code0 = primaryIpv4[0].countryCode.trim().lowercase(java.util.Locale.US)
            val code1 = primaryIpv4[1].countryCode.trim().lowercase(java.util.Locale.US)
            val hasValidCode0 = code0.length == 2 && code0 != "un" && code0 != "xx"
            val hasValidCode1 = code1.length == 2 && code1 != "un" && code1 != "xx"

            if (hasValidCode0 && hasValidCode1 && code0 == code1) {
                return@coroutineScope GeoIpLookup.selectConsensusResult(primaryIpv4)
            }

            var tieBreaker: GeoIpResult? = null
            for (fallbackUrl in GeoIpLookup.FALLBACK_PUBLIC_IP_ENDPOINTS) {
                val candidate = withTimeoutOrNull(3500L) {
                    executeIpLookupRequest(client, fallbackUrl)
                }
                if (candidate != null && !rejectCandidate(candidate)) {
                    if (isValidIpv4(candidate.ip)) {
                        tieBreaker = candidate
                        break
                    } else if (candidate.ip.contains(':')) {
                        ipv6Candidates.add(candidate)
                    }
                }
            }
            val candidates = if (tieBreaker != null) primaryIpv4 + tieBreaker else primaryIpv4
            return@coroutineScope GeoIpLookup.selectConsensusResult(candidates)
        }

        if (primaryIpv4.size == 1) {
            return@coroutineScope primaryIpv4.first()
        }

        for (fallbackUrl in GeoIpLookup.FALLBACK_PUBLIC_IP_ENDPOINTS) {
            val candidate = withTimeoutOrNull(3500L) {
                executeIpLookupRequest(client, fallbackUrl)
            }
            if (candidate != null && !rejectCandidate(candidate)) {
                if (isValidIpv4(candidate.ip)) {
                    return@coroutineScope candidate
                } else if (candidate.ip.contains(':')) {
                    ipv6Candidates.add(candidate)
                }
            }
        }

        if (ipv6Candidates.isNotEmpty()) {
            return@coroutineScope GeoIpLookup.selectConsensusResult(ipv6Candidates)
        }

        null
    }

    fun fetchInitialNetworkInfo(initialDelayMs: Long = 0L) {
        if (!connectionState.value.isTerminal) return
        val currentLookupId = ++ipLookupSessionId
        networkFetchJob?.cancel()
        networkFetchJob = viewModelScope.launch(Dispatchers.IO) {
            if (initialDelayMs > 0L) {
                delay(initialDelayMs)
                if (!isActive || currentLookupId != ipLookupSessionId || !connectionState.value.isTerminal) return@launch
            }
            System.setProperty("http.keepAlive", "false")
            var successResult: GeoIpResult? = null
            val retryDelaysMs = longArrayOf(0L, 2000L)
            val client = buildFreshHttpClient()

            try {
                for (attemptIndex in retryDelaysMs.indices) {
                    val delayBeforeMs = retryDelaysMs[attemptIndex]
                    if (delayBeforeMs > 0L) delay(delayBeforeMs)
                    if (!isActive || currentLookupId != ipLookupSessionId || !connectionState.value.isTerminal) return@launch

                    val result = lookupPublicIpWithConsensus(client) { false }
                    if (result != null) {
                        successResult = result
                        break
                    }
                }
            } finally {
                try { client.connectionPool.evictAll() } catch (_: Throwable) {}
            }

            if (isActive && currentLookupId == ipLookupSessionId && connectionState.value.isTerminal) {
                if (successResult != null) {
                    lastKnownRealNetworkInfo = successResult
                    VpnStateManager.lastKnownRealIp = successResult.ip
                    VpnStateManager.updateNetworkInfo(successResult.ip, successResult.location, successResult.flagCode)
                } else {
                    VpnStateManager.updateNetworkInfo("Unavailable", "Unavailable", "un")
                }
            }
        }
    }

    private fun fetchCurrentNetworkInfo() {
        if (connectionState.value != ConnectionState.CONNECTED) return
        val sessionAtStart = VpnStateManager.currentSessionId
        val currentLookupId = ++ipLookupSessionId
        networkFetchJob?.cancel()
        networkFetchJob = viewModelScope.launch(Dispatchers.IO) {
            System.setProperty("http.keepAlive", "false")
            val detailedState = VpnStateManager.detailedConnectionStateFlow.value
            val detailedProto = (detailedState as? DetailedState.Connected)?.activeProtocol?.title
            val profileAtStart = activeProfile.value
            val activeProto = when {
                profileAtStart?.protocol == "WireGuard" || detailedProto == "WireGuard" -> "WireGuard"
                profileAtStart?.protocol == "v2rayNG" || detailedProto == "v2rayNG" -> "v2rayNG"
                profileAtStart?.protocol == "OpenConnect" || detailedProto == "OpenConnect" -> "OpenConnect"
                else -> _selectedProtocol.value
            }
            val isXray = activeProto == "v2rayNG" || activeProto == "WireGuard"
            val proxy = if (isXray) java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 10809)) else java.net.Proxy.NO_PROXY
            val targetProfileCountry = profileAtStart?.countryCode?.trim()?.lowercase()

            VpnStateManager.updateNetworkInfo("Detecting...", "Detecting...", "un")

            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            // Resolve the VPN network lazily per endpoint attempt. AnyConnect's TUN
            // can take a moment to register with ConnectivityManager after the
            // tunnel reports UP, so a single early query may transiently return
            // null. Re-querying each iteration (plus a small wait when absent)
            // makes the lookup bind to the VPN network reliably for AnyConnect.
            fun findVpnNetwork(): android.net.Network? =
                cm.allNetworks.firstOrNull { net ->
                    cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }

            fun isRealIspLeak(candidate: GeoIpResult): Boolean {
                val realInfo = lastKnownRealNetworkInfo
                val realIp = realInfo?.ip ?: VpnStateManager.lastKnownRealIp
                if (!realIp.isNullOrBlank() && candidate.ip.equals(realIp, ignoreCase = true)) {
                    return true
                }
                if (targetProfileCountry != "ir") {
                    if (candidate.flagCode.equals("ir", ignoreCase = true) ||
                        candidate.location.equals("Iran", ignoreCase = true)
                    ) {
                        return true
                    }
                }
                return false
            }

            var successResult: GeoIpResult? = null
            val retryDelaysMs = longArrayOf(0L, 1500L, 3000L, 5000L)

            for (attemptIndex in retryDelaysMs.indices) {
                val delayBeforeMs = retryDelaysMs[attemptIndex]
                if (delayBeforeMs > 0L) delay(delayBeforeMs)
                if (!isActive || currentLookupId != ipLookupSessionId ||
                    connectionState.value != ConnectionState.CONNECTED ||
                    VpnStateManager.currentSessionId != sessionAtStart
                ) return@launch

                val client = try {
                    when {
                        isXray -> buildFreshHttpClient(proxy = proxy)
                        else -> {
                            var vpnNet = findVpnNetwork()
                            var waitCount = 0
                            while (vpnNet == null && waitCount < 5 && isActive && connectionState.value == ConnectionState.CONNECTED) {
                                delay(400)
                                vpnNet = findVpnNetwork()
                                waitCount++
                            }
                            if (vpnNet == null) {
                                android.util.Log.w("DashboardViewModel", "vpnNetwork null, skipping attempt (no direct fallback)")
                                continue
                            }
                            buildFreshHttpClient(proxy = java.net.Proxy.NO_PROXY, vpnNetwork = vpnNet)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    continue
                }

                try {
                    val candidate = lookupPublicIpWithConsensus(client) { res ->
                        val leak = isRealIspLeak(res)
                        if (leak) {
                            android.util.Log.w("DashboardViewModel", "Rejected pre-tunnel ISP leak: ${res.ip} (${res.location})")
                        }
                        leak
                    }
                    if (candidate != null) {
                        successResult = candidate
                        break
                    }
                } finally {
                    try { client.connectionPool.evictAll() } catch (_: Throwable) {}
                }
            }

            val successFinal = successResult
            if (isActive && connectionState.value == ConnectionState.CONNECTED &&
                VpnStateManager.currentSessionId == sessionAtStart && currentLookupId == ipLookupSessionId
            ) {
                if (successFinal != null) {
                    VpnStateManager.updateNetworkInfo(successFinal.ip, successFinal.location, successFinal.flagCode)
                } else {
                    VpnStateManager.updateNetworkInfo("Unavailable", "VPN exit unavailable", "un")
                }
            }
        }
    }

    private fun isValidIpv4(ip: String): Boolean = GeoIpLookup.isValidIpv4(ip)

    private fun parseIpResponse(url: String, response: String): GeoIpResult? {
        return GeoIpLookup.parseResponse(url, response)
    }

    /**
     * Early refusal check mirroring RoutingPolicyResolver: an allow-mode policy with
     * no usable selected apps must NEVER start a connection (it would either fail at
     * establish() or silently widen). Surface the reason BEFORE any service intent.
     */
    private fun routingRefusalReason(): String? {
        val policy = settingsRepository.snapshotRoutingPolicy()
        if (!policy.enabled) return null
        if (policy.mode != dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy.Mode.ALLOW) return null
        val pm = getApplication<Application>().packageManager
        val installed = pm.getInstalledApplications(0).asSequence().map { it.packageName }.toSet()
        val usable = policy.packages.intersect(installed)
        return if (usable.isEmpty()) {
            "'Only selected apps use VPN' is enabled but none of the selected apps are " +
                "installed. Select at least one installed app or disable per-app routing."
        } else null
    }

    /** Explicit user-triggered reconnect so saved routing edits take effect. */
    fun reconnectWithLatestPolicy(context: Context) {
        if (connectionState.value != ConnectionState.CONNECTED &&
            connectionState.value != ConnectionState.RECONNECTING
        ) return
        showToast("Reconnecting with updated routing settings…")
        val currentProfile = activeProfile.value
        viewModelScope.launch {
            try {
                // Full teardown (native engine included) before re-establishing, so the
                // new TUN is built from the latest policy snapshot.
                VpnStateManager.updateState(ConnectionState.CANCELING)
                // Send the authoritative stop ONCE. The service ignores duplicate stop
                // requests per session, so re-sending FORCE_STOP later would be a no-op
                // at best and a fresh-service-spawn at worst — do not do it.
                val forceStopIntent = Intent(context, HybridVpnService::class.java).apply { action = "ACTION_FORCE_STOP" }
                try {
                    context.startService(forceStopIntent)
                } catch (_: Throwable) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(forceStopIntent)
                        } else {
                            context.startService(forceStopIntent)
                        }
                    } catch (_: Throwable) {}
                }

                // Await REAL teardown completion (native cancel signals + FD close),
                // not merely a DISCONNECTED state flag: VpnActionBus.teardownComplete
                // carries the sessionId whose teardown fully finished.
                val sessionAtStop = RoutingPolicyStatus.lastApplied.value?.sessionId
                val completed: Long? = withTimeoutOrNull(10_000) {
                    VpnActionBus.teardownComplete.first { completedSessionId ->
                        sessionAtStop == null || completedSessionId >= sessionAtStop
                    }
                }
                if (completed == null) {
                    android.util.Log.w("DashboardViewModel", "Teardown completion not observed within 10s; reconnecting anyway (FD close is idempotent).")
                }

                // If allow mode became empty, refuse to reconnect into global mode.
                routingRefusalReason()?.let { reason ->
                    showToast(reason)
                    VpnStateManager.updateState(ConnectionState.ERROR)
                    return@launch
                }
                startVpn(context)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", LogRedactor.redact("reconnectWithLatestPolicy failed: ${e.javaClass.simpleName}: ${e.message}"))
                VpnStateManager.updateState(ConnectionState.ERROR)
            }
        }
    }

    fun startVpn(context: Context) {
        val currentProfile = activeProfile.value
        if (currentProfile == null || currentProfile.protocol != _selectedProtocol.value || isSubscriptionInfoName(currentProfile.name)) return
        routingRefusalReason()?.let { reason ->
            showToast(reason)
            VpnStateManager.updateState(ConnectionState.ERROR)
            return
        }

        ++ipLookupSessionId
        networkFetchJob?.cancel()
        postConnectCheckJob?.cancel()
        cancelJob?.cancel()
        VpnStateManager.updateState(ConnectionState.CONNECTING)

        viewModelScope.launch {
            try {
                val intent = Intent(context, HybridVpnService::class.java).apply {
                    action = HybridVpnService.ACTION_START_VPN
                    putExtra(HybridVpnService.EXTRA_PROTOCOL, currentProfile.protocol)
                }
                context.startService(intent)
            } catch (e: Exception) { VpnStateManager.updateState(ConnectionState.ERROR) }
        }
    }

    fun dispatchVpnStop(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, HybridVpnService::class.java).apply {
            action = "ACTION_STOP_VPN"
        }
        try {
            appContext.startService(intent)
        } catch (_: Throwable) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * User tapped "Disconnect" while already connecting. This is the correct
     * behaviour: the tunnel is still being established, so we cancel the
     * in-flight connection attempt — NOT a normal graceful disconnect from
     * CONNECTED. The notification action currently maps to the same path
     * (ACTION_STOP_VPN) so both UI and notification behave consistently. If in
     * future you want notification-Disconnect to do a clean CONNECTED
     * disconnect vs. a cancel, branch on VpnStateManager.connectionState here.
     */
    fun cancelConnection(context: Context) {
        isIntentionalReconnect = false
        val current = VpnStateManager.connectionState.value
        if (current == ConnectionState.CANCELING || current == ConnectionState.DISCONNECTED) {
            android.util.Log.d("DashboardViewModel", "cancelConnection: already $current, ignoring re-entrant call")
            return
        }
        android.util.Log.d("DashboardViewModel", "cancelConnection from $current via graceful cancel")
        ++ipLookupSessionId
        networkFetchJob?.cancel()
        postConnectCheckJob?.cancel()
        VpnStateManager.beginIntentionalCancel()
        VpnStateManager.updateState(ConnectionState.CANCELING)
        dispatchVpnStop(context)

        cancelJob?.cancel()
        cancelJob = viewModelScope.launch {
            try {
                delay(1000)
                // Only force DISCONNECTED if teardown hasn't already completed
                // and no newer session has started.
                val stateAfterDelay = VpnStateManager.connectionState.value
                if (!isActive || (stateAfterDelay != ConnectionState.CANCELING && stateAfterDelay != ConnectionState.CANCELLED)) {
                    android.util.Log.d("DashboardViewModel", "cancelConnection: state already $stateAfterDelay; skipping forced DISCONNECTED")
                    return@launch
                }
                VpnStateManager.updateState(ConnectionState.DISCONNECTED)
                VpnStateManager.resetUsage()
                stopUptime()
                _uptime.value = 0L
            } catch (_: CancellationException) {
            } catch (e: Exception) { VpnStateManager.updateState(ConnectionState.ERROR) }
        }
    }

    /**
     * User tapped "Disconnect" while CONNECTED (the normal case, both from UI
     * and Notification). Performs a graceful disconnect: DISCONNECTING so the
     * UI shows "Disconnecting" instead of an immediate jump to DISCONNECTED.
     * The service's teardown path will eventually broadcast the terminal state;
     * we do NOT force DISCONNECTED here — the explicit cancel path above does.
     * This addresses the report that the button "immediately shuts down the
     * VPN state as if doing a force stop".
     */
    fun initiateDisconnect(context: Context = getApplication(), onActualStop: (Context) -> Unit = {}) {
        isIntentionalReconnect = false
        android.util.Log.d("DashboardViewModel", "initiateDisconnect via DisconnectCoordinator from ${VpnStateManager.connectionState.value}")
        ++ipLookupSessionId
        networkFetchJob?.cancel()
        postConnectCheckJob?.cancel()
        cancelJob?.cancel()
        stopUptime()
        if (VpnStateManager.connectionState.value == ConnectionState.CONNECTING) {
            cancelConnection(context)
        } else {
            disconnectCoordinator.requestDisconnect()
        }
        onActualStop(context)
    }

    fun resetState() {
        isIntentionalReconnect = false
        VpnStateManager.updateStateFromNative(0)
    }

    fun addServer(name: String, serverAddress: String, user: String, pass: String, protocol: String, countryCode: String = "un") {
        val cName = name.trim(); val cAddr = serverAddress.trim()
        val cUser = user.trim(); val cPass = pass.trim()
        
        viewModelScope.launch(Dispatchers.IO) {
            dao.deactivateAll()
            if (protocol == "v2rayNG") {
                val validPrefixes = listOf("vmess://", "vless://", "trojan://", "ss://", "ssr://", "v2ray://")
                if (cAddr.startsWith("http://") || cAddr.startsWith("https://")) {
                    try {
                        val connection = URL(cAddr).openConnection().apply {
                            connectTimeout = 10000
                            val ua = settingsRepository.getSubUserAgent().trim()
                            if (ua.isNotEmpty()) setRequestProperty("User-Agent", ua)
                        }
                        val response = connection.getInputStream().bufferedReader().use { it.readText() }
                        val decoded = try { String(android.util.Base64.decode(response, android.util.Base64.DEFAULT)) } catch (e: Exception) { response }
                        val lines = decoded.lines().map { it.trim() }.filter { line -> line.isNotBlank() && validPrefixes.any { line.startsWith(it, true) } }
                        if (lines.isEmpty()) { withContext(Dispatchers.Main) { showToast("Invalid subscription!") }; return@launch }
                        val firstRealIndex = lines.indexOfFirst {
                            val name = extractV2rayName(it) ?: ""
                            !isSubscriptionInfoName(name)
                        }.let { if (it >= 0) it else 0 }
                        lines.forEachIndexed { index, config ->
                            val parsedName = extractV2rayName(config) ?: if (cName.isNotBlank()) "$cName ${index + 1}" else "V2Ray Sub ${index + 1}"
                            dao.insertProfile(VpnProfile(name = parsedName, protocol = protocol, serverAddress = config, username = "sub:${cAddr}", password = cPass.ifBlank { cAddr }, isActive = index == firstRealIndex, subName = cName))
                        }
                        withContext(Dispatchers.Main) { showToast("Subscription added (${lines.size} configs)") }
                    } catch (e: Exception) {
                        dao.insertProfile(VpnProfile(name = cName.ifBlank { "V2Ray Sub Link" }, protocol = protocol, serverAddress = cAddr, username = "sub:${cAddr}", password = cPass.ifBlank { cAddr }, isActive = true, subName = cName))
                        withContext(Dispatchers.Main) { showToast("Failed to fetch subscription") }
                    }
                } else {
                    val lines = cAddr.lines().map { it.trim() }.filter { line -> line.isNotBlank() && validPrefixes.any { line.startsWith(it, true) } }
                    if (lines.isEmpty()) { withContext(Dispatchers.Main) { showToast("Invalid configuration!") }; return@launch }
                    lines.forEachIndexed { index, config ->
                        val parsedName = extractV2rayName(config) ?: if (cName.isNotBlank()) (if (lines.size > 1) "$cName ${index + 1}" else cName) else "V2Ray Server"
                        dao.insertProfile(VpnProfile(name = parsedName, protocol = protocol, serverAddress = config, username = "single", password = "", isActive = index == 0))
                    }
                    withContext(Dispatchers.Main) { showToast(if (lines.size == 1) "Config added successfully" else "${lines.size} configs added") }
                }
            } else if (protocol == "WireGuard") {
                try {
                    val parsed = WireGuardConfig.fromConfig(cAddr)
                    parsed.requireValid()
                    val country = if (countryCode != "un") countryCode else resolveHostCountryCode(parsed.endpointHost)
                    val finalName = if (cName.isNotBlank()) cName else parsed.remark
                    dao.insertProfile(
                        VpnProfile(
                            name = finalName,
                            protocol = "WireGuard",
                            serverAddress = parsed.toUri(),
                            username = "single",
                            password = "",
                            isActive = true,
                            countryCode = country
                        )
                    )
                    withContext(Dispatchers.Main) { showToast("Config added successfully") }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showToast(e.message ?: "Invalid WireGuard configuration") }
                }
            } else {
                dao.insertProfile(VpnProfile(name = cName, protocol = protocol, serverAddress = cAddr, username = cUser, password = cPass, isActive = true, countryCode = countryCode))
                withContext(Dispatchers.Main) { showToast("Server added successfully") }
            }
            val newActive = dao.getActiveProfile().firstOrNull()
            if (newActive != null) {
                settingsRepository.setLastServerId(newActive.protocol, newActive.id)
            }
        }
    }

    /**
     * Resolves country code from a hostname or IP address.
     */
    suspend fun resolveHostCountryCode(host: String): String {
        val tld = host.substringAfterLast('.').lowercase(java.util.Locale.US)
        if (tld.length == 2 && BundledFlags.isSupported(tld)) {
            return tld
        }
        return withContext(Dispatchers.IO) {
            try {
                val ip = java.net.InetAddress.getByName(host).hostAddress ?: return@withContext "un"
                val client = buildFreshHttpClient()
                try {
                    val primaryUrls = GeoIpLookup.primaryHostLookupUrls(ip)
                    val primaryResults = coroutineScope {
                        primaryUrls.map { url ->
                            async(Dispatchers.IO) {
                                withTimeoutOrNull(2500L) {
                                    executeIpLookupRequest(client, url)
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }

                    val consensusResult = if (primaryResults.size == 2) {
                        val code0 = primaryResults[0].countryCode.trim().lowercase(java.util.Locale.US)
                        val code1 = primaryResults[1].countryCode.trim().lowercase(java.util.Locale.US)
                        val valid0 = code0.length == 2 && code0 != "un" && code0 != "xx"
                        val valid1 = code1.length == 2 && code1 != "un" && code1 != "xx"
                        if (valid0 && valid1 && code0 == code1) {
                            GeoIpLookup.selectConsensusResult(primaryResults)
                        } else {
                            var tieBreaker: GeoIpResult? = null
                            for (fallbackUrl in GeoIpLookup.fallbackHostLookupUrls(ip)) {
                                val candidate = withTimeoutOrNull(2500L) {
                                    executeIpLookupRequest(client, fallbackUrl)
                                }
                                if (candidate != null) {
                                    tieBreaker = candidate
                                    break
                                }
                            }
                            val pool = if (tieBreaker != null) primaryResults + tieBreaker else primaryResults
                            GeoIpLookup.selectConsensusResult(pool)
                        }
                    } else if (primaryResults.size == 1) {
                        primaryResults.first()
                    } else {
                        var fallbackResult: GeoIpResult? = null
                        for (fallbackUrl in GeoIpLookup.fallbackHostLookupUrls(ip)) {
                            val candidate = withTimeoutOrNull(2500L) {
                                executeIpLookupRequest(client, fallbackUrl)
                            }
                            if (candidate != null) {
                                fallbackResult = candidate
                                break
                            }
                        }
                        fallbackResult
                    }

                    val code = consensusResult?.countryCode?.let { BundledFlags.normalizeCode(it) }
                    if (code != null && code != "un") {
                        code
                    } else {
                        "un"
                    }
                } finally {
                    try { client.connectionPool.evictAll() } catch (_: Throwable) {}
                }
            } catch (_: Exception) {
                "un"
            }
        }
    }

    /**
     * Imports one or more WireGuard configs from raw text (either wireguard:// URIs or .conf text).
     */
    fun importWireGuardConfig(configText: String, nameOverride: String? = null): Boolean {
        val trimmed = configText.trim()
        if (trimmed.isBlank()) return false
        try {
            val configs = if (trimmed.startsWith("[Interface]", ignoreCase = true) || trimmed.contains("\n[Interface]", ignoreCase = true)) {
                listOf(WireGuardConfig.fromConfig(trimmed))
            } else {
                val lines = trimmed.lines().map { it.trim() }.filter { it.startsWith("wireguard://", ignoreCase = true) }
                if (lines.isEmpty() && WireGuardConfig.isWireGuardConfig(trimmed)) {
                    listOf(WireGuardConfig.fromConfig(trimmed))
                } else if (lines.isNotEmpty()) {
                    lines.map { WireGuardConfig.fromConfig(it) }
                } else {
                    return false
                }
            }
            if (configs.isEmpty()) return false

            viewModelScope.launch(Dispatchers.IO) {
                configs.forEachIndexed { index, parsed ->
                    parsed.requireValid()
                    val countryCode = resolveHostCountryCode(parsed.endpointHost)
                    val finalName = if (!nameOverride.isNullOrBlank()) {
                        if (configs.size > 1) "$nameOverride ${index + 1}" else nameOverride
                    } else {
                        parsed.remark
                    }
                    val profile = VpnProfile(
                        name = finalName,
                        protocol = "WireGuard",
                        serverAddress = parsed.toUri(),
                        username = "single",
                        password = "",
                        isActive = index == 0 && activeProfile.value == null,
                        countryCode = countryCode
                    )
                    dao.insertProfile(profile)
                }
            }
            return true
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", LogRedactor.redact("importWireGuardConfig failed: ${e.message}"))
            return false
        }
    }

    /**
     * Imports one or more WireGuard profiles from a file (.conf) or an archive (.zip) URI.
     */
    fun importWireGuardFromUri(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            var importedCount = 0
            try {
                var displayName: String? = null
                runCatching {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) displayName = cursor.getString(idx)
                        }
                    }
                }
                if (displayName.isNullOrBlank()) {
                    displayName = uri.lastPathSegment ?: "wireguard.conf"
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) { showToast("Unable to open file") }
                    return@launch
                }

                val buffered = java.io.BufferedInputStream(inputStream)
                buffered.mark(4)
                val header = ByteArray(4)
                val bytesRead = buffered.read(header)
                buffered.reset()

                val isZip = (bytesRead == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte())
                    || displayName.endsWith(".zip", ignoreCase = true)

                val parsedConfigs = mutableListOf<WireGuardParsedConfig>()

                if (isZip) {
                    val zipStream = java.util.zip.ZipInputStream(buffered)
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val isDir = entry.isDirectory
                        val isMacMeta = name.contains("__MACOSX") || java.io.File(name).name.startsWith(".")
                        if (!isDir && !isMacMeta && (name.endsWith(".conf", ignoreCase = true) || name.endsWith(".txt", ignoreCase = true))) {
                            val baseFileName = java.io.File(name).name.substringBeforeLast('.')
                            val baos = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            var len: Int
                            var total = 0
                            while (zipStream.read(buffer).also { len = it } > 0) {
                                baos.write(buffer, 0, len)
                                total += len
                                if (total > 65536) break
                            }
                            val text = baos.toString("UTF-8").trim()
                            if (WireGuardConfig.isWireGuardConfig(text)) {
                                runCatching {
                                    val parsed = WireGuardConfig.parse(text, defaultRemark = baseFileName)
                                    parsed.requireValid()
                                    parsedConfigs.add(parsed)
                                }
                            }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                    zipStream.close()
                } else {
                    val baseFileName = displayName.substringBeforeLast('.')
                    val text = buffered.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                    if (WireGuardConfig.isWireGuardConfig(text)) {
                        if (text.startsWith("[Interface]", ignoreCase = true) || text.contains("\n[Interface]", ignoreCase = true)) {
                            runCatching {
                                val parsed = WireGuardConfig.parse(text, defaultRemark = baseFileName)
                                parsed.requireValid()
                                parsedConfigs.add(parsed)
                            }
                        } else {
                            val lines = text.lines().map { it.trim() }.filter { it.startsWith("wireguard://", ignoreCase = true) }
                            if (lines.isNotEmpty()) {
                                lines.forEachIndexed { idx, line ->
                                    runCatching {
                                        val overrideName = if (lines.size > 1) "$baseFileName ${idx + 1}" else baseFileName
                                        val parsed = WireGuardConfig.parse(line, defaultRemark = overrideName)
                                        parsed.requireValid()
                                        parsedConfigs.add(parsed)
                                    }
                                }
                            } else {
                                runCatching {
                                    val parsed = WireGuardConfig.parse(text, defaultRemark = baseFileName)
                                    parsed.requireValid()
                                    parsedConfigs.add(parsed)
                                }
                            }
                        }
                    }
                    buffered.close()
                }

                if (parsedConfigs.isEmpty()) {
                    withContext(Dispatchers.Main) { showToast("No valid WireGuard configuration found") }
                    return@launch
                }

                for (parsed in parsedConfigs) {
                    val canonicalUri = parsed.toUri()
                    val isDuplicate = allProfiles.value.any { it.protocol == "WireGuard" && it.serverAddress == canonicalUri }
                    if (!isDuplicate) {
                        val countryCode = resolveHostCountryCode(parsed.endpointHost)
                        val profile = VpnProfile(
                            name = parsed.remark,
                            protocol = "WireGuard",
                            serverAddress = canonicalUri,
                            username = "single",
                            password = "",
                            isActive = (importedCount == 0 && activeProfile.value == null),
                            countryCode = countryCode
                        )
                        dao.insertProfile(profile)
                        importedCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    if (importedCount > 0) {
                        showToast("Imported $importedCount WireGuard profile(s)")
                    } else {
                        showToast("All imported configuration(s) already exist")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", LogRedactor.redact("importWireGuardFromUri failed: ${e.message}"))
                withContext(Dispatchers.Main) {
                    showToast(e.message ?: "Failed to import WireGuard profile(s)")
                }
            }
        }
    }

    /**
     * Saves or updates a manual WireGuard profile from structured form fields.
     */
    fun saveWireGuardProfile(
        name: String,
        endpointHost: String,
        endpointPort: Int,
        secretKey: String,
        publicKey: String,
        localAddress: String = "10.0.0.2/32",
        preSharedKey: String? = null,
        mtu: Int = 1420,
        reserved: String? = null,
        keepAlive: Int = 0,
        dns: String? = null,
        listenPort: Int? = null,
        allowedIps: String? = null,
        editingProfileId: Int? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val addrs = localAddress.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf("10.0.0.2/32") }
                val parsedAllowedIps = allowedIps?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.ifEmpty { listOf("0.0.0.0/0") }
                    ?: listOf("0.0.0.0/0")
                val parsedReserved = reserved?.trim()?.takeIf { it.isNotEmpty() }?.let { WireGuardConfig.parseReserved(it) }
                val parsed = WireGuardParsedConfig(
                    remark = name.trim().ifEmpty { "WireGuard Server" },
                    secretKey = secretKey.trim(),
                    publicKey = publicKey.trim(),
                    preSharedKey = preSharedKey?.trim()?.ifEmpty { null },
                    endpointHost = endpointHost.trim(),
                    endpointPort = endpointPort.coerceIn(1, 65535),
                    localAddresses = addrs,
                    mtu = mtu.coerceIn(1280, 1500),
                    keepAlive = keepAlive.coerceAtLeast(0),
                    reserved = parsedReserved,
                    dns = dns?.trim()?.ifEmpty { null },
                    listenPort = listenPort,
                    allowedIps = parsedAllowedIps
                )
                parsed.requireValid()
                val countryCode = resolveHostCountryCode(parsed.endpointHost)
                val canonicalUri = parsed.toUri()

                if (editingProfileId != null) {
                    val existing = dao.getProfileById(editingProfileId)
                    if (existing != null) {
                        dao.updateProfile(
                            existing.copy(
                                name = parsed.remark,
                                serverAddress = canonicalUri,
                                protocol = "WireGuard",
                                countryCode = if (existing.countryCode != "un") existing.countryCode else countryCode
                            )
                        )
                        return@launch
                    }
                }
                dao.insertProfile(
                    VpnProfile(
                        name = parsed.remark,
                        protocol = "WireGuard",
                        serverAddress = canonicalUri,
                        username = "single",
                        password = "",
                        isActive = activeProfile.value == null,
                        countryCode = countryCode
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", LogRedactor.redact("saveWireGuardProfile failed: ${e.message}"))
                showToast(e.message ?: "Failed to save WireGuard profile")
            }
        }
    }

    private fun extractV2rayName(config: String): String? {
        return try {
            if (config.startsWith("vmess://")) {
                val b64 = config.substringAfter("vmess://")
                val decoded = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                JSONObject(decoded).optString("ps", null).takeIf { !it.isNullOrBlank() }
            } else if (config.contains("#")) {
                java.net.URLDecoder.decode(config.substringAfterLast("#"), "UTF-8").takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) { null }
    }

    fun updateServer(profile: VpnProfile) { viewModelScope.launch(Dispatchers.IO) { dao.updateProfile(profile) } }

    fun selectServer(profileId: Int, context: Context) {
        val currentState = connectionState.value
        if (currentState.isConnectingState || currentState == ConnectionState.DISCONNECTING || currentState == ConnectionState.CANCELING) {
            return
        }
        _pendingRestoredProfile.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isConnected = connectionState.value == ConnectionState.CONNECTED
                val newProfile = dao.getProfileById(profileId)
                if (newProfile == null || isSubscriptionInfoName(newProfile.name)) return@launch
                // Servers that failed their ping test (ping == -1) are unreachable —
                // don't allow them to be selected for a connection.
                if (newProfile?.ping == -1L) {
                    withContext(Dispatchers.Main) {
                        showToast("This server is unreachable right now\nPick another server or retest its ping.")
                    }
                    return@launch
                }
                val currentProfile = activeProfile.value
                if (isConnected) {
                    if (currentProfile?.protocol == newProfile?.protocol) {
                        if (currentProfile?.id == newProfile.id) {
                            return@launch
                        }
                        withContext(Dispatchers.Main) {
                            isIntentionalReconnect = true; VpnStateManager.updateState(ConnectionState.RECONNECTING)
                            context.startService(Intent(context, HybridVpnService::class.java).apply { action = "ACTION_STOP_VPN" })
                            delay(1500)
                        }
                    } else {
                        withContext(Dispatchers.Main) { initiateDisconnect(context) { } }
                        while (connectionState.value != ConnectionState.DISCONNECTED) delay(500)
                        delay(1000)
                    }
                }
                activateProfileAndRemember(newProfile)
                if (isConnected) withContext(Dispatchers.Main) { startVpn(context) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isIntentionalReconnect = false; VpnStateManager.updateState(ConnectionState.ERROR) }
            }
        }
    }

    fun deleteServer(profile: VpnProfile) {
        if (_pendingRestoredProfile.value?.id == profile.id) {
            _pendingRestoredProfile.value = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (settingsRepository.getLastServerId(profile.protocol) == profile.id) {
                settingsRepository.clearLastServerId(profile.protocol)
            }
            dao.deleteProfile(profile)
        }
    }

    fun deleteSubscriptionGroup(subKey: String) {
        stopV2rayPing(subKey)
        if (_pendingRestoredProfile.value?.username == subKey) {
            _pendingRestoredProfile.value = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            val targets = allProfiles.value.filter { it.username == subKey }
            val savedId = settingsRepository.getLastServerId("v2rayNG")
            if (savedId != null && targets.any { it.id == savedId }) {
                settingsRepository.clearLastServerId("v2rayNG")
            }
            val updatedOrder = V2rayTabOrderResolver.removeTabFromSavedOrder(_v2rayTabOrder.value, subKey)
            _v2rayTabOrder.value = updatedOrder
            settingsRepository.setV2rayTabOrder(updatedOrder)
            if (_v2raySelectedTab.value == subKey) {
                _v2raySelectedTab.value = V2rayTabOrderResolver.SINGLE_LINKS_TAB
                settingsRepository.setV2rayLastSelectedTab(V2rayTabOrderResolver.SINGLE_LINKS_TAB)
            }
            targets.forEach { dao.deleteProfile(it) }
        }
    }
    fun renameSubscriptionGroup(subKey: String, newName: String) { viewModelScope.launch(Dispatchers.IO) { allProfiles.value.filter { it.username == subKey }.forEach { dao.updateProfile(it.copy(subName = newName)) } } }

    private fun extractHostPort(config: String): Pair<String, Int>? {
        return try {
            if (config.startsWith("vless://", true) || config.startsWith("trojan://", true)) {
                val uri = android.net.Uri.parse(config.replace("vless://", "http://", true).replace("trojan://", "http://", true))
                Pair(uri.host!!, uri.port)
            } else if (config.startsWith("vmess://", true)) {
                val b64 = config.substringAfter("://")
                val json = JSONObject(String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT)))
                Pair(json.getString("add"), json.getInt("port"))
            } else null
        } catch (e: Exception) { null }
    }

    // Legacy TCP check kept only for diagnostics; real ping must go through the
    // full Xray stack (TLS, REALITY, XHTTP/splithttp, etc.) or we mismatch v2rayNG.
    @Deprecated("Use measureOutboundDelay via Xray core instead")
    private suspend fun tcpPing(host: String, port: Int): Long {
        return withContext(Dispatchers.IO) {
            try {
                val socket = java.net.Socket()
                val start = System.currentTimeMillis()
                socket.connect(java.net.InetSocketAddress(host, port), 2000)
                val time = System.currentTimeMillis() - start
                socket.close()
                time
            } catch (e: Exception) { -1L }
        }
    }

    fun stopV2rayPing(groupName: String = V2rayTabOrderResolver.SINGLE_LINKS_TAB) {
        val previousJob = v2rayPingJobs.remove(groupName)
        val wasActive = previousJob?.isActive == true
        previousJob?.cancel()
        updateV2rayPingProgress { it.clearBatch(groupName) }
        _v2rayPingTabs.value = _v2rayPingTabs.value - groupName
        if (groupName == V2rayTabOrderResolver.SINGLE_LINKS_TAB) {
            _singleLinksPingProgress.value = null
        }
        if (wasActive) {
            _subscriptionToast.tryEmit("Ping stopped")
        }
    }

    /**
     * True v2rayNG-style "Test real delay": builds each profile's full Xray
     * JSON (inbounds stripped internally by MeasureOutboundDelay) and dials
     * through core.Dial. A profile with bad UUID / wrong Reality pbk / bad
     * XHTTP path etc. will correctly return -1 even if the TCP port is open.
     * Matches v2rayNG behavior where impossible configs show -1ms.
     *
     * v2rayNG runs this with limited concurrency (pref_real_ping_concurrency,
     * default 3-5) instead of sequentially — previously each profile blocked
     * up to ~12s, making bulk tests extremely slow. This implementation
     * mirrors that concurrency model.
     */
    fun testV2rayRealDelay(groupName: String) {
        if (v2rayPingJobs[groupName]?.isActive == true) return

        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val currentJob = coroutineContext[Job]
            fun isStillCurrent(): Boolean = isActive && v2rayPingJobs[groupName] === currentJob

            try {
                // Scope to the selected tab: "SingleLinks" pings only non-subscription
                // servers; a sub key (sub:<url>) pings only that group. This avoids
                // grinding through every tab's configs before reaching the one shown.
                val base = allProfiles.value.filter { it.protocol == "v2rayNG" && !isSubscriptionInfoName(it.name) }
                val targets = when (groupName) {
                    V2rayTabOrderResolver.SINGLE_LINKS_TAB -> base.filter { !it.username.startsWith("sub:") }
                    else -> base.filter { it.username == groupName }
                }
                if (targets.isEmpty() || !isStillCurrent()) return@launch

                val targetIds = targets.map { it.id }.toSet()
                updateV2rayPingProgress { it.startBatch(groupName, targetIds) }

                // Mark this tab as pinging so only its UI shows the loading state.
                _v2rayPingTabs.value = _v2rayPingTabs.value + groupName
                if (groupName == V2rayTabOrderResolver.SINGLE_LINKS_TAB) {
                    _singleLinksPingProgress.value = PingProgress(0, targets.size)
                }

                // Reset first so user sees immediate feedback
                for (target in targets) {
                    if (!isStillCurrent()) return@launch
                    ensureActive()
                    dao.updateProfile(target.copy(ping = 0L))
                }

                // v2rayNG default concurrency when not configured
                val completedCounter = AtomicInteger(0)
                val concurrency = 5
                val semaphore = Semaphore(concurrency)

                coroutineScope {
                    targets.map { profile ->
                        async(Dispatchers.IO) {
                            if (!isStillCurrent()) return@async
                            try {
                                semaphore.withPermit {
                                    if (!isStillCurrent()) return@withPermit
                                    val ping = try {
                                        measureProfileDelay(profile)
                                    } catch (ce: CancellationException) {
                                        throw ce
                                    } catch (_: Exception) {
                                        -1L
                                    }
                                    if (!isStillCurrent()) return@withPermit
                                    dao.updateProfile(profile.copy(ping = ping))
                                    if (groupName == V2rayTabOrderResolver.SINGLE_LINKS_TAB && isStillCurrent()) {
                                        _singleLinksPingProgress.value = PingProgress(
                                            completedCounter.incrementAndGet(),
                                            targets.size
                                        )
                                    }
                                }
                            } finally {
                                updateV2rayPingProgress { it.finishProfileInBatch(groupName, profile.id) }
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                updateV2rayPingProgress { it.clearBatch(groupName) }
                if (v2rayPingJobs.remove(groupName, currentJob)) {
                    _v2rayPingTabs.value = _v2rayPingTabs.value - groupName
                    if (groupName == V2rayTabOrderResolver.SINGLE_LINKS_TAB) {
                        _singleLinksPingProgress.value = null
                    }
                }
            }
        }

        v2rayPingJobs[groupName] = job
        job.start()
    }

    /**
     * Toggle sorted-by-test-results for exactly one tab ([groupName]). The tab key
     * is captured at tap time so switching tabs mid-operation can't redirect it.
     */
    fun sortV2rayByTestResults(groupName: String) {
        _sortedV2rayTabs.value =
            if (_sortedV2rayTabs.value.contains(groupName)) _sortedV2rayTabs.value - groupName
            else _sortedV2rayTabs.value + groupName
    }

    /**
     * Delete invalid (-1 ping) configs within exactly one tab ([groupName]). Other
     * tabs are untouched. Info cards are never treated as deletable configs.
     */
    fun deleteInvalidV2rayConfigs(groupName: String) {
        if (_pendingRestoredProfile.value?.ping == -1L) {
            _pendingRestoredProfile.value = null
        }
        viewModelScope.launch(Dispatchers.IO) {
            val base = allProfiles.value.filter {
                it.protocol == "v2rayNG" && !isSubscriptionInfoName(it.name) && it.ping == -1L
            }
            val targets = when (groupName) {
                "SingleLinks" -> base.filter { !it.username.startsWith("sub:") }
                else -> base.filter { it.username == groupName }
            }
            val savedId = settingsRepository.getLastServerId("v2rayNG")
            if (savedId != null && targets.any { it.id == savedId }) {
                settingsRepository.clearLastServerId("v2rayNG")
            }
            targets.forEach { dao.deleteProfile(it) }
        }
    }

    fun testServerPing(profile: VpnProfile) {
        if (isSubscriptionInfoName(profile.name)) return
        val isV2ray = profile.protocol == "v2rayNG"
        if (isV2ray) {
            updateV2rayPingProgress { it.startSingle(profile.id) }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ping = measureProfileDelay(profile)
                dao.updateProfile(profile.copy(ping = ping))
            } finally {
                if (isV2ray) {
                    updateV2rayPingProgress { it.finishSingle(profile.id) }
                }
            }
        }
    }

    fun testSingleV2rayPing(profile: VpnProfile) = testServerPing(profile)

    fun testActiveServerPing() = testPing()

    fun testAllWireGuardPing() {
        viewModelScope.launch(Dispatchers.IO) {
            val targets = allProfiles.value.filter { it.protocol == "WireGuard" }
            if (targets.isEmpty()) return@launch
            targets.forEach { dao.updateProfile(it.copy(ping = 0L)) }
            val concurrency = 5
            val semaphore = Semaphore(concurrency)
            coroutineScope {
                targets.map { profile ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val ping = measureProfileDelay(profile)
                            dao.updateProfile(profile.copy(ping = ping))
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun testAllServersPing(protocol: String = _selectedProtocol.value) {
        if (protocol == "WireGuard") {
            testAllWireGuardPing()
        } else if (protocol == "v2rayNG") {
            testV2rayRealDelay(V2rayTabOrderResolver.SINGLE_LINKS_TAB)
        }
    }

    private fun measureProfileDelay(profile: VpnProfile): Long {
        return try {
            // Dedicated test config: no session DNS module / mux (see
            // XrayConfigGenerator.generatePingConfig). Must run off the main
            // thread; blocks up to ~12s with 2 attempts internally.
            val configJson = dev.rouzbeh.hybridvpn.core.vpn.v2ray.XrayConfigGenerator.generatePingConfig(profile)
            val testUrl = settingsRepository.getPingTestUrl().ifBlank { "https://www.google.com/generate_204" }
            dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2RayManager.measureOutboundDelay(
                configJson,
                testUrl
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.w("DashboardViewModel", LogRedactor.redact("measureProfileDelay failed for ${profile.name}: ${e.message}"))
            -1L
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            android.util.Log.w("DashboardViewModel", LogRedactor.redact("measureProfileDelay throwable for ${profile.name}: ${t.message}"))
            -1L
        }
    }

    /**
     * Refreshes exactly one subscription group (the one matching [groupName]).
     * Only profiles in that group are deleted and re-inserted; other groups,
     * single links, and other protocols are untouched.
     *
     * Protected by an atomic lock and full-screen interaction guard to prevent
     * spamming or concurrent mutations.
     */
    fun updateSubscription(groupName: String) {
        if (!SubscriptionUpdatePolicy.canStartUpdate(_subscriptionUpdateState.value, groupName)) {
            return
        }

        if ((connectionState.value == ConnectionState.CONNECTED || ConnectionLockGuard.isLocked(connectionState.value)) && activeProfile.value?.protocol == "v2rayNG") {
            viewModelScope.launch(Dispatchers.Main) { _subscriptionToast.emit("Please disconnect V2Ray before updating subscription.") }
            return
        }

        if (!subscriptionUpdateLock.compareAndSet(false, true)) {
            return
        }

        val initialProfile = allProfiles.value.firstOrNull { it.username == groupName && it.protocol == "v2rayNG" }
        val initialDisplayName = SubscriptionUpdatePolicy.safeDisplayName(initialProfile?.subName, groupName)

        _subscriptionUpdateState.value = SubscriptionUpdateState(
            phase = SubscriptionUpdatePhase.UPDATING,
            targetGroupName = groupName,
            targetDisplayName = initialDisplayName,
            detailText = "Syncing servers..."
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val startNano = System.nanoTime()
                var updatedCount = 0
                var succeeded = false
                var resolvedDisplayName = initialDisplayName

                try {
                    stopV2rayPing(groupName)

                    val existingProfiles = allProfiles.value.filter { it.username == groupName && it.protocol == "v2rayNG" }
                    if (existingProfiles.isNotEmpty()) {
                        val url = existingProfiles.first().password.trim()
                        val preservedSubName = existingProfiles.first().subName
                        resolvedDisplayName = SubscriptionUpdatePolicy.safeDisplayName(preservedSubName, groupName)

                        if (url.startsWith("http")) {
                            val prevSavedId = settingsRepository.getLastServerId("v2rayNG")
                            val prevSavedProfile = existingProfiles.firstOrNull { it.id == prevSavedId }
                            val wasActive = prevSavedProfile?.isActive == true

                            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                                connectTimeout = SubscriptionUpdatePolicy.CONNECT_TIMEOUT_MS
                                readTimeout = SubscriptionUpdatePolicy.READ_TIMEOUT_MS
                                instanceFollowRedirects = true
                                val ua = settingsRepository.getSubUserAgent().trim()
                                if (ua.isNotEmpty()) setRequestProperty("User-Agent", ua)
                            }

                            val response = try {
                                connection.inputStream.bufferedReader().use { it.readText() }
                            } finally {
                                connection.disconnect()
                            }

                            val decoded = try {
                                String(android.util.Base64.decode(response, android.util.Base64.DEFAULT))
                            } catch (e: Exception) {
                                response
                            }

                            val validPrefixes = listOf("vmess://", "vless://", "trojan://", "v2ray://")
                            val lines = decoded.lines()
                                .map { it.trim() }
                                .filter { line -> line.isNotBlank() && validPrefixes.any { line.startsWith(it, true) } }

                            if (lines.isNotEmpty()) {
                                // Deactivate the group first so a currently-active profile in
                                // it (possible when another protocol is connected) isn't left
                                // dangling after the delete. New rows are inserted below.
                                existingProfiles.forEach { dao.deleteProfile(it) }
                                val firstRealIndex = lines.indexOfFirst {
                                    val name = extractV2rayName(it) ?: ""
                                    !isSubscriptionInfoName(name)
                                }.let { if (it >= 0) it else 0 }

                                lines.forEachIndexed { index, config ->
                                    val parsedName = extractV2rayName(config) ?: "SubLink ${index + 1}"
                                    val initialActive = if (prevSavedProfile != null) false else (index == firstRealIndex)
                                    dao.insertProfile(
                                        VpnProfile(
                                            name = parsedName.trim(),
                                            protocol = "v2rayNG",
                                            serverAddress = config,
                                            username = groupName.trim(),
                                            password = url,
                                            isActive = initialActive,
                                            subName = preservedSubName
                                        )
                                    )
                                }

                                if (prevSavedProfile != null) {
                                    val newProfiles = dao.getAllProfiles().firstOrNull()?.filter { it.username == groupName && it.protocol == "v2rayNG" } ?: emptyList()
                                    val matched = newProfiles.firstOrNull { it.serverAddress == prevSavedProfile.serverAddress }
                                        ?: newProfiles.firstOrNull { it.name == prevSavedProfile.name }
                                    if (matched != null) {
                                        settingsRepository.setLastServerId("v2rayNG", matched.id)
                                        if (wasActive && !ConnectionLockGuard.isLocked(connectionState.value)) {
                                            dao.deactivateAll()
                                            dao.setActiveProfile(matched.id)
                                        }
                                    } else {
                                        settingsRepository.clearLastServerId("v2rayNG")
                                    }
                                }

                                updatedCount = lines.size
                                succeeded = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    android.util.Log.e("DashboardViewModel", "Subscription update failed for group: $groupName", e)
                    succeeded = false
                }

                val elapsedMs = (System.nanoTime() - startNano) / 1_000_000L
                val waitMs = SubscriptionUpdatePolicy.remainingAnimationDelayMs(elapsedMs)
                if (waitMs > 0L) {
                    delay(waitMs)
                }

                if (succeeded) {
                    val countDetail = if (updatedCount == 1) "1 server synced" else "$updatedCount servers synced"
                    _subscriptionUpdateState.value = SubscriptionUpdateState(
                        phase = SubscriptionUpdatePhase.SUCCESS,
                        targetGroupName = groupName,
                        targetDisplayName = resolvedDisplayName,
                        detailText = countDetail
                    )
                    delay(SubscriptionUpdatePolicy.SUCCESS_DISPLAY_MS)
                } else {
                    _subscriptionUpdateState.value = SubscriptionUpdateState(
                        phase = SubscriptionUpdatePhase.FAILED,
                        targetGroupName = groupName,
                        targetDisplayName = resolvedDisplayName,
                        detailText = "Could not refresh subscription"
                    )
                    delay(SubscriptionUpdatePolicy.FAILURE_DISPLAY_MS)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                withContext(NonCancellable) {
                    _subscriptionUpdateState.value = SubscriptionUpdateState()
                    subscriptionUpdateLock.set(false)
                }
            }
        }
    }

    fun selectV2rayTab(tabKey: String) {
        if (tabKey.isNotBlank()) {
            _v2raySelectedTab.value = tabKey
            settingsRepository.setV2rayLastSelectedTab(tabKey)
        }
    }

    fun moveV2rayTab(displayedTabs: List<String>, tabKey: String, delta: Int) {
        val moved = V2rayTabOrderResolver.moveTab(displayedTabs, tabKey, delta)
        if (moved != displayedTabs) {
            val merged = V2rayTabOrderResolver.mergeMovedOrderIntoSaved(_v2rayTabOrder.value, moved)
            _v2rayTabOrder.value = merged
            settingsRepository.setV2rayTabOrder(merged)
        }
    }

    fun exportAllProfilesToJson(onExportReady: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = allProfiles.value.ifEmpty {
                dao.getAllProfiles().firstOrNull() ?: emptyList()
            }
            val rootJson = JSONObject().apply {
                put("app", "HybridNest")
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                val arr = JSONArray()
                profiles.forEach { p ->
                    arr.put(JSONObject().apply {
                        put("name", p.name)
                        put("protocol", p.protocol)
                        put("serverAddress", p.serverAddress)
                        put("username", p.username)
                        put("password", p.password)
                        put("countryCode", p.countryCode)
                        put("subName", p.subName)
                    })
                }
                put("profiles", arr)
            }
            val exportString = rootJson.toString(2)
            withContext(Dispatchers.Main) {
                onExportReady(exportString)
                showToast("Exported ${profiles.size} profiles to clipboard")
            }
        }
    }

    fun restoreProfilesFromBackupJson(rawJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = JSONObject(rawJson)
                val profilesArray = root.optJSONArray("profiles")
                if (profilesArray == null) {
                    withContext(Dispatchers.Main) {
                        showToast("Invalid backup data in clipboard")
                    }
                    return@launch
                }

                val currentProfiles = allProfiles.value.ifEmpty {
                    dao.getAllProfiles().firstOrNull() ?: emptyList()
                }
                val existingKeys = currentProfiles.map {
                    Triple(it.protocol.lowercase(), it.serverAddress.trim(), it.name.trim())
                }.toSet()

                var importedCount = 0
                var skippedCount = 0

                for (i in 0 until profilesArray.length()) {
                    val obj = profilesArray.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    val protocol = obj.optString("protocol").trim()
                    val serverAddress = obj.optString("serverAddress").trim()
                    val username = obj.optString("username", "")
                    val password = obj.optString("password", "")
                    val countryCode = obj.optString("countryCode", "un").ifBlank { "un" }
                    val subName = obj.optString("subName", "")

                    if (name.isBlank() || protocol.isBlank() || serverAddress.isBlank()) {
                        skippedCount++
                        continue
                    }

                    val key = Triple(protocol.lowercase(), serverAddress, name)
                    if (existingKeys.contains(key)) {
                        skippedCount++
                    } else {
                        dao.insertProfile(
                            VpnProfile(
                                name = name,
                                protocol = protocol,
                                serverAddress = serverAddress,
                                username = username,
                                password = password,
                                countryCode = countryCode,
                                subName = subName,
                                isActive = false
                            )
                        )
                        importedCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    showToast("Restored $importedCount profiles ($skippedCount duplicates skipped)")
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", LogRedactor.redact("restoreProfilesFromBackupJson failed: ${e.message}"))
                withContext(Dispatchers.Main) {
                    showToast("Invalid backup data in clipboard")
                }
            }
        }
    }

    fun retryFetchIp() {
        if (connectionState.value == ConnectionState.CONNECTED) {
            fetchCurrentNetworkInfo()
        } else {
            fetchInitialNetworkInfo()
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnectCoordinator.cancel()
        v2rayPingJobs.values.forEach { it.cancel() }
        v2rayPingJobs.clear()
        updateV2rayPingProgress { V2rayPingProgressState() }
    }
}
