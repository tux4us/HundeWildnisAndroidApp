package com.hundewildnis.app

import org.jsoup.Jsoup

data class ContentItem(
    val title: String,
    val url: String,
    val excerpt: String,
    val date: String = ""
)

class WordPressParser {
    private val baseUrl = "https://wildnislehrer.com"

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

                if (href.startsWith(baseUrl) &&
                    !href.contains("/blog") &&
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
                    pages.add(ContentItem(title, href, ""))
                    android.util.Log.d("WordPressParser", "Seite: $title")
                }
            }

            android.util.Log.d("WordPressParser", "${pages.size} Seiten gefunden")

        } catch (e: Exception) {
            android.util.Log.e("WordPressParser", "Fehler: ${e.message}")
            e.printStackTrace()
        }

        return pages.distinctBy { it.url }.sortedBy { it.title }
    }
}