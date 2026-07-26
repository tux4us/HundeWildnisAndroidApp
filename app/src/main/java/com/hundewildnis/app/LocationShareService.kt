package com.hundewildnis.app

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.File
import java.util.*

/**
 * Hintergrunddienst für die Standortfreigabe.
 * Erfasst GPS-Daten und lädt diese regelmäßig auf den WebDAV-Server hoch.
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
        private const val UPLOAD_INTERVAL_MS = 30_000L

        /** GPS-Update-Intervall: alle 10s für bessere Reaktivität. */
        private const val GPS_MIN_TIME_MS = 10_000L

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
    private var wakeLock: android.os.PowerManager.WakeLock? = null

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

        // WakeLock anfordern, um CPU-Schlaf im Hintergrund zu verhindern
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "HundeWildnis:LocationShareWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes fallback*/)

        // Versuche den letzten bekannten Standort als Startwert zu laden
        try {
            lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastLocation == null) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Konnte letzten Standort nicht laden: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground IMMER sofort starten
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

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Standortberechtigung fehlt")
            stopSelf()
            return START_NOT_STICKY
        }

        // Starte die Loops immer, wenn sie noch nicht laufen
        if (!isServiceRunning) {
            isServiceRunning = true
            startGpsUpdates()
            startUploadLoop()
            Log.d(TAG, "LocationShareService gestartet (Loops initiiert)")
        } else {
            // Sicherstellen, dass die Updates laufen, falls der Service reaktiviert wurde
            startGpsUpdates() 
            Log.d(TAG, "LocationShareService bereits aktiv (GPS-Updates aufgefrischt)")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        locationManager.removeUpdates(this)
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

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
            // GPS Provider für hohe Genauigkeit
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    GPS_MIN_TIME_MS,
                    GPS_MIN_DISTANCE_M,
                    this
                )
            }
            
            // Network Provider für schnellen Fix (WLAN/Funkzellen)
            // Nur versuchen, wenn der Provider auf diesem Gerät existiert
            if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    GPS_MIN_TIME_MS,
                    GPS_MIN_DISTANCE_M,
                    this
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Standortberechtigung entzogen", e)
            stopSelf()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Provider nicht verfügbar: ${e.message}")
        }
    }

    private var hasUploadedOnce = false

    override fun onLocationChanged(location: Location) {
        lastLocation = location
        Log.d(TAG, "GPS-Update: ${location.latitude}, ${location.longitude}")
        
        if (!hasUploadedOnce) {
            hasUploadedOnce = true
            serviceScope.launch {
                uploadLocation(location)
            }
        }
    }

    @Deprecated("Deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ── Upload-Loop ───────────────────────────────────────────────────────

    /**
     * Coroutine-Loop auf Dispatchers.IO: alle 30s den aktuellen Standort hochladen.
     */
    private fun startUploadLoop() {
        serviceScope.launch {
            // Warte kurz, um dem GPS Zeit für einen ersten Fix zu geben
            delay(2000)
            while (isActive) {
                val location = lastLocation
                if (location != null) {
                    uploadLocation(location)
                } else {
                    Log.d(TAG, "Warten auf GPS-Fix für Upload...")
                }
                delay(UPLOAD_INTERVAL_MS)
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
        
        // Sicherstellen, dass das Verzeichnis existiert
        client.createDirectory(WebDavSyncManager.REMOTE_DIR)

        val remotePath = "${WebDavSyncManager.REMOTE_DIR}/${remoteFileName(this)}"
        
        serviceScope.launch(Dispatchers.IO) {
            val result = client.uploadFile(remotePath, tempFile)
            if (result.isSuccess) {
                Log.d(TAG, "Standort erfolgreich hochgeladen: $remotePath")
                updateNotification("Standort aktualisiert: ${formatTime(payload.timestamp)}")
            } else {
                Log.e(TAG, "Upload fehlgeschlagen: ${result.exceptionOrNull()?.message}")
            }
            tempFile.delete()
        }
    }

    /**
     * Löscht die eigene Standort-Datei vom Server (HTTP DELETE).
     */
    private fun deleteOwnLocationFile() {
        val config = prefs.loadConfig() ?: return
        val remotePath = "${WebDavSyncManager.REMOTE_DIR}/${remoteFileName(this)}"
        val client = WebDavClient(config.serverUrl, config.username, config.password)

        try {
            val result = client.deleteFile(remotePath)
            if (result.isSuccess) {
                Log.d(TAG, "Eigene Standortdatei erfolgreich gelöscht")
            } else {
                Log.w(TAG, "DELETE eigene Standortdatei fehlgeschlagen: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "DELETE fehlgeschlagen (ignoriert): ${e.message}")
        }
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Standortfreigabe"
            val descriptionText = "Zeigt an, dass dein Standort für andere Teilnehmer sichtbar ist."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, LocationShareActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📍 Standortfreigabe")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_my_location)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun getBatteryLevel(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
}

/** Datenklasse für die Übertragung via WebDAV JSON. */
data class LocationPayload(
    val deviceId: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Float? = null,
    val timestamp: Long,
    val batteryLevel: Int? = null
)
