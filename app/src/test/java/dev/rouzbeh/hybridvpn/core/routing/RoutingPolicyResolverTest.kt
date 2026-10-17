package dev.rouzbeh.hybridvpn.core.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPolicyResolverTest {

    private val installed = setOf("com.a", "com.b", "com.c", "com.system.app")

    @Test
    fun `disabled policy resolves to global`() {
        val policy = RoutingPolicy(
            enabled = false,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = setOf("com.a"),
            revision = 1
        )
        assertEquals(RoutingDecision.Global, RoutingPolicyResolver.resolve(policy, installed))
    }

    @Test
    fun `allow mode routes only selected installed apps`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = setOf("com.a", "com.b"),
            revision = 1
        )
        val decision = RoutingPolicyResolver.resolve(policy, installed)
        assertTrue(decision is RoutingDecision.Allow)
        decision as RoutingDecision.Allow
        assertEquals(setOf("com.a", "com.b"), decision.allowedPackages)
        assertTrue(decision.prunedPackages.isEmpty())
    }

    @Test
    fun `bypass mode disallows selected installed apps`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.BYPASS,
            packages = setOf("com.a"),
            revision = 1
        )
        val decision = RoutingPolicyResolver.resolve(policy, installed)
        assertTrue(decision is RoutingDecision.Bypass)
        decision as RoutingDecision.Bypass
        assertEquals(setOf("com.a"), decision.disallowedPackages)
    }

    @Test
    fun `allow mode with zero usable apps is refused - never widened to global`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = setOf("com.gone", "com.also-gone"),
            revision = 1
        )
        val decision = RoutingPolicyResolver.resolve(policy, installed)
        assertTrue(decision is RoutingDecision.Refused)
    }

    @Test
    fun `allow mode with no selections at all is refused`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = emptySet(),
            revision = 1
        )
        assertTrue(RoutingPolicyResolver.resolve(policy, installed) is RoutingDecision.Refused)
    }

    @Test
    fun `allow mode prunes uninstalled and keeps usable - never widens`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = setOf("com.a", "com.gone"),
            revision = 1
        )
        val decision = RoutingPolicyResolver.resolve(policy, installed)
        assertTrue(decision is RoutingDecision.Allow)
        decision as RoutingDecision.Allow
        assertEquals(setOf("com.a"), decision.allowedPackages)
        assertEquals(setOf("com.gone"), decision.prunedPackages)
    }

    @Test
    fun `bypass mode with zero selections falls back to global`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.BYPASS,
            packages = emptySet(),
            revision = 1
        )
        assertEquals(RoutingDecision.Global, RoutingPolicyResolver.resolve(policy, installed))
    }

    @Test
    fun `bypass mode prunes uninstalled selections and reports them`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.BYPASS,
            packages = setOf("com.a", "com.gone"),
            revision = 1
        )
        val decision = RoutingPolicyResolver.resolve(policy, installed)
        assertTrue(decision is RoutingDecision.Bypass)
        decision as RoutingDecision.Bypass
        assertEquals(setOf("com.a"), decision.disallowedPackages)
        assertEquals(setOf("com.gone"), decision.prunedPackages)
    }

    @Test
    fun `bypass mode with only uninstalled selections is global`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.BYPASS,
            packages = setOf("com.gone"),
            revision = 1
        )
        assertEquals(RoutingDecision.Global, RoutingPolicyResolver.resolve(policy, installed))
    }

    @Test
    fun `system apps are treated like any other package by the resolver`() {
        // The resolver is list-agnostic; the UI decides what is selectable.
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = setOf("com.system.app"),
            revision = 1
        )
        val decision = RoutingPolicyResolver.resolve(policy, installed)
        assertTrue(decision is RoutingDecision.Allow)
        decision as RoutingDecision.Allow
        assertEquals(setOf("com.system.app"), decision.allowedPackages)
    }
}
