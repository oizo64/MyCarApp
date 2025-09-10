package com.example.mycarapp.utils // lub odpowiedni pakiet dla narzędzi

import com.example.mycarapp.HiltModule.ConfigManager
import com.example.mycarapp.controller.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

class StreamUrlGenerator @Inject constructor(
    private val configManager: ConfigManager
) {
    fun generateStreamUrl(songId: String): String {
        val serverUrl = configManager.getConfig().serverUrl
        val username = configManager.getConfig().username
        val subsonicToken = configManager.getConfig().subsonicToken
        val subsonicSalt = configManager.getConfig().subsonicSalt

        return "$serverUrl/rest/stream?u=$username&t=$subsonicToken&s=$subsonicSalt&v=1.8.0&c=MyCarApp&id=$songId"
    }

    suspend fun getFirstSongStreamUrlForAlbum(
        albumId: String
    ): String? = withContext(Dispatchers.IO) {
        val serverUrl = configManager.getConfig().serverUrl
        val authToken = configManager.getConfig().authToken

        try {
            val apiService = createApiService(
                serverUrl,
                authToken
            ) // Użyj swojego mechanizmu tworzenia ApiService
            val response = apiService.getSongsForAlbum(albumId)

            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                val firstSong = response.body()!!.first()
                val songId = firstSong.id

                if (songId.isNotEmpty()) {
                    return@withContext generateStreamUrl(// Choć w tym przypadku songId jest kluczowe
                        songId
                    )
                } else {
                    // Brak danych do uwierzytelnienia strumienia
                    return@withContext null
                }
            } else {
                // Błąd API lub pusta lista piosenek
                return@withContext null
            }
        } catch (e: Exception) {
            // Obsłuż błędy sieciowe, parsowania itp.
            e.printStackTrace() // Zloguj błąd
            return@withContext null
        }
    }

    fun createApiService(baseUrl: String?, authToken: String?): ApiService {
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
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}