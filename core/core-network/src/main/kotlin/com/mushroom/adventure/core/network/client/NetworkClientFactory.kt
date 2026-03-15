package com.mushroom.adventure.core.network.client

import com.mushroom.adventure.core.network.config.ServerUrlManager
import com.mushroom.adventure.core.network.interceptor.AuthInterceptor
import com.mushroom.adventure.core.network.interceptor.TokenRefreshAuthenticator
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
            .addInterceptor(dynamicUrlInterceptor(serverUrlManager))
            .addInterceptor(requestIdInterceptor())
            .addInterceptor(loggingInterceptor())
            .build()
    }

    fun createAuthenticatedOkHttpClient(
        serverUrlManager: ServerUrlManager,
        authInterceptor: AuthInterceptor,
        authenticator: TokenRefreshAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(dynamicUrlInterceptor(serverUrlManager))
            .addInterceptor(requestIdInterceptor())
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor())
            .authenticator(authenticator)
            .build()
    }

    /** 用于 token 刷新的轻量客户端（不带 authenticator，避免循环） */
    fun createRefreshOkHttpClient(serverUrlManager: ServerUrlManager): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(dynamicUrlInterceptor(serverUrlManager))
            .build()
    }

    fun createRetrofit(serverUrlManager: ServerUrlManager): Retrofit {
        return buildRetrofit(createOkHttpClient(serverUrlManager))
    }

    fun createAuthenticatedRetrofit(
        serverUrlManager: ServerUrlManager,
        authInterceptor: AuthInterceptor,
        authenticator: TokenRefreshAuthenticator
    ): Retrofit {
        return buildRetrofit(createAuthenticatedOkHttpClient(serverUrlManager, authInterceptor, authenticator))
    }

    private fun buildRetrofit(client: OkHttpClient): Retrofit {
        val json = Json { ignoreUnknownKeys = true }
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    private fun dynamicUrlInterceptor(serverUrlManager: ServerUrlManager) =
        okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()
            val newBaseUrl = serverUrlManager.currentUrl.value.toHttpUrl()
            val newUrl = originalRequest.url.newBuilder()
                .scheme(newBaseUrl.scheme)
                .host(newBaseUrl.host)
                .port(newBaseUrl.port)
                .build()
            chain.proceed(originalRequest.newBuilder().url(newUrl).build())
        }

    private fun requestIdInterceptor() = okhttp3.Interceptor { chain ->
        val requestId = java.util.UUID.randomUUID().toString().take(8)
        val newRequest = chain.request().newBuilder()
            .header("X-Request-ID", requestId)
            .build()
        chain.proceed(newRequest)
    }

    private fun loggingInterceptor() = okhttp3.Interceptor { chain ->
        val request = chain.request()
        val reqId = request.header("X-Request-ID") ?: "-"
        try {
            val startTime = System.currentTimeMillis()
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            MushroomLogger.d(TAG, "[$reqId] ${request.method} ${request.url.encodedPath} -> ${response.code} (${duration}ms)")
            response
        } catch (e: Exception) {
            MushroomLogger.e(TAG, "[$reqId] Request failed: ${request.method} ${request.url.encodedPath}", e)
            throw e
        }
    }
}
