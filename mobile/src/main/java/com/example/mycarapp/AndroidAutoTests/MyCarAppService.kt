package com.example.mycarapp.AndroidAutoTests

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class MyCarAppService : CarAppService() {
    override fun onCreateSession(): Session {
        return object : Session() {
            override fun onCreateScreen(intent: Intent) =
                ExtendedScreen(carContext)
        }
    }

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }
}