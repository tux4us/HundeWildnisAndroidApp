package com.hundewildnis.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

// ════════════════════════════════════════════════════════════════
// INTERVALL-TIMER MANAGER – Persistenz der Sessions
// Datei: IntervalTimerManager.kt
// ════════════════════════════════════════════════════════════════

object IntervalTimerManager {

    private const val PREFS_NAME   = "interval_timer_prefs"
    private const val KEY_SESSIONS = "interval_sessions"

    private val gson: Gson = GsonBuilder().create()

    // ── Sessions laden ───────────────────────────────────────────

    fun getAllSessions(context: Context): List<IntervalTimerSession> {
        val prefs = prefs(context)
        val json  = prefs.getString(KEY_SESSIONS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<IntervalTimerSession>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSessionsForDog(context: Context, dogId: String): List<IntervalTimerSession> =
        getAllSessions(context).filter { it.dogId == dogId }

    // ── Session speichern ────────────────────────────────────────

    fun saveSession(context: Context, session: IntervalTimerSession) {
        val all = getAllSessions(context).toMutableList()
        // Update wenn ID bereits vorhanden, sonst prepend
        val idx = all.indexOfFirst { it.id == session.id }
        if (idx >= 0) all[idx] = session else all.add(0, session)
        persist(context, all)
    }

    // ── Session löschen ──────────────────────────────────────────

    fun deleteSession(context: Context, sessionId: String) {
        val updated = getAllSessions(context).filterNot { it.id == sessionId }
        persist(context, updated)
    }

    fun deleteAllForDog(context: Context, dogId: String) {
        val updated = getAllSessions(context).filterNot { it.dogId == dogId }
        persist(context, updated)
    }

    // ── CSV-Export ───────────────────────────────────────────────

    fun toCsv(sessions: List<IntervalTimerSession>): String {
        val sb = StringBuilder()
        sb.appendLine(IntervalTimerSession.CSV_HEADER)
        sessions.forEach { sb.appendLine(it.toCsvRow()) }
        return sb.toString()
    }

    // ── Hilfsmethoden ────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun persist(context: Context, sessions: List<IntervalTimerSession>) {
        prefs(context).edit()
            .putString(KEY_SESSIONS, gson.toJson(sessions))
            .apply()
    }
}
