package com.mushroom.adventure.core.network.repository

import com.mushroom.adventure.core.network.api.FriendApi
import com.mushroom.adventure.core.network.data.AddFriendRequest
import com.mushroom.adventure.core.network.data.AddFriendResponse
import com.mushroom.adventure.core.network.data.FriendListResponse
import com.mushroom.adventure.core.network.data.FriendRequestListResponse
import com.mushroom.adventure.core.network.data.FriendStatsResponse
import com.mushroom.adventure.core.network.data.HandleRequestBody
import com.mushroom.adventure.core.network.data.HandleRequestResponse
import com.mushroom.adventure.core.network.data.PendingCountResponse

class FriendRepository(
    private val api: FriendApi
) {
    suspend fun addFriend(phone: String, message: String = ""): Result<AddFriendResponse> = runCatching {
        api.addFriend(AddFriendRequest(phone, message))
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

    suspend fun getFriendRequests(): Result<FriendRequestListResponse> = runCatching {
        api.getFriendRequests()
    }

    suspend fun getPendingRequestCount(): Result<PendingCountResponse> = runCatching {
        api.getPendingRequestCount()
    }

    suspend fun acceptFriendRequest(requestId: Int): Result<HandleRequestResponse> = runCatching {
        api.handleFriendRequest(requestId, HandleRequestBody("accept"))
    }

    suspend fun rejectFriendRequest(requestId: Int): Result<HandleRequestResponse> = runCatching {
        api.handleFriendRequest(requestId, HandleRequestBody("reject"))
    }
}
