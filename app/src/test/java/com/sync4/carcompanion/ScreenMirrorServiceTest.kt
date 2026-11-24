package com.sync4.carcompanion

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sync4.carcompanion.mirror.ScreenMirrorService
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorProfile
import com.sync4.carcompanion.state.MirrorStatus
import com.sync4.carcompanion.state.VehicleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Ön plan MediaProjection servisi (ScreenMirrorService) birim testleri. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenMirrorServiceTest {

    private lateinit var app: Application
    private val repo = CompanionStateRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        repo.setMirrorStatus(MirrorStatus.Idle)
        repo.selectVehicle(VehicleModel.FIAT_EGEA_2020)
    }

    @Test
    fun startHelper_dispatchesForegroundServiceWithExpectedExtras() {
        val dummyData = Intent("dummy.projection.token")
        ScreenMirrorService.start(
            context = app,
            resultCode = Activity.RESULT_OK,
            resultData = dummyData,
            profile = MirrorProfile.SYNC4_720P
        )

        val startedIntent = shadowOf(app).nextStartedService
        assertNotNull(startedIntent)
        assertEquals(ScreenMirrorService::class.java.name, startedIntent.component?.className)
        assertEquals("com.sync4.carcompanion.mirror.ACTION_START", startedIntent.action)
        assertEquals(Activity.RESULT_OK, startedIntent.getIntExtra("extra_result_code", 0))
        assertEquals(MirrorProfile.SYNC4_720P.name, startedIntent.getStringExtra("extra_profile_name"))
    }

    @Test
    fun stopHelper_whenServiceNotRunning_resetsMirrorStatusToIdle() {
        repo.setMirrorStatus(MirrorStatus.Active(MirrorProfile.EGEA_UCONNECT_800, 100L, 12L))
        ScreenMirrorService.stop(app)
        assertEquals(MirrorStatus.Idle, repo.mirrorStatus.value)
    }

    @Test
    fun service_onBind_returnsNull() {
        val controller = Robolectric.buildService(ScreenMirrorService::class.java).create()
        assertNull(controller.get().onBind(Intent()))
    }

    @Test
    fun onStartCommand_withCanceledResult_setsErrorStatusAndCreatesNotificationChannel() {
        val startIntent = Intent(app, ScreenMirrorService::class.java).apply {
            action = "com.sync4.carcompanion.mirror.ACTION_START"
            putExtra("extra_result_code", Activity.RESULT_CANCELED)
            putExtra("extra_profile_name", MirrorProfile.EGEA_UCONNECT_800.name)
        }

        val controller = Robolectric.buildService(ScreenMirrorService::class.java, startIntent).create()
        val result = controller.get().onStartCommand(startIntent, 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        val status = repo.mirrorStatus.value
        assertTrue("Durum Error olmalı ama $status geldi", status is MirrorStatus.Error)

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull(nm.getNotificationChannel("car_screen_mirror_channel"))
    }

    @Test
    fun onStartCommand_withMissingResultData_setsErrorStatus() {
        val startIntent = Intent(app, ScreenMirrorService::class.java).apply {
            action = "com.sync4.carcompanion.mirror.ACTION_START"
            putExtra("extra_result_code", Activity.RESULT_OK)
            // extra_result_data yok
        }

        val controller = Robolectric.buildService(ScreenMirrorService::class.java, startIntent).create()
        controller.get().onStartCommand(startIntent, 0, 1)

        val status = repo.mirrorStatus.value
        assertTrue(status is MirrorStatus.Error)
        assertTrue((status as MirrorStatus.Error).message.contains("izin verisi"))
    }

    @Test
    fun onStartCommand_actionStop_resetsActiveMirrorToIdle() {
        repo.setMirrorStatus(MirrorStatus.Active(MirrorProfile.EGEA_UCONNECT_1024, 50L, 5L))
        val stopIntent = Intent(app, ScreenMirrorService::class.java).apply {
            action = "com.sync4.carcompanion.mirror.ACTION_STOP"
        }

        val controller = Robolectric.buildService(ScreenMirrorService::class.java, stopIntent).create()
        val res = controller.get().onStartCommand(stopIntent, 0, 2)

        assertEquals(Service.START_NOT_STICKY, res)
        assertEquals(MirrorStatus.Idle, repo.mirrorStatus.value)
    }

    @Test
    fun onDestroy_cleansUpAndResetsStatusToIdle() {
        repo.setMirrorStatus(MirrorStatus.Starting)
        val controller = Robolectric.buildService(ScreenMirrorService::class.java).create()
        controller.destroy()
        assertEquals(MirrorStatus.Idle, repo.mirrorStatus.value)
    }
}
