package dev.rouzbeh.hybridvpn.feature.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2rayTabOrderResolverTest {

    @Test
    fun `1 default order when savedOrder is empty places SingleLinks first followed by availableSubKeys`() {
        val availableSubKeys = listOf("sub:Alpha", "sub:Beta", "sub:Gamma")
        val result = V2rayTabOrderResolver.resolveOrderedTabs(
            savedOrder = emptyList(),
            availableSubKeys = availableSubKeys
        )
        assertEquals(
            listOf("SingleLinks", "sub:Alpha", "sub:Beta", "sub:Gamma"),
            result
        )

        // When both savedOrder and availableSubKeys are empty, only SingleLinks is returned
        val emptyResult = V2rayTabOrderResolver.resolveOrderedTabs(
            savedOrder = emptyList(),
            availableSubKeys = emptyList()
        )
        assertEquals(listOf("SingleLinks"), emptyResult)

        // Duplicate or blank keys in availableSubKeys are sanitized
        val dirtyKeys = listOf("sub:Alpha", "", "sub:Alpha", "SingleLinks", "sub:Beta")
        val sanitized = V2rayTabOrderResolver.resolveOrderedTabs(
            savedOrder = emptyList(),
            availableSubKeys = dirtyKeys
        )
        assertEquals(listOf("SingleLinks", "sub:Alpha", "sub:Beta"), sanitized)
    }

    @Test
    fun `2 custom tab order is preserved exactly as saved`() {
        val savedOrder = listOf("SingleLinks", "sub:Infinity", "sub:VIP")
        val availableSubKeys = listOf("sub:VIP", "sub:Infinity")
        val result = V2rayTabOrderResolver.resolveOrderedTabs(
            savedOrder = savedOrder,
            availableSubKeys = availableSubKeys
        )
        assertEquals(listOf("SingleLinks", "sub:Infinity", "sub:VIP"), result)
    }

    @Test
    fun `3 custom tab order preserved even when SingleLinks is moved to non-zero position`() {
        val savedOrder = listOf("sub:VIP", "SingleLinks", "sub:Infinity")
        val availableSubKeys = listOf("sub:Infinity", "sub:VIP")
        val result = V2rayTabOrderResolver.resolveOrderedTabs(
            savedOrder = savedOrder,
            availableSubKeys = availableSubKeys
        )
        assertEquals(listOf("sub:VIP", "SingleLinks", "sub:Infinity"), result)
        assertEquals(1, result.indexOf("SingleLinks"))
    }

    @Test
    fun `4 newly added subscriptions are appended after custom-ordered tabs without disturbing existing order`() {
        val savedOrder = listOf("sub:Beta", "SingleLinks", "sub:Alpha")
        val availableSubKeys = listOf("sub:Alpha", "sub:Beta", "sub:Gamma", "sub:Delta")
        val result = V2rayTabOrderResolver.resolveOrderedTabs(
            savedOrder = savedOrder,
            availableSubKeys = availableSubKeys
        )
        assertEquals(
            listOf("sub:Beta", "SingleLinks", "sub:Alpha", "sub:Gamma", "sub:Delta"),
            result
        )
    }

    @Test
    fun `5 transient empty availableSubKeys returns SingleLinks and restores custom order when keys return`() {
        val savedOrder = listOf("sub:VIP", "SingleLinks", "sub:Ultra")

        // Transient empty state (e.g. protocol switched to OpenConnect or mid-subscription update)
        val transient = V2rayTabOrderResolver.resolveOrderedTabs(
            savedOrder = savedOrder,
            availableSubKeys = emptyList()
        )
        assertEquals(listOf("SingleLinks"), transient)

        // When subKeys return (protocol switched back or update complete), exact saved order is restored
        val restored = V2rayTabOrderResolver.resolveOrderedTabs(
            savedOrder = savedOrder,
            availableSubKeys = listOf("sub:Ultra", "sub:VIP")
        )
        assertEquals(listOf("sub:VIP", "SingleLinks", "sub:Ultra"), restored)
    }

    @Test
    fun `6 resolveEffectiveSelectedTab keeps saved tab when present, falls back when absent, and restores on return`() {
        val orderedTabs = listOf("sub:A", "SingleLinks", "sub:B")

        // Saved tab exists in orderedTabs -> kept
        assertEquals("sub:B", V2rayTabOrderResolver.resolveEffectiveSelectedTab("sub:B", orderedTabs))
        assertEquals("SingleLinks", V2rayTabOrderResolver.resolveEffectiveSelectedTab("SingleLinks", orderedTabs))

        // Saved tab temporarily absent (e.g. sub:C not yet loaded) -> falls back to first visible tab
        assertEquals("sub:A", V2rayTabOrderResolver.resolveEffectiveSelectedTab("sub:C", orderedTabs))

        // Null, blank, or empty -> falls back to first visible tab
        assertEquals("sub:A", V2rayTabOrderResolver.resolveEffectiveSelectedTab(null, orderedTabs))
        assertEquals("sub:A", V2rayTabOrderResolver.resolveEffectiveSelectedTab("", orderedTabs))
        assertEquals("sub:A", V2rayTabOrderResolver.resolveEffectiveSelectedTab("   ", orderedTabs))

        // If orderedTabs is empty -> returns SINGLE_LINKS_TAB
        assertEquals(
            V2rayTabOrderResolver.SINGLE_LINKS_TAB,
            V2rayTabOrderResolver.resolveEffectiveSelectedTab("sub:C", emptyList())
        )

        // When absent tab returns to orderedTabs -> selected again
        val tabsWithC = listOf("sub:A", "SingleLinks", "sub:B", "sub:C")
        assertEquals("sub:C", V2rayTabOrderResolver.resolveEffectiveSelectedTab("sub:C", tabsWithC))
    }

    @Test
    fun `7 moveTab swaps left and right correctly and is safe no-op at boundaries or unknown keys`() {
        val tabs = listOf("SingleLinks", "sub:A", "sub:B")

        // Move sub:A left (swap with SingleLinks)
        val movedLeft = V2rayTabOrderResolver.moveTab(tabs, "sub:A", -1)
        assertEquals(listOf("sub:A", "SingleLinks", "sub:B"), movedLeft)

        // Move sub:A right (swap with sub:B)
        val movedRight = V2rayTabOrderResolver.moveTab(tabs, "sub:A", 1)
        assertEquals(listOf("SingleLinks", "sub:B", "sub:A"), movedRight)

        // Left boundary: cannot move index 0 left
        val atLeftBoundary = V2rayTabOrderResolver.moveTab(tabs, "SingleLinks", -1)
        assertEquals(tabs, atLeftBoundary)

        // Right boundary: cannot move last index right
        val atRightBoundary = V2rayTabOrderResolver.moveTab(tabs, "sub:B", 1)
        assertEquals(tabs, atRightBoundary)

        // Unknown key: no-op
        val unknown = V2rayTabOrderResolver.moveTab(tabs, "sub:Unknown", 1)
        assertEquals(tabs, unknown)

        // Delta 0: no-op
        val deltaZero = V2rayTabOrderResolver.moveTab(tabs, "sub:A", 0)
        assertEquals(tabs, deltaZero)
    }

    @Test
    fun `8 mergeMovedOrderIntoSaved preserves off-screen or absent keys while recording new visible order`() {
        val savedOrder = listOf("sub:Offline", "SingleLinks", "sub:A", "sub:B")
        val movedVisible = listOf("sub:B", "SingleLinks", "sub:A")

        val merged = V2rayTabOrderResolver.mergeMovedOrderIntoSaved(savedOrder, movedVisible)
        assertEquals(listOf("sub:B", "SingleLinks", "sub:A", "sub:Offline"), merged)
    }

    @Test
    fun `9 removeTabFromSavedOrder removes deleted subscription key and refuses to remove SingleLinks`() {
        val savedOrder = listOf("SingleLinks", "sub:A", "sub:B", "sub:C")

        // Remove sub:B
        val removedB = V2rayTabOrderResolver.removeTabFromSavedOrder(savedOrder, "sub:B")
        assertEquals(listOf("SingleLinks", "sub:A", "sub:C"), removedB)

        // Attempting to remove SingleLinks is rejected
        val removeSingle = V2rayTabOrderResolver.removeTabFromSavedOrder(savedOrder, "SingleLinks")
        assertEquals(savedOrder, removeSingle)

        // Removing non-existent key leaves list untouched
        val nonExistent = V2rayTabOrderResolver.removeTabFromSavedOrder(savedOrder, "sub:NonExistent")
        assertEquals(savedOrder, nonExistent)
    }

    @Test
    fun `10 encodeTabOrder and decodeTabOrder round-trip with special characters, emojis, legacy fallback and malformed safety`() {
        val complexTabs = listOf(
            "SingleLinks",
            "sub:https://example.com/sub?token=123,456&mode=fast#remark",
            "sub:کانفیگ ویژه",
            "sub:🚀 Speed: Fast!"
        )

        val encoded = V2rayTabOrderResolver.encodeTabOrder(complexTabs)
        assertTrue(encoded.startsWith("["))
        val decoded = V2rayTabOrderResolver.decodeTabOrder(encoded)
        assertEquals(complexTabs, decoded)

        // Legacy comma-separated format fallback from "tab_prefs"
        val legacyRaw = "SingleLinks,sub:Alpha, sub:Beta , sub:Gamma"
        val legacyDecoded = V2rayTabOrderResolver.decodeTabOrder(legacyRaw)
        assertEquals(listOf("SingleLinks", "sub:Alpha", "sub:Beta", "sub:Gamma"), legacyDecoded)

        // Legacy format deduplicates and ignores blanks
        val dirtyLegacy = "SingleLinks, sub:Alpha , , sub:Alpha, sub:Beta"
        val dirtyDecoded = V2rayTabOrderResolver.decodeTabOrder(dirtyLegacy)
        assertEquals(listOf("SingleLinks", "sub:Alpha", "sub:Beta"), dirtyDecoded)

        // Malformed JSON safety
        assertEquals(emptyList<String>(), V2rayTabOrderResolver.decodeTabOrder("[malformed json{"))
        assertEquals(emptyList<String>(), V2rayTabOrderResolver.decodeTabOrder("[\"incomplete"))

        // Null and blank safety
        assertEquals(emptyList<String>(), V2rayTabOrderResolver.decodeTabOrder(null))
        assertEquals(emptyList<String>(), V2rayTabOrderResolver.decodeTabOrder(""))
        assertEquals(emptyList<String>(), V2rayTabOrderResolver.decodeTabOrder("   "))

        // Empty JSON array
        assertEquals(emptyList<String>(), V2rayTabOrderResolver.decodeTabOrder("[]"))
        assertEquals(emptyList<String>(), V2rayTabOrderResolver.decodeTabOrder("[\"\", \"  \"]"))

        // encodeTabOrder filters blanks and duplicate entries
        val dirtyOrder = listOf("SingleLinks", "", "SingleLinks", "sub:Alpha", "  ")
        val cleanEncoded = V2rayTabOrderResolver.encodeTabOrder(dirtyOrder)
        val cleanDecoded = V2rayTabOrderResolver.decodeTabOrder(cleanEncoded)
        assertEquals(listOf("SingleLinks", "sub:Alpha"), cleanDecoded)
    }
}
