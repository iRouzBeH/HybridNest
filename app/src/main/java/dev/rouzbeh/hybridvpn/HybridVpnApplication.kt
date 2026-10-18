package dev.rouzbeh.hybridvpn

import android.app.Application
import com.tim.basevpn.vpn.api.VpnApplicationsFilter
import com.tim.basevpn.vpn.api.VpnNotificationManager
import com.tim.openvpn.init.initializeOpenVpnLibrary
import dev.rouzbeh.hybridvpn.data.repository.SettingsRepository
import androidx.core.graphics.drawable.toBitmap

class HybridVpnApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (isProcessOpenVpn()) {
            initializeOpenVpn(this)
        }
    }

    private fun isProcessOpenVpn(): Boolean {
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) {
            getProcessName()
        } else {
            // Fallback for older APIs
            @Suppress("DEPRECATION")
            val pid = android.os.Process.myPid()
            val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.runningAppProcesses?.find { it.pid == pid }?.processName ?: ""
        }
        return processName.endsWith(":openvpn")
    }

    companion object {
        private var isOpenVpnInitialized = false

        fun initializeOpenVpn(application: Application) {
            if (isOpenVpnInitialized) {
                android.util.Log.d("VpnLibraryInit", "initializeOpenVpn: already initialized in this process")
                return
            }
            android.util.Log.i("VpnLibraryInit", "initializeOpenVpn: starting library init in pid=${android.os.Process.myPid()}")
            try {
                application.initializeOpenVpnLibrary(
                    applicationsFilter = PerAppRouting.filter(application),
                    notificationFactory = { service: android.app.Service, _ -> AbsorbingVpnNotificationManager(service) }
                )
                isOpenVpnInitialized = true
                android.util.Log.i("VpnLibraryInit", "initializeOpenVpn: initializeOpenVpnLibrary call returned (success)")
            } catch (e: Exception) {
                android.util.Log.e("VpnLibraryInit", "initializeOpenVpn: FATAL ERROR during library init", e)
            }
        }
    }

object TunnelNotificationOwner {
    private val count = java.util.concurrent.atomic.AtomicInteger(0)
    fun acquire() { count.incrementAndGet() }
    fun release(): Boolean = count.decrementAndGet() <= 0
}

object PerAppRouting {
    /**
     * Applications filter for the `:openvpn` process (invoked by the VPN library
     * during connect warm-up, before the library establishes its TUN).
     *
     * Reads the immutable RoutingPolicyFile snapshot transferred by the main process
     * (HybridVpnService.establishSession) — NOT cross-process SharedPreferences.
     *
     * Semantics (mirrors RoutingPolicyResolver in the main process):
     *  - policy disabled / missing -> empty set: with a Disallowed filter an empty
     *    set means global routing (nothing is excluded).
     *  - BYPASS mode -> Disallowed(selected ∩ installed). Uninstalled selections are
     *    pruned: an uninstalled app cannot generate traffic, so pruning never widens
     *    the policy.
     *  - ALLOW mode -> Allowed(selected ∩ installed). NO complement is computed from
     *    any package list — the library's Allowed filter maps 1:1 to Builder
     *    addAllowedApplication, so the transferred set IS the policy.
     *    (NOTE: verified against vpnprotocols 3.0.4 sources/bytecode: its
     *    OpenVPNService.startTun() currently never calls addAllowed/
     *    addDisallowedApplication on the Builder. The filter provider is warmed but
     *    unused by that version — see MANUAL_TEST.md / Stage 1 report.)
     *
     * Never widens: an unreadable/missing snapshot yields global routing and a loud
     * log; allow-mode refusal semantics are enforced by the main process BEFORE the
     * library is asked to connect (establishSession + UI reconnect action).
     */
    fun filter(context: android.content.Context): com.tim.basevpn.vpn.api.VpnApplicationsFilter {
        val policy = dev.rouzbeh.hybridvpn.core.routing.RoutingPolicyFile.read(context)
        android.util.Log.i(
            "PerAppRouting",
            "Filter provider built from snapshot r" + (policy?.revision ?: -1L) +
                ": " + (policy?.describe() ?: "null (global fallback)")
        )
        return com.tim.basevpn.vpn.api.VpnApplicationsFilter.Disallowed { computeDisallowed(context) }
    }

