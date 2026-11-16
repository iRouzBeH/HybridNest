# HybridNest Project Context & Architectural Specification

This document provides a comprehensive technical overview and architectural blueprint of **HybridNest** (package: `dev.rouzbeh.hybridvpn`), an advanced multi-protocol Android VPN client supporting Cisco AnyConnect (via OpenConnect), v2rayNG (V2Ray/Xray cores), and a custom packet-level DNS-over-TUN routing engine.

---

## 1. Project Overview & Build Configuration

| Property | Value / Configuration |
|---|---|
| **Application ID / Package** | `dev.rouzbeh.hybridvpn` |
| **Compile SDK** | `37` (Android 15+) |
| **Target SDK** | `36` |
| **Min SDK** | `24` (Android 7.0 Nougat) |
| **Version Code / Name** | `1` / `"1.0"` |
| **Gradle Version** | `9.9.0` (Gradle Wrapper) |
| **Android Gradle Plugin (AGP)** | `9.5.1` |
| **Kotlin Version** | `2.2.10` (with Jetpack Compose Compiler plugin) |
| **Java Compatibility** | Java 11 (`JavaVersion.VERSION_11` for source & target) |
| **KSP Version** | `2.3.6` |
| **NDK Version** | `26.3.11579264` |
| **CMake Version** | `3.22.1` (`app/src/main/cpp/CMakeLists.txt`) |
| **16KB Page Alignment** | Linker flags `-Wl,-z,max-page-size=16384` enforced in both Gradle `externalNativeBuild.cmake` and `CMakeLists.txt` for Android 15/16 16KB memory page compatibility |
| **ABI Filters** | `armeabi-v7a`, `arm64-v8a` (x86/x86_64 available in JNI/AAR but filtered out in final APK) |
| **Packaging Options** | `jniLibs { useLegacyPackaging = true }` |
| **Build Types** | Release: `isMinifyEnabled = true`, `isShrinkResources = true`, ProGuard rules configured |

---

## 2. Repository Directory Tree

```
HybridNest/
├── build.gradle.kts                      # Root Gradle configuration
├── settings.gradle.kts                   # Plugin & dependency repo declarations
├── gradle.properties                     # Worker limits, JVM args, parallel sync
├── .gitignore                            # Android/IDE/Secrets exclusion rules
├── docs/
│   └── PER_APP_CONTRACT.md               # Contract specification for per-app split tunneling
├── scripts/
│   └── build_openconnect_16kb.sh         # Cross-compilation script for OpenConnect with 16KB flags
├── gradle/
│   ├── libs.versions.toml                # Version catalog for dependencies and plugins
│   └── wrapper/                          # Gradle wrapper binaries and properties (9.9.0)
└── app/
    ├── build.gradle.kts                  # App-level build logic, NDK, Room, Compose
    ├── proguard-rules.pro                # Keep rules for reflection, JNI, and Room
    ├── libs/
    │   └── libv2ray.aar                  # Xray Go core library (Gomobile + geoip/geosite)
    └── src/
        ├── androidTest/                  # Android instrumented tests
        ├── test/                         # JVM unit tests (Routing, DNS, V2Ray config, State)
        └── main/
            ├── AndroidManifest.xml       # App declarations, permissions, services
            ├── assets/
            │   └── flags/                # Bundled lossless WebP country flags (1280px) + LICENSE
            ├── cpp/                      # C++ JNI bridge & OpenConnect headers
            │   ├── CMakeLists.txt        # CMake build script linking openconnect, stoken, log
            │   ├── native-lib.cpp        # JNI implementation, lifecycle, socket cancellation
            │   └── include/
            │       ├── openconnect.h     # OpenConnect library C headers
            │       └── stoken.h          # RSA SecurID token C headers
            ├── jniLibs/                  # Precompiled shared native libraries
            │   ├── arm64-v8a/            # libopenconnect.so, libstoken.so
            │   ├── armeabi-v7a/          # libopenconnect.so, libstoken.so
            │   ├── x86/                  # libopenconnect.so, libstoken.so
            │   └── x86_64/               # libopenconnect.so, libstoken.so
            ├── res/                      # Drawables, icons, custom Sora fonts, XML rules
            └── java/dev/rouzbeh/hybridvpn/
                ├── HybridVpnApplication.kt# App class, uncaught crash & exit diagnostic logger
                ├── MainActivity.kt       # Single Activity host with Edge-to-Edge & Navigation
                ├── core/
                │   ├── designsystem/     # Theme, color palettes (Cyberpunk/Dark), typography
                │   ├── model/            # NetworkState domain models
                │   ├── routing/          # Per-app routing policy, snapshot, and resolver
                │   ├── utils/            # LogRedactor (producer-side credential scrubbing)
                │   └── vpn/
                │       ├── ConnectivityDiagnostics.kt # Non-fatal public connectivity verifier
                │       ├── dns/          # User-space DNS forwarder, packet parser, ping, config
                │       └── v2ray/        # Xray config generator, settings, V2RayManager lifecycle
                ├── data/
                │   ├── local/            # Room AppDatabase, VpnProfile entity, VpnProfileDao
                │   ├── model/            # AppInfo model for per-app app picker
                │   └── repository/       # SettingsRepository, DnsSettingsRepository
                ├── feature/
                │   ├── dashboard/        # DashboardScreen, ViewModel, DnsScreens, BundledFlags
                │   └── splash/           # Animated splash screen
                └── service/
                    ├── HybridVpnService.kt       # Unified Android VpnService implementation
                    ├── VpnStateManager.kt        # State machine, traffic counter, session tracking
                    ├── VpnActionBus.kt           # SharedFlow event bus between Service and UI
                    ├── RoutingPolicyStatus.kt    # Active routing policy tracker
                    ├── LogStore.kt               # In-memory circular log buffer
                    └── VpnDisconnectReceiver.kt  # BroadcastReceiver for ACTION_KILL_ALL_VPN
```

