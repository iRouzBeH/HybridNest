# Implementation Plan: Fix VPN Connection UI, Notification, IP/Location/Flag Display, Traffic Counters, and Per-App Routing

## User Review Required

> [!WARNING]
> These are high-risk areas affecting notification lifecycle, traffic accounting, connection duration, and per-app routing. All changes below are surgical fixes only.

## Open Questions

None. Requirements are explicit.

## Proposed Changes

### Issue 1: Notification Raw Text Fix (✅ Already Fixed)
- Location: `service/HybridVpnService.kt`, `startNotificationLoop`
- Current state: `val title = if (isDetecting) "🌐 ${netInfo.location}"...`
- Status: ✅ Correctly uses `"${netInfo.location}"` with a globe emoji. This is correct.

### Issue 2: Per-App Routing in Allow Mode (✅ Already Corrected)
- Location: `service/HybridVpnService.kt`, `applyPerAppRouting`
- Current state: `addAllow(builder, packageName)` is called unconditionally for both OpenConnect and V2Ray in Allow mode.
- Status: ✅ The VPN app itself is correctly added to the allow list. The guard `if (pkg == packageName) return@forEach` has been removed. Both OpenConnect and V2Ray will now tunnel the VPN app itself so it can resolve IP/location/flag over the tunnel.

### Issue 3: Traffic Counters Resetting on App Reopen (NEEDS FIX)
- Location: `service/VpnStateManager.kt`, `stopEngine()` and `DashboardViewModel.kt` `connectionState.collect`
- Current problem: `DashboardViewModel` has a redundant `if (state.isTerminal) { VpnStateManager.stopEngine(getApplication()) }` block that fires when the ViewModel is recreated. The `stopEngine()` function increments `speedGeneration`, cancels `speedJob`, AND calls `resetUsage()`. This means every time the app is closed and reopened (recreating the ViewModel), the traffic counters get zeroed out even though the VPN is still active.
- Fix: In `DashboardViewModel.kt`, the terminal state branch should **NOT** call `stopEngine()` when the actual VPN state hasn't changed (i.e., the ViewModel just got recreated and the service is still running). We need to track whether the ViewModel's lifecycle state differs from the actual VPN state.
- The simplest robust fix: Remove the redundant `stopEngine` call in the terminal branch of the ViewModel's state collector. The `startEngine` guard already prevents double-starts, but we should also not call `stopEngine` when the state hasn't actually transitioned. The key insight: `VpnStateManager.stopEngine()` should only be called on actual state transitions to DISCONNECTING/CANCELING, not on every ViewModel re-collection of an already-terminal state.

### Issue 4: Connection Duration Pausing on Reopen (PARTIALLY FIXED)
- Location: `feature/dashboard/DashboardViewModel.kt`, `connectionState.collect` + `initPersist`/`updateDetailedState`
- Current state: The ViewModel now restores `_uptime` based on `VpnStateManager.connectionStartTime` immediately on `CONNECTED`. The `applyCommonStateSideEffects` clears `connectionStartTime` when state enters `CONNECTING` (setting it to 0), but does NOT clear it when state is already `CONNECTED` from a previous session.
- Remaining problem: The `DashboardViewModel`'s `connectionState.collect` block calls `stopUptime()` then immediately restores `_uptime` on `CONNECTED`. This should be fine. But the `applyCommonStateSideEffects` for `CONNECTING` sets `connectionStartTime = 0L` and persists it. If the ViewModel is recreated while still connected, the init block in `init` doesn't send a CONNECTING state, so this shouldn't be the issue.

## Verification Plan
- Build the app
- Manually verify: Per-App Allow mode routes the VPN app + selected apps correctly
- Manually verify: Traffic counters don't reset on app reopen while connected
- Manually verify: Notification shows "🌐 Detecting..." then country name without "The"
- Manually verify: Connection duration continues counting when app is closed/reopened mid-connection