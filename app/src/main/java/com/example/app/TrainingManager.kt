package com.hundewildnis.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ════════════════════════════════════════════════════════════════
// TRAINING MANAGER - Komplett mit allen Funktionen
// ════════════════════════════════════════════════════════════════

class TrainingManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // ════════════════════════════════════════════════════════════
    // SESSION MANAGEMENT
    // ════════════════════════════════════════════════════════════

    fun saveSession(session: TrainingSession) {
        val sessions = getAllSessions(context).toMutableList()
        sessions.add(session)

        val json = gson.toJson(sessions)
        prefs.edit().putString(KEY_SESSIONS, json).apply()

        // Update Progress
        updateProgress(session)

        // Check Milestones
        checkMilestones(session)
    }

    fun getSessionsForCategory(dogId: String, category: TrainingCategory): List<TrainingSession> {
        return getAllSessions(context).filter {
            it.dogId == dogId && it.category == category
        }
    }

    fun getAllSessions(context: Context): List<TrainingSession> {
        val json = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<TrainingSession>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ✅ NEU: Einzelne Session löschen
    fun deleteSession(context: Context, sessionId: String): Boolean {
        val allSessions = getAllSessions(context).toMutableList()
        val session = allSessions.find { it.id == sessionId } ?: return false

        // Session entfernen
        allSessions.removeIf { it.id == sessionId }

        // Speichern
        val json = gson.toJson(allSessions)
        prefs.edit().putString(KEY_SESSIONS, json).apply()

        // Progress neu berechnen
        val dogSessions = allSessions.filter {
            it.dogId == session.dogId && it.category == session.category
        }

        if (dogSessions.isEmpty()) {
            deleteProgress(context, session.dogId, session.category)
        } else {
            // Progress komplett neu berechnen
            var progress = TrainingProgress(
                dogId = session.dogId,
                category = session.category
            )
            dogSessions.forEach { s ->
                progress = progress.updateWithSession(s)
            }
            saveProgress(context, progress)
        }

        return true
    }

    // ✅ NEU: Alle Trainingsdaten eines Hundes löschen
    fun deleteDogTrainingData(context: Context, dogId: String): Boolean {
        // Sessions löschen
        val allSessions = getAllSessions(context).toMutableList()
        allSessions.removeIf { it.dogId == dogId }
        val json = gson.toJson(allSessions)
        prefs.edit().putString(KEY_SESSIONS, json).apply()

        // Progress löschen
        val allProgress = getAllProgress(context).toMutableList()
        allProgress.removeIf { it.dogId == dogId }
        val progressJson = gson.toJson(allProgress)
        prefs.edit().putString(KEY_PROGRESS, progressJson).apply()

        // Meilensteine löschen
        val allMilestones = getAllMilestones(context).toMutableList()
        allMilestones.removeIf { it.dogId == dogId }
        val milestonesJson = gson.toJson(allMilestones)
        prefs.edit().putString(KEY_MILESTONES, milestonesJson).apply()

        return true
    }

    /**
     * ✅ NEU: Holt Trainingsempfehlung
     */
    fun getRecommendation(
        dogId: String,
        category: TrainingCategory,
        dogCharacter: DogCharacter? = null
    ): TrainingRecommendation {
        val sessions = getSessionsForCategory(dogId, category)
        val progress = getProgress(dogId, category)

        return TrainingRecommendationEngine.generateRecommendation(
            progress = progress,
            recentSessions = sessions.sortedByDescending { it.date }.take(3),
            dogCharacter = dogCharacter
        )
    }

    /**
     * ✅ NEU: Analyse des Trainingsfortschritts
     */
    fun getProgressAnalysis(dogId: String, category: TrainingCategory): ProgressAnalysis {
        val sessions = getSessionsForCategory(dogId, category)
        val progress = getProgress(dogId, category)

        return TrainingRecommendationEngine.analyzeProgress(
            progress = progress,
            allSessions = sessions
        )
    }

    // ════════════════════════════════════════════════════════════
    // PROGRESS MANAGEMENT
    // ════════════════════════════════════════════════════════════

    fun getProgress(dogId: String, category: TrainingCategory): TrainingProgress {
        val allProgress = getAllProgress(context)
        return allProgress.find { it.dogId == dogId && it.category == category }
            ?: TrainingProgress(dogId = dogId, category = category)
    }

    fun getAllProgress(context: Context): List<TrainingProgress> {
        val json = prefs.getString(KEY_PROGRESS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<TrainingProgress>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveProgress(context: Context, progress: TrainingProgress) {
        val allProgress = getAllProgress(context).toMutableList()
        allProgress.removeIf { it.dogId == progress.dogId && it.category == progress.category }
        allProgress.add(progress)

        val json = gson.toJson(allProgress)
        prefs.edit().putString(KEY_PROGRESS, json).apply()
    }

    private fun updateProgress(session: TrainingSession) {
        val progress = getProgress(session.dogId, session.category)
        val updatedProgress = progress.updateWithSession(session)
        saveProgress(context, updatedProgress)
    }

    private fun deleteProgress(context: Context, dogId: String, category: TrainingCategory) {
        val allProgress = getAllProgress(context).toMutableList()
        allProgress.removeIf { it.dogId == dogId && it.category == category }

        val json = gson.toJson(allProgress)
        prefs.edit().putString(KEY_PROGRESS, json).apply()
    }

    /**
     * ✅ NEU: Wochenstatistik für einen Hund (Instanz-Methode)
     */
    fun getWeeklyStats(dogId: String): WeeklyStats {
        val allSessions = getAllSessions(context).filter { it.dogId == dogId }

        // Letzte 7 Tage
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val recentSessions = allSessions.filter { it.date >= oneWeekAgo }

        if (recentSessions.isEmpty()) {
            return WeeklyStats()
        }

        val totalMinutes = recentSessions.sumOf { it.durationMinutes }
        val categoriesUsed = recentSessions.map { it.category }.distinct().size
        val avgSuccess = recentSessions.map { it.successRating }.average()

        return WeeklyStats(
            totalSessions = recentSessions.size,
            totalMinutes = totalMinutes,
            categoriesUsed = categoriesUsed,
            averageSuccess = avgSuccess
        )
    }

    // ════════════════════════════════════════════════════════════
    // MILESTONE MANAGEMENT
    // ════════════════════════════════════════════════════════════

    fun getMilestones(dogId: String, category: TrainingCategory): List<Milestone> {
        return getAllMilestones(context).filter {
            it.dogId == dogId && it.category == category
        }
    }

    fun getAllMilestones(context: Context): List<Milestone> {
        val json = prefs.getString(KEY_MILESTONES, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<Milestone>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveMilestone(milestone: Milestone) {
        val milestones = getAllMilestones(context).toMutableList()

        // Duplikate vermeiden
        if (milestones.any {
                it.dogId == milestone.dogId &&
                        it.category == milestone.category &&
                        it.type == milestone.type
            }) {
            return
        }

        milestones.add(milestone)
        val json = gson.toJson(milestones)
        prefs.edit().putString(KEY_MILESTONES, json).apply()
    }

    private fun checkMilestones(session: TrainingSession) {
        val progress = getProgress(session.dogId, session.category)

        // Erste Session
        if (progress.totalSessions == 1) {
            saveMilestone(Milestone(
                dogId = session.dogId,
                category = session.category,
                type = MilestoneType.FIRST_SESSION
            ))
        }

        // 5 Sessions
        if (progress.totalSessions == 5) {
            saveMilestone(Milestone(
                dogId = session.dogId,
                category = session.category,
                type = MilestoneType.FIVE_SESSIONS
            ))
        }

        // 10 Sessions
        if (progress.totalSessions == 10) {
            saveMilestone(Milestone(
                dogId = session.dogId,
                category = session.category,
                type = MilestoneType.TEN_SESSIONS
            ))
        }

        // 20 Sessions
        if (progress.totalSessions == 20) {
            saveMilestone(Milestone(
                dogId = session.dogId,
                category = session.category,
                type = MilestoneType.TWENTY_SESSIONS
            ))
        }

        // 5 Tage Streak
        if (progress.consecutiveDays >= 5) {
            saveMilestone(Milestone(
                dogId = session.dogId,
                category = session.category,
                type = MilestoneType.FIVE_DAY_STREAK
            ))
        }

        // Progress Meilensteine
        when (progress.progressPercentage) {
            50 -> saveMilestone(Milestone(
                dogId = session.dogId,
                category = session.category,
                type = MilestoneType.PROGRESS_50
            ))
            75 -> saveMilestone(Milestone(
                dogId = session.dogId,
                category = session.category,
                type = MilestoneType.PROGRESS_75
            ))
            100 -> saveMilestone(Milestone(
                dogId = session.dogId,
                category = session.category,
                type = MilestoneType.PROGRESS_100
            ))
        }

        // Erfolgs-Serie (5 sehr erfolgreiche Sessions hintereinander)
        val recentSessions = getSessionsForCategory(session.dogId, session.category)
            .sortedByDescending { it.date }
            .take(5)

        if (recentSessions.size == 5 && recentSessions.all { it.successRating == 4 }) {
            saveMilestone(Milestone(
                dogId = session.dogId,
                category = session.category,
                type = MilestoneType.SUCCESS_STREAK
            ))
        }
    }

    // ════════════════════════════════════════════════════════════
    // CONTEXT HELPER
    // ════════════════════════════════════════════════════════════

    companion object {
        private const val PREFS_NAME = "training_diary"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_MILESTONES = "milestones"

        // ✅ Statische Funktionen für Export-Manager
        fun getAllSessions(context: Context): List<TrainingSession> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
            return try {
                val type = object : TypeToken<List<TrainingSession>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

        fun getAllProgress(context: Context): List<TrainingProgress> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_PROGRESS, "[]") ?: "[]"
            return try {
                val type = object : TypeToken<List<TrainingProgress>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

        fun getAllMilestones(context: Context): List<Milestone> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_MILESTONES, "[]") ?: "[]"
            return try {
                val type = object : TypeToken<List<Milestone>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

        fun getSessionsForCategory(context: Context, dogId: String, category: TrainingCategory): List<TrainingSession> {
            return getAllSessions(context).filter {
                it.dogId == dogId && it.category == category
            }
        }

        fun getProgress(context: Context, dogId: String, category: TrainingCategory): TrainingProgress {
            val allProgress = getAllProgress(context)
            return allProgress.find { it.dogId == dogId && it.category == category }
                ?: TrainingProgress(dogId = dogId, category = category)
        }
    }
}
