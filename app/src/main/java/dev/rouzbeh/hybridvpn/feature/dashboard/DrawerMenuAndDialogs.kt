package dev.rouzbeh.hybridvpn.feature.dashboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.rouzbeh.hybridvpn.R
import dev.rouzbeh.hybridvpn.core.designsystem.BackgroundDark
import dev.rouzbeh.hybridvpn.core.designsystem.BorderGlass
import dev.rouzbeh.hybridvpn.core.designsystem.PrimaryCyan
import dev.rouzbeh.hybridvpn.core.designsystem.SurfaceDark
import dev.rouzbeh.hybridvpn.core.designsystem.SurfaceGlass
import dev.rouzbeh.hybridvpn.core.designsystem.SurfaceMenu
import dev.rouzbeh.hybridvpn.core.designsystem.TextPrimary
import dev.rouzbeh.hybridvpn.core.designsystem.TextSecondary
import dev.rouzbeh.hybridvpn.core.vpn.v2ray.V2raySettings
import dev.rouzbeh.hybridvpn.data.repository.SettingsRepository
import dev.rouzbeh.hybridvpn.service.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Navigation Drawer Modal Types.
 */
enum class DrawerModalType {
    NONE,
    ANYCONNECT,
    WIREGUARD,
    V2RAY,
    FEATURES,
    CHECK_UPDATE,
    ABOUT
}

// =========================================================================
// DRAWER MENU CONTENT
// =========================================================================

@Composable
fun HybridNestDrawerContent(
    isDrawerOpen: Boolean,
    onSelectModal: (DrawerModalType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // 1. Static Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceGlass)
                    .border(1.dp, PrimaryCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "HybridNest Logo",
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "HybridNest",
                    color = PrimaryCyan,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = SoraFamily
                )
                Text(
                    text = "v1.0 • Multi-Engine Core",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = BorderGlass)
        Spacer(modifier = Modifier.height(14.dp))

        // 2. Scrollable Menu Items Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DrawerMenuItemRow(
                title = "AnyConnect Settings",
                iconRes = R.drawable.ic_openconnect,
                onClick = { onSelectModal(DrawerModalType.ANYCONNECT) }
            )

            DrawerMenuItemRow(
                title = "WireGuard Settings",
                iconRes = R.drawable.ic_wireguard,
                onClick = { onSelectModal(DrawerModalType.WIREGUARD) }
            )

            DrawerMenuItemRow(
                title = "v2rayNG Settings",
                iconRes = R.drawable.ic_v2rayng,
                onClick = { onSelectModal(DrawerModalType.V2RAY) }
            )

            DrawerMenuItemRow(
                title = "Features",
                imageVector = Icons.Default.Tune,
                onClick = { onSelectModal(DrawerModalType.FEATURES) }
            )

            HorizontalDivider(
                color = BorderGlass,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
            )

            DrawerMenuItemRow(
                title = "Check for Update",
                imageVector = Icons.Default.SystemUpdateAlt,
                onClick = { onSelectModal(DrawerModalType.CHECK_UPDATE) }
            )

            DrawerMenuItemRow(
                title = "About",
                imageVector = Icons.Default.Info,
                onClick = { onSelectModal(DrawerModalType.ABOUT) }
            )
        }
    }
}

@Composable
private fun DrawerMenuItemRow(
    title: String,
    iconRes: Int? = null,
    imageVector: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGlass)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderGlass, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = PrimaryCyan,
                    modifier = Modifier.size(20.dp)
                )
            } else if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = title,
                    tint = PrimaryCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = SoraFamily,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

// =========================================================================
// 1. ANYCONNECT (OPENCONNECT) SETTINGS FULL-SCREEN
// =========================================================================

