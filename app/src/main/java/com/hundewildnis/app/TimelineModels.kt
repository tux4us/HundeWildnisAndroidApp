package com.hundewildnis.app

import android.content.Context

// ════════════════════════════════════════════════════════════════
// TIMELINE — MODELLE
//
// Die Timeline speichert nichts. Sie ist eine reine Sicht auf die
// vorhandenen Daten (Training, Nasenarbeit, Spiel & Spaß, Intervall-
// Timer, Vitalwerte, Läufigkeit, Beginn der gemeinsamen Zeit).
// Die Klassen werden nicht mit Gson serialisiert und brauchen daher
// keine ProGuard-Regeln.
// ════════════════════════════════════════════════════════════════

enum class TimelineCategory(
    val icon: String,
    /** 0-basierter Index der Gruppenfarbe (StartGroupColors). */
    val colorGroup: Int,
    /** false = erscheint nicht als Filter-Chip. */
    val filterable: Boolean
) {
    TRAINING("🎓", 0, true),
    NASENARBEIT("🐾", 1, true),
    SPIEL("🎾", 2, true),
    INTERVALL("⏱️", 3, true),
    VITAL("❤️", 4, true),
    HEAT("🌸", 5, true),
    EIGENE("📌", 2, true),
    START("🏁", 0, false);

    fun label(context: Context): String = context.getString(
        when (this) {
            TRAINING -> R.string.timeline_cat_training
            NASENARBEIT -> R.string.timeline_cat_nasenarbeit
            SPIEL -> R.string.timeline_cat_spiel
            INTERVALL -> R.string.timeline_cat_intervall
            VITAL -> R.string.timeline_cat_vital
            HEAT -> R.string.timeline_cat_heat
            EIGENE -> R.string.timeline_cat_eigene
            START -> R.string.timeline_cat_start
        }
    )
}

/**
 * Ein Eintrag der Timeline.
 *
 * @param payload quellenabhängige Referenz für das Öffnen der Detailansicht:
 *  TRAINING = Kategorie-ID, NASENARBEIT/VITAL/HEAT/INTERVALL = Datensatz-ID,
 *  SPIEL = "aktivitaetId|ORT", EIGENE = ID des eigenen Eintrags, START = leer.
 * @param hasTime false bei reinen Datumsangaben (Läufigkeit, eigene Einträge, Beginn der gemeinsamen Zeit).
 * @param icon überschreibt das Symbol der Kategorie (eigene Einträge: Symbol der Eintragsart).
 * @param dateText fertig formatierter Datumstext, z. B. bei ungenauen Daten ("05.2022" oder "2022").
 * @param photoUri optionales Foto (eigene Einträge).
 * @param plain Detailtext ohne Symbole für den Export (leer = bereinigter subtitle).
 */
data class TimelineEvent(
    val id: String,
    val timestamp: Long,
    val hasTime: Boolean,
    val category: TimelineCategory,
    val title: String,
    val subtitle: String,
    val payload: String,
    val icon: String? = null,
    val dateText: String? = null,
    val photoUri: String = "",
    val plain: String = "",
    /** Standort (WGS84), falls bekannt: aus der Quelle, dem eigenen Eintrag oder nachträglich gesetzt. */
    val lat: Double? = null,
    val lon: Double? = null
)

/** Zeilen der RecyclerView: Monatskopf oder Eintrag. */
sealed class TimelineRow {
    data class Header(val label: String) : TimelineRow()
    data class Entry(val event: TimelineEvent) : TimelineRow()
}