---

## 3. Dependencies & Native Components

### Gradle Dependencies (`libs.versions.toml` & `app/build.gradle.kts`)
- **UI & Presentation:**
  - Jetpack Compose BOM `2026.02.01` (Compose UI, Graphics, Tooling Preview)
  - Material 3 (`androidx.compose.material3`) & Extended Icons (`androidx.compose.material:material-icons-extended`)
  - Navigation Compose `2.9.8` (`androidx.navigation:navigation-compose`)
  - Activity Compose `1.13.0` (`androidx.activity:activity-compose`)
  - Core Splashscreen `1.2.0` (`androidx.core:core-splashscreen`)
  - Coil Compose `2.7.0` (`io.coil-kt:coil-compose`)
- **Architecture & Local Storage:**
  - Room `2.7.0-alpha11` (`room-runtime`, `room-ktx`, `room-compiler` via KSP)
  - Lifecycle Runtime KTX `2.10.0` (`androidx.lifecycle:lifecycle-runtime-ktx`)
  - Core KTX `1.19.0` (`androidx.core:core-ktx`)
- **Scanning & Utilities:**
  - Google ML Kit Barcode Scanning `17.2.0` (`com.google.mlkit:barcode-scanning`) for QR code config imports
- **Testing:**
  - JUnit 4 (`4.13.2`), AndroidX JUnit (`1.3.0`), Espresso Core (`3.7.0`)
  - Compose UI Test JUnit4 & Manifest
  - `org.json:json:20240303` on JVM test classpath (bypasses Android stub "not mocked" exceptions)

### Native Libraries & Components
1. **`app/libs/libv2ray.aar`**:
   - Packaged Go-compiled Xray core via Gomobile.
   - Contains `libgojni.so` for 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
   - Assets included inside AAR: `geoip.dat`, `geosite.dat`, `geoip-only-cn-private.dat`.
   - Java bindings: `libv2ray.Libv2ray`, `libv2ray.CoreController`, `libv2ray.CoreCallbackHandler`.
2. **`libopenconnect.so` & `libstoken.so`** (`app/src/main/jniLibs/`):
   - Pre-compiled OpenConnect C shared libraries implementing the Cisco AnyConnect SSL/TLS VPN protocol (CSTP and DTLS).
   - RSA SecurID software token generation library (`libstoken.so`).
