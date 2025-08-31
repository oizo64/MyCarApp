package com.example.mycarapp.Repository

import com.example.mycarapp.dto.Album

interface AlbumsRepository {
    suspend  fun getAlbums(): List<Album>
}