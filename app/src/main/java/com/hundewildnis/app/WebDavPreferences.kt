package com.hundewildnis.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Speichert WebDAV-Zugangsdaten verschlüsselt (EncryptedSharedPreferences)
 * und nicht-sensible Sync-Einstellungen im normalen "settings"-Prefs-File,
 * analog zum bestehenden Muster (siehe UpdateHelper.kt / ContentUpdateWorker.kt).
 */
class WebDavPreferences(private val context: Context) {

    companion object {
        private const val ENCRYPTED_PREFS_FILE = "webdav_credentials"

        private const val KEY_SERVER_URL = "webdav_server_url"
        private const val KEY_USERNAME = "webdav_username"
        private const val KEY_PASSWORD = "webdav_password"

        private const val KEY_SYNC_ENABLED = "webdav_sync_enabled"
        private const val KEY_AUTO_SYNC_ENABLED = "webdav_auto_sync_enabled"
        private const val KEY_SYNC_INTERVAL_HOURS = "webdav_sync_interval_hours"
        private const val KEY_LAST_SYNC_TIMESTAMP = "webdav_last_sync_timestamp"
        private const val KEY_LAST_SYNC_RESULT = "webdav_last_sync_result"
        private const val KEY_LOCATION_SHARE_ENABLED = "location_share_enabled"
        private const val KEY_WIFI_ONLY_SYNC = "webdav_wifi_only_sync"
        private const val KEY_LAST_SYNCED_FINGERPRINT = "webdav_last_synced_fingerprint"
        private const val KEY_REMOTE_FILE_STATES = "webdav_remote_file_states"

        const val DEFAULT_INTERVAL_HOURS = 6
    }

    private val gson = Gson()

    data class WebDavConfig(
        val serverUrl: String,
        val username: String,
        val password: String
    )

    // ── Verschlüsselte Zugangsdaten ────────────────────────────────────────

    private fun encryptedPrefs() = try {
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
        // Fallback (sollte auf API 24+ praktisch nie eintreten)
        context.getSharedPreferences(ENCRYPTED_PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun saveCredentials(serverUrl: String, username: String, password: String) {
        encryptedPrefs().edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun loadConfig(): WebDavConfig? {
        val prefs = encryptedPrefs()
        val url = prefs.getString(KEY_SERVER_URL, null) ?: return null
        val user = prefs.getString(KEY_USERNAME, null) ?: return null
        val pass = prefs.getString(KEY_PASSWORD, null) ?: return null
        if (url.isBlank() || user.isBlank()) return null
        return WebDavConfig(url, user, pass)
    }

    fun clearCredentials() {
        encryptedPrefs().edit().clear().apply()
    }

    fun hasCredentials(): Boolean = loadConfig() != null

    // ── Nicht-sensible Einstellungen (im bestehenden "settings"-Prefs-File) ─

    private fun settings() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var syncEnabled: Boolean
        get() = settings().getBoolean(KEY_SYNC_ENABLED, false)
        set(value) = settings().edit().putBoolean(KEY_SYNC_ENABLED, value).apply()

    var autoSyncEnabled: Boolean
        get() = settings().getBoolean(KEY_AUTO_SYNC_ENABLED, false)
        set(value) = settings().edit().putBoolean(KEY_AUTO_SYNC_ENABLED, value).apply()

    var syncIntervalHours: Int
        get() = settings().getInt(KEY_SYNC_INTERVAL_HOURS, DEFAULT_INTERVAL_HOURS)
        set(value) = settings().edit().putInt(KEY_SYNC_INTERVAL_HOURS, value).apply()

    var lastSyncTimestamp: Long
        get() = settings().getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(value) = settings().edit().putLong(KEY_LAST_SYNC_TIMESTAMP, value).apply()

    var lastSyncResult: String
        get() = settings().getString(KEY_LAST_SYNC_RESULT, "") ?: ""
        set(value) = settings().edit().putString(KEY_LAST_SYNC_RESULT, value).apply()

    var locationShareEnabled: Boolean
        get() = settings().getBoolean(KEY_LOCATION_SHARE_ENABLED, false)
        set(value) = settings().edit().putBoolean(KEY_LOCATION_SHARE_ENABLED, value).apply()

    /**
     * Wenn true (Standard), läuft der automatische Hintergrund-Sync nur über WLAN
     * (NetworkType.UNMETERED). Verhindert unkontrollierten Mobilfunk-Datenverbrauch.
     * Der manuelle "Jetzt synchronisieren"-Button ist davon bewusst ausgenommen,
     * da er ein expliziter Einzel-Nutzeraktion ist.
     */
    var wifiOnlySync: Boolean
        get() = settings().getBoolean(KEY_WIFI_ONLY_SYNC, true)
        set(value) = settings().edit().putBoolean(KEY_WIFI_ONLY_SYNC, value).apply()

    /**
     * Fingerabdruck (SHA-256) der Backup-relevanten Daten zum Zeitpunkt des
     * letzten erfolgreichen Uploads. Dient dazu, unveränderte Backups nicht
     * erneut hochzuladen (siehe BackupManager.computeBackupFingerprint).
     */
    var lastSyncedFingerprint: String?
        get() = settings().getString(KEY_LAST_SYNCED_FINGERPRINT, null)
        set(value) = settings().edit().putString(KEY_LAST_SYNCED_FINGERPRINT, value).apply()

    /**
     * Merkt sich pro Geräte-Dateiname (z.B. "backup_Pixel8_abcd1234.hundebackup")
     * den zuletzt gesehenen Remote-Zustand (ETag, oder ersatzweise Größe+Änderungsdatum).
     * Ermöglicht bedingte Downloads: Unveränderte fremde Backups werden nicht
     * erneut heruntergeladen und gemerged.
     */
    fun getRemoteFileState(fileName: String): String? = loadRemoteFileStates()[fileName]

    fun setRemoteFileState(fileName: String, state: String) {
        val updated = loadRemoteFileStates().toMutableMap()
        updated[fileName] = state
        settings().edit().putString(KEY_REMOTE_FILE_STATES, gson.toJson(updated)).apply()
    }

    private fun loadRemoteFileStates(): Map<String, String> {
        val json = settings().getString(KEY_REMOTE_FILE_STATES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
