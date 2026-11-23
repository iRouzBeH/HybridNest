## HybridNest v1.1

HybridNest **v1.1** (`versionCode 2`) introduces a fully redesigned full-screen glass interface across settings, server lists, and Split Tunneling, alongside parallel HTTPS GeoIP consensus, real GitHub Releases update checking, smoother protocol and server switching, and comprehensive performance and visual polish.

> **Upgrade Safety:** The Room database schema remains at `version = 1`. Updating from `v1.0` preserves all existing server profiles, credentials, custom configurations, and settings without migration friction.

---

### Highlights & New Features

- **Full-Screen Glass Settings & Diagnostics:** Redesigned AnyConnect, WireGuard, v2rayNG, Features & Diagnostics, Check for Update, and About as full-screen views (removing legacy dialog shells), with improved header wrapping on narrower screens.
- **Unsaved Changes Confirmation:** Added `Save` / `Discard` / `Cancel` confirmation when exiting editable settings screens with unsaved edits, preserving input validation and `ConnectionLockGuard` checks.
- **Real GitHub Releases Update Check:** Queries the official GitHub Releases API (`iRouzBeH/HybridNest`) on demand (routing through the local Xray HTTP proxy when connected via `v2rayNG` or `WireGuard`), displays honest error states, and opens the official release page in the browser.
- **Multi-Provider Parallel GeoIP Consensus:** Queries `ipinfo.io` and `ipwho.is` in parallel with fallback tie-breaking and removes dead endpoints for more accurate exit-IP and country/flag detection.
- **Redesigned Split Tunneling UI & Crisp App Icons:** Harmonized Split Tunneling with the dark glass UI, added high-DPI squircle app icons backed by an in-memory `LruCache`, added search and filter chips (`All` / `Selected` / `System`), and enforced `ConnectionLockGuard` on app checkboxes while strictly preserving `PER_APP_CONTRACT.md` (`fail-closed`).
- **Per-Protocol Persistence & Seamless Switching:** Remembers the last selected server per protocol and custom `v2rayNG` tab order/selection across restarts.
- **Direct Same-Protocol Server Switching:** Selecting another server in the active protocol while connected transitions cleanly via `Reconnecting` without requiring a manual disconnect.
- **Enhanced `v2rayNG` Subscriptions & Ping Controls:** Full-screen locking orbital animation during subscription updates, animated ping indicators on individual server cards during batch latency tests, and instant stoppable ping on `SingleLinks` and subscription tabs (with `Update subscription` hidden on `SingleLinks`).
- **Default `.ir` Domain Bypass:** Pre-selects `.ir` in Domain Bypass on fresh install and on Reset, while preserving explicit user edits or removal.

---

### Bug Fixes & Polish

- Eliminated the momentary `"Select Server"` flicker when switching between `AnyConnect`, `WireGuard`, and `v2rayNG`.
- Fixed `v2rayNG` tab capsule borders breaking on tap and aligned unnamed subscription header subtitles (`SubLink 1`, etc.).
- Fixed top spacing on `AnyConnect` and `WireGuard` server lists below the header divider.
- Fixed WireGuard server cards showing the AnyConnect fallback icon and raw `wireguard://` URI; cards now show the WireGuard icon and a clean `host:port` subtitle without exposing private key material.
- Shortened the dashboard `"Test Ping"` label to `"Ping"`.
- Verified with **278/278** unit tests passing.

---

### Downloads & Verification

| APK File | Architecture | Target Devices | Size |
|:---|:---:|:---|:---:|
| **`HybridNest-v1.1-arm64-v8a.apk`** | `arm64-v8a` | **Recommended (95%+ of modern Android devices)**. Optimized for 64-bit ARM. | ~30 MB |
| **`HybridNest-v1.1-armeabi-v7a.apk`** | `armeabi-v7a` | **Legacy 32-bit ARM smartphones, older tablets, and budget TV boxes**. | ~30 MB |
| **`HybridNest-v1.1-universal.apk`** | Universal | **All supported devices**. Contains binaries for both 32-bit and 64-bit architectures. | ~79 MB |

#### Integrity Verification (SHA-256 Checksums)

Download `SHA256SUMS.txt` alongside the APKs and verify file integrity:

```bash
sha256sum -c SHA256SUMS.txt
```

Checksums:
```text
421b5f4e8eb9d40c57889d7fdd46e2c6687dbadb9e1d9017d9af00dee36d5a71  HybridNest-v1.1-arm64-v8a.apk
4df8712a40babf4cf854d0cfc37d8f43e4ec9a72e9a6b51c425ff73205e82383  HybridNest-v1.1-armeabi-v7a.apk
aa467cff4ad0cd2a08cfffe25913a79621e0c0dc46f72db6c3dc81cfc8f5bb0d  HybridNest-v1.1-universal.apk
```
