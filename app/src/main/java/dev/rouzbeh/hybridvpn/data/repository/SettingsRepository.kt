package dev.rouzbeh.hybridvpn.data.repository

import android.content.Context
import android.content.SharedPreferences
import dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy

/**
 * Source of truth for user settings.
 */
class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hybridvpn_settings", Context.MODE_PRIVATE)

    fun isPerAppEnabled(): Boolean = prefs.getBoolean(KEY_PER_APP_ENABLED, false)
    fun setPerAppEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_PER_APP_ENABLED, enabled).apply()

    fun isBypassMode(): Boolean = prefs.getBoolean(KEY_BYPASS_MODE, false)
    fun setBypassMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_BYPASS_MODE, enabled).apply()

    fun getSelectedApps(): Set<String> =
        prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()

    fun setSelectedApps(packageNames: Set<String>) =
        prefs.edit().putStringSet(KEY_SELECTED_APPS, packageNames).apply()

    /**
     * Monotonic policy revision, bumped on every routing-affecting change.
     * Survives process restarts; establishes session identity for the policy
     * snapshot captured at each connection start.
     */
    fun getRoutingRevision(): Long = prefs.getLong(KEY_ROUTING_REVISION, 0L)

    fun snapshotRoutingPolicy(): RoutingPolicy =
        RoutingPolicy(
            enabled = isPerAppEnabled(),
            mode = if (isBypassMode()) RoutingPolicy.Mode.BYPASS else RoutingPolicy.Mode.ALLOW,
            packages = getSelectedApps(),
            revision = getRoutingRevision()
        )

    private fun bumpRevisionInternal(editor: SharedPreferences.Editor) {
        val next = getRoutingRevision() + 1
        editor.putLong(KEY_ROUTING_REVISION, next)
    }

    fun bumpRoutingRevision() {
        prefs.edit().let { bumpRevisionInternal(it); it.apply() }
    }

    fun setPerAppEnabledAndBump(enabled: Boolean) {
        prefs.edit().let { bumpRevisionInternal(it); it.putBoolean(KEY_PER_APP_ENABLED, enabled).apply() }
    }

    fun setBypassModeAndBump(enabled: Boolean) {
        prefs.edit().let { bumpRevisionInternal(it); it.putBoolean(KEY_BYPASS_MODE, enabled).apply() }
    }

    fun setSelectedAppsAndBump(packageNames: Set<String>) {
        prefs.edit().let { bumpRevisionInternal(it); it.putStringSet(KEY_SELECTED_APPS, packageNames).apply() }
    }

    companion object {
        private const val KEY_PER_APP_ENABLED = "per_app_enabled"
        private const val KEY_BYPASS_MODE = "bypass_mode"
        private const val KEY_SELECTED_APPS = "selected_apps"
        private const val KEY_ROUTING_REVISION = "routing_revision"
    }
}
