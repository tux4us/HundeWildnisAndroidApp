package com.hundewildnis.app

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import coil.load
import java.util.Calendar

/**
 * Erfassen / Bearbeiten einer Gesundheitsbeobachtung.
 * Struktur analog zu HeatCycleActivity (Start über Launcher, RESULT_OK nach Speichern/Löschen).
 */
class HealthObservationActivity : SecureBaseActivity() {

    private lateinit var dogId: String
    private var existingEntry: HealthObservationEntry? = null

    private lateinit var tvStart: TextView
    private lateinit var tvEnd: TextView
    private lateinit var rgStatus: RadioGroup
    private lateinit var spCategory: Spinner
    private lateinit var llSymptoms: LinearLayout
    private lateinit var rgSeverity: RadioGroup
    private lateinit var etNotes: EditText
    private lateinit var switchVet: Switch
    private lateinit var llVet: LinearLayout
    private lateinit var etVetNotes: EditText
    private lateinit var btnSave: Button
    private lateinit var btnDelete: Button
    private lateinit var llPhotos: LinearLayout
    private lateinit var btnAddPhoto: Button

    /** Fotos des Eintrags beim Öffnen (bleiben bis zum Speichern unverändert auf dem Datenträger). */
    private var originalPhotos: List<String> = emptyList()
    private val currentPhotos = mutableListOf<String>()
    private var saved = false

