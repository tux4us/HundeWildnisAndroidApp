package com.hundewildnis.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

// ════════════════════════════════════════════════════════════════
// TRAININGSKATEGORIEN - Die 18 ausgewählten Kurse
// ════════════════════════════════════════════════════════════════

enum class TrainingCategory(val id: Int) {
    LEINENARBEIT(13),
    FUSS(15),
    SITZ(16),
    PLATZ(17),
    STEH(18),
    SOZIALISIERUNG(19),
    LANGLEINE(20),
    DISTANZUNTERBRECHUNG(21),
    FREILAUF(22),
    FREIFOLGE(23),
    TEAMWALK(24),
    SCOUTTECHNIKEN(25),
    JAGDVERMEIDUNG(26),
    NASENARBEIT(27),
    LEINENFUEHRIGKEIT(28),
    BEGEGNUNGEN(29),
    STRESSTOLERANZ(30),
    STRASSENROUTINEN(31);

    fun displayName(context: android.content.Context): String = when (this) {
        LEINENARBEIT -> context.getString(R.string.training_category_leinenarbeit)
        FUSS -> context.getString(R.string.training_category_fuss)
        SITZ -> context.getString(R.string.training_category_sitz)
        PLATZ -> context.getString(R.string.training_category_platz)
        STEH -> context.getString(R.string.training_category_steh)
        SOZIALISIERUNG -> context.getString(R.string.training_category_sozialisierung)
        LANGLEINE -> context.getString(R.string.training_category_langleine)
        DISTANZUNTERBRECHUNG -> context.getString(R.string.training_category_distanzunterbrechung)
        FREILAUF -> context.getString(R.string.training_category_freilauf)
        FREIFOLGE -> context.getString(R.string.training_category_freifolge)
        TEAMWALK -> context.getString(R.string.training_category_teamwalk)
        SCOUTTECHNIKEN -> context.getString(R.string.training_category_scouttechniken)
        JAGDVERMEIDUNG -> context.getString(R.string.training_category_jagdvermeidung)
        NASENARBEIT -> context.getString(R.string.training_category_nasenarbeit)
        LEINENFUEHRIGKEIT -> context.getString(R.string.training_category_leinenfuehrigkeit)
        BEGEGNUNGEN -> context.getString(R.string.training_category_begegnungen)
        STRESSTOLERANZ -> context.getString(R.string.training_category_stresstoleranz)
        STRASSENROUTINEN -> context.getString(R.string.training_category_strassenroutinen)
    }

    fun description(context: android.content.Context): String = when (this) {
        LEINENARBEIT -> context.getString(R.string.training_category_leinenarbeit_desc)
        FUSS -> context.getString(R.string.training_category_fuss_desc)
        SITZ -> context.getString(R.string.training_category_sitz_desc)
        PLATZ -> context.getString(R.string.training_category_platz_desc)
        STEH -> context.getString(R.string.training_category_steh_desc)
        SOZIALISIERUNG -> context.getString(R.string.training_category_sozialisierung_desc)
        LANGLEINE -> context.getString(R.string.training_category_langleine_desc)
        DISTANZUNTERBRECHUNG -> context.getString(R.string.training_category_distanzunterbrechung_desc)
        FREILAUF -> context.getString(R.string.training_category_freilauf_desc)
        FREIFOLGE -> context.getString(R.string.training_category_freifolge_desc)
        TEAMWALK -> context.getString(R.string.training_category_teamwalk_desc)
        SCOUTTECHNIKEN -> context.getString(R.string.training_category_scouttechniken_desc)
        JAGDVERMEIDUNG -> context.getString(R.string.training_category_jagdvermeidung_desc)
        NASENARBEIT -> context.getString(R.string.training_category_nasenarbeit_desc)
        LEINENFUEHRIGKEIT -> context.getString(R.string.training_category_leinenfuehrigkeit_desc)
        BEGEGNUNGEN -> context.getString(R.string.training_category_begegnungen_desc)
        STRESSTOLERANZ -> context.getString(R.string.training_category_stresstoleranz_desc)
        STRASSENROUTINEN -> context.getString(R.string.training_category_strassenroutinen_desc)
    }

