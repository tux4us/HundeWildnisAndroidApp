package com.hundewildnis.app

import android.content.Intent
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.GridLayoutManager
import com.hundewildnis.app.databinding.ActivityPulscheckBinding

class PulscheckActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityPulscheckBinding
    private var profile: DogProfile? = null

    // Zustand KFZ
    private var selectedCrt: Int? = null

    // Zustand Timer
    private var pulseTimer: CountDownTimer? = null
    private var respTimer: CountDownTimer? = null
    private var pulseTimerRunning = false
    private var respTimerRunning = false

    // Gespeicherter Record (nach Speichern für Notfall verfügbar)
    private var savedRecord: VitalRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPulscheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra<DogProfile>("profile")
            ?.id?.let { DogProfileManager.getProfile(this, it) }
            ?: intent.getParcelableExtra("profile")

        if (profile == null) {
            Toast.makeText(this, getString(R.string.pulscheck_error_no_profile), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.pulscheck_toolbar_title_format, profile!!.name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupPulseReference()
        setupSpinners()
        setupTimers()
        setupPulseAutoCalc()
        setupRespAutoCalc()
        setupCrtButtons()
        setupActionButtons()
    }

    // ════════════════════════════════════════════════════════════════
    // SETUP
    // ════════════════════════════════════════════════════════════════

    private fun setupPulseReference() {
        val heightCm = profile?.shoulderHeight?.trim()?.toIntOrNull()
        binding.tvPulseReference.text = VitalRecord.pulseReferenceText(this, heightCm)
    }

    private fun setupSpinners() {
        // Belastungsszenario
        val scenarioItems = listOf(getString(R.string.pulscheck_spinner_please_choose)) + VitalRecord.loadScenarios(this)
        binding.spinnerLoadScenario.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, scenarioItems
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerLoadScenario.setSelection(1) // "Ruhe" als Default

        // Schleimhautfarbe
        val colorItems = listOf(getString(R.string.pulscheck_spinner_not_measured)) + VitalRecord.mucousMembraneColors(this)
        binding.spinnerMucousColor.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, colorItems
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Bewusstsein
        val consciousnessItems = listOf(getString(R.string.pulscheck_spinner_not_assessed)) + VitalRecord.consciousnessLevels(this)
        binding.spinnerConsciousness.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, consciousnessItems
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    // ════════════════════════════════════════════════════════════════
    // TIMER
    // ════════════════════════════════════════════════════════════════

    private fun setupTimers() {
        binding.btnStartPulseTimer.setOnClickListener { togglePulseTimer() }
        binding.btnStartRespTimer.setOnClickListener { toggleRespTimer() }
    }

    private fun togglePulseTimer() {
        if (pulseTimerRunning) {
            pulseTimer?.cancel()
            pulseTimerRunning = false
            binding.tvPulseTimer.text = "15"
            binding.btnStartPulseTimer.text = getString(R.string.pulscheck_btn_start_timer)
        } else {
            startPulseTimer()
        }
    }

    private fun startPulseTimer() {
        pulseTimerRunning = true
        binding.btnStartPulseTimer.text = getString(R.string.pulscheck_btn_cancel_timer)
        binding.tvPulseTimerHint.text = getString(R.string.pulscheck_pulse_timer_hint_counting)

        pulseTimer = object : CountDownTimer(15_000, 1_000) {
            override fun onTick(ms: Long) {
                binding.tvPulseTimer.text = (ms / 1000 + 1).toString()
            }
            override fun onFinish() {
                pulseTimerRunning = false
                binding.tvPulseTimer.text = "✓"
                binding.tvPulseTimerHint.text = getString(R.string.pulscheck_pulse_timer_hint_result)
                binding.btnStartPulseTimer.text = getString(R.string.pulscheck_btn_restart_timer)
                binding.etPulseBeats.requestFocus()
            }
        }.start()
    }

    private fun toggleRespTimer() {
        if (respTimerRunning) {
            respTimer?.cancel()
            respTimerRunning = false
            binding.tvRespTimer.text = "15"
            binding.btnStartRespTimer.text = getString(R.string.pulscheck_btn_start_timer)
        } else {
            startRespTimer()
        }
    }

    private fun startRespTimer() {
        respTimerRunning = true
        binding.btnStartRespTimer.text = getString(R.string.pulscheck_btn_cancel_timer)

        respTimer = object : CountDownTimer(15_000, 1_000) {
            override fun onTick(ms: Long) {
                binding.tvRespTimer.text = (ms / 1000 + 1).toString()
            }
            override fun onFinish() {
                respTimerRunning = false
                binding.tvRespTimer.text = "✓"
                binding.btnStartRespTimer.text = getString(R.string.pulscheck_btn_restart_timer)
                binding.etRespBeats.requestFocus()
            }
        }.start()
    }

    // ════════════════════════════════════════════════════════════════
    // AUTO-BERECHNUNG
    // ════════════════════════════════════════════════════════════════

    private fun setupPulseAutoCalc() {
        binding.etPulseBeats.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val beats = s?.toString()?.toIntOrNull()
                if (beats != null) {
                    val result = beats * 4
                    binding.etPulseResult.setText(result.toString())
                    updatePulseBadge(result)
                } else {
                    binding.etPulseResult.text?.clear()
                    binding.tvPulseBadge.visibility = View.GONE
                }
            }
        })

        // Auch bei direkter Eingabe des Ergebnisses Badge aktualisieren
        binding.etPulseResult.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val result = s?.toString()?.toIntOrNull()
                if (result != null && binding.etPulseBeats.text.isNullOrEmpty()) {
                    updatePulseBadge(result)
                }
            }
        })
    }

    private fun updatePulseBadge(pulse: Int) {
        val heightCm = profile?.shoulderHeight?.trim()?.toIntOrNull()
        val record = VitalRecord(pulse = pulse)
        val status = record.pulseStatus(heightCm)

        binding.tvPulseBadge.visibility = View.VISIBLE
        when (status) {
            PulseStatus.NORMAL -> {
                binding.tvPulseBadge.text = getString(R.string.pulscheck_badge_normal)
                binding.tvPulseBadge.setTextColor(ContextCompat.getColor(this@PulscheckActivity, R.color.gl_success))
            }
            PulseStatus.LOW -> {
                binding.tvPulseBadge.text = getString(R.string.pulscheck_badge_low)
                binding.tvPulseBadge.setTextColor(ContextCompat.getColor(this@PulscheckActivity, R.color.gl_warning))
            }
            PulseStatus.HIGH -> {
                binding.tvPulseBadge.text = getString(R.string.pulscheck_badge_high)
                binding.tvPulseBadge.setTextColor(ContextCompat.getColor(this@PulscheckActivity, R.color.gl_danger))
            }
            PulseStatus.UNKNOWN -> {
                binding.tvPulseBadge.text = getString(R.string.pulscheck_badge_unknown)
                binding.tvPulseBadge.setTextColor(ContextCompat.getColor(this@PulscheckActivity, R.color.rd_text_secondary))
            }
        }
    }

    private fun setupRespAutoCalc() {
        binding.etRespBeats.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val beats = s?.toString()?.toIntOrNull()
                if (beats != null) {
                    binding.etRespResult.setText((beats * 4).toString())
                } else {
                    binding.etRespResult.text?.clear()
                }
            }
        })
    }

    // ════════════════════════════════════════════════════════════════
    // KFZ-BUTTONS
    // ════════════════════════════════════════════════════════════════

    private fun setupCrtButtons() {
        val buttons = listOf(
            binding.btnCrt1 to 1,
            binding.btnCrt2 to 2,
            binding.btnCrt3 to 3,
            binding.btnCrt4 to 4
        )
        val colors = listOf(R.color.gl_success_fill, R.color.gl_success_fill, R.color.gl_warning_fill, R.color.gl_danger_fill)
        val labels = listOf("1s", "2s ✓", "3s", ">3s")

        buttons.forEachIndexed { index, (btn, value) ->
            btn.setOnClickListener {
                selectedCrt = value
                // Alle zurücksetzen
                buttons.forEachIndexed { i, (b, _) ->
                    b.strokeWidth = 0
                    b.setBackgroundColor(ContextCompat.getColor(this@PulscheckActivity, colors[i]))
                    b.text = labels[i]
                    b.alpha = 0.7f
                }
                // Ausgewählten hervorheben
                btn.alpha = 1.0f
                btn.strokeWidth = dpToPx(3)
                btn.strokeColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@PulscheckActivity, R.color.gl_stroke_strong))

                val hint = when (value) {
                    1 -> getString(R.string.pulscheck_crt_selected_1s)
                    2 -> getString(R.string.pulscheck_crt_selected_2s)
                    3 -> getString(R.string.pulscheck_crt_selected_3s)
                    else -> getString(R.string.pulscheck_crt_selected_over_3s)
                }
                binding.tvCrtSelected.text = hint
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SPEICHERN & AKTIONEN
    // ════════════════════════════════════════════════════════════════

    private fun setupActionButtons() {
        updateActionGrids()

        // History Grid
        val colorsHistory = StartGroupColors.forGroup(this, 5) // Violett
        binding.gridPulsHistory.layoutManager = GridLayoutManager(this, 1)
        binding.gridPulsHistory.adapter = StartTileAdapter(
            tiles = listOf(StartTile("puls_history", R.drawable.ic_notes, getString(R.string.pulscheck_tile_show_all_measurements))),
            chipColorRes = colorsHistory.chipColorRes,
            accentColorRes = colorsHistory.accentColorRes,
            onTileClick = { viewHistory() }
        )
    }

    private fun updateActionGrids() {
        val actions = mutableListOf<StartTile>()
        actions.add(StartTile("puls_save", R.drawable.ic_notes, getString(R.string.pulscheck_tile_save_measurement)))

        if (savedRecord != null) {
            actions.add(StartTile("puls_emergency", R.drawable.ic_emergency, getString(R.string.pulscheck_tile_emergency_report), isDanger = true))
        }

        val colorsActions = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridPulsActions.layoutManager = GridLayoutManager(this, actions.size)
        binding.gridPulsActions.adapter = StartTileAdapter(
            tiles = actions,
            chipColorRes = colorsActions.chipColorRes,
            accentColorRes = colorsActions.accentColorRes,
            onTileClick = { id ->
                when(id) {
                    "puls_save" -> saveRecord()
                    "puls_emergency" -> transferToEmergency()
                }
            }
        )
    }

    private fun viewHistory() {
        val intent = Intent(this, VitalListActivity::class.java)
        intent.putExtra("dogId", profile!!.id)
        intent.putExtra("dogName", profile!!.name)
        startActivity(intent)
    }

    private fun collectRecord(): VitalRecord? {
        val pulse = binding.etPulseResult.text?.toString()?.toIntOrNull()
            ?: binding.etPulseBeats.text?.toString()?.toIntOrNull()?.let { it * 4 }

        if (pulse == null || pulse <= 0) {
            Toast.makeText(this, getString(R.string.pulscheck_toast_pulse_required), Toast.LENGTH_SHORT).show()
            return null
        }

        val respiration = binding.etRespResult.text?.toString()?.toIntOrNull()
            ?: binding.etRespBeats.text?.toString()?.toIntOrNull()?.let { it * 4 }

        val temperature = binding.etTemperature.text?.toString()
            ?.replace(",", ".")?.toDoubleOrNull()

        val loadScenarioIndex = binding.spinnerLoadScenario.selectedItemPosition
        val loadScenarioOptions = VitalRecord.loadScenarios(this)
        val loadScenario = if (loadScenarioIndex > 0)
            loadScenarioOptions[loadScenarioIndex - 1] else VitalRecord.loadScenarioRestKey()

        val mucousColorIndex = binding.spinnerMucousColor.selectedItemPosition
        val mucousColorOptions = VitalRecord.mucousMembraneColors(this)
        val mucousColor = if (mucousColorIndex > 0)
            mucousColorOptions[mucousColorIndex - 1] else null

        val consciousnessIndex = binding.spinnerConsciousness.selectedItemPosition
        val consciousnessOptions = VitalRecord.consciousnessLevels(this)
        val consciousness = if (consciousnessIndex > 0)
            consciousnessOptions[consciousnessIndex - 1] else null

        return VitalRecord(
            dogId = profile!!.id,
            dogName = profile!!.name,
            pulse = pulse,
            respiration = respiration,
            temperature = temperature,
            capillaryRefillTime = selectedCrt,
            mucousMembraneColor = mucousColor,
            consciousnessLevel = consciousness,
            loadScenario = loadScenario,
            notes = binding.etNotes.text?.toString()?.trim() ?: ""
        )
    }

    private fun saveRecord() {
        val record = collectRecord() ?: return

        VitalManager.save(this, record)
        savedRecord = record

        Toast.makeText(this, getString(R.string.pulscheck_toast_saved), Toast.LENGTH_SHORT).show()

        // Grids aktualisieren (um Notfall-Button zu zeigen)
        updateActionGrids()

        // Erfolgsmeldung mit Bewertung
        val heightCm = profile?.shoulderHeight?.trim()?.toIntOrNull()
        val status = record.pulseStatus(heightCm)
        if (status == PulseStatus.HIGH || status == PulseStatus.LOW) {
            showAbnormalWarning(record, status)
        }
    }

    private fun showAbnormalWarning(record: VitalRecord, status: PulseStatus) {

        val msg = when (status) {
            PulseStatus.HIGH ->
                getString(R.string.pulscheck_dialog_msg_high, record.pulse)
            PulseStatus.LOW ->
                getString(R.string.pulscheck_dialog_msg_low, record.pulse)
            else -> return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.pulscheck_dialog_title_abnormal))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.pulscheck_dialog_btn_understood), null)
            .setNeutralButton(getString(R.string.pulscheck_dialog_btn_emergency_report)) { _, _ -> transferToEmergency() }
            .create()
            .apply {
                show()
            }
    }

    private fun transferToEmergency() {
        val record = savedRecord ?: collectRecord()

        if (record == null) {
            Toast.makeText(this, getString(R.string.pulscheck_toast_save_first), Toast.LENGTH_SHORT).show()
            return
        }

        // Notiz mit Vitalwerten vorbauen
        val vitalText = buildString {
            appendLine(getString(R.string.pulscheck_emergency_header))
            appendLine(getString(R.string.pulscheck_emergency_pulse_line, record.pulse))
            record.respiration?.let { appendLine(getString(R.string.pulscheck_emergency_respiration_line, it)) }
            record.temperature?.let { appendLine(getString(R.string.pulscheck_emergency_temperature_line, "%.1f".format(it))) }
            record.capillaryRefillTime?.let { appendLine(getString(R.string.pulscheck_emergency_crt_line, it)) }
            record.mucousMembraneColor?.let { appendLine(getString(R.string.pulscheck_emergency_mucous_color_line, it)) }
            record.consciousnessLevel?.let { appendLine(getString(R.string.pulscheck_emergency_consciousness_line, it)) }
            appendLine(getString(R.string.pulscheck_emergency_load_line, record.loadScenario))
            if (record.notes.isNotBlank()) {
                appendLine()
                appendLine(getString(R.string.pulscheck_emergency_notes_line, record.notes))
            }
        }

        val intent = Intent(this, EmergencyActivity::class.java)
        intent.putExtra("profile", profile)
        intent.putExtra("prefill_notes", vitalText.trim())
        startActivity(intent)
    }

    // ════════════════════════════════════════════════════════════════
    // UTILS
    // ════════════════════════════════════════════════════════════════

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        pulseTimer?.cancel()
        respTimer?.cancel()
    }
}
