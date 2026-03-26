package com.hundewildnis.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Backup-Datenstruktur für komplettes App-Backup
 */
@Parcelize
data class BackupData(
    val version: String,              // App-Version
    val backupVersion: Int = 1,       // Backup-Format-Version
    val timestamp: Long,              // Backup-Zeitstempel
    val dogs: List<DogProfile>,       // Alle Hunde-Profile
    val settings: Map<String, String>, // App-Einstellungen
    val notes: List<DailyNoteData>?,  // Tagesnotizen (optional)
    val photoMapping: Map<String, String> // URI -> Backup-Pfad Mapping
) : Parcelable

/**
 * Vereinfachte Notizen-Struktur für Backup
 */
@Parcelize
data class DailyNoteData(
    val id: String,
    val date: String,
    val text: String,
    val photoUris: List<String>
) : Parcelable

/**
 * Backup-Metadaten
 */
@Parcelize
data class BackupMetadata(
    val appVersion: String,
    val backupVersion: Int,
    val timestamp: Long,
    val deviceName: String,
    val itemCount: BackupItemCount
) : Parcelable

@Parcelize
data class BackupItemCount(
    val dogs: Int,
    val photos: Int,
    val notes: Int
) : Parcelable
