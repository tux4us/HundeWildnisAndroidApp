package com.hundewildnis.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.hundewildnis.app.databinding.ActivityAddSessionBinding
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddSessionActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityAddSessionBinding
    private lateinit var profile: DogProfile
    private lateinit var category: TrainingCategory
    private lateinit var manager: TrainingManager

    // ✅ Alle Variablen konsolidiert
    private var selectedSuccessRating: Int = 3  // Default: Erfolgreich
    private var selectedDuration: Int = 15  // Default: 15 Min
    private var weatherData: String = ""
    private var gpsLatitude: Double? = null
    private var gpsLongitude: Double? = null
    private var gpsLocationName: String = ""

    // ✅ Permission Launcher
    private val locationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                // Permission granted - führe ursprüngliche Aktion aus
                when (pendingAction) {
                    PendingAction.WEATHER -> fetchWeatherData()
                    PendingAction.GPS -> fetchGpsLocation()
                    PendingAction.NONE -> {}
                }
                pendingAction = PendingAction.NONE
            }
            else -> {
                Toast.makeText(
                    this,
                    "⚠️ Standort-Berechtigung erforderlich für diese Funktion",
                    Toast.LENGTH_LONG
                ).show()
                pendingAction = PendingAction.NONE
            }
        }
    }

    // ✅ Merkt sich welche Aktion nach Permission-Grant ausgeführt werden soll
    private var pendingAction = PendingAction.NONE

    private enum class PendingAction {
        NONE, WEATHER, GPS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")!!
        val categoryId = intent.getIntExtra("category_id", 13)
        category = TrainingCategory.fromId(categoryId)!!

        manager = TrainingManager(this)

        setupUI()
        setupListeners()
        setupSuccessButtons()
        setupWeatherButton()
        setupGpsButton()

        // ✅ FIX: Button-Farben für Dark Mode
        applyButtonColors()
    }

    // ✅ NEU: Button-Farben für Dark Mode
    private fun applyButtonColors() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        if (isDark) {
            // Im Dark Mode: Weiße Schrift für OutlinedButtons/TextButtons
            binding.btnFetchWeather.setTextColor(android.graphics.Color.WHITE)
            binding.btnFetchGps.setTextColor(android.graphics.Color.WHITE)
            binding.btnCancel.setTextColor(android.graphics.Color.WHITE)
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Neue Trainingseinheit"

        binding.tvCategoryName.text = category.displayName
        binding.tvDogName.text = profile.name

        // Dauer-Slider
        binding.sliderDuration.valueFrom = TrainingConfig.MIN_TRAINING_DURATION.toFloat()
        binding.sliderDuration.valueTo = TrainingConfig.MAX_TRAINING_DURATION.toFloat()
        binding.sliderDuration.value = TrainingConfig.IDEAL_TRAINING_DURATION.toFloat()
        binding.tvDurationValue.text = "${TrainingConfig.IDEAL_TRAINING_DURATION} Min"

        // Dropdowns
        setupDropdowns()
    }

    private fun setupDropdowns() {
        // Ablenkung
        val distractionLevels = DistractionLevel.values().map { it.displayName }
        val distractionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, distractionLevels)
        binding.spinnerDistraction.setAdapter(distractionAdapter)
        binding.spinnerDistraction.setText(DistractionLevel.LOW.displayName, false)

        // Schwierigkeit
        val difficultyLevels = DifficultyLevel.values().map { it.displayName }
        val difficultyAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, difficultyLevels)
        binding.spinnerDifficulty.setAdapter(difficultyAdapter)
        binding.spinnerDifficulty.setText(DifficultyLevel.NORMAL.displayName, false)

        // Wetter (optional)
        val weatherConditions = listOf("Nicht angegeben") + WeatherCondition.values().map { it.displayName }
        val weatherAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, weatherConditions)
        binding.spinnerWeather.setAdapter(weatherAdapter)
        binding.spinnerWeather.setText("Nicht angegeben", false)

        // Ort (optional)
        val locations = listOf("Nicht angegeben") + LocationType.values().map { it.displayName }
        val locationAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, locations)
        binding.spinnerLocation.setAdapter(locationAdapter)
        binding.spinnerLocation.setText("Nicht angegeben", false)
    }

    private fun setupListeners() {
        // Dauer-Slider
        binding.sliderDuration.addOnChangeListener { _, value, _ ->
            selectedDuration = value.toInt()
            binding.tvDurationValue.text = "$selectedDuration Min"
        }

        // Speichern & Abbrechen
        binding.btnSave.setOnClickListener { saveSession() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun setupSuccessButtons() {
        val buttons = listOf(
            binding.btnSuccess1,
            binding.btnSuccess2,
            binding.btnSuccess3,
            binding.btnSuccess4
        )

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val colorActive = Color.parseColor("#4a6135")  // Dunkelgrün
        val colorInactive = if (isDark) {
            Color.parseColor("#424242")  // Dunkelgrau
        } else {
            Color.parseColor("#E0E0E0")  // Hellgrau
        }

        val textColorActive = Color.WHITE
        val textColorInactive = if (isDark) {
            Color.WHITE
        } else {
            Color.parseColor("#4a6135")
        }

        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectedSuccessRating = index + 1  // 1-4

                // Alle Buttons zurücksetzen
                buttons.forEach { btn ->
                    btn.setBackgroundColor(colorInactive)
                    btn.setTextColor(textColorInactive)
                }

                // Gewählten Button hervorheben
                button.setBackgroundColor(colorActive)
                button.setTextColor(textColorActive)
            }
        }

        // Standard: Button 3 (Erfolgreich) vorauswählen
        buttons[2].performClick()
    }

    private fun setupWeatherButton() {
        binding.btnFetchWeather.setOnClickListener {
            showWeatherPermissionDialog()
        }
    }

    private fun showWeatherPermissionDialog() {
        // ✅ Prüfe erst Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission fehlt → Zeige Disclosure
            showProminentDisclosure(PendingAction.WEATHER)
            return
        }

        // Permission vorhanden → Direkt Dialog
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("📍 Wetter abrufen")
            .setMessage("Möchtest du die aktuellen Wetterdaten von deinem Standort abrufen?\n\n" +
                    "Dies nutzt deinen aktuellen GPS-Standort und ruft Live-Wetterdaten ab.")
            .setPositiveButton("Ja, abrufen") { _, _ ->
                fetchWeatherData()
            }
            .setNegativeButton("Abbrechen", null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
            }
    }

    // ✅ PROMINENT DISCLOSURE (Google Play Policy)
    private fun showProminentDisclosure(action: PendingAction) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("📍 Standortzugriff für Trainingstagebuch")
            .setMessage("HUNDEWILDNIS - App benötigt Zugriff auf Deinen Standort für folgende Zwecke:\n\n" +
                    "• TRAININGSTAGEBUCH:\n" +
                    "Du kannst Deine Trainingseinheiten optional mit Wetterdaten und GPS-Koordinaten ergänzen, " +
                    "um später nachvollziehen zu können, unter welchen Bedingungen das Training stattgefunden hat.\n\n" +
                    "• NOTFALLMELDUNG:\n" +
                    "Um Deine Sicherheit und die Deines Hundes zu erhöhen, kann HundeWildnis Deinen aktuellen GPS-Standort " +
                    "erfassen und in die Notfallmeldung (PDF) einfügen.\n" +
                    "Dies ermöglicht es Tierärzten oder Rettungskräften Euch im Ernstfall schneller zu finden.\n\n" +
                    "• NAVIGATION:\n" +
                    "Damit Du Standort auf der Karte, Routenberechnung zum Ziel und Anzeige von relevanten Orten " +
                    "in der Nähe aufrufen und nutzen kannst.\n\n" +
                    "• STANDORT WETTER:\n" +
                    "Damit Du Wetterdaten und Wettervorhersage an Deinem aktuellen Standort aufrufen und nutzen kannst.\n\n" +
                    "• TAGESNOTIZEN:\n" +
                    "Du kannst Deine Tagesnotizen optional mit Wetterdaten und GPS-Daten Deines Standortes ergänzen.\n\n" +
                    "DATENSCHUTZ:\n" +
                    "Deine Daten werden nur lokal verarbeitet, ausschließlich auf Deinem Gerät gespeichert und nur von Dir " +
                    "allein verwaltet und nur auf Deinen Wunsch zur Teilenfunktion freigegeben.\n\n" +
                    "Zur Nutzung von Navigation und Standortwetter wird der aktuelle Standort anonymisiert an freie Server " +
                    "mitgeteilt, um die gewünschten Daten abzurufen und Dir zur Verfügung zu stellen.\n\n" +
                    "Du kannst die Berechtigungen für Standort jederzeit überprüfen in den Einstellungen:\n" +
                    "Dein Gerät → Einstellungen → Apps → HundeWildnis → Berechtigungen → Standort verwalten → " +
                    "Zugriff nur während der Nutzung der App zulassen.\n\n" +
                    "Dort kann die Berechtigung zum Standort jederzeit dauerhaft geändert, aktiviert oder deaktiviert werden.")
            .setPositiveButton("AKTIVIEREN") { _, _ ->
                // Merke Aktion für nach Permission-Grant
                pendingAction = action
                // Fordere Permission an
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("ABLEHNEN") { _, _ ->
                Toast.makeText(
                    this,
                    "⚠️ Standort-Berechtigung erforderlich für diese Funktion",
                    Toast.LENGTH_LONG
                ).show()
            }
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)
            }
    }

    private fun fetchWeatherData() {
        // ✅ Permission wurde bereits in showWeatherPermissionDialog() geprüft

        // ✅ Hole aktuellen Standort
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager

        try {
            val lastKnown = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)

            if (lastKnown == null) {
                Toast.makeText(
                    this,
                    "Kein GPS-Signal. Bitte warte einen Moment und versuche es erneut.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // ✅ Wetter abrufen mit GPS-Koordinaten
            fetchWeatherFromAPI(lastKnown.latitude, lastKnown.longitude)

        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "Standort-Berechtigung fehlt",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun fetchWeatherFromAPI(lat: Double, lon: Double) {
        // Zeige Lade-Zustand
        binding.btnFetchWeather.isEnabled = false
        binding.btnFetchWeather.text = "⏳ Lädt..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val weather = withContext(Dispatchers.IO) {
                    val url = "https://api.open-meteo.com/v1/forecast?" +
                            "latitude=$lat&longitude=$lon" +
                            "&current=temperature_2m,relative_humidity_2m,precipitation," +
                            "weather_code,wind_speed_10m,wind_direction_10m"

                    val response = java.net.URL(url).readText()
                    val json = org.json.JSONObject(response)
                    val current = json.getJSONObject("current")

                    val temp = current.getDouble("temperature_2m")
                    val hum = current.getInt("relative_humidity_2m")
                    val precip = current.getDouble("precipitation")
                    val code = current.getInt("weather_code")
                    val windSpeed = current.getDouble("wind_speed_10m")
                    val windDir = current.getInt("wind_direction_10m")

                    val desc = getWeatherDescription(code)
                    val windDirStr = getWindDirection(windDir)

                    "🌡️ ${String.format(java.util.Locale.GERMAN, "%.1f", temp)}°C, $desc\n" +
                            "💨 ${String.format(java.util.Locale.GERMAN, "%.1f", windSpeed)} km/h ($windDirStr), " +
                            "💧 $hum%, 🌧️ ${String.format(java.util.Locale.GERMAN, "%.1f", precip)}mm"
                }

                // ✅ Wetter anzeigen
                weatherData = weather
                binding.tvWeatherDisplay.text = "☀️ $weatherData"
                binding.tvWeatherDisplay.visibility = View.VISIBLE

                binding.btnFetchWeather.isEnabled = true
                binding.btnFetchWeather.text = "☀️ Wetter abrufen"

                Toast.makeText(this@AddSessionActivity, "✅ Wetter hinzugefügt", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                binding.btnFetchWeather.isEnabled = true
                binding.btnFetchWeather.text = "☀️ Wetter abrufen"

                Toast.makeText(
                    this@AddSessionActivity,
                    "❌ Fehler beim Wetter-Abruf: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Klarer Himmel"
            1, 2, 3 -> "Leicht bewölkt"
            45, 48 -> "Nebel"
            51, 53, 55 -> "Nieselregen"
            61, 63, 65 -> "Regen"
            71, 73, 75 -> "Schneefall"
            80, 81, 82 -> "Regenschauer"
            95 -> "Gewitter"
            else -> "Wechselhaft"
        }
    }

    private fun getWindDirection(degrees: Int): String {
        val directions = arrayOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
        return directions[((degrees + 22.5) / 45).toInt() % 8]
    }

    private fun setupGpsButton() {
        binding.btnFetchGps.setOnClickListener {
            showGpsPermissionDialog()
        }
    }

    private fun showGpsPermissionDialog() {
        // ✅ Prüfe erst Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission fehlt → Zeige Disclosure
            showProminentDisclosure(PendingAction.GPS)
            return
        }

        // Permission vorhanden → Direkt Dialog
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("📍 GPS-Standort abrufen")
            .setMessage("Möchtest du deinen aktuellen Standort für diese Trainingseinheit speichern?\n\n" +
                    "Der Standort wird als Koordinaten gespeichert.")
            .setPositiveButton("Ja, Standort abrufen") { _, _ ->
                fetchGpsLocation()
            }
            .setNegativeButton("Abbrechen", null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
            }
    }

    private fun fetchGpsLocation() {
        // ✅ Permission wurde bereits in showGpsPermissionDialog() geprüft

        // ✅ Hole aktuellen Standort
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager

        try {
            val lastKnown = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)

            if (lastKnown == null) {
                Toast.makeText(
                    this,
                    "Kein GPS verfügbar. Bitte warte einen Moment und versuche es erneut.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            // ✅ GPS-Daten speichern
            gpsLatitude = lastKnown.latitude
            gpsLongitude = lastKnown.longitude
            gpsLocationName = "" // Optional: Reverse Geocoding könnte hier Namen ermitteln

            val displayText = String.format("📍 %.4f, %.4f", gpsLatitude, gpsLongitude)

            binding.tvGpsDisplay.text = displayText
            binding.tvGpsDisplay.visibility = View.VISIBLE
            Toast.makeText(this, "✅ Standort hinzugefügt", Toast.LENGTH_SHORT).show()

        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "Standort-Berechtigung fehlt",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveSession() {
        // Validierung
        val distractionText = binding.spinnerDistraction.text.toString()
        val difficultyText = binding.spinnerDifficulty.text.toString()

        if (distractionText.isEmpty() || difficultyText.isEmpty()) {
            Toast.makeText(this, "Bitte alle Pflichtfelder ausfüllen", Toast.LENGTH_SHORT).show()
            return
        }

        // Parse Enums
        val distraction = DistractionLevel.values().find { it.displayName == distractionText }
            ?: DistractionLevel.LOW
        val difficulty = DifficultyLevel.values().find { it.displayName == difficultyText }
            ?: DifficultyLevel.NORMAL

        // Optional: Wetter
        val weatherText = binding.spinnerWeather.text.toString()
        val weather = if (weatherText == "Nicht angegeben") null
        else WeatherCondition.values().find { it.displayName == weatherText }

        // Optional: Ort
        val locationText = binding.spinnerLocation.text.toString()
        val location = if (locationText == "Nicht angegeben") null
        else LocationType.values().find { it.displayName == locationText }

        // ✅ Erstelle Session mit allen neuen Feldern
        val session = TrainingSession(
            dogId = profile.id,
            category = category,
            durationMinutes = selectedDuration,
            successRating = selectedSuccessRating,
            distractionLevel = distraction,
            difficulty = difficulty,
            notes = binding.etNotes.text.toString(),
            weatherCondition = weather,
            location = location,
            // ✅ NEU: Wetter & GPS Daten
            weatherData = weatherData,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude,
            gpsLocationName = gpsLocationName
        )

        // Speichern
        manager.saveSession(session)

        // Erfolgsmeldung
        showSuccessDialog()
    }

    private fun showSuccessDialog() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("✅ Trainingseinheit gespeichert")
            .setMessage("Die Trainingseinheit wurde erfolgreich dokumentiert.")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}