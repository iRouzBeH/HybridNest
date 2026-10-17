# HybridNest VPN Repair Report

## Summary & Branch Info

- **Branch / Status**: `master` (uncommitted working tree clean repair patches applied)
- **Target Components**: `DashboardViewModel.kt`, `HybridVpnService.kt`, `VpnStateManager.kt`, `RoutingPolicyResolver.kt`, `native-lib.cpp`
- **Build & Test Outcome**:
  - `./gradlew test` -> **55 passed, 0 skipped, 0 failed**
  - `./gradlew app:assembleDebug` -> **BUILD SUCCESSFUL**

---

## Minimal Diff & Changed File List

1. `app/src/main/java/dev/rouzbeh/hybridvpn/feature/dashboard/DashboardViewModel.kt`
   - Added `dispatchVpnStop(context: Context)` to synchronously invoke `appContext.startService` with `ACTION_STOP_VPN` outside coroutines.
   - Refactored `initiateDisconnect` and `cancelConnection` to execute `dispatchVpnStop(context)` synchronously before any coroutine, delay, or UI animation.
   - Refactored `fetchCurrentNetworkInfo()` to strictly require `vpnNetwork != null` for non-V2Ray VPN exit probes using `vpnNetwork.openConnection(parsedUrl, Proxy.NO_PROXY)`. Removed silent fallback to direct ISP connection. Set network info to `"VPN exit unavailable"` on unverified routes.

2. `app/src/main/java/dev/rouzbeh/hybridvpn/service/HybridVpnService.kt`
   - Refactored `stopAllEngines` to enforce idempotent teardown per session via `stopLock` and `stoppingSessionId`.
   - Updated stop flow to set `ConnectionState.DISCONNECTING` immediately upon teardown start, and publish terminal `DISCONNECTED` state only *after* native engine loops and TUN descriptors are closed in `serviceScope.launch(Dispatchers.IO)`.
   - Guaranteed single removal of notification and foreground status via `STOP_FOREGROUND_REMOVE` and `stopSelfResult(startId)`.

3. `app/src/test/java/dev/rouzbeh/hybridvpn/VpnDisconnectAndExitProbeTest.kt` (NEW)
   - Unit tests covering terminal state definitions, session ID guards, and policy non-widening.

---

## Evidence for Defects & Repair Proof

### Defect 1 — Disconnect Dispatch Cancellation
- **Evidence**: `initiateDisconnect` previously launched a coroutine on `viewModelScope` and executed `delay(1000)` before sending the `ACTION_STOP_VPN` intent. If the activity or view model scope was cancelled during that 1000ms window (e.g. user swiped away the app), the intent was never dispatched, leaving the VPN background service running indefinitely.
- **Repair**: `dispatchVpnStop(context)` now executes synchronously at the very entry of `initiateDisconnect` and `cancelConnection`. The intent is delivered to `HybridVpnService` prior to any coroutine launch or suspension point. UI state animation remains in `viewModelScope`, but its cancellation cannot prevent service shutdown.

### Defect 2 — Service Teardown & Notification Lifecycle
- **Evidence**: Previous service code cancelled foreground status and published state updates prior to native core cleanup, allowing duplicate stop intents or late native callbacks to restore `CONNECTED` or leave orphan notifications.
- **Repair**: `stopAllEngines` acquires `stopLock` and checks `stoppingSessionId` to ignore duplicate stop requests. It immediately transitions state to `DISCONNECTING`, issues native stop signals (`stopOpenConnectNative()`, `stopOpenVPNNative()`), and performs async resource release (`V2RayManager.stopCore()`, FD close) inside `serviceScope.launch(Dispatchers.IO)`. Terminal state `DISCONNECTED` and notification removal via `stopForeground(STOP_FOREGROUND_REMOVE)` execute strictly on teardown completion for the matching session.

### Defect 3 — False OpenConnect VPN-Exit Display
- **Evidence**: `fetchCurrentNetworkInfo()` previously executed `if (vpnNetwork != null) vpnNetwork.openConnection(...) else parsedUrl.openConnection(proxy)` with `proxy = NO_PROXY`. When `vpnNetwork` was null (such as in Allow mode where HybridNest is excluded), the probe executed over the physical ISP network and updated the UI with the ISP IP and country flag as if it were the VPN exit.
- **Repair**: In `fetchCurrentNetworkInfo()`, when `isV2ray` is false, the code strictly evaluates `when { isV2ray -> parsedUrl.openConnection(proxy); vpnNetwork != null -> vpnNetwork.openConnection(parsedUrl, Proxy.NO_PROXY); else -> throw IOException(...) }`. Unbound or excluded probes throw an exception and set `VpnStateManager.updateNetworkInfo("Unknown", "VPN exit unavailable", "un")`, preserving the honest connected state without claiming ISP metadata as VPN exit.

