package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import dev.rouzbeh.hybridvpn.feature.dashboard.ProtocolSwitchResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolSwitchProfileResolverTest {

    private val openConnectProfile = VpnProfile(
        id = 1,
        name = "OC Server",
        protocol = "OpenConnect",
        serverAddress = "oc.example.com"
    )

    private val wireGuardProfile = VpnProfile(
        id = 2,
        name = "WG Server",
        protocol = "WireGuard",
        serverAddress = "wg.example.com"
    )

    private val v2rayProfile = VpnProfile(
        id = 3,
        name = "V2Ray Server",
        protocol = "v2rayNG",
        serverAddress = "v2.example.com"
    )

    @Test
    fun `resolveDisplayedActiveProfile returns pending profile during protocol switch without null flicker`() {
        // Room still has OpenConnect, but user switched to WireGuard tab
        val resolved = ProtocolSwitchResolver.resolveDisplayedActiveProfile(
            selectedProtocol = "WireGuard",
            roomActiveProfile = openConnectProfile,
            pendingRestoredProfile = wireGuardProfile
        )

        assertEquals("Pending restored profile must be returned immediately with zero null flicker", wireGuardProfile, resolved)
    }

    @Test
    fun `resolveDisplayedActiveProfile returns Room profile once Room catches up`() {
        // Room caught up to WireGuard, pending profile is cleared to null
        val resolved = ProtocolSwitchResolver.resolveDisplayedActiveProfile(
            selectedProtocol = "WireGuard",
            roomActiveProfile = wireGuardProfile,
            pendingRestoredProfile = null
        )

        assertEquals("Room active profile must be returned once persisted state matches selected protocol", wireGuardProfile, resolved)
    }

    @Test
    fun `resolveDisplayedActiveProfile prioritizes matching Room profile over pending profile`() {
        // Both match, Room is authoritative
        val resolved = ProtocolSwitchResolver.resolveDisplayedActiveProfile(
            selectedProtocol = "WireGuard",
            roomActiveProfile = wireGuardProfile,
            pendingRestoredProfile = wireGuardProfile.copy(name = "Optimistic WG")
        )

        assertEquals(wireGuardProfile, resolved)
    }

    @Test
    fun `resolveDisplayedActiveProfile returns null when no server exists for target protocol`() {
        // Protocol switched to v2rayNG where no server has ever been saved
        val resolved = ProtocolSwitchResolver.resolveDisplayedActiveProfile(
            selectedProtocol = "v2rayNG",
            roomActiveProfile = openConnectProfile,
            pendingRestoredProfile = null
        )

        assertNull("Must return null when target protocol has neither Room active profile nor pending profile", resolved)
    }

    @Test
    fun `resolveDisplayedActiveProfile ignores pending profile if its protocol does not match selectedProtocol`() {
        val resolved = ProtocolSwitchResolver.resolveDisplayedActiveProfile(
            selectedProtocol = "OpenConnect",
            roomActiveProfile = null,
            pendingRestoredProfile = wireGuardProfile
        )

        assertNull("Pending profile from another protocol must not be returned", resolved)
    }

    @Test
    fun `resolveDisplayedActiveProfile handles rapid protocol switches ignoring stale Room emissions`() {
        // Rapid switch: A (OpenConnect) -> B (WireGuard) -> C (v2rayNG)
        // User is on v2rayNG, pending profile is v2rayProfile.
        // Delayed Room emissions from A or B arrive:
        val resolvedWithStaleA = ProtocolSwitchResolver.resolveDisplayedActiveProfile(
            selectedProtocol = "v2rayNG",
            roomActiveProfile = openConnectProfile,
            pendingRestoredProfile = v2rayProfile
        )
        assertEquals("Stale Room emission from protocol A must be ignored", v2rayProfile, resolvedWithStaleA)

        val resolvedWithStaleB = ProtocolSwitchResolver.resolveDisplayedActiveProfile(
            selectedProtocol = "v2rayNG",
            roomActiveProfile = wireGuardProfile,
            pendingRestoredProfile = v2rayProfile
        )
        assertEquals("Stale Room emission from protocol B must be ignored", v2rayProfile, resolvedWithStaleB)
    }

    @Test
    fun `shouldClearPendingProfile returns false when pending profile is null`() {
        assertFalse(
            ProtocolSwitchResolver.shouldClearPendingProfile(
                selectedProtocol = "WireGuard",
                roomActiveProfile = wireGuardProfile,
                pendingRestoredProfile = null
            )
        )
    }

    @Test
    fun `shouldClearPendingProfile returns true when pending profile protocol differs from selected protocol`() {
        // Pending profile belongs to WireGuard, but user switched to v2rayNG
        assertTrue(
            ProtocolSwitchResolver.shouldClearPendingProfile(
                selectedProtocol = "v2rayNG",
                roomActiveProfile = null,
                pendingRestoredProfile = wireGuardProfile
            )
        )
    }

    @Test
    fun `shouldClearPendingProfile returns true when Room active profile catches up with pending profile`() {
        // Room has now updated to wireGuardProfile with same ID and protocol
        assertTrue(
            ProtocolSwitchResolver.shouldClearPendingProfile(
                selectedProtocol = "WireGuard",
                roomActiveProfile = wireGuardProfile,
                pendingRestoredProfile = wireGuardProfile
            )
        )
    }

    @Test
    fun `shouldClearPendingProfile returns false while Room active profile has not yet caught up`() {
        // Room still has OpenConnect (stale)
        assertFalse(
            ProtocolSwitchResolver.shouldClearPendingProfile(
                selectedProtocol = "WireGuard",
                roomActiveProfile = openConnectProfile,
                pendingRestoredProfile = wireGuardProfile
            )
        )

        // Room is null (still querying)
        assertFalse(
            ProtocolSwitchResolver.shouldClearPendingProfile(
                selectedProtocol = "WireGuard",
                roomActiveProfile = null,
                pendingRestoredProfile = wireGuardProfile
            )
        )

        // Room has a different ID
        val differentWgProfile = wireGuardProfile.copy(id = 999)
        assertFalse(
            ProtocolSwitchResolver.shouldClearPendingProfile(
                selectedProtocol = "WireGuard",
                roomActiveProfile = differentWgProfile,
                pendingRestoredProfile = wireGuardProfile
            )
        )
    }
}
