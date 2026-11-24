package com.sync4.carcompanion.state

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * Fiat Egea 2020 (Uconnect 7" HD Live) ve Ford Focus (SYNC 4 13.2") ekranları için
 * optimize edilmiş MediaProjection çözünürlük profilleri.
 */
enum class MirrorProfile(
    val title: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val targetFps: Int
) {
    EGEA_UCONNECT_800(
        title = "Egea 7\" Akıcı (480p)",
        subtitle = "800×480 • 30 FPS (Fiat Egea 2020 Uconnect USB için en stabil)",
        width = 800,
        height = 480,
        densityDpi = 160,
        targetFps = 30
    ),
    EGEA_UCONNECT_1024(
        title = "Egea 7\" HD (600p)",
        subtitle = "1024×600 • 30 FPS (Uconnect 7\" HD net görünüm)",
        width = 1024,
        height = 600,
        densityDpi = 200,
        targetFps = 30
    ),
    SYNC4_720P(
        title = "SYNC 4 720p Akıcı",
        subtitle = "1280×720 • 30 FPS (Geniş ekran düşük gecikme ve ısınma)",
        width = 1280,
        height = 720,
        densityDpi = 240,
        targetFps = 30
    ),
    SYNC4_1080P(
        title = "SYNC 4 1080p Keskin",
        subtitle = "1920×1080 • 30 FPS (13.2\" geniş ekran yüksek netlik)",
        width = 1920,
        height = 1080,
        densityDpi = 320,
        targetFps = 30
    )
}

/**
 * Desteklenen araç multimedya profilleri.
 */
enum class VehicleModel(
    val shortTitle: String,
    val displayName: String,
    val systemName: String,
    val connectedBadge: String,
    val connectionTip: String,
    val recommendedProfile: MirrorProfile
) {
    FIAT_EGEA_2020(
        shortTitle = "Fiat Egea 1.4 Fire",
        displayName = "Fiat Egea 2020 (1.4 Fire)",
        systemName = "Uconnect™ 7\" HD Live",
        connectedBadge = "Egea Bağlı",
        connectionTip = "Fiat Egea 2020 1.4 Fire modelinde vites önündeki USB portunu veri aktarım (data) kablosuyla kullanın. Uconnect 7\" ekranı için 800×480 veya 1024×600 profili idealdir.",
        recommendedProfile = MirrorProfile.EGEA_UCONNECT_800
    ),
    FORD_FOCUS_SYNC4(
        shortTitle = "Ford Focus SYNC 4",
        displayName = "Ford Focus Titanium",
        systemName = "SYNC 4 (13.2\" Geniş Ekran)",
        connectedBadge = "SYNC 4 Bağlı",
        connectionTip = "Kablosuz veya USB üzerinden Ford SYNC 4 sistemine bağlayın. 13.2\" geniş ekran için 720p veya 1080p profili önerilir.",
        recommendedProfile = MirrorProfile.SYNC4_720P
    )
}

/**
 * Ön plandaki [com.sync4.carcompanion.mirror.ScreenMirrorService] oturum durumu.
 */
sealed interface MirrorStatus {
    data object Idle : MirrorStatus
    data object Starting : MirrorStatus
    data class Active(
        val profile: MirrorProfile,
        val startedAtMillis: Long,
        val capturedFrames: Long = 0L
    ) : MirrorStatus

    data class Error(val message: String) : MirrorStatus
}

data class QuickBookmark(
    val title: String,
    val subtitle: String,
    val url: String
)

