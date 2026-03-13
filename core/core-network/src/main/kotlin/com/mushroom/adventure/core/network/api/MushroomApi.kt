package com.mushroom.adventure.core.network.api

import com.mushroom.adventure.core.network.data.CloudBackupDownloadResponse
import com.mushroom.adventure.core.network.data.CloudBackupSummary
import com.mushroom.adventure.core.network.data.CloudBackupUploadRequest
import com.mushroom.adventure.core.network.data.CloudBackupUploadResponse
import com.mushroom.adventure.core.network.data.HealthResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MushroomApi {
    @GET("/health")
    suspend fun checkHealth(): HealthResponse

    @POST("/backup/upload")
    suspend fun uploadBackup(@Body request: CloudBackupUploadRequest): CloudBackupUploadResponse

    @GET("/backup/list")
    suspend fun listBackups(@Query("deviceId") deviceId: String): List<CloudBackupSummary>

    @GET("/backup/{id}/download")
    suspend fun downloadBackup(@Path("id") id: Int): CloudBackupDownloadResponse

    @DELETE("/backup/{id}")
    suspend fun deleteBackup(@Path("id") id: Int)
}
