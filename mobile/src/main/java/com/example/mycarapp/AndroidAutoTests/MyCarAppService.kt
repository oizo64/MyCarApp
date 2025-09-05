package com.example.mycarapp.AndroidAutoTests

import android.content.ComponentName
import android.support.v4.media.MediaBrowserCompat
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

class MyCarAppService : CarAppService() {
    private lateinit var mediaBrowser: MediaBrowserCompat

    override fun onCreate() {
        super.onCreate()
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this.packageName, MusicPlaybackService::class.java.name),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                }

                override fun onConnectionSuspended() {
                }

                override fun onConnectionFailed() {
                }
            },
            null
        )
        mediaBrowser.connect()
    }

    override fun createHostValidator(): HostValidator {
        // Ta metoda jest wymagana do zapewnienia bezpieczeństwa.
        // Domyślnie używamy listy dozwolonych hostów od Google.
        return HostValidator.Builder(this)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        // Ta metoda jest wywoływana, gdy aplikacja jest uruchamiana.
        // Musisz zwrócić instancję swojej sesji.
        return MySession()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaBrowser.isConnected) {
            mediaBrowser.disconnect()
        }
    }
}