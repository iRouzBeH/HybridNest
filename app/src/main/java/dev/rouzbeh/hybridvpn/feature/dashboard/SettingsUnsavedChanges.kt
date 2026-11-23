package dev.rouzbeh.hybridvpn.feature.dashboard

/**
 * Outcome resolution when attempting to exit a settings screen.
 */
enum class SettingsExitDecision {
    EXIT_IMMEDIATELY,
    PROMPT_UNSAVED_CHANGES
}

/**
 * Pure evaluator for deciding whether to exit directly or prompt for unsaved changes.
 */
fun resolveSettingsExit(hasUnsavedChanges: Boolean): SettingsExitDecision {
    return if (hasUnsavedChanges) {
        SettingsExitDecision.PROMPT_UNSAVED_CHANGES
    } else {
        SettingsExitDecision.EXIT_IMMEDIATELY
    }
}

/**
 * Immutable snapshot of editable fields in [AnyConnectSettingsFullScreen].
 */
data class AnyConnectSettingsSnapshot(
    val dtlsEnabled: Boolean,
    val mtu: String,
    val dpdTimeout: String,
    val fallbackDns: String
) {
    fun normalized(): AnyConnectSettingsSnapshot = copy(
        mtu = mtu.trim(),
        dpdTimeout = dpdTimeout.trim(),
        fallbackDns = fallbackDns.trim()
    )

    companion object {
        fun create(
            dtlsEnabled: Boolean,
            mtu: String,
            dpdTimeout: String,
            fallbackDns: String
        ): AnyConnectSettingsSnapshot = AnyConnectSettingsSnapshot(
            dtlsEnabled = dtlsEnabled,
            mtu = mtu.trim(),
            dpdTimeout = dpdTimeout.trim(),
            fallbackDns = fallbackDns.trim()
        )

        fun from(
            dtlsEnabled: Boolean,
            mtu: String,
            dpdTimeout: String,
            fallbackDns: String
        ): AnyConnectSettingsSnapshot = create(dtlsEnabled, mtu, dpdTimeout, fallbackDns)
    }
}

fun AnyConnectSettingsSnapshot.isDirty(current: AnyConnectSettingsSnapshot): Boolean =
    this.normalized() != current.normalized()

/**
 * Immutable snapshot of editable fields in [WireGuardSettingsFullScreen].
 */
data class WireGuardSettingsSnapshot(
    val mtuOverride: String,
    val keepaliveOverride: String,
    val tunnelDns: String,
    val bootstrapResolve: Boolean
) {
    fun normalized(): WireGuardSettingsSnapshot = copy(
        mtuOverride = mtuOverride.trim(),
        keepaliveOverride = keepaliveOverride.trim(),
        tunnelDns = tunnelDns.trim(),
        bootstrapResolve = bootstrapResolve
    )

    companion object {
        fun create(
            mtuOverride: String,
            keepaliveOverride: String,
            tunnelDns: String,
            bootstrapResolve: Boolean
        ): WireGuardSettingsSnapshot = WireGuardSettingsSnapshot(
            mtuOverride = mtuOverride.trim(),
            keepaliveOverride = keepaliveOverride.trim(),
            tunnelDns = tunnelDns.trim(),
            bootstrapResolve = bootstrapResolve
        )

        fun from(
            mtuOverride: String,
            keepaliveOverride: String,
            tunnelDns: String,
            bootstrapResolve: Boolean
        ): WireGuardSettingsSnapshot = create(mtuOverride, keepaliveOverride, tunnelDns, bootstrapResolve)
    }
}

fun WireGuardSettingsSnapshot.isDirty(current: WireGuardSettingsSnapshot): Boolean =
    this.normalized() != current.normalized()

/**
 * Immutable snapshot of editable fields in [V2raySettingsFullScreen].
 */
data class V2raySettingsSnapshot(
    val vpnDns: String,
    val remoteDns: String,
    val domesticDns: String,
    val sniffingEnabled: Boolean,
    val bypassLan: Boolean,
    val muxEnabled: Boolean,
    val muxConcurrency: String,
    val muxQuicPolicy: String
) {
    fun normalized(): V2raySettingsSnapshot = copy(
        vpnDns = vpnDns.trim(),
        remoteDns = remoteDns.trim(),
        domesticDns = domesticDns.trim(),
        sniffingEnabled = sniffingEnabled,
        bypassLan = bypassLan,
        muxEnabled = muxEnabled,
        muxConcurrency = muxConcurrency.trim(),
        muxQuicPolicy = muxQuicPolicy.trim()
    )

    companion object {
        fun create(
            vpnDns: String,
            remoteDns: String,
            domesticDns: String,
            sniffingEnabled: Boolean,
            bypassLan: Boolean,
            muxEnabled: Boolean,
            muxConcurrency: String,
            muxQuicPolicy: String
        ): V2raySettingsSnapshot = V2raySettingsSnapshot(
            vpnDns = vpnDns.trim(),
            remoteDns = remoteDns.trim(),
            domesticDns = domesticDns.trim(),
            sniffingEnabled = sniffingEnabled,
            bypassLan = bypassLan,
            muxEnabled = muxEnabled,
            muxConcurrency = muxConcurrency.trim(),
            muxQuicPolicy = muxQuicPolicy.trim()
        )

        fun from(
            vpnDns: String,
            remoteDns: String,
            domesticDns: String,
            sniffingEnabled: Boolean,
            bypassLan: Boolean,
            muxEnabled: Boolean,
            muxConcurrency: String,
            muxQuicPolicy: String
        ): V2raySettingsSnapshot = create(
            vpnDns,
            remoteDns,
            domesticDns,
            sniffingEnabled,
            bypassLan,
            muxEnabled,
            muxConcurrency,
            muxQuicPolicy
        )
    }
}

fun V2raySettingsSnapshot.isDirty(current: V2raySettingsSnapshot): Boolean =
    this.normalized() != current.normalized()

/**
 * Immutable snapshot of editable fields in [FeaturesFullScreen].
 */
data class FeaturesSettingsSnapshot(
    val connectivityCheck: Boolean,
    val pingUrl: String,
    val subUa: String,
    val strictRedaction: Boolean,
    val bypassedDomains: Set<String>,
    val allowLanHotspot: Boolean
) {
    fun normalized(): FeaturesSettingsSnapshot = copy(
        pingUrl = pingUrl.trim(),
        subUa = subUa.trim(),
        bypassedDomains = bypassedDomains
    )

    companion object {
        fun create(
            connectivityCheck: Boolean,
            pingUrl: String,
            subUa: String,
            strictRedaction: Boolean,
            bypassedDomains: Set<String>,
            allowLanHotspot: Boolean
        ): FeaturesSettingsSnapshot = FeaturesSettingsSnapshot(
            connectivityCheck = connectivityCheck,
            pingUrl = pingUrl.trim(),
            subUa = subUa.trim(),
            strictRedaction = strictRedaction,
            bypassedDomains = bypassedDomains,
            allowLanHotspot = allowLanHotspot
        )

        fun from(
            connectivityCheck: Boolean,
            pingUrl: String,
            subUa: String,
            strictRedaction: Boolean,
            bypassedDomains: Set<String>,
            allowLanHotspot: Boolean
        ): FeaturesSettingsSnapshot = create(
            connectivityCheck,
            pingUrl,
            subUa,
            strictRedaction,
            bypassedDomains,
            allowLanHotspot
        )
    }
}

fun FeaturesSettingsSnapshot.isDirty(current: FeaturesSettingsSnapshot): Boolean =
    this.normalized() != current.normalized()
