package com.example.mycarapp.HiltModule

data class AppConfig(
    val authToken: String?,
    val subsonicSalt: String?,
    val subsonicToken: String?,
    val serverUrl: String?,
    val username: String?
)