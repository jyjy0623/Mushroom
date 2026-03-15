package com.mushroom.adventure.core.network.repository

import com.mushroom.adventure.core.network.api.FriendApi
import com.mushroom.adventure.core.network.data.AddFriendRequest
import com.mushroom.adventure.core.network.data.AddFriendResponse
import com.mushroom.adventure.core.network.data.FriendListResponse
import com.mushroom.adventure.core.network.data.FriendStatsResponse

class FriendRepository(
    private val api: FriendApi
) {
    suspend fun addFriend(phone: String): Result<AddFriendResponse> = runCatching {
        api.addFriend(AddFriendRequest(phone))
    }

    suspend fun getFriendList(): Result<FriendListResponse> = runCatching {
        api.getFriendList()
    }

    suspend fun removeFriend(userId: Int): Result<Unit> = runCatching {
        api.removeFriend(userId)
    }

    suspend fun getFriendStats(userId: Int): Result<FriendStatsResponse> = runCatching {
        api.getFriendStats(userId)
    }
}
