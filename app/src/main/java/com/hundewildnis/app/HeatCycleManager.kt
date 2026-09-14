package com.hundewildnis.app

import android.content.Context
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ════════════════════════════════════════════════════════════════════
// ZYKLUSMUSTER — erkanntes Reproduktionsverhalten der Hündin
// ════════════════════════════════════════════════════════════════════
enum class CyclePattern {
    UNKNOWN, ANNUAL, BIANNUAL, FREQUENT, IRREGULAR;

    fun label(context: android.content.Context): String = when (this) {
        UNKNOWN -> context.getString(R.string.cycle_pattern_unknown)
        ANNUAL -> context.getString(R.string.cycle_pattern_annual)
        BIANNUAL -> context.getString(R.string.cycle_pattern_biannual)
        FREQUENT -> context.getString(R.string.cycle_pattern_frequent)
        IRREGULAR -> context.getString(R.string.cycle_pattern_irregular)
    }

    fun descriptionShort(context: android.content.Context): String = when (this) {
        UNKNOWN -> context.getString(R.string.cycle_pattern_unknown_desc)
        ANNUAL -> context.getString(R.string.cycle_pattern_annual_desc)
        BIANNUAL -> context.getString(R.string.cycle_pattern_biannual_desc)
        FREQUENT -> context.getString(R.string.cycle_pattern_frequent_desc)
        IRREGULAR -> context.getString(R.string.cycle_pattern_irregular_desc)
    }
}

data class CycleAnalysis(
    val averageDays: Int,               // berechnete Ø-Zykluslänge in Tagen
    val pattern: CyclePattern,          // erkanntes Muster
    val zwischenlaeufe: Int,            // Anzahl erkannter Zwischenläufigkeiten
    val confidence: Int                 // 0–100 — Verlässlichkeit der Berechnung
)

object HeatCycleManager {

    private val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

    // ── Schwellwerte ─────────────────────────────────────────────────
    /** Abstand < dieser Wert → Zwischenläufigkeit, nicht als echter Zyklus gezählt */
    private const val ZWISCHENL_DAYS = 60

    /** Abstand > dieser Wert → Jahres-Zyklus */
    private const val ANNUAL_THRESHOLD = 300

    /** Abstand < dieser Wert → häufiger Zyklus */
    private const val FREQUENT_THRESHOLD = 120

    /** Fallback wenn keine Daten vorhanden */
    private const val DEFAULT_CYCLE_DAYS = 180L

    /** Reminder-Vorlaufzeit in Tagen */
    private const val REMINDER_DAYS_BEFORE = 30

    // ════════════════════════════════════════════════════════════════
    // ZYKLUSNUMMER
    // ════════════════════════════════════════════════════════════════

    fun nextCycleNumber(cycles: List<HeatCycleEntry>): Int =
        (cycles.maxOfOrNull { it.cycleNumber } ?: 0) + 1

    // ════════════════════════════════════════════════════════════════
    // KERN-ANALYSE
    // Einzige Stelle wo aus den Rohdaten ein Ø berechnet wird.
    // ════════════════════════════════════════════════════════════════

