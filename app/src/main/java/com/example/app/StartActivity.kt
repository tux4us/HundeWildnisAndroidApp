package com.hundewildnis.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import android.content.res.Configuration
import android.net.Uri
import androidx.core.content.FileProvider
import com.hundewildnis.app.databinding.ActivityStartBinding
import kotlinx.coroutines.*
import java.io.File

/**
 * StartActivity - Hauptstartseite der App
 */
class StartActivity : BaseBottomNavActivity() {

    private lateinit var binding: ActivityStartBinding
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val tempFile = File(cacheDir, "temp_backup.hundebackup")
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    confirmRestore(tempFile)
                } catch (e: Exception) {
                    Toast.makeText(this, "Fehler beim Lesen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun getBottomNavigationView() = binding.bottomNavigation
    override fun getCurrentNavigationItemId() = R.id.nav_home

    override fun onHomeSelected() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "HundeWildnis"
        binding.toolbar.overflowIcon?.setTint(Color.WHITE)

        setupButtons()
        super.setupBottomNavigation()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK

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
            R.id.action_theme -> { toggleTheme(); true }
            R.id.action_settings -> { showSettingsDialog(); true }
            R.id.action_instructions -> { showInstructionsDialog(); true }
            R.id.action_permissions -> { showPermissionsDialog(); true }
            R.id.action_backup_create -> { createBackup(); true }
            R.id.action_backup_restore -> { selectBackupFile(); true }
            R.id.action_privacy -> { openPrivacyPolicy(); true }
            R.id.action_imprint -> { openImprint(); true }
            R.id.action_about -> { showAboutDialog(); true }
            R.id.action_home -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showInstructionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_instructions, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        view.findViewById<TextView>(R.id.tvInsTitle).setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton("Verstanden", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
    }

    private fun showPermissionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_permissions, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        view.findViewById<TextView>(R.id.tvPermTitle).setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton("OK", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val titleColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        val linkColor = if (isDark) Color.parseColor("#8fad6f") else Color.parseColor("#2d5016")
        
        dialogView.findViewById<TextView>(R.id.tvAboutTitle).setTextColor(titleColor)
        
        val tvWeb = dialogView.findViewById<TextView>(R.id.tvAboutWeb)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tvAboutEmail)
        tvWeb.setTextColor(linkColor)
        tvEmail.setTextColor(linkColor)

        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "1.0.0" }
        dialogView.findViewById<TextView>(R.id.tvAboutVersion).text = "Version $versionName"

        tvWeb.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wildnislehrer.com"))) }
        tvEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:hundewildnisschuleapp@online.de")
                putExtra(Intent.EXTRA_SUBJECT, "HundeWildnis App Feedback")
            }
            startActivity(Intent.createChooser(intent, "E-Mail senden"))
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).setPositiveButton("Schließen", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
    }

    private fun toggleTheme() {
        val sharedPreferences = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("is_dark_mode", false)
        sharedPreferences.edit().putBoolean("is_dark_mode", !isDarkMode).apply()
        AppCompatDelegate.setDefaultNightMode(if (isDarkMode) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)
        recreate()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val accentColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        val colorStateList = android.content.res.ColorStateList.valueOf(accentColor)

        val s1 = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBackgroundUpdates)
        val s2 = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchWifiOnly)
        val s3 = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotifications)

        listOf(s1, s2, s3).forEach {
            it.thumbTintList = colorStateList
            it.trackTintList = colorStateList.withAlpha(77)
        }

        s1.isChecked = prefs.getBoolean("background_updates_enabled", true)
        s2.isChecked = prefs.getBoolean("background_updates_wifi_only", false)
        s3.isChecked = prefs.getBoolean("notifications_enabled", true)

        s1.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("background_updates_enabled", isChecked).apply()
            s2.isEnabled = isChecked; s3.isEnabled = isChecked
        }
        s2.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_wifi_only", isChecked).apply() }
        s3.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("notifications_enabled", isChecked).apply() }

        s2.isEnabled = s1.isChecked; s3.isEnabled = s1.isChecked

        val dialog = AlertDialog.Builder(this).setTitle("⚙️ Einstellungen").setView(dialogView).setPositiveButton("Fertig", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accentColor)
    }

    private fun openPrivacyPolicy() {
        startActivity(Intent(this, ContentActivity::class.java).apply {
            putExtra("url", "https://wildnislehrer.com/datenschutzerklaerung/")
            putExtra("title", "Datenschutzerklärung")
        })
    }

    private fun openImprint() { startActivity(Intent(this, ImprintActivity::class.java)) }

    private fun setupButtons() {
        binding.btnSupport.setOnClickListener { startActivity(Intent(this, SupportActivity::class.java)) }
        binding.btnDailyExercise.setOnClickListener { openWebView("https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/uebung-des-tages/", "Übung des Tages") }
        binding.btnDailyMeditation.setOnClickListener { openWebView("https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/tagesmeditation/", "Tagesmeditation") }
        binding.btnWalkingSpeed.setOnClickListener { startActivity(Intent(this, WalkingSpeedActivity::class.java)) }
        binding.btnDailyNotes.setOnClickListener { startActivity(Intent(this, DailyNotesActivity::class.java)) }
        binding.btnWeather.setOnClickListener { startActivity(Intent(this, WeatherActivity::class.java)) }
        binding.btnLocation.setOnClickListener { startActivity(Intent(this, LocationActivity::class.java)) }
        binding.btnCompass.setOnClickListener { startActivity(Intent(this, CompassActivity::class.java)) }
        binding.btnAltimeter.setOnClickListener { startActivity(Intent(this, AltimeterActivity::class.java)) }
        binding.btnDogProfiles.setOnClickListener { startActivity(Intent(this, DogProfileActivity::class.java)) }
        binding.btnBackup.setOnClickListener { startActivity(Intent(this, BackupActivity::class.java)) }
    }

    private fun createBackup() {
        val progress = AlertDialog.Builder(this).setTitle("Backup erstellen").setMessage("Bitte warten...").setCancelable(false).show()
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { BackupManager.createBackup(this@StartActivity) }
                progress.dismiss()
                Toast.makeText(this@StartActivity, "✅ Backup gespeichert", Toast.LENGTH_LONG).show()
                offerShare(file)
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(this@StartActivity, "❌ Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun selectBackupFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        pickBackupFile.launch(intent)
    }

    private fun confirmRestore(file: File) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        val dialog = AlertDialog.Builder(this).setTitle("⚠️ Backup wiederherstellen?").setMessage("Alle Daten werden überschrieben!").setPositiveButton("Ja") { _, _ -> restoreBackup(file) }.setNegativeButton("Abbrechen") { _, _ -> file.delete() }.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
    }

    private fun restoreBackup(file: File) {
        val progress = AlertDialog.Builder(this).setTitle("Wiederherstellung").setMessage("Bitte warten...").setCancelable(false).show()
        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) { BackupManager.restoreBackup(this@StartActivity, file) }
                file.delete(); progress.dismiss()
                if (success) restartApp()
                else Toast.makeText(this@StartActivity, "❌ Fehler beim Import", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                progress.dismiss(); Toast.makeText(this@StartActivity, "❌ Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun offerShare(file: File) {
        AlertDialog.Builder(this).setTitle("Backup teilen?").setPositiveButton("Teilen") { _, _ -> shareBackupFile(file) }.setNegativeButton("Später", null).show()
    }

    private fun shareBackupFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Backup teilen"))
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent); finishAffinity()
    }

    private fun openWebView(url: String, title: String) {
        startActivity(Intent(this, ContentActivity::class.java).apply { putExtra("url", url); putExtra("title", title) })
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
