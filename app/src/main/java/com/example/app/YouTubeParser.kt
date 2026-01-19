package com.hundewildnis.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class YouTubeParser {
    companion object {
        private val API_KEY = BuildConfig.YOUTUBE_API_KEY
        private const val CHANNEL_ID = "UCmFBPW59wHASmFE99ay8qaA"
        private const val TIMEOUT = 30000  // 30 Sekunden (war 10000)
    }

    /**
     * Lädt die neuesten Videos vom YouTube-Kanal
     */
    suspend fun loadLatestVideos(maxResults: Int = 100): List<YouTubeVideo> {
        var connection: HttpURLConnection? = null
        return try {
            if (API_KEY.isEmpty()) {
                android.util.Log.e("YouTubeParser", "⚠️ API Key ist leer! Bitte in local.properties eintragen.")
                return emptyList()
            }

            val url = "https://www.googleapis.com/youtube/v3/search?" +
                    "key=$API_KEY" +
                    "&channelId=$CHANNEL_ID" +
                    "&part=snippet" +
                    "&order=date" +
                    "&maxResults=$maxResults" +
                    "&type=video"

            android.util.Log.d("YouTubeParser", "Lade Videos von YouTube API...")

            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                android.util.Log.d("YouTubeParser", "✓ ${response.length} Zeichen empfangen")
                parseVideos(response)
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Keine Fehlermeldung"
                android.util.Log.e("YouTubeParser", "❌ API Fehler $responseCode: $errorResponse")
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeParser", "❌ Fehler beim Laden der Videos", e)
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parst JSON-Response von YouTube API
     */
    private fun parseVideos(jsonString: String): List<YouTubeVideo> {
        val videos = mutableListOf<YouTubeVideo>()
        try {
            val json = JSONObject(jsonString)
            val items = json.getJSONArray("items")

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id = item.getJSONObject("id").getString("videoId")
                val snippet = item.getJSONObject("snippet")
                val title = snippet.getString("title")
                val description = snippet.getString("description")
                val publishedAt = snippet.optString("publishedAt", "")  // optString für Sicherheit

                val thumbnails = snippet.getJSONObject("thumbnails")
                val thumbnailUrl = when {
                    thumbnails.has("high") -> thumbnails.getJSONObject("high").getString("url")
                    thumbnails.has("medium") -> thumbnails.getJSONObject("medium").getString("url")
                    thumbnails.has("default") -> thumbnails.getJSONObject("default").getString("url")
                    else -> ""
                }

                videos.add(YouTubeVideo(id, title, description, thumbnailUrl, publishedAt))
            }

            android.util.Log.d("YouTubeParser", "✓ ${videos.size} Videos geparst")
        } catch (e: Exception) {
            android.util.Log.e("YouTubeParser", "❌ Fehler beim Parsen der Videos", e)
        }
        return videos
    }
}
