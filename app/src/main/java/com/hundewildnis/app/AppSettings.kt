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

        // Netzwerkdienste (Serveradressen sind nicht geheim, Schlüssel schon)
        private const val PLAIN_PREFS_FILE = "app_network_settings"
        private const val KEY_WEATHER_BASE_URL = "weather_base_url"
        private const val KEY_AIR_QUALITY_BASE_URL = "air_quality_base_url"
        private const val KEY_OVERPASS_URL = "overpass_url"
        private const val KEY_WEATHER_API_KEY = "weather_api_key"          // Open-Meteo-Schlüssel
        private const val KEY_OWM_API_KEY = "owm_api_key"                  // OpenWeatherMap-Schlüssel
        private const val KEY_WEATHER_PROVIDER = "weather_provider"
        private const val KEY_WEATHER_BG_ANIMATED = "weather_bg_animated"

        /**
         * Prüft eine eingegebene Serveradresse. Erlaubt sind nur https-Adressen ohne Leerzeichen,
         * Abfrageteil oder Anker. Ein abschließender Schrägstrich wird entfernt.
         * Rückgabe: bereinigte Adresse oder null, wenn die Eingabe ungültig ist.
         */
        fun normalizeHttpsUrl(input: String?): String? {
            val value = input?.trim().orEmpty().trimEnd('/')
            if (!value.startsWith("https://", ignoreCase = true)) return null
            if (value.length <= "https://".length) return null
            if (value.any { it.isWhitespace() } || value.contains('?') || value.contains('#')) return null
            return value
        }
    }

    private fun plainPrefs() = context.getSharedPreferences(PLAIN_PREFS_FILE, Context.MODE_PRIVATE)

    // ── Wetter ────────────────────────────────────────────────────
    // Es gibt bewusst keinen vorbelegten Wetterdienst und keinen Standardserver: Die App
    // ruft erst Wetterdaten ab, wenn der Nutzer einen Anbieter gewählt und seinen eigenen
    // Zugang (Schlüssel bzw. Serveradresse) eingetragen hat.

    /** Gewählter Wetteranbieter; ohne Auswahl [WeatherProviderType.NONE]. */
    fun getWeatherProvider(): WeatherProviderType =
        WeatherProviderType.fromId(plainPrefs().getString(KEY_WEATHER_PROVIDER, null))

    fun setWeatherProvider(type: WeatherProviderType) {
        plainPrefs().edit().putString(KEY_WEATHER_PROVIDER, type.id).apply()
    }

    /** Animierter Wetter-Hintergrund in der Wetteransicht (Standard: an). Aus = ruhiges Standbild. */
    fun isWeatherBackgroundAnimated(): Boolean = plainPrefs().getBoolean(KEY_WEATHER_BG_ANIMATED, true)

    fun setWeatherBackgroundAnimated(enabled: Boolean) {
        plainPrefs().edit().putBoolean(KEY_WEATHER_BG_ANIMATED, enabled).apply()
    }

    /** Open-Meteo: Serveradresse (kommerzielle Kundenadresse oder eigener Server) oder null. */
    fun getWeatherBaseUrl(): String? = normalizeHttpsUrl(plainPrefs().getString(KEY_WEATHER_BASE_URL, null))

    /** Open-Meteo: Adresse des Luftqualitäts-Dienstes oder null (dann keine Luftqualität). */
    fun getAirQualityBaseUrl(): String? = normalizeHttpsUrl(plainPrefs().getString(KEY_AIR_QUALITY_BASE_URL, null))

    /** Leer = nicht gesetzt. Ungültige Eingaben werden nicht gespeichert (siehe [normalizeHttpsUrl]). */
    fun setWeatherServers(weatherBaseUrl: String, airQualityBaseUrl: String) {
        plainPrefs().edit().apply {
            putOrRemove(KEY_WEATHER_BASE_URL, normalizeHttpsUrl(weatherBaseUrl))
            putOrRemove(KEY_AIR_QUALITY_BASE_URL, normalizeHttpsUrl(airQualityBaseUrl))
        }.apply()
    }

    // Open-Meteo-Schlüssel
    fun getWeatherApiKey(): String = securePrefs().getString(KEY_WEATHER_API_KEY, null).orEmpty()

    fun saveWeatherApiKey(key: String) {
        securePrefs().edit().putString(KEY_WEATHER_API_KEY, key.trim()).apply()
    }

    fun clearWeatherApiKey() {
        securePrefs().edit().remove(KEY_WEATHER_API_KEY).apply()
    }

    fun hasWeatherApiKey(): Boolean = getWeatherApiKey().isNotBlank()

    // OpenWeatherMap-Schlüssel
    fun getOwmApiKey(): String = securePrefs().getString(KEY_OWM_API_KEY, null).orEmpty()

    fun saveOwmApiKey(key: String) {
        securePrefs().edit().putString(KEY_OWM_API_KEY, key.trim()).apply()
    }

    fun clearOwmApiKey() {
        securePrefs().edit().remove(KEY_OWM_API_KEY).apply()
    }

    fun hasOwmApiKey(): Boolean = getOwmApiKey().isNotBlank()

    // ── Overpass ──────────────────────────────────────────────────

    /** Eigener Overpass-Server (z. B. selbst betrieben) oder null für die Standardserver. */
    fun getCustomOverpassUrl(): String? = normalizeHttpsUrl(plainPrefs().getString(KEY_OVERPASS_URL, null))

    fun setCustomOverpassUrl(url: String) {
        plainPrefs().edit().apply { putOrRemove(KEY_OVERPASS_URL, normalizeHttpsUrl(url)) }.apply()
    }

    private fun android.content.SharedPreferences.Editor.putOrRemove(key: String, value: String?) {
        if (value == null) remove(key) else putString(key, value)
    }

    private fun createSecurePrefs() = EncryptedSharedPreferences.create(
        context,
        ENCRYPTED_PREFS_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Verschlüsselte Einstellungen (API-Schlüssel). Ist die Datei nicht mehr lesbar (z. B. nach
     * einer Wiederherstellung auf einem anderen Gerät, weil der Keystore-Schlüssel fehlt), wird sie
     * zurückgesetzt; die Schlüssel müssen dann neu eingetragen werden. Nur wenn auch das scheitert,
     * wird eine getrennte, unverschlüsselte Datei verwendet, damit die verschlüsselte nicht
     * mit Klartext vermischt wird.
     */
    private fun securePrefs() = try {
        createSecurePrefs()
    } catch (e: Exception) {
        try {
            context.deleteSharedPreferences(ENCRYPTED_PREFS_FILE)
            createSecurePrefs()
        } catch (e2: Exception) {
            context.getSharedPreferences(ENCRYPTED_PREFS_FILE + "_unavailable", Context.MODE_PRIVATE)
        }
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

    fun clearOrsApiKey() {
        securePrefs().edit().remove(KEY_ORS_API_KEY).apply()
    }

    fun hasCustomOrsKey(): Boolean {
        return !securePrefs().getString(KEY_ORS_API_KEY, null).isNullOrBlank()
    }
}
