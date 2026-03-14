package com.mushroom.adventure.core.network.api

import com.mushroom.adventure.core.network.data.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {

    @POST("/auth/send-code")
    suspend fun sendCode(@Body request: SendCodeRequest): SendCodeResponse

    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): RefreshResponse

    @POST("/auth/logout")
    suspend fun logout(@Body request: LogoutRequest)

    @GET("/user/profile")
    suspend fun getProfile(): UserProfile

    @PUT("/user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): UserProfile
}
