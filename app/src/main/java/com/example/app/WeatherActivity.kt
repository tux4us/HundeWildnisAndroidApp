package com.hundewildnis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Standortwetter"
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
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getLocation()
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
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
                "&hourly=temperature_2m,precipitation_probability,weather_code," +
                "temperature_2m,apparent_temperature" + // NEU: Stündliche Daten
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset," + // NEU: Min/Max, Sonne
                "weather_code,precipitation_probability_max" + // NEU: Tageswarnungen
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

    private fun getWindDirection(degrees: Double): String {
        return when {
            degrees >= 337.5 || degrees < 22.5 -> "↓ N"
            degrees >= 22.5 && degrees < 67.5 -> "↙ NO"
            degrees >= 67.5 && degrees < 112.5 -> "← O"
            degrees >= 112.5 && degrees < 157.5 -> "↖ SO"
            degrees >= 157.5 && degrees < 202.5 -> "↑ S"
            degrees >= 202.5 && degrees < 247.5 -> "↗ SW"
            degrees >= 247.5 && degrees < 292.5 -> "→ W"
            degrees >= 292.5 && degrees < 337.5 -> "↘ NW"
            else -> ""
        }
    }

    // NEU: Sonnenauf-/untergang
    private fun displaySunriseSunset(data: JSONObject) {
        try {
            val daily = data.getJSONObject("daily")
            val sunriseArray = daily.getJSONArray("sunrise")
            val sunsetArray = daily.getJSONArray("sunset")

            if (sunriseArray.length() > 0 && sunsetArray.length() > 0) {
                val sunriseStr = sunriseArray.getString(0)
                val sunsetStr = sunsetArray.getString(0)

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

            if (minTemps.length() > 0) {
                val minTemp = minTemps.getDouble(0)
                val maxTemp = maxTemps.getDouble(0)
                val code = weatherCodes.getInt(0)
                val precipProb = precipProbs.getInt(0)

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
            code >= 71 -> "❄️ Schneefall"
            code >= 61 && precipProb >= 70 -> "🌧️ Starker Regen"
            precipProb >= 80 -> "☔ Hohe Regenwahrscheinlichkeit"
            code >= 80 -> "🌦️ Schauer"
            precipProb >= 50 -> "🌂 Möglicher Regen (${precipProb}%)"
            code >= 45 -> "🌫️ Nebel"
            code >= 3 -> "☁️ Bewölkt"
            else -> "✅ Keine Wetterwarnung" // IMMER etwas anzeigen
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

        // NEU: 24 stündliche Einträge (statt 8 x 3h)
        var count = 0
        for (i in 0 until times.length()) {
            if (count >= 24) break // Genau 24 Stunden

            try {
                val timeStr = times.getString(i)
                val date = dateFormat.parse(timeStr)

                if (date != null && date.time >= now) {
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

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Vorhersage Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    // ERWEITERT: Regenradar mit Nowcast
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

            // NEU: Mit Nowcast (Vorhersage)
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
                    
                    // NEU: Mit Nowcast (Vergangenheit + Vorhersage)
                    // ERWEITERT: 6h Historie statt 2h
                    fetch('https://api.rainviewer.com/public/weather-maps.json')
                        .then(response => response.json())
                        .then(apiData => {
                            if (apiData.radar && apiData.radar.past && apiData.radar.nowcast) {
                                // Kombiniere: Letzte 36 Vergangenheit (6h) + 6 Nowcast (Vorhersage)
                                // RainViewer liefert alle 10 Min einen Frame
                                // 36 Frames = 6 Stunden Historie
                                const pastFrames = apiData.radar.past.slice(-36);
                                const nowcastFrames = apiData.radar.nowcast.slice(0, 6);
                                timestamps = pastFrames.concat(nowcastFrames);
                                
                                timestamps.forEach((item, index) => {
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
                                
                                // Zeige letzten Vergangenheits-Frame (aktuell)
                                if (radarLayers.length > 0) {
                                    currentFrame = pastFrames.length - 1; // Letzter Vergangenheits-Frame
                                    radarLayers[currentFrame].addTo(map);
                                    updateTimestamp();
                                }
                            }
                        })
                        .catch(err => {
                            console.error('Radar error:', err);
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
                        
                        // GEDROSSELT: 750ms pro Frame (50% langsamer)
                        animationInterval = setInterval(() => {
                            radarLayers[currentFrame].remove();
                            currentFrame = (currentFrame + 1) % radarLayers.length;
                            radarLayers[currentFrame].addTo(map);
                            updateTimestamp();
                        }, 750); // War 500ms, jetzt 750ms
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
                            currentFrame = timestamps.length > 6 ? timestamps.length - 7 : 0; // Zurück zu "jetzt" (36. Frame bei 6h Historie)
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
                            
                            // NEU: Markiere Nowcast (Vorhersage)
                            // Mit 6h Historie: Frame 0-35 = Vergangenheit, Frame 36+ = Vorhersage
                            const pastFrameCount = timestamps.length > 6 ? timestamps.length - 6 : 0;
                            const isNowcast = currentFrame >= pastFrameCount;
                            const label = isNowcast ? '🔮 Vorhersage' : '📡 Radar';
                            
                            document.getElementById('timestamp').textContent = 
                                label + ': ' + timeStr + ' Uhr';
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
}