    /**
     * Analysiert die Zyklushistorie und gibt ein CycleAnalysis-Objekt zurück.
     *
     * Algorithmus:
     * 1. Alle Startdaten sortieren
     * 2. Abstände berechnen
     * 3. Zwischenläufigkeiten (< 60 Tage) herausfiltern und zählen
     * 4. Muster erkennen (annual / biannual / frequent / irregular)
     * 5. Ø mit Gewichtung neuerer Zyklen berechnen (exponentiell)
     * 6. Konfidenz aus Datenmenge ableiten
     */
    fun analyze(cycles: List<HeatCycleEntry>): CycleAnalysis {
        val sortedDates = cycles
            .filter { it.startDate.isNotEmpty() }
            .mapNotNull { runCatching { sdf.parse(it.startDate) }.getOrNull() }
            .sortedBy { it.time }

        // ── Weniger als 2 Startdaten → keine Berechnung möglich ──────
        if (sortedDates.size < 2) {
            return CycleAnalysis(
                averageDays  = DEFAULT_CYCLE_DAYS.toInt(),
                pattern      = CyclePattern.UNKNOWN,
                zwischenlaeufe = 0,
                confidence   = 0
            )
        }

        // ── Alle Abstände ─────────────────────────────────────────────
        val allGaps = (1 until sortedDates.size).map { i ->
            ((sortedDates[i].time - sortedDates[i - 1].time) / (1000L * 60 * 60 * 24)).toInt()
        }

        val zwischenlaeufe = allGaps.count { it < ZWISCHENL_DAYS }
        val significantGaps = allGaps.filter { it >= ZWISCHENL_DAYS }

        if (significantGaps.isEmpty()) {
            return CycleAnalysis(
                averageDays  = DEFAULT_CYCLE_DAYS.toInt(),
                pattern      = CyclePattern.UNKNOWN,
                zwischenlaeufe = zwischenlaeufe,
                confidence   = 0
            )
        }

        // ── Muster erkennen ───────────────────────────────────────────
        val median = median(significantGaps)
        val stdDev = stdDev(significantGaps)
        val coefficientOfVariation = if (median > 0) stdDev / median else 1.0

        val pattern = when {
            significantGaps.size < 2                -> CyclePattern.UNKNOWN
            median > ANNUAL_THRESHOLD               -> CyclePattern.ANNUAL
            median < FREQUENT_THRESHOLD             -> CyclePattern.FREQUENT
            coefficientOfVariation > 0.35           -> CyclePattern.IRREGULAR
            else                                    -> CyclePattern.BIANNUAL
        }

        // ── Gewichteten Durchschnitt berechnen ────────────────────────
        // Neuere Zyklen zählen mehr (Gewicht = Position + 1)
        val weightedAvg = weightedAverage(significantGaps)

        // ── Für irreguläre Muster: Median robuster als Mittelwert ─────
        val finalAvg = when (pattern) {
            CyclePattern.IRREGULAR -> median
            CyclePattern.UNKNOWN   -> DEFAULT_CYCLE_DAYS.toInt()
            else                   -> weightedAvg
        }

        // ── Konfidenz ─────────────────────────────────────────────────
        // Steigt mit Anzahl signifikanter Abstände, sinkt bei hoher Varianz
        val baseConfidence = minOf(significantGaps.size * 20, 80)
        val variancePenalty = (coefficientOfVariation * 30).toInt().coerceAtMost(40)
        val confidence = (baseConfidence - variancePenalty).coerceIn(0, 100)

        return CycleAnalysis(
            averageDays    = finalAvg.coerceAtLeast(30),
            pattern        = pattern,
            zwischenlaeufe = zwischenlaeufe,
            confidence     = confidence
        )
    }

    // ════════════════════════════════════════════════════════════════
    // ÖFFENTLICHE BERECHNUNGS-API (bleibt abwärtskompatibel)
    // ════════════════════════════════════════════════════════════════

    /**
     * Ø Zykluslänge in Tagen.
     * Gibt null zurück wenn nicht genug Daten vorhanden.
     */
    fun averageCycleLengthDays(cycles: List<HeatCycleEntry>): Int? {
        val analysis = analyze(cycles)
        return if (analysis.pattern == CyclePattern.UNKNOWN) null else analysis.averageDays
    }

    /**
     * Voraussichtliches Datum der nächsten Läufigkeit.
     */
    fun predictedNextDate(cycles: List<HeatCycleEntry>): Date? {
        val lastStart = cycles
            .filter { it.startDate.isNotEmpty() }
            .mapNotNull { runCatching { sdf.parse(it.startDate) }.getOrNull() }
            .maxByOrNull { it.time } ?: return null

        val cycleDays = averageCycleLengthDays(cycles)?.toLong() ?: DEFAULT_CYCLE_DAYS
        return Date(lastStart.time + cycleDays * 24L * 60 * 60 * 1000)
    }

