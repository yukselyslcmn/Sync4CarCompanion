package com.sync4.carcompanion

import android.content.Intent
import androidx.car.app.SessionInfo
import androidx.car.app.testing.SessionController
import androidx.car.app.testing.TestCarContext
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.sync4.carcompanion.car.CompanionCarAppService
import com.sync4.carcompanion.car.CompanionCarSession
import com.sync4.carcompanion.car.CompanionMainScreen
import com.sync4.carcompanion.state.CompanionStateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android Auto servis giriş noktası ve oturum yaşam döngüsü testleri. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarSessionTest {

    private lateinit var carContext: TestCarContext

    @Before
    fun setUp() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        CompanionStateRepository.setCarConnected(false)
    }

    @Test
    fun session_start_marksCarConnected_andStop_marksDisconnected() {
        val controller = SessionController(CompanionCarSession(), carContext, Intent())
        assertFalse(CompanionStateRepository.isCarConnected.value)

        controller.moveToState(Lifecycle.State.STARTED)
        assertTrue(CompanionStateRepository.isCarConnected.value)

        controller.moveToState(Lifecycle.State.CREATED)
        assertFalse(CompanionStateRepository.isCarConnected.value)
    }

    @Test
    fun session_destroy_leavesCarDisconnected() {
        val controller = SessionController(CompanionCarSession(), carContext, Intent())
        controller.moveToState(Lifecycle.State.RESUMED)
        assertTrue(CompanionStateRepository.isCarConnected.value)

        controller.moveToState(Lifecycle.State.DESTROYED)
        assertFalse(CompanionStateRepository.isCarConnected.value)
    }

    @Test
    fun session_createsMainScreenAsRoot() {
        val controller = SessionController(CompanionCarSession(), carContext, Intent())
        controller.moveToState(Lifecycle.State.CREATED)
        val top = carContext.getCarService(androidx.car.app.ScreenManager::class.java).top
        assertTrue(top is CompanionMainScreen)
    }

    @Test
    fun carAppService_allowsAllHostsAndCreatesCompanionSession() {
        val service = Robolectric.buildService(CompanionCarAppService::class.java).create().get()
        assertSame(HostValidator.ALLOW_ALL_HOSTS_VALIDATOR, service.createHostValidator())
        val session = service.onCreateSession(SessionInfo.DEFAULT_SESSION_INFO)
        assertEquals(CompanionCarSession::class.java, session::class.java)
    }
}
