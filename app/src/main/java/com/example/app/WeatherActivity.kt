package com.hundewildnis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hundewildnis.app.SecureBaseActivity
import com.hundewildnis.app.databinding.ActivityWeatherBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import java.util.concurrent.TimeUnit
import kotlin.math.max

class WeatherActivity : SecureBaseActivity(), LocationListener {

    private lateinit var binding: ActivityWeatherBinding
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private var isRequestingUpdates = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1003
        private const val MIN_TIME_BETWEEN_UPDATES = 5000L
        private const val MIN_DISTANCE_CHANGE = 100f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Wetterinformationen"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        checkLocationPermission()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun checkLocationPermission() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("📍 Standortdienste aktivieren")
                .setMessage("HUNDEWILDNIS - App benötigt Zugriff auf Deinen Standort für folgende Zwecke:\n\n" +
                        "• NOTFALLMELDUNG:\nUm Deine Sicherheit und die Deines Hundes zu erhöhen, kann HundeWildnis Deinen aktuellen GPS-Standort erfassen und in die Notfallmeldung (PDF) einfügen.\n" +
                        "Dies ermöglicht es Tierärzten oder Rettungskräften Euch im Ernstfall schneller zu finden.\n" +
                        "• Die Erfassung erfolgt NUR, wenn Du diesen Schalter aktivierst.\n" +
                        "• Die Daten werden NUR lokal in das PDF-Dokument geschrieben und dem Empfänger der Notfallmeldung übermittelt.\n" +
                        "• Es erfolgt KEINE Übermittlung an HundeWildnis oder Dritte.\n\n\n" +
                        "• TEMPO-MESSUNG:\nUm Deine Geh-Geschwindigkeit im Training präzise zu ermitteln und Dir zu Deiner Auswertung zur Verfügung zu stellen.\n\n" +
                        "• NAVIGATION:\nDamit Du Standort auf der Karte, Routenberechnung zum Ziel und Anzeige von relevanten Orten in der Nähe aufrufen und nutzen kannst.\n\n" +
                        "• STANDORT WETTER:\nDamit Du Wetterdaten und Wettervorhersage an Deinem aktuellen Standort aufrufen und nutzen kannst.\n\n" +
                        "• TAGESNOTIZEN:\nDu kannst Deine Tagesnotizen optional mit Wetterdaten und optional mit den GPS-Daten Deines Standortes ergänzen, um später nachvollziehen zu können, unter welchen Bedingungen Du Deine Beobachtungen zum Training gemacht hast.\n\n" +
                        "WICHTIG:\nDamit die Messung auch bei ausgeschaltetem Bildschirm, wenn die App in den Hintergrund geschoben ist oder in der Tasche ist, stabil funktioniert weil Du während des Trainings und bei Aktivität im Gelände die Hände frei haben musst, nutzt die App einen dauerhaften Dienst (Foreground Service). Dieser wird durch die Benachrichtigung in Deiner Statusleiste angezeigt.\n\n" +
                        "Ohne Aktivierung der Standortdienste, als technische Voraussetzung, kannst Du die Funktionen nicht wie beschrieben nutzen.\n\nDATENSCHUTZ:\nDeine Daten werden nur lokal verarbeitet, ausschliesslich auf Deinem Gerät gespeichert und nur von Dir allein verwaltet und nur auf deinen Wunsch zum Teilenfunktion freigegeben.\n\nZur Nutzung von Navigation und Standortwetter wird der aktuelle Standort anonymisiert an freie Server mitgeteilt, um die gewünschten Daten abzurufen und Dir zur Verfügung zu stellen.\n\nDu kannst die Berechtigungen für Standort jederzeit überprüfen in den Einstellungen:\n\nDein Gerät - Einstellungen - Apps - HundeWildnis - Berechtigungen - Standort verwalten - Zugriff nur während der Nutzung der App zulassen.\n\nDort kann die Berechtigung zum Standort jederzeit dauerhaft geändert, aktiviert oder deaktiviert werden")
                .setPositiveButton("AKTIVIEREN") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
                .setNegativeButton("ABLEHNEN", null)
                .create()
                .apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)
                }
        } else {
            getLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocation()
                } else {
                    binding.descriptionText.text = "Standortberechtigung benötigt"
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "Standortberechtigung wurde verweigert",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.descriptionText.text = "Standort wird ermittelt..."

        var bestLocation: Location? = null

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }

        if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }

        if (bestLocation != null && isLocationRecent(bestLocation)) {
            loadWeather(bestLocation)
        } else {
            requestLocationUpdates()
        }
    }

    private fun isLocationRecent(location: Location): Boolean {
        val maxAge = 300000L // 5 Minuten
        return System.currentTimeMillis() - location.time < maxAge
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        isRequestingUpdates = true

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE,
                this
            )
        }

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_TIME_BETWEEN_UPDATES,
                MIN_DISTANCE_CHANGE,
                this
            )
        }
    }

    private fun stopLocationUpdates() {
        if (isRequestingUpdates) {
            locationManager.removeUpdates(this)
            isRequestingUpdates = false
        }
    }

    override fun onLocationChanged(location: Location) {
        loadWeather(location)
        stopLocationUpdates()
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun loadWeather(location: Location) {
        currentLocation = location

        binding.locationText.text = String.format(
            Locale.GERMAN,
            "📍 %.4f°, %.4f°",
            location.latitude,
            location.longitude
        )

        lifecycleScope.launch {
            try {
                val weatherData = withContext(Dispatchers.IO) {
                    fetchWeatherData(location.latitude, location.longitude)
                }

                displayWeatherData(weatherData)
                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.descriptionText.text = "Fehler beim Laden der Wetterdaten"
                Toast.makeText(
                    this@WeatherActivity,
                    "Fehler: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun fetchWeatherData(latitude: Double, longitude: Double): JSONObject {
        // ERWEITERT: alle neuen Daten-Felder
        val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature," +
                "precipitation,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m," +
                "wind_gusts_10m,uv_index" + // NEU: Windböen
                "&hourly=temperature_2m,precipitation,precipitation_probability,weather_code," +
                "apparent_temperature" + // NEU: Stündliche Daten
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset," + // NEU: Min/Max, Sonne
                "weather_code,precipitation_probability_max" + // NEU: Tageswarnungen
                "&past_days=1" + // NEU: Gestern laden für Trend-Vergleich
                "&forecast_days=2" +
                "&timezone=auto"

        val response = URL(url).readText()
        return JSONObject(response)
    }

    private fun displayWeatherData(data: JSONObject) {
        val current = data.getJSONObject("current")

        val temperature = current.getDouble("temperature_2m")
        val feelsLike = current.getDouble("apparent_temperature")
        val humidity = current.getInt("relative_humidity_2m")
        val precipitation = current.getDouble("precipitation")
        val weatherCode = current.getInt("weather_code")
        val pressure = current.getDouble("surface_pressure")
        val windSpeed = current.getDouble("wind_speed_10m")
        val windDirection = current.getDouble("wind_direction_10m")
        val windGusts = current.getDouble("wind_gusts_10m") // NEU
        val uvIndex = current.getDouble("uv_index")

        // Temperatur
        binding.temperatureText.text = String.format(Locale.GERMAN, "%.1f°C", temperature)

        // NEU: Gefühlte Temperatur mit Windchill-Berechnung
        val windchill = calculateWindchill(temperature, windSpeed)
        binding.feelsLikeText.text = String.format(
            Locale.GERMAN,
            "%.1f°C (Windgeschützt: %.1f°C)",
            feelsLike,
            windchill
        )

        // Wetterbeschreibung und Icon
        val (description, icon) = getWeatherDescription(weatherCode)
        binding.descriptionText.text = description
        binding.weatherIcon.text = icon

        // NEU: Wind mit Beaufort-Skala, Richtung UND Böen (IMMER angezeigt)
        val windDirectionText = getWindDirection(windDirection)
        val beaufort = getBeaufortScale(windSpeed)

        // ✅ NEU: Windstärke-Text (Attribute wie "Leichte Brise")
        binding.windStrengthText.text = getBeaufortDescription(beaufort)

        binding.windText.text = String.format(
            Locale.GERMAN,
            "%.1f km/h %s (Bft %d)\nBöen: %.1f km/h",
            windSpeed,
            windDirectionText,
            beaufort,
            windGusts
        )

        // Weitere Daten
        binding.humidityText.text = "$humidity%"
        binding.precipitationText.text = String.format(Locale.GERMAN, "%.1f mm", precipitation)
        binding.pressureText.text = String.format(Locale.GERMAN, "%.0f hPa", pressure)
        binding.visibilityText.text = "> 10 km"
        binding.uvIndexText.text = String.format(Locale.GERMAN, "%.1f", uvIndex)

        // NEU: Sonnenauf-/untergang
        displaySunriseSunset(data)

        // NEU: Stündliche 24h-Vorhersage
        display24HourForecast(data)

        // NEU: Tages-Übersicht (Min/Max, Warnungen)
        displayDailyOverview(data)

        // Regenradar mit Nowcast
        setupRainRadar()

        // ✅ NEU: Alle Features aufrufen
        displayPawWeather(data, windSpeed, windGusts)
        displayTemperatureChart(data)
        displayPrecipitationChart(data)
        displayDaylightInfo(data)
    }

    // NEU: Windchill berechnen (gefühlte Temperatur bei Wind)
    private fun calculateWindchill(temp: Double, windKmh: Double): Double {
        // Nur bei Temp < 10°C und Wind > 4.8 km/h relevant
        if (temp >= 10.0 || windKmh < 4.8) {
            return temp
        }

        // Windchill-Formel (metrisch)
        val windchill = 13.12 + 0.6215 * temp - 11.37 * windKmh.pow(0.16) +
                0.3965 * temp * windKmh.pow(0.16)

        return windchill
    }

    // NEU: Beaufort-Skala
    private fun getBeaufortScale(windKmh: Double): Int {
        return when {
            windKmh < 1 -> 0      // Windstille
            windKmh < 6 -> 1      // Leiser Zug
            windKmh < 12 -> 2     // Leichte Brise
            windKmh < 20 -> 3     // Schwache Brise
            windKmh < 29 -> 4     // Mäßige Brise
            windKmh < 39 -> 5     // Frische Brise
            windKmh < 50 -> 6     // Starker Wind
            windKmh < 62 -> 7     // Steifer Wind
            windKmh < 75 -> 8     // Stürmischer Wind
            windKmh < 89 -> 9     // Sturm
            windKmh < 103 -> 10   // Schwerer Sturm
            windKmh < 118 -> 11   // Orkanartiger Sturm
            else -> 12            // Orkan
        }
    }

    // ✅ NEU: Beaufort Beschreibungstext
    private fun getBeaufortDescription(beaufort: Int): String {
        return when (beaufort) {
            0 -> "Windstille"
            1 -> "Leiser Zug"
            2 -> "Leichte Brise"
            3 -> "Schwache Brise"
            4 -> "Mäßige Brise"
            5 -> "Frische Brise"
            6 -> "Starker Wind"
            7 -> "Steifer Wind"
            8 -> "Stürmischer Wind"
            9 -> "Sturm"
            10 -> "Schwerer Sturm"
            11 -> "Orkanartiger Sturm"
            12 -> "Orkan"
            else -> "Unbekannt"
        }
    }

    private fun getWindDirection(degrees: Double): String {
        return when {
            degrees >= 337.5 || degrees < 22.5 -> "↓ Nord"
            degrees >= 22.5 && degrees < 67.5 -> "↙ NordOst"
            degrees >= 67.5 && degrees < 112.5 -> "← Ost"
            degrees >= 112.5 && degrees < 157.5 -> "↖ SüdOst"
            degrees >= 157.5 && degrees < 202.5 -> "↑ Süd"
            degrees >= 202.5 && degrees < 247.5 -> "↗ SüdWest"
            degrees >= 247.5 && degrees < 292.5 -> "→ West"
            degrees >= 292.5 && degrees < 337.5 -> "↘ NordWest"
            else -> ""
        }
    }

    // NEU: Sonnenauf-/untergang
    private fun displaySunriseSunset(data: JSONObject) {
        try {
            val daily = data.getJSONObject("daily")
            val sunriseArray = daily.getJSONArray("sunrise")
            val sunsetArray = daily.getJSONArray("sunset")

            if (sunriseArray.length() > 1 && sunsetArray.length() > 1) {
                // Index 1 ist HEUTE (Index 0 ist Gestern wegen past_days=1)
                val sunriseStr = sunriseArray.getString(1)
                val sunsetStr = sunsetArray.getString(1)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

                val sunrise = dateFormat.parse(sunriseStr)
                val sunset = dateFormat.parse(sunsetStr)

                if (sunrise != null && sunset != null) {
                    binding.sunriseText.text = "🌅 ${timeFormat.format(sunrise)}"
                    binding.sunsetText.text = "🌇 ${timeFormat.format(sunset)}"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler beim Laden Sonnenzeiten", e)
        }
    }

    // NEU: Tages-Übersicht mit Min/Max und Warnungen
    private fun displayDailyOverview(data: JSONObject) {
        try {
            val daily = data.getJSONObject("daily")
            val minTemps = daily.getJSONArray("temperature_2m_min")
            val maxTemps = daily.getJSONArray("temperature_2m_max")
            val weatherCodes = daily.getJSONArray("weather_code")
            val precipProbs = daily.getJSONArray("precipitation_probability_max")

            if (minTemps.length() > 1) {
                // Index 1 ist HEUTE
                val minTemp = minTemps.getDouble(1)
                val maxTemp = maxTemps.getDouble(1)
                val code = weatherCodes.getInt(1)
                val precipProb = precipProbs.getInt(1)

                binding.dailyMinMaxText.text = String.format(
                    Locale.GERMAN,
                    "Min: %.1f°C • Max: %.1f°C",
                    minTemp,
                    maxTemp
                )

                // NEU: Wetterwarnung-Badge (IMMER sichtbar)
                val warningBadge = getWeatherWarningBadge(code, precipProb)
                binding.warningBadgeText.text = warningBadge

                // Badge ist IMMER sichtbar (auch bei gutem Wetter)
                binding.warningBadgeText.visibility = View.VISIBLE

                // Farbe je nach Warnstufe anpassen
                when {
                    warningBadge.contains("⚠️") || warningBadge.contains("❄️") ||
                            warningBadge.contains("🌧️") -> {
                        // Warnung: Rotes Badge
                        binding.warningBadgeText.setBackgroundResource(R.drawable.warning_badge_bg)
                    }
                    warningBadge.contains("☔") || warningBadge.contains("🌦️") ||
                            warningBadge.contains("🌂") -> {
                        // Info: Gelbes Badge
                        binding.warningBadgeText.setBackgroundResource(R.drawable.info_badge_bg)
                    }
                    else -> {
                        // Alles gut: Grünes Badge
                        binding.warningBadgeText.setBackgroundResource(R.drawable.good_badge_bg)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei Tages-Übersicht", e)
        }
    }

    // NEU: Wetterwarnung-Badges (IMMER sichtbar)
    private fun getWeatherWarningBadge(code: Int, precipProb: Int): String {
        return when {
            code >= 95 -> "⚠️ Gewitter"
            code >= 85 -> "❄️ Schneeschauer" // 85, 86 sind Schneeschauer
            code >= 80 -> "🌦️ Schauer"       // 80-82 sind Regenschauer
            code >= 71 -> "❄️ Schneefall"    // 71-77 sind Schneefall
            code >= 61 && precipProb >= 70 -> "🌧️ Starker Regen"
            precipProb >= 80 -> "☔ Hohe Regenwahrscheinlichkeit"
            precipProb >= 50 -> "🌂 Möglicher Regen (${precipProb}%)"
            code >= 45 -> "🌫️ Nebel"
            code >= 3 -> "☁️ Bewölkt"
            else -> "✅ Keine Wetterwarnung"
        }
    }


    // NEU: Stündliche 24h-Vorhersage (24 Einträge statt 8)
    private fun display24HourForecast(data: JSONObject) {
        val hourly = data.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temperatures = hourly.getJSONArray("temperature_2m")
        val weatherCodes = hourly.getJSONArray("weather_code")
        val precipProb = hourly.getJSONArray("precipitation_probability")
        val feelsLike = hourly.getJSONArray("apparent_temperature")

        val forecastItems = mutableListOf<ForecastItem>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

        val now = System.currentTimeMillis()
        val oneHour = 3600000L

        // NEU: 24 stündliche Einträge (stündlich ab JETZT)
        var count = 0
        for (i in 0 until times.length()) {
            if (count >= 24) break // Genau 24 Stunden

            try {
                val timeStr = times.getString(i)
                val date = dateFormat.parse(timeStr)

                // Zeige Daten ab der aktuellen Stunde
                if (date != null && date.time >= now - oneHour) {
                    val displayTime = timeFormat.format(date)
                    val temp = temperatures.getDouble(i)
                    val code = weatherCodes.getInt(i)
                    val precip = if (i < precipProb.length()) precipProb.getDouble(i) else 0.0
                    val feels = if (i < feelsLike.length()) feelsLike.getDouble(i) else temp

                    forecastItems.add(ForecastItem(
                        time = displayTime,
                        temperature = temp,
                        weatherCode = code,
                        precipitation = precip,
                        feelsLike = feels // NEU
                    ))
                    count++
                }
            } catch (e: Exception) {
                android.util.Log.e("WeatherActivity", "Fehler bei Vorhersage", e)
            }
        }

        binding.forecastRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.forecastRecyclerView.adapter = ForecastAdapter(forecastItems) { item ->
            // NEU: Detailansicht bei Klick
            showForecastDetail(item)
        }
    }

    // NEU: Detailansicht bei Klick auf Vorhersage-Item
    private fun showForecastDetail(item: ForecastItem) {
        val (description, icon) = getWeatherDescription(item.weatherCode)

        val message = """
            Uhrzeit: ${item.time}
            
            $icon $description
            
            Temperatur: ${String.format(Locale.GERMAN, "%.1f°C", item.temperature)}
            Gefühlt: ${String.format(Locale.GERMAN, "%.1f°C", item.feelsLike)}
            Regen: ${item.precipitation.toInt()}%
        """.trimIndent()

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Vorhersage Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create().apply {
                show()
                getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(color)
            }
    }

    // ════════════════════════════════════════════════════════════════
    // REGENRADAR: 2 Stunden Radar-Historie (RainViewer API)
    // ════════════════════════════════════════════════════════════════

    private fun setupRainRadar() {
        currentLocation?.let { location ->
            val lat = location.latitude
            val lon = location.longitude

            binding.radarWebView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            binding.radarWebView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    if (url.contains("leafletjs.com") ||
                        url.contains("openstreetmap.org") ||
                        url.contains("rainviewer.com")) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    }
                    return false
                }
            }

            val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    * { margin: 0; padding: 0; }
                    html, body { height: 100%; background: #1a1a1a; }
                    #map { width: 100%; height: calc(100% - 50px); }
                    #controls {
                        position: absolute;
                        bottom: 0;
                        left: 0;
                        right: 0;
                        height: 50px;
                        background: rgba(45, 45, 45, 0.95);
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        gap: 10px;
                        z-index: 1000;
                        padding: 0 10px;
                    }
                    .control-btn {
                        background: #8fad6f;
                        color: white;
                        border: none;
                        border-radius: 5px;
                        padding: 8px 15px;
                        font-size: 14px;
                        font-weight: bold;
                        cursor: pointer;
                    }
                    .control-btn:active {
                        background: #7a9760;
                    }
                    #timestamp {
                        color: #999;
                        font-size: 12px;
                        flex: 1;
                        text-align: center;
                    }
                    .leaflet-control-attribution a {
                        color: #8fad6f !important;
                        pointer-events: auto !important;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <div id="controls">
                    <button class="control-btn" id="playBtn" onclick="togglePlay()">▶ Play</button>
                    <button class="control-btn" id="stopBtn" onclick="stopAnimation()">⏹ Stop</button>
                    <div id="timestamp">Lade Radar...</div>
                </div>
                <script>
                    let map, radarLayers = [], timestamps = [], currentFrame = 0;
                    let isPlaying = false, animationInterval = null;
                    let marker = null;
                    
                    map = L.map('map', {
                        center: [$lat, $lon],
                        zoom: 6,
                        zoomControl: true
                    });
                    
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
                        maxZoom: 19
                    }).addTo(map);
                    
                    marker = L.marker([$lat, $lon]).addTo(map);
                    marker.bindPopup('Dein Standort').openPopup();
                    
                    fetch('https://api.rainviewer.com/public/weather-maps.json')
                        .then(response => response.json())
                        .then(apiData => {
                            if (apiData.radar && apiData.radar.past) {
                                timestamps = apiData.radar.past;
                                
                                timestamps.forEach((item) => {
                                    const radarUrl = 'https://tilecache.rainviewer.com' + 
                                        item.path + '/256/{z}/{x}/{y}/2/1_1.png';
                                    
                                    const layer = L.tileLayer(radarUrl, {
                                        tileSize: 256,
                                        opacity: 0.9,
                                        zIndex: 10,
                                        attribution: '© <a href="https://www.rainviewer.com/">RainViewer</a>'
                                    });
                                    
                                    radarLayers.push(layer);
                                });
                                
                                if (radarLayers.length > 0) {
                                    currentFrame = radarLayers.length - 1;
                                    radarLayers[currentFrame].addTo(map);
                                    updateTimestamp();
                                }
                            } else {
                                document.getElementById('timestamp').textContent = 'Radar nicht verfügbar';
                            }
                        })
                        .catch(err => {
                            document.getElementById('timestamp').textContent = 'Radar nicht verfügbar';
                        });
                    
                    function togglePlay() {
                        if (isPlaying) {
                            pauseAnimation();
                        } else {
                            startAnimation();
                        }
                    }
                    
                    function startAnimation() {
                        if (radarLayers.length === 0) return;
                        
                        isPlaying = true;
                        document.getElementById('playBtn').innerHTML = '⏸ Pause';
                        
                        animationInterval = setInterval(() => {
                            radarLayers[currentFrame].remove();
                            currentFrame = (currentFrame + 1) % radarLayers.length;
                            radarLayers[currentFrame].addTo(map);
                            updateTimestamp();
                        }, 500);
                    }
                    
                    function pauseAnimation() {
                        isPlaying = false;
                        document.getElementById('playBtn').innerHTML = '▶ Play';
                        if (animationInterval) {
                            clearInterval(animationInterval);
                            animationInterval = null;
                        }
                    }
                    
                    function stopAnimation() {
                        pauseAnimation();
                        
                        if (radarLayers.length > 0) {
                            radarLayers[currentFrame].remove();
                            currentFrame = radarLayers.length - 1;
                            radarLayers[currentFrame].addTo(map);
                            updateTimestamp();
                        }
                    }
                    
                    function updateTimestamp() {
                        if (timestamps.length > 0 && timestamps[currentFrame]) {
                            const date = new Date(timestamps[currentFrame].time * 1000);
                            const timeStr = date.toLocaleTimeString('de-DE', { 
                                hour: '2-digit', 
                                minute: '2-digit' 
                            });
                            
                            document.getElementById('timestamp').textContent = 
                                '📡 Radar: ' + timeStr + ' Uhr';
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

            binding.radarWebView.loadDataWithBaseURL(
                "https://hundewildnis.com",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    private fun getWeatherDescription(code: Int): Pair<String, String> {
        // DEBUG: Log den Weather Code
        android.util.Log.d("WeatherActivity", "🌤️ Weather Code: $code")

        return when (code) {
            // Klar
            0 -> "Klar" to "☀️"

            // Bewölkt
            1 -> "Leicht bewölkt" to "🌤️"
            2 -> "Teilweise bewölkt" to "⛅"
            3 -> "Bewölkt" to "☁️"

            // Nebel
            45 -> "Nebel" to "🌫️"
            48 -> "Gefrierender Nebel" to "🌫️"

            // Nieselregen
            51 -> "Leichter Nieselregen" to "🌦️"
            53 -> "Mäßiger Nieselregen" to "🌦️"
            55 -> "Starker Nieselregen" to "🌦️"
            56 -> "Gefrierender leichter Nieselregen" to "🌧️"
            57 -> "Gefrierender starker Nieselregen" to "🌧️"

            // Regen
            61 -> "Leichter Regen" to "🌧️"
            63 -> "Mäßiger Regen" to "🌧️"
            65 -> "Starker Regen" to "🌧️"
            66 -> "Gefrierender leichter Regen" to "🌧️"
            67 -> "Gefrierender starker Regen" to "🌧️"

            // Schneefall
            71 -> "Leichter Schneefall" to "🌨️"
            73 -> "Mäßiger Schneefall" to "🌨️"
            75 -> "Starker Schneefall" to "🌨️"
            77 -> "Schneegriesel" to "🌨️"

            // Schauer
            80 -> "Leichte Regenschauer" to "🌦️"
            81 -> "Mäßige Regenschauer" to "🌦️"
            82 -> "Starke Regenschauer" to "🌦️"
            85 -> "Leichte Schneeschauer" to "🌨️"
            86 -> "Starke Schneeschauer" to "🌨️"

            // Gewitter
            95 -> "Gewitter" to "⛈️"
            96 -> "Gewitter mit leichtem Hagel" to "⛈️"
            99 -> "Gewitter mit starkem Hagel" to "⛈️"

            // Fallback: Zeige Code statt "Unbekannt"
            else -> {
                android.util.Log.w("WeatherActivity", "⚠️ Unbekannter Weather Code: $code")
                "Wetter-Code $code" to "🌡️"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ════════════════════════════════════════════════════════════════
// FEATURE 1: TRAININGSWETTER-INDEX
// ════════════════════════════════════════════════════════════════

    private fun displayPawWeather(data: JSONObject, windSpeed: Double, windGusts: Double) {
        val current = data.getJSONObject("current")

        val temperature = current.getDouble("temperature_2m")
        val feelsLike = current.getDouble("apparent_temperature")
        val weatherCode = current.getInt("weather_code")
        val uvIndex = current.getDouble("uv_index")
        val precipitation = current.getDouble("precipitation")

        // Pfotenwetter-Index berechnen
        var pawResult = calculatePawWeatherIndex(
            temperature, feelsLike, weatherCode, uvIndex, precipitation
        )

        // Wind-Warnung hinzufügen
        pawResult = addWindWarning(pawResult, windSpeed, windGusts)

        // Anzeigen
        binding.pawWeatherIndexText.text = pawResult.warnings.joinToString("\n\n")
        binding.pawWeatherIndexText.setTextColor(pawResult.severity.getColor())

        // Pflegetipps
        val groomingTips = getGroomingTips(weatherCode, precipitation)
        if (groomingTips.isNotEmpty()) {
            binding.groomingTipsText.text = "\n" + groomingTips.joinToString("\n")
            binding.groomingTipsText.visibility = View.VISIBLE
        } else {
            binding.groomingTipsText.visibility = View.GONE
        }

        // Gassi-Zeiten Empfehlung
        if (temperature >= 25 || temperature <= 5) {
            try {
                val hourly = data.getJSONObject("hourly")
                val temps = hourly.getJSONArray("temperature_2m")
                val times = hourly.getJSONArray("time")

                // ✅ KORREKTUR: Nächste 24h ab jetzt finden
                val now = System.currentTimeMillis()
                val oneHour = 3600000L
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
                
                var startIndex = 0
                for (i in 0 until times.length()) {
                    val date = dateFormat.parse(times.getString(i))
                    if (date != null && date.time >= now - oneHour) {
                        startIndex = i
                        break
                    }
                }
                
                val endIndex = (startIndex + 24).coerceAtMost(temps.length())

                val hourlyTemps = (startIndex until endIndex).map {
                    temps.getDouble(it)
                }
                val hourlyTimes = (startIndex until endIndex).map {
                    val timeStr = times.getString(it)
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
                        .parse(timeStr)?.let { date ->
                            SimpleDateFormat("HH:mm", Locale.GERMAN).format(date)
                        } ?: ""
                }

                val recommendations = recommendWalkingTimes(temperature, hourlyTemps, hourlyTimes)
                binding.walkingTimesText.text = "\n" + recommendations.joinToString("\n")
                binding.walkingTimesText.visibility = View.VISIBLE
            } catch (e: Exception) {
                android.util.Log.e("WeatherActivity", "Fehler bei geeigneten Trainings-Zeiten", e)
                binding.walkingTimesText.visibility = View.GONE
            }
        } else {
            binding.walkingTimesText.visibility = View.GONE
        }
    }

    private fun calculatePawWeatherIndex(
        temperature: Double,
        feelsLike: Double,
        weatherCode: Int,
        uvIndex: Double,
        precipitation: Double
    ): PawWeatherResult {
        val warnings = mutableListOf<String>()
        var severity = PawWeatherSeverity.GOOD

        // 1. HEISSER ASPHALT
        when {
            temperature >= 35 -> {
                warnings.add("🔥 EXTREM: Asphalt und Strassenpflaster bis zu 65°C! Gefahr ernster gesundheitlicher Schäden! Hitzeschockrisiko. Den Hund bitte nicht belasten.")
                severity = PawWeatherSeverity.CRITICAL
            }
            temperature >= 30 -> {
                warnings.add("⚠️ WARNUNG: Asphalt und Strassenpflaster kann 50-60°C erreichen. Gefahr gesundheitlicher Beeinträchtigungen! Diese Oberflächen bitte meiden.")
                severity = maxOf(severity, PawWeatherSeverity.WARNING)
            }
            temperature >= 25 -> {
                warnings.add("⚡ VORSICHT: Asphalt wird heiß (45°C+). Erhöhte Belastung für den Hund! Schatten nutzen.")
                severity = maxOf(severity, PawWeatherSeverity.CAUTION)
            }
        }

        // 2. KÄLTE & EIS
        when {
            temperature <= -10 -> {
                warnings.add("❄️ EXTREM KALT: Gesundheitsgefahr für unangepasste Rassen! Hund in stetiger Bewegung halten.")
                severity = maxOf(severity, PawWeatherSeverity.CRITICAL)
            }
            temperature <= -5 -> {
                warnings.add("🧊 Sehr kalt: Pfotenschutz empfohlen. Salz auf Wegen beachten! Hund in stetiger Bewegung halten.")
                severity = maxOf(severity, PawWeatherSeverity.WARNING)
            }
            temperature <= 0 -> {
                warnings.add("🌨️ Frost: Achtung vor Streusalz und Eis. Achtung bei kalter Nässe. Hund zeitnah abtrocknen und aufwärmen lassen.")
                severity = maxOf(severity, PawWeatherSeverity.CAUTION)
            }
        }

        // 3. ZECKENRISIKO
        if (temperature in 7.0..25.0 && precipitation < 5.0) {
            warnings.add("🕷️ Erhöhtes Zeckenrisiko. Hund regelmäßig absuchen und Zecken zeitnah entfernen!")
            severity = maxOf(severity, PawWeatherSeverity.INFO)
        }

        // 4. UV-SCHUTZ
        if (uvIndex >= 6.0) {
            warnings.add("☀️ Hohe UV-Strahlung: Schattige Wege bevorzugen! Keine anstrengenden Aktionen durchführen.")
            severity = maxOf(severity, PawWeatherSeverity.INFO)
        }

        // 5. GEWITTER
        if (weatherCode >= 95) {
            warnings.add("⛈️ Gewitter: Achte auf Deinen Hund und auf Eure Sicherheit. Ängstliche Hunde nicht ableinen.")
            severity = maxOf(severity, PawWeatherSeverity.WARNING)
        }

        // 6. STARKER REGEN
        if (precipitation >= 10.0 || weatherCode in 80..82) {
            warnings.add("🌧️ Starker Regen: Rutschgefahr auf nässenempfindlichen Boden!")
            severity = maxOf(severity, PawWeatherSeverity.CAUTION)
        }

        if (warnings.isEmpty()) {
            warnings.add("✅ Sehr gutes Trainingswetter.")
            severity = PawWeatherSeverity.GOOD
        }

        return PawWeatherResult(warnings, severity)
    }

    private fun addWindWarning(
        result: PawWeatherResult,
        windSpeed: Double,
        windGusts: Double
    ): PawWeatherResult {
        val warnings = result.warnings.toMutableList()
        var severity = result.severity

        when {
            windGusts >= 75 -> {
                warnings.add("💨 STURM: Gefahr durch umstürzende Äste! Training verschieben!")
                severity = maxOf(severity, PawWeatherSeverity.CRITICAL)
            }
            windGusts >= 50 -> {
                warnings.add("🌪️ Starker Wind: Im Gelände aufmerksam unterwegs sein.")
                severity = maxOf(severity, PawWeatherSeverity.WARNING)
            }
            windSpeed >= 40 -> {
                warnings.add("💨 Windige Bedingungen: Gerüche über große Entfernungen wahrnehmbar. Unzuverlässige Hunde bitte an der Leine führen..")
                severity = maxOf(severity, PawWeatherSeverity.INFO)
            }
        }

        return PawWeatherResult(warnings, severity)
    }

    private fun recommendWalkingTimes(
        currentTemp: Double,
        hourlyTemps: List<Double>,
        hourlyTimes: List<String>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (currentTemp >= 25) {
            val coolestHours = hourlyTemps
                .mapIndexed { index, temp -> index to temp }
                .sortedBy { it.second }
                .take(3)
                .map { hourlyTimes[it.first] }

            recommendations.add("🌅 Beste Zeiten bei Hitze:")
            coolestHours.forEach { time ->
                recommendations.add("  • $time Uhr (kühlste Phase)")
            }
        }

        if (currentTemp <= 5) {
            val warmestHours = hourlyTemps
                .mapIndexed { index, temp -> index to temp }
                .sortedByDescending { it.second }
                .take(3)
                .map { hourlyTimes[it.first] }

            recommendations.add("☀️ Beste Zeiten bei Kälte:")
            warmestHours.forEach { time ->
                recommendations.add("  • $time Uhr (wärmste Phase)")
            }
        }

        return recommendations
    }

    private fun getGroomingTips(weatherCode: Int, precipitation: Double): List<String> {
        val tips = mutableListOf<String>()

        when {
            weatherCode in 61..67 || weatherCode in 80..82 -> {
                tips.add("🛁 Nach der Runde: Pfoten und Fell trocknen und dabei auf Zecken achten.")
                tips.add("\uD83D\uDC36 Ohren auf Nässe und Verschmutzungen prüfen")
            }
            weatherCode in 71..77 || weatherCode in 85..86 -> {
                tips.add("❄️ Eisklumpen zwischen Zehen entfernen")
                tips.add("🧴 Pfotenbalsam verwenden gegen Risse")
            }
            precipitation >= 5.0 -> {
                tips.add("🚿 Matschige Pfoten vor dem Reingehen säubern")
            }
            precipitation == 0.0 && weatherCode <= 3 -> {
                tips.add("\uD83E\uDDAE Hundefreundliches Wetter - bei intensiven Training an 💧 Trinkwasser denken.")
            }
        }

        return tips
    }

    data class PawWeatherResult(
        val warnings: List<String>,
        val severity: PawWeatherSeverity
    )

    enum class PawWeatherSeverity {
        GOOD, INFO, CAUTION, WARNING, CRITICAL;

        fun getColor(): Int {
            return when (this) {
                GOOD -> Color.parseColor("#4CAF50")
                INFO -> Color.parseColor("#2196F3")
                CAUTION -> Color.parseColor("#FFC107")
                WARNING -> Color.parseColor("#FF9800")
                CRITICAL -> Color.parseColor("#F44336")
            }
        }
    }

// ════════════════════════════════════════════════════════════════
// FEATURE 2: TEMPERATUR-VERLAUFSGRAFIK
// ════════════════════════════════════════════════════════════════

    private fun displayTemperatureChart(data: JSONObject) {
        try {
            val hourly = data.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temperatures = hourly.getJSONArray("temperature_2m")

            val temps = mutableListOf<Double>()
            val labels = mutableListOf<String>()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

            // ✅ KORREKTUR: Nächste 24h ab jetzt finden
            val now = System.currentTimeMillis()
            val oneHour = 3600000L
            
            var startIndex = 0
            for (i in 0 until times.length()) {
                val date = dateFormat.parse(times.getString(i))
                if (date != null && date.time >= now - oneHour) {
                    startIndex = i
                    break
                }
            }
            
            val limit = (startIndex + 24).coerceAtMost(temperatures.length())

            for (i in startIndex until limit) {
                temps.add(temperatures.getDouble(i))

                val timeStr = times.getString(i)
                val date = dateFormat.parse(timeStr)
                labels.add(date?.let { timeFormat.format(it) } ?: "")
            }

            binding.temperatureChart.setData(temps, labels)
        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei Temperatur-Chart", e)
        }
    }

// ════════════════════════════════════════════════════════════════
// FEATURE: NIEDERSCHLAGS-VERLAUFSGRAFIK
// ════════════════════════════════════════════════════════════════

    private fun displayPrecipitationChart(data: JSONObject) {
        try {
            val hourly = data.getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val precipitationArray = hourly.getJSONArray("precipitation")

            val precipValues = mutableListOf<Double>()
            val labels = mutableListOf<String>()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

            // ✅ KORREKTUR: Nächste 24h ab jetzt finden
            val now = System.currentTimeMillis()
            val oneHour = 3600000L
            
            var startIndex = 0
            for (i in 0 until times.length()) {
                val date = dateFormat.parse(times.getString(i))
                if (date != null && date.time >= now - oneHour) {
                    startIndex = i
                    break
                }
            }

            val limit = (startIndex + 24).coerceAtMost(precipitationArray.length())

            for (i in startIndex until limit) {
                precipValues.add(precipitationArray.getDouble(i))

                val timeStr = times.getString(i)
                val date = dateFormat.parse(timeStr)
                labels.add(date?.let { timeFormat.format(it) } ?: "")
            }

            binding.precipitationChart.setData(precipValues, labels)
        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei Niederschlags-Chart", e)
        }
    }

// ════════════════════════════════════════════════════════════════
// FEATURE 3: TAGESLÄNGE & DÄMMERUNG
// ════════════════════════════════════════════════════════════════

    // NEU: Tageslänge & Dämmerung
    private fun displayDaylightInfo(data: JSONObject) {
        try {
            val daily = data.getJSONObject("daily")
            val sunriseArray = daily.getJSONArray("sunrise")
            val sunsetArray = daily.getJSONArray("sunset")

            if (sunriseArray.length() >= 2 && sunsetArray.length() >= 2) {
                // ✅ KORREKTUR: Gestern (Index 0) und Heute (Index 1)
                val yesterdaySunriseStr = sunriseArray.getString(0)
                val yesterdaySunsetStr = sunsetArray.getString(0)
                val todaySunriseStr = sunriseArray.getString(1)
                val todaySunsetStr = sunsetArray.getString(1)

                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

                val sunrise = dateFormat.parse(todaySunriseStr)
                val sunset = dateFormat.parse(todaySunsetStr)

                val yesterdaySunrise = dateFormat.parse(yesterdaySunriseStr)
                val yesterdaySunset = dateFormat.parse(yesterdaySunsetStr)

                if (sunrise != null && sunset != null) {
                    // Tageslänge heute
                    val daylightMillis = sunset.time - sunrise.time
                    val hours = TimeUnit.MILLISECONDS.toHours(daylightMillis)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(daylightMillis) % 60

                    binding.daylightDurationText.text = String.format(
                        Locale.GERMAN,
                        "☀️ Tageslänge: %dh %dm",
                        hours,
                        minutes
                    )

                    // Dämmerungszeiten
                    val civilTwilightDuration = 35 * 60 * 1000L
                    val dawnStart = Date(sunrise.time - civilTwilightDuration)
                    val duskEnd = Date(sunset.time + civilTwilightDuration)

                    binding.dawnText.text = String.format("🌄 Morgendämmerung: %s Uhr", timeFormat.format(dawnStart))
                    binding.duskText.text = String.format("🌆 Abenddämmerung: %s Uhr", timeFormat.format(duskEnd))

                    // ✅ KORREKTUR: Trend-Berechnung mit echtem Gestern
                    if (yesterdaySunrise != null && yesterdaySunset != null) {
                        val yesterdayDaylight = yesterdaySunset.time - yesterdaySunrise.time
                        val change = daylightMillis - yesterdayDaylight
                        val changeMinutes = TimeUnit.MILLISECONDS.toMinutes(change)

                        val trend = when {
                            changeMinutes > 0 -> "📈 +$changeMinutes Min länger"
                            changeMinutes < 0 -> "📉 ${-changeMinutes} Min kürzer"
                            else -> "➡️ Gleich lang"
                        }

                        binding.daylightTrendText.text = "Tendenz: $trend als gestern"
                        binding.daylightTrendText.visibility = View.VISIBLE
                    }

                    displayLightBasedRecommendation(sunrise, sunset)
                    displayGoldenHour(sunrise, sunset)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei Tageslänge", e)
        }
    }

    private fun displayLightBasedRecommendation(sunrise: Date, sunset: Date) {
        val now = Date()
        val recommendations = mutableListOf<String>()

        if (now.before(sunrise)) {
            val minutesUntilSunrise = TimeUnit.MILLISECONDS.toMinutes(sunrise.time - now.time)

            if (minutesUntilSunrise < 60) {
                recommendations.add("🌅 Sonnenaufgang in ${minutesUntilSunrise}Min - Perfekt für Morgenrunde!")
            } else {
                recommendations.add("🌃 Noch dunkel - Achtet auf Eigensicherung und Reflektoren bei gefährlichen Strassensituationen!")
            }
        }

        if (now.before(sunset)) {
            val minutesUntilSunset = TimeUnit.MILLISECONDS.toMinutes(sunset.time - now.time)

            if (minutesUntilSunset < 120) {
                recommendations.add("🌇 Sonnenuntergang in ${minutesUntilSunset}Min - Ideal für Abendrunde!")
            }
        } else {
            recommendations.add("🌙 Dunkel - Achtet auf Eigensicherung und Lampe nicht vergessen!")
        }

        if (recommendations.isNotEmpty()) {
            binding.lightRecommendationText.text = recommendations.joinToString("\n")
            binding.lightRecommendationText.visibility = View.VISIBLE
        } else {
            binding.lightRecommendationText.visibility = View.GONE
        }
    }

    private fun displayGoldenHour(sunrise: Date, sunset: Date) {
        val goldenHourDuration = 60 * 60 * 1000L

        val morningGoldenStart = sunrise
        val morningGoldenEnd = Date(sunrise.time + goldenHourDuration)

        val eveningGoldenStart = Date(sunset.time - goldenHourDuration)
        val eveningGoldenEnd = sunset

        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

        val goldenHourText = """
        📸 Goldene Stunde (schönes Licht für Fotos):
        Morgens: ${timeFormat.format(morningGoldenStart)} - ${timeFormat.format(morningGoldenEnd)}
        Abends: ${timeFormat.format(eveningGoldenStart)} - ${timeFormat.format(eveningGoldenEnd)}
    """.trimIndent()

        binding.goldenHourText.text = goldenHourText
        binding.goldenHourText.visibility = View.VISIBLE
    }

}