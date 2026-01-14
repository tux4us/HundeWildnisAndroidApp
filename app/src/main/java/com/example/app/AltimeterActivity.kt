package com.hundewildnis.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.hundewildnis.app.databinding.ActivityAltimeterBinding
import java.util.Locale
import kotlin.math.pow

class AltimeterActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityAltimeterBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null

    private var gpsAltitude: Double? = null
    private var barometerAltitude: Double? = null
    private var currentPressure: Float? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1002
        private const val SEA_LEVEL_PRESSURE = 1013.25 // hPa
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAltimeterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Höhenmesser"
            setDisplayHomeAsUpEnabled(true)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Sensor Manager für Barometer
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

        if (pressureSensor == null) {
            binding.barometerCard.visibility = View.GONE
            binding.pressureCard.visibility = View.GONE
            binding.statusText.text = "Barometer nicht verfügbar - nur GPS-Höhe"
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
                    binding.statusText.text = "Standortberechtigung benötigt"
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
        binding.statusText.text = "Höhe wird ermittelt..."

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000
        ).apply {
            setMinUpdateIntervalMillis(5000)
            setMaxUpdates(1)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateGpsAltitude(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun updateGpsAltitude(location: Location) {
        if (location.hasAltitude()) {
            gpsAltitude = location.altitude

            binding.gpsAltitudeText.text = String.format(
                Locale.GERMAN,
                "%.0f m",
                gpsAltitude
            )

            // Accuracy anzeigen
            if (location.hasVerticalAccuracy()) {
                binding.accuracyText.text = String.format(
                    Locale.GERMAN,
                    "Genauigkeit: ± %.0f m",
                    location.verticalAccuracyMeters
                )
            } else {
                binding.accuracyText.text = "Genauigkeit: unbekannt"
            }

            updateMainAltitude()
            binding.progressBar.visibility = View.GONE
            binding.statusText.text = "Höhe erfolgreich ermittelt"

        } else {
            binding.gpsAltitudeText.text = "Nicht verfügbar"
            binding.statusText.text = "GPS-Höhe nicht verfügbar"
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            currentPressure = event.values[0]

            // Luftdruck anzeigen
            binding.pressureText.text = String.format(
                Locale.GERMAN,
                "%.1f hPa",
                currentPressure
            )

            // Höhe aus Luftdruck berechnen
            barometerAltitude = calculateAltitudeFromPressure(currentPressure!!)

            binding.barometerAltitudeText.text = String.format(
                Locale.GERMAN,
                "%.0f m",
                barometerAltitude
            )

            updateMainAltitude()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Nicht benötigt
    }

    private fun calculateAltitudeFromPressure(pressure: Float): Double {
        // Barometrische Höhenformel
        // h = 44330 * (1 - (P/P0)^(1/5.255))
        return 44330 * (1.0 - (pressure / SEA_LEVEL_PRESSURE).pow(1.0 / 5.255))
    }

    private fun updateMainAltitude() {
        // Zeige bevorzugt GPS-Höhe, falls verfügbar
        val altitude = gpsAltitude ?: barometerAltitude

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