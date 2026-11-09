package com.hundewildnis.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Hält die Messung des Sprach-Trainers am Leben, wenn das Handy in der Tasche steckt oder der
 * Bildschirm ausgeht.
 *
 * Der Dienst führt selbst keine Aufnahme durch: Mikrofon, Erkennung und GPS-Auswertung laufen
 * weiter in [CommunicationActivity]. Der Dienst stellt nur die Voraussetzungen bereit, die Android
 * für Mikrofon- und Standortzugriff im Hintergrund verlangt:
 * - Vordergrunddienst mit den Typen microphone (und location, wenn die Wegstrecke erfasst wird)
 *   samt dauerhafter Benachrichtigung,
 * - eine Teil-Wakelock, damit die CPU bei ausgeschaltetem Bildschirm weiterläuft.
 *
 * Start und Stopp erfolgen ausschließlich durch die Activity ([start], [stop]); der Dienst
 * speichert und überträgt nichts.
 */
class VoiceSessionService : Service() {

    companion object {
        private const val TAG = "VoiceSessionService"
        private const val CHANNEL_ID = "voice_session_channel"
        private const val NOTIFICATION_ID = 1007
        private const val EXTRA_USE_LOCATION = "use_location"

        /** Obergrenze der Wakelock-Dauer; eine Messung dauert normalerweise Minuten bis wenige Stunden. */
        private const val WAKELOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L

        @Volatile
        var isRunning = false
            private set

        /**
         * Startet den Dienst aus dem Vordergrund (Activity sichtbar). Gibt false zurück, wenn
         * Android den Start verweigert; die Messung läuft dann ohne Hintergrundbetrieb weiter.
         */
        fun start(context: Context, useLocation: Boolean): Boolean {
            return try {
                val intent = Intent(context, VoiceSessionService::class.java)
                    .putExtra(EXTRA_USE_LOCATION, useLocation)
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Dienst konnte nicht gestartet werden: ${e.message}")
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, VoiceSessionService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Dienst konnte nicht beendet werden: ${e.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val useLocation = intent?.getBooleanExtra(EXTRA_USE_LOCATION, false) == true &&
                hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        createChannel()
        val notification = buildNotification()
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    if (useLocation) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    startForeground(NOTIFICATION_ID, notification, type)
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && useLocation -> {
                    startForeground(
                        NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                }
                else -> startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Android verweigert den Vordergrunddienst (z. B. Berechtigung oder Systemvorgabe)
            Log.w(TAG, "startForeground fehlgeschlagen: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        isRunning = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        releaseWakeLock()
        super.onDestroy()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HundeWildnis:VoiceSession").apply {
                setReferenceCounted(false)
                acquire(WAKELOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wakelock nicht verfügbar: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Wakelock-Freigabe: ${e.message}")
        }
        wakeLock = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.comm_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.comm_service_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, CommunicationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.comm_service_notification_title))
            .setContentText(getString(R.string.comm_service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .build()
    }
}
