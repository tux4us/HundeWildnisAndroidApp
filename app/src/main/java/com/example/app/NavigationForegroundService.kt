package com.hundewildnis.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class NavigationForegroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID     = "nav_fg_channel"
        var isRunning            = false
        var notificationInfo     = ""
        var destinationName      = "Ziel"
        var savedDestLat: Double = 0.0
        var savedDestLon: Double = 0.0
    }

    private lateinit var nm: NotificationManager

    override fun onCreate() {
        super.onCreate()
        nm        = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        isRunning = true
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        destinationName  = intent?.getStringExtra("destination") ?: "Ziel"
        notificationInfo = intent?.getStringExtra("info") ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                "Navigation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Aktive Navigation im Hintergrund"
                setShowBadge(false)
            }.also { nm.createNotificationChannel(it) }
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, LocationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🧭 $destinationName")
            .setContentText(notificationInfo.ifEmpty { "Navigation aktiv — tippen zum Öffnen" })
            .setSmallIcon(R.drawable.ic_my_location)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}