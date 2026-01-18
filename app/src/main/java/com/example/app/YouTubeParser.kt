package com.hundewildnis.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class YouTubeParser {
    companion object {
        private const val API_KEY = "AIzaSyAfFLsnejR05F-KgEXrsqM8ZJzLjKqrrVM"
        private const val CHANNEL_ID = "UCmFBPW59wHASmFE99ay8qaA"
        private const val TIMEOUT = 10000
    }

    /**
     * Lädt die neuesten Videos vom YouTube-Kanal
     */
    suspend fun loadLatestVideos(maxResults: Int = 50): List<YouTubeVideo> {
        return try {
            val urlString = "https://www.googleapis.com/youtube/v3/search?" +
                    "key=$API_KEY" +
                    "&channelId=$CHANNEL_ID" +
                    "&part=snippet" +
                    "&order=date" +
                    "&maxResults=$maxResults" +
                    "&type=video"

            android.util.Log.d("YouTubeParser", "Lade Videos von: $urlString")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT

            val responseCode = connection.responseCode
            android.util.Log.d("YouTubeParser", "Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                android.util.Log.d("YouTubeParser", "Response erhalten (${response.length} Zeichen)")
                parseVideos(response)
            } else {
                // Lese Fehler-Response
                val errorStream = connection.errorStream
                val errorResponse = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else {
                    "No error details available"
                }

                android.util.Log.e("YouTubeParser", "HTTP Error $responseCode")
                android.util.Log.e("YouTubeParser", "Error Response: $errorResponse")

                // Parse error details if JSON
                try {
                    val errorJson = JSONObject(errorResponse)
                    if (errorJson.has("error")) {
                        val error = errorJson.getJSONObject("error")
                        val message = error.optString("message", "Unknown error")
                        val code = error.optInt("code", responseCode)
                        val status = error.optString("status", "UNKNOWN")

                        android.util.Log.e("YouTubeParser", "API Error [$code - $status]: $message")

                        val errors = error.optJSONArray("errors")
                        if (errors != null && errors.length() > 0) {
                            for (i in 0 until errors.length()) {
                                val err = errors.getJSONObject(i)
                                android.util.Log.e("YouTubeParser", "  - ${err.optString("reason")}: ${err.optString("message")}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("YouTubeParser", "Could not parse error JSON")
                }

                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeParser", "Exception beim Laden der Videos", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Parst JSON-Response von YouTube API
     */
    private fun parseVideos(jsonString: String): List<YouTubeVideo> {
        val videos = mutableListOf<YouTubeVideo>()

        try {
            val json = JSONObject(jsonString)

            // Prüfe auf Fehler
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                android.util.Log.e("YouTubeParser", "API Error in Response: ${error.getString("message")}")
                return emptyList()
            }

            val items = json.getJSONArray("items")
            android.util.Log.d("YouTubeParser", "✅ Gefundene Videos: ${items.length()}")

            for (i in 0 until items.length()) {
                try {
                    val item = items.getJSONObject(i)
                    val id = item.getJSONObject("id").getString("videoId")
                    val snippet = item.getJSONObject("snippet")

                    val title = snippet.getString("title")
                    val description = snippet.getString("description")
                    val publishedAt = snippet.getString("publishedAt")

                    // Thumbnail URL
                    val thumbnails = snippet.getJSONObject("thumbnails")
                    val thumbnailUrl = when {
                        thumbnails.has("high") -> thumbnails.getJSONObject("high").getString("url")
                        thumbnails.has("medium") -> thumbnails.getJSONObject("medium").getString("url")
                        else -> thumbnails.getJSONObject("default").getString("url")
                    }

                    videos.add(
                        YouTubeVideo(
                            videoId = id,
                            title = title,
                            description = description,
                            thumbnailUrl = thumbnailUrl,
                            publishedAt = publishedAt
                        )
                    )

                    android.util.Log.d("YouTubeParser", "  ✓ Video #${i+1}: $title")
                } catch (e: Exception) {
                    android.util.Log.e("YouTubeParser", "Fehler beim Parsen von Video #$i", e)
                }
            }

            android.util.Log.d("YouTubeParser", "✅ Erfolgreich ${videos.size} Videos geparst")
        } catch (e: Exception) {
            android.util.Log.e("YouTubeParser", "Fehler beim JSON-Parsing", e)
            e.printStackTrace()
        }

        return videos
    }
}
