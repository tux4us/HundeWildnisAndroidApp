package com.hundewildnis.app

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hundewildnis.app.databinding.ActivitySoundLevelBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Lautstärke-Trainer (Stufe A der fachlichen Klassifikation für Stimmarbeit mit Hunden).
 *
 * Eigenständiges Tool, unabhängig vom Sprach-Trainer (CommunicationActivity), der
 * die harmonischen Zielfrequenzen (108–648 Hz) trainiert. Hier geht es ausschließlich
 * um die Lautstärke der eigenen Stimme, geschätzt aus dem Mikrofonsignal und
 * eingeordnet in Sprechweise-Stufen A1 (leise) bis A4 (scharf/rufend).
 *
 * WICHTIG: Ohne Kalibrierung wird niemals ein dB-Wert angezeigt (siehe SoundLevelEstimator).
 */
class SoundLevelActivity : SecureBaseActivity() {

    private lateinit var binding: ActivitySoundLevelBinding

    // Audio
    private var pitchDetector: SimplePitchDetector? = null
    private var isRecording = false

    // Session-Daten
    private val dataPoints = mutableListOf<SoundLevelPoint>()
    private var sessionStartTime = 0L

    // Pegel-Kalibrierung
    private var calibrationOffsetDb: Float? = null

    // Historie
    private val historyList = mutableListOf<SoundLevelSession>()
    private lateinit var historyAdapter: SoundLevelSessionAdapter

    // Aktuell geladene Session (für Export aus der Historie)
    private var currentDisplayedSession: SoundLevelSession? = null

    companion object {
        private const val PREFS_CALIBRATION = "sound_level_calibration"
        private const val KEY_CALIBRATION_OFFSET_DB = "calibration_offset_db"
        private const val PREFS_HISTORY = "sound_level_history"
        private const val CALIBRATION_DURATION_MS = 3000L
    }

