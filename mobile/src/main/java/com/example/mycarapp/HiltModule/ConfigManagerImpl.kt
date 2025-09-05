package com.example.mycarapp.HiltModule

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigManagerImpl @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : ConfigManager {

    override fun getConfig(): AppConfig {
        return AppConfig(
            authToken = sharedPreferences.getString(Constants.AUTH_TOKEN_KEY, null),
            subsonicSalt = sharedPreferences.getString(Constants.SUBSONIC_SALT_KEY, null),
            subsonicToken = sharedPreferences.getString(Constants.SUBSONIC_TOKEN_KEY, null),
            serverUrl = sharedPreferences.getString(Constants.SERVER_URL_KEY, null),
            username = sharedPreferences.getString(Constants.USERNAME_KEY, null)
        )
    }

    override fun updateConfig(config: AppConfig) {
        sharedPreferences.edit {
            putString(Constants.AUTH_TOKEN_KEY, config.authToken)
                .putString(Constants.SUBSONIC_SALT_KEY, config.subsonicSalt)
                .putString(Constants.SUBSONIC_TOKEN_KEY, config.subsonicToken)
                .putString(Constants.SERVER_URL_KEY, config.serverUrl)
                .putString(Constants.USERNAME_KEY, config.username)
        }
    }

    override fun clearConfig() {
        sharedPreferences.edit {
            remove(Constants.AUTH_TOKEN_KEY)
                .remove(Constants.SUBSONIC_SALT_KEY)
                .remove(Constants.SUBSONIC_TOKEN_KEY)
                .remove(Constants.SERVER_URL_KEY)
                .remove(Constants.USERNAME_KEY)
        }
    }
}