# Per-app routing contract

This is the contract implemented by `HybridVpnService.applyPerAppRouting`.

## Policy semantics
- Disabled policy means global routing; no user package is selected.
- ALLOW routes only selected, installed packages through the VPN.
- BYPASS routes every app except selected, installed packages.
- Uninstalled selections are pruned and never widen the policy.
- Empty installed ALLOW refuses connection; it never becomes global.
- The policy is snapshotted once per connection session.

## Builder package calls

| Method | Global | ALLOW | BYPASS |
|---|---|---|---|
| OpenConnect | none | selected + HybridNest | selected disallowed |
| V2Ray | HybridNest disallowed | selected only | selected + HybridNest disallowed |
| DNS-only | HybridNest disallowed | selected only | selected + HybridNest disallowed |

Only one `VpnService.Builder` package API family is ever used per decision.
OpenConnect protects transport sockets and keeps the app inside the tunnel for
its exit-IP check; V2Ray and DNS keep their own upstream sockets outside it.

## Routes and DNS
- OpenConnect uses an IPv4 default route and server DNS (or `8.8.8.8` fallback).
- V2Ray ALLOW uses `0.0.0.0/0`; Global/BYPASS exclude the pinned proxy endpoint.
- V2Ray advertises `9.9.9.9`; its session DNS module sends remote DNS via proxy.
- DNS-only routes only `10.10.10.53/32` and `fd00:646e:7373::53/128` and advertises both.

## Xray and safety invariants
- Real-session port-53 `udp,tcp` traffic from `tun-in` goes to `dns-out` only.
- `dns-module` and `domestic-dns` rules remain transport-specific session rules.
- Ping uses its separate flat-DNS config with no session DNS rules or mux.
- Never mix allow/disallow APIs, silently widen ALLOW, or mutate a live snapshot.
