package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// SELECTIVE BACKUP - DATA CLASSES
// Neue Datei: SelectiveBackupTypes.kt
// ════════════════════════════════════════════════════════════════

enum class BackupDataType(
    val displayName: String,
    val description: String
) {
    PROFILES(
        "Hunde-Profile",
        "Name, Geburtsdatum, Chip-Nr, Foto, etc."
    ),

    DIARY(
        "Trainingstagebuch",
        "Trainingseinheiten, Fortschritt, Meilensteine"
    ),

    WALKING_SPEED(
        "Tempo-Messungen",
        "Walking Speed Aufzeichnungen"
    ),

    NOTES(
        "Notizen",
        "Alle Notizen mit Fotos"
    ),

    VOICE(
        "Sprachaufnahmen",
        "Kommunikations-Sessions"
    ),

    EMERGENCY(
        "Notfall-Meldungen",
        "Notfallberichte mit Fotos"
    ),

    NASENARBEIT(
        "Nasenarbeit",
        "Alle Nasenarbeit-Sessions und Fortschritte"
    ),

    FAVORITES(
        "Favoriten",
        "Markierte Orte auf der Karte"
    ),

    APP_SETTINGS(
        "App-Einstellungen",
        "Theme, Benachrichtigungen, etc."
    )
}

enum class MergeMode(
    val displayName: String,
    val description: String
) {
    ADD_NEW(
        "Hinzufügen",
        "Neue Daten hinzufügen, vorhandene behalten"
    ),

    REPLACE_ALL(
        "Ersetzen",
        "Alle vorhandenen Daten löschen und durch Backup ersetzen"
    ),

    MERGE_BY_TIMESTAMP(
        "Zusammenführen",
        "Neueste Version jedes Eintrags behalten"
    )
}

data class BackupContainer(
    val version: String,
    val timestamp: Long,
    val includedTypes: List<String>,
    val data: MutableMap<String, Any?>
)

data class SelectiveBackupContainer(
    val version: String,
    val timestamp: Long,
    val includedTypes: List<String>,
    val data: MutableMap<String, Any?>,
    val photoMapping: Map<String, String> = emptyMap()
)

sealed class SelectiveRestoreResult {
    data class Success(val message: String) : SelectiveRestoreResult()
    data class Error(val message: String) : SelectiveRestoreResult()
    object Cancelled : SelectiveRestoreResult()
}
