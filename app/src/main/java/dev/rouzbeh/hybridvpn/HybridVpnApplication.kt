package dev.rouzbeh.hybridvpn

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log

class HybridVpnApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initCrashAndExitDiagnostics(this)
    }

    companion object {
        fun initCrashAndExitDiagnostics(context: Context) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                try { Log.e("HybridNest-Crash", "FATAL on thread ${t.name}", e) } catch (_: Throwable) { }
                previous?.uncaughtException(t, e)
            }

            logPreviousExitReasons(context) { line ->
                Log.w("HybridNest-Exit", line)
            }
        }

        fun logPreviousExitReasons(context: Context, log: (String) -> Unit) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val prefs = context.getSharedPreferences("exit_reasons", Context.MODE_PRIVATE)
                val lastSeen = prefs.getLong("last_ts", 0L)
                var newest = lastSeen
                for (info in am.getHistoricalProcessExitReasons(context.packageName, 0, 5)) {
                    if (info.timestamp <= lastSeen) continue
                    if (info.timestamp > newest) newest = info.timestamp
                    val reason = when (info.reason) {
                        ApplicationExitInfo.REASON_ANR -> "ANR"
                        ApplicationExitInfo.REASON_CRASH -> "CRASH"
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
                        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
                        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
                        else -> "OTHER(${info.reason})"
                    }
                    log("ExitInfo ts=${info.timestamp} reason=$reason importance=${info.importance} desc=${info.description}")
                }
                prefs.edit().putLong("last_ts", newest).apply()
            } catch (_: Throwable) { }
        }
    }
}
