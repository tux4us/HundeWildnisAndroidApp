package com.hundewildnis.app

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Ablage der Fotos von Gesundheitsbeobachtungen im internen Speicher
 * (bleibt gültig, unabhängig vom Galerie-Zugriff). Aufbau wie TimelinePhotos.
 */
object HealthPhotos {

    private const val DIR = "health_photos"

    /** Höchstanzahl Fotos je Beobachtung. */
    const val MAX_PER_ENTRY = 6

    /** Kopiert das gewählte Bild verkleinert (max. 1280 px) in den internen Speicher; liefert die FileProvider-URI. */
    fun copyIn(context: Context, source: Uri): String? {
        return try {
            val dest = File(context.filesDir, "$DIR/${UUID.randomUUID()}.jpg")
            dest.parentFile?.mkdirs()
            if (PhotoCompressor.compressToFile(context, source, dest)) {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest).toString()
            } else {
                dest.delete()
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Löscht die Datei zu einer URI, aber nur aus dem eigenen Ordner.
     * Wiederhergestellte Fotos (restored_photos) räumt BackupManager.purgeOrphanFiles auf.
     */
    fun delete(context: Context, uriString: String) {
        if (uriString.isBlank()) return
        try {
            val segments = Uri.parse(uriString).pathSegments
            if (segments.size >= 2 && segments[0] == DIR) {
                File(context.filesDir, "$DIR/${segments.last()}").delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteAll(context: Context, uriStrings: Collection<String>) =
        uriStrings.forEach { delete(context, it) }
}

/** Ersetzt Foto-URIs der Gesundheitsbeobachtungen nach einer Wiederherstellung (Backup-Pfad → neuer Pfad). */
fun DogProfile.remapHealthPhotos(mapping: Map<String, String>): DogProfile {
    if (healthObservations.isEmpty() || mapping.isEmpty()) return this
    return copy(
        healthObservations = healthObservations.map { e ->
            if (e.photoUris.isEmpty()) e else e.copy(photoUris = e.photoUris.map { mapping[it] ?: it })
        }
    )
}
