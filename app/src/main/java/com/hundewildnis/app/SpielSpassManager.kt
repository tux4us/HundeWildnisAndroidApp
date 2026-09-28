package com.hundewildnis.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ════════════════════════════════════════════════════════════════
// SPIEL & SPASS MANAGER – Persistenz der Sessions + Statistik
// ════════════════════════════════════════════════════════════════

object SpielSpassManager {

    private const val PREFS_NAME = "spiel_spass"
    private const val KEY_SESSIONS = "sessions"
    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ════════════════════════════════════════════════════════════
    // SESSION MANAGEMENT
    // ════════════════════════════════════════════════════════════

    fun saveSession(context: Context, session: SpielSession) {
        val sessions = getAllSessions(context).toMutableList()
        sessions.add(session)
        persist(context, sessions)
    }

    fun getAllSessions(context: Context): List<SpielSession> {
        val json = getPrefs(context).getString(KEY_SESSIONS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<SpielSession>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getSessionsForDog(context: Context, dogId: String): List<SpielSession> {
        return getAllSessions(context)
            .filter { it.dogId == dogId }
            .sortedByDescending { it.datum }
    }

    fun deleteSession(context: Context, sessionId: String): Boolean {
        val sessions = getAllSessions(context).toMutableList()
        val removed = sessions.removeIf { it.id == sessionId }
        if (removed) persist(context, sessions)
        return removed
    }

    fun deleteAllDataForDog(context: Context, dogId: String) {
        val sessions = getAllSessions(context).toMutableList()
        sessions.removeIf { it.dogId == dogId }
        persist(context, sessions)
    }

    private fun persist(context: Context, sessions: List<SpielSession>) {
        val json = gson.toJson(sessions)
        getPrefs(context).edit().putString(KEY_SESSIONS, json).apply()
    }

    // ════════════════════════════════════════════════════════════
    // STATISTIK
    // ════════════════════════════════════════════════════════════

    fun getStatistikForDog(context: Context, dogId: String): SpielStatistik {
        val sessions = getSessionsForDog(context, dogId)
        if (sessions.isEmpty()) {
            return SpielStatistik(dogId = dogId)
        }

        val sessionsProOrt = sessions
            .groupingBy { it.ort.name }
            .eachCount()

        val sessionsProFokus = mutableMapOf<String, Int>()
        sessions.forEach { session ->
            val aktivitaet = SpielAktivitaetCatalog.findById(session.aktivitaetId)
            aktivitaet?.fokus?.forEach { fokus ->
                sessionsProFokus[fokus.name] = (sessionsProFokus[fokus.name] ?: 0) + 1
            }
        }

        val topAktivitaeten = sessions
            .groupingBy { it.aktivitaetId }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(10)

        val bewertungen = sessions.filter { it.bewertung > 0 }.map { it.bewertung }
        val avgBewertung = if (bewertungen.isNotEmpty()) bewertungen.average() else 0.0

        return SpielStatistik(
            dogId = dogId,
            gesamtSessions = sessions.size,
            sessionsProOrt = sessionsProOrt,
            sessionsProFokus = sessionsProFokus,
            topAktivitaeten = topAktivitaeten,
            letzteSession = sessions.maxOf { it.datum },
            durchschnittsBewertung = avgBewertung
        )
    }

    // ════════════════════════════════════════════════════════════
    // STATISCHE HELFER (für BackupManager / ExportManager)
    // ════════════════════════════════════════════════════════════

    fun restoreSessions(context: Context, sessions: List<SpielSession>, merge: Boolean) {
        if (merge) {
            val existing = getAllSessions(context).toMutableList()
            val existingIds = existing.map { it.id }.toSet()
            existing.addAll(sessions.filter { it.id !in existingIds })
            persist(context, existing)
        } else {
            persist(context, sessions)
        }
    }
}