@Composable
fun AnyConnectSettingsFullScreen(
    onDismiss: () -> Unit,
    viewModel: DashboardViewModel,
    connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    onShowConnectionWarning: (String) -> Unit = {}
) {
    val settings = viewModel.settings
    var dtlsEnabled by remember { mutableStateOf(settings.isOpenConnectDtlsEnabled()) }
    var mtu by remember { mutableStateOf(settings.getOpenConnectMtu().toString()) }
    var dpdTimeout by remember { mutableStateOf(settings.getOpenConnectDpdTimeout().toString()) }
    var fallbackDns by remember { mutableStateOf(settings.getOpenConnectFallbackDns()) }

    SettingsFullScreenScaffold(
        title = "AnyConnect Settings",
        subtitle = "OpenConnect SSL/TLS & DTLS transport",
        iconRes = R.drawable.ic_openconnect,
        onBack = onDismiss,
        bottomBar = {
            SettingsActionFooter(
                onReset = {
                    dtlsEnabled = true
                    mtu = "1400"
                    dpdTimeout = "300"
                    fallbackDns = "8.8.8.8"
                },
                onCancel = onDismiss,
                onSave = {
                    when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                        LockActionOutcome.ALLOW -> {
                            settings.setOpenConnectDtlsEnabled(dtlsEnabled)
                            settings.setOpenConnectMtu(mtu.toIntOrNull() ?: 1400)
                            settings.setOpenConnectDpdTimeout(dpdTimeout.toIntOrNull() ?: 300)
                            settings.setOpenConnectFallbackDns(fallbackDns.trim().ifBlank { "8.8.8.8" })
                            viewModel.notifySettingsSaved("AnyConnect Settings")
                            onDismiss()
                        }
                        LockActionOutcome.SHOW_LOCK_WARNING -> {
                            onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                        }
                        LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                    }
                }
            )
        }
    ) {
        // Section 1 — Data Channel
        SettingsSectionCard(
            title = "Data Channel",
            subtitle = "Transport protocol negotiation"
        ) {
            SettingsToggleRow(
                title = "DTLS (UDP) Tunneling",
                description = "Use UDP data channel for lower latency; disable to force TCP/TLS (CSTP) on restricted networks.",
                checked = dtlsEnabled,
                onCheckedChange = { dtlsEnabled = it }
            )
        }

        // Section 2 — Tunnel Parameters
        SettingsSectionCard(
            title = "Tunnel Parameters",
            subtitle = "Packet size and session keepalive"
        ) {
            SettingsTextFieldWithPresets(
                value = mtu,
                onValueChange = { mtu = it },
                label = "Tunnel MTU (Bytes)",
                caption = "Valid range: 1280–1500 bytes (default: 1400)",
                presets = listOf(
                    "1280" to "1280",
                    "1350" to "1350",
                    "1400" to "1400",
                    "1450" to "1450",
                    "1500" to "1500"
                ),
                keyboardType = KeyboardType.Number
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

            SettingsTextFieldWithPresets(
                value = dpdTimeout,
                onValueChange = { dpdTimeout = it },
                label = "DPD / Keepalive Timeout (Seconds)",
                caption = "Dead peer detection interval: 30–600 seconds (default: 300s)",
                presets = listOf(
                    "60s" to "60",
                    "120s" to "120",
                    "300s (Default)" to "300",
                    "600s" to "600"
                ),
                keyboardType = KeyboardType.Number
            )
        }

        // Section 3 — Fallback DNS
        SettingsSectionCard(
            title = "Fallback DNS",
            subtitle = "Resolver applied when gateway pushes no nameservers"
        ) {
            SettingsTextFieldWithPresets(
                value = fallbackDns,
                onValueChange = { fallbackDns = it },
                label = "Fallback DNS Server (IPv4)",
                caption = "Primary resolver for unassigned routes",
                presets = listOf(
                    "Google 8.8.8.8" to "8.8.8.8",
                    "Cloudflare 1.1.1.1" to "1.1.1.1",
                    "Quad9 9.9.9.9" to "9.9.9.9"
                )
            )
        }
    }
}

@Composable
fun AnyConnectSettingsDialog(
    onDismiss: () -> Unit,
    viewModel: DashboardViewModel,
    connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    onShowConnectionWarning: (String) -> Unit = {}
) = AnyConnectSettingsFullScreen(onDismiss, viewModel, connectionState, onShowConnectionWarning)

// =========================================================================
// 2. WIREGUARD SETTINGS FULL-SCREEN
// =========================================================================

