package com.hundewildnis.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

// ════════════════════════════════════════════════════════════════
// AKTIVITÄTSKALENDER – PERSISTENZ
// Datei: ActivityCalendarManager.kt
// ════════════════════════════════════════════════════════════════

object ActivityCalendarManager {

    private const val PREFS_NAME = "activity_calendar_prefs"
    private const val KEY_PLANNED = "planned_sessions"

    private val gson: Gson = GsonBuilder().create()

    fun getAllPlanned(context: Context): List<PlannedSession> {
        val json = prefs(context).getString(KEY_PLANNED, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<PlannedSession>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getPlannedForDog(context: Context, dogId: String): List<PlannedSession> =
        getAllPlanned(context).filter { it.dogId == dogId }

    fun getPlannedForDay(context: Context, dayMillis: Long): List<PlannedSession> {
        val dayStart = PlannedSession.startOfDay(dayMillis)
        return getAllPlanned(context).filter {
            PlannedSession.startOfDay(it.plannedDateMillis) == dayStart
        }
    }

    fun getPlannedInRange(context: Context, fromMillis: Long, toMillis: Long): List<PlannedSession> =
        getAllPlanned(context).filter {
            it.plannedDateMillis in fromMillis..toMillis
        }

    fun save(context: Context, session: PlannedSession) {
        val all = getAllPlanned(context).toMutableList()
        val idx = all.indexOfFirst { it.id == session.id }
        if (idx >= 0) all[idx] = session else all.add(session)
        persist(context, all)
    }

    fun delete(context: Context, id: String) {
        persist(context, getAllPlanned(context).filterNot { it.id == id })
    }

    /**
     * Markiert geplante Sessions als erledigt, wenn am selben Tag für
     * dieselbe Kategorie und denselben Hund tatsächlich eine
     * TrainingSession existiert. Wird beim Öffnen des Kalenders aufgerufen.
     */
    fun syncCompletionStatus(context: Context, allTrainingSessions: List<TrainingSession>) {
        val planned = getAllPlanned(context)
        var changed = false

        val updated = planned.map { plan ->
            if (plan.completed) return@map plan
            val dayStart = PlannedSession.startOfDay(plan.plannedDateMillis)
            val dayEnd   = dayStart + 24L * 60 * 60 * 1000

            val match = allTrainingSessions.any {
                it.dogId == plan.dogId &&
                it.category.id == plan.categoryId &&
                it.date in dayStart until dayEnd
            }
            if (match) { changed = true; plan.copy(completed = true) } else plan
        }

        if (changed) persist(context, updated)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun persist(context: Context, sessions: List<PlannedSession>) {
        prefs(context).edit().putString(KEY_PLANNED, gson.toJson(sessions)).apply()
    }
}
