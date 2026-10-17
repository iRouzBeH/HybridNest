package dev.rouzbeh.hybridvpn.data.repository

import android.content.Context
import android.content.SharedPreferences
import dev.rouzbeh.hybridvpn.core.vpn.dns.DnsProviders
import dev.rouzbeh.hybridvpn.core.vpn.dns.DnsServer
import dev.rouzbeh.hybridvpn.core.vpn.dns.DnsValidation
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistence for the "DNS" connection method's server list and selection.
 *
 * Storage: same SharedPreferences approach as [SettingsRepository] but a
 * separate file ("hybridvpn_dns") so DNS settings are isolated from the
 * routing policy snapshot machinery.
 *
 * Persisted keys:
 *  - custom servers: JSON array [{name, primary, secondary?}]
 *  - hidden built-ins: set of builtin ids the user chose to hide
 *  - selected server id
 *  - ping cache: JSON object {id -> ms} (-1 = failed)
 *
 * Built-in entries are never deleted from the list — only hidden — so the
 * "defaults" contract stays reproducible across reinstall/upgrade.
 */
class DnsSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hybridvpn_dns", Context.MODE_PRIVATE)

    // ------------------------------------------------------------- queries

    /** All visible servers (built-ins minus hidden) + customs, ping applied. */
    fun getServers(): List<DnsServer> {
        val hidden = getHiddenBuiltinIds()
        val pings = getPingMap()
        val builtins = DnsProviders.BUILT_INS
            .filter { it.id !in hidden }
            .map { it.copy(ping = pings[it.id] ?: 0L) }
        val customs = getCustomServersInternal().map {
            it.copy(ping = pings[it.id] ?: 0L)
        }
        return builtins + customs
    }

    /** The selected server, falling back to the first visible one. */
    fun getSelectedServer(): DnsServer? {
        val servers = getServers()
        val sel = prefs.getString(KEY_SELECTED_ID, null)
        return servers.firstOrNull { it.id == sel } ?: servers.firstOrNull()
    }

    fun getSelectedId(): String? = prefs.getString(KEY_SELECTED_ID, null)

    // ---------------------------------------------------------- mutations

    fun selectServer(id: String) =
        prefs.edit().putString(KEY_SELECTED_ID, id).apply()

    fun addCustomServer(name: String, primary: String, secondary: String?): Result<DnsServer> {
        val p = primary.trim(); val s = secondary?.trim()?.takeIf { it.isNotBlank() }
        if (!DnsValidation.isValidIp(p)) return Result.failure(IllegalArgumentException("Invalid primary IP"))
        if (s != null && !DnsValidation.isValidIp(s)) return Result.failure(IllegalArgumentException("Invalid secondary IP"))
        val cleanName = name.trim().ifBlank { p }
        val list = getCustomServersInternal()
        if (list.any { it.primary == p && it.secondary == s })
            return Result.failure(IllegalStateException("This DNS server already exists"))
        val entry = DnsServer(
            id = "custom:${System.currentTimeMillis()}:${p}",
            name = cleanName, primary = p, secondary = s, isBuiltin = false
        )
        saveCustomServers(list + entry)
        return Result.success(entry)
    }

    fun updateCustomServer(id: String, name: String, primary: String, secondary: String?): Result<DnsServer> {
        val p = primary.trim(); val s = secondary?.trim()?.takeIf { it.isNotBlank() }
        if (!DnsValidation.isValidIp(p)) return Result.failure(IllegalArgumentException("Invalid primary IP"))
        if (s != null && !DnsValidation.isValidIp(s)) return Result.failure(IllegalArgumentException("Invalid secondary IP"))
        val list = getCustomServersInternal()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return Result.failure(IllegalArgumentException("Not a custom server"))
        val updated = list[idx].copy(name = name.trim().ifBlank { p }, primary = p, secondary = s)
        val newList = list.toMutableList().apply { set(idx, updated) }
        saveCustomServers(newList)
        return Result.success(updated)
    }

    /** Customs are removed; built-ins are hidden instead. */
    fun deleteOrHide(id: String) {
        val custom = getCustomServersInternal()
        if (custom.any { it.id == id }) {
            saveCustomServers(custom.filter { it.id != id })
        } else {
            val hidden = getHiddenBuiltinIds().toMutableSet().apply { add(id) }
            prefs.edit().putStringSet(KEY_HIDDEN_BUILTINS, hidden).apply()
        }
        // If the deleted/hidden entry was selected, clear selection so a
        // fallback (first visible) is used.
        if (getSelectedId() == id) prefs.edit().remove(KEY_SELECTED_ID).apply()
        // Drop stale ping cache for the removed id.
        val pings = getPingMap().toMutableMap().apply { remove(id) }
        savePingMap(pings)
    }

    fun setPing(id: String, ping: Long) {
        val pings = getPingMap().toMutableMap().apply { put(id, ping) }
        savePingMap(pings)
    }

    fun resetPings() = savePingMap(emptyMap())

    // ------------------------------------------------------------ internals

    private fun getCustomServersInternal(): List<DnsServer> {
        val raw = prefs.getString(KEY_CUSTOM_SERVERS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                val name = o.optString("name").ifBlank { return@mapNotNull null }
                val p = o.optString("primary").ifBlank { return@mapNotNull null }
                val s = o.optString("secondary").ifBlank { null }
                DnsServer(id = id, name = name, primary = p, secondary = s, isBuiltin = false)
            }
        } catch (_: Throwable) { emptyList() }
    }

    private fun saveCustomServers(list: List<DnsServer>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("primary", s.primary)
                if (s.secondary != null) put("secondary", s.secondary)
            })
        }
        prefs.edit().putString(KEY_CUSTOM_SERVERS, arr.toString()).apply()
    }

    private fun getHiddenBuiltinIds(): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_BUILTINS, emptySet()) ?: emptySet()

    private fun getPingMap(): Map<String, Long> {
        val raw = prefs.getString(KEY_PINGS, "{}") ?: "{}"
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getLong(it) }
        } catch (_: Throwable) { emptyMap() }
    }

    private fun savePingMap(map: Map<String, Long>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(KEY_PINGS, o.toString()).apply()
    }

    companion object {
        private const val KEY_CUSTOM_SERVERS = "custom_servers"
        private const val KEY_HIDDEN_BUILTINS = "hidden_builtins"
        private const val KEY_SELECTED_ID = "selected_id"
        private const val KEY_PINGS = "pings"
    }
}