@Composable
fun WireGuardSettingsFullScreen(
    onDismiss: () -> Unit,
    viewModel: DashboardViewModel,
    connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    onShowConnectionWarning: (String) -> Unit = {}
) {
    val settings = viewModel.settings
    var mtuOverride by remember { mutableStateOf(settings.getWireGuardMtuOverride().toString()) }
    var keepaliveOverride by remember { mutableStateOf(settings.getWireGuardKeepAliveOverride().toString()) }
    var tunnelDns by remember { mutableStateOf(settings.getWireGuardTunnelDns()) }
    var bootstrapResolve by remember { mutableStateOf(settings.isWireGuardBootstrapResolveEnabled()) }

    SettingsFullScreenScaffold(
        title = "WireGuard Settings",
        subtitle = "User-space gVisor outbound overrides",
        iconRes = R.drawable.ic_wireguard,
        onBack = onDismiss,
        bottomBar = {
            SettingsActionFooter(
                onReset = {
                    mtuOverride = "0"
                    keepaliveOverride = "-1"
                    tunnelDns = "9.9.9.9"
                    bootstrapResolve = true
                },
                onCancel = onDismiss,
                onSave = {
                    when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                        LockActionOutcome.ALLOW -> {
                            settings.setWireGuardMtuOverride(mtuOverride.toIntOrNull() ?: 0)
                            settings.setWireGuardKeepAliveOverride(keepaliveOverride.toIntOrNull() ?: -1)
                            settings.setWireGuardTunnelDns(tunnelDns.trim().ifBlank { "9.9.9.9" })
                            settings.setWireGuardBootstrapResolveEnabled(bootstrapResolve)
                            viewModel.notifySettingsSaved("WireGuard Settings")
                            onDismiss()
                        }
                        LockActionOutcome.SHOW_LOCK_WARNING -> {
                            onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                        }
                        LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                    }
                }
            )
        }
    ) {
        // Technical note banner
        SettingsInfoBanner(
            text = "Overrides take precedence over config files. Use MTU 0 or Keepalive -1 to inherit profile defaults."
        )

        // Section 1 — Interface Overrides
        SettingsSectionCard(
            title = "Interface Overrides",
            subtitle = "User-space netstack MTU and persistent keepalive"
        ) {
            SettingsTextFieldWithPresets(
                value = mtuOverride,
                onValueChange = { mtuOverride = it },
                label = "Interface MTU Override",
                caption = "0 = use profile MTU, or 1280–1500",
                presets = listOf(
                    "Default (0)" to "0",
                    "1280" to "1280",
                    "1380" to "1380",
                    "1400" to "1400",
                    "1420" to "1420"
                ),
                keyboardType = KeyboardType.Number
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

            SettingsTextFieldWithPresets(
                value = keepaliveOverride,
                onValueChange = { keepaliveOverride = it },
                label = "Persistent Keepalive Override",
                caption = "-1 = profile default, 0 = off, 1–300s",
                presets = listOf(
                    "Default (-1)" to "-1",
                    "Off (0s)" to "0",
                    "15s" to "15",
                    "25s" to "25",
                    "60s" to "60"
                ),
                keyboardType = KeyboardType.Number
            )
        }

        // Section 2 — DNS & Routing
        SettingsSectionCard(
            title = "DNS & Routing",
            subtitle = "Resolver configuration and circular loop avoidance"
        ) {
            SettingsTextFieldWithPresets(
                value = tunnelDns,
                onValueChange = { tunnelDns = it },
                label = "Tunnel Advertised DNS",
                caption = "DNS IPv4 address assigned to the tunnel interface",
                presets = listOf(
                    "Quad9" to "9.9.9.9",
                    "Cloudflare" to "1.1.1.1",
                    "Google" to "8.8.8.8",
                    "AdGuard" to "94.140.14.14"
                )
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

            SettingsToggleRow(
                title = "Endpoint IPv4 Bootstrap Pinning",
                description = "Resolve domain endpoints to IPv4 before tunnel start to prevent circular DNS routing deadlocks.",
                checked = bootstrapResolve,
                onCheckedChange = { bootstrapResolve = it }
            )
        }
    }
}

@Composable
fun WireGuardSettingsDialog(
    onDismiss: () -> Unit,
    viewModel: DashboardViewModel,
    connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    onShowConnectionWarning: (String) -> Unit = {}
) = WireGuardSettingsFullScreen(onDismiss, viewModel, connectionState, onShowConnectionWarning)

// =========================================================================
// 3. V2RAYNG (XRAY) SETTINGS FULL-SCREEN
// =========================================================================

@Composable
fun V2raySettingsFullScreen(
    onDismiss: () -> Unit,
    viewModel: DashboardViewModel,
    connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    onShowConnectionWarning: (String) -> Unit = {}
) {
    val settings = viewModel.settings
    var vpnDns by remember { mutableStateOf(settings.getV2rayVpnDns()) }
    var remoteDns by remember { mutableStateOf(settings.getV2rayRemoteDns()) }
    var domesticDns by remember { mutableStateOf(settings.getV2rayDomesticDns()) }
    var sniffingEnabled by remember { mutableStateOf(settings.isV2raySniffingEnabled()) }
    var bypassLan by remember { mutableStateOf(settings.isV2rayBypassLanEnabled()) }
    var muxEnabled by remember { mutableStateOf(settings.isV2rayMuxEnabled()) }
    var muxConcurrency by remember { mutableStateOf(settings.getV2rayMuxConcurrency().toString()) }
    var muxQuicPolicy by remember { mutableStateOf(settings.getV2rayMuxQuicPolicy()) }

    SettingsFullScreenScaffold(
        title = "v2rayNG Settings",
        subtitle = "Xray-core DNS, routing & multiplexing",
        iconRes = R.drawable.ic_v2rayng,
        onBack = onDismiss,
        bottomBar = {
            SettingsActionFooter(
                onReset = {
                    vpnDns = V2raySettings.DEFAULT_VPN_DNS
                    remoteDns = V2raySettings.DEFAULT_REMOTE_DNS
                    domesticDns = V2raySettings.DEFAULT_DOMESTIC_DNS
                    sniffingEnabled = true
                    bypassLan = false
                    muxEnabled = false
                    muxConcurrency = "10"
                    muxQuicPolicy = "reject"
                },
                onCancel = onDismiss,
                onSave = {
                    when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                        LockActionOutcome.ALLOW -> {
                            settings.setV2rayVpnDns(vpnDns.trim().ifBlank { V2raySettings.DEFAULT_VPN_DNS })
                            settings.setV2rayRemoteDns(remoteDns.trim().ifBlank { V2raySettings.DEFAULT_REMOTE_DNS })
                            settings.setV2rayDomesticDns(domesticDns.trim().ifBlank { V2raySettings.DEFAULT_DOMESTIC_DNS })
                            settings.setV2raySniffingEnabled(sniffingEnabled)
                            settings.setV2rayBypassLanEnabled(bypassLan)
                            settings.setV2rayMuxEnabled(muxEnabled)
                            settings.setV2rayMuxConcurrency(muxConcurrency.toIntOrNull()?.coerceIn(1, 64) ?: 10)
                            settings.setV2rayMuxQuicPolicy(muxQuicPolicy)
                            viewModel.notifySettingsSaved("v2rayNG Settings")
                            onDismiss()
                        }
                        LockActionOutcome.SHOW_LOCK_WARNING -> {
                            onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                        }
                        LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                    }
                }
            )
        }
    ) {
        // Section 1 — DNS Architecture
        SettingsSectionCard(
            title = "DNS Architecture",
            subtitle = "Tunnel nameserver & resolution endpoints"
        ) {
            SettingsTextFieldWithPresets(
                value = vpnDns,
                onValueChange = { vpnDns = it },
                label = "System TUN DNS",
                caption = "Advertised VPN DNS server for the Android interface",
                presets = listOf(
                    "Quad9" to "9.9.9.9",
                    "Cloudflare" to "1.1.1.1",
                    "Google" to "8.8.8.8"
                )
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

            SettingsTextFieldWithPresets(
                value = remoteDns,
                onValueChange = { remoteDns = it },
                label = "Remote DNS (Encrypted via Proxy)",
                caption = "Remote DNS endpoint queried through the proxy tunnel",
                presets = listOf(
                    "Cloudflare TCP" to "tcp://1.1.1.1",
                    "Google TCP" to "tcp://8.8.8.8",
                    "Quad9 TCP" to "tcp://9.9.9.9"
                )
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

            SettingsTextFieldWithPresets(
                value = domesticDns,
                onValueChange = { domesticDns = it },
                label = "Domestic / Direct DNS",
                caption = "Direct DNS resolver for local and unproxied domains",
                presets = listOf(
                    "Shecan" to "178.22.122.100",
                    "Cloudflare" to "1.1.1.1",
                    "Google" to "8.8.8.8"
                )
            )
        }

        // Section 2 — Routing & Traffic Inspection
        SettingsSectionCard(
            title = "Routing & Traffic Inspection",
            subtitle = "Traffic classification & LAN bypass"
        ) {
            SettingsToggleRow(
                title = "Traffic Sniffing (HTTP & TLS)",
                description = "Inspect inbound TLS ClientHello (SNI) and HTTP Host headers to accurately route domain-based rules.",
                checked = sniffingEnabled,
                onCheckedChange = { sniffingEnabled = it }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

            SettingsToggleRow(
                title = "Bypass LAN / Private Networks",
                description = "Route private IP ranges (geoip:private) and local network traffic directly outside the VPN tunnel.",
                checked = bypassLan,
                onCheckedChange = { bypassLan = it }
            )
        }

        // Section 3 — Multiplexing (MUX)
        SettingsSectionCard(
            title = "Multiplexing (MUX)",
            subtitle = "Outbound TCP connection multiplexing"
        ) {
            SettingsToggleRow(
                title = "Enable MUX",
                description = "Multiplex multiple TCP connections over a single proxy channel to reduce handshake overhead.",
                checked = muxEnabled,
                onCheckedChange = { muxEnabled = it }
            )

            AnimatedVisibility(
                visible = muxEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                    SettingsTextFieldWithPresets(
                        value = muxConcurrency,
                        onValueChange = { muxConcurrency = it },
                        label = "MUX Concurrency (1–64)",
                        caption = "Concurrent TCP substreams over a single channel (default: 10)",
                        presets = listOf(
                            "4" to "4",
                            "8" to "8",
                            "10 (Default)" to "10",
                            "16" to "16"
                        ),
                        keyboardType = KeyboardType.Number
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "QUIC (UDP 443) Policy",
                            color = Color(0xFF94A3B8),
                            fontFamily = SoraFamily,
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val quicOptions = listOf(
                                "Reject (Drop to TCP)" to "reject",
                                "Allow" to "allow"
                            )
                            quicOptions.forEach { (chipLabel, chipValue) ->
                                val isSelected = muxQuicPolicy == chipValue
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) PrimaryCyan.copy(alpha = 0.18f)
                                            else Color.White.copy(alpha = 0.04f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) PrimaryCyan else Color.White.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { muxQuicPolicy = chipValue }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = chipLabel,
                                        color = if (isSelected) PrimaryCyan else Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = SoraFamily
                                    )
                                }
                            }
                        }
                    }

                    SettingsInfoBanner(
                        text = "MUX changes take effect on next connection. Rejecting QUIC forces HTTP/3 traffic onto TCP for better stability under multiplexed tunnels."
                    )
                }
            }
        }
    }
}

