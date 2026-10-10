package com.hundewildnis.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.hundewildnis.app.databinding.ActivityThunderstormWarningBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * GEWITTERWARNER
 *
 * Zwei Funktionen:
 * 1) Blitz-Donner-Distanzrechner (komplett offline): Der Nutzer tippt bei
 *    Blitz und bei Donner jeweils auf einen Button, die App misst die Zeit
 *    dazwischen und rechnet sie über die Schallgeschwindigkeit in eine
 *    Entfernung um. Der Rechenweg wird sichtbar angezeigt (Lernfunktion).
 * 2) Gewitterrisiko-Vorhersage aus Open-Meteo (CAPE-Wert) für die nächsten
 *    Stunden am aktuellen Standort – ergänzt die Live-Messung um einen
 *    Blick in die nahe Zukunft.
 */
class ThunderstormWarningActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityThunderstormWarningBinding
    private lateinit var locationManager: LocationManager

    // Zeitpunkt des Blitzes (elapsedRealtime, unabhängig von Systemzeit-Änderungen)
    private var flashElapsedMillis: Long? = null
    private val measurements = mutableListOf<LightningMeasurement>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 2101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThunderstormWarningBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.thunderstorm_toolbar_title)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.navigationIcon?.setTint(Color.WHITE)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        setupLightningCalculator()
        setupForecastSection()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // TEIL 1: Blitz-Donner-Distanzrechner
    // ════════════════════════════════════════════════════════════════

    private fun setupLightningCalculator() {
        binding.btnFlash.setOnClickListener {
            flashElapsedMillis = SystemClock.elapsedRealtime()
            binding.btnThunder.isEnabled = true
            binding.btnFlash.isEnabled = false
            binding.tvMeasureStatus.text = getString(R.string.thunderstorm_flash_captured_status)
            binding.cardCalculation.visibility = View.GONE
        }

        binding.btnThunder.setOnClickListener {
            val startTime = flashElapsedMillis
            if (startTime == null) {
                Toast.makeText(this, getString(R.string.thunderstorm_tap_flash_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val elapsedSeconds = (SystemClock.elapsedRealtime() - startTime) / 1000.0
            registerMeasurement(elapsedSeconds)

            flashElapsedMillis = null
            binding.btnThunder.isEnabled = false
            binding.btnFlash.isEnabled = true
        }

        binding.btnResetMeasurements.setOnClickListener {
            measurements.clear()
            updateTrendDisplay()
            binding.cardCalculation.visibility = View.GONE
            binding.tvMeasureStatus.text = getString(R.string.thunderstorm_ready_for_next_measurement)
        }
    }

    private fun registerMeasurement(elapsedSeconds: Double) {
        val distanceKm = LightningCalculator.calculateDistanceKm(elapsedSeconds)
        val level = LightningCalculator.proximityLevel(distanceKm)

        measurements.add(
            LightningMeasurement(
                timestampMillis = System.currentTimeMillis(),
                secondsUntilThunder = elapsedSeconds,
                distanceKm = distanceKm
            )
        )
        // Nur die letzten 10 Messungen behalten, ältere sind für den Trend irrelevant
        if (measurements.size > 10) measurements.removeAt(0)

        showCalculationSteps(elapsedSeconds, distanceKm, level)
        updateTrendDisplay()
    }

    /**
     * Zeigt den vollständigen Rechenweg an – bewusst Schritt für Schritt,
     * damit der Nutzer die Methode nachvollziehen und selbst lernen kann,
     * warum "Sekunden geteilt durch 3" eine gängige Faustregel ist.
     */
    private fun showCalculationSteps(seconds: Double, distanceKm: Double, level: StormProximityLevel) {
        binding.cardCalculation.visibility = View.VISIBLE

        binding.tvStepTime.text = getString(R.string.thunderstorm_step_measured_time, seconds)

        binding.tvStepFormula.text =
            getString(R.string.thunderstorm_step_formula_header) + "\n" +
                    getString(R.string.thunderstorm_step_formula_line) + "\n" +
                    getString(R.string.thunderstorm_step_formula_calc1, seconds, SPEED_OF_SOUND_MPS) +
                    getString(R.string.thunderstorm_step_formula_calc2, seconds * SPEED_OF_SOUND_MPS)

        binding.tvStepResult.text = getString(R.string.thunderstorm_distance_result, distanceKm)
        binding.tvStepResult.setTextColor(Color.parseColor(level.color))

        binding.tvStepRuleOfThumb.text = getString(R.string.thunderstorm_rule_of_thumb, seconds, seconds / 3.0)

        binding.tvProximityLevel.text = "${level.icon} ${level.displayName(this)}"
        binding.tvProximityLevel.setTextColor(Color.parseColor(level.color))

        showRecommendationBanner(level)

        binding.tvMeasureStatus.text = getString(R.string.thunderstorm_measure_saved_status)
    }

    /**
     * Färbt den Empfehlungs-Banner passend zur Ampelstufe ein: dezente,
     * leicht transparente Ampelfarbe als Hintergrund, damit die Dringlichkeit
     * sofort optisch erfassbar ist – ohne bei DANGER unleserlich zu werden.
     */
    private fun showRecommendationBanner(level: StormProximityLevel) {
        val levelColor = Color.parseColor(level.color)

        val background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 10f * resources.displayMetrics.density
            // ~18% Deckkraft der Ampelfarbe als Fläche, Text bleibt in Vollfarbe darüber lesbar
            setColor(ColorUtils.setAlphaComponent(levelColor, 46))
        }
        binding.recommendationContainer.background = background

        binding.tvRecommendationIcon.text = when (level) {
            StormProximityLevel.SAFE -> "🟢"
            StormProximityLevel.CAUTION -> "⚠️"
            StormProximityLevel.DANGER -> "🔴"
        }

        binding.tvRecommendation.text = LightningCalculator.recommendationFor(this, level)
        binding.tvRecommendation.setTextColor(levelColor)
    }

    private fun updateTrendDisplay() {
        if (measurements.isEmpty()) {
            binding.tvTrendResult.text = ""
            binding.tvMeasurementCount.text = getString(R.string.thunderstorm_no_measurement_yet)
            binding.tvApproachPlaceholder.visibility = View.VISIBLE
            binding.approachMetricsContainer.visibility = View.GONE
            binding.tvApproachFormula.visibility = View.GONE
            binding.approachDangerContainer.visibility = View.GONE
            return
        }

        val trend = LightningCalculator.determineTrend(measurements)
        binding.tvTrendResult.text = "${trend.icon} ${trend.displayName(this)}"
        binding.tvMeasurementCount.text = getString(R.string.thunderstorm_measurement_count, measurements.size)

        showApproachEstimate()
    }

    /**
     * Zeigt Näherungsgeschwindigkeit und – sofern das Gewitter noch mehr als
     * 1 km entfernt ist – die theoretische Ankunftszeit als große, farblich
     * hervorgehobene Kennzahlen-Kacheln an (statt Fließtext), damit die
     * wichtigsten Werte auf einen Blick erfassbar sind. Der Rechenweg bleibt
     * darunter als kleine Zeile sichtbar (Lernfunktion).
     * Unterhalb von 1 km wird statt der ETA-Kachel ein deutlicher roter
     * Warnbanner gezeigt, da dort ausschließlich "sofort Schutz suchen" gilt.
     */
    private fun showApproachEstimate() {
        val estimate = LightningCalculator.estimateApproach(measurements)
        if (estimate == null || estimate.speedKmh <= 0.0) {
            binding.tvApproachPlaceholder.visibility = View.VISIBLE
            binding.approachMetricsContainer.visibility = View.GONE
            binding.tvApproachFormula.visibility = View.GONE
            binding.approachDangerContainer.visibility = View.GONE
            return
        }

        binding.tvApproachPlaceholder.visibility = View.GONE
        binding.approachMetricsContainer.visibility = View.VISIBLE

        binding.tvSpeedValue.text = getString(R.string.thunderstorm_speed_value_kmh, estimate.speedKmh)

        val etaMinutes = estimate.etaMinutes
        val isAcuteDanger = estimate.currentDistanceKm <= LightningCalculator.ETA_MIN_DISTANCE_KM

        if (etaMinutes != null) {
            binding.tvEtaValue.text = getString(R.string.thunderstorm_eta_value_min, etaMinutes)
            // Dringlichkeits-Färbung: je kürzer die ETA, desto alarmierender die Farbe
            val etaColor = when {
                etaMinutes <= 10.0 -> "#FF8A65"
                etaMinutes <= 25.0 -> "#FF9800"
                else -> "#4CAF50"
            }
            binding.tvEtaValue.setTextColor(Color.parseColor(etaColor))
            binding.approachDangerContainer.visibility = View.GONE
        } else {
            // Keine sinnvolle ETA (Gewitter <1 km oder Geschwindigkeit unklar)
            binding.tvEtaValue.text = "—"
            binding.tvEtaValue.setTextColor(Color.parseColor("#999999"))
            binding.approachDangerContainer.visibility = if (isAcuteDanger) View.VISIBLE else View.GONE
        }

        binding.tvApproachFormula.visibility = View.VISIBLE
        binding.tvApproachFormula.text = if (etaMinutes != null) {
            val remainingKm = estimate.currentDistanceKm - LightningCalculator.ETA_MIN_DISTANCE_KM
            getString(
                R.string.thunderstorm_approach_formula_with_eta,
                estimate.speedKmh, estimate.currentDistanceKm, estimate.speedKmh, remainingKm / estimate.speedKmh * 60.0
            )
        } else {
            getString(R.string.thunderstorm_approach_formula_no_eta, estimate.speedKmh)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // TEIL 2: Gewitterrisiko-Vorhersage (Open-Meteo)
    // ════════════════════════════════════════════════════════════════

    private fun setupForecastSection() {
        binding.btnLoadForecast.setOnClickListener {
            checkLocationPermissionAndLoadForecast()
        }
    }

    private fun checkLocationPermissionAndLoadForecast() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.thunderstorm_location_dialog_title))
                .setMessage(getString(R.string.thunderstorm_location_dialog_message))
                .setPositiveButton(getString(R.string.thunderstorm_activate)) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
                .setNegativeButton(getString(R.string.thunderstorm_cancel_uppercase), null)
                .create()
                .apply {
                    show()
                }
        } else {
            loadForecast()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadForecast()
            } else {
                Toast.makeText(this, getString(R.string.thunderstorm_location_permission_denied), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadForecast() {
        val location = getBestKnownLocation()
        if (location == null) {
            Toast.makeText(this, getString(R.string.thunderstorm_no_location_available), Toast.LENGTH_LONG).show()
            return
        }

        binding.forecastProgressBar.visibility = View.VISIBLE
        binding.forecastResultContainer.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    fetchThunderstormForecast(location.latitude, location.longitude)
                }
                displayForecast(json)
            } catch (e: Exception) {
                Toast.makeText(this@ThunderstormWarningActivity, getString(R.string.start_error_generic, e.message ?: ""), Toast.LENGTH_LONG).show()
            } finally {
                binding.forecastProgressBar.visibility = View.GONE
            }
        }
    }

    private fun getBestKnownLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        var best: Location? = null
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            best = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        if (best == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            best = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        return best
    }

    /**
     * Open-Meteo liefert den CAPE-Wert (convective available potential energy)
     * stündlich mit – ein etablierter meteorologischer Indikator für
     * Gewitterpotenzial. Gleicher HttpURLConnection-Ansatz wie in
     * WeatherActivity/AltimeterActivity, kein zusätzliches Networking-Pattern.
     * wind_direction_10m wird zusätzlich abgerufen, um die wahrscheinliche
     * Zugrichtung eines Gewitters anzuzeigen (Richtung, aus der der Wind weht).
     */
    private fun fetchThunderstormForecast(latitude: Double, longitude: Double): JSONObject {
        val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$latitude&longitude=$longitude" +
                "&hourly=cape,precipitation_probability,weather_code,wind_direction_10m" +
                "&forecast_hours=12" +
                "&timezone=auto"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "HundeWildnisApp/9.7")
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(response)
    }

    /**
     * Wandelt eine Windrichtung in Grad (meteorologisch: Richtung, aus der der
     * Wind weht) in eine deutsche Himmelsrichtung mit Pfeil um. Ein Gewitter
     * wird von der vorherrschenden Strömung herangetragen, daher gibt die
     * Windrichtung direkt an, aus welcher Himmelsrichtung ein Gewitter
     * wahrscheinlich naht (z. B. 270° = Westwind = Gewitter kommt aus Westen).
     * Der Pfeil zeigt exakt in diese Herkunftsrichtung.
     */
    private fun windDirectionLabel(degrees: Int): Pair<String, String> {
        // Pfeil zeigt jeweils in die Himmelsrichtung, aus der der Wind kommt
        // (Bildschirm-Ausrichtung: oben=Norden, rechts=Osten)
        val directions = listOf(
            getString(R.string.wind_direction_north) to "⬆️", getString(R.string.wind_direction_northeast) to "↗️",
            getString(R.string.wind_direction_east) to "➡️", getString(R.string.wind_direction_southeast) to "↘️",
            getString(R.string.wind_direction_south) to "⬇️", getString(R.string.wind_direction_southwest) to "↙️",
            getString(R.string.wind_direction_west) to "⬅️", getString(R.string.wind_direction_northwest) to "↖️"
        )
        val index = (((degrees % 360) + 22) / 45) % 8
        return directions[index]
    }

    private fun displayForecast(data: JSONObject) {
        val hourly = data.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val capeValues = hourly.getJSONArray("cape")
        val precipProbabilities = hourly.getJSONArray("precipitation_probability")
        val weatherCodes = hourly.getJSONArray("weather_code")
        val windDirections = hourly.optJSONArray("wind_direction_10m")

        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        var worstLevel = ThunderstormForecastLevel.LOW
        var worstHourLabel: String? = null
        var worstHourDirectionLabel: String? = null
        var worstHourDirectionArrow: String? = null
        val sb = StringBuilder()
        val unknownDirection = getString(R.string.wind_direction_unknown)

        for (i in 0 until times.length()) {
            val timeLabel = try {
                outputFormat.format(inputFormat.parse(times.getString(i))!!)
            } catch (e: Exception) {
                times.getString(i)
            }
            val cape = capeValues.optDouble(i, 0.0)
            val precipProb = precipProbabilities.optInt(i, 0)
            val code = weatherCodes.optInt(i, 0)
            val level = ThunderstormForecastEngine.classify(cape, precipProb, code)

            val windDegrees = windDirections?.optInt(i, -1) ?: -1
            val (directionLabel, directionArrow) = if (windDegrees in 0..360) {
                windDirectionLabel(windDegrees)
            } else {
                unknownDirection to ""
            }

            if (level.ordinal > worstLevel.ordinal) {
                worstLevel = level
                worstHourLabel = timeLabel
                worstHourDirectionLabel = directionLabel
                worstHourDirectionArrow = directionArrow
            }

            val directionSuffix = if (directionLabel != unknownDirection) getString(R.string.thunderstorm_forecast_direction_suffix, directionArrow, directionLabel) else ""
            sb.append(getString(R.string.thunderstorm_forecast_line,
                timeLabel, level.icon, level.displayName(this), cape, precipProb, weatherCodeLabel(code), directionSuffix)).append("\n")
        }

        binding.forecastResultContainer.visibility = View.VISIBLE
        binding.tvForecastSummary.text = if (worstHourLabel != null && worstLevel != ThunderstormForecastLevel.LOW) {
            val directionText = if (worstHourDirectionLabel != null && worstHourDirectionLabel != unknownDirection) {
                getString(R.string.thunderstorm_forecast_from_direction, worstHourDirectionArrow, worstHourDirectionLabel)
            } else ""
            getString(R.string.thunderstorm_forecast_summary_risk, worstLevel.icon, worstLevel.displayName(this), worstHourLabel, directionText)
        } else {
            getString(R.string.thunderstorm_forecast_summary_no_risk)
        }

        val levelColor = Color.parseColor(worstLevel.color)
        binding.tvForecastSummary.setTextColor(levelColor)
        binding.tvForecastSummary.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(ColorUtils.setAlphaComponent(levelColor, 30))
        }

        binding.tvForecastDetails.text = sb.toString().trim()
    }

    /**
     * Kurze deutsche Klarschrift für WMO-Wettercodes, damit in der Detailliste
     * nachvollziehbar ist, welcher Rohwert eine Einstufung ausgelöst hat –
     * hilfreich zur Fehlersuche bei unerwarteten Einstufungen.
     */
    private fun weatherCodeLabel(code: Int): String {
        val label = when (code) {
            0 -> getString(R.string.weather_code_clear)
            1, 2, 3 -> getString(R.string.weather_code_cloudy)
            45, 48 -> getString(R.string.weather_code_fog)
            51, 53, 55 -> getString(R.string.weather_code_drizzle)
            56, 57 -> getString(R.string.weather_code_freezing_drizzle)
            61 -> getString(R.string.weather_code_light_rain)
            63 -> getString(R.string.weather_code_moderate_rain)
            65 -> getString(R.string.weather_code_heavy_rain)
            66, 67 -> getString(R.string.weather_code_freezing_rain)
            71, 73, 75 -> getString(R.string.weather_code_snow)
            77 -> getString(R.string.weather_code_snow_grains)
            80 -> getString(R.string.weather_code_light_shower)
            81 -> getString(R.string.weather_code_moderate_shower)
            82 -> getString(R.string.weather_code_heavy_shower)
            85, 86 -> getString(R.string.weather_code_snow_shower)
            95 -> getString(R.string.weather_code_thunderstorm)
            96, 99 -> getString(R.string.weather_code_thunderstorm_hail)
            else -> null
        }
        return if (label != null) getString(R.string.weather_code_prefix, code, label) else getString(R.string.thunderstorm_weather_code_fallback, code)
    }
}