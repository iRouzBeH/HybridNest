package dev.rouzbeh.hybridvpn.feature.dashboard

import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreJsonTest {

    @Test
    fun `test backup json serialization and deserialization`() {
        val originalProfiles = listOf(
            VpnProfile(name = "Profile 1", protocol = "OpenConnect", serverAddress = "vpn1.example.com", username = "user1", password = "pass1", countryCode = "de"),
            VpnProfile(name = "Profile 2", protocol = "v2rayNG", serverAddress = "vless://fake@server:443", subName = "MySub", countryCode = "us"),
            VpnProfile(name = "Profile 3", protocol = "WireGuard", serverAddress = "wireguard://fake@1.2.3.4:51820", countryCode = "fi")
        )

        val rootJson = JSONObject().apply {
            put("app", "HybridNest")
            put("version", 1)
            put("exportedAt", 1700000000000L)
            val arr = JSONArray()
            originalProfiles.forEach { p ->
                arr.put(JSONObject().apply {
                    put("name", p.name)
                    put("protocol", p.protocol)
                    put("serverAddress", p.serverAddress)
                    put("username", p.username)
                    put("password", p.password)
                    put("countryCode", p.countryCode)
                    put("subName", p.subName)
                })
            }
            put("profiles", arr)
        }

        val jsonString = rootJson.toString(2)
        assertNotNull(jsonString)

        val parsed = JSONObject(jsonString)
        assertEquals("HybridNest", parsed.getString("app"))
        assertEquals(1, parsed.getInt("version"))
        val arr = parsed.getJSONArray("profiles")
        assertEquals(3, arr.length())

        val p1 = arr.getJSONObject(0)
        assertEquals("Profile 1", p1.getString("name"))
        assertEquals("OpenConnect", p1.getString("protocol"))
        assertEquals("vpn1.example.com", p1.getString("serverAddress"))
        assertEquals("de", p1.getString("countryCode"))
    }

    @Test
    fun `test duplicate detection logic`() {
        val existing = listOf(
            VpnProfile(name = "Server A", protocol = "OpenConnect", serverAddress = "vpn.example.com")
        )
        val existingKeys = existing.map {
            Triple(it.protocol.lowercase(), it.serverAddress.trim(), it.name.trim())
        }.toSet()

        assertTrue(existingKeys.contains(Triple("openconnect", "vpn.example.com", "Server A")))
        assertTrue(!existingKeys.contains(Triple("openconnect", "vpn.example.com", "Server B")))
    }
}
