package dev.rouzbeh.hybridvpn.feature.dashboard

import dev.rouzbeh.hybridvpn.data.local.VpnProfile

object ProtocolSwitchResolver {
    /**
     * Resolves the active profile to display on the dashboard UI.
     * Prioritizes Room's persisted active profile if it matches the selected protocol.
     * Falls back to the pending/optimistic restored profile if it matches the selected protocol.
     * Returns null if neither matches the selected protocol.
     */
    fun resolveDisplayedActiveProfile(
        selectedProtocol: String,
        roomActiveProfile: VpnProfile?,
        pendingRestoredProfile: VpnProfile?
    ): VpnProfile? {
        return when {
            roomActiveProfile != null && roomActiveProfile.protocol == selectedProtocol -> roomActiveProfile
            pendingRestoredProfile != null && pendingRestoredProfile.protocol == selectedProtocol -> pendingRestoredProfile
            else -> null
        }
    }

    /**
     * Determines whether the pending restored profile should be cleared.
     * Returns true if pending profile is obsolete (its protocol doesn't match selectedProtocol),
     * or if Room has caught up and its active profile now matches the pending profile ID and protocol.
     * Returns false if pendingRestoredProfile is null or still waiting for Room to catch up.
     */
    fun shouldClearPendingProfile(
        selectedProtocol: String,
        roomActiveProfile: VpnProfile?,
        pendingRestoredProfile: VpnProfile?
    ): Boolean {
        if (pendingRestoredProfile == null) return false
        if (pendingRestoredProfile.protocol != selectedProtocol) return true
        if (roomActiveProfile != null && roomActiveProfile.id == pendingRestoredProfile.id && roomActiveProfile.protocol == selectedProtocol) {
            return true
        }
        return false
    }
}