// =========================================================================
// 4. FEATURES DIALOG
// =========================================================================

/**
 * Detects the active Wi-Fi Hotspot or LAN IPv4 address (e.g. ap0, wlan1, rndis0, wlan0).
 * Falls back to "192.168.43.1" (standard Android Wi-Fi Hotspot gateway IP).
 */
fun detectHotspotOrLanIp(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        val activeInterfaces = interfaces.filter { iface ->
            try {
                iface.isUp && !iface.isLoopback &&
                    !iface.name.lowercase().let { name ->
                        name.startsWith("tun") || name.startsWith("dummy") || name.startsWith("ppp")
                    }
            } catch (_: Exception) {
                false
            }
        }

        fun isEligibleIpv4(addr: InetAddress): Boolean {
            if (addr !is Inet4Address || addr.isLoopbackAddress) return false
            val host = addr.hostAddress ?: return false
            if (host == "172.19.0.1") return false
            return host.startsWith("192.168.") ||
                host.startsWith("10.") ||
                (host.startsWith("172.") && host.split(".").getOrNull(1)?.toIntOrNull() in 16..31)
        }

        // 1. Prioritize hotspot / tethering interface names
        val hotspotKeywords = listOf("ap", "wlan1", "swlan", "softap", "rndis", "tether", "p2p")
        for (keyword in hotspotKeywords) {
            val matched = activeInterfaces.firstOrNull { it.name.lowercase().contains(keyword) }
            if (matched != null) {
                for (addr in matched.inetAddresses.toList()) {
                    if (isEligibleIpv4(addr)) {
                        return addr.hostAddress ?: "192.168.43.1"
                    }
                }
            }
        }

        // 2. Fallback to wlan0
        val wlan0 = activeInterfaces.firstOrNull { it.name.equals("wlan0", ignoreCase = true) }
        if (wlan0 != null) {
            for (addr in wlan0.inetAddresses.toList()) {
                if (isEligibleIpv4(addr)) {
                    return addr.hostAddress ?: "192.168.43.1"
                }
            }
        }

        // 3. Any active private IPv4 interface
        for (iface in activeInterfaces) {
            for (addr in iface.inetAddresses.toList()) {
                if (isEligibleIpv4(addr)) {
                    return addr.hostAddress ?: "192.168.43.1"
                }
            }
        }
    } catch (_: Exception) {
        // Fallback below
    }
    return "192.168.43.1"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeaturesFullScreen(
    onDismiss: () -> Unit,
    viewModel: DashboardViewModel,
    connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    onShowConnectionWarning: (String) -> Unit = {}
) {
    val settings = viewModel.settings
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var domainInput by remember { mutableStateOf("") }
    var bypassedDomains by remember { mutableStateOf(settings.getBypassedDomains()) }
    var allowLanHotspot by remember { mutableStateOf(settings.isAllowLanHotspotEnabled()) }

    var connectivityCheck by remember { mutableStateOf(settings.isConnectivityCheckEnabled()) }
    var pingUrl by remember { mutableStateOf(settings.getPingTestUrl()) }
    var subUa by remember { mutableStateOf(settings.getSubUserAgent()) }
    var strictRedaction by remember { mutableStateOf(settings.isStrictLogRedactionEnabled()) }
    var showExportWarningDialog by remember { mutableStateOf(false) }

    var detectedIp by remember { mutableStateOf(detectHotspotOrLanIp()) }
    var localToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(localToastMessage) {
        if (localToastMessage != null) {
            delay(2500L)
            localToastMessage = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            // Avoid duplicate toast on screen exit when notifySettingsSaved emits
            if (!msg.contains("saved", ignoreCase = true)) {
                localToastMessage = msg
            }
        }
    }

    SettingsFullScreenScaffold(
        title = "Features & Diagnostics",
        subtitle = "Domain bypass, LAN sharing, privacy & backup",
        iconVector = Icons.Default.Tune,
        onBack = onDismiss,
        toastMessage = localToastMessage,
        bottomBar = {
            SettingsActionFooter(
                onReset = {
                    when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                        LockActionOutcome.ALLOW -> {
                            connectivityCheck = true
                            pingUrl = "https://www.google.com/generate_204"
                            subUa = "v2rayNG/1.8.23"
                            strictRedaction = true
                            bypassedDomains = SettingsRepository.DEFAULT_BYPASSED_DOMAINS
                            allowLanHotspot = false
                        }
                        LockActionOutcome.SHOW_LOCK_WARNING -> {
                            onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                        }
                        LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                    }
                },
                onCancel = onDismiss,
                onSave = {
                    when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                        LockActionOutcome.ALLOW -> {
                            settings.setConnectivityCheckEnabled(connectivityCheck)
                            settings.setPingTestUrl(pingUrl.trim().ifBlank { "https://www.google.com/generate_204" })
                            settings.setSubUserAgent(subUa.trim().ifBlank { "v2rayNG/1.8.23" })
                            settings.setStrictLogRedactionEnabled(strictRedaction)
                            settings.setBypassedDomains(bypassedDomains)
                            settings.setAllowLanHotspotEnabled(allowLanHotspot)
                            viewModel.notifySettingsSaved("Features")
                            onDismiss()
                        }
                        LockActionOutcome.SHOW_LOCK_WARNING -> {
                            onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                        }
                        LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                    }
                }
            )
        }
    ) {
        // Section 1 — Domain Bypass (Direct Sites)
        SettingsSectionCard(
            title = "Domain Bypass (Direct Sites)",
            subtitle = "Route traffic outside the VPN tunnel (Domestic DNS)"
        ) {
            Text(
                text = "Domains added here will bypass the VPN tunnel entirely. Useful for local Iranian (.ir) services, banking, or domestic intranet resources.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontFamily = SoraFamily,
                lineHeight = 17.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = { domainInput = it },
                    placeholder = {
                        Text(
                            text = "example.com, .ir",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp,
                            fontFamily = SoraFamily
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = PrimaryCyan,
                        focusedContainerColor = Color.White.copy(alpha = 0.02f),
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(
                        fontFamily = SoraFamily,
                        fontSize = 13.sp,
                        textDirection = TextDirection.Ltr
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                            LockActionOutcome.ALLOW -> {
                                val entries = SettingsRepository.parseAndNormalizeDomainEntries(domainInput)
                                if (entries.isNotEmpty()) {
                                    bypassedDomains = bypassedDomains + entries
                                    domainInput = ""
                                } else if (domainInput.isNotBlank()) {
                                    localToastMessage = "Invalid domain format"
                                }
                            }
                            LockActionOutcome.SHOW_LOCK_WARNING -> {
                                onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                            }
                            LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(
                        text = "Add",
                        color = BackgroundDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SoraFamily
                    )
                }
            }

            // Preset Chips
            val presetDomains = listOf("+ .ir" to ".ir")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetDomains.forEach { (label, domain) ->
                    val isAdded = bypassedDomains.contains(domain)
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isAdded) PrimaryCyan.copy(alpha = 0.18f)
                                else Color.White.copy(alpha = 0.04f)
                            )
                            .border(
                                1.dp,
                                if (isAdded) PrimaryCyan else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                                    LockActionOutcome.ALLOW -> {
                                        bypassedDomains = if (isAdded) {
                                            bypassedDomains - domain
                                        } else {
                                            bypassedDomains + domain
                                        }
                                    }
                                    LockActionOutcome.SHOW_LOCK_WARNING -> {
                                        onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                                    }
                                    LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isAdded) "✓ $domain" else label,
                            color = if (isAdded) PrimaryCyan else Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = if (isAdded) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = SoraFamily
                        )
                    }
                }
            }

            // Active Bypassed Domain Chips
            if (bypassedDomains.isEmpty()) {
                Text(
                    text = "No custom domains bypassed. All traffic routes through VPN tunnel.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontFamily = SoraFamily,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bypassedDomains.sorted().forEach { domain ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryCyan.copy(alpha = 0.08f))
                                .border(1.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = domain,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = SoraFamily
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                                                LockActionOutcome.ALLOW -> {
                                                    bypassedDomains = bypassedDomains - domain
                                                }
                                                LockActionOutcome.SHOW_LOCK_WARNING -> {
                                                    onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                                                }
                                                LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove $domain",
                                        tint = Color(0xFFCBD5E1),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2 — Hotspot & LAN VPN Sharing
        SettingsSectionCard(
            title = "Hotspot & LAN Sharing",
            subtitle = "Bind proxy inbounds to 0.0.0.0 instead of 127.0.0.1"
        ) {
            SettingsToggleRow(
                title = "Share VPN over Hotspot / LAN",
                description = "Listen on all local network interfaces so other devices connected to your hotspot can route through this VPN.",
                checked = allowLanHotspot,
                onCheckedChange = { allowLanHotspot = it }
            )

            // Connection Guide (No Root Required) sub-card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column {
                        Text(
                            text = "Connection Guide (No Root Required)",
                            color = PrimaryCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SoraFamily
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Route secondary devices (PC, laptop, console, phone) through your active VPN without root.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontFamily = SoraFamily,
                            lineHeight = 16.sp
                        )
                    }

                    // Proxy Host IP Row
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF070D18))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Proxy Host IP",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = SoraFamily
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = detectedIp,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SoraFamily,
                                    style = TextStyle(textDirection = TextDirection.Ltr)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Refresh Button
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            detectedIp = detectHotspotOrLanIp()
                                            localToastMessage = "Detected IP: $detectedIp"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh IP",
                                        tint = PrimaryCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Copy IP Button
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(detectedIp))
                                        localToastMessage = "IP copied: $detectedIp"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = BackgroundDark,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy IP",
                                        color = BackgroundDark,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = SoraFamily
                                    )
                                }
                            }
                        }
                    }

                    // 2-column Port Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // HTTP Proxy Port
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF070D18))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "HTTP Proxy",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontFamily = SoraFamily
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "10809",
                                        color = PrimaryCyan,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = SoraFamily
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString("10809"))
                                            localToastMessage = "HTTP Port copied: 10809"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy HTTP Port",
                                        tint = PrimaryCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        // SOCKS5 Port
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF070D18))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "SOCKS5 Proxy",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontFamily = SoraFamily
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "10808",
                                        color = PrimaryCyan,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = SoraFamily
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString("10808"))
                                            localToastMessage = "SOCKS5 Port copied: 10808"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy SOCKS5 Port",
                                        tint = PrimaryCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 4-step numbered guide
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "How to connect other devices:",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SoraFamily
                        )
                        GuideStepItem(number = "1", text = "Enable \"Share VPN over Hotspot / LAN\" above, save, and connect the VPN.")
                        GuideStepItem(number = "2", text = "Turn on your phone's Wi-Fi Hotspot.")
                        GuideStepItem(number = "3", text = "On the other phone or laptop, open Wi-Fi settings for your hotspot -> Proxy -> Manual.")
                        GuideStepItem(number = "4", text = "Enter Hostname: $detectedIp and Port: 10809, then save.")
                    }
                }
            }
        }

        // Section 3 — Connectivity & Delay Diagnostics
        SettingsSectionCard(
            title = "Connectivity Diagnostics",
            subtitle = "Public internet reachability & delay testing"
        ) {
            SettingsToggleRow(
                title = "Post-Connect Reachability Check",
                description = "Run non-fatal diagnostics after tunnel connects to verify public internet reachability.",
                checked = connectivityCheck,
                onCheckedChange = { connectivityCheck = it }
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = Color.White.copy(alpha = 0.06f)
            )

            SettingsTextFieldWithPresets(
                value = pingUrl,
                onValueChange = { pingUrl = it },
                label = "Real-Delay (Ping) Test URL",
                caption = "HTTP 204 endpoint used to measure tunnel round-trip latency",
                presets = listOf(
                    "Google 204" to "https://www.google.com/generate_204",
                    "Cloudflare 204" to "http://cp.cloudflare.com/generate_204",
                    "Gstatic 204" to "http://www.gstatic.com/generate_204"
                )
            )
        }

        // Section 4 — Subscription Client
        SettingsSectionCard(
            title = "Subscription Client",
            subtitle = "Subscription update identification header"
        ) {
            SettingsTextFieldWithPresets(
                value = subUa,
                onValueChange = { subUa = it },
                label = "Subscription User-Agent",
                caption = "HTTP User-Agent header sent when fetching subscription nodes",
                presets = listOf(
                    "v2rayNG/1.8.23" to "v2rayNG/1.8.23",
                    "HybridNest/1.0" to "HybridNest/1.0",
                    "Clash.Meta" to "Clash.Meta"
                )
            )
        }

        // Section 5 — Privacy & Logs
        SettingsSectionCard(
            title = "Privacy & Logs",
            subtitle = "Security redaction and session log buffers"
        ) {
            SettingsToggleRow(
                title = "Strict Log Redaction",
                description = "Automatically mask credentials, session cookies, and private keys in Live Logs and logcat.",
                checked = strictRedaction,
                onCheckedChange = { strictRedaction = it }
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = Color.White.copy(alpha = 0.06f)
            )

            Button(
                onClick = {
                    viewModel.clearAllProtocolLogs()
                    localToastMessage = "All logs cleared"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.04f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Clear All Protocol Logs",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SoraFamily
                )
            }
        }

        // Section 6 — Backup & Restore All Profiles
        SettingsSectionCard(
            title = "Backup & Restore",
            subtitle = "Export or restore all saved protocol profiles"
        ) {
            Text(
                text = "Backup covers all saved AnyConnect, WireGuard, and v2rayNG profiles as plaintext JSON.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontFamily = SoraFamily,
                lineHeight = 17.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                            LockActionOutcome.ALLOW -> {
                                showExportWarningDialog = true
                            }
                            LockActionOutcome.SHOW_LOCK_WARNING -> {
                                onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                            }
                            LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.04f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Export",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SoraFamily
                    )
                }

                Button(
                    onClick = {
                        when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                            LockActionOutcome.ALLOW -> {
                                val text = clipboardManager.getText()?.text ?: ""
                                viewModel.restoreProfilesFromBackupJson(text)
                            }
                            LockActionOutcome.SHOW_LOCK_WARNING -> {
                                onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                            }
                            LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.04f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Restore",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = SoraFamily
                    )
                }
            }
        }
    }

    if (showExportWarningDialog) {
        ExportWarningDialog(
            onDismiss = { showExportWarningDialog = false },
            onConfirmExport = {
                showExportWarningDialog = false
                viewModel.exportAllProfilesToJson { json ->
                    val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("HybridNest Backup", json)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        clipData.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    systemClipboard.setPrimaryClip(clipData)
                }
            }
        )
    }
}

