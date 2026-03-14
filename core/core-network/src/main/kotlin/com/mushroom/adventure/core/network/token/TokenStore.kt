package com.mushroom.adventure.core.network.token

interface TokenStore {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String)
    fun clearTokens()
    fun getUserId(): Int?
    fun saveUserId(userId: Int)
    fun getLastPhone(): String?
    fun getLastNickname(): String?
    fun saveLastLogin(phone: String, nickname: String)
}
