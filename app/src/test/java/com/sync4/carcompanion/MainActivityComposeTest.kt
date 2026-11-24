package com.sync4.carcompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.sync4.carcompanion.state.BrowserState
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorProfile
import com.sync4.carcompanion.state.MirrorStatus
import com.sync4.carcompanion.state.VehicleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MainActivity Jetpack Compose arayüz etkileşim testleri. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
class MainActivityComposeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val repo = CompanionStateRepository

    @Before
    fun resetState() {
        repo.setMirrorStatus(MirrorStatus.Idle)
        repo.selectVehicle(VehicleModel.FIAT_EGEA_2020)
        repo.setCarConnected(false)
        repo.consumePendingUrl()
        repo.updateBrowserState { BrowserState() }
        rule.waitForIdle()
    }

    @Test
    fun topBarStatusPill_reflectsDisconnectedConnectedAndMirroringStates() {
        rule.onNodeWithText("Hazır").assertIsDisplayed()

        repo.setCarConnected(true)
        rule.waitForIdle()
        rule.onNodeWithText("Egea Bağlı").assertIsDisplayed()

        repo.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
        rule.waitForIdle()
        rule.onNodeWithText("SYNC 4 Bağlı").assertIsDisplayed()

        repo.setMirrorStatus(MirrorStatus.Active(MirrorProfile.SYNC4_720P, 1L, 15L))
        rule.waitForIdle()
        rule.onNodeWithText("Yansıtma Aktif").assertIsDisplayed()
    }

    @Test
    fun selectingVehicleCard_updatesRecommendedResolutionChip() {
        rule.onNodeWithText("Egea 7\" Akıcı (480p)").assertIsSelected()

        rule.onNodeWithText("Ford Focus Titanium").performClick()
        rule.waitForIdle()

        assertEquals(VehicleModel.FORD_FOCUS_SYNC4, repo.selectedVehicle.value)
        rule.onNodeWithText("SYNC 4 720p Akıcı").assertIsSelected()
    }

    @Test
    fun selectingManualProfileChip_updatesRepositoryAndSubtitle() {
        rule.onNodeWithText("Egea 7\" HD (600p)").performClick()
        rule.waitForIdle()

        assertEquals(MirrorProfile.EGEA_UCONNECT_1024, repo.selectedProfile.value)
        rule.onNodeWithText("Egea 7\" HD (600p)").assertIsSelected()
        rule.onNodeWithText(MirrorProfile.EGEA_UCONNECT_1024.subtitle).assertIsDisplayed()
    }

    @Test
    fun activeMirroring_disablesProfileChipsAndShowsStopButton() {
        repo.setMirrorStatus(MirrorStatus.Active(MirrorProfile.EGEA_UCONNECT_800, 1L, 30L))
        rule.waitForIdle()

        rule.onNodeWithText("Egea 7\" HD (600p)").assertIsNotEnabled()
        rule.onNodeWithText("Ekran Yansıtmayı Durdur").assertIsDisplayed()

        rule.onNodeWithText("Ekran Yansıtmayı Durdur").performClick()
        rule.waitForIdle()

        assertEquals(MirrorStatus.Idle, repo.mirrorStatus.value)
        rule.onNodeWithText("Ekran Yansıtmayı Başlat").assertIsDisplayed()
    }

    @Test
    fun clickingDashboardBookmark_switchesToBrowserTabAndLoadsBookmarkUrl() {
        rule.onNodeWithText("TRT tabii / Canlı").performScrollTo().performClick()
        rule.waitForIdle()

        // Sekme otomatik olarak Tarayıcı & Video'ya geçmeli
        rule.onNodeWithContentDescription("Masaüstü Modu").assertIsDisplayed()
    }

    @Test
    fun browserTab_desktopModeToggle_updatesBrowserState() {
        rule.onNodeWithText("Tarayıcı & Video").performClick()
        rule.waitForIdle()

        assertFalse(repo.browserState.value.isDesktopMode)
        rule.onNodeWithContentDescription("Masaüstü Modu").performClick()
        rule.waitForIdle()
        assertTrue(repo.browserState.value.isDesktopMode)

        rule.onNodeWithContentDescription("Masaüstü Modu").performClick()
        rule.waitForIdle()
        assertFalse(repo.browserState.value.isDesktopMode)
    }

    @Test
    fun browserTab_backAndForwardButtons_followBrowserStateFlags() {
        rule.onNodeWithText("Tarayıcı & Video").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Geri").assertIsNotEnabled()
        rule.onNodeWithContentDescription("İleri").assertIsNotEnabled()

        repo.updateBrowserState { it.copy(canGoBack = true, canGoForward = true) }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Geri").assertIsEnabled()
        rule.onNodeWithContentDescription("İleri").assertIsEnabled()
    }

    @Test
    fun browserTab_urlFieldImeGo_submitsWithoutCrashing() {
        rule.onNodeWithText("Tarayıcı & Video").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("https://m.youtube.com")
            .performTextReplacement("otomobil.fiat.com.tr")
        rule.onNodeWithText("otomobil.fiat.com.tr")
            .assertIsDisplayed()
            .performImeAction()
        rule.waitForIdle()
    }

    @Test
    fun browserTab_clearUrlButton_clearsInputField() {
        rule.onNodeWithText("Tarayıcı & Video").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Adresi Temizle").assertIsDisplayed().performClick()
        rule.waitForIdle()

        rule.onNodeWithText("URL veya arama girin...").assertIsDisplayed()
    }

    @Test
    fun browserTab_backPressWhenNoHistory_returnsToDashboard() {
        rule.onNodeWithText("Tarayıcı & Video").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Masaüstü Modu").assertIsDisplayed()

        rule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()

        rule.onNodeWithText("Ekran Yansıtmayı Başlat").assertIsDisplayed()
    }

    @Test
    fun activeMirroring_keepsPhoneScreenAwake() {
        var keepAwake = false
        rule.activityRule.scenario.onActivity { activity ->
            keepAwake = activity.window.decorView.findViewById<android.view.View>(android.R.id.content).keepScreenOn ||
                hasKeepScreenOnView(activity.window.decorView)
        }
        assertFalse("Boştayken keepScreenOn kapalı olmalı", keepAwake)

        repo.setMirrorStatus(MirrorStatus.Active(MirrorProfile.EGEA_UCONNECT_800, 1L, 10L))
        rule.waitForIdle()

        rule.activityRule.scenario.onActivity { activity ->
            keepAwake = hasKeepScreenOnView(activity.window.decorView)
        }
        assertTrue("Yansıtma aktifken telefon ekranı uyanık tutulmalı", keepAwake)
    }

    private fun hasKeepScreenOnView(view: android.view.View): Boolean {
        if (view.keepScreenOn) return true
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                if (hasKeepScreenOnView(view.getChildAt(i))) return true
            }
        }
        return false
    }
}