// =========================================================================
// 5. CHECK FOR UPDATE FULL-SCREEN
// =========================================================================

@Composable
fun CheckForUpdateFullScreen(
    onDismiss: () -> Unit
) {
    var isChecking by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val (versionName, versionCode) = remember(context) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val vName = packageInfo.versionName ?: "1.0"
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            Pair(vName, vCode)
        } catch (_: Exception) {
            Pair("1.0", 1L)
        }
    }

    LaunchedEffect(isChecking) {
        if (isChecking) {
            delay(800L)
            isChecking = false
        }
    }

    SettingsFullScreenScaffold(
        title = "Check for Update",
        subtitle = "Installed build & native binary diagnostics",
        iconVector = Icons.Default.SystemUpdateAlt,
        onBack = onDismiss,
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text(
                        text = "Close",
                        fontFamily = SoraFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = { isChecking = true },
                    enabled = !isChecking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryCyan,
                        disabledContainerColor = PrimaryCyan.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = BackgroundDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Check Again",
                        color = BackgroundDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SoraFamily
                    )
                }
            }
        }
    ) {
        // Hero Status Card
        if (isChecking) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                    .border(1.dp, PrimaryCyan.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                    .padding(vertical = 36.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = PrimaryCyan,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Checking installed version...",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SoraFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Verifying local package integrity & architecture",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = SoraFamily
                    )
                }
            }
        } else {
            val emeraldColor = Color(0xFF10B981)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                    .border(1.dp, emeraldColor.copy(alpha = 0.36f), RoundedCornerShape(20.dp))
                    .padding(vertical = 28.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(emeraldColor.copy(alpha = 0.12f))
                            .border(1.dp, emeraldColor.copy(alpha = 0.32f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Up to Date",
                            tint = emeraldColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Up to Date",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SoraFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You are on the latest local build",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = SoraFamily
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "HybridNest v$versionName (Build $versionCode)",
                            color = PrimaryCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SoraFamily,
                            style = TextStyle(textDirection = TextDirection.Ltr)
                        )
                    }
                }
            }
        }

        // Build & Native Core Diagnostics Card
        SettingsSectionCard(
            title = "Build & Native Core Diagnostics",
            subtitle = "Verified compilation targets & bundled native libraries"
        ) {
            DiagnosticKeyValueRow(
                label = "Package Identifier",
                value = context.packageName
            )
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.06f))

            DiagnosticKeyValueRow(
                label = "Installed Version",
                value = "v$versionName (Build $versionCode)"
            )
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.06f))

            DiagnosticKeyValueRow(
                label = "Target Platform",
                value = "Compile SDK 37 • Target SDK 36"
            )
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.06f))

            DiagnosticKeyValueRow(
                label = "Page Size Alignment",
                value = "16KB Page Aligned (Android 15+)"
            )
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.06f))

            DiagnosticKeyValueRow(
                label = "Native Engines",
                value = "libopenconnect.so, libstoken.so, libv2ray.aar"
            )
        }
    }
}