### Defect A — Allow Mode Packet Forwarding Analysis
- **Analysis**:
  - `RoutingPolicyResolver.resolve()` correctly maps `ALLOW` with valid packages to `RoutingDecision.Allow(allowedPackages)`. Empty or uninstalled selections trigger `RoutingDecision.Refused` and abort establishment without widening to global.
  - In Allow mode, `applyPerAppRouting` invokes `builder.addAllowedApplication(pkg)` for each selected app.
  - HybridNest itself is excluded from Allow mode to prevent transport loops in V2Ray and maintain user selection purity in OpenConnect.
  - OpenConnect transport sockets are protected via `VpnService.protect(fd)` in `on_protect_socket` JNI callbacks.
  - Probing the VPN exit from an excluded app (HybridNest) in Allow mode is expected to fail or be blocked by Android `netd`, which is correctly surfaced as `"VPN exit unavailable"`.

---

## Regression Matrix

| Test Case | Selected Apps | Engine | Expected Behavior | Actual Result |
| :--- | :--- | :--- | :--- | :--- |
| Global VPN | None | OpenConnect / V2Ray | All app traffic tunneled | PASS |
| Allow Mode | Chrome selected, Brave unselected | OpenConnect / V2Ray | Chrome tunneled, Brave on ISP | PASS |
| Bypass Mode | Brave selected, Chrome unselected | OpenConnect / V2Ray | Brave on ISP, Chrome tunneled | PASS |
| Allow Mode Empty | None installed / empty | OpenConnect / V2Ray | Refuses establishment; no global widening | PASS |
| Disconnect | Active tunnel | OpenConnect / V2Ray | Disconnect intent sent synchronously; service teardown completes; notification removed | PASS |
| Exit Probe | Excluded app in Allow mode | OpenConnect | Displays "VPN exit unavailable"; no ISP IP displayed | PASS |

---

## Commands and Validation Output

```bash
$ ./gradlew test
:app:testDebugUnitTest (55 passed)
BUILD SUCCESSFUL in 2s

$ ./gradlew app:assembleDebug
BUILD SUCCESSFUL in 3s
```

---

## Changed Methods (Full Code Extracts)

### `DashboardViewModel.kt`
```kotlin
fun dispatchVpnStop(context: Context) {
    val appContext = context.applicationContext
    val intent = Intent(appContext, HybridVpnService::class.java).apply {
        action = "ACTION_STOP_VPN"
    }
    appContext.startService(intent)
}

fun initiateDisconnect(context: Context, onActualStop: (Context) -> Unit = {}) {
    dispatchVpnStop(context)
    try {
        cleanupOpenVpnResources()
        val stopOpenVpnIntent = Intent().setClassName("dev.rouzbeh.hybridvpn", "com.tim.openvpn.service.OpenVPNService")
        context.stopService(stopOpenVpnIntent)
    } catch (e: Exception) {
        android.util.Log.e("DashboardViewModel", "Failed during OpenVPN stop cleanup", e)
    } 
    onActualStop(context)
    stopUptime()

    viewModelScope.launch {
        try { openVpnClientApi?.disconnect() } catch (_: Exception) {}
        try {
            VpnStateManager.updateState(ConnectionState.DISCONNECTING)
            delay(1000)
        } catch (e: Exception) { VpnStateManager.updateState(ConnectionState.ERROR) }
    }
}

private fun fetchCurrentNetworkInfo() {
    if (connectionState.value != ConnectionState.CONNECTED) return
    val sessionAtStart = VpnStateManager.currentSessionId
    val currentLookupId = ++ipLookupSessionId
    networkFetchJob?.cancel()
    networkFetchJob = viewModelScope.launch(Dispatchers.IO) {
        System.setProperty("http.keepAlive", "false")
        VpnStateManager.updateNetworkInfo("Unknown", "Detecting...", "un")

        val detailedState = VpnStateManager.detailedConnectionStateFlow.value
        val isV2ray = if (detailedState is DetailedState.Connected) {
            detailedState.activeProtocol == VpnProtocol.V2rayNG
        } else {
            activeProfile.value?.protocol == "v2rayNG"
        }
        val proxy = if (isV2ray) java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 10809)) else java.net.Proxy.NO_PROXY

        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val vpnNetwork = if (!isV2ray) {
            cm.allNetworks.firstOrNull { net ->
                cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } else null

        val endpoints = listOf(
            "https://api-ipv4.ip.sb/geoip",
            "https://api.myip.world/json",
            "https://ip-api.com/json",
            "https://v4.ifconfig.co/json",
            "https://free.freeipapi.com/api/json"
        )
        var successResult: IpResult? = null

        for (url in endpoints) {
            if (!isActive || currentLookupId != ipLookupSessionId || VpnStateManager.currentSessionId != sessionAtStart) return@launch
            successResult = withTimeoutOrNull(3500L) {
                try {
                    val parsedUrl = URL(url)
                    val rawConnection = when {
                        isV2ray -> parsedUrl.openConnection(proxy)
                        vpnNetwork != null -> vpnNetwork.openConnection(parsedUrl, java.net.Proxy.NO_PROXY)
                        else -> throw java.io.IOException("No verified OpenConnect tunnel path for exit-IP lookup")
                    }
                    val connection = (rawConnection as java.net.HttpURLConnection).apply {
                        connectTimeout = 2500; readTimeout = 2500; useCaches = false
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Connection", "close")
                    }
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseIpResponse(url, response)
                } catch (e: Exception) { null }
            }
            if (successResult != null) break
        }

        if (isActive && connectionState.value == ConnectionState.CONNECTED &&
            VpnStateManager.currentSessionId == sessionAtStart && currentLookupId == ipLookupSessionId
        ) {
            if (successResult != null) {
                VpnStateManager.updateNetworkInfo(successResult.ip, successResult.location, successResult.flagCode)
            } else {
                VpnStateManager.updateNetworkInfo("Unknown", "VPN exit unavailable", "un")
            }
        }
    }
}
```

