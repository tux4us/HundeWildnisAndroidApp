package com.hundewildnis.app.podcast

import com.google.gson.Gson
import com.google.gson.JsonParseException

/** Speichert [PodcastData] als JSON-Text. Ein beschädigter oder fremder Text ergibt `null`. */
object PodcastCodec {
    private val gson = Gson()

    fun encode(data: PodcastData): String = gson.toJson(data)

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun decode(text: String): PodcastData? = try {
        val raw = gson.fromJson(text, PodcastData::class.java) ?: return null
        PodcastData(
            version = raw.version,
            subscriptions = (raw.subscriptions ?: emptyList()).filter { it != null && it.feedUrl != null && it.title != null },
            episodes = (raw.episodes ?: emptyMap()).mapValues { (_, list) ->
                (list ?: emptyList()).filter {
                    it != null && it.id != null && it.podcastUrl != null && it.podcastTitle != null &&
                        it.title != null && it.audioUrl != null && it.mime != null && it.notesHtml != null
                }
            },
            positionsMs = (raw.positionsMs ?: emptyMap()).filterValues { it != null },
            played = (raw.played ?: emptySet()).toSet(),
            speed = if (raw.speed in 0.5f..3.0f) raw.speed else 1.0f,
        )
    } catch (e: JsonParseException) {
        null
    } catch (e: IllegalStateException) {
        null
    }
}
