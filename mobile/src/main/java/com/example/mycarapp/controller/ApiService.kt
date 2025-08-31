package com.example.mycarapp.controller

import com.example.mycarapp.dto.Album
import com.example.mycarapp.dto.LoginRequest
import com.example.mycarapp.dto.LoginResponse
import com.example.mycarapp.dto.SongResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// Endpoint logowania, używany do pominięcia nagłówka auth
const val LOGIN_ENDPOINT = "auth/login"
const val GET_ALBUMS_ENDPOINT = "api/album"

// Interfejs Retrofit do definiowania wywołań API
interface ApiService {
    @POST(LOGIN_ENDPOINT)
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>

    // Nowa metoda do pobierania listy albumów
    @GET(GET_ALBUMS_ENDPOINT)
    suspend fun getAlbums(): Response<List<Album>>

    @GET("rest/getCoverArt")
    suspend fun getCoverArt(
        @Query("u") username: String,
        @Query("t") subsonicToken: String,
        @Query("s") subsonicSalt: String,
        @Query("v") version: String,
        @Query("c") client: String,
        @Query("id") albumId: String,
        @Query("size") size: Int
    ): Response<ResponseBody>

    interface ApiService {
        @GET("api/song")
        suspend fun getSongsForAlbum(
            @Query("album_id") albumId: String,
            @Query("_start") start: Int = 0,
            @Query("_end") end: Int = -1,
            @Query("_sort") sort: String = "trackNumber",
            @Query("_order") order: String = "ASC"
        ): Response<List<SongResponse>>
    }
}

