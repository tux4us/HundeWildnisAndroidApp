package com.hundewildnis.app

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder

/** Wählbare Wetteranbieter. Es gibt keinen vorbelegten Anbieter. */
enum class WeatherProviderType(val id: String) {
    NONE("none"),
    OPEN_WEATHER_MAP("owm"),
    OPEN_METEO("open_meteo");

    companion object {
        fun fromId(id: String?): WeatherProviderType = values().firstOrNull { it.id == id } ?: NONE
    }
}

/** Welche Daten der gewählte Anbieter (mit der aktuellen Konfiguration) liefern kann. */
data class WeatherCapabilities(
    val uvIndex: Boolean,
    val sunshineDuration: Boolean,
    val cape: Boolean,
    val pollen: Boolean,
    val airQuality: Boolean,
    val elevation: Boolean
)

/** Fehler beim Wetterabruf mit Ursache, damit die Oberfläche verständlich antworten kann. */
class WeatherProviderException(val kind: Kind, message: String? = null) : Exception(message) {
    enum class Kind {
        /** Kein Anbieter gewählt, Schlüssel oder Serveradresse fehlt. */
        NOT_CONFIGURED,
        /** Der Anbieter kann diese Daten nicht liefern. */
        UNSUPPORTED,
        /** Schlüssel abgelehnt (neue OpenWeatherMap-Schlüssel brauchen einige Zeit bis zur Freischaltung). */
        UNAUTHORIZED,
        /** Zu viele Anfragen. */
        RATE_LIMIT,
        /** Sonstiger HTTP-Fehler. */
        HTTP
    }
}

/**
 * Zentrale Stelle für alle Wetterabrufe. Die Bildschirme fragen hier Daten in der
 * Open-Meteo-Form an; je nach gewähltem Anbieter geht die Anfrage an
 *  - OpenWeatherMap (Nutzerschlüssel, kostenloser Tarif; Antworten werden umgewandelt) oder
 *  - Open-Meteo (vom Nutzer eingetragene Serveradresse und ggf. Schlüssel; kein Standardserver).
 * Ohne vollständige Konfiguration wird nichts abgerufen ([WeatherProviderException.Kind.NOT_CONFIGURED]).
 */
object WeatherProvider {

    private const val OWM_BASE = "https://api.openweathermap.org"
    private const val CACHE_MS = 10 * 60 * 1000L

    private class CacheEntry(val time: Long, val value: JSONObject)
    private val cache = HashMap<String, CacheEntry>()

    fun type(context: Context): WeatherProviderType = AppSettings(context.applicationContext).getWeatherProvider()

    /** Ist ein Anbieter gewählt und sein Zugang (Schlüssel bzw. Adresse) eingetragen? */
    fun isConfigured(context: Context): Boolean {
        val s = AppSettings(context.applicationContext)
        return when (s.getWeatherProvider()) {
            WeatherProviderType.NONE -> false
            WeatherProviderType.OPEN_WEATHER_MAP -> s.hasOwmApiKey()
            WeatherProviderType.OPEN_METEO -> s.getWeatherBaseUrl() != null
        }
    }

    fun capabilities(context: Context): WeatherCapabilities {
        val s = AppSettings(context.applicationContext)
        return when (s.getWeatherProvider()) {
            WeatherProviderType.OPEN_METEO -> WeatherCapabilities(
                uvIndex = true, sunshineDuration = true, cape = true,
                pollen = s.getAirQualityBaseUrl() != null,
                airQuality = s.getAirQualityBaseUrl() != null,
                elevation = s.getWeatherBaseUrl() != null
            )
            WeatherProviderType.OPEN_WEATHER_MAP -> WeatherCapabilities(
                uvIndex = false, sunshineDuration = false, cape = false,
                pollen = false, airQuality = true, elevation = false
            )
            WeatherProviderType.NONE -> WeatherCapabilities(false, false, false, false, false, false)
        }
    }

    /** Pflicht-Quellenangabe des gewählten Anbieters (leer, wenn keiner gewählt ist). */
    fun attribution(context: Context): String = when (type(context)) {
        WeatherProviderType.OPEN_WEATHER_MAP -> context.getString(R.string.weather_attribution_owm)
        WeatherProviderType.OPEN_METEO -> context.getString(R.string.weather_attribution_open_meteo)
        WeatherProviderType.NONE -> ""
    }

    /** Verständlicher Text zu einem Fehler beim Wetterabruf. */
    fun describeError(context: Context, e: Throwable): String {
        val ex = e as? WeatherProviderException
            ?: return context.getString(R.string.start_error_generic, e.message ?: "")
        return when (ex.kind) {
            WeatherProviderException.Kind.NOT_CONFIGURED -> context.getString(R.string.weather_setup_hint)
            WeatherProviderException.Kind.UNSUPPORTED -> context.getString(R.string.weather_not_supported_by_provider)
            WeatherProviderException.Kind.UNAUTHORIZED -> context.getString(R.string.weather_key_rejected)
            WeatherProviderException.Kind.RATE_LIMIT -> context.getString(R.string.weather_rate_limited)
            WeatherProviderException.Kind.HTTP -> context.getString(R.string.start_error_generic, ex.message ?: "")
        }
    }

    // ── Abrufe (blockierend, nur aus einem Hintergrund-Thread aufrufen) ──────────

