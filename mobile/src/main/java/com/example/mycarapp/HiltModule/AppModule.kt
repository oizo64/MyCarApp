// AppModule.kt
package com.example.mycarapp.HiltModule

import android.content.Context
import android.content.SharedPreferences
import com.example.mycarapp.dto.AppDatabase
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
        return context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig {
        return AppConfig()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideConfigManager(
        sharedPreferences: SharedPreferences,
        appConfig: AppConfig,
        database: AppDatabase
    ): ConfigManager {
        return ConfigManagerImpl(sharedPreferences, appConfig, database)
    }
}