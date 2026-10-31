package com.hundewildnis.app.feed

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lokale Datei der Feed-Daten (`files/feed/data.json`). Alle Zugriffe laufen über einen gemeinsamen
 * Zwischenspeicher und eine Sperre, damit gleichzeitige Änderungen (Aktualisieren, Gelesen-Markierung)
 * sich nicht gegenseitig überschreiben.
 */
object FeedStore {
    private val lock = Mutex()
    private var cache: FeedData? = null

    private fun directory(context: Context) = File(context.applicationContext.filesDir, "feed")
    private fun file(context: Context) = File(directory(context), "data.json")

    suspend fun load(context: Context): FeedData = lock.withLock { withContext(Dispatchers.IO) { cached(context) } }

    /** Wendet [change] auf den aktuellen Stand an, speichert und liefert den neuen Stand. */
    suspend fun update(context: Context, change: (FeedData) -> FeedData): FeedData = lock.withLock {
        withContext(Dispatchers.IO) {
            val next = change(cached(context))
            cache = next
            write(context, next)
            next
        }
    }

    private fun cached(context: Context): FeedData {
        cache?.let { return it }
        val f = file(context)
        val data = try {
            if (f.isFile) FeedCodec.decode(f.readText()) else null
        } catch (ignored: IOException) {
            null
        } ?: FeedData()
        cache = data
        return data
    }

    private fun write(context: Context, data: FeedData) {
        try {
            val dir = directory(context)
            dir.mkdirs()
            val temporary = File(dir, "data.json.tmp")
            temporary.writeText(FeedCodec.encode(data))
            if (!temporary.renameTo(file(context))) temporary.delete()
        } catch (ignored: IOException) {
            // Der Stand bleibt im Speicher; beim nächsten Speichern wird erneut versucht.
        }
    }
}