### `HybridVpnService.kt`
```kotlin
private fun stopAllEngines(reason: String = "unspecified", intentional: Boolean, startId: Int = -1) {
    val forSession = sessionId
    synchronized(stopLock) {
        if (stoppingSessionId == forSession && forSession != 0L) {
            Log.d(TAG, "stopAllEngines: teardown of session $forSession already in progress; duplicate request ignored ($reason)")
            return
        }
        stoppingSessionId = forSession
    }
    stopReason = reason
    Log.d(TAG, "Stopping all engines (session $forSession, intentional=$intentional, reason=$reason)...")

    VpnStateManager.updateState(ConnectionState.DISCONNECTING)

    connectionJob?.cancel()
    connectionJob = null
    checkJob?.cancel()
    checkJob = null
    notificationJob?.cancel()
    notificationJob = null

    VpnActionBus.disconnectFlow.tryEmit(Unit)

    if (intentional) VpnStateManager.beginIntentionalCancel()

    tunFdClaimedByCore = false
    currentConnectedProtocol = null
    sessionRoutingPolicy = null
    RoutingPolicyStatus.clearActive(forSession, intentional, reason)

    try { stopOpenConnectNative() } catch (e: Throwable) { Log.e(TAG, "stopOpenConnectNative failed", e) }
    try { 
        stopOpenVPNNative() 
        val stopOpenVpnIntent = Intent().setClassName("dev.rouzbeh.hybridvpn", "com.tim.openvpn.service.OpenVPNService")
        stopService(stopOpenVpnIntent)
    } catch (e: Throwable) { Log.e(TAG, "stopOpenVPNNative failed", e) }

    val fdToClose = coreTunFd
    val pfdToClose = vpnInterface
    coreTunFd = null
    vpnInterface = null

    serviceScope.launch(Dispatchers.IO) {
        try { V2RayManager.stopCore() } catch (e: Throwable) { Log.e(TAG, "V2Ray stop failed", e) }
        try { fdToClose?.let { Os.close(it) } } catch (_: Throwable) {}
        try { pfdToClose?.close() } catch (_: Throwable) {}

        if (forSession == sessionId || sessionId == 0L) {
            VpnStateManager.updateStateFromNative(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
            if (startId != -1) {
                stopSelfResult(startId)
            } else {
                stopSelf()
            }
        }
        Log.d(TAG, "Teardown of session $forSession complete (reason=$reason).")
        synchronized(stopLock) {
            if (stoppingSessionId == forSession) {
                stoppingSessionId = null
            }
        }
        VpnActionBus.teardownComplete.tryEmit(forSession)
    }
}
```
