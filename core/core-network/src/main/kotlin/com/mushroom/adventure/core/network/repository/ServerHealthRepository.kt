package com.mushroom.adventure.core.network.repository

import com.mushroom.adventure.core.network.api.MushroomApi
import com.mushroom.adventure.core.network.data.HealthResponse

class ServerHealthRepository(private val api: MushroomApi) {
    suspend fun checkHealth(): Result<HealthResponse> = try {
        val response = api.checkHealth()
        Result.success(response)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
