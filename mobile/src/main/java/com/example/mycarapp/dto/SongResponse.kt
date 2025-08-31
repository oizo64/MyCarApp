package com.example.mycarapp.dto

import com.google.gson.annotations.SerializedName

data class SongResponse(
    val id: String,
    val name: String,
    val trackNumber: Int,
    @SerializedName("duration") // Wartość z API może mieć inną nazwę niż w Twojej klasie
    val duration: Int, // Czas trwania utworu w sekundach
    val artist: String
)