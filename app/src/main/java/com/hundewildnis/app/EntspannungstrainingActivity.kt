package com.hundewildnis.app

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.animation.LinearInterpolator
import com.hundewildnis.app.databinding.ActivityEntspannungstrainingBinding
import java.util.Locale

class EntspannungstrainingActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityEntspannungstrainingBinding
    private var isRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var secondsElapsed = 0
    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null

    private enum class BreathPhase { INHALE, HOLD_IN, EXHALE, HOLD_OUT }
    private var currentPhase = BreathPhase.INHALE

    companion object {
        private const val TAG = "Entspannung"
        private const val PHASE_DURATION = 4000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntspannungstrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initVibrator()

        binding.btnStartStop.setOnClickListener {
            if (isRunning) stopTraining() else startTraining()
        }
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun vibrate() {
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.entspannungstraining_title)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun startTraining() {
        Log.d(TAG, "Training gestartet")
        isRunning = true
        currentPhase = BreathPhase.INHALE
        binding.btnStartStop.text = getString(R.string.entspannungstraining_stop)
        secondsElapsed = 0
        updateTimerText()
        
        binding.switchMusic.isEnabled = false // Während des Trainings nicht ändern

        if (binding.switchMusic.isChecked) {
            startMusic()
            binding.tvMusicCredit.visibility = android.view.View.VISIBLE
        }

        startTimer()
        runCycleStep()
    }

    private fun stopTraining() {
        Log.d(TAG, "Training gestoppt")
        isRunning = false
        binding.btnStartStop.text = getString(R.string.entspannungstraining_start)
        binding.switchMusic.isEnabled = true
        
        mainHandler.removeCallbacksAndMessages(null)
        binding.breathingCircle.animate().cancel()
        
        stopMusic()
        binding.tvMusicCredit.visibility = android.view.View.GONE

        // Reset View
        binding.breathingCircle.scaleX = 1.0f
        binding.breathingCircle.scaleY = 1.0f
        binding.tvPhase.text = ""
    }

    private fun startMusic() {
        try {
            // Die Datei muss unter res/raw/entspannung_music.mp3 liegen
            val resId = resources.getIdentifier("entspannung_music", "raw", packageName)
            if (resId != 0) {
                mediaPlayer = MediaPlayer.create(this, resId)
                mediaPlayer?.isLooping = true
                mediaPlayer?.start()
            } else {
                Log.e(TAG, "Musikdatei res/raw/entspannung_music.mp3 nicht gefunden")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Starten der Musik: ${e.message}")
        }
    }

    private fun stopMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startTimer() {
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (isRunning) {
                    secondsElapsed++
                    updateTimerText()
                    mainHandler.postDelayed(this, 1000)
                }
            }
        }, 1000)
    }

    private fun updateTimerText() {
        val minutes = secondsElapsed / 60
        val seconds = secondsElapsed % 60
        binding.tvTimer.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun runCycleStep() {
        if (!isRunning) return

        vibrate()
        Log.d(TAG, "Phase Start: $currentPhase")

        when (currentPhase) {
            BreathPhase.INHALE -> {
                binding.tvPhase.text = getString(R.string.entspannungstraining_inhale)
                binding.breathingCircle.animate()
                    .scaleX(2.0f)
                    .scaleY(2.0f)
                    .setDuration(PHASE_DURATION)
                    .setInterpolator(LinearInterpolator())
                    .start()
            }
            BreathPhase.HOLD_IN -> {
                binding.tvPhase.text = getString(R.string.entspannungstraining_hold)
            }
            BreathPhase.EXHALE -> {
                binding.tvPhase.text = getString(R.string.entspannungstraining_exhale)
                binding.breathingCircle.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(PHASE_DURATION)
                    .setInterpolator(LinearInterpolator())
                    .start()
            }
            BreathPhase.HOLD_OUT -> {
                binding.tvPhase.text = getString(R.string.entspannungstraining_hold)
            }
        }

        mainHandler.postDelayed({
            if (isRunning) {
                currentPhase = when (currentPhase) {
                    BreathPhase.INHALE -> BreathPhase.HOLD_IN
                    BreathPhase.HOLD_IN -> BreathPhase.EXHALE
                    BreathPhase.EXHALE -> BreathPhase.HOLD_OUT
                    BreathPhase.HOLD_OUT -> BreathPhase.INHALE
                }
                runCycleStep()
            }
        }, PHASE_DURATION)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onPause() {
        super.onPause()
        if (isRunning) stopTraining()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTraining()
    }
}
