package com.hundewildnis.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
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
        // Backup erstellen
        binding.btnCreateBackup.setOnClickListener {
            createBackup()
        }

        // Backup wiederherstellen
        binding.btnRestoreBackup.setOnClickListener {
            selectBackupFile()
        }
    }

    private fun createBackup() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCreateBackup.isEnabled = false
        binding.btnRestoreBackup.isEnabled = false
        binding.tvStatus.text = "Erstelle Backup..."
        binding.tvStatus.visibility = View.VISIBLE

        scope.launch {
            try {
                val backupFile = withContext(Dispatchers.IO) {
                    BackupManager.createBackup(this@BackupActivity)
                }

                binding.progressBar.visibility = View.GONE
                binding.btnCreateBackup.isEnabled = true
                binding.btnRestoreBackup.isEnabled = true
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
                binding.btnCreateBackup.isEnabled = true
                binding.btnRestoreBackup.isEnabled = true
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
        val dialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Backup wiederherstellen?")
            .setMessage(
                "ACHTUNG: Alle aktuellen Daten werden überschrieben!\n\n" +
                        "Dies umfasst:\n" +
                        "• Hunde-Stammdaten\n" +
                        "• Fotos\n" +
                        "• Einstellungen\n" +
                        "• Tagesnotizen\n\n" +
                        "Möchtest du fortfahren?"
            )
            .setPositiveButton("Ja, wiederherstellen") { _, _ ->
                restoreBackup(backupFile)
            }
            .setNegativeButton("Abbrechen") { _, _ ->
                backupFile.delete()
            }
            .setCancelable(false)
            .create()

        dialog.show()

        // ✅ Button-Farben für Dark Mode
        val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val buttonColor = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#2d5016")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
    }

    private fun restoreBackup(backupFile: File) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCreateBackup.isEnabled = false
        binding.btnRestoreBackup.isEnabled = false
        binding.tvStatus.text = "Stelle Backup wieder her..."
        binding.tvStatus.visibility = View.VISIBLE

        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    BackupManager.restoreBackup(this@BackupActivity, backupFile)
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
                    val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES

                    val buttonColor = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#2d5016")
                    successDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCreateBackup.isEnabled = true
                    binding.btnRestoreBackup.isEnabled = true
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
                binding.btnCreateBackup.isEnabled = true
                binding.btnRestoreBackup.isEnabled = true
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
        val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val buttonColor = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#2d5016")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
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
