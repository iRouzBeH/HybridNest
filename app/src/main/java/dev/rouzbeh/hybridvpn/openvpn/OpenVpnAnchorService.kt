package dev.rouzbeh.hybridvpn.openvpn

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dev.rouzbeh.hybridvpn.service.HybridVpnService
import java.io.File

class OpenVpnAnchorService : Service() {

    override fun onCreate() {
        startForegroundAsAnchor()
        Log.d("OpenVpnAnchor", "Anchor onCreate: init deferred to Application class")

        // The library's own VpnWarmBindingController retries its bind only on
        // Activity lifecycle events, which never fire inside the :openvpn
        // process. If its first bind attempt fails silently, OpenVPNService
        // (and its RPC socket) never starts. Kick it explicitly and
        // deterministically instead, mirroring the explicit setClassName()
        // pattern already used in HybridVpnService.stopAllEngines().
        Log.d("OpenVpnAnchor", "Triggering explicit OpenVPNService warm-up...")
        try {
            val warmupIntent = Intent()
                .setClassName(packageName, "com.tim.openvpn.service.OpenVPNService")
            startService(warmupIntent)
            Log.d("OpenVpnAnchor", "Explicit warm-up Intent sent.")
        } catch (e: Exception) {
            Log.e("OpenVpnAnchor", "Explicit warm-up failed", e)
        }
    }

    private fun startForegroundAsAnchor() {
        val channelId = "vpn_notification_channel_silent"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(channelId, "VPN Background process", android.app.NotificationManager.IMPORTANCE_MIN).apply {
                        setSound(null, null); enableVibration(false); setShowBadge(false)
                    }
                )
            }
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("HybridNest")
            .setContentText("VPN Background Runtime")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)

        val fgsType = if (android.os.Build.VERSION.SDK_INT >= 34) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        
        // Sync with notification ID to ensure we only occupy one slot
        androidx.core.app.ServiceCompat.startForeground(this, HybridVpnService.NOTIFICATION_ID, builder.build(), fgsType)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder = object : IOpenVpnAnchor.Stub() {
        override fun isRuntimeReady(): Boolean {
            val exists = File(filesDir, "openvpn_vpn_rpc.sock").exists()
            if (exists) {
                Log.d("OpenVpnAnchor", "isRuntimeReady: socket FOUND at $filesDir/openvpn_vpn_rpc.sock")
            } else {
                Log.v("OpenVpnAnchor", "isRuntimeReady: socket not found yet in $filesDir")
            }
            return exists
        }
    }

    override fun onDestroy() {
        Log.w(TAG, "Anchor destroyed — :openvpn process will be reclaimed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OpenVpnAnchor"
        private const val SOCKET_NAME = "openvpn_vpn_rpc.sock"
    }
}
