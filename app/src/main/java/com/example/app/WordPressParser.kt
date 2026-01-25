package com.hundewildnis.app

import android.util.Log
import com.hundewildnis.app.ContentItem
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class WordPressParser {
    private val baseUrl = "https://wildnislehrer.com"
    private val coursesUrl = "$baseUrl/onlinekurse-hundeschule/"

    suspend fun parseBlogPosts(): List<ContentItem> {
        val posts = mutableListOf<ContentItem>()

        try {
            Log.d("WordPressParser", "Lade Blogbeiträge...")

            val doc = Jsoup.connect("$baseUrl/blog/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(60000)
                .get()

            val articles = doc.select("article, .post, .entry, .hentry, .blog-post")
            Log.d("WordPressParser", "Gefundene Artikel-Container: ${articles.size}")

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
                        posts.add(
                            ContentItem(
                                title = title,
                                url = url,
                                excerpt = excerpt,
                                date = date,
                                level = 0,
                                type = "blog"
                            )
                        )
                        Log.d("WordPressParser", "✓ Blog: $title")
                    }
                } catch (e: Exception) {
                    Log.e("WordPressParser", "Fehler beim Parsen eines Artikels: ${e.message}")
                }
            }

            Log.d("WordPressParser", "${posts.size} Blogbeiträge gefunden")

        } catch (e: Exception) {
            Log.e("WordPressParser", "Fehler beim Blog-Laden: ${e.message}")
            e.printStackTrace()
        }

        return posts.sortedByDescending { it.date }
    }


    suspend fun parsePages(): List<ContentItem> {
        val pages = mutableListOf<ContentItem>()

        try {
            Log.d("WordPressParser", "Lade Seiten...")

            val doc = Jsoup.connect("$baseUrl/de/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(60000)
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
                    !href.startsWith(coursesUrl) &&
                    !href.contains("/blog") &&
                    !href.contains("/blog/") &&
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

                    pages.add(
                        ContentItem(
                            title = title,
                            url = href,
                            excerpt = "",
                            date = "",
                            level = level,
                            type = "page"
                        )
                    )
                    Log.d("WordPressParser", "✓ Seite (Level $level): $title")
                }
            }

            Log.d("WordPressParser", "${pages.size} Seiten gefunden")

        } catch (e: Exception) {
            Log.e("WordPressParser", "Fehler: ${e.message}")
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
        val seenUrls = mutableSetOf<String>()

        try {
            Log.d("WordPressParser", "⭐ Lade Onlinekurse hierarchisch...")

            // Hauptseite hinzufügen - Level 0
            courses.add(
                ContentItem(
                    title = "Onlinekurse Hundeschule",
                    url = coursesUrl,
                    excerpt = "Übersicht aller Onlinekurse",
                    date = "",
                    level = 0,
                    type = "course"
                )
            )
            seenUrls.add(coursesUrl)

            Log.d("WordPressParser", "✓ Level 0: Onlinekurse Hundeschule")

            // Rekursive Funktion
            suspend fun crawlCoursePage(url: String, currentLevel: Int) {
                try {
                    Log.d("WordPressParser", "🔍 Crawle (Level $currentLevel): $url")

                    val doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(60000)
                        .followRedirects(true)
                        .get()

                    // Links in der Reihenfolge wie sie auf der Seite erscheinen
                    val links = doc.select("a[href]")
                    Log.d("WordPressParser", "📄 Gefundene Links auf Seite: ${links.size}")

                    var foundCount = 0
                    for (link in links) {
                        val href = link.attr("abs:href")
                        val title = link.text().trim()

                        // Nur Links unter /onlinekurse-hundeschule/
                        if (href.startsWith(coursesUrl) &&
                            href != coursesUrl &&
                            !href.contains("#") &&
                            !href.contains("?") &&
                            !href.endsWith(".jpg") &&
                            !href.endsWith(".png") &&
                            !href.endsWith(".pdf") &&
                            title.length > 3 &&
                            href !in seenUrls) {

                            seenUrls.add(href)
                            foundCount++

                            // Nächstes Level ist currentLevel + 1
                            val nextLevel = currentLevel + 1

                            // Füge zur Liste hinzu
                            courses.add(
                                ContentItem(
                                    title = title,
                                    url = href,
                                    excerpt = "",
                                    date = "",
                                    level = nextLevel,
                                    type = "course"
                                )
                            )
                            Log.d("WordPressParser", "  ".repeat(nextLevel) + "✓ Level $nextLevel: $title")

                            // Rekursiv in diese Seite gehen mit dem neuen Level
                            crawlCoursePage(href, nextLevel)
                        }
                    }

                    Log.d("WordPressParser", "📊 Auf Level $currentLevel: $foundCount neue Kurse gefunden")

                    // Kleine Pause
                    delay(100)

                } catch (e: Exception) {
                    Log.e("WordPressParser", "❌ Fehler bei $url: ${e.message}")
                    e.printStackTrace()
                }
            }

            // Starte Crawling von der Hauptseite mit Level 0
            crawlCoursePage(coursesUrl, 0)

            Log.d("WordPressParser", "✅ ${courses.size} Kurs-Seiten gesamt gefunden")

        } catch (e: Exception) {
            Log.e("WordPressParser", "❌ Fehler beim Kurse-Laden: ${e.message}")
            e.printStackTrace()
        }

        // Keine Sortierung - behält die gefundene Reihenfolge
        return courses.distinctBy { it.url }
    }

    private fun calculateMenuLevel(link: Element): Int {
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
