package com.mushroom.adventure.core.network.repository

import com.mushroom.adventure.core.network.api.AuthApi
import com.mushroom.adventure.core.network.api.UserApi
import com.mushroom.adventure.core.network.data.*
import com.mushroom.adventure.core.network.token.TokenStore
import com.mushroom.core.logging.MushroomLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

private const val TAG = "AuthRepo"

class AuthRepository(
    private val authApi: AuthApi,
    private val userApi: UserApi,
    private val tokenStore: TokenStore,
    private val deviceId: String,
    private val avatarDir: File
) {
    private val _isLoggedIn = MutableStateFlow(tokenStore.getAccessToken() != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val httpClient = OkHttpClient()

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
        // 如果服务端有头像 URL，后台下载到本地缓存
        if (profile.avatarUrl.isNotEmpty()) {
            downloadAvatarToLocal(profile.avatarUrl)
        }
        profile
    }

    suspend fun updateProfile(nickname: String?, avatarUrl: String?): Result<UserProfile> = runCatching {
        val profile = userApi.updateProfile(UpdateProfileRequest(nickname, avatarUrl))
        _currentUser.value = profile
        profile
    }

    suspend fun uploadAvatar(avatarBytes: ByteArray, fileName: String): Result<UserProfile> = runCatching {
        MushroomLogger.i(TAG, "uploadAvatar: fileName=$fileName, bytes=${avatarBytes.size}")
        val requestBody = avatarBytes.toRequestBody("image/*".toMediaType())
        val part = MultipartBody.Part.createFormData("avatar", fileName, requestBody)
        val profile = userApi.uploadAvatar(part)
        MushroomLogger.i(TAG, "uploadAvatar: server returned avatarUrl=${profile.avatarUrl}")
        // 上传成功后，直接把压缩后的字节存到本地
        saveAvatarLocally(avatarBytes)
        _currentUser.value = profile
        profile
    }

    /** 获取本地头像文件路径，供 UI 显示 */
    fun getLocalAvatarPath(): String? {
        val file = File(avatarDir, "avatar.jpg")
        return if (file.exists()) file.absolutePath else null
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

    suspend fun syncStats(
        currentStreak: Int,
        longestStreak: Int,
        totalCheckins: Int,
        totalMushroomPoints: Int
    ): Result<Unit> = runCatching {
        userApi.syncStats(
            SyncStatsRequest(
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                totalCheckins = totalCheckins,
                totalMushroomPoints = totalMushroomPoints
            )
        )
    }

    fun getLastPhone(): String? = tokenStore.getLastPhone()

    fun getLastNickname(): String? = tokenStore.getLastNickname()

    private fun saveAvatarLocally(bytes: ByteArray) {
        try {
            avatarDir.mkdirs()
            File(avatarDir, "avatar.jpg").writeBytes(bytes)
            MushroomLogger.i(TAG, "Avatar saved locally, bytes=${bytes.size}")
        } catch (e: Exception) {
            MushroomLogger.w(TAG, "Failed to save avatar locally", e)
        }
    }

    private suspend fun downloadAvatarToLocal(url: String) {
        // 如果本地已有缓存，跳过下载
        val localFile = File(avatarDir, "avatar.jpg")
        if (localFile.exists()) {
            MushroomLogger.d(TAG, "Avatar already cached locally")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                MushroomLogger.i(TAG, "Downloading avatar: $url")
                val request = Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        avatarDir.mkdirs()
                        response.body?.bytes()?.let { bytes ->
                            localFile.writeBytes(bytes)
                            MushroomLogger.i(TAG, "Avatar downloaded and cached, bytes=${bytes.size}")
                        }
                    } else {
                        MushroomLogger.w(TAG, "Avatar download failed: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                MushroomLogger.w(TAG, "Avatar download failed", e)
            }
        }
    }
}
