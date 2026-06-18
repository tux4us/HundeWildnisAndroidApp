package com.hundewildnis.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Surface
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.hundewildnis.app.SecureBaseActivity

class CompassActivity : SecureBaseActivity(), SensorEventListener {

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
    private lateinit var degreeText: TextView
    private lateinit var directionText: TextView

    // Glättungs-Variablen (optimiert für Leichtgängigkeit)
    private var currentAzimuth = 0f
    private val ALPHA = 0.35f  // Erhöht von 0.2 für schnellere Reaktion
    private val azimuthHistory = mutableListOf<Float>()
    private val HISTORY_SIZE = 3 // Reduziert von 5 für weniger Verzögerung
    private var lastDisplayedAzimuth = -1
    private val MIN_CHANGE_THRESHOLD = 1 // Reduziert von 2 für mehr Präzision

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
        degreeText = findViewById(R.id.degreeText)
        directionText = findViewById(R.id.directionText)

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
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

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
        val displayRotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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
            findViewById<TextView>(R.id.infoText)?.text = "Bewege dein Gerät, um den Kompass zu kalibrieren und halte Dein Gerät von magnetischen Quellen fern!"
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
