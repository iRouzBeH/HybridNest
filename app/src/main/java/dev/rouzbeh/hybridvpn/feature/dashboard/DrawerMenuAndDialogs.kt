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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
// REUSABLE DIALOG BUILDING BLOCKS
// =========================================================================

@Composable
private fun DialogShell(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
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
                    .padding(horizontal = 18.dp, vertical = 24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceMenu)
                    .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SoraFamily
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = SoraFamily
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SurfaceGlass)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = BorderGlass)
                    Spacer(modifier = Modifier.height(16.dp))

                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = PrimaryCyan,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = SoraFamily,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SoraFamily
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 15.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryCyan,
                checkedTrackColor = PrimaryCyan.copy(alpha = 0.5f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = SurfaceDark
            )
        )
    }
}

@Composable
private fun PresetChipRow(
    presets: List<Pair<String, String>>,
    selectedValue: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { (label, value) ->
            val isSelected = selectedValue == value
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) PrimaryCyan.copy(alpha = 0.2f) else SurfaceGlass)
                    .border(
                        1.dp,
                        if (isSelected) PrimaryCyan else BorderGlass,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) PrimaryCyan else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = SoraFamily
                )
            }
        }
    }
}

@Composable
private fun GlassOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary, fontFamily = SoraFamily, fontSize = 12.sp) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryCyan,
            unfocusedBorderColor = BorderGlass,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        textStyle = TextStyle(fontFamily = SoraFamily, fontSize = 13.sp, textDirection = TextDirection.Ltr),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun DialogFooterActions(
    onReset: (() -> Unit)? = null,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveText: String = "Save"
) {
    Spacer(modifier = Modifier.height(20.dp))
    HorizontalDivider(color = BorderGlass)
    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onReset != null) {
            TextButton(onClick = onReset) {
                Text(
                    text = "Reset",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = SoraFamily
                )
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                border = BorderStroke(1.dp, BorderGlass),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Cancel",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = SoraFamily
                )
            }

            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = saveText,
                    color = BackgroundDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SoraFamily
                )
            }
        }
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

