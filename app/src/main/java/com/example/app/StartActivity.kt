package com.hundewildnis.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import android.content.res.Configuration
import androidx.core.content.FileProvider
import com.hundewildnis.app.databinding.ActivityStartBinding
import kotlinx.coroutines.*
import java.io.File

/**
 * StartActivity - Hauptstartseite der App
 * Zeigt 11 große Buttons für tägliche Aktivitäten
 * Backup & Restore über Button UND Menü verfügbar
 */
class StartActivity : BaseBottomNavActivity() {

    private lateinit var binding: ActivityStartBinding
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Datei-Picker für Backup-Import (für Menü-Funktion)
    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
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
                    Toast.makeText(this, "Fehler beim Lesen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // BaseBottomNavActivity - Abstract Methoden
    override fun getBottomNavigationView() = binding.bottomNavigation
    override fun getCurrentNavigationItemId() = R.id.nav_home

    // BaseBottomNavActivity - Home Callback überschreiben
    override fun onHomeSelected() {
        // Bereits auf StartActivity - nichts tun
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar Setup
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "HundeWildnis"
        binding.toolbar.overflowIcon?.setTint(Color.WHITE)

        setupButtons()
        super.setupBottomNavigation()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightMode == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkMode) Color.WHITE else Color.BLACK

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val spanString = SpannableString(item.title.toString())
            spanString.setSpan(ForegroundColorSpan(textColor), 0, spanString.length, 0)
            item.title = spanString
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                Toast.makeText(this, "Nur in Blog-Ansicht verfügbar", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_theme -> {
                toggleTheme()
                true
            }
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            // ✅ NEU: BACKUP ERSTELLEN
            R.id.action_backup_create -> {
                createBackup()
                true
            }
            // ✅ NEU: BACKUP IMPORTIEREN
            R.id.action_backup_restore -> {
                selectBackupFile()
                true
            }
            R.id.action_privacy -> {
                openPrivacyPolicy()
                true
            }
            R.id.action_imprint -> {
                openImprint()
                true
            }
            R.id.action_home -> {
                // Bereits auf StartActivity
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleTheme() {
        val sharedPreferences = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("is_dark_mode", false)

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            sharedPreferences.edit().putBoolean("is_dark_mode", false).apply()
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            sharedPreferences.edit().putBoolean("is_dark_mode", true).apply()
        }
        recreate()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        val switchBackgroundUpdates = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBackgroundUpdates)
        val switchWifiOnly = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchWifiOnly)
        val switchNotifications = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotifications)

        switchBackgroundUpdates.isChecked = prefs.getBoolean("background_updates_enabled", true)
        switchWifiOnly.isChecked = prefs.getBoolean("background_updates_wifi_only", false)
        switchNotifications.isChecked = prefs.getBoolean("notifications_enabled", true)

        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        if (isDarkTheme) {
            val whiteColorStateList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            switchBackgroundUpdates.thumbTintList = whiteColorStateList
            switchBackgroundUpdates.trackTintList = whiteColorStateList.withAlpha(77)
            switchWifiOnly.thumbTintList = whiteColorStateList
            switchWifiOnly.trackTintList = whiteColorStateList.withAlpha(77)
            switchNotifications.thumbTintList = whiteColorStateList
            switchNotifications.trackTintList = whiteColorStateList.withAlpha(77)
        } else {
            val greenColor = Color.parseColor("#2d5016")
            val greenColorStateList = android.content.res.ColorStateList.valueOf(greenColor)
            switchBackgroundUpdates.thumbTintList = greenColorStateList
            switchBackgroundUpdates.trackTintList = greenColorStateList.withAlpha(77)
            switchWifiOnly.thumbTintList = greenColorStateList
            switchWifiOnly.trackTintList = greenColorStateList.withAlpha(77)
            switchNotifications.thumbTintList = greenColorStateList
            switchNotifications.trackTintList = greenColorStateList.withAlpha(77)
        }

