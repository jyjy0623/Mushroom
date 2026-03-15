package com.mushroom.adventure.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks GitHub for a newer app version.
 *
 * Uses the Git matching-refs API to find tags by prefix, then fetches the
 * corresponding release details. This avoids pagination issues caused by
 * multiple flavors' releases being interleaved in the releases API.
 *
 * Version strings must follow semantic versioning: MAJOR.MINOR.PATCH (e.g. "1.2.0").
 * Each flavor specifies a tag prefix (e.g. "v" for mushroom, "uk-v" for ukdream).
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
     * Checks for a newer version by:
     * 1. Fetching all tags matching [tagPrefix] via Git matching-refs API
     * 2. Finding the highest semantic version among them
     * 3. If newer than [currentVersion], fetching the release details
     *
     * This approach is immune to release ordering/pagination issues because
     * matching-refs returns only tags with the exact prefix.
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
            // Step 1: Fetch all tags matching the prefix
            val refsUrl = "https://api.github.com/repos/$owner/$repo/git/matching-refs/tags/$tagPrefix"
            val refsBody = fetchJson(refsUrl) ?: return@runCatching null
            val refs = JSONArray(refsBody)

            // Step 2: Find the tag with the highest version
            var latestTag: String? = null
            var latestVersion: Triple<Int, Int, Int>? = null

            for (i in 0 until refs.length()) {
                val ref = refs.getJSONObject(i).optString("ref", "")
                // ref format: "refs/tags/v1.2.3" or "refs/tags/uk-v1.2.3"
                val tag = ref.removePrefix("refs/tags/")
                if (!tag.startsWith(tagPrefix)) continue
                // For "v" prefix, skip "uk-v" etc.
                if (tagPrefix == "v" && tag.length > 1 && !tag[1].isDigit()) continue

                val versionStr = tag.removePrefix(tagPrefix)
                val version = parseVersion(versionStr) ?: continue

                if (latestVersion == null || version > latestVersion) {
                    latestVersion = version
                    latestTag = tag
                }
            }

            if (latestTag == null || latestVersion == null) return@runCatching null

            val remoteVersion = latestTag.removePrefix(tagPrefix)
            if (!isNewerVersion(remoteVersion, currentVersion)) return@runCatching null

            // Step 3: Fetch release details for the latest tag
            val releaseUrl = "https://api.github.com/repos/$owner/$repo/releases/tags/$latestTag"
            val releaseBody = fetchJson(releaseUrl) ?: return@runCatching UpdateInfo(
                remoteVersion = remoteVersion,
                releaseNotes = "",
                downloadUrl = "",
            )
            val release = JSONObject(releaseBody)
            UpdateInfo(
                remoteVersion = remoteVersion,
                releaseNotes = release.optString("body", "").take(MAX_NOTES_LENGTH),
                downloadUrl = release.optString("html_url", ""),
            )
        }.onFailure { e ->
            Log.d(TAG, "Update check failed silently: ${e.message}")
        }.getOrNull()
    }

    private fun fetchJson(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            Log.d(TAG, "GitHub API returned $responseCode for $url")
            connection.disconnect()
            return null
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return body
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
