package com.mushroom.adventure.core.network.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CloudBackupUploadRequest(
    val deviceId: String,
    val backup: JsonObject
)

@Serializable
data class CloudBackupUploadResponse(
    val id: Int,
    val exportedAt: String,
    val createdAt: Long
)

@Serializable
data class CloudBackupSummary(
    val id: Int,
    val exportedAt: String,
    val taskCount: Int,
    val sizeBytes: Int,
    val createdAt: Long
)

@Serializable
data class CloudBackupDownloadResponse(
    val id: Int,
    val deviceId: String,
    val backup: JsonObject,
    val exportedAt: String,
    val createdAt: Long
)
