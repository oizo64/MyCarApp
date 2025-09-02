package com.example.mycarapp.AndroidAutoTests

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/**
 * Sesja aplikacji, która zarządza stosem ekranów.
 * Tutaj definiujemy, który ekran ma być wyświetlony jako pierwszy.
 */
class MyCarSession(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {

        // Utwórz akcję do cofnięcia się
        val backAction = Action.Builder()
            .setTitle("Cofnij")
            .setOnClickListener {
                screenManager.pop()
            }
            .build()

        val pane = Pane.Builder()
            // Poprawiony podtytuł jako zwykły wiersz
            .addRow(Row.Builder().setTitle("Witaj w Android Auto!").build())
            .addAction(backAction)
            .build()

        return PaneTemplate.Builder(pane)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}