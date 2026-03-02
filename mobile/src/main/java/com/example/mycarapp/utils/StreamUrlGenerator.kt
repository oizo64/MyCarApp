package com.example.mycarapp.utils

import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.controller.ApiServiceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamUrlGenerator @Inject constructor(
    private val configManager: ConfigManager,
    private val apiServiceFactory: ApiServiceFactory
) {
    fun generateStreamUrl(songId: String): String {
        val config = configManager.getConfig()
        val serverUrl = config.serverUrl
        val username = config.username
        val subsonicToken = config.subsonicToken
        val subsonicSalt = config.subsonicSalt

        return "$serverUrl/rest/stream?u=$username&t=$subsonicToken&s=$subsonicSalt&v=1.8.0&c=MyCarApp&id=$songId"
    }

    suspend fun getFirstSongStreamUrlForAlbum(
        albumId: String
    ): String? = withContext(Dispatchers.IO) {
        val config = configManager.getConfig()
        val serverUrl = config.serverUrl ?: return@withContext null
        val authToken = config.authToken

        try {
            val apiService = apiServiceFactory.createAuthenticatedApiService(serverUrl, authToken)
            val response = apiService.getSongsForAlbum(albumId)

            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                val firstSong = response.body()!!.first()
                val songId = firstSong.id

                if (songId.isNotEmpty()) {
                    return@withContext generateStreamUrl(songId)
                }
            }
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}