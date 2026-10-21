package com.hundewildnis.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Overpass-Abfragen mit Ausweichserver.
 *
 * Die öffentlichen Overpass-Server werden von allen Nutzern weltweit geteilt und begrenzen die
 * Last (Fair-Use-Richtlinie). Deshalb:
 *  • eindeutige Kennung (User-Agent) mit Kontakt,
 *  • bei „zu viele Anfragen“ (429) oder „Server überlastet“ (504) wird der Server gewechselt
 *    statt sofort erneut angefragt,
 *  • bei anderen Fehlern wird mit wachsendem Abstand wiederholt,
 *  • Nutzerinnen und Nutzer können in den Einstellungen einen eigenen Server eintragen
 *    (z. B. eine selbst betriebene Instanz); dieser wird dann ausschließlich verwendet.
 */
object OverpassHelper {

    private const val TAG = "OverpassHelper"

    /** Öffentliche Standardserver in Reihenfolge der Verwendung. */
    val DEFAULT_SERVERS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    private class RetryableServerBusy(message: String) : Exception(message)

    /** Server-Liste: eigener Server (falls eingetragen), sonst die Standardserver. */
    fun servers(context: Context): List<String> {
        val custom = AppSettings(context.applicationContext).getCustomOverpassUrl()
        return if (custom != null) listOf(custom) else DEFAULT_SERVERS
    }

    /**
     * Adresse des bevorzugten Servers (z. B. für Code, der selbst eine Verbindung aufbaut).
     * Der Pfad „/api/interpreter“ wird bei einer eigenen Adresse ohne Pfad ergänzt.
     */
    fun primaryServer(context: Context): String = servers(context).first()

    suspend fun queryWithRetry(
        context: Context,
        query: String,
        maxRetries: Int = 3
    ): JSONObject? {
        val serverList = servers(context).map { withInterpreterPath(it) }
        for (serverIndex in serverList.indices) {
            val server = serverList[serverIndex]

            for (attempt in 1..maxRetries) {
                try {
                    Log.d(TAG, "Versuch $attempt/$maxRetries auf Server ${serverIndex + 1}")

                    val connection = URL(server).openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 25000
                    AppNetwork.applyUserAgent(connection)
                    connection.doOutput = true

                    connection.outputStream.use {
                        it.write(query.toByteArray())
                    }

                    val code = connection.responseCode
                    if (code == 429 || code == 504) {
                        connection.disconnect()
                        throw RetryableServerBusy("HTTP $code")
                    }
                    if (code !in 200..299) {
                        connection.disconnect()
                        throw java.io.IOException("HTTP $code")
                    }

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()

                    Log.d(TAG, "Erfolgreich auf Server ${serverIndex + 1}")
                    return JSONObject(response)

                } catch (e: RetryableServerBusy) {
                    Log.w(TAG, "Server ${serverIndex + 1} ausgelastet (${e.message}), wechsle den Server")
                    break // nächster Server, keine sofortige Wiederholung
                } catch (e: Exception) {
                    Log.e(TAG, "Versuch $attempt fehlgeschlagen: ${e.message}")

                    if (attempt < maxRetries) {
                        delay(2000L * attempt) // wachsender Abstand
                    }
                }
            }
        }

        Log.e(TAG, "Alle Server und Versuche fehlgeschlagen")
        return null
    }

    /** Ergänzt „/api/interpreter“, wenn der Nutzer nur den Servernamen eingetragen hat. */
    fun withInterpreterPath(url: String): String {
        val withoutScheme = url.substringAfter("://")
        return if (withoutScheme.contains('/')) url else "$url/api/interpreter"
    }

    /**
     * Maskiert Nutzereingaben für die Verwendung in einem Overpass-Regex-String
     * (Anführungszeichen und Backslash werden ersetzt, Regex-Sonderzeichen maskiert), damit Eingaben wie
     * `Bäcker"` oder `(` die Abfrage nicht verändern oder abbrechen lassen.
     */
    fun escapeForRegexLiteral(input: String): String {
        val sb = StringBuilder()
        for (ch in input.trim()) {
            when (ch) {
                '\\', '"' -> sb.append(' ') // würden die Zeichenkette beenden bzw. verfälschen
                '.', '^', '$', '*', '+', '?', '(', ')', '[', ']', '{', '}', '|' -> sb.append("\\\\").append(ch)
                '\n', '\r', '\t' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
