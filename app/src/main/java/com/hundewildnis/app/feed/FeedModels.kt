package com.hundewildnis.app.feed

import java.security.MessageDigest

/** Ein abonnierter Feed. [url] ist die HTTPS-Adresse und dient als Schlüssel. */
data class FeedSub(val url: String, val title: String)

/** Ein gespeicherter Artikel. [id] ist stabil (Hash aus Feed-Adresse und Artikelschlüssel). */
data class FeedArticle(
    val id: String,
    val feedUrl: String,
    val feedTitle: String,
    val title: String,
    val author: String?,
    val url: String?,
    /** Bereinigtes HTML des Artikels. */
    val body: String,
    val publishedSeconds: Long,
)

/** Alle lokal gespeicherten Feed-Daten. [readIds] sind die Kennungen gelesener Artikel. */
data class FeedData(
    val version: Int = 1,
    val subs: List<FeedSub> = emptyList(),
    val articles: List<FeedArticle> = emptyList(),
    val readIds: Set<String> = emptySet(),
)

object FeedIds {
    fun articleId(feedUrl: String, key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$feedUrl\n$key".toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
