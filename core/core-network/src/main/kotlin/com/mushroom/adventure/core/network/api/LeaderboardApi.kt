package com.mushroom.adventure.core.network.api

import com.mushroom.adventure.core.network.data.LeaderboardResponse
import com.mushroom.adventure.core.network.data.SubmitScoreRequest
import com.mushroom.adventure.core.network.data.SubmitScoreResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LeaderboardApi {
    @POST("/rank/submit")
    suspend fun submitScore(@Body request: SubmitScoreRequest): SubmitScoreResponse

    @GET("/rank/list")
    suspend fun getLeaderboard(
        @Query("gameType") gameType: String = "runner",
        @Query("limit") limit: Int = 100
    ): LeaderboardResponse

    @GET("/rank/friends")
    suspend fun getFriendLeaderboard(
        @Query("gameType") gameType: String = "runner"
    ): LeaderboardResponse
}
