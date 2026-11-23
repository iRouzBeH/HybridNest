package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.feature.dashboard.AnyConnectSettingsSnapshot
import dev.rouzbeh.hybridvpn.feature.dashboard.FeaturesSettingsSnapshot
import dev.rouzbeh.hybridvpn.feature.dashboard.SettingsExitDecision
import dev.rouzbeh.hybridvpn.feature.dashboard.V2raySettingsSnapshot
import dev.rouzbeh.hybridvpn.feature.dashboard.WireGuardSettingsSnapshot
import dev.rouzbeh.hybridvpn.feature.dashboard.isDirty
import dev.rouzbeh.hybridvpn.feature.dashboard.resolveSettingsExit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUnsavedChangesTest {

    // =========================================================================
    // 1. Exit Decision Evaluator
    // =========================================================================

    @Test
    fun `resolveSettingsExit returns EXIT_IMMEDIATELY when no unsaved changes`() {
        val decision = resolveSettingsExit(hasUnsavedChanges = false)
        assertEquals(SettingsExitDecision.EXIT_IMMEDIATELY, decision)
    }

    @Test
    fun `resolveSettingsExit returns PROMPT_UNSAVED_CHANGES when unsaved changes exist`() {
        val decision = resolveSettingsExit(hasUnsavedChanges = true)
        assertEquals(SettingsExitDecision.PROMPT_UNSAVED_CHANGES, decision)
    }

    // =========================================================================
    // 2. AnyConnect Settings Snapshot
    // =========================================================================

    @Test
    fun `AnyConnect snapshot - clean state is not dirty`() {
        val initial = AnyConnectSettingsSnapshot.create(
            dtlsEnabled = true,
            mtu = "1400",
            dpdTimeout = "300",
            fallbackDns = "8.8.8.8"
        )
        val current = AnyConnectSettingsSnapshot.create(
            dtlsEnabled = true,
            mtu = "1400",
            dpdTimeout = "300",
            fallbackDns = "8.8.8.8"
        )
        assertEquals(initial, current)
        assertFalse(initial.isDirty(current))
    }

    @Test
    fun `AnyConnect snapshot - editing each field triggers dirty state and reverts cleanly`() {
        val initial = AnyConnectSettingsSnapshot.create(
            dtlsEnabled = true,
            mtu = "1400",
            dpdTimeout = "300",
            fallbackDns = "8.8.8.8"
        )

        // dtlsEnabled modified
        val dtlsToggled = initial.copy(dtlsEnabled = false)
        assertNotEquals(initial, dtlsToggled)
        assertTrue(initial.isDirty(dtlsToggled))

        // mtu modified
        val mtuModified = initial.copy(mtu = "1350")
        assertNotEquals(initial, mtuModified)
        assertTrue(initial.isDirty(mtuModified))

        // dpdTimeout modified
        val dpdModified = initial.copy(dpdTimeout = "60")
        assertNotEquals(initial, dpdModified)
        assertTrue(initial.isDirty(dpdModified))

        // fallbackDns modified
        val dnsModified = initial.copy(fallbackDns = "1.1.1.1")
        assertNotEquals(initial, dnsModified)
        assertTrue(initial.isDirty(dnsModified))

        // Reverting all back to initial
        val reverted = dnsModified.copy(fallbackDns = "8.8.8.8")
        assertEquals(initial, reverted)
        assertFalse(initial.isDirty(reverted))
    }

    @Test
    fun `AnyConnect snapshot - whitespace normalization consistency`() {
        val base = AnyConnectSettingsSnapshot.create(
            dtlsEnabled = true,
            mtu = "1400",
            dpdTimeout = "300",
            fallbackDns = "8.8.8.8"
        )
        val withWhitespace = AnyConnectSettingsSnapshot.create(
            dtlsEnabled = true,
            mtu = "  1400  ",
            dpdTimeout = " 300 ",
            fallbackDns = " 8.8.8.8  "
        )
        assertEquals(base, withWhitespace)
        assertFalse(base.isDirty(withWhitespace))
    }

    // =========================================================================
    // 3. WireGuard Settings Snapshot
    // =========================================================================

    @Test
    fun `WireGuard snapshot - clean state is not dirty`() {
        val initial = WireGuardSettingsSnapshot.create(
            mtuOverride = "0",
            keepaliveOverride = "-1",
            tunnelDns = "9.9.9.9",
            bootstrapResolve = true
        )
        val current = WireGuardSettingsSnapshot.create(
            mtuOverride = "0",
            keepaliveOverride = "-1",
            tunnelDns = "9.9.9.9",
            bootstrapResolve = true
        )
        assertEquals(initial, current)
        assertFalse(initial.isDirty(current))
    }

    @Test
    fun `WireGuard snapshot - editing each field triggers dirty state and reverts cleanly`() {
        val initial = WireGuardSettingsSnapshot.create(
            mtuOverride = "0",
            keepaliveOverride = "-1",
            tunnelDns = "9.9.9.9",
            bootstrapResolve = true
        )

        // mtuOverride modified
        val mtuChanged = initial.copy(mtuOverride = "1420")
        assertNotEquals(initial, mtuChanged)
        assertTrue(initial.isDirty(mtuChanged))

        // keepaliveOverride modified
        val keepaliveChanged = initial.copy(keepaliveOverride = "25")
        assertNotEquals(initial, keepaliveChanged)
        assertTrue(initial.isDirty(keepaliveChanged))

        // tunnelDns modified
        val dnsChanged = initial.copy(tunnelDns = "1.1.1.1")
        assertNotEquals(initial, dnsChanged)
        assertTrue(initial.isDirty(dnsChanged))

        // bootstrapResolve modified
        val bootstrapChanged = initial.copy(bootstrapResolve = false)
        assertNotEquals(initial, bootstrapChanged)
        assertTrue(initial.isDirty(bootstrapChanged))

        // Reverting all back to initial
        val reverted = bootstrapChanged.copy(bootstrapResolve = true)
        assertEquals(initial, reverted)
        assertFalse(initial.isDirty(reverted))
    }

    @Test
    fun `WireGuard snapshot - whitespace normalization consistency`() {
        val base = WireGuardSettingsSnapshot.create(
            mtuOverride = "1380",
            keepaliveOverride = "25",
            tunnelDns = "1.1.1.1",
            bootstrapResolve = true
        )
        val withWhitespace = WireGuardSettingsSnapshot.create(
            mtuOverride = " 1380\t",
            keepaliveOverride = " 25 ",
            tunnelDns = "  1.1.1.1 ",
            bootstrapResolve = true
        )
        assertEquals(base, withWhitespace)
        assertFalse(base.isDirty(withWhitespace))
    }

    // =========================================================================
    // 4. v2rayNG Settings Snapshot
    // =========================================================================

    @Test
    fun `v2rayNG snapshot - clean state is not dirty`() {
        val initial = V2raySettingsSnapshot.create(
            vpnDns = "9.9.9.9",
            remoteDns = "tcp://1.1.1.1",
            domesticDns = "178.22.122.100",
            sniffingEnabled = true,
            bypassLan = false,
            muxEnabled = false,
            muxConcurrency = "10",
            muxQuicPolicy = "reject"
        )
        val current = V2raySettingsSnapshot.create(
            vpnDns = "9.9.9.9",
            remoteDns = "tcp://1.1.1.1",
            domesticDns = "178.22.122.100",
            sniffingEnabled = true,
            bypassLan = false,
            muxEnabled = false,
            muxConcurrency = "10",
            muxQuicPolicy = "reject"
        )
        assertEquals(initial, current)
        assertFalse(initial.isDirty(current))
    }

    @Test
    fun `v2rayNG snapshot - editing each field triggers dirty state and reverts cleanly`() {
        val initial = V2raySettingsSnapshot.create(
            vpnDns = "9.9.9.9",
            remoteDns = "tcp://1.1.1.1",
            domesticDns = "178.22.122.100",
            sniffingEnabled = true,
            bypassLan = false,
            muxEnabled = false,
            muxConcurrency = "10",
            muxQuicPolicy = "reject"
        )

        // vpnDns modified
        val vpnDnsChanged = initial.copy(vpnDns = "8.8.8.8")
        assertNotEquals(initial, vpnDnsChanged)
        assertTrue(initial.isDirty(vpnDnsChanged))

        // remoteDns modified
        val remoteDnsChanged = initial.copy(remoteDns = "tcp://8.8.8.8")
        assertNotEquals(initial, remoteDnsChanged)
        assertTrue(initial.isDirty(remoteDnsChanged))

        // domesticDns modified
        val domesticDnsChanged = initial.copy(domesticDns = "1.1.1.1")
        assertNotEquals(initial, domesticDnsChanged)
        assertTrue(initial.isDirty(domesticDnsChanged))

        // sniffingEnabled modified
        val sniffingChanged = initial.copy(sniffingEnabled = false)
        assertNotEquals(initial, sniffingChanged)
        assertTrue(initial.isDirty(sniffingChanged))

        // bypassLan modified
        val bypassLanChanged = initial.copy(bypassLan = true)
        assertNotEquals(initial, bypassLanChanged)
        assertTrue(initial.isDirty(bypassLanChanged))

        // muxEnabled modified
        val muxChanged = initial.copy(muxEnabled = true)
        assertNotEquals(initial, muxChanged)
        assertTrue(initial.isDirty(muxChanged))

        // muxConcurrency modified
        val concurrencyChanged = initial.copy(muxConcurrency = "16")
        assertNotEquals(initial, concurrencyChanged)
        assertTrue(initial.isDirty(concurrencyChanged))

        // muxQuicPolicy modified
        val quicChanged = initial.copy(muxQuicPolicy = "allow")
        assertNotEquals(initial, quicChanged)
        assertTrue(initial.isDirty(quicChanged))

        // Reverting all back to initial
        val reverted = quicChanged.copy(muxQuicPolicy = "reject")
        assertEquals(initial, reverted)
        assertFalse(initial.isDirty(reverted))
    }

    @Test
    fun `v2rayNG snapshot - whitespace normalization consistency`() {
        val base = V2raySettingsSnapshot.create(
            vpnDns = "9.9.9.9",
            remoteDns = "tcp://1.1.1.1",
            domesticDns = "178.22.122.100",
            sniffingEnabled = true,
            bypassLan = false,
            muxEnabled = false,
            muxConcurrency = "10",
            muxQuicPolicy = "reject"
        )
        val withWhitespace = V2raySettingsSnapshot.create(
            vpnDns = " 9.9.9.9 ",
            remoteDns = " tcp://1.1.1.1 ",
            domesticDns = " 178.22.122.100\t",
            sniffingEnabled = true,
            bypassLan = false,
            muxEnabled = false,
            muxConcurrency = " 10 ",
            muxQuicPolicy = " reject "
        )
        assertEquals(base, withWhitespace)
        assertFalse(base.isDirty(withWhitespace))
    }

    // =========================================================================
    // 5. Features & Diagnostics Settings Snapshot
    // =========================================================================

    @Test
    fun `Features snapshot - clean state is not dirty`() {
        val initial = FeaturesSettingsSnapshot.create(
            connectivityCheck = true,
            pingUrl = "https://www.google.com/generate_204",
            subUa = "v2rayNG/1.8.23",
            strictRedaction = true,
            bypassedDomains = setOf(".ir"),
            allowLanHotspot = false
        )
        val current = FeaturesSettingsSnapshot.create(
            connectivityCheck = true,
            pingUrl = "https://www.google.com/generate_204",
            subUa = "v2rayNG/1.8.23",
            strictRedaction = true,
            bypassedDomains = setOf(".ir"),
            allowLanHotspot = false
        )
        assertEquals(initial, current)
        assertFalse(initial.isDirty(current))
    }

    @Test
    fun `Features snapshot - editing each field triggers dirty state and reverts cleanly`() {
        val initial = FeaturesSettingsSnapshot.create(
            connectivityCheck = true,
            pingUrl = "https://www.google.com/generate_204",
            subUa = "v2rayNG/1.8.23",
            strictRedaction = true,
            bypassedDomains = setOf(".ir"),
            allowLanHotspot = false
        )

        // Adding a domain
        val domainAdded = initial.copy(bypassedDomains = initial.bypassedDomains + "example.com")
        assertNotEquals(initial, domainAdded)
        assertTrue(initial.isDirty(domainAdded))

        // Removing default .ir domain
        val domainRemoved = initial.copy(bypassedDomains = emptySet())
        assertNotEquals(initial, domainRemoved)
        assertTrue(initial.isDirty(domainRemoved))

        // Adding .ir back
        val domainReverted = domainRemoved.copy(bypassedDomains = setOf(".ir"))
        assertEquals(initial, domainReverted)
        assertFalse(initial.isDirty(domainReverted))

        // connectivityCheck toggled
        val connectivityChanged = initial.copy(connectivityCheck = false)
        assertNotEquals(initial, connectivityChanged)
        assertTrue(initial.isDirty(connectivityChanged))

        // strictRedaction toggled
        val redactionChanged = initial.copy(strictRedaction = false)
        assertNotEquals(initial, redactionChanged)
        assertTrue(initial.isDirty(redactionChanged))

        // allowLanHotspot toggled
        val hotspotChanged = initial.copy(allowLanHotspot = true)
        assertNotEquals(initial, hotspotChanged)
        assertTrue(initial.isDirty(hotspotChanged))

        // pingUrl modified
        val pingChanged = initial.copy(pingUrl = "http://cp.cloudflare.com/generate_204")
        assertNotEquals(initial, pingChanged)
        assertTrue(initial.isDirty(pingChanged))

        // subUa modified
        val uaChanged = initial.copy(subUa = "HybridNest/1.0")
        assertNotEquals(initial, uaChanged)
        assertTrue(initial.isDirty(uaChanged))

        // Reverting all back to initial
        val allReverted = uaChanged.copy(subUa = "v2rayNG/1.8.23")
        assertEquals(initial, allReverted)
        assertFalse(initial.isDirty(allReverted))
    }

    @Test
    fun `Features snapshot - whitespace and domain set order normalization`() {
        val base = FeaturesSettingsSnapshot.create(
            connectivityCheck = true,
            pingUrl = "https://www.google.com/generate_204",
            subUa = "v2rayNG/1.8.23",
            strictRedaction = true,
            bypassedDomains = linkedSetOf("alpha.ir", "beta.ir"),
            allowLanHotspot = false
        )
        val withWhitespace = FeaturesSettingsSnapshot.create(
            connectivityCheck = true,
            pingUrl = "  https://www.google.com/generate_204\n",
            subUa = " v2rayNG/1.8.23 ",
            strictRedaction = true,
            bypassedDomains = linkedSetOf("beta.ir", "alpha.ir"),
            allowLanHotspot = false
        )
        assertEquals(base, withWhitespace)
        assertFalse(base.isDirty(withWhitespace))
    }
}
