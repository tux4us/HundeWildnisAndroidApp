package com.hundewildnis.app.podcast

/** Ein abonnierter Podcast. [feedUrl] ist immer eine HTTPS-Adresse und dient als Schlüssel. */
data class Podcast(
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val imageUrl: String? = null,
)

/** Eine Folge. [id] ist stabil (Hash aus Feed-Adresse und GUID). */
data class Episode(
    val id: String,
    val podcastUrl: String,
    val podcastTitle: String,
    val title: String,
    val publishedSeconds: Long,
    val audioUrl: String,
    val mime: String,
    val durationSeconds: Int,
    /** Bereinigtes HTML der Shownotes (ohne Skripte, Rahmen, Formulare). */
    val notesHtml: String,
    val link: String?,
    val imageUrl: String?,
)

data class ParsedPodcast(val podcast: Podcast, val episodes: List<Episode>)

/**
 * Alle lokal gespeicherten Podcast-Daten. [episodes] ist der Zwischenspeicher je Feed für die Anzeige ohne
 * Netz; [positionsMs] sind Wiedergabepositionen, [played] die gehörten Folgen.
 */
data class PodcastData(
    val version: Int = 1,
    val subscriptions: List<Podcast> = emptyList(),
    val episodes: Map<String, List<Episode>> = emptyMap(),
    val positionsMs: Map<String, Long> = emptyMap(),
    val played: Set<String> = emptySet(),
    val speed: Float = 1.0f,
)
