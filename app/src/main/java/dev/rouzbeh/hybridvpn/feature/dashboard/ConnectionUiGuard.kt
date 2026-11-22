package dev.rouzbeh.hybridvpn.feature.dashboard

import dev.rouzbeh.hybridvpn.service.ConnectionState
import dev.rouzbeh.hybridvpn.service.HybridVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class LockActionOutcome {
    ALLOW,
    SHOW_LOCK_WARNING,
    IGNORE_WHILE_DISCONNECTING
}

@Volatile
private var _hybridVpnServiceRunning: Boolean = false

var HybridVpnService.Companion.isRunning: Boolean
    get() = _hybridVpnServiceRunning
    set(value) {
        _hybridVpnServiceRunning = value
    }

object ConnectionLockGuard {
    fun evaluateControlTap(state: ConnectionState): LockActionOutcome {
        return when (state) {
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING -> LockActionOutcome.SHOW_LOCK_WARNING

            ConnectionState.DISCONNECTING,
            ConnectionState.CANCELING -> LockActionOutcome.IGNORE_WHILE_DISCONNECTING

            ConnectionState.DISCONNECTED,
            ConnectionState.IDLE,
            ConnectionState.CANCELLED,
            ConnectionState.ERROR,
            ConnectionState.FAILED -> LockActionOutcome.ALLOW
        }
    }

    fun shouldAutoDismissLockFeedback(state: ConnectionState): Boolean = state.isTerminal

    fun isDisconnectingAnimationActive(state: ConnectionState): Boolean =
        state == ConnectionState.DISCONNECTING || state == ConnectionState.CANCELING

    fun shouldShowLockDialog(state: ConnectionState, isDialogAlreadyVisible: Boolean): Boolean {
        return evaluateControlTap(state) == LockActionOutcome.SHOW_LOCK_WARNING && !isDialogAlreadyVisible
    }

    fun shouldShowLockToast(
        state: ConnectionState,
        isDialogVisible: Boolean,
        hasActiveLockToast: Boolean
    ): Boolean {
        return evaluateControlTap(state) == LockActionOutcome.SHOW_LOCK_WARNING &&
                !isDialogVisible &&
                !hasActiveLockToast
    }
}

object LockWarningDeduplicator {
    fun shouldShowDialog(state: ConnectionState, isDialogAlreadyVisible: Boolean): Boolean =
        ConnectionLockGuard.shouldShowLockDialog(state, isDialogAlreadyVisible)

    fun shouldShowToast(
        state: ConnectionState,
        isDialogVisible: Boolean,
        hasActiveLockToast: Boolean
    ): Boolean = ConnectionLockGuard.shouldShowLockToast(state, isDialogVisible, hasActiveLockToast)
}

class DisconnectCoordinator(
    private val scope: CoroutineScope,
    private val stateFlow: StateFlow<ConnectionState>,
    private val setState: (ConnectionState) -> Unit,
    private val beginIntentionalCancel: () -> Unit,
    private val isServiceRunning: () -> Boolean,
    private val dispatchStopSignal: () -> Unit,
    private val teardownFlow: Flow<*>,
    private val timeoutMs: Long = DEFAULT_DISCONNECT_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_DISCONNECT_TIMEOUT_MS: Long = 4_000L
    }

    private var watchdogJob: Job? = null

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            val result = withTimeoutOrNull(timeoutMs) {
                merge(
                    stateFlow.filter { it.isTerminal }.map { "terminal" },
                    teardownFlow.map { "teardown" }
                ).first()
            }
            val currentState = stateFlow.value
            if (currentState == ConnectionState.DISCONNECTING || currentState == ConnectionState.CANCELING) {
                if (result == null || !isServiceRunning()) {
                    setState(ConnectionState.DISCONNECTED)
                } else if (result == "teardown") {
                    delay(150L)
                    if (stateFlow.value == ConnectionState.DISCONNECTING || stateFlow.value == ConnectionState.CANCELING) {
                        setState(ConnectionState.DISCONNECTED)
                    }
                } else {
                    setState(ConnectionState.DISCONNECTED)
                }
            }
        }
    }

    fun requestDisconnect() {
        val current = stateFlow.value
        if (current.isTerminal) {
            cancelWatchdog()
            if (current != ConnectionState.DISCONNECTED) {
                setState(ConnectionState.DISCONNECTED)
            }
            return
        }

        if (current == ConnectionState.DISCONNECTING || current == ConnectionState.CANCELING) {
            if (!isServiceRunning()) {
                cancelWatchdog()
                setState(ConnectionState.DISCONNECTED)
                return
            }
            if (watchdogJob?.isActive == true) {
                return
            }
            startWatchdog()
            return
        }

        if (!isServiceRunning()) {
            cancelWatchdog()
            setState(ConnectionState.DISCONNECTED)
            return
        }

        // Live CONNECTING or CONNECTED disconnect:
        if (current == ConnectionState.CONNECTING || current == ConnectionState.RECONNECTING) {
            beginIntentionalCancel()
            setState(ConnectionState.CANCELING)
        } else {
            setState(ConnectionState.DISCONNECTING)
        }

        startWatchdog()

        runCatching {
            dispatchStopSignal()
        }.onFailure {
            if (!isServiceRunning()) {
                cancelWatchdog()
                setState(ConnectionState.DISCONNECTED)
            }
        }
    }

    fun cancelWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }
}
