package com.hundewildnis.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Zentrale Klasse für App-Einstellungen, inklusive verschlüsselter API-Keys.
 */
class AppSettings(private val context: Context) {

    companion object {
        private const val ENCRYPTED_PREFS_FILE = "secure_app_settings"
        private const val KEY_ORS_API_KEY = "ors_api_key"
    }

    private fun securePrefs() = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences(ENCRYPTED_PREFS_FILE, Context.MODE_PRIVATE)
    }

    /**
     * Liefert den ORS API Key. 
     * Priorität: Nutzer-Eingabe (verschlüsselt gespeichert) -> BuildConfig (Fallback)
     */
    fun getOrsApiKey(): String {
        val userKey = securePrefs().getString(KEY_ORS_API_KEY, null)
        return if (!userKey.isNullOrBlank()) {
            userKey
        } else {
            BuildConfig.ORS_API_KEY
        }
    }

    fun saveOrsApiKey(key: String) {
        securePrefs().edit().putString(KEY_ORS_API_KEY, key.trim()).apply()
    }

    fun hasCustomOrsKey(): Boolean {
        return !securePrefs().getString(KEY_ORS_API_KEY, null).isNullOrBlank()
    }
}
