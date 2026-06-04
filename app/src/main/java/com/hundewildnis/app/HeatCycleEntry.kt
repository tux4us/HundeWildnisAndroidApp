package com.hundewildnis.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

@Parcelize
data class HeatCycleEntry(

    val id: String = UUID.randomUUID().toString(),

    // ═══════════════════════════════════════════════════════════
    // BASIS
    // ═══════════════════════════════════════════════════════════
    val cycleNumber: Int = 0,           // wird von HeatCycleManager automatisch vergeben
    var startDate: String = "",          // "dd.MM.yyyy"
    var endDate: String = "",            // "dd.MM.yyyy" — leer wenn noch aktiv
    var intensity: String = "Mittel",   // "Leicht" | "Mittel" | "Stark"
    var symptoms: String = "",           // Freitext körperliche Symptome

    // ═══════════════════════════════════════════════════════════
    // VERHALTENSMERKMALE WÄHREND DER LÄUFIGKEIT
    // ═══════════════════════════════════════════════════════════
    var behaviorSigns: List<String> = emptyList(),  // Auswahl aus BEHAVIOR_OPTIONS
    var behaviorNotes: String = "",

    // ═══════════════════════════════════════════════════════════
    // SCHEINTRÄCHTIGKEIT
    // ═══════════════════════════════════════════════════════════
    var hasPseudoPregnancy: Boolean = false,
    var pseudoPregnancySigns: List<String> = emptyList(), // Auswahl aus PSEUDO_OPTIONS
    var pseudoPregnancyNotes: String = "",

    val createdAt: Long = System.currentTimeMillis()

) : Parcelable {

    // ═══════════════════════════════════════════════════════════
    // BERECHNETE PROPERTIES
    // ═══════════════════════════════════════════════════════════

    val durationDays: Int
        get() {
            if (startDate.isEmpty() || endDate.isEmpty()) return 0
            return try {
                val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                val start = sdf.parse(startDate) ?: return 0
                val end   = sdf.parse(endDate)   ?: return 0
                ((end.time - start.time) / (1000L * 60 * 60 * 24)).toInt()
            } catch (e: Exception) { 0 }
        }

    /** true wenn Startdatum gesetzt, aber noch kein Enddatum */
    val isActive: Boolean
        get() = startDate.isNotEmpty() && endDate.isEmpty()

    val durationLabel: String
        get() = when {
            isActive          -> "Noch aktiv"
            durationDays == 0 -> "—"
            else              -> "$durationDays Tage"
        }

    // ═══════════════════════════════════════════════════════════
    // OPTIONSLISTEN (fest definiert, einheitlich in Activity + Manager)
    // ═══════════════════════════════════════════════════════════
    companion object {

        val INTENSITY_OPTIONS = listOf("Leicht", "Mittel", "Stark")

        val BEHAVIOR_OPTIONS = listOf(
            "Unruhe / Nervosität",
            "Erhöhte Anhänglichkeit",
            "Aggressivität gegenüber anderen Hunden",
            "Verstärktes Markierverhalten",
            "Fluchtversuche / Ausbruchsversuche",
            "Verminderter Appetit",
            "Erhöhte Ablenkbarkeit beim Training",
            "Häufiges Urinieren",
            "Reitverhalten",
            "Gesteigertes Interesse an Rüden"
        )

        val PSEUDO_OPTIONS = listOf(
            "Nestbau",
            "Gesäuge geschwollen",
            "Milchproduktion",
            "Spielzeug / Gegenstände adoptieren",
            "Mutterschutzverhalten (Beschützen von Objekten)",
            "Appetitverlust",
            "Unruhe / vermehrtes Hecheln",
            "Gewichtszunahme",
            "Rückzug / Apathie"
        )
    }
}
