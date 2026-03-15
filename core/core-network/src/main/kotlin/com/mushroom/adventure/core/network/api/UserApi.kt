package com.mushroom.adventure.core.network.api

import com.mushroom.adventure.core.network.data.SyncStatsRequest
import com.mushroom.adventure.core.network.data.UpdateProfileRequest
import com.mushroom.adventure.core.network.data.UserProfile
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

/**
 * Authenticated API for user-related endpoints.
 * Uses the authenticated Retrofit client (with AuthInterceptor + TokenRefreshAuthenticator).
 */
interface UserApi {

    @GET("/user/profile")
    suspend fun getProfile(): UserProfile

    @PUT("/user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): UserProfile

    @Multipart
    @POST("/user/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): UserProfile

    @POST("/user/stats")
    suspend fun syncStats(@Body request: SyncStatsRequest)
}
