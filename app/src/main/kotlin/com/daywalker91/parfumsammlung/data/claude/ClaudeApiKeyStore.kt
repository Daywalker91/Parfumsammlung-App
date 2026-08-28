package com.daywalker91.parfumsammlung.data.claude

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Verschlüsselte lokale Ablage des Claude-API-Keys (BYOK-Prinzip, eigener Key
 * pro Nutzer) — gleiches Muster wie zuvor bei GeminiApiKeyStore.
 */
class ClaudeApiKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "claude_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getKey(): String? = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    fun clearKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    private companion object {
        const val KEY_API_KEY = "claude_api_key"
    }
}
