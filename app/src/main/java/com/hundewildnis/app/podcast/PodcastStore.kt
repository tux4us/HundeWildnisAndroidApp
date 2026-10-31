package com.hundewildnis.app.podcast

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lokale Datei der Podcast-Daten (`files/podcasts/data.json`). Oberfläche und Wiedergabedienst greifen über
 * denselben Zwischenspeicher und dieselbe Sperre zu, damit gleichzeitige Änderungen (Aktualisieren,
 * Wiedergabeposition) sich nicht überschreiben.
 */
object PodcastStore {
    private val lock = Mutex()
    private var cache: PodcastData? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun directory(context: Context) = File(context.applicationContext.filesDir, "podcasts")
    private fun file(context: Context) = File(directory(context), "data.json")

    suspend fun load(context: Context): PodcastData = lock.withLock { withContext(Dispatchers.IO) { cached(context) } }

    /** Wendet [change] auf den aktuellen Stand an, speichert und liefert den neuen Stand. */
    suspend fun update(context: Context, change: (PodcastData) -> PodcastData): PodcastData = lock.withLock {
        withContext(Dispatchers.IO) {
            val next = change(cached(context))
            cache = next
            write(context, next)
            next
        }
    }

    /** Wie [update], aber ohne Warten und unabhängig von der Lebensdauer der aufrufenden Komponente. */
    fun updateInBackground(context: Context, change: (PodcastData) -> PodcastData) {
        val appContext = context.applicationContext
        scope.launch { update(appContext, change) }
    }

    private fun cached(context: Context): PodcastData {
        cache?.let { return it }
        val f = file(context)
        val data = try {
            if (f.isFile) PodcastCodec.decode(f.readText()) else null
        } catch (ignored: IOException) {
            null
        } ?: PodcastData()
        cache = data
        return data
    }

    private fun write(context: Context, data: PodcastData) {
        try {
            val dir = directory(context)
            dir.mkdirs()
            val temporary = File(dir, "data.json.tmp")
            temporary.writeText(PodcastCodec.encode(data))
            if (!temporary.renameTo(file(context))) temporary.delete()
        } catch (ignored: IOException) {
            // Der Stand bleibt im Speicher; beim nächsten Speichern wird erneut versucht.
        }
    }
}
