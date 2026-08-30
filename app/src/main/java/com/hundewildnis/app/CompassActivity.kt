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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.hundewildnis.app.SecureBaseActivity
import java.util.Locale

class CompassActivity : SecureBaseActivity(), SensorEventListener, LocationListener {

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    // Für Fallback-Berechnung
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    // Views
    private lateinit var compassContainer: FrameLayout
    private lateinit var compassRoseView: CompassRoseView
    private lateinit var degreeText: TextView
    private lateinit var directionText: TextView
    private lateinit var sunInfoText: TextView
    private lateinit var moonInfoText: TextView
    private lateinit var polarisInfoText: TextView

    // Glättungs-Variablen (optimiert für Leichtgängigkeit)
    private var currentAzimuth = 0f
    private val ALPHA = 0.35f  // Erhöht von 0.2 für schnellere Reaktion
    private val azimuthHistory = mutableListOf<Float>()
    private val HISTORY_SIZE = 3 // Reduziert von 5 für weniger Verzögerung
    private var lastDisplayedAzimuth = -1
    private val MIN_CHANGE_THRESHOLD = 1 // Reduziert von 2 für mehr Präzision

    // ── Standort (Pattern aus LocationActivity_POI übernommen) ──
    private lateinit var locationManager: LocationManager
    private var isRequestingLocationUpdates = false
    private var lastKnownLocation: Location? = null

    private val MIN_TIME_BETWEEN_UPDATES = 10_000L // 10s, Standort ändert sich für Himmelsberechnung kaum
    private val MIN_DISTANCE_CHANGE = 50f // 50m