        switchBackgroundUpdates.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("background_updates_enabled", isChecked).apply()
            switchWifiOnly.isEnabled = isChecked
            switchNotifications.isEnabled = isChecked
        }

        switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("background_updates_wifi_only", isChecked).apply()
        }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
        }

        switchWifiOnly.isEnabled = switchBackgroundUpdates.isChecked
        switchNotifications.isEnabled = switchBackgroundUpdates.isChecked

        val dialog = AlertDialog.Builder(this)
            .setTitle("⚙️ Einstellungen")
            .setView(dialogView)
            .setPositiveButton("Fertig", null)
            .create()

        dialog.show()

        val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", "https://wildnislehrer.com/datenschutzerklaerung/")
            putExtra("title", "Datenschutzerklärung")
        }
        startActivity(intent)
    }

    private fun openImprint() {
        val intent = Intent(this, ImprintActivity::class.java)
        startActivity(intent)
    }

    private fun setupButtons() {
        // Button 1: Unterstützung
        binding.btnSupport.setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }

        // Button 2: Übung des Tages
        binding.btnDailyExercise.setOnClickListener {
            openWebView(
                url = "https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/uebung-des-tages/",
                title = "Übung des Tages"
            )
        }

        // Button 3: Tagesmeditation
        binding.btnDailyMeditation.setOnClickListener {
            openWebView(
                url = "https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/tagesmeditation/",
                title = "Tagesmeditation"
            )
        }

        // Button 4: Geh-Geschwindigkeit
        binding.btnWalkingSpeed.setOnClickListener {
            startActivity(Intent(this, WalkingSpeedActivity::class.java))
        }

        // Button 5: Tagesnotizen
        binding.btnDailyNotes.setOnClickListener {
            startActivity(Intent(this, DailyNotesActivity::class.java))
        }

        // Button 6: Wetter
        binding.btnWeather.setOnClickListener {
            startActivity(Intent(this, WeatherActivity::class.java))
        }

        // Button 7: Standort
        binding.btnLocation.setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }

        // Button 8: Kompass
        binding.btnCompass.setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }

        // Button 9: Höhenmesser
        binding.btnAltimeter.setOnClickListener {
            startActivity(Intent(this, AltimeterActivity::class.java))
        }

        // Button 10: Hunde-Stammdaten
        binding.btnDogProfiles.setOnClickListener {
            startActivity(Intent(this, DogProfileActivity::class.java))
        }

        // Button 11: Backup & Restore
        binding.btnBackup.setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BACKUP-FUNKTIONEN (FÜR MENÜ)
    // ═══════════════════════════════════════════════════════════════

    private fun createBackup() {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Backup erstellen")
            .setMessage("Bitte warten...")
            .setCancelable(false)
            .create()

        progressDialog.show()

        scope.launch {
            try {
                val backupFile = withContext(Dispatchers.IO) {
                    BackupManager.createBackup(this@StartActivity)
                }

                progressDialog.dismiss()

                Toast.makeText(
                    this@StartActivity,
                    "✅ Backup gespeichert: ${backupFile.name}",
                    Toast.LENGTH_LONG
                ).show()

                offerShare(backupFile)

            } catch (e: Exception) {
                e.printStackTrace()
                progressDialog.dismiss()

                Toast.makeText(
                    this@StartActivity,
                    "❌ Backup-Fehler: ${e.message}",
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
                        "• Tagesnotizen und Fotos\n" +
                        "• Einstellungen\n\n" +
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
        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
    }

    private fun restoreBackup(backupFile: File) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Backup wiederherstellen")
            .setMessage("Bitte warten...")
            .setCancelable(false)
            .create()

        progressDialog.show()

        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    BackupManager.restoreBackup(this@StartActivity, backupFile)
                }

                backupFile.delete()
                progressDialog.dismiss()

                if (success) {
                    val successDialog = AlertDialog.Builder(this@StartActivity)
                        .setTitle("✅ Backup wiederhergestellt")
                        .setMessage("Die App wird jetzt neu gestartet.")
                        .setPositiveButton("OK") { _, _ ->
                            restartApp()
                        }
                        .setCancelable(false)
                        .create()

                    successDialog.show()

                    // ✅ Button-Farben für Dark Mode
                    val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                            Configuration.UI_MODE_NIGHT_YES

                    val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")
                    successDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
                } else {
                    Toast.makeText(
                        this@StartActivity,
                        "❌ Backup konnte nicht wiederhergestellt werden",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                backupFile.delete()
                progressDialog.dismiss()

                Toast.makeText(
                    this@StartActivity,
                    "❌ Restore-Fehler: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun offerShare(backupFile: File) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Backup teilen?")
            .setMessage("Möchtest du das Backup auf ein anderes Gerät übertragen?")
            .setPositiveButton("Teilen") { _, _ ->
                shareBackupFile(backupFile)
            }
            .setNegativeButton("Später") { _, _ ->
                Toast.makeText(
                    this,
                    "Backup unter:\n${backupFile.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
            .create()

        dialog.show()

        // ✅ Button-Farben für Dark Mode
        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")
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
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Backup teilen"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Teilen fehlgeschlagen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

    private fun openWebView(url: String, title: String) {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", url)
            putExtra("title", title)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}