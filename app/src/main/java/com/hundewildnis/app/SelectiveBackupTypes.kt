package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// SELECTIVE BACKUP - DATA CLASSES
// Neue Datei: SelectiveBackupTypes.kt
// ════════════════════════════════════════════════════════════════

enum class BackupDataType {
    PROFILES, DIARY, WALKING_SPEED, NOTES, VOICE, EMERGENCY, NASENARBEIT,
    FAVORITES, APP_SETTINGS, WEATHER_FAVORITES, VITAL_RECORDS, INTERVAL_TIMER,
    PLANT_PHOTOS, PLANNED_SESSIONS, PLAY_FUN;

    fun displayName(context: android.content.Context): String = when (this) {
        PROFILES -> context.getString(R.string.backup_type_profiles)
        DIARY -> context.getString(R.string.backup_type_diary)
        WALKING_SPEED -> context.getString(R.string.backup_type_walking_speed)
        NOTES -> context.getString(R.string.backup_type_notes)
        VOICE -> context.getString(R.string.backup_type_voice)
        EMERGENCY -> context.getString(R.string.backup_type_emergency)
        NASENARBEIT -> context.getString(R.string.backup_type_nasenarbeit)
        FAVORITES -> context.getString(R.string.backup_type_favorites)
        APP_SETTINGS -> context.getString(R.string.backup_type_app_settings)
        WEATHER_FAVORITES -> context.getString(R.string.backup_type_weather_favorites)
        VITAL_RECORDS -> context.getString(R.string.backup_type_vital_records)
        INTERVAL_TIMER -> context.getString(R.string.backup_type_interval_timer)
        PLANT_PHOTOS -> context.getString(R.string.backup_type_plant_photos)
        PLANNED_SESSIONS -> context.getString(R.string.backup_type_planned_sessions)
        PLAY_FUN -> context.getString(R.string.backup_type_play_fun)
    }

    fun description(context: android.content.Context): String = when (this) {
        PROFILES -> context.getString(R.string.backup_type_profiles_desc)
        DIARY -> context.getString(R.string.backup_type_diary_desc)
        WALKING_SPEED -> context.getString(R.string.backup_type_walking_speed_desc)
        NOTES -> context.getString(R.string.backup_type_notes_desc)
        VOICE -> context.getString(R.string.backup_type_voice_desc)
        EMERGENCY -> context.getString(R.string.backup_type_emergency_desc)
        NASENARBEIT -> context.getString(R.string.backup_type_nasenarbeit_desc)
        FAVORITES -> context.getString(R.string.backup_type_favorites_desc)
        APP_SETTINGS -> context.getString(R.string.backup_type_app_settings_desc)
        WEATHER_FAVORITES -> context.getString(R.string.backup_type_weather_favorites_desc)
        VITAL_RECORDS -> context.getString(R.string.backup_type_vital_records_desc)
        INTERVAL_TIMER -> context.getString(R.string.backup_type_interval_timer_desc)
        PLANT_PHOTOS -> context.getString(R.string.backup_type_plant_photos_desc)
        PLANNED_SESSIONS -> context.getString(R.string.backup_type_planned_sessions_desc)
        PLAY_FUN -> context.getString(R.string.backup_type_play_fun_desc)
    }
}

enum class MergeMode {
    ADD_NEW, REPLACE_ALL, MERGE_BY_TIMESTAMP;

    fun displayName(context: android.content.Context): String = when (this) {
        ADD_NEW -> context.getString(R.string.merge_mode_add_new)
        REPLACE_ALL -> context.getString(R.string.merge_mode_replace_all)
        MERGE_BY_TIMESTAMP -> context.getString(R.string.merge_mode_merge_by_timestamp)
    }

    fun description(context: android.content.Context): String = when (this) {
        ADD_NEW -> context.getString(R.string.merge_mode_add_new_desc)
        REPLACE_ALL -> context.getString(R.string.merge_mode_replace_all_desc)
        MERGE_BY_TIMESTAMP -> context.getString(R.string.merge_mode_merge_by_timestamp_desc)
    }
}

data class BackupContainer(
    val version: String = "",
    val timestamp: Long = 0L,
    val includedTypes: List<String> = emptyList(),
    val data: MutableMap<String, Any?> = mutableMapOf()
)

data class SelectiveBackupContainer(
    val version: String = "",
    val timestamp: Long = 0L,
    val includedTypes: List<String> = emptyList(),
    val data: MutableMap<String, Any?> = mutableMapOf(),
    val photoMapping: Map<String, String> = emptyMap()
)

sealed class SelectiveRestoreResult {
    data class Success(val message: String) : SelectiveRestoreResult()
    data class Error(val message: String) : SelectiveRestoreResult()
    object Cancelled : SelectiveRestoreResult()
}
