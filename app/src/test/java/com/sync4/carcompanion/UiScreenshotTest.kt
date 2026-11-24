package com.sync4.carcompanion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorProfile
import com.sync4.carcompanion.state.MirrorStatus
import com.sync4.carcompanion.state.VehicleModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Emülatör olmadan arayüzü PNG olarak üretir: app/build/screenshots */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class UiScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun reset() {
        CompanionStateRepository.setMirrorStatus(MirrorStatus.Idle)
        CompanionStateRepository.selectVehicle(VehicleModel.FIAT_EGEA_2020)
        CompanionStateRepository.setCarConnected(false)
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val view: View = rule.activity.window.decorView
        val w = view.width.coerceAtLeast(1)
        val h = view.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun dashboard() {
        save("01_dashboard")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun dashboardFullLength() {
        save("03_dashboard_full")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun fordSelected() {
        rule.onNodeWithText("Ford Focus Titanium").performClick()
        save("04_ford_selected")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h1500dp-xhdpi")
    fun mirroringActiveState() {
        CompanionStateRepository.setCarConnected(true)
        CompanionStateRepository.setMirrorStatus(
            MirrorStatus.Active(MirrorProfile.EGEA_UCONNECT_800, startedAtMillis = 1000L, capturedFrames = 120L)
        )
        save("05_mirror_active")
    }

    @Test
    fun browserTab() {
        rule.onNodeWithText("Tarayıcı & Video").performClick()
        save("02_browser")
    }
}
