package com.hundewildnis.app.podcast

import com.hundewildnis.app.feed.FeedParser
import com.hundewildnis.app.feed.FeedText
import com.hundewildnis.app.feed.Opml
import com.hundewildnis.app.feed.OpmlResult

/** OPML-Import und -Export der Podcast-Abos. Der Import nutzt den OPML-Leser des FeedReaders (nur HTTPS, ohne Doppelte). */
object PodcastOpml {
    /** @throws com.hundewildnis.app.feed.FeedParseException wenn die Datei kein OPML ist */
    fun parse(text: String): OpmlResult = Opml.parse(FeedParser.parseXml(text.trimStart('﻿', ' ', '\t', '\r', '\n')))

    fun export(subscriptions: List<Podcast>, title: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<opml version=\"2.0\">\n  <head><title>").append(FeedText.escapeHtml(title)).append("</title></head>\n  <body>\n")
        for (p in subscriptions) {
            val name = FeedText.escapeHtml(p.title)
            append("    <outline type=\"rss\" text=\"").append(name).append("\" title=\"").append(name)
                .append("\" xmlUrl=\"").append(FeedText.escapeHtml(p.feedUrl)).append("\"/>\n")
        }
        append("  </body>\n</opml>\n")
    }
}
