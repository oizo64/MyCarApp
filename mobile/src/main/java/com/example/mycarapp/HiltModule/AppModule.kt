package com.example.mycarapp.HiltModule

// Mobile/src/main/java/com/example/mycarapp/di/AppModule.kt (kontynuacja)

import android.content.Context
import android.content.SharedPreferences
import com.example.mycarapp.Repository.AlbumsRepository
import com.example.mycarapp.Repository.RemoteAlbumsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val PREFS_NAME = "app_prefs"
    private const val AUTH_TOKEN_KEY = "auth_token"
    private const val SUBSONIC_SALT_KEY = "subsonic_salt"
    private const val SUBSONIC_TOKEN_KEY = "subsonic_token"
    private const val SERVER_URL_KEY = "server_url"
    private const val USERNAME_KEY = "username"

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Zmieniamy dostarczanie Stringów, aby czytały z SharedPreferences
    @Provides
    @Singleton
    @Named("authToken") // Dodajemy nazwy, aby rozróżnić Stringi
    fun provideAuthToken(sharedPreferences: SharedPreferences): String? {
        return sharedPreferences.getString(AUTH_TOKEN_KEY, null)
    }

    @Provides
    @Singleton
    @Named("subsonicSalt")
    fun provideSubsonicSalt(sharedPreferences: SharedPreferences): String? {
        return sharedPreferences.getString(SUBSONIC_SALT_KEY, null)
    }

    @Provides
    @Singleton
    @Named("subsonicToken")
    fun provideSubsonicToken(sharedPreferences: SharedPreferences): String? {
        return sharedPreferences.getString(SUBSONIC_TOKEN_KEY, null)
    }

    @Provides
    @Singleton
    @Named("serverUrl")
    fun provideServerUrl(sharedPreferences: SharedPreferences): String? {
        return sharedPreferences.getString(SERVER_URL_KEY, null)
    }

    @Provides
    @Singleton
    @Named("username")
    fun provideUsername(sharedPreferences: SharedPreferences): String? {
        return sharedPreferences.getString(USERNAME_KEY, null)
    }

    @Provides
    @Singleton
    fun provideAlbumsRepository(
        @Named("authToken") authToken: String?,
        @Named("subsonicSalt") subsonicSalt: String?,
        @Named("subsonicToken") subsonicToken: String?,
        @Named("serverUrl") serverUrl: String?,
        @Named("username") username: String?
    ): AlbumsRepository {
        return RemoteAlbumsRepository(
            authToken = authToken,
            subsonicSalt = subsonicSalt,
            subsonicToken = subsonicToken,
            serverUrl = serverUrl,
            username = username
        )
    }
}
