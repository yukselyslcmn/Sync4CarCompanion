package com.sync4.carcompanion

import android.content.Context
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sync4.carcompanion.car.CompanionCarAppService
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorProfile
import com.sync4.carcompanion.state.VehicleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarCompanionInstrumentedTest {

    @Test
    fun mainActivity_launchesAndInitializesFiatEgeaProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CompanionStateRepository.init(context)
        CompanionStateRepository.selectVehicle(VehicleModel.FIAT_EGEA_2020)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(scenario.state.isAtLeast(Lifecycle.State.CREATED))
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
            assertEquals(
                VehicleModel.FIAT_EGEA_2020,
                CompanionStateRepository.selectedVehicle.value
            )
            assertEquals(
                MirrorProfile.EGEA_UCONNECT_800,
                CompanionStateRepository.selectedProfile.value
            )

            // Ford Focus seçimi ve tekrar Fiat Egea seçiminin canlı doğrulaması
            CompanionStateRepository.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
            assertEquals(MirrorProfile.SYNC4_720P, CompanionStateRepository.selectedProfile.value)

            CompanionStateRepository.selectVehicle(VehicleModel.FIAT_EGEA_2020)
            assertEquals(MirrorProfile.EGEA_UCONNECT_800, CompanionStateRepository.selectedProfile.value)
        }
    }

    @Test
    fun carAppService_allowsAllHostsForUconnectAndSync4Sideload() {
        val service = CompanionCarAppService()
        assertSame(
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR,
            service.createHostValidator()
        )
    }
}
