package com.mushroom.adventure.core.network.repository

import com.mushroom.adventure.core.network.api.MushroomApi
import com.mushroom.adventure.core.network.data.CloudBackupDownloadResponse
import com.mushroom.adventure.core.network.data.CloudBackupSummary
import com.mushroom.adventure.core.network.data.CloudBackupUploadRequest
import com.mushroom.adventure.core.network.data.CloudBackupUploadResponse
import kotlinx.serialization.json.JsonObject

class CloudBackupRepository(private val api: MushroomApi) {

    suspend fun uploadBackup(
        deviceId: String,
        backupJson: JsonObject
    ): Result<CloudBackupUploadResponse> = runCatching {
        api.uploadBackup(CloudBackupUploadRequest(deviceId, backupJson))
    }

    suspend fun listBackups(deviceId: String): Result<List<CloudBackupSummary>> = runCatching {
        api.listBackups(deviceId)
    }

    suspend fun downloadBackup(id: Int): Result<CloudBackupDownloadResponse> = runCatching {
        api.downloadBackup(id)
    }

    suspend fun deleteBackup(id: Int): Result<Unit> = runCatching {
        api.deleteBackup(id)
    }
}
