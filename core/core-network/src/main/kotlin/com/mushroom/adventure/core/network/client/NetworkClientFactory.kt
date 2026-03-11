package com.mushroom.adventure.core.network.client

import com.mushroom.core.logging.MushroomLogger
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClientFactory {
    private const val TAG = "Network"
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L

    fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                try {
                    val startTime = System.currentTimeMillis()
                    val response = chain.proceed(request)
                    val duration = System.currentTimeMillis() - startTime
                    MushroomLogger.d(TAG, "${request.method} ${request.url} -> ${response.code} (${duration}ms)")
                    response
                } catch (e: Exception) {
                    MushroomLogger.e(TAG, "Request failed: ${request.method} ${request.url}", e)
                    throw e
                }
            }
            .build()
    }

    fun createRetrofit(baseUrl: String): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createOkHttpClient())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
