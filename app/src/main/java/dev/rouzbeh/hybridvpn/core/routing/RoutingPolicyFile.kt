package dev.rouzbeh.hybridvpn.core.routing

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Cross-process transfer of the routing-policy snapshot for the `:openvpn` process.
 *
 * Why a file and not cross-process SharedPreferences: ordinary SharedPreferences is
 * not a reliable multi-process synchronization mechanism, and MODE_MULTI_PROCESS is
 * deprecated/unreliable. Both processes of this app share the same sandboxed filesDir.
 *
 * Protocol:
 *  1. The MAIN process atomically writes the snapshot (tmp file + rename) BEFORE any
 *     tunnel establishment is triggered.
 *  2. The `:openvpn` process reads the snapshot lazily when the VPN library invokes
 *     its applications filter (which happens during connect warm-up, before the
 *     library establishes its TUN).
 *  3. The consumed revision is logged; verify the transfer in MANUAL_TEST via the
 *     emitted revision logs in both processes.
 *
 * If the snapshot is missing or malformed the consumer treats the policy as global
 * and logs loudly; per-app enabled connections are additionally verified (and
 * aborted on failure) by the main process.
 */
object RoutingPolicyFile {
    private const val TAG = "RoutingPolicy"
    private const val FILE_NAME = "routing_policy.json"

    /** Atomically persist [policy]. Never throws. */
    fun write(context: Context, policy: RoutingPolicy) {
        try {
            val dir = context.filesDir
            val tmp = File(dir, "$FILE_NAME.tmp")
            val target = File(dir, FILE_NAME)
            tmp.writeText(RoutingPolicyJson.toJson(policy))
            if (!tmp.renameTo(target)) {
                // Defensive: some filesystems refuse rename-over-existing.
                target.delete()
                if (!tmp.renameTo(target)) error("renameTo failed for $FILE_NAME")
            }
            Log.i(TAG, "Persisted routing policy r${policy.revision}: ${policy.describe()}")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to persist routing policy snapshot", t)
        }
    }

    /** Read the latest snapshot, or null when absent/corrupt (consumer falls back to global). */
    fun read(context: Context): RoutingPolicy? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return null
            RoutingPolicyJson.fromJson(file.readText())
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read routing policy snapshot; treating as global", t)
            null
        }
    }
}
