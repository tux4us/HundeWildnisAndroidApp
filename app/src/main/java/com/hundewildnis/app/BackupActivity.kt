package com.hundewildnis.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.hundewildnis.app.databinding.ActivityBackupBinding
import kotlinx.coroutines.*
import java.io.File

class BackupActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityBackupBinding
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Datei-Picker für Backup-Import
    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Temporäre Datei erstellen
                val tempFile = File(cacheDir, "temp_backup.hundebackup")
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    confirmRestore(tempFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Fehler beim Lesen der Datei: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "📦 Backup & Restore"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        setupButtons()
    }

    private fun setupButtons() {
        setupGrids()
    }

    private fun setupGrids() {
        // Backup erstellen
        val colorsCreate = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridBackupCreate.layoutManager = GridLayoutManager(this, 1)
        binding.gridBackupCreate.adapter = StartTileAdapter(
            tiles = listOf(StartTile("backup_create", R.drawable.ic_backup, "📦 Backup erstellen")),
            chipColorRes = colorsCreate.chipColorRes,
            accentColorRes = colorsCreate.accentColorRes,
            onTileClick = { createBackup() }
        )

        // Verwalten & Sync
        val manageTiles = listOf(
            StartTile("backup_restore", R.drawable.ic_remove, "📥 Wiederherstellen"),
            StartTile("backup_webdav", R.drawable.ic_refresh, "🔄 WebDAV-Sync"),
            StartTile("backup_location", R.drawable.ic_my_location, "📍 Standortfreigabe")
        )
        val colorsManage = StartGroupColors.forGroup(this, 3) // Ocker/Braun
        binding.gridBackupManage.layoutManager = GridLayoutManager(this, 2) // 2 columns looks better for 3 items (1 full row, 1 shared) or we use spanCount=3
        binding.gridBackupManage.adapter = StartTileAdapter(
            tiles = manageTiles,
            chipColorRes = colorsManage.chipColorRes,
            accentColorRes = colorsManage.accentColorRes,
            onTileClick = { id ->
                when(id) {
                    "backup_restore" -> selectBackupFile()
                    "backup_webdav" -> startActivity(Intent(this, WebDavSyncActivity::class.java))
                    "backup_location" -> startActivity(Intent(this, LocationShareActivity::class.java))
                }
            }
        )

        // Service
        val colorsService = StartGroupColors.forGroup(this, 4) // Blau
        binding.gridBackupService.layoutManager = GridLayoutManager(this, 1)
        binding.gridBackupService.adapter = StartTileAdapter(
            tiles = listOf(StartTile("backup_purge", R.drawable.ic_remove, "🧹 Speicher bereinigen")),
            chipColorRes = colorsService.chipColorRes,
            accentColorRes = colorsService.accentColorRes,
            onTileClick = { confirmPurge() }
        )
    }

    private fun confirmPurge() {
        AlertDialog.Builder(this)
            .setTitle("🧹 Medienspeicher bereinigen")
            .setMessage("Dies löscht verwaiste Bilddateien aus alten Synchronisationen.\n\nDeine Profile, Einstellungen und aktuell verwendeten Fotos bleiben erhalten.\n\nDieser Vorgang kann je nach Datenmenge einen Moment dauern. Fortfahren?")
            .setPositiveButton("Bereinigen") { _, _ ->
                scope.launch {
                    try {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                        binding.tvStatus.visibility = android.view.View.VISIBLE
                        binding.tvStatus.text = "🔄 Bereinige Medienspeicher..."
                        
                        withContext(Dispatchers.IO) {
                            BackupManager.purgeOrphanFiles(this@BackupActivity)
                        }
                        
                        Toast.makeText(this@BackupActivity, "Bereinigung abgeschlossen", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("BackupActivity", "Fehler bei Bereinigung", e)
                        Toast.makeText(this@BackupActivity, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.tvStatus.text = "Bereinigung abgeschlossen"
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun createBackup() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Erstelle Backup..."
        binding.tvStatus.visibility = View.VISIBLE

        scope.launch {
            try {
                val backupFile = withContext(Dispatchers.IO) {
                    BackupManager.createBackup(this@BackupActivity)
                }

                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "✅ Backup erfolgreich erstellt!"

                Toast.makeText(
                    this@BackupActivity,
                    "Backup gespeichert: ${backupFile.name}",
                    Toast.LENGTH_LONG
                ).show()

                // Teilen-Dialog anbieten
                offerShare(backupFile)

            } catch (e: Exception) {
                e.printStackTrace()
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "❌ Fehler beim Erstellen des Backups"

                Toast.makeText(
                    this@BackupActivity,
                    "Backup-Fehler: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun selectBackupFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/zip",
                "application/octet-stream",
                "*/*"
            ))
        }
        pickBackupFile.launch(intent)
    }

    private fun confirmRestore(backupFile: File) {
        val buttonColor = android.graphics.Color.WHITE

        val modeDialog = AlertDialog.Builder(this)
            .setTitle("📦 Backup importieren")
            .setMessage(
                "Wie möchtest du das Backup importieren?\n\n" +
                        "➕ Zusammenführen (empfohlen)\n" +
                        "Deine vorhandenen Daten bleiben erhalten. Aus dem Backup werden " +
                        "nur Einträge ergänzt, die noch nicht vorhanden sind. " +
                        "Ideal, wenn du Daten von einem zweiten Gerät übernehmen möchtest.\n\n" +
                        "🔄 Ersetzen\n" +
                        "Alle deine aktuellen Daten werden gelöscht und vollständig durch " +
                        "das Backup ersetzt. Sinnvoll nach einem Gerätewechsel oder wenn " +
                        "du die App auf einen früheren Stand zurücksetzen möchtest."
            )
            .setPositiveButton("➕ Zusammenführen") { _, _ ->
                confirmMergeRestore(backupFile, buttonColor)
            }
            .setNeutralButton("🔄 Ersetzen") { _, _ ->
                confirmReplaceRestore(backupFile, buttonColor)
            }
            .setNegativeButton("Abbrechen") { _, _ ->
                backupFile.delete()
            }
            .setCancelable(false)
            .create()

        modeDialog.show()
    }

    private fun confirmReplaceRestore(backupFile: File, buttonColor: Int) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Wirklich alle Daten ersetzen?")
            .setMessage(
                "Achtung: Diese Aktion kann nicht rückgängig gemacht werden!\n\n" +
                        "Folgende Daten werden unwiderruflich gelöscht und durch das " +
                        "Backup ersetzt:\n" +
                        "• Hunde-Stammdaten & Fotos\n" +
                        "• Trainingstagebuch\n" +
                        "• Tagesnotizen\n" +
                        "• Einstellungen\n\n" +
                        "Bist du sicher?"
            )
            .setPositiveButton("Ja, alles ersetzen") { _, _ ->
                restoreBackup(backupFile, mergeMode = false)
            }
            .setNegativeButton("Abbrechen") { _, _ ->
                backupFile.delete()
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun confirmMergeRestore(backupFile: File, buttonColor: Int) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("➕ Backup zusammenführen?")
            .setMessage(
                "Deine vorhandenen Daten bleiben vollständig erhalten.\n\n" +
                        "Aus dem Backup werden nur Einträge hinzugefügt, die noch nicht " +
                        "vorhanden sind – erkannt anhand der Hunde-ID und Chip-Nummer.\n\n" +
                        "Bereits vorhandene Hunde werden nur aktualisiert, wenn die " +
                        "Version im Backup neuer ist als deine aktuelle Version.\n\n" +
                        "Möchtest du fortfahren?"
            )
            .setPositiveButton("Ja, zusammenführen") { _, _ ->
                restoreBackup(backupFile, mergeMode = true)
            }
            .setNegativeButton("Abbrechen") { _, _ ->
                backupFile.delete()
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun restoreBackup(backupFile: File, mergeMode: Boolean = false) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Stelle Backup wieder her..."
        binding.tvStatus.visibility = View.VISIBLE

        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    BackupManager.restoreBackup(this@BackupActivity, backupFile, mergeMode)
                }

                backupFile.delete()

                if (success) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = "✅ Backup wiederhergestellt!"

                    // Erfolgs-Dialog mit App-Neustart
                    val successDialog = AlertDialog.Builder(this@BackupActivity)
                        .setTitle("✅ Backup wiederhergestellt")
                        .setMessage("Das Backup wurde erfolgreich wiederhergestellt.\n\nDie App wird jetzt neu gestartet.")
                        .setPositiveButton("OK") { _, _ ->
                            restartApp()
                        }
                        .setCancelable(false)
                        .create()

                    successDialog.show()

                    // ✅ Button-Farben für Dark Mode

                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = "❌ Fehler beim Wiederherstellen"

                    Toast.makeText(
                        this@BackupActivity,
                        "Backup konnte nicht wiederhergestellt werden",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                backupFile.delete()

                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "❌ Fehler beim Wiederherstellen"

                Toast.makeText(
                    this@BackupActivity,
                    "Restore-Fehler: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun offerShare(backupFile: File) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Backup teilen?")
            .setMessage("Möchtest du das Backup teilen oder auf ein anderes Gerät übertragen?")
            .setPositiveButton("Teilen") { _, _ ->
                shareBackupFile(backupFile)
            }
            .setNegativeButton("Später") { _, _ ->
                Toast.makeText(
                    this,
                    "Backup gespeichert unter:\n${backupFile.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
            .create()

        dialog.show()

        // ✅ Button-Farben für Dark Mode

    }

    private fun shareBackupFile(backupFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                backupFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "HundeWildnis Backup")
                putExtra(Intent.EXTRA_TEXT, "Backup erstellt am: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMAN).format(java.util.Date())}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Backup teilen über"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Teilen fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}