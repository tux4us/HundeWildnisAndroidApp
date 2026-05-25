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
import android.content.Context
import android.graphics.*
import android.util.AttributeSet


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
            loadWeather(bestLocation)
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
        loadWeather(location)
        stopLocationUpdates()
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun loadWeather(location: Location) {
        currentLocation = location
        binding.locationText.text = String.format(Locale.GERMAN, "📍 %.4f°, %.4f°", location.latitude, location.longitude)
        lifecycleScope.launch {
            try {
                val weatherData = withContext(Dispatchers.IO) { fetchWeatherData(location.latitude, location.longitude) }
                displayWeatherData(weatherData)
                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.descriptionText.text = "Fehler beim Laden der Wetterdaten"
                Toast.makeText(this@WeatherActivity, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fetchWeatherData(latitude: Double, longitude: Double): JSONObject {
        val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature," +
                "precipitation,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m," +
                "wind_gusts_10m,uv_index" +
                "&hourly=temperature_2m,precipitation,precipitation_probability,weather_code," +
                "apparent_temperature" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset," +
                "weather_code,precipitation_probability_max" +
                "&past_days=1" +
                "&forecast_days=2" +
                "&timezone=auto"
        val response = URL(url).readText()
        return JSONObject(response)
    }

    // ════════════════════════════════════════════════════════════════
    // displayWeatherData – Haupteinstieg nach API-Antwort
    // ════════════════════════════════════════════════════════════════

    private fun displayWeatherData(data: JSONObject) {
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
        binding.windStrengthText.text = getBeaufortDescription(beaufort)
        binding.windText.text = String.format(Locale.GERMAN, "%.1f km/h %s (Bft %d)\nBöen: %.1f km/h", windSpeed, windDirectionText, beaufort, windGusts)

        binding.humidityText.text = "$humidity%"
        binding.precipitationText.text = String.format(Locale.GERMAN, "%.1f mm", precipitation)
        binding.pressureText.text = String.format(Locale.GERMAN, "%.0f hPa", pressure)
        binding.visibilityText.text = "> 10 km"
        binding.uvIndexText.text = String.format(Locale.GERMAN, "%.1f", uvIndex)

        displaySunriseSunset(data)
        display24HourForecast(data)
        displayDailyOverview(data)
        setupRainRadar()
        displayPawWeather(data, windSpeed, windGusts)
        displayTemperatureChart(data)
        displayPrecipitationChart(data)
        displayDaylightInfo(data)
        displayMoonPhase()          // ← Mondphase-Aufruf (keine Parameter nötig)
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

}