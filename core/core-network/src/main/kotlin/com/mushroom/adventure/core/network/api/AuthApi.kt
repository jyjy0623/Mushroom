package com.mushroom.adventure.core.network.api

import com.mushroom.adventure.core.network.data.*
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Unauthenticated API for auth-related endpoints.
 * Uses the plain Retrofit client (no auth interceptor).
 */
interface AuthApi {

    @POST("/auth/send-code")
    suspend fun sendCode(@Body request: SendCodeRequest): SendCodeResponse

    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): RefreshResponse

    @POST("/auth/logout")
    suspend fun logout(@Body request: LogoutRequest)
}
