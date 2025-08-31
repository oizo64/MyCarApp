package com.example.mycarapp.Repository

import com.example.mycarapp.dto.Album
import kotlinx.coroutines.delay

class LocalAlbumsRepository : AlbumsRepository {
    override suspend fun getAlbums(): List<Album> {
        // Symulacja opóźnienia, jak przy żądaniu sieciowym
        delay(2000) // Opóźnienie na ę sekund, możesz dostosować

        // Zwracanie statycznej listy
        return listOf(
            Album(name = "Album 1"),
            Album(name = "Album 2"),
            Album(name = "Album 3"),
            Album(name = "Album 4"),
            Album(name = "Album 5"),
            Album(name = "Album 6"),
            Album(name = "Album 7"),
            Album(name = "Album 8"),
            Album(name = "Album 9"),
            Album(name = "Album 10")
        )
    }
}