package dev.rouzbeh.hybridvpn.core.routing

/**
 * Fail-safe guard for OpenVPN per-app routing.
 *
 * Bytecode-verified dependency blocker (2026-10-07 audit of the RESOLVED artifacts
 * in the Gradle cache):
 *  - `io.github.tim06:vpnprotocols-openvpn:3.0.4` →
 *    `com.tim.openvpn.service.OpenVPNService` (class is `final`):
 *    `startTun()` contains ZERO calls to `VpnService.Builder.addAllowedApplication`
 *    or `addDisallowedApplication` (only addAddress/addDnsServer/setMtu/addRoute/
 *    excludeRoute/setUnderlyingNetworks/setMetered/setSession/establish).
 *    A full `javap -c` scan of every `com.tim.openvpn` class finds no per-app
 *    Builder call anywhere.
 *  - `io.github.tim06:vpnprotocols-base:3.0.4` →
 *    `BaseVpnService.warmUpApplicationsFilter()` DOES evaluate the applications
 *    filter and caches it (`cachedApplicationsFilter` /
 *    `cachedApplicationRulesSnapshot`), but no OpenVPN code path ever consumes the
 *    cached rules when building its TUN. The filter is warmed and silently dropped.
 *  - `OpenVPNService` being `final` rules out a subclassing workaround in this repo.
 *
 * Consequence: with this dependency, ANY per-app setting (allow or bypass) produces
 * an UNRESTRICTED global tunnel. Per instructions, the connection is refused with an
 * explicit explanation instead of silently establishing it. Global mode is unaffected.
 *
 * NOTE: this is a dependency limitation, not an inherent platform limitation. A
 * minimal upstream patch would pass `getCachedApplicationRulesSnapshot()` into
 * `OpenVPNService.startTun()` and call the corresponding Builder method for each
 * Allowed/Disallowed entry. Documented for the upstream report; NOT implemented here.
 */
object OpenVpnPolicyGuard {

    /**
     * Returns a user-facing refusal reason when [policy] cannot be enforced by the
     * bundled OpenVPN dependency, or null when the connection may proceed.
     *
     * Only per-app ENABLED policies are refused. A disabled policy means global
     * routing, which OpenVPN does enforce (it always routes everything).
     */
    fun refusalReason(policy: RoutingPolicy): String? {
        if (!policy.enabled) return null
        return buildString {
            append("OpenVPN cannot enforce the selected per-app routing in this build. ")
            append("The bundled OpenVPN library (vpnprotocols 3.0.4) ignores application ")
            append("filters when it creates its tunnel, so connecting now would route ALL ")
            append("apps through the VPN regardless of this setting. ")
            append("Choose Global routing for OpenVPN, or use OpenConnect for per-app routing.")
        }
    }
}
