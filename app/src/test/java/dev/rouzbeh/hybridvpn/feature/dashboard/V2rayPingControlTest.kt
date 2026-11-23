package dev.rouzbeh.hybridvpn.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2rayPingControlTest {

    @Test
    fun `1 SingleLinks tab idle hides update subscription, stop ping action, and stop banner`() {
        val state = V2rayTabMenuPolicy.resolveMenuState(
            selectedTab = V2rayTabOrderResolver.SINGLE_LINKS_TAB,
            activePingTabs = emptySet()
        )
        assertFalse("showUpdateSubscription must be false for SingleLinks", state.showUpdateSubscription)
        assertFalse("showStopPingAction must be false when SingleLinks is idle", state.showStopPingAction)
        assertFalse("showStopPingBanner must be false when SingleLinks is idle", state.showStopPingBanner)
    }

    @Test
    fun `2 SingleLinks tab pinging hides update subscription and shows stop ping action and stop banner`() {
        val state = V2rayTabMenuPolicy.resolveMenuState(
            selectedTab = V2rayTabOrderResolver.SINGLE_LINKS_TAB,
            activePingTabs = setOf(V2rayTabOrderResolver.SINGLE_LINKS_TAB)
        )
        assertFalse("showUpdateSubscription must be false for SingleLinks", state.showUpdateSubscription)
        assertTrue("showStopPingAction must be true when SingleLinks is pinging", state.showStopPingAction)
        assertTrue("showStopPingBanner must be true when SingleLinks is pinging", state.showStopPingBanner)
    }

    @Test
    fun `3 subscription tab idle shows update subscription and hides stop ping action and banner`() {
        val subTab = "sub:VIP"
        val state = V2rayTabMenuPolicy.resolveMenuState(
            selectedTab = subTab,
            activePingTabs = emptySet()
        )
        assertTrue("showUpdateSubscription must be true on subscription tab", state.showUpdateSubscription)
        assertFalse("showStopPingAction must be false on subscription tab", state.showStopPingAction)
        assertFalse("showStopPingBanner must be false on subscription tab", state.showStopPingBanner)
    }

    @Test
    fun `4 subscription tab pinging shows update subscription and keeps banner hidden`() {
        val subTab = "sub:VIP"
        val state = V2rayTabMenuPolicy.resolveMenuState(
            selectedTab = subTab,
            activePingTabs = setOf(subTab)
        )
        assertTrue("showUpdateSubscription must be true on subscription tab", state.showUpdateSubscription)
        assertFalse("showStopPingAction must be false for subscription pinging", state.showStopPingAction)
        assertFalse("showStopPingBanner must be false for subscription pinging", state.showStopPingBanner)
    }

    @Test
    fun `5 subscription tab selected while SingleLinks is pinging in background does not show banner on subscription`() {
        val subTab = "sub:VIP"
        val state = V2rayTabMenuPolicy.resolveMenuState(
            selectedTab = subTab,
            activePingTabs = setOf(V2rayTabOrderResolver.SINGLE_LINKS_TAB)
        )
        assertTrue("showUpdateSubscription must be true on subscription tab", state.showUpdateSubscription)
        assertFalse("showStopPingAction must be false when subscription is selected", state.showStopPingAction)
        assertFalse("showStopPingBanner must be false on subscription tab even if SingleLinks is pinging in background", state.showStopPingBanner)
    }

    @Test
    fun `6 SingleLinks tab selected while a subscription tab is pinging stays idle`() {
        val state = V2rayTabMenuPolicy.resolveMenuState(
            selectedTab = V2rayTabOrderResolver.SINGLE_LINKS_TAB,
            activePingTabs = setOf("sub:VIP", "sub:Pro")
        )
        assertFalse("showUpdateSubscription must be false on SingleLinks", state.showUpdateSubscription)
        assertFalse("showStopPingAction must be false when SingleLinks is not the one pinging", state.showStopPingAction)
        assertFalse("showStopPingBanner must be false when SingleLinks is not pinging", state.showStopPingBanner)
    }

    @Test
    fun `7 PingProgress clamps edge cases and prevents division by zero or NaN`() {
        // Zero total
        val zeroTotal = PingProgress(completed = 0, total = 0)
        assertEquals(0, zeroTotal.safeTotal)
        assertEquals(0, zeroTotal.safeCompleted)
        assertEquals(0f, zeroTotal.fraction, 0.0001f)
        assertFalse(zeroTotal.isComplete)

        // Negative values
        val negativeValues = PingProgress(completed = -5, total = -10)
        assertEquals(0, negativeValues.safeTotal)
        assertEquals(0, negativeValues.safeCompleted)
        assertEquals(0f, negativeValues.fraction, 0.0001f)
        assertFalse(negativeValues.isComplete)

        // Negative completed with positive total
        val negCompleted = PingProgress(completed = -3, total = 100)
        assertEquals(100, negCompleted.safeTotal)
        assertEquals(0, negCompleted.safeCompleted)
        assertEquals(0f, negCompleted.fraction, 0.0001f)
        assertFalse(negCompleted.isComplete)

        // Completed > Total clamps safely to 1.0f
        val overflow = PingProgress(completed = 150, total = 100)
        assertEquals(100, overflow.safeTotal)
        assertEquals(100, overflow.safeCompleted)
        assertEquals(1.0f, overflow.fraction, 0.0001f)
        assertTrue(overflow.isComplete)
    }

    @Test
    fun `8 PingProgress normal progression computes fraction and completion status accurately`() {
        val p0 = PingProgress(completed = 0, total = 1000)
        assertEquals(0, p0.safeCompleted)
        assertEquals(1000, p0.safeTotal)
        assertEquals(0.0f, p0.fraction, 0.0001f)
        assertFalse(p0.isComplete)

        val p250 = PingProgress(completed = 250, total = 1000)
        assertEquals(250, p250.safeCompleted)
        assertEquals(1000, p250.safeTotal)
        assertEquals(0.25f, p250.fraction, 0.0001f)
        assertFalse(p250.isComplete)

        val p750 = PingProgress(completed = 750, total = 1000)
        assertEquals(750, p750.safeCompleted)
        assertEquals(1000, p750.safeTotal)
        assertEquals(0.75f, p750.fraction, 0.0001f)
        assertFalse(p750.isComplete)

        val p1000 = PingProgress(completed = 1000, total = 1000)
        assertEquals(1000, p1000.safeCompleted)
        assertEquals(1000, p1000.safeTotal)
        assertEquals(1.0f, p1000.fraction, 0.0001f)
        assertTrue(p1000.isComplete)
    }

    @Test
    fun `9 V2rayPingProgressState starting a batch marks all target profile IDs active`() {
        val state = V2rayPingProgressState()
        assertTrue(state.activeProfileIds.isEmpty())

        val batchState = state.startBatch("SingleLinks", setOf(1, 2, 3))
        assertEquals(setOf(1, 2, 3), batchState.activeProfileIds)
        assertEquals(setOf(1, 2, 3), batchState.batchByGroup["SingleLinks"])
    }

    @Test
    fun `10 V2rayPingProgressState finishing one profile removes only that profile while remaining profiles in batch stay active`() {
        val batchState = V2rayPingProgressState().startBatch("sub:VIP", setOf(10, 20, 30))
        val afterOne = batchState.finishProfileInBatch("sub:VIP", 20)

        assertEquals(setOf(10, 30), afterOne.activeProfileIds)
        assertEquals(setOf(10, 30), afterOne.batchByGroup["sub:VIP"])

        val afterSecond = afterOne.finishProfileInBatch("sub:VIP", 10)
        assertEquals(setOf(30), afterSecond.activeProfileIds)

        // Finishing the last profile cleanly clears the group entry
        val afterLast = afterSecond.finishProfileInBatch("sub:VIP", 30)
        assertTrue(afterLast.activeProfileIds.isEmpty())
        assertFalse(afterLast.batchByGroup.containsKey("sub:VIP"))
    }

    @Test
    fun `11 V2rayPingProgressState concurrent batches on two different groups remain independent and clearing TabA does not clear TabB`() {
        val s1 = V2rayPingProgressState()
            .startBatch("TabA", setOf(100, 101))
            .startBatch("TabB", setOf(200, 201))

        assertEquals(setOf(100, 101, 200, 201), s1.activeProfileIds)

        val s2 = s1.clearBatch("TabA")
        assertEquals(setOf(200, 201), s2.activeProfileIds)
        assertFalse(s2.batchByGroup.containsKey("TabA"))
        assertEquals(setOf(200, 201), s2.batchByGroup["TabB"])
    }

    @Test
    fun `12 V2rayPingProgressState stopping or clearing a batch immediately clears all remaining IDs in that group`() {
        val s = V2rayPingProgressState().startBatch("SingleLinks", setOf(1, 2, 3, 4, 5))
        assertEquals(5, s.activeProfileIds.size)

        val stopped = s.clearBatch("SingleLinks")
        assertTrue("Stopping a batch must immediately clear all remaining IDs", stopped.activeProfileIds.isEmpty())
        assertFalse(stopped.batchByGroup.containsKey("SingleLinks"))
    }

    @Test
    fun `13 V2rayPingProgressState single ping IDs remain independent of batch IDs across finish and clear operations`() {
        val s = V2rayPingProgressState()
            .startBatch("TabA", setOf(1, 2))
            .startSingle(99)

        assertEquals(setOf(1, 2, 99), s.activeProfileIds)
        assertEquals(setOf(99), s.singleIds)

        // Clearing batch does not prematurely clear single ping
        val clearedBatch = s.clearBatch("TabA")
        assertEquals(setOf(99), clearedBatch.activeProfileIds)
        assertEquals(setOf(99), clearedBatch.singleIds)

        // Finishing single ping does not alter batch IDs
        val restoredBatch = s.finishSingle(99)
        assertEquals(setOf(1, 2), restoredBatch.activeProfileIds)
        assertTrue(restoredBatch.singleIds.isEmpty())
    }

    @Test
    fun `14 V2rayPingProgressState edge cases and idempotent transitions handle non-existent IDs and empty sets safely`() {
        val initial = V2rayPingProgressState()

        // Finishing non-existent single id is a no-op
        val s1 = initial.finishSingle(123)
        assertEquals(initial, s1)

        // Finishing non-existent batch id or non-existent group is a no-op
        val s2 = initial.finishProfileInBatch("non_existent", 456)
        assertEquals(initial, s2)

        val sWithBatch = initial.startBatch("TabA", setOf(10))
        val s3 = sWithBatch.finishProfileInBatch("TabA", 999) // id not in group
        assertEquals(sWithBatch, s3)

        // Clearing non-existent group is a no-op
        val s4 = initial.clearBatch("ghost")
        assertEquals(initial, s4)

        // Starting single with existing id is idempotent
        val s5 = initial.startSingle(50).startSingle(50)
        assertEquals(setOf(50), s5.singleIds)

        // Starting batch with empty set safely handles empty
        val s6 = initial.startBatch("emptyGroup", emptySet())
        assertTrue(s6.activeProfileIds.isEmpty())
        assertFalse(s6.batchByGroup.containsKey("emptyGroup"))
    }
}
