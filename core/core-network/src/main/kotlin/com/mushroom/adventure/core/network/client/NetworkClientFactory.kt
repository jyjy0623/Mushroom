package com.mushroom.adventure.core.network.client

import com.mushroom.adventure.core.network.config.ServerUrlManager
import com.mushroom.core.logging.MushroomLogger
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    fun createOkHttpClient(serverUrlManager: ServerUrlManager): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // 动态 base URL 拦截器：每次请求都读取最新的服务器地址
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val newBaseUrl = serverUrlManager.currentUrl.value.toHttpUrl()
                val newUrl = originalRequest.url.newBuilder()
                    .scheme(newBaseUrl.scheme)
                    .host(newBaseUrl.host)
                    .port(newBaseUrl.port)
                    .build()
                val newRequest = originalRequest.newBuilder().url(newUrl).build()
                chain.proceed(newRequest)
            }
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

    fun createRetrofit(serverUrlManager: ServerUrlManager): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        val contentType = "application/json".toMediaType()

        // placeholder base URL，实际会被动态拦截器替换
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(createOkHttpClient(serverUrlManager))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}