3. **`libhybridvpn.so`** (`app/src/main/cpp/native-lib.cpp`):
   - Native C++ JNI bridge linking OpenConnect, stoken, and Android OS logging.
   - Manages OpenConnect lifecycle thread, certificate auto-validation, credentials form injection, socket protection callback, command pipe cancellation, and immediate socket interrupt.

---

## 4. App Architecture & UI Layer

### Architecture Overview
- **Pattern:** MVVM (Model-View-ViewModel) + Single-Activity Architecture.
- **Dependency Injection:** Lightweight manual singleton pattern (e.g. `AppDatabase.getDatabase()`, `VpnStateManager`, `VpnActionBus`, `LogStore`, `BundledFlags`).
- **Reactive Data Streams:** Unidirectional data flow leveraging Kotlin `Coroutines`, `StateFlow`, `SharedFlow`, and Compose `collectAsStateWithLifecycle`.

### Storage & Persistence
- **Room Database (`cybernest_database`):**
  - Entity: `VpnProfile` (`id`, `name`, `protocol`, `serverAddress`, `username`, `password`, `isActive`, `countryCode`, `subName`, `ping`, `mAllowedAppsVpn`, `mAllowedAppsVpnAreDisallowed`).
  - DAO: `VpnProfileDao` provides flows of profiles, active profile queries, and CRUD operations.
- **SharedPreferences:**
  - `"hybridvpn_settings"`: Per-app routing toggle, allow/bypass mode, selected packages set, and monotonic `routing_revision`.
  - `"hybridvpn_dns"`: Selected DNS ID, custom DNS servers JSON array, hidden built-in IDs, cached pings.
  - `"hybridvpn_prefs"`: Session start timestamp (`START_TIME`), baseline Rx/Tx byte counts (`START_RX`, `START_TX`).
  - `"exit_reasons"`: Historical Android process exit tracking timestamp.

### UI Stack & Screens
- **Styling:** Custom dark cyberpunk/glassmorphism theme (`OpenNestTheme`, `BackgroundDark`, `SurfaceDark`, `PrimaryCyan`, `SurfaceGlass`) with custom Sora font family.
- **Navigation:**
  - `"splash"` (`SplashScreen`): App entry, initializes exit diagnostics and auto-navigates.
  - `"dashboard"` (`DashboardScreen`):
    - **Header & Hero Control:** Dynamic interactive Power Button reflecting current connection state (`CONNECTING`, `CONNECTED`, `DISCONNECTING`, `CANCELING`, `ERROR`).
    - **Live Network Card:** IP address, location, country flag (loaded synchronously from bundled assets via `BundledFlags`), ISP name, and live diagnostic badge.
    - **Metrics Bar:** Dynamic session uptime counter and real-time DL / UL throughput meters (`TrafficStats` polling).
    - **Protocol Switcher:** 3-way toggle between **OpenConnect**, **v2rayNG**, and **DNS**.
    - **Server List / Subscription Groups:** Server cards with latency pings, country flag badges, edit/delete actions, and subscription volume/expiry metadata info cards.
    - **Import & Modals:** Manual OpenConnect form, V2Ray link parser (VLESS/VMess/Trojan), QR camera scanner (ML Kit), subscription link updater.
    - **Per-App Routing Sheet:** App selector with search, select-all, and Allow vs Bypass mode toggles.
    - **Live Logs Sheet:** Real-time filtered, sanitized log streaming with producer-side credential redaction.
- **DNS UI (`DnsScreens.kt`):**
  - Dedicated server list screen for DNS mode, custom server creator/editor, ping test actions, and provider hiding.

---

## 5. VPN Core Architecture (Detailed)

### Unified `HybridVpnService` Architecture
A single `VpnService` subclass (`dev.rouzbeh.hybridvpn.service.HybridVpnService`) serves as the single host for all three connection protocols. This eliminates multi-service conflicts on Android, centralizes `VpnService.Builder` TUN instantiation, and guarantees clean teardown synchronization.

