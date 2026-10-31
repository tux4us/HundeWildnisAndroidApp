package com.hundewildnis.app.podcast

/** Reine Zustandsänderungen der Podcast-Daten; jede Funktion liefert einen neuen Stand. */
object PodcastLogic {
    const val MAX_SUBSCRIPTIONS = 100

    /** Zeitraum der „Letzte 4 Wochen“-Liste in Tagen. */
    const val RECENT_DAYS = 28

    /** Bleibt weniger als diese Zeit übrig, gilt die Folge als gehört. */
    const val PLAYED_REMAINING_MS = 30_000L

    /** Positionen unter dieser Grenze werden nicht gemerkt. */
    const val MIN_POSITION_MS = 5_000L

    val SPEEDS = listOf(0.8f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    /** Fügt ein Abo hinzu (Duplikate nach Adresse ohne Beachtung der Groß-/Kleinschreibung entfallen). `null` bei Limit. */
    fun subscribe(data: PodcastData, podcast: Podcast): PodcastData? {
        if (data.subscriptions.any { it.feedUrl.equals(podcast.feedUrl, ignoreCase = true) }) return data
        if (data.subscriptions.size >= MAX_SUBSCRIPTIONS) return null
        return data.copy(subscriptions = data.subscriptions + podcast)
    }

    /** Entfernt Abo und Zwischenspeicher samt Positionen und Gehört-Markierungen dieses Podcasts. */
    fun unsubscribe(data: PodcastData, feedUrl: String): PodcastData {
        val removed = data.episodes[feedUrl].orEmpty().mapTo(HashSet()) { it.id }
        return data.copy(
            subscriptions = data.subscriptions.filterNot { it.feedUrl.equals(feedUrl, ignoreCase = true) },
            episodes = data.episodes - feedUrl,
            positionsMs = data.positionsMs.filterKeys { it !in removed },
            played = data.played.filterTo(HashSet()) { it !in removed },
        )
    }

    /** Übernimmt einen geladenen Feed: Titel, Autor und Bild des Abos werden aktualisiert, die Folgenliste ersetzt. */
    fun applyFeed(data: PodcastData, parsed: ParsedPodcast): PodcastData {
        val url = parsed.podcast.feedUrl
        val subscriptions = data.subscriptions.map { if (it.feedUrl == url) parsed.podcast else it }
        return data.copy(subscriptions = subscriptions, episodes = data.episodes + (url to parsed.episodes))
    }

    /** Folgen eines Podcasts, neueste zuerst. */
    fun episodesOf(data: PodcastData, feedUrl: String): List<Episode> =
        data.episodes[feedUrl].orEmpty().sortedByDescending { it.publishedSeconds }

    /**
     * Folgen aller Abos der letzten [days] Tage, neueste zuerst. Folgen ohne Datum werden nicht aufgenommen,
     * Folgen mit Datum in der Zukunft (mehr als ein Tag) ebenfalls nicht.
     */
    fun recentEpisodes(data: PodcastData, nowSeconds: Long, days: Int = RECENT_DAYS): List<Episode> {
        val from = nowSeconds - days * 86_400L
        val to = nowSeconds + 86_400L
        val subscribed = data.subscriptions.mapTo(HashSet()) { it.feedUrl }
        return data.episodes.filterKeys { it in subscribed }.values.flatten()
            .filter { it.publishedSeconds in from..to }
            .sortedByDescending { it.publishedSeconds }
    }

    /**
     * Merkt die Wiedergabeposition. Bleibt bis zum Ende weniger als [PLAYED_REMAINING_MS], gilt die Folge als
     * gehört und die Position entfällt; sehr frühe Positionen werden nicht gemerkt.
     */
    fun savePosition(data: PodcastData, id: String, positionMs: Long, durationMs: Long): PodcastData {
        if (positionMs < 0) return data
        if (durationMs > 0 && durationMs - positionMs < PLAYED_REMAINING_MS) {
            return data.copy(positionsMs = data.positionsMs - id, played = data.played + id)
        }
        if (positionMs < MIN_POSITION_MS) return data.copy(positionsMs = data.positionsMs - id)
        return data.copy(positionsMs = data.positionsMs + (id to positionMs))
    }

    fun resumePosition(data: PodcastData, id: String): Long = data.positionsMs[id] ?: 0L

    fun setPlayed(data: PodcastData, id: String, played: Boolean): PodcastData = data.copy(
        played = if (played) data.played + id else data.played - id,
        positionsMs = data.positionsMs - id,
    )

    /** `1:02:03` oder `12:03`; Sekunden unter 0 werden wie 0 behandelt. */
    fun formatClock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** Wechselt zur nächsten Geschwindigkeit der Liste und beginnt danach von vorn. */
    fun nextSpeed(current: Float): Float {
        val index = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
        return if (index < 0) 1.0f else SPEEDS[(index + 1) % SPEEDS.size]
    }
}
