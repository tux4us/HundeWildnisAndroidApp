package com.hundewildnis.app

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class HeatCycleActivity : SecureBaseActivity() {

    private lateinit var dogId: String
    private var existingEntry: HeatCycleEntry? = null
    private var newCycleNumber: Int = 1

    // Views
    private lateinit var tvCycleNumber: TextView
    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView
    private lateinit var rgIntensity: RadioGroup
    private lateinit var rbLeicht: RadioButton
    private lateinit var rbMittel: RadioButton
    private lateinit var rbStark: RadioButton
    private lateinit var etSymptoms: EditText
    private lateinit var llBehaviorSigns: LinearLayout
    private lateinit var etBehaviorNotes: EditText
    private lateinit var switchPseudo: Switch
    private lateinit var llPseudoSection: LinearLayout
    private lateinit var llPseudoSigns: LinearLayout
    private lateinit var etPseudoNotes: EditText
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button

    private val behaviorChecks  = LinkedHashMap<String, CheckBox>()
    private val pseudoChecks    = LinkedHashMap<String, CheckBox>()

    // ═══════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heat_cycle)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        dogId = intent.getStringExtra(EXTRA_DOG_ID) ?: return finish()
        newCycleNumber = intent.getIntExtra(EXTRA_CYCLE_NUMBER, 1)
        existingEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ENTRY, HeatCycleEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ENTRY)
        }

        supportActionBar?.apply {
            title = if (existingEntry == null) "Läufigkeit eintragen" else "Läufigkeit bearbeiten"
            setDisplayHomeAsUpEnabled(true)
        }

        bindViews()
        buildBehaviorCheckboxes()
        buildPseudoCheckboxes()
        existingEntry?.let { populateFrom(it) } ?: initDefaults()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ═══════════════════════════════════════════════════════════
    // VIEW SETUP
    // ═══════════════════════════════════════════════════════════

    private fun bindViews() {
        tvCycleNumber   = findViewById(R.id.tvCycleNumber)
        tvStartDate     = findViewById(R.id.tvStartDate)
        tvEndDate       = findViewById(R.id.tvEndDate)
        rgIntensity     = findViewById(R.id.rgIntensity)
        rbLeicht        = findViewById(R.id.rbLeicht)
        rbMittel        = findViewById(R.id.rbMittel)
        rbStark         = findViewById(R.id.rbStark)
        etSymptoms      = findViewById(R.id.etSymptoms)
        llBehaviorSigns = findViewById(R.id.llBehaviorSigns)
        etBehaviorNotes = findViewById(R.id.etBehaviorNotes)
        switchPseudo    = findViewById(R.id.switchPseudoPregnancy)
        llPseudoSection = findViewById(R.id.llPseudoSection)
        llPseudoSigns   = findViewById(R.id.llPseudoSigns)
        etPseudoNotes   = findViewById(R.id.etPseudoNotes)
        btnSave         = findViewById(R.id.btnSaveHeatCycle)
        btnDelete       = findViewById(R.id.btnDeleteHeatCycle)

        tvStartDate.setOnClickListener { showDatePicker { tvStartDate.text = it } }
        tvEndDate.setOnClickListener   { showDatePicker { tvEndDate.text = it } }

        switchPseudo.setOnCheckedChangeListener { _, checked ->
            llPseudoSection.visibility = if (checked) View.VISIBLE else View.GONE
        }

        btnSave.setOnClickListener   { saveEntry() }
        btnDelete.visibility = if (existingEntry != null) View.VISIBLE else View.GONE
        btnDelete.setOnClickListener { deleteEntry() }
    }

    private fun buildBehaviorCheckboxes() {
        HeatCycleEntry.BEHAVIOR_OPTIONS.forEach { option ->
            val cb = CheckBox(this).apply { text = option; textSize = 14f }
            behaviorChecks[option] = cb
            llBehaviorSigns.addView(cb)
        }
    }

    private fun buildPseudoCheckboxes() {
        HeatCycleEntry.PSEUDO_OPTIONS.forEach { option ->
            val cb = CheckBox(this).apply { text = option; textSize = 14f }
            pseudoChecks[option] = cb
            llPseudoSigns.addView(cb)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DATEN BEFÜLLEN
    // ═══════════════════════════════════════════════════════════

    private fun initDefaults() {
        tvCycleNumber.text = "Läufigkeit #$newCycleNumber"
        tvStartDate.text   = getString(R.string.tap_to_select)
        tvEndDate.text     = getString(R.string.tap_to_select_optional)
        rbMittel.isChecked = true
        llPseudoSection.visibility = View.GONE
    }

    private fun populateFrom(entry: HeatCycleEntry) {
        tvCycleNumber.text = "Läufigkeit #${entry.cycleNumber}"
        tvStartDate.text   = entry.startDate.ifEmpty { getString(R.string.tap_to_select) }
        tvEndDate.text     = entry.endDate.ifEmpty { getString(R.string.tap_to_select_optional) }
        etSymptoms.setText(entry.symptoms)
        etBehaviorNotes.setText(entry.behaviorNotes)

        when (entry.intensity) {
            "Leicht" -> rbLeicht.isChecked = true
            "Stark"  -> rbStark.isChecked  = true
            else     -> rbMittel.isChecked = true
        }

        entry.behaviorSigns.forEach         { behaviorChecks[it]?.isChecked = true }
        switchPseudo.isChecked              = entry.hasPseudoPregnancy
        llPseudoSection.visibility          = if (entry.hasPseudoPregnancy) View.VISIBLE else View.GONE
        entry.pseudoPregnancySigns.forEach  { pseudoChecks[it]?.isChecked = true }
        etPseudoNotes.setText(entry.pseudoPregnancyNotes)
    }

    // ═══════════════════════════════════════════════════════════
    // DATEPICKER
    // ═══════════════════════════════════════════════════════════

    private fun showDatePicker(onSelected: (String) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d -> onSelected(String.format("%02d.%02d.%04d", d, m + 1, y)) },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ═══════════════════════════════════════════════════════════
    // SPEICHERN
    // ═══════════════════════════════════════════════════════════

    private fun saveEntry() {
        val startText = tvStartDate.text.toString()
        if (startText == getString(R.string.tap_to_select) || startText.isBlank()) {
            Toast.makeText(this, "Bitte Startdatum auswählen", Toast.LENGTH_SHORT).show()
            return
        }

        val endText = tvEndDate.text.toString().let {
            if (it == getString(R.string.tap_to_select_optional)) "" else it
        }

        val intensity = when {
            rbLeicht.isChecked -> "Leicht"
            rbStark.isChecked  -> "Stark"
            else               -> "Mittel"
        }

        val entry = (existingEntry ?: HeatCycleEntry(
            cycleNumber = newCycleNumber
        )).copy(
            startDate             = startText,
            endDate               = endText,
            intensity             = intensity,
            symptoms              = etSymptoms.text.toString().trim(),
            behaviorSigns         = behaviorChecks.filter { it.value.isChecked }.keys.toList(),
            behaviorNotes         = etBehaviorNotes.text.toString().trim(),
            hasPseudoPregnancy    = switchPseudo.isChecked,
            pseudoPregnancySigns  = pseudoChecks.filter { it.value.isChecked }.keys.toList(),
            pseudoPregnancyNotes  = etPseudoNotes.text.toString().trim()
        )

        persistEntry(entry)
    }

    // ═══════════════════════════════════════════════════════════
    // LÖSCHEN
    // ═══════════════════════════════════════════════════════════

    private fun deleteEntry() {
        val entry = existingEntry ?: return
        val profile = DogProfileManager.getProfile(this, dogId) ?: return
        val updatedCycles = profile.heatCycles.filter { it.id != entry.id }
        val updatedProfile = profile.copy(
            heatCycles   = updatedCycles,
            lastModified = System.currentTimeMillis()
        )
        DogProfileManager.saveProfile(this, updatedProfile)
        HeatCycleManager.scheduleReminder(this, dogId, profile.name, updatedCycles)

        setResult(RESULT_OK)
        finish()
    }

    // ═══════════════════════════════════════════════════════════
    // PERSISTENZ
    // ═══════════════════════════════════════════════════════════

    private fun persistEntry(entry: HeatCycleEntry) {
        val profile = DogProfileManager.getProfile(this, dogId) ?: run {
            Toast.makeText(this, "Profil nicht gefunden", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedCycles = profile.heatCycles.toMutableList()
        val idx = updatedCycles.indexOfFirst { it.id == entry.id }
        if (idx >= 0) updatedCycles[idx] = entry else updatedCycles.add(entry)

        // Chronologisch sortiert (neueste zuerst)
        val sorted = updatedCycles.sortedByDescending { it.startDate }

        val updatedProfile = profile.copy(
            heatCycles   = sorted,
            lastModified = System.currentTimeMillis()
        )
        DogProfileManager.saveProfile(this, updatedProfile)
        HeatCycleManager.scheduleReminder(this, dogId, profile.name, sorted)

        setResult(RESULT_OK)
        finish()
    }

    // ═══════════════════════════════════════════════════════════
    // COMPANION
    // ═══════════════════════════════════════════════════════════

    companion object {
        const val EXTRA_DOG_ID       = "dog_id"
        const val EXTRA_CYCLE_NUMBER = "cycle_number"
        const val EXTRA_ENTRY        = "entry"

        /** Neue Läufigkeit anlegen */
        fun startForNew(
            context: Context,
            dogId: String,
            cycleNumber: Int,
            launcher: ActivityResultLauncher<Intent>
        ) {
            launcher.launch(Intent(context, HeatCycleActivity::class.java).apply {
                putExtra(EXTRA_DOG_ID, dogId)
                putExtra(EXTRA_CYCLE_NUMBER, cycleNumber)
            })
        }

        /** Bestehende Läufigkeit bearbeiten */
        fun startForEdit(
            context: Context,
            dogId: String,
            entry: HeatCycleEntry,
            launcher: ActivityResultLauncher<Intent>
        ) {
            launcher.launch(Intent(context, HeatCycleActivity::class.java).apply {
                putExtra(EXTRA_DOG_ID, dogId)
                putExtra(EXTRA_ENTRY, entry)
            })
        }
    }
}
