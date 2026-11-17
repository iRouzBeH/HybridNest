package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.core.routing.RoutingDecision
import dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy
import dev.rouzbeh.hybridvpn.core.routing.RoutingPolicyResolver
import dev.rouzbeh.hybridvpn.service.ConnectionState
import dev.rouzbeh.hybridvpn.service.VpnStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VpnDisconnectAndExitProbeTest {

    @Before
    fun setup() {
        VpnStateManager.updateState(ConnectionState.DISCONNECTED)
        VpnStateManager.clearIntentionalCancel()
    }

    @Test
    fun `test terminal states include CANCELLED DISCONNECTED IDLE FAILED and ERROR`() {
        assertTrue(ConnectionState.DISCONNECTED.isTerminal)
        assertTrue(ConnectionState.IDLE.isTerminal)
        assertTrue(ConnectionState.FAILED.isTerminal)
        assertTrue(ConnectionState.ERROR.isTerminal)
        assertTrue(ConnectionState.CANCELLED.isTerminal)

        assertFalse(ConnectionState.CONNECTING.isTerminal)
        assertFalse(ConnectionState.CONNECTED.isTerminal)
        assertFalse(ConnectionState.DISCONNECTING.isTerminal)
        assertFalse(ConnectionState.CANCELING.isTerminal)
    }

    @Test
    fun `test obsolete session updates are rejected by session guard`() {
        VpnStateManager.setCurrentSessionId(1001L)
        VpnStateManager.updateState(ConnectionState.CONNECTED)

        // Callback from old session 1000L should be ignored
        VpnStateManager.updateStateFromNativeForSession(0, 1000L)
        assertEquals(ConnectionState.CONNECTED, VpnStateManager.connectionState.value)

        // Callback for active session 1001L should be accepted
        VpnStateManager.updateStateFromNativeForSession(0, 1001L)
        assertEquals(ConnectionState.DISCONNECTED, VpnStateManager.connectionState.value)
    }

    @Test
    fun `test allow mode empty selection refusal prevents global widening`() {
        val installed = setOf("com.brave.browser", "com.android.chrome")
        val emptySelectionPolicy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = emptySet(),
            revision = 42L
        )

        val decision = RoutingPolicyResolver.resolve(emptySelectionPolicy, installed)
        assertTrue(decision is RoutingDecision.Refused)
    }

    @Test
    fun `test allow mode with uninstalled selection is refused`() {
        val installed = setOf("com.brave.browser")
        val uninstalledSelectionPolicy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = setOf("com.nonexistent.app"),
            revision = 43L
        )

        val decision = RoutingPolicyResolver.resolve(uninstalledSelectionPolicy, installed)
        assertTrue(decision is RoutingDecision.Refused)
    }

    @Test
    fun `test bypass mode empty selection falls back to global without app exclusion`() {
        val installed = setOf("com.brave.browser", "com.android.chrome")
        val emptyBypassPolicy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.BYPASS,
            packages = emptySet(),
            revision = 44L
        )

        val decision = RoutingPolicyResolver.resolve(emptyBypassPolicy, installed)
        assertEquals(RoutingDecision.Global, decision)
    }
}
