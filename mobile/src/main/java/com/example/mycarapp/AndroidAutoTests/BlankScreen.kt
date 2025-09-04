package com.example.mycarapp.AndroidAutoTests

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class BlankScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        // KROK 1: Stwórz wiersz (Row)
        val row = Row.Builder().setTitle("Witaj w MyCarApp!").build()

        // KROK 2: Stwórz panel (Pane) i dodaj do niego wiersz
        val pane = Pane.Builder().addRow(row).build()

        // KROK 3: Stwórz szablon (PaneTemplate) używając panelu
        return PaneTemplate.Builder(pane)
            .setTitle("MyCarApp")
            .build()
    }
}