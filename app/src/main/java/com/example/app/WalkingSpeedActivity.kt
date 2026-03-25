package com.hundewildnis.app

import android.Manifest
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.hundewildnis.app.databinding.ActivityWalkingSpeedBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data Classes
 */
data class SpeedDataPoint(
    val timestamp: Long,
    val speed: Float,
    val latitude: Double,
    val longitude: Double
)

/**
 * WalkingSpeedActivity - Überwacht Geh-Geschwindigkeit mit Chart & Export
 */
class WalkingSpeedActivity : SecureBaseActivity(), SpeedUpdateListener {

    private lateinit var binding: ActivityWalkingSpeedBinding
    private var walkingSpeedService: WalkingSpeedService? = null
    private var serviceBound = false

    // Chart & Daten
    private lateinit var speedChart: LineChart
    private val speedDataPoints = mutableListOf<SpeedDataPoint>()
    private var sessionStartTime = 0L  // ✅ Wird beim Start korrekt gesetzt
    private var totalDistance = 0f
    private var exceedCount = 0
    private var speedLimit = 3.0f

    // UI Update Handler
    private val uiUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            if (walkingSpeedService?.isTracking() == true) {
                uiUpdateHandler.postDelayed(this, 1000)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WalkingSpeedService.LocalBinder
            walkingSpeedService = binder.getService()
            serviceBound = true

            walkingSpeedService?.setSpeedUpdateListener(this@WalkingSpeedActivity)

            updateUI()

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

    // Permission Launchers
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocation()
            } else {
                Toast.makeText(this, "✓ Berechtigungen erteilt", Toast.LENGTH_SHORT).show()
            }
        } else {
            showPermissionDeniedDialog()
        }
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "✓ Hintergrund-Standort erlaubt", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ Tracking funktioniert nur wenn App geöffnet ist", Toast.LENGTH_LONG).show()
        }
    }

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
            styleDialogButtons(dialog)
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

        setupChart()
        setupUI()
    }

    private fun setupChart() {
        speedChart = binding.speedChart

        speedChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)

            // X-Achse (Zeit)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val seconds = value.toInt()
                        val minutes = seconds / 60
                        val secs = seconds % 60
                        return String.format("%d:%02d", minutes, secs)
                    }
                }
            }

            // Y-Achse (Geschwindigkeit)
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                axisMaximum = 6f
            }

            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }

    private fun setupUI() {
        // Speed Limit auswählen
        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            speedLimit = when (checkedId) {
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

        // Export Button
        binding.btnExport.setOnClickListener {
            exportSessionToPdf()
        }

        // Defaults
        binding.radioSpeed30.isChecked = true
        binding.radioInterval30.isChecked = true
        binding.switchVibration.isChecked = true
        binding.switchSound.isChecked = true

        speedLimit = 3.0f
    }

    // SpeedUpdateListener Implementation
    override fun onSpeedUpdate(speed: Float, location: Location) {
        runOnUiThread {
            val dataPoint = SpeedDataPoint(
                timestamp = System.currentTimeMillis(),
                speed = speed,
                latitude = location.latitude,
                longitude = location.longitude
            )
            speedDataPoints.add(dataPoint)

            // Distanz vom Service holen
            totalDistance = walkingSpeedService?.getTotalDistance() ?: 0f

            // Zähle Überschreitungen
            if (speed > speedLimit) {
                exceedCount++
            }

            updateChartData()
            updateStatistics()
        }
    }

    private fun updateChartData() {
        if (speedDataPoints.isEmpty()) return

        // Chart-Daten erstellen
        val entries = speedDataPoints.map { point ->
            val seconds = ((point.timestamp - sessionStartTime) / 1000f)
            Entry(seconds, point.speed)
        }

        val dataSet = LineDataSet(entries, "Geschwindigkeit").apply {
            color = Color.parseColor("#2d5016")
            setCircleColor(Color.parseColor("#2d5016"))
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            valueTextSize = 9f
            setDrawValues(false)
        }

        // Limit-Linie hinzufügen
        val limitEntries = entries.map { Entry(it.x, speedLimit) }
        val limitSet = LineDataSet(limitEntries, "Limit").apply {
            color = Color.RED
            lineWidth = 1f
            setDrawCircles(false)
            setDrawValues(false)
            enableDashedLine(10f, 5f, 0f)
        }

        speedChart.data = LineData(dataSet, limitSet)
        speedChart.notifyDataSetChanged()
        speedChart.invalidate()
    }

    // ✅ FIX: Korrekte Dauer-Berechnung
    private fun updateStatistics() {
        if (speedDataPoints.isEmpty()) {
            binding.tvStats.text = "Starte Tracking um Statistiken zu sehen"
            return
        }

        val avgSpeed = speedDataPoints.map { it.speed }.average().toFloat()
        val maxSpeed = speedDataPoints.maxOf { it.speed }

        // ✅ FIX: Nur Zeit seit sessionStartTime berechnen
        val now = System.currentTimeMillis()
        val elapsedTime = (now - sessionStartTime) / 1000  // in Sekunden
        val hours = elapsedTime / 3600
        val minutes = (elapsedTime % 3600) / 60
        val seconds = elapsedTime % 60

        binding.tvStats.text = """
            Dauer: ${String.format("%02d:%02d:%02d", hours, minutes, seconds)}
            Strecke: ${String.format("%.2f km", totalDistance)}
            Durchschnitt: ${String.format("%.1f km/h", avgSpeed)}
            Maximum: ${String.format("%.1f km/h", maxSpeed)}
            Überschreitungen: $exceedCount
        """.trimIndent()
    }

    private fun requestPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPermission) {
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
                styleDialogButtons(dialog)
                return
            }
        }

        val hasFineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasBackgroundLocation) {
                    requestBackgroundLocation()
                    return
                }
            }

            startTracking()
        } else {
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
                styleDialogButtons(dialog)
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
        styleDialogButtons(dialog)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun startTracking() {
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

            binding.btnStartStop.postDelayed({
                applyCurrentSettings()
            }, 500)

            // ✅ FIX: Session-Daten zurücksetzen + JETZT als Startzeit
            speedDataPoints.clear()
            sessionStartTime = System.currentTimeMillis()  // ✅ WICHTIG!
            totalDistance = 0f
            exceedCount = 0
            speedChart.clear()
            binding.tvStats.text = "Tracking läuft..."

            updateUI()
            startUIUpdates()

        } catch (e: SecurityException) {
            Toast.makeText(this, "⚠️ Fehler beim Starten: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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

    private fun applyCurrentSettings() {
        speedLimit = when (binding.radioGroup.checkedRadioButtonId) {
            R.id.radioSpeed25 -> 2.5f
            R.id.radioSpeed30 -> 3.0f
            R.id.radioSpeed40 -> 4.0f
            else -> 3.0f
        }
        walkingSpeedService?.setSpeedLimit(speedLimit)

        val interval = when (binding.radioGroupInterval.checkedRadioButtonId) {
            R.id.radioInterval30 -> 30000L
            R.id.radioInterval60 -> 60000L
            else -> 30000L
        }
        walkingSpeedService?.setCheckInterval(interval)

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

        stopUIUpdates()
        updateUI()
    }

    private fun startUIUpdates() {
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
        uiUpdateHandler.post(uiUpdateRunnable)
    }

    private fun stopUIUpdates() {
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
    }

    private fun updateUI() {
        val isTracking = walkingSpeedService?.isTracking() == true

        binding.btnStartStop.text = if (isTracking) "🛑 Messung stoppen" else "🏁 Messung starten"
        binding.btnStartStop.setBackgroundColor(
            if (isTracking)
                getColor(android.R.color.holo_red_dark)
            else
                getColor(R.color.green_primary)
        )

        val currentSpeed = walkingSpeedService?.getCurrentSpeed() ?: 0f
        binding.tvCurrentSpeed.text = String.format("Geschwindigkeit: %.1f km/h", currentSpeed)

        binding.tvStatus.text = if (isTracking) {
            "\uD83D\uDD14 Messung aktiv"
        } else {
            "\uD83D\uDED1 Messung gestoppt"
        }

        // Export-Button nur aktiv wenn Daten vorhanden
        binding.btnExport.isEnabled = speedDataPoints.isNotEmpty()
    }

    // ✅ Export als PDF
    private fun exportSessionToPdf() {
        if (speedDataPoints.isEmpty()) {
            Toast.makeText(this, "Keine Daten zum Exportieren", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint()

            // Titel
            paint.textSize = 20f
            paint.isFakeBoldText = true
            paint.color = Color.BLACK
            canvas.drawText("Trainingstempo - Protokoll", 50f, 50f, paint)

            // ✅ FIX: Datum mit sessionStartTime (nicht 1970!)
            paint.textSize = 14f
            paint.isFakeBoldText = false
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
            canvas.drawText(dateFormat.format(Date(sessionStartTime)), 50f, 80f, paint)

            // Statistiken
            paint.textSize = 12f
            var y = 120f
            val stats = binding.tvStats.text.toString().split("\n")
            for (line in stats) {
                canvas.drawText(line, 50f, y, paint)
                y += 20f
            }

            // Chart als Bitmap
            y += 20f
            val chartBitmap = speedChart.chartBitmap
            val scaledChart = Bitmap.createScaledBitmap(chartBitmap, 495, 300, true)
            canvas.drawBitmap(scaledChart, 50f, y, null)

            pdfDocument.finishPage(page)

            // Speichern
            val fileName = "Trainingstempo_${SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.GERMAN).format(Date())}.pdf"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                savePdfToMediaStore(pdfDocument, fileName)
            } else {
                val documentsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "HundeWildnis"
                )
                if (!documentsDir.exists()) documentsDir.mkdirs()

                val file = File(documentsDir, fileName)
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                showExportDialog(file)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ Export-Fehler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun savePdfToMediaStore(pdfDocument: PdfDocument, fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/HundeWildnis")
        }

        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            pdfDocument.close()

            Toast.makeText(this, "✓ PDF exportiert", Toast.LENGTH_SHORT).show()
            shareUri(it)
        }
    }

    private fun showExportDialog(file: File) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("✓ PDF exportiert")
            .setMessage("${file.name}\n\nGespeichert in:\nDocuments/HundeWildnis")
            .setPositiveButton("Teilen") { _, _ ->
                shareFile(file)
            }
            .setNegativeButton("OK", null)
            .create()

        dialog.show()
        styleDialogButtons(dialog)
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "PDF teilen"))
    }

    private fun shareUri(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Trainingstempo Session")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "PDF teilen"))
    }

    override fun onStart() {
        super.onStart()

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
        stopUIUpdates()
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
