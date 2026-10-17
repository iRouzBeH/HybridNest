package dev.rouzbeh.hybridvpn.core.routing

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON (de)serialization for routing-policy snapshots.
 *
 * Kept separate from [RoutingPolicyFile] so the codec can be unit-tested on the JVM
 * without Robolectric (org.json is provided by the Android SDK at runtime; the unit
 * test classpath gets it via the `org.json:json` test dependency declared below —
 * actually Android Gradle plugin's unit tests provide a frozen org.json only on
 * device, so we test the codec via plain string parsing instead if needed).
 */
object RoutingPolicyJson {
    private const val VERSION = 1

    fun toJson(policy: RoutingPolicy): String = JSONObject().apply {
        put("version", VERSION)
        put("revision", policy.revision)
        put("enabled", policy.enabled)
        put("mode", policy.mode.name)
        put("packages", JSONArray(policy.packages.sorted()))
    }.toString()

    fun fromJson(json: String): RoutingPolicy? = try {
        val obj = JSONObject(json)
        val packages = mutableListOf<String>()
        val arr = obj.optJSONArray("packages")
        if (arr != null) {
            for (i in 0 until arr.length()) packages.add(arr.getString(i))
        }
        RoutingPolicy(
            enabled = obj.optBoolean("enabled", false),
            mode = if (obj.optString("mode") == RoutingPolicy.Mode.BYPASS.name) {
                RoutingPolicy.Mode.BYPASS
            } else {
                RoutingPolicy.Mode.ALLOW
            },
            packages = packages.toSet(),
            revision = obj.optLong("revision", -1L),
        )
    } catch (t: Throwable) {
        null
    }
}
