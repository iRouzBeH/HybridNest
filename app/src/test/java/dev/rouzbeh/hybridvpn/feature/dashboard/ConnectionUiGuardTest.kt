package dev.rouzbeh.hybridvpn.feature.dashboard

import dev.rouzbeh.hybridvpn.service.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionUiGuardTest {

    @Test
    fun `evaluateControlTap maps every ConnectionState to exact LockActionOutcome`() {
        // Connected & connecting states require lock warning
        assertEquals(LockActionOutcome.SHOW_LOCK_WARNING, ConnectionLockGuard.evaluateControlTap(ConnectionState.CONNECTED))
        assertEquals(LockActionOutcome.SHOW_LOCK_WARNING, ConnectionLockGuard.evaluateControlTap(ConnectionState.CONNECTING))
        assertEquals(LockActionOutcome.SHOW_LOCK_WARNING, ConnectionLockGuard.evaluateControlTap(ConnectionState.RECONNECTING))

        // Disconnecting & canceling states must be silently ignored to prevent re-triggering transitions
        assertEquals(LockActionOutcome.IGNORE_WHILE_DISCONNECTING, ConnectionLockGuard.evaluateControlTap(ConnectionState.DISCONNECTING))
        assertEquals(LockActionOutcome.IGNORE_WHILE_DISCONNECTING, ConnectionLockGuard.evaluateControlTap(ConnectionState.CANCELING))

        // All terminal states allow normal user actions
        assertEquals(LockActionOutcome.ALLOW, ConnectionLockGuard.evaluateControlTap(ConnectionState.DISCONNECTED))
        assertEquals(LockActionOutcome.ALLOW, ConnectionLockGuard.evaluateControlTap(ConnectionState.IDLE))
        assertEquals(LockActionOutcome.ALLOW, ConnectionLockGuard.evaluateControlTap(ConnectionState.CANCELLED))
        assertEquals(LockActionOutcome.ALLOW, ConnectionLockGuard.evaluateControlTap(ConnectionState.ERROR))
        assertEquals(LockActionOutcome.ALLOW, ConnectionLockGuard.evaluateControlTap(ConnectionState.FAILED))

        // Exhaustive verification for every enum entry
        for (state in ConnectionState.values()) {
            val outcome = ConnectionLockGuard.evaluateControlTap(state)
            when (state) {
                ConnectionState.CONNECTED,
                ConnectionState.CONNECTING,
                ConnectionState.RECONNECTING -> assertEquals(LockActionOutcome.SHOW_LOCK_WARNING, outcome)
                ConnectionState.DISCONNECTING,
                ConnectionState.CANCELING -> assertEquals(LockActionOutcome.IGNORE_WHILE_DISCONNECTING, outcome)
                else -> assertEquals(LockActionOutcome.ALLOW, outcome)
            }
        }
    }

    @Test
    fun `shouldAutoDismissLockFeedback is true only for terminal states`() {
        for (state in ConnectionState.values()) {
            val autoDismiss = ConnectionLockGuard.shouldAutoDismissLockFeedback(state)
            assertEquals("State $state mismatch for shouldAutoDismissLockFeedback", state.isTerminal, autoDismiss)
        }

        assertTrue(ConnectionLockGuard.shouldAutoDismissLockFeedback(ConnectionState.DISCONNECTED))
        assertTrue(ConnectionLockGuard.shouldAutoDismissLockFeedback(ConnectionState.IDLE))
        assertTrue(ConnectionLockGuard.shouldAutoDismissLockFeedback(ConnectionState.CANCELLED))
        assertTrue(ConnectionLockGuard.shouldAutoDismissLockFeedback(ConnectionState.ERROR))
        assertTrue(ConnectionLockGuard.shouldAutoDismissLockFeedback(ConnectionState.FAILED))

        assertFalse(ConnectionLockGuard.shouldAutoDismissLockFeedback(ConnectionState.CONNECTED))
        assertFalse(ConnectionLockGuard.shouldAutoDismissLockFeedback(ConnectionState.CONNECTING))
        assertFalse(ConnectionLockGuard.shouldAutoDismissLockFeedback(ConnectionState.RECONNECTING))
        assertFalse(ConnectionLockGuard.shouldAutoDismissLockFeedback(ConnectionState.DISCONNECTING))
        assertFalse(ConnectionLockGuard.shouldAutoDismissLockFeedback(ConnectionState.CANCELING))
    }

    @Test
    fun `isDisconnectingAnimationActive is true only for DISCONNECTING and CANCELING`() {
        assertTrue(ConnectionLockGuard.isDisconnectingAnimationActive(ConnectionState.DISCONNECTING))
        assertTrue(ConnectionLockGuard.isDisconnectingAnimationActive(ConnectionState.CANCELING))

        assertFalse(ConnectionLockGuard.isDisconnectingAnimationActive(ConnectionState.CONNECTED))
        assertFalse(ConnectionLockGuard.isDisconnectingAnimationActive(ConnectionState.CONNECTING))
        assertFalse(ConnectionLockGuard.isDisconnectingAnimationActive(ConnectionState.RECONNECTING))
        assertFalse(ConnectionLockGuard.isDisconnectingAnimationActive(ConnectionState.DISCONNECTED))
        assertFalse(ConnectionLockGuard.isDisconnectingAnimationActive(ConnectionState.IDLE))
        assertFalse(ConnectionLockGuard.isDisconnectingAnimationActive(ConnectionState.CANCELLED))
        assertFalse(ConnectionLockGuard.isDisconnectingAnimationActive(ConnectionState.ERROR))
        assertFalse(ConnectionLockGuard.isDisconnectingAnimationActive(ConnectionState.FAILED))
    }

    @Test
    fun `lock warning deduplication prevents duplicate dialogs and verifies isLocked`() {
        // Dialog deduplication via shouldShowLockDialog
        assertTrue(ConnectionLockGuard.shouldShowLockDialog(ConnectionState.CONNECTED, isDialogAlreadyVisible = false))
        assertFalse(ConnectionLockGuard.shouldShowLockDialog(ConnectionState.CONNECTED, isDialogAlreadyVisible = true))
        assertFalse(ConnectionLockGuard.shouldShowLockDialog(ConnectionState.DISCONNECTING, isDialogAlreadyVisible = false))
        assertFalse(ConnectionLockGuard.shouldShowLockDialog(ConnectionState.DISCONNECTED, isDialogAlreadyVisible = false))

        // shouldShowLockWarningDialog alias verification
        assertTrue(ConnectionLockGuard.shouldShowLockWarningDialog(ConnectionState.CONNECTED, isDialogAlreadyVisible = false))
        assertFalse(ConnectionLockGuard.shouldShowLockWarningDialog(ConnectionState.CONNECTED, isDialogAlreadyVisible = true))
        assertFalse(ConnectionLockGuard.shouldShowLockWarningDialog(ConnectionState.DISCONNECTING, isDialogAlreadyVisible = false))
        assertFalse(ConnectionLockGuard.shouldShowLockWarningDialog(ConnectionState.DISCONNECTED, isDialogAlreadyVisible = false))

        // ConnectionLockGuard.isLocked contract
        assertTrue(ConnectionLockGuard.isLocked(ConnectionState.CONNECTED))
        assertTrue(ConnectionLockGuard.isLocked(ConnectionState.CONNECTING))
        assertTrue(ConnectionLockGuard.isLocked(ConnectionState.RECONNECTING))
        assertFalse(ConnectionLockGuard.isLocked(ConnectionState.DISCONNECTING))
        assertFalse(ConnectionLockGuard.isLocked(ConnectionState.CANCELING))
        assertFalse(ConnectionLockGuard.isLocked(ConnectionState.DISCONNECTED))
        assertFalse(ConnectionLockGuard.isLocked(ConnectionState.IDLE))
        assertFalse(ConnectionLockGuard.isLocked(ConnectionState.CANCELLED))
        assertFalse(ConnectionLockGuard.isLocked(ConnectionState.ERROR))
        assertFalse(ConnectionLockGuard.isLocked(ConnectionState.FAILED))

        // LockWarningDeduplicator delegation check
        assertTrue(LockWarningDeduplicator.shouldShowDialog(ConnectionState.CONNECTED, false))
        assertFalse(LockWarningDeduplicator.shouldShowDialog(ConnectionState.CONNECTED, true))
    }

    @Test
    fun `DisconnectCoordinator in idle and terminal states never enters DISCONNECTING or dispatches stop`() {
        val terminalStates = listOf(
            ConnectionState.DISCONNECTED,
            ConnectionState.IDLE,
            ConnectionState.ERROR,
            ConnectionState.FAILED,
            ConnectionState.CANCELLED
        )

        for (initialState in terminalStates) {
            val stateFlow = MutableStateFlow(initialState)
            var dispatchedStop = false
            var updatedState = initialState

            val coordinator = DisconnectCoordinator(
                scope = CoroutineScope(Dispatchers.Unconfined),
                stateFlow = stateFlow,
                setState = {
                    updatedState = it
                    stateFlow.value = it
                },
                dispatchStopSignal = { dispatchedStop = true }
            )

            coordinator.requestDisconnect()

            assertFalse("Terminal state $initialState must never dispatch stop signal", dispatchedStop)
            assertEquals("Terminal state $initialState must remain unchanged", initialState, updatedState)
            coordinator.cancel()
        }
    }

    @Test
    fun `DisconnectCoordinator requestDisconnect from CONNECTED transitions to DISCONNECTING and invokes dispatchStopSignal exactly once`() {
        val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        var stopCount = 0
        var updatedState = ConnectionState.CONNECTED

        val coordinator = DisconnectCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            stateFlow = stateFlow,
            setState = {
                updatedState = it
                stateFlow.value = it
            },
            dispatchStopSignal = { stopCount++ }
        )

        coordinator.requestDisconnect()

        assertEquals("Must dispatch stop signal exactly once", 1, stopCount)
        assertEquals("Must transition to DISCONNECTING immediately", ConnectionState.DISCONNECTING, updatedState)
        coordinator.cancel()
    }

    @Test
    fun `DisconnectCoordinator rapid repeated requestDisconnect calls are idempotent and do not double dispatch stop`() {
        val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        var stopCount = 0

        val coordinator = DisconnectCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            stateFlow = stateFlow,
            setState = { stateFlow.value = it },
            dispatchStopSignal = { stopCount++ }
        )

        coordinator.requestDisconnect()
        assertEquals(1, stopCount)
        assertEquals(ConnectionState.DISCONNECTING, stateFlow.value)

        // Rapid subsequent taps while already in DISCONNECTING state
        coordinator.requestDisconnect()
        coordinator.requestDisconnect()

        assertEquals("dispatchStopSignal must still be called only once", 1, stopCount)
        assertEquals(ConnectionState.DISCONNECTING, stateFlow.value)
        coordinator.cancel()
    }

    @Test
    fun `DisconnectCoordinator reaching terminal state before timeout cancels watchdog cleanly`() = runBlocking {
        val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        var updatedState = ConnectionState.CONNECTED

        val coordinator = DisconnectCoordinator(
            scope = this,
            stateFlow = stateFlow,
            setState = {
                updatedState = it
                stateFlow.value = it
            },
            dispatchStopSignal = {},
            timeoutMs = 150L
        )

        coordinator.requestDisconnect()
        assertEquals(ConnectionState.DISCONNECTING, updatedState)

        // Simulate service reaching terminal DISCONNECTED state before timeout
        stateFlow.value = ConnectionState.DISCONNECTED
        coordinator.onStateChanged(ConnectionState.DISCONNECTED)

        // Wait past the timeoutMs
        delay(250L)

        // State remains DISCONNECTED and watchdog was cancelled
        assertEquals(ConnectionState.DISCONNECTED, stateFlow.value)
        coordinator.cancel()
    }

    @Test
    fun `DisconnectCoordinator watchdog recovers state to DISCONNECTED if service hangs past timeout`() = runBlocking {
        val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        var updatedState = ConnectionState.CONNECTED

        val coordinator = DisconnectCoordinator(
            scope = this,
            stateFlow = stateFlow,
            setState = {
                updatedState = it
                stateFlow.value = it
            },
            dispatchStopSignal = {},
            timeoutMs = 80L
        )

        coordinator.requestDisconnect()
        assertEquals(ConnectionState.DISCONNECTING, updatedState)

        // Wait past timeout
        delay(160L)

        assertEquals("Watchdog must force state to DISCONNECTED after timeout", ConnectionState.DISCONNECTED, stateFlow.value)
        coordinator.cancel()
    }

    @Test
    fun `DisconnectCoordinator terminal states ERROR and CANCELLED cancel watchdog`() = runBlocking {
        for (terminal in listOf(ConnectionState.ERROR, ConnectionState.CANCELLED)) {
            val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
            val coordinator = DisconnectCoordinator(
                scope = this,
                stateFlow = stateFlow,
                setState = { stateFlow.value = it },
                dispatchStopSignal = {},
                timeoutMs = 100L
            )

            coordinator.requestDisconnect()
            assertEquals(ConnectionState.DISCONNECTING, stateFlow.value)

            stateFlow.value = terminal
            coordinator.onStateChanged(terminal)

            delay(150L)
            assertEquals("State must remain $terminal", terminal, stateFlow.value)
            coordinator.cancel()
        }
    }

    @Test
    fun `DisconnectCoordinator observeState cancels watchdog when terminal state arrives`() = runBlocking {
        val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        val coordinator = DisconnectCoordinator(
            scope = this,
            stateFlow = stateFlow,
            setState = { stateFlow.value = it },
            dispatchStopSignal = {},
            timeoutMs = 150L
        )

        val observer = coordinator.observeState()
        coordinator.requestDisconnect()
        assertEquals(ConnectionState.DISCONNECTING, stateFlow.value)

        stateFlow.value = ConnectionState.DISCONNECTED
        delay(50L)
        delay(150L)
        assertEquals(ConnectionState.DISCONNECTED, stateFlow.value)

        observer.cancel()
        coordinator.cancel()
    }

    @Test
    fun `evaluateServerSelectTap maps every state and combination accurately`() {
        // 1. CONNECTED: Same protocol + same profile -> ALREADY_ACTIVE_NOOP
        assertEquals(
            ServerSelectOutcome.ALREADY_ACTIVE_NOOP,
            ConnectionLockGuard.evaluateServerSelectTap(ConnectionState.CONNECTED, isSameProtocol = true, isSameProfile = true)
        )

        // 2. CONNECTED: Same protocol + different profile -> ALLOW_RECONNECT
        assertEquals(
            ServerSelectOutcome.ALLOW_RECONNECT,
            ConnectionLockGuard.evaluateServerSelectTap(ConnectionState.CONNECTED, isSameProtocol = true, isSameProfile = false)
        )

        // 3. CONNECTED: Different protocol -> SHOW_LOCK_WARNING
        assertEquals(
            ServerSelectOutcome.SHOW_LOCK_WARNING,
            ConnectionLockGuard.evaluateServerSelectTap(ConnectionState.CONNECTED, isSameProtocol = false, isSameProfile = false)
        )
        assertEquals(
            ServerSelectOutcome.SHOW_LOCK_WARNING,
            ConnectionLockGuard.evaluateServerSelectTap(ConnectionState.CONNECTED, isSameProtocol = false, isSameProfile = true)
        )

        // 4. In-flight connecting / reconnecting: always SHOW_LOCK_WARNING
        for (state in listOf(ConnectionState.CONNECTING, ConnectionState.RECONNECTING)) {
            for (sameProto in listOf(true, false)) {
                for (sameProf in listOf(true, false)) {
                    assertEquals(
                        "State $state (sameProto=$sameProto, sameProf=$sameProf) must require lock warning",
                        ServerSelectOutcome.SHOW_LOCK_WARNING,
                        ConnectionLockGuard.evaluateServerSelectTap(state, isSameProtocol = sameProto, isSameProfile = sameProf)
                    )
                }
            }
        }

        // 5. In-flight disconnecting / canceling: always IGNORE_WHILE_DISCONNECTING
        for (state in listOf(ConnectionState.DISCONNECTING, ConnectionState.CANCELING)) {
            for (sameProto in listOf(true, false)) {
                for (sameProf in listOf(true, false)) {
                    assertEquals(
                        "State $state (sameProto=$sameProto, sameProf=$sameProf) must ignore tap",
                        ServerSelectOutcome.IGNORE_WHILE_DISCONNECTING,
                        ConnectionLockGuard.evaluateServerSelectTap(state, isSameProtocol = sameProto, isSameProfile = sameProf)
                    )
                }
            }
        }

        // 6. Terminal / idle states: always ALLOW_SELECT
        val terminalStates = listOf(
            ConnectionState.DISCONNECTED,
            ConnectionState.IDLE,
            ConnectionState.CANCELLED,
            ConnectionState.ERROR,
            ConnectionState.FAILED
        )
        for (state in terminalStates) {
            for (sameProto in listOf(true, false)) {
                for (sameProf in listOf(true, false)) {
                    assertEquals(
                        "State $state (sameProto=$sameProto, sameProf=$sameProf) must allow select",
                        ServerSelectOutcome.ALLOW_SELECT,
                        ConnectionLockGuard.evaluateServerSelectTap(state, isSameProtocol = sameProto, isSameProfile = sameProf)
                    )
                }
            }
        }

        // 7. Exhaustive verification across all ConnectionState enum values
        for (state in ConnectionState.values()) {
            val outcomeSameProtoDiffProf = ConnectionLockGuard.evaluateServerSelectTap(state, isSameProtocol = true, isSameProfile = false)
            when (state) {
                ConnectionState.CONNECTED -> assertEquals(ServerSelectOutcome.ALLOW_RECONNECT, outcomeSameProtoDiffProf)
                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> assertEquals(ServerSelectOutcome.SHOW_LOCK_WARNING, outcomeSameProtoDiffProf)
                ConnectionState.DISCONNECTING, ConnectionState.CANCELING -> assertEquals(ServerSelectOutcome.IGNORE_WHILE_DISCONNECTING, outcomeSameProtoDiffProf)
                else -> assertEquals(ServerSelectOutcome.ALLOW_SELECT, outcomeSameProtoDiffProf)
            }
        }
    }
}