    // Steuert, welche Aktion nach erteilter Mikrofonberechtigung ausgeführt wird
    // (normale Messung oder Kalibrierung), da beide dieselbe Berechtigung nutzen.
    private enum class PendingMicAction { START_RECORDING, CALIBRATE }
    private var pendingPermissionAction: PendingMicAction = PendingMicAction.START_RECORDING

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (pendingPermissionAction) {
                PendingMicAction.START_RECORDING -> startRecording()
                PendingMicAction.CALIBRATE -> showCalibrationIntroDialog()
            }
        } else {
            Toast.makeText(this, "⚠️ Mikrofon erforderlich", Toast.LENGTH_LONG).show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySoundLevelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.sound_header_title)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.navigationIcon?.setTint(Color.WHITE)

        loadCalibration()
        setupUI()
        setupHistory()
        loadHistory()
        updateCalibrationStatusUI()
    }

    private fun setupUI() {
        binding.btnStartStop.setOnClickListener {
            if (isRecording) stopRecording() else requestMicPermissionAndStart()
        }

        binding.btnCalibrate.setOnClickListener {
            if (isRecording) {
                Toast.makeText(this, "Bitte zuerst die laufende Messung stoppen", Toast.LENGTH_SHORT).show()
            } else {
                requestMicPermissionAndCalibrate()
            }
        }

        binding.btnExport.setOnClickListener { exportToPdf(false) }
        binding.btnShare.setOnClickListener { exportToPdf(true) }
    }

    private fun setupHistory() {
        historyAdapter = SoundLevelSessionAdapter(
            historyList,
            onItemClick = { session -> displaySession(session) },
            onDeleteClick = { session -> confirmDelete(session) }
        )
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@SoundLevelActivity)
            adapter = historyAdapter
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Pegel-Kalibrierung
    // ─────────────────────────────────────────────────────────────

    private fun loadCalibration() {
        val prefs = getSharedPreferences(PREFS_CALIBRATION, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_CALIBRATION_OFFSET_DB)) {
            calibrationOffsetDb = prefs.getFloat(KEY_CALIBRATION_OFFSET_DB, 0f)
        }
    }

    private fun saveCalibration(offset: Float) {
        calibrationOffsetDb = offset
        getSharedPreferences(PREFS_CALIBRATION, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_CALIBRATION_OFFSET_DB, offset)
            .apply()
    }

    private fun updateCalibrationStatusUI() {
        if (calibrationOffsetDb != null) {
            binding.tvCalibrationStatus.text = getString(R.string.sound_calib_active)
            binding.btnCalibrate.text = getString(R.string.sound_btn_calib_new)
            binding.meterView.setValue(null, calibrated = true)
        } else {
            binding.tvCalibrationStatus.text = getString(R.string.sound_calib_not_done)
            binding.btnCalibrate.text = getString(R.string.sound_btn_calib)
            binding.meterView.setValue(null, calibrated = false)
        }
    }

    private fun dialogButtonColor(): Int {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (isDark) Color.WHITE else Color.parseColor("#2d5016")
    }

    private fun requestMicPermissionAndCalibrate() {
        pendingPermissionAction = PendingMicAction.CALIBRATE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(this)
                .setTitle("🎤 Mikrofonzugriff für Kalibrierung erforderlich")
                .setMessage(
                    "Für die Pegel-Kalibrierung wird kurz auf das Mikrofon zugegriffen. " +
                            "Es wird kein Audio aufgezeichnet oder übertragen."
                )
                .setPositiveButton("AKTIVIEREN") { _, _ ->
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                .setNegativeButton("ABBRECHEN", null)
                .create().apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(dialogButtonColor())
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(dialogButtonColor())
                }
            return
        }
        showCalibrationIntroDialog()
    }

    private fun showCalibrationIntroDialog() {
        AlertDialog.Builder(this)
            .setTitle("🎚️ Pegel-Kalibrierung")
            .setMessage(
                "Halte ca. 1 m Abstand zum Smartphone und spreche, nachdem Du auf KALIBRIERE geklickt hast, " +
                        "${CALIBRATION_DURATION_MS / 1000} Sekunden lang in normaler, " +
                        "entspannter, ruhiger Gesprächslautstärke.\n\n" +
                        "Der gemessene Referenzwert wird als Ausgangspunkt für die " +
                        "Pegel-Schätzung gespeichert. Da ein Smartphone-Mikrofon kein " +
                        "kalibriertes Messgerät ist, bleibt der spätere dB-Wert eine " +
                        "grobe, gerätespezifische Schätzung."
            )
            .setPositiveButton("KALIBRIERE") { _, _ -> runCalibrationMeasurement() }
            .setNegativeButton("Abbrechen", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(dialogButtonColor())
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(dialogButtonColor())
            }
    }

    private fun runCalibrationMeasurement() {
        val amplitudeSamples = mutableListOf<Float>()
        var calibrationDetector: SimplePitchDetector? = null

        binding.btnCalibrate.isEnabled = false
        binding.btnStartStop.isEnabled = false
        binding.tvCalibrationStatus.text = getString(R.string.sound_calib_running)

        try {
            calibrationDetector = SimplePitchDetector()
            calibrationDetector.startRecording(
                onPitchDetected = { _, amplitude ->
                    if (amplitude > 0f) {
                        synchronized(amplitudeSamples) { amplitudeSamples.add(amplitude) }
                    }
                },
                onError = { e ->
                    runOnUiThread {
                        Toast.makeText(this, "⚠️ Kalibrierung fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                        binding.btnCalibrate.isEnabled = true
                        binding.btnStartStop.isEnabled = true
                        updateCalibrationStatusUI()
                    }
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.btnCalibrate.isEnabled = true
            binding.btnStartStop.isEnabled = true
            updateCalibrationStatusUI()
            return
        }

        binding.root.postDelayed({
            calibrationDetector.stopRecording()
            binding.btnCalibrate.isEnabled = true
            binding.btnStartStop.isEnabled = true

            val validSamples = synchronized(amplitudeSamples) { amplitudeSamples.filter { it > 0f } }
            if (validSamples.isEmpty()) {
                Toast.makeText(
                    this,
                    "Keine Stimme erkannt. Bitte näher am Gerät sprechen und erneut versuchen.",
                    Toast.LENGTH_LONG
                ).show()
                updateCalibrationStatusUI()
                return@postDelayed
            }

            // Median statt Mittelwert: robuster gegen kurze laute/leise Ausreißer
            val sorted = validSamples.sorted()
            val referenceAmplitude = sorted[sorted.size / 2]

            val offset = SoundLevelEstimator.calculateCalibrationOffset(referenceAmplitude)
            if (offset == null) {
                Toast.makeText(this, "Kalibrierung fehlgeschlagen. Bitte erneut versuchen.", Toast.LENGTH_LONG).show()
                updateCalibrationStatusUI()
                return@postDelayed
            }

            saveCalibration(offset)
            updateCalibrationStatusUI()
            Toast.makeText(this, "✅ Kalibrierung gespeichert", Toast.LENGTH_SHORT).show()
        }, CALIBRATION_DURATION_MS)
    }

    // ─────────────────────────────────────────────────────────────
    // Aufnahme-Steuerung
    // ─────────────────────────────────────────────────────────────

    private fun requestMicPermissionAndStart() {
        pendingPermissionAction = PendingMicAction.START_RECORDING
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(this)
                .setTitle("🎤 Berechtigung für Mikrofon-Zugriff erforderlich")
                .setMessage(
                    "Diese Funktion schätzt die Lautstärke Deiner Stimme während des Hundetrainings.\n\n" +
                            "Warum benötigen wir Berechtigungen für Mikrofon-Zugriff?\n\n" +
                            "• Echtzeit-Analyse: Um Deinen Pegel zu schätzen und Dir Feedback zu geben.\n" +
                            "• Training reflektieren: Statistiken helfen Dir, Deine Sprechweise über mehrere Sessions zu vergleichen.\n\n" +
                            "Deine Aufnahmen werden NICHT gespeichert und NUR lokal auf Deinem Gerät verarbeitet. " +
                            "Es erfolgt KEINE Übertragung an Server.\n\n" +
                            "Erklärung gemäß Google Play Store Richtlinien für Transparenz bei Anforderungen von Berechtigungen."
                )
                .setPositiveButton("AKTIVIEREN") { _, _ ->
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                .setNegativeButton("ABLEHNEN", null)
                .create().apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(dialogButtonColor())
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(dialogButtonColor())
                }
            return
        }
        startRecording()
    }

    private fun startRecording() {
        if (isRecording) return

        if (calibrationOffsetDb == null) {
            Toast.makeText(this, "Bitte zuerst kalibrieren", Toast.LENGTH_SHORT).show()
            return
        }

        dataPoints.clear()
        sessionStartTime = System.currentTimeMillis()
        currentDisplayedSession = null

        isRecording = true
        binding.btnStartStop.text = getString(R.string.sound_btn_stop)
        binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_dark))
        binding.btnCalibrate.isEnabled = false

        try {
            pitchDetector = SimplePitchDetector()
            pitchDetector?.startRecording(
                onPitchDetected = { _, amplitude ->
                    runOnUiThread { processAmplitude(amplitude) }
                },
                onError = { e ->
                    runOnUiThread {
                        Toast.makeText(this, "⚠️ Mikrofon-Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                        stopRecording()
                    }
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            stopRecording()
        }
    }

    private fun stopRecording() {
        isRecording = false
        pitchDetector?.stopRecording()
        pitchDetector = null

        binding.btnStartStop.text = getString(R.string.sound_btn_start)
        binding.btnStartStop.setBackgroundColor(Color.parseColor("#2d5016"))
        binding.btnCalibrate.isEnabled = true

        if (dataPoints.isNotEmpty()) {
            saveSession()
            binding.btnExport.isEnabled = true
            binding.btnShare.isEnabled = true
        }
    }

    private fun processAmplitude(amplitude: Float) {
        val offset = calibrationOffsetDb ?: return
        val estimatedDb = SoundLevelEstimator.estimateDb(amplitude, offset)

        if (estimatedDb != null) {
            val elapsed = System.currentTimeMillis() - sessionStartTime
            dataPoints.add(SoundLevelPoint(elapsed, estimatedDb))

            binding.meterView.setValue(estimatedDb, calibrated = true)
            updateLiveStatistics()
        } else {
            binding.meterView.setValue(null, calibrated = true)
        }
    }

    private fun updateLiveStatistics() {
        if (dataPoints.isEmpty()) return
        val values = dataPoints.map { it.estimatedDb }
        val avg = values.average().toInt()
        val min = values.min().toInt()
        val max = values.max().toInt()

        val counts = countByLevel(dataPoints)
        val total = dataPoints.size

        binding.tvStats.text = getString(
            R.string.sound_level_activity_stats,
            avg, min, max,
            SpeechLevel.A1_LEISE.label(this), counts[SpeechLevel.A1_LEISE], percent(counts[SpeechLevel.A1_LEISE]!!, total),
            SpeechLevel.A2_GESPRAECH.label(this), counts[SpeechLevel.A2_GESPRAECH], percent(counts[SpeechLevel.A2_GESPRAECH]!!, total),
            SpeechLevel.A3_ANGEHOBEN.label(this), counts[SpeechLevel.A3_ANGEHOBEN], percent(counts[SpeechLevel.A3_ANGEHOBEN]!!, total),
            SpeechLevel.A4_SCHARF.label(this), counts[SpeechLevel.A4_SCHARF], percent(counts[SpeechLevel.A4_SCHARF]!!, total)
        )
    }

    private fun countByLevel(points: List<SoundLevelPoint>): Map<SpeechLevel, Int> {
        val counts = linkedMapOf(
            SpeechLevel.A1_LEISE to 0,
            SpeechLevel.A2_GESPRAECH to 0,
            SpeechLevel.A3_ANGEHOBEN to 0,
            SpeechLevel.A4_SCHARF to 0
        )
        points.forEach {
            val level = SoundLevelEstimator.classify(it.estimatedDb)
            counts[level] = (counts[level] ?: 0) + 1
        }
        return counts
    }

    private fun percent(count: Int, total: Int): Int {
        if (total == 0) return 0
        return (count.toFloat() / total.toFloat() * 100f).toInt()
    }

    // ─────────────────────────────────────────────────────────────
    // Session speichern / Historie
    // ─────────────────────────────────────────────────────────────

    private fun saveSession() {
        val values = dataPoints.map { it.estimatedDb }
        val counts = countByLevel(dataPoints)

        val session = SoundLevelSession(
            startTime = sessionStartTime,
            endTime = System.currentTimeMillis(),
            totalDuration = System.currentTimeMillis() - sessionStartTime,
            avgEstimatedDb = values.average().toFloat(),
            minEstimatedDb = values.min(),
            maxEstimatedDb = values.max(),
            countA1 = counts[SpeechLevel.A1_LEISE] ?: 0,
            countA2 = counts[SpeechLevel.A2_GESPRAECH] ?: 0,
            countA3 = counts[SpeechLevel.A3_ANGEHOBEN] ?: 0,
            countA4 = counts[SpeechLevel.A4_SCHARF] ?: 0,
            dataPoints = dataPoints.toList()
        )

        historyList.add(0, session)
        historyAdapter.notifyItemInserted(0)
        saveHistory()
    }

    private fun saveHistory() {
        val prefs = getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE)
        prefs.edit().putString("sessions", Gson().toJson(historyList)).apply()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", null) ?: return
        val type = object : TypeToken<List<SoundLevelSession>>() {}.type
        val loaded: List<SoundLevelSession> = Gson().fromJson(json, type)
        historyList.clear()
        historyList.addAll(loaded)
        historyAdapter.notifyDataSetChanged()
    }

    private fun displaySession(session: SoundLevelSession) {
        try {
            currentDisplayedSession = session

            val duration = session.totalDuration / 1000
            val min = duration / 60
            val sec = duration % 60
            val total = session.getTotalPoints()

            binding.tvStats.text = getString(
                R.string.sound_level_activity_session_stats,
                String.format("%02d:%02d", min, sec),
                session.avgEstimatedDb.toInt(), session.minEstimatedDb.toInt(), session.maxEstimatedDb.toInt(),
                SpeechLevel.A1_LEISE.label(this), session.countA1, session.getPercentage(session.countA1),
                SpeechLevel.A2_GESPRAECH.label(this), session.countA2, session.getPercentage(session.countA2),
                SpeechLevel.A3_ANGEHOBEN.label(this), session.countA3, session.getPercentage(session.countA3),
                SpeechLevel.A4_SCHARF.label(this), session.countA4, session.getPercentage(session.countA4),
                session.getDominantLevel().label(this)
            )

            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))
            binding.meterView.setValue(
                session.avgEstimatedDb,
                calibrated = true,
                overrideLabel = getString(R.string.sound_level_activity_session_from, dateStr)
            )

            binding.btnExport.isEnabled = true
            binding.btnShare.isEnabled = true
            binding.nestedScrollView.smoothScrollTo(0, 0)

            Toast.makeText(
                this,
                getString(R.string.sound_level_activity_session_loaded, session.getDominantLevel().label(this)),
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.sound_level_activity_load_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(session: SoundLevelSession) {
        AlertDialog.Builder(this)
            .setTitle("Messung löschen?")
            .setMessage("Möchtest Du diese Trainingseinheit dauerhaft löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                val index = historyList.indexOf(session)
                if (index != -1) {
                    historyList.removeAt(index)
                    historyAdapter.notifyItemRemoved(index)
                    saveHistory()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(dialogButtonColor())
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(dialogButtonColor())
            }
    }

    // ─────────────────────────────────────────────────────────────
    // PDF-Export
    // ─────────────────────────────────────────────────────────────

    private fun exportToPdf(share: Boolean) {
        if (currentDisplayedSession != null) {
            exportSessionToPdf(currentDisplayedSession!!, share)
            return
        }

        if (dataPoints.isEmpty()) {
            Toast.makeText(this, "Keine Daten zum Exportieren", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val values = dataPoints.map { it.estimatedDb }
                val counts = countByLevel(dataPoints)

                val currentSession = SoundLevelSession(
                    startTime = sessionStartTime,
                    endTime = System.currentTimeMillis(),
                    totalDuration = System.currentTimeMillis() - sessionStartTime,
                    avgEstimatedDb = values.average().toFloat(),
                    minEstimatedDb = values.min(),
                    maxEstimatedDb = values.max(),
                    countA1 = counts[SpeechLevel.A1_LEISE] ?: 0,
                    countA2 = counts[SpeechLevel.A2_GESPRAECH] ?: 0,
                    countA3 = counts[SpeechLevel.A3_ANGEHOBEN] ?: 0,
                    countA4 = counts[SpeechLevel.A4_SCHARF] ?: 0,
                    dataPoints = dataPoints.toList()
                )

                exportSessionToPdf(currentSession, share)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SoundLevelActivity, "Fehler beim PDF-Export: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun exportSessionToPdf(session: SoundLevelSession, share: Boolean) {
        lifecycleScope.launch {
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    SoundLevelPDFGenerator.generateSessionPDF(this@SoundLevelActivity, session)
                }
                if (share) shareFile(pdfFile) else savePdfToDocuments(pdfFile)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SoundLevelActivity, "Fehler beim PDF-Export: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun savePdfToDocuments(pdfFile: File) {
        withContext(Dispatchers.IO) {
            try {
                val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOCUMENTS
                )
                val appDir = File(documentsDir, "HundeWildnis")
                if (!appDir.exists()) appDir.mkdirs()

                val targetFile = File(appDir, pdfFile.name)
                pdfFile.copyTo(targetFile, overwrite = true)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SoundLevelActivity, "✅ PDF gespeichert in Documents/HundeWildnis", Toast.LENGTH_LONG).show()
                }

                android.media.MediaScannerConnection.scanFile(
                    this@SoundLevelActivity,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("application/pdf"),
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SoundLevelActivity, "Fehler beim Speichern: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "${applicationContext.packageName}.fileprovider", file
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Lautstärke-Trainer Auswertung")
                putExtra(android.content.Intent.EXTRA_TEXT, "Meine Lautstärke-Trainer Auswertung von HundeWildnis")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, "PDF teilen"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Fehler beim Teilen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle / Navigation
    // ─────────────────────────────────────────────────────────────

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) stopRecording()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}