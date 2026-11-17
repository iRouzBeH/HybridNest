package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.service.ConnectionState
import dev.rouzbeh.hybridvpn.service.VpnStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionStateTest {

    @Before
    fun setup() {
        VpnStateManager.updateState(ConnectionState.DISCONNECTED)
        VpnStateManager.clearIntentionalCancel()
    }

    @Test
    fun `test connection state classification properties`() {
        assertTrue(ConnectionState.CONNECTED.isConnected)
        assertFalse(ConnectionState.CONNECTING.isConnected)

        assertTrue(ConnectionState.CONNECTING.isConnectingState)
        assertTrue(ConnectionState.RECONNECTING.isConnectingState)
        assertFalse(ConnectionState.CONNECTED.isConnectingState)

        assertTrue(ConnectionState.DISCONNECTED.isTerminal)
        assertTrue(ConnectionState.IDLE.isTerminal)
        assertTrue(ConnectionState.FAILED.isTerminal)
        assertTrue(ConnectionState.ERROR.isTerminal)
        assertTrue(ConnectionState.CANCELLED.isTerminal)

        assertFalse(ConnectionState.CONNECTING.isTerminal)
        assertFalse(ConnectionState.CONNECTED.isTerminal)
        assertFalse(ConnectionState.DISCONNECTING.isTerminal)
    }

    @Test
    fun `test native state updates`() {
        VpnStateManager.updateStateFromNative(1)
        assertEquals(ConnectionState.CONNECTING, VpnStateManager.connectionState.value)

        VpnStateManager.updateStateFromNative(2)
        assertEquals(ConnectionState.CONNECTED, VpnStateManager.connectionState.value)

        VpnStateManager.updateStateFromNative(3)
        assertEquals(ConnectionState.FAILED, VpnStateManager.connectionState.value)

        VpnStateManager.updateStateFromNative(0)
        assertEquals(ConnectionState.DISCONNECTED, VpnStateManager.connectionState.value)
    }

    @Test
    fun `test intentional cancellation mapping`() {
        VpnStateManager.updateStateFromNative(1)
        assertEquals(ConnectionState.CONNECTING, VpnStateManager.connectionState.value)

        VpnStateManager.beginIntentionalCancel()
        VpnStateManager.updateStateFromNative(3) // native ERROR code on cancel
        assertEquals(ConnectionState.CANCELLED, VpnStateManager.connectionState.value)
    }

    @Test
    fun `test session id guard rejects superseded session updates`() {
        VpnStateManager.setCurrentSessionId(200L)
        VpnStateManager.updateState(ConnectionState.CONNECTING)

        // Native state update for old session 100L should be ignored
        VpnStateManager.updateStateFromNativeForSession(stateCode = 3, forSessionId = 100L)
        assertEquals(ConnectionState.CONNECTING, VpnStateManager.connectionState.value)

        // Native state update for current session 200L should be accepted
        VpnStateManager.updateStateFromNativeForSession(stateCode = 2, forSessionId = 200L)
        assertEquals(ConnectionState.CONNECTED, VpnStateManager.connectionState.value)
    }
}
