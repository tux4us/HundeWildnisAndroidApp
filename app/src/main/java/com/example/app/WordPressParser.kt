package com.hundewildnis.app

import org.jsoup.Jsoup

data class ContentItem(
    val title: String,
    val url: String,
    val excerpt: String = "",
    val date: String = "",
    val level: Int = 0,
    val type: String = "page"  // NEU: "blog", "page", "course"
)

class WordPressParser {
    private val baseUrl = "https://wildnislehrer.com"
    private val coursesUrl = "$baseUrl/onlinekurse-hundeschule/"

    suspend fun parseBlogPosts(): List<ContentItem> {
        val posts = mutableListOf<ContentItem>()

        try {
            android.util.Log.d("WordPressParser", "Lade Blogbeiträge...")

            val doc = Jsoup.connect("$baseUrl/blog/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            val articles = doc.select("article, .post, .entry, .hentry, .blog-post")
            android.util.Log.d("WordPressParser", "Gefundene Artikel-Container: ${articles.size}")

            if (articles.isNotEmpty()) {
                for (article in articles) {
                    val titleElem = article.select("h2 a, h1 a, h3 a, .entry-title a, .post-title a").firstOrNull()
                    val title = titleElem?.text() ?: continue
                    val url = titleElem.attr("abs:href")
                    val excerpt = article.select("p, .excerpt, .entry-summary, .entry-content p").firstOrNull()?.text()?.take(150) ?: ""
                    val date = article.select(".published, time, .entry-date, .post-date").firstOrNull()?.text() ?: ""

                    if (url.isNotEmpty()) {
                        posts.add(ContentItem(title, url, excerpt, date, 0, "blog"))
                        android.util.Log.d("WordPressParser", "Beitrag: $title")
                    }
                }
            }

            if (posts.isEmpty()) {
                val links = doc.select("a[href*='/blog/']")
                for (link in links) {
                    val href = link.attr("abs:href")
                    val title = link.text()

                    if (href.contains("/blog/") &&
                        !href.endsWith("/blog/") &&
                        title.length > 5 &&
                        !href.contains("#") &&
                        !href.contains("page/")) {
                        posts.add(ContentItem(title, href, ""))
                        android.util.Log.d("WordPressParser", "Fallback-Beitrag: $title")
                    }
                }
            }

            android.util.Log.d("WordPressParser", "${posts.size} Beiträge gefunden")

        } catch (e: Exception) {
            android.util.Log.e("WordPressParser", "Fehler: ${e.message}")
            e.printStackTrace()
        }

        return posts.distinctBy { it.url }
    }

    suspend fun parsePages(): List<ContentItem> {
        val pages = mutableListOf<ContentItem>()

        try {
            android.util.Log.d("WordPressParser", "Lade Seiten...")
            val doc = Jsoup.connect(baseUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            val menuLinks = doc.select("nav a, .menu a, header a, .navigation a")
            val seenUrls = mutableSetOf<String>()

            for (link in menuLinks) {
                val href = link.attr("abs:href")
                val title = link.text()

                // Onlinekurse-Seite und deren Unterseiten ausschließen
                if (href.startsWith(baseUrl) &&
                    !href.contains("/blog") &&
                    !href.contains("/onlinekurse-hundeschule") &&
                    !href.contains("#") &&
                    !href.contains("wp-content") &&
                    !href.contains("wp-admin") &&
                    !href.contains("wp-login") &&
                    title.length > 2 &&
                    href !in seenUrls &&
                    href != baseUrl &&
                    !href.endsWith(".jpg") &&
                    !href.endsWith(".png") &&
                    !href.endsWith(".pdf")) {

                    seenUrls.add(href)

                    // Einrückungsebene basierend auf Menü-Struktur
                    val level = calculateMenuLevel(link)
                    pages.add(ContentItem(title, href, "", "", level, "page"))
                    android.util.Log.d("WordPressParser", "Seite (Level $level): $title")
                }
            }

            android.util.Log.d("WordPressParser", "${pages.size} Seiten gefunden")

        } catch (e: Exception) {
            android.util.Log.e("WordPressParser", "Fehler: ${e.message}")
            e.printStackTrace()
        }

        return pages
    }

    suspend fun parseCourses(): List<ContentItem> {
        val courses = mutableListOf<ContentItem>()
        val coursesBaseUrl = "$baseUrl/onlinekurse-hundeschule/"
        val seenUrls = mutableSetOf<String>()

        try {
            android.util.Log.d("WordPressParser", "Lade Onlinekurse hierarchisch...")

            // Hauptseite hinzufügen - Level 0
            courses.add(ContentItem("Onlinekurse Hundeschule", coursesBaseUrl, "Übersicht aller Onlinekurse", "", 0, "course"))
            seenUrls.add(coursesBaseUrl)

            android.util.Log.d("WordPressParser", "Level 0: Onlinekurse Hundeschule")

            // Rekursive Funktion - behält die Reihenfolge bei
            suspend fun crawlCoursePage(url: String, currentLevel: Int) {
                try {
                    android.util.Log.d("WordPressParser", "Crawle (Level $currentLevel): $url")

                    val doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .followRedirects(true)
                        .get()

                    // Links in der Reihenfolge wie sie auf der Seite erscheinen
                    val links = doc.select("a[href]")

                    for (link in links) {
                        val href = link.attr("abs:href")
                        val title = link.text().trim()

                        // Nur Links unter /onlinekurse-hundeschule/
                        if (href.startsWith(coursesBaseUrl) &&
                            href != coursesBaseUrl &&
                            !href.contains("#") &&
                            !href.contains("?") &&
                            !href.endsWith(".jpg") &&
                            !href.endsWith(".png") &&
                            !href.endsWith(".pdf") &&
                            title.length > 3 &&
                            href !in seenUrls) {

                            seenUrls.add(href)

                            // Nächstes Level ist currentLevel + 1
                            val nextLevel = currentLevel + 1

                            // Füge zur Liste hinzu
                            courses.add(ContentItem(title, href, "", "", nextLevel, "course"))
                            android.util.Log.d("WordPressParser", "  ".repeat(nextLevel) + "→ Level $nextLevel: $title")

                            // Rekursiv in diese Seite gehen mit dem neuen Level
                            crawlCoursePage(href, nextLevel)
                        }
                    }

                    // Kleine Pause
                    kotlinx.coroutines.delay(100)

                } catch (e: Exception) {
                    android.util.Log.e("WordPressParser", "Fehler bei $url: ${e.message}")
                }
            }

            // Starte Crawling von der Hauptseite mit Level 0
            crawlCoursePage(coursesBaseUrl, 0)

            android.util.Log.d("WordPressParser", "${courses.size} Kurs-Seiten gesamt gefunden")

        } catch (e: Exception) {
            android.util.Log.e("WordPressParser", "Fehler beim Kurse-Laden: ${e.message}")
            e.printStackTrace()
        }

        // Keine Sortierung - behält die gefundene Reihenfolge
        return courses.distinctBy { it.url }
    }

    private fun calculateMenuLevel(link: org.jsoup.nodes.Element): Int {
        var level = 0
        var parent = link.parent()

        while (parent != null) {
            if (parent.tagName() == "ul" || parent.tagName() == "ol") {
                level++
            }
            parent = parent.parent()
        }

        return level.coerceAtMost(3)
    }
}