package com.hundewildnis.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.lifecycle.lifecycleScope
import com.hundewildnis.app.databinding.ActivityAddSessionBinding
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
    private var selectedMeasurementType: MeasurementType = MeasurementType.TIME  // ← NEU
    private var selectedSuccessRating: Int = 3  // Default: Erfolgreich
    private var selectedDuration: Int = 15  // Default: 15 Min
    private var selectedRepetitions: Int = 10  // ← NEU: Default 10 Wiederholungen
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

        applyButtonColors()
    }

    private fun applyButtonColors() {
        // Veraltet, da Buttons durch Grid-Tiles ersetzt wurden
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.add_session_toolbar_title)

        binding.tvCategoryName.text = category.displayName(this)
        binding.tvDogName.text = profile.name

        setupGrids()
        updateSliderForMeasurementType()
        setupDropdowns()
    }

    private fun setupGrids() {
        // Messungstyp
        updateMeasurementTypeGrid()

        // Erfolg
        updateSuccessRatingGrid()

        // Fetch (Wetter/GPS)
        val fetchTiles = listOf(
            StartTile("fetch_weather", R.drawable.ic_weather, "Wetter"),
            StartTile("fetch_gps", R.drawable.ic_navigation, "Standort")
        )
        val colorsFetch = StartGroupColors.forGroup(this, 4) // Blau
        binding.gridAddSessionFetch.layoutManager = GridLayoutManager(this, 2)
        binding.gridAddSessionFetch.adapter = StartTileAdapter(fetchTiles, colorsFetch.chipColorRes, colorsFetch.accentColorRes) { id ->
            if (id == "fetch_weather") showWeatherPermissionDialog() else showGpsPermissionDialog()
        }

        // Aktionen (Speichern/Abbrechen)
        val actionTiles = listOf(
            StartTile("action_cancel", R.drawable.ic_remove, "Abbrechen"),
            StartTile("action_save", R.drawable.ic_notes, "Speichern")
        )
        val colorsActions = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridAddSessionActions.layoutManager = GridLayoutManager(this, 2)
        binding.gridAddSessionActions.adapter = StartTileAdapter(actionTiles, colorsActions.chipColorRes, colorsActions.accentColorRes) { id ->
            if (id == "action_save") saveSession() else finish()
        }
    }

    private fun updateMeasurementTypeGrid() {
        val tiles = listOf(
            StartTile("type_time", R.drawable.ic_stopwatch, "⏱️ Minuten"),
            StartTile("type_reps", R.drawable.ic_notes, "🔢 Wiederholungen")
        )
        // Das gewählte Tile soll eine andere Farbe haben.
        // Hier nutzen wir unterschiedliche Gruppen für "aktiv" / "inaktiv" wenn möglich.
        // Einfacher: Wir nutzen Group 2 (Grün) für aktiv und 5 (Violett/Grau) für inaktiv?
        // StartTileAdapter unterstützt das nicht direkt ohne Neuzuweisung des Adapters.

        val colors = StartGroupColors.forGroup(this, 3) // Ocker
        binding.gridMeasurementType.layoutManager = GridLayoutManager(this, 2)
        binding.gridMeasurementType.adapter = StartTileAdapter(tiles, colors.chipColorRes, colors.accentColorRes) { id ->
            selectedMeasurementType = if (id == "type_time") MeasurementType.TIME else MeasurementType.REPETITIONS
            updateSliderForMeasurementType()
            // Optional: Grid neu laden um Selektion zu zeigen (falls wir Design dafür hätten)
        }
    }

    private fun updateSuccessRatingGrid() {
        val tiles = listOf(
            StartTile("success_1", label = "🔴", emoji = "🔴"),
            StartTile("success_2", label = "🟡", emoji = "🟡"),
            StartTile("success_3", label = "🟢", emoji = "🟢"),
            StartTile("success_4", label = "🟢🟢", emoji = "🟢🟢")
        )
        val colors = StartGroupColors.forGroup(this, 1) // Gelb/Ocker
        binding.gridSuccessRating.layoutManager = GridLayoutManager(this, 4)
        binding.gridSuccessRating.adapter = StartTileAdapter(tiles, colors.chipColorRes, colors.accentColorRes) { id ->
            selectedSuccessRating = id.substringAfter("_").toInt()
            Toast.makeText(this, "Bewertung: $selectedSuccessRating", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ NEU: Slider anpassen je nach Messungstyp
    private fun updateSliderForMeasurementType() {
        when (selectedMeasurementType) {
            MeasurementType.TIME -> {
                binding.sliderDuration.valueFrom = TrainingConfig.MIN_TRAINING_DURATION.toFloat()
                binding.sliderDuration.valueTo = TrainingConfig.MAX_TRAINING_DURATION.toFloat()
                binding.sliderDuration.value = selectedDuration.toFloat()
                binding.tvDurationLabel.text = "⏱️ Dauer"
                binding.tvDurationValue.text = "$selectedDuration Min"
            }
            MeasurementType.REPETITIONS -> {
                binding.sliderDuration.valueFrom = TrainingConfig.MIN_REPETITIONS.toFloat()
                binding.sliderDuration.valueTo = TrainingConfig.MAX_REPETITIONS.toFloat()
                binding.sliderDuration.value = selectedRepetitions.toFloat()
                binding.tvDurationLabel.text = "🔢 Wiederholungen"
                binding.tvDurationValue.text = "${selectedRepetitions}x"
            }
        }
    }

    private fun setupDropdowns() {
        // Ablenkung
        val distractionLevels = DistractionLevel.values().map { it.displayName(this) }
        val distractionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, distractionLevels)
        binding.spinnerDistraction.setAdapter(distractionAdapter)
        binding.spinnerDistraction.setText(DistractionLevel.LOW.displayName(this), false)

        // Schwierigkeit
        val difficultyLevels = DifficultyLevel.values().map { it.displayName(this) }
        val difficultyAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, difficultyLevels)
        binding.spinnerDifficulty.setAdapter(difficultyAdapter)
        binding.spinnerDifficulty.setText(DifficultyLevel.NORMAL.displayName(this), false)

        // Wetter (optional)
        val notSpecified = getString(R.string.add_session_not_specified)
        val weatherConditions = listOf(notSpecified) + WeatherCondition.values().map { it.displayName(this) }
        val weatherAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, weatherConditions)
        binding.spinnerWeather.setAdapter(weatherAdapter)
        binding.spinnerWeather.setText(notSpecified, false)

        // Ort (optional)
        val locations = listOf(notSpecified) + LocationType.values().map { it.displayName(this) }
        val locationAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, locations)
        binding.spinnerLocation.setAdapter(locationAdapter)
        binding.spinnerLocation.setText(notSpecified, false)
    }

    private fun setupListeners() {
        // Slider - aktualisiert je nach Messungstyp
        binding.sliderDuration.addOnChangeListener { _, value, _ ->
            when (selectedMeasurementType) {
                MeasurementType.TIME -> {
                    selectedDuration = value.toInt()
                    binding.tvDurationValue.text = "$selectedDuration Min"
                }
                MeasurementType.REPETITIONS -> {
                    selectedRepetitions = value.toInt()
                    binding.tvDurationValue.text = "${selectedRepetitions}x"
                }
            }
        }
    }

    private fun setupSuccessButtons() {
        // Veraltet, wird durch updateSuccessRatingGrid() ersetzt
    }

    // ✅ Wetter & GPS Funktionen bleiben gleich
    private fun setupWeatherButton() {
        // Veraltet, wird durch setupGrids() ersetzt
    }

    private fun showWeatherPermissionDialog() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            showProminentDisclosure(PendingAction.WEATHER)
            return
        }

        val colors = StartGroupColors.forGroup(this, 1) // Gruppe 2 für Training

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
            }
    }

    private fun showProminentDisclosure(action: PendingAction) {
        val colors = StartGroupColors.forGroup(this, 1)

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
                pendingAction = action
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
            }
    }

    private fun fetchWeatherData() {
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
        lifecycleScope.launch {
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

                weatherData = weather
                binding.tvWeatherDisplay.text = "☀️ $weatherData"
                binding.tvWeatherDisplay.visibility = View.VISIBLE

                Toast.makeText(this@AddSessionActivity, "✅ Wetter hinzugefügt", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
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
        // Veraltet, wird durch setupGrids() ersetzt
    }

    private fun showGpsPermissionDialog() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            showProminentDisclosure(PendingAction.GPS)
            return
        }

        val colors = StartGroupColors.forGroup(this, 1) // Gruppe 2 für Training

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
            }
    }

    private fun fetchGpsLocation() {
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

            gpsLatitude = lastKnown.latitude
            gpsLongitude = lastKnown.longitude
            gpsLocationName = ""

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
        val distractionText = binding.spinnerDistraction.text.toString()
        val difficultyText = binding.spinnerDifficulty.text.toString()

        if (distractionText.isEmpty() || difficultyText.isEmpty()) {
            Toast.makeText(this, getString(R.string.add_session_fill_required_fields), Toast.LENGTH_SHORT).show()
            return
        }

        val distraction = DistractionLevel.fromDisplayName(this, distractionText)
            ?: DistractionLevel.LOW
        val difficulty = DifficultyLevel.fromDisplayName(this, difficultyText)
            ?: DifficultyLevel.NORMAL

        val notSpecified = getString(R.string.add_session_not_specified)
        val weatherText = binding.spinnerWeather.text.toString()
        val weather = if (weatherText == notSpecified) null
        else WeatherCondition.fromDisplayName(this, weatherText)

        val locationText = binding.spinnerLocation.text.toString()
        val location = if (locationText == notSpecified) null
        else LocationType.fromDisplayName(this, locationText)

        // ✅ Session erstellen - mit measurementType!
        val session = TrainingSession(
            dogId = profile.id,
            category = category,
            measurementType = selectedMeasurementType,  // ← NEU!
            durationMinutes = if (selectedMeasurementType == MeasurementType.TIME) selectedDuration else null,
            repetitions = if (selectedMeasurementType == MeasurementType.REPETITIONS) selectedRepetitions else null,
            successRating = selectedSuccessRating,
            distractionLevel = distraction,
            difficulty = difficulty,
            notes = binding.etNotes.text.toString(),
            weatherCondition = weather,
            location = location,
            weatherData = weatherData,
            gpsLatitude = gpsLatitude,
            gpsLongitude = gpsLongitude,
            gpsLocationName = gpsLocationName
        )

        manager.saveSession(session)
        showSuccessDialog()
    }

    private fun showSuccessDialog() {
        val colors = StartGroupColors.forGroup(this, 1) // Gruppe 2 für Training

        AlertDialog.Builder(this)
            .setTitle("✅ Trainingseinheit gespeichert")
            .setMessage("Die Trainingseinheit wurde erfolgreich dokumentiert.")
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .create()
            .apply {
                show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}