package com.hundewildnis.app

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import com.hundewildnis.app.databinding.ActivityHlwTimerBinding

// ════════════════════════════════════════════════════════════════
// HLW TIMER - AUTOMATISCHES METRONOM (KEIN BUTTON-DRÜCKEN!)
// Protokoll: 30 Kompressionen (15 Sek) → 2 Beatmungen (5 Sek)
// ════════════════════════════════════════════════════════════════

class HLWTimerActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityHlwTimerBinding
    private var isRunning = false
    private var currentPhase = Phase.COMPRESSION
    private var cycleCount = 0
    private var totalElapsedSeconds = 0
    private var compressionCount = 0

    private var toneGenerator: ToneGenerator? = null
    private var metronomeTimer: CountDownTimer? = null
    private var phaseTimer: CountDownTimer? = null
    private var clockTimer: CountDownTimer? = null
    private var vibrator: Vibrator? = null

    enum class Phase {
        COMPRESSION,  // 15 Sekunden (30 Kompressionen à 120 BPM)
        VENTILATION   // 5 Sekunden (2 Beatmungen)
    }

    companion object {
        private const val TAG = "HLWTimerActivity"
        const val COMPRESSION_RATE = 120        // BPM
        const val COMPRESSIONS_PER_CYCLE = 30
        const val VENTILATIONS_PER_CYCLE = 2
        const val COMPRESSION_DURATION_MS = 15000L  // 15 Sekunden
        const val VENTILATION_DURATION_MS = 5000L   // 5 Sekunden
        const val CHECK_PULSE_EVERY_MINUTES = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityHlwTimerBinding.inflate(layoutInflater)
            setContentView(binding.root)

            Log.d(TAG, "onCreate: Layout inflated")

            setupUI()
            setupListeners()
            initializeToneGenerator()
            initializeVibrator()

            Log.d(TAG, "onCreate: Setup complete")

        } catch (e: Exception) {
            Log.e(TAG, "onCreate: Error", e)
            Toast.makeText(this, "Fehler beim Start: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupUI() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.apply {
                title = "🚨 HLW Timer"
                setDisplayHomeAsUpEnabled(true)
            }

            updateUI()
            Log.d(TAG, "setupUI: Success")

        } catch (e: Exception) {
            Log.e(TAG, "setupUI: Error", e)
        }
    }

    private fun setupListeners() {
        try {
            binding.btnStart.setOnClickListener {
                Log.d(TAG, "btnStart clicked, isRunning: $isRunning")
                if (isRunning) {
                    pauseHLW()
                } else {
                    startHLW()
                }
            }

            binding.btnReset.setOnClickListener {
                Log.d(TAG, "btnReset clicked")
                resetHLW()
            }

            Log.d(TAG, "setupListeners: Success")

        } catch (e: Exception) {
            Log.e(TAG, "setupListeners: Error", e)
        }
    }

    private fun initializeToneGenerator() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            Log.d(TAG, "ToneGenerator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "ToneGenerator init failed", e)
            Toast.makeText(this, "⚠️ Audio nicht verfügbar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeVibrator() {
        try {
            vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            Log.d(TAG, "Vibrator initialized: ${vibrator != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Vibrator init failed", e)
        }
    }

    private fun startHLW() {
        try {
            Log.d(TAG, "startHLW: Starting")

            isRunning = true
            binding.btnStart.text = "⏸ PAUSE"

            startClock()
            startCompressionPhase()

            Log.d(TAG, "startHLW: Success")

        } catch (e: Exception) {
            Log.e(TAG, "startHLW: Error", e)
            Toast.makeText(this, "Fehler beim Start: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pauseHLW() {
        try {
            Log.d(TAG, "pauseHLW: Pausing")

            isRunning = false
            binding.btnStart.text = "▶️ FORTSETZEN"

            stopMetronome()
            stopPhaseTimer()
            stopClock()

            binding.tvInstructions.text = "⏸ PAUSIERT\n\nPuls prüfen!"
            binding.tvPhase.text = ""

        } catch (e: Exception) {
            Log.e(TAG, "pauseHLW: Error", e)
        }
    }

    private fun resetHLW() {
        try {
            Log.d(TAG, "resetHLW: Resetting")

            isRunning = false
            currentPhase = Phase.COMPRESSION
            cycleCount = 0
            totalElapsedSeconds = 0
            compressionCount = 0

            stopMetronome()
            stopPhaseTimer()
            stopClock()

            binding.btnStart.text = "▶️ HLW STARTEN"

            updateUI()

        } catch (e: Exception) {
            Log.e(TAG, "resetHLW: Error", e)
        }
    }

    private fun startCompressionPhase() {
        try {
            Log.d(TAG, "Starting COMPRESSION phase")

            currentPhase = Phase.COMPRESSION
            compressionCount = 0

            binding.tvInstructions.text = "💓 KOMPRESSIONEN"
            binding.tvPhase.text = "30 Kompressionen im Takt\n120 BPM (2 pro Sekunde)"
            binding.cardCompressionPhase.visibility = View.VISIBLE
            binding.cardVentilationPhase.visibility = View.GONE

            // Metronom starten (120 BPM = alle 500ms)
            startMetronome()

            // Phase-Timer: 15 Sekunden
            phaseTimer = object : CountDownTimer(COMPRESSION_DURATION_MS, 100) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = (millisUntilFinished / 1000).toInt()
                    binding.tvPhaseTime.text = "$secondsLeft Sek"

                    // Berechne Kompressions-Count (ca.)
                    val elapsed = (COMPRESSION_DURATION_MS - millisUntilFinished) / 1000.0
                    compressionCount = (elapsed * 2).toInt().coerceAtMost(30)
                    binding.tvCompressionCount.text = "$compressionCount / 30"
                    binding.progressPhase.progress = compressionCount
                }

                override fun onFinish() {
                    stopMetronome()
                    if (isRunning) {
                        startVentilationPhase()
                    }
                }
            }.start()

        } catch (e: Exception) {
            Log.e(TAG, "startCompressionPhase: Error", e)
        }
    }

    private fun startVentilationPhase() {
        try {
            Log.d(TAG, "Starting VENTILATION phase")

            currentPhase = Phase.VENTILATION
            cycleCount++

            binding.tvInstructions.text = "🫁 BEATMUNG"
            binding.tvPhase.text = "2x über die Nase beatmen\nMaul geschlossen halten"
            binding.cardCompressionPhase.visibility = View.GONE
            binding.cardVentilationPhase.visibility = View.VISIBLE

            // Kurze Vibration als Signal
            vibrator?.let { vib ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vib.vibrate(
                        android.os.VibrationEffect.createWaveform(
                            longArrayOf(0, 200, 100, 200),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(longArrayOf(0, 200, 100, 200), -1)
                }
            }

            // Phase-Timer: 5 Sekunden
            phaseTimer = object : CountDownTimer(VENTILATION_DURATION_MS, 100) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = (millisUntilFinished / 1000).toInt() + 1
                    binding.tvVentilationTime.text = "$secondsLeft Sek"
                }

                override fun onFinish() {
                    if (isRunning) {
                        startCompressionPhase()
                    }
                }
            }.start()

            updateUI()

        } catch (e: Exception) {
            Log.e(TAG, "startVentilationPhase: Error", e)
        }
    }

    private fun startMetronome() {
        try {
            // 120 BPM = alle 500ms ein Beat
            metronomeTimer = object : CountDownTimer(Long.MAX_VALUE, 500) {
                var isHighTone = false

                override fun onTick(millisUntilFinished: Long) {
                    // Alterniere zwischen hohem und tiefem Ton
                    playBeep(isHighTone)
                    isHighTone = !isHighTone

                    // Blinke Herz-Icon
                    binding.tvHeartBeat.alpha = if (isHighTone) 1.0f else 0.3f
                }
                override fun onFinish() {}
            }.start()

            Log.d(TAG, "Metronome started")

        } catch (e: Exception) {
            Log.e(TAG, "startMetronome: Error", e)
        }
    }

    private fun stopMetronome() {
        try {
            metronomeTimer?.cancel()
            metronomeTimer = null
            binding.tvHeartBeat.alpha = 1.0f
            Log.d(TAG, "Metronome stopped")
        } catch (e: Exception) {
            Log.e(TAG, "stopMetronome: Error", e)
        }
    }

    private fun stopPhaseTimer() {
        try {
            phaseTimer?.cancel()
            phaseTimer = null
        } catch (e: Exception) {
            Log.e(TAG, "stopPhaseTimer: Error", e)
        }
    }

    private fun startClock() {
        try {
            clockTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    totalElapsedSeconds++
                    updateUI()

                    // Puls-Check Erinnerung alle 2 Minuten
                    if (totalElapsedSeconds % (CHECK_PULSE_EVERY_MINUTES * 60) == 0) {
                        showPulseCheckReminder()
                    }
                }
                override fun onFinish() {}
            }.start()

            Log.d(TAG, "Clock started")

        } catch (e: Exception) {
            Log.e(TAG, "startClock: Error", e)
        }
    }

    private fun stopClock() {
        try {
            clockTimer?.cancel()
            clockTimer = null
        } catch (e: Exception) {
            Log.e(TAG, "stopClock: Error", e)
        }
    }

    private fun showPulseCheckReminder() {
        try {
            pauseHLW()

            binding.tvInstructions.text = "⏱️ PULS-CHECK!"
            binding.tvPhase.text = "Oberschenkelinnenseite fühlen\n15 Sekunden prüfen"

            binding.cardPulseCheck.visibility = View.VISIBLE

            // Nach 20 Sekunden ausblenden
            android.os.Handler(mainLooper).postDelayed({
                binding.cardPulseCheck.visibility = View.GONE
            }, 20000)

        } catch (e: Exception) {
            Log.e(TAG, "showPulseCheckReminder: Error", e)
        }
    }

    private fun playBeep(highTone: Boolean) {
        try {
            val tone = if (highTone) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_BEEP2
            toneGenerator?.startTone(tone, 100)
        } catch (e: Exception) {
            // Ignoriere Audio-Fehler
        }
    }

    private fun updateUI() {
        try {
            // Zyklen
            binding.tvCycleCount.text = "Zyklen: $cycleCount"

            // Gesamt-Zeit
            val minutes = totalElapsedSeconds / 60
            val seconds = totalElapsedSeconds % 60
            binding.tvElapsedTime.text = String.format("%02d:%02d", minutes, seconds)

            // Nächster Puls-Check
            val secondsUntilCheck = (CHECK_PULSE_EVERY_MINUTES * 60) -
                    (totalElapsedSeconds % (CHECK_PULSE_EVERY_MINUTES * 60))
            val checkMinutes = secondsUntilCheck / 60
            val checkSeconds = secondsUntilCheck % 60
            binding.tvNextPulseCheck.text = String.format(
                "Puls-Check in: %02d:%02d",
                checkMinutes,
                checkSeconds
            )

        } catch (e: Exception) {
            Log.e(TAG, "updateUI: Error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            toneGenerator?.release()
            toneGenerator = null
            stopMetronome()
            stopPhaseTimer()
            stopClock()
            Log.d(TAG, "onDestroy: Cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy: Error", e)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