- **Foreground Notification:**
  - Notification channel `vpn_notification_channel` (`IMPORTANCE_LOW`, silent).
  - Configured with `foregroundServiceType="specialUse"` (subtype: `"VPN Tunnel Notification Absorption"`).
  - Ongoing notification shows active country emoji/flag, location, formatted DL/UL speed, and a `"Disconnect"` pending intent.
- **TUN Interface Management:**
  - Created via `VpnService.Builder`.
  - OpenConnect & v2rayNG use MTU `1400`; DNS mode uses MTU `1500`.
  - FD Handoff: The TUN `FileDescriptor` is duplicated via `android.system.Os.dup(pfd.fileDescriptor)` (retrieving the integer descriptor via reflection on `FileDescriptor.descriptor`) and passed to native engines. Fallback calls `pfd.detachFd()`.
- **Session Identification & Race Prevention:**
  - Monotonic `sessionId` generation: every connection attempt advances `sessionId`.
  - Asynchronous callbacks and log emissions from superseded sessions are strictly ignored if their captured `sessionId != currentSessionId`.
  - Teardown synchronization: `stopAllEngines` launches an asynchronous `stopJob` on `stopScope` and signals completion through `VpnActionBus.teardownComplete`. A newly starting session calls `stopJob?.join()` and awaits completion before creating a new TUN interface.
- **Intentional Disconnect vs Crash Handling:**
  - Calling disconnect triggers `VpnStateManager.beginIntentionalCancel()`.
  - Native errors resulting from canceled pipes (e.g. OpenConnect mainloop returning `-4`) are mapped to `CANCELLED` / `DISCONNECTED` instead of `ERROR` / `FAILED`.

---

### Protocol 1: AnyConnect (OpenConnect)
- **Integration Layer:** Native JNI bridge in `native-lib.cpp` linked against `libopenconnect.so` and `libstoken.so`.
- **Connection & Authentication Lifecycle:**
  1. `startOpenConnectPath` calls JNI `startOpenConnectNative`.
  2. Spawns dedicated C++ background thread `vpn_thread_func`.
  3. `openconnect_vpninfo_new(...)` registers callbacks for cert validation, auth forms, socket protection, and progress logs.
  4. Automatically accepts peer certificates (`on_validate_peer_cert`).
  5. Injects credentials into authentication form fields (`username`, `password`).
  6. Socket protection: `on_protect_socket` attaches to the JVM and calls `HybridVpnService.protect(fd)` so transport sockets bypass the tunnel.
  7. Socket tracking: All created sockets are registered in `state->protected_fds`.
  8. Authentication completes, CSTP (TLS) and DTLS (UDP) connections are established.
  9. Native layer reads assigned IP and DNS list (`openconnect_get_ip_info`), then calls back into Java `establishTunNative(assigned_ip, dns_list)`.
  10. Handshake hands off TUN FD via `openconnect_setup_tun_fd(vpninfo, tun_fd)`.
  11. Enters `openconnect_mainloop(vpninfo, 300, 10)`.
- **Fast Cancellation:**
  - `interruptOpenConnectNative()` immediately issues `shutdown(fd, SHUT_RDWR)` on all tracked sockets and writes to the command pipe `cancel_fd` (`"x"` byte), instantaneously terminating blocking SSL/TLS read/connect syscalls without waiting for timeouts.

---

### Protocol 2: v2rayNG (V2Ray / Xray)
- **Integration Layer:** Pre-built `libv2ray.aar` containing Xray core (`libgojni.so` via Gomobile).
- **Configuration & Link Parsing (`XrayConfigGenerator.kt`):**
  - Parses share links: `vless://`, `vmess://` (Base64 JSON format), `trojan://`.
  - Transports: TCP (raw / HTTP header), WebSocket (`ws`), gRPC, HTTPUpgrade, XHTTP / SplitHTTP, mKCP.
  - Security: Plain TLS, Reality (with `publicKey`, `shortId`, `spiderX`, `fingerprint`).
  - MUX Support: Optional outbound multiplexing (`xudpConcurrency`, QUIC reject).
