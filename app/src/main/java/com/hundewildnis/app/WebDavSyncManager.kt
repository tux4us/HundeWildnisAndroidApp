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
            // Stabile Normalisierung: Alle Sonderzeichen und Leerzeichen entfernen
            val model = Build.MODEL
                .replace(Regex("[^a-zA-Z0-9]"), "")
                .take(20)
            return "${model}_${androidId.take(8)}"
        }

        fun remoteFileName(context: Context): String =
            "$FILE_PREFIX${getDeviceId(context)}$FILE_SUFFIX"

        /** Extrahiert die 8-stellige Android-ID aus einem Dateinamen (Suffix vor der Endung). */
        fun getAndroidIdFromFileName(fileName: String): String? {
            // backup_VollaPhone22_2bdc58a0.hundebackup -> 2bdc58a0
            val nameWithoutExt = fileName.substringBeforeLast('.')
            return nameWithoutExt.substringAfterLast('_').takeIf { it.length == 8 }
        }
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
     * 1. Remote-Dateiliste inkl. Metadaten EINMAL laden (Basis für Skip-Upload und
     *    bedingte Downloads - vermeidet einen zweiten PROPFIND-Request)
     * 2. Lokalen Fingerabdruck berechnen; Upload nur bei tatsächlicher Änderung
     *    seit dem letzten Sync (siehe BackupManager.computeBackupFingerprint)
     * 3. Alle anderen Geräte-Dateien auflisten; Download nur, wenn sich ETag/Größe/
     *    Änderungsdatum seit dem letzten Sync geändert haben
     * 4. Jede tatsächlich heruntergeladene Datei per restoreBackup(mergeMode=true) einspielen
     * 5. Nach einem Merge: aktualisiertes lokales Backup erneut hochladen (Daten haben
     *    sich durch den Merge lokal geändert, unabhängig vom ursprünglichen Fingerabdruck)
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

        // 1.5 Remote-Dateiliste inkl. Metadaten EINMAL laden - wird sowohl für die
        // Skip-Upload-Entscheidung (existiert die eigene Datei bereits?) als auch für
        // bedingte Downloads (haben sich fremde Dateien geändert?) wiederverwendet.
        val remoteFiles = client.listFilesDetailed(REMOTE_DIR).getOrElse {
            return SyncResult.NetworkError("Dateiliste konnte nicht abgerufen werden: ${it.message}")
        }

        val ownRemoteName = remoteFileName(context)
        val ownAndroidId = getAndroidIdFromFileName(ownRemoteName) ?: "unknown"

        // 1.6 Server-Cleanup: Veraltete Namensvarianten des EIGENEN Geräts finden und löschen
        // (z.B. Wechsel von Volla_Phone_22_... zu VollaPhone22_...)
        remoteFiles.forEach { file ->
            val fileAndroidId = getAndroidIdFromFileName(file.name)
            if (fileAndroidId == ownAndroidId && file.name != ownRemoteName) {
                Log.i(TAG, "Lösche redundante Server-Datei des eigenen Geräts: ${file.name}")
                client.deleteFile("$REMOTE_DIR/${file.name}")
            }
        }

        // Aktuellen Stand der eigenen Datei prüfen
        val ownFileExistsRemotely = remoteFiles.any { it.name == ownRemoteName }

        // 2. Lokalen Fingerabdruck prüfen - Upload nur bei tatsächlicher Änderung.
        // Bei Fehlern beim Berechnen (sollte praktisch nie vorkommen) wird sicherheitshalber
        // trotzdem hochgeladen, statt einen möglichen Datenverlust zu riskieren.
        val currentFingerprint = try {
            BackupManager.computeBackupFingerprint(context)
        } catch (e: Exception) {
            Log.w(TAG, "Fingerabdruck konnte nicht berechnet werden - lade sicherheitshalber hoch", e)
            null
        }
        val skipUpload = ownFileExistsRemotely &&
                currentFingerprint != null &&
                currentFingerprint == prefs.lastSyncedFingerprint

        if (skipUpload) {
            Log.d(TAG, "Keine Datenänderung seit letztem Sync - Upload übersprungen")
        } else {
            val localBackupFile = try {
                BackupManager.createBackup(context)
            } catch (e: Exception) {
                Log.e(TAG, "Lokales Backup konnte nicht erstellt werden", e)
                return SyncResult.Error("Backup-Erstellung fehlgeschlagen: ${e.message}")
            }

            client.uploadFile("$REMOTE_DIR/$ownRemoteName", localBackupFile).onFailure {
                localBackupFile.delete()
                return SyncResult.NetworkError("Upload fehlgeschlagen: ${it.message}")
            }
            localBackupFile.delete()
            if (currentFingerprint != null) prefs.lastSyncedFingerprint = currentFingerprint
            Log.d(TAG, "Eigenes Backup hochgeladen: $ownRemoteName")
        }

        // 3. Andere Geräte-Dateien ermitteln
        // Kriterium: Andere Android-ID als das aktuelle Gerät
        val otherDeviceFiles = remoteFiles.filter {
            val fileAndroidId = getAndroidIdFromFileName(it.name)
            it.name.startsWith(FILE_PREFIX) && 
            it.name.endsWith(FILE_SUFFIX) && 
            fileAndroidId != null && 
            fileAndroidId != ownAndroidId
        }

        if (otherDeviceFiles.isEmpty()) {
            Log.d(TAG, "Keine anderen Geräte gefunden (eigene ID: $ownAndroidId) - Sync abgeschlossen")
            return SyncResult.Success(mergedDeviceCount = 0)
        }

        // 4. Jede fremde Datei prüfen: nur bei Änderung herunterladen und mergen
        var mergedCount = 0
        val tempDir = File(context.cacheDir, "webdav_sync_downloads")
        tempDir.mkdirs()

        try {
            for (info in otherDeviceFiles) {
                // ETag ist der zuverlässigste Indikator; falls der Server keinen liefert,
                // dient Größe+Änderungsdatum als Fallback zur Änderungserkennung.
                val remoteState = info.etag ?: "${info.size ?: -1}_${info.lastModified ?: ""}"
                val knownState = prefs.getRemoteFileState(info.name)

                if (knownState != null && knownState == remoteState) {
                    Log.d(TAG, "Unverändert seit letztem Sync - Download übersprungen: ${info.name}")
                    continue
                }

                val tempFile = File(tempDir, info.name)
                val downloadResult = client.downloadFile("$REMOTE_DIR/${info.name}", tempFile)

                if (downloadResult.isFailure) {
                    Log.w(TAG, "Download fehlgeschlagen für ${info.name}: ${downloadResult.exceptionOrNull()?.message}")
                    continue
                }

                val restoreSuccess = try {
                    BackupManager.restoreBackup(context, tempFile, mergeMode = true)
                } catch (e: Exception) {
                    Log.e(TAG, "Merge fehlgeschlagen für ${info.name}", e)
                    false
                }

                if (restoreSuccess) {
                    mergedCount++
                    prefs.setRemoteFileState(info.name, remoteState)
                    Log.d(TAG, "Erfolgreich gemerged: ${info.name}")
                } else {
                    Log.w(TAG, "Merge nicht erfolgreich für ${info.name}")
                }

                tempFile.delete()
            }
        } finally {
            tempDir.deleteRecursively()
        }

        // 5. Nach dem Merge: aktualisierten Stand erneut hochladen,
        //    damit andere Geräte beim nächsten Sync den vollständigen Stand sehen.
        //    (Der Merge hat die lokalen Daten verändert, unabhängig vom Fingerabdruck von oben.)
        if (mergedCount > 0) {
            val updatedBackupFile = try {
                BackupManager.createBackup(context)
            } catch (e: Exception) {
                Log.e(TAG, "Backup nach Merge konnte nicht erstellt werden", e)
                return SyncResult.Success(mergedCount) // Merge war trotzdem erfolgreich
            }
            client.uploadFile("$REMOTE_DIR/$ownRemoteName", updatedBackupFile)
            val fingerprintAfterMerge = try {
                BackupManager.computeBackupFingerprint(context)
            } catch (e: Exception) {
                null
            }
            if (fingerprintAfterMerge != null) prefs.lastSyncedFingerprint = fingerprintAfterMerge
            updatedBackupFile.delete()
        }

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
