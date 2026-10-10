package com.hundewildnis.app

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MenuItem
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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

    // Audio
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

    // Shimmer-Animator (Glow-Effekt)
    private var shimmerAnimator: ValueAnimator? = null

    // Historie
    private val historyList = mutableListOf<VoiceSession>()
    private lateinit var historyAdapter: VoiceSessionAdapter

    // Chart
    private lateinit var frequencyChart: LineChart

    // Aktuell geladene Session (für Export von Historie)
    private var currentDisplayedSession: VoiceSession? = null

    companion object {
        private const val VOICE_THRESHOLD = 0.001f
        private const val TOLERANCE_HZ = 5f
        private const val HIT_COOLDOWN_MS = 1000L

        // Glow-Farben
        private const val COLOR_CARD_NORMAL = "#6FBF73"
        private const val COLOR_GLOW_AMBER  = "#FFB300"
        // Dauer des Shimmer-Sweeps in ms
        private const val SHIMMER_DURATION_MS = 1000L
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else Toast.makeText(this, "⚠️ Mikrofon erforderlich", Toast.LENGTH_LONG).show()
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunicationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.comm_toolbar_title)
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

    // ─────────────────────────────────────────────────────────────
    // Chart
    // ─────────────────────────────────────────────────────────────

    /**
     * Baut den Chart auf. Limit-Linien werden separat via [updateChartLimitLines] gesetzt,
     * damit sie auch bei Modus-Wechsel aktualisiert werden können.
     */
    private fun setupChart() {
        frequencyChart = binding.frequencyChart
        val textColor = Color.WHITE

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
                axisMinimum = 100f
                axisMaximum = 700f
                this.textColor = textColor
            }

            axisRight.isEnabled = false
            legend.textColor = textColor
        }

        // Initial alle 5 Linien (Modus = FREE beim Start)
        updateChartLimitLines()
    }

    /**
     * Setzt die roten Limit-Linien je nach aktuellem Trainingsmodus.
     * FREE  → alle 5 Zielfrequenzen
     * FOCUS → nur die gewählte Frequenz
     * Kräftiger als bisher: lineWidth 3f, größerer Label-Text.
     */
    private fun updateChartLimitLines() {
        frequencyChart.axisLeft.removeAllLimitLines()

        val freqsToShow: List<TargetFrequency> = when {
            trainingMode == TrainingMode.FOCUS && focusFrequency != null -> listOf(focusFrequency!!)
            else -> TargetFrequency.ALL
        }

        freqsToShow.forEach { target ->
            frequencyChart.axisLeft.addLimitLine(
                LimitLine(target.hz, "${target.hz.toInt()} Hz").apply {
                    lineColor = Color.RED
                    lineWidth = 3f               // kräftiger (vorher 2f)
                    textSize = 11f               // besser lesbar
                    textColor = Color.RED
                    enableDashedLine(12f, 6f, 0f)
                }
            )
        }

        frequencyChart.invalidate()
    }

    // ─────────────────────────────────────────────────────────────
    // UI-Setup
    // ─────────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            trainingMode = if (checkedId == R.id.radioModeFree) TrainingMode.FREE else TrainingMode.FOCUS
            binding.layoutFocusFrequency.visibility =
                if (trainingMode == TrainingMode.FOCUS) android.view.View.VISIBLE
                else android.view.View.GONE
            // Limit-Linien sofort aktualisieren (wirkt erst bei nächstem Start,
            // aber Vorschau im Chart ist trotzdem hilfreich)
            updateChartLimitLines()
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
            updateChartLimitLines()
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

    // ─────────────────────────────────────────────────────────────
    // Aufnahme-Steuerung
    // ─────────────────────────────────────────────────────────────

    private fun requestMicPermissionAndStart() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(this)
                .setTitle("🎤 Berechtigungen für Mikrofon-Zugriff erforderlich")
                .setMessage(
                    "Diese Funktion analysiert Deine Stimmfrequenz während des Hundetrainings oder während Du Deine Stimme für die Kommunikation mit Deinem Hund trainierst.\n\n" +
                            "Warum benötigen wir Berechtigungen für Mikrofon-Zugriff?\n\n" +
                            "• Echtzeit-Analyse: Um Deine Stimmfrequenz zu messen und Dir Feedback zu geben.\n" +
                            "• Harmonische Kommunikation: Damit Du lernst, in dem für Hunde optimalen Frequenzbereichen zu sprechen.\n" +
                            "• Training optimieren: Statistiken helfen Dir, Deine Kommunikation aufgrund der Ergebnisse zu verbessern.\n\n" +
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

        currentDisplayedSession = null
        binding.tvChartTitle.text = "🎵 Live Frequenz-Verlauf"

        isRecording = true
        binding.btnStartStop.text = getString(R.string.comm_btn_stop)
        binding.btnStartStop.setBackgroundColor(ContextCompat.getColor(this@CommunicationActivity, R.color.gl_danger_fill))

        // Steuerelemente während der Messung sperren
        setControlsEnabled(false)

        // Chart-Linien beim Start auf aktuelle Auswahl setzen
        updateChartLimitLines()

        if (soundEnabled) {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
        }

        try {
            pitchDetector = SimplePitchDetector()
            pitchDetector?.startRecording(
                onPitchDetected = { frequency, amplitude ->
                    runOnUiThread { processPitchDetection(frequency, amplitude) }
                },
                onError = { e ->
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "⚠️ Mikrofon-Fehler: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
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
        toneGenerator?.release()
        toneGenerator = null

        // Laufenden Shimmer stoppen und Karte zurücksetzen
        cancelShimmer()

        binding.btnStartStop.text = getString(R.string.comm_btn_start)
        binding.btnStartStop.setBackgroundColor(ContextCompat.getColor(this@CommunicationActivity, R.color.gl_success_fill))

        // Steuerelemente wieder freigeben
        setControlsEnabled(true)

        if (frequencyDataPoints.isNotEmpty()) {
            saveSession()
            binding.btnExport.isEnabled = true
            binding.btnShare.isEnabled = true
        }
    }

    /**
     * Aktiviert/deaktiviert alle Modus- und Frequenz-Auswahlfelder.
     * Wird beim Start/Stop der Messung aufgerufen.
     */
    private fun setControlsEnabled(enabled: Boolean) {
        binding.radioGroupMode.isEnabled = enabled
        for (i in 0 until binding.radioGroupMode.childCount) {
            binding.radioGroupMode.getChildAt(i).isEnabled = enabled
        }
        binding.radioGroupFocusFrequency.isEnabled = enabled
        for (i in 0 until binding.radioGroupFocusFrequency.childCount) {
            binding.radioGroupFocusFrequency.getChildAt(i).isEnabled = enabled
        }
        binding.switchVibration.isEnabled = enabled
        binding.switchSound.isEnabled = enabled
    }

    // ─────────────────────────────────────────────────────────────
    // Pitch-Verarbeitung
    // ─────────────────────────────────────────────────────────────

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
                triggerShimmerFeedback()
            }

            binding.tvCurrentFrequency.text = "${frequency.toInt()} Hz"
            binding.tvFrequencyStatus.text = "🎯 ${getNearestFrequency(frequency)?.name ?: "Sprechend..."}"

            val progress = ((frequency - 100f) / (700f - 100f) * 100f).toInt().coerceIn(0, 100)
            binding.progressFrequency.progress = progress

            updateChart()
            updateStatistics()
        } else {
            if (isCurrentlyVoice) {
                isCurrentlyVoice = false
                totalVoiceDuration += System.currentTimeMillis() - lastVoiceTimestamp
            }
            binding.tvFrequencyStatus.text = getString(R.string.comm_status_waiting)
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

    // ─────────────────────────────────────────────────────────────
    // Shimmer-Glow-Feedback
    // ─────────────────────────────────────────────────────────────

    /**
     * Startet einen Links-nach-Rechts-Shimmer über die Frequenz-Card.
     *
     * Implementierung: translationX-Animation auf einem halbtransparenten
     * Amber-Overlay (tvFrequencyMatch), das von -cardWidth bis +cardWidth läuft.
     * Da wir kein zusätzliches View-Overlay haben möchten, animieren wir stattdessen
     * die Hintergrundfarbe der Card von Normal → Amber → Normal über die Dauer,
     * kombiniert mit einem translationX auf tvCurrentFrequency als visuellen Sweep-Effekt.
     *
     * Konkret: Die Card-Farbe pulst Amber, während tvFrequencyMatch (jetzt als
     * Shimmer-Streifen genutzt) von links nach rechts über die Card gleitet.
     */
    private fun triggerShimmerFeedback() {
        // Laufenden Shimmer abbrechen und neu starten (überlappende Treffer)
        cancelShimmer()

        val card = binding.cardFrequency          // ID der grünen Frequenz-Card (s.u.)
        val cardWidth = card.width.toFloat().takeIf { it > 0f } ?: 800f

        // 1) Hintergrundfarbe: Normal → Amber → Normal
        val colorNormal = Color.parseColor(COLOR_CARD_NORMAL)
        val colorAmber  = Color.parseColor(COLOR_GLOW_AMBER)

        val colorAnim = ValueAnimator.ofArgb(colorNormal, colorAmber, colorNormal).apply {
            duration = SHIMMER_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                card.setCardBackgroundColor(anim.animatedValue as Int)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    card.setCardBackgroundColor(colorNormal)
                }
            })
        }

        // 2) Shimmer-Streifen (tvFrequencyMatch) von links nach rechts
        val shimmerView = binding.tvFrequencyMatch
        shimmerView.alpha = 0.55f
        shimmerView.translationX = -cardWidth

        val sweepAnim = ObjectAnimator.ofFloat(shimmerView, "translationX", -cardWidth, cardWidth).apply {
            duration = SHIMMER_DURATION_MS
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    shimmerView.alpha = 0f
                    shimmerView.translationX = 0f
                }
            })
        }

        colorAnim.start()
        sweepAnim.start()

        shimmerAnimator = colorAnim   // Referenz zum Abbrechen
    }

    private fun cancelShimmer() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        val colorNormal = Color.parseColor(COLOR_CARD_NORMAL)
        try {
            binding.cardFrequency.setCardBackgroundColor(colorNormal)
            binding.tvFrequencyMatch.alpha = 0f
            binding.tvFrequencyMatch.translationX = 0f
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────────────────────
    // Hilfsfunktionen
    // ─────────────────────────────────────────────────────────────

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
            Entry((it.timestamp) / 1000f, it.frequency)
        }

        val dataSet = LineDataSet(entries, "Frequenz").apply {
            color = Color.parseColor(COLOR_CARD_NORMAL)
            setCircleColor(Color.parseColor(COLOR_CARD_NORMAL))
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

        binding.tvStats.text = getString(
            R.string.comm_stats_format,
            min, sec, totalVoiceDuration / 1000,
            hits108Hz, time108Hz / 1000,
            hits216Hz, time216Hz / 1000,
            hits324Hz, time324Hz / 1000,
            hits432Hz, time432Hz / 1000,
            hits648Hz, time648Hz / 1000,
            efficiency
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Session-Verwaltung
    // ─────────────────────────────────────────────────────────────

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
        prefs.edit().putString("sessions", Gson().toJson(historyList)).apply()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("voice_history", Context.MODE_PRIVATE)
        val json = prefs.getString("sessions", null) ?: return
        val type = object : TypeToken<List<VoiceSession>>() {}.type
        val loaded: List<VoiceSession> = Gson().fromJson(json, type)
        historyList.clear()
        historyList.addAll(loaded)
        historyAdapter.notifyDataSetChanged()
    }

    private fun displaySession(session: VoiceSession) {
        try {
            currentDisplayedSession = session

            frequencyDataPoints.clear()
            frequencyDataPoints.addAll(session.dataPoints)

            if (frequencyDataPoints.isNotEmpty()) {
                val entries = frequencyDataPoints.map {
                    Entry((it.timestamp / 1000f), it.frequency)
                }
                val dataSet = LineDataSet(entries, "Frequenz").apply {
                    color = Color.parseColor(COLOR_CARD_NORMAL)
                    setCircleColor(Color.parseColor(COLOR_CARD_NORMAL))
                    lineWidth = 2f
                    circleRadius = 0f
                    setDrawCircles(false)
                    setDrawValues(false)
                }
                frequencyChart.data = LineData(dataSet)
                frequencyChart.notifyDataSetChanged()
                frequencyChart.invalidate()
            }

            val duration = session.totalDuration / 1000
            val min = duration / 60
            val sec = duration % 60
            val efficiency = session.getEfficiency().toInt()

            val bestFreq = session.getBestFrequency()?.name ?: getString(R.string.walking_speed_no_data)

            binding.tvStats.text = getString(
                R.string.comm_stats_history_format,
                min, sec, session.voiceDuration / 1000,
                session.hits108Hz, session.time108Hz / 1000,
                session.hits216Hz, session.time216Hz / 1000,
                session.hits324Hz, session.time324Hz / 1000,
                session.hits432Hz, session.time432Hz / 1000,
                session.hits648Hz, session.time648Hz / 1000,
                efficiency, session.avgFrequency.toInt(),
                session.minFrequency.toInt(), session.maxFrequency.toInt(),
                bestFreq
            )

            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))
            binding.tvChartTitle.text = getString(R.string.comm_session_title_format, dateStr)

            if (session.dataPoints.isNotEmpty()) {
                val lastPoint = session.dataPoints.last()
                binding.tvCurrentFrequency.text = "${lastPoint.frequency.toInt()} Hz"
                binding.tvFrequencyStatus.text = getString(R.string.comm_status_finished)
            }

            binding.btnExport.isEnabled = true
            binding.btnShare.isEnabled = true
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

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.voice_session_delete_title))
            .setMessage(getString(R.string.voice_session_delete_message))
            .setPositiveButton(getString(R.string.start_delete)) { _, _ ->
                val index = historyList.indexOf(session)
                if (index != -1) {
                    historyList.removeAt(index)
                    historyAdapter.notifyItemRemoved(index)
                    saveHistory()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().apply {
                show()
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

        if (frequencyDataPoints.isEmpty()) {
            Toast.makeText(this, "Keine Daten zum Exportieren", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
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

    private fun exportSessionToPdf(session: VoiceSession, share: Boolean) {
        lifecycleScope.launch {
            try {
                val chartBitmap = withContext(Dispatchers.Main) { frequencyChart.chartBitmap }
                val pdfFile = withContext(Dispatchers.IO) {
                    VoicePDFGenerator.generateSessionPDF(this@CommunicationActivity, session, chartBitmap)
                }
                if (share) shareFile(pdfFile) else savePdfToDocuments(pdfFile)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CommunicationActivity, "Fehler beim PDF-Export: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@CommunicationActivity, "✅ PDF gespeichert in Documents/HundeWildnis", Toast.LENGTH_LONG).show()
                }

                android.media.MediaScannerConnection.scanFile(
                    this@CommunicationActivity,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("application/pdf"),
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CommunicationActivity, "Fehler beim Speichern: ${e.message}", Toast.LENGTH_LONG).show()
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