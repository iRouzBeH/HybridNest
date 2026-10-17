package dev.rouzbeh.hybridvpn.feature.dashboard

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.rouzbeh.hybridvpn.R
import dev.rouzbeh.hybridvpn.core.designsystem.BackgroundDark
import dev.rouzbeh.hybridvpn.core.designsystem.BorderGlass
import dev.rouzbeh.hybridvpn.core.designsystem.PrimaryCyan
import dev.rouzbeh.hybridvpn.core.designsystem.SurfaceDark
import dev.rouzbeh.hybridvpn.core.designsystem.SurfaceGlass
import dev.rouzbeh.hybridvpn.core.designsystem.SurfaceMenu
import dev.rouzbeh.hybridvpn.core.designsystem.TextPrimary
import dev.rouzbeh.hybridvpn.core.designsystem.TextSecondary
import dev.rouzbeh.hybridvpn.data.local.VpnProfile
import dev.rouzbeh.hybridvpn.service.ConnectionState
import dev.rouzbeh.hybridvpn.service.NetworkInfo
import dev.rouzbeh.hybridvpn.service.VpnStateManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val TelegramReorderIcon: androidx.compose.ui.graphics.vector.ImageVector
    get() = androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "Reorder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            stroke = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            // Box 1
            moveTo(5.5f, 15f); lineTo(7.5f, 15f); arcTo(
            1.5f,
            1.5f,
            0f,
            false,
            true,
            9f,
            16.5f
        ); lineTo(9f, 18.5f); arcTo(1.5f, 1.5f, 0f, false, true, 7.5f, 20f); lineTo(
            5.5f,
            20f
        ); arcTo(1.5f, 1.5f, 0f, false, true, 4f, 18.5f); lineTo(4f, 16.5f); arcTo(
            1.5f,
            1.5f,
            0f,
            false,
            true,
            5.5f,
            15f
        ); close()
            // Box 2
            moveTo(12.5f, 15f); lineTo(14.5f, 15f); arcTo(
            1.5f,
            1.5f,
            0f,
            false,
            true,
            16f,
            16.5f
        ); lineTo(16f, 18.5f); arcTo(1.5f, 1.5f, 0f, false, true, 14.5f, 20f); lineTo(
            12.5f,
            20f
        ); arcTo(1.5f, 1.5f, 0f, false, true, 11f, 18.5f); lineTo(11f, 16.5f); arcTo(
            1.5f,
            1.5f,
            0f,
            false,
            true,
            12.5f,
            15f
        ); close()
            // Box 3
            moveTo(19.5f, 15f); lineTo(21.5f, 15f); arcTo(
            1.5f,
            1.5f,
            0f,
            false,
            true,
            23f,
            16.5f
        ); lineTo(23f, 18.5f); arcTo(1.5f, 1.5f, 0f, false, true, 21.5f, 20f); lineTo(
            19.5f,
            20f
        ); arcTo(1.5f, 1.5f, 0f, false, true, 18f, 18.5f); lineTo(18f, 16.5f); arcTo(
            1.5f,
            1.5f,
            0f,
            false,
            true,
            19.5f,
            15f
        ); close()

            // Arrow pointing from first box up, right, and down to third box
            moveTo(6.5f, 13f)
            lineTo(6.5f, 7f)
            arcTo(3f, 3f, 0f, false, true, 9.5f, 4f)
            lineTo(17.5f, 4f)
            arcTo(3f, 3f, 0f, false, true, 20.5f, 7f)
            lineTo(20.5f, 12f)

            // Arrow head
            moveTo(17.5f, 10f)
            lineTo(20.5f, 13f)
            lineTo(23.5f, 10f)
        }
    }.build()

val AsteriskVisualTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
    androidx.compose.ui.text.input.TransformedText(
        androidx.compose.ui.text.AnnotatedString("*".repeat(text.text.length)),
        androidx.compose.ui.text.input.OffsetMapping.Identity
    )
}

val SfProPlusBlack = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sf_proplus_black))

@Composable
fun Modifier.bounceClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.85f else 1f, label = "bounce")

    return this
        .scale(scale)
        .pointerInput(Unit) {
            coroutineScope {
                detectTapGestures(
                    onLongPress = { onLongClick?.invoke() },
                    onTap = { onClick() },
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        interactionSource.emit(PressInteraction.Release(press))
                    }
                )
            }
        }
}

enum class VpnProtocolDisplay(val title: String, val iconResId: Int) {
    OpenConnect("OpenConnect", R.drawable.ic_openconnect),
    OpenVPN("OpenVPN", R.drawable.ic_openvpn),
    V2rayNG("v2rayNG", R.drawable.ic_v2rayng) // Ensure ic_v2rayng.png exists in res/drawable
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val networkInfo by viewModel.networkInfo.collectAsStateWithLifecycle()
    val filteredProfiles by viewModel.filteredProfiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val rawSpeed by VpnStateManager.rawSpeed.collectAsStateWithLifecycle()
    val pingResult by viewModel.pingResult.collectAsStateWithLifecycle()
    val selectedProtocol by viewModel.selectedProtocol.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isPerAppEnabled by viewModel.isPerAppEnabled.collectAsStateWithLifecycle()
    val isBypassMode by viewModel.isBypassMode.collectAsStateWithLifecycle()
    val isRoutingPending by viewModel.isRoutingPending.collectAsStateWithLifecycle()
    val currentLogs by viewModel.currentLogs.collectAsStateWithLifecycle()

