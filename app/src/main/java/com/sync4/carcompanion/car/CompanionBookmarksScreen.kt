package com.sync4.carcompanion.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.sync4.carcompanion.MainActivity
import com.sync4.carcompanion.state.CompanionStateRepository

/**
 * Araç multimedya ekranından (Fiat Egea Uconnect 7" veya Ford SYNC 4)
 * telefonun WebView sekmesine hızlı kaynak göndermeyi sağlayan liste ekranı.
 */
class CompanionBookmarksScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        CompanionStateRepository.defaultBookmarks.forEach { bookmark ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(bookmark.title)
                    .addText(bookmark.subtitle)
                    .setOnClickListener(
                        ParkedOnlyOnClickListener.create {
                            CompanionStateRepository.requestOpenUrl(bookmark.url)
                            val phoneIntent = Intent(carContext, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            carContext.startActivity(phoneIntent)
                            CarToast.makeText(
                                carContext,
                                "${bookmark.title} telefonda açılıyor...",
                                CarToast.LENGTH_SHORT
                            ).show()
                            screenManager.pop()
                        }
                    )
                    .build()
            )
        }

        val templateBuilder = ListTemplate.Builder()
            .setSingleList(listBuilder.build())

        if (carContext.carAppApiLevel >= 7) {
            templateBuilder.setHeader(
                Header.Builder()
                    .setTitle("Hızlı Web Kaynakları")
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            templateBuilder
                .setTitle("Hızlı Web Kaynakları")
                .setHeaderAction(Action.BACK)
        }

        return templateBuilder.build()
    }
}
