# Per-App VPN Diagnosis — HybridNest (dev.rouzbeh.hybridvpn)

_Generated 2026-10-14 — static analysis, no device run._

## 1. Every addAllowed / addDisallowed callsite

| File | Line | Call | Context |
|------|------|------|---------|
| `app/src/main/java/dev/rouzbeh/hybridvpn/service/HybridVpnService.kt` | ~792 | `builder.addAllowedApplication(pkg)` inside `addAllow()` | Called only from `Allow` branch via `decision.allowedPackages.forEach` |
| `app/src/main/java/dev/rouzbeh/hybridvpn/service/HybridVpnService.kt` | ~807 | `builder.addDisallowedApplication(pkg)` inside `addDisallow()` | Called from `Global` (V2Ray only) and `Bypass` branches, plus V2Ray self-exclude |
| `app/src/main/java/dev/rouzbeh/hybridvpn/core/routing/OpenVpnPolicyGuard.kt` | 10-11 | comment only | states OpenVPN 3.0.4 has zero Builder per-app calls |
| `app/src/main/java/dev/rouzbeh/hybridvpn/HybridVpnApplication.kt` | 75,78 | comment only | describes BaseVpnService warm-up but not used |

No other file calls these APIs (verified with `grep -rn addAllowedApplication|addDisallowedApplication app/src`).

## 2. Exact Builder call order per protocol/mode

### OpenConnect — `establishTunNative(ip, dns)` (lines 552-618)
```
Builder()
  .setSession("HybridNestVPN")
  .setConfigureIntent(pendingConfigure)
  .setMtu(1400)
  .addAddress(ip, 32)
  .addRoute("0.0.0.0", 0)                 // always, before per-app
  // DNS: dns.split(",") -> each addDnsServer(); fallback 8.8.8.8 if empty
  .setMetered(false)  [API Q+]
  .setBlocking(false) [API LMR1+]
  -> applyPerAppRouting(builder)           // injects allow/disallow
  -> builder.establish()
  -> dup/establish FD handling
```

### V2Ray — `establishTunNative(ip, dns, excludedServerIp)` (lines 620-711)
```
Builder()
  .setSession("HybridNestVPN")
  .setConfigureIntent(pendingConfigure)
  .setMtu(1400)
  .addAddress("172.19.0.1", 32)            // fixed
  // Route: conditional
  if (isAllowMode)  builder.addRoute("0.0.0.0", 0)
  else              builder.addIpv4DefaultRouteExcept(excludedServerIp)
                    // -> addRoute 0.0.0.0/0 + excludeRoute /32 (Tiramisu) or 32 complement routes (<Tiramisu)
  // DNS: split(",") -> addDnsServer each; fallback 8.8.8.8
  .setMetered(false)
  .setBlocking(false)
  -> applyPerAppRouting(builder)
  -> establish()
```

### applyPerAppRouting (lines 713-782)

```
policy = sessionRoutingPolicy ?: snapshotNow
decision = RoutingPolicyResolver.resolve(policy, installedPackages)

Global:   if (V2Ray) addDisallow(packageName)   // OpenConnect: no-op
Allow:    for pkg in allowedPackages: addAllow(pkg)
          // self NEVER added (current)
          if (added==0) throw RoutingRefusedException
Bypass:   for pkg in disallowedPackages: addDisallow(pkg)
          if (V2Ray) addDisallow(packageName)
Refused:  throw RoutingRefusedException
```

**Allow mode Builder final state (Chrome selected):**
- OpenConnect: `addAllowedApplication("com.android.chrome")` only, DNS 0.0.0.0/0, own UID excluded
- V2Ray: same allowed app, DNS 0.0.0.0/0, own UID excluded (full route)

**Bypass mode Builder final state (Brave excluded):**
- OpenConnect: `addDisallowedApplication("com.brave.browser")`
- V2Ray: `addDisallowedApplication("com.brave.browser")` + `addDisallowedApplication("dev.rouzbeh.hybridvpn")` + `excludeRoute(serverIp/32)`

## 3. Can both families be called on same Builder?

**No — current code structurally prevents it.** `RoutingDecision` sealed class has mutually exclusive cases (Global/Allow/Bypass/Refused). `applyPerAppRouting` switches on decision and each branch calls only one family (`addAllow` or `addDisallow`). No branch calls both. Additionally `addAllow`/`addDisallow` catch `UnsupportedOperationException` and rethrow, so a mixed call would crash loudly rather than silently corrupt.

Historical risk: a prior agent did `addAllow(packageName)` for self inside Allow branch while also adding user packages — still single family, so not mixed. But V2Ray Allow with self-added + user packages would be allowed-only (still not mixed) but caused transport loop.

## 4. Package list storage/loading & exception handling

- **Store:** `SettingsRepository` uses `SharedPreferences "hybridvpn_settings"` keys `per_app_enabled`, `bypass_mode`, `selected_apps` (StringSet), `routing_revision`. Mutations via `*AndBump` do `bumpRevision + putX + apply()` atomically in one editor.
- **Load:** `snapshotRoutingPolicy()` reads `isPerAppEnabled()`, `isBypassMode()`, `getSelectedApps()`, `getRoutingRevision()` synchronously.
- **Pruning:** `installedPackagesForRouting()` enumerates `packageManager.getInstalledApplications(0)` -> Set<String>. On exception returns `emptySet()` (fail-closed). `RoutingPolicyResolver` intersects `policy.packages ∩ installed`, prunes rest. Allow with zero usable -> `Refused` (throws, never widens to global). Bypass with zero usable -> `Global`.
- **Builder exception:** `addAllow` returns false on `NameNotFoundException` (app vanished between selection and establish), true otherwise; `UnsupportedOperationException` propagated. `addDisallow` logs and swallows `NameNotFoundException`, propagates `UnsupportedOperationException`. No silent fallback to global; `userPackagesAdded==0` throws `RoutingRefusedException`.

