package dev.rouzbeh.hybridvpn.feature.dashboard

import dev.rouzbeh.hybridvpn.service.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class LockActionOutcome {
    ALLOW,
    SHOW_LOCK_WARNING,
    IGNORE_WHILE_DISCONNECTING
}

enum class ServerSelectOutcome {
    ALLOW_SELECT,
    ALLOW_RECONNECT,
    ALREADY_ACTIVE_NOOP,
    SHOW_LOCK_WARNING,
    IGNORE_WHILE_DISCONNECTING
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

    fun evaluateServerSelectTap(
        state: ConnectionState,
        isSameProtocol: Boolean,
        isSameProfile: Boolean
    ): ServerSelectOutcome {
        return when (state) {
            ConnectionState.CONNECTED -> {
                when {
                    isSameProtocol && isSameProfile -> ServerSelectOutcome.ALREADY_ACTIVE_NOOP
                    isSameProtocol -> ServerSelectOutcome.ALLOW_RECONNECT
                    else -> ServerSelectOutcome.SHOW_LOCK_WARNING
                }
            }

            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING -> ServerSelectOutcome.SHOW_LOCK_WARNING

            ConnectionState.DISCONNECTING,
            ConnectionState.CANCELING -> ServerSelectOutcome.IGNORE_WHILE_DISCONNECTING

            ConnectionState.DISCONNECTED,
            ConnectionState.IDLE,
            ConnectionState.CANCELLED,
            ConnectionState.ERROR,
            ConnectionState.FAILED -> ServerSelectOutcome.ALLOW_SELECT
        }
    }

    fun isLocked(state: ConnectionState): Boolean =
        evaluateControlTap(state) == LockActionOutcome.SHOW_LOCK_WARNING

    fun shouldAutoDismissLockFeedback(state: ConnectionState): Boolean = state.isTerminal

    fun isDisconnectingAnimationActive(state: ConnectionState): Boolean =
        state == ConnectionState.DISCONNECTING || state == ConnectionState.CANCELING

    fun shouldShowLockDialog(state: ConnectionState, isDialogAlreadyVisible: Boolean): Boolean {
        return evaluateControlTap(state) == LockActionOutcome.SHOW_LOCK_WARNING && !isDialogAlreadyVisible
    }

    fun shouldShowLockWarningDialog(state: ConnectionState, isDialogAlreadyVisible: Boolean): Boolean =
        shouldShowLockDialog(state, isDialogAlreadyVisible)
}

object LockWarningDeduplicator {
    fun shouldShowDialog(state: ConnectionState, isDialogAlreadyVisible: Boolean): Boolean =
        ConnectionLockGuard.shouldShowLockDialog(state, isDialogAlreadyVisible)
}

class DisconnectCoordinator(
    private val scope: CoroutineScope,
    private val stateFlow: StateFlow<ConnectionState>,
    private val setState: (ConnectionState) -> Unit,
    private val dispatchStopSignal: () -> Unit,
    private val timeoutMs: Long = DEFAULT_DISCONNECT_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_DISCONNECT_TIMEOUT_MS: Long = 4_000L
    }

    private var watchdogJob: Job? = null
    private var stateObserverJob: Job? = null

    fun observeState(): Job {
        stateObserverJob?.cancel()
        val job = scope.launch {
            stateFlow.collect { state ->
                onStateChanged(state)
            }
        }
        stateObserverJob = job
        return job
    }

    fun onStateChanged(state: ConnectionState) {
        if (state.isTerminal) {
            onTerminalStateReached()
        }
    }

    fun onTerminalStateReached() {
        cancelWatchdog()
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(timeoutMs)
            val currentState = stateFlow.value
            if (currentState == ConnectionState.DISCONNECTING || currentState == ConnectionState.CANCELING) {
                setState(ConnectionState.DISCONNECTED)
            }
        }
    }

    fun requestDisconnect() {
        val current = stateFlow.value
        if (current.isTerminal) {
            cancelWatchdog()
            return
        }

        if (current == ConnectionState.DISCONNECTING || current == ConnectionState.CANCELING) {
            if (watchdogJob?.isActive != true) {
                startWatchdog()
            }
            return
        }

        setState(ConnectionState.DISCONNECTING)
        runCatching {
            dispatchStopSignal()
        }
        startWatchdog()
    }

    fun cancelWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    fun cancel() {
        cancelWatchdog()
        stateObserverJob?.cancel()
        stateObserverJob = null
    }
}
