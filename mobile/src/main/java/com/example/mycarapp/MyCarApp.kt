package com.example.mycarapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyCarApp : Application() {
    // Możesz dodać tutaj inicjalizację jeśli potrzebujesz
    override fun onCreate() {
        super.onCreate()
        // Tutaj możesz dodać inicjalizację bibliotek itp.
    }
}