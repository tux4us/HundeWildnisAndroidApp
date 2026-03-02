package com.hundewildnis.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.app.SecureBaseActivity

class CompassActivity : SecureBaseActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null

    // Views
    private lateinit var compassContainer: FrameLayout
    private lateinit var degreeText: TextView
    private lateinit var directionText: TextView

    // Glättungs-Variablen
    private var currentAzimuth = 0f
    private val ALPHA = 0.2f  // Glättungsfaktor (0.1 = sehr glatt, 0.3 = reaktiv)
    private val azimuthHistory = mutableListOf<Float>()
    private val HISTORY_SIZE = 5
    private var lastDisplayedAzimuth = 0
    private val MIN_CHANGE_THRESHOLD = 2  // Grad

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
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Fallback falls kein Rotation Vector vorhanden
        if (rotationSensor == null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.also { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            // Roher Azimuth-Wert
            val rawAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val azimuthDegrees = (rawAzimuth + 360) % 360

            // Glättung anwenden
            currentAzimuth = lowPassFilter(azimuthDegrees, currentAzimuth)
            val smoothedAzimuth = addToHistoryAndAverage(currentAzimuth)

            // UI nur bei signifikanter Änderung updaten
            updateCompassIfChanged(smoothedAzimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Optional: Warnung bei niedriger Genauigkeit
        when (accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> {
                android.util.Log.w("Compass", "Sensor unzuverlässig - bitte kalibrieren")
            }
        }
    }

    // Low-Pass Filter für glatte Übergänge
    private fun lowPassFilter(newValue: Float, oldValue: Float): Float {
        var delta = newValue - oldValue

        // Handle 0/360 degree wrap-around
        if (delta > 180) delta -= 360
        if (delta < -180) delta += 360

        return (oldValue + ALPHA * delta + 360) % 360
    }

    // Averaging über mehrere Werte
    private fun addToHistoryAndAverage(value: Float): Float {
        azimuthHistory.add(value)

        if (azimuthHistory.size > HISTORY_SIZE) {
            azimuthHistory.removeAt(0)
        }

        return azimuthHistory.average().toFloat()
    }

    // Update nur bei signifikanter Änderung
    private fun updateCompassIfChanged(azimuth: Float) {
        val roundedAzimuth = azimuth.toInt()

        if (Math.abs(roundedAzimuth - lastDisplayedAzimuth) >= MIN_CHANGE_THRESHOLD) {
            lastDisplayedAzimuth = roundedAzimuth
            updateCompassUI(roundedAzimuth)
        }
    }

    // UI Update
    private fun updateCompassUI(azimuth: Int) {
        // Rotiere den Kompass-Container (FrameLayout)
        // Negativ, weil Kompass gegen den Uhrzeigersinn dreht
        compassContainer.animate()
            .rotation(-azimuth.toFloat())
            .setDuration(100)  // 100ms sanfte Animation
            .start()

        // Text-Updates (rotieren NICHT mit)
        degreeText.text = "${azimuth}°"
        directionText.text = getDirectionName(azimuth)
    }

    // Himmelsrichtung als Text (Nord, Nordost, etc.)
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