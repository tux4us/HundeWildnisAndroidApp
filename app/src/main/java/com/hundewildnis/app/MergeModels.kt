package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// MERGE-MODELLE FÜR BACKUP-ZUSAMMENFÜHRUNG
// ════════════════════════════════════════════════════════════════

/**
 * Strategie, die beim Importieren eines Backups angewendet wird.
 *
 * ASK_USER     → Konflikte erkennen, User entscheiden lassen
 * MERGE_ALL    → Automatisch zusammenführen (neuester Timestamp gewinnt)
 * REPLACE_ALL  → Alle vorhandenen Daten durch Backup ersetzen
 * KEEP_EXISTING → Backup ignorieren, vorhandene Daten behalten
 */
enum class MergeStrategy {
    ASK_USER,
    MERGE_ALL,
    REPLACE_ALL,
    KEEP_EXISTING
}

/**
 * Datenkategorie, in der ein Konflikt aufgetreten ist.
 */
enum class ConflictType {
    PROFILE,
    DIARY_ENTRY,
    WALKING_SPEED,
    EMERGENCY_DATA
}

/**
 * Beschreibt einen einzelnen Konflikt zwischen Backup- und Gerätedaten.
 *
 * [backupVersion]   – Objekt aus dem Backup-File
 * [existingVersion] – Objekt, das aktuell auf dem Gerät gespeichert ist
 */
data class DataConflict(
    val type: ConflictType,
    val itemId: String,
    val itemName: String,
    val backupVersion: Any,
    val existingVersion: Any
)

/**
 * Ergebnis eines Restore-Vorgangs.
 *
 * Success            → Fertig, keine weiteren Aktionen nötig
 * NeedsUserDecision  → Konflikte gefunden, User muss wählen
 * Error              → Technischer Fehler
 * Cancelled          → User hat abgebrochen
 */
sealed class RestoreResult {
    data class Success(val message: String) : RestoreResult()
    data class NeedsUserDecision(
        val conflicts: List<DataConflict>,
        val backupData: BackupData,
        val existingData: BackupData
    ) : RestoreResult()
    data class Error(val message: String) : RestoreResult()
    object Cancelled : RestoreResult()
}
