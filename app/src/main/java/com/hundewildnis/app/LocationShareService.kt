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

        const val LOCATION_FILE_PREFIX = "location_"
        const val LOCATION_FILE_SUFFIX = ".json"

        private const val UPLOAD_INTERVAL_MS = 30_000L
        private const val GPS_MIN_TIME_MS = 10_000L
        private const val GPS_MIN_DISTANCE_M = 0f

        const val PREF_LOCATION_LABEL = "location_share_label"

        var isServiceRunning = false

        fun remoteFileName(context: Context): String =
            "$LOCATION_FILE_PREFIX${WebDavSyncManager.getDeviceId(context)}$LOCATION_FILE_SUFFIX"
    }

    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    @Volatile private var lastLocation: Location? = null

    private val gson = Gson()
    private val prefs by lazy { WebDavPreferences(this) }
    @Volatile private var remoteDirEnsured = false
    @Volatile private var gpsUpdatesActive = false
    /** Verhindert doppelten Upload-Loop bei START_STICKY-Neustart. */
    @Volatile private var uploadLoopActive = false

    // ── Service-Lifecycle ──────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        acquireWakeLock()

        try {
            lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastLocation == null) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Konnte letzten Standort nicht laden: ${e.message}")
        }
    }

    /**
     * Acquired den WakeLock idempotent: gibt einen ggf. noch gehaltenen alten
     * WakeLock frei bevor ein neuer angefordert wird (verhindert Leak) und wird
     * auch aus onStartCommand() aufgerufen, damit ein START_STICKY-Neustart
     * ohne onCreate() den Lock wiederherstellt.
     */
    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return  // bereits gehalten – nichts zu tun
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            @Suppress("WakelockTimeout")
            wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "HundeWildnis:LocationShareWakeLock"
            ).also { it.acquire() }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock acquire fehlgeschlagen: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        // WakeLock bei jedem onStartCommand() sicherstellen –
        // deckt den START_STICKY-Neustart ohne onCreate() ab.
        acquireWakeLock()

        isServiceRunning = true
        BootReceiver.setServiceActive(this, true)
        startGpsUpdates()      // idempotent durch gpsUpdatesActive-Guard
        startUploadLoop()      // idempotent durch uploadLoopActive-Guard
        Log.d(TAG, "LocationShareService onStartCommand – Loops sichergestellt")

        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        gpsUpdatesActive = false
        uploadLoopActive = false
        BootReceiver.setServiceActive(this, false)
        locationManager.removeUpdates(this)

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released")
        }
        wakeLock = null

        serviceScope.launch { deleteOwnLocationFile() }
        Thread.sleep(2000)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── GPS ───────────────────────────────────────────────────────────────

    private fun startGpsUpdates() {
        if (gpsUpdatesActive) {
            Log.d(TAG, "GPS-Listener bereits aktiv – keine Neuregistrierung")
            return
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, GPS_MIN_TIME_MS, GPS_MIN_DISTANCE_M, this
                )
            }
            if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, GPS_MIN_TIME_MS, GPS_MIN_DISTANCE_M, this
                )
            }
            gpsUpdatesActive = true
            Log.d(TAG, "GPS-Listener registriert")
        } catch (e: SecurityException) {
            Log.e(TAG, "Standortberechtigung entzogen", e)
            stopSelf()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Provider nicht verfügbar: ${e.message}")
        }
    }

    private var lastUploadedLocation: Location? = null
    private var hasUploadedOnce = false

    override fun onLocationChanged(location: Location) {
        lastLocation = location
        Log.d(TAG, "GPS-Update: ${location.latitude}, ${location.longitude}" +
                if (location.hasAccuracy()) " (±${location.accuracy.toInt()}m)" else "")

        if (!hasUploadedOnce) {
            val accuracyOk = !location.hasAccuracy() || location.accuracy < 50f
            if (accuracyOk) {
                hasUploadedOnce = true
                serviceScope.launch { uploadLocation(location) }
            } else {
                Log.d(TAG, "Erster Fix zu ungenau (${location.accuracy.toInt()}m) – warte auf besseren Fix")
            }
            return
        }

        val lastUploaded = lastUploadedLocation
        if (lastUploaded != null) {
            if (location.distanceTo(lastUploaded) > 15f) {
                Log.d(TAG, "Signifikante Bewegung (${location.distanceTo(lastUploaded).toInt()}m) – Sofort-Upload")
                serviceScope.launch { uploadLocation(location) }
            }
        }
    }

    @Deprecated("Deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    // ── Upload-Loop ───────────────────────────────────────────────────────

    private fun startUploadLoop() {
        if (uploadLoopActive) {
            Log.d(TAG, "Upload-Loop bereits aktiv – kein zweiter Loop")
            return
        }
        uploadLoopActive = true
        serviceScope.launch {
            delay(2000)
            while (isActive) {
                val location = lastLocation
                if (location != null) uploadLocation(location)
                else Log.d(TAG, "Warten auf GPS-Fix für Upload...")
                delay(UPLOAD_INTERVAL_MS)
            }
        }
    }

    /** Mutex verhindert überlappende Uploads bei langsamer Verbindung. */
    private val uploadMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun uploadLocation(location: Location) {
        // Überlappende Uploads vermeiden – wenn bereits einer läuft, überspringen
        if (!uploadMutex.tryLock()) {
            Log.d(TAG, "Upload läuft bereits – überspringe diesen Trigger")
            return
        }
        try {
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
            val tempFile = File(cacheDir, "${remoteFileName(this)}.${System.currentTimeMillis()}.tmp")
            tempFile.writeText(json)

            val client = WebDavClient(config.serverUrl, config.username, config.password)

            if (!remoteDirEnsured) {
                val mkcolResult = client.createDirectory(WebDavSyncManager.REMOTE_DIR)
                if (mkcolResult.isSuccess) remoteDirEnsured = true
            }

            val remotePath = "${WebDavSyncManager.REMOTE_DIR}/${remoteFileName(this)}"
            val result = client.uploadFile(remotePath, tempFile)

            if (result.isSuccess) {
                lastUploadedLocation = location
                Log.d(TAG, "Standort erfolgreich hochgeladen: $remotePath")
                updateNotification("Standort aktualisiert: ${formatTime(payload.timestamp)}")
            } else {
                Log.e(TAG, "Upload fehlgeschlagen: ${result.exceptionOrNull()?.message}")
            }
            tempFile.delete()
        } finally {
            uploadMutex.unlock()
        }
    }

    private fun deleteOwnLocationFile() {
        val config = prefs.loadConfig() ?: return
        val remotePath = "${WebDavSyncManager.REMOTE_DIR}/${remoteFileName(this)}"
        val client = WebDavClient(config.serverUrl, config.username, config.password)
        try {
            val result = client.deleteFile(remotePath)
            if (result.isSuccess) Log.d(TAG, "Eigene Standortdatei gelöscht")
            else Log.w(TAG, "DELETE fehlgeschlagen: ${result.exceptionOrNull()?.message}")
        } catch (e: Exception) {
            Log.w(TAG, "DELETE fehlgeschlagen (ignoriert): ${e.message}")
        }
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Standortfreigabe", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zeigt an, dass dein Standort für andere Teilnehmer sichtbar ist."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, LocationShareActivity::class.java), PendingIntent.FLAG_IMMUTABLE
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

    private fun formatTime(timestamp: Long): String =
        java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

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