    val drawerState =
        androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen, // CRITICAL FIX: Disables swipe-to-open
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.75f), // Takes exactly 3/4 of the screen
                drawerContainerColor = SurfaceMenu
            ) {
                Spacer(modifier = Modifier.height(64.dp))
                Text(
                    "HybridNest",
                    color = PrimaryCyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                androidx.compose.material3.HorizontalDivider(color = BorderGlass)
                // Future menu items will go here
            }
        }
    ) {
        DashboardContent(
            uptimeFlow = viewModel.uptime,
            connectionState = connectionState,
            networkInfo = networkInfo,
            filteredProfiles = filteredProfiles,
            activeProfile = activeProfile,
            speed = speed,
            rawSpeed = rawSpeed,
            pingResult = pingResult,
            selectedProtocol = selectedProtocol,
            installedApps = installedApps,
            isPerAppEnabled = isPerAppEnabled,
            isBypassMode = isBypassMode,
            isRoutingPending = isRoutingPending,
            currentLogs = currentLogs,
            onClearLogs = { viewModel.clearLogs(selectedProtocol) },
            onProtocolSelected = { viewModel.setProtocol(it) },
            onStartVpn = { context -> viewModel.startVpn(context) },
            onStopVpn = { context -> viewModel.initiateDisconnect(context) { /* handled in VM */ } },
            onCancelVpn = { context -> viewModel.cancelConnection(context) },
            onResetState = { viewModel.resetState() },
            onSelectServer = { profileId, context -> viewModel.selectServer(profileId, context) },
            onDeleteServer = { profile -> viewModel.deleteServer(profile) },
            onAddServer = { name, addr, user, pass, proto ->
                viewModel.addServer(
                    name,
                    addr,
                    user,
                    pass,
                    proto
                )
            },
            onUpdateServer = { profile -> viewModel.updateServer(profile) },
            onDeleteSubGroup = { subKey -> viewModel.deleteSubscriptionGroup(subKey) },
            onRenameSubGroup = { subKey, newName -> viewModel.renameSubscriptionGroup(subKey, newName) },
            onTestSinglePing = { viewModel.testSingleV2rayPing(it) },
            onTestAllV2rayPing = { viewModel.testV2rayRealDelay() },
            onSortV2ray = { viewModel.sortV2rayByTestResults() },
            onDeleteInvalidV2ray = { viewModel.deleteInvalidV2rayConfigs() },
            onUpdateSub = { subKey -> viewModel.updateSubscription(subKey) },
            onTestPing = { viewModel.testPing() },
            onToggleAppSelection = { viewModel.toggleAppSelection(it) },
            onSetPerAppEnabled = { viewModel.setPerAppEnabled(it) },
            onSetBypassMode = { viewModel.setBypassMode(it) },
            onSelectAllApps = { viewModel.selectAllApps() },
            onDeselectAllApps = { viewModel.deselectAllApps() },
            onReconnectWithLatestPolicy = { context -> viewModel.reconnectWithLatestPolicy(context) },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onRetryIpFetch = { viewModel.retryFetchIp() },
            toastFlow = viewModel.toastMessage
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    uptimeFlow: StateFlow<Long>,
    connectionState: ConnectionState,
    networkInfo: NetworkInfo,
    filteredProfiles: List<VpnProfile>,
    activeProfile: VpnProfile?,
    speed: Pair<String, String>,
    rawSpeed: Pair<Long, Long>,
    pingResult: String?,
    selectedProtocol: String,
    installedApps: List<dev.rouzbeh.hybridvpn.data.model.AppInfo>,
    isPerAppEnabled: Boolean,
    isBypassMode: Boolean,
    isRoutingPending: Boolean,
    currentLogs: List<String>,
    onClearLogs: () -> Unit,
    onProtocolSelected: (String) -> Unit,
    onStartVpn: (android.content.Context) -> Unit,
    onStopVpn: (android.content.Context) -> Unit,
    onCancelVpn: (android.content.Context) -> Unit,
    onResetState: () -> Unit,
    onSelectServer: (Int, android.content.Context) -> Unit,
    onDeleteServer: (VpnProfile) -> Unit,
    onDeleteSubGroup: (String) -> Unit,
    onRenameSubGroup: (String, String) -> Unit,
    onTestSinglePing: (VpnProfile) -> Unit,
    onTestAllV2rayPing: () -> Unit,
    onSortV2ray: () -> Unit,
    onDeleteInvalidV2ray: () -> Unit,
    onUpdateSub: (String) -> Unit,
    onAddServer: (String, String, String, String, String) -> Unit,
    onUpdateServer: (VpnProfile) -> Unit,
    onTestPing: () -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onSetPerAppEnabled: (Boolean) -> Unit,
    onSetBypassMode: (Boolean) -> Unit,
    onSelectAllApps: () -> Unit,
    onDeselectAllApps: () -> Unit,
    onReconnectWithLatestPolicy: (android.content.Context) -> Unit,
    onOpenDrawer: () -> Unit,
    onRetryIpFetch: () -> Unit,
    toastFlow: kotlinx.coroutines.flow.Flow<String>
) {
    var showAddServerSheet by remember { mutableStateOf(false) }
    var showServerListSheet by remember { mutableStateOf(false) }
    // REMOVED LOCAL STATE for isPerAppEnabled and isBypassMode
    var showSplitSheet by remember { mutableStateOf(false) }
    var showAppRoutingScreen by remember { mutableStateOf(false) }
    var showLiveLogScreen by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<VpnProfile?>(null) }
    var dashboardToastMessage by remember { mutableStateOf<String?>(null) }
    val onShowToast = { msg: String -> dashboardToastMessage = msg }

    androidx.activity.compose.BackHandler(enabled = showAppRoutingScreen) {
        showAppRoutingScreen = false
    }
    androidx.activity.compose.BackHandler(enabled = showServerListSheet && filteredProfiles.isNotEmpty()) {
        showServerListSheet = false
    }
    androidx.activity.compose.BackHandler(enabled = showLiveLogScreen) {
        showLiveLogScreen = false
    }

    LaunchedEffect(dashboardToastMessage) {
        if (dashboardToastMessage != null) {
            delay(4000)
            dashboardToastMessage = null
        }
    }

    LaunchedEffect(Unit) {
        toastFlow.collect { message ->
            dashboardToastMessage = message
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val splitSheetState =
        androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val currentProtocolDisplay = VpnProtocolDisplay.entries.find { it.title == selectedProtocol }
        ?: VpnProtocolDisplay.OpenConnect

    val currentActiveProfile =
        if (activeProfile?.protocol == selectedProtocol) activeProfile else null

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onStartVpn(context)
        } else {
            onResetState()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Handled automatically by the OS */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val isUiUnlocked = connectionState.isTerminal
    val hasServers = filteredProfiles.isNotEmpty()

    androidx.compose.animation.AnimatedVisibility(
        visible = showAppRoutingScreen,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
    ) {
        AppRoutingScreen(
            apps = installedApps,
            isPerAppEnabled = isPerAppEnabled,
            isBypassMode = isBypassMode,
            isPending = isRoutingPending,
            onReconnect = { onReconnectWithLatestPolicy(context) },
            onBypassModeChange = onSetBypassMode,
            onToggleApp = onToggleAppSelection,
            onNavigateBack = { showAppRoutingScreen = false },
            onDisablePerApp = {
                onSetPerAppEnabled(false)
                showAppRoutingScreen = false
            },
            onSelectAllApps = onSelectAllApps,
            onDeselectAllApps = onDeselectAllApps
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showServerListSheet && hasServers,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
    ) {
        ServerListFullScreen(
            protocol = selectedProtocol,
            servers = filteredProfiles,
            activeProfile = activeProfile,
            connectionState = connectionState,
            onSelect = { profileId -> onSelectServer(profileId, context) },
            onDelete = onDeleteServer,
            onDeleteSubGroup = onDeleteSubGroup,
            onRenameSubGroup = onRenameSubGroup,
            onTestSinglePing = onTestSinglePing,
            onTestAllV2rayPing = onTestAllV2rayPing,
            onSortV2ray = onSortV2ray,
            onDeleteInvalidV2ray = onDeleteInvalidV2ray,
            onUpdateSub = onUpdateSub,
            onEdit = { profile -> editingProfile = profile },
            onNavigateBack = { showServerListSheet = false },
            toastMessage = dashboardToastMessage
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showLiveLogScreen,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
    ) {
        LiveLogFullScreen(
            logs = currentLogs,
            protocol = selectedProtocol,
            onClear = onClearLogs,
            onNavigateBack = { showLiveLogScreen = false }
        )
    }

    if (!showAppRoutingScreen && !(showServerListSheet && hasServers) && !showLiveLogScreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceDark)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp), // Keep bottom safe area
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. TOP HEADER (Replaces the weight-based spacer)
                TopHeader(
                    onStopVpn = { onStopVpn(context) },
                    connectionState = connectionState,
                    hasActiveProfile = activeProfile != null,
                    onLiveLogClick = { showLiveLogScreen = true },
                    onOpenDrawer = onOpenDrawer
                )

                // 2. Network Info Card
                NetworkInfoCard(
                    modifier = Modifier.weight(1f),
                    networkInfo = networkInfo,
                    connectionState = connectionState,
                    onMoreClick = {
                        val intent =
                            Intent(Intent.ACTION_VIEW, "https://browserleaks.com/ip".toUri())
                        context.startActivity(intent)
                    },
                    onRetryClick = onRetryIpFetch
                )

                // 3. Speed Tracker (Only ONE instance, remove duplicates)
                SpeedTrackerRow(
                    downloadSpeed = speed.first,
                    uploadSpeed = speed.second,
                    downloadRaw = rawSpeed.first,
                    uploadRaw = rawSpeed.second,
                    connectionState = connectionState
                )

                // NEW: App Routing / Split Tunneling Button
                val currentMode = if (!isPerAppEnabled) {
                    "All apps use VPN"
                } else if (isBypassMode) {
                    "All apps except those selected use VPN"
                } else {
                    "Only selected apps use VPN"
                }
                RoutingSelectorButton(
                    currentMode = currentMode,
                    isEnabled = isUiUnlocked, // Reuse the same lock logic as protocols
                    onClick = {
                        if (isPerAppEnabled) showAppRoutingScreen = true else showSplitSheet = true
                    }
                )

                // 4. Protocol Selector
                ProtocolSelector(
                    selectedProtocol = currentProtocolDisplay,
                    isEnabled = isUiUnlocked,
                    onProtocolSelected = {
                        onProtocolSelected(it.title)
                    }
                )

                // 5. Server Selector (Using the updated signature with onAddClick)
                ServerSelectorButton(
                    activeProfile = activeProfile,
                    selectedProtocol = currentProtocolDisplay,
                    hasProfiles = filteredProfiles.isNotEmpty(),
                    isEnabled = isUiUnlocked,
                    onClick = { showServerListSheet = true },
                    onAddClick = { if (isUiUnlocked) showAddServerSheet = true }
                )

                // 6. Bottom Grid (4 boxes: Status, Ping, Uptime, Power)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceGlass)
                                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentActiveProfile == null) {
                                Text(
                                    text = "Select Server",
                                    color = Color.Gray,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SfProPlusBlack
                                )
                            } else {
                                when (connectionState) {
                                    ConnectionState.CONNECTED -> Text(
                                        text = "Connected",
                                        color = Color(0xFF00D8E6),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = SfProPlusBlack
                                    )

                                    ConnectionState.CONNECTING -> ShimmerText(
                                        "Connecting",
                                        Color.Gray.copy(alpha = 0.5f),
                                        PrimaryCyan
                                    )

                                    ConnectionState.RECONNECTING -> ShimmerText(
                                        "Reconnecting",
                                        Color.Gray.copy(alpha = 0.5f),
                                        Color(0xFFFFD700)
                                    )

                                    ConnectionState.DISCONNECTING -> ShimmerText(
                                        "Disconnecting",
                                        Color.Gray.copy(alpha = 0.5f),
                                        Color(0xFFFF5252)
                                    )

                                    ConnectionState.CANCELING, ConnectionState.CANCELLED -> ShimmerText(
                                        "Canceling",
                                        Color.Gray.copy(alpha = 0.5f),
                                        Color.White
                                    )

                                    ConnectionState.ERROR, ConnectionState.FAILED -> Text(
                                        text = "Failed",
                                        color = Color(0xFFFF5252),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = SfProPlusBlack
                                    )

                                    else -> {
                                        Text(
                                            text = "Ready",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = SfProPlusBlack
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceGlass)
                                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                                .clickable { onTestPing() },
                            contentAlignment = Alignment.Center
                        ) {
                            val isTesting = remember(pingResult, connectionState) {
                                pingResult?.contains("Testing") == true || (connectionState == ConnectionState.CONNECTED && pingResult == null)
                            }
                            val pValue = remember(pingResult) {
                                pingResult?.filter { it.isDigit() }?.toIntOrNull() ?: -1
                            }
                            val pText =
                                remember(pValue) { if (pValue != -1) "${pValue}ms" else "Test Ping" }
                            val pColor = Color(0xFF00D8E6) // PrimaryCyan fixed color

                            if (isTesting) {
                                ShimmerText(
                                    "Testing",
                                    Color.Gray.copy(alpha = 0.5f),
                                    PrimaryCyan,
                                    16.sp
                                )
                            } else {
                                Text(
                                    text = pText,
                                    color = pColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    fontFamily = SfProPlusBlack
                                )
                            }
                        }

                        val uptimeSecs by uptimeFlow.collectAsState()
                        val formattedTime = remember(uptimeSecs, connectionState) {
                            if (connectionState != ConnectionState.CONNECTED) {
                                buildAnnotatedString { append("00:02:00".replace(Regex("\\d"), "0")) } // "00:00:00" effectively, but matching length
                            } else if (uptimeSecs == -1L) {
                                buildAnnotatedString { append("Syncing") }
                            } else {
                                val h = uptimeSecs / 3600
                                val m = (uptimeSecs % 3600) / 60
                                val s = uptimeSecs % 60
                                val activeColor = Color(0xFF00D8E6)

                                fun androidx.compose.ui.text.AnnotatedString.Builder.appendTimeUnit(
                                    value: Long,
                                    forceColorAll: Boolean
                                ) {
                                    val str = String.format(java.util.Locale.US, "%02d", value)
                                    if (forceColorAll) {
                                        withStyle(SpanStyle(color = activeColor)) { append(str) }
                                    } else if (value == 0L) {
                                        withStyle(SpanStyle(color = Color.White)) { append(str) }
                                    } else if (value < 10L) {
                                        withStyle(SpanStyle(color = Color.White)) { append(str[0].toString()) }
                                        withStyle(SpanStyle(color = activeColor)) { append(str[1].toString()) }
                                    } else {
                                        withStyle(SpanStyle(color = activeColor)) { append(str) }
                                    }
                                }

                                buildAnnotatedString {
                                    appendTimeUnit(h, forceColorAll = false)
                                    withStyle(SpanStyle(color = Color.White)) { append(":") }
                                    appendTimeUnit(m, forceColorAll = uptimeSecs >= 3600)
                                    withStyle(SpanStyle(color = Color.White)) { append(":") }
                                    appendTimeUnit(s, forceColorAll = uptimeSecs >= 60)
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceGlass)
                                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uptimeSecs == -1L) {
                                ShimmerText(
                                    "Syncing",
                                    Color.Gray.copy(alpha = 0.5f),
                                    PrimaryCyan,
                                    16.sp
                                )
                            } else {
                                Text(
                                    text = formattedTime,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SfProPlusBlack
                                )
                            }
                        }
                    }

                    val isConnected = connectionState == ConnectionState.CONNECTED
                    val isConnecting =
                        connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING || connectionState == ConnectionState.DISCONNECTING || connectionState == ConnectionState.CANCELING
                    val selectionLocked by VpnStateManager.selectionLocked.collectAsStateWithLifecycle()
                    val isConnectEnabled =
                        (currentActiveProfile != null && !isConnecting && !selectionLocked) || isConnected || isConnecting

                    val btnColor = when (connectionState) {
                        ConnectionState.DISCONNECTING -> Color(0xFFFF5252)
                        ConnectionState.CANCELING -> Color.White
                        else -> PrimaryCyan
                    }

                    val pulseAlpha by rememberInfiniteTransition(label = "btn_pulse")
                        .animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                            label = "pulse"
                        )

                    val animatedBtnBgColor by animateColorAsState(
                        targetValue = if (isConnected || connectionState == ConnectionState.DISCONNECTING) btnColor.copy(
                            alpha = 0.15f
                        ) else SurfaceGlass,
                        animationSpec = tween(durationMillis = 300),
                        label = "btnBgColor"
                    )
                    val animatedBorderColor by animateColorAsState(
                        targetValue = if (isConnected || connectionState == ConnectionState.DISCONNECTING) btnColor else BorderGlass,
                        animationSpec = tween(durationMillis = 300),
                        label = "borderColor"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(animatedBtnBgColor)
                            .border(1.dp, animatedBorderColor, RoundedCornerShape(24.dp))
                            .clickable(enabled = isConnectEnabled) {
                                if (isConnected || isConnecting) {
                                    if (connectionState == ConnectionState.CONNECTING) onCancelVpn(
                                        context
                                    ) else onStopVpn(context)
                                } else {
                                    if (currentActiveProfile == null) return@clickable
                                    val intent = VpnService.prepare(context)
                                    if (intent != null) vpnPermissionLauncher.launch(intent) else onStartVpn(
                                        context
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Power",
                            tint = if (isConnected) btnColor
                            else if (connectionState == ConnectionState.DISCONNECTING || isConnecting) btnColor.copy(
                                alpha = pulseAlpha
                            )
                            else Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (showAddServerSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showAddServerSheet = false },
                        sheetState = sheetState,
                        containerColor = SurfaceMenu,
                        dragHandle = null
                    ) {
                        AddServerSheetContent(
                            protocol = currentProtocolDisplay,
                            servers = filteredProfiles,
                            onAdd = onAddServer,
                            onUpdate = onUpdateServer,
                            onClose = { showAddServerSheet = false },
                            onShowToast = onShowToast
                        )
                    }
                }

                if (showServerListSheet && !hasServers) {
                    ModalBottomSheet(
                        onDismissRequest = { showServerListSheet = false },
                        sheetState = sheetState,
                        containerColor = SurfaceMenu,
                        dragHandle = null
                    ) {
                        ServerListSheetContent(
                            profiles = filteredProfiles,
                            activeProfile = activeProfile,
                            connectionState = connectionState,
                            onSelect = { profileId ->
                                onSelectServer(profileId, context)
                                showServerListSheet = false
                            },
                            onDelete = { profile -> onDeleteServer(profile) },
                            onEdit = { profile ->
                                editingProfile = profile
                                showServerListSheet = false
                            },
                            onTestSinglePing = onTestSinglePing
                        )
                    }
                }

                if (showSplitSheet) {
                    androidx.compose.material3.ModalBottomSheet(
                        onDismissRequest = { showSplitSheet = false },
                        sheetState = splitSheetState,
                        containerColor = SurfaceMenu,
                        contentWindowInsets = {
                            androidx.compose.foundation.layout.WindowInsets(
                                0,
                                0,
                                0,
                                0
                            )
                        }, // Kills the extra navigation bar gap
                        dragHandle = null
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SplitTunnelingSheetContent(
                                isPerAppEnabled = isPerAppEnabled,
                                onEnable = {
                                    onSetPerAppEnabled(true)
                                    showSplitSheet = false
                                    showAppRoutingScreen = true
                                }
                            )
                            // Custom Toast Overlay inside the sheet
                            CustomGlassToast(
                                message = dashboardToastMessage,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingProfile != null) {
        ModalBottomSheet(
            onDismissRequest = { editingProfile = null },
            sheetState = sheetState,
            containerColor = SurfaceMenu,
            dragHandle = null
        ) {
            AddServerSheetContent(
                protocol = currentProtocolDisplay,
                servers = filteredProfiles,
                editingProfile = editingProfile,
                onAdd = onAddServer,
                onUpdate = onUpdateServer,
                onClose = { editingProfile = null },
                onShowToast = onShowToast
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
fun DashboardPreview() {
        DashboardContent(
            uptimeFlow = MutableStateFlow(0L),
            connectionState = ConnectionState.DISCONNECTED,
            networkInfo = NetworkInfo("1.2.3.4", "Preview Country", "us"),
            filteredProfiles = emptyList(),
            activeProfile = null,
            speed = Pair("0 B/s", "0 B/s"),
            rawSpeed = Pair(0L, 0L),
            pingResult = null,
            selectedProtocol = "OpenConnect",
            onProtocolSelected = {},
            onStartVpn = {},
            onStopVpn = {},
            onResetState = {},
            onSelectServer = { _, _ -> },
            onDeleteServer = {},
            onDeleteSubGroup = {},
            onRenameSubGroup = { _, _ -> },
            onTestSinglePing = {},
            onTestAllV2rayPing = {},
            onSortV2ray = {},
            onDeleteInvalidV2ray = {},
            onUpdateSub = {},
            onAddServer = { _, _, _, _, _ -> },
            onUpdateServer = {},
            onTestPing = {},
            onRetryIpFetch = {},
            onCancelVpn = {},
            installedApps = emptyList(),
            onToggleAppSelection = {},
            isPerAppEnabled = false,
            isBypassMode = false,
            isRoutingPending = false,
            currentLogs = emptyList(),
            onClearLogs = {},
            onSetPerAppEnabled = {},
            onSetBypassMode = {},
            onSelectAllApps = {},
            onDeselectAllApps = {},
            onReconnectWithLatestPolicy = {},
            onOpenDrawer = {},
            toastFlow = kotlinx.coroutines.flow.emptyFlow()
        )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
fun ServerItemCardV2RayPreview() {
    dev.rouzbeh.hybridvpn.core.designsystem.OpenNestTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ServerItemCard(
                profile = VpnProfile(
                    name = "🇺🇸 VLESS TCP TLS",
                    protocol = "v2rayNG",
                    serverAddress = "vless://uuid@host:port?type=tcp&security=tls#remark"
                ),
                isSelected = false,
                connectionState = ConnectionState.DISCONNECTED,
                onClick = {}
            )
            ServerItemCard(
                profile = VpnProfile(
                    name = "🇩🇪 Trojan WS",
                    protocol = "v2rayNG",
                    serverAddress = "trojan://password@host:port?type=ws&security=none#remark"
                ),
                isSelected = false,
                connectionState = ConnectionState.DISCONNECTED,
                onClick = {}
            )
            // Note: VMess preview might fail if Base64 decoding or JSON parsing depends on Android runtime
            // but we can try with a simple mock or just check if it compiles.
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
fun SplitTunnelingPreview() {
    dev.rouzbeh.hybridvpn.core.designsystem.OpenNestTheme {
        SplitTunnelingSheetContent(isPerAppEnabled = true, onEnable = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
fun ServerSelectorEmojiPreview() {
    dev.rouzbeh.hybridvpn.core.designsystem.OpenNestTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ServerSelectorButton(
                activeProfile = VpnProfile(
                    name = "🇺🇸 US Server",
                    protocol = "OpenVPN",
                    serverAddress = "us.server.com"
                ),
                selectedProtocol = VpnProtocolDisplay.OpenVPN,
                hasProfiles = true,
                isEnabled = true,
                onClick = {},
                onAddClick = {}
            )
        }
    }
}

@Composable
fun ServerSelectorButton(
    modifier: Modifier = Modifier,
    activeProfile: VpnProfile?,
    selectedProtocol: VpnProtocolDisplay,
    hasProfiles: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    onAddClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val flagRegex = remember { Regex("[\\uD83C\\uDDE6-\\uD83C\\uDDFF]{2}") }
    val serverText = when {
        activeProfile != null && activeProfile.protocol == selectedProtocol.title -> activeProfile.name.replace(
            flagRegex,
            ""
        ).trim()

        !hasProfiles -> "No ${if (selectedProtocol.title == "OpenConnect") "AnyConnect" else selectedProtocol.title} Server"
        else -> "Select Server"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Server List Box
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlass)
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "Server List",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = SfProPlusBlack
                )
                Text(
                    text = serverText,
                    color = if (activeProfile != null && activeProfile.protocol == selectedProtocol.title) PrimaryCyan else Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    fontFamily = SfProPlusBlack
                )
            }
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = "Server",
                tint = PrimaryCyan,
                modifier = Modifier.size(24.dp)
            )
        }

        // Right: Add (+) Button Box
        Box(
            modifier = Modifier
                .size(64.dp)
                .alpha(if (isEnabled) 1f else 0.5f)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlass)
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                .clickable(enabled = isEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAddClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Server",
                tint = if (isEnabled) PrimaryCyan else Color.Gray.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun ServerListSheetContent(
    profiles: List<VpnProfile>,
    activeProfile: VpnProfile?,
    connectionState: ConnectionState,
    onSelect: (Int) -> Unit,
    onDelete: (VpnProfile) -> Unit,
    onEdit: (VpnProfile) -> Unit,
    onTestSinglePing: (VpnProfile) -> Unit = {}
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 48.dp, top = 8.dp)) {
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
            text = "Saved Servers", color = TextPrimary, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp),
            fontFamily = SfProPlusBlack
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (profiles.isEmpty()) {
            Text(
                text = "No servers found. Tap the [+] button to add a new server.",
                color = TextSecondary, modifier = Modifier.padding(horizontal = 24.dp),
                fontFamily = SfProPlusBlack
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles) { profile ->
                    val isSelected = profile.id == activeProfile?.id
                    ServerItemCard(
                        profile = profile, isSelected = isSelected,
                        connectionState = connectionState,
                        onClick = { onSelect(profile.id) },
                        onDelete = { onDelete(profile) },
                        onEdit = { onEdit(profile) },
                        onPingClick = { onTestSinglePing(profile) }
                    )
                }
            }
        }
    }
}

@Composable
fun ServerItemCard(
    profile: VpnProfile,
    isSelected: Boolean,
    connectionState: ConnectionState,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onPingClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val bgColor = if (isSelected) PrimaryCyan.copy(alpha = 0.1f) else SurfaceGlass

    val protocolIcon = when (profile.protocol) {
        "OpenConnect" -> R.drawable.ic_openconnect
        "v2rayNG" -> R.drawable.ic_v2rayng // Ensure this exists
        else -> R.drawable.ic_openvpn
    }

    val isConnectedOrConnecting =
        connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING

    val flagRegex = remember { Regex("[\\uD83C\\uDDE6-\\uD83C\\uDDFF]{2}") }
    val flags = remember(profile.name) { flagRegex.findAll(profile.name).map { it.value }.toList() }
    val cleanName = remember(profile.name) { profile.name.replace(flagRegex, "").trim() }

    val countryCodes = remember(flags) {
        flags.map { emoji ->
            try {
                val cp1 = emoji.codePointAt(0)
                val cp2 = emoji.codePointAt(emoji.offsetByCodePoints(0, 1))
                val c1 = (cp1 - 0x1F1E6 + 97).toChar()
                val c2 = (cp2 - 0x1F1E6 + 97).toChar()
                "$c1$c2"
            } catch (e: Exception) {
                "un"
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(
                1.dp,
                if (isSelected) PrimaryCyan.copy(alpha = 0.5f) else BorderGlass,
                RoundedCornerShape(20.dp)
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
    ) {
        var isPinging by remember { mutableStateOf(false) }
        LaunchedEffect(profile.ping) {
            isPinging = false
        }

        val hasFlag = countryCodes.isNotEmpty()
        val boxModifier = if (hasFlag) {
            Modifier
                .size(width = 48.dp, height = 32.dp)
                .clip(RoundedCornerShape(6.dp)) // Clean 3:2 graphic rectangle for flags
        } else {
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)) // Squarcle for pure protocol icons
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = boxModifier.background(
                if (isSelected) PrimaryCyan.copy(alpha = 0.2f) else BackgroundDark.copy(
                    alpha = 0.3f
                )
            )
        ) {
            if (!hasFlag) {
                Icon(
                    painter = painterResource(id = protocolIcon),
                    contentDescription = null,
                    tint = if (isSelected) PrimaryCyan else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            } else if (countryCodes.size == 1) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data("https://flagcdn.com/w160/${countryCodes[0]}.png")
                        .crossfade(true).build(),
                    contentDescription = "Flag",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(androidx.compose.foundation.shape.GenericShape { size, _ ->
                                moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(
                                0f,
                                size.height
                            ); close()
                            }), contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                .data("https://flagcdn.com/w160/${countryCodes[1]}.png")
                                .crossfade(true).build(),
                            contentDescription = "Flag 2",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(androidx.compose.foundation.shape.GenericShape { size, _ ->
                                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(
                                0f,
                                size.height
                            ); close()
                            }), contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                .data("https://flagcdn.com/w160/${countryCodes[0]}.png")
                                .crossfade(true).build(),
                            contentDescription = "Flag 1",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = BackgroundDark,
                            start = androidx.compose.ui.geometry.Offset(0f, size.height),
                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = 5f
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cleanName,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                fontFamily = SfProPlusBlack
            )
            val subTitle = remember(profile.serverAddress, profile.protocol) {
                when (profile.protocol) {
                    "v2rayNG" -> {
                        try {
                            val config = profile.serverAddress
                            val protocol = config.substringBefore("://").uppercase()
                            when (protocol.lowercase()) {
                                "vless", "trojan" -> {
                                    val type = Regex("type=([^&#]+)").find(config)?.groupValues?.get(1)?.uppercase() ?: "TCP"
                                    val security = Regex("security=([^&#]+)").find(config)?.groupValues?.get(1)?.uppercase()
                                    val secString = if (!security.isNullOrBlank() && security != "NONE") " / $security" else ""
                                    "$protocol / $type$secString"
                                }
                                "vmess" -> {
                                    val b64 = config.substringAfter("vmess://")
                                    val decoded = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                                    val json = org.json.JSONObject(decoded)
                                    val net = json.optString("net", "TCP").uppercase()
                                    val tls = json.optString("tls", "").uppercase()
                                    val tlsString = if (tls.isNotBlank() && tls != "NONE") " / $tls" else ""
                                    "VMESS / $net$tlsString"
                                }
                                else -> protocol
                            }
                        } catch (e: Exception) {
                            profile.serverAddress.substringBefore("://").uppercase()
                        }
                    }
                    "OpenVPN" -> {
                        val match = Regex("^remote\\s+([a-zA-Z0-9.-]+)", RegexOption.MULTILINE).find(profile.serverAddress)
                        match?.groups?.get(1)?.value ?: "OpenVPN Config"
                    }
                    else -> profile.serverAddress
                }
            }
            Text(
                text = subTitle,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (profile.protocol == "v2rayNG") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .clickable { isPinging = true; onPingClick() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isPinging,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                                    fadeOut(animationSpec = tween(90))
                        },
                        label = "ping_animation"
                    ) { loading ->
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryCyan
                            )
                        } else {
                            val pText = if (profile.ping == -1L) "-1" else if (profile.ping == 0L) "Test" else "${profile.ping} ms"
                            val pColor = if (profile.ping == -1L) Color(0xFFFF5252) else if (profile.ping == 0L) Color.Gray else PrimaryCyan
                            Text(text = pText, color = pColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = SfProPlusBlack)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (onEdit != null) {
                IconButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onEdit() },
                    enabled = !isConnectedOrConnecting,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = if (isConnectedOrConnecting) Color.DarkGray else TextSecondary.copy(
                            alpha = 0.6f
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (onDelete != null) {
                IconButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDelete() },
                    enabled = !(isSelected && isConnectedOrConnecting),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = if (isSelected && isConnectedOrConnecting) Color.DarkGray else Color(
                            0xFFFF3B30
                        ).copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedTrackerRow(
    downloadSpeed: String,
    uploadSpeed: String,
    downloadRaw: Long,
    uploadRaw: Long,
    connectionState: ConnectionState
) {
    Row(
        modifier = Modifier.fillMaxWidth(), // Removed the 8.dp horizontal padding
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SpeedItem(
            label = "DownLink",
            speed = downloadSpeed,
            rawSpeed = downloadRaw,
            icon = Icons.Default.FileDownload,
            color = PrimaryCyan,
            connectionState = connectionState,
            modifier = Modifier.weight(1f)
        )
        SpeedItem(
            label = "UpLink",
            speed = uploadSpeed,
            rawSpeed = uploadRaw,
            icon = Icons.Default.FileUpload,
            color = PrimaryCyan,
            connectionState = connectionState,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SpeedItem(
    label: String,
    speed: String,
    rawSpeed: Long,
    icon: ImageVector,
    color: Color,
    connectionState: ConnectionState,
    modifier: Modifier
) {
    val rxTxBorderColor =
        if (connectionState == ConnectionState.CONNECTED) PrimaryCyan else BorderGlass

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlass)
            .border(1.dp, rxTxBorderColor, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = SfProPlusBlack
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            SpeedText(speedBytesLong = rawSpeed, tint = TextPrimary)
        }
    }
}

@Composable
fun SpeedText(speedBytesLong: Long, tint: Color) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speedBytesLong.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "speed_counting"
    )

    val speedText = remember(animatedSpeed) { formatBytesToSpeed(animatedSpeed.toLong()) }

    Text(
        text = speedText,
        color = tint,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        fontFamily = SfProPlusBlack
    )
}

private fun formatBytesToSpeed(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        java.util.Locale.US,
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
}

@Composable
fun ProtocolSelector(
    selectedProtocol: VpnProtocolDisplay,
    isEnabled: Boolean,
    onProtocolSelected: (VpnProtocolDisplay) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isEnabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VpnProtocolDisplay.entries.forEach { protocol ->
            val isSelected = protocol == selectedProtocol
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) PrimaryCyan else SurfaceGlass,
                label = "bgColor"
            )
            val iconColor by animateColorAsState(
                targetValue = if (isSelected) BackgroundDark else TextSecondary,
                label = "iconColor"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) BackgroundDark else TextSecondary,
                label = "textColor"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) PrimaryCyan else BorderGlass,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable(enabled = isEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onProtocolSelected(protocol)
                    }
                    .padding(vertical = 12.dp, horizontal = 4.dp)
            ) {
                Icon(
                    painter = painterResource(id = protocol.iconResId),
                    contentDescription = protocol.title,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (protocol.title == "OpenConnect") "AnyConnect" else protocol.title,
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SfProPlusBlack
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerSheetContent(
    protocol: VpnProtocolDisplay,
    servers: List<VpnProfile>,
    editingProfile: VpnProfile? = null,
    onAdd: (String, String, String, String, String) -> Unit,
    onUpdate: (VpnProfile) -> Unit,
    onClose: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val context = LocalContext.current
    val currentProtocol = editingProfile?.protocol?.let { pName ->
        VpnProtocolDisplay.entries.find { it.title == pName }
    } ?: protocol

    var name by remember { mutableStateOf(editingProfile?.name ?: "") }
    var serverAddress by remember { mutableStateOf(editingProfile?.serverAddress ?: "") }
    var username by remember { mutableStateOf(editingProfile?.username ?: "") }
    var password by remember { mutableStateOf(editingProfile?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isOvpnFileUploaded by remember { mutableStateOf(false) }

    val barcodeScanner = remember { com.google.mlkit.vision.barcode.BarcodeScanning.getClient() }
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val image = com.google.mlkit.vision.common.InputImage.fromFilePath(context, it)
                barcodeScanner.process(image).addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { value -> serverAddress = value }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

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
        val displayTitle = if (currentProtocol.title == "OpenConnect") "AnyConnect" else currentProtocol.title
        Text(
            text = if (editingProfile == null) "New $displayTitle Profile" else "Edit Profile",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SfProPlusBlack
        )
        Spacer(modifier = Modifier.height(24.dp))

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        serverAddress = inputStream.bufferedReader().use { it.readText() }
                        isOvpnFileUploaded = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        when (currentProtocol) {
            VpnProtocolDisplay.OpenConnect -> {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = {
                        Text(
                            "Profile Name",
                            color = TextSecondary,
                            fontFamily = SfProPlusBlack
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = serverAddress, onValueChange = { serverAddress = it },
                    label = {
                        Text(
                            "Server Address",
                            color = TextSecondary,
                            fontFamily = SfProPlusBlack
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = username, onValueChange = { username = it.replace(" ", "") },
                    label = {
                        Text(
                            "Username",
                            color = TextSecondary,
                            fontFamily = SfProPlusBlack
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it.replace(" ", "") },
                    label = {
                        Text(
                            "Password",
                            color = TextSecondary,
                            fontFamily = SfProPlusBlack
                        )
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else AsteriskVisualTransformation,
                    trailingIcon = {
                        val image =
                            if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = image,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }

            VpnProtocolDisplay.OpenVPN -> {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = {
                        Text(
                            "Profile Name",
                            color = TextSecondary,
                            fontFamily = SfProPlusBlack
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = if (editingProfile != null) "Config Saved (.ovpn)" else if (isOvpnFileUploaded) "File uploaded successfully" else serverAddress,
                    onValueChange = { if (!isOvpnFileUploaded && editingProfile == null) serverAddress = it },
                    readOnly = isOvpnFileUploaded || editingProfile != null,
                    label = {
                        Text(
                            "Server Address",
                            color = TextSecondary,
                            fontFamily = SfProPlusBlack
                        )
                    },
                    trailingIcon = if (editingProfile != null) null else {
                        {
                            IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                Icon(
                                    Icons.Default.FileUpload,
                                    contentDescription = "Upload .ovpn",
                                    tint = PrimaryCyan
                                )
                            }
                        }
                    },
                    textStyle = TextStyle(color = if (isOvpnFileUploaded || editingProfile != null) PrimaryCyan else TextPrimary),
                    placeholder = {
                        Text(
                            text = "Enter Manually or Upload .ovpn File",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp,
                            color = Color.Gray,
                            fontFamily = SfProPlusBlack
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = username, onValueChange = { username = it.replace(" ", "") },
                    label = {
                        Text(
                            "Username",
                            color = TextSecondary,
                            fontFamily = SfProPlusBlack
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it.replace(" ", "") },
                    label = {
                        Text(
                            "Password",
                            color = TextSecondary,
                            fontFamily = SfProPlusBlack
                        )
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else AsteriskVisualTransformation,
                    trailingIcon = {
                        val image =
                            if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = image,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }

            VpnProtocolDisplay.V2rayNG -> {
                if (editingProfile != null) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Profile Name", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = serverAddress, onValueChange = { serverAddress = it },
                        label = {
                            Text(
                                "Config String or Subscription Link",
                                color = TextSecondary,
                                fontFamily = SfProPlusBlack
                            )
                        },
                        placeholder = {
                            Text(
                                "vmess://, vless://, http://...",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        maxLines = 5
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.getText()?.text?.let { serverAddress = it }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = PrimaryCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clipboard", color = Color.White)
                        }
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.QrCode,
                                contentDescription = "QR Code",
                                tint = PrimaryCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("QR Code", color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        val isFormValid = if (currentProtocol == VpnProtocolDisplay.V2rayNG) {
            serverAddress.isNotBlank()
        } else {
            name.isNotBlank() && serverAddress.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        }
        val hasChanges = editingProfile == null || (
                name != editingProfile.name ||
                        serverAddress != editingProfile.serverAddress ||
                        username != editingProfile.username ||
                        password != editingProfile.password ||
                        currentProtocol.title != editingProfile.protocol
                )
        val isButtonEnabled = isFormValid && hasChanges

        Button(
            onClick = {
                if (isButtonEnabled) {
                    if (editingProfile == null) {
                        val isDuplicate = servers.any {
                            when (currentProtocol) {
                                VpnProtocolDisplay.OpenConnect -> it.protocol == "OpenConnect" && it.serverAddress == serverAddress
                                VpnProtocolDisplay.OpenVPN -> it.protocol == "OpenVPN" && it.serverAddress == serverAddress
                                VpnProtocolDisplay.V2rayNG -> {
                                    val input = serverAddress.trim()
                                    if (input.startsWith("http://") || input.startsWith("https://")) {
                                        it.username == "sub:$input"
                                    } else {
                                        it.protocol == "v2rayNG" && it.serverAddress == serverAddress
                                    }
                                }
                            }
                        }
                        if (isDuplicate) {
                            onShowToast("This configuration already exists!")
                            return@Button
                        }
                        val finalPassword = if (currentProtocol == VpnProtocolDisplay.V2rayNG && (serverAddress.startsWith("http://") || serverAddress.startsWith("https://"))) serverAddress else password
                        onAdd(name, serverAddress, username, finalPassword, currentProtocol.title)
                    } else {
                        onUpdate(
                            editingProfile.copy(
                                name = name,
                                serverAddress = serverAddress,
                                username = username,
                                password = password,
                                protocol = currentProtocol.title
                            )
                        )
                    }
                    onClose()
                }
            },
            enabled = isButtonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                disabledContainerColor = SurfaceGlass
            ),
            shape = RoundedCornerShape(16.dp),
            // NEW: Retain the glass border and dim text when disabled
            border = if (isButtonEnabled) null else androidx.compose.foundation.BorderStroke(
                1.dp,
                BorderGlass
            )
        ) {
            Text(
                text = if (editingProfile == null) "Add" else "Apply",
                color = if (isButtonEnabled) BackgroundDark else TextSecondary, // TextSecondary is gray
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SfProPlusBlack
            )
        }
    }
}

@Composable
fun ShimmerText(text: String, baseColor: Color, shimmerColor: Color, fontSize: TextUnit = 18.sp) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_anim"
    )

    val brush = Brush.linearGradient(
        colors = listOf(baseColor, shimmerColor, baseColor),
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim)
    )

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        fontFamily = SfProPlusBlack,
        style = TextStyle(brush = brush)
    )
}

@Composable
fun SmoothTypingDots(textStyle: androidx.compose.ui.text.TextStyle) {
    val infiniteTransition =
        androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(
                500,
                delayMillis = 0
            ), androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "d1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(
                500,
                delayMillis = 150
            ), androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "d2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(
                500,
                delayMillis = 300
            ), androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "d3"
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            ".",
            color = Color.White,
            fontSize = 14.sp,
            style = textStyle,
            modifier = Modifier.alpha(alpha1)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            ".",
            color = Color.White,
            fontSize = 14.sp,
            style = textStyle,
            modifier = Modifier.alpha(alpha2)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            ".",
            color = Color.White,
            fontSize = 14.sp,
            style = textStyle,
            modifier = Modifier.alpha(alpha3)
        )
    }
}

@Composable
fun NetworkInfoCard(
    modifier: Modifier = Modifier,
    networkInfo: NetworkInfo,
    connectionState: ConnectionState,
    onMoreClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    val contrastMatrix = remember {
        ColorMatrix().apply {
            setToScale(1.15f, 1.15f, 1.15f, 1f)
        }
    }

    var ipVisible by remember(connectionState) { mutableStateOf(connectionState == ConnectionState.CONNECTED) }

    val isTransitioning =
        connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING || connectionState == ConnectionState.DISCONNECTING || connectionState == ConnectionState.CANCELING
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isUnresolved = isConnected && (networkInfo.ip == "0.0.0.0" || networkInfo.ip == "127.0.0.1" || networkInfo.location == "Detecting...")
    val showLoading = isTransitioning || isUnresolved

    val customFont =
        FontFamily(androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sf_proplus_black))
    val textShadow = TextStyle(
        fontFamily = customFont,
        shadow = Shadow(color = Color.Black, offset = Offset(2f, 3f), blurRadius = 4f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceGlass)
            .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val safeFlagCode = networkInfo.flagCode.lowercase()

            if (safeFlagCode != "un" && safeFlagCode.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("https://flagcdn.com/w640/$safeFlagCode.png")
                        .crossfade(true)
                        .build(),
                    contentDescription = "Flag Background",
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(contrastMatrix),
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.9f)
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to SurfaceDark.copy(alpha = 0.8f),
                            0.35f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1.0f to SurfaceDark.copy(alpha = 0.9f)
                        )
                    )
            )

            Box(modifier = Modifier
                .matchParentSize()
                .padding(24.dp)) {
                // Top Left: IP Address
                Row(
                    modifier = Modifier.align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isConnected) "Virtual IP: " else "Public IP: ",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        style = textShadow
                    )
                    if (showLoading) {
                        SmoothTypingDots(textStyle = textShadow)
                    } else {
                        val displayIp = if (ipVisible) networkInfo.ip else networkInfo.ip.replace(
                            Regex("\\d"),
                            "*"
                        )
                        Text(
                            text = displayIp,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            style = textShadow
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (ipVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle IP",
                            tint = PrimaryCyan,
                            modifier = Modifier
                                .size(20.dp)
                                .bounceClick { ipVisible = !ipVisible }
                        )
                    }
                }

                // Bottom Left: Location
                Row(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Location: ",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        style = textShadow
                    )
                    if (showLoading) {
                        SmoothTypingDots(textStyle = textShadow)
                    } else {
                        Text(
                            text = networkInfo.location,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            style = textShadow
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.Info,
                            contentDescription = "Info",
                            tint = PrimaryCyan,
                            modifier = Modifier
                                .size(20.dp)
                                .bounceClick { onMoreClick() }
                        )
                    }
                }

                // NEW: Top Right: Retry Button (Only on Error)
                val hasError = networkInfo.ip == "Error" || networkInfo.location == "Failed to fetch IP" || networkInfo.location.contains("Error")
                if (hasError) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry Fetch IP",
                        tint = PrimaryCyan,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .bounceClick { onRetryClick() }
                    )
                }
            }
        }
    }
}

@Composable
fun RoutingSelectorButton(
    modifier: Modifier = Modifier,
    currentMode: String = "Global - All Apps",
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .alpha(if (isEnabled) 1f else 0.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlass)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .clickable(enabled = isEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Split Tunneling",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = SfProPlusBlack
            )
            Text(
                text = currentMode,
                color = PrimaryCyan,
                fontSize = if (currentMode.contains("except")) 12.5.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                fontFamily = SfProPlusBlack
            )
        }
        Icon(
            imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.AltRoute,
            contentDescription = "Split Tunneling",
            tint = PrimaryCyan,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SplitTunnelingSheetContent(isPerAppEnabled: Boolean, onEnable: () -> Unit) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 48.dp)) {
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
            "Split Tunneling Settings", color = Color.White, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlass)
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Per-App",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            androidx.compose.material3.Switch(
                checked = isPerAppEnabled,
                onCheckedChange = { if (it) onEnable() },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SurfaceDark
                )
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AppRoutingScreen(
    apps: List<dev.rouzbeh.hybridvpn.data.model.AppInfo>,
    isPerAppEnabled: Boolean,
    isBypassMode: Boolean,
    isPending: Boolean,
    onReconnect: () -> Unit,
    onBypassModeChange: (Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onDisablePerApp: () -> Unit,
    onSelectAllApps: () -> Unit,
    onDeselectAllApps: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSystemApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val filteredApps = remember(apps, showSystemApps, searchQuery) {
        apps.filter { app ->
            (showSystemApps || !app.isSystemApp) &&
                    (searchQuery.isEmpty() || app.appName.contains(
                        searchQuery,
                        ignoreCase = true
                    ) || app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(4000)
            toastMessage = null
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(SurfaceMenu)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ... (top bar code stays same)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .bounceClick { onNavigateBack() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Per-App Settings",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))

                // Three-dots menu (Always visible when ON)
                Box {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .bounceClick { showMenu = true }
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(SurfaceMenu)
                            .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    if (showSystemApps) "Hide System Apps" else "Show System Apps",
                                    color = Color.White
                                )
                            },
                            onClick = { showSystemApps = !showSystemApps; showMenu = false }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Select All", color = Color.White) },
                            onClick = { onSelectAllApps(); showMenu = false }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Deselect All", color = Color.White) },
                            onClick = { onDeselectAllApps(); showMenu = false }
                        )
                    }
                }
            }

            // Pending-changes banner: settings changed since the active tunnel was
            // established with its routing snapshot. Never applied silently.
            if (isPending) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFD700).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Routing changes apply after reconnect",
                        color = Color(0xFFFFD700),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "RECONNECT",
                        color = PrimaryCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onReconnect() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // ... (toggles container code stays same)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlass)
                    .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                // Main Enable Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Per-App",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Info,
                                contentDescription = "Info",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .bounceClick {
                                        toastMessage =
                                            "Disable this option to have\nall apps use the VPN."
                                    }
                            )
                        }
                    }
                    androidx.compose.material3.Switch(
                        checked = isPerAppEnabled,
                        onCheckedChange = { if (!it) onDisablePerApp() },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = PrimaryCyan,
                            checkedTrackColor = PrimaryCyan.copy(alpha = 0.5f)
                        )
                    )
                }

                androidx.compose.material3.HorizontalDivider(
                    color = BorderGlass,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Bypass Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Bypass Mode",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Info,
                                contentDescription = "Info",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .bounceClick {
                                        toastMessage =
                                            if (isBypassMode) "Disable this option to only allow apps you select to use the VPN." else "Enable this option to prevent apps you select from using the VPN and allow other apps you don't select to use the VPN."
                                    }
                            )
                        }
                    }
                    androidx.compose.material3.Switch(
                        checked = isBypassMode,
                        onCheckedChange = { onBypassModeChange(it) },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = PrimaryCyan,
                            checkedTrackColor = PrimaryCyan.copy(alpha = 0.5f)
                        )
                    )
                }

                androidx.compose.material3.HorizontalDivider(
                    color = BorderGlass,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Custom Search Bar
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(PrimaryCyan),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceDark.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.Search,
                                contentDescription = "Search",
                                tint = PrimaryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) Text(
                                    "Search Apps",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App List
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceGlass)
                            .clickable { onToggleApp(app.packageName) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = app.icon,
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(SurfaceDark)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                app.appName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                app.packageName,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        androidx.compose.material3.Checkbox(
                            checked = app.isSelected,
                            onCheckedChange = { onToggleApp(app.packageName) },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                checkedColor = PrimaryCyan,
                                checkmarkColor = Color.Black,
                                uncheckedColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }
        CustomGlassToast(message = toastMessage, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun TopHeader(
    onStopVpn: () -> Unit,
    connectionState: ConnectionState,
    hasActiveProfile: Boolean,
    onLiveLogClick: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hamburger Menu Box
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlass)
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onOpenDrawer() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Menu,
                contentDescription = "Menu",
                tint = PrimaryCyan,
                modifier = Modifier.size(32.dp)
            )
        }

        // Live Log Box
        Row(
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlass)
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                .clickable { onLiveLogClick() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Live Log",
                    color = PrimaryCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SfProPlusBlack
                )
            }

            // Smart LED Logic
            val isIdle = connectionState == ConnectionState.DISCONNECTED || !hasActiveProfile
            val dotColor = when (connectionState) {
                ConnectionState.CONNECTING, ConnectionState.RECONNECTING, ConnectionState.CONNECTED -> PrimaryCyan
                ConnectionState.DISCONNECTING, ConnectionState.ERROR, ConnectionState.CANCELING -> Color(
                    0xFFFF5252
                )

                else -> Color.White
            }
            val isConnecting =
                connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING

            val infiniteTransition = rememberInfiniteTransition(label = "pulse")

            // ALWAYS provide a non-zero duration (e.g., 1000) to prevent divide-by-zero crashes
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(if (isConnecting) 600 else 1000),
                    RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )

            val haloScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Restart),
                label = "halo_scale"
            )

            val haloAlpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Restart),
                label = "halo_alpha"
            )

            // Override animation when Idle
            val finalAlpha = if (isIdle) 1f else pulseAlpha

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                if (isConnecting) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(haloScale)
                            .alpha(haloAlpha)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(finalAlpha)
                        .clip(CircleShape)
                        .background(dotColor)
                        .border(1.dp, dotColor.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun CustomGlassToast(message: String?, modifier: Modifier = Modifier) {
    androidx.compose.animation.AnimatedVisibility(
        visible = message != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(
            initialOffsetY = { it }),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(
            targetOffsetY = { it }),
        modifier = modifier.padding(bottom = 64.dp, start = 24.dp, end = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF020B14).copy(alpha = 0.95f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message ?: "",
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun ServerListFullScreen(
    protocol: String,
    servers: List<VpnProfile>,
    activeProfile: VpnProfile?,
    connectionState: ConnectionState,
    onSelect: (Int) -> Unit,
    onDelete: (VpnProfile) -> Unit,
    onDeleteSubGroup: (String) -> Unit,
    onRenameSubGroup: (String, String) -> Unit,
    onTestSinglePing: (VpnProfile) -> Unit,
    onTestAllV2rayPing: () -> Unit,
    onSortV2ray: () -> Unit,
    onDeleteInvalidV2ray: () -> Unit,
    onUpdateSub: (String) -> Unit,
    onEdit: (VpnProfile) -> Unit,
    onNavigateBack: () -> Unit,
    toastMessage: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("tab_prefs", android.content.Context.MODE_PRIVATE) }

    val v2rayServers = androidx.compose.runtime.remember(servers) { servers.filter { it.protocol == "v2rayNG" } }
    val singleLinks = androidx.compose.runtime.remember(v2rayServers) { v2rayServers.filter { !it.username.startsWith("sub:") } }
    val subGroups = androidx.compose.runtime.remember(v2rayServers) { v2rayServers.filter { it.username.startsWith("sub:") }.groupBy { it.username } }
    val subKeys = androidx.compose.runtime.remember(subGroups) { subGroups.keys.toList() }

    val allDynamicTabs = androidx.compose.runtime.remember(subKeys) { mutableListOf("SingleLinks").apply { addAll(subKeys) } }
    
    // 1. Robust Persistence Logic for Tab Order
    var tabOrder by androidx.compose.runtime.remember { mutableStateOf(prefs.getString("order", "")!!.split(",").filter { it.isNotBlank() }) }
    
    androidx.compose.runtime.LaunchedEffect(allDynamicTabs) {
        val currentSaved = prefs.getString("order", "")!!.split(",").filter { it.isNotBlank() }
        val newOrder = currentSaved.toMutableList()
        var changed = false
        
        allDynamicTabs.forEach { tab ->
            if (!newOrder.contains(tab)) {
                newOrder.add(tab)
                changed = true
            }
        }
        val iterator = newOrder.iterator()
        while(iterator.hasNext()) {
            if(!allDynamicTabs.contains(iterator.next())) {
                iterator.remove()
                changed = true
            }
        }
        if (changed) {
            tabOrder = newOrder
            prefs.edit().putString("order", newOrder.joinToString(",")).apply()
        }
    }

    val sortedTabs = androidx.compose.runtime.remember(allDynamicTabs, tabOrder) {
        allDynamicTabs.sortedBy { if (tabOrder.contains(it)) tabOrder.indexOf(it) else Int.MAX_VALUE }
    }

    var isReorderMode by androidx.compose.runtime.remember { mutableStateOf(false) }
    var expandedTabMenu by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var renamingSubKey by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

    // 2. Smart Default Tab Logic (Avoids empty SingleLinks if subs exist)
    var selectedTab by androidx.compose.runtime.remember { 
        mutableStateOf(if (singleLinks.isNotEmpty() || subKeys.isEmpty()) "SingleLinks" else subKeys.first()) 
    }

    androidx.compose.runtime.LaunchedEffect(subKeys, singleLinks.size) {
        if (selectedTab == "SingleLinks" && singleLinks.isEmpty() && subKeys.isNotEmpty()) {
            selectedTab = subKeys.first()
        } else if (selectedTab != "SingleLinks" && !subKeys.contains(selectedTab)) {
            selectedTab = if (singleLinks.isNotEmpty() || subKeys.isEmpty()) "SingleLinks" else subKeys.first()
        }
    }

    val displayServers = if (protocol == "v2rayNG") {
        if (selectedTab == "SingleLinks") singleLinks else subGroups[selectedTab] ?: emptyList()
    } else {
        servers
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(SurfaceDark)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(28.dp).bounceClick { if (isReorderMode) isReorderMode = false else onNavigateBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))

            if (!isReorderMode) {
                Text("Server List", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))

                if (protocol == "v2rayNG") {
                    var showV2rayMenu by androidx.compose.runtime.remember { mutableStateOf(false) }
                    Box {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp).bounceClick { showV2rayMenu = true }
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = showV2rayMenu,
                            onDismissRequest = { showV2rayMenu = false },
                            modifier = Modifier.background(SurfaceDark).border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Sorting by test results", color = Color.White) },
                                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Sort, contentDescription = null, tint = Color.White) },
                                onClick = { showV2rayMenu = false; onSortV2ray() }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Test Real delay config", color = Color.White) },
                                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Speed, contentDescription = null, tint = Color.White) },
                                onClick = { showV2rayMenu = false; onTestAllV2rayPing() }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Update subscription", color = Color.White) },
                                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Sync, contentDescription = null, tint = Color.White) },
                                onClick = { showV2rayMenu = false; if (selectedTab != "SingleLinks") onUpdateSub(selectedTab) }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Delete invalid config", color = Color(0xFFE53935)) },
                                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFE53935)) },
                                onClick = { showV2rayMenu = false; onDeleteInvalidV2ray() }
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val listToUse = if (tabOrder.isEmpty()) allDynamicTabs else tabOrder
                    val currentIndex = listToUse.indexOf(selectedTab)

                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (currentIndex > 0) {
                                val newList = listToUse.toMutableList()
                                java.util.Collections.swap(newList, currentIndex, currentIndex - 1)
                                tabOrder = newList
                                prefs.edit().putString("order", newList.joinToString(",")).apply()
                            }
                        },
                        enabled = currentIndex > 0
                    ) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move Left", tint = if (currentIndex > 0) PrimaryCyan else Color.Gray)
                    }

                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (currentIndex < listToUse.size - 1) {
                                val newList = listToUse.toMutableList()
                                java.util.Collections.swap(newList, currentIndex, currentIndex + 1)
                                tabOrder = newList
                                prefs.edit().putString("order", newList.joinToString(",")).apply()
                            }
                        },
                        enabled = currentIndex < listToUse.size - 1
                    ) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move Right", tint = if (currentIndex < listToUse.size - 1) PrimaryCyan else Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "Done", color = PrimaryCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.bounceClick { isReorderMode = false }.padding(8.dp))
            }
        }

        if (protocol == "v2rayNG") {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // CRITICAL FIX: key = { it } prevents the visual state inversion bug
                items(sortedTabs, key = { it }) { tabKey ->
                    val count = if (tabKey == "SingleLinks") singleLinks.size else (subGroups[tabKey]?.size ?: 0)
                    val customName = if (tabKey == "SingleLinks") "SingleLinks" 
                        else subGroups[tabKey]?.firstOrNull()?.subName?.takeIf { it.isNotBlank() } ?: "SubLink ${subKeys.indexOf(tabKey) + 1}"

                    Box {
                        Text(
                            text = "$customName ($count)",
                            color = if (selectedTab == tabKey) PrimaryCyan else TextSecondary,
                            fontWeight = if (selectedTab == tabKey) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .bounceClick(onLongClick = { selectedTab = tabKey; expandedTabMenu = tabKey }) { selectedTab = tabKey }
                                .padding(8.dp)
                        )

                        androidx.compose.material3.DropdownMenu(
                            expanded = expandedTabMenu == tabKey,
                            onDismissRequest = { expandedTabMenu = null },
                            modifier = Modifier.background(SurfaceDark).border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Reorder", color = Color.White) },
                                leadingIcon = { Icon(TelegramReorderIcon, contentDescription = null, tint = Color.White) },
                                onClick = { expandedTabMenu = null; isReorderMode = true }
                            )
                            if (tabKey != "SingleLinks") {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Rename", color = Color.White) },
                                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                                    onClick = { expandedTabMenu = null; renamingSubKey = tabKey }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Delete Sub", color = Color(0xFFE53935)) },
                                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFE53935)) },
                                    onClick = {
                                        expandedTabMenu = null
                                        onDeleteSubGroup(tabKey)
                                        if (selectedTab == tabKey) selectedTab = "SingleLinks" // Fallback will be handled by LaunchedEffect
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayServers) { profile ->
                val isSelected = profile.id == activeProfile?.id
                val isSub = profile.username.startsWith("sub:")
                ServerItemCard(
                    profile = profile,
                    isSelected = isSelected,
                    connectionState = connectionState,
                    onClick = { onSelect(profile.id); onNavigateBack() },
                    onDelete = if (isSub) null else { { onDelete(profile) } },
                    onEdit = if (isSub) null else { { onEdit(profile) } },
                    onPingClick = { onTestSinglePing(profile) }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (renamingSubKey != null) {
        val originalName = subGroups[renamingSubKey]?.firstOrNull()?.subName?.takeIf { it.isNotBlank() } ?: "SubLink ${subKeys.indexOf(renamingSubKey) + 1}"
        var customName by androidx.compose.runtime.remember(renamingSubKey) { mutableStateOf(originalName) }
        
        // FIX: Only enable button if text is not blank and is actually changed
        val isButtonEnabled = customName.isNotBlank() && customName != originalName

        androidx.compose.ui.window.Dialog(onDismissRequest = { renamingSubKey = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceMenu)
                    .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Text("Rename Subscription", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = SfProPlusBlack)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Subscription Name", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (isButtonEnabled) {
                                onRenameSubGroup(renamingSubKey!!, customName)
                            }
                            renamingSubKey = null
                        },
                        enabled = isButtonEnabled,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryCyan,
                            disabledContainerColor = SurfaceGlass
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = if (isButtonEnabled) null else androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
                    ) {
                        Text(
                            "Apply", 
                            color = if (isButtonEnabled) BackgroundDark else TextSecondary, 
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.Bold, 
                            fontFamily = SfProPlusBlack
                        )
                    }
                }
            }
        }
    }

    // NEW: Render the custom toast over the server list
    CustomGlassToast(
        message = toastMessage,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
}

