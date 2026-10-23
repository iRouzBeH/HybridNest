package dev.rouzbeh.hybridvpn.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rouzbeh.hybridvpn.R
import dev.rouzbeh.hybridvpn.core.designsystem.BackgroundDark
import dev.rouzbeh.hybridvpn.core.designsystem.BorderGlass
import dev.rouzbeh.hybridvpn.core.designsystem.PrimaryCyan
import dev.rouzbeh.hybridvpn.core.designsystem.SurfaceDark
import dev.rouzbeh.hybridvpn.core.designsystem.SurfaceGlass
import dev.rouzbeh.hybridvpn.core.designsystem.SurfaceMenu
import dev.rouzbeh.hybridvpn.core.designsystem.TextPrimary
import dev.rouzbeh.hybridvpn.core.designsystem.TextSecondary
import dev.rouzbeh.hybridvpn.core.vpn.dns.DnsServer
import dev.rouzbeh.hybridvpn.service.ConnectionState

/**
 * Server list screen for the "DNS" connection method.
 *
 * Mirrors the structure/styling of ServerListFullScreen but backed by
 * DnsServer entries (no Room profiles). Built-ins can be hidden (not edited);
 * custom entries can be added, edited and deleted. Ping is measured with a
 * real UDP DNS query to the server's primary address.
 */