    // ── Live-Update der Himmelsberechnung, nur während Activity im Vordergrund ──
    private val skyUpdateHandler = Handler(Looper.getMainLooper())
    private val SKY_UPDATE_INTERVAL_MS = 30_000L // alle 30s aktualisieren (Sonnen-/Mondstand ändert sich langsam)
    private val skyUpdateRunnable = object : Runnable {
        override fun run() {
            updateSkyPositions()
            skyUpdateHandler.postDelayed(this, SKY_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_compass)

        // Toolbar Setup
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = "Kompass"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        // Views initialisieren
        compassContainer = findViewById(R.id.compassContainer)
        compassRoseView = findViewById(R.id.compassRoseView)
        degreeText = findViewById(R.id.degreeText)
        directionText = findViewById(R.id.directionText)
        sunInfoText = findViewById(R.id.sunInfoText)
        moonInfoText = findViewById(R.id.moonInfoText)
        polarisInfoText = findViewById(R.id.polarisInfoText)

        // Sensor initialisieren
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // 1. Wahl: Rotation Vector (Präzise & Stabil)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // 2. Wahl: Geomagnetic Rotation Vector (Stromsparend & gut)
        if (rotationSensor == null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        }

        // 3. Wahl: Fallback (Magnetometer + Accelerometer)
        if (rotationSensor == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }

        // Standort-Manager initialisieren
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onResume() {
        super.onResume()
        // Registriere verfügbare Sensoren mit SENSOR_DELAY_GAME für hohe Reaktivität
        rotationSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: run {
            accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            magnetometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }

        startLocationUpdates()

        // Himmelsberechnung nur starten, wenn Activity im Fokus ist
        skyUpdateHandler.post(skyUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopLocationUpdates()
        skyUpdateHandler.removeCallbacks(skyUpdateRunnable)
    }

    // ─────────────────────────────────────────────────────────
    // Standort (identisches Pattern wie in LocationActivity_POI)
    // ─────────────────────────────────────────────────────────

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            polarisInfoText.text = "Standortberechtigung fehlt – Himmelsberechnung nicht möglich"
            sunInfoText.text = ""
            moonInfoText.text = ""
            return
        }

        // Letzten bekannten Standort sofort verwenden, falls vorhanden
        var bestLocation: Location? = null
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        bestLocation?.let {
            lastKnownLocation = it
            updateSkyPositions()
        }

        requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        isRequestingLocationUpdates = true
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_CHANGE, this
            )
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_CHANGE, this
            )
        }
    }

    private fun stopLocationUpdates() {
        if (isRequestingLocationUpdates) {
            locationManager.removeUpdates(this)
            isRequestingLocationUpdates = false
        }
    }

    override fun onLocationChanged(location: Location) {
        lastKnownLocation = location
        updateSkyPositions()
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    // ─────────────────────────────────────────────────────────
    // Himmelsberechnung (Sonne, Mond, Polaris/Großer Wagen)
    // ─────────────────────────────────────────────────────────

    private fun updateSkyPositions() {
        val location = lastKnownLocation ?: run {
            sunInfoText.text = "☀ Sonne: Standort wird ermittelt …"
            moonInfoText.text = "☾ Mond: Standort wird ermittelt …"
            polarisInfoText.text = "★ Polaris: Standort wird ermittelt …"
            return
        }

        val sky = SkyCalculator.calculate(location.latitude, location.longitude)

        // Text-Panel aktualisieren
        sunInfoText.text = formatSkyLine("☀ Sonne", sky.sun)
        moonInfoText.text = formatSkyLine("☾ Mond", sky.moon) +
                String.format(Locale.GERMAN, " · Beleuchtung: %.0f%%", sky.moonIlluminationPercent)
        val polarisLine = formatSkyLine("★ Polaris", sky.polarisResult.polaris)
        polarisInfoText.text = "$polarisLine · Großer Wagen: ${sky.polarisResult.greatBearClockPosition} Uhr (zu Polaris)"

        // Positionen an die Kompassrose übergeben (zeichnet Icons am Ring)
        compassRoseView.updateSkyData(sky)
    }

    private fun formatSkyLine(label: String, pos: SkyCalculator.SkyPosition): String {
        val visibility = if (pos.isVisible) "" else " (unter Horizont)"
        return String.format(
            Locale.GERMAN,
            "%s: %.0f° %s, Höhe %.0f°%s",
            label, pos.azimuth, getDirectionName(pos.azimuth.toInt()), pos.altitude, visibility
        )
    }

    // ─────────────────────────────────────────────────────────
    // Kompass-Sensorik (unverändert)
    // ─────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent?) {
        val type = event?.sensor?.type ?: return

        if (type == Sensor.TYPE_ROTATION_VECTOR || type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            processRotationMatrix(rotationMatrix)
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, event.values.size)
            hasGravity = true
            tryComputeAzimuth()
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
            hasGeomagnetic = true
            tryComputeAzimuth()
        }
    }

    private fun tryComputeAzimuth() {
        if (hasGravity && hasGeomagnetic) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                processRotationMatrix(r)
            }
        }
    }

    private fun processRotationMatrix(rotationMatrix: FloatArray) {
        val outR = FloatArray(9)

        // Berücksichtigung der Display-Rotation (Wichtig für Genauigkeit)
        val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }

        when (displayRotation) {
            Surface.ROTATION_0 -> System.arraycopy(rotationMatrix, 0, outR, 0, 9)
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, outR)
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, outR)
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, outR)
        }

        val orientation = FloatArray(3)
        SensorManager.getOrientation(outR, orientation)

        // Azimuth (Z-Achse) in Grad umrechnen
        val rawAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val azimuthDegrees = (rawAzimuth + 360) % 360

        // Glättung
        currentAzimuth = lowPassFilter(azimuthDegrees, currentAzimuth)
        val smoothedAzimuth = addToHistoryAndAverage(currentAzimuth)

        updateCompassIfChanged(smoothedAzimuth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            findViewById<TextView>(R.id.infoText)?.text = "⚠️ Kompass ungenau! Bitte bewege dein Gerät in einer 8er-Form, um den Sensor zu kalibrieren."
        } else {
            findViewById<TextView>(R.id.infoText)?.text = "Bewege Dein Phone großräumig und mehrmals in Form einer Acht, um den Kompass zu kalibrieren. Halte Dein Phone von magnetischen Quellen fern!"
        }
    }

    private fun lowPassFilter(newValue: Float, oldValue: Float): Float {
        var delta = newValue - oldValue
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360
        return (oldValue + ALPHA * delta + 360) % 360
    }

    private fun addToHistoryAndAverage(value: Float): Float {
        azimuthHistory.add(value)
        if (azimuthHistory.size > HISTORY_SIZE) azimuthHistory.removeAt(0)
        return azimuthHistory.average().toFloat()
    }

    private fun updateCompassIfChanged(azimuth: Float) {
        val roundedAzimuth = azimuth.toInt()
        if (Math.abs(roundedAzimuth - lastDisplayedAzimuth) >= MIN_CHANGE_THRESHOLD) {
            lastDisplayedAzimuth = roundedAzimuth
            updateCompassUI(roundedAzimuth)
        }
    }

    private fun updateCompassUI(azimuth: Int) {
        // Direkte Rotation ohne Animation für maximale Leichtgängigkeit
        // Negativ, da sich die Rose gegen die Drehrichtung des Geräts bewegen muss
        compassContainer.rotation = -azimuth.toFloat()

        degreeText.text = "${azimuth}°"
        directionText.text = getDirectionName(azimuth)
    }

    private fun getDirectionName(azimuth: Int): String {
        return when {
            azimuth in 0..22 || azimuth in 338..360 -> "Nord"
            azimuth in 23..67 -> "Nordost"
            azimuth in 68..112 -> "Ost"
            azimuth in 113..157 -> "Südost"
            azimuth in 158..202 -> "Süd"
            azimuth in 203..247 -> "Südwest"
            azimuth in 248..292 -> "West"
            azimuth in 293..337 -> "Nordwest"
            else -> "Nord"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