    private fun computeDisallowed(context: android.content.Context): Set<String> {
        val policy = dev.rouzbeh.hybridvpn.core.routing.RoutingPolicyFile.read(context)
        if (policy == null || !policy.enabled) {
            android.util.Log.i("PerAppRouting", "No enabled snapshot -> global routing (empty disallow set)")
            return emptySet()
        }
        val installed = installedPackages(context)
        val usable = policy.packages.intersect(installed)
        val pruned = policy.packages - usable
        if (pruned.isNotEmpty()) {
            android.util.Log.w(
                "PerAppRouting",
                "Snapshot r" + policy.revision + " contains uninstalled packages (pruned): " + pruned.sorted()
            )
        }
        return when (policy.mode) {
            dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy.Mode.BYPASS -> usable
            dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy.Mode.ALLOW -> {
                // Guard: a Disallowed callback can only express bypass semantics.
                // An ALLOW policy arriving here means the library applied the wrong
                // filter type; log loudly instead of silently widening to global.
                android.util.Log.e(
                    "PerAppRouting",
                    "ALLOW-mode snapshot reached Disallowed filter — library is not " +
                        "applying the Allowed entry. Per-app allow routing CANNOT work in " +
                        "vpnprotocols 3.0.4 (startTun applies no per-app rules)."
                )
                emptySet()
            }
        }
    }

    private fun installedPackages(context: android.content.Context): Set<String> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .asSequence()
            .map { it.packageName }
            .toSet()
    }
}

    class AbsorbingVpnNotificationManager(private val service: android.app.Service) : com.tim.basevpn.vpn.api.VpnNotificationManager {

        override fun startNotification() {
            val channelId = "vpn_notification_channel_silent"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val nm = service.getSystemService(android.app.NotificationManager::class.java)
                if (nm.getNotificationChannel(channelId) == null) {
                    nm.createNotificationChannel(
                        android.app.NotificationChannel(channelId, "VPN Background process", android.app.NotificationManager.IMPORTANCE_MIN).apply {
                            setSound(null, null); enableVibration(false); setShowBadge(false)
                        }
                    )
                }
                // BaseVpnService.onCreate -> ServiceForegroundPromoter posts a
                // hardcoded notification ID=1 ("VPN") on the shared
                // "vpn_notification_channel". It is posted BEFORE this factory
                // runs and is not routed through notificationFactory, so absorb
                // it here: cancel ID=1 so the generic "VPN" entry never lingers
                // alongside the main-process branded status notification.
                nm.cancel(LIBRARY_FOREGROUND_NOTIFICATION_ID)
            }

            val builder = androidx.core.app.NotificationCompat.Builder(service, channelId)
                .setContentTitle("HybridNest")
                .setContentText("VPN Runtime Active")
                .setSmallIcon(android.R.drawable.ic_secure)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setSilent(true)

            val fgsType = if (android.os.Build.VERSION.SDK_INT >= 34) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

            // Dedicated stub id: must NOT collide with HybridVpnService.NOTIFICATION_ID
            // (0x5650), which the main process uses for its rich status notification.
            // Using the same id let the :openvpn process overwrite/fight the main one.
            androidx.core.app.ServiceCompat.startForeground(service, STUB_NOTIFICATION_ID, builder.build(), fgsType)
            TunnelNotificationOwner.acquire()
        }

        override fun updateNotification(notification: android.app.Notification) = Unit
        override fun updateNotificationForState(state: com.tim.basevpn.vpn.api.VpnNotificationState) = Unit

        override fun stopNotification() {
            val last = TunnelNotificationOwner.release()
            androidx.core.app.ServiceCompat.stopForeground(service, if (last) androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE else androidx.core.app.ServiceCompat.STOP_FOREGROUND_DETACH)
        }

        companion object {
            // Distinct from HybridVpnService.NOTIFICATION_ID (0x5650).
            private const val STUB_NOTIFICATION_ID = 0x5651
            // Hardcoded inside the library's ServiceForegroundPromoter.
            private const val LIBRARY_FOREGROUND_NOTIFICATION_ID = 1
        }
    }
}
