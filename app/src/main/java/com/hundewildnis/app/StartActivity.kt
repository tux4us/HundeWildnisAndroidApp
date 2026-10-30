package com.hundewildnis.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.hundewildnis.app.databinding.ActivityStartBinding
import kotlinx.coroutines.*
import com.hundewildnis.app.BuildConfig
import java.io.File
import android.view.View
import coil.load
import coil.size.Scale
import coil.request.CachePolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.GridLayoutManager


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
                    Toast.makeText(this, getString(R.string.start_error_reading_backup, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, getString(R.string.start_notifications_enabled), Toast.LENGTH_SHORT).show()
        }
    }

    override fun getBottomNavigationView() = binding.bottomNavigation
    override fun getCurrentNavigationItemId() = R.id.nav_home

    override fun onHomeSelected() {
        // Falls wir schon in StartActivity sind, nichts tun
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
        binding.toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu_hamburger)?.apply {
            // Themenkonformes Tinting: das Icon war fest auf Weiß kodiert und dadurch
            // im hellen Modus auf hellem Toolbar-Hintergrund unsichtbar (weiß auf weiß).
            setTint(Color.WHITE)
        }

        setupButtons()
        super.setupBottomNavigation()

        // ✅ Hintergrund-Updates initialisieren (Wichtig bei Neuinstallation!)
        UpdateHelper.scheduleContentUpdates(this)

        // ✅ Erst-Info Dialog für Benachrichtigungen (Google Play Konform)
        checkNotificationDisclosure()

        // ✅ Update-Dialog prüfen
        checkForUpdate()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val textColor = Color.WHITE

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
            R.id.action_language -> { showLanguageDialog(); true }
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

    private fun checkNotificationDisclosure() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasAccepted = prefs.getBoolean("notification_disclosure_accepted", false)

        if (!hasAccepted) {
            showNotificationDisclosure()
        }
    }

    private fun showNotificationDisclosure() {

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.start_notification_disclosure_title))
            .setMessage(getString(R.string.start_notification_disclosure_message))
            .setPositiveButton(getString(R.string.start_notification_disclosure_accept)) { _, _ ->
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putBoolean("notification_disclosure_accepted", true).apply()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton(getString(R.string.start_notification_disclosure_decline), null)
            .create().apply {
                show()
            }
    }


    private fun showInstructionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_instructions, null)
        view.findViewById<TextView>(R.id.tvInsTitle).setTextColor(Color.WHITE)
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton(getString(R.string.start_understood), null).create()
        dialog.show()
    }

    private fun showPermissionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_permissions, null)
        view.findViewById<TextView>(R.id.tvPermTitle).setTextColor(Color.WHITE)
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton(getString(R.string.ok), null).create()
        dialog.show()
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)

        val titleColor = Color.WHITE
        val linkColor = Color.parseColor("#B0A25A")

        dialogView.findViewById<TextView>(R.id.tvAboutTitle).setTextColor(titleColor)

        val tvWeb = dialogView.findViewById<TextView>(R.id.tvAboutWeb)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tvAboutEmail)
        tvWeb.setTextColor(linkColor)
        tvEmail.setTextColor(linkColor)

        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "1.0.0" }
        dialogView.findViewById<TextView>(R.id.tvAboutVersion).text = getString(R.string.start_version, versionName)

        tvWeb.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wildnislehrer.com"))) }
        tvEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:hundewildnisschuleapp@online.de")
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.start_feedback_subject))
            }
            startActivity(Intent.createChooser(intent, getString(R.string.start_send_email)))
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).setPositiveButton(getString(R.string.start_close), null).create()
        dialog.show()
    }

    private fun showLanguageDialog() {

        // Explizite Liste um Index-Fehler zu vermeiden
        val supportedLanguages = listOf(
            LanguageManager.SupportedLanguage.ENGLISH,
            LanguageManager.SupportedLanguage.GERMAN
        )
        
        val displayNames = supportedLanguages.map { it.displayName }.toTypedArray()
        val currentLanguage = LanguageManager.getCurrentLanguage()
        
        // Finde den Index der aktuellen Sprache in unserer Liste
        val currentIndex = supportedLanguages.indexOfFirst { it.tag == currentLanguage.tag }
            .let { if (it == -1) 0 else it }

        var selectedIndex = currentIndex

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.start_language_dialog_title))
            .setSingleChoiceItems(displayNames, currentIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                if (selectedIndex != currentIndex) {
                    val chosen = supportedLanguages[selectedIndex]
                    LanguageManager.setLanguage(chosen)
                    
                    // Recreate wird oft automatisch gerufen, aber wir forcieren es für sofortige Wirkung
                    // und nutzen eine kleine Verzögerung um AppCompatDelegate Zeit zu geben
                    binding.root.postDelayed({
                        recreate()
                    }, 100)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .apply {
            }
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val appSettings = AppSettings(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val accentColor = Color.WHITE
        val colorStateList = android.content.res.ColorStateList.valueOf(accentColor)

        val s1 = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBackgroundUpdates)
        val s2 = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchWifiOnly)
        val s3 = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotifications)
        val orsEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editOrsApiKey)
        val orsLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutOrsApiKey)
        val btnClearOrs = dialogView.findViewById<View>(R.id.btnClearOrsKey)
        dialogView.findViewById<View>(R.id.btnNetworkServices).setOnClickListener {
            showNetworkServicesDialog()
        }

        orsLayout.setEndIconOnClickListener {
            showOrsInfoDialog()
        }

        btnClearOrs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.start_delete_key_title))
                .setMessage(getString(R.string.start_delete_key_message))
                .setPositiveButton(getString(R.string.start_delete)) { _, _ ->
                    appSettings.clearOrsApiKey()
                    orsEdit.setText("")
                    Toast.makeText(this, getString(R.string.start_key_deleted), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        listOf(s1, s2, s3).forEach {
            it.thumbTintList = colorStateList
            it.trackTintList = colorStateList.withAlpha(77)
        }

        s1.isChecked = prefs.getBoolean("background_updates_enabled", true)
        s2.isChecked = prefs.getBoolean("background_updates_wifi_only", false)
        s3.isChecked = prefs.getBoolean("notifications_enabled", true)

        if (appSettings.hasCustomOrsKey()) {
            orsEdit.setText("********")
        }

        s1.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("background_updates_enabled", isChecked).apply()
            s2.isEnabled = isChecked; s3.isEnabled = isChecked
        }
        s2.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_wifi_only", isChecked).apply() }
        s3.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("notifications_enabled", isChecked).apply() }

        s2.isEnabled = s1.isChecked; s3.isEnabled = s1.isChecked

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.start_settings_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.start_done)) { _, _ ->
                val newKey = orsEdit.text?.toString() ?: ""
                if (newKey.isNotBlank() && newKey != "********") {
                    appSettings.saveOrsApiKey(newKey)
                }
            }
            .create()
        dialog.show()
    }

    /**
     * Wetteranbieter (OpenWeatherMap oder Open-Meteo, jeweils mit eigenem Zugang) und Overpass.
     * Es gibt keinen vorbelegten Wetterdienst: Ohne Auswahl und Zugang ruft die App kein Wetter ab.
     * Es werden nur https-Adressen akzeptiert.
     */
    private fun showNetworkServicesDialog() {
        val settings = AppSettings(this)
        val view = layoutInflater.inflate(R.layout.dialog_network_services, null)
        val radio = view.findViewById<android.widget.RadioGroup>(R.id.radioWeatherProvider)
        val groupOwm = view.findViewById<View>(R.id.groupOwm)
        val groupOm = view.findViewById<View>(R.id.groupOpenMeteo)
        val editOwmKey = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editOwmApiKey)
        val editWeather = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editWeatherUrl)
        val editAir = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editAirQualityUrl)
        val editKey = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editWeatherApiKey)
        val editOverpass = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editOverpassUrl)
        val btnReset = view.findViewById<View>(R.id.btnResetNetworkServices)

        view.findViewById<View>(R.id.btnWeatherHelp).setOnClickListener { showWeatherHelpDialog() }

        fun updateGroups() {
            groupOwm.visibility = if (radio.checkedRadioButtonId == R.id.radioProviderOwm) View.VISIBLE else View.GONE
            groupOm.visibility = if (radio.checkedRadioButtonId == R.id.radioProviderOpenMeteo) View.VISIBLE else View.GONE
        }
        radio.check(when (settings.getWeatherProvider()) {
            WeatherProviderType.OPEN_WEATHER_MAP -> R.id.radioProviderOwm
            WeatherProviderType.OPEN_METEO -> R.id.radioProviderOpenMeteo
            WeatherProviderType.NONE -> R.id.radioProviderNone
        })
        updateGroups()
        radio.setOnCheckedChangeListener { _, _ -> updateGroups() }

        settings.getWeatherBaseUrl()?.let { editWeather.setText(it) }
        settings.getAirQualityBaseUrl()?.let { editAir.setText(it) }
        settings.getCustomOverpassUrl()?.let { editOverpass.setText(it) }
        if (settings.hasWeatherApiKey()) editKey.setText("********")
        if (settings.hasOwmApiKey()) editOwmKey.setText("********")

        btnReset.setOnClickListener {
            radio.check(R.id.radioProviderNone)
            editOwmKey.setText("")
            editWeather.setText("")
            editAir.setText("")
            editKey.setText("")
            editOverpass.setText("")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.net_services_title))
            .setView(view)
            .setPositiveButton(getString(R.string.start_done), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.show()

        // Eigener Klick-Handler, damit der Dialog bei ungültiger Eingabe offen bleibt
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val inputs = listOf(editWeather, editAir, editOverpass).map { it.text?.toString()?.trim().orEmpty() }
            val invalid = inputs.firstOrNull { it.isNotEmpty() && AppSettings.normalizeHttpsUrl(it) == null }
            if (invalid != null) {
                Toast.makeText(this, getString(R.string.net_services_invalid_url, invalid), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            settings.setWeatherServers(inputs[0], inputs[1])
            settings.setCustomOverpassUrl(inputs[2])

            val omKeyInput = editKey.text?.toString()?.trim().orEmpty()
            when {
                omKeyInput.isEmpty() -> settings.clearWeatherApiKey()
                omKeyInput != "********" -> settings.saveWeatherApiKey(omKeyInput)
            }
            val owmKeyInput = editOwmKey.text?.toString()?.trim().orEmpty()
            when {
                owmKeyInput.isEmpty() -> settings.clearOwmApiKey()
                owmKeyInput != "********" -> settings.saveOwmApiKey(owmKeyInput)
            }

            val chosen = when (radio.checkedRadioButtonId) {
                R.id.radioProviderOwm -> WeatherProviderType.OPEN_WEATHER_MAP
                R.id.radioProviderOpenMeteo -> WeatherProviderType.OPEN_METEO
                else -> WeatherProviderType.NONE
            }
            settings.setWeatherProvider(chosen)

            val incomplete = when (chosen) {
                WeatherProviderType.OPEN_WEATHER_MAP -> !settings.hasOwmApiKey()
                WeatherProviderType.OPEN_METEO -> settings.getWeatherBaseUrl() == null
                WeatherProviderType.NONE -> false
            }
            Toast.makeText(
                this,
                getString(if (incomplete) R.string.net_services_saved_incomplete else R.string.net_services_saved),
                Toast.LENGTH_LONG
            ).show()
            dialog.dismiss()
        }
    }

    /** Ausführliche Anleitung: Warum ein eigener Zugang nötig ist und wie man ihn anlegt. */
    private fun showWeatherHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.net_services_help_title))
            .setMessage(getString(R.string.net_services_help_body))
            .setPositiveButton(getString(R.string.ok), null)
            .setNeutralButton(getString(R.string.net_services_help_open_owm)) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://home.openweathermap.org/users/sign_up")))
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.net_services_help_no_browser), Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun showOrsInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.start_ors_api_key_title))
            .setMessage(getString(R.string.start_ors_api_key_message))
            .setPositiveButton(getString(R.string.ok), null)
            .create()
            .apply {
                show()
            }
    }

    private fun openPrivacyPolicy() { startActivity(Intent(this, PrivacyActivity::class.java)) }

    private fun openImprint() { startActivity(Intent(this, ImprintActivity::class.java)) }

    // ════════════════════════════════════════════════════════════════
