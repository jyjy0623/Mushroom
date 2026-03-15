package com.mushroom.adventure.core.network.data

import kotlinx.serialization.Serializable

@Serializable
data class AddFriendRequest(val phone: String, val message: String = "")

@Serializable
data class AddFriendResponse(val success: Boolean, val message: String)

@Serializable
data class FriendInfo(val userId: Int, val nickname: String, val maskedPhone: String)

@Serializable
data class FriendListResponse(val friends: List<FriendInfo>)

@Serializable
data class FriendRequestInfo(
    val id: Int,
    val fromUserId: Int,
    val nickname: String,
    val maskedPhone: String,
    val message: String,
    val createdAt: Long
)

@Serializable
data class FriendRequestListResponse(
    val requests: List<FriendRequestInfo>,
    val total: Int
)

@Serializable
data class HandleRequestBody(val action: String)

@Serializable
data class PendingCountResponse(val count: Int)

@Serializable
data class FriendStatsResponse(
    val userId: Int,
    val nickname: String,
    val bestScore: Int? = null,
    val globalRank: Int? = null,
    val gameType: String = "runner",
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalCheckins: Int = 0,
    val totalMushroomPoints: Int = 0
)

@Serializable
data class SyncStatsRequest(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCheckins: Int,
    val totalMushroomPoints: Int
)
