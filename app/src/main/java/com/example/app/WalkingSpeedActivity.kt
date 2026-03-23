package com.hundewildnis.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.hundewildnis.app.R
import com.hundewildnis.app.databinding.ActivityWalkingSpeedBinding
import android.content.res.Configuration
import android.graphics.Color

/**
 * WalkingSpeedActivity - Überwacht Geh-Geschwindigkeit
 * Benachrichtigt bei Überschreitung der eingestellten Grenze
 */
class WalkingSpeedActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityWalkingSpeedBinding
    private var walkingSpeedService: WalkingSpeedService? = null
    private var serviceBound = false

    // UI Update Handler
    private val uiUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            if (walkingSpeedService?.isTracking() == true) {
                uiUpdateHandler.postDelayed(this, 1000) // Update jede Sekunde
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WalkingSpeedService.LocalBinder
            walkingSpeedService = binder.getService()
            serviceBound = true
            updateUI()

            // Starte UI-Updates wenn Tracking läuft
            if (walkingSpeedService?.isTracking() == true) {
                startUIUpdates()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            walkingSpeedService = null
            serviceBound = false
            stopUIUpdates()
        }
    }

    // Permission Launcher für Location
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // Standort-Permission erteilt
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Jetzt Background Location anfragen
                requestBackgroundLocation()
            } else {
                // Android 9 und älter: Tracking kann starten
                Toast.makeText(this, "✓ Berechtigungen erteilt", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Abgelehnt
            showPermissionDeniedDialog()
        }
    }

    // Permission Launcher für Background Location (Android 10+)
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "✓ Hintergrund-Standort erlaubt", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ Tracking funktioniert nur wenn App geöffnet ist", Toast.LENGTH_LONG).show()
        }
    }

    // Permission Launcher für Notifications (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "✓ Benachrichtigungen erlaubt", Toast.LENGTH_SHORT).show()
        } else {
            val dialog = AlertDialog.Builder(this)
                .setTitle("⚠️ Benachrichtigungen deaktiviert")
                .setMessage(
                    "Ohne Benachrichtigungen wirst du nicht gewarnt wenn du zu schnell gehst.\n\n" +
                            "Bitte aktiviere Benachrichtigungen in den App-Einstellungen."
                )
                .setPositiveButton("Einstellungen") { _, _ ->
                    openAppSettings()
                }
                .setNegativeButton("Später", null)
                .create()

            dialog.show()
            styleDialogButtons(dialog)  // ✅ DIESE ZEILE HINZUFÜGEN
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWalkingSpeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Trainingstempo"
            setDisplayHomeAsUpEnabled(true)
        }

        setupUI()
    }

    private fun setupUI() {
        // Speed Limit auswählen
        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val speedLimit = when (checkedId) {
                R.id.radioSpeed25 -> 2.5f
                R.id.radioSpeed30 -> 3.0f
                R.id.radioSpeed40 -> 4.0f
                else -> 3.0f
            }
            walkingSpeedService?.setSpeedLimit(speedLimit)
        }

        // Messintervall auswählen
        binding.radioGroupInterval.setOnCheckedChangeListener { _, checkedId ->
            val interval = when (checkedId) {
                R.id.radioInterval30 -> 30000L
                R.id.radioInterval60 -> 60000L
                else -> 30000L
            }
            walkingSpeedService?.setCheckInterval(interval)
        }

        // Vibration an/aus
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            walkingSpeedService?.setVibrationEnabled(isChecked)
        }

        // Sound an/aus
        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            walkingSpeedService?.setSoundEnabled(isChecked)
        }

        // Start/Stop Button
        binding.btnStartStop.setOnClickListener {
            if (walkingSpeedService?.isTracking() == true) {
                stopTracking()
            } else {
                requestPermissionsAndStart()
            }
        }

        // Defaults
        binding.radioSpeed30.isChecked = true
        binding.radioInterval30.isChecked = true
        binding.switchVibration.isChecked = true
        binding.switchSound.isChecked = true
    }

    private fun requestPermissionsAndStart() {
        // 1. Prüfe Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
                // Erklärungs-Dialog für Notifications
                val dialog = AlertDialog.Builder(this)
                    .setTitle("📬 Benachrichtigungen aktivieren")
                    .setMessage(
                        "Die App benötigt Benachrichtigungen um dich zu warnen wenn du zu schnell gehst.\n\n" +
                                "Im nächsten Dialog bitte \"Erlauben\" wählen."
                    )
                    .setPositiveButton("Weiter") { _, _ ->
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setNegativeButton("Überspringen") { _, _ ->
                        Toast.makeText(this, "⚠️ Keine Warnungen möglich", Toast.LENGTH_SHORT).show()
                    }
                    .create()

                dialog.show()
                styleDialogButtons(dialog)  // ✅ HIER
                return
            }
        }

        // 2. Prüfe ob Standort-Permissions bereits vorhanden
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            // Standort-Permission vorhanden
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasBackgroundLocation) {
                    // Background Location fehlt noch
                    requestBackgroundLocation()
                    return
                }
            }

            // Alle Permissions vorhanden - Tracking starten
            startTracking()
        } else {
            // Standort-Permission anfragen
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasBackgroundLocation) {
                // Erklärungs-Dialog zeigen
                val dialog = AlertDialog.Builder(this)
                    .setTitle("Hintergrund-Standort erforderlich")
                    .setMessage(
                        "Für GPS-Tracking im Hintergrund benötigt die App Zugriff auf deinen Standort, " +
                                "auch wenn die App geschlossen ist.\n\n" +
                                "Im nächsten Dialog wähle bitte: \"Immer erlauben\""
                    )
                    .setPositiveButton("Weiter") { _, _ ->
                        backgroundLocationPermissionLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                    }
                    .setNegativeButton("Abbrechen", null)
                    .create()

                dialog.show()
                styleDialogButtons(dialog)  // ✅ HIER
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("⚠️ Berechtigung erforderlich")
            .setMessage(
                "Die App benötigt Zugriff auf deinen Standort für GPS-Tracking.\n\n" +
                        "Bitte erlaube den Standort-Zugriff in den App-Einstellungen."
            )
            .setPositiveButton("Einstellungen öffnen") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.show()
        styleDialogButtons(dialog)  // ✅ HIER
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    // ERSETZE die startTracking() Methode in WalkingSpeedActivity.kt:

    private fun startTracking() {
        // Final Check: Alle Permissions vorhanden?
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            Toast.makeText(this, "⚠️ Standort-Berechtigung fehlt", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, WalkingSpeedService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

            // ✅ NEU: Warte kurz bis Service gebunden ist, dann setze Werte
            binding.btnStartStop.postDelayed({
                applyCurrentSettings()
            }, 500)

            updateUI()
            startUIUpdates()

        } catch (e: SecurityException) {
            Toast.makeText(this, "⚠️ Fehler beim Starten: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ✅ NEU: Methode zum Erzwingen weisser Schrift im AlertDialog für Buttons
    private fun styleDialogButtons(dialog: AlertDialog) {
        dialog.window?.decorView?.post {
            val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(buttonColor)
        }
    }

    // ✅ NEU: Methode zum Anwenden der aktuellen Einstellungen
    private fun applyCurrentSettings() {
        // Geschwindigkeitslimit
        val speedLimit = when (binding.radioGroup.checkedRadioButtonId) {
            R.id.radioSpeed25 -> 2.5f
            R.id.radioSpeed30 -> 3.0f
            R.id.radioSpeed40 -> 4.0f
            else -> 3.0f
        }
        walkingSpeedService?.setSpeedLimit(speedLimit)

        // Messintervall
        val interval = when (binding.radioGroupInterval.checkedRadioButtonId) {
            R.id.radioInterval30 -> 30000L
            R.id.radioInterval60 -> 60000L
            else -> 30000L
        }
        walkingSpeedService?.setCheckInterval(interval)

        // Vibration & Sound
        walkingSpeedService?.setVibrationEnabled(binding.switchVibration.isChecked)
        walkingSpeedService?.setSoundEnabled(binding.switchSound.isChecked)
    }

    private fun stopTracking() {
        walkingSpeedService?.stopTracking()

        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }

        stopService(Intent(this, WalkingSpeedService::class.java))

        stopUIUpdates() // Stoppe UI-Updates
        updateUI()
    }

    private fun startUIUpdates() {
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable) // Vorherige stoppen
        uiUpdateHandler.post(uiUpdateRunnable)
    }

    private fun stopUIUpdates() {
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
    }

    private fun updateUI() {
        val isTracking = walkingSpeedService?.isTracking() == true

        binding.btnStartStop.text = if (isTracking) "Messung stoppen" else "Messung starten"
        binding.btnStartStop.setBackgroundColor(
            if (isTracking)
                getColor(android.R.color.holo_red_dark)
            else
                getColor(R.color.green_primary)
        )

        // Aktuelle Geschwindigkeit anzeigen
        val currentSpeed = walkingSpeedService?.getCurrentSpeed() ?: 0f
        binding.tvCurrentSpeed.text = String.format("Geschwindigkeit: %.1f km/h", currentSpeed)

        // Status anzeigen
        binding.tvStatus.text = if (isTracking) {
            "\uD83D\uDD14 Messung aktiv"
        } else {
            "\uD83D\uDED1 Messung gestoppt"
        }
    }

    override fun onStart() {
        super.onStart()

        // Service binden wenn bereits läuft
        if (WalkingSpeedService.isServiceRunning) {
            val intent = Intent(this, WalkingSpeedService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()

        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopUIUpdates() // Cleanup
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
