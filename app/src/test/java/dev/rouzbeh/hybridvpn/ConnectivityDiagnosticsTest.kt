package dev.rouzbeh.hybridvpn

import dev.rouzbeh.hybridvpn.core.vpn.ConnectivityDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verdict-classification contract: the diagnostic layer classifies outcomes, it
 * never decides engine lifecycle. The device-behavior guarantee (a DnsFailure does
 * NOT disconnect a ready session) lives in HybridVpnService.launchConnectivityCheck,
 * whose code path exclusively maps non-Verified verdicts to
 * VpnStateManager.setConnectivityDegraded(true) — verified by inspection of the
 * single `when` over the verdict there.
 */
class ConnectivityDiagnosticsTest {

    @Test
    fun `HTTP 204 is Verified`() {
        val v = ConnectivityDiagnostics.classifyResponse(204)
        assertTrue(v is ConnectivityDiagnostics.Verdict.Verified)
    }

    @Test
    fun `HTTP 200 is Verified`() {
        val v = ConnectivityDiagnostics.classifyResponse(200)
        assertTrue(v is ConnectivityDiagnostics.Verdict.Verified)
    }

    @Test
    fun `redirect is an HttpFailure not Verified`() {
        val v = ConnectivityDiagnostics.classifyResponse(302)
        assertTrue(v is ConnectivityDiagnostics.Verdict.HttpFailure)
        assertEquals(302, (v as ConnectivityDiagnostics.Verdict.HttpFailure).code)
    }

    @Test
    fun `captive-portal style 403 is an HttpFailure`() {
        val v = ConnectivityDiagnostics.classifyResponse(403)
        assertTrue(v is ConnectivityDiagnostics.Verdict.HttpFailure)
    }

    @Test
    fun `5xx is an HttpFailure`() {
        val v = ConnectivityDiagnostics.classifyResponse(503)
        assertTrue(v is ConnectivityDiagnostics.Verdict.HttpFailure)
    }

    @Test
    fun `connection error (-1) is Inconclusive`() {
        val v = ConnectivityDiagnostics.classifyResponse(-1)
        assertTrue(v is ConnectivityDiagnostics.Verdict.Inconclusive)
    }
}
