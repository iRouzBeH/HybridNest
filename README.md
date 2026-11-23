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

**HybridNest** is an advanced Android VPN client built from the ground up in modern Kotlin and Jetpack Compose. It unifies three distinct VPN and anti-censorship engines into a single seamless interface with zero root requirements, fail-closed split tunneling, and 16KB page-size alignment for Android 15/16+:

1. **Cisco AnyConnect (`OpenConnect`)**:
   - Native C / JNI integration with `libopenconnect` (`libstoken.so` is bundled alongside `libopenconnect.so`).
   - Dual-channel SSL/TLS control plane (`CSTP`) and UDP datapath (`DTLS`).
   - XML form authentication support.
   - Configurable MTU, Dead Peer Detection (DPD), batch packet processing, and atomic socket cancellation.

2. **WireGuard (`gVisor Netstack`)**:
   - User-space TCP/IP netstack outbound (`noKernelTun`) bypassing kernel TUN limitations.
   - Pure-Kotlin RFC 7748 Curve25519 keypair generation.
   - Comprehensive configuration import: `.conf`, `.zip` bundles, camera/gallery QR scanning, and URI deep-links.
   - Pre-resolved endpoint IPv4 bootstrap pinning to mitigate DNS poisoning on initial handshake.

3. **v2rayNG (`Xray-core`)**:
   - Embedded Gomobile `Xray-core` runtime with support for modern anti-censorship protocols: **VLESS**, **VMess**, **Trojan**, **Reality**, **XHTTP / SplitHTTP**, **WebSocket**, and **gRPC**.
   - Tun2socks routing with TCP multiplexing (`MUX`), TLS padding, and domain-based routing rules.

---

## 🌟 Key Features

- **Full-Screen Glass UI & Unsaved Changes Guard**:
  - Redesigned full-screen settings (AnyConnect, WireGuard, v2rayNG, Features & Diagnostics, Check for Update, About), harmonized Server List, and Split Tunneling screen with squircle app icons.
  - Unsaved-changes confirmation prompts across configuration screens to prevent accidental data loss.

- **Per-Protocol State Persistence & Seamless Server Switching**:
  - Remembers the last selected server per protocol and custom `v2rayNG` tab order/selection across restarts.
  - Supports direct same-protocol server switching (`Reconnecting`) while connected without requiring manual disconnection.

- **Multi-Provider Parallel GeoIP Consensus**:
  - Exit IP and country detection powered by parallel HTTPS lookups (`ipinfo.io` + `ipwho.is` with fallback tie-breaking) for accurate and resilient detection.

- **Real GitHub Releases Update Check**:
  - Queries the official GitHub Releases API on demand (proxy-aware when connected via `v2rayNG` or `WireGuard`) and opens the official release page in the browser.

- **Enhanced `v2rayNG` Subscription & Ping Controls**:
  - Full-screen locking animation during subscription updates, animated batch ping cards, and instant stoppable ping on `SingleLinks` and subscription tabs.

- **Default `.ir` Domain Bypass**:
  - Pre-selects `.ir` for domain bypass out of the box while preserving explicit user edits and removal.
  - Custom domain routing rules in Xray-core for low-latency direct local access without disconnecting.

- **Fail-Closed Per-App Split Tunneling**:
  - **Allow Mode**: Only selected applications route traffic through the encrypted VPN tunnel.
  - **Bypass Mode**: Selected applications bypass the VPN directly while all other device traffic is tunneled.
  - Dynamic installed app scanner with real-time search, package filtering, and state persistence.

- **Universal Hotspot & LAN VPN Sharing (No Root Required)**:
  - Built-in HTTP / SOCKS5 proxy relay server running over active VPN tunnels.
  - Automatic detection and display of active Wi-Fi / Tethering / Hotspot interface IP (e.g., `192.168.43.1`).
  - Clear step-by-step connection guide for PCs, consoles, smart TVs, and other mobile devices on the same local network.

- **Full JSON Profile Backup & Restore**:
  - Export and import all VPN configurations (AnyConnect, WireGuard, and Xray-core) via structured plaintext JSON copied to or pasted from the clipboard (featuring a pre-export plaintext warning dialog and Android 13+ sensitive clipboard flag).

- **Live Sanitized Diagnostic Logs**:
  - In-app live logging console with category filtering (`ALL`, `OpenConnect`, `WireGuard`, `v2rayNG`).
  - Producer-side real-time credential, token, and private key redaction via `LogRedactor` before any log line reaches memory or disk (Strict Log Redaction can be disabled by the user in Settings).

- **Android 15+ 16KB Page Size Alignment**:
  - All native libraries (`.so`) and CMake toolchains are built and verified with `-Wl,-z,max-page-size=16384` to guarantee compatibility with Android 15/16 memory page architecture.

---

## 📦 Download Guide (GitHub Releases)

Download the latest signed release APKs directly from [GitHub Releases](https://github.com/iRouzBeH/HybridNest/releases):

| APK File | Architecture | Description & Recommendation |
|:---|:---:|:---|
| **`HybridNest-v1.1-arm64-v8a.apk`** | `arm64-v8a` | **Recommended (Modern Phones)**<br>Optimized for 64-bit ARM devices (95%+ of Android smartphones manufactured since 2018). Provides the smallest download size and maximum CPU performance. |
| **`HybridNest-v1.1-armeabi-v7a.apk`** | `armeabi-v7a` | **Older / 32-bit Phones**<br>Targeted for older 32-bit ARM smartphones, legacy budget tablets, and 32-bit Android TV boxes. |
| **`HybridNest-v1.1-universal.apk`** | Multi-ABI | **Universal (All ARM Devices)**<br>Contains native binaries for both 32-bit and 64-bit architectures. Recommended if you are unsure of your device architecture or redistributing across multiple hardware types. |

> [!TIP]
> Download the `SHA256SUMS.txt` file from [GitHub Releases](https://github.com/iRouzBeH/HybridNest/releases) to verify the file integrity of your downloaded APK.

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

- **No Analytics / Telemetry**: HybridNest includes no analytics, telemetry, or advertising SDKs. It uses Google ML Kit Barcode Scanning (`com.google.mlkit:barcode-scanning`) strictly for on-device QR code scanning.
- **Log Sanitation**: `LogRedactor` prevents credentials, PSKs, tokens, and authorization headers from ever being rendered or exported (Strict Log Redaction can be disabled by the user in Settings).

---

## ⚠️ Known Limitations

1. **Local Plaintext Credential Storage**: Profile credentials (OpenConnect passwords and WireGuard private keys) are stored in plaintext in the local Room database inside the Android app private sandbox (`allowBackup="false"` with Android cloud backup and device transfer excluded).
2. **Plaintext Profile Export via Clipboard**: Profile export produces unencrypted (plaintext) JSON copied to the clipboard (guarded by a pre-export confirmation warning dialog and flagged with `EXTRA_IS_SENSITIVE` on Android 13+).
3. **User-Configurable Log Redaction**: Strict Log Redaction can be disabled by the user in Settings.

---

## 📄 License
This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for details.
