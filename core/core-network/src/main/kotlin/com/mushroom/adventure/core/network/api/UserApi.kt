package com.mushroom.adventure.core.network.api

import com.mushroom.adventure.core.network.data.UpdateProfileRequest
import com.mushroom.adventure.core.network.data.UserProfile
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

/**
 * Authenticated API for user-related endpoints.
 * Uses the authenticated Retrofit client (with AuthInterceptor + TokenRefreshAuthenticator).
 */
interface UserApi {

    @GET("/user/profile")
    suspend fun getProfile(): UserProfile

    @PUT("/user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): UserProfile
}
