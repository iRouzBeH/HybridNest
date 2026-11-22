package dev.rouzbeh.hybridvpn.feature.dashboard

import org.json.JSONArray
import java.util.Collections

/**
 * Pure JVM resolver for v2rayNG tab ordering, selection resolution,
 * and JSON serialization / legacy comma-separated deserialization.
 */
object V2rayTabOrderResolver {
    const val SINGLE_LINKS_TAB = "SingleLinks"

    fun resolveOrderedTabs(savedOrder: List<String>, availableSubKeys: List<String>): List<String> {
        val availableTabs = listOf(SINGLE_LINKS_TAB) + availableSubKeys.filter {
            it.isNotBlank() && it != SINGLE_LINKS_TAB
        }.distinct()
        val availableSet = availableTabs.toSet()
        val inSaved = savedOrder.filter { it.isNotBlank() }.distinct().filter { it in availableSet }
        val inSavedSet = inSaved.toSet()
        val remaining = availableTabs.filter { it !in inSavedSet }
        return inSaved + remaining
    }

    fun resolveEffectiveSelectedTab(savedSelectedTab: String?, orderedTabs: List<String>): String {
        if (!savedSelectedTab.isNullOrBlank() && savedSelectedTab in orderedTabs) {
            return savedSelectedTab
        }
        return orderedTabs.firstOrNull() ?: SINGLE_LINKS_TAB
    }

    fun moveTab(orderedTabs: List<String>, tabKey: String, delta: Int): List<String> {
        val index = orderedTabs.indexOf(tabKey)
        if (index == -1) return orderedTabs
        val target = index + delta
        if (target !in orderedTabs.indices) return orderedTabs
        val result = orderedTabs.toMutableList()
        Collections.swap(result, index, target)
        return result
    }

    fun mergeMovedOrderIntoSaved(savedOrder: List<String>, movedVisibleOrder: List<String>): List<String> {
        val visibleClean = movedVisibleOrder.filter { it.isNotBlank() }.distinct()
        val visibleSet = visibleClean.toSet()
        val savedRemaining = savedOrder.filter { it.isNotBlank() && it !in visibleSet }.distinct()
        return visibleClean + savedRemaining
    }

    fun removeTabFromSavedOrder(savedOrder: List<String>, removedTabKey: String): List<String> {
        if (removedTabKey == SINGLE_LINKS_TAB) return savedOrder
        return savedOrder.filter { it != removedTabKey }
    }

    fun encodeTabOrder(order: List<String>): String {
        val array = JSONArray()
        order.filter { it.isNotBlank() }.distinct().forEach { array.put(it) }
        return array.toString()
    }

    fun decodeTabOrder(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("[")) {
            return try {
                val array = JSONArray(raw)
                val result = ArrayList<String>(array.length())
                for (i in 0 until array.length()) {
                    val item = array.optString(i)
                    if (!item.isNullOrBlank()) {
                        result.add(item)
                    }
                }
                result.distinct()
            } catch (_: Exception) {
                emptyList()
            }
        }
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
}
