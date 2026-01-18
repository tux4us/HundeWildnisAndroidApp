package com.hundewildnis.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class WordPressApiParser {
    companion object {
        private const val BASE_URL = "https://wildnislehrer.com/wp-json/wp/v2"
        private const val TIMEOUT = 60000
    }

    /**
     * Lädt Blog-Posts von der WordPress REST API
     * @param page Seitennummer (Standard: 1)
     * @param perPage Anzahl Posts pro Seite (Standard: 20)
     */
    suspend fun loadBlogPosts(page: Int = 1, perPage: Int = 20): List<ContentItem> {
        return try {
            val url = "$BASE_URL/posts?page=$page&per_page=$perPage&_embed"
            android.util.Log.d("WordPressAPI", "Lade Blog-Posts von: $url")
            val json = fetchJson(url)
            android.util.Log.d("WordPressAPI", "Blog-Posts geladen: ${json.length()}")
            parsePostsFromJson(json)
        } catch (e: Exception) {
            android.util.Log.e("WordPressAPI", "Error loading blog posts", e)
            emptyList()
        }
    }

    /**
     * Lädt Seiten von der WordPress REST API
     */
    suspend fun loadPages(): List<ContentItem> {
        return try {
            val url = "$BASE_URL/pages?per_page=100&_embed"
            android.util.Log.d("WordPressAPI", "Lade Seiten von: $url")
            val json = fetchJson(url)
            val allPages = parsePagesFromJson(json)

            // WICHTIG: Filtere Kurs-Seiten heraus
            val filteredPages = allPages.filter { page ->
                !page.url.contains("/onlinekurse-hundeschule") &&
                        !page.url.contains("/onlinekurse-hundeschule/")
            }

            android.util.Log.d("WordPressAPI", "Seiten geladen: ${allPages.size}, nach Filter: ${filteredPages.size}")

            filteredPages
        } catch (e: Exception) {
            android.util.Log.e("WordPressAPI", "Error loading pages", e)
            emptyList()
        }
    }

    /**
     * Sucht nach Inhalten
     */
    suspend fun searchContent(query: String): List<ContentItem> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/search?search=$encodedQuery&per_page=50&_embed"
            android.util.Log.d("WordPressAPI", "Suche nach: $query")
            val json = fetchJson(url)
            parseSearchFromJson(json)
        } catch (e: Exception) {
            android.util.Log.e("WordPressAPI", "Error searching", e)
            emptyList()
        }
    }

    /**
     * Lädt JSON von URL
     */
    private fun fetchJson(urlString: String): JSONArray {
        val connection = URL(urlString).openConnection()
        connection.connectTimeout = TIMEOUT
        connection.readTimeout = TIMEOUT
        connection.setRequestProperty("User-Agent", "HundeWildnis-App/1.0")

        val response = connection.getInputStream().bufferedReader().use { it.readText() }
        return JSONArray(response)
    }

    /**
     * Parst Posts/Courses aus JSON
     */
    private fun parsePostsFromJson(jsonArray: JSONArray): List<ContentItem> {
        val items = mutableListOf<ContentItem>()

        for (i in 0 until jsonArray.length()) {
            try {
                val post = jsonArray.getJSONObject(i)

                val id = post.getInt("id")
                val title = cleanHtml(post.getJSONObject("title").getString("rendered"))
                val excerpt = cleanHtml(post.getJSONObject("excerpt").getString("rendered"))
                val link = post.getString("link")
                val date = post.getString("date")

                // Bild-URL extrahieren
                val imageUrl = extractImageUrl(post)

                // KORRIGIERT: Richtige Parameter-Reihenfolge für ContentItem
                items.add(
                    ContentItem(
                        title = title,
                        url = link,
                        excerpt = excerpt,
                        date = date,
                        level = 0,
                        type = "blog",
                        imageUrl = imageUrl,
                        id = id
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("WordPressAPI", "Error parsing post at index $i", e)
            }
        }

        return items
    }

    /**
     * Parst Seiten aus JSON
     */
    private fun parsePagesFromJson(jsonArray: JSONArray): List<ContentItem> {
        val items = mutableListOf<ContentItem>()
        val baseUrl = "https://wildnislehrer.com"

        for (i in 0 until jsonArray.length()) {
            try {
                val page = jsonArray.getJSONObject(i)

                val id = page.getInt("id")
                val title = cleanHtml(page.getJSONObject("title").getString("rendered"))
                val excerpt = if (page.has("excerpt")) {
                    cleanHtml(page.getJSONObject("excerpt").getString("rendered"))
                } else {
                    ""
                }
                val link = page.getString("link")
                val imageUrl = extractImageUrl(page)

                // Berechne Level basierend auf URL-Struktur
                val level = calculateLevel(link, baseUrl)

                items.add(
                    ContentItem(
                        title = title,
                        url = link,
                        excerpt = excerpt,
                        date = "",
                        level = level,
                        type = "page",
                        imageUrl = imageUrl,
                        id = id
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("WordPressAPI", "Error parsing page at index $i", e)
            }
        }

        // Sortiere wie im alten Parser
        return items.sortedWith(
            compareBy<ContentItem>(
                {
                    // Primäre Sortierung: Kategorie-Gruppe
                    when {
                        it.url.trimEnd('/') == baseUrl.trimEnd('/') -> 0  // Startseite zuerst
                        it.url.contains("/hundeschule/") -> 1              // Dann Hundeschule-Bereich
                        it.url.contains("/wildnisschule/") -> 2            // Dann Wildnisschule-Bereich
                        else -> 3                                          // Dann Rest
                    }
                },
                { it.level }           // Sekundär: Level (innerhalb der Kategorie)
            )
        )
    }

    /**
     * Parst Suchergebnisse aus JSON
     */
    private fun parseSearchFromJson(jsonArray: JSONArray): List<ContentItem> {
        val items = mutableListOf<ContentItem>()

        for (i in 0 until jsonArray.length()) {
            try {
                val result = jsonArray.getJSONObject(i)

                val id = result.getInt("id")
                val title = cleanHtml(result.getString("title"))
                val url = result.getString("url")
                val subtype = result.optString("subtype", "post")

                items.add(
                    ContentItem(
                        title = title,
                        url = url,
                        excerpt = "Typ: $subtype",
                        date = "",
                        level = 0,
                        type = subtype,
                        imageUrl = "",
                        id = id
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("WordPressAPI", "Error parsing search result at index $i", e)
            }
        }

        return items
    }

    /**
     * Berechnet das Level einer Seite basierend auf der URL-Struktur
     */
    private fun calculateLevel(url: String, baseUrl: String): Int {
        val normalizedUrl = url.trimEnd('/')
        val normalizedBase = baseUrl.trimEnd('/')

        return when {
            // Level 0: Nur die Hauptseite
            normalizedUrl == normalizedBase -> 0

            // Level 1: Hundeschule Hauptseite
            normalizedUrl == "$baseUrl/hundeschule" -> 1

            // Level 1: Wildnisschule Hauptseite
            normalizedUrl == "$baseUrl/wildnisschule" -> 1

            // Level 2: Direkte Unterseiten von Hundeschule
            url.startsWith("$baseUrl/hundeschule/") &&
                    url.removePrefix("$baseUrl/hundeschule/").trimEnd('/').count { it == '/' } == 0 -> 2

            // Level 2: Direkte Unterseiten von Wildnisschule
            url.startsWith("$baseUrl/wildnisschule/") &&
                    url.removePrefix("$baseUrl/wildnisschule/").trimEnd('/').count { it == '/' } == 0 -> 2

            // Level 3+: Tiefere Verschachtelung unter Hundeschule
            url.startsWith("$baseUrl/hundeschule/") -> {
                2 + url.removePrefix("$baseUrl/hundeschule/").trimEnd('/').count { it == '/' }
            }

            // Level 3+: Tiefere Verschachtelung unter Wildnisschule
            url.startsWith("$baseUrl/wildnisschule/") -> {
                2 + url.removePrefix("$baseUrl/wildnisschule/").trimEnd('/').count { it == '/' }
            }

            // Level 1: ALLE anderen Seiten
            else -> 1
        }
    }

    /**
     * Extrahiert Bild-URL aus Post/Page JSON
     */
    private fun extractImageUrl(json: JSONObject): String {
        return try {
            // Versuche Featured Media (_embedded)
            if (json.has("_embedded")) {
                val embedded = json.getJSONObject("_embedded")
                if (embedded.has("wp:featuredmedia")) {
                    val media = embedded.getJSONArray("wp:featuredmedia")
                    if (media.length() > 0) {
                        val mediaObj = media.getJSONObject(0)
                        if (mediaObj.has("source_url")) {
                            return mediaObj.getString("source_url")
                        }
                    }
                }
            }

            ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Entfernt HTML-Tags und bereinigt Text
     */
    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "") // Entferne HTML-Tags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#8211;", "–")
            .replace("&#8212;", "—")
            .replace("&#8230;", "…")
            .replace(Regex("\\s+"), " ") // Mehrfache Leerzeichen
            .trim()
    }
}