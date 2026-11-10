package dev.rouzbeh.hybridvpn.core.routing

/**
 * Immutable snapshot of the user's per-app routing configuration.
 *
 * The global settings remain the source of truth; this snapshot is captured once
 * per connection session so that edits made while a tunnel is up never mutate the
 * live TUN configuration (they are marked "applies after reconnect" instead).
 *
 * This class is intentionally free of Android dependencies so the resolution
 * semantics can be unit-tested on the JVM.
 *
 * Semantics (authoritative):
 *  - [enabled] == false  -> global VPN routing for every app (protocol transport
 *    exceptions are applied by the transport layer, never here).
 *  - [Mode.ALLOW]        -> only the selected user apps are routed through the VPN.
 *  - [Mode.BYPASS]       -> the selected apps bypass the VPN; all other apps are routed.
 */
data class RoutingPolicy(
    val enabled: Boolean,
    val mode: Mode,
    val packages: Set<String>,
    val revision: Long,
) {
    enum class Mode { ALLOW, BYPASS }

    fun describe(): String =
        if (!enabled) {
            "GLOBAL (per-app disabled)"
        } else {
            "enabled mode=${mode.name} packages=${packages.size}"
        }

    companion object {
        fun disabled(revision: Long): RoutingPolicy =
            RoutingPolicy(
                enabled = false,
                mode = Mode.BYPASS,
                packages = emptySet(),
                revision = revision
            )
    }
}

/**
 * The concrete Builder-level routing decision derived from a [RoutingPolicy].
 *
 * Structural guarantee: each decision maps to exactly ONE VpnService.Builder API
 * family, so a builder can never receive both addAllowedApplication and
 * addDisallowedApplication calls from policy application.
 */
sealed class RoutingDecision {
    abstract fun describe(): String

    /** No per-app rules; global routing. */
    object Global : RoutingDecision() {
        override fun describe(): String = "GLOBAL routing (no per-app rules)"
    }

    /**
     * Apply addAllowedApplication for each package in [allowedPackages].
     * [prunedPackages] were selected but not installed (reported, never applied).
     */
    data class Allow(
        val allowedPackages: Set<String>,
        val prunedPackages: Set<String>
    ) : RoutingDecision() {
        override fun describe(): String = buildString {
            append("ALLOW ").append(allowedPackages.size).append(" app(s)")
            if (prunedPackages.isNotEmpty()) {
                append(", pruned(uninstalled)=").append(prunedPackages.sorted())
            }
        }
    }

    /**
     * Apply addDisallowedApplication for each package in [disallowedPackages].
     * [prunedPackages] were selected but not installed (reported, never applied).
     */
    data class Bypass(
        val disallowedPackages: Set<String>,
        val prunedPackages: Set<String>
    ) : RoutingDecision() {
        override fun describe(): String = buildString {
            append("BYPASS ").append(disallowedPackages.size).append(" app(s)")
            if (prunedPackages.isNotEmpty()) {
                append(", pruned(uninstalled)=").append(prunedPackages.sorted())
            }
        }
    }

    /** Allow mode requested but no usable apps exist: the caller MUST abort, never fall back. */
    data class Refused(val reason: String) : RoutingDecision() {
        override fun describe(): String = "REFUSED: $reason"
    }
}

/**
 * Thrown when a requested policy cannot be applied safely (e.g. allow mode with no
 * usable selected apps). Callers must abort the connection and surface the reason;
 * silently falling back to global routing is forbidden.
 */
class RoutingRefusedException(message: String) : IllegalStateException(message)

/**
 * Resolves a [RoutingPolicy] against the set of currently installed packages.
 *
 * Contract:
 *  - Disabled policy -> [RoutingDecision.Global] (no per-app Builder calls).
 *  - ALLOW with zero usable (selected AND installed) apps -> [RoutingDecision.Refused].
 *    Callers must abort the connection; silently widening to global is forbidden.
 *  - ALLOW with some usable apps -> allow only the usable set; stale selections are
 *    reported in [RoutingDecision.Allow.prunedPackages] and never widen the policy.
 *  - BYPASS with zero usable apps -> [RoutingDecision.Global] (nothing to bypass).
 *  - BYPASS with some usable apps -> bypass the usable set; stale selections are pruned
 *    (an uninstalled app cannot generate traffic, so pruning never widens the policy)
 *    and reported.
 */
object RoutingPolicyResolver {

    /**
     * Resolves the policy at tunnel start. If [policy.enabled] is false (Global mode),
     * unconditionally returns [RoutingDecision.Global] without any fallback override.
     */
    fun resolveForTunnelStart(policy: RoutingPolicy, installedPackages: Set<String>): RoutingDecision =
        resolve(policy, installedPackages)

    fun resolve(policy: RoutingPolicy, installedPackages: Set<String>): RoutingDecision {
        if (!policy.enabled) return RoutingDecision.Global

        val usable = policy.packages.intersect(installedPackages)
        val pruned = policy.packages - usable

        return when (policy.mode) {
            RoutingPolicy.Mode.ALLOW ->
                if (usable.isEmpty()) {
                    RoutingDecision.Refused(
                        "'Only selected apps use VPN' is on, but none of the selected apps " +
                            "are installed. Select at least one installed app or disable per-app routing."
                    )
                } else {
                    RoutingDecision.Allow(allowedPackages = usable, prunedPackages = pruned)
                }

            RoutingPolicy.Mode.BYPASS ->
                if (usable.isEmpty()) {
                    RoutingDecision.Global
                } else {
                    RoutingDecision.Bypass(disallowedPackages = usable, prunedPackages = pruned)
                }
        }
    }
}
