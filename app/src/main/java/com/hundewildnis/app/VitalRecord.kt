package com.hundewildnis.app

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

/**
 * Eine Vitalwert-Messung für einen Hund.
 *
 * Pflichtfeld: pulse (Herzfrequenz).
 * Alle anderen Werte sind optional (null = nicht gemessen).
 */
@Parcelize
data class VitalRecord(

    val id: String = UUID.randomUUID().toString(),
    val dogId: String = "",
    val dogName: String = "",

    /** Timestamp der Messung (Millisekunden seit Epoch) */
    val timestamp: Long = System.currentTimeMillis(),

    // ═══════════════════════════════════════════════════════════
    // VITALWERTE
    // ═══════════════════════════════════════════════════════════

    /** Herzfrequenz in Schlägen/min — Pflichtfeld */
    val pulse: Int = 0,

    /** Atemfrequenz in Zügen/min — optional */
    val respiration: Int? = null,

    /** Körpertemperatur in °C (z.B. 38.5) — optional */
    val temperature: Double? = null,

    /** Kapillarfüllzeit in Sekunden (z.B. 1, 2, 3) — optional */
    val capillaryRefillTime: Int? = null,

    /**
     * Schleimhautfarbe — optional
     * Stabiler interner Code (siehe MucousColorCode), NICHT der Anzeigetext.
     */
    val mucousMembraneColor: String? = null,

    /**
     * Bewusstseinszustand — optional
     * Stabiler interner Code (siehe ConsciousnessCode), NICHT der Anzeigetext.
     */
    val consciousnessLevel: String? = null,

    // ═══════════════════════════════════════════════════════════
    // KONTEXT
    // ═══════════════════════════════════════════════════════════

    /**
     * Belastungsszenario zum Zeitpunkt der Messung.
     * Stabiler interner Code (siehe LoadScenarioCode), NICHT der Anzeigetext.
     */
    val loadScenario: String = LoadScenarioCode.REST,

    /** Freitext: aktuelle Bedingungen und Beobachtungen */
    val notes: String = "",

    /** true wenn diese Messung in eine Notfallmeldung übernommen wurde */
    val transferredToEmergency: Boolean = false

) : Parcelable {

    // ═══════════════════════════════════════════════════════════
    // FORMATIERTE ANZEIGE
    // ═══════════════════════════════════════════════════════════

    fun formattedDate(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formattedDateShort(): String {
        val sdf = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun summaryLine(context: Context): String {
        val parts = mutableListOf<String>()
        parts.add(context.getString(R.string.vital_summary_pulse, pulse))
        respiration?.let { parts.add(context.getString(R.string.vital_summary_respiration, it)) }
        temperature?.let { parts.add(context.getString(R.string.vital_summary_temperature, "%.1f".format(it))) }
        capillaryRefillTime?.let { parts.add(context.getString(R.string.vital_summary_crt, it)) }
        mucousMembraneColor?.let { parts.add(context.getString(R.string.vital_summary_mucous, MucousColorCode.displayName(context, it))) }
        consciousnessLevel?.let { parts.add(context.getString(R.string.vital_summary_consciousness, ConsciousnessCode.displayName(context, it))) }
        return parts.joinToString(" · ")
    }

    /** Vollständiger Teilen-Text (Share Sheet) */
    fun toShareText(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine(context.getString(R.string.vital_share_dog_line, dogName))
        sb.appendLine(context.getString(R.string.vital_share_date_line, formattedDate()))
        sb.appendLine(context.getString(R.string.vital_share_load_line, LoadScenarioCode.displayName(context, loadScenario)))
        sb.appendLine()
        sb.appendLine(context.getString(R.string.vital_share_pulse_line, pulse))
        respiration?.let { sb.appendLine(context.getString(R.string.vital_share_respiration_line, it)) }
        temperature?.let { sb.appendLine(context.getString(R.string.vital_share_temperature_line, "%.1f".format(it))) }
        capillaryRefillTime?.let { sb.appendLine(context.getString(R.string.vital_share_crt_line, it)) }
        mucousMembraneColor?.let { sb.appendLine(context.getString(R.string.vital_share_mucous_line, MucousColorCode.displayName(context, it))) }
        consciousnessLevel?.let { sb.appendLine(context.getString(R.string.vital_share_consciousness_line, ConsciousnessCode.displayName(context, it))) }
        if (notes.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(context.getString(R.string.vital_share_notes_line, notes))
        }
        return sb.toString().trim()
    }

    /** CSV-Zeile (Semikolon-getrennt) */
    fun toCsvRow(context: Context): String {
        return listOf(
            formattedDate(),
            dogName,
            LoadScenarioCode.displayName(context, loadScenario),
            pulse.toString(),
            respiration?.toString() ?: "",
            temperature?.let { "%.1f".format(it) } ?: "",
            capillaryRefillTime?.toString() ?: "",
            mucousMembraneColor?.let { MucousColorCode.displayName(context, it) } ?: "",
            consciousnessLevel?.let { ConsciousnessCode.displayName(context, it) } ?: "",
            notes.replace(";", ",").replace("\n", " ")
        ).joinToString(";")
    }

    // ═══════════════════════════════════════════════════════════
    // BEWERTUNG: Puls im Normalbereich?
    // ═══════════════════════════════════════════════════════════

    /**
     * Gibt zurück ob der Puls im Normalbereich liegt,
     * basierend auf Schulterhöhe (Gewicht als Proxy für Größe).
     * null = keine Beurteilung möglich (kein Profil verfügbar)
     */
    fun pulseStatus(shoulderHeightCm: Int?): PulseStatus {
        val (low, high) = when {
            shoulderHeightCm == null -> return PulseStatus.UNKNOWN
            shoulderHeightCm < 30   -> Pair(100, 160) // Kleinhund
            shoulderHeightCm < 50   -> Pair(70, 120)  // Mittelhund
            else                     -> Pair(60, 100)  // Großhund
        }
        return when {
            pulse < low  -> PulseStatus.LOW
            pulse > high -> PulseStatus.HIGH
            else         -> PulseStatus.NORMAL
        }
    }

    companion object {

        fun csvHeader(context: Context): String = listOf(
            context.getString(R.string.vital_csv_header_datetime),
            context.getString(R.string.vital_csv_header_dog),
            context.getString(R.string.vital_csv_header_load),
            context.getString(R.string.vital_csv_header_pulse),
            context.getString(R.string.vital_csv_header_respiration),
            context.getString(R.string.vital_csv_header_temperature),
            context.getString(R.string.vital_csv_header_crt),
            context.getString(R.string.vital_csv_header_mucous),
            context.getString(R.string.vital_csv_header_consciousness),
            context.getString(R.string.vital_csv_header_notes)
        ).joinToString(";")

        // ── Belastungsszenarien: stabile Codes für Anzeigereihenfolge ──
        fun loadScenarios(context: Context): List<String> =
            LoadScenarioCode.ALL.map { LoadScenarioCode.displayName(context, it) }

        fun loadScenarioRestKey(): String = LoadScenarioCode.REST

        // ── Schleimhautfarbe: stabile Codes für Anzeigereihenfolge ──
        fun mucousMembraneColors(context: Context): List<String> =
            MucousColorCode.ALL.map { MucousColorCode.displayName(context, it) }

        // ── Bewusstseinszustände: stabile Codes für Anzeigereihenfolge ──
        fun consciousnessLevels(context: Context): List<String> =
            ConsciousnessCode.ALL.map { ConsciousnessCode.displayName(context, it) }

        // ── Normalwerte-Referenz ──────────────────────────────────
        /**
         * Gibt Normalwert-Text für Puls zurück, je nach Schulterhöhe.
         */
        fun pulseReferenceText(context: Context, shoulderHeightCm: Int?): String {
            return when {
                shoulderHeightCm == null -> context.getString(R.string.vital_pulse_reference_unknown)
                shoulderHeightCm < 30   -> context.getString(R.string.vital_pulse_reference_small)
                shoulderHeightCm < 50   -> context.getString(R.string.vital_pulse_reference_medium)
                else                     -> context.getString(R.string.vital_pulse_reference_large)
            }
        }
    }
}

enum class PulseStatus {
    NORMAL, LOW, HIGH, UNKNOWN
}

/**
 * Stabile interne Codes für Belastungsszenario.
 * loadScenario in VitalRecord speichert IMMER einen dieser Codes,
 * niemals den übersetzten Anzeigetext.
 */
object LoadScenarioCode {
    const val REST = "rest"
    const val LIGHT_ACTIVITY = "light_activity"
    const val MODERATE_ACTIVITY = "moderate_activity"
    const val INTENSE_ACTIVITY = "intense_activity"
    const val RECOVERY = "recovery"
    const val EXCITEMENT_STRESS = "excitement_stress"
    const val VET_VISIT = "vet_visit"
    const val ILLNESS = "illness"

    val ALL = listOf(
        REST, LIGHT_ACTIVITY, MODERATE_ACTIVITY, INTENSE_ACTIVITY,
        RECOVERY, EXCITEMENT_STRESS, VET_VISIT, ILLNESS
    )

    fun displayName(context: Context, code: String): String = when (code) {
        REST -> context.getString(R.string.vital_load_rest)
        LIGHT_ACTIVITY -> context.getString(R.string.vital_load_light_activity)
        MODERATE_ACTIVITY -> context.getString(R.string.vital_load_moderate_activity)
        INTENSE_ACTIVITY -> context.getString(R.string.vital_load_intense_activity)
        RECOVERY -> context.getString(R.string.vital_load_recovery)
        EXCITEMENT_STRESS -> context.getString(R.string.vital_load_excitement_stress)
        VET_VISIT -> context.getString(R.string.vital_load_vet_visit)
        ILLNESS -> context.getString(R.string.vital_load_illness)
        else -> code
    }

    /** Migriert alte, direkt gespeicherte deutsche Anzeigetexte auf stabile Codes. */
    fun migrate(value: String): String = when (value) {
        "Ruhe", REST -> REST
        "Leichte Aktivität", LIGHT_ACTIVITY -> LIGHT_ACTIVITY
        "Moderate Aktivität", MODERATE_ACTIVITY -> MODERATE_ACTIVITY
        "Intensive Aktivität", INTENSE_ACTIVITY -> INTENSE_ACTIVITY
        "Erholung nach Aktivität", RECOVERY -> RECOVERY
        "Aufregung / Stress / Belastung", EXCITEMENT_STRESS -> EXCITEMENT_STRESS
        "Tierarztbesuch oder Ähnliches", VET_VISIT -> VET_VISIT
        "Erkrankung oder Unwohlsein", ILLNESS -> ILLNESS
        else -> value
    }
}

/**
 * Stabile interne Codes für Schleimhautfarbe.
 * mucousMembraneColor in VitalRecord speichert IMMER einen dieser Codes
 * (oder null), niemals den übersetzten Anzeigetext.
 */
object MucousColorCode {
    const val PINK = "pink"
    const val PALE_PINK = "pale_pink"
    const val WHITE = "white"
    const val BLUE_GRAY = "blue_gray"
    const val YELLOW = "yellow"
    const val BRICK_RED = "brick_red"

    val ALL = listOf(PINK, PALE_PINK, WHITE, BLUE_GRAY, YELLOW, BRICK_RED)

    fun displayName(context: Context, code: String): String = when (code) {
        PINK -> context.getString(R.string.vital_mucous_pink)
        PALE_PINK -> context.getString(R.string.vital_mucous_pale_pink)
        WHITE -> context.getString(R.string.vital_mucous_white)
        BLUE_GRAY -> context.getString(R.string.vital_mucous_blue_gray)
        YELLOW -> context.getString(R.string.vital_mucous_yellow)
        BRICK_RED -> context.getString(R.string.vital_mucous_brick_red)
        else -> code
    }

    /** Migriert alte, direkt gespeicherte deutsche Anzeigetexte auf stabile Codes. */
    fun migrate(value: String?): String? = when (value) {
        "Rosa (normal)", PINK -> PINK
        "Blass rosa", PALE_PINK -> PALE_PINK
        "Weiß / Sehr blass", WHITE -> WHITE
        "Blau / Grau (Zyanose)", BLUE_GRAY -> BLUE_GRAY
        "Gelb (Ikterus)", YELLOW -> YELLOW
        "Ziegelrot / Dunkelrot", BRICK_RED -> BRICK_RED
        else -> value
    }
}

/**
 * Stabile interne Codes für Bewusstseinszustand.
 * consciousnessLevel in VitalRecord speichert IMMER einen dieser Codes
 * (oder null), niemals den übersetzten Anzeigetext.
 */
object ConsciousnessCode {
    const val ALERT = "alert"
    const val DAMPENED = "dampened"
    const val SOMNOLENT = "somnolent"
    const val UNCONSCIOUS = "unconscious"

    val ALL = listOf(ALERT, DAMPENED, SOMNOLENT, UNCONSCIOUS)

    fun displayName(context: Context, code: String): String = when (code) {
        ALERT -> context.getString(R.string.vital_consciousness_alert)
        DAMPENED -> context.getString(R.string.vital_consciousness_dampened)
        SOMNOLENT -> context.getString(R.string.vital_consciousness_somnolent)
        UNCONSCIOUS -> context.getString(R.string.vital_consciousness_unconscious)
        else -> code
    }

    /** Migriert alte, direkt gespeicherte deutsche Anzeigetexte auf stabile Codes. */
    fun migrate(value: String?): String? = when (value) {
        "Alert (wach, reaktiv)", ALERT -> ALERT
        "Gedämpft (schläfrig, aber ansprechbar)", DAMPENED -> DAMPENED
        "Somnolent (schwer weckbar)", SOMNOLENT -> SOMNOLENT
        "Bewusstlos", UNCONSCIOUS -> UNCONSCIOUS
        else -> value
    }
}
