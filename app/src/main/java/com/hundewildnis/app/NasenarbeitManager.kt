package com.hundewildnis.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

object NasenarbeitManager {

    private const val PREFS_NAME = "nasenarbeit_data"
    private const val KEY_SESSIONS = "sessions"
    private const val KEY_FORTSCHRITT = "fortschritt"

    private val gson: Gson = GsonBuilder().create()

    // ── SESSIONS ──────────────────────────────────────────────

    fun saveSessions(context: Context, sessions: List<NasenarbeitSession>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSIONS, gson.toJson(sessions))
            .apply()
    }

    fun loadSessions(context: Context): MutableList<NasenarbeitSession> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SESSIONS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<NasenarbeitSession>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun loadSessionsForDog(context: Context, dogId: String): List<NasenarbeitSession> =
        loadSessions(context).filter { it.dogId == dogId }
            .sortedByDescending { it.datum }

    fun addSession(context: Context, session: NasenarbeitSession) {
        val sessions = loadSessions(context)
        sessions.add(0, session)
        saveSessions(context, sessions)
        updateFortschritt(context, session)
    }

    fun deleteSession(context: Context, sessionId: String) {
        val sessions = loadSessions(context).filter { it.id != sessionId }.toMutableList()
        saveSessions(context, sessions)
        // Recalculate fortschritt for affected dog
        val dogId = loadSessions(context).find { it.id == sessionId }?.dogId ?: return
        recalcFortschritt(context, dogId)
    }

    // ── FORTSCHRITT ───────────────────────────────────────────

    fun loadFortschritt(context: Context): MutableList<NasenarbeitFortschritt> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FORTSCHRITT, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<NasenarbeitFortschritt>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun loadFortschrittForDog(context: Context, dogId: String): NasenarbeitFortschritt {
        return loadFortschritt(context).find { it.dogId == dogId }
            ?: NasenarbeitFortschritt(dogId = dogId)
    }

    private fun saveFortschritt(context: Context, list: List<NasenarbeitFortschritt>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FORTSCHRITT, gson.toJson(list))
            .apply()
    }

    private fun updateFortschritt(context: Context, session: NasenarbeitSession) {
        val list = loadFortschritt(context).toMutableList()
        val idx = list.indexOfFirst { it.dogId == session.dogId }
        val current = if (idx >= 0) list[idx] else NasenarbeitFortschritt(dogId = session.dogId)

        val updated = current.copy(
            sessionsVerlorensuche = current.sessionsVerlorensuche +
                    if (session.durchgefuehrteDisziplin == NasenarbeitDisziplin.VERLORENSUCHE) 1 else 0,
            sessionsPersonensuche = current.sessionsPersonensuche +
                    if (session.durchgefuehrteDisziplin == NasenarbeitDisziplin.PERSONENSUCHE) 1 else 0,
            sessionsFreisuche = current.sessionsFreisuche +
                    if (session.durchgefuehrteDisziplin == NasenarbeitDisziplin.FREISUCHE) 1 else 0,
            maxSchritte = maxOf(current.maxSchritte, session.durchgefuehrteSchritte ?: 0),
            maxHa = maxOf(current.maxHa, session.durchgefuehrteHa ?: 0.0),
            letzteSession = session.datum
        )

        if (idx >= 0) list[idx] = updated else list.add(updated)
        saveFortschritt(context, list)
    }

    private fun recalcFortschritt(context: Context, dogId: String) {
        val sessions = loadSessionsForDog(context, dogId)
        val fortschritt = NasenarbeitFortschritt(
            dogId = dogId,
            sessionsVerlorensuche = sessions.count { it.durchgefuehrteDisziplin == NasenarbeitDisziplin.VERLORENSUCHE },
            sessionsPersonensuche = sessions.count { it.durchgefuehrteDisziplin == NasenarbeitDisziplin.PERSONENSUCHE },
            sessionsFreisuche = sessions.count { it.durchgefuehrteDisziplin == NasenarbeitDisziplin.FREISUCHE },
            maxSchritte = sessions.mapNotNull { it.durchgefuehrteSchritte }.maxOrNull() ?: 0,
            maxHa = sessions.mapNotNull { it.durchgefuehrteHa }.maxOrNull() ?: 0.0,
            letzteSession = sessions.firstOrNull()?.datum ?: 0L
        )
        val list = loadFortschritt(context).toMutableList()
        val idx = list.indexOfFirst { it.dogId == dogId }
        if (idx >= 0) list[idx] = fortschritt else list.add(fortschritt)
        saveFortschritt(context, list)
    }

    // ── BACKUP SUPPORT ─────────────────────────────────────────

    fun exportForBackup(context: Context): Pair<List<NasenarbeitSession>, List<NasenarbeitFortschritt>> {
        return Pair(loadSessions(context), loadFortschritt(context))
    }

    fun importFromBackup(
        context: Context,
        sessions: List<NasenarbeitSession>?,
        fortschritt: List<NasenarbeitFortschritt>?
    ) {
        sessions?.let { saveSessions(context, it.toMutableList()) }
        fortschritt?.let { saveFortschritt(context, it) }
    }

    /**
     * Löscht ALLE Nasenarbeit-Daten für einen bestimmten Hund.
     * Stammdaten (Profil) bleiben erhalten!
     */
    fun deleteAllDataForDog(context: Context, dogId: String) {
        // 1. Sessions filtern
        val allSessions = loadSessions(context)
        val filteredSessions = allSessions.filter { it.dogId != dogId }
        saveSessions(context, filteredSessions)

        // 2. Fortschritt filtern
        val allFortschritt = loadFortschritt(context)
        val filteredFortschritt = allFortschritt.filter { it.dogId != dogId }
        saveFortschritt(context, filteredFortschritt)
    }

    // ── CLEAR ──────────────────────────────────────────────────

    fun clearAllData(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
