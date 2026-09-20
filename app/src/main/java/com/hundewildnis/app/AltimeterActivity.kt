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
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.pow
import androidx.core.view.WindowCompat

class AltimeterActivity : SecureBaseActivity(), SensorEventListener, LocationListener {

    private lateinit var binding: ActivityAltimeterBinding
    private lateinit var locationManager: LocationManager
    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null

    private var gpsAltitude: Double? = null
    private var barometerAltitude: Double? = null
    private var currentPressure: Float? = null

    // Dynamischer Referenzdruck (QNH)
    private var dynamicSeaLevelPressure = 1013.25
    private var isQnhFetched = false

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

        binding = ActivityAltimeterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.tile_altimeter)
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
            binding.statusText.text = getString(R.string.altimeter_barometer_unavailable_status)
        } else {
            binding.statusText.text = getString(R.string.altimeter_gps_inaccurate_status)
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
                    binding.statusText.text = getString(R.string.altimeter_location_permission_needed_status)
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this,
                        getString(R.string.altimeter_location_permission_denied_toast),
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
            val mslAltitude = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE && lastLocation.hasMslAltitude()) {
                lastLocation.mslAltitudeMeters
            } else {
                lastLocation.altitude
            }
            updateGpsAltitude(lastLocation, mslAltitude)
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

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE,
                    this
                )
            }
        } catch (e: Exception) {}

        try {
            if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE,
                    this
                )
            }
        } catch (e: Exception) {}
    }

    private fun stopLocationUpdates() {
        if (isRequestingUpdates) {
            locationManager.removeUpdates(this)
            isRequestingUpdates = false
        }
    }

    override fun onLocationChanged(location: Location) {
        val mslAltitude = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE && location.hasMslAltitude()) {
            location.mslAltitudeMeters
        } else {
            location.altitude
        }

        // Fetch QNH if not done yet
        if (dynamicSeaLevelPressure == 1013.25) {
            fetchQnh(location.latitude, location.longitude)
        }

        updateGpsAltitude(location, mslAltitude)
    }

    private fun fetchQnh(lat: Double, lon: Double) {
        lifecycleScope.launch {
            try {
                val qnh = withContext(Dispatchers.IO) {
                    val url = URL("https://api.brightsky.dev/current_weather?lat=$lat&lon=$lon")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()

                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().readText()
                        val json = JsonParser.parseString(response).asJsonObject
                        if (json.has("weather")) {
                            json.getAsJsonObject("weather").get("pressure_msl").asDouble
                        } else null
                    } else null
                }

                qnh?.let {
                    dynamicSeaLevelPressure = it
                    isQnhFetched = true
                    binding.statusText.text = String.format(
                        Locale.getDefault(),
                        getString(R.string.altimeter_qnh_updated_status),
                        it
                    )
                    // Trigger redraw of barometer altitude
                    currentPressure?.let { p ->
                        if (referenceAltitude == null) {
                            barometerAltitude = calculateAltitudeFromPressure(p)
                            binding.barometerAltitudeText.text = String.format(
                                Locale.getDefault(),
                                getString(R.string.altimeter_qnh_based_format),
                                barometerAltitude
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun updateGpsAltitude(location: Location, mslAltitude: Double) {
        gpsAltitude = mslAltitude

        val accuracyInfo = if (location.hasAccuracy()) {
            val acc = location.accuracy
            when {
                acc < 10 -> getString(R.string.altimeter_accuracy_very_good)
                acc < 50 -> getString(R.string.altimeter_accuracy_good)
                acc < 100 -> getString(R.string.altimeter_accuracy_medium)
                acc < 200 -> getString(R.string.altimeter_accuracy_poor)
                else -> getString(R.string.altimeter_accuracy_very_poor)
            }
        } else {
            getString(R.string.altimeter_accuracy_unknown)
        }

        binding.gpsAltitudeText.text = String.format(
            Locale.getDefault(),
            getString(R.string.altimeter_meters_format),
            gpsAltitude
        )

        // Genauigkeit mit Warnung
        if (location.hasAccuracy()) {
            val acc = location.accuracy
            binding.accuracyText.text = String.format(
                Locale.getDefault(),
                getString(R.string.altimeter_gps_accuracy_format),
                acc,
                accuracyInfo
            )
            binding.accuracyText.setTextColor(
                if (acc > 100) android.graphics.Color.parseColor("#ff6b6b")
                else android.graphics.Color.parseColor("#999999")
            )
        } else {
            binding.accuracyText.text = getString(R.string.altimeter_gps_accuracy_unknown)
            binding.accuracyText.setTextColor(android.graphics.Color.parseColor("#ff6b6b"))
        }

        // Wenn Barometer verfügbar und noch nicht kalibriert: Kalibriere jetzt
        if (pressureSensor != null && referenceAltitude == null && currentPressure != null) {
            calibrateBarometer(gpsAltitude!!, currentPressure!!)
        }

        updateMainAltitude()
        binding.progressBar.visibility = View.GONE

        if (pressureSensor != null) {
            if (referenceAltitude != null) {
                binding.statusText.text = getString(R.string.altimeter_barometer_calibrated_status)
            }
        } else {
            binding.statusText.text = getString(R.string.altimeter_gps_only_status)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            currentPressure = event.values[0]

            binding.pressureText.text = String.format(
                Locale.getDefault(),
                getString(R.string.altimeter_pressure_format),
                currentPressure
            )

            if (referenceAltitude != null && referencePressure != null) {
                barometerAltitude = calculateCalibratedAltitude(currentPressure!!)

                binding.barometerAltitudeText.text = String.format(
                    Locale.getDefault(),
                    getString(R.string.altimeter_calibrated_format),
                    barometerAltitude
                )
            } else {
                barometerAltitude = calculateAltitudeFromPressure(currentPressure!!)

                val label = if (isQnhFetched) getString(R.string.altimeter_qnh_based_label) else getString(R.string.altimeter_not_calibrated_label)
                binding.barometerAltitudeText.text = String.format(
                    Locale.getDefault(),
                    getString(R.string.altimeter_uncalibrated_format),
                    barometerAltitude,
                    label
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
            String.format(Locale.getDefault(), getString(R.string.altimeter_calibrated_toast), altitude.toInt()),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun calculateCalibratedAltitude(currentPressure: Float): Double {
        val pressureDiff = referencePressure!! - currentPressure
        val altitudeDiff = pressureDiff * 8.3

        return referenceAltitude!! + altitudeDiff
    }

    private fun calculateAltitudeFromPressure(pressure: Float): Double {
        return 44330 * (1.0 - (pressure / dynamicSeaLevelPressure).pow(1.0 / 5.255))
    }

    private fun updateMainAltitude() {
        val altitude = if (referenceAltitude != null && barometerAltitude != null) {
            barometerAltitude
        } else {
            gpsAltitude ?: barometerAltitude
        }

        altitude?.let {
            binding.altitudeText.text = String.format(
                Locale.getDefault(),
                getString(R.string.altimeter_meters_format),
                it
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}