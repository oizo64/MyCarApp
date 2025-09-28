package com.example.mycarapp.dto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val serverUrl: String,
    val username: String,
    val password: String,
    val authToken: String? = null,
    val subsonicSalt: String? = null,
    val subsonicToken: String? = null,
    val isActive: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)