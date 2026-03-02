package com.mushroom.adventure.parent

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "parent_prefs"
private const val KEY_PIN_HASH = "pin_hash"

/**
 * Stores the parent PIN as a SHA-256 hash in private SharedPreferences.
 * No plaintext PIN is ever persisted.
 */
@Singleton
class PinRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPinSet(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return stored == hash(pin)
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
    }

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
