package com.hundewildnis.app

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
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

        // Design-Anpassung (Gruppe 2 = Index 1)
        val colors = StartGroupColors.forGroup(this, 1)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val accentColor = ContextCompat.getColor(this, colors.accentColorRes)
        val chipColor = ContextCompat.getColor(this, colors.chipColorRes)
        val textColor = ContextCompat.getColor(this, if (isDark) R.color.rd_text_primary_dark else R.color.rd_text_primary_light)

        binding.toolbar.navigationIcon?.setTint(textColor)

        // Hinweis-Karten einfärben
        binding.cardCharacterHint.setCardBackgroundColor(chipColor)
        binding.tvCharacterHint.setTextColor(textColor)
        binding.cardCharacterHint.setStrokeColor(ColorStateList.valueOf(accentColor))

        binding.cardExplanation.setCardBackgroundColor(chipColor)
        binding.cardExplanation.setStrokeColor(ColorStateList.valueOf(accentColor))
        binding.tvExplanationTitle.setTextColor(accentColor)
        binding.tvAblaufTitel.setTextColor(textColor)
        binding.tvAblaufInhalt.setTextColor(textColor)
        binding.tvWarumTitel.setTextColor(textColor)
        binding.tvWarumInhalt.setTextColor(textColor)
        binding.tvSignaleTitel.setTextColor(textColor)
        binding.tvSignaleInhalt.setTextColor(textColor)

        dogProfile?.let {
            binding.tvDogLabel.text       = "🐕 ${it.name}"
            binding.tvDogLabel.visibility = View.VISIBLE
            binding.tvDogLabel.setTextColor(accentColor)
        }
    }

    private fun setupQuickProfiles() {
        val profiles = listOf(
            getString(R.string.interval_timer_preset_standard)    to IntervalTimerConfig(),
            getString(R.string.interval_timer_preset_puppy)       to IntervalTimerConfig.fromQuickProfile(QuickCharacterProfile.PUPPY),
            getString(R.string.interval_timer_preset_senior)      to IntervalTimerConfig.fromQuickProfile(QuickCharacterProfile.SENIOR),
            getString(R.string.interval_timer_preset_energetic)   to IntervalTimerConfig.fromQuickProfile(QuickCharacterProfile.ENERGETIC),
            getString(R.string.interval_timer_preset_working_dog) to IntervalTimerConfig.fromQuickProfile(QuickCharacterProfile.WORKING_DOG),
            getString(R.string.interval_timer_preset_rescue_dog)  to IntervalTimerConfig.fromQuickProfile(QuickCharacterProfile.RESCUE_DOG),
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
        val colors = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridStartTimer.layoutManager = GridLayoutManager(this, 1)
        binding.gridStartTimer.adapter = StartTileAdapter(
            tiles = listOf(StartTile("timer_start", R.drawable.ic_stopwatch, "▶️ Timer starten")),
            chipColorRes = colors.chipColorRes,
            accentColorRes = colors.accentColorRes,
            onTileClick = { startTimer() }
        )
    }

    private fun startTimer() {
        if (selectedConfig.rounds < 1) {
            Toast.makeText(this, "Bitte mindestens 1 Runde wählen.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, IntervalTimerActivity::class.java).apply {
            putExtra(IntervalTimerActivity.EXTRA_CONFIG, selectedConfig)
            dogProfile?.let { putExtra(IntervalTimerActivity.EXTRA_DOG_PROFILE, it) }
        }
        startActivity(intent)
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