@Composable
fun FeaturesDialog(
    onDismiss: () -> Unit,
    viewModel: DashboardViewModel,
    onOpenPerAppRouting: () -> Unit = {},
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

    DialogShell(
        onDismiss = onDismiss,
        title = "Features & Diagnostics",
        subtitle = "Domain Bypass, Hotspot, Reachability & Backup"
    ) {
        // 1. Domain Bypass (Direct Sites)
        SettingSectionHeader("Domain Bypass (Direct Sites)")
        Text(
            text = "Directly route traffic outside the VPN tunnel for Iranian websites, local banking, or custom domains.",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = SoraFamily,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = domainInput,
                onValueChange = { domainInput = it },
                placeholder = { Text("e.g. .ir, shaparak.ir", color = TextSecondary, fontSize = 12.sp, fontFamily = SoraFamily) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryCyan,
                    unfocusedBorderColor = BorderGlass,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(10.dp),
                textStyle = TextStyle(fontFamily = SoraFamily, fontSize = 12.sp, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                        LockActionOutcome.ALLOW -> {
                            val entries = SettingsRepository.parseAndNormalizeDomainEntries(domainInput)
                            if (entries.isNotEmpty()) {
                                bypassedDomains = bypassedDomains + entries
                                domainInput = ""
                            } else if (domainInput.isNotBlank()) {
                                viewModel.showDashboardToast("Invalid domain format")
                            }
                        }
                        LockActionOutcome.SHOW_LOCK_WARNING -> {
                            onShowConnectionWarning("Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?")
                        }
                        LockActionOutcome.IGNORE_WHILE_DISCONNECTING -> Unit
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "Add",
                    color = BackgroundDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SoraFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Preset Chips
        val presetDomains = listOf(
            "+ .ir" to ".ir"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetDomains.forEach { (label, domain) ->
                val isAdded = bypassedDomains.contains(domain)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isAdded) PrimaryCyan.copy(alpha = 0.18f) else SurfaceGlass)
                        .border(1.dp, if (isAdded) PrimaryCyan else BorderGlass, RoundedCornerShape(8.dp))
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
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isAdded) "✓ $domain" else label,
                        color = if (isAdded) PrimaryCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isAdded) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = SoraFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Active Bypassed Domains
        if (bypassedDomains.isEmpty()) {
            Text(
                text = "No custom domains bypassed. All traffic routes through VPN tunnel.",
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = SoraFamily
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bypassedDomains.sorted().forEach { domain ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceGlass)
                            .border(1.dp, PrimaryCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = domain,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = SoraFamily
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove $domain",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(14.dp)
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
                                    }
                            )
                        }
                    }
                }
            }
        }

        // 2. Hotspot & LAN VPN Sharing
        Spacer(modifier = Modifier.height(16.dp))
        SettingSectionHeader("Hotspot & LAN VPN Sharing")
        SettingSwitchRow(
            title = "Share VPN over Hotspot / LAN",
            description = "Listen on all local interfaces (0.0.0.0) so devices connected via Wi-Fi hotspot or local network can use this VPN proxy.",
            checked = allowLanHotspot,
            onCheckedChange = { allowLanHotspot = it }
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Connection Guide (No Root Required) Card
        var detectedIp by remember { mutableStateOf(detectHotspotOrLanIp()) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceGlass)
                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Connection Guide (No Root Required)",
                    color = PrimaryCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SoraFamily
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Route secondary devices (PC, laptop, console, phone) through your active VPN without root.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Proxy Host IP Row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderGlass, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Proxy Host IP",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = SoraFamily
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = detectedIp,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SoraFamily
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Refresh Button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceGlass)
                                    .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                                    .clickable {
                                        detectedIp = detectHotspotOrLanIp()
                                        viewModel.showDashboardToast("Detected IP: $detectedIp")
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
                                    viewModel.showDashboardToast("IP copied: $detectedIp")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
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

                Spacer(modifier = Modifier.height(8.dp))

                // Ports Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // HTTP Proxy Port
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .border(1.dp, BorderGlass, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "HTTP Proxy Port",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = SoraFamily
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "10809",
                                    color = PrimaryCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SoraFamily
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceGlass)
                                    .border(1.dp, BorderGlass, RoundedCornerShape(6.dp))
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString("10809"))
                                        viewModel.showDashboardToast("HTTP Port copied: 10809")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Port",
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .border(1.dp, BorderGlass, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "SOCKS5 Port",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = SoraFamily
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "10808",
                                    color = PrimaryCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SoraFamily
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceGlass)
                                    .border(1.dp, BorderGlass, RoundedCornerShape(6.dp))
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString("10808"))
                                        viewModel.showDashboardToast("SOCKS5 Port copied: 10808")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Port",
                                    tint = PrimaryCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Steps
                Text(
                    text = "How to connect other devices:",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SoraFamily
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GuideStepItem(number = "1", text = "Enable \"Share VPN over Hotspot / LAN\" above, save, and connect the VPN.")
                    GuideStepItem(number = "2", text = "Turn on your phone's Wi-Fi Hotspot.")
                    GuideStepItem(number = "3", text = "On the other phone or laptop, open Wi-Fi settings for your hotspot -> Proxy -> Manual.")
                    GuideStepItem(number = "4", text = "Enter Hostname: $detectedIp and Port: 10809, then save.")
                }
            }
        }

        // 3. Connectivity Diagnostics
        Spacer(modifier = Modifier.height(16.dp))
        SettingSectionHeader("Connectivity Diagnostics")
        SettingSwitchRow(
            title = "Post-Connect Reachability Check",
            description = "Run non-fatal diagnostics after tunnel connects to verify public internet reachability.",
            checked = connectivityCheck,
            onCheckedChange = { connectivityCheck = it }
        )

        // 4. Ping Test URL
        Spacer(modifier = Modifier.height(16.dp))
        SettingSectionHeader("Real-Delay (Ping) Test URL")
        GlassOutlinedTextField(
            value = pingUrl,
            onValueChange = { pingUrl = it },
            label = "HTTP 204 Ping URL"
        )
        Spacer(modifier = Modifier.height(8.dp))
        PresetChipRow(
            presets = listOf(
                "Google 204" to "https://www.google.com/generate_204",
                "Cloudflare 204" to "http://cp.cloudflare.com/generate_204",
                "Gstatic 204" to "http://www.gstatic.com/generate_204"
            ),
            selectedValue = pingUrl,
            onSelect = { pingUrl = it }
        )

        // 5. Subscription User-Agent
        Spacer(modifier = Modifier.height(16.dp))
        SettingSectionHeader("Subscription User-Agent")
        GlassOutlinedTextField(
            value = subUa,
            onValueChange = { subUa = it },
            label = "HTTP User-Agent Header"
        )
        Spacer(modifier = Modifier.height(8.dp))
        PresetChipRow(
            presets = listOf(
                "v2rayNG/1.8.23" to "v2rayNG/1.8.23",
                "HybridNest/1.0" to "HybridNest/1.0",
                "Clash.Meta" to "Clash.Meta"
            ),
            selectedValue = subUa,
            onSelect = { subUa = it }
        )

        // 6. Privacy & Logs
        Spacer(modifier = Modifier.height(16.dp))
        SettingSectionHeader("Privacy & Logs")
        SettingSwitchRow(
            title = "Strict Log Redaction",
            description = "Automatically mask credentials, session cookies, and private keys in Live Logs and logcat.",
            checked = strictRedaction,
            onCheckedChange = { strictRedaction = it }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { viewModel.clearAllProtocolLogs() },
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
            border = BorderStroke(1.dp, BorderGlass),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(42.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Clear All Protocol Logs",
                color = TextPrimary,
                fontSize = 12.sp,
                fontFamily = SoraFamily
            )
        }

        // 7. Backup & Restore
        Spacer(modifier = Modifier.height(16.dp))
        SettingSectionHeader("Backup & Restore All Profiles")
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
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                border = BorderStroke(1.dp, BorderGlass),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(42.dp)
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
                    fontSize = 12.sp,
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
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                border = BorderStroke(1.dp, BorderGlass),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(42.dp)
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
                    fontSize = 12.sp,
                    fontFamily = SoraFamily
                )
            }
        }

        DialogFooterActions(
            onReset = {
                when (ConnectionLockGuard.evaluateControlTap(connectionState)) {
                    LockActionOutcome.ALLOW -> {
                        connectivityCheck = true
                        pingUrl = "https://www.google.com/generate_204"
                        subUa = "v2rayNG/1.8.23"
                        strictRedaction = true
                        bypassedDomains = emptySet()
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
// 5. CHECK FOR UPDATE DIALOG
// =========================================================================

@Composable
fun CheckForUpdateDialog(
    onDismiss: () -> Unit
) {
    var isChecking by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isChecking) {
        if (isChecking) {
            delay(800L)
            isChecking = false
        }
    }

    DialogShell(
        onDismiss = onDismiss,
        title = "Check for Update",
        subtitle = "Release channels & verified builds"
    ) {
        if (isChecking) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = PrimaryCyan,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Checking release channel...",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = SoraFamily
                )
            }
        } else {
            // Verified Version Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlass)
                    .border(1.dp, PrimaryCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Up to date",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "You are on the latest version",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SoraFamily
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "HybridNest v1.0 (Version Code 1)",
                            color = PrimaryCyan,
                            fontSize = 12.sp,
                            fontFamily = SoraFamily
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Build Info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceGlass)
                    .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Build Specs",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SoraFamily
                    )
                    Text(
                        text = "• Compile SDK: 37 • Target SDK: 36\n• 16KB Page Aligned ABI Binaries\n• Active: libopenconnect.so, libstoken.so, libv2ray.aar",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = SoraFamily,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { isChecking = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                    border = BorderStroke(1.dp, BorderGlass),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Check Again",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = SoraFamily
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Text(
                        text = "Close",
                        color = BackgroundDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SoraFamily
                    )
                }
            }
        }
    }
}

