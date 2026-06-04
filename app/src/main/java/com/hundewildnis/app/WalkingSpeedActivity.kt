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
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hundewildnis.app.databinding.ActivityWalkingSpeedBinding
import com.hundewildnis.app.databinding.ItemSpeedSessionBinding
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

data class SpeedSession(
    val startTime: Long,
    val endTime: Long,
    val distance: Float,
    val exceedCount: Int,
    val dataPoints: List<SpeedDataPoint>,
    val limit: Float,
    val limitChanges: List<LimitChange> = emptyList()
)

// ──────────────────────────────────────────────────────────────
// NEUE DATA CLASS: Limit-Wechsel speichern
// ──────────────────────────────────────────────────────────────
data class LimitChange(
    val timestamp: Long,
    val oldLimit: Float,
    val newLimit: Float
)


/**
 * WalkingSpeedActivity - Überwacht Geh-Geschwindigkeit mit Chart, Export & Historie
 */
class WalkingSpeedActivity : SecureBaseActivity(), SpeedUpdateListener {

    private lateinit var binding: ActivityWalkingSpeedBinding
    private var walkingSpeedService: WalkingSpeedService? = null
    private var serviceBound = false

    // Chart & Daten
    private lateinit var speedChart: LineChart
    private var speedDataPoints = mutableListOf<SpeedDataPoint>()
    private var sessionStartTime = 0L
    private var totalDistance = 0f
    private var exceedCount = 0
    private var speedLimit = 3.0f

    // Historie
    private val historyList = mutableListOf<SpeedSession>()
    private lateinit var historyAdapter: HistoryAdapter

