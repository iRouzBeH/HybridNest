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

data class V2rayPingProgressState(
    val batchByGroup: Map<String, Set<Int>> = emptyMap(),
    val singleIds: Set<Int> = emptySet()
) {
    val activeProfileIds: Set<Int>
        get() {
            if (batchByGroup.isEmpty()) return singleIds
            if (singleIds.isEmpty() && batchByGroup.size == 1) return batchByGroup.values.first()
            val all = HashSet<Int>(singleIds)
            for (set in batchByGroup.values) {
                all.addAll(set)
            }
            return all
        }

    fun startBatch(groupName: String, profileIds: Set<Int>): V2rayPingProgressState {
        if (profileIds.isEmpty()) {
            if (!batchByGroup.containsKey(groupName)) return this
            return copy(batchByGroup = batchByGroup - groupName)
        }
        return copy(batchByGroup = batchByGroup + (groupName to profileIds))
    }

    fun finishProfileInBatch(groupName: String, profileId: Int): V2rayPingProgressState {
        val currentGroupIds = batchByGroup[groupName] ?: return this
        if (!currentGroupIds.contains(profileId)) return this
        val nextGroupIds = currentGroupIds - profileId
        return if (nextGroupIds.isEmpty()) {
            copy(batchByGroup = batchByGroup - groupName)
        } else {
            copy(batchByGroup = batchByGroup + (groupName to nextGroupIds))
        }
    }

    fun clearBatch(groupName: String): V2rayPingProgressState {
        if (!batchByGroup.containsKey(groupName)) return this
        return copy(batchByGroup = batchByGroup - groupName)
    }

    fun startSingle(profileId: Int): V2rayPingProgressState {
        if (singleIds.contains(profileId)) return this
        return copy(singleIds = singleIds + profileId)
    }

    fun finishSingle(profileId: Int): V2rayPingProgressState {
        if (!singleIds.contains(profileId)) return this
        return copy(singleIds = singleIds - profileId)
    }
}

