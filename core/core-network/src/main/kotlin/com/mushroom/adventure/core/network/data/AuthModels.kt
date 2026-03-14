package com.mushroom.adventure.core.network.data

import kotlinx.serialization.Serializable

@Serializable
data class SendCodeRequest(val phone: String)

@Serializable
data class SendCodeResponse(val success: Boolean, val message: String)

@Serializable
data class LoginRequest(val phone: String, val code: String, val deviceId: String, val nickname: String? = null)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserProfile,
    val isNewUser: Boolean = false
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponse(val accessToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class UserProfile(
    val id: Int,
    val phone: String,
    val nickname: String,
    val avatarUrl: String,
)

@Serializable
data class UpdateProfileRequest(
    val nickname: String? = null,
    val avatarUrl: String? = null
)
