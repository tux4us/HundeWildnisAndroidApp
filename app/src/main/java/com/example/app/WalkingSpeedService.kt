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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Tracking gestartet", false))
        startTracking()
        isServiceRunning = true
        return START_STICKY
    }

    private fun startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        isTracking = true

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000L,
            0f,
            this
        )

        startCheckTimer()
    }

    fun stopTracking() {
        isTracking = false
        locationManager.removeUpdates(this)
        checkTimer?.cancel()
        totalDistance = 0f
        previousLocation = null
        speedUpdateListener = null
        stopForeground(true)
        stopSelf()
        isServiceRunning = false
    }

    private fun startCheckTimer() {
        checkTimer?.cancel()
        checkTimer = Timer()

        checkTimer?.schedule(checkInterval, checkInterval) {
            checkSpeed()
        }
        android.util.Log.d("WalkingSpeedService", "✓ Timer gestartet: Intervall ${checkInterval}ms")
    }

    private fun checkSpeed() {
        android.util.Log.d("WalkingSpeedService", "🔍 Prüfe Geschwindigkeit: $currentSpeed km/h (Limit: $speedLimit km/h)")

        if (currentSpeed > speedLimit) {
            android.util.Log.w("WalkingSpeedService", "⚠️ ZU SCHNELL! $currentSpeed > $speedLimit")
            notifySpeedExceeded()
        } else {
            android.util.Log.d("WalkingSpeedService", "✓ OK: $currentSpeed <= $speedLimit")
        }
    }

    private fun notifySpeedExceeded() {
        val now = System.currentTimeMillis()
        if (now - lastWarningTime < 10000) {
            android.util.Log.d("WalkingSpeedService", "⏭️ Warnung übersprungen (zu früh)")
            return
        }
        lastWarningTime = now

        android.util.Log.w("WalkingSpeedService", "🚨 BENACHRICHTIGUNG: Zu schnell!")

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
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
                ringtone.play()
            } catch (e: Exception) {
                android.util.Log.e("WalkingSpeedService", "❌ Sound-Fehler: ${e.message}")
            }
        }

        // ✅ NUR bei Warnung Notification aktualisieren
        val notification = createNotification(
            "⚠️ Zu schnell! ${String.format("%.1f", currentSpeed)} km/h (Limit: ${String.format("%.1f", speedLimit)} km/h)",
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

        speedUpdateListener?.onSpeedUpdate(currentSpeed, location)

        android.util.Log.d("WalkingSpeedService", "📍 GPS: ${String.format("%.1f", currentSpeed)} km/h")

        // ✅ KEINE Notification-Updates mehr bei normalem GPS-Update!
        // Die Foreground-Notification bleibt einfach bestehen
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Geh-Geschwindigkeit Tracking",
                NotificationManager.IMPORTANCE_LOW  // ✅ LOW = silent
            ).apply {
                description = "Benachrichtigungen für Geschwindigkeitsüberwachung"
                setSound(null, null)  // ✅ Kein Sound
                enableVibration(false)  // ✅ Keine Vibration
                setShowBadge(false)  // ✅ Kein Badge
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String, isWarning: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isWarning) "⚠️ Zu schnell!" else "🚶 Trainingstempo")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(if (isWarning) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, WalkingSpeedActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setSound(null)  // ✅ Explizit kein Sound
            .setVibrate(null)  // ✅ Explizit keine Vibration
            .setSilent(true)  // ✅ Silent-Flag
            .build()

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }

    // PUBLIC API
    fun setSpeedLimit(limit: Float) {
        speedLimit = limit
        android.util.Log.d("WalkingSpeedService", "✓ Speed Limit gesetzt: $speedLimit km/h")
    }

    fun setCheckInterval(interval: Long) {
        checkInterval = interval
        android.util.Log.d("WalkingSpeedService", "✓ Check Interval gesetzt: $checkInterval ms")
        if (isTracking) {
            startCheckTimer()
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabled = enabled
        android.util.Log.d("WalkingSpeedService", "✓ Vibration: $enabled")
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        android.util.Log.d("WalkingSpeedService", "✓ Sound: $enabled")
    }

    fun setSpeedUpdateListener(listener: SpeedUpdateListener?) {
        speedUpdateListener = listener
    }

    fun getTotalDistance() = totalDistance / 1000f

    fun getCurrentSpeed() = currentSpeed
    fun isTracking() = isTracking
}
