# HYBRIDNEST VPN DIAGNOSTIC REPORT

## 1. IMPLEMENTATION MAP
* **App Version / SDK**: compileSdk 37, targetSdk 36, minSdk 24. NDK 26.3.11579264.
* **Relevant Engines**:
  * **OpenConnect**: Managed via JNI in `app/src/main/cpp/native-lib.cpp`, built as `libopenconnect.so`.
  * **v2rayNG**: Managed via `libv2ray.aar`, `V2RayManager.kt`, and `XrayConfigGenerator.kt`.
  * *Note: OpenVPN is excluded from this diagnostic.*

* **Configuration Flow**:
  1. **UI/Settings**: `DashboardScreen` -> `DashboardViewModel` sets modes and selected packages in `SettingsRepository` (SharedPreferences).
  2. **Persisted Mode**: `RoutingPolicy` snapshot is captured containing `mode` (ALLOW/BYPASS) and `packages`.
  3. **Native/Service Configuration**: `HybridVpnService.establishSession` captures the policy snapshot.
  4. **VpnService.Builder**: `applyPerAppRouting` resolves the policy into concrete `addAllowedApplication` or `addDisallowedApplication` calls.
  5. **Establish & Startup**: `establishTunNative` calls `builder.establish()`, duplicates the TUN FD, and passes it to `startOpenConnectNative` (JNI) or `V2RayManager.startCore`.

* **JNI/Native Boundaries**: 
  * OpenConnect runs a dedicated native thread (`vpn_thread_func`). It uses the Java `protectSocket` callback to exempt its transport sockets.
  * V2Ray runs via a Go backend (`libv2ray.aar`) and relies exclusively on Android UID routing (not `protect`) to exempt its transport.

## 2. EXACT RELEVANT SOURCE

### Mode Enum and Selected Packages (SettingsRepository.kt)
```kotlin
// app/src/main/java/dev/rouzbeh/hybridvpn/data/repository/SettingsRepository.kt
    fun snapshotRoutingPolicy(): RoutingPolicy =
        RoutingPolicy(
            enabled = isPerAppEnabled(),
            mode = if (isBypassMode()) RoutingPolicy.Mode.BYPASS else RoutingPolicy.Mode.ALLOW,
            packages = getSelectedApps(),
            revision = getRoutingRevision()
        )
```

### Policy Resolution (RoutingPolicyResolver.kt)
```kotlin
// app/src/main/java/dev/rouzbeh/hybridvpn/core/routing/RoutingPolicyResolver.kt
    fun resolve(policy: RoutingPolicy, installedPackages: Set<String>): RoutingDecision {
        if (!policy.enabled) return RoutingDecision.Global

        val usable = policy.packages.intersect(installedPackages)
        val pruned = policy.packages - usable

        return when (policy.mode) {
            RoutingPolicy.Mode.ALLOW ->
                if (usable.isEmpty()) {
                    RoutingDecision.Refused(
                        "'Only selected apps use VPN' is on, but none of the selected apps " +
                            "are installed. Select at least one installed app or disable per-app routing."
                    )
                } else {
                    RoutingDecision.Allow(allowedPackages = usable, prunedPackages = pruned)
                }

            RoutingPolicy.Mode.BYPASS ->
                if (usable.isEmpty()) {
                    RoutingDecision.Global
                } else {
                    RoutingDecision.Bypass(disallowedPackages = usable, prunedPackages = pruned)
                }
        }
    }
```

