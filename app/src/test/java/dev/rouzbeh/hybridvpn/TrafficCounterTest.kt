package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.service.ConnectionState
import dev.rouzbeh.hybridvpn.service.VpnStateManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TrafficCounterTest {

    @Before
    fun setup() {
        VpnStateManager.resetUsage()
    }

    @Test
    fun `resetUsage zeros speed and rawSpeed`() {
        VpnStateManager.updateSpeed("1.5 MB/s", "500 KB/s", 1500000L, 500000L)
        assertEquals(Pair("1.5 MB/s", "500 KB/s"), VpnStateManager.speed.value)
        assertEquals(Pair(1500000L, 500000L), VpnStateManager.rawSpeed.value)

        VpnStateManager.resetUsage()
        assertEquals(Pair("0 B/s", "0 B/s"), VpnStateManager.speed.value)
        assertEquals(Pair(0L, 0L), VpnStateManager.rawSpeed.value)
    }

    @Test
    fun `terminal state transition resets traffic counters`() {
        VpnStateManager.updateSpeed("10 MB/s", "2 MB/s", 10000000L, 2000000L)
        VpnStateManager.updateState(ConnectionState.FAILED)

        assertEquals(Pair("0 B/s", "0 B/s"), VpnStateManager.speed.value)
        assertEquals(Pair(0L, 0L), VpnStateManager.rawSpeed.value)
    }

    @Test
    fun `connecting state transition resets traffic counters for new session`() {
        VpnStateManager.updateSpeed("5 MB/s", "1 MB/s", 5000000L, 1000000L)
        VpnStateManager.updateState(ConnectionState.CONNECTING)

        assertEquals(Pair("0 B/s", "0 B/s"), VpnStateManager.speed.value)
        assertEquals(Pair(0L, 0L), VpnStateManager.rawSpeed.value)
    }
}
