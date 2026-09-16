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
import androidx.recyclerview.widget.GridLayoutManager
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
            title = getString(R.string.interval_setup_title)
            setDisplayHomeAsUpEnabled(true)
        }
        // Zusammenfassung unter Toolbar
        binding.tvConfigSummary.text =
            getString(R.string.interval_unit_s, config.activeDurationSeconds) + " " + getString(R.string.interval_run_label_active_phase) + "  ·  " +
            getString(R.string.interval_unit_s, config.restDurationSeconds) + " " + getString(R.string.interval_run_label_rest_phase) + "  ·  " +
            getString(R.string.interval_run_label_round, config.rounds, config.rounds) + "  ·  " +
            getString(R.string.interval_setup_total_duration, config.getTotalDurationLabel(this))
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
        updateControlGrid()
    }

    private fun updateControlGrid() {
        val controls = mutableListOf<StartTile>()
        
        // Stop
        controls.add(StartTile("timer_stop", R.drawable.ic_remove, getString(R.string.interval_run_btn_stop), isDanger = true))
        
        // Start/Pause
        val startPauseLabel = when {
            !isRunning && !isPaused -> getString(R.string.interval_run_btn_start)
            isRunning               -> getString(R.string.interval_run_btn_pause)
            isPaused                -> getString(R.string.interval_run_btn_resume)
            else                    -> getString(R.string.interval_run_btn_start)
        }
        controls.add(StartTile("timer_start_pause", R.drawable.ic_stopwatch, startPauseLabel))

        val colors = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridTimerControls.layoutManager = GridLayoutManager(this, 2)
        binding.gridTimerControls.adapter = StartTileAdapter(
            tiles = controls,
            chipColorRes = colors.chipColorRes,
            accentColorRes = colors.accentColorRes,
            onTileClick = { id ->
                when(id) {
                    "timer_stop" -> if (isRunning || isPaused) confirmStop()
                    "timer_start_pause" -> {
                        when {
                            !isRunning && !isPaused -> startTimer()
                            isRunning               -> pauseTimer()
                            isPaused                -> resumeTimer()
                        }
                    }
                }
            }
        )
    }

    private fun updateFinishedGrid() {
        val actions = mutableListOf<StartTile>()
        
        if (dogProfile != null) {
            actions.add(StartTile("timer_diary", R.drawable.ic_diary, getString(R.string.interval_finish_btn_diary)))
        }
        actions.add(StartTile("timer_share", R.drawable.ic_map_pin_share, getString(R.string.interval_finish_btn_share)))
        actions.add(StartTile("timer_history", R.drawable.ic_notes, getString(R.string.interval_finish_btn_history)))

        val colors = StartGroupColors.forGroup(this, 3) // Ocker
        binding.gridTimerFinished.layoutManager = GridLayoutManager(this, actions.size)
        binding.gridTimerFinished.adapter = StartTileAdapter(
            tiles = actions,
            chipColorRes = colors.chipColorRes,
            accentColorRes = colors.accentColorRes,
            onTileClick = { id ->
                when(id) {
                    "timer_diary" -> openTrainingDiary()
                    "timer_share" -> shareResult()
                    "timer_history" -> openHistory()
                }
            }
        )
    }

    // ════════════════════════════════════════════════════════════
    // TIMER-STEUERUNG
    // ════════════════════════════════════════════════════════════

    private fun startTimer() {
        isRunning  = true
        isPaused   = false
        currentRound = 0
        totalElapsedMs = 0L

        updateControlGrid()

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
        updateControlGrid()
        showPhaseCard(IntervalTimerPhase.PREPARE) // zeigt "Pausiert"-Status
        binding.tvMainTimer.text    = formatMs(remainingMs)
        binding.tvStatus.text       = getString(R.string.interval_run_status_paused)
        binding.tvPhaseLabel.text   = getString(R.string.interval_run_label_remaining)
    }

    private fun resumeTimer() {
        isRunning = true
        isPaused  = false
        updateControlGrid()
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
        binding.tvStatus.text     = getString(R.string.interval_run_status_get_ready)
        binding.tvPhaseLabel.text = getString(R.string.interval_run_label_start_in)
        binding.tvRoundInfo.text  = getString(R.string.interval_run_label_round, 1, config.rounds)

        startPhaseCountdown(remainingMs) { startActivePhase() }
    }

    private fun startActivePhase() {
        currentRound++
        currentPhase = IntervalTimerPhase.ACTIVE
        remainingMs  = config.activeDurationSeconds * 1000L
        showPhaseCard(IntervalTimerPhase.ACTIVE)
        binding.tvStatus.text     = getString(R.string.interval_run_status_active)
        binding.tvPhaseLabel.text = getString(R.string.interval_run_label_active_phase)
        binding.tvRoundInfo.text  = getString(R.string.interval_run_label_round, currentRound, config.rounds)
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
        binding.tvStatus.text     = getString(R.string.interval_run_status_rest)
        binding.tvPhaseLabel.text = getString(R.string.interval_run_label_rest_phase)
        binding.tvRoundInfo.text  = getString(R.string.interval_run_label_next_round, currentRound + 1, config.rounds)

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
        binding.tvStatus.text     = getString(R.string.interval_run_status_cooldown)
        binding.tvPhaseLabel.text = getString(R.string.interval_run_label_cooldown_phase)
        binding.tvRoundInfo.text  = getString(R.string.interval_run_label_all_rounds_done)
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
                binding.tvElapsed.text = getString(R.string.interval_run_label_total_elapsed, formatMs(totalElapsedMs))
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
        binding.tvStatus.text       = getString(R.string.interval_run_status_ready)
        binding.tvPhaseLabel.text   = getString(R.string.interval_run_label_active_phase)
        binding.tvRoundInfo.text    = getString(R.string.interval_run_label_round, 1, config.rounds)
        binding.tvElapsed.text      = getString(R.string.interval_run_label_total_elapsed, "0:00")
        binding.progressRounds.max  = config.rounds
        binding.progressRounds.progress = 0
        binding.progressPhase.progress  = 0
        binding.groupFinished.visibility = View.GONE
        updateControlGrid()
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
        binding.gridTimerControls.visibility = View.GONE

        val icon   = if (abortedEarly) "⏹️" else "🏆"
        val title  = if (abortedEarly) getString(R.string.interval_finish_title_stopped) else getString(R.string.interval_finish_title_done)
        val rounds = if (abortedEarly) getString(R.string.interval_run_label_round, currentRound, config.rounds) else getString(R.string.interval_run_label_round, config.rounds, config.rounds)

        binding.tvFinishedIcon.text    = icon
        binding.tvFinishedTitle.text   = title
        binding.tvFinishedRounds.text  = rounds
        binding.tvFinishedActive.text  = getString(R.string.interval_finish_label_active_time, finishedSession?.effectiveActiveMinutes ?: 0)

        updateFinishedGrid()
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
            appendLine(getString(R.string.interval_share_header))
            if (session.dogName.isNotEmpty()) appendLine("🐕 ${session.dogName}")
            appendLine("📅 ${session.formattedDate()}")
            appendLine()
            appendLine("✅ " + getString(R.string.interval_share_rounds, session.completedRounds, session.config.rounds))
            appendLine("⚡ " + getString(R.string.interval_share_config, session.config.activeDurationSeconds, session.config.restDurationSeconds))
            appendLine("💪 " + getString(R.string.interval_share_effective_time, session.effectiveActiveMinutes))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.interval_share_title)))
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
            .setTitle(getString(R.string.interval_dialog_stop_title))
            .setMessage(getString(R.string.interval_dialog_stop_message, currentRound, config.rounds))
            .setPositiveButton(getString(R.string.interval_run_btn_stop)) { _, _ -> stopEarly() }
            .setNegativeButton(getString(R.string.interval_dialog_btn_stay), null)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(android.graphics.Color.parseColor("#C0392B"))
            }
    }

    private fun confirmExit() {
        if (!isRunning && !isPaused) { finish(); return }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.interval_dialog_leave_title))
            .setMessage(getString(R.string.interval_dialog_leave_message))
            .setPositiveButton(getString(R.string.interval_dialog_btn_leave)) { _, _ -> stopEarly(); finish() }
            .setNegativeButton(getString(R.string.interval_dialog_btn_stay), null)
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
