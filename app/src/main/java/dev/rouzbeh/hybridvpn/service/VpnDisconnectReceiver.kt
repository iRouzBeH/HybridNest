package dev.rouzbeh.hybridvpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VpnDisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_KILL_ALL_VPN") {
            val stopIntent = Intent(context, HybridVpnService::class.java).apply {
                action = "ACTION_STOP_VPN"
            }
            try {
                context.startService(stopIntent)
            } catch (_: Throwable) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(stopIntent)
                    } else {
                        context.startService(stopIntent)
                    }
                } catch (_: Throwable) {}
            }
            VpnActionBus.disconnectFlow.tryEmit(Unit)
        }
    }
}