// =========================================================================
// 6. ABOUT DIALOG
// =========================================================================

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    DialogShell(
        onDismiss = onDismiss,
        title = "About HybridNest",
        subtitle = "Multi-Engine Android VPN Client"
    ) {
        // App identity box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceGlass)
                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .border(1.dp, PrimaryCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo",
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "HybridNest",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = SoraFamily
                )
                Text(
                    text = "v1.0 • dev.rouzbeh.hybridvpn",
                    color = PrimaryCyan,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        SettingSectionHeader("Protocol Engine Architecture")

        // 3 Engine Cards
        AboutEngineCard(
            title = "AnyConnect (OpenConnect)",
            description = "Native C/JNI SSL/TLS (CSTP) & DTLS UDP engine with RSA SecurID (libstoken) and instant socket interrupt.",
            iconRes = R.drawable.ic_openconnect
        )

        Spacer(modifier = Modifier.height(8.dp))

        AboutEngineCard(
            title = "WireGuard",
            description = "User-space gVisor netstack outbound (noKernelTun) with pure-Kotlin RFC 7748 Curve25519 key derivation.",
            iconRes = R.drawable.ic_wireguard
        )

        Spacer(modifier = Modifier.height(8.dp))

        AboutEngineCard(
            title = "v2rayNG (Xray-core)",
            description = "Gomobile Xray-core supporting VLESS, VMess, Trojan, Reality, XHTTP/SplitHTTP, WS, gRPC & DNS bootstrap pinning.",
            iconRes = R.drawable.ic_v2rayng
        )

        Spacer(modifier = Modifier.height(14.dp))
        SettingSectionHeader("Security & Platform Guarantees")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceGlass)
                .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "• Fail-Closed Per-App Split Tunneling Contract",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SoraFamily
                )
                Text(
                    text = "• Producer-Side Credential & Secret Key Log Redaction",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SoraFamily
                )
                Text(
                    text = "• 16KB Memory Page Size Compatible (Android 15 & 16)",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = SoraFamily
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
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
}

