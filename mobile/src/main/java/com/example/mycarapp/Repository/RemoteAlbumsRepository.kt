package com.example.mycarapp.Repository

import com.example.mycarapp.controller.ApiService
import com.example.mycarapp.dto.Album
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class RemoteAlbumsRepository(
    private val authToken: String?,
    private val subsonicSalt: String?,
    private val subsonicToken: String?,
    private val serverUrl: String?,
    private val username: String?
) : AlbumsRepository {

    private val apiService: ApiService = createApiService()

    private fun createApiService(): ApiService {
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val modifiedRequest = originalRequest.newBuilder()
                .header("X-ND-Authorization", "Bearer $authToken")
                .build()
            chain.proceed(modifiedRequest)
        }

        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(serverUrl.orEmpty().let { if (it.endsWith("/")) it else "$it/" })
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }

    override suspend fun getAlbums(): List<Album> {
        return try {
            val response = apiService.getAlbums()
            if (response.isSuccessful) {
                val albums = response.body() ?: emptyList()
                albums.map { album ->
                    album.copy(coverArtUrl = generateCoverArtUrl(album.id))
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

    private fun generateCoverArtUrl(albumId: String): String {
        return "$serverUrl/rest/getCoverArt?u=$username&t=$subsonicToken&s=$subsonicSalt&v=1.8.0&c=NavidromeUI&id=al-$albumId&size=300"
    }
}