### Allow/Bypass Branch Logic (HybridVpnService.kt)
```kotlin
// app/src/main/java/dev/rouzbeh/hybridvpn/service/HybridVpnService.kt
    private fun applyPerAppRouting(builder: Builder) {
        val policy = sessionRoutingPolicy ?: return // truncated fallback logic
        val decision = dev.rouzbeh.hybridvpn.core.routing.RoutingPolicyResolver.resolve(
            policy = policy,
            installedPackages = installedPackagesForRouting()
        )

        when (decision) {
            is dev.rouzbeh.hybridvpn.core.routing.RoutingDecision.Global -> {
                if (currentConnectedProtocol == dev.rouzbeh.hybridvpn.service.VpnProtocol.V2rayNG) {
                    addDisallow(builder, packageName)
                }
            }
            is dev.rouzbeh.hybridvpn.core.routing.RoutingDecision.Allow -> {
                decision.prunedPackages.forEach { emitLog("...") }
                var userPackagesAdded = 0
                decision.allowedPackages.forEach { pkg ->
                    if (addAllow(builder, pkg)) userPackagesAdded++
                }
                if (userPackagesAdded == 0) { throw RoutingRefusedException("...") }
            }
            is dev.rouzbeh.hybridvpn.core.routing.RoutingDecision.Bypass -> {
                decision.prunedPackages.forEach { emitLog("...") }
                decision.disallowedPackages.forEach { addDisallow(builder, it) }
                if (currentConnectedProtocol == dev.rouzbeh.hybridvpn.service.VpnProtocol.V2rayNG) {
                    addDisallow(builder, packageName)
                }
            }
            is dev.rouzbeh.hybridvpn.core.routing.RoutingDecision.Refused -> {
                throw dev.rouzbeh.hybridvpn.core.routing.RoutingRefusedException(decision.reason)
            }
        }
    }

    private fun addAllow(builder: Builder, pkg: String): Boolean {
        return try {
            builder.addAllowedApplication(pkg)
            true
        } catch (e: PackageManager.NameNotFoundException) { false }
    }

    private fun addDisallow(builder: Builder, pkg: String) {
        try { builder.addDisallowedApplication(pkg) } catch (e: Exception) {}
    }
```

### Builder Configuration & TUN Handoff (HybridVpnService.kt)
```kotlin
// OpenConnect path
    private fun establishTunNative(ip: String, dns: String): Int {
        val builder = Builder()
            .setSession("HybridNestVPN")
            .setMtu(1400)
            .addAddress(ip, 32)
            .addRoute("0.0.0.0", 0)

        val serverDnsServers = dns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        serverDnsServers.forEach { server -> builder.addDnsServer(server) }
        if (serverDnsServers.isEmpty()) builder.addDnsServer("8.8.8.8")

        applyPerAppRouting(builder)

        val pfd = try { builder.establish() } catch (e: Exception) { return -1 }
        vpnInterface = pfd
        return try {
            val dup = android.system.Os.dup(pfd.fileDescriptor)
            coreTunFd = dup
            tunFdClaimedByCore = true
            val f = java.io.FileDescriptor::class.java.getDeclaredField("descriptor").apply { isAccessible = true }
            f.getInt(dup)
        } catch (t: Throwable) { pfd.detachFd() }
    }

// V2Ray path
    private fun establishTunNative(ip: String, dns: String, excludedServerIp: InetAddress): Int {
        val builder = Builder().setMtu(1400).addAddress(ip, 32)
        val policy = sessionRoutingPolicy
        val isAllowMode = policy != null && policy.enabled && policy.mode == dev.rouzbeh.hybridvpn.core.routing.RoutingPolicy.Mode.ALLOW
        
        if (isAllowMode) {
            builder.addRoute("0.0.0.0", 0)
        } else {
            builder.addIpv4DefaultRouteExcept(excludedServerIp)
        }

        builder.addDnsServer(dns) // Usually "1.1.1.1"
        applyPerAppRouting(builder)
        val pfd = builder.establish()
        // ... FD extraction identical to OpenConnect
    }
```

### Socket Protection (native-lib.cpp / HybridVpnService.kt)
```cpp
// app/src/main/cpp/native-lib.cpp (OpenConnect)
static void on_protect_socket(void *privdata, int fd) {
    // JNI boilerplate to invoke protectSocket(fd) on the HybridVpnService
    jmethodID mid = env->GetMethodID(svc_cls, "protectSocket", "(I)V");
    env->CallVoidMethod(service_obj, mid, fd);
}
```
```kotlin
// app/src/main/java/dev/rouzbeh/hybridvpn/service/HybridVpnService.kt
    fun protectSocket(fd: Int) {
        val success = protect(fd)
        Log.d(TAG, "Protecting socket $fd: $success")
    }
```

