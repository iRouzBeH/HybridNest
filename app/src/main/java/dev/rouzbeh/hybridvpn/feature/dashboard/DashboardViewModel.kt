package dev.rouzbeh.hybridvpn.feature.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rouzbeh.hybridvpn.data.local.AppDatabase
import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2RayManager
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
import org.json.JSONObject
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).vpnProfileDao()
    private val settingsRepository = SettingsRepository(application)
    private val dnsSettingsRepository = dev.rouzbeh.hybridvpn.data.repository.DnsSettingsRepository(application)
    private var isIntentionalReconnect = false
    private var isInitialStateEmission = true
    private var logcatJob: Job? = null
    private var networkFetchJob: Job? = null
    private val _isV2raySorted = MutableStateFlow(false)

    val allProfiles: StateFlow<List<VpnProfile>> = dao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProtocol = MutableStateFlow("OpenConnect")
    val selectedProtocol: StateFlow<String> = _selectedProtocol.asStateFlow()

    val filteredProfiles: StateFlow<List<VpnProfile>> = combine(
        allProfiles, _selectedProtocol, _isV2raySorted
    ) { profiles, protocol, isSorted ->
        val filtered = profiles.filter { it.protocol == protocol }
        if (protocol == "v2rayNG" && isSorted) {
            // Keep subscription info cards pinned at top regardless of sort / ping.
            val (infoCards, regular) = filtered.partition { isSubscriptionInfoName(it.name) }
            val sortedRegular = regular.sortedBy { if (it.ping > 0) it.ping else Long.MAX_VALUE }
            infoCards + sortedRegular
        } else if (protocol == "v2rayNG") {
            val (infoCards, regular) = filtered.partition { isSubscriptionInfoName(it.name) }
            infoCards + regular
        } else {
            filtered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile: StateFlow<VpnProfile?> = dao.getActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // -------------------------------------------------------------- DNS mode

    /** Visible DNS servers (built-ins minus hidden + customs), ping applied. */
    private val _dnsServers = MutableStateFlow<List<dev.rouzbeh.hybridvpn.core.vpn.dns.DnsServer>>(emptyList())
    val dnsServers: StateFlow<List<dev.rouzbeh.hybridvpn.core.vpn.dns.DnsServer>> = _dnsServers.asStateFlow()

    /** The currently selected DNS server (falls back to first visible). */
    private val _selectedDnsServer = MutableStateFlow<dev.rouzbeh.hybridvpn.core.vpn.dns.DnsServer?>(null)
    val selectedDnsServer: StateFlow<dev.rouzbeh.hybridvpn.core.vpn.dns.DnsServer?> = _selectedDnsServer.asStateFlow()

    fun refreshDnsServers() {
        viewModelScope.launch(Dispatchers.IO) {
            val servers = dnsSettingsRepository.getServers()
            _dnsServers.value = servers
            _selectedDnsServer.value = dnsSettingsRepository.getSelectedServer()
        }
    }

    fun selectDnsServer(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = _dnsServers.value.firstOrNull { it.id == id }
            // Servers that failed their ping test (-1) are unreachable — mirror
            // the VPN server rule and refuse selection with the same toast.
            if (target?.ping == -1L) {
                showToast("This server is unreachable right now\nPick another server or retest its ping.")
                return@launch
            }
            dnsSettingsRepository.selectServer(id)
            _selectedDnsServer.value = dnsSettingsRepository.getSelectedServer()
        }
    }

    fun addDnsServer(name: String, primary: String, secondary: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = dnsSettingsRepository.addCustomServer(name, primary, secondary)
            result.onSuccess { refreshDnsServers() }
                .onFailure { showToast(it.message ?: "Could not add DNS server") }
        }
    }

    fun updateDnsServer(id: String, name: String, primary: String, secondary: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = dnsSettingsRepository.updateCustomServer(id, name, primary, secondary)
            result.onSuccess { refreshDnsServers() }
                .onFailure { showToast(it.message ?: "Could not update DNS server") }
        }
    }

    fun deleteDnsServer(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dnsSettingsRepository.deleteOrHide(id)
            refreshDnsServers()
        }
    }

    /** Ping a single DNS server via a real UDP DNS query and cache the result. */
    fun testDnsPing(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = _dnsServers.value.firstOrNull { it.id == id } ?: return@launch
            val ping = dev.rouzbeh.hybridvpn.core.vpn.dns.DnsPing.pingUdp(server.primary)
            dnsSettingsRepository.setPing(id, ping)
            _dnsServers.value = dnsSettingsRepository.getServers()
        }
    }

    /** Retest ping for every visible DNS server (bounded concurrency). */
    fun testAllDnsPing() {
        viewModelScope.launch(Dispatchers.IO) {
            val targets = _dnsServers.value
            if (targets.isEmpty()) return@launch
            coroutineScope {
                targets.map { server ->
                    async(Dispatchers.IO) {
                        val ping = dev.rouzbeh.hybridvpn.core.vpn.dns.DnsPing.pingUdp(server.primary)
                        dnsSettingsRepository.setPing(server.id, ping)
                    }
                }.awaitAll()
            }
            _dnsServers.value = dnsSettingsRepository.getServers()
        }
    }

    val connectionState: StateFlow<ConnectionState> = VpnStateManager.connectionState
    val speed: StateFlow<Pair<String, String>> = VpnStateManager.speed
    val networkInfo: StateFlow<NetworkInfo> = VpnStateManager.networkInfo

    /** Live Log now backed by process-scoped LogStore so it survives re-entry. */
    val currentLogs: StateFlow<List<String>> = combine(
        selectedProtocol, LogStore.openConnectLogs, LogStore.v2rayLogs, LogStore.dnsLogs
    ) { protocol, ocLogs, v2Logs, dnsLogs ->
        when (protocol) {
            "OpenConnect" -> ocLogs
            "DNS" -> dnsLogs
            else -> v2Logs
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearLogs(protocol: String) { LogStore.clear(protocol) }

    fun appendLog(protocol: String, log: String) { LogStore.append(protocol, log) }

    private fun startLogcatCapture(protocol: String) {
        logcatJob?.cancel()
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor()
                val cmd = when (protocol) {
                    "v2rayNG" ->
                        "logcat -v time HybridNestVPN:D GoLog:D libv2ray:D Go:D AndroidRuntime:E DEBUG:E libc:E System.err:W *:S"
                    "DNS" ->
                        "logcat -v time HybridNestVPN:D HybridVpnService:D DnsForwarder:D DnsPacket:D AndroidRuntime:E *:S"
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

    private fun showToast(message: String) {
        viewModelScope.launch { _toastMessage.emit(message) }
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

    /**
     * True when the ACTIVE connection is the DNS method. Uses the authoritative
     * detailed-state first (set by the service broadcast), falling back to the
     * selected protocol so the card can be labelled even on a replay where the
     * detailed broadcast hasn't landed yet.
     */
    private fun isDnsActive(): Boolean {
        val ds = VpnStateManager.detailedConnectionStateFlow.value
        return when {
            ds is DetailedState.Connected -> ds.activeProtocol == VpnProtocol.Dns
            else -> _selectedProtocol.value == "DNS"
        }
    }

    // Helpers for pinning subscription info cards (names containing volume/days)
    private fun isSubscriptionInfoName(name: String): Boolean {
        val hasVolume = Regex("""\d+(?:\.\d+)?\s*GB\s*/\s*\d+(?:\.\d+)?\s*GB""", RegexOption.IGNORE_CASE).containsMatchIn(name) ||
                Regex("""\d+(?:\.\d+)?\s*/\s*\d+(?:\.\d+)?\s*GB""", RegexOption.IGNORE_CASE).containsMatchIn(name)
        val hasDays = Regex("""\d+\s*Days?""", RegexOption.IGNORE_CASE).containsMatchIn(name)
        return hasVolume || hasDays
    }

    init {
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
            VpnStateManager.updateNetworkInfo("Unknown", "Detecting...", "un")
        }

        viewModelScope.launch {
            VpnStateManager.vpnLogs.collect { (protocol, msg) -> appendLog(protocol, msg) }
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
                if (current == ConnectionState.CANCELING || current == ConnectionState.DISCONNECTED) {
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
            if (active != null) {
                // Migrate a stale selection from a removed protocol to a
                // still-supported default so the selector lands on a real tab.
                _selectedProtocol.value = when (active.protocol) {
                    "OpenConnect", "v2rayNG", "DNS" -> active.protocol
                    else -> "OpenConnect"
                }
            }
        }

        // DNS mode has no Room profile — seed the server list + selection.
        refreshDnsServers()

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
                if (state.isConnectingState || state == ConnectionState.CONNECTED) {
                    val protocol = activeProfile.value?.protocol ?: _selectedProtocol.value
                    VpnStateManager.startEngine(getApplication(), protocol)
                } else if (state == ConnectionState.DISCONNECTING || state == ConnectionState.CANCELING) {
                    VpnStateManager.stopEngine(getApplication())
                }

                if (state == ConnectionState.CONNECTING) {
                    networkFetchJob?.cancel()
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
                } else if (state.isTerminal) {
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

                        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.IDLE) {
                            if (initialNetworkInfo != null) {
                                val init = initialNetworkInfo!!
                                VpnStateManager.updateNetworkInfo(init.ip, init.location, init.flagCode)
                            } else {
                                fetchInitialNetworkInfo()
                            }
                        }
                    }
                }

                if (isIntentionalReconnect) {
                    if (state == ConnectionState.CONNECTED || state.isTerminal) {
                        isIntentionalReconnect = false
                    } else {
                        VpnStateManager.updateState(ConnectionState.RECONNECTING)
                        return@collect
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

                    if (isInitialStateEmission) {
                        isInitialStateEmission = false
                    }

                    // Reset IP/location to the loading animation immediately so
                    // the card does not keep showing the previous location until
                    // the exit IP is resolved. The VM's fetchCurrentNetworkInfo
                    // also resets to Detecting, but we do it eagerly here so
                    // the UI never has a frame where old data is presented as
                    // if it were the VPN exit. The AnimatedContent in
                    // NetworkInfoCard cross-fades between old and new flag.
                    // Note: fetchCurrentNetworkInfo guards with
                    // connectionState == CONNECTED and session id, so we
                    // invalidate the display synchronously then kick the fetch
                    // after a short settle delay.
                    // Only reset if we still hold the previous public IP (not
                    // already Detecting) to avoid redundant emissions.
                    val curNet = VpnStateManager.networkInfo.value
                    if (curNet.location != "Detecting..." && curNet.ip != "Unknown") {
                        VpnStateManager.updateNetworkInfo("Unknown", "Detecting...", "un")
                    }

                    // Launch network checks in parallel without blocking the flow collection
                    viewModelScope.launch {
                        delay(2000.milliseconds)
                        if (connectionState.value == ConnectionState.CONNECTED) {
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
        _selectedProtocol.value = protocol
        // Do NOT clear logs when switching protocol tabs; logs are per-protocol
        // buffers in LogStore and should persist until a new connection starts.
    }

    fun testPing(ipToPing: String = "8.8.8.8") {
        if (connectionState.value != ConnectionState.CONNECTED) return // CRITICAL FIX: Guard against early execution
        pingJob?.cancel()
        pingJob = viewModelScope.launch(Dispatchers.IO) {
            _pingResult.value = "Testing..."
            if (activeProfile.value?.protocol == "v2rayNG") {
                val rtt = V2RayManager.measureRealPing()
                _pingResult.value = if (rtt >= 0) "$rtt ms" else "Timeout"
                return@launch
            }
            if (isDnsActive()) {
                // DNS mode: measure a real DNS query to the selected resolver
                // through a protect()ed socket so it leaves on the raw network.
                val target = _selectedDnsServer.value?.primary
                val rtt = target?.let { dev.rouzbeh.hybridvpn.core.vpn.dns.DnsPing.pingUdp(it) } ?: -1L
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
                    AppInfo(
                        appName = app.loadLabel(pm).toString(),
                        packageName = app.packageName,
                        icon = app.loadIcon(pm),
                        isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        isSelected = selectedApps.contains(app.packageName)
                    )
                }.sortedWith(compareByDescending<AppInfo> { it.isSelected }.thenBy { it.appName.lowercase() })
                _installedApps.value = appInfoList
                refreshRoutingPendingState()
            } catch (e: Exception) { android.util.Log.e("DashboardViewModel", "Failed to load apps: ${e.message}") }
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

    private var initialNetworkInfo: IpResult? = null
    @Volatile private var ipLookupSessionId = 0L

    fun fetchInitialNetworkInfo() {
        if (connectionState.value.isConnected) return
        val currentLookupId = ++ipLookupSessionId
        networkFetchJob?.cancel()
        networkFetchJob = viewModelScope.launch(Dispatchers.IO) {
            System.setProperty("http.keepAlive", "false")
            // Ordered by reliability: ip-api.com and ipwho.is correctly map 45.93.168.99
            // to NL (verified against browserleaks/ipinfo); ip.sb mis-maps it to TR.
            val endpoints = listOf(
                "https://ip-api.com/json",
                "https://ipwho.is/",
                "https://v4.ifconfig.co/json",
                "https://api.myip.world/json",
                "https://api-ipv4.ip.sb/geoip",
                "https://free.freeipapi.com/api/json"
            )
            var successResult: IpResult? = null

            for (url in endpoints) {
                if (!isActive || currentLookupId != ipLookupSessionId) return@launch
                successResult = withTimeoutOrNull(3500L) {
                    try {
                        val connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 2500; readTimeout = 2500; useCaches = false
                            instanceFollowRedirects = true
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("Connection", "close")
                        }
                        if (connection.responseCode !in 200..299) {
                            null
                        } else {
                            val response = connection.inputStream.bufferedReader().use { it.readText() }
                            parseIpResponse(url, response)
                        }
                    } catch (e: Exception) { null }
                }
                if (successResult != null) break
            }

            if (isActive && currentLookupId == ipLookupSessionId && !connectionState.value.isConnected) {
                if (successResult != null) {
                    initialNetworkInfo = successResult
                    VpnStateManager.updateNetworkInfo(successResult.ip, successResult.location, successResult.flagCode)
                } else {
                    VpnStateManager.updateNetworkInfo("Unknown", "Unavailable", "un")
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
            // Show Detecting only briefly; underlying fetch will retry automatically.
            // If we already have a valid IP, keep displaying it while re-fetching
            // to avoid flicker "Unknown" during a manual/automatic retry.
                // Per user request: on (re)connect the old IP/location must NOT remain
            // visible - the card should revert to the Detecting animation and stay
            // there until the new exit IP is actually resolved. Previously we kept
            // the previous (public) IP to avoid flicker; that caused the
            // "shows previous information again" bug. Always reset to Detecting
            // unconditionally at the start of a post-connect fetch so
            // NetworkInfoCard's showLoading (= isTransitioning || isUnresolved)
            // stays true until the fresh fetch succeeds.
            VpnStateManager.updateNetworkInfo("Unknown", "Detecting...", "un")

            val detailedState = VpnStateManager.detailedConnectionStateFlow.value
            val isV2ray = if (detailedState is DetailedState.Connected) {
                detailedState.activeProtocol == VpnProtocol.V2rayNG
            } else {
                activeProfile.value?.protocol == "v2rayNG"
            }
            val isDns = if (detailedState is DetailedState.Connected) {
                detailedState.activeProtocol == VpnProtocol.Dns
            } else {
                _selectedProtocol.value == "DNS"
            }
            val proxy = if (isV2ray) java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 10809)) else java.net.Proxy.NO_PROXY

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

            // Ordered by reliability (ip-api.com + ipwho.is verified against browserleaks for 45.93.168.99 → NL).
            val endpoints = listOf(
                "https://ip-api.com/json",
                "https://ipwho.is/",
                "https://v4.ifconfig.co/json",
                "https://api.myip.world/json",
                "https://api-ipv4.ip.sb/geoip",
                "https://free.freeipapi.com/api/json"
            )
            var successResult: IpResult? = null

            for (url in endpoints) {
                if (!isActive || currentLookupId != ipLookupSessionId || VpnStateManager.currentSessionId != sessionAtStart) return@launch
                successResult = withTimeoutOrNull(3500L) {
                    try {
                        val parsedUrl = URL(url)
                        val rawConnection = when {
                            isV2ray -> parsedUrl.openConnection(proxy)
                            isDns -> {
                                // DNS mode never tunnels: the IP shown is the
                                // real public IP, so fetch it directly on the
                                // default network (never bound to the VPN).
                                parsedUrl.openConnection(java.net.Proxy.NO_PROXY)
                            }
                            else -> {
                                // AnyConnect: prefer binding to the VPN network
                                // so we report the tunnel exit IP. Give the TUN a brief
                                // moment to register if it isn't up yet.
                                var vpnNet = findVpnNetwork()
                                if (vpnNet == null) { delay(300); vpnNet = findVpnNetwork() }
                                if (vpnNet != null) {
                                    vpnNet.openConnection(parsedUrl, java.net.Proxy.NO_PROXY)
                                } else {
                                    // TUN not yet visible to ConnectivityManager; the
                                    // default network is the VPN in a full-tunnel setup,
                                    // so this still returns the exit IP in most cases.
                                    android.util.Log.w("DashboardViewModel", "vpnNetwork null for $url, falling back to default network")
                                    parsedUrl.openConnection(java.net.Proxy.NO_PROXY)
                                }
                            }
                        }
                        val connection = (rawConnection as java.net.HttpURLConnection).apply {
                            connectTimeout = 2500; readTimeout = 2500; useCaches = false
                            instanceFollowRedirects = true
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("Connection", "close")
                        }
                        if (connection.responseCode !in 200..299) {
                            null
                        } else {
                            val response = connection.inputStream.bufferedReader().use { it.readText() }
                            parseIpResponse(url, response)
                        }
                    } catch (e: Exception) { null }
                }
                if (successResult != null) break
            }

            if (isActive && connectionState.value == ConnectionState.CONNECTED &&
                VpnStateManager.currentSessionId == sessionAtStart && currentLookupId == ipLookupSessionId
            ) {
                if (successResult != null) {
                    VpnStateManager.updateNetworkInfo(successResult.ip, successResult.location, successResult.flagCode)
                } else {
                    VpnStateManager.updateNetworkInfo("Unknown", "VPN exit unavailable", "un")
                    // Auto-retry path for the intermittent "VPN exit unavailable" glitch
                    // (screenshot 2). The single 2s-delayed fetch in CONNECTED collect
                    // is too early when DNS/HTTP hasn't settled after tunnel up.
                    // Schedule 2 background retries with backoff; each re-enters
                    // fetchCurrentNetworkInfo (validates vpnNetwork/proxy + session).
                    val failedSession = sessionAtStart
                    viewModelScope.launch {
                        var delayMs = 3000L
                        repeat(2) {
                            delay(delayMs)
                            delayMs += 3000L
                            if (connectionState.value == ConnectionState.CONNECTED &&
                                VpnStateManager.currentSessionId == failedSession &&
                                VpnStateManager.networkInfo.value.let {
                                    it.ip == "Unknown" || it.location == "VPN exit unavailable" || it.location == "Unavailable"
                                }
                            ) {
                                fetchCurrentNetworkInfo()
                            } else return@launch
                        }
                    }
                }
            }
        }
    }

    private data class IpResult(val ip: String, val location: String, val flagCode: String)

    private fun parseIpResponse(url: String, response: String): IpResult? {
        return try {
            val json = JSONObject(response)
            
            fun cleanCountryName(name: String): String {
                val cleaned = if (name.startsWith("The ", ignoreCase = true)) name.substring(4).trim() else name
                return if (cleaned.isNotBlank()) cleaned else "Unknown"
            }

            when {
                url.contains("ip-api.com") -> {
                    // ip-api returns {"status":"fail"...} when queried without required fields
                    if (json.optString("status", "success") == "fail") return null
                    val ip = json.optString("query", "0.0.0.0")
                    val countryCode = json.optString("countryCode", "un").lowercase()
                    val country = json.optString("country", java.util.Locale("", countryCode).displayCountry)
                    IpResult(ip, cleanCountryName(country), countryCode)
                }
                url.contains("ipwho.is") -> {
                    if (json.optBoolean("success", true) == false) return null
                    val ip = json.optString("ip", "0.0.0.0")
                    val countryCode = json.optString("country_code", "un").lowercase()
                    val country = json.optString("country", java.util.Locale("", countryCode).displayCountry)
                    IpResult(ip, cleanCountryName(country), countryCode)
                }
                url.contains("ip.sb") -> {
                    val ip = json.optString("ip", "0.0.0.0")
                    val countryCode = json.optString("country_code", "un").lowercase()
                    val country = json.optString("country", java.util.Locale("", countryCode).displayCountry)
                    IpResult(ip, cleanCountryName(country), countryCode)
                }
                url.contains("myip.world") -> {
                    val ip = json.optString("ip", "0.0.0.0")
                    val countryCode = json.optString("country_code", "un").lowercase()
                    val country = json.optString("country", java.util.Locale("", countryCode).displayCountry)
                    IpResult(ip, cleanCountryName(country), countryCode)
                }
                url.contains("ifconfig.co") -> {
                    val ip = json.optString("ip", "0.0.0.0")
                    val countryCode = json.optString("country_iso", "un").lowercase()
                    val country = json.optString("country", java.util.Locale("", countryCode).displayCountry)
                    IpResult(ip, cleanCountryName(country), countryCode)
                }
                url.contains("freeipapi.com") -> {
                    val ip = json.optString("ipAddress", "0.0.0.0")
                    val countryCode = json.optString("countryCode", "un").lowercase()
                    val country = json.optString("countryName", java.util.Locale("", countryCode).displayCountry)
                    IpResult(ip, cleanCountryName(country), countryCode)
                }
                else -> null
            }
        } catch (e: Exception) { null }
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
                context.startService(forceStopIntent)

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
                android.util.Log.e("DashboardViewModel", "reconnectWithLatestPolicy failed", e)
                VpnStateManager.updateState(ConnectionState.ERROR)
            }
        }
    }

    fun startVpn(context: Context) {
        val isDns = _selectedProtocol.value == "DNS"
        val currentProfile = activeProfile.value
        // DNS mode has no Room profile; its selection lives in
        // DnsSettingsRepository. All other protocols require a matching active
        // profile before a session can start.
        if (!isDns && (currentProfile == null || currentProfile.protocol != _selectedProtocol.value)) return
        if (isDns && _selectedDnsServer.value == null) {
            showToast("Select a DNS server first")
            return
        }
        routingRefusalReason()?.let { reason ->
            showToast(reason)
            VpnStateManager.updateState(ConnectionState.ERROR)
            return
        }

        viewModelScope.launch {
            try {
                val intent = Intent(context, HybridVpnService::class.java).apply {
                    action = HybridVpnService.ACTION_START_VPN
                    putExtra(
                        HybridVpnService.EXTRA_PROTOCOL,
                        if (isDns) "DNS" else currentProfile!!.protocol
                    )
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
        appContext.startService(intent)
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
        val current = VpnStateManager.connectionState.value
        if (current == ConnectionState.CANCELING || current == ConnectionState.DISCONNECTED) {
            android.util.Log.d("DashboardViewModel", "cancelConnection: already $current, ignoring re-entrant call")
            return
        }
        android.util.Log.d("DashboardViewModel", "cancelConnection from $current via graceful cancel")
        dispatchVpnStop(context)

        viewModelScope.launch {
            try {
                VpnStateManager.updateState(ConnectionState.CANCELING)
                delay(1000)
                // Cross-session safety: a NEWER session may have already started
                // (user hit disconnect then connect quickly). Do not clobber its
                // CONNECTING/CONNECTED state with a stale forced DISCONNECTED.
                val stateAfterDelay = VpnStateManager.connectionState.value
                if (stateAfterDelay == ConnectionState.CONNECTING || stateAfterDelay == ConnectionState.CONNECTED) {
                    android.util.Log.d("DashboardViewModel", "cancelConnection: newer session already $stateAfterDelay; skipping forced DISCONNECTED")
                    return@launch
                }
                VpnStateManager.updateState(ConnectionState.DISCONNECTED)
                VpnStateManager.resetUsage()
                stopUptime()
                _uptime.value = 0L
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
    fun initiateDisconnect(context: Context, onActualStop: (Context) -> Unit = {}) {
        android.util.Log.d("DashboardViewModel", "initiateDisconnect (graceful) from ${VpnStateManager.connectionState.value}")
        dispatchVpnStop(context)
        onActualStop(context)
        stopUptime()
        VpnStateManager.updateState(ConnectionState.DISCONNECTING)
        // No forced DISCONNECTED — the service broadcasts the final
        // terminal state once TUN/FDs are closed. We leave the state in
        // DISCONNECTING so the button/indicator can animate "Disconnecting".
    }

    fun resetState() { VpnStateManager.updateStateFromNative(0) }

    fun addServer(name: String, serverAddress: String, user: String, pass: String, protocol: String, countryCode: String = "un") {
        val cName = name.trim(); val cAddr = serverAddress.trim()
        val cUser = user.trim(); val cPass = pass.trim()
        
        viewModelScope.launch(Dispatchers.IO) {
            dao.deactivateAll()
            if (protocol == "v2rayNG") {
                val validPrefixes = listOf("vmess://", "vless://", "trojan://", "ss://", "ssr://", "v2ray://")
                if (cAddr.startsWith("http://") || cAddr.startsWith("https://")) {
                    try {
                        val connection = URL(cAddr).openConnection().apply { connectTimeout = 10000 }
                        val response = connection.getInputStream().bufferedReader().use { it.readText() }
                        val decoded = try { String(android.util.Base64.decode(response, android.util.Base64.DEFAULT)) } catch (e: Exception) { response }
                        val lines = decoded.lines().map { it.trim() }.filter { line -> line.isNotBlank() && validPrefixes.any { line.startsWith(it, true) } }
                        if (lines.isEmpty()) { withContext(Dispatchers.Main) { showToast("Invalid subscription!") }; return@launch }
                        lines.forEachIndexed { index, config ->
                            val parsedName = extractV2rayName(config) ?: if (cName.isNotBlank()) "$cName ${index + 1}" else "V2Ray Sub ${index + 1}"
                            dao.insertProfile(VpnProfile(name = parsedName, protocol = protocol, serverAddress = config, username = "sub:${cAddr}", password = cPass.ifBlank { cAddr }, isActive = index == 0))
                        }
                    } catch (e: Exception) { dao.insertProfile(VpnProfile(name = cName.ifBlank { "V2Ray Sub Link" }, protocol = protocol, serverAddress = cAddr, username = "sub:${cAddr}", password = cPass.ifBlank { cAddr }, isActive = true)) }
                } else {
                    val lines = cAddr.lines().map { it.trim() }.filter { line -> line.isNotBlank() && validPrefixes.any { line.startsWith(it, true) } }
                    if (lines.isEmpty()) { withContext(Dispatchers.Main) { showToast("Invalid configuration!") }; return@launch }
                    lines.forEachIndexed { index, config ->
                        val parsedName = extractV2rayName(config) ?: if (cName.isNotBlank()) (if (lines.size > 1) "$cName ${index + 1}" else cName) else "V2Ray Server"
                        dao.insertProfile(VpnProfile(name = parsedName, protocol = protocol, serverAddress = config, username = "single", password = "", isActive = index == 0))
                    }
                }
            } else {
                dao.insertProfile(VpnProfile(name = cName, protocol = protocol, serverAddress = cAddr, username = cUser, password = cPass, isActive = true, countryCode = countryCode))
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isConnected = connectionState.value == ConnectionState.CONNECTED
                val newProfile = dao.getProfileById(profileId)
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
                dao.deactivateAll(); dao.setActiveProfile(profileId)
                if (isConnected) withContext(Dispatchers.Main) { startVpn(context) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isIntentionalReconnect = false; VpnStateManager.updateState(ConnectionState.ERROR) }
            }
        }
    }

    fun deleteServer(profile: VpnProfile) { viewModelScope.launch(Dispatchers.IO) { dao.deleteProfile(profile) } }
    fun deleteSubscriptionGroup(subKey: String) { viewModelScope.launch(Dispatchers.IO) { allProfiles.value.filter { it.username == subKey }.forEach { dao.deleteProfile(it) } } }
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
    fun testV2rayRealDelay() {
        viewModelScope.launch(Dispatchers.IO) {
            val targets = allProfiles.value.filter { it.protocol == "v2rayNG" && !isSubscriptionInfoName(it.name) }
            if (targets.isEmpty()) return@launch
            // Reset first so user sees immediate feedback
            targets.forEach { dao.updateProfile(it.copy(ping = 0L)) }

            // v2rayNG default concurrency when not configured
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

    fun sortV2rayByTestResults() { _isV2raySorted.value = !_isV2raySorted.value }
    fun deleteInvalidV2rayConfigs() { viewModelScope.launch(Dispatchers.IO) { allProfiles.value.filter { it.protocol == "v2rayNG" && !isSubscriptionInfoName(it.name) && it.ping == -1L }.forEach { dao.deleteProfile(it) } } }

    fun testSingleV2rayPing(profile: VpnProfile) {
        if (isSubscriptionInfoName(profile.name)) return
        viewModelScope.launch(Dispatchers.IO) {
            val ping = measureProfileDelay(profile)
            dao.updateProfile(profile.copy(ping = ping))
        }
    }

    private fun measureProfileDelay(profile: VpnProfile): Long {
        return try {
            val configJson = dev.rouzbeh.hybridvpn.core.vpn.v2ray.XrayConfigGenerator.generateConfig(profile)
            // Must run off the main thread; blocks up to ~12s with 2 attempts internally.
            dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2RayManager.measureOutboundDelay(
                configJson,
                "https://www.google.com/generate_204"
            )
        } catch (e: Exception) {
            android.util.Log.w("DashboardViewModel", "measureProfileDelay failed for ${profile.name}: ${e.message}")
            -1L
        } catch (t: Throwable) {
            android.util.Log.w("DashboardViewModel", "measureProfileDelay throwable for ${profile.name}: ${t.message}")
            -1L
        }
    }

    fun updateSubscription(groupName: String) {
        if (connectionState.value == ConnectionState.CONNECTED && activeProfile.value?.protocol == "v2rayNG") {
            showToast("Please disconnect V2Ray before updating subscription.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingProfiles = allProfiles.value.filter { it.username == groupName && it.protocol == "v2rayNG" }
                if (existingProfiles.isEmpty()) return@launch
                val url = existingProfiles.first().password.trim()
                if (!url.startsWith("http")) return@launch
                val preservedSubName = existingProfiles.first().subName
                val connection = URL(url).openConnection().apply { connectTimeout = 10000 }
                val response = connection.getInputStream().bufferedReader().use { it.readText() }
                val decoded = try { String(android.util.Base64.decode(response, android.util.Base64.DEFAULT)) } catch (e: Exception) { response }
                val validPrefixes = listOf("vmess://", "vless://", "trojan://", "v2ray://")
                val lines = decoded.lines().map { it.trim() }.filter { line -> line.isNotBlank() && validPrefixes.any { line.startsWith(it, true) } }
                if (lines.isNotEmpty()) {
                    existingProfiles.forEach { dao.deleteProfile(it) }
                    lines.forEachIndexed { index, config ->
                        val parsedName = extractV2rayName(config) ?: "SubLink ${index + 1}"
                        dao.insertProfile(VpnProfile(name = parsedName.trim(), protocol = "v2rayNG", serverAddress = config, username = groupName.trim(), password = url, isActive = index == 0, subName = preservedSubName))
                    }
                    withContext(Dispatchers.Main) { showToast("Subscription Updated Successfully") }
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { showToast("Update Failed") } }
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
    }
}
