package dev.rouzbeh.hybridvpn.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionUpdateControlTest {

    @Test
    fun defaultState_isIdleAndUnlocked() {
        val state = SubscriptionUpdateState()
        assertEquals(SubscriptionUpdatePhase.IDLE, state.phase)
        assertFalse(state.isLocked)
        assertNull(state.targetGroupName)
        assertNull(state.targetDisplayName)
        assertNull(state.detailText)
    }

    @Test
    fun activePhases_reportLocked() {
        val updatingState = SubscriptionUpdateState(
            phase = SubscriptionUpdatePhase.UPDATING,
            targetGroupName = "sub:VIP",
            targetDisplayName = "VIP",
            detailText = "Syncing servers..."
        )
        val successState = SubscriptionUpdateState(
            phase = SubscriptionUpdatePhase.SUCCESS,
            targetGroupName = "sub:VIP",
            targetDisplayName = "VIP",
            detailText = "5 servers synced"
        )
        val failedState = SubscriptionUpdateState(
            phase = SubscriptionUpdatePhase.FAILED,
            targetGroupName = "sub:VIP",
            targetDisplayName = "VIP",
            detailText = "Could not refresh subscription"
        )

        assertTrue(updatingState.isLocked)
        assertTrue(successState.isLocked)
        assertTrue(failedState.isLocked)
    }

    @Test
    fun canStartUpdate_returnsTrueForValidGroupWhenIdle() {
        val idleState = SubscriptionUpdateState()
        assertTrue(SubscriptionUpdatePolicy.canStartUpdate(idleState, "sub:VIP"))
        assertTrue(SubscriptionUpdatePolicy.canStartUpdate(idleState, "Work"))
        assertTrue(SubscriptionUpdatePolicy.canStartUpdate(idleState, "sub:Gaming Servers"))
    }

    @Test
    fun canStartUpdate_returnsFalseWhenLocked_antiSpam() {
        val updatingState = SubscriptionUpdateState(phase = SubscriptionUpdatePhase.UPDATING)
        val successState = SubscriptionUpdateState(phase = SubscriptionUpdatePhase.SUCCESS)
        val failedState = SubscriptionUpdateState(phase = SubscriptionUpdatePhase.FAILED)

        assertFalse(SubscriptionUpdatePolicy.canStartUpdate(updatingState, "sub:VIP"))
        assertFalse(SubscriptionUpdatePolicy.canStartUpdate(successState, "sub:VIP"))
        assertFalse(SubscriptionUpdatePolicy.canStartUpdate(failedState, "sub:VIP"))
    }

    @Test
    fun canStartUpdate_returnsFalseForSingleLinksAndBlankGroups() {
        val idleState = SubscriptionUpdateState()
        assertFalse(SubscriptionUpdatePolicy.canStartUpdate(idleState, "SingleLinks"))
        assertFalse(SubscriptionUpdatePolicy.canStartUpdate(idleState, V2rayTabOrderResolver.SINGLE_LINKS_TAB))
        assertFalse(SubscriptionUpdatePolicy.canStartUpdate(idleState, "  SingleLinks  "))
        assertFalse(SubscriptionUpdatePolicy.canStartUpdate(idleState, ""))
        assertFalse(SubscriptionUpdatePolicy.canStartUpdate(idleState, "   "))
    }

    @Test
    fun remainingAnimationDelayMs_computesAccurateDelayAndClamps() {
        assertEquals(950L, SubscriptionUpdatePolicy.remainingAnimationDelayMs(0L))
        assertEquals(700L, SubscriptionUpdatePolicy.remainingAnimationDelayMs(250L))
        assertEquals(0L, SubscriptionUpdatePolicy.remainingAnimationDelayMs(950L))
        assertEquals(0L, SubscriptionUpdatePolicy.remainingAnimationDelayMs(1500L))
        assertEquals(950L, SubscriptionUpdatePolicy.remainingAnimationDelayMs(-100L))
        assertEquals(200L, SubscriptionUpdatePolicy.remainingAnimationDelayMs(300L, minDurationMs = 500L))
    }

    @Test
    fun safeDisplayName_handlesUrlsSubPrefixAndFallbacks() {
        // Valid custom subName is preferred
        assertEquals("My VIP", SubscriptionUpdatePolicy.safeDisplayName("My VIP", "sub:https://example.com/sub"))
        assertEquals("Work Servers", SubscriptionUpdatePolicy.safeDisplayName("Work Servers", "sub:Work Servers"))

        // Raw URLs inside subName are ignored, fallback to groupKey
        assertEquals("VIP", SubscriptionUpdatePolicy.safeDisplayName("https://example.com/token", "sub:VIP"))

        // sub: prefix stripped cleanly
        assertEquals("VIP", SubscriptionUpdatePolicy.safeDisplayName(null, "sub:VIP"))
        assertEquals("VIP", SubscriptionUpdatePolicy.safeDisplayName("", "sub:VIP"))
        assertEquals("Personal", SubscriptionUpdatePolicy.safeDisplayName(null, "Personal"))

        // Raw URLs in groupKey fall back to "Subscription"
        assertEquals("Subscription", SubscriptionUpdatePolicy.safeDisplayName(null, "sub:https://example.com/sub"))
        assertEquals("Subscription", SubscriptionUpdatePolicy.safeDisplayName(null, "https://example.com/sub"))
        assertEquals("Subscription", SubscriptionUpdatePolicy.safeDisplayName("http://custom.url", "sub:http://custom.url"))

        // Blank or empty group keys fall back to "Subscription"
        assertEquals("Subscription", SubscriptionUpdatePolicy.safeDisplayName(null, "sub:"))
        assertEquals("Subscription", SubscriptionUpdatePolicy.safeDisplayName(null, "sub:   "))
        assertEquals("Subscription", SubscriptionUpdatePolicy.safeDisplayName(null, ""))
        assertEquals("Subscription", SubscriptionUpdatePolicy.safeDisplayName(null, "   "))
    }

    @Test
    fun constants_arePositiveAndBounded() {
        assertTrue(SubscriptionUpdatePolicy.CONNECT_TIMEOUT_MS in 5_000..30_000)
        assertTrue(SubscriptionUpdatePolicy.READ_TIMEOUT_MS in 5_000..30_000)
        assertTrue(SubscriptionUpdatePolicy.MIN_UPDATING_ANIMATION_MS in 500..3000)
        assertTrue(SubscriptionUpdatePolicy.SUCCESS_DISPLAY_MS in 300..2000)
        assertTrue(SubscriptionUpdatePolicy.FAILURE_DISPLAY_MS in 300..2000)
    }
}
