package com.hundewildnis.app

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

/**
 * VitalManager — Singleton für alle Vitalwert-Operationen.
 *
 * Speicherung:
 *  - SharedPreferences "vital_records" → schneller Listenzugriff
 *  - DogProfile.vitalRecords → mitgesichert im Backup
 *
 * Beide Stores werden bei jedem Schreiben synchron gehalten.
 */
object VitalManager {

    private const val TAG = "VitalManager"
    private const val PREFS_NAME = "vital_records"
    private const val KEY_RECORDS = "records"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // ════════════════════════════════════════════════════════════════
    // LESEN
    // ════════════════════════════════════════════════════════════════

    /** Alle Messungen aller Hunde, neueste zuerst */
    fun getAll(context: Context): List<VitalRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<VitalRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden: ${e.message}")
            emptyList()
        }
    }

    /** Alle Messungen für einen bestimmten Hund, neueste zuerst */
    fun getForDog(context: Context, dogId: String): List<VitalRecord> {
        return getAll(context)
            .filter { it.dogId == dogId }
            .sortedByDescending { it.timestamp }
    }

    /** Einzelne Messung per ID */
    fun getById(context: Context, recordId: String): VitalRecord? {
        return getAll(context).find { it.id == recordId }
    }

    // ════════════════════════════════════════════════════════════════
    // SCHREIBEN
    // ════════════════════════════════════════════════════════════════

    /** Neue Messung speichern */
    fun save(context: Context, record: VitalRecord) {
        val all = getAll(context).toMutableList()
        val existingIndex = all.indexOfFirst { it.id == record.id }
        if (existingIndex >= 0) {
            all[existingIndex] = record
        } else {
            all.add(0, record) // neueste zuerst
        }
        persist(context, all)
        syncToDogProfile(context, record.dogId)
        Log.d(TAG, "Gespeichert: ${record.id} für Hund ${record.dogName}")
    }

    /** Messung aktualisieren */
    fun update(context: Context, record: VitalRecord) {
        save(context, record)
    }

    /** Messung löschen */
    fun delete(context: Context, recordId: String) {
        val all = getAll(context).toMutableList()
        val removed = all.removeAll { it.id == recordId }
        if (removed) {
            persist(context, all)
            // Sync aller betroffenen Profile (wir kennen dogId nur über den Record)
            // → Alle Profile neu synchronisieren
            syncAllDogProfiles(context, all)
            Log.d(TAG, "Gelöscht: $recordId")
        }
    }

    // ════════════════════════════════════════════════════════════════
    // BACKUP-INTEGRATION
    // ════════════════════════════════════════════════════════════════

    /** Alle Records für Backup-Export */
    fun exportForBackup(context: Context): List<VitalRecord> = getAll(context)

    /**
     * Records aus Backup wiederherstellen.
     * mergeMode: true = nur neue hinzufügen, false = alles ersetzen
     */
    fun importFromBackup(
        context: Context,
        records: List<VitalRecord>,
        mergeMode: Boolean = false
    ) {
        if (mergeMode) {
            val existing = getAll(context)
            val existingIds = existing.map { it.id }.toSet()
            val merged = (existing + records.filter { it.id !in existingIds })
                .sortedByDescending { it.timestamp }
            persist(context, merged)
        } else {
            persist(context, records.sortedByDescending { it.timestamp })
        }
        syncAllDogProfiles(context, getAll(context))
    }

    // ════════════════════════════════════════════════════════════════
    // CSV-EXPORT
    // ════════════════════════════════════════════════════════════════

    /**
     * CSV-String für alle Messungen eines Hundes.
     * UTF-8 mit BOM für Excel-Kompatibilität.
     */
    fun toCsvString(context: Context, dogId: String): String {
        val records = getForDog(context, dogId)
        val sb = StringBuilder()
        sb.appendLine(VitalRecord.CSV_HEADER)
        records.forEach { sb.appendLine(it.toCsvRow()) }
        return sb.toString()
    }

    /**
     * CSV-String für alle Hunde (gesamt).
     */
    fun toCsvStringAll(context: Context): String {
        val records = getAll(context).sortedByDescending { it.timestamp }
        val sb = StringBuilder()
        sb.appendLine(VitalRecord.CSV_HEADER)
        records.forEach { sb.appendLine(it.toCsvRow()) }
        return sb.toString()
    }

    // ════════════════════════════════════════════════════════════════
    // PRIVAT
    // ════════════════════════════════════════════════════════════════

    private fun persist(context: Context, records: List<VitalRecord>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECORDS, gson.toJson(records)).apply()
    }

    /**
     * Synchronisiert die Messungen eines Hundes in sein DogProfile.
     * Damit sind sie automatisch im Backup enthalten.
     */
    private fun syncToDogProfile(context: Context, dogId: String) {
        if (dogId.isEmpty()) return
        val profile = DogProfileManager.getProfile(context, dogId) ?: return
        val records = getAll(context).filter { it.dogId == dogId }
        val updated = profile.copy(vitalRecords = records)
        DogProfileManager.saveProfile(context, updated)
    }

    private fun syncAllDogProfiles(context: Context, allRecords: List<VitalRecord>) {
        val byDog = allRecords.groupBy { it.dogId }
        DogProfileManager.getAllProfiles(context).forEach { profile ->
            val records = byDog[profile.id] ?: emptyList()
            DogProfileManager.saveProfile(context, profile.copy(vitalRecords = records))
        }
    }
}
