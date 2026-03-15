package com.mushroom.adventure.core.network.api

import com.mushroom.adventure.core.network.data.AddFriendRequest
import com.mushroom.adventure.core.network.data.AddFriendResponse
import com.mushroom.adventure.core.network.data.FriendListResponse
import com.mushroom.adventure.core.network.data.FriendRequestListResponse
import com.mushroom.adventure.core.network.data.FriendStatsResponse
import com.mushroom.adventure.core.network.data.HandleRequestBody
import com.mushroom.adventure.core.network.data.PendingCountResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface FriendApi {
    @POST("/friend/add")
    suspend fun addFriend(@Body request: AddFriendRequest): AddFriendResponse

    @GET("/friend/list")
    suspend fun getFriendList(): FriendListResponse

    @DELETE("/friend/{userId}")
    suspend fun removeFriend(@Path("userId") userId: Int)

    @GET("/friend/{userId}/stats")
    suspend fun getFriendStats(@Path("userId") userId: Int): FriendStatsResponse

    @GET("/friend/requests")
    suspend fun getFriendRequests(): FriendRequestListResponse

    @GET("/friend/requests/count")
    suspend fun getPendingRequestCount(): PendingCountResponse

    @POST("/friend/requests/{id}/handle")
    suspend fun handleFriendRequest(@Path("id") requestId: Int, @Body body: HandleRequestBody)
}
