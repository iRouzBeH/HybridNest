package dev.rouzbeh.hybridvpn.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    CANCELING,
    ERROR,
    IDLE,
    FAILED,
    CANCELLED;

    val isConnected: Boolean
        get() = this == CONNECTED

    val isConnectingState: Boolean
        get() = this == CONNECTING || this == RECONNECTING

    val isTerminal: Boolean
        get() = this == DISCONNECTED || this == IDLE || this == ERROR || this == FAILED || this == CANCELLED
}

enum class VpnProtocol(val title: String) {
    OpenConnect("OpenConnect"),
    OpenVPN("OpenVPN"),
    V2rayNG("v2rayNG")
}

sealed class DetailedState {
    object Ready : DetailedState()
    data class Connected(val activeProtocol: VpnProtocol) : DetailedState()
}

data class NetworkInfo(
    val ip: String = "0.0.0.0",
    val location: String = "Detecting...",
    val flagCode: String = "un"
)

object VpnStateManager {
    val vpnLogs =
        kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1000)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _detailedConnectionState = MutableStateFlow<DetailedState>(DetailedState.Ready)
    val detailedConnectionStateFlow: StateFlow<DetailedState> =
        _detailedConnectionState.asStateFlow()

    private val _selectionLocked = MutableStateFlow(false)
    val selectionLocked: StateFlow<Boolean> = _selectionLocked.asStateFlow()

    private val _networkInfo = MutableStateFlow(NetworkInfo())
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo.asStateFlow()

    /**
     * True when the tunnel is up but the OPTIONAL public connectivity check did
     * not fully verify (DNS/HTTP failure or inconclusive). NEVER fails the engine;
     * surfaces degraded diagnostics so the UI can warn without disconnecting.
     * Cleared on every transition out of CONNECTED and on new sessions.
     */
    private val _connectivityDegraded = MutableStateFlow(false)
    val connectivityDegraded: StateFlow<Boolean> = _connectivityDegraded.asStateFlow()

    /**
     * Set while a user/application-initiated cancellation is in progress for the
     * current session. While true, a native ERROR report is re-interpreted as the
     * expected result of that cancellation (OpenConnect mainloop returns -4 on
     * cancel) instead of a spurious engine failure. Consumed once.
     */
    @Volatile
    var intentionalCancelInFlight: Boolean = false
        private set

    private val _speed = MutableStateFlow(Pair("0 B/s", "0 B/s"))
    val speed: StateFlow<Pair<String, String>> = _speed.asStateFlow()

    private val _rawSpeed = MutableStateFlow(Pair(0L, 0L))
    val rawSpeed: StateFlow<Pair<Long, Long>> = _rawSpeed.asStateFlow()

    private var startRx = -1L
    private var startTx = -1L
    var connectionStartTime: Long = 0L
        private set

    private var sharedPrefs: android.content.SharedPreferences? = null

    private val engineScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private var speedJob: kotlinx.coroutines.Job? = null

    private fun formatBytesStr(bytes: Long): String {
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

    @Volatile
    private var speedGeneration = 0L

    fun startEngine(context: android.content.Context, protocol: String) {
        if (speedJob != null && speedJob?.isActive == true) {
            // Already running, no need to restart and reset usage
            return
        }
        val generation = ++speedGeneration
        speedJob?.cancel()
        speedJob = null
        resetUsage()

        speedJob = engineScope.launch {
            val myUid = android.os.Process.myUid()
            val uidRx = android.net.TrafficStats.getUidRxBytes(myUid)
            val uidTx = android.net.TrafficStats.getUidTxBytes(myUid)
            val useUidCounters = uidRx != android.net.TrafficStats.UNSUPPORTED.toLong() &&
                uidTx != android.net.TrafficStats.UNSUPPORTED.toLong()
            var baselineRx = if (useUidCounters) uidRx else android.net.TrafficStats.getTotalRxBytes()
            var baselineTx = if (useUidCounters) uidTx else android.net.TrafficStats.getTotalTxBytes()
            var prevRx = baselineRx
            var prevTx = baselineTx
            startRx = baselineRx
            startTx = baselineTx

            try {
                while (kotlinx.coroutines.currentCoroutineContext().isActive && generation == speedGeneration) {
                    val currentRx = if (useUidCounters) {
                        android.net.TrafficStats.getUidRxBytes(myUid)
                    } else {
                        android.net.TrafficStats.getTotalRxBytes()
                    }
                    val currentTx = if (useUidCounters) {
                        android.net.TrafficStats.getUidTxBytes(myUid)
                    } else {
                        android.net.TrafficStats.getTotalTxBytes()
                    }

                    val deltaRx = currentRx - prevRx
                    val deltaTx = currentTx - prevTx
                    // Android counters can reset across a network/interface change or overflow.
                    if (currentRx < baselineRx || deltaRx < 0 || deltaRx > 10_000_000_000L) {
                        baselineRx = currentRx
                    }
                    if (currentTx < baselineTx || deltaTx < 0 || deltaTx > 10_000_000_000L) {
                        baselineTx = currentTx
                    }
                    prevRx = currentRx
                    prevTx = currentTx

                    val rxVolume = (currentRx - baselineRx).coerceAtLeast(0L)
                    val txVolume = (currentTx - baselineTx).coerceAtLeast(0L)
                    updateSpeed(
                        formatBytesStr(rxVolume), formatBytesStr(txVolume), rxVolume, txVolume
                    )
                    kotlinx.coroutines.delay(1000)
                }
            } finally {
                if (generation == speedGeneration) {
                    resetUsage()
                }
            }
        }
    }

    fun stopEngine(context: android.content.Context) {
        speedGeneration++
        speedJob?.cancel()
        speedJob = null
        resetUsage()
    }

    fun initPersist(context: android.content.Context) {
        if (sharedPrefs == null) {
            sharedPrefs = context.getSharedPreferences(
                "hybridvpn_prefs",
                android.content.Context.MODE_PRIVATE
            )
        }
        // Always refresh cached values from disk so a recreated ViewModel/Service
        // sees the persisted START_TIME even when the singleton already has a
        // SharedPreferences instance (process survived while Activity died).
        connectionStartTime = sharedPrefs?.getLong("START_TIME", 0L) ?: 0L
        startRx = sharedPrefs?.getLong("START_RX", -1L) ?: -1L
        startTx = sharedPrefs?.getLong("START_TX", -1L) ?: -1L
    }

    fun getSavedState(context: android.content.Context): Pair<ConnectionState, Long> {
        val prefs =
            context.getSharedPreferences("hybridvpn_prefs", android.content.Context.MODE_PRIVATE)
        val startTime = prefs.getLong("START_TIME", 0L)
        // We only care about start time for now, but returning ConnectionState for future-proofing
        return Pair(_connectionState.value, startTime)
    }

    fun updateState(newState: ConnectionState) {
        _connectionState.value = newState
        applyCommonStateSideEffects(newState, isNative = false)
    }

    fun updateSelectionLockedState(locked: Boolean) {
        _selectionLocked.value = locked
    }

    /** Marks an intentional user/app cancellation; affects the next native ERROR report. */
    fun beginIntentionalCancel() {
        intentionalCancelInFlight = true
    }

    /** Clears the intentional-cancel flag (new session start or teardown done). */
    fun clearIntentionalCancel() {
        intentionalCancelInFlight = false
    }

    fun setConnectivityDegraded(degraded: Boolean) {
        _connectivityDegraded.value = degraded
    }

    @Volatile
    var currentSessionId: Long = 0L
        private set

    fun setCurrentSessionId(id: Long) {
        currentSessionId = id
    }

    private const val TAG = "VpnStateManager"

    private fun logDebug(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            // Ignored in unit test environment
        }
    }

    @JvmStatic
    fun updateStateFromNativeForSession(stateCode: Int, forSessionId: Long) {
        if (forSessionId != 0L && currentSessionId != 0L && forSessionId != currentSessionId) {
            logDebug(
                TAG,
                "Native state update ($stateCode) for superseded session $forSessionId ignored (currentSessionId=$currentSessionId)"
            )
            return
        }
        updateStateFromNative(stateCode)
    }

    @JvmStatic
    fun updateStateFromNative(stateCode: Int) {
        val newState = when (stateCode) {
            0 -> ConnectionState.DISCONNECTED
            1 -> ConnectionState.CONNECTING
            2 -> ConnectionState.CONNECTED
            3 -> ConnectionState.ERROR
            else -> ConnectionState.DISCONNECTED
        }

        // Avoid resetting state if it's identical (e.g. initial DISCONNECTED on app start)
        if (_connectionState.value == newState && newState == ConnectionState.DISCONNECTED) {
            return
        }

        val effectiveState = if (newState == ConnectionState.ERROR && intentionalCancelInFlight) {
            logDebug(
                TAG,
                "Native ERROR suppressed as expected cancellation (intentionalCancelInFlight=true)"
            )
            intentionalCancelInFlight = false
            ConnectionState.CANCELLED
        } else if (newState == ConnectionState.ERROR) {
            ConnectionState.FAILED
        } else {
            newState
        }

        _connectionState.value = effectiveState
        applyCommonStateSideEffects(effectiveState, isNative = true)
    }

    /**
     * Shared side effects for both state-entry points so JVM state transitions and
     * JNI state transitions stay consistent.
     */
    private fun applyCommonStateSideEffects(state: ConnectionState, isNative: Boolean) {
        if (state == ConnectionState.CONNECTED) {
            // Persist the connection start instant exactly once per session.
            // This is the authoritative fix for the uptime-reset-on-re-entry bug:
            // OpenConnect's native path calls updateStateFromNative(2) without
            // ever broadcasting DetailedState, so connectionStartTime would remain
            // 0 and the ViewModel would compute uptime = 0 on replay.
            val prefs = sharedPrefs
            if (prefs != null) {
                val existing = prefs.getLong("START_TIME", 0L)
                if (existing == 0L) {
                    val now = System.currentTimeMillis()
                    connectionStartTime = now
                    prefs.edit().putLong("START_TIME", now).apply()
                } else {
                    connectionStartTime = existing
                }
            } else {
                if (connectionStartTime == 0L) connectionStartTime = System.currentTimeMillis()
            }
            _connectivityDegraded.value = false
            // Do NOT reset usage on CONNECTED.
            return
        }

        if (state == ConnectionState.CONNECTING) {
            connectionStartTime = 0L
            sharedPrefs?.edit()?.putLong("START_TIME", 0L)?.apply()
            _connectivityDegraded.value = false
            resetUsage()
        }

        if (state.isTerminal) {
            connectionStartTime = 0L
            sharedPrefs?.edit()?.putLong("START_TIME", 0L)?.apply()
            _selectionLocked.value = false
            _connectivityDegraded.value = false
            resetUsage()
        }
    }

    @JvmStatic
    fun updateNetworkInfo(ip: String, location: String, flagCode: String = "un") {
        _networkInfo.value = NetworkInfo(ip, location, flagCode)
    }

    @JvmStatic
    fun updateIp(ip: String) {
        _networkInfo.value = _networkInfo.value.copy(ip = ip)
    }

    @JvmStatic
    fun updateSpeed(down: String, up: String, downRaw: Long, upRaw: Long) {
        _speed.value = Pair(down, up)
        _rawSpeed.value = Pair(downRaw, upRaw)
    }

    fun getStartRx() = startRx
    fun getStartTx() = startTx

    fun setStartRx(value: Long) {
        startRx = value
    }

    fun setStartTx(value: Long) {
        startTx = value
    }

    fun resetUsage() {
        startRx = -1L
        startTx = -1L
        _speed.value = Pair("0 B/s", "0 B/s")
        _rawSpeed.value = Pair(0L, 0L)
    }

    fun updateDetailedState(detailedState: DetailedState) {
        _detailedConnectionState.value = detailedState
        if (detailedState is DetailedState.Connected) {
            // Ensure SharedPreferences handle is available (e.g. ViewModel was
            // destroyed and recreated after process stayed alive; initPersist may
            // not have been called on the new instance path).
            if (sharedPrefs == null) {
                // Fall back to reading without caching handle if no context; else
                // this will be retried via the next broadcast with a context.
                // Prefer no-op persistence over crash.
            }
            val existingTime = sharedPrefs?.getLong("START_TIME", 0L) ?: 0L
            if (existingTime == 0L) {
                val currentTime = System.currentTimeMillis()
                connectionStartTime = currentTime
                sharedPrefs?.edit()?.putLong("START_TIME", currentTime)?.apply()
            } else {
                connectionStartTime = existingTime
            }
        }
    }

    /**
     * Refresh the in-memory connectionStartTime from persisted SharedPreferences.
     * Used on ViewModel recreation so the uptime timer can resume without a new
     * CONNECTED broadcast.
     */
    fun refreshPersistedStartTime(context: Context) {
        try {
            if (sharedPrefs == null) initPersist(context)
            val t = sharedPrefs?.getLong("START_TIME", 0L) ?: 0L
            if (t != 0L) connectionStartTime = t
        } catch (_: Throwable) {}
    }
}

object BroadcastStateManager {
    fun broadcastConnectedStateDetailed(context: Context, activeProtocol: VpnProtocol) {
        VpnStateManager.updateDetailedState(DetailedState.Connected(activeProtocol))
    }

    fun broadcastReadyState(context: Context) {
        VpnStateManager.updateDetailedState(DetailedState.Ready)
    }
}