    /**
     * Wettervorhersage in Open-Meteo-Form.
     * @param openMeteoParams Abfrageparameter für Open-Meteo, beginnend mit „&“ (z. B.
     * "&current=temperature_2m&timezone=auto"). OpenWeatherMap liefert immer alles, was der
     * kostenlose Tarif hergibt, und ignoriert sie.
     */
    fun fetchForecast(context: Context, lat: Double, lon: Double, openMeteoParams: String): JSONObject {
        val s = AppSettings(context.applicationContext)
        val withYesterday = openMeteoParams.contains("past_days=")
        return when (s.getWeatherProvider()) {
            WeatherProviderType.NONE -> throw WeatherProviderException(WeatherProviderException.Kind.NOT_CONFIGURED)
            WeatherProviderType.OPEN_WEATHER_MAP -> cached("owm:f:${key(lat, lon)}:$withYesterday") {
                val k = requireOwmKey(s)
                val q = "lat=$lat&lon=$lon&units=metric&appid=${enc(k)}"
                val current = JSONObject(httpGet("$OWM_BASE/data/2.5/weather?$q"))
                val forecast = JSONObject(httpGet("$OWM_BASE/data/2.5/forecast?$q"))
                OpenWeatherMapAdapter.buildForecast(current, forecast, withYesterday)
            }
            WeatherProviderType.OPEN_METEO -> {
                val base = s.getWeatherBaseUrl()
                    ?: throw WeatherProviderException(WeatherProviderException.Kind.NOT_CONFIGURED)
                cached("om:f:${key(lat, lon)}:${openMeteoParams.hashCode()}") {
                    JSONObject(httpGet("$base/v1/forecast?${omKey(s)}latitude=$lat&longitude=$lon$openMeteoParams"))
                }
            }
        }
    }

    /** Luftqualität (und bei Open-Meteo Pollen) in Open-Meteo-Form. */
    fun fetchAirQuality(context: Context, lat: Double, lon: Double, openMeteoParams: String): JSONObject {
        val s = AppSettings(context.applicationContext)
        return when (s.getWeatherProvider()) {
            WeatherProviderType.NONE -> throw WeatherProviderException(WeatherProviderException.Kind.NOT_CONFIGURED)
            WeatherProviderType.OPEN_WEATHER_MAP -> cached("owm:a:${key(lat, lon)}") {
                val k = requireOwmKey(s)
                val q = "lat=$lat&lon=$lon&appid=${enc(k)}"
                val air = JSONObject(httpGet("$OWM_BASE/data/2.5/air_pollution/forecast?$q"))
                val offset = owmUtcOffset(k, lat, lon)
                OpenWeatherMapAdapter.buildAirQuality(air, offset)
            }
            WeatherProviderType.OPEN_METEO -> {
                val base = s.getAirQualityBaseUrl()
                    ?: throw WeatherProviderException(WeatherProviderException.Kind.UNSUPPORTED)
                cached("om:a:${key(lat, lon)}:${openMeteoParams.hashCode()}") {
                    JSONObject(httpGet("$base/v1/air-quality?${omKey(s)}latitude=$lat&longitude=$lon$openMeteoParams"))
                }
            }
        }
    }

    /**
     * Höhendaten (nur Open-Meteo mit eingetragener Adresse). Parameter sind kommagetrennte
     * Listen von Breiten- und Längengraden. Liefert null, wenn der Anbieter das nicht kann.
     */
    fun fetchElevation(context: Context, latitudes: String, longitudes: String): JSONObject? {
        val s = AppSettings(context.applicationContext)
        if (s.getWeatherProvider() != WeatherProviderType.OPEN_METEO) return null
        val base = s.getWeatherBaseUrl() ?: return null
        return JSONObject(httpGet("$base/v1/elevation?${omKey(s)}latitude=$latitudes&longitude=$longitudes"))
    }

    // ── Hilfsfunktionen ──────────────────────────────────────────────────────────

    /** Zeitzonenversatz des Orts (Sekunden); aus dem Zwischenspeicher oder einem kleinen Abruf. */
    private fun owmUtcOffset(apiKey: String, lat: Double, lon: Double): Int {
        val cached = synchronized(cache) {
            (cache["owm:f:${key(lat, lon)}:true"] ?: cache["owm:f:${key(lat, lon)}:false"])
                ?.value?.optInt("utc_offset_seconds", 0)
        }
        if (cached != null) return cached
        val current = cached("owm:tz:${key(lat, lon)}") {
            JSONObject(httpGet("$OWM_BASE/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=${enc(apiKey)}"))
        }
        return current.optInt("timezone", 0)
    }

    private fun requireOwmKey(s: AppSettings): String {
        val k = s.getOwmApiKey()
        if (k.isBlank()) throw WeatherProviderException(WeatherProviderException.Kind.NOT_CONFIGURED)
        return k
    }

    private fun omKey(s: AppSettings): String {
        val key = s.getWeatherApiKey()
        return if (key.isBlank()) "" else "apikey=${enc(key)}&"
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    private fun key(lat: Double, lon: Double) = String.format(java.util.Locale.US, "%.3f,%.3f", lat, lon)

    private fun cached(key: String, load: () -> JSONObject): JSONObject {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[key]?.let { if (now - it.time < CACHE_MS) return JSONObject(it.value.toString()) }
        }
        val value = load()
        synchronized(cache) {
            if (cache.size > 40) cache.clear()
            cache[key] = CacheEntry(now, value)
        }
        return JSONObject(value.toString())   // Kopie: Aufrufer dürfen verändern
    }

    private fun httpGet(url: String): String {
        val conn = AppNetwork.openGet(url, 12_000, 20_000)
        try {
            val code = conn.responseCode
            when {
                code == 401 || code == 403 -> throw WeatherProviderException(WeatherProviderException.Kind.UNAUTHORIZED, "HTTP $code")
                code == 429 -> throw WeatherProviderException(WeatherProviderException.Kind.RATE_LIMIT, "HTTP 429")
                code !in 200..299 -> throw WeatherProviderException(WeatherProviderException.Kind.HTTP, "HTTP $code")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
