package com.hundewildnis.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

// ════════════════════════════════════════════════════════════════
// TRAININGSKATEGORIEN - Die 18 ausgewählten Kurse
// ════════════════════════════════════════════════════════════════

enum class TrainingCategory(
    val id: Int,
    val displayName: String,
    val description: String
) {
    LEINENARBEIT(13, "Leinenarbeit", "Grundlagen der Leinenführung"),
    FUSS(15, "Fuß", "Präzise Fußarbeit"),
    SITZ(16, "Sitz", "Sitz-Kommando und Festigung"),
    PLATZ(17, "Platz", "Platz-Kommando und Ausdauer"),
    STEH(18, "Steh", "Steh-Kommando und Position"),
    SOZIALISIERUNG(19, "Sozialisierungstechniken", "Sozialverhalten fördern"),
    LANGLEINE(20, "Langleinentraining", "Arbeit mit langer Leine"),
    DISTANZUNTERBRECHUNG(21, "Distanzunterbrechungen", "Unterbrechung auf Distanz"),
    FREILAUF(22, "Freilauf", "Freiarbeit ohne Leine"),
    FREIFOLGE(23, "Freifolge", "Folgen ohne Leine"),
    TEAMWALK(24, "Teamwalk", "Gemeinsames Laufen"),
    SCOUTTECHNIKEN(25, "Scouttechniken", "Vorausschau und Orientierung"),
    JAGDVERMEIDUNG(26, "Jagdvermeidung", "Anti-Jagd-Training"),
    NASENARBEIT(27, "Nasenarbeit", "Geruchsarbeit und Suche"),
    LEINENFUEHRIGKEIT(28, "Leinenführigkeit", "Entspannte Leinenführung"),
    BEGEGNUNGEN(29, "Begegnungssituationen", "Souveräne Begegnungen"),
    STRESSTOLERANZ(30, "Stresstoleranztraining", "Gelassenheit unter Stress"),
    STRASSENROUTINEN(31, "Straßenroutinen", "Sicheres Verhalten im Straßenverkehr");

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
enum class DistractionLevel(val displayName: String) : Parcelable {
    NONE("Keine Ablenkung"),
    LOW("Geringe Ablenkung"),
    MEDIUM("Mittlere Ablenkung"),
    HIGH("Hohe Ablenkung");
}

/**
 * Schwierigkeitsgrad der Trainingseinheit
 */
@Parcelize
enum class DifficultyLevel(val displayName: String) : Parcelable {
    EASY("Leicht"),
    NORMAL("Normal"),
    HARD("Schwer");
}

/**
 * Wetterbedingungen (optional)
 */
@Parcelize
enum class WeatherCondition(val displayName: String) : Parcelable {
    SUNNY("Sonnig"),
    CLOUDY("Bewölkt"),
    RAINY("Regnerisch"),
    SNOWY("Schnee"),
    WINDY("Windig"),
    FOGGY("Neblig"),
    STORMY("Stürmisch");
}

/**
 * Trainingsort (optional)
 */
@Parcelize
enum class LocationType(val displayName: String) : Parcelable {
    HOME("Zuhause"),
    GARDEN("Garten"),
    PARK("Park"),
    FOREST("Wald"),
    FIELD("Feld/Wiese"),
    CITY("Stadt"),
    INDOOR_HALL("Trainingshalle"),
    DOG_SCHOOL("Hundeschule"),
    OTHER("Sonstiges");
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
    val temperament: Int,           // 1=Sehr ruhig, 5=Sehr lebhaft
    val selfConfidence: Int,        // 1=Ängstlich, 5=Selbstsicher
    val socialBehavior: Int,        // 1=Zurückhaltend, 5=Kontaktfreudig
    val excitability: Int,          // 1=Gelassen, 5=Leicht erregbar
    val learningSpeed: Int,         // 1=Langsam, 5=Schnell
    val frustrationTolerance: Int   // 1=Niedrig, 5=Hoch
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
    val dogId: String,
    val category: TrainingCategory,
    val date: Long = System.currentTimeMillis(),

    // Core Training Data
    val durationMinutes: Int,  // 5-60
    val successRating: Int,     // 1-4
    val distractionLevel: DistractionLevel,
    val difficulty: DifficultyLevel,

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
}

// ════════════════════════════════════════════════════════════════
// FORTSCHRITT - Trainingsstand pro Kategorie und Hund
// ════════════════════════════════════════════════════════════════

@Parcelize
data class TrainingProgress(
    val dogId: String,
    val category: TrainingCategory,

    // Fortschritt
    val progressPercentage: Int = 0,  // 0-100
    val currentLevel: TrainingLevel = TrainingLevel.BEGINNER,

    // Statistiken
    val totalSessions: Int = 0,
    val totalMinutes: Int = 0,
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
        val newTotalMinutes = totalMinutes + session.durationMinutes

        // Neue durchschnittliche Erfolgsrate
        val newAverageSuccess = ((averageSuccess * totalSessions) + session.successRating) / newTotalSessions

        // Streak berechnen
        val newConsecutiveDays = calculateStreak(session.date)

        // Fortschritt berechnen (basierend auf Sessions, Erfolg, Minuten)
        val newProgressPercentage = calculateProgressPercentage(
            sessions = newTotalSessions,
            avgSuccess = newAverageSuccess,
            minutes = newTotalMinutes
        )

        // Level basierend auf Fortschritt
        val newLevel = TrainingLevel.fromProgress(newProgressPercentage)

        // Trend berechnen (letzte 3 vs. vorherige Sessions)
        val newImprovement = calculateImprovement(session)

        return copy(
            totalSessions = newTotalSessions,
            totalMinutes = newTotalMinutes,
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
    private fun calculateProgressPercentage(sessions: Int, avgSuccess: Double, minutes: Int): Int {
        // Gewichtung:
        // - Sessions: 40% (max 50 Sessions = 100%)
        // - Erfolgsrate: 40% (4.0 = 100%)
        // - Trainingszeit: 20% (max 500 Min = 100%)

        val sessionScore = (sessions.coerceAtMost(50) / 50.0) * 40
        val successScore = ((avgSuccess - 1.0) / 3.0) * 40  // 1.0-4.0 → 0-100%
        val timeScore = (minutes.coerceAtMost(500) / 500.0) * 20

        return (sessionScore + successScore + timeScore).toInt().coerceIn(0, 100)
    }

    /**
     * Berechnet Streak (aufeinanderfolgende Trainingstage)
     */
    private fun calculateStreak(newSessionDate: Long): Int {
        if (lastTrainingDate == 0L) return 1

        val daysDiff = ((newSessionDate - lastTrainingDate) / (1000 * 60 * 60 * 24)).toInt()

        return when {
            daysDiff == 0 -> consecutiveDays  // Gleicher Tag
            daysDiff == 1 -> consecutiveDays + 1  // Nächster Tag
            else -> 1  // Streak unterbrochen
        }
    }

    /**
     * Berechnet Verbesserung (letzte 3 Sessions vs. davor)
     */
    private fun calculateImprovement(newSession: TrainingSession): Double {
        // Vereinfacht: Vergleiche neue Session mit Durchschnitt
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
enum class TrainingLevel(val displayName: String) : Parcelable {
    BEGINNER("Beginn"),
    BASICS("Grundlagen"),
    INTERMEDIATE("Aufbau"),
    ADVANCED("Festigung"),
    ESTABLISHED("Etabliert");

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
    val dogId: String,  // ✅ WICHTIG!
    val category: TrainingCategory,
    val type: MilestoneType,
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
enum class MilestoneType(val displayName: String, val icon: String) : Parcelable {
    FIRST_SESSION("Erste Trainingseinheit", "🎯"),
    FIVE_SESSIONS("5 Trainingseinheiten", "⭐"),
    TEN_SESSIONS("10 Trainingseinheiten", "🏅"),
    TWENTY_SESSIONS("20 Trainingseinheiten", "🏆"),
    FIFTY_SESSIONS("50 Trainingseinheiten", "👑"),

    FIVE_DAY_STREAK("5 Tage am Stück", "🔥"),
    TEN_DAY_STREAK("10 Tage am Stück", "🔥🔥"),

    PROGRESS_50("50% Fortschritt", "📈"),
    PROGRESS_75("75% Fortschritt", "⭐"),
    PROGRESS_100("100% Fortschritt", "🏆"),

    SUCCESS_STREAK("5x sehr erfolgreich", "💯"),
    PERFECT_WEEK("Perfekte Woche", "🌟");
}

/**
 * Trainingsempfehlung
 */
@Parcelize
data class TrainingRecommendation(
    val recommendation: String,
    val reasoning: String,
    val suggestedDuration: Int,  // Minuten
    val suggestedDifficulty: DifficultyLevel,
    val priority: RecommendationPriority
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
    val trend: ProgressTrend,
    val improvement: Double,  // Prozent
    val consistency: Double   // 0-100
) : Parcelable

/**
 * Fortschritts-Trend
 */
@Parcelize
enum class ProgressTrend(
    val displayName: String,
    val icon: String
) : Parcelable {
    IMPROVING("Verbesserung", "📈"),
    STABLE("Stabil", "➡️"),
    DECLINING("Rückläufig", "📉");
}

/**
 * Wochenstatistik
 */
@Parcelize
data class WeeklyStats(
    val totalSessions: Int = 0,
    val totalMinutes: Int = 0,
    val categoriesUsed: Int = 0,
    val averageSuccess: Double = 0.0
) : Parcelable
