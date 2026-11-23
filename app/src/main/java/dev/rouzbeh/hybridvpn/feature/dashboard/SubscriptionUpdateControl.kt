package dev.rouzbeh.hybridvpn.feature.dashboard

/**
 * Phase of an individual subscription update cycle.
 */
enum class SubscriptionUpdatePhase {
    IDLE,
    UPDATING,
    SUCCESS,
    FAILED
}

/**
 * Observable UI state for subscription update synchronization.
 * [isLocked] is true whenever [phase] is not [SubscriptionUpdatePhase.IDLE],
 * enforcing an interaction lock across the entire UI.
 */
data class SubscriptionUpdateState(
    val phase: SubscriptionUpdatePhase = SubscriptionUpdatePhase.IDLE,
    val targetGroupName: String? = null,
    val targetDisplayName: String? = null,
    val detailText: String? = null
) {
    val isLocked: Boolean get() = phase != SubscriptionUpdatePhase.IDLE
}

/**
 * Pure JVM policy helpers and timing constants for subscription updates.
 */
object SubscriptionUpdatePolicy {
    const val MIN_UPDATING_ANIMATION_MS = 950L
    const val SUCCESS_DISPLAY_MS = 750L
    const val FAILURE_DISPLAY_MS = 850L
    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 20_000

    /**
     * Determines whether a new subscription update can be initiated.
     * Returns false if an update is already locked/running, if [targetGroupName] is blank,
     * or if [targetGroupName] is the SingleLinks tab.
     */
    fun canStartUpdate(currentState: SubscriptionUpdateState, targetGroupName: String): Boolean {
        if (currentState.isLocked) return false
        val trimmed = targetGroupName.trim()
        if (trimmed.isBlank() || trimmed == V2rayTabOrderResolver.SINGLE_LINKS_TAB) return false
        return true
    }

    /**
     * Calculates the remaining delay in milliseconds needed to guarantee a smooth
     * minimum updating animation duration of [minDurationMs].
     */
    fun remainingAnimationDelayMs(
        elapsedMs: Long,
        minDurationMs: Long = MIN_UPDATING_ANIMATION_MS
    ): Long {
        return (minDurationMs - elapsedMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    /**
     * Returns a safe, user-facing display name for the subscription overlay.
     * Raw URLs, token links, or blank names are never returned to prevent UI clutter or token leaks.
     */
    fun safeDisplayName(subName: String?, groupKey: String): String {
        val trimmedSub = subName?.trim()
        if (!trimmedSub.isNullOrBlank() && !trimmedSub.contains("://", ignoreCase = true)) {
            return trimmedSub
        }
        var cleaned = groupKey.trim()
        if (cleaned.startsWith("sub:", ignoreCase = true)) {
            cleaned = cleaned.substring(4).trim()
        }
        if (cleaned.isBlank() || cleaned.contains("://", ignoreCase = true)) {
            return "Subscription"
        }
        return cleaned
    }
}
