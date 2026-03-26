package com.hundewildnis.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private const val BACKUP_EXTENSION = ".hundebackup"
    private const val BACKUP_DIR = "HundeWildnis_Backups"
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

        // Backup-Verzeichnis erstellen
        val backupDir = File(context.cacheDir, "backup_temp")
        if (backupDir.exists()) {
            backupDir.deleteRecursively()
        }
        backupDir.mkdirs()

        // Fotos-Verzeichnis
        val photosDir = File(backupDir, PHOTOS_DIR)
        photosDir.mkdirs()

        // 1. Sammle alle Daten
        val dogs = DogProfileManager.getAllProfiles(context)
        val settings = collectSettings(context)
        val notes = collectNotes(context)

        // 2. Kopiere Fotos und erstelle Mapping
        val photoMapping = mutableMapOf<String, String>()

        dogs.forEachIndexed { index, dog ->
            if (dog.photoUri.isNotEmpty()) {
                try {
                    val sourceUri = Uri.parse(dog.photoUri)
                    val photoFile = File(photosDir, "dog_${index}_${timestamp}.jpg")
                    copyUriToFile(context, sourceUri, photoFile)
                    photoMapping[dog.photoUri] = "${PHOTOS_DIR}/${photoFile.name}"
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Notizen-Fotos (falls vorhanden)
        notes?.forEachIndexed { noteIndex, note ->
            note.photoUris.forEachIndexed { photoIndex, uriString ->
                try {
                    val sourceUri = Uri.parse(uriString)
                    val photoFile = File(photosDir, "note_${noteIndex}_${photoIndex}_${timestamp}.jpg")
                    copyUriToFile(context, sourceUri, photoFile)
                    photoMapping[uriString] = "${PHOTOS_DIR}/${photoFile.name}"
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 3. Erstelle Backup-Daten
        val backupData = BackupData(
            version = getAppVersion(context),
            backupVersion = 1,
            timestamp = timestamp,
            dogs = dogs,
            settings = settings,
            notes = notes,
            photoMapping = photoMapping
        )

        // 4. Erstelle Metadaten
        val metadata = BackupMetadata(
            appVersion = getAppVersion(context),
            backupVersion = 1,
            timestamp = timestamp,
            deviceName = Build.MODEL,
            itemCount = BackupItemCount(
                dogs = dogs.size,
                photos = photoMapping.size,
                notes = notes?.size ?: 0
            )
        )

        // 5. Schreibe JSON-Dateien
        File(backupDir, METADATA_FILE).writeText(gson.toJson(metadata))
        File(backupDir, DATA_FILE).writeText(gson.toJson(backupData))

        // 6. Erstelle ZIP-Datei
        val outputFile = File(getBackupDirectory(context), fileName)
        zipDirectory(backupDir, outputFile)

        // 7. Aufräumen
        backupDir.deleteRecursively()

        return outputFile
    }

    /**
     * Stellt ein Backup wieder her
     */
    fun restoreBackup(context: Context, backupFile: File): Boolean {
        return try {
            // 1. Temporäres Verzeichnis
            val tempDir = File(context.cacheDir, "restore_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()

            // 2. ZIP entpacken
            unzipFile(backupFile, tempDir)

            // 3. Metadaten lesen
            val metadataFile = File(tempDir, METADATA_FILE)
            if (!metadataFile.exists()) {
                throw Exception("Ungültiges Backup: Metadaten fehlen")
            }

            // 4. Backup-Daten lesen
            val dataFile = File(tempDir, DATA_FILE)
            if (!dataFile.exists()) {
                throw Exception("Ungültiges Backup: Daten fehlen")
            }

            val backupData = gson.fromJson(dataFile.readText(), BackupData::class.java)

            // 5. Fotos wiederherstellen
            val restoredPhotoMapping = mutableMapOf<String, String>()
            val photosDir = File(tempDir, PHOTOS_DIR)

            if (photosDir.exists()) {
                backupData.photoMapping.forEach { (originalUri, backupPath) ->
                    val sourceFile = File(tempDir, backupPath)
                    if (sourceFile.exists()) {
                        // Kopiere Foto in App-Verzeichnis
                        val destFile = File(context.filesDir, "photos/${sourceFile.name}")
                        destFile.parentFile?.mkdirs()
                        sourceFile.copyTo(destFile, overwrite = true)

                        // Erstelle Content-URI
                        val newUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            destFile
                        )
                        restoredPhotoMapping[originalUri] = newUri.toString()
                    }
                }
            }

            // 6. Hunde-Profile wiederherstellen
            backupData.dogs.forEach { dog ->
                val restoredDog = if (dog.photoUri.isNotEmpty() && restoredPhotoMapping.containsKey(dog.photoUri)) {
                    dog.copy(photoUri = restoredPhotoMapping[dog.photoUri]!!)
                } else {
                    dog
                }
                DogProfileManager.saveProfile(context, restoredDog)
            }

            // 7. Einstellungen wiederherstellen
            restoreSettings(context, backupData.settings)

            // 8. Notizen wiederherstellen (falls vorhanden)
            backupData.notes?.forEach { note ->
                val restoredPhotoUris = note.photoUris.map { uri ->
                    restoredPhotoMapping[uri] ?: uri
                }
                restoreNote(context, note.copy(photoUris = restoredPhotoUris))
            }

            // 9. Aufräumen
            tempDir.deleteRecursively()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sammelt alle App-Einstellungen
     */
    private fun collectSettings(context: Context): Map<String, String> {
        val settings = mutableMapOf<String, String>()

        // Theme-Einstellungen
        val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        settings["is_dark_mode"] = themePrefs.getBoolean("is_dark_mode", false).toString()

        // App-Einstellungen
        val appPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        settings["background_updates_enabled"] = appPrefs.getBoolean("background_updates_enabled", true).toString()
        settings["background_updates_wifi_only"] = appPrefs.getBoolean("background_updates_wifi_only", false).toString()
        settings["notifications_enabled"] = appPrefs.getBoolean("notifications_enabled", true).toString()

        return settings
    }

    /**
     * Stellt Einstellungen wieder her
     */
    private fun restoreSettings(context: Context, settings: Map<String, String>) {
        // Theme
        val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        settings["is_dark_mode"]?.let {
            themePrefs.edit().putBoolean("is_dark_mode", it.toBoolean()).apply()
        }

        // App-Einstellungen
        val appPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        settings["background_updates_enabled"]?.let {
            appPrefs.edit().putBoolean("background_updates_enabled", it.toBoolean()).apply()
        }
        settings["background_updates_wifi_only"]?.let {
            appPrefs.edit().putBoolean("background_updates_wifi_only", it.toBoolean()).apply()
        }
        settings["notifications_enabled"]?.let {
            appPrefs.edit().putBoolean("notifications_enabled", it.toBoolean()).apply()
        }
    }

    /**
     * Sammelt Tagesnotizen (falls vorhanden)
     */
    private fun collectNotes(context: Context): List<DailyNoteData>? {
        // TODO: Implementiere, falls DailyNotesActivity Daten speichert
        return null
    }

    /**
     * Stellt eine Notiz wieder her
     */
    private fun restoreNote(context: Context, note: DailyNoteData) {
        // TODO: Implementiere, falls DailyNotesActivity Daten speichert
    }

    /**
     * Kopiert URI zu Datei
     */
    private fun copyUriToFile(context: Context, uri: Uri, destFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * ZIP-Verzeichnis erstellen
     */
    private fun zipDirectory(sourceDir: File, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                val zipFileName = file.absolutePath.removePrefix(sourceDir.absolutePath).removePrefix("/")
                if (zipFileName.isNotEmpty()) {
                    val entry = ZipEntry("$zipFileName${if (file.isDirectory) "/" else ""}")
                    zos.putNextEntry(entry)
                    if (file.isFile) {
                        file.inputStream().use { it.copyTo(zos) }
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    /**
     * ZIP-Datei entpacken
     */
    private fun unzipFile(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Backup-Verzeichnis ermitteln
     * ✅ Speichert im öffentlichen Downloads-Ordner des Nutzers!
     */
    private fun getBackupDirectory(context: Context): File {
        // Öffentlicher Downloads-Ordner (für ALLE Android-Versionen)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloadsDir, BACKUP_DIR)
        dir.mkdirs()
        return dir
    }

    /**
     * App-Version ermitteln
     */
    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}