package com.hundewildnis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaScannerConnection
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MenuItem
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hundewildnis.app.databinding.ActivityCommunicationBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class CommunicationActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityCommunicationBinding

    // Audio - OHNE TarsosDSP!
    private var pitchDetector: SimplePitchDetector? = null
    private var isRecording = false

    // Session Data
    private val frequencyDataPoints = mutableListOf<FrequencyPoint>()
    private var sessionStartTime = 0L
    private var totalVoiceDuration = 0L
    private var lastVoiceTimestamp = 0L
    private var isCurrentlyVoice = false

    // Treffer & Zeit
    private var hits108Hz = 0
    private var hits216Hz = 0
    private var hits324Hz = 0
    private var hits432Hz = 0
    private var hits648Hz = 0
    private var time108Hz = 0L
    private var time216Hz = 0L
    private var time324Hz = 0L
    private var time432Hz = 0L
    private var time648Hz = 0L
    private var lastHitTimestamp = 0L
    private var lastHitFrequency: TargetFrequency? = null
    
    // Feedback-Symbol Timer
    private var lastMatchFeedbackTime = 0L

    // Aktuelle Werte
    private var currentFrequency = 0f

    // Einstellungen
    private var vibrationEnabled = true
    private var soundEnabled = false
    private var trainingMode = TrainingMode.FREE
    private var focusFrequency: TargetFrequency? = null

    // Feedback
    private lateinit var vibrator: Vibrator
    private var toneGenerator: ToneGenerator? = null

    // Historie
    private val historyList = mutableListOf<VoiceSession>()
    private lateinit var historyAdapter: VoiceSessionAdapter

    // Chart
    private lateinit var frequencyChart: LineChart

    // Aktuell geladene Session (für Export von Historie)
    private var currentDisplayedSession: VoiceSession? = null

    companion object {
        private const val VOICE_THRESHOLD = 0.001f  // ✅ Stark gesenkt von 0.005f (sehr empfindlich für 1-2m)
        private const val TOLERANCE_HZ = 5f
        private const val HIT_COOLDOWN_MS = 1000L
        private const val FEEDBACK_SYMBOL_COOLDOWN = 1200L
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else Toast.makeText(this, "⚠️ Mikrofon erforderlich", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunicationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Kommunikation"
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.navigationIcon?.setTint(Color.WHITE)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        setupChart()
        setupUI()
        setupHistory()
        loadHistory()
    }

    private fun setupChart() {
        frequencyChart = binding.frequencyChart
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK

        frequencyChart.apply {
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
                        val min = value.toInt() / 60
                        val sec = value.toInt() % 60
                        return String.format("%d:%02d", min, sec)
                    }
                }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 100f  // ✅ Gesenkt von 180f auf 100f für 108 Hz
                axisMaximum = 700f
                this.textColor = textColor

                // ✅ 5 ROTE LINIEN für Zielfrequenzen
                addLimitLine(LimitLine(108f, "108 Hz").apply {
                    lineColor = Color.RED
                    lineWidth = 2f
                    enableDashedLine(10f, 5f, 0f)
                })
                addLimitLine(LimitLine(216f, "216 Hz").apply {
                    lineColor = Color.RED
                    lineWidth = 2f
                    enableDashedLine(10f, 5f, 0f)
                })
                addLimitLine(LimitLine(324f, "324 Hz").apply {
                    lineColor = Color.RED
                    lineWidth = 2f
                    enableDashedLine(10f, 5f, 0f)
                })
                addLimitLine(LimitLine(432f, "432 Hz").apply {
                    lineColor = Color.RED
                    lineWidth = 2f
                    enableDashedLine(10f, 5f, 0f)
                })
                addLimitLine(LimitLine(648f, "648 Hz").apply {
                    lineColor = Color.RED
                    lineWidth = 2f
                    enableDashedLine(10f, 5f, 0f)
                })
            }

            axisRight.isEnabled = false
            legend.textColor = textColor
        }
    }

    private fun setupUI() {
        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            trainingMode = if (checkedId == R.id.radioModeFree) TrainingMode.FREE else TrainingMode.FOCUS
            binding.layoutFocusFrequency.visibility = if (trainingMode == TrainingMode.FOCUS)
                android.view.View.VISIBLE else android.view.View.GONE
        }

        binding.radioGroupFocusFrequency.setOnCheckedChangeListener { _, checkedId ->
            focusFrequency = when (checkedId) {
                R.id.radioFreq108 -> TargetFrequency.FREQUENCY_108
                R.id.radioFreq216 -> TargetFrequency.FREQUENCY_216
                R.id.radioFreq324 -> TargetFrequency.FREQUENCY_324
                R.id.radioFreq432 -> TargetFrequency.FREQUENCY_432
                R.id.radioFreq648 -> TargetFrequency.FREQUENCY_648
                else -> TargetFrequency.FREQUENCY_432
            }
        }

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked -> vibrationEnabled = isChecked }
        binding.switchSound.setOnCheckedChangeListener { _, isChecked -> soundEnabled = isChecked }

        binding.btnStartStop.setOnClickListener {
            if (isRecording) stopRecording() else requestMicPermissionAndStart()
        }

        binding.btnExport.setOnClickListener { exportToPdf(false) }
        binding.btnShare.setOnClickListener { exportToPdf(true) }
    }

    private fun setupHistory() {
        historyAdapter = VoiceSessionAdapter(historyList,
            onItemClick = { session -> displaySession(session) },
            onDeleteClick = { session -> confirmDelete(session) }
        )
        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(this@CommunicationActivity)
            adapter = historyAdapter
        }
    }

    private fun requestMicPermissionAndStart() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("🎤 Mikrofon-Zugriff erforderlich")
                .setMessage("Diese Funktion analysiert Deine Stimmfrequenz während des Hundetrainings.\n\n" +
                        "Warum benötigen wir Mikrofon-Zugriff?\n\n" +
                        "• Echtzeit-Analyse: Um Deine Stimmfrequenz zu messen und Dir Feedback zu geben.\n" +
                        "• Harmonische Kommunikation: Damit Du lernst, in den optimalen Frequenzbereichen zu sprechen.\n" +
                        "• Training optimieren: Statistiken helfen Dir, Deine Kommunikation zu verbessern.\n\n" +
                        "Deine Aufnahmen werden NICHT gespeichert und NUR lokal auf Deinem Gerät verarbeitet. " +
                        "Es erfolgt KEINE Übertragung an Server.\n\n" +
                        "Gemäß Google Play Store Richtlinien müssen wir transparent erklären, warum diese Berechtigung benötigt wird.")
                .setPositiveButton("Verstanden") { _, _ -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                .setNegativeButton("Abbrechen", null)
                .create().apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
                }
            return
        }
        startRecording()
    }

    private fun startRecording() {
        if (isRecording) return

        frequencyDataPoints.clear()
        sessionStartTime = System.currentTimeMillis()
        totalVoiceDuration = 0L
        hits108Hz = 0; hits216Hz = 0; hits324Hz = 0; hits432Hz = 0; hits648Hz = 0
        time108Hz = 0L; time216Hz = 0L; time324Hz = 0L; time432Hz = 0L; time648Hz = 0L
        lastHitTimestamp = 0L
        lastHitFrequency = null
        isCurrentlyVoice = false

        // ✅ Reset geladene Session
        currentDisplayedSession = null
        binding.tvChartTitle.text = "🎵 Live Frequenz-Verlauf (5 Zielfrequenzen)"

        isRecording = true
        binding.btnStartStop.text = "🛑 Messung stoppen"
        binding.btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_dark))

        if (soundEnabled) {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
        }

        try {
            pitchDetector = SimplePitchDetector()
            pitchDetector?.startRecording { frequency, amplitude ->
                runOnUiThread {
                    processPitchDetection(frequency, amplitude)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            stopRecording()
        }
    }

    private fun processPitchDetection(frequency: Float, amplitude: Float) {
        val isVoice = amplitude > VOICE_THRESHOLD && frequency > 80f && frequency < 800f

        if (isVoice) {
            currentFrequency = frequency
            val now = System.currentTimeMillis()
            val elapsed = now - sessionStartTime

            if (!isCurrentlyVoice) {
                isCurrentlyVoice = true
                lastVoiceTimestamp = now
            }

            frequencyDataPoints.add(FrequencyPoint(elapsed, frequency, amplitude))

            val targetFreqs = if (trainingMode == TrainingMode.FOCUS && focusFrequency != null) {
                listOf(focusFrequency!!)
            } else {
                TargetFrequency.ALL
            }

            var isAnyMatch = false
            targetFreqs.forEach { target ->
                if (abs(frequency - target.hz) <= TOLERANCE_HZ) {
                    handleHit(target, now, elapsed)
                    isAnyMatch = true
                }
            }
            
            if (isAnyMatch) {
                triggerMatchFeedback()
            }

            binding.tvCurrentFrequency.text = "${frequency.toInt()} Hz"
            binding.tvFrequencyStatus.text = "🎯 ${getNearestFrequency(frequency)?.name ?: "Sprechend..."}"

            val progress = ((frequency - 100f) / (700f - 100f) * 100f).toInt().coerceIn(0, 100)  // ✅ 100-700 Hz
            binding.progressFrequency.progress = progress

            updateChart()
            updateStatistics()
        } else {
            if (isCurrentlyVoice) {
                isCurrentlyVoice = false
                val voiceDuration = System.currentTimeMillis() - lastVoiceTimestamp
                totalVoiceDuration += voiceDuration
            }
            binding.tvFrequencyStatus.text = "Warte auf Stimme..."
            binding.tvCurrentFrequency.text = "--- Hz"
        }
    }

    private fun handleHit(target: TargetFrequency, now: Long, elapsed: Long) {
        when (target.hz) {
            108f -> hits108Hz++
            216f -> hits216Hz++
            324f -> hits324Hz++
            432f -> hits432Hz++
            648f -> hits648Hz++
        }

        if (lastHitFrequency == target) {
            val duration = now - lastHitTimestamp
            when (target.hz) {
                108f -> time108Hz += duration
                216f -> time216Hz += duration
                324f -> time324Hz += duration
                432f -> time432Hz += duration
                648f -> time648Hz += duration
            }
        }

        val lastTime = lastHitTimestamp
        lastHitFrequency = target
        lastHitTimestamp = now

        if (now - lastTime > HIT_COOLDOWN_MS) {
            provideFeedback()
        }
    }
    
    private fun triggerMatchFeedback() {
        val now = System.currentTimeMillis()
        if (now - lastMatchFeedbackTime > FEEDBACK_SYMBOL_COOLDOWN) {
            lastMatchFeedbackTime = now
            showVisualMatchFeedback()
        }
    }

    private fun showVisualMatchFeedback() {
        binding.tvFrequencyMatch.animate().cancel()
        binding.tvFrequencyMatch.alpha = 0f
        binding.tvFrequencyMatch.scaleX = 0f
        binding.tvFrequencyMatch.scaleY = 0f
        
        binding.tvFrequencyMatch.animate()
            .alpha(1f)
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                binding.tvFrequencyMatch.animate()
                    .alpha(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setStartDelay(800)
                    .setDuration(450)
                    .start()
            }
            .start()
    }

    private fun getNearestFrequency(freq: Float): TargetFrequency? {
        return TargetFrequency.ALL.minByOrNull { abs(it.hz - freq) }
            ?.takeIf { abs(it.hz - freq) <= TOLERANCE_HZ * 3 }
    }

    private fun provideFeedback() {
        if (vibrationEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(100)
            }
        }

        if (soundEnabled) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        }
    }

    private fun updateChart() {
        if (frequencyDataPoints.isEmpty()) return

        val entries = frequencyDataPoints.map {
            Entry(((it.timestamp) / 1000f), it.frequency)
        }

        val dataSet = LineDataSet(entries, "Frequenz").apply {
            color = Color.parseColor("#2d5016")
            setCircleColor(Color.parseColor("#2d5016"))
            lineWidth = 2f
            circleRadius = 0f
            setDrawCircles(false)
            setDrawValues(false)
        }

        frequencyChart.data = LineData(dataSet)
        frequencyChart.notifyDataSetChanged()
        frequencyChart.invalidate()
    }

    private fun updateStatistics() {
        val duration = (System.currentTimeMillis() - sessionStartTime) / 1000
        val min = duration / 60
        val sec = duration % 60

        val efficiency = if (totalVoiceDuration > 0) {
            val totalHarmonic = time108Hz + time216Hz + time324Hz + time432Hz + time648Hz
            (totalHarmonic.toFloat() / totalVoiceDuration.toFloat() * 100f).toInt()
        } else 0

        binding.tvStats.text = """
            ⏱️ Dauer: ${String.format("%02d:%02d", min, sec)}
            🗣️ Sprechzeit: ${totalVoiceDuration / 1000}s
            
            🎯 Treffer:
            108 Hz: $hits108Hz (${time108Hz / 1000}s)
            216 Hz: $hits216Hz (${time216Hz / 1000}s)
            324 Hz: $hits324Hz (${time324Hz / 1000}s)
            432 Hz: $hits432Hz (${time432Hz / 1000}s)
            648 Hz: $hits648Hz (${time648Hz / 1000}s)
            
            📈 Effizienz: $efficiency%
        """.trimIndent()
    }

    private fun stopRecording() {
        isRecording = false
        pitchDetector?.stopRecording()
        pitchDetector = null
        toneGenerator?.release()
        toneGenerator = null

        binding.btnStartStop.text = "🎤 Messung starten"
        binding.btnStartStop.setBackgroundColor(Color.parseColor("#2d5016"))

        if (frequencyDataPoints.isNotEmpty()) {
            saveSession()
            binding.btnExport.isEnabled = true
            binding.btnShare.isEnabled = true
        }
    }

    private fun saveSession() {
        if (frequencyDataPoints.isEmpty()) return

        val avgFreq = frequencyDataPoints.map { it.frequency }.average().toFloat()
        val minFreq = frequencyDataPoints.minOf { it.frequency }
        val maxFreq = frequencyDataPoints.maxOf { it.frequency }

        val session = VoiceSession(
            startTime = sessionStartTime,
            endTime = System.currentTimeMillis(),
            mode = trainingMode,
            focusFrequency = if (trainingMode == TrainingMode.FOCUS) focusFrequency else null,
            totalDuration = System.currentTimeMillis() - sessionStartTime,
            voiceDuration = totalVoiceDuration,
            pauseDuration = (System.currentTimeMillis() - sessionStartTime) - totalVoiceDuration,
            hits108Hz = hits108Hz,
            hits216Hz = hits216Hz,
            hits324Hz = hits324Hz,
            hits432Hz = hits432Hz,
            hits648Hz = hits648Hz,
            time108Hz = time108Hz,
            time216Hz = time216Hz,
            time324Hz = time324Hz,
            time432Hz = time432Hz,
            time648Hz = time648Hz,
            avgFrequency = avgFreq,
            minFrequency = minFreq,
            maxFrequency = maxFreq,
            dataPoints = frequencyDataPoints.toList()
        )

        historyList.add(0, session)
        historyAdapter.notifyItemInserted(0)
        saveHistory()
    }

    private fun saveHistory() {
        val prefs = getSharedPreferences("voice_history", Context.MODE_PRIVATE)
        val json = Gson().toJson(historyList)
        prefs.edit().putString("sessions", json).apply()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("voice_history", Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", null)
        if (json != null) {
            val type = object : TypeToken<List<VoiceSession>>() {}.type
            val loaded: List<VoiceSession> = Gson().fromJson(json, type)
            historyList.clear()
            historyList.addAll(loaded)
            historyAdapter.notifyDataSetChanged()
        }
    }

    private fun displaySession(session: VoiceSession) {
        // Session-Daten in UI laden
        try {
            // Aktuell angezeigte Session speichern (für Export)
            currentDisplayedSession = session

            // Chart leeren und neu aufbauen
            frequencyDataPoints.clear()
            frequencyDataPoints.addAll(session.dataPoints)

            // Chart aktualisieren
            if (frequencyDataPoints.isNotEmpty()) {
                val entries = frequencyDataPoints.map {
                    Entry((it.timestamp / 1000f), it.frequency)
                }

                val dataSet = LineDataSet(entries, "Frequenz").apply {
                    color = Color.parseColor("#2d5016")
                    setCircleColor(Color.parseColor("#2d5016"))
                    lineWidth = 2f
                    circleRadius = 0f
                    setDrawCircles(false)
                    setDrawValues(false)
                }

                frequencyChart.data = LineData(dataSet)
                frequencyChart.notifyDataSetChanged()
                frequencyChart.invalidate()
            }

            // Statistik anzeigen
            val duration = session.totalDuration / 1000
            val min = duration / 60
            val sec = duration % 60
            val efficiency = session.getEfficiency().toInt()

            binding.tvStats.text = """
                ⏱️ Dauer: ${String.format("%02d:%02d", min, sec)}
                🗣️ Sprechzeit: ${session.voiceDuration / 1000}s
                
                🎯 Treffer:
                108 Hz: ${session.hits108Hz} (${session.time108Hz / 1000}s)
                216 Hz: ${session.hits216Hz} (${session.time216Hz / 1000}s)
                324 Hz: ${session.hits324Hz} (${session.time324Hz / 1000}s)
                432 Hz: ${session.hits432Hz} (${session.time432Hz / 1000}s)
                648 Hz: ${session.hits648Hz} (${session.time648Hz / 1000}s)
                
                📈 Effizienz: $efficiency%
                📊 Ø Frequenz: ${session.avgFrequency.toInt()} Hz
                🔽 Min: ${session.minFrequency.toInt()} Hz
                🔼 Max: ${session.maxFrequency.toInt()} Hz
                ⭐ Beste: ${session.getBestFrequency()?.name ?: "Keine Treffer"}
            """.trimIndent()

            // Chart-Titel aktualisieren
            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))
            binding.tvChartTitle.text = "🎵 Session vom $dateStr"

            // Aktuelle Frequenz-Anzeige auf Session-Daten setzen
            if (session.dataPoints.isNotEmpty()) {
                val lastPoint = session.dataPoints.last()
                binding.tvCurrentFrequency.text = "${lastPoint.frequency.toInt()} Hz"
                binding.tvFrequencyStatus.text = "Session beendet"
            }

            // Export-Buttons aktivieren (für diese Session)
            binding.btnExport.isEnabled = true
            binding.btnShare.isEnabled = true

            // Scroll nach oben, damit User die Daten sieht
            binding.nestedScrollView.smoothScrollTo(0, 0)

            Toast.makeText(
                this,
                "Session geladen: ${session.getBestFrequency()?.name ?: "Keine Treffer"} (${efficiency}% Effizienz)",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Fehler beim Laden: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(session: VoiceSession) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

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
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
            }
    }

    private fun exportToPdf(share: Boolean) {
        // Prüfe ob eine Session angezeigt wird (aus Historie) oder aktuelle Messung
        if (currentDisplayedSession != null) {
            // Export der geladenen Session
            exportSessionToPdf(currentDisplayedSession!!, share)
            return
        }

        if (frequencyDataPoints.isEmpty()) {
            Toast.makeText(this, "Keine Daten zum Exportieren", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Session erstellen
                val avgFreq = frequencyDataPoints.map { it.frequency }.average().toFloat()
                val minFreq = frequencyDataPoints.minOf { it.frequency }
                val maxFreq = frequencyDataPoints.maxOf { it.frequency }

                val currentSession = VoiceSession(
                    startTime = sessionStartTime,
                    endTime = System.currentTimeMillis(),
                    mode = trainingMode,
                    focusFrequency = if (trainingMode == TrainingMode.FOCUS) focusFrequency else null,
                    totalDuration = System.currentTimeMillis() - sessionStartTime,
                    voiceDuration = totalVoiceDuration,
                    pauseDuration = (System.currentTimeMillis() - sessionStartTime) - totalVoiceDuration,
                    hits108Hz = hits108Hz,
                    hits216Hz = hits216Hz,
                    hits324Hz = hits324Hz,
                    hits432Hz = hits432Hz,
                    hits648Hz = hits648Hz,
                    time108Hz = time108Hz,
                    time216Hz = time216Hz,
                    time324Hz = time324Hz,
                    time432Hz = time432Hz,
                    time648Hz = time648Hz,
                    avgFrequency = avgFreq,
                    minFrequency = minFreq,
                    maxFrequency = maxFreq,
                    dataPoints = frequencyDataPoints.toList()
                )

                exportSessionToPdf(currentSession, share)

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CommunicationActivity, "Fehler beim PDF-Export: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Exportiert eine VoiceSession als PDF
     */
    private fun exportSessionToPdf(session: VoiceSession, share: Boolean) {
        lifecycleScope.launch {
            try {
                // Chart als Bitmap
                val chartBitmap = withContext(Dispatchers.Main) {
                    frequencyChart.chartBitmap
                }

                // PDF erstellen
                val pdfFile = withContext(Dispatchers.IO) {
                    VoicePDFGenerator.generateSessionPDF(this@CommunicationActivity, session, chartBitmap)
                }

                if (share) {
                    shareFile(pdfFile)
                } else {
                    savePdfToDocuments(pdfFile)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CommunicationActivity, "Fehler beim PDF-Export: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Speichert PDF in Documents/HundeWildnis (wie DailyNotes & WalkingSpeed)
     */
    private suspend fun savePdfToDocuments(pdfFile: File) {
        withContext(Dispatchers.IO) {
            try {
                // Öffentlicher Ordner Documents/HundeWildnis
                val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOCUMENTS
                )
                val appDir = File(documentsDir, "HundeWildnis")

                if (!appDir.exists()) {
                    appDir.mkdirs()
                }

                val targetFile = File(appDir, pdfFile.name)
                pdfFile.copyTo(targetFile, overwrite = true)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CommunicationActivity,
                        "PDF gespeichert:\n${targetFile.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // MediaScanner benachrichtigen (damit Datei sofort sichtbar)
                android.media.MediaScannerConnection.scanFile(
                    this@CommunicationActivity,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("application/pdf"),
                    null
                )

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CommunicationActivity,
                        "Fehler beim Speichern: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Teilt PDF mit anderen Apps (WhatsApp, Email, etc.)
     */
    private fun shareFile(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Kommunikations-Analyse")
                putExtra(android.content.Intent.EXTRA_TEXT, "Meine Kommunikations-Analyse von HundeWildnis")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(android.content.Intent.createChooser(shareIntent, "PDF teilen"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Fehler beim Teilen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

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