    companion object {
        fun fromId(id: Int): TrainingCategory? = values().find { it.id == id }
    }
}

// ════════════════════════════════════════════════════════════════
// TRAINING SESSION ENUMS
// Diese in TrainingModels.kt einfügen (NACH TrainingCategory)!
// ════════════════════════════════════════════════════════════════

/**
 * Ablenkungslevel während des Trainings
 */
@Parcelize
enum class DistractionLevel : Parcelable {
    NONE, LOW, MEDIUM, HIGH;

    fun displayName(context: android.content.Context): String = when (this) {
        NONE -> context.getString(R.string.distraction_level_none)
        LOW -> context.getString(R.string.distraction_level_low)
        MEDIUM -> context.getString(R.string.distraction_level_medium)
        HIGH -> context.getString(R.string.distraction_level_high)
    }

    companion object {
        fun fromDisplayName(context: android.content.Context, text: String): DistractionLevel? =
            values().find { it.displayName(context) == text }
    }
}

/**
 * Schwierigkeitsgrad der Trainingseinheit
 */
@Parcelize
enum class DifficultyLevel : Parcelable {
    EASY, NORMAL, HARD;

    fun displayName(context: android.content.Context): String = when (this) {
        EASY -> context.getString(R.string.difficulty_level_easy)
        NORMAL -> context.getString(R.string.difficulty_level_normal)
        HARD -> context.getString(R.string.difficulty_level_hard)
    }

    companion object {
        fun fromDisplayName(context: android.content.Context, text: String): DifficultyLevel? =
            values().find { it.displayName(context) == text }
    }
}

/**
 * Messungstyp für Trainingseinheiten
 */
enum class MeasurementType {
    TIME, REPETITIONS;

    fun displayName(context: android.content.Context): String = when (this) {
        TIME -> context.getString(R.string.measurement_type_time)
        REPETITIONS -> context.getString(R.string.measurement_type_repetitions)
    }

    companion object {
        fun fromString(value: String): MeasurementType {
            return values().find { it.name == value } ?: TIME
        }
    }
}

/**
 * Wetterbedingungen (optional)
 */
@Parcelize
enum class WeatherCondition : Parcelable {
    SUNNY, CLOUDY, RAINY, SNOWY, WINDY, FOGGY, STORMY;

    fun displayName(context: android.content.Context): String = when (this) {
        SUNNY -> context.getString(R.string.weather_condition_sunny)
        CLOUDY -> context.getString(R.string.weather_condition_cloudy)
        RAINY -> context.getString(R.string.weather_condition_rainy)
        SNOWY -> context.getString(R.string.weather_condition_snowy)
        WINDY -> context.getString(R.string.weather_condition_windy)
        FOGGY -> context.getString(R.string.weather_condition_foggy)
        STORMY -> context.getString(R.string.weather_condition_stormy)
    }

    companion object {
        fun fromDisplayName(context: android.content.Context, text: String): WeatherCondition? =
            values().find { it.displayName(context) == text }
    }
}

/**
 * Trainingsort (optional)
 */
@Parcelize
enum class LocationType : Parcelable {
    HOME, GARDEN, PARK, FOREST, FIELD, CITY, INDOOR_HALL, DOG_SCHOOL, OTHER;

    fun displayName(context: android.content.Context): String = when (this) {
        HOME -> context.getString(R.string.location_type_home)
        GARDEN -> context.getString(R.string.location_type_garden)
        PARK -> context.getString(R.string.location_type_park)
        FOREST -> context.getString(R.string.location_type_forest)
        FIELD -> context.getString(R.string.location_type_field)
        CITY -> context.getString(R.string.location_type_city)
        INDOOR_HALL -> context.getString(R.string.location_type_indoor_hall)
        DOG_SCHOOL -> context.getString(R.string.location_type_dog_school)
        OTHER -> context.getString(R.string.location_type_other)
    }

