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
            Toast.makeText(this, "Messung nicht gefunden", Toast.LENGTH_SHORT).show()
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
        val mucousItems = listOf("– nicht gemessen –") + VitalRecord.MUCOUS_MEMBRANE_COLORS
        binding.spinnerDetailMucous.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, mucousItems
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val consciousnessItems = listOf("– nicht beurteilt –") + VitalRecord.CONSCIOUSNESS_LEVELS
        binding.spinnerDetailConsciousness.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, consciousnessItems
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val loadItems = listOf("– Bitte wählen –") + VitalRecord.LOAD_SCENARIOS
        binding.spinnerDetailLoad.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, loadItems
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun fillFields(r: VitalRecord) {
        // Meta
        binding.tvDetailDog.text = "🐕 ${r.dogName}"
        binding.tvDetailDate.text = "📅 ${r.formattedDate()}"
        binding.tvDetailScenario.text = "⚡ ${r.loadScenario}"

        // Vitalwerte
        binding.etDetailPulse.setText(r.pulse.toString())
        r.respiration?.let { binding.etDetailResp.setText(it.toString()) }
        r.temperature?.let { binding.etDetailTemp.setText("%.1f".format(it).replace(",", ".")) }
        r.capillaryRefillTime?.let { binding.etDetailCrt.setText(it.toString()) }

        // Schleimhautfarbe
        val mucousIdx = r.mucousMembraneColor?.let {
            VitalRecord.MUCOUS_MEMBRANE_COLORS.indexOf(it) + 1
        } ?: 0
        binding.spinnerDetailMucous.setSelection(mucousIdx)

        // Bewusstsein
        val consciousnessIdx = r.consciousnessLevel?.let {
            VitalRecord.CONSCIOUSNESS_LEVELS.indexOf(it) + 1
        } ?: 0
        binding.spinnerDetailConsciousness.setSelection(consciousnessIdx)

        // Belastung
        val loadIdx = VitalRecord.LOAD_SCENARIOS.indexOf(r.loadScenario)
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
            Toast.makeText(this, "Bitte eine gültige Herzfrequenz eingeben.", Toast.LENGTH_SHORT).show()
            return null
        }

        val respiration = binding.etDetailResp.text?.toString()?.toIntOrNull()
        val temperature = binding.etDetailTemp.text?.toString()
            ?.replace(",", ".")?.toDoubleOrNull()
        val crt = binding.etDetailCrt.text?.toString()?.toIntOrNull()

        val mucousIdx = binding.spinnerDetailMucous.selectedItemPosition
        val mucousColor = if (mucousIdx > 0) VitalRecord.MUCOUS_MEMBRANE_COLORS[mucousIdx - 1] else null

        val consciousnessIdx = binding.spinnerDetailConsciousness.selectedItemPosition
        val consciousness = if (consciousnessIdx > 0) VitalRecord.CONSCIOUSNESS_LEVELS[consciousnessIdx - 1] else null

        val loadIdx = binding.spinnerDetailLoad.selectedItemPosition
        val loadScenario = if (loadIdx > 0) VitalRecord.LOAD_SCENARIOS[loadIdx - 1] else "Ruhe"

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
        binding.tvDetailScenario.text = "⚡ ${updated.loadScenario}"

        Toast.makeText(this, "✅ Änderungen gespeichert", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
    }

    private fun shareRecord() {
        val r = record ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Vitalwerte ${r.dogName} – ${r.formattedDate()}")
            putExtra(Intent.EXTRA_TEXT, r.toShareText())
        }
        startActivity(Intent.createChooser(intent, "Messung teilen"))
    }

    private fun shareAsText() {
        val r = record ?: return
        // Ausführlichere Textversion
        val text = buildString {
            appendLine("🐕 Vitalwerte – ${r.dogName}")
            appendLine("📅 Datum: ${r.formattedDate()}")
            appendLine("⚡ Belastung: ${r.loadScenario}")
            appendLine()
            appendLine("═══════════════════")
            appendLine("❤️  Herzfrequenz: ${r.pulse} /min")
            r.respiration?.let { appendLine("🫁  Atemfrequenz: $it /min") }
            r.temperature?.let { appendLine("🌡️  Temperatur: ${"%.1f".format(it)} °C") }
            r.capillaryRefillTime?.let { appendLine("💉  Kapillarfüllzeit: ${it}s") }
            r.mucousMembraneColor?.let { appendLine("👁️  Schleimhautfarbe: $it") }
            r.consciousnessLevel?.let { appendLine("🧠  Bewusstsein: $it") }
            if (r.notes.isNotBlank()) {
                appendLine()
                appendLine("📝 Notizen:")
                appendLine(r.notes)
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Vitalwerte ${r.dogName}")
            putExtra(Intent.EXTRA_TEXT, text.trim())
        }
        startActivity(Intent.createChooser(intent, "Als Text teilen"))
    }
}