    fun predictedNextDateFormatted(cycles: List<HeatCycleEntry>): String =
        predictedNextDate(cycles)?.let { sdf.format(it) } ?: "Noch keine Daten"

    /**
     * Tage bis zur nächsten Läufigkeit.
     * Negativ = überfällig. null = nicht berechenbar.
     */
    fun daysUntilNext(cycles: List<HeatCycleEntry>): Long? {
        val next = predictedNextDate(cycles) ?: return null
        return (next.time - todayMidnight().time) / (1000L * 60 * 60 * 24)
    }

    /** Countdown-Label für die UI */
    fun countdownLabel(cycles: List<HeatCycleEntry>): String {
        val days = daysUntilNext(cycles) ?: return "Keine Daten"
        return when {
            days < 0   -> "Vor ${-days} Tagen erwartet"
            days == 0L -> "Heute erwartet"
            days <= 14 -> "In $days Tagen"
            else       -> "In $days Tagen (${predictedNextDateFormatted(cycles)})"
        }
    }

    /**
     * Kompakter Analyse-Text für Anzeige in der UI.
     * Z.B. "Ø 183 Tage · Halbjährlich · ⚠️ 1 Zwischenläufigkeit"
     */
    fun analysisLabel(context: android.content.Context, cycles: List<HeatCycleEntry>): String {
        val a = analyze(cycles)
        return buildString {
            if (a.pattern == CyclePattern.UNKNOWN) {
                append(context.getString(R.string.emergency_heat_cycle_not_yet_calculable))
                return@buildString
            }
            append(context.getString(R.string.heat_cycle_analysis_label, a.averageDays, a.pattern.descriptionShort(context)))
            if (a.zwischenlaeufe > 0) {
                append(
                    if (a.zwischenlaeufe > 1)
                        context.getString(R.string.heat_cycle_zwischenlaeufe_plural, a.zwischenlaeufe)
                    else
                        context.getString(R.string.heat_cycle_zwischenlaeufe_singular, a.zwischenlaeufe)
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // WORKMANAGER REMINDER
    // ════════════════════════════════════════════════════════════════

    fun scheduleReminder(
        context: Context,
        dogId: String,
        dogName: String,
        cycles: List<HeatCycleEntry>
    ) {
        val workManager = WorkManager.getInstance(context)
        val tag = reminderTag(dogId)
        workManager.cancelAllWorkByTag(tag)

        val daysUntil = daysUntilNext(cycles) ?: return
        val delayDays = daysUntil - REMINDER_DAYS_BEFORE
        if (delayDays <= 0) return

        val inputData = workDataOf(
            HeatCycleReminderWorker.KEY_DOG_ID      to dogId,
            HeatCycleReminderWorker.KEY_DOG_NAME    to dogName,
            HeatCycleReminderWorker.KEY_PREDICTED   to predictedNextDateFormatted(cycles)
        )

        val request = OneTimeWorkRequestBuilder<HeatCycleReminderWorker>()
            .setInitialDelay(delayDays, TimeUnit.DAYS)
            .setInputData(inputData)
            .addTag(tag)
            .build()

        workManager.enqueue(request)
    }

    fun cancelReminder(context: Context, dogId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(reminderTag(dogId))
    }

    // ════════════════════════════════════════════════════════════════
    // MATH-HILFSFUNKTIONEN
    // ════════════════════════════════════════════════════════════════

    private fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 0)
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        else
            sorted[sorted.size / 2]
    }

    private fun stdDev(values: List<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return Math.sqrt(variance)
    }

    /** Gewichteter Durchschnitt — neuere Abstände (höherer Index) zählen mehr */
    private fun weightedAverage(values: List<Int>): Int {
        if (values.isEmpty()) return DEFAULT_CYCLE_DAYS.toInt()
        val weights = values.indices.map { (it + 1).toDouble() }
        val weightedSum = values.zip(weights).sumOf { (v, w) -> v * w }
        return (weightedSum / weights.sum()).toInt()
    }

    private fun reminderTag(dogId: String) = "heat_reminder_$dogId"

    private fun todayMidnight(): Date = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
    }.time
}
