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
        var normalized = url.trim().trimEnd('/')
        // 用户可能只输入 ip:port，自动补全 http://
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
        _currentUrl.value = normalized
    }

    fun resetToDefault() {
        prefs.edit().remove(KEY_SERVER_URL).apply()
        _currentUrl.value = defaultUrl
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
    }
}
