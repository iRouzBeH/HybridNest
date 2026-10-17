package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.core.routing.OpenVpnPolicyGuard
import dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fail-safe contract: OpenVPN + per-app ENABLED (either mode) must be REFUSED with
 * an explicit explanation, because vpnprotocols 3.0.4 ignores its applications
 * filter (bytecode-verified). Global mode must remain available.
 */
class OpenVpnPolicyGuardTest {

    @Test
    fun `enabled allow policy is refused with explanation`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = setOf("org.telegram.messenger"),
            revision = 1
        )
        val reason = OpenVpnPolicyGuard.refusalReason(policy)
        assertTrue(reason != null)
        assertTrue(reason!!.contains("cannot enforce"))
        assertTrue(reason.contains("vpnprotocols"))
    }

    @Test
    fun `enabled bypass policy is refused too - it would silently route everything`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.BYPASS,
            packages = setOf("com.instagram.android"),
            revision = 1
        )
        assertTrue(OpenVpnPolicyGuard.refusalReason(policy) != null)
    }

    @Test
    fun `empty allow selection is still refused rather than widened`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = emptySet(),
            revision = 1
        )
        assertTrue(OpenVpnPolicyGuard.refusalReason(policy) != null)
    }

    @Test
    fun `disabled policy (global) passes - OpenVPN may connect`() {
        val policy = RoutingPolicy.disabled(revision = 1)
        assertNull(OpenVpnPolicyGuard.refusalReason(policy))
    }
}