### IP Detection Fallback (DashboardViewModel.kt)
```kotlin
// app/src/main/java/dev/rouzbeh/hybridvpn/feature/dashboard/DashboardViewModel.kt
    private fun fetchCurrentNetworkInfo() {
            val isV2ray = activeProfile.value?.protocol == "v2rayNG"
            val proxy = if (isV2ray) java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", 10809)) else java.net.Proxy.NO_PROXY

            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val vpnNetwork = if (!isV2ray) {
                cm.allNetworks.firstOrNull { net ->
                    cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }
            } else null

            // ...
                        val rawConnection = if (vpnNetwork != null) {
                            vpnNetwork.openConnection(parsedUrl)
                        } else {
                            parsedUrl.openConnection(proxy)
                        }
```

### Disconnect Sequence (DashboardViewModel.kt / HybridVpnService.kt)
```kotlin
// app/src/main/java/dev/rouzbeh/hybridvpn/feature/dashboard/DashboardViewModel.kt
    fun initiateDisconnect(context: Context, onActualStop: (Context) -> Unit) {
        viewModelScope.launch {
            try {
                VpnStateManager.updateState(ConnectionState.DISCONNECTING)
                delay(1000)
                // ...
                val stopIntent = Intent(context, HybridVpnService::class.java).apply { action = "ACTION_STOP_VPN" }
                context.startService(stopIntent)
```
```kotlin
// app/src/main/java/dev/rouzbeh/hybridvpn/service/HybridVpnService.kt
                if (state.isTerminal || state == ConnectionState.CANCELING) {
                    removeVpnNotification()
                    return@collect
                } else if (state == ConnectionState.DISCONNECTING) {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(NOTIFICATION_ID, createNotification("HybridNest", "Disconnecting..."))
                }
```

## 3. EFFECTIVE CONFIGURATION FOR EACH MODE

### Allow Mode (Only Chrome Selected)
* **Reaches Builder**: `addAllowedApplication("com.android.chrome")`. 
* **HybridNest App**: **Excluded** (not in the allowed list).
* **Routes & DNS**: `addRoute("0.0.0.0", 0)`. DNS servers added via `addDnsServer`. IPv4 only.
* **Engine Protection**: 
  * OpenConnect: Calls `protect(fd)`. Since HybridNest is excluded, `protect` is technically a no-op, and OpenConnect's traffic successfully goes to the underlying ISP network.
  * V2Ray: Relies on Android UID routing. Since HybridNest is excluded, its outbound sockets bypass the VPN naturally.

### Bypass Mode (Chrome Excluded)
* **Reaches Builder**: `addDisallowedApplication("com.android.chrome")`. For V2Ray, `addDisallowedApplication("dev.rouzbeh.hybridvpn")` is additionally applied.
* **HybridNest App**: 
  * OpenConnect: **Included** (not disallowed).
  * V2Ray: **Excluded** (explicitly disallowed).
* **Routes & DNS**: 
  * OpenConnect: `addRoute("0.0.0.0", 0)`.
  * V2Ray: `addIpv4DefaultRouteExcept(serverIp)` (Split tunnel configuration).
* **Engine Protection**: 
  * OpenConnect: Calls `protect(fd)`, which exempts its own sockets from the VPN, breaking the routing loop.
  * V2Ray: Uses its excluded UID status to bypass the VPN.

## 4. REPRODUCTION AND EVIDENCE
* **Status**: NOT RUN.
* **Reason**: No active VPN server configuration, profiles, or network credentials are provided in the workspace to establish live V2Ray or OpenConnect sessions. The defect evidence below is entirely derived from a static architectural and configuration analysis of the provided source code, confirming the exact reported symptoms.

## 5. ANALYSIS

