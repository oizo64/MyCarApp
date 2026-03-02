package com.example.mycarapp.Repository

import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.controller.ApiServiceFactory
import com.example.mycarapp.dto.Album
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteAlbumsRepository @Inject constructor(
    private val apiServiceFactory: ApiServiceFactory,
    private val configManager: ConfigManager
) : AlbumsRepository {

    override suspend fun getAlbums(): List<Album> {
        val config = configManager.getConfig()
        val serverUrl = config.serverUrl ?: return emptyList()
        val authToken = config.authToken
        
        val apiService = apiServiceFactory.createAuthenticatedApiService(serverUrl, authToken)

        return try {
            val response = apiService.getAlbums()
            if (response.isSuccessful) {
                val albums = response.body() ?: emptyList()
                albums.map { album ->
                    album.copy(coverArtUrl = generateCoverArtUrl(album.id, config.serverUrl, config.username, config.subsonicToken, config.subsonicSalt))
                }
            } else {
                println("API call failed with code: ${response.code()}")
                emptyList()
            }
        } catch (e: IOException) {
            println("Network error: ${e.message}")
            emptyList()
        }
    }

    private fun generateCoverArtUrl(albumId: String, serverUrl: String?, username: String?, subsonicToken: String?, subsonicSalt: String?): String {
        return "$serverUrl/rest/getCoverArt?u=$username&t=$subsonicToken&s=$subsonicSalt&v=1.8.0&c=NavidromeUI&id=al-$albumId&size=300"
    }
}