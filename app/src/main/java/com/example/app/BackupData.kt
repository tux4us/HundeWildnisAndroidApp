package com.hundewildnis.app

import com.google.gson.annotations.SerializedName

/**
 * Backup-Datenstruktur für komplettes App-Backup
 * Wir nutzen GSON für die Serialisierung im Backup, daher kein Parcelize nötig.
 */
data class BackupData(
    @SerializedName("version") val version: String,
    @SerializedName("backupVersion") val backupVersion: Int = 3,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("dogs") val dogs: List<DogProfile>,
    @SerializedName("settings") val settings: Map<String, String>,
    @SerializedName("notes") val notes: List<NoteSession>?,
    @SerializedName("speedSessions") val speedSessions: List<SpeedSession>?,
    @SerializedName("voiceSessions") val voiceSessions: List<VoiceSession>?,
    @SerializedName("photoMapping") val photoMapping: Map<String, String>
)

/**
 * Backup-Metadaten
 */
data class BackupMetadata(
    @SerializedName("appVersion") val appVersion: String,
    @SerializedName("backupVersion") val backupVersion: Int,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("itemCount") val itemCount: BackupItemCount
)

data class BackupItemCount(
    @SerializedName("dogs") val dogs: Int,
    @SerializedName("photos") val photos: Int,
    @SerializedName("notes") val notes: Int,
    @SerializedName("speedSessions") val speedSessions: Int = 0,
    @SerializedName("voiceSessions") val voiceSessions: Int = 0
)
