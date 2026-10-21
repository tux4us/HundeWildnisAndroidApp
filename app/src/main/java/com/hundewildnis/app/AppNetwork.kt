package com.hundewildnis.app

import java.net.HttpURLConnection

/**
 * Gemeinsame Netzwerk-Einstellungen aller Abrufe.
 */
object AppNetwork {

    /**
     * Kennung gegenüber externen Diensten. Die Nutzungsrichtlinien von OpenStreetMap
     * (Nominatim, Overpass) verlangen eine eindeutige Kennung mit Kontaktmöglichkeit.
     */
    val USER_AGENT: String
        get() = "HundeWildnisApp/${BuildConfig.VERSION_NAME} (Android; +https://wildnislehrer.com)"

    /** Setzt die einheitliche Kennung auf eine Verbindung. */
    fun applyUserAgent(connection: java.net.URLConnection) {
        connection.setRequestProperty("User-Agent", USER_AGENT)
    }

    private val nominatimLock = Any()
    private var lastNominatimCall = 0L

    /**
     * Die Nominatim-Richtlinie erlaubt höchstens eine Anfrage pro Sekunde. Wird vor jedem
     * Nominatim-Aufruf (in einem Hintergrund-Thread) aufgerufen und wartet bei Bedarf.
     */
    fun waitForNominatimSlot() {
        synchronized(nominatimLock) {
            val wait = lastNominatimCall + 1100L - System.currentTimeMillis()
            if (wait > 0) {
                try { Thread.sleep(wait) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
            lastNominatimCall = System.currentTimeMillis()
        }
    }

    fun openGet(url: String, connectTimeoutMs: Int = 10_000, readTimeoutMs: Int = 15_000): HttpURLConnection {
        val conn = java.net.URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        applyUserAgent(conn)
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        return conn
    }
}
