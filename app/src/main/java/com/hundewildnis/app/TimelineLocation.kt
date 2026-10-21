package com.hundewildnis.app

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Locale

// ════════════════════════════════════════════════════════════════
// TIMELINE — STANDORTE
//
// Quellen mit eigenen GPS-Daten (Trainings, Nasenarbeit) liefern ihren Standort
// direkt. Für alle übrigen Einträge und für nachträgliche Korrekturen gibt es
// eine Liste im Hundeprofil (DogProfile.timelineLocations): Ereignis-ID → Koordinaten.
// Sie hat Vorrang vor dem Standort der Quelle, ändert die Quelldaten aber nicht.
// Eigene Einträge speichern ihren Standort direkt im Eintrag (TimelineUserEntry.lat/lon).
//
// Gson-serialisiert: braucht eine Keep-Regel in proguard-rules.pro.
// ════════════════════════════════════════════════════════════════

@Parcelize
data class TimelineLocation(
    val eventId: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0
) : Parcelable

object GeoCoordinates {

    fun isValid(lat: Double?, lon: Double?): Boolean =
        lat != null && lon != null &&
            lat in -90.0..90.0 && lon in -180.0..180.0 &&
            !(lat == 0.0 && lon == 0.0)

    /** Anzeige und Eingabefeld: "50.115520, 8.684170" (Punkt als Dezimaltrenner, unabhängig von der Sprache). */
    fun format(lat: Double, lon: Double): String =
        String.format(Locale.ROOT, "%.6f, %.6f", lat, lon)

    private const val NUM = """-?\d+(?:\.\d+)?"""

    private val URL_PATTERNS = listOf(
        Regex("""@($NUM),($NUM)"""),                                        // Google Maps: .../@50.1,8.6,17z
        Regex("""geo:($NUM),($NUM)"""),                                     // geo:50.1,8.6
        Regex("""mlat=($NUM)&mlon=($NUM)"""),                               // OpenStreetMap-Marker
        Regex("""#map=\d+/($NUM)/($NUM)"""),                                // OpenStreetMap-Ansicht
        Regex("""[?&](?:q|ll|query|destination|center)=($NUM)(?:,|%2C)($NUM)""")  // ...?q=50.1,8.6
    )

    private val PLAIN_TOKEN = Regex(
        """(?<![A-Za-z])([NSEWnsew])?\s*(-?\d+(?:[.,]\d+)?)\s*°?\s*([NSEWnsew])?(?![A-Za-z])"""
    )

    /**
     * Liest Koordinaten aus Freitext. Unterstützt:
     *  - "50.11552, 8.68417", "50.11552 8.68417", "50,11552; 8,68417" (Komma als Dezimaltrenner)
     *  - "50.11552° N, 8.68417° E" (S und W ergeben negative Werte)
     *  - Karten-Links (Google Maps, OpenStreetMap) und geo:-Adressen
     * @return Breite/Länge oder null, wenn nichts Gültiges erkannt wird.
     */
    fun parse(input: String): Pair<Double, Double>? {
        val text = input.trim()
        if (text.isEmpty()) return null

        for (pattern in URL_PATTERNS) {
            val m = pattern.find(text) ?: continue
            val lat = m.groupValues[1].toDoubleOrNull()
            val lon = m.groupValues[2].toDoubleOrNull()
            if (isValid(lat, lon)) return lat!! to lon!!
        }

        val tokens = PLAIN_TOKEN.findAll(text).toList()
        if (tokens.size != 2) return null

        data class Part(val value: Double, val letter: Char?)
        val parts = tokens.map { m ->
            val number = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
            val letter = (m.groupValues[1].ifEmpty { m.groupValues[3] }).firstOrNull()?.uppercaseChar()
            val signed = if (letter == 'S' || letter == 'W') -Math.abs(number) else number
            Part(signed, letter)
        }

        // Schreibweise "8.68 E, 50.11 N": Buchstaben bestimmen die Reihenfolge
        val ordered = if (parts[0].letter in listOf('E', 'W') && parts[1].letter in listOf('N', 'S')) {
            listOf(parts[1], parts[0])
        } else {
            parts
        }
        val lat = ordered[0].value
        val lon = ordered[1].value
        return if (isValid(lat, lon)) lat to lon else null
    }
}

object TimelineLocations {

    /**
     * Setzt (lat/lon) oder entfernt (null) den Standort eines Ereignisses und speichert das Profil.
     * Eigene Einträge speichern im Eintrag selbst, alle anderen in der Liste des Profils.
     * Das Profil wird frisch geladen, damit parallele Änderungen nicht überschrieben werden.
     */
    fun save(context: Context, profileId: String, event: TimelineEvent, lat: Double?, lon: Double?): Boolean {
        val fresh = DogProfileManager.getProfile(context, profileId) ?: return false
        val hasLocation = lat != null && lon != null

        val updated = if (event.category == TimelineCategory.EIGENE) {
            fresh.copy(
                timelineEntries = fresh.timelineEntries.map {
                    if (it.id == event.payload) it.copy(lat = lat, lon = lon) else it
                }
            )
        } else {
            val rest = fresh.timelineLocations.filterNot { it.eventId == event.id }
            fresh.copy(
                timelineLocations = if (hasLocation) rest + TimelineLocation(event.id, lat!!, lon!!) else rest
            )
        }
        return DogProfileManager.saveProfile(context, updated.copy(lastModified = System.currentTimeMillis()))
    }
}
