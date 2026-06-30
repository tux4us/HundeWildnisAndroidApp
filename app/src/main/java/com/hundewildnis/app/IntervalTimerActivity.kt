package com.hundewildnis.app

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.hundewildnis.app.databinding.ActivityIntervalTimerBinding

// ════════════════════════════════════════════════════════════════
// INTERVALL-TIMER – HAUPT-ACTIVITY
// Datei: IntervalTimerActivity.kt
//
// Ablauf:
//   [3s Vorbereitung] → [Aktiv-Phase] ⇄ [Pause-Phase] × rounds
//                     → [Abkühlphase (optional)] → [Fertig]
// ════════════════════════════════════════════════════════════════

class IntervalTimerActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityIntervalTimerBinding

    // ── Zustand ──────────────────────────────────────────────────
    private var config       = IntervalTimerConfig()
    private var dogProfile: DogProfile? = null

    private var currentPhase       = IntervalTimerPhase.PREPARE
    private var currentRound       = 0
    private var isRunning          = false
    private var isPaused           = false
    private var remainingMs        = 0L
    private var totalElapsedMs     = 0L

    // ── Timer ────────────────────────────────────────────────────
    private var phaseTimer: CountDownTimer? = null
    private var clockTimer: CountDownTimer? = null

    // ── Audio / Haptik ───────────────────────────────────────────
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null

    // ── Gespeicherte Session (nach Abschluss) ────────────────────
    private var finishedSession: IntervalTimerSession? = null

    companion object {
        private const val TAG = "IntervalTimerActivity"
        const val EXTRA_CONFIG     = "interval_config"
        const val EXTRA_DOG_PROFILE = "dog_profile"
    }

    // ════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntervalTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Extras lesen
        config     = intent.getParcelableExtra(EXTRA_CONFIG) ?: IntervalTimerConfig()
        dogProfile = intent.getParcelableExtra(EXTRA_DOG_PROFILE)

        setupToolbar()
        setupAudio()
        setupListeners()
        renderIdle()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAllTimers()
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun onSupportNavigateUp(): Boolean {
        confirmExit()
        return true
    }

    override fun onBackPressed() {
        confirmExit()
    }

    // ════════════════════════════════════════════════════════════
    // SETUP
    // ════════════════════════════════════════════════════════════

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "⏱️ Intervall-Timer"
            setDisplayHomeAsUpEnabled(true)
        }
        // Zusammenfassung unter Toolbar
        binding.tvConfigSummary.text =
            "${config.activeDurationSeconds}s aktiv  ·  " +
            "${config.restDurationSeconds}s Pause  ·  " +
            "${config.rounds} Runden  ·  " +
            "Gesamt: ${config.totalDurationLabel}"
        binding.tvDogName.text = dogProfile?.name ?: ""
        binding.tvDogName.visibility = if (dogProfile != null) View.VISIBLE else View.GONE
    }

    private fun setupAudio() {
        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        } catch (e: Exception) { null }

        vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
    }

    private fun setupListeners() {
        binding.btnStartPause.setOnClickListener {
            when {
                !isRunning && !isPaused -> startTimer()
                isRunning               -> pauseTimer()
                isPaused                -> resumeTimer()
            }
        }

        binding.btnStop.setOnClickListener {
            if (isRunning || isPaused) confirmStop()
        }

        // Verknüpfung mit Trainingstagebuch nach Abschluss
        binding.btnOpenDiary.setOnClickListener {
            openTrainingDiary()
        }
        binding.btnShare.setOnClickListener {
            shareResult()
        }
        binding.btnHistory.setOnClickListener {
            openHistory()
        }
    }

    // ════════════════════════════════════════════════════════════
    // TIMER-STEUERUNG
    // ════════════════════════════════════════════════════════════

    private fun startTimer() {
        isRunning  = true
        isPaused   = false
        currentRound = 0
        totalElapsedMs = 0L

        updateStartPauseButton()
        binding.btnStop.isEnabled = true

        startPreparePhase()
        startClock()
    }

    private fun pauseTimer() {
        isRunning = false
        isPaused  = true
        phaseTimer?.cancel()
        phaseTimer = null
        clockTimer?.cancel()
        clockTimer = null
        updateStartPauseButton()
        showPhaseCard(IntervalTimerPhase.PREPARE) // zeigt "Pausiert"-Status
        binding.tvMainTimer.text    = formatMs(remainingMs)
        binding.tvStatus.text       = "⏸ Pausiert"
        binding.tvPhaseLabel.text   = "Verbleibend in dieser Phase"
    }

    private fun resumeTimer() {
        isRunning = true
        isPaused  = false
        updateStartPauseButton()
        startClock()
        // Aktuelle Phase fortsetzen
        when (currentPhase) {
            IntervalTimerPhase.PREPARE   -> startPhaseCountdown(remainingMs) { startActivePhase() }
            IntervalTimerPhase.ACTIVE    -> startPhaseCountdown(remainingMs) { onActivePhaseEnd() }
            IntervalTimerPhase.REST      -> startPhaseCountdown(remainingMs) { onRestPhaseEnd() }
            IntervalTimerPhase.COOLDOWN  -> startPhaseCountdown(remainingMs) { onCooldownEnd() }
            IntervalTimerPhase.FINISHED  -> {}
        }
    }

    private fun stopEarly() {
        isRunning = false
        isPaused  = false
        cancelAllTimers()
        val session = buildSession(abortedEarly = true)
        finishedSession = session
        IntervalTimerManager.saveSession(this, session)
        showFinishedState(abortedEarly = true)
    }

    // ════════════════════════════════════════════════════════════
    // PHASEN
    // ════════════════════════════════════════════════════════════

    private fun startPreparePhase() {
        currentPhase = IntervalTimerPhase.PREPARE
        remainingMs  = IntervalTimerConfig.PREPARE_SECONDS * 1000L
        showPhaseCard(IntervalTimerPhase.PREPARE)
        binding.tvStatus.text     = "Mach Dich bereit …"
        binding.tvPhaseLabel.text = "Start in"
        binding.tvRoundInfo.text  = "Runde 1 von ${config.rounds}"

        startPhaseCountdown(remainingMs) { startActivePhase() }
    }

    private fun startActivePhase() {
        currentRound++
        currentPhase = IntervalTimerPhase.ACTIVE
        remainingMs  = config.activeDurationSeconds * 1000L
        showPhaseCard(IntervalTimerPhase.ACTIVE)
        binding.tvStatus.text     = "🟢 Aktiv"
        binding.tvPhaseLabel.text = "Aktiv-Phase"
        binding.tvRoundInfo.text  = "Runde $currentRound von ${config.rounds}"
        binding.progressRounds.progress = currentRound - 1

        playSignal(SignalType.ACTIVE_START)

        startPhaseCountdown(remainingMs) { onActivePhaseEnd() }
    }

    private fun onActivePhaseEnd() {
        if (currentRound >= config.rounds) {
            // Alle Runden durch
            if (config.cooldownSeconds > 0) {
                startCooldownPhase()
            } else {
                onCooldownEnd()
            }
        } else {
            startRestPhase()
        }
    }

    private fun startRestPhase() {
        currentPhase = IntervalTimerPhase.REST
        remainingMs  = config.restDurationSeconds * 1000L
        showPhaseCard(IntervalTimerPhase.REST)
        binding.tvStatus.text     = "🔵 Pause"
        binding.tvPhaseLabel.text = "Pause"
        binding.tvRoundInfo.text  = "Nächste: Runde ${currentRound + 1} von ${config.rounds}"

        playSignal(SignalType.REST_START)

        startPhaseCountdown(remainingMs) { onRestPhaseEnd() }
    }

    private fun onRestPhaseEnd() {
        startActivePhase()
    }

    private fun startCooldownPhase() {
        currentPhase = IntervalTimerPhase.COOLDOWN
        remainingMs  = config.cooldownSeconds * 1000L
        showPhaseCard(IntervalTimerPhase.COOLDOWN)
        binding.tvStatus.text     = "❄️ Abkühlen"
        binding.tvPhaseLabel.text = "Abkühlphase"
        binding.tvRoundInfo.text  = "Alle Runden abgeschlossen!"
        binding.progressRounds.progress = config.rounds

        playSignal(SignalType.COOLDOWN_START)

        startPhaseCountdown(remainingMs) { onCooldownEnd() }
    }

    private fun onCooldownEnd() {
        isRunning = false
        cancelAllTimers()
        currentPhase = IntervalTimerPhase.FINISHED
        playSignal(SignalType.FINISHED)

        val session = buildSession(abortedEarly = false)
        finishedSession = session
        IntervalTimerManager.saveSession(this, session)
        showFinishedState(abortedEarly = false)
    }

    // ════════════════════════════════════════════════════════════
    // COUNTDOWN-TIMER HELPER
    // ════════════════════════════════════════════════════════════

    private fun startPhaseCountdown(durationMs: Long, onEnd: () -> Unit) {
        phaseTimer?.cancel()
        phaseTimer = object : CountDownTimer(durationMs, 100) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMs = millisUntilFinished
                binding.tvMainTimer.text = formatMs(millisUntilFinished)
                updateProgressBar(millisUntilFinished, durationMs)
            }
            override fun onFinish() {
                remainingMs = 0L
                binding.tvMainTimer.text = "0:00"
                if (isRunning) onEnd()
            }
        }.start()
    }

    private fun startClock() {
        clockTimer?.cancel()
        clockTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                totalElapsedMs += 1000L
                binding.tvElapsed.text = "Gesamt: ${formatMs(totalElapsedMs)}"
            }
            override fun onFinish() {}
        }.start()
    }

    private fun cancelAllTimers() {
        phaseTimer?.cancel(); phaseTimer = null
        clockTimer?.cancel(); clockTimer = null
    }

    // ════════════════════════════════════════════════════════════
    // UI-RENDERING
    // ════════════════════════════════════════════════════════════

    private fun renderIdle() {
        currentPhase = IntervalTimerPhase.PREPARE
        showPhaseCard(IntervalTimerPhase.PREPARE)
        binding.tvMainTimer.text    = formatSec(config.activeDurationSeconds)
        binding.tvStatus.text       = "Bereit"
        binding.tvPhaseLabel.text   = "Aktiv-Phase"
        binding.tvRoundInfo.text    = "Runde 1 von ${config.rounds}"
        binding.tvElapsed.text      = "Gesamt: 0:00"
        binding.progressRounds.max  = config.rounds
        binding.progressRounds.progress = 0
        binding.progressPhase.progress  = 0
        binding.btnStop.isEnabled   = false
        binding.groupFinished.visibility = View.GONE
        updateStartPauseButton()
    }

    private fun showPhaseCard(phase: IntervalTimerPhase) {
        // Hintergrundfarbe des Timer-Displays je nach Phase
        val bgColor = when (phase) {
            IntervalTimerPhase.PREPARE  -> "#1B3A0A"   // dunkelgrün (neutral)
            IntervalTimerPhase.ACTIVE   -> "#1B3A0A"   // grün
            IntervalTimerPhase.REST     -> "#0D2A4A"   // blau
            IntervalTimerPhase.COOLDOWN -> "#1A1A2E"   // dunkelblau
            IntervalTimerPhase.FINISHED -> "#1B3A0A"
        }
        binding.cardTimerDisplay.setCardBackgroundColor(
            android.graphics.Color.parseColor(bgColor)
        )

        val indicatorColor = when (phase) {
            IntervalTimerPhase.ACTIVE   -> "#4CAF50"
            IntervalTimerPhase.REST     -> "#2196F3"
            IntervalTimerPhase.COOLDOWN -> "#7C4DFF"
            else                        -> "#4CAF50"
        }
        binding.progressPhase.progressTintList =
            android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor(indicatorColor)
            )
    }

    private fun showFinishedState(abortedEarly: Boolean) {
        binding.groupFinished.visibility = View.VISIBLE
        binding.btnStartPause.isEnabled  = false
        binding.btnStop.isEnabled        = false

        val icon   = if (abortedEarly) "⏹️" else "🏆"
        val title  = if (abortedEarly) "Timer gestoppt" else "Fertig!"
        val rounds = if (abortedEarly) "$currentRound/${config.rounds} Runden" else "${config.rounds}/${config.rounds} Runden"

        binding.tvFinishedIcon.text    = icon
        binding.tvFinishedTitle.text   = title
        binding.tvFinishedRounds.text  = rounds
        binding.tvFinishedActive.text  = "Aktive Übungszeit: ${finishedSession?.effectiveActiveMinutes ?: 0} Min"

        // Trainingstagebuch-Button nur anzeigen wenn Hund und Kategorie verknüpfbar
        binding.btnOpenDiary.visibility = if (dogProfile != null) View.VISIBLE else View.GONE
        binding.btnHistory.visibility = View.VISIBLE
    }

    private fun updateStartPauseButton() {
        binding.btnStartPause.text = when {
            !isRunning && !isPaused -> "▶️  STARTEN"
            isRunning               -> "⏸  PAUSE"
            isPaused                -> "▶️  WEITER"
            else                    -> "▶️  STARTEN"
        }
    }

    private fun updateProgressBar(remaining: Long, total: Long) {
        if (total <= 0) return
        val pct = ((total - remaining) * 100L / total).toInt()
        binding.progressPhase.progress = pct
    }

    // ════════════════════════════════════════════════════════════
    // AUDIO & VIBRATION
    // ════════════════════════════════════════════════════════════

    private enum class SignalType { ACTIVE_START, REST_START, COOLDOWN_START, FINISHED }

    private fun playSignal(type: SignalType) {
        if (config.soundEnabled) {
            try {
                when (type) {
                    SignalType.ACTIVE_START  ->
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                    SignalType.REST_START    ->
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
                    SignalType.COOLDOWN_START ->
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                    SignalType.FINISHED     -> {
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                        android.os.Handler(mainLooper).postDelayed({
                            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                        }, 200)
                        android.os.Handler(mainLooper).postDelayed({
                            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 400)
                        }, 450)
                    }
                }
            } catch (_: Exception) {}
        }
        if (config.vibrationEnabled) {
            try {
                val pattern = when (type) {
                    SignalType.ACTIVE_START  -> longArrayOf(0, 100)
                    SignalType.REST_START    -> longArrayOf(0, 80, 80, 80)
                    SignalType.COOLDOWN_START -> longArrayOf(0, 200)
                    SignalType.FINISHED     -> longArrayOf(0, 100, 100, 100, 100, 300)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }
            } catch (_: Exception) {}
        }
    }

    // ════════════════════════════════════════════════════════════
    // NAVIGATION & AKTIONEN NACH ABSCHLUSS
    // ════════════════════════════════════════════════════════════

    private fun openTrainingDiary() {
        val dog = dogProfile ?: return
        val minutes = finishedSession?.effectiveActiveMinutes ?: 0
        val intent = Intent(this, AddSessionActivity::class.java).apply {
            putExtra("profile", dog)
            // Kategorie-ID: letzte verknüpfte oder Default (Leinenarbeit = 13)
            putExtra("category_id", finishedSession?.linkedCategoryId ?: 13)
            // Dauer vorausfüllen
            putExtra("prefill_minutes", minutes)
        }
        startActivity(intent)
    }

    private fun shareResult() {
        val session = finishedSession ?: return
        val text = buildString {
            appendLine("⏱️ Intervall-Timer – Ergebnis")
            if (session.dogName.isNotEmpty()) appendLine("🐕 ${session.dogName}")
            appendLine("📅 ${session.formattedDate()}")
            appendLine()
            appendLine("✅ ${session.completedRounds}/${session.config.rounds} Runden abgeschlossen")
            appendLine("⚡ ${session.config.activeDurationSeconds}s aktiv / ${session.config.restDurationSeconds}s Pause")
            appendLine("💪 Effektive Aktivzeit: ${session.effectiveActiveMinutes} Min")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Ergebnis teilen"))
    }

    private fun openHistory() {
        val intent = Intent(this, IntervalTimerHistoryActivity::class.java).apply {
            dogProfile?.let {
                putExtra(IntervalTimerHistoryActivity.EXTRA_DOG_ID, it.id)
                putExtra(IntervalTimerHistoryActivity.EXTRA_DOG_NAME, it.name)
            }
        }
        startActivity(intent)
    }

    // ════════════════════════════════════════════════════════════
    // DIALOGE
    // ════════════════════════════════════════════════════════════

    private fun confirmStop() {
        AlertDialog.Builder(this)
            .setTitle("Timer stoppen?")
            .setMessage("Der aktuelle Fortschritt wird gespeichert ($currentRound/${config.rounds} Runden).")
            .setPositiveButton("Stoppen") { _, _ -> stopEarly() }
            .setNegativeButton("Weiter", null)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(android.graphics.Color.parseColor("#C0392B"))
            }
    }

    private fun confirmExit() {
        if (!isRunning && !isPaused) { finish(); return }
        AlertDialog.Builder(this)
            .setTitle("Timer verlassen?")
            .setMessage("Der Timer läuft noch. Beim Verlassen wird der aktuelle Fortschritt gespeichert.")
            .setPositiveButton("Verlassen") { _, _ -> stopEarly(); finish() }
            .setNegativeButton("Bleiben", null)
            .show()
    }

    // ════════════════════════════════════════════════════════════
    // SESSION ERSTELLEN
    // ════════════════════════════════════════════════════════════

    private fun buildSession(abortedEarly: Boolean) = IntervalTimerSession(
        dogId          = dogProfile?.id ?: "",
        dogName        = dogProfile?.name ?: "",
        config         = config,
        completedRounds = currentRound,
        abortedEarly   = abortedEarly
    )

    // ════════════════════════════════════════════════════════════
    // FORMATIERUNG
    // ════════════════════════════════════════════════════════════

    private fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000L).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    private fun formatSec(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return "%d:%02d".format(m, s)
    }
}
