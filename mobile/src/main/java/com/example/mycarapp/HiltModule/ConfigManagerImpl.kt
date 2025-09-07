// ConfigManagerImpl.kt
package com.example.mycarapp.HiltModule

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.mycarapp.dto.Album
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigManagerImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val appConfig: AppConfig // Wstrzyknij instancję singletonu AppConfig
) : ConfigManager {

    init {
        // Wczytaj początkową konfigurację z SharedPreferences po wstrzyknięciu
        loadConfigFromSharedPreferences()
    }

    override fun getConfig(): AppConfig {
        return appConfig // Zwróć istniejącą instancję singletonu
    }

    override fun updateConfig(config: AppConfig) {
        // Zaktualizuj in-memory singleton
        appConfig.authToken = config.authToken
        appConfig.subsonicSalt = config.subsonicSalt
        appConfig.subsonicToken = config.subsonicToken
        appConfig.serverUrl = config.serverUrl
        appConfig.username = config.username

        // Zapisz do SharedPreferences
        sharedPreferences.edit {
            putString(Constants.AUTH_TOKEN_KEY, config.authToken)
            putString(Constants.SUBSONIC_SALT_KEY, config.subsonicSalt)
            putString(Constants.SUBSONIC_TOKEN_KEY, config.subsonicToken)
            putString(Constants.SERVER_URL_KEY, config.serverUrl)
            putString(Constants.USERNAME_KEY, config.username)
        }
    }

    override fun clearConfig() {
        // Wyczyść in-memory singleton
        appConfig.authToken = null
        appConfig.subsonicSalt = null
        appConfig.subsonicToken = null
        appConfig.serverUrl = null
        appConfig.username = null
        appConfig.sortedAlbums = emptyList()

        // Wyczyść SharedPreferences
        sharedPreferences.edit {
            remove(Constants.AUTH_TOKEN_KEY)
            remove(Constants.SUBSONIC_SALT_KEY)
            remove(Constants.SUBSONIC_TOKEN_KEY)
            remove(Constants.SERVER_URL_KEY)
            remove(Constants.USERNAME_KEY)
        }
    }

    private fun loadConfigFromSharedPreferences() {
        appConfig.authToken = sharedPreferences.getString(Constants.AUTH_TOKEN_KEY, null)
        appConfig.subsonicSalt = sharedPreferences.getString(Constants.SUBSONIC_SALT_KEY, null)
        appConfig.subsonicToken = sharedPreferences.getString(Constants.SUBSONIC_TOKEN_KEY, null)
        appConfig.serverUrl = sharedPreferences.getString(Constants.SERVER_URL_KEY, null)
        appConfig.username = sharedPreferences.getString(Constants.USERNAME_KEY, null)
    }
}