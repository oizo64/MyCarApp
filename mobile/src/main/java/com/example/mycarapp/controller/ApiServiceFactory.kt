package com.example.mycarapp.controller

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiServiceFactory @Inject constructor(
    private val gsonConverterFactory: GsonConverterFactory,
    private val loggingInterceptor: HttpLoggingInterceptor
) {

    fun createPublicApiService(serverUrl: String): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        return createRetrofit(serverUrl, client).create(ApiService::class.java)
    }

    fun createAuthenticatedApiService(serverUrl: String, authToken: String?): ApiService {
        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val modifiedRequest = originalRequest.newBuilder()
                .header("X-ND-Authorization", "Bearer $authToken")
                .build()
            chain.proceed(modifiedRequest)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .build()

        return createRetrofit(serverUrl, client).create(ApiService::class.java)
    }

    private fun createRetrofit(serverUrl: String, client: OkHttpClient): Retrofit {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(gsonConverterFactory)
            .build()
    }
}