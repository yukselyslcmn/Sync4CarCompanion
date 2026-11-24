package com.sync4.carcompanion

import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorProfile
import com.sync4.carcompanion.state.MirrorStatus
import com.sync4.carcompanion.state.VehicleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CompanionStateRepositoryTest {

    @Before
    fun setUp() {
        CompanionStateRepository.setMirrorStatus(MirrorStatus.Idle)
        CompanionStateRepository.selectVehicle(VehicleModel.FIAT_EGEA_2020)
        CompanionStateRepository.consumePendingUrl()
    }

    @Test
    fun defaultVehicle_isFiatEgea2020WithUconnect800Profile() {
        assertEquals(VehicleModel.FIAT_EGEA_2020, CompanionStateRepository.selectedVehicle.value)
        assertEquals(MirrorProfile.EGEA_UCONNECT_800, CompanionStateRepository.selectedProfile.value)
        assertEquals(800, CompanionStateRepository.selectedProfile.value.width)
        assertEquals(480, CompanionStateRepository.selectedProfile.value.height)
    }

    @Test
    fun selectVehicle_switchesProfileAutomaticallyWhenIdle() {
        CompanionStateRepository.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
        assertEquals(VehicleModel.FORD_FOCUS_SYNC4, CompanionStateRepository.selectedVehicle.value)
        assertEquals(MirrorProfile.SYNC4_720P, CompanionStateRepository.selectedProfile.value)

        CompanionStateRepository.selectVehicle(VehicleModel.FIAT_EGEA_2020)
        assertEquals(VehicleModel.FIAT_EGEA_2020, CompanionStateRepository.selectedVehicle.value)
        assertEquals(MirrorProfile.EGEA_UCONNECT_800, CompanionStateRepository.selectedProfile.value)
    }

    @Test
    fun selectVehicle_doesNotInterruptActiveMirrorProfile() {
        CompanionStateRepository.selectProfile(MirrorProfile.EGEA_UCONNECT_1024)
        CompanionStateRepository.setMirrorStatus(
            MirrorStatus.Active(
                profile = MirrorProfile.EGEA_UCONNECT_1024,
                startedAtMillis = 500L,
                capturedFrames = 10L
            )
        )

        CompanionStateRepository.selectVehicle(VehicleModel.FORD_FOCUS_SYNC4)
        assertEquals(MirrorProfile.EGEA_UCONNECT_1024, CompanionStateRepository.selectedProfile.value)
    }

    @Test
    fun defaultBookmarks_respectUconnectSixItemLimit() {
        assertTrue(
            "Fiat Egea Uconnect 7\" ekranı için liste en fazla 6 öğe içermelidir",
            CompanionStateRepository.defaultBookmarks.size <= 6
        )
        assertTrue(
            CompanionStateRepository.defaultBookmarks.any { it.title.contains("Fiat") }
        )
    }

    @Test
    fun normalizeUrl_addsHttpsSchemeForDomainInput() {
        assertEquals(
            "https://otomobil.fiat.com.tr",
            CompanionStateRepository.normalizeUrl("otomobil.fiat.com.tr")
        )
    }

    @Test
    fun normalizeUrl_turnsSearchQueryIntoGoogleSearch() {
        val result = CompanionStateRepository.normalizeUrl("fiat egea 1.4 fire uconnect")
        assertTrue(result.startsWith("https://www.google.com/search?q="))
    }

    @Test
    fun requestAndConsumePendingUrl_worksOnce() {
        CompanionStateRepository.requestOpenUrl("m.youtube.com")
        assertEquals("https://m.youtube.com", CompanionStateRepository.consumePendingUrl())
        assertNull(CompanionStateRepository.consumePendingUrl())
    }

    @Test
    fun updateCapturedFrames_updatesActiveMirrorStatus() {
        CompanionStateRepository.setMirrorStatus(
            MirrorStatus.Active(
                profile = MirrorProfile.EGEA_UCONNECT_800,
                startedAtMillis = 1000L,
                capturedFrames = 0L
            )
        )
        CompanionStateRepository.updateCapturedFrames(45L)

        val current = CompanionStateRepository.mirrorStatus.value
        assertTrue(current is MirrorStatus.Active)
        assertEquals(45L, (current as MirrorStatus.Active).capturedFrames)

        CompanionStateRepository.setMirrorStatus(MirrorStatus.Idle)
    }
}
