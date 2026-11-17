# HybridNest

<p align="center">
  <strong>Advanced Multi-Protocol Android VPN Client</strong><br>
  <em>Unified Native Cisco AnyConnect, WireGuard (gVisor Netstack), and v2rayNG (Xray-core)</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024--36)-3DDC84?logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose">
  <img src="https://img.shields.io/badge/16KB%20Page%20Size-Aligned%20(Android%2015%2B)-success" alt="16KB Page Aligned">
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg" alt="License">
</p>

---

## 🚀 Overview

**HybridNest** is an enterprise-grade, high-performance Android VPN client built from the ground up in modern Kotlin and Jetpack Compose. It unifies three distinct, battle-tested VPN and anti-censorship engines into a single seamless interface with zero root requirements, fail-closed split tunneling, and 16KB page-size alignment for Android 15/16+:

1. **Cisco AnyConnect (`OpenConnect`)**:
   - Native C / JNI integration with `libopenconnect` and `libstoken`.
   - Dual-channel SSL/TLS control plane (`CSTP`) and ultra-fast UDP datapath (`DTLS`).
   - Integrated hardware-token RSA SecurID token generation and XML form authentication.
   - Configurable MTU, Dead Peer Detection (DPD), batch packet processing, and atomic socket cancellation.

2. **WireGuard (`gVisor Netstack`)**:
   - User-space TCP/IP netstack outbound (`noKernelTun`) bypassing kernel TUN limitations.
   - Pure-Kotlin RFC 7748 Curve25519 keypair generation with strict cryptographic zeroization.
   - Comprehensive configuration import: `.conf`, `.zip` bundles, camera/gallery QR scanning, and URI deep-links.
   - Pre-resolved endpoint IPv4 bootstrap pinning to neutralize DNS poisoning on initial handshake.

3. **v2rayNG (`Xray-core`)**:
   - Embedded Gomobile `Xray-core` runtime with support for modern anti-censorship protocols: **VLESS**, **VMess**, **Trojan**, **Reality**, **XHTTP / SplitHTTP**, **WebSocket**, and **gRPC**.
   - Tun2socks routing with TCP multiplexing (`MUX`), TLS padding, and domain-based routing rules.

---

## 🌟 Key Features

- **Fail-Closed Per-App Split Tunneling**:
  - **Allow Mode**: Only selected applications route traffic through the encrypted VPN tunnel.
  - **Bypass Mode**: Selected applications bypass the VPN directly while all other device traffic is tunneled.
  - Dynamic installed app scanner with real-time search, package filtering, and zero-leak state persistence.

- **Domain Bypass (Direct Sites)**:
  - Built-in one-tap direct routing for domestic domains (`.ir` direct bypass rule).
  - Custom domain routing rules in Xray-core for low-latency direct local access without disconnecting.

- **Universal Hotspot & LAN VPN Sharing (No Root Required)**:
  - Built-in HTTP / SOCKS5 proxy relay server running over active VPN tunnels.
  - Automatic detection and display of active Wi-Fi / Tethering / Hotspot interface IP (e.g., `192.168.43.1`).
  - Clear step-by-step connection guide for PCs, consoles, smart TVs, and other mobile devices on the same local network.

- **Full JSON Profile Backup & Restore**:
  - Export and import all VPN configurations (AnyConnect, WireGuard, and Xray-core) via structured, encrypted or unencrypted JSON files using Android's Storage Access Framework (SAF).

- **Live Sanitized Diagnostic Logs**:
  - In-app live logging console with category filtering (`ALL`, `OpenConnect`, `WireGuard`, `v2rayNG`).
  - Producer-side real-time credential, token, and private key redaction via `LogRedactor` before any log line reaches memory or disk.

- **Android 15+ 16KB Page Size Alignment**:
  - All native libraries (`.so`) and CMake toolchains are built and verified with `-Wl,-z,max-page-size=16384` to guarantee compatibility with Android 15/16 memory page architecture.

---

## 📦 Download Guide (GitHub Releases)

Download the latest signed release APKs directly from [GitHub Releases](https://github.com/iRouzBeH/HybridNest/releases):

| APK File | Architecture | Description & Recommendation |
|:---|:---:|:---|
| **`HybridNest-v1.0-arm64-v8a.apk`** | `arm64-v8a` | **Recommended (Modern Phones)**<br>Optimized for 64-bit ARM devices (95%+ of Android smartphones manufactured since 2018). Provides the smallest download size and maximum CPU performance. |
| **`HybridNest-v1.0-armeabi-v7a.apk`** | `armeabi-v7a` | **Older / 32-bit Phones**<br>Targeted for older 32-bit ARM smartphones, legacy budget tablets, and 32-bit Android TV boxes. |
| **`HybridNest-v1.0-universal.apk`** | Multi-ABI | **Universal (All ARM Devices)**<br>Contains native binaries for both 32-bit and 64-bit architectures. Recommended if you are unsure of your device architecture or redistributing across multiple hardware types. |

> [!TIP]
> Download the `SHA256SUMS.txt` file from [GitHub Releases](https://github.com/iRouzBeH/HybridNest/releases) to verify the integrity and cryptographic authenticity of your downloaded APK.

---

## 🛠️ Building from Source

### Prerequisites
- **JDK 17 or JDK 21**
- **Android SDK** (API Level 36/37)
- **Android NDK** (`26.3.11579264`)
- **CMake** (`3.22.1+`)

### 1. Clone the Repository
```bash
git clone https://github.com/iRouzBeH/HybridNest.git
cd HybridNest
```

### 2. Run JVM Unit Tests
```bash
./gradlew testDebugUnitTest
```

### 3. Build Release APKs
To produce signed release APKs, ensure `keystore.properties` is configured in the root directory:
```properties
storeFile=hybridnest-release.jks
storePassword=YourStorePassword
keyAlias=YourKeyAlias
keyPassword=YourKeyPassword
```

Run the Gradle release assembly:
```bash
./gradlew assembleRelease
```
The resulting split APKs will be output to `app/build/outputs/apk/release/`:
- `app-arm64-v8a-release.apk`
- `app-armeabi-v7a-release.apk`
- `app-universal-release.apk`

---

## 🔒 Security & Privacy

- **No Analytics / Telemetry**: HybridNest contains zero trackers, third-party analytics SDKs, or advertising libraries.
- **Key Zeroization**: Sensitive private keys and passwords are scrubbed from memory buffers upon tunnel establishment.
- **Log Sanitation**: `LogRedactor` prevents credentials, PSKs, tokens, and authorization headers from ever being rendered or exported.

---

## 📄 License
This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for details.
