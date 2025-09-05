package com.example.mycarapp.HiltModule

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideConfigManager(sharedPreferences: SharedPreferences): ConfigManager {
        return ConfigManagerImpl(sharedPreferences)
    }

    @Provides
    @Singleton
    fun provideAppConfig(configManager: ConfigManager): AppConfig {
        return configManager.getConfig()
    }
}