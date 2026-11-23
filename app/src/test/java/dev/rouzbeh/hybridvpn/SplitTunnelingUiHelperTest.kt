package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.feature.dashboard.SplitAppFilter
import dev.rouzbeh.hybridvpn.feature.dashboard.SplitAppFilterEntry
import dev.rouzbeh.hybridvpn.feature.dashboard.countSelectedApps
import dev.rouzbeh.hybridvpn.feature.dashboard.countVisibleApps
import dev.rouzbeh.hybridvpn.feature.dashboard.filterAppEntries
import dev.rouzbeh.hybridvpn.feature.dashboard.filterApps
import dev.rouzbeh.hybridvpn.feature.dashboard.formatRoutingSubtitle
import org.junit.Assert.assertEquals
import org.junit.Test

class SplitTunnelingUiHelperTest {

    private val sampleApps = listOf(
        SplitAppFilterEntry(
            appName = "Telegram",
            packageName = "org.telegram.messenger",
            isSystemApp = false,
            isSelected = true
        ),
        SplitAppFilterEntry(
            appName = "Chrome",
            packageName = "com.android.chrome",
            isSystemApp = false,
            isSelected = false
        ),
        SplitAppFilterEntry(
            appName = "Android System WebView",
            packageName = "com.google.android.webview",
            isSystemApp = true,
            isSelected = false
        ),
        SplitAppFilterEntry(
            appName = "Google Play Services",
            packageName = "com.google.android.gms",
            isSystemApp = true,
            isSelected = true
        ),
        SplitAppFilterEntry(
            appName = "Firefox",
            packageName = "org.mozilla.firefox",
            isSystemApp = false,
            isSelected = false
        )
    )

    @Test
    fun `filterApps with ALL returns all eligible apps matching system rule`() {
        val result = filterAppEntries(
            items = sampleApps,
            query = "",
            filter = SplitAppFilter.ALL,
            showSystemApps = false
        )
        // Telegram (user, selected) -> shown
        // Chrome (user, unselected) -> shown
        // Android System WebView (system, unselected) -> hidden
        // Google Play Services (system, selected) -> shown because selected
        // Firefox (user, unselected) -> shown
        val packageNames = result.map { it.packageName }
        assertEquals(
            listOf("org.telegram.messenger", "com.android.chrome", "com.google.android.gms", "org.mozilla.firefox"),
            packageNames
        )
    }

    @Test
    fun `filterApps with SELECTED returns only selected apps`() {
        val result = filterAppEntries(
            items = sampleApps,
            query = "",
            filter = SplitAppFilter.SELECTED,
            showSystemApps = false
        )
        // Only Telegram and Google Play Services are selected
        val packageNames = result.map { it.packageName }
        assertEquals(
            listOf("org.telegram.messenger", "com.google.android.gms"),
            packageNames
        )
    }

    @Test
    fun `filterApps with showSystemApps true includes unselected system apps`() {
        val result = filterAppEntries(
            items = sampleApps,
            query = "",
            filter = SplitAppFilter.ALL,
            showSystemApps = true
        )
        assertEquals(5, result.size)
        assertEquals(
            listOf(
                "org.telegram.messenger",
                "com.android.chrome",
                "com.google.android.webview",
                "com.google.android.gms",
                "org.mozilla.firefox"
            ),
            result.map { it.packageName }
        )
    }

    @Test
    fun `filterApps search matches case-insensitively against appName`() {
        val result = filterAppEntries(
            items = sampleApps,
            query = "  tele  ",
            filter = SplitAppFilter.ALL,
            showSystemApps = false
        )
        assertEquals(1, result.size)
        assertEquals("org.telegram.messenger", result.first().packageName)
    }

    @Test
    fun `filterApps search matches case-insensitively against packageName`() {
        val result = filterAppEntries(
            items = sampleApps,
            query = "MOZILLA",
            filter = SplitAppFilter.ALL,
            showSystemApps = false
        )
        assertEquals(1, result.size)
        assertEquals("org.mozilla.firefox", result.first().packageName)
    }

