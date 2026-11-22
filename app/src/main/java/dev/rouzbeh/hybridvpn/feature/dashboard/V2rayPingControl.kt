package dev.rouzbeh.hybridvpn.feature.dashboard

data class PingProgress(val completed: Int, val total: Int) {
    val safeTotal: Int get() = total.coerceAtLeast(0)
    val safeCompleted: Int get() = completed.coerceIn(0, safeTotal)
    val fraction: Float get() = if (safeTotal <= 0) 0f else (safeCompleted.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    val isComplete: Boolean get() = safeTotal > 0 && safeCompleted >= safeTotal
}

data class V2rayTabMenuState(
    val showUpdateSubscription: Boolean,
    val showStopPingAction: Boolean,
    val showStopPingBanner: Boolean
)

object V2rayTabMenuPolicy {
    fun isSingleLinksTab(tabKey: String): Boolean = tabKey == V2rayTabOrderResolver.SINGLE_LINKS_TAB

    fun resolveMenuState(selectedTab: String, activePingTabs: Set<String>): V2rayTabMenuState {
        val isSingleLinks = isSingleLinksTab(selectedTab)
        val isSingleLinksPinging = isSingleLinks && activePingTabs.contains(V2rayTabOrderResolver.SINGLE_LINKS_TAB)
        return V2rayTabMenuState(
            showUpdateSubscription = !isSingleLinks,
            showStopPingAction = isSingleLinksPinging,
            showStopPingBanner = isSingleLinksPinging
        )
    }
}
