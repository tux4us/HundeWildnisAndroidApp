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
                .timeout(8000)
                .get()

            val articles = doc.select("article, .post, .entry, .hentry, .blog-post")
            android.util.Log.d("WordPressParser", "Gefundene Artikel-Container: ${articles.size}")

            for (article in articles) {
                try {
                    val titleElement = article.select("h1, h2, h3, .entry-title, .post-title").first()
                    val linkElement = article.select("a[href]").first()
                    val excerptElement = article.select(".entry-content, .post-content, p").first()
                    val dateElement = article.select("time, .date, .post-date").first()

                    val title = titleElement?.text()?.trim() ?: continue
                    val url = linkElement?.attr("abs:href") ?: continue
                    val excerpt = excerptElement?.text()?.trim()?.take(200) ?: ""
                    val date = dateElement?.text()?.trim() ?: ""

                    if (url.startsWith(baseUrl) && !url.contains("/blog/") && title.isNotEmpty()) {
                        posts.add(ContentItem(title, url, excerpt, date, 0, "blog"))
                        android.util.Log.d("WordPressParser", "✓ Blog: $title")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WordPressParser", "Fehler beim Parsen eines Artikels: ${e.message}")
                }
            }

            android.util.Log.d("WordPressParser", "${posts.size} Blogbeiträge gefunden")

        } catch (e: Exception) {
            android.util.Log.e("WordPressParser", "Fehler beim Blog-Laden: ${e.message}")
            e.printStackTrace()
        }

        return posts.sortedByDescending { it.date }
    }


    suspend fun parsePages(): List<ContentItem> {
        val pages = mutableListOf<ContentItem>()

        try {
            android.util.Log.d("WordPressParser", "Lade Seiten...")

            val doc = Jsoup.connect("$baseUrl/de/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(8000)
                .get()

            val menuLinks = doc.select("nav a, .menu a, header a, .navigation a")
            val seenUrls = mutableSetOf<String>()

            // Unerwünschte Navigationselemente
            val excludedTitles = listOf(
                "vorheriger beitrag",
                "nächster beitrag",
                "previous post",
                "next post",
                "zurück",
                "weiter",
                "start"  // NEU: "Start"-Link ausschließen
            )

            // Bekannte Seiten-Kategorien (alles andere sind vermutlich Blogbeiträge)
            val validPageCategories = listOf(
                "/hundeschule/",
                "/wildnisschule/",
                "/informationen/",
                "/impressum/",
                "/honorare",
                "/downloads"
            )

            for (link in menuLinks) {
                val href = link.attr("abs:href")
                val title = link.text().trim()
                val titleLower = title.lowercase()

                // Prüfung auf unerwünschte Titel
                val isExcluded = excludedTitles.any { excluded ->
                    titleLower.contains(excluded) || titleLower.startsWith(excluded)
                }

                // Prüfung ob es eine bekannte Seitenkategorie ist ODER die Startseite
                val normalizedHref = href.trimEnd('/')
                val isValidPage = normalizedHref == baseUrl.trimEnd('/') ||
                        validPageCategories.any { href.contains(it) }

                if (href.startsWith(baseUrl) &&
                    !href.contains("/blog") &&
                    !href.contains("/blog/") &&
                    !href.contains("/onlinekurse-hundeschule") &&
                    !href.contains("/onlinekurse-hundeschule/") &&
                    !href.contains("#") &&
                    !href.contains("?") &&
                    title.length > 2 &&
                    href !in seenUrls &&
                    !isExcluded &&
                    isValidPage) {

                    seenUrls.add(href)

                    val level = when {
                        // Level 0: Nur die Hauptseite
                        normalizedHref == baseUrl.trimEnd('/') -> 0

                        // Level 1: Hundeschule Hauptseite
                        normalizedHref == "$baseUrl/hundeschule".trimEnd('/') -> 1

                        // Level 1: Wildnisschule Hauptseite
                        normalizedHref == "$baseUrl/wildnisschule".trimEnd('/') -> 1

                        // Level 2: Direkte Unterseiten von Hundeschule
                        href.startsWith("$baseUrl/hundeschule/") &&
                                href.removePrefix("$baseUrl/hundeschule/").trimEnd('/').count { it == '/' } == 0 -> 2

                        // Level 2: Direkte Unterseiten von Wildnisschule
                        href.startsWith("$baseUrl/wildnisschule/") &&
                                href.removePrefix("$baseUrl/wildnisschule/").trimEnd('/').count { it == '/' } == 0 -> 2

                        // Level 3+: Tiefere Verschachtelung unter Hundeschule
                        href.startsWith("$baseUrl/hundeschule/") -> {
                            2 + href.removePrefix("$baseUrl/hundeschule/").trimEnd('/').count { it == '/' }
                        }

                        // Level 3+: Tiefere Verschachtelung unter Wildnisschule
                        href.startsWith("$baseUrl/wildnisschule/") -> {
                            2 + href.removePrefix("$baseUrl/wildnisschule/").trimEnd('/').count { it == '/' }
                        }

                        // Level 1: ALLE anderen Seiten (egal wie tief verschachtelt)
                        // Das betrifft: /informationen/, /impressum/, /honorare, /downloads, etc.
                        else -> 1
                    }

                    pages.add(ContentItem(title, href, "", "", level, "page"))
                    android.util.Log.d("WordPressParser", "✓ Seite (Level $level): $title")
                }
            }

            android.util.Log.d("WordPressParser", "${pages.size} Seiten gefunden")

        } catch (e: Exception) {
            android.util.Log.e("WordPressParser", "Fehler: ${e.message}")
            e.printStackTrace()
        }

        // Sortierung: Erst nach Level, dann nach Kategoriegruppe, dann alphabetisch
        return pages.sortedWith(
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
                { it.level },           // Sekundär: Level (innerhalb der Kategorie)
                // { it.title }            // Tertiär: Alphabetisch (innerhalb Level)
            )
        )
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
                        .timeout(8000)
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