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
            title = "Wetterinformationen"
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocation()
                } else {
                    binding.descriptionText.text = "Standortberechtigung benötigt"
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Standortberechtigung wurde verweigert", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
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
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_CHANGE, this)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_CHANGE, this)
        }
    }

    private fun stopLocationUpdates() {
        if (isRequestingUpdates) {
            locationManager.removeUpdates(this)
            isRequestingUpdates = false
        }
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
            hint = "Stadt oder Ort eingeben..."
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        
        AlertDialog.Builder(this)
            .setTitle("🌍 Ort suchen")
            .setView(input)
            .setPositiveButton("Suchen") { _, _ ->
                val query = input.text.toString()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun loadWeather(latitude: Double, longitude: Double, cityName: String? = null) {
        currentLat = latitude
        currentLon = longitude
        currentCityName = cityName
        
        binding.progressBar.visibility = View.VISIBLE
        updateFavoriteIcon()
        
        val displayLoc = cityName ?: String.format(Locale.GERMAN, "%.4f°, %.4f°", latitude, longitude)
        binding.locationText.text = "📍 $displayLoc"
        
        lifecycleScope.launch {
            try {
                // Falls cityName null ist, versuchen wir ihn via Reverse-Geocoding zu finden
                val nameToUse = cityName ?: withContext(Dispatchers.IO) {
                    reverseGeocode(latitude, longitude)
                }
                
                if (nameToUse != null) {
                    currentCityName = nameToUse
                    binding.locationText.text = "📍 $nameToUse"
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
                binding.descriptionText.text = "Fehler beim Laden der Wetterdaten"
                Toast.makeText(this@WeatherActivity, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
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
                    val connection = URL(url).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "HundeWildnis-App/1.0")
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
                    Toast.makeText(this@WeatherActivity, "Keine Ergebnisse gefunden", Toast.LENGTH_SHORT).show()
                } else {
                    showSearchResultsDialog(results)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@WeatherActivity, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSearchResultsDialog(results: List<WeatherFavorite>) {
        val names = results.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Ergebnisse")
            .setItems(names) { _, which ->
                val selected = results[which]
                loadWeather(selected.lat, selected.lon, selected.name)
            }
            .show()
    }

    private fun toggleFavorite() {
        if (WeatherFavoritesManager.isFavorite(this, currentLat, currentLon)) {
            WeatherFavoritesManager.remove(this, currentLat, currentLon)
            Toast.makeText(this, "Aus Favoriten entfernt", Toast.LENGTH_SHORT).show()
        } else {
            val name = currentCityName ?: String.format(Locale.GERMAN, "%.2f, %.2f", currentLat, currentLon)
            WeatherFavoritesManager.add(this, WeatherFavorite(name = name, lat = currentLat, lon = currentLon))
            Toast.makeText(this, "Zu Favoriten hinzugefügt", Toast.LENGTH_SHORT).show()
        }
        updateFavoriteIcon()
    }

    private fun showFavoritesDialog() {
        val favorites = WeatherFavoritesManager.getAll(this)
        if (favorites.isEmpty()) {
            Toast.makeText(this, "Noch keine Favoriten gespeichert", Toast.LENGTH_SHORT).show()
            return
        }

        val names = favorites.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("⭐ Wetter Favoriten")
            .setItems(names) { _, which ->
                val selected = favorites[which]
                loadWeather(selected.lat, selected.lon, selected.name)
            }
            .setNeutralButton("Verwalten") { _, _ -> manageFavorites() }
            .setPositiveButton("Schließen", null)
            .show()
    }

    private fun manageFavorites() {
        val favorites = WeatherFavoritesManager.getAll(this)
        val names = favorites.map { it.name }.toTypedArray()
        val checked = BooleanArray(favorites.size) { false }

        AlertDialog.Builder(this)
            .setTitle("Favoriten löschen")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Ausgewählte löschen") { _, _ ->
                val newList = favorites.filterIndexed { index, _ -> !checked[index] }
                WeatherFavoritesManager.saveAll(this, newList)
                updateFavoriteIcon()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): String? {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&accept-language=de"
            val connection = URL(url).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "HundeWildnis-App/1.0")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val address = json.optJSONObject("address")
            address?.optString("city") ?: address?.optString("town") ?: address?.optString("village")
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchWeatherData(latitude: Double, longitude: Double): JSONObject {
        val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature," +
                "precipitation,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m," +
                "wind_gusts_10m,uv_index,is_day" +
                "&hourly=temperature_2m,precipitation,precipitation_probability,weather_code," +
                "apparent_temperature,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
                "cloud_cover,visibility,uv_index" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset," +
                "weather_code,precipitation_probability_max," +
                "wind_speed_10m_max,wind_gusts_10m_max,uv_index_max,sunshine_duration" +
                "&past_days=1" +
                "&forecast_days=7" +
                "&timezone=auto"
        val response = URL(url).readText()
        return JSONObject(response)
    }

    private fun fetchAirQualityData(latitude: Double, longitude: Double): JSONObject {
        // Separater Open-Meteo Air-Quality Endpunkt – kostenlos, kein API-Key nötig
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality?" +
                "latitude=$latitude&longitude=$longitude" +
                "&hourly=pm10,pm2_5,nitrogen_dioxide,ozone,carbon_monoxide," +
                "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen" +
                "&forecast_days=2" +
                "&timezone=auto"
        val response = URL(url).readText()
        return JSONObject(response)
    }

    // ════════════════════════════════════════════════════════════════
    // displayWeatherData – Haupteinstieg nach API-Antwort
    // ════════════════════════════════════════════════════════════════

    private fun displayWeatherData(data: JSONObject, airQualityData: JSONObject?, latitude: Double, longitude: Double) {
        val current = data.getJSONObject("current")
        val temperature  = current.getDouble("temperature_2m")
        val feelsLike    = current.getDouble("apparent_temperature")
        val humidity     = current.getInt("relative_humidity_2m")
        val precipitation = current.getDouble("precipitation")
        val weatherCode  = current.getInt("weather_code")
        val pressure     = current.getDouble("surface_pressure")
        val windSpeed    = current.getDouble("wind_speed_10m")
        val windDirection = current.getDouble("wind_direction_10m")
        val windGusts    = current.getDouble("wind_gusts_10m")
        val uvIndex      = current.getDouble("uv_index")

        binding.temperatureText.text = String.format(Locale.GERMAN, "%.1f°C", temperature)

        val windchill = calculateWindchill(temperature, windSpeed)
        binding.feelsLikeText.text = String.format(Locale.GERMAN, "%.1f°C (Windgeschützt: %.1f°C)", feelsLike, windchill)

        val (description, icon) = getWeatherDescription(weatherCode)
        binding.descriptionText.text = description
        binding.weatherIcon.text = icon

        val windDirectionText = getWindDirection(windDirection)
        val beaufort = getBeaufortScale(windSpeed)
        binding.tvWindSymbol.text = getBeaufortSymbol(beaufort)
        binding.windText.text = String.format(Locale.GERMAN, "%.1f km/h %s (Bft %d)\nBöen: %.1f km/h", windSpeed, windDirectionText, beaufort, windGusts)

        binding.humidityText.text = "$humidity%"
        binding.precipitationText.text = String.format(Locale.GERMAN, "%.1f mm", precipitation)
        binding.pressureText.text = String.format(Locale.GERMAN, "%.0f hPa", pressure)
        binding.visibilityText.text = "> 10 km"
        binding.uvIndexText.text = String.format(Locale.GERMAN, "%.1f", uvIndex)

        displaySunriseSunset(data)
        display24HourForecast(data)
        displayDailyOverview(data)
        display7DayForecast(data)           // NEU: 7-Tage-Card
        displayWindForecast(data)           // NEU: Wind-stündlich-Card
        displaySunCard(data, latitude, longitude)                // NEU: Sonne-Card
        if (airQualityData != null) {
            displayAirQualityCard(airQualityData)   // NEU: Luftqualität-Card
            displayPollenCard(airQualityData)       // NEU: Pollen-Card
        }
        setupRainRadar(latitude, longitude)
        displayPawWeather(data, windSpeed, windGusts)
        displayTemperatureChart(data)
        displayPrecipitationChart(data)
        displayDaylightInfo(data)
        displayMoonPhase()
    }


    // ════════════════════════════════════════════════════════════════
    // MONDPHASE  (rein lokal, kein API-Aufruf)
    // ════════════════════════════════════════════════════════════════

    private fun displayMoonPhase() {
        try {
            val moonInfo = calculateMoonPhase()
            binding.moonPhaseView.moonAge        = moonInfo.age
            binding.moonPhaseNameText.text       = moonInfo.phaseName
            binding.moonIlluminationText.text    = String.format(Locale.GERMAN, "Beleuchtung: %.0f%%", moonInfo.illumination)
            binding.moonIlluminationBar.progress = moonInfo.illumination.toInt()
            binding.moonAgeText.text             = String.format(Locale.GERMAN, "Mondalter: %.1f von 29,5 Tagen", moonInfo.age)
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
            nextFullMoon  = buildNextEventText("🌕 Vollmond", daysToFull),
            nextNewMoon   = buildNextEventText("🌑 Neumond",  daysToNew),
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
        days < 0.5 -> "$label: heute!"
        days < 1.5 -> "$label: morgen"
        else       -> "$label: in ${days.toInt()} Tagen"
    }

    private fun getMoonPhaseNameAndEmoji(phaseIndex: Int): Pair<String, String> = when (phaseIndex) {
        0 -> "Neumond"                           to "🌑"
        1 -> "Zunehmende Sichel (1. Viertel)"    to "🌒"
        2 -> "Erstes Viertel (Halbmond)"         to "🌓"
        3 -> "Zunehmender Mond (2. Viertel)"     to "🌔"
        4 -> "Vollmond"                          to "🌕"
        5 -> "Abnehmender Mond (3. Viertel)"     to "🌖"
        6 -> "Letztes Viertel (Halbmond)"        to "🌗"
        else -> "Abnehmende Sichel (4. Viertel)" to "🌘"
    }

    private fun getMoonAstronomyInfo(phaseIndex: Int, illumination: Double): String {
        val pct = illumination.toInt()
        return when (phaseIndex) {
            0 -> "🔭 Astronomie:\nDer Mond steht in Konjunktion zur Sonne – seine beleuchtete Seite zeigt von der Erde weg. Er geht mit der Sonne auf und unter, ist tagsüber unsichtbar und erscheint am Nachthimmel nicht.\nBeleuchtung: ~${pct}%"
            1 -> "🔭 Astronomie:\nDie schmale Sichel erscheint kurz nach Sonnenuntergang im Westen und folgt der Sonne bald unter den Horizont (sichtbar ca. 1–2 Stunden). Die unbeleuchtete Seite schimmert bläulich (Erdschein).\nBeleuchtung: ~${pct}%"
            2 -> "🔭 Astronomie:\nErste Quadratur (90°-Winkel Sonne–Erde–Mond). Exakt die rechte Hälfte ist beleuchtet. Aufgang gegen Mittag, Untergang gegen Mitternacht. Der Terminator ist ideal für Teleskopbeobachtungen.\nBeleuchtung: ~${pct}%"
            3 -> "🔭 Astronomie:\nGibbous-Phase (lat. gibbus = buckelig): Mehr als die Hälfte ist beleuchtet. Der Mond geht am Nachmittag auf und ist auch noch in den frühen Morgenstunden sichtbar.\nBeleuchtung: ~${pct}%"
            4 -> "🔭 Astronomie:\nOpposition: Erde liegt zwischen Sonne und Mond. Die gesamte sichtbare Mondseite wird beleuchtet. Er geht bei Sonnenuntergang auf und bei Sonnenaufgang unter – die ganze Nacht sichtbar. Entfernung: ~384.400 km.\nBeleuchtung: ~${pct}%"
            5 -> "🔭 Astronomie:\nDer Mond nimmt ab – er geht zunehmend später auf (nach Mitternacht) und steht morgens noch am Himmel. Die beleuchtete Fläche auf der linken Seite wird von Nacht zu Nacht kleiner.\nBeleuchtung: ~${pct}%"
            6 -> "🔭 Astronomie:\nZweite Quadratur (270°). Jetzt ist die linke Hälfte beleuchtet. Aufgang gegen Mitternacht, Untergang gegen Mittag. Der Mond steht bei Sonnenaufgang im Süden.\nBeleuchtung: ~${pct}%"
            else -> "🔭 Astronomie:\nDie abnehmende Sichel ist kurz vor Sonnenaufgang im Osten sichtbar. Der Mond nähert sich wieder der Sonne an. In wenigen Tagen beginnt ein neuer synodischer Zyklus (29 Tage, 12 Std., 44 Min.).\nBeleuchtung: ~${pct}%"
        }
    }

    private fun getMoonNatureInfo(phaseIndex: Int): String = when (phaseIndex) {
        0 -> "🌿 Natur & Tierwelt:\n• Dunkle Nächte – Eulen, Füchse und Marder jagen besonders effektiv.\n• Rehe, Hasen und Kaninchen meiden offenes Gelände (höhere Fressfeindgefahr).\n• Fische stehen tiefer und sind weniger aktiv an der Oberfläche.\n• Springtide: Wechselspiel von Sonne und Mond erzeugt stärkste Gezeiten.\n• Biodynamisch: Gute Keimkraft, ideale Zeit für Aussaat von Blattgemüse."
        1 -> "🌿 Natur & Tierwelt:\n• Licht nimmt zu – Nachtaktivität der Wildtiere wächst langsam.\n• Zugvögel orientieren sich bei schwacher Mondbeleuchtung an den Sternen.\n• Fische steigen etwas höher in die Wassersäule.\n• Pflanzensaft steigt nach oben – günstig für Pflanzungen und Transplantationen.\n• Pilze fruchten gut bei zunehmendem Mond und feuchtem Wetter."
        2 -> "🌿 Natur & Tierwelt:\n• Erste Quadratur: Tiere sind spürbar aktiver – Dämmerungsrunden häufiger.\n• Zugvögel nutzen das zunehmende Mondlicht für Nachtflüge über Gebirge.\n• Süßwasserfische besonders aktiv, stehen hoch in ruhigen Buchten.\n• Traditionell günstig für Pflanzenschnitt; Wunden verheilen besser."
        3 -> "🌿 Natur & Tierwelt:\n• Mit wachsendem Licht steigen Aktivität und Risikobereitschaft der Wildtiere.\n• Hirsche ziehen weite Distanzen; Bachen mit Frischlingen sind unterwegs.\n• Insekten (Nachtfalter, Mücken) deutlich häufiger aktiv.\n• Achtung: Zeckenaktivität kann auch nächtlich erhöht sein.\n• Meerestiere synchronisieren Laichen mit dem Mondzyklus."
        4 -> "🌿 Natur & Tierwelt:\n• Vollmond-Maximum: Studien belegen erhöhte Aktivität fast aller Wildtierarten.\n• Wölfe orientieren sich auf die Jagd – Rudelkommunikation per Heulen.\n• Rehe, Wildschweine, Füchse wechseln in offenem Gelände.\n• Hechte und Forellen fressen bevorzugt nachts bei Vollmond.\n• Korallenriffe: viele Arten spawnen synchron in Vollmondnächten.\n• Maximale Wasseraufnahme in Pflanzen – Früchte besonders saftig und aromatisch."
        5 -> "🌿 Natur & Tierwelt:\n• Wildtiere reduzieren langsam ihre Nachtaktivität, bleiben aber noch aktiv.\n• Gute Beobachtungsphase: Tiere noch unterwegs, Licht noch ausreichend.\n• Fische werden langsam wieder träger an der Oberfläche.\n• Holzeinschlag traditionell empfohlen: Holz trocknet besser, ist haltbarer."
        6 -> "🌿 Natur & Tierwelt:\n• Tiere kehren zu ruhigeren Tagesrhythmen zurück.\n• Nachts kaum Wildtierbegegnungen auf offenem Gelände.\n• Fischaktivität stark reduziert; Tiere suchen tiefere, ruhige Bereiche.\n• Biodynamisch: Günstig für Beschnitt von Obstbäumen und Wurzelarbeiten."
        else -> "🌿 Natur & Tierwelt:\n• Ruhigste Phase des Mondzyklus – Wildtiere am zurückgezogensten.\n• Beutetiere meiden offenes Gelände; dichte Deckung bevorzugt.\n• Frühmorgens oft gute Chance zur stillen Wildtierbeobachtung.\n• Biodynamisch: Günstig für Bodenbearbeitung, Kompostierung und Regeneration."
    }

    private fun getMoonDogTips(phaseIndex: Int): String = when (phaseIndex) {
        0 -> "🐕 Hund & Training:\n• Ruhige, ausgeglichene Phase – ideal für neue Trainingsimpulse.\n• Viele Hunde sind entspannt und lernbereit: Grundkommandos festigen.\n• Dunkle Nächte: Leine und Reflektoren sind Pflicht bei Abendrunden.\n• Wildtiere aktiver → Beuteinstinkt beachten; unzuverlässige Hunde an der Leine führen."
        1 -> "🐕 Hund & Training:\n• Energie nimmt zu – Hunde werden etwas lebhafter und neugieriger.\n• Gute Phase für aufbauende Übungen und das Einführen neuer Kommandos.\n• Abendrunden mit Blick auf die Abendsichel – ein schöner gemeinsamer Moment.\n• Zeckencheck nach Runden in der Dämmerung nicht vergessen."
        2 -> "🐕 Hund & Training:\n• Halbmond: Aktivitätsphase im Aufbau – Hunde häufig motivierter als sonst.\n• Sehr gute Zeit für Leinenarbeit, Distanzunterbrechungen und Positionsübungen.\n• Wildtiere nun deutlich aktiver → Begegnungssituationen wahrscheinlicher.\n• Rückruf und verlässliches Sitzenbleiben als Sicherheitsbasis trainieren."
        3 -> "🐕 Hund & Training:\n• Zunehmende Energie – intensive Trainingseinheiten gut durchführbar.\n• Manche Hunde reagieren sensibler auf Gerüche, Geräusche und Bewegungen.\n• Nasenarbeit besonders spannend: Wildtiergerüche sehr präsent.\n• Hunde mit starkem Jagdtrieb: Leine als kluge Vorsichtsmaßnahme beibehalten."
        4 -> "🐕 Hund & Training:\n⚠️ Vollmond – besondere Aufmerksamkeit:\n• Wissenschaftliche Studien (BMJ 2007, Anthrozoös 2011) belegen signifikant mehr Tierarztbesuche in Vollmondnächten – v. a. wegen Unfällen und Erregungszuständen.\n• Viele Hunde schlafen schlechter oder zeigen erhöhte Wachsamkeit und Unruhe.\n• Reizschwelle oft niedriger: Hunde reagieren auf schwächste Reize.\n• Wildtiere maximal aktiv → erhöhte Ablenkung und Ablenkungsintensität.\n• Empfehlung: Kurze, ruhige Runden bevorzugen. Vorhersehbare Routinen stärken das Sicherheitsgefühl.\n• Ängstliche Hunde: Ruhe und Gelassenheit als Sicherheitsanker ausstrahlen."
        5 -> "🐕 Hund & Training:\n• Energie nimmt ab – ideale Phase zum Festigen gelernter Übungen.\n• Hunde kehren zu ruhigeren Verhaltensmustern zurück.\n• Rückblick im Trainingstagebuch: Was klappt gut? Was braucht mehr Wiederholung?\n• Teamwalk und ruhige Geländeläufe jetzt besonders harmonisch."
        6 -> "🐕 Hund & Training:\n• Ruhigere, beständige Phase – gute Lernbereitschaft für Feinheiten und Präzision.\n• Letztes Viertel: Ideal für Konsolidierungstraining und Reflexion.\n• Welpen und sensible Hunde oft merklich entspannter als rund um den Vollmond.\n• Stresstoleranztraining: Ruhige, kontrollierte Reizeinführung jetzt besonders effektiv."
        else -> "🐕 Hund & Training:\n• Ruhigste und entspannteste Phase des gesamten Zyklus.\n• Ideal für regeneratives Training: Nasenarbeit, Scouttechniken, ruhige Erkundungstouren.\n• Hunde mit Aufregungsthemen: Gezielte Entspannungs- und Achtsamkeitsübungen einbauen.\n• Energie sammeln vor dem nächsten Neumond – Genuss-Runden ohne Leistungsdruck."
    }

    private fun getMoonZodiacSign(index: Int): String {
        val signs = listOf(
            "♈ Widder"     to "Feuerzeichen – aktiv, impulsiv, direkt",
            "♉ Stier"      to "Erdezeichen – beständig, sinnlich, geerdet",
            "♊ Zwillinge"  to "Luftzeichen – neugierig, beweglich, kommunikativ",
            "♋ Krebs"      to "Wasserzeichen – gefühlvoll, instinktiv, schützend",
            "♌ Löwe"       to "Feuerzeichen – ausdrucksstark, kraftvoll, mutig",
            "♍ Jungfrau"   to "Erdezeichen – präzise, beobachtend, analytisch",
            "♎ Waage"      to "Luftzeichen – harmonisch, sozial, ausgleichend",
            "♏ Skorpion"   to "Wasserzeichen – intensiv, tief, transformativ",
            "♐ Schütze"    to "Feuerzeichen – freiheitsliebend, weiträumig, optimistisch",
            "♑ Steinbock"  to "Erdezeichen – ausdauernd, diszipliniert, verlässlich",
            "♒ Wassermann" to "Luftzeichen – unabhängig, originell, zukunftsorientiert",
            "♓ Fische"     to "Wasserzeichen – sensibel, intuitiv, anpassungsfähig"
        )
        val (sign, description) = signs[index]
        return "🔯 Mondzeichen (Näherung): $sign\n$description"
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
        0  -> "Windstille"
        1  -> "Leiser Zug"
        2  -> "Leichte Brise"
        3  -> "Schwache Brise"
        4  -> "Mäßige Brise"
        5  -> "Frische Brise"
        6  -> "Starker Wind"
        7  -> "Steifer Wind"
        8  -> "Stürmischer Wind"
        9  -> "Sturm"
        10 -> "Schwerer Sturm"
        11 -> "Orkanartiger Sturm"
        12 -> "Orkan"
        else -> "Unbekannt"
    }

    private fun getBeaufortSymbol(bf: Int): String = when (bf) {
        0  -> "🌿 Windstille – Rauch steigt senkrecht"
        1  -> "🍃 Leiser Zug – Rauch zeigt Windrichtung"
        2  -> "🍃💨 Leichte Brise – Blätter rascheln"
        3  -> "🌿💨 Schwache Brise – Zweige bewegen sich"
        4  -> "🌾💨 Mäßige Brise – Kleine Äste bewegen sich"
        5  -> "🌲💨 Frische Brise – Kleine Bäume schwanken"
        6  -> "🌳💨 Starker Wind – Große Äste schwingen"
        7  -> "🌳🌬️ Steifer Wind – Ganze Bäume bewegen sich"
        8  -> "🌬️🌳 Stürmisch – Zweige brechen"
        9  -> "🌀🌲 Sturm – Äste brechen, leichte Schäden"
        10 -> "🌪️🌳 Schwerer Sturm – Bäume entwurzelt"
        11 -> "🌪️💥 Orkanartiger Sturm – Verbreitet Schäden"
        12 -> "🌀🌪️ Orkan – Verheerende Zerstörung"
        else -> ""
    }

    private fun getWindDirection(degrees: Double): String = when {
        degrees >= 337.5 || degrees < 22.5  -> "↓ Nord"
        degrees >= 22.5  && degrees < 67.5  -> "↙ NordOst"
        degrees >= 67.5  && degrees < 112.5 -> "← Ost"
        degrees >= 112.5 && degrees < 157.5 -> "↖ SüdOst"
        degrees >= 157.5 && degrees < 202.5 -> "↑ Süd"
        degrees >= 202.5 && degrees < 247.5 -> "↗ SüdWest"
        degrees >= 247.5 && degrees < 292.5 -> "→ West"
        degrees >= 292.5 && degrees < 337.5 -> "↘ NordWest"
        else -> ""
    }

    // ════════════════════════════════════════════════════════════════
    // WETTERBESCHREIBUNG
    // ════════════════════════════════════════════════════════════════

    private fun getWeatherDescription(code: Int): Pair<String, String> {
        android.util.Log.d("WeatherActivity", "🌤️ Weather Code: $code")
        return when (code) {
            0    -> "Klar"                              to "☀️"
            1    -> "Leicht bewölkt"                    to "🌤️"
            2    -> "Teilweise bewölkt"                 to "⛅"
            3    -> "Bewölkt"                           to "☁️"
            45   -> "Nebel"                             to "🌫️"
            48   -> "Gefrierender Nebel"                to "🌫️"
            51   -> "Leichter Nieselregen"              to "🌦️"
            53   -> "Mäßiger Nieselregen"               to "🌦️"
            55   -> "Starker Nieselregen"               to "🌦️"
            56   -> "Gefrierender leichter Nieselregen" to "🌧️"
            57   -> "Gefrierender starker Nieselregen"  to "🌧️"
            61   -> "Leichter Regen"                    to "🌧️"
            63   -> "Mäßiger Regen"                     to "🌧️"
            65   -> "Starker Regen"                     to "🌧️"
            66   -> "Gefrierender leichter Regen"       to "🌧️"
            67   -> "Gefrierender starker Regen"        to "🌧️"
            71   -> "Leichter Schneefall"               to "🌨️"
            73   -> "Mäßiger Schneefall"                to "🌨️"
            75   -> "Starker Schneefall"                to "🌨️"
            77   -> "Schneegriesel"                     to "🌨️"
            80   -> "Leichte Regenschauer"              to "🌦️"
            81   -> "Mäßige Regenschauer"               to "🌦️"
            82   -> "Starke Regenschauer"               to "🌦️"
            85   -> "Leichte Schneeschauer"             to "🌨️"
            86   -> "Starke Schneeschauer"              to "🌨️"
            95   -> "Gewitter"                          to "⛈️"
            96   -> "Gewitter mit leichtem Hagel"       to "⛈️"
            99   -> "Gewitter mit starkem Hagel"        to "⛈️"
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
    // SONNENZEITEN
    // ════════════════════════════════════════════════════════════════

    private fun displaySunriseSunset(data: JSONObject) {
        try {
            val daily = data.getJSONObject("daily")
            val sunriseArray = daily.getJSONArray("sunrise")
            val sunsetArray  = daily.getJSONArray("sunset")
            if (sunriseArray.length() > 1 && sunsetArray.length() > 1) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)
                val sunrise = dateFormat.parse(sunriseArray.getString(1))
                val sunset  = dateFormat.parse(sunsetArray.getString(1))
                if (sunrise != null && sunset != null) {
                    binding.sunriseText.text = "🌅 ${timeFormat.format(sunrise)}"
                    binding.sunsetText.text  = "🌇 ${timeFormat.format(sunset)}"
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
                binding.dailyMinMaxText.text = String.format(Locale.GERMAN, "Min: %.1f°C • Max: %.1f°C", minTemp, maxTemp)
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
        code >= 95               -> "⚠️ Gewitter"
        code >= 85               -> "❄️ Schneeschauer"
        code >= 80               -> "🌦️ Schauer"
        code >= 71               -> "❄️ Schneefall"
        code >= 61 && precipProb >= 70 -> "🌧️ Starker Regen"
        precipProb >= 80         -> "☔ Hohe Regenwahrscheinlichkeit"
        precipProb >= 50         -> "🌂 Möglicher Regen (${precipProb}%)"
        code >= 45               -> "🌫️ Nebel"
        code >= 3                -> "☁️ Bewölkt"
        else                     -> "✅ Keine Wetterwarnung"
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
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)
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
        val message = "Uhrzeit: ${item.time}\n\n$icon $description\n\nTemperatur: ${String.format(Locale.GERMAN, "%.1f°C", item.temperature)}\nGefühlt: ${String.format(Locale.GERMAN, "%.1f°C", item.feelsLike)}\nRegen: ${item.precipitation.toInt()}%"
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
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
    // REGENRADAR
    // ════════════════════════════════════════════════════════════════

    private fun setupRainRadar(lat: Double, lon: Double) {
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
                    if (url.contains("leafletjs.com") || url.contains("openstreetmap.org") || url.contains("rainviewer.com")) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        return true
                    }
                    return false
                }
            }
            val html = """<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>* { margin: 0; padding: 0; } html, body { height: 100%; background: #1a1a1a; } #map { width: 100%; height: calc(100% - 50px); } #controls { position: absolute; bottom: 0; left: 0; right: 0; height: 50px; background: rgba(45,45,45,0.95); display: flex; align-items: center; justify-content: center; gap: 10px; z-index: 1000; padding: 0 10px; } .control-btn { background: #8fad6f; color: white; border: none; border-radius: 5px; padding: 8px 15px; font-size: 14px; font-weight: bold; cursor: pointer; } #timestamp { color: #999; font-size: 12px; flex: 1; text-align: center; }</style>
</head><body>
<div id="map"></div>
<div id="controls">
  <button class="control-btn" id="playBtn" onclick="togglePlay()">▶ Play</button>
  <button class="control-btn" onclick="stopAnimation()">⏹ Stop</button>
  <div id="timestamp">Lade Radar...</div>
</div>
<script>
let map, radarLayers=[], timestamps=[], currentFrame=0, isPlaying=false, animationInterval=null;
map = L.map('map', { center: [$lat, $lon], zoom: 6, zoomControl: true });
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '© OpenStreetMap', maxZoom: 19 }).addTo(map);
L.marker([$lat, $lon]).addTo(map).bindPopup('Dein Standort').openPopup();
fetch('https://api.rainviewer.com/public/weather-maps.json').then(r=>r.json()).then(apiData=>{
  if(apiData.radar && apiData.radar.past){ timestamps=apiData.radar.past; timestamps.forEach(item=>{ radarLayers.push(L.tileLayer('https://tilecache.rainviewer.com'+item.path+'/256/{z}/{x}/{y}/2/1_1.png',{tileSize:256,opacity:0.9,zIndex:10})); }); if(radarLayers.length>0){ currentFrame=radarLayers.length-1; radarLayers[currentFrame].addTo(map); updateTimestamp(); } }
}).catch(()=>{ document.getElementById('timestamp').textContent='Radar nicht verfügbar'; });
function togglePlay(){ isPlaying ? pauseAnimation() : startAnimation(); }
function startAnimation(){ if(!radarLayers.length) return; isPlaying=true; document.getElementById('playBtn').innerHTML='⏸ Pause'; animationInterval=setInterval(()=>{ radarLayers[currentFrame].remove(); currentFrame=(currentFrame+1)%radarLayers.length; radarLayers[currentFrame].addTo(map); updateTimestamp(); },500); }
function pauseAnimation(){ isPlaying=false; document.getElementById('playBtn').innerHTML='▶ Play'; if(animationInterval){ clearInterval(animationInterval); animationInterval=null; } }
function stopAnimation(){ pauseAnimation(); if(radarLayers.length>0){ radarLayers[currentFrame].remove(); currentFrame=radarLayers.length-1; radarLayers[currentFrame].addTo(map); updateTimestamp(); } }
function updateTimestamp(){ if(timestamps[currentFrame]){ const d=new Date(timestamps[currentFrame].time*1000); document.getElementById('timestamp').textContent='📡 Radar: '+d.toLocaleTimeString('de-DE',{hour:'2-digit',minute:'2-digit'})+' Uhr'; } }
</script></body></html>"""
            binding.radarWebView.loadDataWithBaseURL("https://hundewildnis.com", html, "text/html", "UTF-8", null)
    }

    // ════════════════════════════════════════════════════════════════
    // TRAININGSWETTER-INDEX (Pfotenwetter)
    // ════════════════════════════════════════════════════════════════

    private fun displayPawWeather(data: JSONObject, windSpeed: Double, windGusts: Double) {
        val current = data.getJSONObject("current")
        val temperature  = current.getDouble("temperature_2m")
        val feelsLike    = current.getDouble("apparent_temperature")
        val weatherCode  = current.getInt("weather_code")
        val uvIndex      = current.getDouble("uv_index")
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
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
                var startIndex = 0
                for (i in 0 until times.length()) {
                    val date = dateFormat.parse(times.getString(i))
                    if (date != null && date.time >= now - oneHour) { startIndex = i; break }
                }
                val endIndex = (startIndex + 24).coerceAtMost(temps.length())
                val hourlyTemps = (startIndex until endIndex).map { temps.getDouble(it) }
                val hourlyTimes = (startIndex until endIndex).map {
                    dateFormat.parse(times.getString(it))?.let { d -> SimpleDateFormat("HH:mm", Locale.GERMAN).format(d) } ?: ""
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
            temperature >= 35 -> { warnings.add("🔥 EXTREM: Asphalt und Strassenpflaster bis zu 65°C! Gefahr ernster gesundheitlicher Schäden! Hitzeschockrisiko. Den Hund bitte nicht belasten."); severity = PawWeatherSeverity.CRITICAL }
            temperature >= 30 -> { warnings.add("⚠️ WARNUNG: Asphalt und Strassenpflaster kann 50-60°C erreichen. Gefahr gesundheitlicher Beeinträchtigungen! Diese Oberflächen bitte meiden."); severity = maxOf(severity, PawWeatherSeverity.WARNING) }
            temperature >= 25 -> { warnings.add("⚡ VORSICHT: Asphalt wird heiß (45°C+). Erhöhte Belastung für den Hund! Schatten nutzen."); severity = maxOf(severity, PawWeatherSeverity.CAUTION) }
        }
        when {
            temperature <= -10 -> { warnings.add("❄️ EXTREM KALT: Gesundheitsgefahr für unangepasste Rassen! Hund in stetiger Bewegung halten."); severity = maxOf(severity, PawWeatherSeverity.CRITICAL) }
            temperature <= -5  -> { warnings.add("🧊 Sehr kalt: Pfotenschutz empfohlen. Salz auf Wegen beachten! Hund in stetiger Bewegung halten."); severity = maxOf(severity, PawWeatherSeverity.WARNING) }
            temperature <= 0   -> { warnings.add("🌨️ Frost: Achtung vor Streusalz und Eis. Hund zeitnah abtrocknen und aufwärmen lassen."); severity = maxOf(severity, PawWeatherSeverity.CAUTION) }
        }
        if (temperature in 7.0..25.0 && precipitation < 5.0) { warnings.add("🕷️ Erhöhtes Zeckenrisiko. Hund regelmäßig absuchen und Zecken zeitnah entfernen!"); severity = maxOf(severity, PawWeatherSeverity.INFO) }
        if (uvIndex >= 6.0) { warnings.add("☀️ Hohe UV-Strahlung: Schattige Wege bevorzugen! Keine anstrengenden Aktionen durchführen."); severity = maxOf(severity, PawWeatherSeverity.INFO) }
        if (weatherCode >= 95) { warnings.add("⛈️ Gewitter: Achte auf Deinen Hund und auf Eure Sicherheit. Ängstliche Hunde nicht ableinen."); severity = maxOf(severity, PawWeatherSeverity.WARNING) }
        if (precipitation >= 10.0 || weatherCode in 80..82) { warnings.add("🌧️ Starker Regen: Rutschgefahr auf nässenempfindlichen Boden!"); severity = maxOf(severity, PawWeatherSeverity.CAUTION) }
        if (warnings.isEmpty()) { warnings.add("✅ Sehr gutes Trainingswetter."); severity = PawWeatherSeverity.GOOD }
        return PawWeatherResult(warnings, severity)
    }

    private fun addWindWarning(result: PawWeatherResult, windSpeed: Double, windGusts: Double): PawWeatherResult {
        val warnings = result.warnings.toMutableList()
        var severity = result.severity
        when {
            windGusts >= 75 -> { warnings.add("💨 STURM: Gefahr durch umstürzende Äste! Training verschieben!"); severity = maxOf(severity, PawWeatherSeverity.CRITICAL) }
            windGusts >= 50 -> { warnings.add("🌪️ Starker Wind: Im Gelände aufmerksam unterwegs sein."); severity = maxOf(severity, PawWeatherSeverity.WARNING) }
            windSpeed >= 40 -> { warnings.add("💨 Windige Bedingungen: Gerüche über große Entfernungen wahrnehmbar. Unzuverlässige Hunde bitte an der Leine führen."); severity = maxOf(severity, PawWeatherSeverity.INFO) }
        }
        return PawWeatherResult(warnings, severity)
    }

    private fun recommendWalkingTimes(currentTemp: Double, hourlyTemps: List<Double>, hourlyTimes: List<String>): List<String> {
        val recommendations = mutableListOf<String>()
        if (currentTemp >= 25) {
            val coolestHours = hourlyTemps.mapIndexed { i, t -> i to t }.sortedBy { it.second }.take(3).map { hourlyTimes[it.first] }
            recommendations.add("🌅 Beste Zeiten bei Hitze:")
            coolestHours.forEach { recommendations.add("  • $it Uhr (kühlste Phase)") }
        }
        if (currentTemp <= 5) {
            val warmestHours = hourlyTemps.mapIndexed { i, t -> i to t }.sortedByDescending { it.second }.take(3).map { hourlyTimes[it.first] }
            recommendations.add("☀️ Beste Zeiten bei Kälte:")
            warmestHours.forEach { recommendations.add("  • $it Uhr (wärmste Phase)") }
        }
        return recommendations
    }

    private fun getGroomingTips(weatherCode: Int, precipitation: Double): List<String> {
        val tips = mutableListOf<String>()
        when {
            weatherCode in 61..67 || weatherCode in 80..82 -> { tips.add("🛁 Nach der Runde: Pfoten und Fell trocknen und dabei auf Zecken achten."); tips.add("🐶 Ohren auf Nässe und Verschmutzungen prüfen") }
            weatherCode in 71..77 || weatherCode in 85..86 -> { tips.add("❄️ Eisklumpen zwischen Zehen entfernen"); tips.add("🧴 Pfotenbalsam verwenden gegen Risse") }
            precipitation >= 5.0 -> tips.add("🚿 Matschige Pfoten vor dem Reingehen säubern")
            precipitation == 0.0 && weatherCode <= 3 -> tips.add("🦮 Hundefreundliches Wetter - bei intensivem Training an 💧 Trinkwasser denken.")
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
            CRITICAL -> Color.parseColor("#F44336")
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
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)
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
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)
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
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)
                val yesterdaySunrise = dateFormat.parse(sunriseArray.getString(0))
                val yesterdaySunset  = dateFormat.parse(sunsetArray.getString(0))
                val sunrise = dateFormat.parse(sunriseArray.getString(1))
                val sunset  = dateFormat.parse(sunsetArray.getString(1))
                if (sunrise != null && sunset != null) {
                    val daylightMillis = sunset.time - sunrise.time
                    val hours   = TimeUnit.MILLISECONDS.toHours(daylightMillis)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(daylightMillis) % 60
                    binding.daylightDurationText.text = String.format(Locale.GERMAN, "☀️ Tageslänge: %dh %dm", hours, minutes)
                    val civilTwilightDuration = 35 * 60 * 1000L
                    binding.dawnText.text = String.format("🌄 Morgendämmerung: %s Uhr", timeFormat.format(Date(sunrise.time - civilTwilightDuration)))
                    binding.duskText.text = String.format("🌆 Abenddämmerung: %s Uhr", timeFormat.format(Date(sunset.time + civilTwilightDuration)))
                    if (yesterdaySunrise != null && yesterdaySunset != null) {
                        val yesterdayDaylight = yesterdaySunset.time - yesterdaySunrise.time
                        val changeMinutes = TimeUnit.MILLISECONDS.toMinutes(daylightMillis - yesterdayDaylight)
                        val trend = when { changeMinutes > 0 -> "📈 +$changeMinutes Min länger"; changeMinutes < 0 -> "📉 ${-changeMinutes} Min kürzer"; else -> "➡️ Gleich lang" }
                        binding.daylightTrendText.text = "Tendenz: $trend als gestern"
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
            recommendations.add(if (min < 60) "🌅 Sonnenaufgang in ${min}Min - Perfekt für Morgenrunde!" else "🌃 Noch dunkel - Achtet auf Eigensicherung und Reflektoren bei gefährlichen Strassensituationen!")
        }
        if (now.before(sunset)) {
            val min = TimeUnit.MILLISECONDS.toMinutes(sunset.time - now.time)
            if (min < 120) recommendations.add("🌇 Sonnenuntergang in ${min}Min - Ideal für Abendrunde!")
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
        val goldenHourDuration = 3600000L
        val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)
        binding.goldenHourText.text = "📸 Goldene Stunde (schönes Licht für Fotos):\nMorgens: ${timeFormat.format(sunrise)} - ${timeFormat.format(Date(sunrise.time + goldenHourDuration))}\nAbends: ${timeFormat.format(Date(sunset.time - goldenHourDuration))} - ${timeFormat.format(sunset)}"
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
        val name: String,              // z.B. "Birke"
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
        0 -> "Gut"
        1 -> "Mäßig"
        2 -> "Sensible gefährdet"
        3 -> "Ungesund"
        4 -> "Sehr ungesund"
        else -> "Gefährlich"
    }

    private fun aqiLevelColor(level: Int): Int = when (level) {
        0 -> android.graphics.Color.parseColor("#4CAF50")   // grün
        1 -> android.graphics.Color.parseColor("#8BC34A")   // hellgrün
        2 -> android.graphics.Color.parseColor("#FFC107")   // gelb
        3 -> android.graphics.Color.parseColor("#FF9800")   // orange
        4 -> android.graphics.Color.parseColor("#F44336")   // rot
        else -> android.graphics.Color.parseColor("#9C27B0") // lila
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
        0 -> "Keine"
        1 -> "Niedrig"
        2 -> "Mittel"
        3 -> "Hoch"
        else -> "Sehr hoch"
    }

    private fun pollenLevelColor(level: Int): Int = when (level) {
        0 -> android.graphics.Color.parseColor("#4CAF50")
        1 -> android.graphics.Color.parseColor("#8BC34A")
        2 -> android.graphics.Color.parseColor("#FFC107")
        3 -> android.graphics.Color.parseColor("#FF9800")
        else -> android.graphics.Color.parseColor("#F44336")
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
            val uvMax      = daily.getJSONArray("uv_index_max")
            val sunshine   = daily.getJSONArray("sunshine_duration") // Sekunden

            val dayNames = listOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")
            val dateFmt  = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN)
            val labelFmt = SimpleDateFormat("dd.MM.", Locale.GERMAN)

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
                    uvIndexMax        = uvMax.getDouble(i),
                    sunshineDurationMin = sunshine.getDouble(i) / 60.0
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
        val uvWarning = when {
            item.uvIndexMax >= 8 -> "☀️ Sehr hohe UV-Strahlung – Schatten bevorzugen!"
            item.uvIndexMax >= 6 -> "⚡ Hohe UV-Strahlung – Schutz empfohlen"
            item.uvIndexMax >= 3 -> "🌤️ Moderate UV-Strahlung"
            else                 -> "✅ Niedrige UV-Strahlung"
        }
        val sunshineh = (item.sunshineDurationMin / 60).toInt()
        val sunshinem = (item.sunshineDurationMin % 60).toInt()

        val message = buildString {
            append("${item.dayLabel}, ${item.dateLabel}\n\n")
            append("$icon $description\n\n")
            append("🌡️ Max: ${String.format(Locale.GERMAN, "%.1f°C", item.tempMax)}  " +
                    "Min: ${String.format(Locale.GERMAN, "%.1f°C", item.tempMin)}\n")
            append("💧 Regenwahrscheinlichkeit: ${item.precipProbability}%\n")
            append("💨 Wind max: ${String.format(Locale.GERMAN, "%.0f km/h", item.windSpeedMax)} " +
                    "(Bft $beaufort)  Böen: ${String.format(Locale.GERMAN, "%.0f km/h", item.windGustsMax)}\n")
            append("☀️ Sonnenschein: ${sunshineh}h ${sunshinem}m\n")
            append("🔆 UV-Index max: ${String.format(Locale.GERMAN, "%.1f", item.uvIndexMax)}\n")
            append("$uvWarning")
        }

        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("📅 Tagesvorhersage")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
            }
    }

    private fun showUvHourlyDetail(item: UvForecastItem) {
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val accentColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("🔆 UV-Index ${item.time} Uhr – " +
                    "${String.format(Locale.GERMAN, "%.1f", item.uvIndex)} (${item.label})")
            .setMessage(buildUvHourlyTip(item))
            .setPositiveButton("OK", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accentColor)
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
                append("UV-Index unter 1 – keine relevante UV-Belastung.\n\n")
                append("👤 Mensch: Kein Sonnenschutz erforderlich.\n\n")
                append("🐕 Hund: Keine besonderen Maßnahmen nötig.")
                return@buildString
            }

            // ── Einordnung ──────────────────────────────────────────────────
            append(when {
                uv < 3  -> "UV-Index ${String.format(Locale.GERMAN, "%.1f", uv)} – Gering\n" +
                        "Kein zwingender Sonnenschutz, bei längeren Aufenthalten empfohlen.\n\n"
                uv < 6  -> "UV-Index ${String.format(Locale.GERMAN, "%.1f", uv)} – Mittel\n" +
                        "Sonnenschutz für längere Aufenthalte im Freien empfohlen.\n\n"
                uv < 8  -> "UV-Index ${String.format(Locale.GERMAN, "%.1f", uv)} – Hoch\n" +
                        "Sonnenschutz notwendig. Mittagszeit meiden.\n\n"
                uv < 11 -> "UV-Index ${String.format(Locale.GERMAN, "%.1f", uv)} – Sehr hoch\n" +
                        "Umfassender Sonnenschutz erforderlich. Schatten bevorzugen.\n\n"
                else    -> "UV-Index ${String.format(Locale.GERMAN, "%.1f", uv)} – Extrem ⚠️\n" +
                        "Aufenthalt im Freien so kurz wie möglich halten.\n\n"
            })

            // ── Mensch ──────────────────────────────────────────────────────
            append("👤 Mensch:\n")
            append(when {
                uv < 3  -> "• Kein zwingender Sonnenschutz; bei heller Haut oder Reflexionsflächen " +
                        "(Schnee, Wasser) Sonnencreme sinnvoll.\n" +
                        "• Sonnenbrille empfohlen."
                uv < 6  -> "• Sonnencreme LSF 15–30 auf unbedeckten Hautstellen.\n" +
                        "• Kopfbedeckung bei längerem Aufenthalt.\n" +
                        "• Zwischen 11 und 15 Uhr Aufenthalt in der Sonne begrenzen."
                uv < 8  -> "• Sonnencreme LSF 30+ zwingend, auch Ohren, Nacken, Lippen.\n" +
                        "• Sonnenbrille und Kopfbedeckung tragen.\n" +
                        "• Zwischen 11 und 15 Uhr Schatten bevorzugen."
                uv < 11 -> "• Sonnencreme LSF 50+, alle 2 Stunden erneuern.\n" +
                        "• Lange Kleidung, Sonnenbrille, Hut.\n" +
                        "• Direkte Sonne zwischen 10 und 16 Uhr meiden.\n" +
                        "• Ausreichend trinken."
                else    -> "• LSF 50+ zwingend, alle 1–2 Stunden erneuern.\n" +
                        "• Nur im Schatten aufhalten oder drinnen bleiben.\n" +
                        "• UV-Schutzkleidung empfohlen.\n" +
                        "• Kinder und ältere Menschen besonders schützen."
            })

            append("\n\n")

            // ── Hund ────────────────────────────────────────────────────────
            append("🐕 Hund:\n")
            append(when {
                uv < 3  -> "• Keine besonderen Maßnahmen nötig.\n" +
                        "• Frisches Trinkwasser bereitstellen."
                uv < 6  -> "• Hund: Helle oder dünn behaarte Hunde" +
                        "sind UV-empfindlich. In der Mittagszeit Schatten bevorzugen."
                uv < 8  -> "• Training vor 10 oder nach 16 Uhr legen.\n" +
                        "• Tagsüber Schatten bevorzugen.\n" +
                        "• Asphalt und dunkle Oberflächen meiden – können Pfoten verbrennen.\n" +
                        "• Ausreichend Wasser mitführen."
                uv < 11 -> "• Training auf Morgen- oder Abendstunden verschieben.\n" +
                        "• Hund: Training auf frühe Morgen- oder Abendstunden verschieben. " +
                        "• Asphalt und Steinboden meiden – kann Pfoten überhitzen.\n" +
                        "• Anhaltende UV-Exposition erhöht bei anfälligen Rassen langfristig " +
                        "das Risiko für Erkrankungen.\n" +
                        "• Viel Trinkwasser, Pausen im Schatten."
                else    -> "• Training heute möglichst ganz vermeiden.\n" +
                        "• Notwendige Runden auf schattige Routen beschränken.\n" +
                        "• Hund regelmäßig auf der Bauchseite anfeuchten.\n" +
                        "• Überhitzungszeichen beobachten: starkes Hecheln, Speichelfluss, " +
                        "Koordinationsstörungen → sofort kühlen, Tierarzt aufsuchen.\n" +
                        "• Trinkwasser in ausreichender Menge mitführen."
            })

            append("\n\n")

            // ── Schutzmaßnahmen-Zusammenfassung ────────────────────────────
            append("🛡️ Schutzmaßnahmen ${item.time} Uhr:\n")
            append(when {
                uv < 3  -> "LSF: nicht zwingend  •  Schatten: optional  •  " +
                        "Kopfbedeckung: optional  •  Sonnenbrille: empfohlen"
                uv < 6  -> "LSF 15–30  •  Schatten: bei längerem Aufenthalt  •  " +
                        "Kopfbedeckung: empfohlen  •  Sonnenbrille: empfohlen"
                uv < 8  -> "LSF 30+  •  Schatten: bevorzugen  •  " +
                        "Kopfbedeckung: notwendig  •  Sonnenbrille: notwendig"
                uv < 11 -> "LSF 50+  •  Schatten: zwingend  •  " +
                        "Kopfbedeckung: zwingend  •  Sonnenbrille: zwingend  •  " +
                        "Lange Kleidung: empfohlen"
                else    -> "LSF 50+  •  Aufenthalt im Freien minimieren  •  " +
                        "Vollständiger Schutz: Kleidung, Hut, Brille  •  " +
                        "Kinder & Hunde: drinnen lassen"
            })

            append("\n\n⚕️ Kein Ersatz für tierärztliche oder medizinische Beratung.")
        }
    }



    private fun displayWindForecast(data: JSONObject) {
        try {
            val hourly    = data.getJSONObject("hourly")
            val times     = hourly.getJSONArray("time")
            val speeds    = hourly.getJSONArray("wind_speed_10m")
            val gusts     = hourly.getJSONArray("wind_gusts_10m")
            val degrees   = hourly.getJSONArray("wind_direction_10m")

            val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMAN)
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
            val sunshine   = daily.getJSONArray("sunshine_duration") // Sekunden

            val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMAN)

            // Index 1 = heute (Index 0 = gestern durch past_days=1)
            val todaySunrise = dateFmt.parse(sunriseArr.getString(1)) ?: return
            val todaySunset  = dateFmt.parse(sunsetArr.getString(1))  ?: return

            // ── Azimut-Offset bei Auf- und Untergang ──────────────────────────
            // Berechnung: Azimut des Sonnenaufgangs aus Breitengrad + Deklination
            val sunriseAzimuth = calcSunriseAzimuth(lat, todaySunrise)
            val sunsetAzimuth  = 360.0 - sunriseAzimuth  // symmetrisch

            // Offset vom reinen Ost-Punkt (90°) bzw. West-Punkt (270°)
            val riseOffsetDeg  = Math.abs(sunriseAzimuth - 90.0)
            val riseDirection  = if (sunriseAzimuth < 90.0) "Nord von Ost" else "Süd von Ost"
            val setOffsetDeg   = Math.abs(sunsetAzimuth - 270.0)
            val setDirection   = if (sunsetAzimuth > 270.0) "Nord von West" else "Süd von West"

            binding.sunriseAzimuthText.text =
                "🌅 Aufgang\n${timeFmt.format(todaySunrise)} Uhr\n" +
                        "${String.format(Locale.GERMAN, "%.1f", riseOffsetDeg)}° $riseDirection"

            binding.sunsetAzimuthText.text =
                "🌇 Untergang\n${timeFmt.format(todaySunset)} Uhr\n" +
                        "${String.format(Locale.GERMAN, "%.1f", setOffsetDeg)}° $setDirection"

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
                elevation < 0  -> "unter dem Horizont"
                elevation < 10 -> "sehr flach – lange Schatten"
                elevation < 30 -> "niedrig"
                elevation < 50 -> "mittel"
                else           -> "hoch"
            }
            binding.sunElevationText.text =
                "📐 Sonnenstand: ${String.format(Locale.GERMAN, "%.1f", elevation)}° ($elevDesc)\n" +
                        "🧭 Azimut: ${String.format(Locale.GERMAN, "%.1f", azimuth)}° – $azimuthCardinal"

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
                    append("👤 Person (${String.format(Locale.GERMAN, "%.2f", heightPerson)}m): " +
                            "${String.format(Locale.GERMAN, "%.2f", shadowPerson)}m  ")
                    append("🐕 Hund (${String.format(Locale.GERMAN, "%.2f", heightDog)}m): " +
                            "${String.format(Locale.GERMAN, "%.2f", shadowDog)}m  ")
                    append("📦 Objekt (${String.format(Locale.GERMAN, "%.2f", heightObject)}m): " +
                            "${String.format(Locale.GERMAN, "%.2f", shadowObject)}m")
                }
            } else {
                "Keine Schattenlänge – Sonne unter dem Horizont"
            }
            binding.shadowLengthText.text = shadowText

            // Bearbeiten-Button für Höhenwerte
            binding.shadowEditButton.setOnClickListener {
                showShadowHeightDialog(prefs) { displaySunCard(data, lat, lon) }
            }

            // ── Sonnenscheindauer heute ────────────────────────────────────────
            val todaySunshineSec = sunshine.getDouble(1)
            val shH = (todaySunshineSec / 3600).toInt()
            val shM = ((todaySunshineSec % 3600) / 60).toInt()
            binding.sunshineTodayText.text = "Heute: ${shH}h ${shM}m"

            // ── 7-Tage Sonnenschein-Balken (inline LinearLayout) ──────────────
            val dayNames = listOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")
            val dateFmtShort = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN)
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
                        else      -> android.graphics.Color.parseColor("#555555")
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

            // ── UV-Index stündlich ─────────────────────────────────────────────
            val hourly   = data.getJSONObject("hourly")
            val hTimes   = hourly.getJSONArray("time")
            val uvValues = hourly.getJSONArray("uv_index")
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
            0.0   to "N – Sonne im Norden",
            22.5  to "NNO",
            45.0  to "NO – Sonne im Nordosten",
            67.5  to "ONO",
            90.0  to "O – Sonne im Osten",
            112.5 to "OSO",
            135.0 to "SO – Sonne im Südosten",
            157.5 to "SSO",
            180.0 to "S – Sonne im Süden",
            202.5 to "SSW",
            225.0 to "SW – Sonne im Südwesten",
            247.5 to "WSW",
            270.0 to "W – Sonne im Westen",
            292.5 to "WNW",
            315.0 to "NW – Sonne im Nordwesten",
            337.5 to "NNW",
            360.0 to "N – Sonne im Norden"
        )
        return directions.minByOrNull { Math.abs(it.first - az) }?.second ?: "–"
    }

    /** Dialog zur Eingabe der Höhenwerte für die Schattenlängen-Berechnung */
    private fun showShadowHeightDialog(
        prefs: android.content.SharedPreferences,
        onDone: () -> Unit
    ) {
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val accentColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
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
                setText(String.format(Locale.GERMAN, "%.2f", defaultVal))
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#888888"))
                hint = "Meter (z.B. 1.75)"
            }
            container.addView(tv)
            container.addView(et)
            return et
        }

        val etPerson = makeField(
            "👤 Größe Person (m)",
            prefs.getFloat("height_person", 1.75f)
        )
        val etDog = makeField(
            "🐕 Widerristhöhe Hund (m)",
            prefs.getFloat("height_dog", 0.55f)
        )
        val etObject = makeField(
            "📦 Höhe Objekt (m)",
            prefs.getFloat("height_object", 1.00f)
        )

        AlertDialog.Builder(this)
            .setTitle("📏 Höhen für Schattenlänge")
            .setView(container)
            .setPositiveButton("Speichern") { _, _ ->
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
            .setNegativeButton("Abbrechen", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accentColor)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accentColor)
            }
    }

    // ── UV-Hilfsfunktionen ────────────────────────────────────────────────────

    private fun uvIndexLabel(uv: Double): String = when {
        uv < 1  -> "Minimal"
        uv < 3  -> "Gering"
        uv < 6  -> "Mittel"
        uv < 8  -> "Hoch"
        uv < 11 -> "Sehr hoch"
        else    -> "Extrem"
    }

    private fun uvIndexColor(uv: Double): Int = when {
        uv < 1  -> android.graphics.Color.parseColor("#757575")   // grau
        uv < 3  -> android.graphics.Color.parseColor("#4CAF50")   // grün
        uv < 6  -> android.graphics.Color.parseColor("#FFC107")   // gelb
        uv < 8  -> android.graphics.Color.parseColor("#FF9800")   // orange
        uv < 11 -> android.graphics.Color.parseColor("#F44336")   // rot
        else    -> android.graphics.Color.parseColor("#9C27B0")   // lila
    }

    private fun buildUvProtectionText(peakUv: Double): String {
        if (peakUv < 3.0) return ""
        return buildString {
            append("🔆 UV-Schutzempfehlung (Tages-Maximum: ${String.format(Locale.GERMAN, "%.1f", peakUv)}):\n\n")
            when {
                peakUv < 6 -> {
                    append("👤 Mensch: Sonnencreme empfohlen (LSF 15+) bei längeren Aufenthalten.\n\n")
                    append("🐕 Hund: Helle oder dünn behaarte Hunde" +
                            "sind UV-empfindlich. In der Mittagszeit Schatten bevorzugen.")
                }
                peakUv < 8 -> {
                    append("👤 Mensch: Sonnencreme LSF 30+, gegebenenfalls Kopfbedeckung und Sonnenbrille empfohlen. " +
                            "Mittagssonne meiden.\n\n")
                    append("🐕 Hund: Trainingseinheiten vor 10 oder nach 16 Uhr legen. " +
                            "Tagsüber Schatten bevorzugen.")
                }
                peakUv < 11 -> {
                    append("👤 Mensch: LSF 50+, Schatten bevorzugen, zwischen 11 und 16 Uhr dauerhaften Aufenthalt in der Sonne vermeiden.\n\n")
                    append("🐕 Hund: Training auf frühe Morgen- oder Abendstunden verschieben. " +
                            "Ausreichend Wasser mitführen." +
                            "Asphaltstrassen vermeiden" +
                            "Im schattigen Umfeld bewegen")
                }
                else -> {
                    append("⚠️ Extrem-UV! Mensch: Aufenthalt im Freien so kurz wie möglich.\n\n")
                    append("🐕 Hund: Training heute meiden. Bei notwendigen Runden: " +
                            "schattige Routen wählen, sonnige Exposition minimieren. " +
                            "Trinkwasser mitführen. Hund regelmäßig auf der Bauchseite anfeuchten.")
                }
            }
            append("\n\n⚕️ Kein Ersatz für tierärztliche Beratung.")
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

            val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMAN)
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
            binding.currentPollutantsText.text = buildString {
                append("PM2.5: ${String.format(Locale.GERMAN, "%.1f", current.pm25)} µg/m³  ")
                append("PM10: ${String.format(Locale.GERMAN, "%.1f", current.pm10)} µg/m³\n")
                append("NO₂: ${String.format(Locale.GERMAN, "%.1f", current.no2)} µg/m³  ")
                append("O₃: ${String.format(Locale.GERMAN, "%.1f", current.o3)} µg/m³")
            }

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

            // Pollenarten mit deutschen Namen und Emoji
            data class PollenSource(val key: String, val name: String, val emoji: String)
            val sources = listOf(
                PollenSource("birch_pollen",   "Birke",     "🌳"),
                PollenSource("grass_pollen",   "Gräser",    "🌾"),
                PollenSource("alder_pollen",   "Erle",      "🌿"),
                PollenSource("mugwort_pollen", "Beifuß",    "🌿"),
                PollenSource("olive_pollen",   "Olive",     "🫒"),
                PollenSource("ragweed_pollen", "Ambrosia",  "🌻")
            )

            // Erstes gültiges Stunden-Fenster (= aktuell) ermitteln
            val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
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
                "✅ Derzeit keine nennenswerte Pollenbelastung gemessen."
            } else {
                activeItems.sortedByDescending { it.level }.joinToString("\n") { item ->
                    "${item.emoji} ${item.name}: ${item.levelLabel} " +
                            "(${String.format(Locale.GERMAN, "%.1f", item.value)} Gr./m³)"
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

        val sb = StringBuilder("🐕 Hund & Pollen:\n\n")

        // Allgemeine Hinweise je Belastungsstufe
        when (highestLevel) {
            1 -> sb.append("Die aktuelle Pollenbelastung ist niedrig. Für die meisten Hunde sind " +
                    "keine besonderen Maßnahmen erforderlich.\n\n")
            2 -> sb.append("Mittlere Pollenbelastung. Beobachte Deinen Hund nach Aufenthalten im " +
                    "Freien auf mögliche Reaktionen – besonders empfindliche Hunde können " +
                    "Beschwerden zeigen.\n\n")
            3 -> sb.append("Hohe Pollenbelastung. Hunde können ähnlich wie Menschen unter " +
                    "Pollenallergien leiden – häufig als atopische Dermatitis (Hautjucken, " +
                    "Schütteln des Kopfes, Pfoten lecken).\n\n")
            4 -> sb.append("Sehr hohe Pollenbelastung. Bei bekannt allergischen Hunden unbedingt " +
                    "Rücksprache mit dem Tierarzt halten. Trainingseinheiten ggf. in die frühen " +
                    "Morgenstunden oder nach Regenfällen verlegen.\n\n")
        }

        // Pollenspezifische Hinweise
        val highItems = items.filter { it.level >= 2 }
        if (highItems.any { it.name == "Birke" || it.name == "Erle" }) {
            sb.append("🌳 Baumpollen (Birke/Erle): Kreuzreaktionen mit bestimmten Proteinen in " +
                    "Hundefutter sind möglich. Bei unklaren Verdauungsproblemen ggf. Tierarzt konsultieren.\n\n")
        }
        if (highItems.any { it.name == "Gräser" }) {
            sb.append("🌾 Gräserpollen: Häufigste Pollenallergieauslöser bei Hunden. Symptome: " +
                    "Juckreiz an Pfoten, Bauch und Ohren. Pfoten nach Spaziergängen im Gras " +
                    "abspülen kann helfen.\n\n")
        }
        if (highItems.any { it.name == "Beifuß" || it.name == "Ambrosia" }) {
            sb.append("🌿 Beifuß/Ambrosia: Kräuterpollen – Kreuzreaktionen mit Lebensmittelallergenen " +
                    "möglich. Hunde mit bekannten Futtermittelallergien können empfindlicher reagieren.\n\n")
        }

        // Allgemeine Pflegetipps bei erhöhter Belastung
        if (highestLevel >= 2) {
            sb.append("💡 Tipps bei erhöhter Belastung:\n" +
                    "• Nach Freilandrunden Fell und Pfoten abwischen\n" +
                    "• Augen und Nase beobachten (Ausfluss, Rötungen)\n" +
                    "• Häufiges Lecken der Pfoten kann auf Pollenreiz hinweisen\n" +
                    "• Schütteln und Kratzen am Kopf beobachten")
        }

        sb.append("\n\n⚕️ Kein Ersatz für tierärztliche Beratung.")
        return sb.toString()
    }

}