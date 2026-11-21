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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
import dev.rouzbeh.hybridvpn.service.VpnProtocol
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

val SoraFamily = FontFamily(
    androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sora_thin, FontWeight.Thin),
    androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sora_extralight, FontWeight.ExtraLight),
    androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sora_light, FontWeight.Light),
    androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sora_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sora_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sora_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sora_bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sora_extrabold, FontWeight.ExtraBold),
    androidx.compose.ui.text.font.Font(dev.rouzbeh.hybridvpn.R.font.sora_extrabold, FontWeight.Black),
)

/**
 * Strips emoji and emoji-modifier code points while preserving everything else
 * (letters, digits, Persian/Arabic text, ZWNJ, punctuation). Used to keep
 * AnyConnect username/password fields emoji-free for both keyboard and paste.
 */
object EmojiFilter {
    fun strip(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            if (!isEmoji(cp)) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun isEmoji(cp: Int) =
        cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2300..0x23FF ||
        cp in 0x2B00..0x2BFF || cp in 0x2194..0x21AA || cp in 0xE0020..0xE007F ||
        cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D || cp == 0x20E3
}

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
    WireGuard("WireGuard", R.drawable.ic_wireguard),
    V2rayNG("v2rayNG", R.drawable.ic_v2rayng)
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
    val isConnectivityDegraded by viewModel.connectivityDegraded.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isPerAppEnabled by viewModel.isPerAppEnabled.collectAsStateWithLifecycle()
    val isBypassMode by viewModel.isBypassMode.collectAsStateWithLifecycle()
    val isRoutingPending by viewModel.isRoutingPending.collectAsStateWithLifecycle()
    val currentLogs by viewModel.currentLogs.collectAsStateWithLifecycle()
    val v2rayPingTabs by viewModel.v2rayPingTabs.collectAsStateWithLifecycle()

    val drawerState =
        androidx.compose.material3.rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var activeDrawerModal by remember { mutableStateOf(DrawerModalType.NONE) }
    var openSplitSheetTrigger by remember { mutableStateOf(0) }
    var disconnectWarningMessage by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen, // CRITICAL FIX: Disables swipe-to-open
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.80f),
                drawerContainerColor = SurfaceMenu,
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            ) {
                HybridNestDrawerContent(
                    isDrawerOpen = drawerState.isOpen,
                    onSelectModal = { modal ->
                        scope.launch { drawerState.close() }
                        when (modal) {
                            DrawerModalType.ANYCONNECT,
                            DrawerModalType.WIREGUARD,
                            DrawerModalType.V2RAY,
                            DrawerModalType.FEATURES -> {
                                if (!connectionState.isTerminal) {
                                    disconnectWarningMessage = "Cannot modify settings while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?"
                                } else {
                                    activeDrawerModal = modal
                                }
                            }
                            DrawerModalType.CHECK_UPDATE,
                            DrawerModalType.ABOUT -> {
                                activeDrawerModal = modal
                            }
                            DrawerModalType.NONE -> Unit
                        }
                    }
                )
            }
        }
    ) {
        DashboardContent(
            uptimeFlow = viewModel.uptime,
            connectionState = connectionState,
            networkInfo = networkInfo,
            isConnectivityDegraded = isConnectivityDegraded,
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
            onTestAllV2rayPing = { groupName -> viewModel.testV2rayRealDelay(groupName) },
            onSortV2ray = { groupName -> viewModel.sortV2rayByTestResults(groupName) },
            onDeleteInvalidV2ray = { groupName -> viewModel.deleteInvalidV2rayConfigs(groupName) },
            onUpdateSub = { subKey -> viewModel.updateSubscription(subKey) },
            v2rayPingTabs = v2rayPingTabs,
            onTestPing = { viewModel.testPing() },
            onToggleAppSelection = { viewModel.toggleAppSelection(it) },
            onSetPerAppEnabled = { viewModel.setPerAppEnabled(it) },
            onSetBypassMode = { viewModel.setBypassMode(it) },
            onSelectAllApps = { viewModel.selectAllApps() },
            onDeselectAllApps = { viewModel.deselectAllApps() },
            onReconnectWithLatestPolicy = { context -> viewModel.reconnectWithLatestPolicy(context) },
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onRetryIpFetch = { viewModel.retryFetchIp() },
            onImportWireGuardUri = { uri -> viewModel.importWireGuardFromUri(context, uri) },
            openSplitSheetTrigger = openSplitSheetTrigger,
            toastFlow = viewModel.toastMessage,
            subscriptionToastFlow = viewModel.subscriptionToast,
            onShowDisconnectWarning = { disconnectWarningMessage = it }
        )
    }

    DrawerModalHost(
        activeModal = activeDrawerModal,
        onDismiss = { activeDrawerModal = DrawerModalType.NONE },
        viewModel = viewModel,
        onOpenPerAppRouting = {
            activeDrawerModal = DrawerModalType.NONE
            openSplitSheetTrigger++
        }
    )

    ActiveConnectionWarningDialog(
        message = disconnectWarningMessage,
        onDismiss = { disconnectWarningMessage = null },
        onDisconnect = {
            disconnectWarningMessage = null
            viewModel.initiateDisconnect(context) { /* handled in VM */ }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    uptimeFlow: StateFlow<Long>,
    connectionState: ConnectionState,
    networkInfo: NetworkInfo,
    isConnectivityDegraded: Boolean = false,
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
    onTestAllV2rayPing: (String) -> Unit,
    onSortV2ray: (String) -> Unit,
    onDeleteInvalidV2ray: (String) -> Unit,
    onUpdateSub: (String) -> Unit,
    v2rayPingTabs: Set<String>,
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
    onImportWireGuardUri: ((android.net.Uri) -> Unit)? = null,
    openSplitSheetTrigger: Int = 0,
    toastFlow: kotlinx.coroutines.flow.Flow<String>,
    subscriptionToastFlow: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.emptyFlow(),
    onShowDisconnectWarning: (String) -> Unit = {}
) {
    var showAddServerSheet by remember { mutableStateOf(false) }
    var showServerListSheet by remember { mutableStateOf(false) }
    var showSplitSheet by remember { mutableStateOf(false) }
    var showAppRoutingScreen by remember { mutableStateOf(false) }
    var showLiveLogScreen by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<VpnProfile?>(null) }
    var dashboardToastMessage by remember { mutableStateOf<String?>(null) }
    val onShowToast = { msg: String -> dashboardToastMessage = msg }

    LaunchedEffect(openSplitSheetTrigger) {
        if (openSplitSheetTrigger > 0) {
            showSplitSheet = true
        }
    }

    val currentProtocolDisplay = VpnProtocolDisplay.entries.find { it.title == selectedProtocol }
        ?: VpnProtocolDisplay.OpenConnect

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
            delay(2500L)
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

    val currentActiveProfile =
        if (activeProfile?.protocol == selectedProtocol) activeProfile else null

    val canConnect = currentActiveProfile != null

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
            v2rayPingTabs = v2rayPingTabs,
            onEdit = { profile -> editingProfile = profile },
            onNavigateBack = { showServerListSheet = false },
            toastMessage = dashboardToastMessage,
            onShowToast = onShowToast,
            subscriptionToastFlow = subscriptionToastFlow
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .navigationBarsPadding()
            ) {
                val activeProtocolName = activeProfile?.protocol ?: selectedProtocol
                val showWireGuardDegradedHint = connectionState == ConnectionState.CONNECTED &&
                    isConnectivityDegraded &&
                    activeProtocolName == "WireGuard"
                val density = LocalDensity.current
                val needsScroll = maxHeight < 640.dp || density.fontScale > 1.15f || (showWireGuardDegradedHint && maxHeight < 700.dp)
                val columnModifier = if (needsScroll) {
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                } else {
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                }

                Column(
                    modifier = columnModifier,
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
                        modifier = if (needsScroll) Modifier.height(160.dp) else Modifier.weight(1f).heightIn(min = 140.dp),
                        networkInfo = networkInfo,
                        connectionState = connectionState,
                        onMoreClick = {
                            val intent =
                                Intent(Intent.ACTION_VIEW, "https://browserleaks.com/ip".toUri())
                            context.startActivity(intent)
                        },
                        onRetryClick = onRetryIpFetch
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = showWireGuardDegradedHint,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFFFA000).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFFFA000).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Connectivity Degraded",
                                    tint = Color(0xFFFFA000),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Handshake may have failed; check that the server has your public key.",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = SoraFamily,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                // 3. Speed Tracker (Only ONE instance, remove duplicates)
                SpeedTrackerRow(
                    downloadSpeed = speed.first,
                    uploadSpeed = speed.second,
                    downloadRaw = rawSpeed.first,
                    uploadRaw = rawSpeed.second,
                    connectionState = connectionState
                )

                // NEW: App Routing / Split Tunneling Button.
                val currentMode = when {
                    !isPerAppEnabled -> "All apps use VPN"
                    isBypassMode -> "All apps except those selected use VPN"
                    else -> "Only selected apps use VPN"
                }
                RoutingSelectorButton(
                    currentMode = currentMode,
                    isEnabled = isUiUnlocked,
                    onClick = {
                        if (isUiUnlocked) {
                            if (isPerAppEnabled) showAppRoutingScreen = true else showSplitSheet = true
                        } else {
                            onShowDisconnectWarning(
                                "Cannot modify Split Tunneling while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?"
                            )
                        }
                    }
                )

                // 4. Protocol Selector
                ProtocolSelector(
                    selectedProtocol = currentProtocolDisplay,
                    isEnabled = isUiUnlocked,
                    onProtocolSelected = {
                        if (it == currentProtocolDisplay) return@ProtocolSelector
                        if (isUiUnlocked) {
                            onProtocolSelected(it.title)
                        } else {
                            onShowDisconnectWarning(
                                "Cannot switch protocol while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?"
                            )
                        }
                    }
                )

                // 5. Server Selector
                ServerSelectorButton(
                    activeProfile = activeProfile,
                    selectedProtocol = currentProtocolDisplay,
                    hasProfiles = filteredProfiles.isNotEmpty(),
                    isEnabled = isUiUnlocked,
                    onClick = { showServerListSheet = true },
                    onAddClick = {
                        if (isUiUnlocked) {
                            showAddServerSheet = true
                        } else {
                            onShowDisconnectWarning(
                                "Cannot add a server while VPN is connected. Please disconnect your active connection and try again.\n\nWould you like to disconnect now?"
                            )
                        }
                    }
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
                            if (!canConnect) {
                                Text(
                                    text = "Select Server",
                                    color = Color.Gray,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SoraFamily
                                )
                            } else {
                                when (connectionState) {
                                    ConnectionState.CONNECTED -> Text(
                                        text = "Connected",
                                        color = Color(0xFF00D8E6),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = SoraFamily
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
                                        fontFamily = SoraFamily
                                    )

                                    else -> {
                                        Text(
                                            text = "Ready",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = SoraFamily
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
                                    fontFamily = SoraFamily
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
                                    fontFamily = SoraFamily
                                )
                            }
                        }
                    }

                    val isConnected = connectionState == ConnectionState.CONNECTED
                    val isConnecting =
                        connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING || connectionState == ConnectionState.DISCONNECTING || connectionState == ConnectionState.CANCELING
                    val selectionLocked by VpnStateManager.selectionLocked.collectAsStateWithLifecycle()
                    val isConnectEnabled =
                        (canConnect && !isConnecting && !selectionLocked) || isConnected || isConnecting

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
                                    if (!canConnect) return@clickable
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

                if (showAddServerSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showAddServerSheet = false },
                        sheetState = sheetState,
                        containerColor = SurfaceMenu,
                        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
                        dragHandle = null
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            AddServerSheetContent(
                                protocol = currentProtocolDisplay,
                                servers = filteredProfiles,
                                onAdd = onAddServer,
                                onUpdate = onUpdateServer,
                                onClose = { showAddServerSheet = false },
                                onShowToast = onShowToast,
                                onImportWireGuardUri = onImportWireGuardUri
                            )
                        }
                    }
                }

                if (showServerListSheet && !hasServers) {
                    ModalBottomSheet(
                        onDismissRequest = { showServerListSheet = false },
                        sheetState = sheetState,
                        containerColor = SurfaceMenu,
                        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
                        dragHandle = null
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                                onTestSinglePing = onTestSinglePing,
                                onShowToast = onShowToast
                            )
                        }
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
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
            CustomGlassToast(
                message = dashboardToastMessage,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (editingProfile != null) {
        ModalBottomSheet(
            onDismissRequest = { editingProfile = null },
            sheetState = sheetState,
            containerColor = SurfaceMenu,
            contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
            dragHandle = null
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                AddServerSheetContent(
                    protocol = currentProtocolDisplay,
                    servers = filteredProfiles,
                    editingProfile = editingProfile,
                    onAdd = onAddServer,
                    onUpdate = onUpdateServer,
                    onClose = { editingProfile = null },
                    onShowToast = onShowToast,
                    onImportWireGuardUri = onImportWireGuardUri
                )
            }
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
            onTestAllV2rayPing = { _ -> },
            onSortV2ray = { _ -> },
            onDeleteInvalidV2ray = { _ -> },
            onUpdateSub = {},
            v2rayPingTabs = emptySet(),
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
                    protocol = "OpenConnect",
                    serverAddress = "us.server.com"
                ),
                selectedProtocol = VpnProtocolDisplay.OpenConnect,
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
        activeProfile != null && activeProfile.protocol == selectedProtocol.title -> normalizeStyledText(
            activeProfile.name.replace(
                flagRegex,
                ""
            ).trim()
        )

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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Server List",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = SoraFamily
                )
                Spacer(modifier = Modifier.height(4.dp))
                var nameFontSize by remember(serverText) { mutableStateOf(14.sp) }
                Text(
                    text = serverText,
                    color = if (activeProfile != null && activeProfile.protocol == selectedProtocol.title) PrimaryCyan else Color.Gray,
                    fontSize = nameFontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        val minFontSize = 10.sp
                        if (result.hasVisualOverflow && nameFontSize.value > minFontSize.value) {
                            nameFontSize = (nameFontSize.value - 1f).coerceAtLeast(minFontSize.value).sp
                        }
                    },
                    fontFamily = SoraFamily
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
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
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlass)
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAddClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Server",
                tint = PrimaryCyan
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
    onTestSinglePing: (VpnProfile) -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 16.dp, top = 8.dp)
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
            text = "Saved Servers", color = TextPrimary, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp),
            fontFamily = SoraFamily
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (profiles.isEmpty()) {
            Text(
                text = "No servers found. Tap the [+] button to add a new server.",
                color = TextSecondary, modifier = Modifier.padding(horizontal = 24.dp),
                fontFamily = SoraFamily
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles) { profile ->
                    val isSelected = !isSubscriptionInfoName(profile.name) && profile.id == activeProfile?.id
                    ServerItemCard(
                        profile = profile, isSelected = isSelected,
                        connectionState = connectionState,
                        onClick = { onSelect(profile.id) },
                        onDelete = { onDelete(profile) },
                        onEdit = { onEdit(profile) },
                        onPingClick = { onTestSinglePing(profile) },
                        onShowToast = onShowToast
                    )
                }
            }
        }
    }
}

