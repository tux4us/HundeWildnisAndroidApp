package com.hundewildnis.app

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
     * Werte: "Rosa" | "Blass" | "Weiß" | "Blau/Grau" | "Gelb" | "Ziegelrot"
     */
    val mucousMembraneColor: String? = null,

    /**
     * Bewusstseinszustand — optional
     * Werte: "Alert" | "Gedämpft" | "Somnolent" | "Bewusstlos"
     */
    val consciousnessLevel: String? = null,

    // ═══════════════════════════════════════════════════════════
    // KONTEXT
    // ═══════════════════════════════════════════════════════════

    /**
     * Belastungsszenario zum Zeitpunkt der Messung.
     * Werte aus LOAD_SCENARIOS.
     */
    val loadScenario: String = "Ruhe",

    /** Freitext: aktuelle Bedingungen und Beobachtungen */
    val notes: String = "",

    /** true wenn diese Messung in eine Notfallmeldung übernommen wurde */
    val transferredToEmergency: Boolean = false

) : Parcelable {

    // ═══════════════════════════════════════════════════════════
    // FORMATIERTE ANZEIGE
    // ═══════════════════════════════════════════════════════════

    fun formattedDate(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
        return sdf.format(Date(timestamp))
    }

    fun formattedDateShort(): String {
        val sdf = SimpleDateFormat("dd.MM.yy", Locale.GERMANY)
        return sdf.format(Date(timestamp))
    }

    fun summaryLine(): String {
        val parts = mutableListOf<String>()
        parts.add("Puls: $pulse /min")
        respiration?.let { parts.add("Atem: $it /min") }
        temperature?.let { parts.add("Temp: ${"%.1f".format(it)} °C") }
        capillaryRefillTime?.let { parts.add("KFZ: ${it}s") }
        mucousMembraneColor?.let { parts.add("SH: $it") }
        consciousnessLevel?.let { parts.add("BW: $it") }
        return parts.joinToString(" · ")
    }

    /** Vollständiger Teilen-Text (Share Sheet) */
    fun toShareText(): String {
        val sb = StringBuilder()
        sb.appendLine("🐕 Vitalwerte: $dogName")
        sb.appendLine("📅 ${formattedDate()}")
        sb.appendLine("⚡ Belastung: $loadScenario")
        sb.appendLine()
        sb.appendLine("❤️ Herzfrequenz: $pulse /min")
        respiration?.let { sb.appendLine("🫁 Atemfrequenz: $it /min") }
        temperature?.let { sb.appendLine("🌡️ Temperatur: ${"%.1f".format(it)} °C") }
        capillaryRefillTime?.let { sb.appendLine("💉 Kapillarfüllzeit: ${it}s") }
        mucousMembraneColor?.let { sb.appendLine("👁️ Schleimhautfarbe: $it") }
        consciousnessLevel?.let { sb.appendLine("🧠 Bewusstseinszustand: $it") }
        if (notes.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("📝 Notizen: $notes")
        }
        return sb.toString().trim()
    }

    /** CSV-Zeile (Semikolon-getrennt) */
    fun toCsvRow(): String {
        return listOf(
            formattedDate(),
            dogName,
            loadScenario,
            pulse.toString(),
            respiration?.toString() ?: "",
            temperature?.let { "%.1f".format(it) } ?: "",
            capillaryRefillTime?.toString() ?: "",
            mucousMembraneColor ?: "",
            consciousnessLevel ?: "",
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

        val CSV_HEADER = listOf(
            "Datum/Uhrzeit", "Hund", "Belastung", "Puls (/min)",
            "Atemfrequenz (/min)", "Temperatur (°C)", "Kapillarfüllzeit (s)",
            "Schleimhautfarbe", "Bewusstsein", "Notizen"
        ).joinToString(";")

        // ── Belastungsszenarien ──────────────────────────────────
        val LOAD_SCENARIOS = listOf(
            "Ruhe",
            "Leichte Aktivität",
            "Moderate Aktivität",
            "Intensive Aktivität",
            "Erholung nach Aktivität",
            "Aufregung / Stress / Belastung",
            "Tierarztbesuch oder Ähnliches",
            "Erkrankung oder Unwohlsein"
        )

        // ── Schleimhautfarbe ─────────────────────────────────────
        val MUCOUS_MEMBRANE_COLORS = listOf(
            "Rosa (normal)",
            "Blass rosa",
            "Weiß / Sehr blass",
            "Blau / Grau (Zyanose)",
            "Gelb (Ikterus)",
            "Ziegelrot / Dunkelrot"
        )

        // ── Bewusstseinszustände ─────────────────────────────────
        val CONSCIOUSNESS_LEVELS = listOf(
            "Alert (wach, reaktiv)",
            "Gedämpft (schläfrig, aber ansprechbar)",
            "Somnolent (schwer weckbar)",
            "Bewusstlos"
        )

        // ── Normalwerte-Referenz ──────────────────────────────────
        /**
         * Gibt Normalwert-Text für Puls zurück, je nach Schulterhöhe.
         */
        fun pulseReferenceText(shoulderHeightCm: Int?): String {
            return when {
                shoulderHeightCm == null -> "Normalwert: ca. 60–160 /min (je nach Größe)"
                shoulderHeightCm < 30   -> "Normalwert Kleinhund (< 30 cm): 100–160 /min"
                shoulderHeightCm < 50   -> "Normalwert Mittelhund (30–50 cm): 70–120 /min"
                else                     -> "Normalwert Großhund (> 50 cm): 60–100 /min"
            }
        }
    }
}

enum class PulseStatus {
    NORMAL, LOW, HIGH, UNKNOWN
}
