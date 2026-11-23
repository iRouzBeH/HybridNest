package dev.rouzbeh.hybridvpn.feature.dashboard

/**
 * Filter mode for the Split Tunneling app list.
 */
enum class SplitAppFilter {
    ALL,
    SELECTED
}

/**
 * Pure Kotlin data holder for apps in Split Tunneling, enabling fast JVM unit testing
 * without requiring Android framework or Drawable mocks.
 */
data class SplitAppFilterEntry(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val isSelected: Boolean
)

/**
 * Generic filtering helper for Split Tunneling app lists.
 *
 * Rules:
 * - If [showSystemApps] is false, system apps are excluded UNLESS they are already selected ([isSelectedOf] == true).
 * - If [filter] is [SplitAppFilter.SELECTED], only selected apps are kept.
 * - If [query] (after trimming) is non-empty, matches case-insensitively against app name OR package name.
 */
fun <T> filterApps(
    items: List<T>,
    query: String,
    filter: SplitAppFilter = SplitAppFilter.ALL,
    showSystemApps: Boolean = false,
    nameOf: (T) -> String,
    packageOf: (T) -> String,
    isSystemOf: (T) -> Boolean,
    isSelectedOf: (T) -> Boolean
): List<T> {
    val trimmed = query.trim()
    return items.filter { item ->
        val isSystem = isSystemOf(item)
        val isSelected = isSelectedOf(item)

        // Preserves system app rule: if !showSystemApps, exclude system apps UNLESS already selected
        val matchesSystem = showSystemApps || !isSystem || isSelected
        if (!matchesSystem) return@filter false

        // Filter chip rule: SELECTED keeps only isSelected == true
        if (filter == SplitAppFilter.SELECTED && !isSelected) return@filter false

        // Search query rule: case-insensitive trimmed query match against name OR package
        if (trimmed.isNotEmpty()) {
            val nameMatches = nameOf(item).contains(trimmed, ignoreCase = true)
            val pkgMatches = packageOf(item).contains(trimmed, ignoreCase = true)
            if (!nameMatches && !pkgMatches) return@filter false
        }

        true
    }
}

/**
 * Convenience filter overload for [SplitAppFilterEntry] lists.
 */
fun filterAppEntries(
    items: List<SplitAppFilterEntry>,
    query: String,
    filter: SplitAppFilter = SplitAppFilter.ALL,
    showSystemApps: Boolean = false
): List<SplitAppFilterEntry> = filterApps(
    items = items,
    query = query,
    filter = filter,
    showSystemApps = showSystemApps,
    nameOf = { it.appName },
    packageOf = { it.packageName },
    isSystemOf = { it.isSystemApp },
    isSelectedOf = { it.isSelected }
)

/**
 * Calculates the number of apps visible under the current system-app visibility setting.
 */
fun <T> countVisibleApps(
    items: List<T>,
    showSystemApps: Boolean,
    isSystemOf: (T) -> Boolean,
    isSelectedOf: (T) -> Boolean
): Int = items.count { showSystemApps || !isSystemOf(it) || isSelectedOf(it) }

/**
 * Calculates the total number of selected apps.
 */
fun <T> countSelectedApps(
    items: List<T>,
    isSelectedOf: (T) -> Boolean
): Int = items.count { isSelectedOf(it) }

/**
 * Formats the live subtitle text displayed in the Split Tunneling header bar.
 *
 * Rules:
 * - If [totalLoadedCount] == 0 -> "Loading installed apps..."
 * - If ![isPerAppEnabled] -> "All apps use VPN"
 * - If [isBypassMode]:
 *     - [selectedCount] == 0 -> "Bypass mode • 0 apps selected"
 *     - [selectedCount] == 1 -> "Bypassing 1 app"
 *     - else -> "Bypassing $selectedCount apps"
 * - Else (Allow-only mode):
 *     - [selectedCount] == 0 -> "Allow mode • Select at least 1 app"
 *     - [selectedCount] == 1 -> "VPN active for 1 app"
 *     - else -> "VPN active for $selectedCount apps"
 */
fun formatRoutingSubtitle(
    isPerAppEnabled: Boolean,
    isBypassMode: Boolean,
    selectedCount: Int,
    totalLoadedCount: Int
): String {
    if (totalLoadedCount == 0) return "Loading installed apps..."
    if (!isPerAppEnabled) return "All apps use VPN"
    return if (isBypassMode) {
        when (selectedCount) {
            0 -> "Bypass mode • 0 apps selected"
            1 -> "Bypassing 1 app"
            else -> "Bypassing $selectedCount apps"
        }
    } else {
        when (selectedCount) {
            0 -> "Allow mode • Select at least 1 app"
            1 -> "VPN active for 1 app"
            else -> "VPN active for $selectedCount apps"
        }
    }
}
