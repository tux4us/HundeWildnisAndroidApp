package com.hundewildnis.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.hundewildnis.app.databinding.ActivityWeatherBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Locale
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class WeatherActivity : AppCompatActivity(), LocationListener {

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
            title = "Wetter"
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
        val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature," +
                "precipitation,weather_code,surface_pressure,wind_speed_10m,uv_index" +
                "&hourly=temperature_2m,precipitation_probability,weather_code" +
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
        val uvIndex = current.getDouble("uv_index")

        // Temperatur
        binding.temperatureText.text = String.format(Locale.GERMAN, "%.1f°C", temperature)
        binding.feelsLikeText.text = String.format(Locale.GERMAN, "%.1f°C", feelsLike)

        // Wetterbeschreibung und Icon
        val (description, icon) = getWeatherDescription(weatherCode)
        binding.descriptionText.text = description
        binding.weatherIcon.text = icon

        // Weitere Daten
        binding.humidityText.text = "$humidity%"
        binding.windText.text = String.format(Locale.GERMAN, "%.1f km/h", windSpeed)
        binding.precipitationText.text = String.format(Locale.GERMAN, "%.1f mm", precipitation)
        binding.pressureText.text = String.format(Locale.GERMAN, "%.0f hPa", pressure)
        binding.visibilityText.text = "> 10 km"
        binding.uvIndexText.text = String.format(Locale.GERMAN, "%.1f", uvIndex)

        // 24h Vorhersage
        display24HourForecast(data)

        // Regenradar
        setupRainRadar()
    }

    private fun display24HourForecast(data: JSONObject) {
        val hourly = data.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temperatures = hourly.getJSONArray("temperature_2m")
        val weatherCodes = hourly.getJSONArray("weather_code")
        val precipProb = hourly.getJSONArray("precipitation_probability")

        val forecastItems = mutableListOf<ForecastItem>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

        // Nächste 24 Stunden (jede 3. Stunde für bessere Übersicht)
        for (i in 0 until minOf(24, times.length()) step 3) {
            try {
                val timeStr = times.getString(i)
                val date = dateFormat.parse(timeStr)
                val displayTime = if (date != null) timeFormat.format(date) else timeStr

                val temp = temperatures.getDouble(i)
                val code = weatherCodes.getInt(i)
                val precip = if (i < precipProb.length()) precipProb.getDouble(i) else 0.0

                forecastItems.add(ForecastItem(displayTime, temp, code, precip))
            } catch (e: Exception) {
                // Skip fehlerhaften Eintrag
            }
        }

        // RecyclerView einrichten
        binding.forecastRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.forecastRecyclerView.adapter = ForecastAdapter(forecastItems)
    }

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

            binding.radarWebView.webViewClient = WebViewClient()

            // Vereinfachte OpenStreetMap Karte mit Standort
            val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    * { margin: 0; padding: 0; }
                    html, body { height: 100%; }
                    #map { width: 100%; height: 100%; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    try {
                        var map = L.map('map', {
                            center: [$lat, $lon],
                            zoom: 8,
                            zoomControl: true
                        });
                        
                        // OpenStreetMap Tiles
                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            attribution: '© OpenStreetMap',
                            maxZoom: 19
                        }).addTo(map);
                        
                        // Standort Marker
                        var marker = L.marker([$lat, $lon]).addTo(map);
                        marker.bindPopup('Dein Standort').openPopup();
                        
                        // RainViewer Radar
                        fetch('https://api.rainviewer.com/public/weather-maps.json')
                            .then(response => response.json())
                            .then(apiData => {
                                if (apiData.radar && apiData.radar.past && apiData.radar.past.length > 0) {
                                    var radarLayers = [];
                                    var timestamps = apiData.radar.past.concat(apiData.radar.nowcast);
                                    
                                    // Zeige letzte verfügbare Radaraufnahme
                                    var lastFrame = timestamps[timestamps.length - 1];
                                    var radarUrl = 'https://tilecache.rainviewer.com' + lastFrame.path + '/256/{z}/{x}/{y}/2/1_1.png';
                                    
                                    L.tileLayer(radarUrl, {
                                        tileSize: 256,
                                        opacity: 0.5,
                                        zIndex: 10,
                                        attribution: '© RainViewer'
                                    }).addTo(map);
                                }
                            })
                            .catch(err => console.error('Radar error:', err));
                            
                    } catch (e) {
                        console.error('Map error:', e);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

            binding.radarWebView.loadDataWithBaseURL(
                "https://example.com",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    private fun getWeatherDescription(code: Int): Pair<String, String> {
        return when (code) {
            0 -> "Klar" to "☀️"
            1, 2, 3 -> "Bewölkt" to "⛅"
            45, 48 -> "Nebel" to "🌫️"
            51, 53, 55 -> "Nieselregen" to "🌦️"
            61, 63, 65 -> "Regen" to "🌧️"
            71, 73, 75 -> "Schneefall" to "🌨️"
            77 -> "Graupel" to "🌨️"
            80, 81, 82 -> "Regenschauer" to "🌦️"
            85, 86 -> "Schneeschauer" to "🌨️"
            95 -> "Gewitter" to "⛈️"
            96, 99 -> "Gewitter mit Hagel" to "⛈️"
            else -> "Unbekannt" to "🌡️"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}