// ERSATZ für die alte setupButtons()-Funktion in StartActivity.kt
// ════════════════════════════════════════════════════════════════
//
// Einbau-Hinweise:
// 1. Diese Funktion ersetzt die alte "private fun setupButtons()" komplett.
// 2. Zusätzliche Imports oben in StartActivity.kt ergänzen:
//      import androidx.recyclerview.widget.GridLayoutManager
// 3. Die Kachel-IDs (z.B. "daily_exercise") werden im onTileClick-Callback
//    ausgewertet und lösen exakt dieselben Intents wie vorher aus.
// 4. StartGroupColors.forGroup() liest bei jedem Aufruf das aktuelle Theme
//    (Hell/Dunkel) aus — bei Theme-Wechsel per toggleTheme() wird die
//    Activity ohnehin per recreate() neu aufgebaut, die Farben passen sich
//    also automatisch an, ohne zusätzlichen Code.

    private fun setupButtons() {
        setupStartGrids()
    }


    /**
     * Baut die 6 Icon-Grid-Gruppen der Startseite auf.
     * spanCount wird dynamisch aus der aktuellen Bildschirmbreite berechnet,
     * damit Tablets automatisch mehr Spalten bekommen und große Systemschrift
     * automatisch weniger Spalten (siehe calculateSpanCount()).
     */
    private fun setupStartGrids() {
        val spanCount = calculateSpanCount(referenceItemWidthDp = 100)

        // GRUPPE 1 (Index 0): Übungen der Woche
        bindGrid(
            recyclerView = binding.gridGroup1,
            spanCount = spanCount,
            groupIndex = 0,
            tiles = listOf(
                StartTile("entspannung", R.drawable.ic_breathing, getString(R.string.tile_relaxation))
            )
        )

        // GRUPPE 2 (Index 1): Trainingswerkzeuge
        bindGrid(
            recyclerView = binding.gridGroup2,
            spanCount = spanCount,
            groupIndex = 1,
            tiles = listOf(
                StartTile("walking_speed", R.drawable.ic_stopwatch, getString(R.string.tile_pace_trainer)),
                StartTile("communication", R.drawable.ic_microphone, getString(R.string.tile_voice_trainer)),
                StartTile("sound_level", R.drawable.ic_sound_level, getString(R.string.tile_volume_trainer)),
                StartTile("interval_timer", R.drawable.ic_interval, getString(R.string.tile_interval_timer)),
                StartTile("training_diary", R.drawable.ic_diary, getString(R.string.tile_training_diary)),
                StartTile("activity_calendar", R.drawable.ic_calendar_moon, getString(R.string.tile_activity_calendar)),
                StartTile("nasenarbeit", R.drawable.ic_nose_work, getString(R.string.tile_nasenarbeit)),
                StartTile("spiel_spass", R.drawable.ic_spiel_spass, getString(R.string.tile_spiel_spass))
            )
        )

        // GRUPPE 3 (Index 2): Outdoorwerkzeuge
        bindGrid(
            recyclerView = binding.gridGroup3,
            spanCount = spanCount,
            groupIndex = 2,
            tiles = listOf(
                StartTile("daily_notes", R.drawable.ic_notes, getString(R.string.tile_daily_notes)),
                StartTile("weather", R.drawable.ic_weather, getString(R.string.tile_area_weather)),
                StartTile("thunderstorm", R.drawable.ic_thunderstorm, getString(R.string.tile_thunderstorm_warning)),
                StartTile("location", R.drawable.ic_navigation, getString(R.string.tile_area_navigation)),
                StartTile("compass", R.drawable.ic_compass, getString(R.string.tile_compass)),
                StartTile("altimeter", R.drawable.ic_altimeter, getString(R.string.tile_altimeter))
            )
        )

        // GRUPPE 4 (Index 3): Hund, Sicherheit und Notfall
        bindGrid(
            recyclerView = binding.gridGroup4,
            spanCount = spanCount,
            groupIndex = 3,
            tiles = listOf(
                StartTile("dog_profiles", R.drawable.ic_dog, getString(R.string.tile_master_data)),
                StartTile("pulscheck", R.drawable.ic_heartbeat, getString(R.string.tile_pulse_check)),
                StartTile("tick_loupe", R.drawable.ic_magnifier, getString(R.string.tile_tick_loupe)),
                StartTile("plant_scout", R.drawable.ic_plant_warning, getString(R.string.tile_hazardous_plants)),
                StartTile("location_share", R.drawable.ic_map_pin_share, getString(R.string.tile_location_share)),
                StartTile("emergency", R.drawable.ic_emergency, getString(R.string.tile_emergency), isDanger = true)
            )
        )

        // GRUPPE 5 (Index 4): Informationen und Lernen
        bindGrid(
            recyclerView = binding.gridGroup5,
            spanCount = spanCount,
            groupIndex = 4,
            tiles = listOf(
                StartTile("nav_courses", R.drawable.ic_courses, getString(R.string.tile_online_courses))
            )
        )

        // GRUPPE 6 (Index 5): App-Service
        bindGrid(
            recyclerView = binding.gridGroup6,
            spanCount = spanCount,
            groupIndex = 5,
            tiles = listOf(
                StartTile("backup", R.drawable.ic_backup, getString(R.string.tile_backup)),
                StartTile("support", R.drawable.ic_heart_support, getString(R.string.tile_support))
            )
        )
    }

    /**
     * Baut eine einzelne Gruppen-RecyclerView auf: GridLayoutManager + Adapter + Klick-Routing.
     * Die Gruppenfarben werden zentral über StartGroupColors aufgelöst (siehe StartGroupColors.kt),
     * damit Dark/Light-Entscheidung an genau einer Stelle im Code getroffen wird.
     */
    private fun bindGrid(
        recyclerView: androidx.recyclerview.widget.RecyclerView,
        spanCount: Int,
        groupIndex: Int,
        tiles: List<StartTile>
    ) {
        val colors = StartGroupColors.forGroup(this, groupIndex)
        recyclerView.layoutManager = GridLayoutManager(this, spanCount)
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = StartTileAdapter(
            tiles = tiles,
            chipColorRes = colors.chipColorRes,
            accentColorRes = colors.accentColorRes,
            onTileClick = ::handleTileClick
        )
    }

    /**
     * Zentrales Klick-Routing für alle Kacheln — ersetzt die alten binding.btnXyz.setOnClickListener-Aufrufe 1:1.
     */
    private fun handleTileClick(tileId: String) {
        when (tileId) {
            "entspannung" -> startActivity(Intent(this, EntspannungstrainingActivity::class.java))
            "walking_speed" -> startActivity(Intent(this, WalkingSpeedActivity::class.java))
            "communication" -> startActivity(Intent(this, CommunicationActivity::class.java))
            "sound_level" -> startActivity(Intent(this, SoundLevelActivity::class.java))
            "interval_timer" -> startActivity(Intent(this, IntervalTimerSetupActivity::class.java))
            "training_diary" -> startActivity(Intent(this, TrainingDiaryActivity::class.java))
            "activity_calendar" -> startActivity(Intent(this, ActivityCalendarActivity::class.java))
            "nasenarbeit" -> startActivity(Intent(this, NasenarbeitActivity::class.java))
            "spiel_spass" -> startActivity(Intent(this, SpielSpassDogSelectActivity::class.java))
            "daily_notes" -> startActivity(Intent(this, DailyNotesActivity::class.java))
            "weather" -> startActivity(Intent(this, WeatherActivity::class.java))
            "thunderstorm" -> startActivity(Intent(this, ThunderstormWarningActivity::class.java))
            "location" -> startActivity(Intent(this, LocationActivity::class.java))
            "compass" -> startActivity(Intent(this, CompassActivity::class.java))
            "altimeter" -> startActivity(Intent(this, AltimeterActivity::class.java))
            "dog_profiles" -> startActivity(Intent(this, DogProfileActivity::class.java))
            "pulscheck" -> startActivity(Intent(this, PulscheckDogSelectActivity::class.java))
            "tick_loupe" -> startActivity(Intent(this, TickLoupeActivity::class.java))
            "plant_scout" -> startActivity(Intent(this, PlantScoutActivity::class.java))
            "location_share" -> startActivity(Intent(this, LocationShareActivity::class.java))
            "emergency" -> openEmergencyWizard()
            "nav_courses" -> openMainActivity(MainActivity.VIEW_COURSES)
            "backup" -> startActivity(Intent(this, BackupActivity::class.java))
            "support" -> startActivity(Intent(this, SupportActivity::class.java))
        }
    }

    /**
     * Berechnet die Spaltenzahl für die Icon-Grids dynamisch aus der aktuellen
     * Bildschirmbreite UND der Systemschrift-Skalierung (Bedienungshilfen).
     *
     * - referenceItemWidthDp: Zielbreite EINER Kachel in dp bei Schriftskalierung 100%.
     * - Bei größerer Systemschrift wächst die effektive Zielbreite mit, wodurch
     *   automatisch weniger Spalten berechnet werden (verhindert abgeschnittenen Text).
     * - min 2, max 5 Spalten (Absprache: Phone Hochformat 3, Tablet 4-5).
     */
    private fun calculateSpanCount(referenceItemWidthDp: Int): Int {
        val screenWidthDp = resources.configuration.screenWidthDp
        val fontScale = resources.configuration.fontScale
        val effectiveItemWidthDp = referenceItemWidthDp * fontScale

        // Dynamische Berechnung
        val rawSpan = (screenWidthDp / effectiveItemWidthDp).toInt()

        // Korrektur für Phones vs Tablets:
        // Phones haben meist screenWidthDp < 600.
        // Wir erzwingen auf Phones 2 Spalten, auf Tablets erlauben wir mehr.
        return if (screenWidthDp < 600) {
            rawSpan.coerceIn(2, 2)
        } else {
            rawSpan.coerceIn(3, 5)
        }
    }

    private fun createBackup() {
        val progress = AlertDialog.Builder(this).setTitle(getString(R.string.start_create_backup)).setMessage(getString(R.string.start_please_wait)).setCancelable(false).show()
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { BackupManager.createBackup(this@StartActivity) }
                progress.dismiss()
                Toast.makeText(this@StartActivity, getString(R.string.start_backup_saved), Toast.LENGTH_LONG).show()
                offerShare(file)
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(this@StartActivity, getString(R.string.start_error_generic, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun selectBackupFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        pickBackupFile.launch(intent)
    }

    private fun confirmRestore(file: File) {
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.start_restore_backup_title)).setMessage(getString(R.string.start_restore_backup_message)).setPositiveButton(getString(R.string.yes)) { _, _ -> restoreBackup(file) }.setNegativeButton(getString(R.string.cancel)) { _, _ -> file.delete() }.create()
        dialog.show()
    }

    private fun restoreBackup(file: File) {
        val progress = AlertDialog.Builder(this).setTitle(getString(R.string.start_restoration)).setMessage(getString(R.string.start_please_wait)).setCancelable(false).show()
        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) { BackupManager.restoreBackup(this@StartActivity, file) }
                file.delete(); progress.dismiss()
                if (success) restartApp()
                else Toast.makeText(this@StartActivity, getString(R.string.start_import_error), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                progress.dismiss(); Toast.makeText(this@StartActivity, getString(R.string.start_error_generic, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun offerShare(file: File) {

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.start_share_backup_title))
            .setPositiveButton(getString(R.string.start_share)) { _, _ -> shareBackupFile(file) }
            .setNegativeButton(getString(R.string.start_later), null)
            .create().apply {
                show()
            }
    }

    private fun shareBackupFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, getString(R.string.backup_share_title)))
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent); finishAffinity()
    }

    private fun openMainActivity(viewType: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("INITIAL_VIEW", viewType)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    // ════════════════════════════════════════════════════════════════
    // ✅ UPDATE-DIALOG FUNKTIONEN
    // ════════════════════════════════════════════════════════════════

    private fun checkForUpdate() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentVersion = BuildConfig.VERSION_CODE
        val lastSeenVersion = prefs.getInt("last_seen_version", 0)

        if (currentVersion > lastSeenVersion) {
            showUpdateDialog()
            prefs.edit().putInt("last_seen_version", currentVersion).apply()
        }
    }

    private fun showUpdateDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update_support, null)

        val titleColor = Color.WHITE
        val textColor = ContextCompat.getColor(this, R.color.rd_text_primary)
        val buttonBgColor = ContextCompat.getColor(this, R.color.gl_primary_fill)
        val buttonOutlineColor = Color.WHITE

        dialogView.findViewById<TextView>(R.id.tvUpdateTitle).setTextColor(titleColor)
        dialogView.findViewById<TextView>(R.id.tvUpdateVersion).apply {
            text = getString(R.string.start_version, BuildConfig.VERSION_NAME)
            setTextColor(textColor)
        }
        dialogView.findViewById<TextView>(R.id.tvUpdateMessage).setTextColor(textColor)

        // Dialog erstellen
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Unterstützen-Button
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSupport).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(buttonBgColor)
            setOnClickListener {
                startActivity(Intent(this@StartActivity, SupportActivity::class.java))
                dialog.dismiss()
            }
        }

        // Später-Button
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLater).apply {
            setTextColor(buttonOutlineColor)
            strokeColor = android.content.res.ColorStateList.valueOf(buttonOutlineColor)
            strokeWidth = 2
            setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        // WICHTIG: Auswahl in der BottomNav zurücksetzen, wenn wir zu Home zurückkehren
        binding.bottomNavigation.selectedItemId = R.id.nav_home
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
