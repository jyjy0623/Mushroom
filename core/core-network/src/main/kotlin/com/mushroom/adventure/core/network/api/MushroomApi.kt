package com.mushroom.adventure.core.network.api

import com.mushroom.adventure.core.network.data.HealthResponse
import retrofit2.http.GET

interface MushroomApi {
    @GET("/health")
    suspend fun checkHealth(): HealthResponse
}
