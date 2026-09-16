package com.hundewildnis.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.hundewildnis.app.R
import java.util.Timer
import kotlin.concurrent.schedule

/**
 * Callback-Interface für Activity
 */
interface SpeedUpdateListener {
    fun onSpeedUpdate(speed: Float, location: Location)
}

/**
 * WalkingSpeedService - Background GPS Tracking Service
 */
class WalkingSpeedService : Service(), LocationListener {

    private val binder = LocalBinder()
    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var vibrator: Vibrator

    private var speedLimit = 3.0f
    private var checkInterval = 30000L
    private var vibrationEnabled = true
    private var soundEnabled = true

    private var currentSpeed = 0f
    private var isTracking = false
    private var checkTimer: Timer? = null

    private var lastWarningTime = 0L

    private var speedUpdateListener: SpeedUpdateListener? = null

    private var previousLocation: Location? = null
    private var totalDistance = 0f

    // Session Daten
    private val speedDataPoints = mutableListOf<SpeedDataPoint>()
    private var sessionStartTime = 0L
    private var exceedCount = 0

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "walking_speed_channel"
        var isServiceRunning = false
    }

    inner class LocalBinder : Binder() {
        fun getService(): WalkingSpeedService = this@WalkingSpeedService
    }

    override fun onCreate() {
        super.onCreate()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Berechtigungen prüfen
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }

        // ✅ FIX: Nur starten, wenn nicht bereits getrackt wird (verhindert Datenverlust beim Wiederöffnen)
        if (!isTracking) {
            startForeground(NOTIFICATION_ID, createNotification(getString(R.string.walking_speed_service_tracking_started), false))
            startTracking()
        }

        isServiceRunning = true
        return START_STICKY
    }

    private fun startTracking() {
        isTracking = true
        sessionStartTime = System.currentTimeMillis()
        speedDataPoints.clear()
        totalDistance = 0f
        exceedCount = 0
        previousLocation = null

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                0f,
                this
            )
            startCheckTimer()
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    fun stopTracking() {
        isTracking = false
        locationManager.removeUpdates(this)
        checkTimer?.cancel()
        speedUpdateListener = null
        stopForeground(true)
        stopSelf()
        isServiceRunning = false
    }

    private fun startCheckTimer() {
        checkTimer?.cancel()
        checkTimer = Timer()

        checkTimer?.schedule(checkInterval, checkInterval) {
            if (isTracking) {
                checkSpeed()
            }
        }
    }

    private fun checkSpeed() {
        if (currentSpeed > speedLimit) {
            notifySpeedExceeded()
        }
    }

    private fun notifySpeedExceeded() {
        val now = System.currentTimeMillis()
        // 10 Sekunden Cooldown zwischen Warnungen
        if (now - lastWarningTime < 10000) return
        lastWarningTime = now

        // Vibration
        if (vibrationEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        }

        // Sound
        if (soundEnabled) {
            try {
                val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
                ringtone.play()
            } catch (e: Exception) {
                android.util.Log.e("WalkingSpeedService", "Sound error: ${e.message}")
            }
        }

        // Notification aktualisieren (Wichtig für Sperrbildschirm)
        val notification = createNotification(
            getString(R.string.walking_speed_service_too_fast, String.format("%.1f", currentSpeed), String.format("%.1f", speedLimit)),
            true
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onLocationChanged(location: Location) {
        currentSpeed = location.speed * 3.6f

        previousLocation?.let { prev ->
            val distance = prev.distanceTo(location)
            totalDistance += distance
        }
        previousLocation = location

        val dataPoint = SpeedDataPoint(
            timestamp = System.currentTimeMillis(),
            speed = currentSpeed,
            latitude = location.latitude,
            longitude = location.longitude
        )
        speedDataPoints.add(dataPoint)

        if (currentSpeed > speedLimit) {
            exceedCount++
        }

        speedUpdateListener?.onSpeedUpdate(currentSpeed, location)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.walking_speed_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.walking_speed_service_channel_description)
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String, isWarning: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isWarning) getString(R.string.walking_speed_service_too_fast_title) else getString(R.string.walking_speed_service_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(if (isWarning) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, WalkingSpeedActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setSilent(true) // Wir steuern Sound/Vibration manuell
            .build()

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }

    // PUBLIC API
    fun setSpeedLimit(limit: Float) { speedLimit = limit }
    fun setCheckInterval(interval: Long) {
        checkInterval = interval
        if (isTracking) startCheckTimer()
    }
    fun setVibrationEnabled(enabled: Boolean) { vibrationEnabled = enabled }
    fun setSoundEnabled(enabled: Boolean) { soundEnabled = enabled }
    fun setSpeedUpdateListener(listener: SpeedUpdateListener?) { speedUpdateListener = listener }
    fun getTotalDistance() = totalDistance / 1000f
    fun getCurrentSpeed() = currentSpeed
    fun isTracking() = isTracking
    fun getSpeedDataPoints(): List<SpeedDataPoint> = speedDataPoints.toList()
    fun getSessionStartTime(): Long = sessionStartTime
    fun getExceedCount(): Int = exceedCount
}
