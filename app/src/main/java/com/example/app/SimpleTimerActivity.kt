package com.hundewildnis.app

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import com.hundewildnis.app.databinding.ActivitySimpleTimerBinding

// ════════════════════════════════════════════════════════════════
// SIMPLE TIMER ACTIVITY
// Für Hitzschlag (15 Min), Krampfanfall (5 Min) etc.
// Neu erstellen: SimpleTimerActivity.kt
// ════════════════════════════════════════════════════════════════

class SimpleTimerActivity : SecureBaseActivity() {

    private lateinit var binding: ActivitySimpleTimerBinding
    private var timerMinutes: Int = 0
    private var title: String = ""
    private var countDownTimer: CountDownTimer? = null
    private var isRunning = false
    private var remainingMillis: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        timerMinutes = intent.getIntExtra("minutes", 5)
        title = intent.getStringExtra("title") ?: "Timer"
        remainingMillis = timerMinutes * 60 * 1000L

        setupUI()
        setupListeners()
        updateDisplay()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "⏱️ $title"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.tvTitle.text = title
        binding.tvDescription.text = "Timer: $timerMinutes Minuten"
    }

    private fun setupListeners() {
        binding.btnStartPause.setOnClickListener {
            if (isRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        binding.btnReset.setOnClickListener {
            resetTimer()
        }
    }

    private fun startTimer() {
        isRunning = true
        binding.btnStartPause.text = "⏸ PAUSE"

        countDownTimer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                updateDisplay()
            }

            override fun onFinish() {
                onTimerFinished()
            }
        }.start()
    }

    private fun pauseTimer() {
        isRunning = false
        binding.btnStartPause.text = "▶️ FORTSETZEN"
        countDownTimer?.cancel()
    }

    private fun resetTimer() {
        isRunning = false
        remainingMillis = timerMinutes * 60 * 1000L
        binding.btnStartPause.text = "▶️ STARTEN"
        countDownTimer?.cancel()
        updateDisplay()
        binding.cardFinished.visibility = View.GONE
    }

    private fun updateDisplay() {
        val minutes = (remainingMillis / 1000) / 60
        val seconds = (remainingMillis / 1000) % 60
        binding.tvTime.text = String.format("%02d:%02d", minutes, seconds)

        // Progress
        val totalMillis = timerMinutes * 60 * 1000L
        val progress = ((totalMillis - remainingMillis) * 100 / totalMillis).toInt()
        binding.progressTimer.progress = progress
    }

    private fun onTimerFinished() {
        isRunning = false
        binding.btnStartPause.text = "▶️ NEUSTART"
        binding.tvTime.text = "00:00"
        binding.cardFinished.visibility = View.VISIBLE

        // Vibration
        val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(
                android.os.VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 200, 500, 200, 500),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
        }

        // Ton
        try {
            val toneGen = android.media.ToneGenerator(
                android.media.AudioManager.STREAM_ALARM, 100
            )
            toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
