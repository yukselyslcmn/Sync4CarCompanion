package com.sync4.carcompanion.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sync4.carcompanion.mirror.CarSurfaceRenderer

/**
 * Telefon ekranındaki aktif YouTube / WebView videosunu veya MediaProjection sanal ekranını
 * doğrudan araç ekranındaki (Fiat Egea Uconnect 7" / Ford SYNC 4 / DHU) [android.view.Surface]
 * üzerine tam ekran olarak çizen canlı video oynatma ekranı.
 */
class CompanionVideoMirrorScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                try {
                    carContext.getCarService(AppManager::class.java)
                        .setSurfaceCallback(CarSurfaceRenderer)
                } catch (_: Exception) {
                    // Bazı test ortamlarında veya izin eksikliğinde güvenli geçiş
                }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val playPauseAction = Action.Builder()
            .setTitle("Oynat / Duraklat")
            .setOnClickListener {
                CarSurfaceRenderer.togglePlayPauseOnPhone()
            }
            .build()

        val bookmarksAction = Action.Builder()
            .setTitle("Web Kısayolları")
            .setOnClickListener {
                screenManager.push(CompanionBookmarksScreen(carContext))
            }
            .build()

        val panelAction = Action.Builder()
            .setTitle("Kontrol Paneli")
            .setOnClickListener {
                if (screenManager.stackSize > 1) {
                    screenManager.pop()
                } else {
                    screenManager.push(CompanionMainScreen(carContext))
                }
            }
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(playPauseAction)
            .addAction(bookmarksAction)
            .addAction(panelAction)
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }
}
