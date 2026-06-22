package com.hundewildnis.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * LocationShareService – Foreground Service für die gegenseitige Standortfreigabe.
 *
 * Läuft als Foreground Service mit foregroundServiceType="location" (analog zu
 * WalkingSpeedService und NavigationForegroundService).
 *
 * Ablauf:
 * 1. GPS-Updates via LocationManager empfangen (minTime 30s, minDistance 0m)
 * 2. Alle 60 Sekunden den letzten bekannten Standort als JSON auf WebDAV hochladen
 *    Dateiname: location_[DeviceId].json im Verzeichnis /HundeWildnis/
 * 3. Beim Stopp: eigene JSON-Datei vom Server löschen (optionales DELETE –
 *    da HttpURLConnection DELETE unterstützt, kein Reflection nötig)
 *
 * Das JSON-Format ist bewusst minimal gehalten, damit es auch von
 * zukünftigen Erweiterungen (z.B. Hund-Tracker) lesbar bleibt.
 */
class LocationShareService : Service(), LocationListener {

    companion object {
        private const val TAG = "LocationShareService"
        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "location_share_channel"

        /** Präfix für Standort-JSON-Dateien auf dem WebDAV-Server. */
        const val LOCATION_FILE_PREFIX = "location_"
        const val LOCATION_FILE_SUFFIX = ".json"

        /** Upload-Intervall in Millisekunden. */
        private const val UPLOAD_INTERVAL_MS = 60_000L

        /** GPS-Update-Intervall: alle 30s (zwischen zwei Uploads je ein frischer Fix). */
        private const val GPS_MIN_TIME_MS = 30_000L

        /** Kein Mindest-Abstand – wir wollen auch Standort-Updates ohne Bewegung. */
        private const val GPS_MIN_DISTANCE_M = 0f

        /** SharedPreferences-Key für den konfigurierten Anzeigenamen dieses Geräts. */
        const val PREF_LOCATION_LABEL = "location_share_label"

        var isServiceRunning = false

        /** Liefert den Dateinamen für dieses Gerät, z.B. "location_Pixel_6_a1b2c3d4.json". */
        fun remoteFileName(context: Context): String =
            "$LOCATION_FILE_PREFIX${WebDavSyncManager.getDeviceId(context)}$LOCATION_FILE_SUFFIX"
    }

    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    /** Letzter bekannter GPS-Standort – wird von onLocationChanged() aktualisiert. */
    @Volatile
    private var lastLocation: Location? = null

    private val gson = Gson()
    private val prefs by lazy { WebDavPreferences(this) }

    // ── Service-Lifecycle ──────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground IMMER sofort starten, um Crash (ForegroundServiceDidNotStartInTimeException) zu vermeiden,
        // falls startForegroundService() aufgerufen wurde.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification("Standortfreigabe aktiv"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Standortfreigabe aktiv"))
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Standortberechtigung fehlt – Service wird nicht gestartet")
            stopSelf()
            return START_NOT_STICKY
        }

        if (isServiceRunning) {
            Log.d(TAG, "Service läuft bereits – Loops werden nicht erneut gestartet")
            return START_STICKY
        }

        isServiceRunning = true
        startGpsUpdates()
        startUploadLoop()

        Log.d(TAG, "LocationShareService gestartet")
        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        locationManager.removeUpdates(this)
        serviceScope.launch {
            deleteOwnLocationFile()
        }
        // Kurzes Delay damit der Delete-Upload noch abgeschlossen werden kann,
        // bevor der Scope gecancelt wird.
        Thread.sleep(2000)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── GPS ───────────────────────────────────────────────────────────────

    private fun startGpsUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_MIN_TIME_MS,
                GPS_MIN_DISTANCE_M,
                this
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "GPS-Berechtigung entzogen", e)
            stopSelf()
        }
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location
        Log.d(TAG, "GPS-Update: ${location.latitude}, ${location.longitude}")
    }

    @Deprecated("Deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ── Upload-Loop ───────────────────────────────────────────────────────

    /**
     * Coroutine-Loop auf Dispatchers.IO: alle 60s den aktuellen Standort hochladen.
     * Wenn noch kein GPS-Fix vorliegt, wird der Upload übersprungen (kein Dummy-Wert).
     */
    private fun startUploadLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(UPLOAD_INTERVAL_MS)
                val location = lastLocation
                if (location != null) {
                    uploadLocation(location)
                } else {
                    Log.d(TAG, "Noch kein GPS-Fix – Upload übersprungen")
                }
            }
        }
    }

    private fun uploadLocation(location: Location) {
        val config = prefs.loadConfig()
        if (config == null) {
            Log.w(TAG, "Keine WebDAV-Zugangsdaten – Upload nicht möglich")
            return
        }

        val label = getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(PREF_LOCATION_LABEL, Build.MODEL) ?: Build.MODEL

        val payload = LocationPayload(
            deviceId = WebDavSyncManager.getDeviceId(this),
            label = label,
            lat = location.latitude,
            lon = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            timestamp = System.currentTimeMillis(),
            batteryLevel = getBatteryLevel()
        )

        val json = gson.toJson(payload)
        val tempFile = File(cacheDir, remoteFileName(this))
        tempFile.writeText(json)

        val client = WebDavClient(config.serverUrl, config.username, config.password)
        val remotePath = "${WebDavSyncManager.REMOTE_DIR}/${remoteFileName(this)}"

        val result = client.uploadFile(remotePath, tempFile)
        tempFile.delete()

        if (result.isSuccess) {
            Log.d(TAG, "Standort hochgeladen: ${location.latitude}, ${location.longitude}")
            updateNotification("Zuletzt gesendet: ${formatTime(payload.timestamp)}")
        } else {
            Log.w(TAG, "Upload fehlgeschlagen: ${result.exceptionOrNull()?.message}")
        }
    }

    /**
     * Löscht die eigene Standort-Datei vom Server (HTTP DELETE).
     * Damit erscheint das Gerät nach dem Stopp nicht mehr auf anderen Geräten.
     * Fehler werden nur geloggt – der Service stoppt so oder so.
     */
    private fun deleteOwnLocationFile() {
        val config = prefs.loadConfig() ?: return
        val remotePath = "${WebDavSyncManager.REMOTE_DIR}/${remoteFileName(this)}"

        try {
            val url = java.net.URL(
                config.serverUrl.trimEnd('/') + "/$remotePath"
            )
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "DELETE"
            val raw = "${config.username}:${config.password}"
            val auth = "Basic " + android.util.Base64.encodeToString(
                raw.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
            )
            conn.setRequestProperty("Authorization", auth)
            conn.setRequestProperty("User-Agent", "HundeWildnisApp/14.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val code = conn.responseCode
            conn.disconnect()
            Log.d(TAG, "DELETE eigene Standortdatei: HTTP $code")
        } catch (e: Exception) {
            Log.w(TAG, "DELETE fehlgeschlagen (ignoriert): ${e.message}")
        }
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                CHANNEL_ID,
                "Standortfreigabe",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Aktive Standortfreigabe im Hintergrund"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }.also { notificationManager.createNotificationChannel(it) }
        }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 Standortfreigabe")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_my_location)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, LocationShareActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ── Hilfsfunktionen ───────────────────────────────────────────────────

    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    private fun getBatteryLevel(): Int {
        val intent = registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
}

/**
 * Datenklasse für das JSON, das auf dem WebDAV-Server abgelegt wird.
 * Bewusst flach und ohne verschachtelte Objekte gehalten.
 */
data class LocationPayload(
    val deviceId: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Float?,
    val timestamp: Long,
    val batteryLevel: Int? = null
)
