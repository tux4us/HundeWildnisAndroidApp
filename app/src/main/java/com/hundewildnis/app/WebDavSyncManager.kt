package com.hundewildnis.app

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * Orchestriert die WebDAV-Synchronisation für HundeWildnis.
 *
 * WICHTIG: Diese Klasse erfindet KEINE eigene Merge-Logik. Stattdessen wird
 * die bereits vorhandene und bewährte BackupManager-Logik wiederverwendet:
 * - BackupManager.createBackup(context): File  → erzeugt ein .hundebackup-ZIP
 * - BackupManager.restoreBackup(context, file, mergeMode): Boolean
 *   → mergeMode=true nutzt die bestehende mergeProfiles()-Logik
 *     (UUID-Match, dann Chip-Nummer, dann lastModified-Vergleich)
 *
 * Strategie: Hub-and-Spoke / gerätespezifisch
 * - Jedes Gerät legt seine eigene Backup-ZIP unter /HundeWildnis/ ab:
 *   backup_[Gerätename]_[ID].hundebackup
 * - Beim Sync werden alle ANDEREN Geräte-Dateien heruntergeladen und nacheinander
 *   per restoreBackup(mergeMode=true) eingespielt
 * - Anschließend wird der aktuelle (gemergte) Stand wieder als eigene Datei hochgeladen
 *
 * Da restoreBackup() bereits synchron viele Daten verändert (Hunde, Notizen,
 * Trainingsdaten, Settings ...), läuft der komplette Sync auf Dispatchers.IO
 * und sollte nicht parallel zu manuellen Backup-Vorgängen laufen.
 */
class WebDavSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "WebDavSyncManager"
        const val REMOTE_DIR = "HundeWildnis"
        const val FILE_PREFIX = "backup_"
        const val FILE_SUFFIX = ".hundebackup"

        /** Liefert eine stabile, dateisystem-taugliche Geräte-ID. */
        fun getDeviceId(context: Context): String {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            val model = Build.MODEL
                .replace(" ", "_")
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "")
                .take(20)
            return "${model}_${androidId.take(8)}"
        }

        fun remoteFileName(context: Context): String =
            "$FILE_PREFIX${getDeviceId(context)}$FILE_SUFFIX"
    }

    sealed class SyncResult {
        /** Sync ohne Konflikte abgeschlossen (oder keine anderen Geräte gefunden). */
        data class Success(val mergedDeviceCount: Int) : SyncResult()
        object NoCredentials : SyncResult()
        data class NetworkError(val message: String) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    private val gson = Gson()
    private val prefs = WebDavPreferences(context)

    /**
     * Führt einen vollständigen Sync-Zyklus aus:
     * 1. Lokales Backup erzeugen (BackupManager.createBackup)
     * 2. Lokales Backup hochladen (eigene Geräte-Datei)
     * 3. Alle anderen Geräte-Dateien auflisten und herunterladen
     * 4. Jede heruntergeladene Datei per restoreBackup(mergeMode=true) einspielen
     * 5. Nach dem Merge: aktualisiertes lokales Backup erneut hochladen
     *
     * Läuft blockierend - immer von Dispatchers.IO aus aufrufen.
     */
    fun sync(): SyncResult {
        val config = prefs.loadConfig() ?: return SyncResult.NoCredentials
        val client = WebDavClient(config.serverUrl, config.username, config.password)

        // 1. Verzeichnis sicherstellen
        client.createDirectory(REMOTE_DIR).onFailure {
            return SyncResult.NetworkError("Verzeichnis konnte nicht angelegt werden: ${it.message}")
        }

        // 2. Lokales Backup erzeugen und hochladen
        val localBackupFile = try {
            BackupManager.createBackup(context)
        } catch (e: Exception) {
            Log.e(TAG, "Lokales Backup konnte nicht erstellt werden", e)
            return SyncResult.Error("Backup-Erstellung fehlgeschlagen: ${e.message}")
        }

        val ownRemoteName = remoteFileName(context)
        client.uploadFile("$REMOTE_DIR/$ownRemoteName", localBackupFile).onFailure {
            return SyncResult.NetworkError("Upload fehlgeschlagen: ${it.message}")
        }
        Log.d(TAG, "Eigenes Backup hochgeladen: $ownRemoteName")

        // 3. Andere Geräte-Dateien auflisten
        val remoteFiles = client.listFiles(REMOTE_DIR).getOrElse {
            return SyncResult.NetworkError("Dateiliste konnte nicht abgerufen werden: ${it.message}")
        }

        val otherDeviceFiles = remoteFiles.filter {
            it.startsWith(FILE_PREFIX) && it.endsWith(FILE_SUFFIX) && it != ownRemoteName
        }

        if (otherDeviceFiles.isEmpty()) {
            Log.d(TAG, "Keine anderen Geräte gefunden - Sync abgeschlossen")
            return SyncResult.Success(mergedDeviceCount = 0)
        }

        // 4. Jede fremde Datei herunterladen und per bestehender Merge-Logik einspielen
        var mergedCount = 0
        val tempDir = File(context.cacheDir, "webdav_sync_downloads")
        tempDir.mkdirs()

        try {
            for (fileName in otherDeviceFiles) {
                val tempFile = File(tempDir, fileName)
                val downloadResult = client.downloadFile("$REMOTE_DIR/$fileName", tempFile)

                if (downloadResult.isFailure) {
                    Log.w(TAG, "Download fehlgeschlagen für $fileName: ${downloadResult.exceptionOrNull()?.message}")
                    continue
                }

                val restoreSuccess = try {
                    BackupManager.restoreBackup(context, tempFile, mergeMode = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Merge fehlgeschlagen für $fileName", e)
                    false
                }

                if (restoreSuccess) {
                    mergedCount++
                    Log.d(TAG, "Erfolgreich gemerged: $fileName")
                } else {
                    Log.w(TAG, "Merge nicht erfolgreich für $fileName")
                }

                tempFile.delete()
            }
        } finally {
            tempDir.deleteRecursively()
        }

        // 5. Nach dem Merge: aktualisierten Stand erneut hochladen,
        //    damit andere Geräte beim nächsten Sync den vollständigen Stand sehen
        if (mergedCount > 0) {
            val updatedBackupFile = try {
                BackupManager.createBackup(context)
            } catch (e: Exception) {
                Log.e(TAG, "Backup nach Merge konnte nicht erstellt werden", e)
                return SyncResult.Success(mergedCount) // Merge war trotzdem erfolgreich
            }
            client.uploadFile("$REMOTE_DIR/$ownRemoteName", updatedBackupFile)
            updatedBackupFile.delete()
        }

        localBackupFile.delete()

        return SyncResult.Success(mergedCount)
    }

    /**
     * Baut eine menschenlesbare Zusammenfassung der gefundenen Geräte-Dateien,
     * z.B. zur Anzeige vor dem Sync ("Es wurden 2 weitere Geräte gefunden").
     */
    fun listOtherDevices(): Result<List<String>> {
        val config = prefs.loadConfig() ?: return Result.failure(IllegalStateException("Keine Zugangsdaten hinterlegt"))
        val client = WebDavClient(config.serverUrl, config.username, config.password)
        val ownFile = remoteFileName(context)

        return client.listFiles(REMOTE_DIR).map { files ->
            files.filter { it.startsWith(FILE_PREFIX) && it.endsWith(FILE_SUFFIX) && it != ownFile }
                .map { it.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX) }
        }
    }
}
