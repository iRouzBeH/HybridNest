package dev.rouzbeh.hybridvpn.feature.dashboard

import dev.rouzbeh.hybridvpn.service.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
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
    fun `lock warning deduplication prevents duplicate dialogs and stacked lock toasts`() {
        // Dialog deduplication
        assertTrue(ConnectionLockGuard.shouldShowLockDialog(ConnectionState.CONNECTED, isDialogAlreadyVisible = false))
        assertFalse(ConnectionLockGuard.shouldShowLockDialog(ConnectionState.CONNECTED, isDialogAlreadyVisible = true))
        assertFalse(ConnectionLockGuard.shouldShowLockDialog(ConnectionState.DISCONNECTING, isDialogAlreadyVisible = false))
        assertFalse(ConnectionLockGuard.shouldShowLockDialog(ConnectionState.DISCONNECTED, isDialogAlreadyVisible = false))

        // Toast deduplication
        assertTrue(ConnectionLockGuard.shouldShowLockToast(ConnectionState.CONNECTED, isDialogVisible = false, hasActiveLockToast = false))
        // Suppressed when dialog is already visible
        assertFalse(ConnectionLockGuard.shouldShowLockToast(ConnectionState.CONNECTED, isDialogVisible = true, hasActiveLockToast = false))
        // Suppressed when another lock toast is active
        assertFalse(ConnectionLockGuard.shouldShowLockToast(ConnectionState.CONNECTED, isDialogVisible = false, hasActiveLockToast = true))
        // Suppressed when both dialog and toast are active
        assertFalse(ConnectionLockGuard.shouldShowLockToast(ConnectionState.CONNECTED, isDialogVisible = true, hasActiveLockToast = true))

        // Suppressed during disconnecting or terminal states
        assertFalse(ConnectionLockGuard.shouldShowLockToast(ConnectionState.DISCONNECTING, isDialogVisible = false, hasActiveLockToast = false))
        assertFalse(ConnectionLockGuard.shouldShowLockToast(ConnectionState.DISCONNECTED, isDialogVisible = false, hasActiveLockToast = false))

        // LockWarningDeduplicator object delegation check
        assertTrue(LockWarningDeduplicator.shouldShowDialog(ConnectionState.CONNECTED, false))
        assertFalse(LockWarningDeduplicator.shouldShowDialog(ConnectionState.CONNECTED, true))
        assertTrue(LockWarningDeduplicator.shouldShowToast(ConnectionState.CONNECTED, false, false))
        assertFalse(LockWarningDeduplicator.shouldShowToast(ConnectionState.CONNECTED, true, false))
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
            var cancelCalled = false
            var updatedState = initialState

            val coordinator = DisconnectCoordinator(
                scope = CoroutineScope(Dispatchers.Unconfined),
                stateFlow = stateFlow,
                setState = {
                    updatedState = it
                    stateFlow.value = it
                },
                beginIntentionalCancel = { cancelCalled = true },
                isServiceRunning = { true },
                dispatchStopSignal = { dispatchedStop = true },
                teardownFlow = MutableSharedFlow<Unit>()
            )

            coordinator.requestDisconnect()

            assertFalse("Terminal state $initialState must never dispatch stop signal", dispatchedStop)
            assertFalse("Terminal state $initialState must never call beginIntentionalCancel", cancelCalled)
            assertEquals("Terminal state $initialState must normalize to DISCONNECTED", ConnectionState.DISCONNECTED, updatedState)
        }
    }

    @Test
    fun `DisconnectCoordinator transitions directly to DISCONNECTED when service is not running`() {
        val activeStates = listOf(
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING,
            ConnectionState.DISCONNECTING,
            ConnectionState.CANCELING
        )

        for (initialState in activeStates) {
            val stateFlow = MutableStateFlow(initialState)
            var dispatchedStop = false
            var cancelCalled = false
            var updatedState = initialState

            val coordinator = DisconnectCoordinator(
                scope = CoroutineScope(Dispatchers.Unconfined),
                stateFlow = stateFlow,
                setState = {
                    updatedState = it
                    stateFlow.value = it
                },
                beginIntentionalCancel = { cancelCalled = true },
                isServiceRunning = { false }, // Service is dead
                dispatchStopSignal = { dispatchedStop = true },
                teardownFlow = MutableSharedFlow<Unit>()
            )

            coordinator.requestDisconnect()

            assertFalse("Dead service from $initialState must never dispatch stop signal", dispatchedStop)
            assertFalse("Dead service from $initialState must never call beginIntentionalCancel", cancelCalled)
            assertEquals("Dead service from $initialState must transition directly to DISCONNECTED", ConnectionState.DISCONNECTED, updatedState)
        }
    }

    @Test
    fun `DisconnectCoordinator transitions CONNECTED to DISCONNECTING and completes cleanly when service disconnects`() = runBlocking {
        val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        val teardownFlow = MutableSharedFlow<Unit>(replay = 1)
        var dispatchedStop = false
        var updatedState = ConnectionState.CONNECTED

        val coordinator = DisconnectCoordinator(
            scope = this,
            stateFlow = stateFlow,
            setState = {
                updatedState = it
                stateFlow.value = it
            },
            beginIntentionalCancel = {},
            isServiceRunning = { true },
            dispatchStopSignal = { dispatchedStop = true },
            teardownFlow = teardownFlow,
            timeoutMs = 1_000L
        )

        coordinator.requestDisconnect()

        assertTrue("Must dispatch stop signal", dispatchedStop)
        assertEquals("Must enter DISCONNECTING state", ConnectionState.DISCONNECTING, updatedState)

        // Allow watchdog coroutine to begin collecting
        delay(50L)

        // Simulate service finishing teardown
        teardownFlow.emit(Unit)
        delay(300L) // Wait for race and delay(150L) in DisconnectCoordinator

        assertEquals("Must reach DISCONNECTED upon teardown completion", ConnectionState.DISCONNECTED, stateFlow.value)
        coordinator.cancelWatchdog()
    }

    @Test
    fun `DisconnectCoordinator transitions CONNECTING to CANCELING and invokes beginIntentionalCancel`() = runBlocking {
        val stateFlow = MutableStateFlow(ConnectionState.CONNECTING)
        val teardownFlow = MutableSharedFlow<Unit>()
        var dispatchedStop = false
        var cancelCalled = false
        var updatedState = ConnectionState.CONNECTING

        val coordinator = DisconnectCoordinator(
            scope = this,
            stateFlow = stateFlow,
            setState = {
                updatedState = it
                stateFlow.value = it
            },
            beginIntentionalCancel = { cancelCalled = true },
            isServiceRunning = { true },
            dispatchStopSignal = { dispatchedStop = true },
            teardownFlow = teardownFlow,
            timeoutMs = 1_000L
        )

        coordinator.requestDisconnect()

        assertTrue("Must call beginIntentionalCancel when canceling CONNECTING", cancelCalled)
        assertTrue("Must dispatch stop signal", dispatchedStop)
        assertEquals("Must enter CANCELING state", ConnectionState.CANCELING, updatedState)

        coordinator.cancelWatchdog()
    }

    @Test
    fun `DisconnectCoordinator falls back to DISCONNECTED after timeout when service hangs`() = runBlocking {
        val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        val teardownFlow = MutableSharedFlow<Unit>()
        var updatedState = ConnectionState.CONNECTED

        val coordinator = DisconnectCoordinator(
            scope = this,
            stateFlow = stateFlow,
            setState = {
                updatedState = it
                stateFlow.value = it
            },
            beginIntentionalCancel = {},
            isServiceRunning = { true },
            dispatchStopSignal = {},
            teardownFlow = teardownFlow,
            timeoutMs = 80L // Fast timeout for test
        )

        coordinator.requestDisconnect()
        assertEquals(ConnectionState.DISCONNECTING, updatedState)

        // Wait past the timeout without sending teardown or terminal state
        delay(160L)

        assertEquals("Watchdog must force DISCONNECTED on timeout", ConnectionState.DISCONNECTED, stateFlow.value)
        coordinator.cancelWatchdog()
    }

    @Test
    fun `DisconnectCoordinator ignores redundant disconnect taps while live disconnect is in progress but recovers if service died`() = runBlocking {
        val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        val teardownFlow = MutableSharedFlow<Unit>()
        var stopSignalCount = 0
        var serviceRunning = true

        val coordinator = DisconnectCoordinator(
            scope = this,
            stateFlow = stateFlow,
            setState = { stateFlow.value = it },
            beginIntentionalCancel = {},
            isServiceRunning = { serviceRunning },
            dispatchStopSignal = { stopSignalCount++ },
            teardownFlow = teardownFlow,
            timeoutMs = 1_000L
        )

        coordinator.requestDisconnect()
        assertEquals(1, stopSignalCount)
        assertEquals(ConnectionState.DISCONNECTING, stateFlow.value)

        // Second tap while live disconnect is actively in progress
        coordinator.requestDisconnect()
        assertEquals("Redundant tap must be ignored without double-dispatching stop signal", 1, stopSignalCount)
        assertEquals(ConnectionState.DISCONNECTING, stateFlow.value)

        // If service died unexpectedly while in DISCONNECTING state
        serviceRunning = false
        coordinator.requestDisconnect()
        assertEquals("Must immediately recover to DISCONNECTED when service is dead", ConnectionState.DISCONNECTED, stateFlow.value)

        coordinator.cancelWatchdog()
    }

    @Test
    fun `DisconnectCoordinator recovers to DISCONNECTED if dispatchStopSignal throws exception and service dead`() {
        val stateFlow = MutableStateFlow(ConnectionState.CONNECTED)
        var serviceRunning = true

        val coordinator = DisconnectCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            stateFlow = stateFlow,
            setState = { stateFlow.value = it },
            beginIntentionalCancel = {},
            isServiceRunning = { serviceRunning },
            dispatchStopSignal = {
                serviceRunning = false
                throw RuntimeException("Dead service communication failure")
            },
            teardownFlow = MutableSharedFlow<Unit>()
        )

        coordinator.requestDisconnect()
        assertEquals(ConnectionState.DISCONNECTED, stateFlow.value)
    }
}