- **Circular DNS Deadlock Prevention (Bootstrap Resolution):**
  - Before starting the core, `preparePinnedXrayConfig` resolves the proxy domain on the underlying network to an IPv4 address.
  - The resolved IP is pinned into `dns.hosts` and routed to the `direct` outbound. This prevents the circular dependency of resolving the proxy host through the proxy itself.
- **Xray Engine Runtime:**
  - Inbounds:
    - `tun-in` (`protocol: tun`): Directly ingests packets from the duplicated `tunFd` (MTU 1400, userLevel 8, sniffing HTTP/TLS enabled).
    - `socks-in`: `127.0.0.1:10808`.
    - `http-in`: `127.0.0.1:10809`.
  - Outbounds: `proxy` (configured link), `direct` (freedom), `block` (blackhole), `dns-out` (dns protocol handler).
  - DNS Module: Built-in DNS module configured with remote DNS `tcp://1.1.1.1` (forwarded securely through the proxy) and domestic DNS `178.22.122.100` (routed direct). Port 53 queries from `tun-in` are hijacked to `dns-out`.
  - Dedicated Speed Test Config (`generatePingConfig`): Real ping / delay measurements use an ephemeral, trimmed config without session DNS hijacking or MUX to ensure direct delay measurement without proxy loops.
  - Core Execution: `Libv2ray.newCoreController(callbacks)` -> `controller.startLoop(configJson, tunFd)` executed on dedicated single-thread executor (`xray-core-thread`).
  - Teardown: `controller.stopLoop()` executed in separate thread with a 3000ms join deadline.

---

### Protocol 3: DNS Mode (DNS-over-TUN)
- **Concept:** A lightweight, non-proxying mode where **only DNS traffic** is intercepted and redirected to custom or privacy-preserving DNS resolvers (Google, Cloudflare, Quad9, AdGuard, etc.). The user's IP is **not** altered, and regular traffic does not pass through any proxy.
- **TUN Configuration:**
  - TUN Interface IP: `10.10.10.1/32` (IPv4), `fd00:646e:7373::1/128` (IPv6).
  - Advertised DNS Server: `10.10.10.53` (IPv4), `fd00:646e:7373::53` (IPv6).
  - Routes Added: **Strictly** `10.10.10.53/32` and `fd00:646e:7373::53/128`. No default route (`0.0.0.0/0`) is installed.
- **User-Space Forwarder (`DnsForwarder.kt`):**
  - Reader thread reads raw IP packets directly off the TUN `FileDescriptor`.
  - Packet Parser (`DnsPacket.kt`): Parses IPv4 / IPv6 headers, verifies destination IP is the fake DNS address (`10.10.10.53`), and extracts UDP or TCP DNS payloads.
  - Relaying:
    - UDP queries are forwarded to the configured primary (or secondary fallback) upstream resolver using protected UDP sockets (`protectUdp`).
    - If the response has the Truncation bit set (`TC=1`), the forwarder automatically retries the query over a protected TCP socket according to RFC 7766.
    - Upstream timeouts trigger an immediate retry and failover to the secondary resolver. If both fail, a `SERVFAIL` packet is generated and written back to the TUN.
  - Packet Synthesis: Constructs IPv4/IPv6 responses, recalculates standard Internet checksums (IP header checksum, UDP pseudo-header checksum, TCP pseudo-header checksum), and writes raw bytes back into the TUN fd.

---

### Per-App Split Tunneling Contract (`docs/PER_APP_CONTRACT.md`)

Implemented via `HybridVpnService.applyPerAppRouting` and resolved via `RoutingPolicyResolver`:

| Method | Global Mode | ALLOW Mode | BYPASS Mode |
|---|---|---|---|
| **OpenConnect** | None (all apps routed) | Selected apps + `dev.rouzbeh.hybridvpn` | Selected apps disallowed |
| **v2rayNG** | `dev.rouzbeh.hybridvpn` disallowed | Selected apps only (`dev.rouzbeh.hybridvpn` excluded) | Selected apps + `dev.rouzbeh.hybridvpn` disallowed |
| **DNS-Only** | `dev.rouzbeh.hybridvpn` disallowed | Selected apps only (`dev.rouzbeh.hybridvpn` excluded) | Selected apps + `dev.rouzbeh.hybridvpn` disallowed |

