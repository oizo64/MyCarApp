package com.example.mycarapp.HiltModule


interface ConfigManager {
    fun getConfig(): AppConfig
    fun updateConfig(config: AppConfig)
    fun clearConfig()
}
