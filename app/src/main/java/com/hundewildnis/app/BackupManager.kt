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
import java.security.MessageDigest
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
        val favorites = FavoritesManager.getAll(context)
        val plannedSessions = ActivityCalendarManager.getAllPlanned(context)

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
            val voiceSessions = collectVoiceSessions(context)
            val emergencyReports = collectEmergencyReports(context)

            val photoMapping = mutableMapOf<String, String>()

            // ✅ NEU: Trainingstagebuch laden
            val trainingSessions = loadTrainingSessions(context)
            val trainingProgress = loadTrainingProgress(context)
            val trainingMilestones = loadTrainingMilestones(context)

            // ✅ NEU: Nasenarbeit laden
            val (nasenarbeitSessions, nasenarbeitFortschritt) = NasenarbeitManager.exportForBackup(context)

            // ✅ NEU: Wetter-Favoriten
            val weatherFavorites = WeatherFavoritesManager.getAll(context)

            // ✅ NEU: Vitalwerte
            val vitalRecords = VitalManager.exportForBackup(context)

            // ✅ NEU: IntervalTimer
            val intervalTimerSessions = IntervalTimerManager.getAllSessions(context)

            // ✅ NEU: Aktivitätskalender
            val plannedSessions = ActivityCalendarManager.getAllPlanned(context)

            // ✅ NEU: Gefahrenpflanzen-Fotos sammeln (relativ für Backup)
            val originalPlantPhotoMap = PlantPhotoManager.getAllPhotos(context)
            val plantPhotoMapForBackup = mutableMapOf<String, List<String>>()

            originalPlantPhotoMap.forEach { (plantId, paths) ->
                val backupPaths = mutableListOf<String>()
                paths.forEachIndexed { idx, path ->
                    val sourceFile = File(path)
                    if (sourceFile.exists()) {
                        // Stabilen Dateinamen ohne Zeitstempel verwenden
                        val fileNameInZip = "plant_${plantId}_${idx}.jpg"
                        val destFile = File(photosInZipDir, fileNameInZip)
                        try {
                            if (sourceFile.copyTo(destFile, overwrite = true).exists()) {
                                backupPaths.add("${PHOTOS_DIR}/$fileNameInZip")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Fehler beim Kopieren von Pflanzenfoto: ${e.message}")
                        }
                    }
                }
                if (backupPaths.isNotEmpty()) {
                    plantPhotoMapForBackup[plantId] = backupPaths
                }
            }


            // Hunde-Fotos kopieren
            dogs.forEachIndexed { index, dog ->
                if (dog.photoUri.isNotEmpty()) {
                    try {
                        val sourceUri = Uri.parse(dog.photoUri)
                        // Stabile ID statt Index/Zeitstempel
                        val photoFile = File(photosInZipDir, "dog_${dog.id}.jpg")
                        if (copyUriToFile(context, sourceUri, photoFile)) {
                            photoMapping[dog.photoUri] = "${PHOTOS_DIR}/${photoFile.name}"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler beim Kopieren von Hundefoto: ${e.message}")
                    }
                }
            }

            // Notizen-Fotos kopieren
            notes?.forEachIndexed { _, note ->
                note.photoUris.forEachIndexed { photoIndex, uriString ->
                    try {
                        val sourceUri = Uri.parse(uriString)
                        // Stabile ID
                        val photoFile = File(photosInZipDir, "note_${note.id}_${photoIndex}.jpg")
                        if (copyUriToFile(context, sourceUri, photoFile)) {
                            photoMapping[uriString] = "${PHOTOS_DIR}/${photoFile.name}"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler beim Kopieren von Notizfoto: ${e.message}")
                    }
                }
            }

            // Notfallmeldung-Fotos kopieren
            emergencyReports?.forEachIndexed { _, report ->
                report.photoUris.forEachIndexed { photoIndex, uriString ->
                    try {
                        val sourceUri = Uri.parse(uriString)
                        // Stabile ID
                        val photoFile = File(photosInZipDir, "emergency_${report.id}_${photoIndex}.jpg")
                        if (copyUriToFile(context, sourceUri, photoFile)) {
                            photoMapping[uriString] = "${PHOTOS_DIR}/${photoFile.name}"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler beim Kopieren von Notfallfoto: ${e.message}")
                    }
                }
            }

            // Nasenarbeit-Fotos kopieren
            nasenarbeitSessions.forEach { session ->
                session.fotoUris.forEachIndexed { photoIndex, uriString ->
                    try {
                        val sourceUri = Uri.parse(uriString)
                        val photoFile = File(photosInZipDir, "nasen_${session.id}_${photoIndex}.jpg")
                        if (copyUriToFile(context, sourceUri, photoFile)) {
                            photoMapping[uriString] = "${PHOTOS_DIR}/${photoFile.name}"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler beim Kopieren von Nasenarbeitfoto: ${e.message}")
                    }
                }
            }

            // Backup-Daten erstellen
            val backupData = BackupData(
                version = getAppVersion(context),
                backupVersion = 9, // Erhöht auf 9: Vitalwerte
                timestamp = timestamp,
                dogs = dogs,
                settings = settings,
                notes = notes,
                speedSessions = speedSessions,
                voiceSessions = voiceSessions,
                emergencyReports = emergencyReports,
                photoMapping = photoMapping,
                // ✅ NEU: Trainingstagebuch
                trainingSessions = trainingSessions,
                trainingProgress = trainingProgress,
                trainingMilestones = trainingMilestones,
                favorites = favorites,
                // ✅ NEU: Nasenarbeit
                nasenarbeitSessions = nasenarbeitSessions,
                nasenarbeitFortschritt = nasenarbeitFortschritt,
                // ✅ NEU: Wetter-Favoriten
                weatherFavorites = weatherFavorites,
                // ✅ NEU: Vitalwerte
                vitalRecords = vitalRecords,
                // ✅ NEU: IntervalTimer
                intervalTimerSessions = intervalTimerSessions,
                // ✅ NEU: Aktivitätskalender
                plannedSessions = plannedSessions,
                // ✅ NEU: Pflanzen Foto Map
                plantPhotoMap = plantPhotoMapForBackup
            )

            // Metadaten erstellen
            val metadata = BackupMetadata(
                appVersion = getAppVersion(context),
                backupVersion = 9,
                timestamp = timestamp,
                deviceName = Build.MODEL,
                itemCount = BackupItemCount(
                    dogs = dogs.size,
                    photos = photoMapping.size,
                    notes = notes?.size ?: 0,
                    speedSessions = speedSessions?.size ?: 0,
                    voiceSessions = voiceSessions?.size ?: 0,
                    emergencyReports = emergencyReports?.size ?: 0,
                    trainingSessions = trainingSessions?.size ?: 0,
                    trainingProgress = trainingProgress?.size ?: 0,
                    trainingMilestones = trainingMilestones?.size ?: 0,
                    favorites = favorites.size,
                    // ✅ NEU: Nasenarbeit
                    nasenarbeitSessions = nasenarbeitSessions.size,
                    nasenarbeitFortschritt = nasenarbeitFortschritt.size,
                    // ✅ NEU: Wetter-Favoriten
                    weatherFavorites = weatherFavorites.size,
                    // ✅ NEU: Vitalwerte
                    vitalRecords = vitalRecords.size,
                    // ✅ NEU: IntervalTimer
                    intervalTimerSessions = intervalTimerSessions.size,
                    // ✅ NEU: Aktivitätskalender
                    plannedSessions = plannedSessions.size,
                    // ✅ NEU: Pflanzen Photo Map
                    plantPhotos = plantPhotoMapForBackup.values.sumOf { it.size }
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
     * Erzeugt einen stabilen Fingerabdruck (SHA-256) der Backup-relevanten Daten,
     * OHNE ein ZIP zu erstellen und OHNE Fotos zu kopieren.
     *
     * Verwendungszweck: WebDAV-Sync-Optimierung (WebDavSyncManager) - ein Upload
     * lohnt sich nur, wenn sich dieser Fingerabdruck seit dem letzten erfolgreichen
     * Sync geändert hat.
     *
     * Wichtig: Enthält bewusst KEINEN Zeitstempel (im Gegensatz zu createBackup()),
     * da sonst zwei inhaltlich identische Zustände unterschiedliche Fingerabdrücke
     * ergeben würden und die Optimierung wirkungslos wäre. Fotos werden über
     * (URI + Dateigröße) statt über ihren vollen Inhalt erfasst - das genügt, um
     * "geändert" von "unverändert" zu unterscheiden, ohne alle Fotos byteweise
     * einlesen zu müssen (was bei vielen/großen Fotos selbst spürbar Akku/Zeit kosten würde).
     */
    fun computeBackupFingerprint(context: Context): String {
        val dogs = DogProfileManager.getAllProfiles(context)
        val settings = collectSettings(context)
        val notes = collectNotes(context)
        val speedSessions = collectSpeedSessions(context)
        val voiceSessions = collectVoiceSessions(context)
        val emergencyReports = collectEmergencyReports(context)
        val trainingSessions = loadTrainingSessions(context)
        val trainingProgress = loadTrainingProgress(context)
        val trainingMilestones = loadTrainingMilestones(context)
        val favorites = FavoritesManager.getAll(context)
        val (nasenarbeitSessions, nasenarbeitFortschritt) = NasenarbeitManager.exportForBackup(context)
        val weatherFavorites = WeatherFavoritesManager.getAll(context)
        val vitalRecords = VitalManager.exportForBackup(context)
        val intervalTimerSessions = IntervalTimerManager.getAllSessions(context)
        val plannedSessions = ActivityCalendarManager.getAllPlanned(context)
        val plantPhotoMap = PlantPhotoManager.getAllPhotos(context)

        val photoFingerprints = mutableListOf<String>()
        fun addPhotoFingerprint(uriString: String) {
            if (uriString.isBlank()) return
            photoFingerprints.add("$uriString:${photoStat(context, uriString)}")
        }
        dogs.forEach { if (it.photoUri.isNotEmpty()) addPhotoFingerprint(it.photoUri) }
        notes?.forEach { note -> note.photoUris.forEach { addPhotoFingerprint(it) } }
        emergencyReports?.forEach { report -> report.photoUris.forEach { addPhotoFingerprint(it) } }
        plantPhotoMap.forEach { (_, paths) -> paths.forEach { addPhotoFingerprint(it) } }

        val fingerprintData = linkedMapOf(
            "dogs" to dogs,
            "settings" to settings,
            "notes" to notes,
            "speedSessions" to speedSessions,
            "voiceSessions" to voiceSessions,
            "emergencyReports" to emergencyReports,
            "trainingSessions" to trainingSessions,
            "trainingProgress" to trainingProgress,
            "trainingMilestones" to trainingMilestones,
            "favorites" to favorites,
            "nasenarbeitSessions" to nasenarbeitSessions,
            "nasenarbeitFortschritt" to nasenarbeitFortschritt,
            "weatherFavorites" to weatherFavorites,
            "vitalRecords" to vitalRecords,
            "intervalTimerSessions" to intervalTimerSessions,
            "plannedSessions" to plannedSessions,
            "photos" to photoFingerprints.sorted()
        )

        val json = gson.toJson(fingerprintData)
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Liefert einen leichten Änderungsindikator ("Größe") für eine Foto-URI,
     * soweit auflösbar - ohne die Datei komplett einzulesen. Bei nicht auflösbaren
     * URIs wird "na" zurückgegeben (führt im Zweifel zu einem zusätzlichen, aber
     * unschädlichen Sync statt zu einem übersehenen Foto-Update).
     */
    private fun photoStat(context: Context, uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val f = File(uri.path ?: return "na")
                // Stabilität: Nur Größe verwenden. lastModified ändert sich bei jedem Sync-Restore,
                // was zu unnötigen Upload-Schleifen führen würde.
                "${f.length()}"
            } else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    "${afd.length}"
                } ?: "na"
            }
        } catch (e: Exception) {
            "na"
        }
    }

    /**
     * Stellt ein Backup wieder her
     */
    fun restoreBackup(context: Context, backupFile: File, mergeMode: Boolean = false): Boolean {
        Log.d(TAG, "Starte Wiederherstellung von: ${backupFile.absolutePath}, mergeMode=$mergeMode")
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
            // ✅ FIX: Bestehende Profile zuerst löschen, damit beim Full-Restore keine
            // Duplikate entstehen, wenn dasselbe Profil mit unterschiedlicher ID vorliegt.
            if (mergeMode) {
                val existing = DogProfileManager.getAllProfiles(context)
                val merged = mergeProfiles(
                    backup = backupData.dogs.map { dog ->
                        if (dog.photoUri.isNotEmpty() && restoredPhotoMapping.containsKey(dog.photoUri))
                            dog.copy(photoUri = restoredPhotoMapping[dog.photoUri]!!)
                        else dog
                    },
                    existing = existing
                )
                existing.forEach { DogProfileManager.deleteProfile(context, it.id) }
                merged.forEach { DogProfileManager.saveProfile(context, it) }
            } else {
                DogProfileManager.getAllProfiles(context).forEach {
                    DogProfileManager.deleteProfile(context, it.id)
                }
                backupData.dogs.forEach { dog ->
                    val restoredDog = if (dog.photoUri.isNotEmpty() && restoredPhotoMapping.containsKey(dog.photoUri)) {
                        dog.copy(photoUri = restoredPhotoMapping[dog.photoUri]!!)
                    } else dog
                    DogProfileManager.saveProfile(context, restoredDog)
                }
            }

            // 3. Einstellungen (immer übernehmen)
            restoreSettings(context, backupData.settings)

            // 4. Notizen
            backupData.notes?.let { notes ->
                val restoredNotes = notes.map { note ->
                    val restoredUris = note.photoUris.map { restoredPhotoMapping[it] ?: it }
                    note.copy(photoUris = restoredUris)
                }
                if (mergeMode) {
                    // ✅ Merge: nur neue Notizen hinzufügen (Deduplizierung per ID)
                    val existing = collectNotes(context) ?: emptyList()
                    val existingIds = existing.map { it.id }.toSet()
                    val merged = (existing + restoredNotes.filter { it.id !in existingIds })
                        .sortedByDescending { it.date }
                    saveNotesHistory(context, merged)
                } else {
                    saveNotesHistory(context, restoredNotes.sortedByDescending { it.date })
                }
            }

            // 5. Trainingstempo
            backupData.speedSessions?.let { sessions ->
                if (mergeMode) {
                    val existing = collectSpeedSessions(context) ?: emptyList()
                    val existingTimes = existing.map { it.startTime }.toSet()
                    val merged = (existing + sessions.filter { it.startTime !in existingTimes })
                        .sortedByDescending { it.startTime }
                    saveSpeedHistory(context, merged)
                } else {
                    saveSpeedHistory(context, sessions.sortedByDescending { it.startTime })
                }
            }

            // 6. Kommunikations-Sessions
            backupData.voiceSessions?.let { sessions ->
                if (mergeMode) {
                    val existing = collectVoiceSessions(context) ?: emptyList()
                    val existingTimes = existing.map { it.startTime }.toSet()
                    val merged = (existing + sessions.filter { it.startTime !in existingTimes })
                        .sortedByDescending { it.startTime }
                    saveVoiceHistory(context, merged)
                } else {
                    saveVoiceHistory(context, sessions.sortedByDescending { it.startTime })
                }
            }

            // 7. Notfallmeldungen
            backupData.emergencyReports?.let { reports ->
                val restoredReports = reports.map { report ->
                    val restoredUris = report.photoUris.map { restoredPhotoMapping[it] ?: it }
                    report.copy(photoUris = restoredUris)
                }
                if (mergeMode) {
                    val existing = collectEmergencyReports(context) ?: emptyList()
                    val existingIds = existing.map { it.id }.toSet()
                    val merged = (existing + restoredReports.filter { it.id !in existingIds })
                        .sortedByDescending { it.timestamp }
                    saveEmergencyHistory(context, merged)
                } else {
                    saveEmergencyHistory(context, restoredReports.sortedByDescending { it.timestamp })
                }
            }

            // 8. Trainingstagebuch
            backupData.trainingSessions?.let { sessions ->
                if (mergeMode) {
                    val existing = loadTrainingSessions(context)
                    val existingIds = existing.map { it.id }.toSet()
                    val merged = (existing + sessions.filter { it.id !in existingIds })
                        .sortedByDescending { it.date }
                    saveTrainingSessions(context, merged)
                } else {
                    saveTrainingSessions(context, sessions.sortedByDescending { it.date })
                }
            }
            backupData.trainingProgress?.let { progressList ->
                if (mergeMode) {
                    val existing = loadTrainingProgress(context)
                    val existingKeys = existing.map { "${it.dogId}_${it.category.name}" }.toSet()
                    val merged = existing + progressList.filter { "${it.dogId}_${it.category.name}" !in existingKeys }
                    saveTrainingProgress(context, merged)
                } else {
                    saveTrainingProgress(context, progressList)
                }
            }
            backupData.trainingMilestones?.let { milestones ->
                if (mergeMode) {
                    val existing = loadTrainingMilestones(context)
                    val existingIds = existing.map { it.id }.toSet()
                    val merged = existing + milestones.filter { it.id !in existingIds }
                    saveTrainingMilestones(context, merged)
                } else {
                    saveTrainingMilestones(context, milestones)
                }
            }

            // 9. Favoriten
            backupData.favorites?.let { favs ->
                if (mergeMode) {
                    val existing    = FavoritesManager.getAll(context)
                    val existingKeys = existing
                        .map { "%.5f_%.5f".format(it.lat, it.lon) }
                        .toSet()
                    favs.filter { "%.5f_%.5f".format(it.lat, it.lon) !in existingKeys }
                        .forEach { FavoritesManager.add(context, it) }
                } else {
                    FavoritesManager.restoreAll(context, favs)
                }
            }

            // 10. Nasenarbeit
            backupData.nasenarbeitSessions?.let { sessions ->
                val restoredSessions = sessions.map { session ->
                    val restoredUris = session.fotoUris.map { restoredPhotoMapping[it] ?: it }
                    // Migration: ältere Backups speicherten den deutschen Anzeigetext
                    // ("Anfänger" etc.) statt des stabilen Enum-Namens.
                    session.copy(
                        fotoUris = restoredUris,
                        levelVerlorensuche = NasenarbeitLevel.fromLegacyStoredName(session.levelVerlorensuche).name,
                        levelPersonensuche = NasenarbeitLevel.fromLegacyStoredName(session.levelPersonensuche).name,
                        levelFreisuche = NasenarbeitLevel.fromLegacyStoredName(session.levelFreisuche).name
                    )
                }
                if (mergeMode) {
                    val existing = NasenarbeitManager.loadSessions(context)
                    val existingIds = existing.map { it.id }.toSet()
                    val merged = (existing + restoredSessions.filter { it.id !in existingIds })
                        .sortedByDescending { it.datum }
                    NasenarbeitManager.saveSessions(context, merged.toMutableList())
                } else {
                    NasenarbeitManager.saveSessions(context, restoredSessions.sortedByDescending { it.datum }.toMutableList())
                }
            }
            backupData.nasenarbeitFortschritt?.let { fortschritt ->
                if (mergeMode) {
                    val existing = NasenarbeitManager.loadFortschritt(context)
                    val existingIds = existing.map { it.dogId }.toSet()
                    val merged = existing + fortschritt.filter { it.dogId !in existingIds }
                    NasenarbeitManager.importFromBackup(context, null, merged)
                } else {
                    NasenarbeitManager.importFromBackup(context, null, fortschritt)
                }
            }

            // 11. Wetter-Favoriten
            backupData.weatherFavorites?.let { favs ->
                if (mergeMode) {
                    val existing = WeatherFavoritesManager.getAll(context)
                    val existingKeys = existing.map { "${it.lat}_${it.lon}" }.toSet()
                    favs.filter { "${it.lat}_${it.lon}" !in existingKeys }.forEach {
                        WeatherFavoritesManager.add(context, it)
                    }
                } else {
                    WeatherFavoritesManager.saveAll(context, favs)
                }
            }

            // 12. Vitalwerte
            backupData.vitalRecords?.let { records ->
                VitalManager.importFromBackup(context, records, mergeMode)
            }

            // 13. ✅ NEU: Intervall-Timer
            backupData.intervalTimerSessions?.forEach { session ->
                IntervalTimerManager.saveSession(context, session)
            }

            // ✅ NEU: Gefahrenpflanzen-Fotos wiederherstellen
            backupData.plantPhotoMap?.forEach { (plantId, backupPaths) ->
                val restoredPaths = backupPaths.mapNotNull { backupPath ->
                    val sourceFile = File(tempUnzipDir, backupPath)
                    if (!sourceFile.exists()) return@mapNotNull null
                    val destFile = File(context.filesDir, "plant_photos/${sourceFile.name}")
                    destFile.parentFile?.mkdirs()
                    try {
                        sourceFile.copyTo(destFile, overwrite = true)
                        destFile.absolutePath
                    } catch (e: Exception) {
                        Log.e(TAG, "Plant photo restore error: ${e.message}")
                        null
                    }
                }
                if (restoredPaths.isNotEmpty()) {
                    PlantPhotoManager.restorePhotos(context, plantId, restoredPaths, mergeMode)
                }
            }

            backupData.plannedSessions?.forEach { planned ->
                ActivityCalendarManager.save(context, planned)
            }

            // ✅ NEU: Verwaiste Fotos bereinigen, um Datenwachstum zu verhindern
            purgeOrphanFiles(context)

            Log.d(TAG, "Wiederherstellung erfolgreich abgeschlossen")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Wiederherstellung: ${e.message}", e)
            false
        } finally {
            tempUnzipDir.deleteRecursively()
        }
    }

    /**
     * Löscht Fotos in restored_photos, plant_photos und nasenarbeit_photos,
     * die von keinem Objekt mehr referenziert werden.
     */
    fun purgeOrphanFiles(context: Context) {
        Log.d(TAG, "Starte Medienspeicher-Bereinigung (Purge)...")
        val activeUris = mutableSetOf<String>()

        // 1. Alle aktuell referenzierten URIs aus allen Modulen sammeln
        try {
            DogProfileManager.getAllProfiles(context).forEach { if (it.photoUri.isNotEmpty()) activeUris.add(it.photoUri) }
            collectNotes(context)?.forEach { it.photoUris.forEach { uri -> activeUris.add(uri) } }
            collectEmergencyReports(context)?.forEach { it.photoUris.forEach { uri -> activeUris.add(uri) } }
            PlantPhotoManager.getAllPhotos(context).values.forEach { it.forEach { uri -> activeUris.add(uri) } }
            NasenarbeitManager.loadSessions(context).forEach { it.fotoUris.forEach { uri -> activeUris.add(uri) } }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Sammeln aktiver URIs", e)
        }

        // 2. Dateinamen extrahieren, die wir behalten wollen
        val activeFilenames = activeUris.mapNotNull { uriString ->
            try {
                // Extrahiert den Teil nach dem letzten Slash, ignoriert Query-Parameter
                uriString.substringAfterLast('/').substringBefore('?')
            } catch (e: Exception) { null }
        }.toSet()

        Log.d(TAG, "DB-Check abgeschlossen: ${activeFilenames.size} aktive Dateien referenziert.")

        // 3. Verzeichnisse prüfen und Unbekanntes löschen
        val dirs = listOf(
            File(context.filesDir, "restored_photos"),
            File(context.filesDir, "plant_photos"),
            File(context.filesDir, "nasenarbeit_photos")
        )

        var deletedCount = 0
        var freedBytes = 0L

        dirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()
                Log.d(TAG, "Scanne Ordner: ${dir.name} (${files?.size ?: 0} Dateien)")
                files?.forEach { file ->
                    if (file.isFile && file.name !in activeFilenames) {
                        val size = file.length()
                        if (file.delete()) {
                            deletedCount++
                            freedBytes += size
                        } else {
                            Log.w(TAG, "Konnte Datei nicht löschen: ${file.name}")
                        }
                    }
                }
            }
        }
        Log.i(TAG, "Bereinigung abgeschlossen: $deletedCount Dateien gelöscht, ${freedBytes / 1024 / 1024} MB freigegeben.")
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

        // Versuche zuerst die Liste "sessions" (einzelne Notizen)
        val sessionsJson = prefs.getString("sessions", null)
        val sessions: List<NoteSession>? = if (sessionsJson != null) {
            try {
                val type = object : TypeToken<List<NoteSession>>() {}.type
                gson.fromJson(sessionsJson, type)
            } catch (e: Exception) { null }
        } else null

        // Versuche zusätzlich die Liste "notes_history" (falls vorhanden, evtl. Alias oder älteres Format)
        val historyJson = prefs.getString("notes_history", null)
        val history: List<NoteSession>? = if (historyJson != null) {
            try {
                val type = object : TypeToken<List<NoteSession>>() {}.type
                gson.fromJson(historyJson, type)
            } catch (e: Exception) { null }
        } else null

        // Kombiniere beide Listen (Deduplizierung per ID)
        val combined = (sessions ?: emptyList()) + (history ?: emptyList())
        return combined.distinctBy { it.id }.ifEmpty { null }
    }

    private fun loadTrainingSessions(context: Context): List<TrainingSession> {
        val prefs = context.getSharedPreferences("training_diary", Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", "[]") ?: "[]"

        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<TrainingSession>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun loadTrainingProgress(context: Context): List<TrainingProgress> {
        val prefs = context.getSharedPreferences("training_diary", Context.MODE_PRIVATE)
        val json = prefs.getString("progress", "[]") ?: "[]"

        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<TrainingProgress>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun loadTrainingMilestones(context: Context): List<Milestone> {
        val prefs = context.getSharedPreferences("training_diary", Context.MODE_PRIVATE)
        val json = prefs.getString("milestones", "[]") ?: "[]"

        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<Milestone>>() {}.type
            com.google.gson.Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveNotesHistory(context: Context, notes: List<NoteSession>) {
        val prefs = context.getSharedPreferences("notes_history", Context.MODE_PRIVATE)
        val json = gson.toJson(notes)
        prefs.edit()
            .putString("sessions", json)
            .putString("notes_history", json) // Als Fallback/Alias mitspeichern
            .apply()
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

    private fun collectVoiceSessions(context: Context): List<VoiceSession>? {
        val prefs = context.getSharedPreferences("voice_history", Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", null) ?: return null
        return try {
            val type = object : TypeToken<List<VoiceSession>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) { null }
    }

    private fun saveVoiceHistory(context: Context, sessions: List<VoiceSession>) {
        val prefs = context.getSharedPreferences("voice_history", Context.MODE_PRIVATE)
        prefs.edit().putString("sessions", gson.toJson(sessions)).apply()
    }

    private fun collectEmergencyReports(context: Context): List<EmergencyReport>? {
        val prefs = context.getSharedPreferences("emergency_history", Context.MODE_PRIVATE)
        val json = prefs.getString("reports", null) ?: return null
        return try {
            val type = object : TypeToken<List<EmergencyReport>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) { null }
    }

    private fun saveTrainingSessions(context: Context, sessions: List<TrainingSession>) {
        val prefs = context.getSharedPreferences("training_diary", Context.MODE_PRIVATE)
        prefs.edit().putString("sessions", gson.toJson(sessions)).apply()
    }

    private fun saveTrainingProgress(context: Context, progressList: List<TrainingProgress>) {
        val prefs = context.getSharedPreferences("training_diary", Context.MODE_PRIVATE)
        prefs.edit().putString("progress", gson.toJson(progressList)).apply()
    }

    private fun saveTrainingMilestones(context: Context, milestones: List<Milestone>) {
        val prefs = context.getSharedPreferences("training_diary", Context.MODE_PRIVATE)
        prefs.edit().putString("milestones", gson.toJson(milestones)).apply()
    }

    private fun saveEmergencyHistory(context: Context, reports: List<EmergencyReport>) {
        val prefs = context.getSharedPreferences("emergency_history", Context.MODE_PRIVATE)
        prefs.edit().putString("reports", gson.toJson(reports)).apply()
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


    // ════════════════════════════════════════════════════════════════
    // SELECTIVE BACKUP - NEU!
    // ════════════════════════════════════════════════════════════════

    /**
     * Erstellt Backup mit Auswahl welche Daten gesichert werden sollen
     */
    fun createSelectiveBackup(
        context: Context,
        selectedTypes: Set<BackupDataType>
    ): File {
        val timestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.GERMAN)
        // ✅ NEU: .hundebackup (ZIP) statt .json, damit Fotos enthalten sein können
        val fileName = "HundeWildnis_Selective_${dateFormat.format(Date(timestamp))}$BACKUP_EXTENSION"

        // 1. Temporäres Verzeichnis für den Inhalt des Zips
        val tempDir = File(context.cacheDir, "selective_backup_content")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        try {
            val photosInZipDir = File(tempDir, PHOTOS_DIR)
            photosInZipDir.mkdirs()

            val photoMapping = mutableMapOf<String, String>()
            val data = mutableMapOf<String, Any?>()

            // Nur ausgewählte Daten einpacken
            if (BackupDataType.PROFILES in selectedTypes) {
                val profiles = DogProfileManager.getAllProfiles(context)
                data["profiles"] = profiles
                // Fotos sammeln
                profiles.forEachIndexed { index, dog ->
                    if (dog.photoUri.isNotEmpty()) {
                        val photoFile = File(photosInZipDir, "dog_${index}_${timestamp}.jpg")
                        if (copyUriToFile(context, Uri.parse(dog.photoUri), photoFile)) {
                            photoMapping[dog.photoUri] = "${PHOTOS_DIR}/${photoFile.name}"
                        }
                    }
                }
            }

            if (BackupDataType.DIARY in selectedTypes) {
                data["trainingSessions"] = loadTrainingSessions(context)
                data["trainingProgress"] = loadTrainingProgress(context)
                data["trainingMilestones"] = loadTrainingMilestones(context)
            }

            if (BackupDataType.WALKING_SPEED in selectedTypes) {
                data["speedSessions"] = collectSpeedSessions(context)
            }

            if (BackupDataType.NOTES in selectedTypes) {
                val notes = collectNotes(context)
                data["notes"] = notes
                // Fotos sammeln
                notes?.forEachIndexed { noteIndex, note ->
                    note.photoUris.forEachIndexed { photoIndex, uriString ->
                        val photoFile = File(photosInZipDir, "note_${noteIndex}_${photoIndex}_${timestamp}.jpg")
                        if (copyUriToFile(context, Uri.parse(uriString), photoFile)) {
                            photoMapping[uriString] = "${PHOTOS_DIR}/${photoFile.name}"
                        }
                    }
                }
            }

            if (BackupDataType.VOICE in selectedTypes) {
                data["voiceSessions"] = collectVoiceSessions(context)
            }

            if (BackupDataType.EMERGENCY in selectedTypes) {
                val reports = collectEmergencyReports(context)
                data["emergencyReports"] = reports
                // Fotos sammeln
                reports?.forEachIndexed { reportIndex, report ->
                    report.photoUris.forEachIndexed { photoIndex, uriString ->
                        val photoFile = File(photosInZipDir, "emergency_${reportIndex}_${photoIndex}_${timestamp}.jpg")
                        if (copyUriToFile(context, Uri.parse(uriString), photoFile)) {
                            photoMapping[uriString] = "${PHOTOS_DIR}/${photoFile.name}"
                        }
                    }
                }
            }

            if (BackupDataType.NASENARBEIT in selectedTypes) {
                val (sessions, progress) = NasenarbeitManager.exportForBackup(context)
                data["nasenarbeitSessions"] = sessions
                data["nasenarbeitFortschritt"] = progress
            }

            if (BackupDataType.PLAY_FUN in selectedTypes) {
                data["spielSpassSessions"] = SpielSpassManager.getAllSessions(context)
            }

            if (BackupDataType.FAVORITES in selectedTypes) {
                data["favorites"] = FavoritesManager.getAll(context)
            }

            if (BackupDataType.APP_SETTINGS in selectedTypes) {
                data["settings"] = collectSettings(context)
            }

            if (BackupDataType.WEATHER_FAVORITES in selectedTypes) {
                data["weatherFavorites"] = WeatherFavoritesManager.getAll(context)
            }

            if (BackupDataType.VITAL_RECORDS in selectedTypes) {
                data["vitalRecords"] = VitalManager.exportForBackup(context)
            }

            // ✅ NEU
            if (BackupDataType.INTERVAL_TIMER in selectedTypes) {
                data["intervalTimerSessions"] = IntervalTimerManager.getAllSessions(context)
            }

            // ✅ NEU
            if (BackupDataType.PLANNED_SESSIONS in selectedTypes) {
                data["plannedSessions"] = ActivityCalendarManager.getAllPlanned(context)
            }

            // ✅ NEU: Pflanzen Photos
            if (BackupDataType.PLANT_PHOTOS in selectedTypes) {
                val photoMap = PlantPhotoManager.getAllPhotos(context)
                val photoMapping = mutableMapOf<String, List<String>>()
                photoMap.forEach { (plantId, paths) ->
                    val copiedPaths = mutableListOf<String>()
                    paths.forEachIndexed { idx, path ->
                        val src = File(path)
                        if (src.exists()) {
                            val dest = File(tempDir, "${PHOTOS_DIR}/plant_${plantId}_${idx}.jpg")
                            dest.parentFile?.mkdirs()
                            try { src.copyTo(dest, overwrite = true); copiedPaths.add("${PHOTOS_DIR}/plant_${plantId}_${idx}.jpg") }
                            catch (_: Exception) {}
                        }
                    }
                    if (copiedPaths.isNotEmpty()) photoMapping[plantId] = copiedPaths
                }
                data["plantPhotoMap"] = photoMapping
            }

            // Container erstellen
            val backupContainer = SelectiveBackupContainer(
                version = getAppVersion(context),
                timestamp = timestamp,
                includedTypes = selectedTypes.map { it.name },
                data = data,
                photoMapping = photoMapping
            )

            // JSON-Datei im Zip schreiben
            File(tempDir, DATA_FILE).writeText(gson.toJson(backupContainer))

            // ZIP-Datei am Zielort erstellen
            val backupFolder = getBackupDirectory(context)
            val outputFile = File(backupFolder, fileName)

            zipDirectory(tempDir, outputFile)

            // Medienscan anstoßen
            MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)

            Log.d(TAG, "Selective ZIP backup created: ${outputFile.absolutePath}")
            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Error creating selective backup", e)
            throw e
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Stellt Backup mit Auswahl wieder her
     */
    fun restoreSelectiveBackup(
        context: Context,
        backupFile: File,
        selectedTypes: Set<BackupDataType>,
        mergeMode: MergeMode = MergeMode.ADD_NEW
    ): SelectiveRestoreResult {

        Log.d(TAG, "Restoring selective backup: ${backupFile.name}, mode: ${mergeMode.name}")

        val tempDir = File(context.cacheDir, "selective_restore_unzip")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        return try {
            val isZip = backupFile.name.endsWith(BACKUP_EXTENSION)
            val backup: SelectiveBackupContainer
            val photoMapping = mutableMapOf<String, String>()

            if (isZip) {
                unzipFile(backupFile, tempDir)
                val dataFile = File(tempDir, DATA_FILE)
                if (!dataFile.exists()) return SelectiveRestoreResult.Error("❌ Ungültige Backup-Datei")

                val json = dataFile.readText()
                backup = gson.fromJson(json, SelectiveBackupContainer::class.java)

                // Fotos wiederherstellen (analog zum Full Restore)
                val photosInZipDir = File(tempDir, PHOTOS_DIR)
                if (photosInZipDir.exists()) {
                    backup.photoMapping.forEach { (originalUri, backupPath) ->
                        val sourceFileInZip = File(tempDir, backupPath)
                        if (sourceFileInZip.exists()) {
                            val destFile = File(context.filesDir, "restored_photos/${sourceFileInZip.name}")
                            destFile.parentFile?.mkdirs()
                            sourceFileInZip.copyTo(destFile, overwrite = true)

                            val newUri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", destFile
                            )
                            photoMapping[originalUri] = newUri.toString()
                        }
                    }
                }
            } else {
                // Altes JSON-Format (keine Fotos möglich)
                val json = backupFile.readText()
                backup = gson.fromJson(json, SelectiveBackupContainer::class.java)
            }

            // Nur ausgewählte Daten wiederherstellen
            if (BackupDataType.PROFILES in selectedTypes && backup.data.containsKey("profiles")) {
                restoreProfilesSelective(context, backup.data["profiles"], mergeMode, photoMapping)
            }

            if (BackupDataType.DIARY in selectedTypes) {
                if (backup.data.containsKey("trainingSessions")) {
                    restoreTrainingSessionsSelective(context, backup.data["trainingSessions"], mergeMode)
                }
                if (backup.data.containsKey("trainingProgress")) {
                    restoreTrainingProgressSelective(context, backup.data["trainingProgress"], mergeMode)
                }
                if (backup.data.containsKey("trainingMilestones")) {
                    restoreTrainingMilestonesSelective(context, backup.data["trainingMilestones"], mergeMode)
                }
            }

            if (BackupDataType.WALKING_SPEED in selectedTypes && backup.data.containsKey("speedSessions")) {
                restoreSpeedSessionsSelective(context, backup.data["speedSessions"], mergeMode)
            }

            if (BackupDataType.NOTES in selectedTypes && backup.data.containsKey("notes")) {
                restoreNotesSelective(context, backup.data["notes"], mergeMode, photoMapping)
            }

            if (BackupDataType.VOICE in selectedTypes && backup.data.containsKey("voiceSessions")) {
                restoreVoiceSessionsSelective(context, backup.data["voiceSessions"], mergeMode)
            }

            if (BackupDataType.EMERGENCY in selectedTypes && backup.data.containsKey("emergencyReports")) {
                restoreEmergencyReportsSelective(context, backup.data["emergencyReports"], mergeMode, photoMapping)
            }

            if (BackupDataType.NASENARBEIT in selectedTypes) {
                if (backup.data.containsKey("nasenarbeitSessions")) {
                    restoreNasenarbeitSessionsSelective(context, backup.data["nasenarbeitSessions"], mergeMode)
                }
                if (backup.data.containsKey("nasenarbeitFortschritt")) {
                    restoreNasenarbeitFortschrittSelective(context, backup.data["nasenarbeitFortschritt"], mergeMode)
                }
            }

            if (BackupDataType.FAVORITES in selectedTypes && backup.data.containsKey("favorites")) {
                restoreFavoritesSelective(context, backup.data["favorites"], mergeMode)
            }

            if (BackupDataType.APP_SETTINGS in selectedTypes && backup.data.containsKey("settings")) {
                val settingsData = backup.data["settings"]
                if (settingsData is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    restoreSettings(context, settingsData as Map<String, String>)
                }
            }

            if (BackupDataType.WEATHER_FAVORITES in selectedTypes && backup.data.containsKey("weatherFavorites")) {
                restoreWeatherFavoritesSelective(context, backup.data["weatherFavorites"], mergeMode)
            }

            if (BackupDataType.VITAL_RECORDS in selectedTypes && backup.data.containsKey("vitalRecords")) {
                restoreVitalRecordsSelective(context, backup.data["vitalRecords"], mergeMode)
            }

            // ✅ NEU
            if (BackupDataType.INTERVAL_TIMER in selectedTypes && backup.data.containsKey("intervalTimerSessions")) {
                restoreIntervalTimerSessionsSelective(context, backup.data["intervalTimerSessions"], mergeMode)
            }

            // ✅ NEU
            if (BackupDataType.PLANNED_SESSIONS in selectedTypes && backup.data.containsKey("plannedSessions")) {
                restorePlannedSessionsSelective(context, backup.data["plannedSessions"], mergeMode)
            }

            if (BackupDataType.PLANT_PHOTOS in selectedTypes &&                    // ✅ NEU
                backup.data.containsKey("plantPhotoMap")) {
                restorePlantPhotosSelective(context, backup.data["plantPhotoMap"], mergeMode, tempDir)
            }

            Log.d(TAG, "Selective backup restored successfully")
            SelectiveRestoreResult.Success("✅ Backup erfolgreich wiederhergestellt")

        } catch (e: Exception) {
            Log.e(TAG, "Error restoring selective backup", e)
            SelectiveRestoreResult.Error("❌ Fehler: ${e.message}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Liest Backup-Datei und gibt verfügbare Datentypen zurück
     */
    fun getAvailableTypesInBackup(backupFile: File): List<BackupDataType> {
        return try {
            val json = backupFile.readText()

            // Versuche als SelectiveBackup zu parsen
            val backup = try {
                gson.fromJson(json, SelectiveBackupContainer::class.java)
            } catch (e: Exception) {
                // Falls altes Full-Backup: Alle Typen verfügbar
                return BackupDataType.values().toList()
            }

            backup.includedTypes.mapNotNull { typeName ->
                try {
                    BackupDataType.valueOf(typeName)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading backup types", e)
            emptyList()
        }
    }

    // ════════════════════════════════════════════════════════════════
// KORRIGIERTE RESTORE-FUNKTIONEN MIT EXPLIZITEN TYPEN
// Ersetze die entsprechenden Funktionen in BackupManager.kt
// ════════════════════════════════════════════════════════════════

    private fun restoreProfilesSelective(
        context: Context,
        data: Any?,
        mode: MergeMode,
        photoMapping: Map<String, String> = emptyMap()
    ) {
        val type = object : TypeToken<List<DogProfile>>() {}.type
        val rawProfiles: List<DogProfile> = gson.fromJson(gson.toJson(data), type)

        // ✅ Fotos mappen
        val profiles = rawProfiles.map { profile ->
            if (profile.photoUri.isNotEmpty() && photoMapping.containsKey(profile.photoUri)) {
                profile.copy(photoUri = photoMapping[profile.photoUri]!!)
            } else profile
        }

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing = DogProfileManager.getAllProfiles(context)
                val existingIds = existing.map { it.id }.toSet()
                // ✅ Stufe 2: Chip-Nummer statt Name+Geburtsdatum (kein Schreibfehler-Risiko)
                val existingChips = existing
                    .map { it.chipNumber.filter { c -> c.isDigit() } }
                    .filter { it.isNotEmpty() }
                    .toSet()
                profiles.forEach { profile ->
                    val chip = profile.chipNumber.filter { it.isDigit() }
                    val chipAlreadyExists = chip.isNotEmpty() && chip in existingChips
                    if (profile.id !in existingIds && !chipAlreadyExists) {
                        DogProfileManager.saveProfile(context, profile)
                    }
                }
            }

            MergeMode.REPLACE_ALL -> {
                val existing = DogProfileManager.getAllProfiles(context)
                existing.forEach { DogProfileManager.deleteProfile(context, it.id) }
                profiles.forEach { DogProfileManager.saveProfile(context, it) }
            }

            MergeMode.MERGE_BY_TIMESTAMP -> {
                val existing = DogProfileManager.getAllProfiles(context)
                val merged = mergeProfiles(profiles, existing)
                // Alle löschen und merged-Liste speichern
                existing.forEach { DogProfileManager.deleteProfile(context, it.id) }
                merged.forEach { DogProfileManager.saveProfile(context, it) }
            }
        }
    }

    private fun restoreTrainingSessionsSelective(
        context: Context,
        data: Any?,
        mode: MergeMode
    ) {
        val type = object : TypeToken<List<TrainingSession>>() {}.type
        val sessions: List<TrainingSession> = gson.fromJson(gson.toJson(data), type)

        val prefs = context.getSharedPreferences("training_diary", Context.MODE_PRIVATE)

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing: List<TrainingSession> = loadTrainingSessions(context)
                val existingIds: Set<String> = existing.map { it.id }.toSet()
                val newSessions: List<TrainingSession> = sessions.filter { it.id !in existingIds }

                val merged: List<TrainingSession> = existing + newSessions
                Log.d(TAG, "ADD_NEW: ${newSessions.size} new training sessions")
                prefs.edit().putString("sessions", gson.toJson(merged)).apply()
            }

            MergeMode.REPLACE_ALL -> {
                Log.d(TAG, "REPLACE_ALL: Replacing all training sessions")
                prefs.edit().putString("sessions", gson.toJson(sessions)).apply()
            }

            MergeMode.MERGE_BY_TIMESTAMP -> {
                val existing: List<TrainingSession> = loadTrainingSessions(context)
                val merged = mergeSessionsByTimestamp(existing, sessions)

                Log.d(TAG, "MERGE_BY_TIMESTAMP: ${merged.size} training sessions")
                prefs.edit().putString("sessions", gson.toJson(merged)).apply()
            }
        }
    }

    private fun restoreTrainingProgressSelective(
        context: Context,
        data: Any?,
        mode: MergeMode
    ) {
        val type = object : TypeToken<List<TrainingProgress>>() {}.type
        val progressList: List<TrainingProgress> = gson.fromJson(gson.toJson(data), type)

        val prefs = context.getSharedPreferences("training_diary", Context.MODE_PRIVATE)

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing: List<TrainingProgress> = loadTrainingProgress(context)
                val existingKeys: Set<String> = existing.map { "${it.dogId}_${it.category.name}" }.toSet()
                val newProgress: List<TrainingProgress> = progressList.filter { "${it.dogId}_${it.category.name}" !in existingKeys }

                val merged: List<TrainingProgress> = existing + newProgress
                Log.d(TAG, "ADD_NEW: ${newProgress.size} new training progress")
                prefs.edit().putString("progress", gson.toJson(merged)).apply()
            }

            MergeMode.REPLACE_ALL -> {
                Log.d(TAG, "REPLACE_ALL: Replacing all training progress")
                prefs.edit().putString("progress", gson.toJson(progressList)).apply()
            }

            MergeMode.MERGE_BY_TIMESTAMP -> {
                val existing: List<TrainingProgress> = loadTrainingProgress(context)
                val merged = mergeProgressByTimestamp(existing, progressList)

                Log.d(TAG, "MERGE_BY_TIMESTAMP: ${merged.size} training progress")
                prefs.edit().putString("progress", gson.toJson(merged)).apply()
            }
        }
    }

    private fun restoreTrainingMilestonesSelective(
        context: Context,
        data: Any?,
        mode: MergeMode
    ) {
        val type = object : TypeToken<List<Milestone>>() {}.type
        val milestones: List<Milestone> = gson.fromJson(gson.toJson(data), type)

        val prefs = context.getSharedPreferences("training_diary", Context.MODE_PRIVATE)

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing: List<Milestone> = loadTrainingMilestones(context)
                val existingIds: Set<String> = existing.map { it.id }.toSet()
                val newMilestones: List<Milestone> = milestones.filter { it.id !in existingIds }

                val merged: List<Milestone> = existing + newMilestones
                Log.d(TAG, "ADD_NEW: ${newMilestones.size} new milestones")
                prefs.edit().putString("milestones", gson.toJson(merged)).apply()
            }

            MergeMode.REPLACE_ALL -> {
                Log.d(TAG, "REPLACE_ALL: Replacing all milestones")
                prefs.edit().putString("milestones", gson.toJson(milestones)).apply()
            }

            MergeMode.MERGE_BY_TIMESTAMP -> {
                val existing: List<Milestone> = loadTrainingMilestones(context)
                val merged = mergeMilestonesByTimestamp(existing, milestones)

                Log.d(TAG, "MERGE_BY_TIMESTAMP: ${merged.size} milestones")
                prefs.edit().putString("milestones", gson.toJson(merged)).apply()
            }
        }
    }

    private fun restoreSpeedSessionsSelective(
        context: Context,
        data: Any?,
        mode: MergeMode
    ) {
        val type = object : TypeToken<List<SpeedSession>>() {}.type
        val sessions: List<SpeedSession> = gson.fromJson(gson.toJson(data), type)

        val prefs = context.getSharedPreferences("walking_history", Context.MODE_PRIVATE)

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing: List<SpeedSession> = collectSpeedSessions(context) ?: emptyList()
                val existingIds: Set<String> = existing.map { it.startTime.toString() }.toSet()
                val newSessions: List<SpeedSession> = sessions.filter { it.startTime.toString() !in existingIds }

                val merged: List<SpeedSession> = existing + newSessions
                Log.d(TAG, "ADD_NEW: ${newSessions.size} new speed sessions")
                prefs.edit().putString("sessions", gson.toJson(merged)).apply()
            }

            MergeMode.REPLACE_ALL -> {
                Log.d(TAG, "REPLACE_ALL: Replacing all speed sessions")
                prefs.edit().putString("sessions", gson.toJson(sessions)).apply()
            }

            MergeMode.MERGE_BY_TIMESTAMP -> {
                val existing: List<SpeedSession> = collectSpeedSessions(context) ?: emptyList()
                val merged = mergeSpeedSessionsByTimestamp(existing, sessions)

                Log.d(TAG, "MERGE_BY_TIMESTAMP: ${merged.size} speed sessions")
                prefs.edit().putString("sessions", gson.toJson(merged)).apply()
            }
        }
    }

    private fun restoreNotesSelective(
        context: Context,
        data: Any?,
        mode: MergeMode,
        photoMapping: Map<String, String> = emptyMap()
    ) {
        val type = object : TypeToken<List<NoteSession>>() {}.type
        val rawNotes: List<NoteSession> = gson.fromJson(gson.toJson(data), type)

        // ✅ Fotos mappen
        val notes = rawNotes.map { note ->
            val restoredUris = note.photoUris.map { photoMapping[it] ?: it }
            note.copy(photoUris = restoredUris)
        }

        val prefs = context.getSharedPreferences("notes_history", Context.MODE_PRIVATE)

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing: List<NoteSession> = collectNotes(context) ?: emptyList()
                val existingIds: Set<String> = existing.map { it.id }.toSet()
                val newNotes: List<NoteSession> = notes.filter { it.id !in existingIds }

                val merged: List<NoteSession> = existing + newNotes
                Log.d(TAG, "ADD_NEW: ${newNotes.size} new notes")
                prefs.edit().putString("sessions", gson.toJson(merged)).apply()
            }

            MergeMode.REPLACE_ALL -> {
                Log.d(TAG, "REPLACE_ALL: Replacing all notes")
                prefs.edit().putString("sessions", gson.toJson(notes)).apply()
            }

            MergeMode.MERGE_BY_TIMESTAMP -> {
                val existing: List<NoteSession> = collectNotes(context) ?: emptyList()
                val merged = mergeNotesByTimestamp(existing, notes)

                Log.d(TAG, "MERGE_BY_TIMESTAMP: ${merged.size} notes")
                prefs.edit().putString("sessions", gson.toJson(merged)).apply()
            }
        }
    }

    private fun restoreVoiceSessionsSelective(
        context: Context,
        data: Any?,
        mode: MergeMode
    ) {
        val type = object : TypeToken<List<VoiceSession>>() {}.type
        val sessions: List<VoiceSession> = gson.fromJson(gson.toJson(data), type)

        val prefs = context.getSharedPreferences("voice_history", Context.MODE_PRIVATE)

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing: List<VoiceSession> = collectVoiceSessions(context) ?: emptyList()
                val existingIds: Set<String> = existing.map { it.startTime.toString() }.toSet()
                val newSessions: List<VoiceSession> = sessions.filter { it.startTime.toString() !in existingIds }

                val merged: List<VoiceSession> = existing + newSessions
                Log.d(TAG, "ADD_NEW: ${newSessions.size} new voice sessions")
                prefs.edit().putString("sessions", gson.toJson(merged)).apply()
            }

            MergeMode.REPLACE_ALL -> {
                Log.d(TAG, "REPLACE_ALL: Replacing all voice sessions")
                prefs.edit().putString("sessions", gson.toJson(sessions)).apply()
            }

            MergeMode.MERGE_BY_TIMESTAMP -> {
                val existing: List<VoiceSession> = collectVoiceSessions(context) ?: emptyList()
                val merged = mergeVoiceSessionsByTimestamp(existing, sessions)

                Log.d(TAG, "MERGE_BY_TIMESTAMP: ${merged.size} voice sessions")
                prefs.edit().putString("sessions", gson.toJson(merged)).apply()
            }
        }
    }

    private fun restoreEmergencyReportsSelective(
        context: Context,
        data: Any?,
        mode: MergeMode,
        photoMapping: Map<String, String> = emptyMap()
    ) {
        val type = object : TypeToken<List<EmergencyReport>>() {}.type
        val rawReports: List<EmergencyReport> = gson.fromJson(gson.toJson(data), type)

        // ✅ Fotos mappen
        val reports = rawReports.map { report ->
            val restoredUris = report.photoUris.map { photoMapping[it] ?: it }
            report.copy(photoUris = restoredUris)
        }

        val prefs = context.getSharedPreferences("emergency_history", Context.MODE_PRIVATE)

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing: List<EmergencyReport> = collectEmergencyReports(context) ?: emptyList()
                val existingIds: Set<String> = existing.map { it.id }.toSet()
                val newReports: List<EmergencyReport> = reports.filter { it.id !in existingIds }

                val merged: List<EmergencyReport> = existing + newReports
                Log.d(TAG, "ADD_NEW: ${newReports.size} new emergency reports")
                prefs.edit().putString("reports", gson.toJson(merged)).apply()
            }

            MergeMode.REPLACE_ALL -> {
                Log.d(TAG, "REPLACE_ALL: Replacing all emergency reports")
                prefs.edit().putString("reports", gson.toJson(reports)).apply()
            }

            MergeMode.MERGE_BY_TIMESTAMP -> {
                val existing: List<EmergencyReport> = collectEmergencyReports(context) ?: emptyList()
                val merged = mergeEmergencyReportsByTimestamp(existing, reports)

                Log.d(TAG, "MERGE_BY_TIMESTAMP: ${merged.size} emergency reports")
                prefs.edit().putString("reports", gson.toJson(merged)).apply()
            }
        }
    }

    private fun restoreNasenarbeitSessionsSelective(
        context: Context,
        data: Any?,
        mode: MergeMode
    ) {
        val type = object : TypeToken<List<NasenarbeitSession>>() {}.type
        val rawSessions: List<NasenarbeitSession> = gson.fromJson(gson.toJson(data), type)

        // Migration: ältere Backups (vor der Sprachumstellung) speicherten hier den
        // deutschen Anzeigetext ("Anfänger" etc.) statt des stabilen Enum-Namens.
        // fromLegacyStoredName() erkennt beide Formen und normalisiert auf den Enum-Namen.
        val sessions = rawSessions.map { session ->
            session.copy(
                levelVerlorensuche = NasenarbeitLevel.fromLegacyStoredName(session.levelVerlorensuche).name,
                levelPersonensuche = NasenarbeitLevel.fromLegacyStoredName(session.levelPersonensuche).name,
                levelFreisuche = NasenarbeitLevel.fromLegacyStoredName(session.levelFreisuche).name
            )
        }

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing = NasenarbeitManager.loadSessions(context)
                val existingIds = existing.map { it.id }.toSet()
                val merged = existing + sessions.filter { it.id !in existingIds }
                NasenarbeitManager.saveSessions(context, merged.toMutableList())
            }
            MergeMode.REPLACE_ALL -> {
                NasenarbeitManager.saveSessions(context, sessions.toMutableList())
            }
            MergeMode.MERGE_BY_TIMESTAMP -> {
                val existing = NasenarbeitManager.loadSessions(context)
                val merged = mergeSessionsByTimestamp_Nasen(existing, sessions)
                NasenarbeitManager.saveSessions(context, merged.toMutableList())
            }
        }
    }

    private fun restoreNasenarbeitFortschrittSelective(
        context: Context,
        data: Any?,
        mode: MergeMode
    ) {
        val type = object : TypeToken<List<NasenarbeitFortschritt>>() {}.type
        val fortschritt: List<NasenarbeitFortschritt> = gson.fromJson(gson.toJson(data), type)

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing = NasenarbeitManager.loadFortschritt(context)
                val existingIds = existing.map { it.dogId }.toSet()
                val merged = existing + fortschritt.filter { it.dogId !in existingIds }
                NasenarbeitManager.importFromBackup(context, null, merged)
            }
            MergeMode.REPLACE_ALL -> {
                NasenarbeitManager.importFromBackup(context, null, fortschritt)
            }
            MergeMode.MERGE_BY_TIMESTAMP -> {
                val existing = NasenarbeitManager.loadFortschritt(context)
                val merged = mergeFortschrittByTimestamp_Nasen(existing, fortschritt)
                NasenarbeitManager.importFromBackup(context, null, merged)
            }
        }
    }

    private fun restoreFavoritesSelective(
        context: Context,
        data: Any?,
        mode: MergeMode
    ) {
        val type = object : TypeToken<List<FavoritePOI>>() {}.type
        val favs: List<FavoritePOI> = gson.fromJson(gson.toJson(data), type)

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing = FavoritesManager.getAll(context)
                val existingKeys = existing.map { "%.5f_%.5f".format(it.lat, it.lon) }.toSet()
                favs.filter { "%.5f_%.5f".format(it.lat, it.lon) !in existingKeys }
                    .forEach { FavoritesManager.add(context, it) }
            }
            MergeMode.REPLACE_ALL -> {
                FavoritesManager.restoreAll(context, favs)
            }
            MergeMode.MERGE_BY_TIMESTAMP -> {
                // Favoriten haben keinen Timestamp, daher ADD_NEW als Fallback
                val existing = FavoritesManager.getAll(context)
                val existingKeys = existing.map { "%.5f_%.5f".format(it.lat, it.lon) }.toSet()
                favs.filter { "%.5f_%.5f".format(it.lat, it.lon) !in existingKeys }
                    .forEach { FavoritesManager.add(context, it) }
            }
        }
    }

    private fun mergeSessionsByTimestamp_Nasen(
        existing: List<NasenarbeitSession>,
        backup: List<NasenarbeitSession>
    ): List<NasenarbeitSession> {
        val allIds = (existing.map { it.id } + backup.map { it.id }).distinct()
        return allIds.mapNotNull { id ->
            val e = existing.find { it.id == id }
            val b = backup.find { it.id == id }
            when {
                e == null -> b
                b == null -> e
                b.datum > e.datum -> b
                else -> e
            }
        }
    }

    private fun mergeFortschrittByTimestamp_Nasen(
        existing: List<NasenarbeitFortschritt>,
        backup: List<NasenarbeitFortschritt>
    ): List<NasenarbeitFortschritt> {
        val allIds = (existing.map { it.dogId } + backup.map { it.dogId }).distinct()
        return allIds.mapNotNull { id ->
            val e = existing.find { it.dogId == id }
            val b = backup.find { it.dogId == id }
            when {
                e == null -> b
                b == null -> e
                b.letzteSession > e.letzteSession -> b
                else -> e
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // MERGE HELPERS
    // ════════════════════════════════════════════════════════════════

    private fun restoreVitalRecordsSelective(
        context: Context,
        data: Any?,
        mode: MergeMode
    ) {
        val type = object : TypeToken<List<VitalRecord>>() {}.type
        val records: List<VitalRecord> = gson.fromJson(gson.toJson(data), type)
        VitalManager.importFromBackup(context, records, mergeMode = (mode == MergeMode.ADD_NEW || mode == MergeMode.MERGE_BY_TIMESTAMP))
    }

    private fun restoreIntervalTimerSessionsSelective(
        context: Context,
        data: Any?,
        mergeMode: MergeMode
    ) {
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<IntervalTimerSession>>() {}.type
            val json = gson.toJson(data)
            val sessions: List<IntervalTimerSession> = gson.fromJson(json, type) ?: return

            when (mergeMode) {
                MergeMode.REPLACE_ALL -> {
                    // Alle löschen, dann neu einfügen
                    val existing = IntervalTimerManager.getAllSessions(context)
                    existing.forEach { IntervalTimerManager.deleteSession(context, it.id) }
                    sessions.forEach { IntervalTimerManager.saveSession(context, it) }
                }
                MergeMode.ADD_NEW -> {
                    // Nur Sessions hinzufügen, die noch nicht vorhanden sind (UUID-Vergleich)
                    val existingIds = IntervalTimerManager.getAllSessions(context).map { it.id }.toSet()
                    sessions.filterNot { it.id in existingIds }
                        .forEach { IntervalTimerManager.saveSession(context, it) }
                }
                MergeMode.MERGE_BY_TIMESTAMP -> {
                    // Neueste Version je UUID behalten
                    val existing = IntervalTimerManager.getAllSessions(context).associateBy { it.id }
                    sessions.forEach { incoming ->
                        val current = existing[incoming.id]
                        if (current == null || incoming.timestamp > current.timestamp) {
                            IntervalTimerManager.saveSession(context, incoming)
                        }
                    }
                }
            }
            Log.d(TAG, "Intervall-Timer Sessions wiederhergestellt: ${sessions.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Intervall-Timer Restore", e)
        }
    }

    private fun restorePlannedSessionsSelective(
        context: Context,
        data: Any?,
        mergeMode: MergeMode
    ) {
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<PlannedSession>>() {}.type
            val json = gson.toJson(data)
            val sessions: List<PlannedSession> = gson.fromJson(json, type) ?: return

            when (mergeMode) {
                MergeMode.REPLACE_ALL -> {
                    val existing = ActivityCalendarManager.getAllPlanned(context)
                    existing.forEach { ActivityCalendarManager.delete(context, it.id) }
                    sessions.forEach { ActivityCalendarManager.save(context, it) }
                }
                MergeMode.ADD_NEW -> {
                    val existingIds = ActivityCalendarManager.getAllPlanned(context).map { it.id }.toSet()
                    sessions.filterNot { it.id in existingIds }
                        .forEach { ActivityCalendarManager.save(context, it) }
                }
                MergeMode.MERGE_BY_TIMESTAMP -> {
                    // Planned Sessions haben keinen Timestamp, daher ADD_NEW
                    val existingIds = ActivityCalendarManager.getAllPlanned(context).map { it.id }.toSet()
                    sessions.filterNot { it.id in existingIds }
                        .forEach { ActivityCalendarManager.save(context, it) }
                }
            }
            Log.d(TAG, "Geplante Sessions wiederhergestellt: ${sessions.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Planned Sessions Restore", e)
        }
    }

    private fun restorePlantPhotosSelective(
        context: Context,
        data: Any?,
        mergeMode: MergeMode,
        tempDir: File
    ) {
        try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, List<String>>>() {}.type
            val map: Map<String, List<String>> = Gson().fromJson(Gson().toJson(data), type)
                ?: return
            map.forEach { (plantId, relativePaths) ->
                val restoredPaths = relativePaths.mapNotNull { rel ->
                    val src = File(tempDir, rel)
                    if (!src.exists()) return@mapNotNull null
                    val dest = File(context.filesDir, "plant_photos/${src.name}")
                    dest.parentFile?.mkdirs()
                    try { src.copyTo(dest, overwrite = true); dest.absolutePath }
                    catch (e: Exception) { Log.e(TAG, "Plant photo selective restore: ${e.message}"); null }
                }
                if (restoredPaths.isNotEmpty()) {
                    PlantPhotoManager.restorePhotos(
                        context, plantId, restoredPaths,
                        mergeMode = (mergeMode == MergeMode.ADD_NEW || mergeMode == MergeMode.MERGE_BY_TIMESTAMP)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "restorePlantPhotosSelective Fehler: ${e.message}")
        }
    }


    private fun restoreWeatherFavoritesSelective(
        context: Context,
        data: Any?,
        mode: MergeMode
    ) {        val type = object : TypeToken<List<WeatherFavorite>>() {}.type
        val favorites: List<WeatherFavorite> = gson.fromJson(gson.toJson(data), type)

        when (mode) {
            MergeMode.ADD_NEW -> {
                val existing = WeatherFavoritesManager.getAll(context)
                val existingKeys = existing.map { "${it.lat}_${it.lon}" }.toSet()
                favorites.filter { "${it.lat}_${it.lon}" !in existingKeys }.forEach {
                    WeatherFavoritesManager.add(context, it)
                }
            }
            MergeMode.REPLACE_ALL -> {
                WeatherFavoritesManager.saveAll(context, favorites)
            }
            MergeMode.MERGE_BY_TIMESTAMP -> {
                // Wetter-Favoriten haben keinen Timestamp, daher ADD_NEW als Fallback
                val existing = WeatherFavoritesManager.getAll(context)
                val existingKeys = existing.map { "${it.lat}_${it.lon}" }.toSet()
                favorites.filter { "${it.lat}_${it.lon}" !in existingKeys }.forEach {
                    WeatherFavoritesManager.add(context, it)
                }
            }
        }
    }

    private fun mergeSessionsByTimestamp(
        existing: List<TrainingSession>,
        backup: List<TrainingSession>
    ): List<TrainingSession> {
        val allIds = (existing.map { it.id } + backup.map { it.id }).distinct()

        return allIds.mapNotNull { id: String ->
            val existingSession = existing.find { it.id == id }
            val backupSession = backup.find { it.id == id }

            when {
                existingSession == null -> backupSession
                backupSession == null -> existingSession
                backupSession.date > existingSession.date -> backupSession
                else -> existingSession
            }
        }.sortedByDescending { it.date }
    }

    private fun mergeProgressByTimestamp(
        existing: List<TrainingProgress>,
        backup: List<TrainingProgress>
    ): List<TrainingProgress> {
        val allKeys = (existing.map { "${it.dogId}_${it.category.name}" } +
                backup.map { "${it.dogId}_${it.category.name}" }).distinct()

        return allKeys.mapNotNull { key ->
            val existingProgress = existing.find { "${it.dogId}_${it.category.name}" == key }
            val backupProgress = backup.find { "${it.dogId}_${it.category.name}" == key }

            when {
                existingProgress == null -> backupProgress
                backupProgress == null -> existingProgress
                backupProgress.lastTrainingDate > existingProgress.lastTrainingDate -> backupProgress
                else -> existingProgress
            }
        }.sortedByDescending { it.lastTrainingDate }
    }

    private fun mergeMilestonesByTimestamp(
        existing: List<Milestone>,
        backup: List<Milestone>
    ): List<Milestone> {
        val allIds = (existing.map { it.id } + backup.map { it.id }).distinct()

        return allIds.mapNotNull { id: String ->
            val existingMilestone = existing.find { it.id == id }
            val backupMilestone = backup.find { it.id == id }

            when {
                existingMilestone == null -> backupMilestone
                backupMilestone == null -> existingMilestone
                backupMilestone.achievedDate > existingMilestone.achievedDate -> backupMilestone
                else -> existingMilestone
            }
        }.sortedByDescending { it.achievedDate }
    }

    private fun mergeSpeedSessionsByTimestamp(
        existing: List<SpeedSession>,
        backup: List<SpeedSession>
    ): List<SpeedSession> {
        val allTimes = (existing.map { it.startTime } + backup.map { it.startTime }).distinct()

        return allTimes.mapNotNull { startTime ->
            val existingSession = existing.find { it.startTime == startTime }
            val backupSession = backup.find { it.startTime == startTime }

            when {
                existingSession == null -> backupSession
                backupSession == null -> existingSession
                backupSession.startTime > existingSession.startTime -> backupSession
                else -> existingSession
            }
        }.sortedByDescending { it.startTime }
    }

    private fun mergeNotesByTimestamp(
        existing: List<NoteSession>,
        backup: List<NoteSession>
    ): List<NoteSession> {
        val allIds = (existing.map { it.id } + backup.map { it.id }).distinct()

        return allIds.mapNotNull { id: String ->
            val existingNote = existing.find { it.id == id }
            val backupNote = backup.find { it.id == id }

            when {
                existingNote == null -> backupNote
                backupNote == null -> existingNote
                backupNote.date > existingNote.date -> backupNote
                else -> existingNote
            }
        }.sortedByDescending { it.date }
    }

    private fun mergeVoiceSessionsByTimestamp(
        existing: List<VoiceSession>,
        backup: List<VoiceSession>
    ): List<VoiceSession> {
        val allTimes = (existing.map { it.startTime } + backup.map { it.startTime }).distinct()

        return allTimes.mapNotNull { startTime ->
            val existingSession = existing.find { it.startTime == startTime }
            val backupSession = backup.find { it.startTime == startTime }

            when {
                existingSession == null -> backupSession
                backupSession == null -> existingSession
                backupSession.startTime > existingSession.startTime -> backupSession
                else -> existingSession
            }
        }.sortedByDescending { it.startTime }
    }

    private fun mergeEmergencyReportsByTimestamp(
        existing: List<EmergencyReport>,
        backup: List<EmergencyReport>
    ): List<EmergencyReport> {
        val allIds = (existing.map { it.id } + backup.map { it.id }).distinct()

        return allIds.mapNotNull { id: String ->
            val existingReport = existing.find { it.id == id }
            val backupReport = backup.find { it.id == id }

            when {
                existingReport == null -> backupReport
                backupReport == null -> existingReport
                backupReport.timestamp > existingReport.timestamp -> backupReport
                else -> existingReport
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun mergeProfiles(
        backup: List<DogProfile>,
        existing: List<DogProfile>
    ): List<DogProfile> {
        val merged = existing.toMutableList()

        backup.forEach { backupProfile ->

            // Stufe 1: Übereinstimmung per UUID
            val byId = merged.indexOfFirst { it.id == backupProfile.id }
            if (byId >= 0) {
                val existingProfile = merged[byId]
                if (backupProfile.lastModified > existingProfile.lastModified) {
                    merged[byId] = backupProfile
                }
                return@forEach
            }

            // Stufe 2: Übereinstimmung per Chip-Nummer (normalisiert)
            val backupChip = backupProfile.chipNumber.filter { it.isDigit() }
            if (backupChip.isNotEmpty()) {
                val byChip = merged.indexOfFirst { existingProfile ->
                    val existingChip = existingProfile.chipNumber.filter { it.isDigit() }
                    existingChip.isNotEmpty() && existingChip == backupChip
                }
                if (byChip >= 0) {
                    val existingProfile = merged[byChip]
                    if (backupProfile.lastModified > existingProfile.lastModified) {
                        merged[byChip] = backupProfile.copy(id = existingProfile.id)
                    }
                    return@forEach
                }
            }

            // Kein Match → neues Profil hinzufügen
            merged.add(backupProfile)
        }

        return merged
    }

    // ════════════════════════════════════════════════════════════════
    // UTILITY FUNCTIONS
    // ════════════════════════════════════════════════════════════════

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