    // ✅ NEU: Liste der Limit-Änderungen während Messung
    private val limitChanges = mutableListOf<LimitChange>()

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
            loadDataFromService()
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

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            checkNotificationPermissionAndStart()
        } else {
            Toast.makeText(this, "⚠️ Ohne Standort keine Messung möglich", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "✓ Benachrichtigungen erlaubt", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ Keine Warnungen möglich", Toast.LENGTH_SHORT).show()
        }
        startTracking()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWalkingSpeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Tempo-Trainer"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.toolbar.navigationIcon?.setTint(Color.WHITE)

        setupChart()
        setupHistory()
        setupUI()
        loadHistory()
    }

    private fun setupHistory() {
        historyAdapter = HistoryAdapter(historyList,
            onItemClick = { session -> displaySession(session) },
            onDeleteClick = { session -> confirmDelete(session) }
        )
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@WalkingSpeedActivity)
            adapter = historyAdapter
        }
    }

    private fun confirmDelete(session: SpeedSession) {
        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

        val dialog = AlertDialog.Builder(this)
            .setTitle("Messung löschen?")
            .setMessage("Möchtest Du diese Trainingseinheit dauerhaft löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                val index = historyList.indexOfFirst { it.startTime == session.startTime }
                if (index != -1) {
                    historyList.removeAt(index)
                    historyAdapter.notifyItemRemoved(index)
                    saveHistory()
                    Toast.makeText(this, "Eintrag entfernt", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
    }

    private fun showInstructionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_instructions, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        view.findViewById<TextView>(R.id.tvInsTitle).setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton("Verstanden", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
    }

    private fun showPermissionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_permissions, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        view.findViewById<TextView>(R.id.tvPermTitle).setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton("OK", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val titleColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        val linkColor = if (isDark) Color.parseColor("#8fad6f") else Color.parseColor("#2d5016")

        dialogView.findViewById<TextView>(R.id.tvAboutTitle).setTextColor(titleColor)

        val tvWeb = dialogView.findViewById<TextView>(R.id.tvAboutWeb)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tvAboutEmail)
        tvWeb.setTextColor(linkColor)
        tvEmail.setTextColor(linkColor)

        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "1.0.0" }
        dialogView.findViewById<TextView>(R.id.tvAboutVersion).text = "Version $versionName"

        tvWeb.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wildnislehrer.com"))) }
        tvEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:hundewildnisschuleapp@online.de")
                putExtra(Intent.EXTRA_SUBJECT, "HundeWildnis App Feedback")
            }
            startActivity(Intent.createChooser(intent, "E-Mail senden"))
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).setPositiveButton("Schließen", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
    }

    private fun displaySession(session: SpeedSession) {
        if (walkingSpeedService?.isTracking() == true) {
            Toast.makeText(this, "Hinweis: Aktuelle Messung läuft im Hintergrund weiter", Toast.LENGTH_SHORT).show()
        }

        speedDataPoints = session.dataPoints.toMutableList()
        sessionStartTime = session.startTime
        totalDistance = session.distance
        exceedCount = session.exceedCount
        speedLimit = session.limit
        limitChanges.clear()
        limitChanges.addAll(session.limitChanges)

        binding.tvChartTitle.text = "📊 Historie: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))

        updateChartData()
        updateStatistics()

        binding.btnExport.isEnabled = true
        binding.btnExportGpx.isEnabled = true
        binding.btnShare.isEnabled = true

        binding.speedChart.parent.requestChildFocus(binding.speedChart, binding.speedChart)
    }

    private fun saveSessionToHistory() {
        if (speedDataPoints.isEmpty()) return

        val newSession = SpeedSession(
            startTime = sessionStartTime,
            endTime = System.currentTimeMillis(),
            distance = totalDistance,
            exceedCount = exceedCount,
            dataPoints = speedDataPoints.toList(),
            limit = speedLimit,
            limitChanges = limitChanges.toList()
        )

        historyList.add(0, newSession)
        historyAdapter.notifyItemInserted(0)
        saveHistory()
    }

    private fun saveHistory() {
        val prefs = getSharedPreferences("walking_history", Context.MODE_PRIVATE)
        val json = Gson().toJson(historyList)
        prefs.edit().putString("sessions", json).apply()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("walking_history", Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", null)
        if (json != null) {
            val type = object : TypeToken<List<SpeedSession>>() {}.type
            val loaded: List<SpeedSession> = Gson().fromJson(json, type)
            historyList.clear()
            historyList.addAll(loaded)
            historyAdapter.notifyDataSetChanged()
        }
    }

    private fun loadDataFromService() {
        walkingSpeedService?.let { service ->
            if (service.isTracking()) {
                speedDataPoints = service.getSpeedDataPoints().toMutableList()
                sessionStartTime = service.getSessionStartTime()
                exceedCount = service.getExceedCount()
                totalDistance = service.getTotalDistance()
                binding.tvChartTitle.text = "📊 Aktueller Geschwindigkeitsverlauf"
                updateChartData()
                updateStatistics()
            }
        }
    }

    private fun setupChart() {
        speedChart = binding.speedChart
        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK

        speedChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                granularity = 1f
                this.textColor = textColor
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val seconds = value.toInt()
                        val minutes = seconds / 60
                        val secs = seconds % 60
                        return String.format("%d:%02d", minutes, secs)
                    }
                }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                axisMaximum = 6f
                this.textColor = textColor
            }

            axisRight.isEnabled = false
            legend.textColor = textColor
        }
    }

    private fun setupUI() {
        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val oldLimit = speedLimit

            speedLimit = when (checkedId) {
                R.id.radioSpeed25 -> 2.5f
                R.id.radioSpeed30 -> 3.0f
                R.id.radioSpeed40 -> 4.0f
                else -> 3.0f
            }
            // ✅ NEU: Wechsel protokollieren (nur während Messung)
            if (walkingSpeedService?.isTracking() == true && oldLimit != speedLimit) {
                limitChanges.add(LimitChange(
                    timestamp = System.currentTimeMillis(),
                    oldLimit = oldLimit,
                    newLimit = speedLimit
                ))

                // Service aktualisieren
                walkingSpeedService?.setSpeedLimit(speedLimit)

                // Chart aktualisieren
                updateChartData()

                // User-Feedback
                Toast.makeText(
                    this,
                    "Limit geändert: ${String.format("%.1f", oldLimit)} → ${String.format("%.1f", speedLimit)} km/h",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // Nicht während Messung → nur Service updaten
                walkingSpeedService?.setSpeedLimit(speedLimit)
            }
        }


        binding.radioGroupInterval.setOnCheckedChangeListener { _, checkedId ->
            val interval = when (checkedId) {
                R.id.radioInterval30 -> 30000L
                R.id.radioInterval60 -> 60000L
                else -> 30000L
            }
            walkingSpeedService?.setCheckInterval(interval)

            // ✅ Feedback nur während Messung
            if (walkingSpeedService?.isTracking() == true) {
                Toast.makeText(
                    this,
                    "Intervall geändert: ${interval / 1000}s",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            walkingSpeedService?.setVibrationEnabled(isChecked)
        }

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            walkingSpeedService?.setSoundEnabled(isChecked)
        }

        binding.btnStartStop.setOnClickListener {
            if (walkingSpeedService?.isTracking() == true) {
                stopTracking()
            } else {
                requestPermissionsAndStart()
            }
        }

        binding.btnExport.setOnClickListener {
            exportSessionToPdf(false)
        }

        binding.btnExportGpx.setOnClickListener {
            exportSessionToGpx()
        }

        binding.btnShare.setOnClickListener {
            exportSessionToPdf(true)
        }
    }

    override fun onSpeedUpdate(speed: Float, location: Location) {
        runOnUiThread {
            walkingSpeedService?.let { service ->
                speedDataPoints = service.getSpeedDataPoints().toMutableList()
                totalDistance = service.getTotalDistance()
                exceedCount = service.getExceedCount()
            }
            updateChartData()
            updateStatistics()
        }
    }

    private fun updateChartData() {
        if (speedDataPoints.isEmpty()) return

        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK

        // ═══════════════════════════════════════════════════════════
        // GESCHWINDIGKEITS-LINIE (wie bisher)
        // ═══════════════════════════════════════════════════════════
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
            setDrawValues(false)
        }

        // ═══════════════════════════════════════════════════════════
        // LIMIT-LINIEN (MEHRERE bei Wechsel!)
        // ═══════════════════════════════════════════════════════════
        val allDataSets = mutableListOf<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>(dataSet)

        if (limitChanges.isEmpty()) {
            // ✅ FALL 1: Kein Wechsel → eine durchgehende Limit-Linie
            val limitEntries = entries.map { Entry(it.x, speedLimit) }
            val limitSet = LineDataSet(limitEntries, "Limit ${String.format("%.1f", speedLimit)} km/h").apply {
                color = Color.RED
                lineWidth = 1f
                setDrawCircles(false)
                setDrawValues(false)
                enableDashedLine(10f, 5f, 0f)
            }
            allDataSets.add(limitSet)

        } else {
            // ✅ FALL 2: Wechsel vorhanden → mehrere Limit-Segmente

            // Sortierte Wechsel mit Start-Limit
            val changes = mutableListOf(
                LimitChange(sessionStartTime, 0f, limitChanges.firstOrNull()?.oldLimit ?: speedLimit)
            )
            changes.addAll(limitChanges)

            // Für jeden Abschnitt eine Limit-Linie
            for (i in 0 until changes.size) {
                val change = changes[i]
                val nextChange = changes.getOrNull(i + 1)

                val limitValue = if (i == 0) change.newLimit else change.newLimit

                val startTime = ((change.timestamp - sessionStartTime) / 1000f)
                val endTime = if (nextChange != null) {
                    ((nextChange.timestamp - sessionStartTime) / 1000f)
                } else {
                    entries.lastOrNull()?.x ?: startTime
                }

                // Limit-Linie für diesen Zeitraum
                val segmentEntries = listOf(
                    Entry(startTime, limitValue),
                    Entry(endTime, limitValue)
                )

                val limitSet = LineDataSet(segmentEntries, "Limit ${String.format("%.1f", limitValue)} km/h").apply {
                    color = when (i % 3) {
                        0 -> Color.RED
                        1 -> Color.parseColor("#FF6600")
                        else -> Color.parseColor("#CC0000")
                    }
                    lineWidth = 1f
                    setDrawCircles(false)
                    setDrawValues(false)
                    enableDashedLine(10f, 5f, 0f)
                }
                allDataSets.add(limitSet)
            }
        }

        // Chart aktualisieren
        speedChart.data = LineData(allDataSets)
        speedChart.xAxis.textColor = textColor
        speedChart.axisLeft.textColor = textColor
        speedChart.legend.textColor = textColor
        speedChart.notifyDataSetChanged()
        speedChart.invalidate()
    }


    private fun updateStatistics() {
        if (speedDataPoints.isEmpty()) {
            binding.tvStats.text = "Keine Daten vorhanden"
            return
        }

        val avgSpeed = speedDataPoints.map { it.speed }.average().toFloat()
        val maxSpeed = speedDataPoints.maxOf { it.speed }

        val elapsedMillis = if (walkingSpeedService?.isTracking() == true) {
            System.currentTimeMillis() - sessionStartTime
        } else {
            (speedDataPoints.lastOrNull()?.timestamp ?: sessionStartTime) - sessionStartTime
        }

        val elapsedTime = elapsedMillis / 1000
        val hours = elapsedTime / 3600
        val minutes = (elapsedTime % 3600) / 60
        val seconds = elapsedTime % 60

        binding.tvStats.text = """
            Dauer: ${String.format("%02d:%02d:%02d", hours, minutes, seconds)}
            Strecke: ${String.format("%.2f km", totalDistance)}
            Durchschnitt: ${String.format("%.1f km/h", avgSpeed)}
            Maximum: ${String.format("%.1f km/h", maxSpeed)}
            Überschreitungen: $exceedCount
            Limit: ${String.format("%.1f km/h", speedLimit)}
        """.trimIndent()
    }

    private fun startTracking() {
        val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation) {
            Toast.makeText(this, "Standort-Berechtigung erforderlich", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, WalkingSpeedService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

            speedDataPoints.clear()
            limitChanges.clear()  // ✅ NEU
            sessionStartTime = System.currentTimeMillis()
            totalDistance = 0f
            exceedCount = 0
            speedChart.clear()
            binding.tvChartTitle.text = "📊 Aktueller Geschwindigkeitsverlauf"

            updateUI()
            startUIUpdates()
        } catch (e: Exception) {}
    }

    private fun stopTracking() {
        saveSessionToHistory()

        walkingSpeedService?.stopTracking()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        stopService(Intent(this, WalkingSpeedService::class.java))
        stopUIUpdates()
        updateUI()
    }

    private fun updateUI() {
        val isTracking = walkingSpeedService?.isTracking() == true
        binding.btnStartStop.text = if (isTracking) "🛑 Messung stoppen" else "🏁 Messung starten"
        binding.btnStartStop.setBackgroundColor(if (isTracking) getColor(android.R.color.holo_red_dark) else getColor(R.color.green_primary))

        val currentSpeed = walkingSpeedService?.getCurrentSpeed() ?: 0f
        binding.tvCurrentSpeed.text = String.format("Geschwindigkeit: %.1f km/h", currentSpeed)
        binding.tvStatus.text = if (isTracking) "🔔 Messung aktiv" else "🛑 Messung gestoppt"

        binding.btnExport.isEnabled = speedDataPoints.isNotEmpty()
        binding.btnExportGpx.isEnabled = speedDataPoints.isNotEmpty()
        binding.btnShare.isEnabled = speedDataPoints.isNotEmpty()
    }

    private fun startUIUpdates() {
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
        uiUpdateHandler.post(uiUpdateRunnable)
    }

    private fun stopUIUpdates() {
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_instructions -> { showInstructionsDialog(); true }
            R.id.action_permissions -> { showPermissionsDialog(); true }
            R.id.action_about -> { showAboutDialog(); true }
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestPermissionsAndStart() {
        val isDarkThemeMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDarkThemeMode) Color.WHITE else Color.parseColor("#2d5016")

        // 1. Standort-Erklärungs-Dialog (Angepasst auf Vordergrund-Nutzung)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("📍 Standortdienste aktivieren")
                .setMessage("HUNDEWILDNIS - App benötigt Zugriff auf Deinen Standort für folgende Zwecke:\n\n" +
                        "• NOTFALLMELDUNG:\nUm Deine Sicherheit und die Deines Hundes zu erhöhen, kann HundeWildnis Deinen aktuellen GPS-Standort erfassen und in die Notfallmeldung (PDF) einfügen.\n" +
                        "Dies ermöglicht es Tierärzten oder Rettungskräften Euch im Ernstfall schneller zu finden.\n" +
                        "• Die Erfassung erfolgt NUR, wenn Du diesen Schalter aktivierst.\n" +
                        "• Die Daten werden NUR lokal in das PDF-Dokument geschrieben und dem Empfänger der Notfallmeldung übermittelt.\n" +
                        "• Es erfolgt KEINE Übermittlung an HundeWildnis oder Dritte.\n\n\n" +
                        "• TEMPO-MESSUNG:\nUm Deine Geh-Geschwindigkeit im Training präzise zu ermitteln und Dir zu Deiner Auswertung zur Verfügung zu stellen.\n\n" +
                        "• NAVIGATION:\nDamit Du Standort auf der Karte, Routenberechnung zum Ziel und Anzeige von relevanten Orten in der Nähe aufrufen und nutzen kannst.\n\n" +
                        "• STANDORT WETTER:\nDamit Du Wetterdaten und Wettervorhersage an Deinem aktuellen Standort aufrufen und nutzen kannst.\n\n" +
                        "• TAGESNOTIZEN:\nDu kannst Deine Tagesnotizen optional mit Wetterdaten und optional mit den GPS-Daten Deines Standortes ergänzen, um später nachvollziehen zu können, unter welchen Bedingungen Du Deine Beobachtungen zum Training gemacht hast.\n\n" +
                        "WICHTIG:\nDamit die Messung auch bei ausgeschaltetem Bildschirm, wenn die App in den Hintergrund geschoben ist oder in der Tasche ist, stabil funktioniert weil Du während des Trainings und bei Aktivität im Gelände die Hände frei haben musst, nutzt die App einen dauerhaften Dienst (Foreground Service). Dieser wird durch die Benachrichtigung in Deiner Statusleiste angezeigt.\n\n" +
                        "Ohne Aktivierung der Standortdienste, als technische Voraussetzung, kannst Du die Funktionen nicht wie beschrieben nutzen.\n\nDATENSCHUTZ:\nDeine Daten werden nur lokal verarbeitet, ausschliesslich auf Deinem Gerät gespeichert und nur von Dir allein verwaltet und nur auf deinen Wunsch zum Teilenfunktion freigegeben.\n\nZur Nutzung von Navigation und Standortwetter wird der aktuelle Standort anonymisiert an freie Server mitgeteilt, um die gewünschten Daten abzurufen und Dir zur Verfügung zu stellen.\n\nDu kannst die Berechtigungen für Standort jederzeit überprüfen in den Einstellungen:\n\nDein Gerät - Einstellungen - Apps - HundeWildnis - Berechtigungen - Standort verwalten - Zugriff nur während der Nutzung der App zulassen.\n\nDort kann die Berechtigung zum Standort jederzeit dauerhaft geändert, aktiviert oder deaktiviert werden")
                .setPositiveButton("AKTIVIEREN") { _, _ ->
                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                .setNegativeButton("ABLEHNEN", null)
                .create()
                .apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)
                }
            return
        }

        checkNotificationPermissionAndStart()
    }

    private fun checkNotificationPermissionAndStart() {
        val isDarkThemeMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDarkThemeMode) Color.WHITE else Color.parseColor("#2d5016")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                AlertDialog.Builder(this)
                    .setTitle("🔔 Benachrichtigungen aktivieren")
                    .setMessage("Damit der Tempo-Trainer auch bei ausgeschaltetem Display zuverlässig funktioniert, benötigt die App die Berechtigung für Benachrichtigungen.\nBei deaktivierten Benachrichtigungen funktioniert der Tempo-Trainer nur, wenn die App im Vordergrund ist und der Bildschirm aktiv ist.")
                    .setPositiveButton("Aktivieren") { _, _ ->
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setNegativeButton("Nicht jetzt") { _, _ ->
                        startTracking()
                    }
                    .create()
                    .apply {
                        show()
                        getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
                        getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)
                    }
                return
            }
        }
        startTracking()
    }

    private fun exportSessionToPdf(shareAfterExport: Boolean) {
        if (speedDataPoints.isEmpty()) return

        // ✅ SCHRITT 1: Chart für PDF vorbereiten (SCHWARZE Texte!)
        prepareChartForPdf()

        // ✅ SCHRITT 2: 200ms warten damit Labels gerendert werden
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                generatePdfWithStats(shareAfterExport)

                // ✅ SCHRITT 3: Chart zurück auf Theme-Farben
                restoreChartThemeColors()

            } catch (e: Exception) {
                restoreChartThemeColors()  // Auch bei Fehler zurücksetzen!
                Toast.makeText(this, "Fehler beim Export: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, 200)
    }


    // ──────────────────────────────────────────────────────────────
// ✅ NEU: Chart für PDF vorbereiten (SCHWARZ statt Theme)
// ──────────────────────────────────────────────────────────────
    private fun prepareChartForPdf() {
        // Temporär ALLE Texte auf SCHWARZ setzen (für PDF)
        speedChart.xAxis.textColor = Color.BLACK
        speedChart.axisLeft.textColor = Color.BLACK
        speedChart.legend.textColor = Color.BLACK
        speedChart.invalidate()
    }

    // ──────────────────────────────────────────────────────────────
// ✅ NEU: Chart zurück auf Theme-Farben
// ──────────────────────────────────────────────────────────────
    private fun restoreChartThemeColors() {
        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDarkTheme) Color.WHITE else Color.BLACK

        speedChart.xAxis.textColor = textColor
        speedChart.axisLeft.textColor = textColor
        speedChart.legend.textColor = textColor
        speedChart.invalidate()
    }



    // ──────────────────────────────────────────────────────────────
    // PDF GENERIERUNG MIT STATISTIKEN
    // ──────────────────────────────────────────────────────────────
    private fun generatePdfWithStats(shareAfterExport: Boolean) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint()

        // ════════════════════════════════════════════════════════════
        // TITEL
        // ════════════════════════════════════════════════════════════
        paint.textSize = 20f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#2d5016")
        canvas.drawText("🏃 Trainingstempo - Protokoll", 50f, 50f, paint)

        // ════════════════════════════════════════════════════════════
        // DATUM & ZEIT
        // ════════════════════════════════════════════════════════════
        paint.textSize = 12f
        paint.isFakeBoldText = false
        paint.color = Color.BLACK
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
        canvas.drawText("Start: " + dateFormat.format(Date(sessionStartTime)), 50f, 80f, paint)

        // ════════════════════════════════════════════════════════════
        // STATISTIKEN - FESTE FARBEN
        // ════════════════════════════════════════════════════════════
        var y = 120f

        if (speedDataPoints.isNotEmpty()) {
            val avgSpeed = speedDataPoints.map { it.speed }.average().toFloat()
            val maxSpeed = speedDataPoints.maxOf { it.speed }
            val totalMeasurements = speedDataPoints.size
            val exceedCountForPdf = speedDataPoints.count { it.speed > speedLimit }
            val exceedPercentage = if (totalMeasurements > 0) {
                (exceedCountForPdf.toFloat() / totalMeasurements * 100)
            } else 0f

            val elapsedMillis = (speedDataPoints.lastOrNull()?.timestamp ?: sessionStartTime) - sessionStartTime
            val elapsedTime = elapsedMillis / 1000
            val hours = elapsedTime / 3600
            val minutes = (elapsedTime % 3600) / 60
            val seconds = elapsedTime % 60

            // Überschrift - GRÜN
            paint.textSize = 14f
            paint.isFakeBoldText = true
            paint.color = Color.parseColor("#2d5016")  // ✅ FESTE Farbe
            canvas.drawText("📊 Statistiken:", 50f, y, paint)
            y += 25f

            // Werte - SCHWARZ
            paint.textSize = 12f
            paint.isFakeBoldText = false
            paint.color = Color.BLACK  // ✅ FESTE Farbe

            canvas.drawText("Dauer: ${String.format("%02d:%02d:%02d", hours, minutes, seconds)}", 50f, y, paint)
            y += 20f

            canvas.drawText("Strecke: ${String.format("%.2f km", totalDistance)}", 50f, y, paint)
            y += 20f

            canvas.drawText("Durchschnitt: ${String.format("%.1f km/h", avgSpeed)}", 50f, y, paint)
            y += 20f

            canvas.drawText("Maximum: ${String.format("%.1f km/h", maxSpeed)}", 50f, y, paint)
            y += 20f

            canvas.drawText("Limit: ${String.format("%.1f km/h", speedLimit)}", 50f, y, paint)
            y += 20f

            canvas.drawText("Gesamtzahl Messungen: $totalMeasurements", 50f, y, paint)
            y += 20f

            canvas.drawText("Überschreitungen: $exceedCountForPdf", 50f, y, paint)
            y += 20f

            // ✅ PROZENT - IMMER ROT wenn > 20% (nicht grün!)
            paint.color = if (exceedPercentage > 20f) {
                Color.RED  // ✅ IMMER ROT (nicht Theme-abhängig)
            } else {
                Color.parseColor("#2d5016")  // ✅ Grün wenn OK
            }
            canvas.drawText("Anteil Überschreitungen: ${String.format("%.1f%%", exceedPercentage)}", 50f, y, paint)
            paint.color = Color.BLACK  // ✅ Zurück auf SCHWARZ
            y += 30f
        }

        // ════════════════════════════════════════════════════════════
        // LIMIT-ÄNDERUNGEN - FESTE FARBEN
        // ════════════════════════════════════════════════════════════
        if (limitChanges.isNotEmpty()) {
            paint.textSize = 14f
            paint.isFakeBoldText = true
            paint.color = Color.parseColor("#2d5016")  // ✅ FESTE Farbe
            canvas.drawText("🔄 Limit-Änderungen während Messung:", 50f, y, paint)
            y += 25f

            paint.textSize = 11f
            paint.isFakeBoldText = false
            paint.color = Color.DKGRAY  // ✅ FESTE Farbe

            limitChanges.forEachIndexed { index, change ->
                val timeFromStart = (change.timestamp - sessionStartTime) / 1000
                val minutes = timeFromStart / 60
                val seconds = timeFromStart % 60

                canvas.drawText(
                    "${index + 1}. Nach ${minutes}:${String.format("%02d", seconds)} min: " +
                            "${String.format("%.1f", change.oldLimit)} → ${String.format("%.1f", change.newLimit)} km/h",
                    50f,
                    y,
                    paint
                )
                y += 18f
            }
            y += 15f
        }

        // ════════════════════════════════════════════════════════════
        // CHART - MIT SCHWARZEN LABELS (bereits vorbereitet)
        // ════════════════════════════════════════════════════════════
        paint.textSize = 14f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor("#2d5016")  // ✅ FESTE Farbe
        canvas.drawText("📈 Geschwindigkeitsverlauf:", 50f, y, paint)
        y += 20f

        speedChart.invalidate()

        val chartBitmap = Bitmap.createBitmap(
            speedChart.width,
            speedChart.height,
            Bitmap.Config.ARGB_8888
        )
        val chartCanvas = android.graphics.Canvas(chartBitmap)
        chartCanvas.drawColor(Color.WHITE)

        // ✅ Chart zeichnen (Labels jetzt SCHWARZ!)
        speedChart.draw(chartCanvas)

        val scaledChart = Bitmap.createScaledBitmap(chartBitmap, 495, 300, true)
        canvas.drawBitmap(scaledChart, 50f, y, null)
        y += 320f

        // ════════════════════════════════════════════════════════════
        // FOOTER - IMMER GRAU (nicht Theme-abhängig)
        // ════════════════════════════════════════════════════════════
        val footerPaint = Paint().apply {
            color = Color.LTGRAY  // ✅ FESTE Farbe (nicht Theme)
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date())
        canvas.drawText(
            "Erstellt am $dateStr mit HundeWildnis App - www.wildnislehrer.de",
            297f,
            820f,
            footerPaint
        )

        pdfDocument.finishPage(page)

        // ════════════════════════════════════════════════════════════
        // SPEICHERN
        // ════════════════════════════════════════════════════════════
        val fileName = "Trainingstempo_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN).format(Date(sessionStartTime))}.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            savePdfToMediaStore(pdfDocument, fileName, shareAfterExport)
        } else {
            val documentsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "HundeWildnis"
            )
            if (!documentsDir.exists()) documentsDir.mkdirs()
            val file = File(documentsDir, fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            if (shareAfterExport) {
                sharePdfFile(file)
            }
            Toast.makeText(this, "✅ PDF gespeichert in Documents/HundeWildnis", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun savePdfToMediaStore(pdfDocument: PdfDocument, fileName: String, shareAfterExport: Boolean) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/HundeWildnis")
        }
        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { pdfDocument.writeTo(it) }
            pdfDocument.close()

            if (shareAfterExport) {
                sharePdfUri(it)
            }
            Toast.makeText(this, "✅ PDF gespeichert in Documents/HundeWildnis", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportSessionToGpx() {
        if (speedDataPoints.isEmpty()) return

        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")

            val gpxBuilder = StringBuilder()
            gpxBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            gpxBuilder.append("<gpx version=\"1.1\" creator=\"HundeWildnis\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            gpxBuilder.append("  <metadata>\n")
            gpxBuilder.append("    <time>${sdf.format(Date(sessionStartTime))}</time>\n")
            gpxBuilder.append("  </metadata>\n")
            gpxBuilder.append("  <trk>\n")
            gpxBuilder.append("    <name>Trainingstempo ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(sessionStartTime))}</name>\n")
            gpxBuilder.append("    <trkseg>\n")

            for (point in speedDataPoints) {
                // ✅ Aktuelles Limit zum Zeitpunkt des Trackpoints ermitteln
                val currentLimit = getCurrentLimitAtTimestamp(point.timestamp)

                gpxBuilder.append("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">\n")
                gpxBuilder.append("        <time>${sdf.format(Date(point.timestamp))}</time>\n")
                gpxBuilder.append("        <extensions>\n")
                gpxBuilder.append("          <speed>${point.speed / 3.6f}</speed>\n") // m/s
                gpxBuilder.append("          <limit>${currentLimit / 3.6f}</limit>\n") // ✅ NEU: Limit
                gpxBuilder.append("        </extensions>\n")
                gpxBuilder.append("      </trkpt>\n")
            }

            gpxBuilder.append("    </trkseg>\n")
            gpxBuilder.append("  </trk>\n")
            gpxBuilder.append("</gpx>")


            val fileName = "Trainingstempo_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN).format(Date(sessionStartTime))}.gpx"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveGpxToMediaStore(gpxBuilder.toString(), fileName)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "HundeWildnis")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(gpxBuilder.toString().toByteArray()) }
                Toast.makeText(this, "✅ GPX gespeichert in Documents/HundeWildnis", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler beim GPX-Export: ${e.message}", Toast.LENGTH_SHORT).show()
        }

    }

    // ──────────────────────────────────────────────────────────────
    // HILFSFUNKTION: Limit zum Zeitpunkt ermitteln
    // ──────────────────────────────────────────────────────────────
    private fun getCurrentLimitAtTimestamp(timestamp: Long): Float {
        if (limitChanges.isEmpty()) return speedLimit

        // Letzten Wechsel vor diesem Zeitpunkt finden
        val relevantChange = limitChanges
            .filter { it.timestamp <= timestamp }
            .maxByOrNull { it.timestamp }

        return relevantChange?.newLimit ?: speedLimit
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveGpxToMediaStore(gpxContent: String, fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/HundeWildnis")
        }
        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { it.write(gpxContent.toByteArray()) }
            Toast.makeText(this, "✅ GPX gespeichert in Documents/HundeWildnis", Toast.LENGTH_LONG).show()
        }
    }

    private fun sharePdfFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        sharePdfUri(uri)
    }

    private fun sharePdfUri(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Protokoll teilen"))
    }

    // ADAPTER
    inner class HistoryAdapter(
        private val sessions: List<SpeedSession>,
        private val onItemClick: (SpeedSession) -> Unit,
        private val onDeleteClick: (SpeedSession) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemSpeedSessionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemSpeedSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))
            holder.binding.tvSessionDate.text = date
            holder.binding.tvSessionSummary.text = String.format("%.2f km - Limit: %.1f km/h", session.distance, session.limit)
            holder.binding.sessionClickArea.setOnClickListener { onItemClick(session) }
            holder.binding.btnDeleteSession.setOnClickListener { onDeleteClick(session) }
        }

        override fun getItemCount() = sessions.size
    }

    override fun onStart() {
        super.onStart()
        if (WalkingSpeedService.isServiceRunning) {
            bindService(Intent(this, WalkingSpeedService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
