package com.example.mycarapp.utils

import androidx.room.TypeConverter
import com.example.mycarapp.dto.Genre
import com.example.mycarapp.dto.Participants
import com.example.mycarapp.dto.Tags
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoomConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromGenreList(value: List<Genre>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toGenreList(value: String): List<Genre> {
        val listType = object : TypeToken<List<Genre>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromTags(value: Tags?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toTags(value: String): Tags {
        return gson.fromJson(value, Tags::class.java) ?: Tags(emptyList(), emptyList())
    }

    @TypeConverter
    fun fromParticipants(value: Participants?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toParticipants(value: String): Participants {
        return gson.fromJson(value, Participants::class.java) ?: Participants(
            emptyList(),
            emptyList(),
            emptyList()
        )
    }

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> {
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(value, mapType) ?: emptyMap()
    }
}
