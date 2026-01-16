package com.hundewildnis.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hundewildnis.app.databinding.ActivityCompassBinding
import kotlin.math.roundToInt
import androidx.core.view.WindowCompat

class CompassActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityCompassBinding
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private var lastAccelerometer = FloatArray(3)
    private var lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false

    private var currentDegree = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge aktivieren
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityCompassBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Kompass"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        // Sensor Manager initialisieren
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accelerometer == null || magnetometer == null) {
            Toast.makeText(
                this,
                "Sensoren nicht verfügbar. Kompass funktioniert nicht auf diesem Gerät.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
                lastAccelerometerSet = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
                lastMagnetometerSet = true
            }
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            val r = FloatArray(9)
            val success = SensorManager.getRotationMatrix(r, null, lastAccelerometer, lastMagnetometer)

            if (success) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)

                // Azimuth (Ausrichtung) in Grad umrechnen
                val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val degree = (azimuth + 360) % 360

                updateCompass(degree)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Nicht benötigt, aber muss implementiert werden
    }

    private fun updateCompass(degree: Float) {
        // Grad-Anzeige aktualisieren
        binding.degreeText.text = "${degree.roundToInt()}°"

        // Richtungstext aktualisieren
        binding.directionText.text = getDirectionText(degree)

        // Kompass-Bild rotieren
        val rotateAnimation = RotateAnimation(
            currentDegree,
            -degree,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        rotateAnimation.duration = 210
        rotateAnimation.fillAfter = true

        binding.compassImage.startAnimation(rotateAnimation)
        currentDegree = -degree
    }

    private fun getDirectionText(degree: Float): String {
        return when {
            degree >= 337.5 || degree < 22.5 -> "Blickrichtung Nord"
            degree >= 22.5 && degree < 67.5 -> "Blickrichtung Nordost"
            degree >= 67.5 && degree < 112.5 -> "Blickrichtung Ost"
            degree >= 112.5 && degree < 157.5 -> "Blickrichtung Südost"
            degree >= 157.5 && degree < 202.5 -> "Blickrichtung Süd"
            degree >= 202.5 && degree < 247.5 -> "Blickrichtung Südwest"
            degree >= 247.5 && degree < 292.5 -> "Blickrichtung West"
            degree >= 292.5 && degree < 337.5 -> "Blickrichtung Nordwest"
            else -> "Nord"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}