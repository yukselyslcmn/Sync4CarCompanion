package com.sync4.carcompanion.car

import android.content.Intent
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.sync4.carcompanion.mirror.CarSurfaceRenderer
import com.sync4.carcompanion.state.CompanionStateRepository

/**
 * Araç ekranı bağlantısının yaşam döngüsünü yöneten Android Auto oturumu.
 */
class CompanionCarSession : Session() {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                CompanionStateRepository.setCarConnected(true)
                try {
                    carContext.getCarService(AppManager::class.java)
                        .setSurfaceCallback(CarSurfaceRenderer)
                } catch (_: Exception) {
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                CompanionStateRepository.setCarConnected(false)
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return CompanionMainScreen(carContext)
    }
}