@Composable
fun LiveLogFullScreen(
    logs: List<String>,
    protocol: String,
    onClear: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(modifier = Modifier
        .fillMaxSize()
        .background(SurfaceDark)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .bounceClick { onNavigateBack() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            val displayProtocol = if (protocol == "OpenConnect") "AnyConnect" else protocol
            Text(
                "$displayProtocol Logs",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.Delete,
                contentDescription = "Clear Logs",
                tint = Color(0xFFFF5252),
                modifier = Modifier
                    .size(28.dp)
                    .bounceClick { onClear() }
            )
        }

        val symbolRegex = remember { Regex("[\\p{Punct}]+") }
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp, top = 24.dp),
            reverseLayout = true // The secret to zero-lag log rendering
        ) {
            items(logs.asReversed()) { log ->
                val annotatedLog = remember(log) {
                    buildAnnotatedString {
                        var lastIndex = 0
                        symbolRegex.findAll(log).forEach { matchResult ->
                            withStyle(SpanStyle(color = Color(0xFFE0E0E0))) {
                                append(log.substring(lastIndex, matchResult.range.first))
                            }
                            withStyle(SpanStyle(color = PrimaryCyan)) {
                                append(matchResult.value)
                            }
                            lastIndex = matchResult.range.last + 1
                        }
                        withStyle(SpanStyle(color = Color(0xFFE0E0E0))) {
                            append(log.substring(lastIndex))
                        }
                    }
                }
                Text(
                    text = annotatedLog,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (logs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No logs available.",
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
