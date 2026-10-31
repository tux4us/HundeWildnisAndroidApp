package com.hundewildnis.app.feed

/** Reine Zustandsänderungen der Feed-Daten; jede Funktion liefert einen neuen Stand. */
object FeedLogic {
    const val MAX_SUBSCRIPTIONS = 100

    /** Pro Feed werden höchstens so viele Artikel gehalten. */
    const val MAX_ARTICLES_PER_FEED = 50

    /** Gesamtgrenze der gespeicherten Artikel. */
    const val MAX_ARTICLES = 1500

    /** Ergebnis von [addSubs]. */
    data class AddResult(val data: FeedData, val added: Int, val duplicates: Int, val overLimit: Int)

    /** Fügt Abos hinzu; Duplikate (Adresse ohne Beachtung der Groß-/Kleinschreibung) und Überschreitungen werden gezählt. */
    fun addSubs(data: FeedData, incoming: List<FeedSub>): AddResult {
        val subs = ArrayList(data.subs)
        val known = subs.mapTo(HashSet()) { it.url.lowercase() }
        var added = 0
        var duplicates = 0
        var over = 0
        for (sub in incoming) {
            when {
                !known.add(sub.url.lowercase()) -> duplicates++
                subs.size >= MAX_SUBSCRIPTIONS -> over++
                else -> {
                    subs.add(sub)
                    added++
                }
            }
        }
        return AddResult(data.copy(subs = subs), added, duplicates, over)
    }

    /** Entfernt das Abo samt seiner Artikel. */
    fun removeSub(data: FeedData, url: String): FeedData {
        val keep = data.articles.filterNot { it.feedUrl == url }
        val ids = keep.mapTo(HashSet()) { it.id }
        return data.copy(
            subs = data.subs.filterNot { it.url == url },
            articles = keep,
            readIds = data.readIds.filterTo(HashSet()) { it in ids },
        )
    }

    /** Anzeigename: Titel, sonst Host der Adresse. */
    fun displayTitle(sub: FeedSub): String = sub.title.ifBlank { sub.url.substringAfter("://").substringBefore('/') }

    /**
     * Übernimmt einen geladenen Feed: Titel des Abos wird aktualisiert, die Artikel des Feeds werden durch die
     * neuen ersetzt. Artikel ohne Datum erhalten den Zeitpunkt, an dem sie zuerst gesehen wurden. Gelesene
     * Artikel, die nicht mehr im Feed stehen, entfallen samt Markierung.
     */
    fun applyFeed(data: FeedData, feedUrl: String, parsed: ParsedFeed, nowSeconds: Long): FeedData {
        val sub = data.subs.firstOrNull { it.url == feedUrl } ?: return data
        val title = parsed.title.ifBlank { sub.title }
        val known = data.articles.filter { it.feedUrl == feedUrl }.associateBy { it.id }
        val fresh = LinkedHashMap<String, FeedArticle>()
        for (entry in parsed.items) {
            val id = FeedIds.articleId(feedUrl, entry.key)
            if (id in fresh) continue
            val published = if (entry.publishedSeconds > 0L) entry.publishedSeconds else known[id]?.publishedSeconds ?: nowSeconds
            fresh[id] = FeedArticle(
                id = id,
                feedUrl = feedUrl,
                feedTitle = title,
                title = entry.title.ifBlank { entry.url.orEmpty() },
                author = entry.author,
                url = entry.url,
                body = FeedText.sanitize(entry.body),
                // Ein Datum in der Zukunft würde den Artikel dauerhaft nach oben setzen.
                publishedSeconds = minOf(published, nowSeconds),
            )
        }
        val newest = fresh.values.sortedByDescending { it.publishedSeconds }.take(MAX_ARTICLES_PER_FEED)
        val others = data.articles.filterNot { it.feedUrl == feedUrl }
        val all = (others + newest).sortedByDescending { it.publishedSeconds }.take(MAX_ARTICLES)
        val ids = all.mapTo(HashSet()) { it.id }
        return data.copy(
            subs = data.subs.map { if (it.url == feedUrl) it.copy(title = title) else it },
            articles = all,
            readIds = data.readIds.filterTo(HashSet()) { it in ids },
        )
    }

    fun markRead(data: FeedData, id: String, read: Boolean = true): FeedData =
        data.copy(readIds = if (read) data.readIds + id else data.readIds - id)

    fun markAllRead(data: FeedData, feedUrl: String?): FeedData {
        val ids = data.articles.filter { feedUrl == null || it.feedUrl == feedUrl }.map { it.id }
        return data.copy(readIds = data.readIds + ids)
    }

    /** Artikel, neueste zuerst; optional nur eines Feeds und/oder nur ungelesene. */
    fun visible(data: FeedData, feedUrl: String?, unreadOnly: Boolean): List<FeedArticle> =
        data.articles
            .filter { (feedUrl == null || it.feedUrl == feedUrl) && (!unreadOnly || it.id !in data.readIds) }
            .sortedByDescending { it.publishedSeconds }

    fun unreadCount(data: FeedData, feedUrl: String?): Int =
        data.articles.count { (feedUrl == null || it.feedUrl == feedUrl) && it.id !in data.readIds }

    /** Liest eine OPML-Datei (nur HTTPS-Adressen, ohne Doppelte). @throws FeedParseException wenn die Datei kein OPML ist */
    fun parseOpml(text: String): OpmlResult =
        Opml.parse(FeedParser.parseXml(text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')))

    /**
     * Liest eine Textliste mit einer Adresse pro Zeile (leere Zeilen und Zeilen mit `#` am Anfang entfallen).
     * Nur HTTPS-Adressen werden übernommen; Doppelte entfallen.
     */
    fun parseUrlList(text: String): OpmlResult {
        val feeds = LinkedHashMap<String, OpmlFeed>()
        var insecure = 0
        var invalid = 0
        for (raw in text.lineSequence().take(5_000)) {
            val line = raw.trim().trimStart('\uFEFF')
            if (line.isEmpty() || line.startsWith("#")) continue
            when (val url = FeedUrl.parse(line.substringBefore(' '))) {
                is FeedUrlResult.Ok -> feeds.putIfAbsent(url.url.lowercase(), OpmlFeed(url.url, ""))
                FeedUrlResult.NotHttps -> insecure++
                FeedUrlResult.Empty, FeedUrlResult.Invalid -> invalid++
            }
        }
        return OpmlResult(feeds.values.toList(), insecure, invalid)
    }

    /** OPML-Datei oder Textliste, je nach Inhalt. @throws FeedParseException wenn weder noch lesbar */
    fun parseImport(text: String): OpmlResult {
        val trimmed = text.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (trimmed.startsWith("<")) return parseOpml(trimmed)
        val list = parseUrlList(trimmed)
        if (list.feeds.isEmpty() && list.skippedInsecure == 0) throw FeedParseException("Keine Adressen gefunden")
        return list
    }

    /** OPML-Datei der Abos (Export). */
    fun exportOpml(subs: List<FeedSub>, title: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<opml version=\"2.0\">\n  <head><title>").append(FeedText.escapeHtml(title)).append("</title></head>\n  <body>\n")
        for (s in subs) {
            val name = FeedText.escapeHtml(displayTitle(s))
            append("    <outline type=\"rss\" text=\"").append(name).append("\" title=\"").append(name)
                .append("\" xmlUrl=\"").append(FeedText.escapeHtml(s.url)).append("\"/>\n")
        }
        append("  </body>\n</opml>\n")
    }
}