**Core Invariants:**
1. **Single API Family:** `VpnService.Builder` must never be called with both `addAllowedApplication` and `addDisallowedApplication` in the same session.
2. **Self-Package Semantics:**
   - OpenConnect protects its sockets via `protect()` and includes `HybridNest` inside the tunnel during ALLOW mode so its in-app exit IP detection succeeds.
   - V2Ray runs in-process without socket protection; allowing `HybridNest` inside the tunnel would cause outbound proxy traffic to loop into its own TUN. V2Ray detects exit IP via its local HTTP proxy (`127.0.0.1:10809`).
   - DNS forwarder upstream sockets must never loop into the TUN.
3. **Fail-Closed Policy:** In ALLOW mode, if all selected apps are uninstalled or missing, connection is refused (`RoutingRefusedException`) rather than silently widened to global routing.

---

## 6. AndroidManifest & Permissions

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" tools:node="remove" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" tools:ignore="QueryAllPackagesPermission" />

    <application
        android:name=".HybridVpnApplication"
        android:allowBackup="true"
        android:theme="@style/Theme.OpenNest"
        android:usesCleartextTraffic="true">

        <service
            android:name="dev.rouzbeh.hybridvpn.service.HybridVpnService"
            android:exported="false"
            android:foregroundServiceType="specialUse"
            android:permission="android.permission.BIND_VPN_SERVICE">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="VPN Tunnel Notification Absorption" />
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>

        <activity
            android:name="dev.rouzbeh.hybridvpn.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.App.Starting"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".service.VpnDisconnectReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="ACTION_KILL_ALL_VPN" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

---

## 7. Key Files Map

