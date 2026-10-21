package com.hundewildnis.app

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import androidx.core.content.FileProvider
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.UUID

// ════════════════════════════════════════════════════════════════
// EIGENE TIMELINE-EINTRÄGE
//
// Vom Nutzer nachträglich erfasste Stationen (Unternehmungen, Erlebnisse,
// Gesundheit, Meilensteine, Sonstiges). Sie liegen als Liste im Hundeprofil
// (DogProfile.timelineEntries), wie heatCycles und vitalRecords. Dadurch
// laufen Backup, Wiederherstellung und WebDAV-Sync über das Profil.
//
// Gson-serialisiert: braucht eine Keep-Regel in proguard-rules.pro.
// ════════════════════════════════════════════════════════════════

@Parcelize
data class TimelineUserEntry(
    val id: String = UUID.randomUUID().toString(),
    /** Format wie birthDate: dd.MM.yyyy, MM.yyyy oder yyyy. */
    val date: String = "",
    /** Stabiler Code aus TimelineEntryKind. */
    val kind: String = TimelineEntryKind.OTHER,
    val title: String = "",
    val place: String = "",
    val note: String = "",
    /** content://-URI einer Datei in filesDir/timeline_photos (FileProvider) oder leer. */
    val photoUri: String = "",
    /** Optionaler Standort (WGS84, Dezimalgrad). */
    val lat: Double? = null,
    val lon: Double? = null
) : Parcelable

/** Stabile interne Codes der Eintragsarten (nicht der Anzeigetext). */
object TimelineEntryKind {
    const val ACTIVITY = "activity"
    const val EXPERIENCE = "experience"
    const val HEALTH = "health"
    const val MILESTONE = "milestone"
    const val OTHER = "other"

    /** Reihenfolge der Auswahl im Eingabe-Screen. */
    val ORDER = listOf(ACTIVITY, EXPERIENCE, HEALTH, MILESTONE, OTHER)

    fun normalize(code: String): String = if (code in ORDER) code else OTHER

    fun icon(code: String): String = when (code) {
        ACTIVITY -> "🥾"
        EXPERIENCE -> "⭐"
        HEALTH -> "🩺"
        MILESTONE -> "🏆"
        else -> "📌"
    }

    fun label(context: Context, code: String): String = context.getString(
        when (code) {
            ACTIVITY -> R.string.timeline_kind_activity
            EXPERIENCE -> R.string.timeline_kind_experience
            HEALTH -> R.string.timeline_kind_health
            MILESTONE -> R.string.timeline_kind_milestone
            else -> R.string.timeline_kind_other
        }
    )
}

/** Ersetzt Foto-URIs der eigenen Einträge nach einer Wiederherstellung (Backup-Pfad → neuer Pfad). */
fun DogProfile.remapTimelinePhotos(mapping: Map<String, String>): DogProfile {
    if (timelineEntries.isEmpty() || mapping.isEmpty()) return this
    return copy(
        timelineEntries = timelineEntries.map { e ->
            val mapped = mapping[e.photoUri]
            if (e.photoUri.isNotEmpty() && mapped != null) e.copy(photoUri = mapped) else e
        }
    )
}

/** Ablage der Fotos im internen Speicher (bleibt gültig, unabhängig vom Galerie-Zugriff). */
object TimelinePhotos {

    private const val DIR = "timeline_photos"

    /** Kopiert das gewählte Bild in den internen Speicher und liefert die FileProvider-URI. */
    fun copyIn(context: Context, source: Uri): String? {
        return try {
            val dest = File(context.filesDir, "$DIR/${UUID.randomUUID()}.jpg")
            dest.parentFile?.mkdirs()
            // Verkleinert (max. 1280 px) übernehmen
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
     * Wiederhergestellte Fotos (restored_photos) räumt purgeOrphanFiles auf.
     */
    fun delete(context: Context, uriString: String) {
        if (uriString.isBlank()) return
        try {
            val uri = Uri.parse(uriString)
            val segments = uri.pathSegments
            if (segments.size >= 2 && segments[0] == DIR) {
                File(context.filesDir, "$DIR/${segments.last()}").delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}