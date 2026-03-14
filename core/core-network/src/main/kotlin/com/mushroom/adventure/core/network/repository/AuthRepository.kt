package com.mushroom.adventure.core.network.repository

import com.mushroom.adventure.core.network.api.AuthApi
import com.mushroom.adventure.core.network.data.*
import com.mushroom.adventure.core.network.token.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val deviceId: String
) {
    private val _isLoggedIn = MutableStateFlow(tokenStore.getAccessToken() != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    suspend fun sendCode(phone: String): Result<SendCodeResponse> = runCatching {
        authApi.sendCode(SendCodeRequest(phone))
    }

    suspend fun login(phone: String, code: String): Result<UserProfile> = runCatching {
        val response = authApi.login(LoginRequest(phone, code, deviceId))
        tokenStore.saveTokens(response.accessToken, response.refreshToken)
        tokenStore.saveUserId(response.user.id)
        _isLoggedIn.value = true
        _currentUser.value = response.user
        response.user
    }

    suspend fun logout(): Result<Unit> = runCatching {
        val refreshToken = tokenStore.getRefreshToken()
        if (refreshToken != null) {
            runCatching { authApi.logout(LogoutRequest(refreshToken)) }
        }
        tokenStore.clearTokens()
        _isLoggedIn.value = false
        _currentUser.value = null
    }

    suspend fun fetchProfile(): Result<UserProfile> = runCatching {
        val profile = authApi.getProfile()
        _currentUser.value = profile
        profile
    }

    suspend fun updateProfile(nickname: String?, avatarUrl: String?): Result<UserProfile> = runCatching {
        val profile = authApi.updateProfile(UpdateProfileRequest(nickname, avatarUrl))
        _currentUser.value = profile
        profile
    }

    fun checkLoginState() {
        _isLoggedIn.value = tokenStore.getAccessToken() != null
    }

    fun onSessionExpired() {
        tokenStore.clearTokens()
        _isLoggedIn.value = false
        _currentUser.value = null
    }
}
