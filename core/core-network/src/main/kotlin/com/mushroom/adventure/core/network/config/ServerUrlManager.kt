package com.mushroom.adventure.core.network.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServerUrlManager(
    context: Context,
    private val defaultUrl: String
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mushroom_network", Context.MODE_PRIVATE)

    private val _currentUrl = MutableStateFlow(loadUrl())
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private fun loadUrl(): String =
        prefs.getString(KEY_SERVER_URL, null) ?: defaultUrl

    fun updateUrl(url: String) {
        val trimmed = url.trimEnd('/')
        prefs.edit().putString(KEY_SERVER_URL, trimmed).apply()
        _currentUrl.value = trimmed
    }

    fun resetToDefault() {
        prefs.edit().remove(KEY_SERVER_URL).apply()
        _currentUrl.value = defaultUrl
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
    }
}
