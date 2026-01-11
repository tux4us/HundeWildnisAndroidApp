package com.hundewildnis.app

import org.jsoup.Jsoup

data class ContentItem(
    val title: String,
    val url: String,
    val excerpt: String,
    val date: String = "",
    val level: Int = 0
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
                        posts.add(ContentItem(title, url, excerpt, date))
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
                    pages.add(ContentItem(title, href, "", "", level))
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

        try {
            android.util.Log.d("WordPressParser", "Lade Onlinekurse...")

            // Hauptseite Onlinekurse
            val mainDoc = Jsoup.connect(coursesUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            courses.add(ContentItem("Onlinekurse Hundeschule", coursesUrl, "Übersicht aller Onlinekurse", "", 0))

            // Unterseiten der Onlinekurse
            val courseLinks = mainDoc.select("a[href*='/onlinekurse-hundeschule/']")
            val seenUrls = mutableSetOf(coursesUrl)

            for (link in courseLinks) {
                val href = link.attr("abs:href")
                val title = link.text()

                if (href.startsWith(coursesUrl) &&
                    href != coursesUrl &&
                    !href.contains("#") &&
                    title.length > 3 &&
                    href !in seenUrls) {

                    seenUrls.add(href)

                    // Einrückungsebene basierend auf URL-Tiefe
                    val level = href.removePrefix(coursesUrl).count { it == '/' }
                    courses.add(ContentItem(title, href, "", "", level + 1))
                    android.util.Log.d("WordPressParser", "Kurs (Level ${level + 1}): $title")
                }
            }

            // Auch Links aus dem Hauptmenü prüfen
            val homeDoc = Jsoup.connect(baseUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(15000)
                .get()

            val menuCourseLinks = homeDoc.select("nav a[href*='/onlinekurse-hundeschule/'], .menu a[href*='/onlinekurse-hundeschule/']")

            for (link in menuCourseLinks) {
                val href = link.attr("abs:href")
                val title = link.text()

                if (href.startsWith(coursesUrl) &&
                    title.length > 2 &&
                    href !in seenUrls) {

                    seenUrls.add(href)
                    val level = if (href == coursesUrl) 0 else 1
                    courses.add(ContentItem(title, href, "", "", level))
                    android.util.Log.d("WordPressParser", "Menü-Kurs (Level $level): $title")
                }
            }

            android.util.Log.d("WordPressParser", "${courses.size} Kurse gefunden")

        } catch (e: Exception) {
            android.util.Log.e("WordPressParser", "Fehler: ${e.message}")
            e.printStackTrace()
        }

        return courses.distinctBy { it.url }.sortedBy { it.level }
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