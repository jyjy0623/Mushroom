package com.mushroom.adventure.core.network.token

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedTokenStore(context: Context) : TokenStore {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "mushroom_auth_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    override fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    override fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    override fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .apply()
    }

    override fun getUserId(): Int? {
        val id = prefs.getInt(KEY_USER_ID, -1)
        return if (id == -1) null else id
    }

    override fun saveUserId(userId: Int) {
        prefs.edit().putInt(KEY_USER_ID, userId).apply()
    }

    override fun getLastPhone(): String? = prefs.getString(KEY_LAST_PHONE, null)

    override fun getLastNickname(): String? = prefs.getString(KEY_LAST_NICKNAME, null)

    override fun saveLastLogin(phone: String, nickname: String) {
        prefs.edit()
            .putString(KEY_LAST_PHONE, phone)
            .putString(KEY_LAST_NICKNAME, nickname)
            .apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LAST_PHONE = "last_phone"
        private const val KEY_LAST_NICKNAME = "last_nickname"
    }
}
