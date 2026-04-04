package com.hundewildnis.app

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.hundewildnis.app.databinding.ActivityAddSessionBinding
import android.content.res.Configuration

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
        setupSuccessButtons()  // ✅ Korrigierte Funktion
        setupWeatherButton()
        setupGpsButton()
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

    // ✅ NUR EINE setupSuccessButtons() Funktion!
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
            Color.WHITE  // ✅ Weiß im Dark Mode
        } else {
            Color.parseColor("#4a6135")  // Grün im Light Mode
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
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("📍 Wetter abrufen")
            .setMessage("Möchtest du die aktuellen Wetterdaten von deinem Standort abrufen?\n\nDies nutzt die gleiche Funktion wie in der Wetter-Activity.")
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

    private fun fetchWeatherData() {
        val prefs = getSharedPreferences("weather_data", MODE_PRIVATE)
        val lastWeather = prefs.getString("last_weather", "")
        val lastTemp = prefs.getString("last_temp", "")

        if (lastWeather.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "Keine Wetterdaten verfügbar. Bitte erst Wetter-Activity öffnen.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            weatherData = "$lastWeather, $lastTemp°C"
            binding.tvWeatherDisplay.text = "☀️ $weatherData"
            binding.tvWeatherDisplay.visibility = View.VISIBLE
            Toast.makeText(this, "✅ Wetter hinzugefügt", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupGpsButton() {
        binding.btnFetchGps.setOnClickListener {
            showGpsPermissionDialog()
        }
    }

    private fun showGpsPermissionDialog() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("📍 GPS-Standort abrufen")
            .setMessage("Möchtest du deinen aktuellen Standort für diese Trainingseinheit speichern?\n\nDer Standort wird als Koordinaten und OpenStreetMap-Link gespeichert.")
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
        val prefs = getSharedPreferences("location_data", MODE_PRIVATE)
        val lastLat = prefs.getString("last_latitude", "")
        val lastLon = prefs.getString("last_longitude", "")
        val lastLocationName = prefs.getString("last_location_name", "")

        if (lastLat.isNullOrEmpty() || lastLon.isNullOrEmpty()) {
            Toast.makeText(
                this,
                "Kein GPS verfügbar. Bitte erst Standort in Navigation aktivieren.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            gpsLatitude = lastLat.toDoubleOrNull()
            gpsLongitude = lastLon.toDoubleOrNull()
            gpsLocationName = lastLocationName ?: ""

            val displayText = if (gpsLocationName.isNotEmpty()) {
                "📍 $gpsLocationName"
            } else {
                "📍 ${String.format("%.4f, %.4f", gpsLatitude, gpsLongitude)}"
            }

            binding.tvGpsDisplay.text = displayText
            binding.tvGpsDisplay.visibility = View.VISIBLE
            Toast.makeText(this, "✅ Standort hinzugefügt", Toast.LENGTH_SHORT).show()
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
            successRating = selectedSuccessRating,  // ✅ Nutze selectedSuccessRating
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
