package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.hundewildnis.app.databinding.ActivityVitalDetailBinding

class VitalDetailActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityVitalDetailBinding

    private var record: VitalRecord? = null
    private var dogId: String = ""
    private var dogName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVitalDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recordId = intent.getStringExtra("recordId") ?: ""
        dogId = intent.getStringExtra("dogId") ?: ""
        dogName = intent.getStringExtra("dogName") ?: ""

        record = VitalManager.getById(this, recordId)

        if (record == null) {
            Toast.makeText(this, getString(R.string.vital_detail_toast_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupSpinners()
        fillFields(record!!)
        setupButtons()
    }

    // ════════════════════════════════════════════════════════════════
    // SETUP
    // ════════════════════════════════════════════════════════════════

    private fun setupSpinners() {
        val mucousItems = listOf(getString(R.string.pulscheck_spinner_not_measured)) + VitalRecord.mucousMembraneColors(this)
        binding.spinnerDetailMucous.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, mucousItems
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val consciousnessItems = listOf(getString(R.string.pulscheck_spinner_not_assessed)) + VitalRecord.consciousnessLevels(this)
        binding.spinnerDetailConsciousness.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, consciousnessItems
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val loadItems = listOf(getString(R.string.pulscheck_spinner_please_choose)) + VitalRecord.loadScenarios(this)
        binding.spinnerDetailLoad.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, loadItems
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun fillFields(r: VitalRecord) {
        // Meta
        binding.tvDetailDog.text = getString(R.string.vital_detail_dog_format, r.dogName)
        binding.tvDetailDate.text = getString(R.string.vital_detail_date_format, r.formattedDate())
        binding.tvDetailScenario.text = getString(
            R.string.vital_detail_scenario_format,
            LoadScenarioCode.displayName(this, r.loadScenario)
        )

        // Vitalwerte
        binding.etDetailPulse.setText(r.pulse.toString())
        r.respiration?.let { binding.etDetailResp.setText(it.toString()) }
        r.temperature?.let { binding.etDetailTemp.setText("%.1f".format(it).replace(",", ".")) }
        r.capillaryRefillTime?.let { binding.etDetailCrt.setText(it.toString()) }

        // Schleimhautfarbe
        val mucousOptions = MucousColorCode.ALL
        val mucousIdx = r.mucousMembraneColor?.let {
            mucousOptions.indexOf(it) + 1
        } ?: 0
        binding.spinnerDetailMucous.setSelection(mucousIdx)

        // Bewusstsein
        val consciousnessOptions = ConsciousnessCode.ALL
        val consciousnessIdx = r.consciousnessLevel?.let {
            consciousnessOptions.indexOf(it) + 1
        } ?: 0
        binding.spinnerDetailConsciousness.setSelection(consciousnessIdx)

        // Belastung
        val loadOptions = LoadScenarioCode.ALL
        val loadIdx = loadOptions.indexOf(r.loadScenario)
        binding.spinnerDetailLoad.setSelection(if (loadIdx >= 0) loadIdx + 1 else 1)

        // Notizen
        binding.etDetailNotes.setText(r.notes)
    }

    // ════════════════════════════════════════════════════════════════
    // AKTIONEN
    // ════════════════════════════════════════════════════════════════

    private fun setupButtons() {
        binding.btnDetailSave.setOnClickListener { saveChanges() }
        binding.btnDetailShare.setOnClickListener { shareRecord() }
        binding.btnDetailSharePdf.setOnClickListener { shareAsText() }
    }

    private fun collectUpdated(): VitalRecord? {
        val pulse = binding.etDetailPulse.text?.toString()?.toIntOrNull()
        if (pulse == null || pulse <= 0) {
            Toast.makeText(this, getString(R.string.vital_detail_toast_invalid_pulse), Toast.LENGTH_SHORT).show()
            return null
        }

        val respiration = binding.etDetailResp.text?.toString()?.toIntOrNull()
        val temperature = binding.etDetailTemp.text?.toString()
            ?.replace(",", ".")?.toDoubleOrNull()
        val crt = binding.etDetailCrt.text?.toString()?.toIntOrNull()

        val mucousOptions = MucousColorCode.ALL
        val mucousIdx = binding.spinnerDetailMucous.selectedItemPosition
        val mucousColor = if (mucousIdx > 0) mucousOptions[mucousIdx - 1] else null

        val consciousnessOptions = ConsciousnessCode.ALL
        val consciousnessIdx = binding.spinnerDetailConsciousness.selectedItemPosition
        val consciousness = if (consciousnessIdx > 0) consciousnessOptions[consciousnessIdx - 1] else null

        val loadOptions = LoadScenarioCode.ALL
        val loadIdx = binding.spinnerDetailLoad.selectedItemPosition
        val loadScenario = if (loadIdx > 0) loadOptions[loadIdx - 1] else LoadScenarioCode.REST

        return record!!.copy(
            pulse = pulse,
            respiration = respiration,
            temperature = temperature,
            capillaryRefillTime = crt,
            mucousMembraneColor = mucousColor,
            consciousnessLevel = consciousness,
            loadScenario = loadScenario,
            notes = binding.etDetailNotes.text?.toString()?.trim() ?: ""
        )
    }

    private fun saveChanges() {
        val updated = collectUpdated() ?: return
        VitalManager.update(this, updated)
        record = updated

        // Meta-Anzeige aktualisieren
        binding.tvDetailScenario.text = getString(
            R.string.vital_detail_scenario_format,
            LoadScenarioCode.displayName(this, updated.loadScenario)
        )

        Toast.makeText(this, getString(R.string.vital_detail_toast_saved), Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
    }

    private fun shareRecord() {
        val r = record ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.vital_detail_share_subject_format, r.dogName, r.formattedDate()))
            putExtra(Intent.EXTRA_TEXT, r.toShareText(this@VitalDetailActivity))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.vital_detail_share_chooser_title)))
    }

    private fun shareAsText() {
        val r = record ?: return
        // Ausführlichere Textversion
        val text = buildString {
            appendLine(getString(R.string.vital_detail_text_dog_line, r.dogName))
            appendLine(getString(R.string.vital_detail_text_date_line, r.formattedDate()))
            appendLine(getString(R.string.vital_detail_text_load_line, LoadScenarioCode.displayName(this@VitalDetailActivity, r.loadScenario)))
            appendLine()
            appendLine("═══════════════════")
            appendLine(getString(R.string.vital_detail_text_pulse_line, r.pulse))
            r.respiration?.let { appendLine(getString(R.string.vital_detail_text_respiration_line, it)) }
            r.temperature?.let { appendLine(getString(R.string.vital_detail_text_temperature_line, "%.1f".format(it))) }
            r.capillaryRefillTime?.let { appendLine(getString(R.string.vital_detail_text_crt_line, it)) }
            r.mucousMembraneColor?.let { appendLine(getString(R.string.vital_detail_text_mucous_line, MucousColorCode.displayName(this@VitalDetailActivity, it))) }
            r.consciousnessLevel?.let { appendLine(getString(R.string.vital_detail_text_consciousness_line, ConsciousnessCode.displayName(this@VitalDetailActivity, it))) }
            if (r.notes.isNotBlank()) {
                appendLine()
                appendLine(getString(R.string.vital_detail_text_notes_header))
                appendLine(r.notes)
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.vital_detail_text_share_subject_format, r.dogName))
            putExtra(Intent.EXTRA_TEXT, text.trim())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.vital_detail_share_as_text_chooser_title)))
    }
}
