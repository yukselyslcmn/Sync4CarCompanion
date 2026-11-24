package com.sync4.carcompanion

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sync4.carcompanion.mirror.ScreenMirrorService
import com.sync4.carcompanion.state.BrowserState
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorProfile
import com.sync4.carcompanion.state.MirrorStatus
import com.sync4.carcompanion.state.VehicleModel
import com.sync4.carcompanion.ui.theme.Sync4CarCompanionTheme

private const val TAG = "CarCompanionMain"
private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CompanionStateRepository.init(applicationContext)
        enableEdgeToEdge()

        setContent {
            Sync4CarCompanionTheme(darkTheme = true) {
                CompanionAppRoot(
                    onToggleSystemBars = { fullscreen ->
                        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                        if (fullscreen) {
                            insetsController.systemBarsBehavior =
                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                            insetsController.hide(WindowInsetsCompat.Type.systemBars())
                        } else {
                            insetsController.show(WindowInsetsCompat.Type.systemBars())
                        }
                    }
                )
            }
        }
    }
}

private enum class CompanionTab(val label: String) {
    DASHBOARD("Kontrol Paneli"),
    BROWSER("Tarayıcı & Video")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanionAppRoot(
    onToggleSystemBars: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val selectedVehicle by CompanionStateRepository.selectedVehicle.collectAsStateWithLifecycle()
    val mirrorStatus by CompanionStateRepository.mirrorStatus.collectAsStateWithLifecycle()
    val selectedProfile by CompanionStateRepository.selectedProfile.collectAsStateWithLifecycle()
    val browserState by CompanionStateRepository.browserState.collectAsStateWithLifecycle()
    val isCarConnected by CompanionStateRepository.isCarConnected.collectAsStateWithLifecycle()
    val pendingUrl by CompanionStateRepository.pendingNavigationUrl.collectAsStateWithLifecycle()

    var activeTabIndex by rememberSaveable { mutableIntStateOf(CompanionTab.DASHBOARD.ordinal) }
    val activeTab = CompanionTab.entries[activeTabIndex]

    var hasNotificationPermission by remember {
        mutableStateOf(checkNotificationPermission(context))
    }
    var pendingMirrorAfterNotifPermission by remember { mutableStateOf(false) }

    // HTML5 Tam ekran video görünümü (WebChromeClient.onShowCustomView)
    var fullscreenCustomView by remember { mutableStateOf<View?>(null) }
    var fullscreenCustomViewCallback by remember {
        mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
    }

    // Ekran yansıtma veya tam ekran video aktifken telefon ekranının zaman aşımıyla kapanmasını önle
    val hostView = LocalView.current
    val shouldKeepScreenOn = mirrorStatus is MirrorStatus.Active ||
        mirrorStatus is MirrorStatus.Starting ||
        fullscreenCustomView != null
    DisposableEffect(hostView, shouldKeepScreenOn) {
        hostView.keepScreenOn = shouldKeepScreenOn
        onDispose {
            hostView.keepScreenOn = false
        }
    }

    // Sekmeler arasında geçiş yapıldığında video akışının kesilmemesi için WebView örneğini koruyoruz;
    // render süreci çökerse yalnızca WebView örneğini yeniliyoruz.
    var webViewGeneration by remember { mutableIntStateOf(0) }
    val webView = remember(webViewGeneration) {
        createConfiguredWebView(
            context = context,
            initialUrl = browserState.currentUrl,
            desktopMode = browserState.isDesktopMode,
            onEnterFullscreen = { view, callback ->
                fullscreenCustomView = view
                fullscreenCustomViewCallback = callback
                onToggleSystemBars(true)
            },
            onExitFullscreen = {
                fullscreenCustomView = null
                fullscreenCustomViewCallback = null
                onToggleSystemBars(false)
            },
            onRenderProcessGone = {
                fullscreenCustomView = null
                fullscreenCustomViewCallback = null
                onToggleSystemBars(false)
                Toast.makeText(
                    context,
                    "Tarayıcı yeniden başlatılıyor...",
                    Toast.LENGTH_SHORT
                ).show()
                webViewGeneration++
            }
        )
    }

    // Yaşam döngüsü takibi: Bildirim izni durumunu ve WebView durumunu güncel tut
    DisposableEffect(lifecycleOwner, webView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasNotificationPermission = checkNotificationPermission(context)
                    webView.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    webView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.stopLoading()
            webView.destroy()
        }
    }

