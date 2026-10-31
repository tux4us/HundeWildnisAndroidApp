package com.hundewildnis.app.podcast

import com.hundewildnis.app.feed.FeedDiscovery
import com.hundewildnis.app.feed.FeedFetcher
import com.hundewildnis.app.feed.FeedParseException

/** Netzwerkzugriff der Podcast-Seite: lädt einen Feed. Ausschließlich HTTPS (siehe [FeedFetcher]). */
object PodcastRepository {
    /** @throws FeedParseException wenn kein Podcast-Feed */
    suspend fun loadFeed(feedUrl: String): ParsedPodcast {
        val page = FeedFetcher.fetch(feedUrl)
        return PodcastParser.parse(page.text, feedUrl)
    }

    /**
     * Lädt [url] als Podcast-Feed. Ist die Antwort eine Webseite, wird deren Feed-Verweis gesucht und einmal
     * gefolgt.
     */
    suspend fun loadOrDiscover(url: String): ParsedPodcast {
        val page = FeedFetcher.fetch(url)
        try {
            return PodcastParser.parse(page.text, url)
        } catch (first: FeedParseException) {
            val found = FeedDiscovery.find(page.text, page.finalUrl) ?: throw first
            return PodcastParser.parse(FeedFetcher.fetch(found).text, found)
        }
    }
}