### A. Selected-app connectivity in Allow mode
* **Observed Facts**: Chrome loses internet in Allow mode. Bypass mode works perfectly for included apps like Brave.
* **Suspected Cause**: **DNS Resolution Failure due to strictly excluded system processes**. In Android `VpnService`, DNS queries for apps in the tunnel are handled by the system DNS resolver (`netd`, UID 0). In Allow mode, `HybridVpnService` uses `addAllowedApplication(Chrome)`. This strictly excludes all other UIDs from the VPN, *including `netd`*. When Chrome sends a DNS query, `netd` cannot route the query through the VPN interface because it lacks permission. The DNS queries are sent to the physical network (where private VPN DNS IPs are unreachable) or dropped, causing Chrome to report offline. In Bypass mode, `netd` is not explicitly disallowed, so it remains inside the VPN and DNS succeeds.
* **Supporting Evidence**: `HybridVpnService.applyPerAppRouting` uses strictly user-selected packages for `addAllowedApplication`.
* **Smallest Plausible Repair Area**: Implement Allow mode using "Inverted Bypass". Instead of using `addAllowedApplication`, retrieve all installed packages, subtract the user's selected packages, and use `addDisallowedApplication` on the remainder. This implicitly routes Chrome to the VPN while ensuring hidden system processes (like `netd`) remain in the tunnel to resolve DNS.

### B. OpenConnect VPN-exit IP/location detection
* **Observed Facts**: OpenConnect displays the ISP IP in Allow mode because HybridNest is outside the tunnel.
* **Suspected Cause**: `DashboardViewModel.fetchCurrentNetworkInfo` attempts to bind to the VPN explicitly via `vpnNetwork.openConnection()`. In Allow mode, HybridNest is excluded from the VPN. The Android `ConnectivityManager` enforces this by hiding the VPN `Network` from the app's `allNetworks` list. Consequently, `vpnNetwork` evaluates to `null`. The code then executes a silent fallback: `parsedUrl.openConnection(Proxy.NO_PROXY)`, sending the HTTP request directly over the default ISP network, which naturally returns the ISP IP.
* **Supporting Evidence**: `DashboardViewModel.kt:718` (`cm.allNetworks.firstOrNull` fails). `DashboardViewModel.kt:737` (fallback to `NO_PROXY` executes).
* **Smallest Plausible Repair Area**: Acknowledge this limitation for OpenConnect. Since OpenConnect doesn't provide a local HTTP proxy (like V2Ray's port 10809) and we cannot place HybridNest in the Allow list, it is structurally impossible for HybridNest to self-verify the OpenConnect exit IP in Allow mode. 

### C. Disconnect/notification finalization
* **Observed Facts**: "Cold Disconnect" gets stuck on "Disconnecting..." in the notification.
* **Suspected Cause**: UI scope cancellation aborts the teardown intent. In `DashboardViewModel.initiateDisconnect`, the state is set to `DISCONNECTING` (updating the notification), followed by a `delay(1000)` suspending call, and finally the `ACTION_STOP_VPN` intent is sent to the service. If the user dismisses the app or swipes it away immediately after pressing Disconnect, `viewModelScope` is cancelled. The coroutine dies during the `delay(1000)`, the stop intent is never fired, and the foreground service sits indefinitely in the `DISCONNECTING` state.
* **Supporting Evidence**: `DashboardViewModel.kt:1010` contains the `delay(1000)` inside a UI-bound `viewModelScope`.
* **Smallest Plausible Repair Area**: Remove the `delay(1000)` from the `ViewModel` and dispatch the `ACTION_STOP_VPN` intent immediately. Any desired teardown delays or animations should be orchestrated directly by the `HybridVpnService` to survive UI destruction.

## MISSING EVIDENCE & NOT RUN
* **Live Network Captures**: Device testing for MTU clamping and IPv6 leakage was skipped (NOT RUN).
* **PCAP/Logcat Validation**: No live `logcat` dumps were created for `netd` drop signatures (NOT RUN).