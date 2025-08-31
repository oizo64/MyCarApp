package com.example.mycarapp.dto

data class LoginResponse(
    val token: String,
    val username: String,
    val subsonicSalt: String,
    val subsonicToken: String
)