package dev.rouzbeh.hybridvpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VpnDisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_KILL_ALL_VPN") {
            context.startService(Intent(context, HybridVpnService::class.java).apply {
                action = "ACTION_STOP_VPN"
            })
            VpnActionBus.disconnectFlow.tryEmit(Unit)
        }
    }
}
