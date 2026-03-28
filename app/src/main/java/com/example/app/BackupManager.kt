package com.hundewildnis.app

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private const val TAG = "BackupManager"
    private const val BACKUP_EXTENSION = ".hundebackup"
    private const val METADATA_FILE = "metadata.json"
    private const val DATA_FILE = "backup_data.json"
    private const val PHOTOS_DIR = "photos"

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Erstellt ein komplettes Backup aller App-Daten
     */
    fun createBackup(context: Context): File {
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.GERMAN)
        val fileName = "HundeWildnis_Backup_${dateFormat.format(Date(timestamp))}$BACKUP_EXTENSION"

        // 1. Temporäres Verzeichnis für den Inhalt des Zips
        val tempZipContentDir = File(context.cacheDir, "backup_content")
        if (tempZipContentDir.exists()) tempZipContentDir.deleteRecursively()
        tempZipContentDir.mkdirs()

        try {
            // Fotos-Verzeichnis im Zip
            val photosInZipDir = File(tempZipContentDir, PHOTOS_DIR)
            photosInZipDir.mkdirs()

            // Daten sammeln
            val dogs = DogProfileManager.getAllProfiles(context)
            val settings = collectSettings(context)
            val notes = collectNotes(context)
            val speedSessions = collectSpeedSessions(context)

            val photoMapping = mutableMapOf<String, String>()

            // Hunde-Fotos kopieren
            dogs.forEachIndexed { index, dog ->
                if (dog.photoUri.isNotEmpty()) {
                    try {
                        val sourceUri = Uri.parse(dog.photoUri)
                        val photoFile = File(photosInZipDir, "dog_${index}_${timestamp}.jpg")
                        if (copyUriToFile(context, sourceUri, photoFile)) {
                            photoMapping[dog.photoUri] = "${PHOTOS_DIR}/${photoFile.name}"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler beim Kopieren von Hundefoto: ${e.message}")
                    }
                }
            }

            // Notizen-Fotos kopieren
            notes?.forEachIndexed { noteIndex, note ->
                note.photoUris.forEachIndexed { photoIndex, uriString ->
                    try {
                        val sourceUri = Uri.parse(uriString)
                        val photoFile = File(photosInZipDir, "note_${noteIndex}_${photoIndex}_${timestamp}.jpg")
                        if (copyUriToFile(context, sourceUri, photoFile)) {
                            photoMapping[uriString] = "${PHOTOS_DIR}/${photoFile.name}"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler beim Kopieren von Notizfoto: ${e.message}")
                    }
                }
            }

            // Backup-Daten erstellen
            val backupData = BackupData(
                version = getAppVersion(context),
                backupVersion = 2,
                timestamp = timestamp,
                dogs = dogs,
                settings = settings,
                notes = notes,
                speedSessions = speedSessions,
                photoMapping = photoMapping
            )

            // Metadaten erstellen
            val metadata = BackupMetadata(
                appVersion = getAppVersion(context),
                backupVersion = 2,
                timestamp = timestamp,
                deviceName = Build.MODEL,
                itemCount = BackupItemCount(
                    dogs = dogs.size,
                    photos = photoMapping.size,
                    notes = notes?.size ?: 0,
                    speedSessions = speedSessions?.size ?: 0
                )
            )

            // JSON-Dateien schreiben
            File(tempZipContentDir, METADATA_FILE).writeText(gson.toJson(metadata))
            File(tempZipContentDir, DATA_FILE).writeText(gson.toJson(backupData))

            // ZIP-Datei am Zielort erstellen
            val backupFolder = getBackupDirectory(context)
            val outputFile = File(backupFolder, fileName)
            
            zipDirectory(tempZipContentDir, outputFile)
            
            // Medienscan anstoßen, damit die Datei sofort sichtbar ist
            MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)
            
            Log.d(TAG, "Backup erfolgreich erstellt: ${outputFile.absolutePath}")
            return outputFile

        } finally {
            tempZipContentDir.deleteRecursively()
        }
    }

    /**
     * Stellt ein Backup wieder her
     */
    fun restoreBackup(context: Context, backupFile: File): Boolean {
        Log.d(TAG, "Starte Wiederherstellung von: ${backupFile.absolutePath}")
        val tempUnzipDir = File(context.cacheDir, "restore_unzip")
        if (tempUnzipDir.exists()) tempUnzipDir.deleteRecursively()
        tempUnzipDir.mkdirs()

        return try {
            unzipFile(backupFile, tempUnzipDir)

            val dataFile = File(tempUnzipDir, DATA_FILE)
            if (!dataFile.exists()) {
                Log.e(TAG, "Backup-Datei $DATA_FILE nicht gefunden")
                return false
            }

            val jsonContent = dataFile.readText()
            val backupData = gson.fromJson(jsonContent, BackupData::class.java)

            // 1. Fotos wiederherstellen
            val restoredPhotoMapping = mutableMapOf<String, String>()
            val photosInZipDir = File(tempUnzipDir, PHOTOS_DIR)
            
            if (photosInZipDir.exists()) {
                backupData.photoMapping.forEach { (originalUri, backupPath) ->
                    val sourceFileInZip = File(tempUnzipDir, backupPath)
                    if (sourceFileInZip.exists()) {
                        val destFile = File(context.filesDir, "restored_photos/${sourceFileInZip.name}")
                        destFile.parentFile?.mkdirs()
                        sourceFileInZip.copyTo(destFile, overwrite = true)

                        val newUri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", destFile
                        )
                        restoredPhotoMapping[originalUri] = newUri.toString()
                    }
                }
            }

            // 2. Hunde-Profile
            backupData.dogs.forEach { dog ->
                val restoredDog = if (dog.photoUri.isNotEmpty() && restoredPhotoMapping.containsKey(dog.photoUri)) {
                    dog.copy(photoUri = restoredPhotoMapping[dog.photoUri]!!)
                } else dog
                DogProfileManager.saveProfile(context, restoredDog)
            }

            // 3. Einstellungen
            restoreSettings(context, backupData.settings)

            // 4. Notizen
            backupData.notes?.let { notes ->
                val restoredNotes = notes.map { note ->
                    val restoredUris = note.photoUris.map { restoredPhotoMapping[it] ?: it }
                    note.copy(photoUris = restoredUris)
                }
                saveNotesHistory(context, restoredNotes)
            }

            // 5. Trainingstempo
            backupData.speedSessions?.let { sessions ->
                saveSpeedHistory(context, sessions)
            }

            Log.d(TAG, "Wiederherstellung erfolgreich abgeschlossen")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Wiederherstellung: ${e.message}", e)
            false
        } finally {
            tempUnzipDir.deleteRecursively()
        }
    }

    private fun collectSettings(context: Context): Map<String, String> {
        val settings = mutableMapOf<String, String>()
        val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        settings["is_dark_mode"] = themePrefs.getBoolean("is_dark_mode", false).toString()
        val appPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        settings["background_updates_enabled"] = appPrefs.getBoolean("background_updates_enabled", true).toString()
        settings["notifications_enabled"] = appPrefs.getBoolean("notifications_enabled", true).toString()
        return settings
    }

    private fun restoreSettings(context: Context, settings: Map<String, String>) {
        val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        settings["is_dark_mode"]?.let { themePrefs.edit().putBoolean("is_dark_mode", it.toBoolean()).apply() }
        val appPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        settings["background_updates_enabled"]?.let { appPrefs.edit().putBoolean("background_updates_enabled", it.toBoolean()).apply() }
        settings["notifications_enabled"]?.let { appPrefs.edit().putBoolean("notifications_enabled", it.toBoolean()).apply() }
    }

    private fun collectNotes(context: Context): List<NoteSession>? {
        val prefs = context.getSharedPreferences("notes_history", Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", null) ?: return null
        return try {
            val type = object : TypeToken<List<NoteSession>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) { null }
    }

    private fun saveNotesHistory(context: Context, notes: List<NoteSession>) {
        val prefs = context.getSharedPreferences("notes_history", Context.MODE_PRIVATE)
        prefs.edit().putString("sessions", gson.toJson(notes)).apply()
    }

    private fun collectSpeedSessions(context: Context): List<SpeedSession>? {
        val prefs = context.getSharedPreferences("walking_history", Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", null) ?: return null
        return try {
            val type = object : TypeToken<List<SpeedSession>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) { null }
    }

    private fun saveSpeedHistory(context: Context, sessions: List<SpeedSession>) {
        val prefs = context.getSharedPreferences("walking_history", Context.MODE_PRIVATE)
        prefs.edit().putString("sessions", gson.toJson(sessions)).apply()
    }

    private fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Kopieren von URI $uri: ${e.message}")
            false
        }
    }

    private fun zipDirectory(sourceDir: File, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.absolutePath == sourceDir.absolutePath) return@forEach
                val entryName = file.absolutePath.removePrefix(sourceDir.absolutePath).removePrefix("/")
                val entry = ZipEntry(if (file.isDirectory) "$entryName/" else entryName)
                zos.putNextEntry(entry)
                if (file.isFile) {
                    file.inputStream().use { it.copyTo(zos) }
                }
                zos.closeEntry()
            }
        }
    }

    private fun unzipFile(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Backup-Verzeichnis ermitteln
     * ✅ Speichert im öffentlichen Download-Ordner!
     */
    private fun getBackupDirectory(context: Context): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloadsDir, "HundeWildnis_Backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
    }
}