| Relative File Path | Exact Responsibilities |
|---|---|
| `app/src/main/java/dev/rouzbeh/hybridvpn/HybridVpnApplication.kt` | Application class; configures uncaught exception handlers and historical exit diagnostics (`ApplicationExitInfo`). |
| `app/src/main/java/dev/rouzbeh/hybridvpn/MainActivity.kt` | Single Activity host; handles system splash screen installation, edge-to-edge layout, and Compose navigation. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/service/HybridVpnService.kt` | Unified VPN service (~1400 lines); manages TUN allocation, session IDs, routing policies, and dispatches OpenConnect, V2Ray, and DNS engines. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/service/VpnStateManager.kt` | Thread-safe state container; tracks `ConnectionState`, speed calculation (`TrafficStats`), persistent start times, and network info. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/service/VpnActionBus.kt` | Asynchronous decoupled event bus (`disconnectFlow`, `teardownComplete`) coordinating Service and ViewModel actions. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/service/RoutingPolicyStatus.kt` | Single source of truth for the actively established routing policy revision and teardown reasons. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/service/LogStore.kt` | Process-wide circular in-memory log buffer (500 lines per protocol) persisting across UI recreations. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/utils/LogRedactor.kt` | Producer-side redaction utility scrubbing auth cookies, session tokens, passwords, and authorization headers from logs. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/vpn/v2ray/V2RayManager.kt` | Manages Gomobile `CoreController` lifecycle, config validation, and outbound ping tests. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/vpn/v2ray/XrayConfigGenerator.kt` | Generates full session and trimmed ping JSON configs for Xray; handles VLESS/VMess/Trojan and transport stream settings. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/vpn/v2ray/V2raySettings.kt` | Constants for DNS, MUX concurrency, XUDP settings, and routing tags. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/vpn/dns/DnsForwarder.kt` | Packet-level user-space forwarder; reads TUN IP packets, forwards DNS to upstreams over protected sockets, rewrites checksums. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/vpn/dns/DnsPacket.kt` | Low-level IPv4, IPv6, UDP, and TCP header parser and binary serializer. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/vpn/dns/DnsServer.kt` | Domain model for DNS servers, built-in resolvers catalog, and IP validator. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/vpn/ConnectivityDiagnostics.kt` | Bounded diagnostic verifier checking DNS and HTTP reachability through established tunnels without tearing them down. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/routing/RoutingPolicy.kt` | Immutable policy snapshot, routing decisions (`Global`, `Allow`, `Bypass`, `Refused`), and `RoutingRefusedException`. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/routing/RoutingPolicyResolver.kt` | Pure JVM resolver resolving desired policies against installed applications. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/data/local/VpnProfile.kt` | Room entity representing a saved OpenConnect or V2Ray server profile. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/data/local/VpnProfileDao.kt` | Room DAO with reactive Flow queries and CRUD operations. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/data/local/AppDatabase.kt` | Room database definition (`cybernest_database`). |
| `app/src/main/java/dev/rouzbeh/hybridvpn/data/repository/SettingsRepository.kt` | SharedPreferences storage for per-app routing and policy revisions. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/data/repository/DnsSettingsRepository.kt` | SharedPreferences storage for DNS server list, selections, and latency caches. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/feature/dashboard/DashboardViewModel.kt` | Central ViewModel (~1470 lines); handles server pinging, network IP lookups, subscriptions, and state coordination. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/feature/dashboard/DashboardScreen.kt` | Main Composable screen (~4000 lines); contains all UI dialogs, dashboard cards, animations, and controls. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/feature/dashboard/BundledFlags.kt` | In-memory cache and synchronous decoder for bundled 1280px lossless WebP country flags. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/feature/dashboard/DnsScreens.kt` | Full-screen UI for DNS-only server selection, editing, and latency testing. |
| `app/src/main/java/dev/rouzbeh/hybridvpn/feature/dashboard/InfoRemarkClassifier.kt` | Natural language and regex classifier extracting subscription bandwidth and duration metadata from config names. |
| `app/src/main/cpp/native-lib.cpp` | C++ JNI bridge (~690 lines); controls OpenConnect main loop, thread lifecycle, and socket interruption. |

---

## 8. Current Observations (Read-Only)

1. **Monolithic UI Files:**
   `DashboardScreen.kt` is over 4,000 lines, housing numerous sub-components, dialogs, bottom sheets, and animations in one file. A future architectural refactoring would benefit from decomposing this into dedicated feature components.
2. **Reflection on Private Android Field:**
   In `HybridVpnService.kt`, the integer descriptor is extracted via Java reflection:
   ```kotlin
   val f = java.io.FileDescriptor::class.java.getDeclaredField("descriptor").apply { isAccessible = true }
   val rawFd = f.getInt(dup)
   ```
   While reliable on current Android versions, accessing private platform fields carries greylist/hidden API restriction risks on future Android releases.
3. **Hardcoded Endpoints & Fallbacks:**
   - `V2raySettings.kt`: Hardcodes `VPN_DNS = "9.9.9.9"`, `REMOTE_DNS = "tcp://1.1.1.1"`, and `DOMESTIC_DNS = "178.22.122.100"`.
   - `DnsTunnelConfig`: Hardcodes fake addresses `10.10.10.1`, `10.10.10.53`, `fd00:646e:7373::1`, and `fd00:646e:7373::53`.
   - `ConnectivityDiagnostics.kt`: Hardcodes probe host `cp.cloudflare.com` and raw IP `1.1.1.1`.
4. **Synchronous Network Calls for Subscription Fetching:**
   `DashboardViewModel.updateSubscription` directly utilizes blocking `java.net.URL(url).openConnection()` on `Dispatchers.IO` without HTTP connection pooling or custom TLS handling.
5. **Google Play Policy Considerations:**
   - Manifest declares `android.permission.QUERY_ALL_PACKAGES` (required for per-app split tunneling selection). Distribution via Google Play requires explicit policy justification.
   - Foreground service uses type `specialUse` with subtype `"VPN Tunnel Notification Absorption"`. Standard Play Store review requires justification for `specialUse`.
6. **Destructive Migration on Database:**
   `AppDatabase` is configured with `.fallbackToDestructiveMigration()`. Incrementing the database version without defining Room `Migration` paths will wipe existing user profiles.
7. **Global Cleartext Traffic Allowed:**
   `android:usesCleartextTraffic="true"` is declared in `AndroidManifest.xml` at the application level.
