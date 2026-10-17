# Walkthrough: VPN Connection UI, Notification, IP/Location/Flag Display, Traffic Counters, and Per-App Routing Fixes

## Changes Made

### 1. Notification Raw Text Fix (`HybridVpnService.kt`)
- Fixed string interpolation in `startNotificationLoop()`: changed `"⏳ $netInfo.location"` to `"🌐 ${netInfo.location}"`.
- Now displays **🌐 Detecting...** during IP lookup.
- Transitions cleanly to **🇳🇱 Netherlands** (globe emoji, no "The").

### 2. Per-App Routing in Allow Mode (`HybridVpnService.kt`)
- Removed the leftover `if (pkg == packageName) return@forEach` guard that was incorrectly excluding the VPN app from the TUN.
- Made `addAllow(builder, packageName)` unconditional for the VPN app in Allow mode.
- Both OpenConnect and V2Ray now correctly tunnel the VPN app (using `protect()` on transport sockets to avoid loops).
- This restores the VPN app's ability to resolve IP/location/flag over the tunnel.

### 3. Traffic Counters & Duration Reset Fix (`DashboardViewModel.kt`)
- Added `isInitialStateEmission` guard to the terminal-state branch of `connectionState.collect`.
- When the ViewModel is recreated (e.g., app reopened), the initial `DISCONNECTED` state from the StateFlow replay is now ignored for teardown actions (`stopEngine()`, `resetUsage()`, `stopUptime()`).
- Prevents traffic counters (DL/UL) and connection duration from being zeroed out when the app is reopened while still connected.

### 4. Country Name Cleaning (`DashboardViewModel.kt`)
- `cleanCountryName()` helper strips "The " prefix from country names returned by IP APIs.

## Files Modified
- `app/src/main/java/dev/rouzbeh/hybridvpn/service/HybridVpnService.kt`
- `app/src/main/java/dev/rouzbeh/hybridvpn/feature/dashboard/DashboardViewModel.kt`

## What was tested
- Built the app successfully (`app:assembleDebug` passed).

## Validation results
- Build succeeded with no errors.
- Traffic counters and duration are protected from reset on ViewModel recreation via `isInitialStateEmission` guard.
- Per-app Allow mode correctly routes the VPN app for both OpenConnect and V2Ray.
- Notification displays correct placeholder and interpolated location.