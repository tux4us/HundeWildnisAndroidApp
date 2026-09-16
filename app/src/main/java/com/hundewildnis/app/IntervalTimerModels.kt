package com.hundewildnis.app

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════
// INTERVALL-TIMER – DATENMODELL
// Datei: IntervalTimerModels.kt
// ════════════════════════════════════════════════════════════════

// ── Phasen des Timers ────────────────────────────────────────────
enum class IntervalTimerPhase {
    PREPARE,   // 3-Sekunden-Countdown vor dem ersten Start
    ACTIVE,    // Aktive Übungsphase
    REST,      // Pausephase
    COOLDOWN,  // Abkühlphase am Ende
    FINISHED   // Session abgeschlossen
}

// ── Konfiguration eines Timer-Laufs ─────────────────────────────
@Parcelize
data class IntervalTimerConfig(
    val activeDurationSeconds: Int = 45,   // Aktiv-Phase in Sekunden
    val restDurationSeconds: Int   = 30,   // Pause-Phase in Sekunden
    val rounds: Int                = 6,    // Anzahl Durchgänge
    val cooldownSeconds: Int       = 60,   // Abkühlphase (0 = deaktiviert)
    val soundEnabled: Boolean      = true, // Ton-Feedback
    val vibrationEnabled: Boolean  = true  // Vibrations-Feedback
) : Parcelable {

    /** Gesamtdauer in Sekunden */
    val totalDurationSeconds: Int
        get() = PREPARE_SECONDS +
                (activeDurationSeconds + restDurationSeconds) * rounds -
                restDurationSeconds +           // letzte Pause entfällt
                (if (cooldownSeconds > 0) cooldownSeconds else 0)

    /** Gesamtdauer als formatierter String "X Min Y Sek" */
    fun getTotalDurationLabel(context: Context): String {
        val min = totalDurationSeconds / 60
        val sec = totalDurationSeconds % 60
        return when {
            min == 0 -> context.getString(R.string.interval_unit_seconds, sec)
            sec == 0 -> context.getString(R.string.interval_unit_minutes, min)
            else     -> context.getString(R.string.interval_unit_min_sec, min, sec)
        }
    }

    /** Legacy property for background services or non-UI usage */
    val totalDurationLabel: String
        get() {
            val min = totalDurationSeconds / 60
            val sec = totalDurationSeconds % 60
            return when {
                min == 0 -> "$sec Sek"
                sec == 0 -> "$min Min"
                else     -> "$min Min $sec Sek"
            }
        }

    companion object {
        const val PREPARE_SECONDS = 3

        // ── Vorschläge aus Wesensmerkmalen ──────────────────────
        fun fromQuickProfile(profile: QuickCharacterProfile): IntervalTimerConfig =
            when (profile) {
                QuickCharacterProfile.PUPPY -> IntervalTimerConfig(
                    activeDurationSeconds = 20, restDurationSeconds = 40,
                    rounds = 4, cooldownSeconds = 60
                )
                QuickCharacterProfile.SENIOR -> IntervalTimerConfig(
                    activeDurationSeconds = 30, restDurationSeconds = 60,
                    rounds = 5, cooldownSeconds = 90
                )
                QuickCharacterProfile.ENERGETIC -> IntervalTimerConfig(
                    activeDurationSeconds = 60, restDurationSeconds = 30,
                    rounds = 8, cooldownSeconds = 60
                )
                QuickCharacterProfile.CALM -> IntervalTimerConfig(
                    activeDurationSeconds = 40, restDurationSeconds = 40,
                    rounds = 6, cooldownSeconds = 60
                )
                QuickCharacterProfile.ANXIOUS, QuickCharacterProfile.RESCUE_DOG -> IntervalTimerConfig(
                    activeDurationSeconds = 20, restDurationSeconds = 50,
                    rounds = 4, cooldownSeconds = 120
                )
                QuickCharacterProfile.WORKING_DOG -> IntervalTimerConfig(
                    activeDurationSeconds = 90, restDurationSeconds = 30,
                    rounds = 8, cooldownSeconds = 60
                )
                else -> IntervalTimerConfig() // Ausgeglichen / Standard
            }

        // ── Vorschlag aus DogCharacter-Werten ───────────────────
        fun fromCharacter(character: DogCharacter): IntervalTimerConfig {
            // Aktiv-Dauer: Energie (Temperament) × Frustrationstole­ranz
            val activeBase = when {
                character.temperament >= 4 && character.frustrationTolerance >= 4 -> 75
                character.temperament >= 4                                          -> 60
                character.temperament <= 2                                          -> 25
                else                                                                -> 45
            }
            // Pause: Ängstliche / langsam lernende Hunde brauchen mehr
            val restBase = when {
                character.selfConfidence <= 2 || character.learningSpeed <= 2 -> 60
                character.excitability >= 4                                    -> 40
                else                                                           -> 30
            }
            // Runden: Ausdauerkorrelat aus Frustrations-Toleranz
            val roundsBase = when {
                character.frustrationTolerance >= 4 -> 8
                character.frustrationTolerance <= 2 -> 4
                else                                -> 6
            }
            return IntervalTimerConfig(
                activeDurationSeconds = activeBase,
                restDurationSeconds   = restBase,
                rounds                = roundsBase,
                cooldownSeconds       = 60
            )
        }
    }
}

// ── Protokolleintrag einer abgeschlossenen Session ───────────────
@Parcelize
data class IntervalTimerSession(
    val id: String                    = UUID.randomUUID().toString(),
    val dogId: String                 = "",
    val dogName: String               = "",
    val timestamp: Long               = System.currentTimeMillis(),
    val config: IntervalTimerConfig   = IntervalTimerConfig(),
    val completedRounds: Int          = 0,
    val abortedEarly: Boolean         = false,
    val linkedCategoryId: Int?        = null,  // TrainingCategory.id – optional
    val notes: String                 = ""
) : Parcelable {

    fun formattedDate(): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

    fun formattedDateShort(): String =
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))

    /** Effektiv absolvierte Aktivzeit in Minuten (für Trainingstagebuch) */
    val effectiveActiveMinutes: Int
        get() = (completedRounds * config.activeDurationSeconds) / 60

    fun getSummaryLine(context: Context): String {
        val status = if (abortedEarly) context.getString(R.string.interval_history_status_aborted) else context.getString(R.string.interval_history_status_completed)
        return context.getString(R.string.interval_history_summary_format, completedRounds, config.rounds, status, config.activeDurationSeconds, config.restDurationSeconds)
    }

    fun summaryLine(): String {
        val status = if (abortedEarly) "Abgebrochen" else "Abgeschlossen"
        return "$completedRounds/${config.rounds} Runden · $status · ${config.activeDurationSeconds}s aktiv / ${config.restDurationSeconds}s Pause"
    }

    fun toCsvRow(): String = listOf(
        formattedDate(),
        dogName,
        completedRounds.toString(),
        config.rounds.toString(),
        config.activeDurationSeconds.toString(),
        config.restDurationSeconds.toString(),
        config.cooldownSeconds.toString(),
        if (abortedEarly) "Ja" else "Nein",
        linkedCategoryId?.toString() ?: "",
        notes.replace(";", ",").replace("\n", " ")
    ).joinToString(";")

    companion object {
        val CSV_HEADER = listOf(
            "Datum/Uhrzeit", "Hund", "Abgeschlossene Runden", "Geplante Runden",
            "Aktiv-Dauer (s)", "Pause-Dauer (s)", "Abkühl-Dauer (s)",
            "Abgebrochen", "Trainingskategorie-ID", "Notizen"
        ).joinToString(";")
    }
}