    // Araç ekranından (Fiat Uconnect 7" veya Ford SYNC 4) gelen URL isteklerini yakala
    LaunchedEffect(pendingUrl) {
        val targetUrl = CompanionStateRepository.consumePendingUrl()
        if (targetUrl != null) {
            activeTabIndex = CompanionTab.BROWSER.ordinal
            webView.loadUrl(targetUrl)
        }
    }

    // Android 14/15/16+ (API 34+): Her yeni yansıtma oturumu için taze MediaProjection izni alınır
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            CompanionStateRepository.setMirrorStatus(MirrorStatus.Starting)
            ScreenMirrorService.start(
                context = context,
                resultCode = result.resultCode,
                resultData = data,
                profile = selectedProfile
            )
        } else {
            Toast.makeText(
                context,
                "Ekran yakalama izni iptal edildi.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val launchScreenCaptureDialog: () -> Unit = {
        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    // İki sistem izin penceresinin aynı anda çakışmasını önlemek için sıralı izin akışı
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (pendingMirrorAfterNotifPermission) {
            pendingMirrorAfterNotifPermission = false
            launchScreenCaptureDialog()
        }
    }

    val requestMirrorStart: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            pendingMirrorAfterNotifPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchScreenCaptureDialog()
        }
    }

    // Geri tuşu yönetimi: Önce tam ekran videodan çık, sonra WebView geçmişinde geri git,
    // geçmiş yoksa Tarayıcı sekmesinden Kontrol Paneli sekmesine dön.
    BackHandler(
        enabled = fullscreenCustomView != null || activeTab == CompanionTab.BROWSER
    ) {
        when {
            fullscreenCustomView != null -> {
                fullscreenCustomViewCallback?.onCustomViewHidden()
                fullscreenCustomView = null
                fullscreenCustomViewCallback = null
                onToggleSystemBars(false)
            }
            activeTab == CompanionTab.BROWSER && webView.canGoBack() -> {
                webView.goBack()
            }
            activeTab == CompanionTab.BROWSER -> {
                CompanionStateRepository.updateBrowserState { it.copy(canGoBack = false) }
                activeTabIndex = CompanionTab.DASHBOARD.ordinal
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Araç Companion",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedVehicle.displayName} • ${selectedVehicle.systemName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        StatusPill(
                            connectedLabel = selectedVehicle.connectedBadge,
                            isConnected = isCarConnected,
                            isMirroring = mirrorStatus is MirrorStatus.Active
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = activeTab == CompanionTab.DASHBOARD,
                        onClick = { activeTabIndex = CompanionTab.DASHBOARD.ordinal },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.DirectionsCar,
                                contentDescription = CompanionTab.DASHBOARD.label
                            )
                        },
                        label = { Text(CompanionTab.DASHBOARD.label) }
                    )
                    NavigationBarItem(
                        selected = activeTab == CompanionTab.BROWSER,
                        onClick = { activeTabIndex = CompanionTab.BROWSER.ordinal },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = CompanionTab.BROWSER.label
                            )
                        },
                        label = { Text(CompanionTab.BROWSER.label) }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    CompanionTab.DASHBOARD -> {
                        DashboardPane(
                            selectedVehicle = selectedVehicle,
                            mirrorStatus = mirrorStatus,
                            selectedProfile = selectedProfile,
                            isCarConnected = isCarConnected,
                            hasNotificationPermission = hasNotificationPermission,
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            },
                            onSelectVehicle = CompanionStateRepository::selectVehicle,
                            onSelectProfile = CompanionStateRepository::selectProfile,
                            onStartMirror = requestMirrorStart,
                            onStopMirror = { ScreenMirrorService.stop(context) },
                            onOpenBookmark = { url ->
                                CompanionStateRepository.requestOpenUrl(url)
                            }
                        )
                    }

                    CompanionTab.BROWSER -> {
                        BrowserPane(
                            webView = webView,
                            browserState = browserState,
                            onNavigateToUrl = { rawInput ->
                                val normalized = CompanionStateRepository.normalizeUrl(rawInput)
                                webView.loadUrl(normalized)
                            },
                            onToggleDesktopMode = {
                                val nextDesktop = !browserState.isDesktopMode
                                webView.settings.userAgentString =
                                    if (nextDesktop) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
                                webView.settings.useWideViewPort = true
                                webView.settings.loadWithOverviewMode = nextDesktop
                                CompanionStateRepository.updateBrowserState {
                                    it.copy(isDesktopMode = nextDesktop)
                                }
                                webView.reload()
                            }
                        )
                    }
                }
            }
        }

        // Tam ekran HTML5 video katmanı
        fullscreenCustomView?.let { videoView ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { container ->
                    if (videoView.parent !== container) {
                        (videoView.parent as? ViewGroup)?.removeView(videoView)
                        container.removeAllViews()
                        container.addView(
                            videoView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun StatusPill(
    connectedLabel: String,
    isConnected: Boolean,
    isMirroring: Boolean
) {
    val dotColor = when {
        isMirroring -> Color(0xFF34D399)
        isConnected -> Color(0xFF38BDF8)
        else -> Color(0xFF64748B)
    }
    val statusText = when {
        isMirroring -> "Yansıtma Aktif"
        isConnected -> connectedLabel
        else -> "Hazır"
    }

    Surface(
        modifier = Modifier.padding(end = 12.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardPane(
    selectedVehicle: VehicleModel,
    mirrorStatus: MirrorStatus,
    selectedProfile: MirrorProfile,
    isCarConnected: Boolean,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onSelectVehicle: (VehicleModel) -> Unit,
    onSelectProfile: (MirrorProfile) -> Unit,
    onStartMirror: () -> Unit,
    onStopMirror: () -> Unit,
    onOpenBookmark: (String) -> Unit
) {
    val isMirrorActive = mirrorStatus is MirrorStatus.Active || mirrorStatus is MirrorStatus.Starting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bildirim İzni Uyarısı (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Ön plan yansıtma kontrol bildirimi için izin verin.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onRequestNotificationPermission) {
                        Text("İzin Ver")
                    }
                }
            }
        }

        // Araç Modeli Seçimi Kartı (Fiat Egea 2020 1.4 Fire & Ford Focus SYNC 4)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Araç Modeli & Multimedya Profili",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Aracınızı seçtiğinizde ideal ekran çözünürlüğü otomatik ayarlanır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                VehicleModel.entries.forEach { vehicle ->
                    val isSelected = vehicle == selectedVehicle
                    OutlinedCard(
                        onClick = { onSelectVehicle(vehicle) },
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vehicle.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${vehicle.systemName} • Önerilen: ${vehicle.recommendedProfile.width}×${vehicle.recommendedProfile.height}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Seçili",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Usb,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedVehicle.connectionTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // MediaProjection Ekran Yansıtma Kartı
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isMirrorActive) {
                            Icons.Rounded.CastConnected
                        } else {
                            Icons.Rounded.Cast
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "MediaProjection Ekran Yansıtma",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when (mirrorStatus) {
                                MirrorStatus.Idle -> "Hazır • Henüz ekran yakalama başlatılmadı"
                                MirrorStatus.Starting -> "Ön plan servisi ve VirtualDisplay hazırlanıyor..."
                                is MirrorStatus.Active ->
                                    "Aktif (${mirrorStatus.profile.width}×${mirrorStatus.profile.height}) • ${mirrorStatus.capturedFrames} kare işlendi"
                                is MirrorStatus.Error -> "Hata: ${mirrorStatus.message}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Çözünürlük Profili (${selectedVehicle.systemName})",
                    style = MaterialTheme.typography.labelLarge
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MirrorProfile.entries.forEach { profile ->
                        FilterChip(
                            selected = selectedProfile == profile,
                            enabled = !isMirrorActive,
                            onClick = { onSelectProfile(profile) },
                            label = { Text(profile.title) }
                        )
                    }
                }

                Text(
                    text = selectedProfile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isMirrorActive) {
                    Button(
                        onClick = onStartMirror,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ekran Yansıtmayı Başlat")
                    }
                } else {
                    Button(
                        onClick = onStopMirror,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StopCircle,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ekran Yansıtmayı Durdur")
                    }
                }
            }
        }

        // Android Auto Bağlantı Durum Kartı
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Android Auto (${selectedVehicle.shortTitle})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = if (isCarConnected) {
                        "${selectedVehicle.displayName} (${selectedVehicle.systemName}) veya DHU şu anda bağlı ve aktif."
                    } else {
                        "Araç bağlantısı bekleniyor. Telefonunuzu ${selectedVehicle.displayName} multimedya sistemine veya DHU emülatörüne bağladığınızda araç ekranı otomatik devreye girer."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Hızlı Web & Video Kısayolları
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Hızlı Web & Video Kaynakları",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Seçilen kaynak dahili WebView sekmesinde tam ekran video desteğiyle açılır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                CompanionStateRepository.defaultBookmarks.forEachIndexed { index, bookmark ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenBookmark(bookmark.url) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookmark.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = bookmark.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserPane(
    webView: WebView,
    browserState: BrowserState,
    onNavigateToUrl: (String) -> Unit,
    onToggleDesktopMode: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var urlInput by remember(browserState.currentUrl) {
        mutableStateOf(browserState.currentUrl)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        enabled = browserState.canGoBack,
                        onClick = { if (webView.canGoBack()) webView.goBack() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                    IconButton(
                        enabled = browserState.canGoForward,
                        onClick = { if (webView.canGoForward()) webView.goForward() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "İleri"
                        )
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = {
                            Text(
                                text = "URL veya arama girin...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = if (urlInput.isNotEmpty()) {
                            {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Adresi Temizle"
                                    )
                                }
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                onNavigateToUrl(urlInput)
                            }
                        )
                    )

                    IconButton(onClick = { webView.reload() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Yenile"
                        )
                    }
                    IconButton(onClick = onToggleDesktopMode) {
                        Icon(
                            imageVector = Icons.Rounded.DesktopWindows,
                            contentDescription = "Masaüstü Modu",
                            tint = if (browserState.isDesktopMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                if (browserState.isLoading) {
                    LinearProgressIndicator(
                        progress = { (browserState.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { container ->
                if (webView.parent !== container) {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    container.removeAllViews()
                    container.addView(
                        webView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    initialUrl: String,
    desktopMode: Boolean,
    onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onExitFullscreen: () -> Unit,
    onRenderProcessGone: () -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Activity yeniden oluşturulduğunda kayıtlı masaüstü modu ile UA uyumlu kalsın
            userAgentString = if (desktopMode) DESKTOP_USER_AGENT else MOBILE_USER_AGENT
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                CompanionStateRepository.updateBrowserState { state ->
                    state.copy(
                        currentUrl = url ?: state.currentUrl,
                        isLoading = true,
                        canGoBack = view?.canGoBack() == true,
                        canGoForward = view?.canGoForward() == true
                    )
                }
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                // YouTube ve TRT tabii gibi tek sayfa (SPA) uygulamalarda geri tuşu ve URL senkronizasyonu
                CompanionStateRepository.updateBrowserState { state ->
                    state.copy(
                        currentUrl = url ?: state.currentUrl,
                        canGoBack = view?.canGoBack() == true,
                        canGoForward = view?.canGoForward() == true
                    )
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CompanionStateRepository.updateBrowserState { state ->
                    state.copy(
                        currentUrl = url ?: state.currentUrl,
                        pageTitle = view?.title?.takeIf { it.isNotBlank() } ?: "Web Tarayıcı",
                        isLoading = false,
                        progress = 100,
                        canGoBack = view?.canGoBack() == true,
                        canGoForward = view?.canGoForward() == true
                    )
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val scheme = request?.url?.scheme?.lowercase() ?: return false
                // Yalnızca http/https adreslerini WebView içinde tut; harici intent şemalarını engelle
                return scheme != "http" && scheme != "https"
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                // Video oynatırken WebView render süreci çökerse (bellek baskısı vb.) tüm
                // uygulamanın kapanmasını önle: bozuk WebView'ı bırakıp ekranı yeniden kur.
                Log.w(TAG, "WebView render süreci sonlandı (crash=${detail?.didCrash()})")
                CompanionStateRepository.updateBrowserState {
                    it.copy(isLoading = false, canGoBack = false, canGoForward = false)
                }
                onRenderProcessGone()
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                CompanionStateRepository.updateBrowserState { state ->
                    state.copy(
                        progress = newProgress,
                        isLoading = newProgress < 100
                    )
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    CompanionStateRepository.updateBrowserState { state ->
                        state.copy(pageTitle = title)
                    }
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    onEnterFullscreen(view, callback)
                } else {
                    super.onShowCustomView(view, callback)
                }
            }

            override fun onHideCustomView() {
                onExitFullscreen()
                super.onHideCustomView()
            }
        }

        loadUrl(initialUrl)
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
