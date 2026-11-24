package com.sync4.carcompanion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sync4.carcompanion.state.BrowserState
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorProfile
import com.sync4.carcompanion.state.MirrorStatus
import com.sync4.carcompanion.state.VehicleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Durum deposu, profiller ve araç modelleri için ek testler. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StateModelsTest {

    private val repo = CompanionStateRepository

    @Before
    fun reset() {
        resetPrefs(null)
        repo.setMirrorStatus(MirrorStatus.Idle)
        repo.selectVehicle(VehicleModel.FIAT_EGEA_2020)
        repo.setCarConnected(false)
        repo.consumePendingUrl()
        repo.updateBrowserState { BrowserState() }
    }

    /** Singleton'ın SharedPreferences önbelleğini sıfırlar (testler arası yalıtım için). */
    private fun resetPrefs(value: Any?) {
        val f = CompanionStateRepository::class.java.getDeclaredField("prefs")
        f.isAccessible = true
        f.set(CompanionStateRepository, value)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    // ---- Profil / model verisi ----

    @Test
    fun everyVehicleRecommendedProfile_belongsToItsScreenClass() {
        assertEquals(MirrorProfile.EGEA_UCONNECT_800, VehicleModel.FIAT_EGEA_2020.recommendedProfile)
        assertEquals(MirrorProfile.SYNC4_720P, VehicleModel.FORD_FOCUS_SYNC4.recommendedProfile)
    }

    @Test
    fun mirrorProfiles_haveSaneDimensionsAndFps() {
        MirrorProfile.entries.forEach {
            assertTrue("${it.name} genişlik", it.width in 480..1920)
            assertTrue("${it.name} yükseklik", it.height in 360..1080)
            assertTrue("${it.name} yatay (landscape)", it.width > it.height)
            assertTrue("${it.name} dpi", it.densityDpi in 120..480)
            assertEquals("${it.name} fps", 30, it.targetFps)
            assertTrue("${it.name} alt metin çözünürlüğü içermeli",
                it.subtitle.contains("${it.width}×${it.height}"))
        }
    }

    @Test
    fun uconnectSevenInchProfiles_doNotExceedScreenNativeLimit() {
        // Egea Uconnect 7" HD en fazla 1024x600
        listOf(MirrorProfile.EGEA_UCONNECT_800, MirrorProfile.EGEA_UCONNECT_1024).forEach {
            assertTrue(it.width <= 1024 && it.height <= 600)
        }
    }

    @Test
    fun vehicleModels_haveDistinctNamesAndBadges() {
        val names = VehicleModel.entries.map { it.displayName }
        assertEquals(names.size, names.toSet().size)
        assertEquals(VehicleModel.entries.size, VehicleModel.entries.map { it.connectedBadge }.toSet().size)
    }

    // ---- normalizeUrl kenar durumları ----

    @Test
    fun normalizeUrl_emptyOrBlank_returnsGoogle() {
        assertEquals("https://www.google.com", repo.normalizeUrl(""))
        assertEquals("https://www.google.com", repo.normalizeUrl("   "))
    }

    @Test
    fun normalizeUrl_keepsExistingHttpAndHttpsCaseInsensitive() {
        assertEquals("http://example.com", repo.normalizeUrl("http://example.com"))
        assertEquals("HTTPS://Example.com", repo.normalizeUrl("HTTPS://Example.com"))
    }

    @Test
    fun normalizeUrl_trimsWhitespace() {
        assertEquals("https://example.com", repo.normalizeUrl("  example.com  "))
    }

    @Test
    fun normalizeUrl_searchQueryIsUrlEncoded() {
        val result = repo.normalizeUrl("egea ışık")
        assertEquals("https://www.google.com/search?q=egea+%C4%B1%C5%9F%C4%B1k", result)
    }

    @Test
    fun normalizeUrl_wordWithoutDotIsSearch() {
        assertTrue(repo.normalizeUrl("youtube").startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun normalizeUrl_domainWithSpaceIsSearch() {
        assertTrue(repo.normalizeUrl("fiat egea.com tr").startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun normalizeUrl_localhostUsesHttpScheme() {
        assertEquals("http://localhost", repo.normalizeUrl("localhost"))
        assertEquals("http://localhost:8080/stream", repo.normalizeUrl("localhost:8080/stream"))
    }

    // ---- Durum akışları ----

    @Test
    fun updateCapturedFrames_isIgnoredWhenNotActive() {
        repo.updateCapturedFrames(99L)
        assertEquals(MirrorStatus.Idle, repo.mirrorStatus.value)

        repo.setMirrorStatus(MirrorStatus.Starting)
        repo.updateCapturedFrames(99L)
        assertEquals(MirrorStatus.Starting, repo.mirrorStatus.value)

        repo.setMirrorStatus(MirrorStatus.Error("x"))
        repo.updateCapturedFrames(99L)
        assertEquals(MirrorStatus.Error("x"), repo.mirrorStatus.value)
    }

    @Test
    fun updateCapturedFrames_keepsProfileAndStartTime() {
        val active = MirrorStatus.Active(MirrorProfile.SYNC4_1080P, startedAtMillis = 123L)
        repo.setMirrorStatus(active)
        repo.updateCapturedFrames(7L)
        val now = repo.mirrorStatus.value as MirrorStatus.Active
        assertEquals(MirrorProfile.SYNC4_1080P, now.profile)
        assertEquals(123L, now.startedAtMillis)
        assertEquals(7L, now.capturedFrames)
    }

    @Test
    fun selectVehicle_whileStarting_keepsCurrentProfile() {
        repo.selectProfile(MirrorProfile.EGEA_UCONNECT_1024)
        repo.setMirrorStatus(MirrorStatus.Starting)
        repo.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
        assertEquals(MirrorProfile.EGEA_UCONNECT_1024, repo.selectedProfile.value)
    }

    @Test
    fun selectVehicle_afterErrorState_switchesProfile() {
        repo.setMirrorStatus(MirrorStatus.Error("izin yok"))
        repo.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
        assertEquals(MirrorProfile.SYNC4_720P, repo.selectedProfile.value)
    }

    @Test
    fun selectProfile_canOverrideRecommendedProfile() {
        repo.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
        repo.selectProfile(MirrorProfile.SYNC4_1080P)
        assertEquals(VehicleModel.FORD_FOCUS_SYNC4, repo.selectedVehicle.value)
        assertEquals(MirrorProfile.SYNC4_1080P, repo.selectedProfile.value)
    }

    @Test
    fun carConnected_togglesFlow() {
        assertFalse(repo.isCarConnected.value)
        repo.setCarConnected(true)
        assertTrue(repo.isCarConnected.value)
        repo.setCarConnected(false)
        assertFalse(repo.isCarConnected.value)
    }

    @Test
    fun browserState_defaultsAndUpdate() {
        val d = repo.browserState.value
        assertEquals("https://m.youtube.com", d.currentUrl)
        assertFalse(d.isLoading)
        assertFalse(d.isDesktopMode)

        repo.updateBrowserState { it.copy(currentUrl = "https://www.tabii.com", isDesktopMode = true, canGoBack = true) }
        val u = repo.browserState.value
        assertEquals("https://www.tabii.com", u.currentUrl)
        assertTrue(u.isDesktopMode)
        assertTrue(u.canGoBack)
        assertEquals(d.pageTitle, u.pageTitle)
    }

    @Test
    fun requestOpenUrl_overwritesPreviousPendingUrl() {
        repo.requestOpenUrl("a.com")
        repo.requestOpenUrl("b.com")
        assertEquals("https://b.com", repo.consumePendingUrl())
        assertNull(repo.consumePendingUrl())
    }

    @Test
    fun defaultBookmarks_haveValidHttpsUrlsAndTitles() {
        repo.defaultBookmarks.forEach {
            assertTrue(it.url, it.url.startsWith("https://"))
            assertTrue(it.title.isNotBlank())
            assertTrue(it.subtitle.isNotBlank())
        }
        assertEquals(repo.defaultBookmarks.size, repo.defaultBookmarks.map { it.url }.toSet().size)
    }

    // ---- Kalıcılık (SharedPreferences) ----

    @Test
    fun selections_arePersistedAndRestoredAfterInit() {
        repo.init(context())
        repo.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
        repo.selectProfile(MirrorProfile.SYNC4_1080P)

        // Uygulama yeniden başlıyormuş gibi bellek durumunu sıfırla
        resetPrefs(null)
        repo.selectVehicle(VehicleModel.FIAT_EGEA_2020)   // bellekte farklı değer
        resetPrefs(null)
        context().getSharedPreferences("car_companion_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_vehicle_model", "FORD_FOCUS_SYNC4")
            .putString("selected_mirror_profile", "SYNC4_1080P").commit()

        repo.init(context())
        assertEquals(VehicleModel.FORD_FOCUS_SYNC4, repo.selectedVehicle.value)
        assertEquals(MirrorProfile.SYNC4_1080P, repo.selectedProfile.value)
    }

    @Test
    fun init_withCorruptedValues_fallsBackToDefaults() {
        context().getSharedPreferences("car_companion_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_vehicle_model", "TOFAS_BILINMEYEN")
            .putString("selected_mirror_profile", "YOK").commit()

        repo.init(context())
        assertEquals(VehicleModel.FIAT_EGEA_2020, repo.selectedVehicle.value)
        assertEquals(MirrorProfile.EGEA_UCONNECT_800, repo.selectedProfile.value)
    }

    @Test
    fun init_withSavedVehicleButNoProfile_usesVehicleRecommendedProfile() {
        context().getSharedPreferences("car_companion_prefs", Context.MODE_PRIVATE)
            .edit().clear().putString("selected_vehicle_model", "FORD_FOCUS_SYNC4").commit()

        repo.init(context())
        assertEquals(VehicleModel.FORD_FOCUS_SYNC4, repo.selectedVehicle.value)
        assertEquals(MirrorProfile.SYNC4_720P, repo.selectedProfile.value)
    }

    @Test
    fun init_calledTwice_doesNotOverwriteLaterSelections() {
        repo.init(context())
        repo.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
        repo.init(context())
        assertEquals(VehicleModel.FORD_FOCUS_SYNC4, repo.selectedVehicle.value)
    }
}
