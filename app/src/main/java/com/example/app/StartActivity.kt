package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import android.content.res.Configuration
import com.hundewildnis.app.ContentActivity
import com.hundewildnis.app.R
import com.hundewildnis.app.databinding.ActivityStartBinding

/**
 * StartActivity - Hauptstartseite der App
 * Zeigt 8 große Buttons für tägliche Aktivitäten
 */
class StartActivity : BaseBottomNavActivity() {

    private lateinit var binding: ActivityStartBinding

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
        // ✅ NEU: Unterstützung
        binding.btnSupport.setOnClickListener {
            startActivity(Intent(this, SupportActivity::class.java))
        }

        // Button 1: Übung des Tages
        binding.btnDailyExercise.setOnClickListener {
            openWebView(
                url = "https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/uebung-des-tages/",
                title = "Übung des Tages"
            )
        }

        // Button 2: Tagesmeditation
        binding.btnDailyMeditation.setOnClickListener {
            openWebView(
                url = "https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/tagesmeditation/",
                title = "Tagesmeditation"
            )
        }

        // Button 3: Geh-Geschwindigkeit
        binding.btnWalkingSpeed.setOnClickListener {
            startActivity(Intent(this, WalkingSpeedActivity::class.java))
        }

        // Button 4: Tagesnotizen
        binding.btnDailyNotes.setOnClickListener {
            startActivity(Intent(this, DailyNotesActivity::class.java))
        }

        // NEU: Button 5: Wetter
        binding.btnWeather.setOnClickListener {
            startActivity(Intent(this, WeatherActivity::class.java))
        }

        // NEU: Button 6: Standort
        binding.btnLocation.setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }

        // NEU: Button 7: Kompass
        binding.btnCompass.setOnClickListener {
            startActivity(Intent(this, CompassActivity::class.java))
        }

        // NEU: Button 8: Höhenmesser
        binding.btnAltimeter.setOnClickListener {
            startActivity(Intent(this, AltimeterActivity::class.java))
        }

        // ✅ Hunde-Stammdaten
        binding.btnDogProfiles.setOnClickListener {
            startActivity(Intent(this, DogProfileActivity::class.java)) }
        }

    private fun openWebView(url: String, title: String) {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", url)
            putExtra("title", title)
        }
        startActivity(intent)
    }
}
