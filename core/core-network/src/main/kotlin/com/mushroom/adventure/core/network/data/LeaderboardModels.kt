package com.mushroom.adventure.core.network.data

import kotlinx.serialization.Serializable

@Serializable
data class SubmitScoreRequest(val gameType: String, val score: Int)

@Serializable
data class SubmitScoreResponse(val success: Boolean, val rank: Int? = null)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val userId: Int,
    val nickname: String,
    val score: Int,
    val createdAt: Long
)

@Serializable
data class LeaderboardResponse(
    val entries: List<LeaderboardEntry>,
    val myEntry: LeaderboardEntry? = null
)
