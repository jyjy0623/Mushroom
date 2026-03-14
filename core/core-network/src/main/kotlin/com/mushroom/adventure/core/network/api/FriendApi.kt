package com.mushroom.adventure.core.network.api

import com.mushroom.adventure.core.network.data.AddFriendRequest
import com.mushroom.adventure.core.network.data.AddFriendResponse
import com.mushroom.adventure.core.network.data.FriendListResponse
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
}
