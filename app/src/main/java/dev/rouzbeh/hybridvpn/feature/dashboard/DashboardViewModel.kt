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
import com.tim.basevpn.vpn.api.VpnClientApi
import com.tim.basevpn.vpn.api.VpnClientApiFactory
import com.tim.basevpn.vpn.api.VpnConfig
import com.tim.basevpn.vpn.api.VpnStateHolder
import com.tim.basevpn.vpn.api.VpnStateHolders
import com.tim.basevpn.vpn.api.VpnUiState
import com.tim.openvpn.OpenVpnProtocol
import dev.rouzbeh.hybridvpn.core.routing.OpenVpnPolicyGuard
import dev.rouzbeh.hybridvpn.core.utils.LogRedactor
import dev.rouzbeh.hybridvpn.data.local.AppDatabase
import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2RayManager
import dev.rouzbeh.hybridvpn.data.model.AppInfo
import dev.rouzbeh.hybridvpn.data.repository.SettingsRepository
import dev.rouzbeh.hybridvpn.openvpn.IOpenVpnAnchor
import dev.rouzbeh.hybridvpn.openvpn.OpenVpnAnchorService
import dev.rouzbeh.hybridvpn.service.BroadcastStateManager
import dev.rouzbeh.hybridvpn.service.ConnectionState
import dev.rouzbeh.hybridvpn.service.DetailedState
import dev.rouzbeh.hybridvpn.service.HybridVpnService
import dev.rouzbeh.hybridvpn.service.NetworkInfo
import dev.rouzbeh.hybridvpn.service.RoutingPolicyStatus
import dev.rouzbeh.hybridvpn.service.VpnActionBus
import dev.rouzbeh.hybridvpn.service.VpnProtocol
import dev.rouzbeh.hybridvpn.service.VpnStateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).vpnProfileDao()
    private val settingsRepository = SettingsRepository(application)
    private var isIntentionalReconnect = false
    private var isInitialStateEmission = true
    private var logcatJob: Job? = null
    private var networkFetchJob: Job? = null
    private val _isV2raySorted = MutableStateFlow(false)

    private var openVpnClientApi: VpnClientApi? = null
    private var openVpnStateHolder: VpnStateHolder? = null
    private var openVpnScope: CoroutineScope? = null
    private var isOpenVpnInitialized = false
    private var anchorConnection: android.content.ServiceConnection? = null
    private var anchorBinder: dev.rouzbeh.hybridvpn.openvpn.IOpenVpnAnchor? = null
    private var serviceUnavailableSince = 0L
    private var openVpnWatchdogJob: Job? = null

    val allProfiles: StateFlow<List<VpnProfile>> = dao.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProtocol = MutableStateFlow("OpenConnect")
    val selectedProtocol: StateFlow<String> = _selectedProtocol.asStateFlow()

    val filteredProfiles: StateFlow<List<VpnProfile>> = combine(
        allProfiles, _selectedProtocol, _isV2raySorted
    ) { profiles, protocol, isSorted ->
        val filtered = profiles.filter { it.protocol == protocol }
        if (protocol == "v2rayNG" && isSorted) {
            filtered.sortedBy { if (it.ping > 0) it.ping else Long.MAX_VALUE }
        } else {
            filtered
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfile: StateFlow<VpnProfile?> = dao.getActiveProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val connectionState: StateFlow<ConnectionState> = VpnStateManager.connectionState
    val speed: StateFlow<Pair<String, String>> = VpnStateManager.speed
    val networkInfo: StateFlow<NetworkInfo> = VpnStateManager.networkInfo

    private val _openConnectLogs = MutableStateFlow<List<String>>(emptyList())
    private val _openVpnLogs = MutableStateFlow<List<String>>(emptyList())
    private val _v2rayLogs = MutableStateFlow<List<String>>(emptyList())

    val currentLogs: StateFlow<List<String>> = combine(
        selectedProtocol, _openConnectLogs, _openVpnLogs, _v2rayLogs
    ) { protocol, ocLogs, ovpnLogs, v2Logs ->
        when (protocol) {
            "OpenConnect" -> ocLogs
            "OpenVPN" -> ovpnLogs
            else -> v2Logs
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearLogs(protocol: String) {
        when (protocol) {
            "OpenConnect" -> _openConnectLogs.value = emptyList()
            "OpenVPN" -> _openVpnLogs.value = emptyList()
            else -> _v2rayLogs.value = emptyList()
        }
    }

    fun appendLog(protocol: String, log: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        // Redact at the producer: this flow also drives UI display; logcat capture
        // of the same content must never contain secrets (see LogRedactor doc).
        val formattedLog = "[$time] ${LogRedactor.redact(log)}"
        when (protocol) {
            "OpenConnect" -> _openConnectLogs.value = (_openConnectLogs.value + formattedLog).takeLast(500)
            "OpenVPN" -> _openVpnLogs.value = (_openVpnLogs.value + formattedLog).takeLast(500)
            else -> _v2rayLogs.value = (_v2rayLogs.value + formattedLog).takeLast(500)
        }
    }

    private fun startLogcatCapture(protocol: String) {
        logcatJob?.cancel()
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor()
                val cmd = if (protocol == "v2rayNG")
                    "logcat -v time HybridNestVPN:D GoLog:D libv2ray:D Go:D AndroidRuntime:E DEBUG:E libc:E System.err:W *:S"
                else
                    "logcat -v time OpenConnect:D HybridNestJNI:D HybridNestVPN:D VPNService:D AndroidRuntime:E DEBUG:E libc:E *:S"
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

    init {
        VpnStateManager.updateNetworkInfo("Unknown", "Detecting...", "un")
        VpnStateManager.initPersist(application)

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
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val isVpnActive = cm.allNetworks.any { network ->
                val capabilities = cm.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }

            if (!isVpnActive) {
                VpnStateManager.updateStateFromNative(0)
                BroadcastStateManager.broadcastReadyState(application)
            } else {
                val syncIntent = Intent(getApplication(), HybridVpnService::class.java).apply {
                    action = "ACTION_REQUEST_STATE_SYNC"
                }
                getApplication<Application>().startService(syncIntent)
            }

            val active = dao.getActiveProfile().firstOrNull()
            if (active != null) {
                _selectedProtocol.value = active.protocol
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
                if (state.isConnectingState || state == ConnectionState.CONNECTED) {
                    val protocol = activeProfile.value?.protocol ?: _selectedProtocol.value
                    VpnStateManager.startEngine(getApplication(), protocol)
                } else if (state == ConnectionState.DISCONNECTING || state == ConnectionState.CANCELING) {
                    VpnStateManager.stopEngine(getApplication())
                }

                if (state == ConnectionState.CONNECTING) {
                    networkFetchJob?.cancel()
                    _uptime.value = 0L // Force zero during connection phase
                    clearLogs(activeProfile.value?.protocol ?: _selectedProtocol.value)
                    startLogcatCapture(activeProfile.value?.protocol ?: _selectedProtocol.value)
                if (state.isTerminal) {
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
        clearLogs("OpenConnect")
        clearLogs("OpenVPN")
        clearLogs("v2rayNG")
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
            try {
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
            val endpoints = listOf(
                "https://api-ipv4.ip.sb/geoip",
                "https://api.myip.world/json",
                "https://ip-api.com/json",
                "https://v4.ifconfig.co/json",
                "https://free.freeipapi.com/api/json"
            )
            var successResult: IpResult? = null

            for (url in endpoints) {
                if (!isActive || currentLookupId != ipLookupSessionId) return@launch
                successResult = withTimeoutOrNull(3500L) {
                    try {
                        val connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 2500; readTimeout = 2500; useCaches = false
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("Connection", "close")
                        }
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        parseIpResponse(url, response)
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
            VpnStateManager.updateNetworkInfo("Unknown", "Detecting...", "un")

            val isV2ray = activeProfile.value?.protocol == "v2rayNG"
            val proxy = if (isV2ray) java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 10809)) else java.net.Proxy.NO_PROXY

            val endpoints = listOf(
                "https://api-ipv4.ip.sb/geoip",
                "https://api.myip.world/json",
                "https://ip-api.com/json",
                "https://v4.ifconfig.co/json",
                "https://free.freeipapi.com/api/json"
            )
            var successResult: IpResult? = null

            for (url in endpoints) {
                if (!isActive || currentLookupId != ipLookupSessionId || VpnStateManager.currentSessionId != sessionAtStart) return@launch
                successResult = withTimeoutOrNull(3500L) {
                    try {
                        val connection = (URL(url).openConnection(proxy) as java.net.HttpURLConnection).apply {
                            connectTimeout = 2500; readTimeout = 2500; useCaches = false
                            requestMethod = "GET"
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("Connection", "close")
                        }
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        parseIpResponse(url, response)
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
                    VpnStateManager.updateNetworkInfo("Unknown", "Unavailable", "un")
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
                url.contains("ip-api.com") -> {
                    val ip = json.optString("query", "0.0.0.0")
                    val countryCode = json.optString("countryCode", "un").lowercase()
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

    private suspend fun ensureOpenVpnRuntime(): Boolean {
        anchorBinder?.let { 
            if (it.asBinder().isBinderAlive && it.isRuntimeReady) {
                android.util.Log.d("DashboardViewModel", "Anchor binder already alive and ready.")
                return true 
            }
        }

        android.util.Log.d("DashboardViewModel", "Ensuring OpenVPN runtime: binding to anchor...")
        val app = getApplication<Application>()
        val bound = CompletableDeferred<IOpenVpnAnchor?>()
        val conn = object : android.content.ServiceConnection {
            override fun onServiceConnected(n: android.content.ComponentName?, b: android.os.IBinder?) {
                android.util.Log.d("DashboardViewModel", "Anchor service connected.")
                bound.complete(IOpenVpnAnchor.Stub.asInterface(b))
            }
            override fun onNullBinding(n: android.content.ComponentName?) { 
                android.util.Log.e("DashboardViewModel", "Anchor service returned null binding.")
                bound.complete(null) 
            }
            override fun onServiceDisconnected(n: android.content.ComponentName?) { 
                android.util.Log.w("DashboardViewModel", "Anchor service disconnected unexpectedly.")
                anchorBinder = null 
                if (bound.isActive) bound.complete(null) // Fail fast
            }
            override fun onBindingDied(name: android.content.ComponentName?) {
                android.util.Log.e("DashboardViewModel", "Anchor binding died.")
                anchorBinder = null
                if (bound.isActive) bound.complete(null) // Fail fast
                if (VpnStateManager.connectionState.value == ConnectionState.CONNECTED || 
                    VpnStateManager.connectionState.value == ConnectionState.CONNECTING) {
                    VpnStateManager.updateState(ConnectionState.ERROR)
                }
            }
        }

        val intent = Intent(app, OpenVpnAnchorService::class.java)
        // Explicitly start the service to ensure it survives as a sticky/foreground process
        app.startService(intent)
        
        if (!app.bindService(intent, conn, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)) {
            android.util.Log.e("DashboardViewModel", "Failed to bind to Anchor service.")
            return false
        }
        anchorConnection = conn

        val binder = withTimeoutOrNull(10000) { bound.await() } ?: run {
            android.util.Log.e("DashboardViewModel", "Timeout or failure awaiting Anchor binder.")
            return false
        }
        anchorBinder = binder
        
        android.util.Log.d("DashboardViewModel", "Anchor bound. Waiting for socket...")
        return withTimeoutOrNull(10000) {
            var attempts = 0
            while (!binder.isRuntimeReady) {
                attempts++
                if (attempts % 10 == 0) android.util.Log.v("DashboardViewModel", "Still waiting for socket (attempt $attempts)...")
                delay(100)
            }
            android.util.Log.i("DashboardViewModel", "Anchor readiness confirmed (socket exists).")
            true
        } == true
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
                if (currentProfile?.protocol == "OpenVPN") {
                    try { openVpnClientApi?.disconnect() } catch (_: Exception) {}
                    cleanupOpenVpnResources()
                }
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
        val currentProfile = activeProfile.value
        if (currentProfile == null || currentProfile.protocol != _selectedProtocol.value) return
        routingRefusalReason()?.let { reason ->
            showToast(reason)
            VpnStateManager.updateState(ConnectionState.ERROR)
            return
        }

        // OpenVPN per-app fail-safe: the bundled vpnprotocols 3.0.4 library ignores
        // application filters when creating its TUN (bytecode-verified). Refuse
        // BEFORE any state change, anchor startup or service intent — the user gets
        // an explicit explanation instead of a silent unrestricted tunnel.
        if (currentProfile.protocol == VpnProtocol.OpenVPN.title) {
            val policy = settingsRepository.snapshotRoutingPolicy()
            OpenVpnPolicyGuard.refusalReason(policy)?.let { reason ->
                android.util.Log.e("DashboardViewModel", "OpenVPN start refused: $reason")
                appendLog("OpenVPN", "Connection refused: $reason")
                showToast(reason)
                VpnStateManager.updateState(ConnectionState.ERROR)
                return
            }
        }

        viewModelScope.launch {
            try {
                if (currentProfile.protocol == "OpenVPN") {
                    android.util.Log.d("DashboardViewModel", "Starting OpenVPN sequence...")

                    cleanupOpenVpnResources(releaseAnchor = false)
                    
                    VpnStateManager.updateState(ConnectionState.CONNECTING)

                    if (!ensureOpenVpnRuntime()) {
                        appendLog("OpenVPN", "Runtime process failed to start (socket never appeared)")
                        VpnStateManager.updateState(ConnectionState.ERROR)
                        return@launch
                    }

                    val newScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
                    openVpnScope = newScope

                    openVpnClientApi = VpnClientApiFactory.create(
                        context = getApplication(),
                        scope = newScope,
                        protocol = OpenVpnProtocol.descriptor
                    )
                    
                    if (!isOpenVpnInitialized) {
                        isOpenVpnInitialized = true
                        serviceUnavailableSince = 0L
                        newScope.launch {
                            try {
                                openVpnStateHolder = VpnStateHolders.create(getApplication(), OpenVpnProtocol.descriptor)
                                openVpnStateHolder?.state?.collect { uiState ->
                                    android.util.Log.d("DashboardViewModel", "OpenVPN UI State: $uiState")
                                    val mappedState = when (uiState) {
                                        is VpnUiState.Connecting, is VpnUiState.Checking -> ConnectionState.CONNECTING
                                        is VpnUiState.Connected -> ConnectionState.CONNECTED
                                        is VpnUiState.Disconnecting -> ConnectionState.DISCONNECTING
                                        is VpnUiState.ServiceUnavailable -> {
                                            if (serviceUnavailableSince == 0L) serviceUnavailableSince = android.os.SystemClock.elapsedRealtime()
                                            val elapsed = android.os.SystemClock.elapsedRealtime() - serviceUnavailableSince
                                            if (elapsed > 15000) ConnectionState.ERROR else ConnectionState.CONNECTING
                                        }
                                        is VpnUiState.Disconnected -> ConnectionState.DISCONNECTED
                                    }
                                    
                                    if (activeProfile.value?.protocol == "OpenVPN") {
                                        VpnStateManager.updateState(mappedState)
                                        
                                        if (uiState is VpnUiState.Connected) {
                                            openVpnWatchdogJob?.cancel()
                                        }

                                        if (mappedState == ConnectionState.ERROR) {
                                            android.util.Log.e("DashboardViewModel", "OpenVPN fatal state - Force Killing")
                                            forceKillOpenVpnService(getApplication())
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("DashboardViewModel", "State collection failed", e)
                            }
                        }
                    }
                    val cleanAddr = hardenForDpi(currentProfile.serverAddress.trim())
                    openVpnClientApi?.connect(VpnConfig(cleanAddr))
                    startOpenVpnWatchdog()
                }
                
                val intent = Intent(context, HybridVpnService::class.java).apply {
                    action = HybridVpnService.ACTION_START_VPN
                    putExtra(HybridVpnService.EXTRA_PROTOCOL, currentProfile.protocol)
                }
                context.startService(intent)
            } catch (e: Exception) { VpnStateManager.updateState(ConnectionState.ERROR) }
        }
    }

    private fun cleanupOpenVpnResources(releaseAnchor: Boolean = true) {
        android.util.Log.d("DashboardViewModel", "Cleaning up OpenVPN resources (releaseAnchor=$releaseAnchor)...")
        openVpnWatchdogJob?.cancel()
        openVpnWatchdogJob = null
        openVpnScope?.cancel()
        openVpnScope = null
        openVpnClientApi = null
        openVpnStateHolder = null
        isOpenVpnInitialized = false
        serviceUnavailableSince = 0L

        if (releaseAnchor) {
            anchorConnection?.let {
                try {
                    getApplication<Application>().unbindService(it)
                } catch (e: Exception) {
                    android.util.Log.w("DashboardViewModel", "Failed to unbind anchor connection", e)
                }
            }
            anchorConnection = null
            anchorBinder = null
        }
    }

    private fun startOpenVpnWatchdog() {
        openVpnWatchdogJob?.cancel()
        openVpnWatchdogJob = viewModelScope.launch {
            android.util.Log.d("DashboardViewModel", "OpenVPN Watchdog started (30s)")
            delay(30000)
            val currentState = VpnStateManager.connectionState.value
            if (currentState != ConnectionState.CONNECTED) {
                android.util.Log.w("DashboardViewModel", "OpenVPN Watchdog triggered. Force stopping.")
                appendLog("OpenVPN", "Connection timed out (Watchdog 30s). Force stopping.")
                forceKillOpenVpnService(getApplication())
            }
        }
    }

    private fun forceKillOpenVpnService(context: Context) {
        viewModelScope.launch {
            try {
                android.util.Log.d("DashboardViewModel", "forceKillOpenVpnService initiated")
                try { openVpnClientApi?.disconnect() } catch (e: Exception) {}
                cleanupOpenVpnResources()
                // EXPLICIT KILL: Ensure the native core retry loop is terminated
                val stopIntent = Intent().setClassName("dev.rouzbeh.hybridvpn", "com.tim.openvpn.service.OpenVPNService")
                context.stopService(stopIntent)
                VpnStateManager.updateState(ConnectionState.ERROR)
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Failed to force kill OpenVPN", e)
            }
        }
    }

    fun cancelConnection(context: Context) {
        val current = VpnStateManager.connectionState.value
        if (current == ConnectionState.CANCELING || current == ConnectionState.DISCONNECTED) {
            android.util.Log.d("DashboardViewModel", "cancelConnection: already $current, ignoring re-entrant call")
            return
        }
        viewModelScope.launch {
            try {
                VpnStateManager.updateState(ConnectionState.CANCELING)
                val forceStopIntent = Intent(context, HybridVpnService::class.java).apply { action = "ACTION_FORCE_STOP" }
                context.startService(forceStopIntent)
                try { 
                    openVpnClientApi?.disconnect() 
                    cleanupOpenVpnResources()
                    val stopOpenVpnIntent = Intent().setClassName("dev.rouzbeh.hybridvpn", "com.tim.openvpn.service.OpenVPNService")
                    context.stopService(stopOpenVpnIntent)
                } catch (e: Exception) {} 
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

    fun initiateDisconnect(context: Context, onActualStop: (Context) -> Unit) {
        viewModelScope.launch {
            try {
                VpnStateManager.updateState(ConnectionState.DISCONNECTING)
                delay(1000)
                try { 
                    openVpnClientApi?.disconnect() 
                    cleanupOpenVpnResources()
                    val stopOpenVpnIntent = Intent().setClassName("dev.rouzbeh.hybridvpn", "com.tim.openvpn.service.OpenVPNService")
                    context.stopService(stopOpenVpnIntent)
                } catch (e: Exception) {} 
                val stopIntent = Intent(context, HybridVpnService::class.java).apply { action = "ACTION_STOP_VPN" }
                context.startService(stopIntent)
                onActualStop(context)
                stopUptime()
            } catch (e: Exception) { VpnStateManager.updateState(ConnectionState.ERROR) }
        }
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

    fun testV2rayRealDelay() {
        viewModelScope.launch(Dispatchers.IO) {
            allProfiles.value.filter { it.protocol == "v2rayNG" }.forEach { profile ->
                val hostPort = extractHostPort(profile.serverAddress)
                val ping = if (hostPort != null && hostPort.second > 0) tcpPing(hostPort.first, hostPort.second) else -1L
                dao.updateProfile(profile.copy(ping = ping))
            }
        }
    }

    fun sortV2rayByTestResults() { _isV2raySorted.value = !_isV2raySorted.value }
    fun deleteInvalidV2rayConfigs() { viewModelScope.launch(Dispatchers.IO) { allProfiles.value.filter { it.protocol == "v2rayNG" && it.ping == -1L }.forEach { dao.deleteProfile(it) } } }
    fun testSingleV2rayPing(profile: VpnProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            val hostPort = extractHostPort(profile.serverAddress)
            val ping = if (hostPort != null && hostPort.second > 0) tcpPing(hostPort.first, hostPort.second) else -1L
            dao.updateProfile(profile.copy(ping = ping))
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
        cleanupOpenVpnResources(releaseAnchor = true)
    }

    private fun hardenForDpi(ovpn: String): String = buildString {
        val overridden = listOf("resolv-retry", "connect-timeout", "server-poll-timeout", "connect-retry", "connect-retry-max", "hand-window", "ping", "ping-restart", "ping-exit", "inactive")
        ovpn.lineSequence().filterNot { line -> overridden.any { line.trimStart().startsWith(it) } }.forEach { appendLine(it) }
        appendLine("\nresolv-retry 4\nconnect-timeout 8\nconnect-retry 2\nconnect-retry-max 2\nhand-window 20\nping 10\nping-restart 30")
    }
}