    @Test
    fun `filterApps search trims whitespace and returns empty if no match`() {
        val result = filterAppEntries(
            items = sampleApps,
            query = "   nonexistent_app_query   ",
            filter = SplitAppFilter.ALL,
            showSystemApps = true
        )
        assertEquals(0, result.size)
    }

    @Test
    fun `filterApps handles generic custom types correctly`() {
        data class CustomApp(val name: String, val id: String, val sys: Boolean, val sel: Boolean)
        val customList = listOf(
            CustomApp("AppA", "pkg.a", sys = false, sel = true),
            CustomApp("AppB", "pkg.b", sys = true, sel = false)
        )
        val filtered = filterApps(
            items = customList,
            query = "pkg.a",
            filter = SplitAppFilter.ALL,
            showSystemApps = false,
            nameOf = { it.name },
            packageOf = { it.id },
            isSystemOf = { it.sys },
            isSelectedOf = { it.sel }
        )
        assertEquals(1, filtered.size)
        assertEquals("pkg.a", filtered.first().id)
    }

    @Test
    fun `countVisibleApps and countSelectedApps compute correct counts`() {
        val visibleWithoutSystem = countVisibleApps(
            items = sampleApps,
            showSystemApps = false,
            isSystemOf = { it.isSystemApp },
            isSelectedOf = { it.isSelected }
        )
        assertEquals(4, visibleWithoutSystem)

        val visibleWithSystem = countVisibleApps(
            items = sampleApps,
            showSystemApps = true,
            isSystemOf = { it.isSystemApp },
            isSelectedOf = { it.isSelected }
        )
        assertEquals(5, visibleWithSystem)

        val selectedCount = countSelectedApps(
            items = sampleApps,
            isSelectedOf = { it.isSelected }
        )
        assertEquals(2, selectedCount)
    }

    @Test
    fun `formatRoutingSubtitle returns expected strings across all states`() {
        // 1. Loading state when totalLoadedCount == 0
        assertEquals(
            "Loading installed apps...",
            formatRoutingSubtitle(
                isPerAppEnabled = true,
                isBypassMode = false,
                selectedCount = 0,
                totalLoadedCount = 0
            )
        )

        // 2. Disabled per-app state
        assertEquals(
            "All apps use VPN",
            formatRoutingSubtitle(
                isPerAppEnabled = false,
                isBypassMode = false,
                selectedCount = 5,
                totalLoadedCount = 100
            )
        )
        assertEquals(
            "All apps use VPN",
            formatRoutingSubtitle(
                isPerAppEnabled = false,
                isBypassMode = true,
                selectedCount = 0,
                totalLoadedCount = 100
            )
        )

        // 3. Bypass mode: 0, 1, N
        assertEquals(
            "Bypass mode • 0 apps selected",
            formatRoutingSubtitle(
                isPerAppEnabled = true,
                isBypassMode = true,
                selectedCount = 0,
                totalLoadedCount = 50
            )
        )
        assertEquals(
            "Bypassing 1 app",
            formatRoutingSubtitle(
                isPerAppEnabled = true,
                isBypassMode = true,
                selectedCount = 1,
                totalLoadedCount = 50
            )
        )
        assertEquals(
            "Bypassing 5 apps",
            formatRoutingSubtitle(
                isPerAppEnabled = true,
                isBypassMode = true,
                selectedCount = 5,
                totalLoadedCount = 50
            )
        )

        // 4. Allow-only mode: 0, 1, N
        assertEquals(
            "Allow mode • Select at least 1 app",
            formatRoutingSubtitle(
                isPerAppEnabled = true,
                isBypassMode = false,
                selectedCount = 0,
                totalLoadedCount = 50
            )
        )
        assertEquals(
            "VPN active for 1 app",
            formatRoutingSubtitle(
                isPerAppEnabled = true,
                isBypassMode = false,
                selectedCount = 1,
                totalLoadedCount = 50
            )
        )
        assertEquals(
            "VPN active for 4 apps",
            formatRoutingSubtitle(
                isPerAppEnabled = true,
                isBypassMode = false,
                selectedCount = 4,
                totalLoadedCount = 50
            )
        )
    }
}
