package com.example.mycarapp.HiltModule

import com.example.mycarapp.HiltModule.AppConfig

interface ConfigManager {
    fun getConfig(): AppConfig
    fun updateConfig(config: AppConfig)
    fun clearConfig()
}