package com.sync4.carcompanion.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.sync4.carcompanion.MainActivity
import com.sync4.carcompanion.mirror.ScreenMirrorService
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.MirrorStatus
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Fiat Egea 2020 (Uconnect 7" 800×480) ve Ford Focus (SYNC 4 13.2") ekranlarında gösterilen
 * ana kontrol ve durum şablonu. 7" ekran satır/aksiyon sınırlarına tam uyumludur.
 */
class CompanionMainScreen(carContext: CarContext) : Screen(carContext) {

    init {
        // Telefon tarafındaki durum veya araç profili değiştiğinde araç ekranını yenile.
        // Android Auto şablon yenileme kotasını korumak için yalnızca ekranda görünen
        // alanlar izlenir (WebView yükleme yüzdesi gibi sık değişen alanlar hariç) ve
        // kare sayacı en fazla FRAME_REFRESH_STEP karede bir yenileme tetikler.
        lifecycleScope.launch {
            combine(
                CompanionStateRepository.selectedVehicle,
                CompanionStateRepository.selectedProfile,
                CompanionStateRepository.mirrorStatus.map { it.toDisplayKey() },
                CompanionStateRepository.browserState.map { it.pageTitle to it.currentUrl }
            ) { vehicle, profile, mirror, browser ->
                listOf(vehicle, profile, mirror, browser)
            }
                .distinctUntilChanged()
                .drop(1) // İlk şablon zaten onGetTemplate ile çiziliyor
                .collect { invalidate() }
        }
    }

    private fun MirrorStatus.toDisplayKey(): Any = when (this) {
        is MirrorStatus.Active -> copy(capturedFrames = capturedFrames / FRAME_REFRESH_STEP)
        else -> this
    }

    override fun onGetTemplate(): Template {
        val vehicle = CompanionStateRepository.selectedVehicle.value
        val selectedProfile = CompanionStateRepository.selectedProfile.value
        val mirrorStatus = CompanionStateRepository.mirrorStatus.value
        val browserState = CompanionStateRepository.browserState.value
        val isMirrorActive =
            mirrorStatus is MirrorStatus.Active || mirrorStatus is MirrorStatus.Starting

        val vehicleRow = Row.Builder()
            .setTitle("Araç: ${vehicle.displayName}")
            .addText("${vehicle.systemName} (${selectedProfile.width}×${selectedProfile.height})")
            .build()

        val mirrorStatusRow = Row.Builder()
            .setTitle("Ekran Yansıtma Durumu")
            .addText(
                when (mirrorStatus) {
                    MirrorStatus.Idle -> "Beklemede • Telefondan başlatmaya hazır"
                    MirrorStatus.Starting -> "Hazırlanıyor • Sanal ekran kuruluyor..."
                    is MirrorStatus.Active ->
                        "Aktif (${mirrorStatus.profile.width}×${mirrorStatus.profile.height} • ${mirrorStatus.capturedFrames} kare)"
                    is MirrorStatus.Error -> "Hata: ${mirrorStatus.message}"
                }
            )
            .build()

        val browserStatusRow = Row.Builder()
            .setTitle("Web: ${browserState.pageTitle}")
            .addText(browserState.currentUrl)
            .build()

        val primaryMirrorAction = if (isMirrorActive) {
            Action.Builder()
                .setTitle("Yansıtmayı Durdur")
                .setBackgroundColor(CarColor.RED)
                .setOnClickListener {
                    ScreenMirrorService.stop(carContext)
                    CarToast.makeText(
                        carContext,
                        "Ekran yansıtma durduruldu.",
                        CarToast.LENGTH_SHORT
                    ).show()
                }
                .build()
        } else {
            // Android 14+ güvenlik politikası gereği MediaProjection onayı araç park halindeyken telefondan verilir
            Action.Builder()
                .setTitle("Telefonda Aç")
                .setBackgroundColor(CarColor.BLUE)
                .setOnClickListener(
                    ParkedOnlyOnClickListener.create {
                        val phoneIntent = Intent(carContext, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        carContext.startActivity(phoneIntent)
                        CarToast.makeText(
                            carContext,
                            "Yansıtma izni için telefon ekranını onaylayın.",
                            CarToast.LENGTH_LONG
                        ).show()
                    }
                )
                .build()
        }

        val bookmarksAction = Action.Builder()
            .setTitle("Web Kısayolları")
            .setOnClickListener {
                screenManager.push(CompanionBookmarksScreen(carContext))
            }
            .build()

        val switchVehicleAction = Action.Builder()
            .setTitle("Araç Seç")
            .setOnClickListener {
                screenManager.push(CompanionVehicleScreen(carContext))
            }
            .build()

        val pane = Pane.Builder()
            .addRow(vehicleRow)
            .addRow(mirrorStatusRow)
            .addRow(browserStatusRow)
            .addAction(primaryMirrorAction)
            .addAction(bookmarksAction)
            .build()

        val headerTitle = "${vehicle.shortTitle} Yardımcı"
        val templateBuilder = PaneTemplate.Builder(pane)

        if (carContext.carAppApiLevel >= 7) {
            templateBuilder.setHeader(
                Header.Builder()
                    .setTitle(headerTitle)
                    .setStartHeaderAction(Action.APP_ICON)
                    .addEndHeaderAction(switchVehicleAction)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            templateBuilder
                .setTitle(headerTitle)
                .setHeaderAction(Action.APP_ICON)
                .setActionStrip(
                    ActionStrip.Builder()
                        .addAction(switchVehicleAction)
                        .build()
                )
        }

        return templateBuilder.build()
    }

    private companion object {
        /** ~30 FPS'te yaklaşık 5 saniyede bir araç ekranı sayaç yenilemesi. */
        const val FRAME_REFRESH_STEP = 150L
    }
}
