package com.hundewildnis.app.feed

import com.google.gson.Gson
import com.google.gson.JsonParseException

/** Speichert [FeedData] als JSON-Text. Ein beschädigter oder fremder Text ergibt `null`. */
object FeedCodec {
    private val gson = Gson()

    fun encode(data: FeedData): String = gson.toJson(data)

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun decode(text: String): FeedData? = try {
        val raw = gson.fromJson(text, FeedData::class.java) ?: return null
        // Gson setzt fehlende Felder auf null (Kotlin-Standardwerte werden umgangen): absichern.
        FeedData(
            version = raw.version,
            subs = (raw.subs ?: emptyList()).filter { it != null && it.url != null && it.title != null },
            articles = (raw.articles ?: emptyList()).filter {
                it != null && it.id != null && it.feedUrl != null && it.feedTitle != null && it.title != null && it.body != null
            },
            readIds = (raw.readIds ?: emptySet()).toSet(),
        )
    } catch (e: JsonParseException) {
        null
    } catch (e: IllegalStateException) {
        null
    }
}
