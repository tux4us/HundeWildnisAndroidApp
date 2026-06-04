package com.hundewildnis.app

import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URL

object OverpassHelper {

    private val OVERPASS_SERVERS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.openstreetmap.ru/api/interpreter"
    )

    suspend fun queryWithRetry(
        query: String,
        maxRetries: Int = 3
    ): JSONObject? {
        for (serverIndex in OVERPASS_SERVERS.indices) {
            val server = OVERPASS_SERVERS[serverIndex]

            for (attempt in 1..maxRetries) {
                try {
                    Log.d("OverpassHelper", "Versuch $attempt/$maxRetries auf Server ${serverIndex + 1}")

                    val connection = URL(server).openConnection()
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("User-Agent", "HundeWildnis-App/1.0")
                    connection.doOutput = true

                    connection.getOutputStream().use {
                        it.write(query.toByteArray())
                    }

                    val response = connection.getInputStream().bufferedReader().use {
                        it.readText()
                    }

                    Log.d("OverpassHelper", "✓ Erfolgreich auf Server ${serverIndex + 1}")
                    return JSONObject(response)

                } catch (e: Exception) {
                    Log.e("OverpassHelper", "Versuch $attempt fehlgeschlagen: ${e.message}")

                    if (attempt < maxRetries) {
                        delay(2000L * attempt) // Exponentielles Backoff
                    }
                }
            }
        }

        Log.e("OverpassHelper", "Alle Server und Versuche fehlgeschlagen")
        return null
    }
}