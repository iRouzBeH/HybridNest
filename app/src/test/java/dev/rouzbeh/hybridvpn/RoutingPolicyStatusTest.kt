package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.service.RoutingPolicyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session-identity contract for routing-status publication:
 *  - publish only after successful establish (simulated here by calling publish);
 *  - a stale teardown from a superseded session must NOT clear a newer session;
 *  - failed setup never publishes; teardown rotates into history.
 */
class RoutingPolicyStatusTest {

    @Test
    fun `publish sets active lastApplied and appliedRevision`() {
        RoutingPolicyStatus.resetForTest()
        RoutingPolicyStatus.publish(sessionId = 100, revision = 7, protocol = "OpenConnect", nowMs = 1_000L)

        assertEquals(7L, RoutingPolicyStatus.active.value?.revision)
        assertEquals(7L, RoutingPolicyStatus.appliedRevision.value)
        assertEquals(100L, RoutingPolicyStatus.active.value?.sessionId)
        assertEquals("OpenConnect", RoutingPolicyStatus.active.value?.protocol)
    }

    @Test
    fun `matching-session clear empties active and records teardown reason`() {
        RoutingPolicyStatus.resetForTest()
        RoutingPolicyStatus.publish(sessionId = 100, revision = 7, protocol = "OpenConnect", nowMs = 1_000L)
        RoutingPolicyStatus.clearActive(100, intentional = true, detail = "user stop")

        assertNull(RoutingPolicyStatus.active.value)
        assertNull(RoutingPolicyStatus.appliedRevision.value)
        // History survives teardown.
        assertEquals(7L, RoutingPolicyStatus.lastApplied.value?.revision)
        assertTrue(RoutingPolicyStatus.lastTeardown.value?.intentional == true)
    }

    @Test
    fun `stale teardown from superseded session is ignored`() {
        RoutingPolicyStatus.resetForTest()
        RoutingPolicyStatus.publish(sessionId = 100, revision = 7, protocol = "OpenConnect", nowMs = 1_000L)

        // A NEWER session replaces the active policy (service re-established).
        RoutingPolicyStatus.publish(sessionId = 200, revision = 9, protocol = "OpenVPN", nowMs = 2_000L)

        // LATE teardown of the OLD session arrives: must NOT clobber session 200.
        RoutingPolicyStatus.clearActive(100, intentional = true, detail = "late teardown of old session")

        assertEquals(200L, RoutingPolicyStatus.active.value?.sessionId)
        assertEquals(9L, RoutingPolicyStatus.appliedRevision.value)
    }

    @Test
    fun `clear without active policy is a safe no-op`() {
        RoutingPolicyStatus.resetForTest()
        RoutingPolicyStatus.clearActive(123, intentional = false, detail = "nothing active")
        assertNull(RoutingPolicyStatus.active.value)
        assertNull(RoutingPolicyStatus.lastTeardown.value)
    }

    @Test
    fun `lastApplied history keeps established revision even after failure teardown`() {
        RoutingPolicyStatus.resetForTest()
        RoutingPolicyStatus.publish(sessionId = 300, revision = 12, protocol = "OpenConnect", nowMs = 3_000L)
        RoutingPolicyStatus.clearActive(300, intentional = false, detail = "engine failure")

        assertEquals(12L, RoutingPolicyStatus.lastApplied.value?.revision)
        assertTrue(RoutingPolicyStatus.lastTeardown.value?.intentional == false)
        assertEquals("engine failure", RoutingPolicyStatus.lastTeardown.value?.detail)
    }
}
