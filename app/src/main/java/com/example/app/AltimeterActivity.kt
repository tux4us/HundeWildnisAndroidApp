package com.hundewildnis.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hundewildnis.app.databinding.ActivityAltimeterBinding
import java.util.Locale
import kotlin.math.pow
import androidx.core.view.WindowCompat
import com.example.app.SecureBaseActivity

class AltimeterActivity : SecureBaseActivity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityAltimeterBinding
    private lateinit var locationManager: LocationManager
    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null

    private var gpsAltitude: Double? = null
    private var barometerAltitude: Double? = null
    private var currentPressure: Float? = null

    // Für Barometer-Kalibrierung
    private var referenceAltitude: Double? = null
    private var referencePressure: Float? = null
    private var isRequestingUpdates = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1002
        private const val SEA_LEVEL_PRESSURE = 1013.25 // hPa
        private const val MIN_TIME_BETWEEN_UPDATES = 2000L // 2 Sekunden
        private const val MIN_DISTANCE_CHANGE = 0f // Jede Änderung
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge aktivieren
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityAltimeterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Höhenmesser"
            setDisplayHomeAsUpEnabled(true)
        }

        // WICHTIG: Navigation Icon weiß machen
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // Sensor Manager für Barometer
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

        if (pressureSensor == null) {
            binding.barometerCard.visibility = View.GONE
            binding.pressureCard.visibility = View.GONE
            binding.statusText.text = "⚠️ Barometer nicht verfügbar\nNur GPS-Höhe (ungenau)"
        } else {
            binding.statusText.text = "📡 GPS-Höhe ist sehr ungenau!\n🌡️ Barometer wird für Genauigkeit genutzt"
        }

        checkLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        pressureSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        stopLocationUpdates()
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getAltitude()
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
                    getAltitude()
                } else {
                    binding.statusText.text = "❌ Standortberechtigung benötigt"
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

    private fun getAltitude() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        binding.progressBar.visibility = View.VISIBLE

        // Versuche zuerst letzte bekannte Position
        var lastLocation: Location? = null

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }

        if (lastLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }

        if (lastLocation != null && lastLocation.hasAltitude()) {
            updateGpsAltitude(lastLocation)
        }

        // Fordere neue Updates an
        requestLocationUpdates()
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
        if (location.hasAltitude()) {
            updateGpsAltitude(location)
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun updateGpsAltitude(location: Location) {
        if (location.hasAltitude()) {
            gpsAltitude = location.altitude

            val accuracyInfo = if (location.hasAccuracy()) {
                val acc = location.accuracy
                when {
                    acc < 10 -> "sehr gut"
                    acc < 50 -> "gut"
                    acc < 100 -> "mittel"
                    acc < 200 -> "schlecht"
                    else -> "sehr schlecht"
                }
            } else {
                "unbekannt"
            }

            binding.gpsAltitudeText.text = String.format(
                Locale.GERMAN,
                "%.0f m",
                gpsAltitude
            )

            // Genauigkeit mit Warnung
            if (location.hasAccuracy()) {
                val acc = location.accuracy
                binding.accuracyText.text = String.format(
                    Locale.GERMAN,
                    "GPS-Genauigkeit: ± %.0f m (%s)\n⚠️ GPS-Höhe ist sehr ungenau!",
                    acc,
                    accuracyInfo
                )
                binding.accuracyText.setTextColor(
                    if (acc > 100) android.graphics.Color.parseColor("#ff6b6b")
                    else android.graphics.Color.parseColor("#999999")
                )
            } else {
                binding.accuracyText.text = "GPS-Genauigkeit: unbekannt\n⚠️ GPS-Höhe ist sehr ungenau!"
                binding.accuracyText.setTextColor(android.graphics.Color.parseColor("#ff6b6b"))
            }

            // Wenn Barometer verfügbar und noch nicht kalibriert: Kalibriere jetzt
            if (pressureSensor != null && referenceAltitude == null && currentPressure != null) {
                calibrateBarometer(gpsAltitude!!, currentPressure!!)
            }

            updateMainAltitude()
            binding.progressBar.visibility = View.GONE

            if (pressureSensor != null) {
                binding.statusText.text = "✅ Barometer kalibriert\n🎯 Nutze Barometer für genaue Höhe"
            } else {
                binding.statusText.text = "⚠️ Nur GPS verfügbar (sehr ungenau)"
            }

        } else {
            binding.gpsAltitudeText.text = "Nicht verfügbar"
            binding.statusText.text = "❌ GPS-Höhe nicht verfügbar"
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            currentPressure = event.values[0]

            binding.pressureText.text = String.format(
                Locale.GERMAN,
                "%.1f hPa",
                currentPressure
            )

            if (referenceAltitude != null && referencePressure != null) {
                barometerAltitude = calculateCalibratedAltitude(currentPressure!!)

                binding.barometerAltitudeText.text = String.format(
                    Locale.GERMAN,
                    "%.1f m (kalibriert)",
                    barometerAltitude
                )
            } else {
                barometerAltitude = calculateAltitudeFromPressure(currentPressure!!)

                binding.barometerAltitudeText.text = String.format(
                    Locale.GERMAN,
                    "%.0f m (nicht kalibriert)",
                    barometerAltitude
                )
            }

            updateMainAltitude()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calibrateBarometer(altitude: Double, pressure: Float) {
        referenceAltitude = altitude
        referencePressure = pressure

        Toast.makeText(
            this,
            "✅ Barometer kalibriert auf ${altitude.toInt()}m",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun calculateCalibratedAltitude(currentPressure: Float): Double {
        val pressureDiff = referencePressure!! - currentPressure
        val altitudeDiff = pressureDiff * 8.3

        return referenceAltitude!! + altitudeDiff
    }

    private fun calculateAltitudeFromPressure(pressure: Float): Double {
        return 44330 * (1.0 - (pressure / SEA_LEVEL_PRESSURE).pow(1.0 / 5.255))
    }

    private fun updateMainAltitude() {
        val altitude = if (referenceAltitude != null && barometerAltitude != null) {
            barometerAltitude
        } else {
            gpsAltitude ?: barometerAltitude
        }

        altitude?.let {
            binding.altitudeText.text = String.format(
                Locale.GERMAN,
                "%.0f m",
                it
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}