## 5. Socket protection (anti-loop)

- **OpenConnect:** `native-lib.cpp: on_protect_socket()` obtains `HybridVpnService.protectSocket(fd)` via JNI `GetMethodID("protectSocket","(I)V")` which calls `VpnService.protect(fd)`. Registered via `openconnect_set_protect_socket_handler` at session start. Every transport socket (CSTP, DTLS, cancel pipe) is protected. This is the only mechanism that lets the VPN app stay INSIDE the tunnel while its VPN transport bypasses it.
- **V2Ray:** No `protect()` call. Relies entirely on UID routing: the VPN app's UID is excluded from the TUN (via `addDisallowedApplication(packageName)` in Global/Bypass, and via not being in allowed set in Allow). Therefore every socket Xray opens (proxy transport, dns-out direct, etc.) automatically bypasses the TUN. If the app were incorrectly placed INSIDE the TUN in Allow mode, Xray transport would be captured -> loop -> no connectivity.

## 6. Ranked root-cause hypotheses

### H1 — Allow-mode “Chrome offline” is caused by missing tunnel for HybridNest itself? No — but V2Ray loop when self was included (P0 for V2Ray, fix already applied)
- **Evidence:** diff shows prior code did `addAllow(packageName)` in Allow branch. For V2Ray that captures Xray transport into TUN (no protect), classic loop. Current code removed that line and added full default route for V2Ray Allow, which fixes loop. `HybridVpnService.kt:750` comment now says NEVER add self for V2Ray.
- **Test:** V2Ray Allow with self-added -> immediate total blackhole (all TUN traffic loops). Removing self fixes V2Ray but leaves OpenConnect IP detection broken.
- **Likelihood:** Very high for historic V2Ray offline; current code already mitigates by excluding self + full route.

### H2 — OpenConnect Allow-mode IP/flag shows ISP because HybridNest is excluded from tunnel, so `ConnectivityManager` hides VPN Network and `fetchCurrentNetworkInfo` falls back to ISP (P0 for OpenConnect, current code shows “VPN exit unavailable”)
- **Evidence:** `DashboardViewModel.kt:522-527` looks for `TRANSPORT_VPN` network; when HybridNest is excluded (Allow without self), `allNetworks` does not contain VPN `Network` (enforced by OS). Current code then `throw IOException("No verified OpenConnect tunnel path")` -> “VPN exit unavailable”. Previous code did `openConnection(NO_PROXY)` fallback -> showed ISP IP (bug report #2). `HybridVpnService.kt:741-755` explicitly never adds self in Allow.
- **Fix per spec:** In Allow mode, OpenConnect MUST add `packageName` via `addAllowedApplication`, and rely on `protect()` for loop avoidance (already correct for Bypass/Global). Request doc explicitly says this.
- **Likelihood:** Certain. This is the exact bug #2.

### H3 — Bypass regression after last change: V2Ray route change inadvertently broke non-Allow paths? Unlikely — Bypass route unchanged, but last agent may have introduced mixed allow/disallow or stale policy file
- **Evidence:** `HybridVpnService.kt:653` conditional route `if (isAllowMode)` else `excludeRoute`. Bypass path still hits `excludeRoute`. No code path calls both families. `RoutingPolicyFile.write()` is called synchronously in `establishSession` before any TUN, so `:openvpn` process gets correct snapshot. Bypass should be intact in current code.
- **Likelihood:** Medium — user reports Bypass now broken, but current code's Bypass logic matches spec (disallow only). May be misreport of Allow still broken.

### H4 — DNS unreachable through tunnel for allowed apps (requires verify)
- **Evidence:** Both tunnels use `addRoute 0.0.0.0/0` (or complement routes) and `addDnsServer`. If DNS server is private (OpenConnect) and not reachable via ISP, and if `netd` were excluded, DNS would fail. However Android routes DNS for allowed apps via VPN's DNS regardless of `netd` UID, so H4 is unlikely. Diagnostic doc's “netd” hypothesis is probably wrong per Android docs.
- **Next check:** PerAppDebug logs of final DNS list and `ip_info->dns` comma join.

### H5 — IPv6 leakage / missing ::/0 route causes Chrome to try IPv6 and appear offline on IPv6-enabled networks
- **Evidence:** Neither tunnel adds `::/0` or blocks IPv6. If device has IPv6, Chrome may prefer AAAA, bypass VPN (since no IPv6 route), and fail if ISP blocks it. Not observed in Bypass vs Allow difference though.
- **Likelihood:** Low for selective offline, but worth logging.

### H6 — Empty/stale allowed list due to `installedPackagesForRouting()` fail-closed returning emptySet -> Refused -> connection aborts instead of offline
- **Evidence:** `installedPackagesForRouting()` catches Throwable and returns emptySet, causing `Refused`. That would surface as ERROR, not silent offline. User reports offline, not error.
- **Likelihood:** Low.

## Next step per instructions
- Add `PerAppDebug` logs at VPN start printing mode, revision, allowed/disallowed packages, pruned, routes, DNS.
- Restore Bypass (verify disallow-only, V2Ray self-exclude) — already correct.
- Fix Allow: for OpenConnect, add self to allowed set; for V2Ray keep self excluded + full route.
- Keep notification fix intact.
