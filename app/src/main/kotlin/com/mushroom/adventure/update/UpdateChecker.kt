package com.mushroom.adventure.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks GitHub Releases API for a newer app version.
 *
 * Version strings must follow semantic versioning: MAJOR.MINOR.PATCH (e.g. "1.2.0").
 * Each flavor specifies a tag prefix (e.g. "v" for mushroom, "uk-v" for ukdream) —
 * only releases matching that prefix are considered.
 *
 * On any network or parse error the checker returns null (silent failure).
 */
@Singleton
class UpdateChecker @Inject constructor() {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_NOTES_LENGTH = 300
    }

    /**
     * Fetches releases from GitHub and finds the latest release matching [tagPrefix]
     * to compare against [currentVersion].
     *
     * @param owner GitHub repository owner.
     * @param repo GitHub repository name.
     * @param currentVersion The app's current version string (e.g. "1.0.0").
     * @param tagPrefix Tag prefix to match (e.g. "v" for mushroom, "uk-v" for ukdream).
     * @param enabled If false, immediately returns null without making a network call.
     * @return [UpdateInfo] if a newer version is available, null otherwise.
     */
    suspend fun checkForUpdate(
        owner: String,
        repo: String,
        currentVersion: String,
        tagPrefix: String = "v",
        enabled: Boolean = true,
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!enabled || owner.isBlank() || repo.isBlank()) return@withContext null

        runCatching {
            // Use per_page=10 to limit payload; releases are sorted newest-first by default
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases?per_page=10"
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "GitHub API returned $responseCode — skipping update check")
                return@runCatching null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val releases = JSONArray(body)
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                val tag = release.optString("tag_name", "")
                // Only match releases whose tag starts with this flavor's prefix
                if (!tag.startsWith(tagPrefix)) continue
                // For "v" prefix, skip "uk-v" etc. by checking the char after prefix is a digit
                if (tagPrefix == "v" && tag.length > 1 && !tag[1].isDigit()) continue

                val remoteVersion = tag.removePrefix(tagPrefix)
                if (parseVersion(remoteVersion) == null) continue

                if (isNewerVersion(remoteVersion, currentVersion)) {
                    val htmlUrl = release.optString("html_url", "")
                    val notes = release.optString("body", "").take(MAX_NOTES_LENGTH)
                    return@runCatching UpdateInfo(
                        remoteVersion = remoteVersion,
                        releaseNotes = notes,
                        downloadUrl = htmlUrl,
                    )
                } else {
                    return@runCatching null
                }
            }
            null
        }.onFailure { e ->
            Log.d(TAG, "Update check failed silently: ${e.message}")
        }.getOrNull()
    }

    /**
     * Returns true if [remote] is strictly newer than [current].
     * Both strings must be in MAJOR.MINOR.PATCH format; returns false on parse error.
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        val r = parseVersion(remote) ?: return false
        val c = parseVersion(current) ?: return false
        return r > c
    }

    private fun parseVersion(version: String): Triple<Int, Int, Int>? {
        val parts = version.trim().split(".")
        if (parts.size != 3) return null
        return runCatching {
            Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }.getOrNull()
    }

    // Comparable helper so we can use > on Triple<Int,Int,Int>
    private operator fun Triple<Int, Int, Int>.compareTo(other: Triple<Int, Int, Int>): Int {
        if (first != other.first) return first.compareTo(other.first)
        if (second != other.second) return second.compareTo(other.second)
        return third.compareTo(other.third)
    }
}
