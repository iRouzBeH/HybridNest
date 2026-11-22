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
}