// Helpers for subscription-info detection (e.g. 👤...|📊 41.79 GB / 100 GB|📆 11 Day)
// Used to render a custom badge row and hide the raw stats from the name.
data class SubscriptionMeta(
    val usedStr: String,
    val totalStr: String,
    val usedGb: Double?,
    val totalGb: Double?,
    val daysLeft: Int?,
    val daysRaw: String?,
    val daysNum: String?
)

private fun normalizeStyledText(input: String): String {
    // Map mathematical alphanumeric symbols (bold/italic/script) to plain ASCII so app font applies.
    // Covers: Mathematical Bold (1D400), Italic (1D434), Bold Italic (1D468), Sans, etc. — we collapse letters/digits.
    val sb = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val cp = input.codePointAt(i)
        val mapped = when (cp) {
            in 0x1D400..0x1D419 -> 'A'.code + (cp - 0x1D400) // Bold A-Z
            in 0x1D41A..0x1D433 -> 'a'.code + (cp - 0x1D41A) // Bold a-z
            in 0x1D434..0x1D44D -> 'A'.code + (cp - 0x1D434) // Italic A-Z
            in 0x1D44E..0x1D467 -> 'a'.code + (cp - 0x1D44E) // Italic a-z
            in 0x1D468..0x1D481 -> 'A'.code + (cp - 0x1D468) // Bold Italic A-Z
            in 0x1D482..0x1D49B -> 'a'.code + (cp - 0x1D482)
            in 0x1D49C..0x1D4B5 -> 'A'.code + (cp - 0x1D49C) // Script
            in 0x1D4B6..0x1D4CF -> 'a'.code + (cp - 0x1D4B6)
            in 0x1D4D0..0x1D4E9 -> 'A'.code + (cp - 0x1D4D0)
            in 0x1D4EA..0x1D503 -> 'a'.code + (cp - 0x1D4EA)
            in 0x1D504..0x1D51D -> 'A'.code + (cp - 0x1D504) // Fraktur / etc -> fallback to plain
            in 0x1D51E..0x1D537 -> 'a'.code + (cp - 0x1D51E)
            in 0x1D538..0x1D551 -> 'A'.code + (cp - 0x1D538)
            in 0x1D552..0x1D56B -> 'a'.code + (cp - 0x1D552)
            in 0x1D56C..0x1D585 -> 'A'.code + (cp - 0x1D56C)
            in 0x1D586..0x1D59F -> 'a'.code + (cp - 0x1D586)
            in 0x1D5A0..0x1D5B9 -> 'A'.code + (cp - 0x1D5A0)
            in 0x1D5BA..0x1D5D3 -> 'a'.code + (cp - 0x1D5BA)
            in 0x1D5D4..0x1D5ED -> 'A'.code + (cp - 0x1D5D4)
            in 0x1D5EE..0x1D607 -> 'a'.code + (cp - 0x1D5EE)
            in 0x1D608..0x1D621 -> 'A'.code + (cp - 0x1D608)
            in 0x1D622..0x1D63B -> 'a'.code + (cp - 0x1D622)
            in 0x1D63C..0x1D655 -> 'A'.code + (cp - 0x1D63C)
            in 0x1D656..0x1D66F -> 'a'.code + (cp - 0x1D656)
            in 0x1D670..0x1D689 -> 'A'.code + (cp - 0x1D670)
            in 0x1D68A..0x1D6A3 -> 'a'.code + (cp - 0x1D68A)
            in 0x1D7CE..0x1D7D7 -> '0'.code + (cp - 0x1D7CE) // Bold digits
            in 0x1D7D8..0x1D7E1 -> '0'.code + (cp - 0x1D7D8)
            in 0x1D7E2..0x1D7EB -> '0'.code + (cp - 0x1D7E2)
            in 0x1D7EC..0x1D7F5 -> '0'.code + (cp - 0x1D7EC)
            in 0x1D7F6..0x1D7FF -> '0'.code + (cp - 0x1D7F6)
            else -> cp
        }
        sb.appendCodePoint(mapped)
        i += Character.charCount(cp)
    }
    return sb.toString()
}

