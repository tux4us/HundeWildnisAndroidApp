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
 * WalkingSpeedService - Background GPS Tracking Service
 * Überwacht Geschwindigkeit und benachrichtigt bei Überschreitung
 */
class WalkingSpeedService : Service(), LocationListener {

    private val binder = LocalBinder()
    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var vibrator: Vibrator

    private var speedLimit = 3.0f // km/h
    private var checkInterval = 30000L // 30 Sekunden
    private var vibrationEnabled = true
    private var soundEnabled = true

    private var currentSpeed = 0f
    private var isTracking = false
    private var checkTimer: Timer? = null

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
        // WICHTIG: Permissions prüfen BEVOR startForeground
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            // Permissions fehlen - Service kann nicht starten
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Tracking gestartet"))
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

        // GPS Updates alle 5 Sekunden
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            5000L,
            0f,
            this
        )

        // Check Timer starten
        startCheckTimer()
    }

    fun stopTracking() {
        isTracking = false
        locationManager.removeUpdates(this)
        checkTimer?.cancel()
        stopForeground(true)
        stopSelf()
        isServiceRunning = false
    }

    private fun startCheckTimer() {
        checkTimer?.cancel()
        checkTimer = Timer()

        // Erste Prüfung nach 5 Sekunden, dann wiederholt im Intervall
        checkTimer?.schedule(5000L, checkInterval) {
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
        android.util.Log.w("WalkingSpeedService", "🚨 BENACHRICHTIGUNG: Zu schnell!")

        // Vibration
        if (vibrationEnabled) {
            android.util.Log.d("WalkingSpeedService", "📳 Vibriere...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        }

        // Sound
        if (soundEnabled) {
            android.util.Log.d("WalkingSpeedService", "🔔 Spiele Sound...")
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
                ringtone.play()
            } catch (e: Exception) {
                android.util.Log.e("WalkingSpeedService", "❌ Sound-Fehler: ${e.message}")
                e.printStackTrace()
            }
        }

        // Notification aktualisieren
        val notification = createNotification(
            "⚠️ Zu schnell! ${String.format("%.1f", currentSpeed)} km/h (Limit: ${String.format("%.1f", speedLimit)} km/h)"
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
        android.util.Log.d("WalkingSpeedService", "✓ Notification aktualisiert")
    }

    override fun onLocationChanged(location: Location) {
        // Geschwindigkeit in km/h (location.speed ist in m/s)
        currentSpeed = location.speed * 3.6f

        android.util.Log.d("WalkingSpeedService", "📍 GPS: ${String.format("%.1f", currentSpeed)} km/h (${location.speed} m/s)")

        // Notification aktualisieren
        val notification = createNotification(
            "Aktuell: ${String.format("%.1f", currentSpeed)} km/h | Limit: ${String.format("%.1f", speedLimit)} km/h"
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Geh-Geschwindigkeit Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Benachrichtigungen für Geschwindigkeitsüberwachung"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("🚶 Geh-Geschwindigkeit")
        .setContentText(text)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, WalkingSpeedActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
    }

    // Public API
    fun setSpeedLimit(limit: Float) {
        speedLimit = limit
    }

    fun setCheckInterval(interval: Long) {
        checkInterval = interval
        if (isTracking) {
            startCheckTimer()
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        vibrationEnabled = enabled
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    fun getCurrentSpeed() = currentSpeed
    fun isTracking() = isTracking
}
