package dev.rouzbeh.hybridvpn.core.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPolicyJsonTest {

    @Test
    fun `round trip preserves all fields`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.BYPASS,
            packages = setOf("com.a", "com.b", "com.system.app"),
            revision = 42
        )
        val decoded = RoutingPolicyJson.fromJson(RoutingPolicyJson.toJson(policy))
        assertEquals(policy, decoded)
    }

    @Test
    fun `disabled policy round trips`() {
        val policy = RoutingPolicy.disabled(revision = 7)
        val decoded = RoutingPolicyJson.fromJson(RoutingPolicyJson.toJson(policy))
        assertEquals(policy, decoded)
    }

    @Test
    fun `corrupt json yields null - consumer falls back to global`() {
        assertNull(RoutingPolicyJson.fromJson("not json at all {{{"))
        assertNull(RoutingPolicyJson.fromJson(""))
    }

    @Test
    fun `unknown mode string decodes to ALLOW - documented default`() {
        val json = """{"version":1,"revision":3,"enabled":true,"mode":"SOMETHING_NEW","packages":["com.a"]}"""
        val decoded = RoutingPolicyJson.fromJson(json)
        assertEquals(RoutingPolicy.Mode.ALLOW, decoded?.mode)
        assertEquals(setOf("com.a"), decoded?.packages)
    }

    @Test
    fun `missing enabled field decodes to disabled - fail closed`() {
        val json = """{"revision":3,"mode":"BYPASS","packages":["com.a"]}"""
        val decoded = RoutingPolicyJson.fromJson(json)
        assertEquals(false, decoded?.enabled)
    }

    @Test
    fun `empty packages list round trips`() {
        val policy = RoutingPolicy(
            enabled = true,
            mode = RoutingPolicy.Mode.ALLOW,
            packages = emptySet(),
            revision = 1
        )
        val decoded = RoutingPolicyJson.fromJson(RoutingPolicyJson.toJson(policy))
        assertEquals(policy, decoded)
        assertTrue(decoded?.packages?.isEmpty() == true)
    }
}