/**
 * True when a profile name carries subscription volume/time tokens and should be
 * rendered as an info card (never selectable, never pinged). Shared between the
 * UI list and the ViewModel so both agree on what counts as an info card.
 */
internal fun isSubscriptionInfoName(name: String): Boolean {
    return InfoRemarkClassifier.classify(name) != null
}

fun parseSubscriptionMeta(name: String): SubscriptionMeta? {
    return InfoRemarkClassifier.classify(name)?.toSubscriptionMeta()
}

/**
 * Leading info-entry scan: consumes ONLY the contiguous leading run of info
 * entries at the top of a subscription list. Real servers further down are never swallowed.
 */
internal fun leadingSubscriptionInfoNames(names: List<String>): List<String> {
    val (consumed, _) = mergeLeadingInfo(names)
    return names.take(consumed)
}

/**
 * Aggregates subscription volume/time fields across the leading info block of a subscription.
 */
internal fun parseSubscriptionMeta(names: List<String>): SubscriptionMeta? {
    val (_, piece) = mergeLeadingInfo(names)
    return piece?.toSubscriptionMeta()
}

/** Strips the subscription volume/time tokens from [name] and returns the cleaned remainder. */
private fun extractSubscriptionMetaFromName(name: String): String {
    return name.replace(Regex("[👤📊📆🧑‍💼📅⏳🕒|¦—–\\[\\]]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "Subscription Info" }
}

@Composable
fun ServerItemCard(
    profile: VpnProfile,
    isSelected: Boolean,
    connectionState: ConnectionState,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onPingClick: () -> Unit = {},
    onShowToast: (String) -> Unit = {},
    subscriptionMeta: SubscriptionMeta? = null,
    subscriptionLabel: String = "Subscription"
) {
    val haptic = LocalHapticFeedback.current
    val bgColor = if (isSelected) PrimaryCyan.copy(alpha = 0.1f) else SurfaceGlass

    val protocolIcon = when (profile.protocol) {
        "v2rayNG" -> R.drawable.ic_v2rayng // Ensure this exists
        else -> R.drawable.ic_openconnect
    }

    val isConnectedOrConnecting =
        connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING

    val flagRegex = remember { Regex("[\\uD83C\\uDDE6-\\uD83C\\uDDFF]{2}") }
    val flags = remember(profile.name) { flagRegex.findAll(profile.name).map { it.value }.toList() }
    // The caller may pass an aggregated meta (e.g. volume and time live in
    // different profile names within the same sub group); otherwise parse the
    // single profile name.
    val meta = subscriptionMeta ?: remember(profile.name) { parseSubscriptionMeta(profile.name) }
    val isSubscriptionInfoCard = meta != null && (meta.usedStr.isNotBlank() || meta.daysRaw != null)
    val rawClean = remember(profile.name, isSubscriptionInfoCard) {
        val stripped = if (isSubscriptionInfoCard) extractSubscriptionMetaFromName(profile.name) else profile.name.replace(flagRegex, "").trim()
        // Apply font normalization (optional stylized -> plain) but keep emojis
        normalizeStyledText(stripped).trim()
    }
    // For subscription info cards keep the ID part if present, else fallback
    val displayName = remember(rawClean, isSubscriptionInfoCard, subscriptionLabel) {
        if (isSubscriptionInfoCard && rawClean.isBlank()) subscriptionLabel else rawClean
    }

    val context = LocalContext.current
    val countryCodes = remember(flags) {
        flags.mapNotNull { emoji ->
            try {
                val cp1 = emoji.codePointAt(0)
                val cp2 = emoji.codePointAt(emoji.offsetByCodePoints(0, 1))
                val c1 = (cp1 - 0x1F1E6 + 97).toChar()
                val c2 = (cp2 - 0x1F1E6 + 97).toChar()
                BundledFlags.normalizeCode("$c1$c2")
            } catch (e: Exception) {
                null
            }
        }
    }
    val flagBitmaps = remember(context, countryCodes) {
        countryCodes.take(2).mapNotNull { code -> BundledFlags.getFlagBitmap(context, code) }
    }

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
            .then(
                when {
                    // Subscription volume/time cards are informational only — never selectable.
                    isSubscriptionInfoCard -> Modifier
                    // Servers that failed their ping (-1) can't be selected for connecting.
                    profile.ping == -1L -> Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onShowToast("This server is unreachable right now\nPick another server or retest its ping.")
                    }
                    else -> Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                }
            )
            .padding(start = 16.dp, end = 10.dp, top = 18.dp, bottom = 18.dp)
    ) {
        var isPinging by remember { mutableStateOf(false) }
        LaunchedEffect(profile.ping) {
            isPinging = false
        }

        // Subscription info cards (volume/time) don't show the protocol/v2rayNG logo
        // — removing it frees up room for the meta row and keeps the card minimal.
        if (!isSubscriptionInfoCard) {
        val hasFlag = flagBitmaps.isNotEmpty()
        val boxModifier = if (hasFlag) {
            Modifier
                .size(width = 56.dp, height = 36.dp)
                .clip(RoundedCornerShape(9.dp))
        } else {
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(11.dp))
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
            } else if (flagBitmaps.size == 1) {
                androidx.compose.foundation.Image(
                    bitmap = flagBitmaps[0],
                    contentDescription = "Flag",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
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
                        androidx.compose.foundation.Image(
                            bitmap = flagBitmaps[1],
                            contentDescription = "Flag 2",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
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
                        androidx.compose.foundation.Image(
                            bitmap = flagBitmaps[0],
                            contentDescription = "Flag 1",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
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
        Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!isSubscriptionInfoCard) {
                Text(
                    text = displayName,
                    color = TextPrimary,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = SoraFamily,
                    lineHeight = 18.sp
                )
            }
            // Subscription info card: only volume + time row. No name, no VLESS/TCP.
            if (isSubscriptionInfoCard) {
                SubscriptionMetaRow(meta = meta!!)
            }
            if (!isSubscriptionInfoCard) {
                Spacer(modifier = Modifier.height(3.dp))
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
                        else -> profile.serverAddress
                    }
                }
                Text(
                    text = subTitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = SoraFamily
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (profile.protocol == "v2rayNG" && !isSubscriptionInfoCard) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.30f))
                        .border(1.dp, BorderGlass.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                        .clickable { isPinging = true; onPingClick() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
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
                            val isFailed = profile.ping == -1L
                            val isUntested = profile.ping == 0L
                            val pText = when {
                                isFailed -> "-1"
                                isUntested -> "Test"
                                else -> "${profile.ping} ms"
                            }
                            val pColor = when {
                                isFailed -> Color(0xFFFF5252)
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
            }
            // Subscription info card: no "Info" chip, no ping badge, no VLESS/TCP — only the volume+time row.
            if (onEdit != null && !isSubscriptionInfoCard) {
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

            if (onDelete != null && !isSubscriptionInfoCard) {
                IconButton(
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDelete() },
                    enabled = !(isSelected && isConnectedOrConnecting),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = if (isSelected && isConnectedOrConnecting) Color.DarkGray else Color(0xFFFF3B30).copy(alpha = 0.75f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionMetaRow(meta: SubscriptionMeta) {
    val volumeText = when {
        meta.usedStr.isNotBlank() && meta.totalStr.isNotBlank() -> "${meta.usedStr} / ${meta.totalStr}"
        meta.usedStr.isNotBlank() -> meta.usedStr
        meta.totalStr.isNotBlank() -> meta.totalStr
        else -> null
    }
    val daysText = meta.daysRaw
    // Volume + time text only, no icons. The divider sits between them with even,
    // modest spacing — the row sizes to its content so the gap around the divider
    // stays tight and the box grows/shrinks with how far the two texts reach.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, BorderGlass.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (volumeText != null) {
            val volAnnotated = buildAnnotatedString {
                var i = 0
                val s = volumeText
                while (i < s.length) {
                    // Color "GB", "MB", "TB", "KB", "B" and "." in cyan
                    if (s.startsWith("GB", i) || s.startsWith("MB", i) || s.startsWith("TB", i) || s.startsWith("KB", i)) {
                        withStyle(SpanStyle(color = PrimaryCyan)) { append(s.substring(i, i + 2)) }
                        i += 2
                    } else if (s[i] == '.') {
                        withStyle(SpanStyle(color = PrimaryCyan)) { append(".") }
                        i++
                    } else {
                        // Collect digits and other chars
                        val start = i
                        while (i < s.length && !s.startsWith("GB", i) && !s.startsWith("MB", i) && !s.startsWith("TB", i) && !s.startsWith("KB", i) && s[i] != '.') {
                            i++
                        }
                        withStyle(SpanStyle(color = Color.White)) { append(s.substring(start, i)) }
                    }
                }
            }
            Text(
                text = volAnnotated,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = SoraFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (volumeText != null && daysText != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .width(1.dp)
                    .height(18.dp)
                    .background(TextSecondary.copy(alpha = 0.22f))
            )
        }
        if (daysText != null) {
            // Split days text: the number stays white, "Day"/"Days" goes cyan.
            val dayAnnotated = buildAnnotatedString {
                val num = meta.daysNum ?: ""
                val unit = daysText.removePrefix(num).trim()
                if (num.isNotBlank()) {
                    withStyle(SpanStyle(color = Color.White)) { append(num) }
                    if (unit.isNotBlank()) {
                        withStyle(SpanStyle(color = PrimaryCyan)) { append(" $unit") }
                    }
                } else {
                    withStyle(SpanStyle(color = Color.White)) { append(daysText) }
                }
            }
            Text(
                text = dayAnnotated,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = SoraFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (volumeText == null && daysText == null) {
            Text("Subscription", color = TextSecondary, fontSize = 14.sp, fontFamily = SoraFamily)
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
            fontFamily = SoraFamily
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
        fontFamily = SoraFamily
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
            .fillMaxWidth(),
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
                    .clickable {
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
                    fontFamily = SoraFamily
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
    onShowToast: (String) -> Unit,
    onImportWireGuardUri: ((android.net.Uri) -> Unit)? = null
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

    val editingWg = remember(editingProfile) {
        if (editingProfile != null && editingProfile.protocol == "WireGuard") {
            try {
                dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig.fromConfig(editingProfile.serverAddress)
            } catch (_: Exception) { null }
        } else null
    }

    var wgName by remember(editingWg) { mutableStateOf(editingProfile?.name ?: editingWg?.remark ?: "") }
    var wgSecretKey by remember(editingWg) { mutableStateOf(editingWg?.secretKey ?: "") }
    val wgInterfacePublicKey = remember(wgSecretKey) { dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardKeyUtils.derivePublicKey(wgSecretKey.trim()) ?: "" }
    var showWgSecretKey by remember(editingWg) { mutableStateOf(false) }
    var wgSecretKeyHistory by remember(editingWg) { mutableStateOf<List<String>>(emptyList()) }
    var showRegenerateWgKeyDialog by remember(editingWg) { mutableStateOf(false) }
    var localSheetToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(localSheetToast) {
        if (localSheetToast != null) {
            kotlinx.coroutines.delay(2200L)
            localSheetToast = null
        }
    }
    var wgLocalAddress by remember(editingWg) { mutableStateOf(editingWg?.localAddresses?.joinToString(", ") ?: "") }
    var wgListenPort by remember(editingWg) { mutableStateOf(editingWg?.listenPort?.toString() ?: "") }
    var wgDns by remember(editingWg) { mutableStateOf(editingWg?.dns ?: "") }
    var wgMtu by remember(editingWg) { mutableStateOf(editingWg?.mtu?.toString() ?: "") }
    var wgPublicKey by remember(editingWg) { mutableStateOf(editingWg?.publicKey ?: "") }
    var wgPresharedKey by remember(editingWg) { mutableStateOf(editingWg?.preSharedKey ?: "") }
    var wgKeepAlive by remember(editingWg) { mutableStateOf(editingWg?.keepAlive?.takeIf { it > 0 }?.toString() ?: "") }
    var wgEndpoint by remember(editingWg) { mutableStateOf(if (editingWg != null) "${editingWg.endpointHost}:${editingWg.endpointPort}" else "") }
    var wgAllowedIps by remember(editingWg) { mutableStateOf(editingWg?.allowedIps?.joinToString(", ") ?: "") }
    var wgReserved by remember(editingWg) { mutableStateOf(editingWg?.reserved?.joinToString(",") ?: "") }
    var showPeerSection by remember(editingProfile) { mutableStateOf(editingProfile != null) }

    fun applyParsedWg(parsed: dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardParsedConfig, nameOverride: String? = null) {
        wgName = nameOverride?.takeIf { it.isNotBlank() } ?: parsed.remark
        wgSecretKey = parsed.secretKey
        wgLocalAddress = parsed.localAddresses.joinToString(", ")
        wgListenPort = parsed.listenPort?.toString() ?: ""
        wgDns = parsed.dns ?: ""
        wgMtu = parsed.mtu.toString()
        wgPublicKey = parsed.publicKey
        wgPresharedKey = parsed.preSharedKey ?: ""
        wgKeepAlive = if (parsed.keepAlive > 0) parsed.keepAlive.toString() else ""
        wgEndpoint = "${parsed.endpointHost}:${parsed.endpointPort}"
        wgAllowedIps = parsed.allowedIps.joinToString(", ")
        wgReserved = parsed.reserved?.joinToString(",") ?: ""
        showPeerSection = true
    }

    val barcodeScanner = remember {
        try {
            com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
        } catch (_: Throwable) {
            null
        }
    }
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val scanner = barcodeScanner ?: try {
                com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
            } catch (_: Throwable) {
                null
            }
            if (scanner == null) {
                onShowToast("QR scanner is unavailable on this device")
                return@let
            }
            try {
                val image = com.google.mlkit.vision.common.InputImage.fromFilePath(context, it)
                scanner.process(image).addOnSuccessListener { barcodes ->
                    val raw = barcodes.firstOrNull()?.rawValue
                    if (!raw.isNullOrBlank()) {
                        if (currentProtocol == VpnProtocolDisplay.WireGuard) {
                            try {
                                val parsed = dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig.fromConfig(raw)
                                applyParsedWg(parsed)
                                onShowToast("WireGuard config loaded from QR!")
                            } catch (_: Exception) {
                                onShowToast("QR is not a valid WireGuard configuration")
                            }
                        } else {
                            serverAddress = raw
                        }
                    } else {
                        onShowToast("No QR code found in selected image")
                    }
                }.addOnFailureListener {
                    onShowToast("Failed to scan QR code from image")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onShowToast("Error reading image file")
            }
        }
    }

    val wgFileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { fileUri ->
            try {
                var displayName: String? = null
                runCatching {
                    context.contentResolver.query(fileUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) displayName = cursor.getString(idx)
                        }
                    }
                }
                if (displayName.isNullOrBlank()) {
                    displayName = fileUri.lastPathSegment ?: "wireguard.conf"
                }

                val inputStream = context.contentResolver.openInputStream(fileUri)
                if (inputStream == null) {
                    onShowToast("Unable to open selected file")
                    return@rememberLauncherForActivityResult
                }

                val buffered = java.io.BufferedInputStream(inputStream)
                buffered.mark(4)
                val header = ByteArray(4)
                val bytesRead = buffered.read(header)
                buffered.reset()

                val isZip = (bytesRead == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() && header[2] == 0x03.toByte() && header[3] == 0x04.toByte())
                    || displayName.endsWith(".zip", ignoreCase = true)

                if (isZip) {
                    val zipEntries = mutableListOf<Pair<String, String>>()
                    val zipStream = java.util.zip.ZipInputStream(buffered)
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val isDir = entry.isDirectory
                        val isMacMeta = name.contains("__MACOSX") || java.io.File(name).name.startsWith(".")
                        if (!isDir && !isMacMeta && (name.endsWith(".conf", ignoreCase = true) || name.endsWith(".txt", ignoreCase = true))) {
                            val baseName = java.io.File(name).name.substringBeforeLast('.')
                            val baos = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            var len: Int
                            var total = 0
                            while (zipStream.read(buffer).also { len = it } > 0) {
                                baos.write(buffer, 0, len)
                                total += len
                                if (total > 65536) break
                            }
                            val text = baos.toString("UTF-8").trim()
                            if (dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig.isWireGuardConfig(text)) {
                                zipEntries.add(Pair(baseName, text))
                            }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                    zipStream.close()

                    if (zipEntries.isEmpty()) {
                        onShowToast("No WireGuard configs found in archive")
                    } else if (zipEntries.size == 1) {
                        val (baseName, text) = zipEntries.first()
                        val parsed = dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig.parseConf(text, defaultRemark = baseName)
                        applyParsedWg(parsed, nameOverride = baseName)
                        onShowToast("Config loaded from file!")
                    } else {
                        onImportWireGuardUri?.invoke(fileUri)
                        onClose()
                    }
                } else {
                    val baseFileName = displayName.substringBeforeLast('.')
                    val text = buffered.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                    if (dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig.isWireGuardConfig(text)) {
                        val parsed = dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig.parse(text, defaultRemark = baseFileName)
                        applyParsedWg(parsed, nameOverride = baseFileName)
                        onShowToast("Config loaded from file!")
                    } else {
                        onShowToast("Selected file is not a valid WireGuard configuration")
                    }
                }
            } catch (e: Exception) {
                onShowToast(e.message ?: "Failed to read configuration file")
            }
        }
    }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)
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
            fontFamily = SoraFamily
        )
        Spacer(modifier = Modifier.height(24.dp))

        when (currentProtocol) {
            VpnProtocolDisplay.OpenConnect -> {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = {
                        Text(
                            "Profile Name",
                            color = TextSecondary,
                            fontFamily = SoraFamily
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = serverAddress, onValueChange = { serverAddress = it },
                    label = {
                        Text(
                            "Server Address",
                            color = TextSecondary,
                            fontFamily = SoraFamily
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = username, onValueChange = { username = EmojiFilter.strip(it).replace(" ", "") },
                    label = {
                        Text(
                            "Username",
                            color = TextSecondary,
                            fontFamily = SoraFamily
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = EmojiFilter.strip(it).replace(" ", "") },
                    label = {
                        Text(
                            "Password",
                            color = TextSecondary,
                            fontFamily = SoraFamily
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
                    textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }

            VpnProtocolDisplay.WireGuard -> {
                // Top: Interface Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceGlass)
                        .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Interface",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SoraFamily
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = wgName,
                        onValueChange = { wgName = it },
                        label = { Text("Name", color = TextSecondary, fontFamily = SoraFamily) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = wgSecretKey,
                        onValueChange = { wgSecretKey = it.trim() },
                        visualTransformation = if (showWgSecretKey) VisualTransformation.None else PasswordVisualTransformation(),
                        label = { Text("Private key", color = TextSecondary, fontFamily = SoraFamily) },
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                IconButton(
                                    onClick = { showWgSecretKey = !showWgSecretKey }
                                ) {
                                    Icon(
                                        imageVector = if (showWgSecretKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showWgSecretKey) "Hide private key" else "Show private key",
                                        tint = PrimaryCyan
                                    )
                                }
                                IconButton(
                                    onClick = { showRegenerateWgKeyDialog = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Regenerate private key",
                                        tint = PrimaryCyan
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (wgSecretKeyHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFA000).copy(alpha = 0.10f))
                                .border(1.dp, Color(0xFFFFA000).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Key changed — update public key on server",
                                color = Color(0xFFFFD54F),
                                fontSize = 11.sp,
                                fontFamily = SoraFamily,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val lastKey = wgSecretKeyHistory.last()
                                        wgSecretKeyHistory = wgSecretKeyHistory.dropLast(1)
                                        wgSecretKey = lastKey
                                        localSheetToast = "Previous private key restored"
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = PrimaryCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Undo",
                                    color = PrimaryCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SoraFamily
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = if (wgInterfacePublicKey.isNotEmpty()) wgInterfacePublicKey else "(generated)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Public key", color = TextSecondary, fontFamily = SoraFamily) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (wgInterfacePublicKey.isNotEmpty()) {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(wgInterfacePublicKey))
                                        localSheetToast = "Public key copied"
                                    }
                                },
                                enabled = wgInterfacePublicKey.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy public key",
                                    tint = if (wgInterfacePublicKey.isNotEmpty()) PrimaryCyan else TextSecondary.copy(alpha = 0.5f)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = if (wgInterfacePublicKey.isNotEmpty()) TextPrimary else TextSecondary.copy(alpha = 0.7f),
                            unfocusedTextColor = if (wgInterfacePublicKey.isNotEmpty()) TextPrimary else TextSecondary.copy(alpha = 0.7f)
                        ),
                        textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = wgLocalAddress,
                            onValueChange = { wgLocalAddress = it },
                            label = { Text("Addresses", color = TextSecondary, fontFamily = SoraFamily) },
                            placeholder = { Text("10.0.0.2/32", color = Color.Gray, fontFamily = SoraFamily) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                            modifier = Modifier.weight(0.6f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = wgListenPort,
                            onValueChange = { wgListenPort = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Listen port", color = TextSecondary, fontFamily = SoraFamily) },
                            placeholder = { Text("(random)", color = Color.Gray, fontFamily = SoraFamily) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                            modifier = Modifier.weight(0.4f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = wgDns,
                            onValueChange = { wgDns = it },
                            label = { Text("DNS servers", color = TextSecondary, fontFamily = SoraFamily) },
                            placeholder = { Text("1.1.1.1", color = Color.Gray, fontFamily = SoraFamily) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                            modifier = Modifier.weight(0.6f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = wgMtu,
                            onValueChange = { wgMtu = it.filter { ch -> ch.isDigit() } },
                            label = { Text("MTU", color = TextSecondary, fontFamily = SoraFamily) },
                            placeholder = { Text("(auto)", color = Color.Gray, fontFamily = SoraFamily) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                            modifier = Modifier.weight(0.4f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Middle: Add Peer button / Peer Card
                if (!showPeerSection) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceGlass)
                            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                            .clickable { showPeerSection = true }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Add peer",
                            color = PrimaryCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = SoraFamily
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceGlass)
                            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Peer",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = SoraFamily
                            )
                            IconButton(
                                onClick = {
                                    wgPublicKey = ""
                                    wgPresharedKey = ""
                                    wgKeepAlive = ""
                                    wgEndpoint = ""
                                    wgAllowedIps = ""
                                    showPeerSection = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Peer",
                                    tint = TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = wgPublicKey,
                            onValueChange = { wgPublicKey = it.trim() },
                            label = { Text("Public key", color = TextSecondary, fontFamily = SoraFamily) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = wgPresharedKey,
                            onValueChange = { wgPresharedKey = it.trim() },
                            label = { Text("Pre-shared key", color = TextSecondary, fontFamily = SoraFamily) },
                            placeholder = { Text("(optional)", color = Color.Gray, fontFamily = SoraFamily) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = wgKeepAlive,
                            onValueChange = { wgKeepAlive = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Persistent keepalive", color = TextSecondary, fontFamily = SoraFamily) },
                            placeholder = { Text("(optional, not recommended)", color = Color.Gray, fontFamily = SoraFamily) },
                            suffix = { Text("seconds", color = TextSecondary, fontSize = 12.sp, fontFamily = SoraFamily) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = wgEndpoint,
                            onValueChange = { wgEndpoint = it.trim() },
                            label = { Text("Endpoint", color = TextSecondary, fontFamily = SoraFamily) },
                            placeholder = { Text("host:51820", color = Color.Gray, fontFamily = SoraFamily) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = wgAllowedIps,
                            onValueChange = { wgAllowedIps = it },
                            label = { Text("Allowed IPs", color = TextSecondary, fontFamily = SoraFamily) },
                            placeholder = { Text("0.0.0.0/0", color = Color.Gray, fontFamily = SoraFamily) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // Bottom: Import buttons row (matching V2rayNG)
                if (editingProfile == null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { wgFileLauncher.launch("*/*") },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceGlass),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Config File",
                                tint = PrimaryCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Config File", color = Color.White, fontFamily = SoraFamily)
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
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "QR Code",
                                tint = PrimaryCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("QR Code", color = Color.White, fontFamily = SoraFamily)
                        }
                    }
                }
            }

            VpnProtocolDisplay.V2rayNG -> {
                if (editingProfile != null) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Profile Name", color = TextSecondary, fontFamily = SoraFamily) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = serverAddress, onValueChange = { serverAddress = it },
                        label = {
                            Text(
                                "Config String or Subscription Link",
                                color = TextSecondary,
                                fontFamily = SoraFamily
                            )
                        },
                        placeholder = {
                            Text(
                                "vmess://, vless://, http://...",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = SoraFamily
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
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
                            Text("Clipboard", color = Color.White, fontFamily = SoraFamily)
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
                            Text("QR Code", color = Color.White, fontFamily = SoraFamily)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        val isFormValid = when (currentProtocol) {
            VpnProtocolDisplay.V2rayNG -> serverAddress.isNotBlank()
            VpnProtocolDisplay.WireGuard -> {
                val ep = wgEndpoint.trim()
                val hasEndpoint = if (ep.isNotBlank()) {
                    val (h, p) = dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig.parseHostAndPort(ep) ?: Pair("", -1)
                    h.isNotBlank() && p in 1..65535
                } else false
                hasEndpoint && wgSecretKey.isNotBlank() && wgPublicKey.isNotBlank()
            }
            VpnProtocolDisplay.OpenConnect -> name.isNotBlank() && serverAddress.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        }
        val hasChanges = editingProfile == null || (
            if (currentProtocol == VpnProtocolDisplay.WireGuard) {
                true
            } else {
                name != editingProfile.name ||
                serverAddress != editingProfile.serverAddress ||
                username != editingProfile.username ||
                password != editingProfile.password ||
                currentProtocol.title != editingProfile.protocol
            }
        )
        val isButtonEnabled = isFormValid && hasChanges

        Button(
            onClick = {
                if (isButtonEnabled) {
                    if (currentProtocol == VpnProtocolDisplay.WireGuard) {
                        try {
                            val (host, port) = dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig.parseHostAndPort(wgEndpoint.trim())
                                ?: error("Invalid Endpoint. Expected host:port (e.g. 192.0.2.1:51820)")
                            val addrs = wgLocalAddress.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("10.0.0.2/32") }
                            val allowedIpsList = wgAllowedIps.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("0.0.0.0/0") }
                            val parsed = dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardParsedConfig(
                                remark = wgName.trim().ifEmpty { "WireGuard Server" },
                                secretKey = wgSecretKey.trim(),
                                publicKey = wgPublicKey.trim(),
                                preSharedKey = wgPresharedKey.trim().ifEmpty { null },
                                endpointHost = host,
                                endpointPort = port,
                                localAddresses = addrs,
                                listenPort = wgListenPort.trim().toIntOrNull(),
                                dns = wgDns.trim().ifEmpty { null },
                                mtu = wgMtu.trim().toIntOrNull()?.coerceIn(1280, 1500) ?: 1420,
                                keepAlive = wgKeepAlive.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                allowedIps = allowedIpsList,
                                reserved = wgReserved.trim().takeIf { it.isNotEmpty() }?.let { dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardConfig.parseReserved(it) }
                            )
                            parsed.requireValid()
                            val canonicalUri = parsed.toUri()
                            if (editingProfile == null) {
                                val isDuplicate = servers.any { it.protocol == "WireGuard" && it.serverAddress == canonicalUri }
                                if (isDuplicate) {
                                    onShowToast("This configuration already exists!")
                                    return@Button
                                }
                                onAdd(parsed.remark, canonicalUri, "single", "", "WireGuard")
                            } else {
                                onUpdate(
                                    editingProfile.copy(
                                        name = parsed.remark,
                                        serverAddress = canonicalUri,
                                        protocol = "WireGuard"
                                    )
                                )
                            }
                            onClose()
                        } catch (e: Exception) {
                            onShowToast(e.message ?: "Invalid WireGuard configuration")
                        }
                        return@Button
                    }

                    if (editingProfile == null) {
                        val isDuplicate = servers.any {
                            when (currentProtocol) {
                                VpnProtocolDisplay.OpenConnect -> it.protocol == "OpenConnect" && it.serverAddress == serverAddress
                                VpnProtocolDisplay.V2rayNG -> {
                                    val input = serverAddress.trim()
                                    if (input.startsWith("http://") || input.startsWith("https://")) {
                                        it.username == "sub:$input"
                                    } else {
                                        it.protocol == "v2rayNG" && it.serverAddress == serverAddress
                                    }
                                }
                                VpnProtocolDisplay.WireGuard -> false
                            }
                        }
                        if (isDuplicate) {
                            onShowToast("This configuration already exists!")
                            return@Button
                        }
                        val finalPassword = if (currentProtocol == VpnProtocolDisplay.V2rayNG && (serverAddress.startsWith("http://") || serverAddress.startsWith("https://"))) serverAddress else EmojiFilter.strip(password)
                        onAdd(name, serverAddress, EmojiFilter.strip(username), finalPassword, currentProtocol.title)
                    } else {
                        onUpdate(
                            editingProfile.copy(
                                name = name,
                                serverAddress = serverAddress,
                                username = if (currentProtocol == VpnProtocolDisplay.OpenConnect) EmojiFilter.strip(username) else username,
                                password = if (currentProtocol == VpnProtocolDisplay.OpenConnect) EmojiFilter.strip(password) else password,
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
                fontFamily = SoraFamily
            )
        }
    }

        CustomGlassToast(
            message = localSheetToast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp)
        )
    }

    if (showRegenerateWgKeyDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showRegenerateWgKeyDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
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
                            Text(
                                text = "Generate New Private Key?",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = SoraFamily
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Generating a new private key changes your derived public key. You must register the new public key on your WireGuard server, otherwise the VPN connection will not work.",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontFamily = SoraFamily
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                            ) {
                                Button(
                                    onClick = { showRegenerateWgKeyDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Cancel",
                                        color = TextSecondary,
                                        fontFamily = SoraFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Button(
                                    onClick = {
                                        if (wgSecretKey.isNotBlank()) {
                                            wgSecretKeyHistory = wgSecretKeyHistory + wgSecretKey
                                        }
                                        wgSecretKey = dev.rouzbeh.hybridvpn.core.vpn.wireguard.WireGuardKeyUtils.generatePrivateKey()
                                        showRegenerateWgKeyDialog = false
                                        localSheetToast = "New key generated — copy public key to server"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Generate",
                                        color = BackgroundDark,
                                        fontFamily = SoraFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
        fontFamily = SoraFamily,
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
    // Always animate ("...") during transitioning and whenever the IP/location
    // is still unresolved ("Detecting...") — both after connecting and after
    // disconnecting — so the card transitions directly from "..." to the
    // resolved IP, location, and flag together without flashing "Detecting...".
    val isUnresolved = networkInfo.ip == "Detecting..." || networkInfo.ip == "Unknown" || networkInfo.ip == "0.0.0.0" || networkInfo.ip == "127.0.0.1" || networkInfo.location == "Detecting..."
    val showLoading = isTransitioning || isUnresolved
    // Coarse "which flag" key: drive AnimatedContent so the new VPN flag
    // cross-fades together with the IP and location text in one atomic step.
    val context = LocalContext.current
    val effectiveFlagCode = remember(networkInfo.flagCode, networkInfo.location) {
        val normalized = BundledFlags.normalizeCode(networkInfo.flagCode)
        if (normalized != null) {
            normalized
        } else if (networkInfo.location.isNotBlank() && networkInfo.location != "Unknown" && networkInfo.location != "Detecting..." && !networkInfo.location.contains("Unavailable")) {
            val locName = networkInfo.location
            val resolved = java.util.Locale.getISOCountries().firstOrNull { iso ->
                val loc = java.util.Locale("", iso)
                loc.displayCountry.equals(locName, ignoreCase = true) ||
                loc.getDisplayCountry(java.util.Locale.ENGLISH).equals(locName, ignoreCase = true)
            }?.lowercase()
            BundledFlags.normalizeCode(resolved) ?: "un"
        } else {
            "un"
        }
    }
    val flagKey = remember(effectiveFlagCode, showLoading) {
        if (showLoading) "__loading__" else effectiveFlagCode
    }

    val customFont = SoraFamily
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
            AnimatedContent(
                targetState = flagKey,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                label = "flag_crossfade",
                modifier = Modifier.matchParentSize()
            ) { key ->
                val flagBitmap = remember(context, key) {
                    if (key != "un" && key != "__loading__" && key.isNotBlank()) {
                        BundledFlags.getFlagBitmap(context, key)
                    } else {
                        null
                    }
                }
                if (flagBitmap != null) {
                    Box(modifier = Modifier.matchParentSize()) {
                        Box(Modifier.matchParentSize().background(SurfaceDark))
                        androidx.compose.foundation.Image(
                            bitmap = flagBitmap,
                            contentDescription = "Flag Background",
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(contrastMatrix),
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(0.9f)
                        )
                    }
                } else {
                    // No flag (loading / un / unmapped) — keep a muted placeholder so the
                    // gradient animation doesn't flash a missing image.
                    Box(Modifier.matchParentSize().background(SurfaceDark))
                }
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
                // Retry / spinner at TopEnd — computed first so IP row can reserve space.
                val hasError = networkInfo.ip == "Error" || networkInfo.location == "Failed to fetch IP" || networkInfo.location.contains("Error")
                val isExitUnavailable = networkInfo.ip == "Unavailable" ||
                    networkInfo.ip == "Unknown" ||
                    networkInfo.location == "VPN exit unavailable" ||
                    networkInfo.location == "Unavailable" ||
                    networkInfo.location.contains("VPN exit unavailable")
                val showRetryIcon = isConnected && (hasError || isExitUnavailable) && !showLoading
                val showSpinner = isConnected && showLoading && isUnresolved

                // Reserve 28dp at end when retry/spinner is visible so IP row never overlaps it.
                val ipRowEndPadding = if (showRetryIcon || showSpinner) 28.dp else 0.dp

                // Top: IP Address
                // Always keep targetState a Pair<String, Boolean> so AnimatedContent
                // never has to reconcile two different state types during the
                // loading<->resolved crossfade (that mismatch caused a
                // ClassCastException crash). A sentinel first value marks loading.
                AnimatedContent(
                    targetState = if (showLoading) ("__loading__" to false) else (networkInfo.ip to ipVisible),
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                    label = "ip_crossfade",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(end = ipRowEndPadding)
                ) { ipState ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isConnected) "Virtual IP: " else "Public IP: ",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            style = textShadow
                        )
                        if (ipState.first == "__loading__") {
                            SmoothTypingDots(textStyle = textShadow)
                        } else {
                            val rawIp = ipState.first
                            val isDetecting = rawIp == "Detecting..." || (networkInfo.location == "Detecting..." && (rawIp == "Unknown" || rawIp == "0.0.0.0" || rawIp.isBlank()))
                            val isUnavailable = !isDetecting && (rawIp == "Unavailable" || rawIp == "Unknown" || rawIp == "0.0.0.0" || rawIp.isBlank())
                            val ip = when {
                                isDetecting -> "Detecting..."
                                isUnavailable -> "Unavailable"
                                else -> rawIp
                            }
                            val visible = ipState.second
                            val displayIp = if (visible || isDetecting || isUnavailable) ip else ip.replace(
                                Regex("[A-Za-z0-9]"),
                                "*"
                            )
                            val ipFontSize = when {
                                ip.length > 32 -> 10.sp
                                ip.length > 24 -> 11.sp
                                ip.length > 18 -> 12.sp
                                else -> 14.sp
                            }
                            Text(
                                text = displayIp,
                                color = Color.White,
                                fontSize = ipFontSize,
                                fontWeight = FontWeight.Bold,
                                style = textShadow,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (!isDetecting && !isUnavailable) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle IP",
                                    tint = PrimaryCyan,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .bounceClick { ipVisible = !ipVisible }
                                )
                            }
                        }
                    }
                }

                // Bottom: Location — same fix as the IP row: a stable
                // Pair<String, Boolean> targetState (second = loading flag) so the
                // content lambda never receives a bare String mid-crossfade.
                AnimatedContent(
                    targetState = if (showLoading) ("__loading__" to true) else (networkInfo.location to false),
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                    label = "location_crossfade",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                ) { locState ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Location: ",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            style = textShadow
                        )
                        if (locState.second) {
                            SmoothTypingDots(textStyle = textShadow)
                        } else {
                            Text(
                                text = locState.first,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                style = textShadow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
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
                }

                // Top Right: Retry / Spinner
                if (showRetryIcon) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry Fetch IP",
                        tint = PrimaryCyan,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp)
                            .bounceClick { onRetryClick() }
                    )
                } else if (showSpinner) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp),
                        color = PrimaryCyan,
                        strokeWidth = 2.dp
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
                fontFamily = SoraFamily
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentMode,
                color = PrimaryCyan,
                fontSize = if (currentMode.contains("except")) 12.5.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                fontFamily = SoraFamily
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 24.dp)
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
            "Split Tunneling Settings", color = Color.White, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, fontFamily = SoraFamily, modifier = Modifier.padding(horizontal = 24.dp)
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
                        fontWeight = FontWeight.Bold,
                        fontFamily = SoraFamily
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // ... (top bar code stays same)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
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
                    fontWeight = FontWeight.Bold,
                    fontFamily = SoraFamily
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
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        if (showSystemApps) "Hide System Apps" else "Show System Apps",
                                        color = Color.White,
                                        fontFamily = SoraFamily
                                    )
                                },
                                onClick = { showSystemApps = !showSystemApps; showMenu = false }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Select All", color = Color.White, fontFamily = SoraFamily) },
                                onClick = { onSelectAllApps(); showMenu = false }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Deselect All", color = Color.White, fontFamily = SoraFamily) },
                                onClick = { onDeselectAllApps(); showMenu = false }
                            )
                        }
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
                        fontFamily = SoraFamily,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "RECONNECT",
                        color = PrimaryCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SoraFamily,
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
                                fontWeight = FontWeight.Bold,
                                fontFamily = SoraFamily
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Info,
                                contentDescription = "Info",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .bounceClick {
                                        toastMessage = "Disable this option to have\nall apps use the VPN."
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
                                fontWeight = FontWeight.Bold,
                                fontFamily = SoraFamily
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
                                            if (isBypassMode) {
                                                "Disable this option to only allow apps you select to use the VPN."
                                            } else {
                                                "Enable this option to prevent apps you select from using the VPN and allow other apps you don't select to use the VPN."
                                            }
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
                        fontSize = 14.sp,
                        fontFamily = SoraFamily,
                        textDirection = TextDirection.Ltr
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
                                    fontSize = 14.sp,
                                    fontFamily = SoraFamily
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
                                fontFamily = SoraFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                app.packageName,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = SoraFamily,
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
            .padding(top = 8.dp),
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
                    fontFamily = SoraFamily
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
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
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
                fontFamily = SoraFamily,
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
    onTestAllV2rayPing: (String) -> Unit,
    onSortV2ray: (String) -> Unit,
    onDeleteInvalidV2ray: (String) -> Unit,
    onUpdateSub: (String) -> Unit,
    v2rayPingTabs: Set<String> = emptySet(),
    onEdit: (VpnProfile) -> Unit,
    onNavigateBack: () -> Unit,
    toastMessage: String? = null,
    onShowToast: (String) -> Unit = {},
    subscriptionToastFlow: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.emptyFlow()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("tab_prefs", android.content.Context.MODE_PRIVATE) }

    // Local toast scoped to THIS server-list screen. Subscription-update and
    // other messages surface here and self-clear, instead of relying on the
    // shared dashboardToastMessage which leaks into other methods' lists.
    var localToast by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(localToast) {
        if (localToast != null) {
            kotlinx.coroutines.delay(4000)
            localToast = null
        }
    }
    // Subscription-update results arrive on their own flow so they only render
    // here — never in the shared toast that other methods' lists also show.
    androidx.compose.runtime.LaunchedEffect(subscriptionToastFlow) {
        subscriptionToastFlow.collect { localToast = it }
    }

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

    // 2. Smart Default Tab Logic (Remembers last viewed tab or avoids empty SingleLinks if subs exist)
    var selectedTab by androidx.compose.runtime.remember { 
        val saved = prefs.getString("last_selected_tab", null)
        val initial = if (saved != null && (saved == "SingleLinks" || subKeys.contains(saved))) {
            saved
        } else if (singleLinks.isNotEmpty() || subKeys.isEmpty()) {
            "SingleLinks"
        } else {
            subKeys.first()
        }
        mutableStateOf(initial)
    }

    androidx.compose.runtime.LaunchedEffect(subKeys, singleLinks.size) {
        if (selectedTab != "SingleLinks" && !subKeys.contains(selectedTab)) {
            val fallback = if (singleLinks.isNotEmpty() || subKeys.isEmpty()) "SingleLinks" else subKeys.first()
            selectedTab = fallback
            prefs.edit().putString("last_selected_tab", fallback).apply()
        } else if (prefs.getString("last_selected_tab", null) == null && selectedTab == "SingleLinks" && singleLinks.isEmpty() && subKeys.isNotEmpty()) {
            selectedTab = subKeys.first()
            prefs.edit().putString("last_selected_tab", subKeys.first()).apply()
        }
    }

    androidx.compose.runtime.LaunchedEffect(selectedTab) {
        prefs.edit().putString("last_selected_tab", selectedTab).apply()
    }

    val displayServers = if (protocol == "v2rayNG") {
        if (selectedTab == "SingleLinks") singleLinks else subGroups[selectedTab] ?: emptyList()
    } else {
        servers
    }

    // Leading subscription-info block for the visible tab: only the contiguous
    // run of info entries at the very top is consumed and merged into ONE pinned
    // box (the first entry renders it; the rest are hidden from the list).
    val leadingInfoProfiles = androidx.compose.runtime.remember(displayServers) {
        val infoNames = leadingSubscriptionInfoNames(displayServers.map { it.name })
        displayServers.take(infoNames.size).filter { isSubscriptionInfoName(it.name) }
    }
    val leadingInfoIds = leadingInfoProfiles.map { it.id }.toSet()
    val firstInfoId = leadingInfoProfiles.firstOrNull()?.id
    val visibleServers = androidx.compose.runtime.remember(displayServers, leadingInfoIds, firstInfoId) {
        displayServers.filter { profile -> !leadingInfoIds.contains(profile.id) || profile.id == firstInfoId }
    }
    val mergedSubMeta = androidx.compose.runtime.remember(displayServers) {
        parseSubscriptionMeta(displayServers.map { it.name })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
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
                Text(
                    when (protocol) {
                        "v2rayNG" -> "v2rayNG Server List"
                        "WireGuard" -> "WireGuard Server List"
                        else -> "AnyConnect Server List"
                    },
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = SoraFamily
                )
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
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                // Every action captures the currently-selected tab at tap time
                                // so a mid-operation tab switch can't redirect it elsewhere.
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Sorting by test results", color = Color.White, fontFamily = SoraFamily) },
                                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Sort, contentDescription = null, tint = Color.White) },
                                    onClick = { val tab = selectedTab; showV2rayMenu = false; onSortV2ray(tab) }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Test Real delay config", color = Color.White, fontFamily = SoraFamily) },
                                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Speed, contentDescription = null, tint = Color.White) },
                                    onClick = { val tab = selectedTab; showV2rayMenu = false; onTestAllV2rayPing(tab) }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Update subscription", color = Color.White, fontFamily = SoraFamily) },
                                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Sync, contentDescription = null, tint = Color.White) },
                                    onClick = { val tab = selectedTab; showV2rayMenu = false; if (tab != "SingleLinks") onUpdateSub(tab) }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Delete invalid config", color = Color(0xFFE53935), fontFamily = SoraFamily) },
                                    leadingIcon = { Icon(androidx.compose.material.icons.Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFE53935)) },
                                    onClick = { val tab = selectedTab; showV2rayMenu = false; onDeleteInvalidV2ray(tab) }
                                )
                            }
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
                Text(text = "Done", color = PrimaryCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = SoraFamily, modifier = Modifier.bounceClick { isReorderMode = false }.padding(8.dp))
            }
        }

        if (protocol == "v2rayNG") {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // CRITICAL FIX: key = { it } prevents the visual state inversion bug
                items(sortedTabs, key = { it }) { tabKey ->
                    // Server count excludes consumed leading info entries — only
                    // real selectable/connectable servers are counted.
                    val count = if (tabKey == "SingleLinks") {
                        singleLinks.size - leadingSubscriptionInfoNames(singleLinks.map { it.name }).size
                    } else {
                        val group = subGroups[tabKey] ?: emptyList()
                        group.size - leadingSubscriptionInfoNames(group.map { it.name }).size
                    }
                    val customName = if (tabKey == "SingleLinks") "SingleLinks"
                        else subGroups[tabKey]?.firstOrNull()?.subName?.takeIf { it.isNotBlank() } ?: "SubLink ${subKeys.indexOf(tabKey) + 1}"
                    val isTabPinging = v2rayPingTabs.contains(tabKey)

                    Box {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isTabPinging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(11.dp),
                                    strokeWidth = 1.5.dp,
                                    color = PrimaryCyan
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = "$customName ($count)",
                                color = if (selectedTab == tabKey) PrimaryCyan else TextSecondary,
                                fontWeight = if (selectedTab == tabKey) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = SoraFamily,
                                modifier = Modifier
                                    .bounceClick(onLongClick = { selectedTab = tabKey; expandedTabMenu = tabKey }) { selectedTab = tabKey }
                                    .padding(8.dp)
                            )
                        }

                        androidx.compose.material3.DropdownMenu(
                            expanded = expandedTabMenu == tabKey,
                            onDismissRequest = { expandedTabMenu = null },
                            modifier = Modifier.background(SurfaceDark).border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Reorder", color = Color.White, fontFamily = SoraFamily) },
                                    leadingIcon = { Icon(TelegramReorderIcon, contentDescription = null, tint = Color.White) },
                                    onClick = { expandedTabMenu = null; isReorderMode = true }
                                )
                                if (tabKey != "SingleLinks") {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Rename", color = Color.White, fontFamily = SoraFamily) },
                                        leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                                        onClick = { expandedTabMenu = null; renamingSubKey = tabKey }
                                    )
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Delete Sub", color = Color(0xFFE53935), fontFamily = SoraFamily) },
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
        }

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The leading info block merges into a single pinned box: render only
            // its first entry (carrying the merged meta); the remaining info
            // entries are hidden entirely — not selectable, connectable, pingable,
            // or counted. Info ids are also excluded from the list below.
            items(visibleServers, key = { it.id }) { profile ->
                val isInfoEntry = leadingInfoIds.contains(profile.id)
                val isSelected = !isInfoEntry && profile.id == activeProfile?.id
                val isSub = profile.username.startsWith("sub:")
                // Only the first leading info entry renders the merged box; a
                // non-leading info-looking name (a real server) is left alone.
                val isInfoCard = isInfoEntry && profile.id == firstInfoId
                val groupLabel = if (isInfoCard) {
                    (subGroups[profile.username]?.firstOrNull()?.subName?.takeIf { it.isNotBlank() })
                        ?: "Subscription"
                } else "Subscription"
                ServerItemCard(
                    profile = profile,
                    isSelected = isSelected,
                    connectionState = connectionState,
                    onClick = { onSelect(profile.id); onNavigateBack() },
                    onDelete = if (isSub) null else { { onDelete(profile) } },
                    onEdit = if (isSub) null else { { onEdit(profile) } },
                    onPingClick = { onTestSinglePing(profile) },
                    onShowToast = onShowToast,
                    subscriptionMeta = if (isInfoCard) mergedSubMeta else null,
                    subscriptionLabel = groupLabel
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

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { renamingSubKey = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(horizontal = 24.dp)
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
                        Text("Rename Subscription", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = SoraFamily)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Subscription Name", color = TextSecondary, fontFamily = SoraFamily) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = TextStyle(fontFamily = SoraFamily, textDirection = TextDirection.Ltr),
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
                                fontFamily = SoraFamily
                            )
                        }
                    }
                }
            }
        }
    }

    // NEW: Render the custom toast over the server list. Uses the screen-local
    // toast so messages don't bleed into other protocol server lists.
    CustomGlassToast(
        message = localToast ?: toastMessage,
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
    // Title: Sora font; log lines: keep Monospace (user explicitly requested).
    val titleFont = SoraFamily
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var copyToastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copyToastMessage) {
        if (copyToastMessage != null) {
            kotlinx.coroutines.delay(2000L)
            copyToastMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
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
                    fontWeight = FontWeight.Bold,
                    fontFamily = titleFont
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ContentCopy,
                    contentDescription = "Copy Logs",
                    tint = PrimaryCyan,
                    modifier = Modifier
                        .size(26.dp)
                        .bounceClick {
                            if (logs.isNotEmpty()) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(logs.joinToString("\n")))
                                copyToastMessage = "Logs copied to clipboard"
                            } else {
                                copyToastMessage = "No logs to copy"
                            }
                        }
                )
                Spacer(modifier = Modifier.width(16.dp))
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
                                fontFamily = SoraFamily
                            )
                        }
                    }
                }
            }
        }
        CustomGlassToast(
            message = copyToastMessage,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