data class BrowserState(
    val currentUrl: String = "https://m.youtube.com",
    val pageTitle: String = "Web Tarayıcı",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val isDesktopMode: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

/**
 * Telefon arayüzü (MainActivity), arka plan MediaProjection servisi (ScreenMirrorService)
 * ve araç ekranı (CompanionCarAppService) arasındaki süreç-içi (in-process) reaktif durum köprüsü.
 */
object CompanionStateRepository {

    private const val PREFS_NAME = "car_companion_prefs"
    private const val KEY_VEHICLE_MODEL = "selected_vehicle_model"
    private const val KEY_MIRROR_PROFILE = "selected_mirror_profile"

    @Volatile
    private var prefs: SharedPreferences? = null

    /**
     * Fiat Egea Uconnect 7" maksimum liste sınırlarına (<= 6 öğe) tam uyumlu hızlı kaynaklar.
     */
    val defaultBookmarks: List<QuickBookmark> = listOf(
        QuickBookmark(
            title = "YouTube Web",
            subtitle = "HTML5 video oynatıcı",
            url = "https://m.youtube.com"
        ),
        QuickBookmark(
            title = "TRT tabii / Canlı",
            subtitle = "Canlı TV ve yayın akışı",
            url = "https://www.tabii.com"
        ),
        QuickBookmark(
            title = "Fiat Türkiye (Egea)",
            subtitle = "Fiat Egea & Uconnect destek",
            url = "https://www.otomobil.fiat.com.tr"
        ),
        QuickBookmark(
            title = "Ford Türkiye",
            subtitle = "SYNC 4 & Araç kılavuzları",
            url = "https://www.ford.com.tr"
        ),
        QuickBookmark(
            title = "Google Arama",
            subtitle = "Hızlı web gezintisi",
            url = "https://www.google.com"
        )
    )

    private val _selectedVehicle = MutableStateFlow(VehicleModel.FIAT_EGEA_2020)
    val selectedVehicle: StateFlow<VehicleModel> = _selectedVehicle.asStateFlow()

    private val _selectedProfile = MutableStateFlow(MirrorProfile.EGEA_UCONNECT_800)
    val selectedProfile: StateFlow<MirrorProfile> = _selectedProfile.asStateFlow()

    private val _mirrorStatus = MutableStateFlow<MirrorStatus>(MirrorStatus.Idle)
    val mirrorStatus: StateFlow<MirrorStatus> = _mirrorStatus.asStateFlow()

    private val _browserState = MutableStateFlow(BrowserState())
    val browserState: StateFlow<BrowserState> = _browserState.asStateFlow()

    private val _isCarConnected = MutableStateFlow(false)
    val isCarConnected: StateFlow<Boolean> = _isCarConnected.asStateFlow()

    private val _pendingNavigationUrl = MutableStateFlow<String?>(null)
    val pendingNavigationUrl: StateFlow<String?> = _pendingNavigationUrl.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val appContext = context.applicationContext
            val sharedPrefs = try {
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } catch (_: IllegalStateException) {
                // Cihaz kilitliyken (Direct Boot) USB kabloyla araca bağlanılırsa çökmesini önle
                appContext.createDeviceProtectedStorageContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            prefs = sharedPrefs

            val savedVehicleName = sharedPrefs.getString(KEY_VEHICLE_MODEL, null)
            val restoredVehicle = VehicleModel.entries.find { it.name == savedVehicleName }
                ?: VehicleModel.FIAT_EGEA_2020
            _selectedVehicle.value = restoredVehicle

            val savedProfileName = sharedPrefs.getString(KEY_MIRROR_PROFILE, null)
            val restoredProfile = MirrorProfile.entries.find { it.name == savedProfileName }
                ?: restoredVehicle.recommendedProfile
            _selectedProfile.value = restoredProfile
        }
    }

    fun selectVehicle(vehicle: VehicleModel) {
        _selectedVehicle.value = vehicle
        prefs?.edit { putString(KEY_VEHICLE_MODEL, vehicle.name) }

        // Yansıtma şu an aktif değilse seçilen aracın önerilen ekran çözünürlüğüne otomatik geç
        val currentMirror = _mirrorStatus.value
        if (currentMirror !is MirrorStatus.Active && currentMirror !is MirrorStatus.Starting) {
            selectProfile(vehicle.recommendedProfile)
        }
    }

    fun selectProfile(profile: MirrorProfile) {
        _selectedProfile.value = profile
        prefs?.edit { putString(KEY_MIRROR_PROFILE, profile.name) }
    }

    fun setMirrorStatus(status: MirrorStatus) {
        _mirrorStatus.value = status
    }

    fun updateCapturedFrames(totalFrames: Long) {
        _mirrorStatus.update { current ->
            if (current is MirrorStatus.Active) {
                current.copy(capturedFrames = totalFrames)
            } else {
                current
            }
        }
    }

    fun updateBrowserState(transform: (BrowserState) -> BrowserState) {
        _browserState.update(transform)
    }

    fun setCarConnected(connected: Boolean) {
        _isCarConnected.value = connected
    }

    fun requestOpenUrl(url: String) {
        val normalized = normalizeUrl(url)
        _pendingNavigationUrl.value = normalized
    }

    fun consumePendingUrl(): String? {
        return _pendingNavigationUrl.getAndUpdate { null }
    }

    fun normalizeUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.equals("localhost", ignoreCase = true) ||
                trimmed.startsWith("localhost:", ignoreCase = true) ||
                trimmed.startsWith("localhost/", ignoreCase = true) -> "http://$trimmed"
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
        }
    }
}