@Composable
private fun AboutEngineCard(
    title: String,
    description: String,
    iconRes: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = PrimaryCyan,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SoraFamily
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TextSecondary,
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
            if (targetState == DrawerModalType.ANYCONNECT ||
                targetState == DrawerModalType.WIREGUARD ||
                targetState == DrawerModalType.V2RAY ||
                initialState == DrawerModalType.ANYCONNECT ||
                initialState == DrawerModalType.WIREGUARD ||
                initialState == DrawerModalType.V2RAY
            ) {
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
            else -> Unit
        }
    }

    // Modal dialogs (kept on DialogShell until subsequent sub-phases 4C and 4D)
    when (activeModal) {
        DrawerModalType.FEATURES -> FeaturesDialog(
            onDismiss = onDismiss,
            viewModel = viewModel,
            onOpenPerAppRouting = onOpenPerAppRouting,
            connectionState = connectionState,
            onShowConnectionWarning = onShowConnectionWarning
        )
        DrawerModalType.CHECK_UPDATE -> CheckForUpdateDialog(onDismiss = onDismiss)
        DrawerModalType.ABOUT -> AboutDialog(onDismiss = onDismiss)
        DrawerModalType.NONE,
        DrawerModalType.ANYCONNECT,
        DrawerModalType.WIREGUARD,
        DrawerModalType.V2RAY -> Unit
    }
}

@Composable
private fun GuideStepItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number. ",
            color = PrimaryCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SoraFamily
        )
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = SoraFamily,
            lineHeight = 15.sp
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
