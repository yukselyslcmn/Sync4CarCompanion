package com.sync4.carcompanion.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.sync4.carcompanion.state.CompanionStateRepository
import com.sync4.carcompanion.state.VehicleModel

/**
 * Araç multimedya ekranından (Fiat Egea Uconnect 7" veya Ford SYNC 4)
 * aktif araç ve çözünürlük profilini seçmeyi sağlayan ekran.
 */
class CompanionVehicleScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val currentVehicle = CompanionStateRepository.selectedVehicle.value
        val listBuilder = ItemList.Builder()

        VehicleModel.entries.forEach { vehicle ->
            val isSelected = vehicle == currentVehicle
            val prefix = if (isSelected) "✓ " else ""
            val recProfile = vehicle.recommendedProfile

            listBuilder.addItem(
                Row.Builder()
                    .setTitle("$prefix${vehicle.displayName}")
                    .addText("${vehicle.systemName} • ${recProfile.width}×${recProfile.height}")
                    .setOnClickListener {
                        CompanionStateRepository.selectVehicle(vehicle)
                        CarToast.makeText(
                            carContext,
                            "${vehicle.shortTitle} profili seçildi.",
                            CarToast.LENGTH_SHORT
                        ).show()
                        screenManager.pop()
                    }
                    .build()
            )
        }

        val templateBuilder = ListTemplate.Builder()
            .setSingleList(listBuilder.build())

        if (carContext.carAppApiLevel >= 7) {
            templateBuilder.setHeader(
                Header.Builder()
                    .setTitle("Araç Profili Seç")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            templateBuilder
                .setTitle("Araç Profili Seç")
                .setHeaderAction(Action.BACK)
        }

        return templateBuilder.build()
    }
}