    private val pickPhotos = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        var limitReached = false
        var failed = false
        for (uri in uris) {
            if (currentPhotos.size >= HealthPhotos.MAX_PER_ENTRY) {
                limitReached = true
                break
            }
            val copied = HealthPhotos.copyIn(this, uri)
            if (copied == null) failed = true else currentPhotos.add(copied)
        }
        renderPhotos()
        if (limitReached) {
            Toast.makeText(
                this, getString(R.string.health_form_photo_limit, HealthPhotos.MAX_PER_ENTRY), Toast.LENGTH_SHORT
            ).show()
        } else if (failed) {
            Toast.makeText(this, getString(R.string.health_form_photo_error), Toast.LENGTH_SHORT).show()
        }
    }

    private val categories = HealthCategory.values().toList()

    /** Ausgewählte Symptom-Codes über Kategoriewechsel hinweg. */
    private val selectedSymptoms = LinkedHashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_observation)

        setSupportActionBar(findViewById(R.id.toolbar))

        dogId = intent.getStringExtra(EXTRA_DOG_ID) ?: return finish()
        existingEntry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_ENTRY, HealthObservationEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_ENTRY)
        }

        supportActionBar?.apply {
            title = getString(
                if (existingEntry == null) R.string.health_form_title_new else R.string.health_form_title_edit
            )
            setDisplayHomeAsUpEnabled(true)
        }

        bindViews()
        buildStatusRadios()
        buildSeverityRadios()
        setupCategorySpinner()

        val entry = existingEntry
        if (entry != null) populateFrom(entry) else initDefaults()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Beim Abbrechen keine verwaisten, neu hinzugefügten Fotos zurücklassen
        if (!saved) discardUnsavedPhotos()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ═══════════════════════════════════════════════════════════
    // VIEW SETUP
    // ═══════════════════════════════════════════════════════════

    private fun bindViews() {
        tvStart = findViewById(R.id.tvHealthStart)
        tvEnd = findViewById(R.id.tvHealthEnd)
        rgStatus = findViewById(R.id.rgHealthStatus)
        spCategory = findViewById(R.id.spHealthCategory)
        llSymptoms = findViewById(R.id.llHealthSymptoms)
        rgSeverity = findViewById(R.id.rgHealthSeverity)
        etNotes = findViewById(R.id.etHealthNotes)
        switchVet = findViewById(R.id.switchHealthVet)
        llVet = findViewById(R.id.llHealthVet)
        etVetNotes = findViewById(R.id.etHealthVetNotes)
        btnSave = findViewById(R.id.btnSaveHealth)
        btnDelete = findViewById(R.id.btnDeleteHealth)
        llPhotos = findViewById(R.id.llHealthPhotos)
        btnAddPhoto = findViewById(R.id.btnAddHealthPhoto)

        btnAddPhoto.setOnClickListener {
            if (currentPhotos.size >= HealthPhotos.MAX_PER_ENTRY) {
                Toast.makeText(
                    this, getString(R.string.health_form_photo_limit, HealthPhotos.MAX_PER_ENTRY), Toast.LENGTH_SHORT
                ).show()
            } else {
                pickPhotos.launch(arrayOf("image/*"))
            }
        }

        tvStart.setOnClickListener { showDatePicker { tvStart.text = it } }
        tvEnd.setOnClickListener { showDatePicker { tvEnd.text = it } }

        switchVet.setOnCheckedChangeListener { _, checked ->
            llVet.visibility = if (checked) View.VISIBLE else View.GONE
        }

        btnSave.setOnClickListener { saveEntry() }
        btnDelete.visibility = if (existingEntry != null) View.VISIBLE else View.GONE
        btnDelete.setOnClickListener { confirmDelete() }
    }

    private fun buildStatusRadios() {
        HealthStatus.values().forEach { status ->
            rgStatus.addView(RadioButton(this).apply {
                id = View.generateViewId()
                tag = status.code
                text = getString(status.labelRes)
            })
        }
    }

    private fun buildSeverityRadios() {
        HealthSeverity.values().forEach { severity ->
            rgSeverity.addView(RadioButton(this).apply {
                id = View.generateViewId()
                tag = severity.code
                text = getString(severity.labelRes)
                layoutParams = RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun setupCategorySpinner() {
        val labels = categories.map { "${it.emoji} ${getString(it.labelRes)}" }
        spCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                rebuildSymptomChecks(categories[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** Zeigt die Symptome der gewählten Kategorie; bereits gewählte bleiben erhalten. */
    private fun rebuildSymptomChecks(category: HealthCategory) {
        llSymptoms.removeAllViews()
        val symptoms = category.symptoms()
        if (symptoms.isEmpty()) {
            llSymptoms.addView(TextView(this).apply {
                text = getString(R.string.health_form_symptoms_none)
                textSize = 13f
                alpha = 0.7f
            })
            return
        }
        symptoms.forEach { symptom ->
            llSymptoms.addView(CheckBox(this).apply {
                text = getString(symptom.labelRes)
                textSize = 14f
                isChecked = symptom.code in selectedSymptoms
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedSymptoms.add(symptom.code) else selectedSymptoms.remove(symptom.code)
                }
            })
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DATEN BEFÜLLEN
    // ═══════════════════════════════════════════════════════════

    private fun initDefaults() {
        tvStart.text = HealthDates.today()
        tvEnd.text = getString(R.string.tap_to_select_optional)
        checkByTag(rgStatus, HealthStatus.ACTIVE.code)
        checkByTag(rgSeverity, HealthSeverity.MODERATE.code)
        spCategory.setSelection(0)
        renderPhotos()
    }

    private fun populateFrom(entry: HealthObservationEntry) {
        tvStart.text = entry.startDate.ifEmpty { getString(R.string.tap_to_select) }
        tvEnd.text = entry.endDate.ifEmpty { getString(R.string.tap_to_select_optional) }
        checkByTag(rgStatus, HealthStatus.fromCode(entry.status).code)
        checkByTag(rgSeverity, HealthSeverity.fromCode(entry.severity).code)

        selectedSymptoms.clear()
        selectedSymptoms.addAll(entry.symptoms)
        // Setzt das Spinner-Item und löst rebuildSymptomChecks über den Listener aus
        spCategory.setSelection(categories.indexOf(HealthCategory.fromCode(entry.category)).coerceAtLeast(0))

        etNotes.setText(entry.notes)
        switchVet.isChecked = entry.vetVisited
        llVet.visibility = if (entry.vetVisited) View.VISIBLE else View.GONE
        etVetNotes.setText(entry.vetNotes)

        originalPhotos = entry.photoUris
        currentPhotos.clear()
        currentPhotos.addAll(entry.photoUris)
        renderPhotos()
    }

    // ═══════════════════════════════════════════════════════════
    // FOTOS
    // ═══════════════════════════════════════════════════════════

    private fun renderPhotos() {
        llPhotos.removeAllViews()
        val size = (96 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()
        currentPhotos.toList().forEach { uriString ->
            llPhotos.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = getString(R.string.health_form_photo_remove_title)
                load(Uri.parse(uriString))
                setOnClickListener { confirmRemovePhoto(uriString) }
            })
        }
        findViewById<View>(R.id.tvHealthPhotosHint).visibility =
            if (currentPhotos.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmRemovePhoto(uriString: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.health_form_photo_remove_title)
            .setMessage(R.string.health_form_photo_remove_message)
            .setPositiveButton(R.string.health_form_photo_remove) { _, _ ->
                currentPhotos.remove(uriString)
                // Neu hinzugefügte Fotos sofort löschen; bereits gespeicherte erst beim Speichern
                if (uriString !in originalPhotos) HealthPhotos.delete(this, uriString)
                renderPhotos()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun discardUnsavedPhotos() {
        HealthPhotos.deleteAll(this, currentPhotos.filter { it !in originalPhotos })
    }

    private fun checkByTag(group: RadioGroup, code: String) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child.tag == code) {
                group.check(child.id)
                return
            }
        }
    }

    private fun checkedTag(group: RadioGroup): String? =
        group.findViewById<View>(group.checkedRadioButtonId)?.tag as? String

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
    // SPEICHERN / LÖSCHEN
    // ═══════════════════════════════════════════════════════════

    private fun saveEntry() {
        val startText = tvStart.text.toString()
        val start = HealthDates.parse(startText)
        if (start == null) {
            Toast.makeText(this, getString(R.string.health_form_toast_start_required), Toast.LENGTH_SHORT).show()
            return
        }

        var endText = tvEnd.text.toString().let {
            if (HealthDates.parse(it) == null) "" else it
        }
        val status = checkedTag(rgStatus) ?: HealthStatus.ACTIVE.code

        // Abgeklungen ohne Enddatum → heutiges Datum übernehmen
        if (status == HealthStatus.RESOLVED.code && endText.isEmpty()) {
            endText = HealthDates.today()
        }
        // Aktiv/bessert sich mit Enddatum ist widersprüchlich → Enddatum verwerfen
        if (status != HealthStatus.RESOLVED.code) {
            endText = ""
        }
        if (endText.isNotEmpty()) {
            val end = HealthDates.parse(endText)
            if (end != null && end.before(start)) {
                Toast.makeText(this, getString(R.string.health_form_toast_end_before_start), Toast.LENGTH_LONG).show()
                return
            }
        }

        val category = categories[spCategory.selectedItemPosition]
        // Nur Symptome übernehmen, die es (noch) als Code gibt; Reihenfolge wie im Enum
        val symptoms = HealthSymptom.values().filter { it.code in selectedSymptoms }.map { it.code }

        if (symptoms.isEmpty() && etNotes.text.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.health_form_toast_content_required), Toast.LENGTH_LONG).show()
            return
        }

        val vet = switchVet.isChecked
        val base = existingEntry ?: HealthObservationEntry()
        val entry = base.copy(
            startDate = startText,
            endDate = endText,
            status = status,
            category = category.code,
            symptoms = symptoms,
            severity = checkedTag(rgSeverity) ?: HealthSeverity.MODERATE.code,
            notes = etNotes.text.toString().trim(),
            vetVisited = vet,
            vetNotes = if (vet) etVetNotes.text.toString().trim() else "",
            photoUris = currentPhotos.toList()
        )

        if (!HealthObservationManager.save(this, dogId, entry)) {
            Toast.makeText(this, getString(R.string.health_form_toast_profile_missing), Toast.LENGTH_SHORT).show()
            return
        }
        // Entfernte, bereits gespeicherte Fotos jetzt vom Datenträger löschen
        HealthPhotos.deleteAll(this, originalPhotos.filter { it !in currentPhotos })
        saved = true
        setResult(RESULT_OK)
        finish()
    }

    private fun confirmDelete() {
        val entry = existingEntry ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.health_form_delete_title)
            .setMessage(R.string.health_form_delete_message)
            .setPositiveButton(R.string.health_form_delete) { _, _ ->
                // Neu hinzugefügte, noch nicht gespeicherte Fotos; gespeicherte löscht der Manager
                discardUnsavedPhotos()
                HealthObservationManager.delete(this, dogId, entry.id)
                saved = true
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════
    // COMPANION
    // ═══════════════════════════════════════════════════════════

    companion object {
        const val EXTRA_DOG_ID = "dog_id"
        const val EXTRA_ENTRY = "entry"

        fun startForNew(context: Context, dogId: String, launcher: ActivityResultLauncher<Intent>) {
            launcher.launch(Intent(context, HealthObservationActivity::class.java).apply {
                putExtra(EXTRA_DOG_ID, dogId)
            })
        }

        fun startForEdit(
            context: Context,
            dogId: String,
            entry: HealthObservationEntry,
            launcher: ActivityResultLauncher<Intent>
        ) {
            launcher.launch(Intent(context, HealthObservationActivity::class.java).apply {
                putExtra(EXTRA_DOG_ID, dogId)
                putExtra(EXTRA_ENTRY, entry)
            })
        }
    }
}
