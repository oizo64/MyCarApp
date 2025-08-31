package com.example.mycarapp.HiltModule

import com.example.mycarapp.dto.Album
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfig @Inject constructor() {
    var authToken: String? = null
    var subsonicSalt: String? = null
    var subsonicToken: String? = null
    var serverUrl: String? = null
    var username: String? = null
    var sortedAlbums: List<Album> = emptyList()
}