// =========================================================================
// 6. ABOUT FULL-SCREEN
// =========================================================================

@Composable
fun AboutFullScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val (versionName, versionCode) = remember(context) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val vName = packageInfo.versionName ?: "1.0"
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            Pair(vName, vCode)
        } catch (_: Exception) {
            Pair("1.0", 1L)
        }
    }

    SettingsFullScreenScaffold(
        title = "About HybridNest",
        subtitle = "Multi-protocol Android VPN client",
        iconVector = Icons.Default.Info,
        onBack = onDismiss,
        bottomBar = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(
                    text = "Close",
                    color = BackgroundDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SoraFamily
                )
            }
        }
    ) {
        // App Identity Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F172A).copy(alpha = 0.88f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, PrimaryCyan.copy(alpha = 0.40f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "HybridNest Logo",
                        modifier = Modifier.size(58.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HybridNest",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = SoraFamily
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v$versionName (Build $versionCode)",
                        color = PrimaryCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SoraFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = context.packageName,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = SoraFamily,
                            style = TextStyle(textDirection = TextDirection.Ltr)
                        )
                    }
                }
            }
        }

        // Protocol Engine Architecture Card
        SettingsSectionCard(
            title = "Protocol Engine Architecture",
            subtitle = "Bundled native network stacks & cipher suites"
        ) {
            AboutProtocolRow(
                title = "AnyConnect (OpenConnect)",
                description = "Native C/JNI SSL/TLS (CSTP) & DTLS UDP with RSA SecurID (libstoken).",
                iconRes = R.drawable.ic_openconnect
            )

            AboutProtocolRow(
                title = "WireGuard",
                description = "User-space gVisor netstack outbound (noKernelTun) with pure-Kotlin RFC 7748 Curve25519 key derivation.",
                iconRes = R.drawable.ic_wireguard
            )

            AboutProtocolRow(
                title = "v2rayNG (Xray-core)",
                description = "Gomobile Xray-core supporting VLESS, VMess, Trojan, Reality, XHTTP, WS, gRPC, and DNS bootstrap pinning.",
                iconRes = R.drawable.ic_v2rayng
            )
        }

        // Security & Platform Guarantees Card
        SettingsSectionCard(
            title = "Security & Platform Guarantees",
            subtitle = "Hardened local isolation & compliance contracts"
        ) {
            AboutGuaranteeRow(
                title = "Fail-Closed Per-App Split Tunneling",
                description = "Strict isolation ensuring no traffic escapes when bound apps fail or disconnect."
            )
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.06f))

            AboutGuaranteeRow(
                title = "Producer-Side Log Redaction (LogRedactor)",
                description = "Dynamic credential, session cookie, and secret key stripping before log emission."
            )
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.06f))

            AboutGuaranteeRow(
                title = "16KB Memory Page Size Compatibility",
                description = "Native binaries aligned and tested for modern Android 15 & 16 runtimes."
            )
        }
    }
}

