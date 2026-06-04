package com.hundewildnis.app

import kotlin.math.abs

// ════════════════════════════════════════════════════════════════
// TRAININGS-KONFIGURATION - Zentrale Parameter
// ════════════════════════════════════════════════════════════════

object TrainingRecommendationEngine {

    /**
     * Generiert intelligente Trainingsempfehlung
     */
    fun generateRecommendation(
        progress: TrainingProgress,
        recentSessions: List<TrainingSession>,
        dogCharacter: DogCharacter? = null
    ): TrainingRecommendation {

        // Basis-Empfehlung basierend auf Fortschritt
        return when {
            // Szenario 1: Anfänger (0-20%)
            progress.progressPercentage < 20 -> {
                TrainingRecommendation(
                    recommendation = "Fokussiere auf kurze, häufige Einheiten",
                    reasoning = "Am Anfang ist Regelmäßigkeit wichtiger als lange Sessions. " +
                            "Trainiere täglich 10-15 Minuten mit geringer Ablenkung.",
                    suggestedDuration = adjustForCharacter(15, dogCharacter),
                    suggestedDifficulty = DifficultyLevel.EASY,
                    priority = RecommendationPriority.HIGH
                )
            }

            // Szenario 2: Grundlagen gelegt (20-40%)
            progress.progressPercentage < 40 -> {
                TrainingRecommendation(
                    recommendation = "Steigere langsam die Schwierigkeit",
                    reasoning = "Die Grundlagen sitzen. Führe nun leichte Ablenkungen ein " +
                            "und verlängere die Sessions auf 15-20 Minuten.",
                    suggestedDuration = adjustForCharacter(18, dogCharacter),
                    suggestedDifficulty = DifficultyLevel.NORMAL,
                    priority = RecommendationPriority.MEDIUM
                )
            }

            // Szenario 3: Guter Fortschritt (40-60%)
            progress.progressPercentage < 60 -> {
                val trend = analyzeRecentTrend(recentSessions)
                if (trend < 0) {
                    TrainingRecommendation(
                        recommendation = "Pause oder zurück zu Basics",
                        reasoning = "Der Trend zeigt Rückschritte. Gönne deinem Hund eine Pause " +
                                "oder wiederhole bereits gemeisterte Übungen zur Festigung.",
                        suggestedDuration = adjustForCharacter(12, dogCharacter),
                        suggestedDifficulty = DifficultyLevel.EASY,
                        priority = RecommendationPriority.HIGH
                    )
                } else {
                    TrainingRecommendation(
                        recommendation = "Variiere die Trainingsumgebung",
                        reasoning = "Festige das Gelernte in verschiedenen Situationen. " +
                                "Trainiere an neuen Orten mit unterschiedlichen Ablenkungen.",
                        suggestedDuration = adjustForCharacter(20, dogCharacter),
                        suggestedDifficulty = DifficultyLevel.NORMAL,
                        priority = RecommendationPriority.MEDIUM
                    )
                }
            }

            // Szenario 4: Fortgeschritten (60-80%)
            progress.progressPercentage < 80 -> {
                TrainingRecommendation(
                    recommendation = "Meistere anspruchsvolle Situationen",
                    reasoning = "Dein Hund zeigt gute Fortschritte. Teste das Gelernte nun " +
                            "unter schwierigen Bedingungen mit hoher Ablenkung.",
                    suggestedDuration = adjustForCharacter(25, dogCharacter),
                    suggestedDifficulty = DifficultyLevel.HARD,
                    priority = RecommendationPriority.LOW
                )
            }

            // Szenario 5: Meisterschaft (80-100%)
            else -> {
                TrainingRecommendation(
                    recommendation = "Halte das Niveau mit Auffrischungen",
                    reasoning = "Exzellent! Trainiere nun 2-3x pro Woche zur Erhaltung. " +
                            "Integriere das Gelernte in den Alltag.",
                    suggestedDuration = adjustForCharacter(15, dogCharacter),
                    suggestedDifficulty = DifficultyLevel.NORMAL,
                    priority = RecommendationPriority.LOW
                )
            }
        }
    }

    /**
     * Analysiert Fortschritts-Trend
     */
    fun analyzeProgress(
        progress: TrainingProgress,
        allSessions: List<TrainingSession>
    ): ProgressAnalysis {

        if (allSessions.isEmpty()) {
            return ProgressAnalysis(
                trend = ProgressTrend.STABLE,
                improvement = 0.0,
                consistency = 0.0
            )
        }

        // Trend berechnen
        val recentAvg = allSessions
            .sortedByDescending { it.date }
            .take(3)
            .map { it.successRating }
            .average()

        val olderAvg = allSessions
            .sortedByDescending { it.date }
            .drop(3)
            .take(3)
            .map { it.successRating }
            .average()

        val improvement = if (olderAvg > 0) {
            ((recentAvg - olderAvg) / olderAvg) * 100
        } else {
            0.0
        }

        val trend = when {
            improvement > 10 -> ProgressTrend.IMPROVING
            improvement < -10 -> ProgressTrend.DECLINING
            else -> ProgressTrend.STABLE
        }

        // Konsistenz berechnen
        val consistency = calculateConsistency(allSessions)

        return ProgressAnalysis(
            trend = trend,
            improvement = improvement,
            consistency = consistency
        )
    }

    /**
     * Passt Dauer an Hundecharakter an
     */
    private fun adjustForCharacter(baseDuration: Int, character: DogCharacter?): Int {
        if (character == null) return baseDuration

        return when {
            // Ängstlicher Hund → kürzere Sessions
            character.selfConfidence <= 2 -> (baseDuration * 0.7).toInt()

            // Sehr energiegeladener Hund → längere Sessions
            character.temperament >= 4 -> (baseDuration * 1.3).toInt()

            // Schneller Lerner → etwas länger
            character.learningSpeed >= 4 -> (baseDuration * 1.1).toInt()

            // Niedrige Frustrationstoleranz → kürzer
            character.frustrationTolerance <= 2 -> (baseDuration * 0.8).toInt()

            else -> baseDuration
        }
    }

    /**
     * Berechnet Trend der letzten Sessions
     */
    private fun analyzeRecentTrend(sessions: List<TrainingSession>): Double {
        if (sessions.size < 2) return 0.0

        val recent = sessions.take(2).map { it.successRating }.average()
        val previous = sessions.drop(2).take(2).map { it.successRating }.average()

        return if (previous > 0) {
            ((recent - previous) / previous) * 100
        } else {
            0.0
        }
    }

    /**
     * Berechnet Trainings-Konsistenz
     */
    private fun calculateConsistency(sessions: List<TrainingSession>): Double {
        if (sessions.size < 5) return (sessions.size * 20).toDouble()

        val sortedSessions = sessions.sortedBy { it.date }
        val timeGaps = mutableListOf<Long>()

        for (i in 1 until sortedSessions.size) {
            val gap = sortedSessions[i].date - sortedSessions[i-1].date
            val gapDays = gap / (1000 * 60 * 60 * 24)
            timeGaps.add(gapDays)
        }

        val avgGap = timeGaps.average()

        // Je näher an 1 Tag, desto besser (100%)
        // Je weiter weg, desto schlechter
        return when {
            avgGap <= 1.5 -> 100.0
            avgGap <= 3.0 -> 80.0
            avgGap <= 5.0 -> 60.0
            avgGap <= 7.0 -> 40.0
            else -> 20.0
        }
    }
}
