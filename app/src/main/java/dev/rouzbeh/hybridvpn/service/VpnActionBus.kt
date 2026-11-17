package dev.rouzbeh.hybridvpn.service

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * One-way event bus between the service (engine owner) and the UI layer.
 *
 * [disconnectFlow] signals the ViewModel to clean up library resources (IPC loops).
 * [teardownComplete] carries the sessionId whose FULL teardown finished (native
 * cancel signals sent, FDs scheduled for close, async stop work kicked off). This
 * is the synchronization primitive reconnect waits on — see
 * DashboardViewModel.reconnectWithLatestPolicy().
 */
object VpnActionBus {
    val disconnectFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits the sessionId whose teardown fully completed. */
    val teardownComplete = MutableSharedFlow<Long>(extraBufferCapacity = 1)
}
