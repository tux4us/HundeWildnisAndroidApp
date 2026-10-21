package com.hundewildnis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID
import kotlin.math.pow
import java.util.concurrent.TimeUnit
import kotlin.math.max
import android.content.Context
import android.graphics.*
import android.util.AttributeSet


class WeatherActivity : SecureBaseActivity(), LocationListener {

    private lateinit var binding: ActivityWeatherBinding
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private var currentCityName: String? = null
    private var isRequestingUpdates = false

    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0

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
            title = getString(R.string.weather_toolbar_title)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        checkLocationPermission()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_weather, menu)
        updateFavoriteIcon(menu)
        return true
    }

    private fun updateFavoriteIcon(menu: android.view.Menu? = null) {
        val targetMenu = menu ?: binding.toolbar.menu
        val favItem = targetMenu.findItem(R.id.action_favorite_toggle)
        if (WeatherFavoritesManager.isFavorite(this, currentLat, currentLon)) {
            favItem?.setIcon(R.drawable.ic_star_filled)
        } else {
            favItem?.setIcon(R.drawable.ic_star_outline)
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_search -> { showSearchDialog(); true }
            R.id.action_favorite_toggle -> { toggleFavorite(); true }
            R.id.action_favorites -> { showFavoritesDialog(); true }
            R.id.action_current_location -> { getLocation(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.weather_location_services_title))
                .setMessage(getString(R.string.weather_location_services_message))
                .setPositiveButton(getString(R.string.start_notification_disclosure_accept)) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
                .setNegativeButton(getString(R.string.start_notification_disclosure_decline), null)
                .create()
                .apply {
                    show()
                }
        } else {
            getLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocation()
                } else {
                    binding.descriptionText.text = getString(R.string.weather_location_permission_required)
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, getString(R.string.weather_location_permission_denied), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        binding.progressBar.visibility = View.VISIBLE
        binding.descriptionText.text = getString(R.string.weather_determining_location)
        var bestLocation: Location? = null
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        if (bestLocation != null && isLocationRecent(bestLocation)) {
            currentLat = bestLocation.latitude
            currentLon = bestLocation.longitude
            loadWeather(bestLocation.latitude, bestLocation.longitude)
        } else {
            requestLocationUpdates()
        }
    }

    private fun isLocationRecent(location: Location): Boolean {
        val maxAge = 300000L
        return System.currentTimeMillis() - location.time < maxAge
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        isRequestingUpdates = true
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_CHANGE, this)
            }
        } catch (e: Exception) {}

        try {
            if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_CHANGE, this)
            }
        } catch (e: Exception) {}
    }

    private fun stopLocationUpdates() {
        if (isRequestingUpdates) {
            locationManager.removeUpdates(this)
            isRequestingUpdates = false
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onLocationChanged(location: Location) {
        currentLat = location.latitude
        currentLon = location.longitude
        currentCityName = null
        loadWeather(location.latitude, location.longitude)
        stopLocationUpdates()
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun showSearchDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.weather_search_hint)
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.weather_search_title))
            .setView(input)
            .setPositiveButton(getString(R.string.search)) { _, _ ->
                val query = input.text.toString()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun loadWeather(latitude: Double, longitude: Double, cityName: String? = null) {
        currentLat = latitude
        currentLon = longitude
        currentCityName = cityName

        updateFavoriteIcon()

        // Ohne Anbieter/Zugang ruft die App nichts ab und zeigt nur den Hinweis.
        if (!WeatherProvider.isConfigured(this)) {
            binding.progressBar.visibility = View.GONE
            binding.descriptionText.text = getString(R.string.weather_setup_hint)
            binding.weatherSourceFooter.text = ""
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.weather_setup_title))
                .setMessage(getString(R.string.weather_setup_hint))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
            return
        }
        binding.progressBar.visibility = View.VISIBLE

        val displayLoc = cityName ?: String.format(Locale.getDefault(), "%.4f°, %.4f°", latitude, longitude)
        binding.locationText.text = getString(R.string.weather_location_prefix, displayLoc)

        lifecycleScope.launch {
            try {
                // Falls cityName null ist, versuchen wir ihn via Reverse-Geocoding zu finden
                val nameToUse = cityName ?: withContext(Dispatchers.IO) {
                    reverseGeocode(latitude, longitude)
                }

                if (nameToUse != null) {
                    currentCityName = nameToUse
                    binding.locationText.text = getString(R.string.weather_location_prefix, nameToUse)
                }

                // Beide API-Calls parallel starten
                val weatherDeferred = async(Dispatchers.IO) {
                    fetchWeatherData(latitude, longitude)
                }
                val airQualityDeferred = async(Dispatchers.IO) {
                    try { fetchAirQualityData(latitude, longitude) }
                    catch (e: Exception) {
                        android.util.Log.w("WeatherActivity", "Luftqualitätsdaten nicht verfügbar", e)
                        null
                    }
                }
                // Warten bis beide fertig sind
                val weatherData    = weatherDeferred.await()
                val airQualityData = airQualityDeferred.await()

                displayWeatherData(weatherData, airQualityData, latitude, longitude)
                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.descriptionText.text = getString(R.string.weather_load_error)
                Toast.makeText(this@WeatherActivity, WeatherProvider.describeError(applicationContext, e), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performSearch(query: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                    val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5&accept-language=de"
                    AppNetwork.waitForNominatimSlot()
                    val connection = URL(url).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", AppNetwork.USER_AGENT)
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = org.json.JSONArray(response)
                    val list = mutableListOf<WeatherFavorite>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(WeatherFavorite(
                            name = obj.optString("display_name").split(",").first(),
                            lat = obj.optString("lat").toDouble(),
                            lon = obj.optString("lon").toDouble()
                        ))
                    }
                    list
                }

                binding.progressBar.visibility = View.GONE
                if (results.isEmpty()) {
                    Toast.makeText(this@WeatherActivity, getString(R.string.weather_search_no_results), Toast.LENGTH_SHORT).show()
                } else {
                    showSearchResultsDialog(results)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@WeatherActivity, getString(R.string.weather_error_prefix, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSearchResultsDialog(results: List<WeatherFavorite>) {
        val names = results.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.weather_results))
            .setItems(names) { _, which ->
                val selected = results[which]
                loadWeather(selected.lat, selected.lon, selected.name)
            }
            .show()
    }

    private fun toggleFavorite() {
        if (WeatherFavoritesManager.isFavorite(this, currentLat, currentLon)) {
            WeatherFavoritesManager.remove(this, currentLat, currentLon)
            Toast.makeText(this, getString(R.string.weather_removed_from_favorites), Toast.LENGTH_SHORT).show()
        } else {
            val name = currentCityName ?: String.format(Locale.getDefault(), "%.2f, %.2f", currentLat, currentLon)
            WeatherFavoritesManager.add(this, WeatherFavorite(name = name, lat = currentLat, lon = currentLon))
            Toast.makeText(this, getString(R.string.weather_added_to_favorites), Toast.LENGTH_SHORT).show()
        }
        updateFavoriteIcon()
    }

    private fun showFavoritesDialog() {
        val favorites = WeatherFavoritesManager.getAll(this)
        if (favorites.isEmpty()) {
            Toast.makeText(this, getString(R.string.weather_no_favorites_yet), Toast.LENGTH_SHORT).show()
            return
        }

        val names = favorites.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.weather_favorites_title))
            .setItems(names) { _, which ->
                val selected = favorites[which]
                loadWeather(selected.lat, selected.lon, selected.name)
            }
            .setNeutralButton(getString(R.string.weather_manage)) { _, _ -> manageFavorites() }
            .setPositiveButton(getString(R.string.start_close), null)
            .show()
    }

    private fun manageFavorites() {
        val favorites = WeatherFavoritesManager.getAll(this)
        val names = favorites.map { it.name }.toTypedArray()
        val checked = BooleanArray(favorites.size) { false }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.weather_delete_favorites_title))
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(getString(R.string.weather_delete_selected)) { _, _ ->
                val newList = favorites.filterIndexed { index, _ -> !checked[index] }
                WeatherFavoritesManager.saveAll(this, newList)
                updateFavoriteIcon()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): String? {
        return try {
            val languageTag = LanguageManager.getCurrentLanguage().tag
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&accept-language=$languageTag"
            AppNetwork.waitForNominatimSlot()
            val connection = URL(url).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", AppNetwork.USER_AGENT)
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val address = json.optJSONObject("address")
            address?.optString("city") ?: address?.optString("town") ?: address?.optString("village")
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchWeatherData(latitude: Double, longitude: Double): JSONObject {
        return WeatherProvider.fetchForecast(applicationContext, latitude, longitude,
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature," +
                "precipitation,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m," +
                "wind_gusts_10m,uv_index,is_day" +
                "&hourly=temperature_2m,precipitation,precipitation_probability,weather_code," +
                "apparent_temperature,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
                "cloud_cover,visibility,uv_index,cape,surface_pressure" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset," +
                "weather_code,precipitation_probability_max," +
                "wind_speed_10m_max,wind_gusts_10m_max,uv_index_max,sunshine_duration" +
                "&past_days=1" +
                "&forecast_days=7" +
                "&timezone=auto")
    }

    private fun fetchAirQualityData(latitude: Double, longitude: Double): JSONObject {
        // Luftqualität: je nach Anbieter (Open-Meteo-Dienst oder OpenWeatherMap)
        return WeatherProvider.fetchAirQuality(applicationContext, latitude, longitude,
                "&hourly=pm10,pm2_5,nitrogen_dioxide,ozone,carbon_monoxide," +
                "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen" +
                "&forecast_days=2" +
                "&timezone=auto")
    }

    // ════════════════════════════════════════════════════════════════
    // displayWeatherData – Haupteinstieg nach API-Antwort
    // ════════════════════════════════════════════════════════════════

    private fun displayWeatherData(data: JSONObject, airQualityData: JSONObject?, latitude: Double, longitude: Double) {
        val current = data.getJSONObject("current")
        val temperature   = current.getDouble("temperature_2m")
        val feelsLike     = current.getDouble("apparent_temperature")
        val weatherCode   = current.getInt("weather_code")
        val windSpeed     = current.getDouble("wind_speed_10m")
        val windGusts     = current.getDouble("wind_gusts_10m")
        val windDirection = current.getDouble("wind_direction_10m")
        val humidity      = current.getInt("relative_humidity_2m")
        val precipitation = current.getDouble("precipitation")
        val pressure      = current.getDouble("surface_pressure")
        val uvIndex       = current.optDouble("uv_index", Double.NaN)   // nicht bei jedem Anbieter

        binding.temperatureText.text = getString(R.string.weather_degree_celsius, temperature)

        val windchill = calculateWindchill(temperature, windSpeed)
        binding.feelsLikeText.text = getString(R.string.weather_feels_like, feelsLike, windchill)

        val (description, icon) = getWeatherDescription(weatherCode)
        binding.descriptionText.text = description
        binding.weatherIcon.text = icon

        val windDirectionText = getWindDirection(windDirection)
        val beaufort = getBeaufortScale(windSpeed)
        binding.tvWindSymbol.text = getBeaufortSymbol(beaufort)
        binding.windText.text = getString(R.string.weather_wind_display, windSpeed, windDirectionText, beaufort, windGusts)

        binding.humidityText.text = getString(R.string.weather_percent, humidity)
        binding.precipitationText.text = getString(R.string.weather_mm, precipitation)
        binding.pressureText.text = getString(R.string.weather_hpa, pressure)
        binding.visibilityText.text = getString(R.string.weather_visibility_format)
        binding.uvIndexRow.visibility = if (uvIndex.isNaN()) View.GONE else View.VISIBLE
        if (!uvIndex.isNaN()) binding.uvIndexText.text = String.format(Locale.getDefault(), "%.1f", uvIndex)

        displaySunriseSunset(data)
        display24HourForecast(data)
        displayDailyOverview(data)
        display7DayForecast(data)           // NEU: 7-Tage-Card
        displayWindForecast(data)           // NEU: Wind-stündlich-Card
        displaySunCard(data, latitude, longitude)                // NEU: Sonne-Card
        if (airQualityData != null) {
            displayAirQualityCard(airQualityData)   // NEU: Luftqualität-Card
            if (airQualityData.optJSONObject("hourly")?.has("birch_pollen") == true) {
                displayPollenCard(airQualityData)   // NEU: Pollen-Card (nur Open-Meteo)
            }
        }
        binding.weatherSourceFooter.text = WeatherProvider.attribution(this)
        displayPawWeather(data, windSpeed, windGusts)
        displayTemperatureChart(data)
        displayPrecipitationChart(data)
        displayDaylightInfo(data)
        displayMoonPhase()
        displayBiowetter(data)    // NEU: Biowetter-Card
    }


    // ════════════════════════════════════════════════════════════════
    // MONDPHASE  (rein lokal, kein API-Aufruf)
    // ════════════════════════════════════════════════════════════════

    private fun displayMoonPhase() {
        try {
            val moonInfo = calculateMoonPhase()
            binding.moonPhaseView.moonAge        = moonInfo.age
            binding.moonPhaseNameText.text       = moonInfo.phaseName
            binding.moonIlluminationText.text    = getString(R.string.weather_moon_illumination, moonInfo.illumination)
            binding.moonIlluminationBar.progress = moonInfo.illumination.toInt()
            binding.moonAgeText.text             = getString(R.string.weather_moon_age, moonInfo.age)
            binding.moonNextFullText.text        = moonInfo.nextFullMoon
            binding.moonNextNewText.text         = moonInfo.nextNewMoon
            binding.moonZodiacText.text          = moonInfo.zodiacSign
            binding.moonAstronomyText.text       = moonInfo.astronomyInfo
            binding.moonNatureText.text          = moonInfo.natureInfo
            binding.moonDogTipsText.text         = moonInfo.dogTips
        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei Mondphase", e)
        }
    }

    data class MoonInfo(
        val phaseName:     String,
        val emoji:         String,
        val illumination:  Double,
        val age:           Double,
        val nextFullMoon:  String,
        val nextNewMoon:   String,
        val zodiacSign:    String,
        val astronomyInfo: String,
        val natureInfo:    String,
        val dogTips:       String
    )

    private fun calculateMoonPhase(): MoonInfo {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val jd = toJulianDate(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)
        )
        val referenceNewMoon = 2451550.259
        val synodicPeriod   = 29.53059
        val siderealPeriod  = 27.32158

        var moonAge = (jd - referenceNewMoon) % synodicPeriod
        if (moonAge < 0) moonAge += synodicPeriod

        val illumination = (1.0 - Math.cos(Math.PI * moonAge / (synodicPeriod / 2.0))) / 2.0 * 100.0

        val halfPeriod = synodicPeriod / 2.0
        val daysToFull = if (moonAge <= halfPeriod) halfPeriod - moonAge else synodicPeriod - moonAge + halfPeriod
        val daysToNew  = synodicPeriod - moonAge

        // Verfeinerte Phasen-Logik:
        // Wir nutzen engere Fenster für die Hauptphasen (Neumond, Halbmond, Vollmond),
        // damit die Bezeichnung schneller auf das "nächste Viertel" umspringt.
        val progress = moonAge / synodicPeriod
        val phaseIndex = when {
            progress < 0.03 || progress > 0.97 -> 0 // Neumond
            progress < 0.22 -> 1                   // Zunehmende Sichel
            progress < 0.28 -> 2                   // Erstes Viertel (Halbmond)
            progress < 0.47 -> 3                   // Zunehmender Mond
            progress < 0.53 -> 4                   // Vollmond
            progress < 0.72 -> 5                   // Abnehmender Mond
            progress < 0.78 -> 6                   // Letztes Viertel (Halbmond)
            else            -> 7                   // Abnehmende Sichel
        }

        var zodiacAge = (jd - referenceNewMoon) % siderealPeriod
        if (zodiacAge < 0) zodiacAge += siderealPeriod
        val zodiacIndex = ((zodiacAge / siderealPeriod) * 12.0).toInt().coerceIn(0, 11)

        val (phaseName, emoji) = getMoonPhaseNameAndEmoji(phaseIndex)

        return MoonInfo(
            phaseName     = phaseName,
            emoji         = emoji,
            illumination  = illumination,
            age           = moonAge,
            nextFullMoon  = buildNextEventText(getString(R.string.weather_moon_full_moon_label), daysToFull),
            nextNewMoon   = buildNextEventText(getString(R.string.weather_moon_new_moon_label),  daysToNew),
            zodiacSign    = getMoonZodiacSign(zodiacIndex),
            astronomyInfo = getMoonAstronomyInfo(phaseIndex, illumination),
            natureInfo    = getMoonNatureInfo(phaseIndex),
            dogTips       = getMoonDogTips(phaseIndex)
        )
    }

    private fun toJulianDate(year: Int, month: Int, day: Int, hour: Int, minute: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y--; m += 12 }
        val a = y / 100
        val b = 2 - a + a / 4
        val dayFraction = day + hour / 24.0 + minute / 1440.0
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + dayFraction + b - 1524.5
    }

    private fun buildNextEventText(label: String, days: Double): String = when {
        days < 0.5 -> getString(R.string.weather_moon_event_today, label)
        days < 1.5 -> getString(R.string.weather_moon_event_tomorrow, label)
        else       -> getString(R.string.weather_moon_event_in_days, label, days.toInt())
    }

    private fun getMoonPhaseNameAndEmoji(phaseIndex: Int): Pair<String, String> = when (phaseIndex) {
        0 -> getString(R.string.weather_moon_phase_0) to "🌑"
        1 -> getString(R.string.weather_moon_phase_1) to "🌒"
        2 -> getString(R.string.weather_moon_phase_2) to "🌓"
        3 -> getString(R.string.weather_moon_phase_3) to "🌔"
        4 -> getString(R.string.weather_moon_phase_4) to "🌕"
        5 -> getString(R.string.weather_moon_phase_5) to "🌖"
        6 -> getString(R.string.weather_moon_phase_6) to "🌗"
        else -> getString(R.string.weather_moon_phase_7) to "🌘"
    }

    private fun getMoonAstronomyInfo(phaseIndex: Int, illumination: Double): String {
        val pct = illumination.toInt()
        val res = when (phaseIndex) {
            0 -> R.string.weather_moon_astronomy_0
            1 -> R.string.weather_moon_astronomy_1
            2 -> R.string.weather_moon_astronomy_2
            3 -> R.string.weather_moon_astronomy_3
            4 -> R.string.weather_moon_astronomy_4
            5 -> R.string.weather_moon_astronomy_5
            6 -> R.string.weather_moon_astronomy_6
            else -> R.string.weather_moon_astronomy_7
        }
        return getString(res, pct)
    }

    private fun getMoonNatureInfo(phaseIndex: Int): String = when (phaseIndex) {
        0 -> getString(R.string.weather_moon_nature_0)
        1 -> getString(R.string.weather_moon_nature_1)
        2 -> getString(R.string.weather_moon_nature_2)
        3 -> getString(R.string.weather_moon_nature_3)
        4 -> getString(R.string.weather_moon_nature_4)
        5 -> getString(R.string.weather_moon_nature_5)
        6 -> getString(R.string.weather_moon_nature_6)
        else -> getString(R.string.weather_moon_nature_7)
    }

    private fun getMoonDogTips(phaseIndex: Int): String = when (phaseIndex) {
        0 -> getString(R.string.weather_moon_dogtips_0)
        1 -> getString(R.string.weather_moon_dogtips_1)
        2 -> getString(R.string.weather_moon_dogtips_2)
        3 -> getString(R.string.weather_moon_dogtips_3)
        4 -> getString(R.string.weather_moon_dogtips_4)
        5 -> getString(R.string.weather_moon_dogtips_5)
        6 -> getString(R.string.weather_moon_dogtips_6)
        else -> getString(R.string.weather_moon_dogtips_7)
    }

    private fun getMoonZodiacSign(index: Int): String {
        val signs = listOf(
            R.string.weather_zodiac_0 to R.string.weather_zodiac_0_desc,
            R.string.weather_zodiac_1 to R.string.weather_zodiac_1_desc,
            R.string.weather_zodiac_2 to R.string.weather_zodiac_2_desc,
            R.string.weather_zodiac_3 to R.string.weather_zodiac_3_desc,
            R.string.weather_zodiac_4 to R.string.weather_zodiac_4_desc,
            R.string.weather_zodiac_5 to R.string.weather_zodiac_5_desc,
            R.string.weather_zodiac_6 to R.string.weather_zodiac_6_desc,
            R.string.weather_zodiac_7 to R.string.weather_zodiac_7_desc,
            R.string.weather_zodiac_8 to R.string.weather_zodiac_8_desc,
            R.string.weather_zodiac_9 to R.string.weather_zodiac_9_desc,
            R.string.weather_zodiac_10 to R.string.weather_zodiac_10_desc,
            R.string.weather_zodiac_11 to R.string.weather_zodiac_11_desc
        )
        val (signRes, descRes) = signs[index]
        return getString(R.string.weather_moon_zodiac_line, getString(signRes), getString(descRes))
    }

    // ════════════════════════════════════════════════════════════════
    // WINDCHILL & BEAUFORT
    // ════════════════════════════════════════════════════════════════

    private fun calculateWindchill(temp: Double, windKmh: Double): Double {
        if (temp >= 10.0 || windKmh < 4.8) return temp
        return 13.12 + 0.6215 * temp - 11.37 * windKmh.pow(0.16) + 0.3965 * temp * windKmh.pow(0.16)
    }

    private fun getBeaufortScale(windKmh: Double): Int = when {
        windKmh < 1   -> 0
        windKmh < 6   -> 1
        windKmh < 12  -> 2
        windKmh < 20  -> 3
        windKmh < 29  -> 4
        windKmh < 39  -> 5
        windKmh < 50  -> 6
        windKmh < 62  -> 7
        windKmh < 75  -> 8
        windKmh < 89  -> 9
        windKmh < 103 -> 10
        windKmh < 118 -> 11
        else          -> 12
    }

    private fun getBeaufortDescription(beaufort: Int): String = when (beaufort) {
        0  -> getString(R.string.weather_beaufort_0)
        1  -> getString(R.string.weather_beaufort_1)
        2  -> getString(R.string.weather_beaufort_2)
        3  -> getString(R.string.weather_beaufort_3)
        4  -> getString(R.string.weather_beaufort_4)
        5  -> getString(R.string.weather_beaufort_5)
        6  -> getString(R.string.weather_beaufort_6)
        7  -> getString(R.string.weather_beaufort_7)
        8  -> getString(R.string.weather_beaufort_8)
        9  -> getString(R.string.weather_beaufort_9)
        10 -> getString(R.string.weather_beaufort_10)
        11 -> getString(R.string.weather_beaufort_11)
        12 -> getString(R.string.weather_beaufort_12)
        else -> getString(R.string.weather_unknown)
    }

    private fun getBeaufortSymbol(bf: Int): String = when (bf) {
        0  -> getString(R.string.weather_beaufort_symbol_0)
        1  -> getString(R.string.weather_beaufort_symbol_1)
        2  -> getString(R.string.weather_beaufort_symbol_2)
        3  -> getString(R.string.weather_beaufort_symbol_3)
        4  -> getString(R.string.weather_beaufort_symbol_4)
        5  -> getString(R.string.weather_beaufort_symbol_5)
        6  -> getString(R.string.weather_beaufort_symbol_6)
        7  -> getString(R.string.weather_beaufort_symbol_7)
        8  -> getString(R.string.weather_beaufort_symbol_8)
        9  -> getString(R.string.weather_beaufort_symbol_9)
        10 -> getString(R.string.weather_beaufort_symbol_10)
        11 -> getString(R.string.weather_beaufort_symbol_11)
        12 -> getString(R.string.weather_beaufort_symbol_12)
        else -> ""
    }

    private fun getWindDirection(degrees: Double): String = when {
        degrees >= 337.5 || degrees < 22.5  -> getString(R.string.weather_wind_dir_n)
        degrees >= 22.5  && degrees < 67.5  -> getString(R.string.weather_wind_dir_ne)
        degrees >= 67.5  && degrees < 112.5 -> getString(R.string.weather_wind_dir_e)
        degrees >= 112.5 && degrees < 157.5 -> getString(R.string.weather_wind_dir_se)
        degrees >= 157.5 && degrees < 202.5 -> getString(R.string.weather_wind_dir_s)
        degrees >= 202.5 && degrees < 247.5 -> getString(R.string.weather_wind_dir_sw)
        degrees >= 247.5 && degrees < 292.5 -> getString(R.string.weather_wind_dir_w)
        degrees >= 292.5 && degrees < 337.5 -> getString(R.string.weather_wind_dir_nw)
        else -> ""
    }

    // ════════════════════════════════════════════════════════════════
    // WETTERBESCHREIBUNG
    // ════════════════════════════════════════════════════════════════

    private fun getWeatherDescription(code: Int): Pair<String, String> {
        android.util.Log.d("WeatherActivity", "🌤️ Weather Code: $code")
        return when (code) {
            0    -> getString(R.string.weather_desc_clear)                    to "☀️"
            1    -> getString(R.string.weather_desc_mostly_clear)             to "🌤️"
            2    -> getString(R.string.weather_desc_partly_cloudy)            to "⛅"
            3    -> getString(R.string.weather_desc_cloudy)                   to "☁️"
            45   -> getString(R.string.weather_desc_fog)                      to "🌫️"
            48   -> getString(R.string.weather_desc_freezing_fog)             to "🌫️"
            51   -> getString(R.string.weather_desc_light_drizzle)            to "🌦️"
            53   -> getString(R.string.weather_desc_moderate_drizzle)         to "🌦️"
            55   -> getString(R.string.weather_desc_heavy_drizzle)            to "🌦️"
            56   -> getString(R.string.weather_desc_freezing_light_drizzle)   to "🌧️"
            57   -> getString(R.string.weather_desc_freezing_heavy_drizzle)   to "🌧️"
            61   -> getString(R.string.weather_desc_light_rain)               to "🌧️"
            63   -> getString(R.string.weather_desc_moderate_rain)            to "🌧️"
            65   -> getString(R.string.weather_desc_heavy_rain)               to "🌧️"
            66   -> getString(R.string.weather_desc_freezing_light_rain)      to "🌧️"
            67   -> getString(R.string.weather_desc_freezing_heavy_rain)      to "🌧️"
            71   -> getString(R.string.weather_desc_light_snow)               to "🌨️"
            73   -> getString(R.string.weather_desc_moderate_snow)            to "🌨️"
            75   -> getString(R.string.weather_desc_heavy_snow)               to "🌨️"
            77   -> getString(R.string.weather_desc_snow_grains)              to "🌨️"
            80   -> getString(R.string.weather_desc_light_showers)            to "🌦️"
            81   -> getString(R.string.weather_desc_moderate_showers)         to "🌦️"
            82   -> getString(R.string.weather_desc_heavy_showers)            to "🌦️"
            85   -> getString(R.string.weather_desc_light_snow_showers)       to "🌨️"
            86   -> getString(R.string.weather_desc_heavy_snow_showers)       to "🌨️"
            95   -> getString(R.string.weather_desc_thunderstorm)             to "⛈️"
            96   -> getString(R.string.weather_desc_thunderstorm_light_hail)  to "⛈️"
            99   -> getString(R.string.weather_desc_thunderstorm_heavy_hail)  to "⛈️"
            else -> {
                android.util.Log.w("WeatherActivity", "⚠️ Unbekannter Weather Code: $code")
                getString(R.string.weather_desc_code_unknown, code) to "🌡️"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ════════════════════════════════════════════════════════════════
    // SONNENZEITEN
    // ════════════════════════════════════════════════════════════════

    private fun displaySunriseSunset(data: JSONObject) {
        try {
            val daily = data.getJSONObject("daily")
            val sunriseArray = daily.getJSONArray("sunrise")
            val sunsetArray  = daily.getJSONArray("sunset")
            if (sunriseArray.length() > 1 && sunsetArray.length() > 1) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val sunrise = dateFormat.parse(sunriseArray.getString(1))
                val sunset  = dateFormat.parse(sunsetArray.getString(1))
                if (sunrise != null && sunset != null) {
                    binding.sunriseText.text = getString(R.string.weather_sunrise_display, timeFormat.format(sunrise))
                    binding.sunsetText.text  = getString(R.string.weather_sunset_display, timeFormat.format(sunset))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler beim Laden Sonnenzeiten", e)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // TAGES-ÜBERSICHT (Min/Max + Wetterwarnung-Badge)
    // ════════════════════════════════════════════════════════════════

    private fun displayDailyOverview(data: JSONObject) {
        try {
            val daily = data.getJSONObject("daily")
            val minTemps   = daily.getJSONArray("temperature_2m_min")
            val maxTemps   = daily.getJSONArray("temperature_2m_max")
            val weatherCodes = daily.getJSONArray("weather_code")
            val precipProbs  = daily.getJSONArray("precipitation_probability_max")
            if (minTemps.length() > 1) {
                val minTemp    = minTemps.getDouble(1)
                val maxTemp    = maxTemps.getDouble(1)
                val code       = weatherCodes.getInt(1)
                val precipProb = precipProbs.getInt(1)
                binding.dailyMinMaxText.text = getString(R.string.weather_daily_min_max_display, minTemp, maxTemp)
                val warningBadge = getWeatherWarningBadge(code, precipProb)
                binding.warningBadgeText.text = warningBadge
                binding.warningBadgeText.visibility = View.VISIBLE
                when {
                    warningBadge.contains("⚠️") || warningBadge.contains("❄️") || warningBadge.contains("🌧️") ->
                        binding.warningBadgeText.setBackgroundResource(R.drawable.warning_badge_bg)
                    warningBadge.contains("☔") || warningBadge.contains("🌦️") || warningBadge.contains("🌂") ->
                        binding.warningBadgeText.setBackgroundResource(R.drawable.info_badge_bg)
                    else ->
                        binding.warningBadgeText.setBackgroundResource(R.drawable.good_badge_bg)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei Tages-Übersicht", e)
        }
    }

    private fun getWeatherWarningBadge(code: Int, precipProb: Int): String = when {
        code >= 95               -> getString(R.string.weather_badge_thunderstorm)
        code >= 85               -> getString(R.string.weather_badge_snow_showers)
        code >= 80               -> getString(R.string.weather_badge_showers)
        code >= 71               -> getString(R.string.weather_badge_snow)
        code >= 61 && precipProb >= 70 -> getString(R.string.weather_badge_heavy_rain)
        precipProb >= 80         -> getString(R.string.weather_badge_high_precip)
        precipProb >= 50         -> getString(R.string.weather_badge_possible_rain, precipProb)
        code >= 45               -> getString(R.string.weather_badge_fog)
        code >= 3                -> getString(R.string.weather_badge_cloudy)
        else                     -> getString(R.string.weather_badge_none)
    }

    // ════════════════════════════════════════════════════════════════
    // 24h-VORHERSAGE
    // ════════════════════════════════════════════════════════════════

    private fun display24HourForecast(data: JSONObject) {
        val hourly = data.getJSONObject("hourly")
        val times        = hourly.getJSONArray("time")
        val temperatures = hourly.getJSONArray("temperature_2m")
        val weatherCodes = hourly.getJSONArray("weather_code")
        val precipProb   = hourly.getJSONArray("precipitation_probability")
        val feelsLike    = hourly.getJSONArray("apparent_temperature")
        val forecastItems = mutableListOf<ForecastItem>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        val oneHour = 3600000L
        var count = 0
        for (i in 0 until times.length()) {
            if (count >= 24) break
            try {
                val date = dateFormat.parse(times.getString(i))
                if (date != null && date.time >= now - oneHour) {
                    forecastItems.add(ForecastItem(
                        time        = timeFormat.format(date),
                        temperature = temperatures.getDouble(i),
                        weatherCode = weatherCodes.getInt(i),
                        precipitation = if (i < precipProb.length()) precipProb.getDouble(i) else 0.0,
                        feelsLike   = if (i < feelsLike.length()) feelsLike.getDouble(i) else temperatures.getDouble(i)
                    ))
                    count++
                }
            } catch (e: Exception) {
                android.util.Log.e("WeatherActivity", "Fehler bei Vorhersage", e)
            }
        }
        binding.forecastRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.forecastRecyclerView.adapter = ForecastAdapter(forecastItems) { showForecastDetail(it) }
    }

    private fun showForecastDetail(item: ForecastItem) {
        val (description, icon) = getWeatherDescription(item.weatherCode)
        val message = getString(
            R.string.weather_forecast_item_detail_message,
            item.time,
            icon,
            description,
            String.format(Locale.getDefault(), "%.1f°C", item.temperature),
            String.format(Locale.getDefault(), "%.1f°C", item.feelsLike),
            item.precipitation.toInt()
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.weather_forecast_item_detail_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .create().apply {
                show()
            }
    }

    // ════════════════════════════════════════════════════════════════
    // TRAININGSWETTER-INDEX (Pfotenwetter)
    // ════════════════════════════════════════════════════════════════

    private fun displayPawWeather(data: JSONObject, windSpeed: Double, windGusts: Double) {
        val current = data.getJSONObject("current")
        val temperature  = current.getDouble("temperature_2m")
        val feelsLike    = current.getDouble("apparent_temperature")
        val weatherCode  = current.getInt("weather_code")
        val uvIndex      = current.optDouble("uv_index", 0.0)   // ohne UV-Daten keine UV-Warnung
        val precipitation = current.getDouble("precipitation")
        var pawResult = calculatePawWeatherIndex(temperature, feelsLike, weatherCode, uvIndex, precipitation)
        pawResult = addWindWarning(pawResult, windSpeed, windGusts)
        binding.pawWeatherIndexText.text = pawResult.warnings.joinToString("\n\n")
        binding.pawWeatherIndexText.setTextColor(pawResult.severity.getColor())
        val groomingTips = getGroomingTips(weatherCode, precipitation)
        if (groomingTips.isNotEmpty()) {
            binding.groomingTipsText.text = "\n" + groomingTips.joinToString("\n")
            binding.groomingTipsText.visibility = View.VISIBLE
        } else {
            binding.groomingTipsText.visibility = View.GONE
        }
        if (temperature >= 25 || temperature <= 5) {
            try {
                val hourly = data.getJSONObject("hourly")
                val temps  = hourly.getJSONArray("temperature_2m")
                val times  = hourly.getJSONArray("time")
                val now = System.currentTimeMillis()
                val oneHour = 3600000L
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                var startIndex = 0
                for (i in 0 until times.length()) {
                    val date = dateFormat.parse(times.getString(i))
                    if (date != null && date.time >= now - oneHour) { startIndex = i; break }
                }
                val endIndex = (startIndex + 24).coerceAtMost(temps.length())
                val hourlyTemps = (startIndex until endIndex).map { temps.getDouble(it) }
                val hourlyTimes = (startIndex until endIndex).map {
                    dateFormat.parse(times.getString(it))?.let { d -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(d) } ?: ""
                }
                val recommendations = recommendWalkingTimes(temperature, hourlyTemps, hourlyTimes)
                binding.walkingTimesText.text = "\n" + recommendations.joinToString("\n")
                binding.walkingTimesText.visibility = View.VISIBLE
            } catch (e: Exception) {
                android.util.Log.e("WeatherActivity", "Fehler bei Trainings-Zeiten", e)
                binding.walkingTimesText.visibility = View.GONE
            }
        } else {
            binding.walkingTimesText.visibility = View.GONE
        }
    }

    private fun calculatePawWeatherIndex(temperature: Double, feelsLike: Double, weatherCode: Int, uvIndex: Double, precipitation: Double): PawWeatherResult {
        val warnings = mutableListOf<String>()
        var severity = PawWeatherSeverity.GOOD
        when {
            temperature >= 35 -> { warnings.add(getString(R.string.weather_paw_hot_extreme)); severity = PawWeatherSeverity.CRITICAL }
            temperature >= 30 -> { warnings.add(getString(R.string.weather_paw_hot_warning)); severity = maxOf(severity, PawWeatherSeverity.WARNING) }
            temperature >= 25 -> { warnings.add(getString(R.string.weather_paw_hot_caution)); severity = maxOf(severity, PawWeatherSeverity.CAUTION) }
        }
        when {
            temperature <= -10 -> { warnings.add(getString(R.string.weather_paw_cold_extreme)); severity = maxOf(severity, PawWeatherSeverity.CRITICAL) }
            temperature <= -5  -> { warnings.add(getString(R.string.weather_paw_cold_warning)); severity = maxOf(severity, PawWeatherSeverity.WARNING) }
            temperature <= 0   -> { warnings.add(getString(R.string.weather_paw_cold_caution)); severity = maxOf(severity, PawWeatherSeverity.CAUTION) }
        }
        if (temperature in 7.0..25.0 && precipitation < 5.0) { warnings.add(getString(R.string.weather_paw_tick_risk)); severity = maxOf(severity, PawWeatherSeverity.INFO) }
        if (uvIndex >= 6.0) { warnings.add(getString(R.string.weather_paw_uv_high)); severity = maxOf(severity, PawWeatherSeverity.INFO) }
        if (weatherCode >= 95) { warnings.add(getString(R.string.weather_paw_thunderstorm)); severity = maxOf(severity, PawWeatherSeverity.WARNING) }
        if (precipitation >= 10.0 || weatherCode in 80..82) { warnings.add(getString(R.string.weather_paw_heavy_rain)); severity = maxOf(severity, PawWeatherSeverity.CAUTION) }
        if (warnings.isEmpty()) { warnings.add(getString(R.string.weather_paw_good)); severity = PawWeatherSeverity.GOOD }
        return PawWeatherResult(warnings, severity)
    }

    private fun addWindWarning(result: PawWeatherResult, windSpeed: Double, windGusts: Double): PawWeatherResult {
        val warnings = result.warnings.toMutableList()
        var severity = result.severity
        when {
            windGusts >= 75 -> { warnings.add(getString(R.string.weather_paw_storm)); severity = maxOf(severity, PawWeatherSeverity.CRITICAL) }
            windGusts >= 50 -> { warnings.add(getString(R.string.weather_paw_strong_wind)); severity = maxOf(severity, PawWeatherSeverity.WARNING) }
            windSpeed >= 40 -> { warnings.add(getString(R.string.weather_paw_windy)); severity = maxOf(severity, PawWeatherSeverity.INFO) }
        }
        return PawWeatherResult(warnings, severity)
    }

    private fun recommendWalkingTimes(currentTemp: Double, hourlyTemps: List<Double>, hourlyTimes: List<String>): List<String> {
        val recommendations = mutableListOf<String>()

        // Nur Zeiten zwischen 06:00 und 22:00 Uhr berücksichtigen (Nachtstunden ausschließen)
        val reasonableIndices = hourlyTimes.indices.filter { i ->
            val hour = hourlyTimes[i].substringBefore(":").toIntOrNull() ?: -1
            hour in 6..21
        }

        if (currentTemp >= 25) {
            val coolestHours = reasonableIndices.map { it to hourlyTemps[it] }
                .sortedBy { it.second }
                .take(3)
                .map { hourlyTimes[it.first] }

            if (coolestHours.isNotEmpty()) {
                recommendations.add(getString(R.string.weather_best_times_heat_header))
                coolestHours.forEach { recommendations.add(getString(R.string.weather_best_times_coolest, it)) }
            }
        }
        if (currentTemp <= 5) {
            val warmestHours = reasonableIndices.map { it to hourlyTemps[it] }
                .sortedByDescending { it.second }
                .take(3)
                .map { hourlyTimes[it.first] }

            if (warmestHours.isNotEmpty()) {
                recommendations.add(getString(R.string.weather_best_times_cold_header))
                warmestHours.forEach { recommendations.add(getString(R.string.weather_best_times_warmest, it)) }
            }
        }
        return recommendations
    }

    private fun getGroomingTips(weatherCode: Int, precipitation: Double): List<String> {
        val tips = mutableListOf<String>()
        when {
            weatherCode in 61..67 || weatherCode in 80..82 -> { tips.add(getString(R.string.weather_grooming_dry_paws)); tips.add(getString(R.string.weather_grooming_check_ears)) }
            weatherCode in 71..77 || weatherCode in 85..86 -> { tips.add(getString(R.string.weather_grooming_ice_between_toes)); tips.add(getString(R.string.weather_grooming_paw_balm)) }
            precipitation >= 5.0 -> tips.add(getString(R.string.weather_grooming_muddy_paws))
            precipitation == 0.0 && weatherCode <= 3 -> tips.add(getString(R.string.weather_grooming_dog_friendly))
        }
        return tips
    }

    data class PawWeatherResult(val warnings: List<String>, val severity: PawWeatherSeverity)

    enum class PawWeatherSeverity {
        GOOD, INFO, CAUTION, WARNING, CRITICAL;
        fun getColor(): Int = when (this) {
            GOOD     -> Color.parseColor("#4CAF50")
            INFO     -> Color.parseColor("#2196F3")
            CAUTION  -> Color.parseColor("#FFC107")
            WARNING  -> Color.parseColor("#FF9800")
            CRITICAL -> Color.parseColor("#FF6F60")
        }
    }

    // ════════════════════════════════════════════════════════════════
    // TEMPERATUR-CHART
    // ════════════════════════════════════════════════════════════════

    private fun displayTemperatureChart(data: JSONObject) {
        try {
            val hourly = data.getJSONObject("hourly")
            val times        = hourly.getJSONArray("time")
            val temperatures = hourly.getJSONArray("temperature_2m")
            val temps  = mutableListOf<Double>()
            val labels = mutableListOf<String>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now = System.currentTimeMillis(); val oneHour = 3600000L
            var startIndex = 0
            for (i in 0 until times.length()) { val date = dateFormat.parse(times.getString(i)); if (date != null && date.time >= now - oneHour) { startIndex = i; break } }
            val limit = (startIndex + 24).coerceAtMost(temperatures.length())
            for (i in startIndex until limit) {
                temps.add(temperatures.getDouble(i))
                labels.add(dateFormat.parse(times.getString(i))?.let { timeFormat.format(it) } ?: "")
            }
            binding.temperatureChart.setData(temps, labels)
        } catch (e: Exception) { android.util.Log.e("WeatherActivity", "Fehler bei Temperatur-Chart", e) }
    }

    // ════════════════════════════════════════════════════════════════
    // NIEDERSCHLAGS-CHART
    // ════════════════════════════════════════════════════════════════

    private fun displayPrecipitationChart(data: JSONObject) {
        try {
            val hourly = data.getJSONObject("hourly")
            val times             = hourly.getJSONArray("time")
            val precipitationArray = hourly.getJSONArray("precipitation")
            val precipValues = mutableListOf<Double>()
            val labels       = mutableListOf<String>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now = System.currentTimeMillis(); val oneHour = 3600000L
            var startIndex = 0
            for (i in 0 until times.length()) { val date = dateFormat.parse(times.getString(i)); if (date != null && date.time >= now - oneHour) { startIndex = i; break } }
            val limit = (startIndex + 24).coerceAtMost(precipitationArray.length())
            for (i in startIndex until limit) {
                precipValues.add(precipitationArray.getDouble(i))
                labels.add(dateFormat.parse(times.getString(i))?.let { timeFormat.format(it) } ?: "")
            }
            binding.precipitationChart.setData(precipValues, labels)
        } catch (e: Exception) { android.util.Log.e("WeatherActivity", "Fehler bei Niederschlags-Chart", e) }
    }

    // ════════════════════════════════════════════════════════════════
    // TAGESLÄNGE & DÄMMERUNG
    // ════════════════════════════════════════════════════════════════

    private fun displayDaylightInfo(data: JSONObject) {
        try {
            val daily = data.getJSONObject("daily")
            val sunriseArray = daily.getJSONArray("sunrise")
            val sunsetArray  = daily.getJSONArray("sunset")
            if (sunriseArray.length() >= 2 && sunsetArray.length() >= 2) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val yesterdaySunrise = dateFormat.parse(sunriseArray.getString(0))
                val yesterdaySunset  = dateFormat.parse(sunsetArray.getString(0))
                val sunrise = dateFormat.parse(sunriseArray.getString(1))
                val sunset  = dateFormat.parse(sunsetArray.getString(1))
                if (sunrise != null && sunset != null) {
                    val daylightMillis = sunset.time - sunrise.time
                    val hours   = TimeUnit.MILLISECONDS.toHours(daylightMillis)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(daylightMillis) % 60
                    binding.daylightDurationText.text = getString(R.string.weather_daylight_duration, hours, minutes)
                    val civilTwilightDuration = 35 * 60 * 1000L
                    binding.dawnText.text = getString(R.string.weather_dawn, timeFormat.format(Date(sunrise.time - civilTwilightDuration)))
                    binding.duskText.text = getString(R.string.weather_dusk, timeFormat.format(Date(sunset.time + civilTwilightDuration)))
                    if (yesterdaySunrise != null && yesterdaySunset != null) {
                        val yesterdayDaylight = yesterdaySunset.time - yesterdaySunrise.time
                        val changeMinutes = TimeUnit.MILLISECONDS.toMinutes(daylightMillis - yesterdayDaylight)
                        val trend = when {
                            changeMinutes > 0 -> getString(R.string.weather_daylight_trend_longer, changeMinutes)
                            changeMinutes < 0 -> getString(R.string.weather_daylight_trend_shorter, -changeMinutes)
                            else -> getString(R.string.weather_daylight_trend_same)
                        }
                        binding.daylightTrendText.text = getString(R.string.weather_daylight_trend_prefix, trend)
                        binding.daylightTrendText.visibility = View.VISIBLE
                    }
                    displayLightBasedRecommendation(sunrise, sunset)
                    displayGoldenHour(sunrise, sunset)
                }
            }
        } catch (e: Exception) { android.util.Log.e("WeatherActivity", "Fehler bei Tageslänge", e) }
    }

    private fun displayLightBasedRecommendation(sunrise: Date, sunset: Date) {
        val now = Date()
        val recommendations = mutableListOf<String>()
        if (now.before(sunrise)) {
            val min = TimeUnit.MILLISECONDS.toMinutes(sunrise.time - now.time)
            recommendations.add(if (min < 60) getString(R.string.weather_light_sunrise_soon, min) else getString(R.string.weather_light_still_dark_morning))
        }
        if (now.before(sunset)) {
            val min = TimeUnit.MILLISECONDS.toMinutes(sunset.time - now.time)
            if (min < 120) recommendations.add(getString(R.string.weather_light_sunset_soon, min))
        } else {
            recommendations.add(getString(R.string.weather_light_dark_evening))
        }
        if (recommendations.isNotEmpty()) {
            binding.lightRecommendationText.text = recommendations.joinToString("\n")
            binding.lightRecommendationText.visibility = View.VISIBLE
        } else {
            binding.lightRecommendationText.visibility = View.GONE
        }
    }

    private fun displayGoldenHour(sunrise: Date, sunset: Date) {
        val goldenHourDuration = 3600000L
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.goldenHourText.text = getString(
            R.string.weather_golden_hour,
            timeFormat.format(sunrise), timeFormat.format(Date(sunrise.time + goldenHourDuration)),
            timeFormat.format(Date(sunset.time - goldenHourDuration)), timeFormat.format(sunset)
        )
        binding.goldenHourText.visibility = View.VISIBLE
    }

    // ════════════════════════════════════════════════════════════════
    // NEUE DATA CLASSES – 7-Tage, Wind stündlich, Luftqualität, Pollen
    // ════════════════════════════════════════════════════════════════

    data class DailyForecastItem(
        val dayLabel: String,          // z.B. "Mo", "Di"
        val dateLabel: String,         // z.B. "16.06."
        val weatherCode: Int,
        val tempMin: Double,
        val tempMax: Double,
        val precipProbability: Int,
        val windSpeedMax: Double,
        val windGustsMax: Double,
        val uvIndexMax: Double,
        val sunshineDurationMin: Double  // in Minuten (API liefert Sekunden → umrechnen)
    )

    data class WindForecastItem(
        val time: String,
        val windSpeed: Double,         // km/h
        val windGusts: Double,         // km/h
        val windDegree: Double,        // 0–360°
        val beaufort: Int
    )

    data class AirQualityHourItem(
        val time: String,
        val pm25: Double,
        val pm10: Double,
        val no2: Double,
        val o3: Double,
        val aqi: Int,                  // berechneter Gesamt-Index (0–5)
        val aqiLabel: String,
        val aqiColor: Int
    )

    data class PollenItem(
        val key: String,               // stabiler, sprachneutraler Bezeichner, z.B. "birch_pollen"
        val name: String,              // Anzeigename, z.B. "Birke" / "Birch"
        val emoji: String,
        val value: Double,             // Grains/m³
        val level: Int,               // 0=keine, 1=niedrig, 2=mittel, 3=hoch, 4=sehr hoch
        val levelLabel: String,
        val levelColor: Int
    )

    // ════════════════════════════════════════════════════════════════
    // HILFSFUNKTIONEN – AQI-Berechnung (nach EU-Norm)
    // ════════════════════════════════════════════════════════════════

    /** Berechnet vereinfachten AQI-Level 0–5 aus PM2.5 (µg/m³) nach WHO/EU-Schwellen */
    private fun calcAqiFromPm25(pm25: Double): Int = when {
        pm25 < 10  -> 0  // Gut
        pm25 < 20  -> 1  // Mäßig
        pm25 < 25  -> 2  // Ungesund für Sensible
        pm25 < 50  -> 3  // Ungesund
        pm25 < 75  -> 4  // Sehr ungesund
        else       -> 5  // Gefährlich
    }

    private fun aqiLevelLabel(level: Int): String = when (level) {
        0 -> getString(R.string.weather_aqi_good)
        1 -> getString(R.string.weather_aqi_moderate)
        2 -> getString(R.string.weather_aqi_unhealthy_sensitive)
        3 -> getString(R.string.weather_aqi_unhealthy)
        4 -> getString(R.string.weather_aqi_very_unhealthy)
        else -> getString(R.string.weather_aqi_hazardous)
    }

    private fun aqiLevelColor(level: Int): Int = when (level) {
        0 -> android.graphics.Color.parseColor("#4CAF50")   // grün
        1 -> android.graphics.Color.parseColor("#8BC34A")   // hellgrün
        2 -> android.graphics.Color.parseColor("#FFC107")   // gelb
        3 -> android.graphics.Color.parseColor("#FF9800")   // orange
        4 -> android.graphics.Color.parseColor("#FF6F60")   // rot
        else -> android.graphics.Color.parseColor("#CE93D8") // lila
    }

    /** Pollen-Konzentration in Level 0–4 (Grains/m³, nach DWD-Skala) */
    private fun pollenLevel(value: Double): Int = when {
        value < 1   -> 0  // keine/vernachlässigbar
        value < 10  -> 1  // niedrig
        value < 50  -> 2  // mittel
        value < 200 -> 3  // hoch
        else        -> 4  // sehr hoch
    }

    private fun pollenLevelLabel(level: Int): String = when (level) {
        0 -> getString(R.string.weather_pollen_none)
        1 -> getString(R.string.weather_pollen_low)
        2 -> getString(R.string.weather_pollen_medium)
        3 -> getString(R.string.weather_pollen_high)
        else -> getString(R.string.weather_pollen_very_high)
    }

    private fun pollenLevelColor(level: Int): Int = when (level) {
        0 -> android.graphics.Color.parseColor("#4CAF50")
        1 -> android.graphics.Color.parseColor("#8BC34A")
        2 -> android.graphics.Color.parseColor("#FFC107")
        3 -> android.graphics.Color.parseColor("#FF9800")
        else -> android.graphics.Color.parseColor("#FF6F60")
    }

    // ════════════════════════════════════════════════════════════════
    // STUBS – werden in den nächsten Schritten implementiert
    // ════════════════════════════════════════════════════════════════

    private fun display7DayForecast(data: JSONObject) {
        try {
            val daily      = data.getJSONObject("daily")
            val times      = daily.getJSONArray("time")
            val minTemps   = daily.getJSONArray("temperature_2m_min")
            val maxTemps   = daily.getJSONArray("temperature_2m_max")
            val codes      = daily.getJSONArray("weather_code")
            val precipProb = daily.getJSONArray("precipitation_probability_max")
            val windMax    = daily.getJSONArray("wind_speed_10m_max")
            val gustsMax   = daily.getJSONArray("wind_gusts_10m_max")
            val uvMax      = daily.optJSONArray("uv_index_max")
            val sunshine   = daily.optJSONArray("sunshine_duration") // Sekunden

            val dayNames = listOf(
                getString(R.string.day_sun), getString(R.string.day_mon), getString(R.string.day_tue),
                getString(R.string.day_wed), getString(R.string.day_thu), getString(R.string.day_fri), getString(R.string.day_sat)
            )
            val dateFmt  = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val labelFmt = SimpleDateFormat("dd.MM.", Locale.getDefault())

            val items = mutableListOf<DailyForecastItem>()
            // Index 0 = gestern (past_days=1) → überspringen; Index 1 = heute
            val startIndex = 1
            val endIndex   = times.length().coerceAtMost(startIndex + 7)

            for (i in startIndex until endIndex) {
                val date = dateFmt.parse(times.getString(i)) ?: continue
                val cal  = Calendar.getInstance().also { it.time = date }
                items.add(DailyForecastItem(
                    dayLabel          = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1],
                    dateLabel         = labelFmt.format(date),
                    weatherCode       = codes.getInt(i),
                    tempMin           = minTemps.getDouble(i),
                    tempMax           = maxTemps.getDouble(i),
                    precipProbability = precipProb.getInt(i),
                    windSpeedMax      = windMax.getDouble(i),
                    windGustsMax      = gustsMax.getDouble(i),
                    uvIndexMax        = uvMax?.optDouble(i, Double.NaN) ?: Double.NaN,
                    sunshineDurationMin = (sunshine?.optDouble(i, Double.NaN) ?: Double.NaN) / 60.0
                ))
            }

            binding.dailyForecastRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.dailyForecastRecyclerView.adapter =
                DailyForecastAdapter(items) { showDailyForecastDetail(it) }

        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei 7-Tage-Vorhersage", e)
        }
    }

    private fun showDailyForecastDetail(item: DailyForecastItem) {
        val (description, icon) = getWeatherDescription(item.weatherCode)
        val beaufort = getBeaufortScale(item.windSpeedMax)
        val hasUv = !item.uvIndexMax.isNaN()
        val hasSunshine = !item.sunshineDurationMin.isNaN()
        val uvWarning = when {
            item.uvIndexMax >= 8 -> "☀️ Sehr hohe UV-Strahlung – Schatten bevorzugen!"
            item.uvIndexMax >= 6 -> "⚡ Hohe UV-Strahlung – Schutz empfohlen"
            item.uvIndexMax >= 3 -> "🌤️ Moderate UV-Strahlung"
            else                 -> "✅ Niedrige UV-Strahlung"
        }
        val sunshineh = if (hasSunshine) (item.sunshineDurationMin / 60).toInt() else 0
        val sunshinem = if (hasSunshine) (item.sunshineDurationMin % 60).toInt() else 0

        val message = buildString {
            append("${item.dayLabel}, ${item.dateLabel}\n\n")
            append("$icon $description\n\n")
            append(getString(R.string.weather_forecast_temp_range, item.tempMax, item.tempMin)).append("\n")
            append(getString(R.string.weather_forecast_precip_prob, item.precipProbability)).append("\n")
            append(getString(R.string.weather_forecast_wind_max, item.windSpeedMax, beaufort, item.windGustsMax)).append("\n")
            if (hasSunshine) append(getString(R.string.weather_forecast_sunshine, sunshineh, sunshinem)).append("\n")
            if (hasUv) {
                append(getString(R.string.weather_forecast_uv_max, item.uvIndexMax)).append("\n")
                append("$uvWarning")
            }
        }


        AlertDialog.Builder(this)
            .setTitle(getString(R.string.weather_daily_forecast_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .create().apply {
                show()
            }
    }

    private fun showUvHourlyDetail(item: UvForecastItem) {

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.weather_uv_hourly_dialog_title, item.time, String.format(Locale.getDefault(), "%.1f", item.uvIndex), item.label))
            .setMessage(buildUvHourlyTip(item))
            .setPositiveButton(getString(R.string.ok), null)
            .create().apply {
                show()
            }
    }

    /**
     * Vollständige stündliche UV-Empfehlung:
     * Einordnung → Mensch → Hund → Schutzmaßnahmen-Zusammenfassung.
     * Bezogen ausschließlich auf den UV-Wert dieser Stunde.
     */
    private fun buildUvHourlyTip(item: UvForecastItem): String {
        val uv = item.uvIndex

        return buildString {

            // ── Kein relevanter UV ──────────────────────────────────────────
            if (uv < 1.0) {
                append(getString(R.string.weather_uv_level_0_desc)).append("\n\n")
                append(getString(R.string.weather_uv_level_0_human)).append("\n\n")
                append(getString(R.string.weather_uv_level_0_dog))
                return@buildString
            }

            val uvStr = String.format(Locale.getDefault(), "%.1f", uv)

            // ── Einordnung ──────────────────────────────────────────────────
            append(when {
                uv < 3  -> getString(R.string.weather_uv_level_1_desc, uvStr)
                uv < 6  -> getString(R.string.weather_uv_level_2_desc, uvStr)
                uv < 8  -> getString(R.string.weather_uv_level_3_desc, uvStr)
                uv < 11 -> getString(R.string.weather_uv_level_4_desc, uvStr)
                else    -> getString(R.string.weather_uv_level_5_desc, uvStr)
            })

            // ── Mensch ──────────────────────────────────────────────────────
            append(getString(R.string.weather_uv_human_header)).append("\n")
            append(when {
                uv < 3  -> getString(R.string.weather_uv_level_1_human)
                uv < 6  -> getString(R.string.weather_uv_level_2_human)
                uv < 8  -> getString(R.string.weather_uv_level_3_human)
                uv < 11 -> getString(R.string.weather_uv_level_4_human)
                else    -> getString(R.string.weather_uv_level_5_human)
            })

            append("\n\n")

            // ── Hund ────────────────────────────────────────────────────────
            append(getString(R.string.weather_uv_dog_header)).append("\n")
            append(when {
                uv < 3  -> getString(R.string.weather_uv_level_1_dog)
                uv < 6  -> getString(R.string.weather_uv_level_2_dog)
                uv < 8  -> getString(R.string.weather_uv_level_3_dog)
                uv < 11 -> getString(R.string.weather_uv_level_4_dog)
                else    -> getString(R.string.weather_uv_level_5_dog)
            })

            append("\n\n")

            // ── Schutzmaßnahmen-Zusammenfassung ────────────────────────────
            append(getString(R.string.weather_uv_protection_summary_header, item.time)).append("\n")
            append(when {
                uv < 3  -> getString(R.string.weather_uv_level_1_summary)
                uv < 6  -> getString(R.string.weather_uv_level_2_summary)
                uv < 8  -> getString(R.string.weather_uv_level_3_summary)
                uv < 11 -> getString(R.string.weather_uv_level_4_summary)
                else    -> getString(R.string.weather_uv_level_5_summary)
            })

            append("\n\n").append(getString(R.string.weather_uv_hourly_vet_disclaimer))
        }
    }



    private fun displayWindForecast(data: JSONObject) {
        try {
            val hourly    = data.getJSONObject("hourly")
            val times     = hourly.getJSONArray("time")
            val speeds    = hourly.getJSONArray("wind_speed_10m")
            val gusts     = hourly.getJSONArray("wind_gusts_10m")
            val degrees   = hourly.getJSONArray("wind_direction_10m")

            val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now     = System.currentTimeMillis()
            val oneHour = 3600000L

            val items = mutableListOf<WindForecastItem>()
            var count = 0
            for (i in 0 until times.length()) {
                if (count >= 24) break
                val date = dateFmt.parse(times.getString(i)) ?: continue
                if (date.time < now - oneHour) continue
                val speed = speeds.getDouble(i)
                items.add(WindForecastItem(
                    time       = timeFmt.format(date),
                    windSpeed  = speed,
                    windGusts  = gusts.getDouble(i),
                    windDegree = degrees.getDouble(i),
                    beaufort   = getBeaufortScale(speed)
                ))
                count++
            }

            binding.windForecastRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.windForecastRecyclerView.adapter = WindForecastAdapter(items)

        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei Wind-Vorhersage", e)
        }
    }

    private fun displaySunCard(data: JSONObject, lat: Double, lon: Double) {
        try {
            val daily      = data.getJSONObject("daily")
            val times      = daily.getJSONArray("time")
            val sunriseArr = daily.getJSONArray("sunrise")
            val sunsetArr  = daily.getJSONArray("sunset")
            val sunshine   = daily.optJSONArray("sunshine_duration") // Sekunden, nicht bei jedem Anbieter

            val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

            // Index 1 = heute (Index 0 = gestern durch past_days=1)
            val todaySunrise = dateFmt.parse(sunriseArr.getString(1)) ?: return
            val todaySunset  = dateFmt.parse(sunsetArr.getString(1))  ?: return

            // ── Azimut-Offset bei Auf- und Untergang ──────────────────────────
            // Berechnung: Azimut des Sonnenaufgangs aus Breitengrad + Deklination
            val sunriseAzimuth = calcSunriseAzimuth(lat, todaySunrise)
            val sunsetAzimuth  = 360.0 - sunriseAzimuth  // symmetrisch

            // Offset vom reinen Ost-Punkt (90°) bzw. West-Punkt (270°)
            val riseOffsetDeg  = Math.abs(sunriseAzimuth - 90.0)
            val riseDirection  = if (sunriseAzimuth < 90.0) getString(R.string.weather_azimuth_north_of_east) else getString(R.string.weather_azimuth_south_of_east)
            val setOffsetDeg   = Math.abs(sunsetAzimuth - 270.0)
            val setDirection   = if (sunsetAzimuth > 270.0) getString(R.string.weather_azimuth_north_of_west) else getString(R.string.weather_azimuth_south_of_west)

            binding.sunriseAzimuthText.text = getString(
                R.string.weather_sunrise_azimuth,
                timeFmt.format(todaySunrise),
                String.format(Locale.getDefault(), "%.1f", riseOffsetDeg),
                riseDirection
            )

            binding.sunsetAzimuthText.text = getString(
                R.string.weather_sunset_azimuth,
                timeFmt.format(todaySunset),
                String.format(Locale.getDefault(), "%.1f", setOffsetDeg),
                setDirection
            )

            // ── Aktueller Sonnenstand (Elevation + Azimut) ────────────────────
            val nowCal = Calendar.getInstance()
            val tzOffsetH = nowCal.timeZone.getOffset(nowCal.timeInMillis) / 3600000.0
            val solarAngles = calcSolarAngles(
                lat, lon,
                nowCal.get(Calendar.YEAR),
                nowCal.get(Calendar.MONTH) + 1,
                nowCal.get(Calendar.DAY_OF_MONTH),
                nowCal.get(Calendar.HOUR_OF_DAY),
                nowCal.get(Calendar.MINUTE),
                tzOffsetH
            )
            val elevation = solarAngles.first
            val azimuth   = solarAngles.second

            val azimuthCardinal = azimuthToCardinal(azimuth)
            val elevDesc = when {
                elevation < 0  -> getString(R.string.weather_sun_elevation_below_horizon)
                elevation < 10 -> getString(R.string.weather_sun_elevation_very_flat)
                elevation < 30 -> getString(R.string.weather_sun_elevation_low)
                elevation < 50 -> getString(R.string.weather_sun_elevation_medium)
                else           -> getString(R.string.weather_sun_elevation_high)
            }
            binding.sunElevationText.text = getString(R.string.weather_sun_elevation_azimuth_display, elevation, elevDesc, azimuth, azimuthCardinal)

            // ── Schattenlänge ──────────────────────────────────────────────────
            val prefs = getSharedPreferences("sun_shadow_prefs", android.content.Context.MODE_PRIVATE)
            val heightPerson = prefs.getFloat("height_person", 1.75f).toDouble()
            val heightDog    = prefs.getFloat("height_dog",    0.55f).toDouble()
            val heightObject = prefs.getFloat("height_object", 1.00f).toDouble()

            val shadowText = if (elevation > 0) {
                val tanEl = Math.tan(Math.toRadians(elevation))
                val shadowPerson = heightPerson / tanEl
                val shadowDog    = heightDog    / tanEl
                val shadowObject = heightObject / tanEl
                buildString {
                    append(getString(R.string.weather_shadow_person, String.format(Locale.getDefault(), "%.2f", heightPerson), String.format(Locale.getDefault(), "%.2f", shadowPerson))).append("  ")
                    append(getString(R.string.weather_shadow_dog, String.format(Locale.getDefault(), "%.2f", heightDog), String.format(Locale.getDefault(), "%.2f", shadowDog))).append("  ")
                    append(getString(R.string.weather_shadow_object, String.format(Locale.getDefault(), "%.2f", heightObject), String.format(Locale.getDefault(), "%.2f", shadowObject)))
                }
            } else {
                getString(R.string.weather_no_shadow_below_horizon)
            }
            binding.shadowLengthText.text = shadowText

            // Bearbeiten-Button für Höhenwerte
            binding.shadowEditButton.setOnClickListener {
                showShadowHeightDialog(prefs) { displaySunCard(data, lat, lon) }
            }

            // ── Sonnenscheindauer heute (nur wenn der Anbieter sie liefert) ──────
            binding.sunshineSection.visibility = if (sunshine != null) View.VISIBLE else View.GONE
            if (sunshine != null) {
            val todaySunshineSec = sunshine.getDouble(1)
            val shH = (todaySunshineSec / 3600).toInt()
            val shM = ((todaySunshineSec % 3600) / 60).toInt()
            binding.sunshineTodayText.text = getString(R.string.weather_sunshine_today_format, shH, shM)

            // ── 7-Tage Sonnenschein-Balken (inline LinearLayout) ──────────────
            val dayNames = listOf(
                getString(R.string.day_sun), getString(R.string.day_mon), getString(R.string.day_tue),
                getString(R.string.day_wed), getString(R.string.day_thu), getString(R.string.day_fri), getString(R.string.day_sat)
            )
            val dateFmtShort = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val startIdx = 1
            val endIdx   = times.length().coerceAtMost(startIdx + 7)

            // Max-Wert für Skalierung (max. 16h = 57600 Sekunden)
            val maxSec = 57600.0

            binding.sunshineBarsContainer.removeAllViews()
            for (i in startIdx until endIdx) {
                val date = dateFmtShort.parse(times.getString(i)) ?: continue
                val cal  = Calendar.getInstance().also { it.time = date }
                val dayLabel = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
                val sec  = sunshine.getDouble(i)
                val frac = (sec / maxSec).coerceIn(0.0, 1.0)

                // Jede Tagesspalte als vertikales LinearLayout
                val col = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }

                // Balken (max. 48dp hoch)
                val maxBarDp = 48
                val barHeightDp = (frac * maxBarDp).toInt().coerceAtLeast(2)
                val density = resources.displayMetrics.density
                val bar = View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        (18 * density).toInt(), (barHeightDp * density).toInt()
                    ).also { it.bottomMargin = (4 * density).toInt() }
                    val barH = (sec / 3600).toInt()
                    setBackgroundColor(when {
                        barH >= 8 -> android.graphics.Color.parseColor("#FFD54F")
                        barH >= 4 -> android.graphics.Color.parseColor("#FFA726")
                        barH >= 1 -> android.graphics.Color.parseColor("#FF7043")
                        else      -> android.graphics.Color.parseColor("#8A8A86")
                    })
                }

                // Stunden-Label über Balken
                val barH = (sec / 3600).toInt()
                val hLabel = android.widget.TextView(this).apply {
                    text = "${barH}h"
                    textSize = 9f
                    setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = (2 * density).toInt() }
                }

                // Tag-Label unter Balken
                val dLabel = android.widget.TextView(this).apply {
                    text = dayLabel
                    textSize = 10f
                    setTextColor(if (i == 1) android.graphics.Color.parseColor("#8fad6f")
                    else android.graphics.Color.parseColor("#999999"))
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (2 * density).toInt() }
                }

                col.addView(hLabel)
                col.addView(bar)
                col.addView(dLabel)
                binding.sunshineBarsContainer.addView(col)
            }
            }

            // ── UV-Index stündlich (nur wenn der Anbieter ihn liefert) ──────────
            val hourly   = data.getJSONObject("hourly")
            val hTimes   = hourly.getJSONArray("time")
            val uvValues = hourly.optJSONArray("uv_index")
            binding.uvSection.visibility = if (uvValues != null) View.VISIBLE else View.GONE
            if (uvValues != null) {
            val now      = System.currentTimeMillis()
            val oneHour  = 3600000L

            val uvItems = mutableListOf<UvForecastItem>()
            var count = 0
            var peakUv = 0.0
            for (i in 0 until hTimes.length()) {
                if (count >= 24) break
                val date = dateFmt.parse(hTimes.getString(i)) ?: continue
                if (date.time < now - oneHour) continue
                val uv = if (!uvValues.isNull(i)) uvValues.getDouble(i) else 0.0
                if (uv > peakUv) peakUv = uv
                uvItems.add(UvForecastItem(
                    time    = timeFmt.format(date),
                    uvIndex = uv,
                    label   = uvIndexLabel(uv),
                    color   = uvIndexColor(uv)
                ))
                count++
            }

            binding.uvForecastRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.uvForecastRecyclerView.adapter = UvForecastAdapter(uvItems) { showUvHourlyDetail(it) }

            // ── UV-Schutzempfehlung (Mensch + Hund) ───────────────────────────
            val protectionText = buildUvProtectionText(peakUv)
            if (protectionText.isNotEmpty()) {
                binding.uvProtectionText.text = protectionText
                binding.uvProtectionText.visibility = View.VISIBLE
            } else {
                binding.uvProtectionText.visibility = View.GONE
            }
            }

        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei Sonne-Card", e)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SOLAR-BERECHNUNGEN  (alle offline, keine API nötig)
    // ════════════════════════════════════════════════════════════════

    /**
     * Berechnet den Sonnenaufgangs-Azimut in Grad (0° = Nord, 90° = Ost).
     * Verwendet die astronomische Formel über Sonnendeklination + Breitengrad.
     */
    private fun calcSunriseAzimuth(latDeg: Double, sunriseTime: Date): Double {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).also { it.time = sunriseTime }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)

        // Sonnendeklination δ (in Grad) – vereinfachte Formel (Spencer 1971)
        val b = Math.toRadians(360.0 / 365.0 * (dayOfYear - 81))
        val declinationDeg = 23.45 * Math.sin(b)
        val decRad = Math.toRadians(declinationDeg)
        val latRad = Math.toRadians(latDeg)

        // cos(Azimut) = sin(δ) / cos(φ)
        val cosAz = Math.sin(decRad) / Math.cos(latRad)
        val cosAzClamped = cosAz.coerceIn(-1.0, 1.0)
        val azimuthFromNorth = Math.toDegrees(Math.acos(cosAzClamped))

        // Im Nordhalbkugel-Sommer (δ > 0): Aufgang nördlich von Ost → Azimut < 90°
        // Im Winter (δ < 0): Aufgang südlich von Ost → Azimut > 90°
        return if (declinationDeg >= 0) azimuthFromNorth else 180.0 - azimuthFromNorth
    }

    /**
     * Berechnet die aktuelle Sonnenhöhe (Elevation) in Grad über dem Horizont.
     * Negative Werte bedeuten Sonne unter dem Horizont.
     * @param tzOffsetH Zeitzonenversatz in Stunden (z.B. 2.0 für CEST)
     */
    /**
     * Berechnet Elevation und Azimut der Sonne für den gegebenen Zeitpunkt.
     * @return Pair(elevation°, azimuth°) – Azimut: 0°=Nord, 90°=Ost, 180°=Süd, 270°=West
     */
    private fun calcSolarAngles(
        latDeg: Double, lonDeg: Double,
        year: Int, month: Int, day: Int,
        hour: Int, minute: Int,
        tzOffsetH: Double
    ): Pair<Double, Double> {
        val dayOfYear = run {
            val cal = Calendar.getInstance()
            cal.set(year, month - 1, day)
            cal.get(Calendar.DAY_OF_YEAR)
        }

        // Sonnendeklination (Spencer 1971)
        val b = Math.toRadians(360.0 / 365.0 * (dayOfYear - 81))
        val decRad = Math.toRadians(23.45 * Math.sin(b))

        // Gleichung der Zeit (Minuten)
        val eot = 9.87 * Math.sin(2 * b) - 7.53 * Math.cos(b) - 1.5 * Math.sin(b)

        // Wahre Sonnenzeit → Stundenwinkel
        val localTimeMin     = hour * 60.0 + minute.toDouble()
        val lonCorrectionMin = 4.0 * (lonDeg - tzOffsetH * 15.0)
        val trueSolarTimeH   = (localTimeMin + lonCorrectionMin + eot) / 60.0
        val hourAngleDeg     = (trueSolarTimeH - 12.0) * 15.0
        val haRad  = Math.toRadians(hourAngleDeg)
        val latRad = Math.toRadians(latDeg)

        // Elevation
        val sinEl = Math.sin(latRad) * Math.sin(decRad) +
                Math.cos(latRad) * Math.cos(decRad) * Math.cos(haRad)
        val elevation = Math.toDegrees(Math.asin(sinEl.coerceIn(-1.0, 1.0)))

        // Azimut (0°=Nord, im Uhrzeigersinn)
        // cos(Az) = (sin(δ) - sin(φ)·sin(El)) / (cos(φ)·cos(El))
        val elRad = Math.toRadians(elevation)
        val cosAz = (Math.sin(decRad) - Math.sin(latRad) * Math.sin(elRad)) /
                (Math.cos(latRad) * Math.cos(elRad))
        val azBase = Math.toDegrees(Math.acos(cosAz.coerceIn(-1.0, 1.0)))
        // Nachmittags (Stundenwinkel positiv) → Azimut > 180°
        val azimuth = if (hourAngleDeg >= 0) 360.0 - azBase else azBase

        return Pair(elevation, azimuth)
    }

    /** Azimut in Grad → Himmelsrichtung + Klartext */
    private fun azimuthToCardinal(az: Double): String {
        val directions = listOf(
            0.0   to getString(R.string.weather_sun_dir_n),
            22.5  to getString(R.string.weather_sun_dir_nno),
            45.0  to getString(R.string.weather_sun_dir_ne),
            67.5  to getString(R.string.weather_sun_dir_ono),
            90.0  to getString(R.string.weather_sun_dir_e),
            112.5 to getString(R.string.weather_sun_dir_oso),
            135.0 to getString(R.string.weather_sun_dir_se),
            157.5 to getString(R.string.weather_sun_dir_sso),
            180.0 to getString(R.string.weather_sun_dir_s),
            202.5 to getString(R.string.weather_sun_dir_ssw),
            225.0 to getString(R.string.weather_sun_dir_sw),
            247.5 to getString(R.string.weather_sun_dir_wsw),
            270.0 to getString(R.string.weather_sun_dir_w),
            292.5 to getString(R.string.weather_sun_dir_wnw),
            315.0 to getString(R.string.weather_sun_dir_nw),
            337.5 to getString(R.string.weather_sun_dir_nnw),
            360.0 to getString(R.string.weather_sun_dir_n)
        )
        return directions.minByOrNull { Math.abs(it.first - az) }?.second ?: "–"
    }

    /** Dialog zur Eingabe der Höhenwerte für die Schattenlängen-Berechnung */
    private fun showShadowHeightDialog(
        prefs: android.content.SharedPreferences,
        onDone: () -> Unit
    ) {
        val density = resources.displayMetrics.density

        // Drei EditText-Felder
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(), (8 * density).toInt(),
                (24 * density).toInt(), 0
            )
        }

        fun makeField(label: String, defaultVal: Float): android.widget.EditText {
            val tv = android.widget.TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 13f
            }
            val et = android.widget.EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(String.format(Locale.getDefault(), "%.2f", defaultVal))
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#888888"))
                hint = getString(R.string.weather_meter_hint)
            }
            container.addView(tv)
            container.addView(et)
            return et
        }

        val etPerson = makeField(
            getString(R.string.weather_shadow_height_person),
            prefs.getFloat("height_person", 1.75f)
        )
        val etDog = makeField(
            getString(R.string.weather_shadow_height_dog),
            prefs.getFloat("height_dog", 0.55f)
        )
        val etObject = makeField(
            getString(R.string.weather_shadow_height_object),
            prefs.getFloat("height_object", 1.00f)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.weather_shadow_dialog_title))
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val person = etPerson.text.toString()
                    .replace(",", ".").toFloatOrNull() ?: 1.75f
                val dog = etDog.text.toString()
                    .replace(",", ".").toFloatOrNull() ?: 0.55f
                val obj = etObject.text.toString()
                    .replace(",", ".").toFloatOrNull() ?: 1.00f
                prefs.edit()
                    .putFloat("height_person", person.coerceIn(0.1f, 5.0f))
                    .putFloat("height_dog",    dog.coerceIn(0.05f, 2.0f))
                    .putFloat("height_object", obj.coerceIn(0.1f, 50.0f))
                    .apply()
                onDone()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().apply {
                show()
            }
    }

    // ── UV-Hilfsfunktionen ────────────────────────────────────────────────────

    private fun uvIndexLabel(uv: Double): String = when {
        uv < 1  -> getString(R.string.uv_index_minimal)
        uv < 3  -> getString(R.string.uv_index_low)
        uv < 6  -> getString(R.string.uv_index_medium)
        uv < 8  -> getString(R.string.uv_index_high)
        uv < 11 -> getString(R.string.uv_index_very_high)
        else    -> getString(R.string.uv_index_extreme)
    }

    private fun uvIndexColor(uv: Double): Int = when {
        uv < 1  -> android.graphics.Color.parseColor("#B2B2AE")   // grau
        uv < 3  -> android.graphics.Color.parseColor("#4CAF50")   // grün
        uv < 6  -> android.graphics.Color.parseColor("#FFC107")   // gelb
        uv < 8  -> android.graphics.Color.parseColor("#FF9800")   // orange
        uv < 11 -> android.graphics.Color.parseColor("#FF6F60")   // rot
        else    -> android.graphics.Color.parseColor("#CE93D8")   // lila
    }

    private fun buildUvProtectionText(peakUv: Double): String {
        if (peakUv < 3.0) return ""
        return buildString {
            append(getString(R.string.uv_protection_header, String.format(Locale.getDefault(), "%.1f", peakUv))).append("\n\n")
            when {
                peakUv < 6 -> {
                    append(getString(R.string.uv_protection_human_moderate)).append("\n\n")
                    append(getString(R.string.uv_protection_dog_moderate))
                }
                peakUv < 8 -> {
                    append(getString(R.string.uv_protection_human_high)).append("\n\n")
                    append(getString(R.string.uv_protection_dog_high))
                }
                peakUv < 11 -> {
                    append(getString(R.string.uv_protection_human_very_high)).append("\n\n")
                    append(getString(R.string.uv_protection_dog_very_high))
                }
                else -> {
                    append(getString(R.string.uv_protection_human_extreme)).append("\n\n")
                    append(getString(R.string.uv_protection_dog_extreme))
                }
            }
            append("\n\n").append(getString(R.string.uv_protection_vet_disclaimer))
        }
    }

    private fun displayAirQualityCard(airQualityData: JSONObject) {
        try {
            val hourly  = airQualityData.getJSONObject("hourly")
            val times   = hourly.getJSONArray("time")
            val pm25arr = hourly.getJSONArray("pm2_5")
            val pm10arr = hourly.getJSONArray("pm10")
            val no2arr  = hourly.getJSONArray("nitrogen_dioxide")
            val o3arr   = hourly.getJSONArray("ozone")

            val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now     = System.currentTimeMillis()
            val oneHour = 3600000L

            val items = mutableListOf<AirQualityHourItem>()
            var count = 0
            for (i in 0 until times.length()) {
                if (count >= 24) break
                val date = dateFmt.parse(times.getString(i)) ?: continue
                if (date.time < now - oneHour) continue

                val pm25 = if (!pm25arr.isNull(i)) pm25arr.getDouble(i) else 0.0
                val pm10 = if (!pm10arr.isNull(i)) pm10arr.getDouble(i) else 0.0
                val no2  = if (!no2arr.isNull(i))  no2arr.getDouble(i)  else 0.0
                val o3   = if (!o3arr.isNull(i))   o3arr.getDouble(i)   else 0.0
                val level = calcAqiFromPm25(pm25)

                items.add(AirQualityHourItem(
                    time     = timeFmt.format(date),
                    pm25     = pm25,
                    pm10     = pm10,
                    no2      = no2,
                    o3       = o3,
                    aqi      = level,
                    aqiLabel = aqiLevelLabel(level),
                    aqiColor = aqiLevelColor(level)
                ))
                count++
            }

            if (items.isEmpty()) return

            // Aktuellen Wert prominent anzeigen
            val current = items.first()
            binding.currentAqiText.text = "${current.aqi} – ${current.aqiLabel}"
            binding.currentAqiText.setTextColor(current.aqiColor)
            binding.currentPollutantsText.text = getString(
                R.string.weather_pollutants_format,
                String.format(Locale.getDefault(), "%.1f", current.pm25),
                String.format(Locale.getDefault(), "%.1f", current.pm10),
                String.format(Locale.getDefault(), "%.1f", current.no2),
                String.format(Locale.getDefault(), "%.1f", current.o3)
            )

            binding.airQualityRecyclerView.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.airQualityRecyclerView.adapter = AirQualityAdapter(items)

            binding.cardAirQuality.visibility = View.VISIBLE

        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei Luftqualität-Card", e)
        }
    }

    private fun displayPollenCard(airQualityData: JSONObject) {
        try {
            val hourly = airQualityData.getJSONObject("hourly")
            val times  = hourly.getJSONArray("time")

            // Pollenarten mit Namen und Emoji
            data class PollenSource(val key: String, val name: String, val emoji: String)
            val sources = listOf(
                PollenSource("birch_pollen",   getString(R.string.weather_pollen_birch),   "🌳"),
                PollenSource("grass_pollen",   getString(R.string.weather_pollen_grass),   "🌾"),
                PollenSource("alder_pollen",   getString(R.string.weather_pollen_alder),   "🌿"),
                PollenSource("mugwort_pollen", getString(R.string.weather_pollen_mugwort), "🌿"),
                PollenSource("olive_pollen",   getString(R.string.weather_pollen_olive),   "🫒"),
                PollenSource("ragweed_pollen", getString(R.string.weather_pollen_ragweed), "🌻")
            )

            // Erstes gültiges Stunden-Fenster (= aktuell) ermitteln
            val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val now = System.currentTimeMillis()
            val oneHour = 3600000L
            var currentIdx = -1
            for (i in 0 until times.length()) {
                val date = dateFmt.parse(times.getString(i)) ?: continue
                if (date.time >= now - oneHour) { currentIdx = i; break }
            }
            if (currentIdx < 0) return

            // Pollenarten mit Wert > 0 oder bekannten Belastern sammeln
            val activeItems = mutableListOf<PollenItem>()
            var highestLevel = 0

            for (src in sources) {
                if (!hourly.has(src.key)) continue
                val arr = hourly.getJSONArray(src.key)
                if (arr.isNull(currentIdx)) continue
                val value = arr.getDouble(currentIdx)
                val level = pollenLevel(value)
                if (level > highestLevel) highestLevel = level
                if (level > 0) {  // Nur anzeigen wenn messbar
                    activeItems.add(PollenItem(
                        key        = src.key,
                        name       = src.name,
                        emoji      = src.emoji,
                        value      = value,
                        level      = level,
                        levelLabel = pollenLevelLabel(level),
                        levelColor = pollenLevelColor(level)
                    ))
                }
            }

            // Anzeigetext aufbauen
            val displayText = if (activeItems.isEmpty()) {
                getString(R.string.weather_pollen_none_measured)
            } else {
                activeItems.sortedByDescending { it.level }.joinToString("\n") { item ->
                    getString(R.string.weather_pollen_item_line, item.emoji, item.name, item.levelLabel, String.format(Locale.getDefault(), "%.1f", item.value))
                }
            }
            binding.pollenTodayText.text = displayText

            // Hundemedizinische Tipps basierend auf Belastungsgrad
            val dogTips = getPollenDogTips(activeItems, highestLevel)
            if (dogTips.isNotEmpty()) {
                binding.pollenDogTipsText.text = dogTips
                binding.pollenDogTipsText.visibility = View.VISIBLE
            } else {
                binding.pollenDogTipsText.visibility = View.GONE
            }

            binding.cardPollen.visibility = View.VISIBLE

        } catch (e: Exception) {
            android.util.Log.e("WeatherActivity", "Fehler bei Pollen-Card", e)
        }
    }

    private fun getPollenDogTips(items: List<PollenItem>, highestLevel: Int): String {
        if (highestLevel == 0) return ""

        val sb = StringBuilder(getString(R.string.weather_pollen_dog_header)).append("\n\n")

        // Allgemeine Hinweise je Belastungsstufe
        when (highestLevel) {
            1 -> sb.append(getString(R.string.weather_pollen_dog_level_1)).append("\n\n")
            2 -> sb.append(getString(R.string.weather_pollen_dog_level_2)).append("\n\n")
            3 -> sb.append(getString(R.string.weather_pollen_dog_level_3)).append("\n\n")
            4 -> sb.append(getString(R.string.weather_pollen_dog_level_4)).append("\n\n")
        }

        // Pollenspezifische Hinweise
        val highItems = items.filter { it.level >= 2 }
        if (highItems.any { it.key == "birch_pollen" || it.key == "alder_pollen" }) {
            sb.append(getString(R.string.weather_pollen_dog_tree)).append("\n\n")
        }
        if (highItems.any { it.key == "grass_pollen" }) {
            sb.append(getString(R.string.weather_pollen_dog_grass)).append("\n\n")
        }
        if (highItems.any { it.key == "mugwort_pollen" || it.key == "ragweed_pollen" }) {
            sb.append(getString(R.string.weather_pollen_dog_herb)).append("\n\n")
        }

        // Allgemeine Pflegetipps bei erhöhter Belastung
        if (highestLevel >= 2) {
            sb.append(getString(R.string.weather_pollen_dog_general_tips))
        }

        sb.append("\n\n").append(getString(R.string.uv_protection_vet_disclaimer))
        return sb.toString()
    }

    // ════════════════════════════════════════════════════════════════
    // BIOWETTER-CARD
    //
    // EINBAU:
    // 1. fetchWeatherData() – hourly-Block:
    //    "cloud_cover,visibility,uv_index,cape,surface_pressure"
    //
    // 2. displayWeatherData() – nach displayMoonPhase():
    //    displayBiowetter(data)
    //
    // 3. Diese Funktion + showBiowetterDetail() am Ende der Klasse einfügen.
    // ════════════════════════════════════════════════════════════════

    private fun displayBiowetter(data: JSONObject) {
        try {
            val current = data.getJSONObject("current")
            val hourly  = data.getJSONObject("hourly")

            val tempC       = current.getDouble("temperature_2m")
            val humidity    = current.getInt("relative_humidity_2m")
            val windSpeed   = current.getDouble("wind_speed_10m")
            val weatherCode = current.getInt("weather_code")

            val dateFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val timeFmt  = hourly.getJSONArray("time")
            val now      = System.currentTimeMillis()
            val sixHours = 6 * 3600_000L
            val oneHour  = 3_600_000L

            // ── Luftdruck-Stundenwerte (letzten 6h) ───────────────────────
            val pressureArr = hourly.getJSONArray("surface_pressure")
            val recentPressures = mutableListOf<Double>()
            for (i in 0 until timeFmt.length()) {
                val date = dateFmt.parse(timeFmt.getString(i)) ?: continue
                if (date.time in (now - sixHours)..(now + oneHour)) {
                    if (!pressureArr.isNull(i)) recentPressures.add(pressureArr.getDouble(i))
                }
            }

            // ── Niederschlagswahrscheinlichkeit aktuelle Stunde ───────────
            val precipProbArr = hourly.getJSONArray("precipitation_probability")
            var precipProb = 0
            for (i in 0 until timeFmt.length()) {
                val date = dateFmt.parse(timeFmt.getString(i)) ?: continue
                if (date.time >= now - oneHour) {
                    precipProb = if (!precipProbArr.isNull(i)) precipProbArr.getInt(i) else 0
                    break
                }
            }

            // ── CAPE aktuelle Stunde (optional) ───────────────────────────
            var capeValue = -1.0
            if (hourly.has("cape")) {
                val capeArr = hourly.getJSONArray("cape")
                for (i in 0 until timeFmt.length()) {
                    val date = dateFmt.parse(timeFmt.getString(i)) ?: continue
                    if (date.time >= now - oneHour) {
                        capeValue = if (!capeArr.isNull(i)) capeArr.getDouble(i) else -1.0
                        break
                    }
                }
            }

            // ── Biowetter-Bewertung ────────────────────────────────────────
            val result = BiowetterEngine.evaluate(
                context        = this,
                pressureHourly = recentPressures,
                tempC          = tempC,
                relHumidityPct = humidity,
                windSpeedKmh   = windSpeed,
                capeJPerKg     = capeValue,
                weatherCode    = weatherCode,
                precipProb     = precipProb
            )

            // ── Gesamtampel ────────────────────────────────────────────────
            binding.biowetterOverallBadge.text = "● ${result.overallLevel.label(this)}"
            binding.biowetterOverallBadge.setTextColor(result.overallLevel.color)
            binding.biowetterSummaryText.text  = result.overallSummary
            binding.biowetterAdviceText.text   = result.trainingAdvice

            // ── Indikatoren dynamisch aufbauen ─────────────────────────────
            binding.biowetterIndicatorsContainer.removeAllViews()
            val density = resources.displayMetrics.density

            result.indicators.forEachIndexed { idx, indicator ->
                // Klickbare Zeile pro Indikator
                val row = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showBiowetterDetail(indicator) }
                }

                // Kopfzeile: Name links, Ampel rechts
                val header = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                val nameText = android.widget.TextView(this).apply {
                    text = "${indicator.emoji} ${indicator.name}"
                    textSize = 14f
                    setTextColor(android.graphics.Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                val levelDot = android.widget.TextView(this).apply {
                    text = "● ${indicator.level.label(this@WeatherActivity)}"
                    textSize = 12f
                    setTextColor(indicator.level.color)
                }
                header.addView(nameText)
                header.addView(levelDot)

                // Messwert-Zeile
                val valueText = android.widget.TextView(this).apply {
                    text = indicator.valueText
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (2 * density).toInt() }
                }

                // Tipp-Hinweis
                val tapHint = android.widget.TextView(this).apply {
                    text = getString(R.string.weather_tap_for_details)
                    textSize = 11f
                    setTextColor(android.graphics.Color.parseColor("#B2B2AE"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.topMargin = (1 * density).toInt() }
                }

                row.addView(header)
                row.addView(valueText)
                row.addView(tapHint)
                binding.biowetterIndicatorsContainer.addView(row)

                // Trennlinie (nicht nach dem letzten Element)
                if (idx < result.indicators.size - 1) {
                    val divider = android.view.View(this).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        ).also {
                            it.topMargin    = (4 * density).toInt()
                            it.bottomMargin = (4 * density).toInt()
                        }
                        setBackgroundColor(ContextCompat.getColor(this@WeatherActivity, R.color.gl_divider))
                    }
                    binding.biowetterIndicatorsContainer.addView(divider)
                }
            }

        } catch (e: Exception) {
            // Fehler sichtbar machen statt stumm scheitern
            android.util.Log.e("WeatherActivity", "Fehler bei Biowetter-Card: ${e.message}", e)
            binding.biowetterOverallBadge.text = getString(R.string.weather_biowetter_data_unavailable)
            binding.biowetterOverallBadge.setTextColor(android.graphics.Color.parseColor("#999999"))
            binding.biowetterSummaryText.text  = getString(R.string.weather_biowetter_calc_failed)
            binding.biowetterAdviceText.text   = ""
        }
    }

    private fun showBiowetterDetail(indicator: BioIndicator) {

        val message = buildString {
            append("${indicator.valueText}\n\n")
            append(getString(R.string.biowetter_dialog_human_effect, indicator.humanEffect)).append("\n\n")
            append(getString(R.string.biowetter_dialog_dog_effect, indicator.dogEffect)).append("\n\n")
            append(getString(R.string.biowetter_dialog_disclaimer))
        }

        AlertDialog.Builder(this)
            .setTitle("${indicator.emoji} ${indicator.name} – ${indicator.level.label(this)}")
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .create().apply {
                show()
            }
    }
}