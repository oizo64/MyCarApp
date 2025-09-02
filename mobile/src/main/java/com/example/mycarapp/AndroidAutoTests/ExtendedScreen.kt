package com.example.mycarapp.AndroidAutoTests

import android.widget.Toast
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import com.example.mycarapp.R

/**
 * Rozszerzony ekran, który pokazuje dodatkowe elementy interfejsu.
 */
class ExtendedScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {

        val actionButton = Action.Builder()
            .setTitle("Kliknij mnie!")
            .setBackgroundColor(CarColor.BLUE)
            .setOnClickListener {
                // Wywołaj nowy ekran HelloWorldScreen
                screenManager.push(MyCarSession(carContext))
            }
            .build()

        val rowWithIcon = Row.Builder()
            .setTitle("Wiersz z ikoną")
            .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_settings)).build())

            .build()

        val pane = Pane.Builder()
            // Poprawiony podtytuł jako zwykły wiersz
            .addRow(Row.Builder().setTitle("Oto kilka dodatkowych opcji:").build())
            .addRow(Row.Builder().setTitle("Pierwsza opcja").build())
            .addRow(rowWithIcon)
            .addAction(actionButton)
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("Rozbudowany Ekran")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}