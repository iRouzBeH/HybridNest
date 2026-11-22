# HybridNest v1.0 — Initial Official Release 🚀

Welcome to the initial official release of **HybridNest**, an advanced multi-protocol Android VPN client built with modern Kotlin and Jetpack Compose. HybridNest unifies native Cisco AnyConnect, WireGuard (gVisor Netstack), and v2rayNG (Xray-core) into a single, high-performance interface with zero root requirements.

---

### 🌟 What's New in v1.0

#### 1. 🛡️ Three Unified VPN Engines
- **Cisco AnyConnect (`OpenConnect`)**:
  - Native C / JNI implementation with `libopenconnect` and `libstoken`.
  - Seamless dual-channel CSTP (SSL/TLS) and DTLS (UDP datapath) tunneling.
  - Integrated RSA SecurID hardware-token generation and form authentication.
  - Instant socket teardown and configurable MTU / Dead Peer Detection.
- **WireGuard (`gVisor Netstack`)**:
  - Outbound user-space TCP/IP netstack bypassing kernel TUN constraints.
  - Pure-Kotlin RFC 7748 Curve25519 keypair generation.
  - Full import support: `.conf` files, `.zip` bundles, QR scanning (camera & gallery), and deep links.
  - Pre-resolved endpoint IPv4 bootstrap pinning to mitigate DNS poisoning on initial handshake.
- **v2rayNG (`Xray-core`)**:
  - Embedded Gomobile `Xray-core` runtime.
  - Full support for VLESS, VMess, Trojan, Reality, XHTTP / SplitHTTP, WebSocket, gRPC, and TCP MUX.
  - Domain-based routing and tun2socks packet handling.

#### 2. ⚡ Core Capabilities
- **Fail-Closed Per-App Split Tunneling**: Choose between **Allow Mode** (tunnel only selected apps) and **Bypass Mode** (exclude selected apps from tunnel).
- **Direct Domain Bypass**: One-tap direct routing for domestic `.ir` websites to prevent unnecessary latency.
- **Universal Hotspot & LAN Sharing (No Root)**: Share your active VPN connection over Wi-Fi Hotspot or local network via built-in HTTP/SOCKS5 proxy relay with auto-detected gateway IP and step-by-step client guide.
- **Profile Backup & Restore**: Full JSON import/export for all protocol configurations via clipboard (with pre-export plaintext confirmation warning and Android 13+ sensitive clipboard flag).
- **Live Sanitized Diagnostic Logs**: Filterable in-app log viewer with producer-side credential, token, and private key redaction via `LogRedactor` (Strict Log Redaction can be toggled by the user in Settings).
- **16KB Memory Page Size Aligned**: All native libraries compiled with `-Wl,-z,max-page-size=16384` for full Android 15/16 compatibility.

---

### 📦 Which APK Should I Download?

| APK File | Architecture | Target Devices | Size |
|:---|:---:|:---|:---:|
| **`HybridNest-v1.0-arm64-v8a.apk`** | `arm64-v8a` | **Recommended (95%+ of modern smartphones since 2018)**. Smaller size, optimal battery & performance. | ~30 MB |
| **`HybridNest-v1.0-armeabi-v7a.apk`** | `armeabi-v7a` | **Legacy 32-bit ARM smartphones, older tablets, and budget Android TV boxes**. | ~30 MB |
| **`HybridNest-v1.0-universal.apk`** | Universal | **All ARM devices**. Includes binaries for both 32-bit and 64-bit architectures. | ~79 MB |

---

### 🔒 File Integrity Verification (SHA-256 Checksums)

Verify the integrity of your download:

```text
e619357f293807e20820a95b056fc5605568887dc7e97f6f26d435ac72adb9f6  HybridNest-v1.0-arm64-v8a.apk
b2e27f68f6a80ce94d1648b4739e3a6a769b6f7b5b491cc672115179e2bc1261  HybridNest-v1.0-armeabi-v7a.apk
a2a365057cc0de088ed307015f581f8d1dd1be9f37553181ab9314e3a9f9fd0f  HybridNest-v1.0-universal.apk
```