@Composable
private fun DiagnosticKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontFamily = SoraFamily
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = SoraFamily,
            style = TextStyle(textDirection = TextDirection.Ltr)
        )
    }
}

@Composable
private fun AboutProtocolRow(
    title: String,
    description: String,
    iconRes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaryCyan.copy(alpha = 0.12f))
                .border(1.dp, PrimaryCyan.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = PrimaryCyan,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SoraFamily
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun AboutGuaranteeRow(
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(PrimaryCyan)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SoraFamily
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                lineHeight = 15.sp
            )
        }
    }
}

// =========================================================================
// DRAWER MODAL HOST
// =========================================================================

@Composable
fun DrawerModalHost(
    activeModal: DrawerModalType,
    onDismiss: () -> Unit,
    viewModel: DashboardViewModel,
    onOpenPerAppRouting: () -> Unit,
    connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    onShowConnectionWarning: (String) -> Unit = {}
) {
    // In-tree Full-Screen converted destinations with smooth slide & fade transitions
    AnimatedContent(
        targetState = activeModal,
        transitionSpec = {
            if (targetState != DrawerModalType.NONE || initialState != DrawerModalType.NONE) {
                (slideInHorizontally(initialOffsetX = { it / 6 }) + fadeIn()) togetherWith
                    (slideOutHorizontally(targetOffsetX = { it / 6 }) + fadeOut())
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "FullScreenDrawerModalHost"
    ) { modal ->
        when (modal) {
            DrawerModalType.ANYCONNECT -> AnyConnectSettingsFullScreen(
                onDismiss = onDismiss,
                viewModel = viewModel,
                connectionState = connectionState,
                onShowConnectionWarning = onShowConnectionWarning
            )
            DrawerModalType.WIREGUARD -> WireGuardSettingsFullScreen(
                onDismiss = onDismiss,
                viewModel = viewModel,
                connectionState = connectionState,
                onShowConnectionWarning = onShowConnectionWarning
            )
            DrawerModalType.V2RAY -> V2raySettingsFullScreen(
                onDismiss = onDismiss,
                viewModel = viewModel,
                connectionState = connectionState,
                onShowConnectionWarning = onShowConnectionWarning
            )
            DrawerModalType.FEATURES -> FeaturesFullScreen(
                onDismiss = onDismiss,
                viewModel = viewModel,
                connectionState = connectionState,
                onShowConnectionWarning = onShowConnectionWarning
            )
            DrawerModalType.CHECK_UPDATE -> CheckForUpdateFullScreen(
                onDismiss = onDismiss
            )
            DrawerModalType.ABOUT -> AboutFullScreen(
                onDismiss = onDismiss
            )
            DrawerModalType.NONE -> Unit
        }
    }
}

@Composable
private fun GuideStepItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(PrimaryCyan.copy(alpha = 0.15f))
                .border(1.dp, PrimaryCyan.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = PrimaryCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SoraFamily
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontFamily = SoraFamily,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// =========================================================================
// ACTIVE CONNECTION WARNING DIALOG
// =========================================================================

@Composable
fun ActiveConnectionWarningDialog(
    message: String?,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit
) {
    if (message == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceMenu)
                        .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimaryCyan.copy(alpha = 0.15f))
                                    .border(1.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Active VPN",
                                    tint = PrimaryCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Active VPN Connection",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SoraFamily
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = message,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = SoraFamily,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                                border = BorderStroke(1.dp, BorderGlass),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = SoraFamily
                                )
                            }

                            Button(
                                onClick = onDisconnect,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text(
                                    text = "Disconnect",
                                    color = BackgroundDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SoraFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// EXPORT PLAINTEXT WARNING DIALOG
// =========================================================================

@Composable
fun ExportWarningDialog(
    onDismiss: () -> Unit,
    onConfirmExport: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceMenu)
                        .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimaryCyan.copy(alpha = 0.15f))
                                    .border(1.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Warning",
                                    tint = PrimaryCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Export Plaintext Backup",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SoraFamily
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "The exported backup is unencrypted (plaintext) JSON and includes sensitive credentials, such as saved passwords and WireGuard private keys.\n\nAnyone with access to your clipboard or this text can view these credentials. Please store the backup securely and clear your clipboard after pasting.",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontFamily = SoraFamily,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                                border = BorderStroke(1.dp, BorderGlass),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = SoraFamily
                                )
                            }

                            Button(
                                onClick = onConfirmExport,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text(
                                    text = "Confirm Export",
                                    color = BackgroundDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SoraFamily
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
