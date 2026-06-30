package com.hundewildnis.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════
// AKTIVITÄTSKALENDER & TRAININGSPLANUNG
// Datei: ActivityCalendarModels.kt
//
// Vorausplanung von Trainingseinheiten mit wetterbasierter
// Eignungsampel. Nutzt ausschließlich bereits vorhandene Daten:
// TrainingManager (Fortschritt), TrainingRecommendationEngine
// (Empfehlungslogik) und einen eigenständigen 7-Tage-Wetterabruf
// (Open-Meteo, identisches Muster wie WeatherActivity).
// ════════════════════════════════════════════════════════════════

// ── Geplante Trainingseinheit ────────────────────────────────────
@Parcelize
data class PlannedSession(
    val id: String          = UUID.randomUUID().toString(),
    val dogId: String       = "",
    val dogName: String     = "",
    val categoryId: Int     = 13,
    val plannedDateMillis: Long = 0L,   // Tagesbeginn (00:00) des geplanten Tages
    val plannedDurationMinutes: Int = 15,
    val note: String        = "",
    val reminderEnabled: Boolean = true,
    val completed: Boolean  = false,    // wird true wenn an dem Tag tatsächlich eine Session existiert
    val createdAt: Long     = System.currentTimeMillis()
) : Parcelable {

    fun category(): TrainingCategory =
        TrainingCategory.fromId(categoryId) ?: TrainingCategory.LEINENARBEIT

    fun formattedDate(): String =
        SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(plannedDateMillis))

    fun isToday(): Boolean = isSameDay(plannedDateMillis, System.currentTimeMillis())
    fun isPast(): Boolean  = plannedDateMillis < startOfDay(System.currentTimeMillis())

    companion object {
        fun startOfDay(millis: Long): Long =
            Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        fun isSameDay(a: Long, b: Long): Boolean {
            val ca = Calendar.getInstance().apply { timeInMillis = a }
            val cb = Calendar.getInstance().apply { timeInMillis = b }
            return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                   ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
        }
    }
}

// ── Wetter-Eignung pro Tag ────────────────────────────────────────
enum class DayWeatherSuitability(val icon: String, val label: String, val color: String) {
    IDEAL("🟢", "Ideal", "#4CAF50"),
    CONDITIONAL("🟡", "Bedingt", "#FF9800"),
    NOT_RECOMMENDED("🔴", "Nicht empfohlen", "#E53935"),
    UNKNOWN("⚪", "Unbekannt", "#777777")
}

// ── Vorhersage-Eintrag für einen einzelnen Tag (eigenständig, leichtgewichtig) ──
data class CalendarDayForecast(
    val dateMillis: Long,
    val dayLabel: String,        // "Mo", "Di", ...
    val dateLabel: String,       // "16.06."
    val weatherCode: Int,
    val tempMax: Double,
    val precipProbability: Int,
    val windSpeedMax: Double
) {
    fun suitability(): DayWeatherSuitability {
        val isThunder = weatherCode in 95..99
        val isHeavyRain = weatherCode in 65..67 || weatherCode == 82
        val isExtreme = tempMax >= 32.0 || windSpeedMax >= 50.0

        return when {
            isThunder || isExtreme -> DayWeatherSuitability.NOT_RECOMMENDED
            isHeavyRain || precipProbability >= 70 || tempMax >= 28.0 || windSpeedMax >= 35.0 ->
                DayWeatherSuitability.CONDITIONAL
            precipProbability >= 40 -> DayWeatherSuitability.CONDITIONAL
            else -> DayWeatherSuitability.IDEAL
        }
    }
}

// ── Lückenanalyse: vernachlässigte Kategorien ────────────────────
data class NeglectedCategory(
    val category: TrainingCategory,
    val daysSinceLastSession: Int?,   // null = noch nie trainiert
    val progress: TrainingProgress
)

// ════════════════════════════════════════════════════════════════
// PLANUNGS-/EMPFEHLUNGSLOGIK
// ════════════════════════════════════════════════════════════════
object ActivityCalendarEngine {

    /**
     * Ermittelt für einen Hund alle Kategorien, die in den letzten
     * [thresholdDays] Tagen nicht trainiert wurden – sortiert nach
     * Dringlichkeit (am längsten vernachlässigt zuerst).
     */
    fun findNeglectedCategories(
        dogId: String,
        allSessions: List<TrainingSession>,
        allProgress: List<TrainingProgress>,
        thresholdDays: Int = 14
    ): List<NeglectedCategory> {
        val now = System.currentTimeMillis()
        val msPerDay = 24L * 60 * 60 * 1000

        return TrainingCategory.values().mapNotNull { category ->
            val sessions = allSessions.filter { it.dogId == dogId && it.category == category }
            val lastSession = sessions.maxByOrNull { it.date }
            val daysSince = lastSession?.let { ((now - it.date) / msPerDay).toInt() }

            if (daysSince == null || daysSince >= thresholdDays) {
                val progress = allProgress.firstOrNull {
                    it.dogId == dogId && it.category == category
                } ?: TrainingProgress(dogId = dogId, category = category)
                NeglectedCategory(category, daysSince, progress)
            } else null
        }.sortedByDescending { it.daysSinceLastSession ?: Int.MAX_VALUE }
    }

    /**
     * Schlägt die nächste sinnvolle Kategorie für eine Trainingsplanung vor,
     * basierend auf Lückenanalyse und niedrigstem Fortschritt.
     */
    fun suggestNextCategory(
        dogId: String,
        allSessions: List<TrainingSession>,
        allProgress: List<TrainingProgress>
    ): TrainingCategory {
        val neglected = findNeglectedCategories(dogId, allSessions, allProgress, thresholdDays = 7)
        neglected.firstOrNull()?.let { return it.category }

        val lowestProgress = allProgress
            .filter { it.dogId == dogId }
            .minByOrNull { it.progressPercentage }

        return lowestProgress?.category ?: TrainingCategory.LEINENARBEIT
    }

    /**
     * Empfohlene Trainingsdauer für eine Planung – nutzt die bestehende
     * TrainingRecommendationEngine mit dem aktuellen Fortschritt.
     */
    fun suggestDuration(
        progress: TrainingProgress,
        recentSessions: List<TrainingSession>,
        character: DogCharacter?
    ): Int {
        val rec = TrainingRecommendationEngine.generateRecommendation(
            progress, recentSessions, character
        )
        return rec.suggestedDuration
    }
}
