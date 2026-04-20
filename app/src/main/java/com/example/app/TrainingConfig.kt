package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// TRAINING CONFIG - Zentrale Parameter
// FÜGE DIESE in TrainingModels.kt ein (am Anfang, nach imports)!
// ════════════════════════════════════════════════════════════════

/**
 * Zentrale Konfiguration für Trainingssystem
 */
object TrainingConfig {

    // ══════════════════════════════════════════════════════════
    // TRAININGS-DAUER / WIEDERHOLUNGEN
    // ══════════════════════════════════════════════════════════

    // Zeit-basiert
    const val MIN_TRAINING_DURATION = 5      // Minuten
    const val IDEAL_TRAINING_DURATION = 15   // Minuten
    const val MAX_TRAINING_DURATION = 120    // Minuten

    // Wiederholungs-basiert
    const val MIN_REPETITIONS = 1            // Wiederholungen
    const val IDEAL_REPETITIONS = 10         // Wiederholungen
    const val MAX_REPETITIONS = 100          // Wiederholungen

    // ══════════════════════════════════════════════════════════
    // FORTSCHRITTS-SCHWELLENWERTE
    // ══════════════════════════════════════════════════════════

    const val BEGINNER_THRESHOLD = 20       // %
    const val BASICS_THRESHOLD = 40         // %
    const val INTERMEDIATE_THRESHOLD = 60   // %
    const val ADVANCED_THRESHOLD = 80       // %

    // ══════════════════════════════════════════════════════════
    // ERFOLGS-BEWERTUNG
    // ══════════════════════════════════════════════════════════

    const val MIN_SUCCESS_RATING = 1        // Schwierigkeiten
    const val MAX_SUCCESS_RATING = 4        // Sehr gut
    const val TARGET_SUCCESS_RATING = 3     // Erfolgreich

    // ══════════════════════════════════════════════════════════
    // MEILENSTEIN-SCHWELLEN
    // ══════════════════════════════════════════════════════════

    const val MILESTONE_FIRST_SESSION = 1
    const val MILESTONE_FIVE_SESSIONS = 5
    const val MILESTONE_TEN_SESSIONS = 10
    const val MILESTONE_TWENTY_SESSIONS = 20
    const val MILESTONE_FIFTY_SESSIONS = 50

    const val MILESTONE_FIVE_DAY_STREAK = 5
    const val MILESTONE_TEN_DAY_STREAK = 10

    // ══════════════════════════════════════════════════════════
    // FORTSCHRITTS-BERECHNUNG (Gewichtung)
    // ══════════════════════════════════════════════════════════

    const val WEIGHT_SESSIONS = 0.4f        // 40%
    const val WEIGHT_SUCCESS = 0.4f         // 40%
    const val WEIGHT_EFFORT = 0.2f          // 20% (Zeit ODER Wiederholungen)

    const val MAX_SESSIONS_FOR_PROGRESS = 50    // Ab 50 Sessions = 100%
    const val MAX_MINUTES_FOR_PROGRESS = 800    // Ab 800 Min = 100%
    const val MAX_REPS_FOR_PROGRESS = 500       // Ab 500 Wiederholungen = 100%

    
    // ══════════════════════════════════════════════════════════
    // EMPFEHLUNGS-PARAMETER
    // ══════════════════════════════════════════════════════════
    
    const val TREND_THRESHOLD_POSITIVE = 10.0   // % Verbesserung
    const val TREND_THRESHOLD_NEGATIVE = -10.0  // % Verschlechterung
    
    const val CONSISTENCY_EXCELLENT = 1.5       // Tage zwischen Sessions
    const val CONSISTENCY_GOOD = 3.0
    const val CONSISTENCY_MODERATE = 5.0
    const val CONSISTENCY_POOR = 7.0
}
