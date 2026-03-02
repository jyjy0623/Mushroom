package com.mushroom.adventure.update

/**
 * Information about an available app update fetched from GitHub Releases API.
 */
data class UpdateInfo(
    val remoteVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
)
