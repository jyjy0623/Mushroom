package com.mushroom.adventure.core.network.repository

import com.mushroom.adventure.core.network.api.LeaderboardApi
import com.mushroom.adventure.core.network.data.LeaderboardResponse
import com.mushroom.adventure.core.network.data.SubmitScoreRequest
import com.mushroom.adventure.core.network.data.SubmitScoreResponse

class LeaderboardRepository(
    private val api: LeaderboardApi
) {
    suspend fun submitScore(gameType: String, score: Int): Result<SubmitScoreResponse> = runCatching {
        api.submitScore(SubmitScoreRequest(gameType, score))
    }

    suspend fun getLeaderboard(gameType: String = "runner", limit: Int = 100): Result<LeaderboardResponse> = runCatching {
        api.getLeaderboard(gameType, limit)
    }

    suspend fun getFriendLeaderboard(gameType: String = "runner"): Result<LeaderboardResponse> = runCatching {
        api.getFriendLeaderboard(gameType)
    }
}
