package com.mushroom.adventure.core.network.interceptor

import com.mushroom.adventure.core.network.data.RefreshRequest
import com.mushroom.adventure.core.network.data.RefreshResponse
import com.mushroom.adventure.core.network.token.TokenStore
import com.mushroom.core.logging.MushroomLogger
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class TokenRefreshAuthenticator(
    private val tokenStore: TokenStore,
    private val refreshClient: OkHttpClient,
    private val getBaseUrl: () -> String,
    private val onSessionExpired: () -> Unit
) : Authenticator {

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun authenticate(route: Route?, response: Response): Request? {
        // 避免无限重试
        if (responseCount(response) >= 2) {
            MushroomLogger.w(TAG, "Token refresh retry limit reached, session expired")
            tokenStore.clearTokens()
            onSessionExpired()
            return null
        }

        val refreshToken = tokenStore.getRefreshToken() ?: run {
            MushroomLogger.w(TAG, "No refresh token available, session expired")
            onSessionExpired()
            return null
        }

        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
        val refreshRequest = Request.Builder()
            .url("${getBaseUrl()}/auth/refresh")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val refreshResponse = refreshClient.newCall(refreshRequest).execute()
            if (refreshResponse.isSuccessful) {
                val responseBody = refreshResponse.body?.string() ?: return null
                val result = json.decodeFromString(RefreshResponse.serializer(), responseBody)
                tokenStore.saveTokens(result.accessToken, refreshToken)
                MushroomLogger.d(TAG, "Token refreshed successfully")

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${result.accessToken}")
                    .build()
            } else {
                MushroomLogger.w(TAG, "Token refresh failed: ${refreshResponse.code}")
                tokenStore.clearTokens()
                onSessionExpired()
                null
            }
        } catch (e: Exception) {
            MushroomLogger.e(TAG, "Token refresh error", e)
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private const val TAG = "TokenRefresh"
    }
}
