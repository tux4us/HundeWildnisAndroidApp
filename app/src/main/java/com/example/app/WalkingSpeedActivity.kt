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
    val limit: Float
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
            title = "Trainingstempo"
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
            limit = speedLimit
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
            speedLimit = when (checkedId) {
                R.id.radioSpeed25 -> 2.5f
                R.id.radioSpeed30 -> 3.0f
                R.id.radioSpeed40 -> 4.0f
                else -> 3.0f
            }
            walkingSpeedService?.setSpeedLimit(speedLimit)
        }

        binding.radioGroupInterval.setOnCheckedChangeListener { _, checkedId ->
            val interval = when (checkedId) {
                R.id.radioInterval30 -> 30000L
                R.id.radioInterval60 -> 60000L
                else -> 30000L
            }
            walkingSpeedService?.setCheckInterval(interval)
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

        val limitEntries = entries.map { Entry(it.x, speedLimit) }
        val limitSet = LineDataSet(limitEntries, "Limit").apply {
            color = Color.RED
            lineWidth = 1f
            setDrawCircles(false)
            setDrawValues(false)
            enableDashedLine(10f, 5f, 0f)
        }

        speedChart.data = LineData(dataSet, limitSet)
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

        // 1. Standort-Erklärungs-Dialog
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("📍 Standortdienste aktivieren")
                .setMessage("Diese App benötigt Zugriff auf Deinen Standort für folgende Funktionen:\n\n" +
                        "• Tempo-Messung: Um Deine Geh-Geschwindigkeit im Training präzise zu ermitteln.\n" +
                        "• Navigation: Damit Du Standort und Navigation aufrufen und nutzen kannst.\n" +
                        "• Standort-Infos: Für GPS-Daten in Deinen Notizen, um diese mit Deinem Standort zu verknüpfen.\n\n" +
                        "Ohne Aktivierung der Standortdienste, als technische Voraussetzung, kannst Du diese Funktionen nicht nutzen. Deine Daten werden nur lokal verarbeitet, ausschliesslich auf Deinem Gerät gespeichert und nur von Dir allein verwaltet und auf Wunsch zum Teilen freigegeben.")
                .setPositiveButton("Verstanden") { _, _ ->
                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                .setNegativeButton("Abbrechen", null)
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
                    .setMessage("Damit der Geschwindigkeitswächter Dich auch bei ausgeschaltetem Display zuverlässig bei Überschreitung Deines Trainingstempos benachrichtigen kann, benötigen wir die Berechtigung für Benachrichtigungen.")
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
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint()

            paint.textSize = 20f
            paint.isFakeBoldText = true
            canvas.drawText("Trainingstempo - Protokoll", 50f, 50f, paint)

            paint.textSize = 12f
            paint.isFakeBoldText = false
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
            canvas.drawText("Start: " + dateFormat.format(Date(sessionStartTime)), 50f, 80f, paint)

            var y = 120f
            val stats = binding.tvStats.text.toString().split("\n")
            for (line in stats) {
                canvas.drawText(line, 50f, y, paint)
                y += 20f
            }

            val chartBitmap = speedChart.chartBitmap
            val scaledChart = Bitmap.createScaledBitmap(chartBitmap, 495, 300, true)
            canvas.drawBitmap(scaledChart, 50f, y + 20, null)

            pdfDocument.finishPage(page)
            val fileName = "Trainingstempo_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN).format(Date(sessionStartTime))}.pdf"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                savePdfToMediaStore(pdfDocument, fileName, shareAfterExport)
            } else {
                val documentsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "HundeWildnis")
                if (!documentsDir.exists()) documentsDir.mkdirs()
                val file = File(documentsDir, fileName)
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()
                
                if (shareAfterExport) {
                    sharePdfFile(file)
                } else {
                    Toast.makeText(this, "PDF gespeichert unter Documents/HundeWildnis", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler beim Export: ${e.message}", Toast.LENGTH_SHORT).show()
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
            } else {
                Toast.makeText(this, "PDF exportiert nach Documents/HundeWildnis", Toast.LENGTH_LONG).show()
            }
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
                gpxBuilder.append("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">\n")
                gpxBuilder.append("        <time>${sdf.format(Date(point.timestamp))}</time>\n")
                gpxBuilder.append("        <extensions>\n")
                gpxBuilder.append("          <speed>${point.speed / 3.6f}</speed>\n") // m/s
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
                Toast.makeText(this, "GPX gespeichert unter Documents/HundeWildnis", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler beim GPX-Export: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
            Toast.makeText(this, "GPX exportiert nach Documents/HundeWildnis", Toast.LENGTH_LONG).show()
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
