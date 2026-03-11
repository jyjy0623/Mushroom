package com.mushroom.adventure.core.network.data

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val version: String = "unknown"
)
