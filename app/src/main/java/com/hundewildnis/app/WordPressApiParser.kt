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

    // ⚡ VERBESSERT: Hierarchische Sortierung für Seiten
    suspend fun loadPages(): List<ContentItem> {
        return try {
            val url = "$BASE_URL/pages?per_page=100&_embed&orderby=menu_order&order=asc"
            android.util.Log.d("WordPressAPI", "Lade Seiten von: $url")
            val json = fetchJson(url)
            val allPages = parsePagesFromJson(json)

            // Filtere Kurs-Seiten heraus
            val filteredPages = allPages.filter { page ->
                !page.url.contains("/onlinekurse-hundeschule") &&
                        !page.url.contains("/onlinekurse-hundeschule/")
            }

            android.util.Log.d("WordPressAPI", "Seiten geladen: ${allPages.size}, nach Filter: ${filteredPages.size}")

            // ⚡ NEU: Hierarchische Sortierung (Parent → Children rekursiv)
            sortHierarchically(filteredPages)

        } catch (e: Exception) {
            android.util.Log.e("WordPressAPI", "Error loading pages", e)
            emptyList()
        }
    }

    // ⚡ VERBESSERT: Hierarchische Sortierung für Kurse
    suspend fun loadCourses(): List<ContentItem> {
        return try {
            val url = "$BASE_URL/pages?per_page=100&_embed"
            android.util.Log.d("WordPressAPI", "Lade Kurse via API...")

            val json = fetchJson(url)
            val allPages = mutableListOf<ContentItem>()

            for (i in 0 until json.length()) {
                val page = json.getJSONObject(i)
                val link = page.getString("link")

                if (link.contains("/onlinekurse-hundeschule")) {
                    val id = page.getInt("id")
                    val title = cleanHtml(page.getJSONObject("title").getString("rendered"))
                    val parent = page.getInt("parent")
                    val menuOrder = page.optInt("menu_order", 0)

                    allPages.add(
                        ContentItem(
                            title = title,
                            url = link,
                            excerpt = "",
                            date = "",
                            level = 0,
                            type = "course",
                            imageUrl = extractImageUrl(page),
                            id = id,
                            parentId = parent,
                            menuOrder = menuOrder
                        )
                    )
                }
            }

            val coursesWithLevel = calculateLevels(allPages)

            android.util.Log.d("WordPressAPI", "✓ Kurse via API: ${coursesWithLevel.size}")

            // ⚡ NEU: Hierarchische Sortierung
            sortHierarchically(coursesWithLevel)

        } catch (e: Exception) {
            android.util.Log.e("WordPressAPI", "Error loading courses via API", e)
            emptyList()
        }
    }

    // ⚡ UNIVERSELL: Hierarchische Sortierung für Seiten UND Kurse
    private fun sortHierarchically(items: List<ContentItem>): List<ContentItem> {
        val result = mutableListOf<ContentItem>()
        val itemMap = items.associateBy { it.id }
        val processed = mutableSetOf<Int>()

        // Rekursive Funktion: Fügt Item + alle Children hinzu
        fun addItemWithChildren(item: ContentItem) {
            if (item.id in processed) return

            result.add(item)
            processed.add(item.id)

            // Finde alle direkten Children dieses Items
            val children = items
                .filter { it.parentId == item.id }
                .sortedBy { it.menuOrder }

            // Füge Children rekursiv hinzu
            children.forEach { child ->
                addItemWithChildren(child)
            }
        }

        // Finde Root-Items (parentId = 0 ODER parentId zeigt auf nicht-existierenden Parent)
        val rootItems = items.filter { item ->
            item.parentId == 0 || item.parentId !in itemMap.keys
        }.sortedBy { it.menuOrder }

        // Starte rekursive Sortierung mit jedem Root
        rootItems.forEach { root ->
            addItemWithChildren(root)
        }

        // Falls Items übrig bleiben (Waisen), füge sie am Ende hinzu
        val orphans = items.filter { it.id !in processed }.sortedBy { it.menuOrder }
        orphans.forEach { orphan ->
            addItemWithChildren(orphan)
        }

        android.util.Log.d("WordPressAPI", "📚 Hierarchische Sortierung: ${result.size} Items (${items.firstOrNull()?.type ?: "unknown"})")

        return result
    }

    // Berechnet Level basierend auf Parent-Hierarchie
    private fun calculateLevels(items: List<ContentItem>): List<ContentItem> {
        val itemMap = items.associateBy { it.id }

        return items.map { item ->
            var level = 0
            var currentParent = item.parentId

            // Zähle wie viele Parents es gibt
            while (currentParent != 0 && currentParent in itemMap.keys) {
                level++
                currentParent = itemMap[currentParent]?.parentId ?: 0
                if (level > 10) break // Sicherheit
            }

            item.copy(level = level)
        }
    }

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

    private fun fetchJson(urlString: String): JSONArray {
        val connection = URL(urlString).openConnection()
        connection.connectTimeout = TIMEOUT
        connection.readTimeout = TIMEOUT
        connection.setRequestProperty("User-Agent", "HundeWildnis-App/1.0")

        val response = connection.getInputStream().bufferedReader().use { it.readText() }
        return JSONArray(response)
    }

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
                val imageUrl = extractImageUrl(post)

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

    private fun parsePagesFromJson(jsonArray: JSONArray): List<ContentItem> {
        val items = mutableListOf<ContentItem>()
        val baseUrl = "https://wildnislehrer.com"

        for (i in 0 until jsonArray.length()) {
            try {
                val page = jsonArray.getJSONObject(i)

                val id = page.getInt("id")
                val parent = page.getInt("parent")
                val title = cleanHtml(page.getJSONObject("title").getString("rendered"))
                val excerpt = if (page.has("excerpt")) {
                    cleanHtml(page.getJSONObject("excerpt").getString("rendered"))
                } else {
                    ""
                }
                val link = page.getString("link")
                val imageUrl = extractImageUrl(page)
                val menuOrder = page.optInt("menu_order", 0)

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
                        id = id,
                        parentId = parent,  // WICHTIG: parentId für Hierarchie
                        menuOrder = menuOrder
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("WordPressAPI", "Error parsing page at index $i", e)
            }
        }

        return items
    }

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

    private fun calculateLevel(url: String, baseUrl: String): Int {
        val normalizedUrl = url.trimEnd('/')
        val normalizedBase = baseUrl.trimEnd('/')

        return when {
            normalizedUrl == normalizedBase -> 0
            normalizedUrl == "$baseUrl/hundeschule" -> 1
            normalizedUrl == "$baseUrl/wildnisschule" -> 1
            url.startsWith("$baseUrl/hundeschule/") &&
                    url.removePrefix("$baseUrl/hundeschule/").trimEnd('/').count { it == '/' } == 0 -> 2
            url.startsWith("$baseUrl/wildnisschule/") &&
                    url.removePrefix("$baseUrl/wildnisschule/").trimEnd('/').count { it == '/' } == 0 -> 2
            url.startsWith("$baseUrl/hundeschule/") -> {
                2 + url.removePrefix("$baseUrl/hundeschule/").trimEnd('/').count { it == '/' }
            }
            url.startsWith("$baseUrl/wildnisschule/") -> {
                2 + url.removePrefix("$baseUrl/wildnisschule/").trimEnd('/').count { it == '/' }
            }
            else -> 1
        }
    }

    private fun extractImageUrl(json: JSONObject): String {
        return try {
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

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#8211;", "–")
            .replace("&#8212;", "—")
            .replace("&#8230;", "…")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}