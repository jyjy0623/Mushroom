package com.mushroom.adventure.core.network.repository

import com.mushroom.adventure.core.network.api.AuthApi
import com.mushroom.adventure.core.network.api.UserApi
import com.mushroom.adventure.core.network.data.*
import com.mushroom.adventure.core.network.token.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository(
    private val authApi: AuthApi,
    private val userApi: UserApi,
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

    suspend fun login(phone: String, code: String, nickname: String? = null): Result<UserProfile> = runCatching {
        val response = authApi.login(LoginRequest(phone, code, deviceId, nickname))
        tokenStore.saveTokens(response.accessToken, response.refreshToken)
        tokenStore.saveUserId(response.user.id)
        tokenStore.saveLastLogin(response.user.phone, response.user.nickname)
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
        val profile = userApi.getProfile()
        _currentUser.value = profile
        profile
    }

    suspend fun updateProfile(nickname: String?, avatarUrl: String?): Result<UserProfile> = runCatching {
        val profile = userApi.updateProfile(UpdateProfileRequest(nickname, avatarUrl))
        _currentUser.value = profile
        profile
    }

    suspend fun uploadAvatar(avatarBytes: ByteArray, fileName: String): Result<UserProfile> = runCatching {
        val requestBody = avatarBytes.toRequestBody("image/*".toMediaType())
        val part = MultipartBody.Part.createFormData("avatar", fileName, requestBody)
        val profile = userApi.uploadAvatar(part)
        _currentUser.value = profile
        profile
    }

    fun checkLoginState() {
        _isLoggedIn.value = tokenStore.getAccessToken() != null
    }

    /**
     * App 启动时恢复登录会话：如果本地有 token，自动拉取用户资料。
     * 网络失败时静默忽略，下次打开 App 再重试。
     */
    suspend fun restoreSession() {
        if (tokenStore.getAccessToken() != null) {
            runCatching { fetchProfile() }
                .onSuccess { result ->
                    result.onSuccess { profile ->
                        tokenStore.saveLastLogin(profile.phone, profile.nickname)
                    }
                }
        }
    }

    fun onSessionExpired() {
        tokenStore.clearTokens()
        _isLoggedIn.value = false
        _currentUser.value = null
    }

    fun getLastPhone(): String? = tokenStore.getLastPhone()

    fun getLastNickname(): String? = tokenStore.getLastNickname()
}
