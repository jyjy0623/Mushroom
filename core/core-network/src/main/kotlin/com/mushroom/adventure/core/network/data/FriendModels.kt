package com.mushroom.adventure.core.network.data

import kotlinx.serialization.Serializable

@Serializable
data class AddFriendRequest(val phone: String)

@Serializable
data class AddFriendResponse(val success: Boolean, val message: String)

@Serializable
data class FriendInfo(val userId: Int, val nickname: String, val maskedPhone: String)

@Serializable
data class FriendListResponse(val friends: List<FriendInfo>)
