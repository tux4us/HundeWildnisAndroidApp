package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import com.hundewildnis.app.databinding.ActivityIntervalTimerSetupBinding

// ════════════════════════════════════════════════════════════════
// INTERVALL-TIMER SETUP – Konfigurationsscreen
// Datei: IntervalTimerSetupActivity.kt
//
// Wird vor IntervalTimerActivity geöffnet.
// Empfängt optionales DogProfile für Wesensmerkmale-Vorschlag.
// ════════════════════════════════════════════════════════════════

class IntervalTimerSetupActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityIntervalTimerSetupBinding

    private var dogProfile: DogProfile? = null
    private var selectedConfig = IntervalTimerConfig()

    companion object {
        const val EXTRA_DOG_PROFILE = "dog_profile"

        // SeekBar-Ranges
        private val ACTIVE_RANGE   = 10..120  // Sekunden
        private val REST_RANGE     = 10..120
        private val ROUNDS_RANGE   = 1..20
        private val COOLDOWN_RANGE = 0..300   // 0 = deaktiviert
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntervalTimerSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dogProfile = intent.getParcelableExtra(EXTRA_DOG_PROFILE)

        setupToolbar()
        setupQuickProfiles()
        setupSeekBars()
        setupToggles()
        applyConfig(selectedConfig)   // Standardwerte anzeigen
        setupStartButton()

        // Wesensmerkmale-Vorschlag direkt anwenden wenn Profil vorhanden
        dogProfile?.let { dog ->
            val suggestedConfig = IntervalTimerConfig.fromCharacter(dog.toCharacter())
            applyConfig(suggestedConfig)
            binding.tvCharacterHint.visibility = View.VISIBLE
            binding.tvCharacterHint.text =
                "💡 Vorschlag basierend auf ${dog.name}s Wesensmerkmalen – Du kannst ihn frei anpassen."
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_interval_timer, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_history -> {
                val intent = Intent(this, IntervalTimerHistoryActivity::class.java).apply {
                    dogProfile?.let {
                        putExtra(IntervalTimerHistoryActivity.EXTRA_DOG_ID, it.id)
                        putExtra(IntervalTimerHistoryActivity.EXTRA_DOG_NAME, it.name)
                    }
                }
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
        dogProfile?.let {
            binding.tvDogLabel.text       = "🐕 ${it.name}"
            binding.tvDogLabel.visibility = View.VISIBLE
        }
    }

    private fun setupQuickProfiles() {
        val profiles = listOf(
            "Standard (45s / 30s / 6 Runden)"      to IntervalTimerConfig(),
            "Welpe – sanft (20s / 40s / 4 Runden)" to IntervalTimerConfig.fromQuickProfile(QuickCharacterProfile.PUPPY),
            "Senior – ruhig (30s / 60s / 5 Runden)" to IntervalTimerConfig.fromQuickProfile(QuickCharacterProfile.SENIOR),
            "Energiegeladen (60s / 30s / 8 Runden)" to IntervalTimerConfig.fromQuickProfile(QuickCharacterProfile.ENERGETIC),
            "Arbeitshund (90s / 30s / 8 Runden)"    to IntervalTimerConfig.fromQuickProfile(QuickCharacterProfile.WORKING_DOG),
            "Tierschutzhund (20s / 50s / 4 Runden)" to IntervalTimerConfig.fromQuickProfile(QuickCharacterProfile.RESCUE_DOG),
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profiles.map { it.first })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerQuickProfile.adapter = adapter

        binding.spinnerQuickProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                applyConfig(profiles[pos].second)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSeekBars() {
        // Aktiv-Dauer
        binding.seekBarActive.max = ACTIVE_RANGE.last - ACTIVE_RANGE.first
        binding.seekBarActive.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = ACTIVE_RANGE.first + progress
                selectedConfig = selectedConfig.copy(activeDurationSeconds = value)
                binding.tvActiveValue.text = "${value}s"
                updateTotalDuration()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Pause-Dauer
        binding.seekBarRest.max = REST_RANGE.last - REST_RANGE.first
        binding.seekBarRest.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = REST_RANGE.first + progress
                selectedConfig = selectedConfig.copy(restDurationSeconds = value)
                binding.tvRestValue.text = "${value}s"
                updateTotalDuration()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Runden
        binding.seekBarRounds.max = ROUNDS_RANGE.last - ROUNDS_RANGE.first
        binding.seekBarRounds.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = ROUNDS_RANGE.first + progress
                selectedConfig = selectedConfig.copy(rounds = value)
                binding.tvRoundsValue.text = "$value"
                updateTotalDuration()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Abkühlphase
        binding.seekBarCooldown.max = COOLDOWN_RANGE.last
        binding.seekBarCooldown.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedConfig = selectedConfig.copy(cooldownSeconds = progress)
                binding.tvCooldownValue.text = if (progress == 0) "Aus" else "${progress}s"
                updateTotalDuration()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupToggles() {
        binding.switchSound.setOnCheckedChangeListener { _, checked ->
            selectedConfig = selectedConfig.copy(soundEnabled = checked)
        }
        binding.switchVibration.setOnCheckedChangeListener { _, checked ->
            selectedConfig = selectedConfig.copy(vibrationEnabled = checked)
        }
    }

    private fun setupStartButton() {
        binding.btnStart.setOnClickListener {
            if (selectedConfig.rounds < 1) {
                Toast.makeText(this, "Bitte mindestens 1 Runde wählen.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, IntervalTimerActivity::class.java).apply {
                putExtra(IntervalTimerActivity.EXTRA_CONFIG, selectedConfig)
                dogProfile?.let { putExtra(IntervalTimerActivity.EXTRA_DOG_PROFILE, it) }
            }
            startActivity(intent)
        }
    }

    // ════════════════════════════════════════════════════════════
    // HELFER
    // ════════════════════════════════════════════════════════════

    /** Setzt alle UI-Elemente auf den übergebenen Config-Stand */
    private fun applyConfig(config: IntervalTimerConfig) {
        selectedConfig = config

        // SeekBars setzen (ohne Listener-Feedback-Loop)
        binding.seekBarActive.progress   = config.activeDurationSeconds - ACTIVE_RANGE.first
        binding.seekBarRest.progress     = config.restDurationSeconds   - REST_RANGE.first
        binding.seekBarRounds.progress   = config.rounds                - ROUNDS_RANGE.first
        binding.seekBarCooldown.progress = config.cooldownSeconds

        // Labels
        binding.tvActiveValue.text    = "${config.activeDurationSeconds}s"
        binding.tvRestValue.text      = "${config.restDurationSeconds}s"
        binding.tvRoundsValue.text    = "${config.rounds}"
        binding.tvCooldownValue.text  = if (config.cooldownSeconds == 0) "Aus" else "${config.cooldownSeconds}s"

        // Toggles
        binding.switchSound.isChecked     = config.soundEnabled
        binding.switchVibration.isChecked = config.vibrationEnabled

        updateTotalDuration()
    }

    private fun updateTotalDuration() {
        binding.tvTotalDuration.text = "Gesamtdauer: ${selectedConfig.totalDurationLabel}"
    }
}