    companion object {
        fun fromDisplayName(context: android.content.Context, text: String): LocationType? =
            values().find { it.displayName(context) == text }
    }
}

// ════════════════════════════════════════════════════════════════
// HUNDECHARAKTER - Wesensmerkmale (für Stammdaten-Erweiterung)
// ════════════════════════════════════════════════════════════════

/**
 * Hundecharakter mit 6 Wesensmerkmalen
 * Jedes Merkmal: 1-5 (1 = niedrig, 5 = hoch)
 */
@Parcelize
data class DogCharacter(
    val temperament: Int = 3,           // 1=Sehr ruhig, 5=Sehr lebhaft
    val selfConfidence: Int = 3,        // 1=Ängstlich, 5=Selbstsicher
    val socialBehavior: Int = 3,        // 1=Zurückhaltend, 5=Kontaktfreudig
    val excitability: Int = 3,          // 1=Gelassen, 5=Leicht erregbar
    val learningSpeed: Int = 3,         // 1=Langsam, 5=Schnell
    val frustrationTolerance: Int = 3   // 1=Niedrig, 5=Hoch
) : Parcelable {

    companion object {
        /**
         * Standard-Charakter (ausgeglichen)
         */
        fun default(): DogCharacter {
            return DogCharacter(
                temperament = 3,
                selfConfidence = 3,
                socialBehavior = 3,
                excitability = 3,
                learningSpeed = 3,
                frustrationTolerance = 3
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
// TRAININGSEINHEIT - Einzelne Trainings-Session
// ════════════════════════════════════════════════════════════════

@Parcelize
data class TrainingSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val dogId: String = "",
    val category: TrainingCategory = TrainingCategory.LEINENARBEIT,
    val date: Long = System.currentTimeMillis(),

    // Core Training Data
    val measurementType: MeasurementType = MeasurementType.TIME,  // ← NEU!
    val durationMinutes: Int? = null,  // ← Nullable gemacht!
    val repetitions: Int? = null,       // ← NEU!
    val successRating: Int = 0,
    val distractionLevel: DistractionLevel = DistractionLevel.NONE,
    val difficulty: DifficultyLevel = DifficultyLevel.NORMAL,

    // Optional Details
    val notes: String = "",
    val weatherCondition: WeatherCondition? = null,
    val location: LocationType? = null,

    // ✅ NEU: Wetter-Integration
    val weatherData: String = "",  // "Sonnig, 18°C" oder JSON von WeatherActivity

    // ✅ NEU: GPS-Integration
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsLocationName: String = ""  // "Stadtpark, Frankfurt" oder ähnlich

) : Parcelable {

    /**
     * ✅ NEU: OpenStreetMap Link generieren
     */
    fun getOpenStreetMapLink(): String? {
        return if (gpsLatitude != null && gpsLongitude != null) {
            "https://www.openstreetmap.org/?mlat=$gpsLatitude&mlon=$gpsLongitude#map=15/$gpsLatitude/$gpsLongitude"
        } else null
    }

    /**
     * ✅ NEU: GPS Kurzformat für Anzeige
     */
    fun getGpsShortFormat(): String {
        return if (gpsLatitude != null && gpsLongitude != null) {
            String.format("%.4f, %.4f", gpsLatitude, gpsLongitude)
        } else ""
    }

    /**
     * Formatiertes Datum
     */
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMAN)
        return sdf.format(java.util.Date(date))
    }

    /**
     * Erfolgs-Icon
     */
    fun getSuccessIcon(): String {
        return when (successRating) {
            1 -> "🔴"
            2 -> "🟡"
            3 -> "🟢"
            4 -> "🟢🟢"
            else -> "⚪"
        }
    }

    /**
     * Erfolgs-Text
     */
    fun getSuccessText(): String {
        return when (successRating) {
            1 -> "Schwierigkeiten"
            2 -> "Teilweise"
            3 -> "Erfolgreich"
            4 -> "Sehr gut"
            else -> "Unbekannt"
        }
    }

    // ✅ NEU: Diese 2 Funktionen hinzufügen
    fun getEffortValue(): Int {
        return when (measurementType) {
            MeasurementType.TIME -> durationMinutes ?: 0
            MeasurementType.REPETITIONS -> repetitions ?: 0
        }
    }

    fun getEffortText(): String {
        return when (measurementType) {
            MeasurementType.TIME -> "${durationMinutes ?: 0} Min"
            MeasurementType.REPETITIONS -> "${repetitions ?: 0}x"
        }
    }

}

// ════════════════════════════════════════════════════════════════
// FORTSCHRITT - Trainingsstand pro Kategorie und Hund
// ════════════════════════════════════════════════════════════════

@Parcelize
data class TrainingProgress(
    val dogId: String = "",
    val category: TrainingCategory = TrainingCategory.LEINENARBEIT,

    // Fortschritt
    val progressPercentage: Int = 0,  // 0-100
    val currentLevel: TrainingLevel = TrainingLevel.BEGINNER,

    // Statistiken
    val totalSessions: Int = 0,
    val totalMinutes: Int = 0,
    val totalRepetitions: Int = 0,  // ← NEU!
    val averageSuccess: Double = 0.0,  // 1.0-4.0

    // Streak & Regelmäßigkeit
    val consecutiveDays: Int = 0,
    val lastTrainingDate: Long = 0,

    // Trend
    val recentImprovement: Double = 0.0  // Prozent, kann negativ sein

) : Parcelable {

    /**
     * Aktualisiert Progress mit neuer Session
     */
    fun updateWithSession(session: TrainingSession): TrainingProgress {
        val newTotalSessions = totalSessions + 1

        // ✅ Nur Minuten/Wiederholungen addieren basierend auf Typ
        var newTotalMinutes = totalMinutes
        var newTotalRepetitions = totalRepetitions

        if (session.measurementType == MeasurementType.TIME) {
            newTotalMinutes += (session.durationMinutes ?: 0)
        } else {
            newTotalRepetitions += (session.repetitions ?: 0)
        }

        // Neue durchschnittliche Erfolgsrate
        val newAverageSuccess = ((averageSuccess * totalSessions) + session.successRating) / newTotalSessions

        // Streak berechnen
        val newConsecutiveDays = calculateStreak(session.date)

        // ✅ HIER: Effort berechnen und Progress berechnen
        val effort = session.getEffortValue()  // Nutzt die Helper-Funktion!
        val newProgressPercentage = calculateProgressPercentage(
            sessions = newTotalSessions,
            avgSuccess = newAverageSuccess,
            effort = newTotalMinutes + newTotalRepetitions + effort // ✅ Mix aus beidem für Score
        )

        // Level basierend auf Fortschritt
        val newLevel = TrainingLevel.fromProgress(newProgressPercentage)

        // Trend berechnen
        val newImprovement = calculateImprovement(session)

        return copy(
            totalSessions = newTotalSessions,
            totalMinutes = newTotalMinutes,
            totalRepetitions = newTotalRepetitions,
            averageSuccess = newAverageSuccess,
            progressPercentage = newProgressPercentage,
            currentLevel = newLevel,
            consecutiveDays = newConsecutiveDays,
            lastTrainingDate = session.date,
            recentImprovement = newImprovement
        )
    }

    /**
     * Berechnet Fortschritt in Prozent
     */
    private fun calculateProgressPercentage(sessions: Int, avgSuccess: Double, effort: Int): Int {
        val sessionScore = (sessions.coerceAtMost(50) / 50.0) * 40
        val successScore = ((avgSuccess - 1.0) / 3.0) * 40
        val effortScore = (effort.coerceAtMost(500) / 500.0) * 20

        return (sessionScore + successScore + effortScore).toInt().coerceIn(0, 100)
    }

    /**
     * Berechnet Streak (aufeinanderfolgende Trainingstage)
     */
    private fun calculateStreak(newSessionDate: Long): Int {
        if (lastTrainingDate == 0L) return 1

        val daysDiff = ((newSessionDate - lastTrainingDate) / (1000 * 60 * 60 * 24)).toInt()

        return when {
            daysDiff == 0 -> consecutiveDays
            daysDiff == 1 -> consecutiveDays + 1
            else -> 1
        }
    }

    /**
     * Berechnet Verbesserung
     */
    private fun calculateImprovement(newSession: TrainingSession): Double {
        if (totalSessions == 0) return 0.0

        val improvement = ((newSession.successRating - averageSuccess) / averageSuccess) * 100
        return improvement.coerceIn(-100.0, 100.0)
    }

    /**
     * ✅ NEU: Wochenstatistik
     */
    fun getWeeklyStats(): String {
        val sessionsPerWeek = if (totalSessions > 0) {
            val weeks = (totalMinutes / 60.0 / 7.0).coerceAtLeast(1.0)
            String.format("%.1f", totalSessions / weeks)
        } else "0"

        return "$sessionsPerWeek Sessions/Woche"
    }

    /**
     * ✅ NEU: Konsistenz-Score (0-100)
     */
    fun getConsistencyScore(): Int {
        if (totalSessions < 5) return 0

        // Basierend auf Streak und Regelmäßigkeit
        val streakScore = (consecutiveDays * 10).coerceAtMost(50)
        val regularityScore = if (totalSessions >= 10) 50 else (totalSessions * 5)

        return (streakScore + regularityScore).coerceAtMost(100)
    }

    /**
     * ✅ NEU: Trend-Text
     */
    fun getTrendText(): String {
        return when {
            recentImprovement > 10 -> "📈 Stark verbessert"
            recentImprovement > 0 -> "📊 Leicht verbessert"
            recentImprovement > -10 -> "➡️ Stabil"
            else -> "📉 Rückläufig"
        }
    }

    /**
     * ✅ NEU: Tage seit letztem Training
     */
    fun getDaysSinceLastTraining(): Int {
        if (lastTrainingDate == 0L) return -1

        val now = System.currentTimeMillis()
        return ((now - lastTrainingDate) / (1000 * 60 * 60 * 24)).toInt()
    }

    /**
     * ✅ NEU: Ist Training überfällig?
     */
    fun isOverdue(): Boolean {
        return getDaysSinceLastTraining() > 7
    }

    /**
     * Formatierte Erfolgsrate
     */
    fun getFormattedSuccess(): String {
        return String.format("%.1f / 4.0", averageSuccess)
    }

    /**
     * Progress-Icon
     */
    fun getProgressIcon(): String {
        return when {
            progressPercentage >= 90 -> "🏆"
            progressPercentage >= 75 -> "⭐"
            progressPercentage >= 50 -> "📈"
            progressPercentage >= 25 -> "🌱"
            else -> "🔰"
        }
    }
}

// ════════════════════════════════════════════════════════════════
// TRAINING LEVEL
// ════════════════════════════════════════════════════════════════

@Parcelize
enum class TrainingLevel : Parcelable {
    BEGINNER, BASICS, INTERMEDIATE, ADVANCED, ESTABLISHED;

    fun displayName(context: android.content.Context): String = when (this) {
        BEGINNER -> context.getString(R.string.training_level_beginner)
        BASICS -> context.getString(R.string.training_level_basics)
        INTERMEDIATE -> context.getString(R.string.training_level_intermediate)
        ADVANCED -> context.getString(R.string.training_level_advanced)
        ESTABLISHED -> context.getString(R.string.training_level_established)
    }

    companion object {
        fun fromProgress(percentage: Int): TrainingLevel {
            return when {
                percentage >= 80 -> ESTABLISHED
                percentage >= 60 -> ADVANCED
                percentage >= 40 -> INTERMEDIATE
                percentage >= 20 -> BASICS
                else -> BEGINNER
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// MEILENSTEINE - Trainings-Erfolge
// ════════════════════════════════════════════════════════════════

/**
 * Trainings-Meilenstein
 */
@Parcelize
data class Milestone(
    val id: String = java.util.UUID.randomUUID().toString(),
    val dogId: String = "",
    val category: TrainingCategory = TrainingCategory.LEINENARBEIT,
    val type: MilestoneType = MilestoneType.FIRST_SESSION,
    val achievedDate: Long = System.currentTimeMillis()
) : Parcelable {

    /**
     * Formatiertes Datum
     */
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
        return sdf.format(java.util.Date(achievedDate))
    }
}

/**
 * Meilenstein-Typen
 */
@Parcelize
enum class MilestoneType(val icon: String) : Parcelable {
    FIRST_SESSION("🎯"),
    FIVE_SESSIONS("⭐"),
    TEN_SESSIONS("🏅"),
    TWENTY_SESSIONS("🏆"),
    FIFTY_SESSIONS("👑"),

    FIVE_DAY_STREAK("🔥"),
    TEN_DAY_STREAK("🔥🔥"),

    PROGRESS_50("📈"),
    PROGRESS_75("⭐"),
    PROGRESS_100("🏆"),

    SUCCESS_STREAK("💯"),
    PERFECT_WEEK("🌟");

    fun displayName(context: android.content.Context): String = when (this) {
        FIRST_SESSION -> context.getString(R.string.milestone_type_first_session)
        FIVE_SESSIONS -> context.getString(R.string.milestone_type_five_sessions)
        TEN_SESSIONS -> context.getString(R.string.milestone_type_ten_sessions)
        TWENTY_SESSIONS -> context.getString(R.string.milestone_type_twenty_sessions)
        FIFTY_SESSIONS -> context.getString(R.string.milestone_type_fifty_sessions)
        FIVE_DAY_STREAK -> context.getString(R.string.milestone_type_five_day_streak)
        TEN_DAY_STREAK -> context.getString(R.string.milestone_type_ten_day_streak)
        PROGRESS_50 -> context.getString(R.string.milestone_type_progress_50)
        PROGRESS_75 -> context.getString(R.string.milestone_type_progress_75)
        PROGRESS_100 -> context.getString(R.string.milestone_type_progress_100)
        SUCCESS_STREAK -> context.getString(R.string.milestone_type_success_streak)
        PERFECT_WEEK -> context.getString(R.string.milestone_type_perfect_week)
    }
}

/**
 * Trainingsempfehlung
 */
@Parcelize
data class TrainingRecommendation(
    val recommendation: String = "",
    val reasoning: String = "",
    val suggestedDuration: Int = 15,  // Minuten
    val suggestedDifficulty: DifficultyLevel = DifficultyLevel.NORMAL,
    val priority: RecommendationPriority = RecommendationPriority.MEDIUM
) : Parcelable

/**
 * Priorität der Empfehlung
 */
@Parcelize
enum class RecommendationPriority : Parcelable {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Fortschritts-Analyse
 */
@Parcelize
data class ProgressAnalysis(
    val trend: ProgressTrend = ProgressTrend.STABLE,
    val improvement: Double = 0.0,  // Prozent
    val consistency: Double = 0.0   // 0-100
) : Parcelable

/**
 * Fortschritts-Trend
 */
@Parcelize
enum class ProgressTrend(
    val icon: String
) : Parcelable {
    IMPROVING("📈"),
    STABLE("➡️"),
    DECLINING("📉");

    fun displayName(context: android.content.Context): String = when (this) {
        IMPROVING -> context.getString(R.string.progress_trend_improving)
        STABLE -> context.getString(R.string.progress_trend_stable)
        DECLINING -> context.getString(R.string.progress_trend_declining)
    }
}

/**
 * Wochenstatistik
 */
@Parcelize
data class WeeklyStats(
    val totalSessions: Int = 0,
    val totalMinutes: Int = 0,
    val totalRepetitions: Int = 0,  // ← NEU!
    val categoriesUsed: Int = 0,
    val averageSuccess: Double = 0.0
) : Parcelable