@Composable
fun DnsServerListScreen(
    servers: List<DnsServer>,
    selectedServerId: String?,
    connectionState: ConnectionState,
    onSelect: (String) -> Unit,
    onDelete: (DnsServer) -> Unit,
    onEdit: (DnsServer) -> Unit,
    onTestPing: (DnsServer) -> Unit,
    onTestAllPing: () -> Unit,
    onNavigateBack: () -> Unit,
    toastMessage: String? = null,
    onShowToast: (String) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(SurfaceDark)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp).bounceClick { onNavigateBack() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "DNS Server List",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SoraFamily
                )
                Spacer(modifier = Modifier.weight(1f))

                Box {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp).bounceClick { showMenu = true }
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(SurfaceDark)
                            .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ping all servers", color = Color.White, fontFamily = SoraFamily) },
                            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White) },
                            onClick = { showMenu = false; onTestAllPing() }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(servers, key = { it.id }) { server ->
                    DnsServerCard(
                        server = server,
                        isSelected = server.id == selectedServerId,
                        connectionState = connectionState,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(server.id)
                        },
                        onDelete = if (server.isBuiltin) {
                            { onDelete(server) } // hide built-in
                        } else {
                            { onDelete(server) }
                        },
                        onEdit = if (server.isBuiltin) null else { { onEdit(server) } },
                        onPingClick = { onTestPing(server) },
                        onShowToast = onShowToast
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }

        CustomGlassToast(
            message = toastMessage,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun DnsServerCard(
    server: DnsServer,
    isSelected: Boolean,
    connectionState: ConnectionState,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onPingClick: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val bgColor = if (isSelected) PrimaryCyan.copy(alpha = 0.1f) else SurfaceGlass
    val isConnectedOrConnecting =
        connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING
    val isUnreachable = server.ping == -1L

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(
                1.dp,
                if (isSelected) PrimaryCyan.copy(alpha = 0.5f) else BorderGlass,
                RoundedCornerShape(18.dp)
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (isUnreachable) {
                    onShowToast("This server is unreachable right now\nPick another server or retest its ping.")
                } else {
                    onClick()
                }
            }
            .padding(start = 16.dp, end = 10.dp, top = 18.dp, bottom = 18.dp)
    ) {
        var isPinging by remember { mutableStateOf(false) }
        LaunchedEffect(server.ping) { isPinging = false }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (isSelected) PrimaryCyan.copy(alpha = 0.2f) else BackgroundDark.copy(alpha = 0.3f))
        ) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_dns),
                contentDescription = null,
                tint = if (isSelected) PrimaryCyan else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                color = TextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = SoraFamily
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = server.primary,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = SoraFamily
            )
            if (server.secondary != null) {
                Text(
                    text = server.secondary,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = SoraFamily
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Ping badge (tappable to retest). Fixed size so the address
            // column always keeps its remaining width and never slides under it.
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.30f))
                    .border(1.dp, BorderGlass.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .clickable { isPinging = true; onPingClick() },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isPinging,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                                fadeOut(animationSpec = tween(90))
                    },
                    label = "dns_ping_animation"
                ) { loading ->
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryCyan
                        )
                    } else {
                        val isUntested = server.ping == 0L
                        val pText = when {
                            isUnreachable -> "-1"
                            isUntested -> "Test"
                            else -> "${server.ping} ms"
                        }
                        val pColor = when {
                            isUnreachable -> Color(0xFFFF5252)
                            isUntested -> Color(0xFF9E9E9E)
                            else -> PrimaryCyan
                        }
                        Text(
                            text = pText,
                            color = pColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = SoraFamily,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))

            if (onEdit != null) {
                IconButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onEdit() },
                    enabled = !isConnectedOrConnecting,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = if (isConnectedOrConnecting) Color.DarkGray else TextSecondary.copy(alpha = 0.65f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (onDelete != null) {
                IconButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDelete() },
                    enabled = !(isSelected && isConnectedOrConnecting),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = if (server.isBuiltin) "Hide" else "Delete",
                        tint = if (isSelected && isConnectedOrConnecting) Color.DarkGray else Color(0xFFFF3B30).copy(alpha = 0.75f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * Add / edit sheet for a custom DNS server. Built-ins are never edited here.
 * Primary is required; secondary is optional. Both must be literal IPs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsServerSheetContent(
    editingServer: DnsServer? = null,
    onAdd: (String, String, String?) -> Unit,
    onUpdate: (String, String, String, String?) -> Unit,
    onClose: () -> Unit,
    onShowToast: (String) -> Unit
) {
    var name by remember { mutableStateOf(editingServer?.name ?: "") }
    var primary by remember { mutableStateOf(editingServer?.primary ?: "") }
    var secondary by remember { mutableStateOf(editingServer?.secondary ?: "") }

    val primaryValid = dev.rouzbeh.hybridvpn.core.vpn.dns.DnsValidation.isValidIp(primary)
    val secondaryValid = secondary.isBlank() ||
        dev.rouzbeh.hybridvpn.core.vpn.dns.DnsValidation.isValidIp(secondary)
    val isFormValid = primaryValid && secondaryValid

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 48.dp, top = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(TextSecondary)
            )
        }
        Text(
            text = if (editingServer == null) "New DNS Server" else "Edit DNS Server",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SoraFamily
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Name (optional)", color = TextSecondary, fontFamily = SoraFamily) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = BorderGlass,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            textStyle = TextStyle(fontFamily = SoraFamily),
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = primary, onValueChange = { primary = it.trim() },
            label = { Text("Primary DNS IP", color = TextSecondary, fontFamily = SoraFamily) },
            isError = primary.isNotBlank() && !primaryValid,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = BorderGlass,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            textStyle = TextStyle(fontFamily = SoraFamily),
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = secondary, onValueChange = { secondary = it.trim() },
            label = { Text("Secondary DNS IP (optional)", color = TextSecondary, fontFamily = SoraFamily) },
            isError = secondary.isNotBlank() && !secondaryValid,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = BorderGlass,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            textStyle = TextStyle(fontFamily = SoraFamily),
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (editingServer == null) {
                    onAdd(name, primary, secondary.ifBlank { null })
                } else {
                    onUpdate(editingServer.id, name, primary, secondary.ifBlank { null })
                }
                onClose()
            },
            enabled = isFormValid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                disabledContainerColor = SurfaceGlass
            ),
            shape = RoundedCornerShape(16.dp),
            border = if (isFormValid) null else androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
        ) {
            Text(
                if (editingServer == null) "Add" else "Save",
                color = if (isFormValid) BackgroundDark else TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SoraFamily
            )
        }
    }
}
