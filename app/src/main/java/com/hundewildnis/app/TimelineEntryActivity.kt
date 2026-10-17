package com.hundewildnis.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import coil.load
import com.hundewildnis.app.databinding.ActivityTimelineEntryBinding

/**
 * Eingabe, Bearbeitung und Löschen eines eigenen Timeline-Eintrags.
 *
 * Extras:
 *  - "profile"  : DogProfile (Pflicht)
 *  - "entry_id" : ID eines vorhandenen Eintrags (nur beim Bearbeiten)
 *
 * Gespeichert wird in DogProfile.timelineEntries. Das Profil wird beim Speichern
 * frisch geladen, damit zwischenzeitliche Änderungen anderer Bereiche (Vitalwerte,
 * Läufigkeit) nicht überschrieben werden.
 */
class TimelineEntryActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityTimelineEntryBinding
    private lateinit var profile: DogProfile
    private var editing: TimelineUserEntry? = null

    /** Foto, das beim Öffnen gespeichert war. */
    private var originalPhotoUri: String = ""

    /** Aktuell angezeigtes Foto (leer = keins). Kann ein neu kopiertes Foto sein. */
    private var currentPhotoUri: String = ""

    private var saved = false

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val copied = TimelinePhotos.copyIn(this, uri)
        if (copied == null) {
            Toast.makeText(this, getString(R.string.timeline_entry_photo_error), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        // Ein zuvor in dieser Sitzung neu kopiertes (noch nicht gespeichertes) Foto verwerfen
        discardUnsavedPhoto()
        currentPhotoUri = copied
        showPhoto()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimelineEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        val loadedProfile = intent.getParcelableExtra<DogProfile>("profile")
        if (loadedProfile == null) {
            finish()
            return
        }
        profile = DogProfileManager.getProfile(this, loadedProfile.id) ?: loadedProfile

        val entryId = intent.getStringExtra("entry_id")
        editing = entryId?.let { id -> profile.timelineEntries.find { it.id == id } }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(
            if (editing == null) R.string.timeline_entry_title_new else R.string.timeline_entry_title_edit
        )
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupDropdowns()
        setupDateField()
        setupButtons()
        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Beim Abbrechen kein verwaistes Foto zurücklassen
        if (!saved) discardUnsavedPhoto()
    }

    // ════════════════════════════════════════════════════════════════
    // SETUP
    // ════════════════════════════════════════════════════════════════

    private fun precisionOptions(): List<String> =
        resources.getStringArray(R.array.birthdate_type_options).take(3)

    private fun setupDropdowns() {
        val kinds = TimelineEntryKind.ORDER.map {
            "${TimelineEntryKind.icon(it)} ${TimelineEntryKind.label(this, it)}"
        }
        binding.spinnerKind.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, kinds)
        )
        binding.spinnerPrecision.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, precisionOptions())
        )
        binding.spinnerPrecision.setOnItemClickListener { _, _, position, _ ->
            updateDateField(position)
        }
    }

    private fun currentKindIndex(): Int =
        (0 until binding.spinnerKind.adapter.count)
            .firstOrNull { binding.spinnerKind.adapter.getItem(it).toString() == binding.spinnerKind.text.toString() }
            ?: TimelineEntryKind.ORDER.indexOf(TimelineEntryKind.OTHER)

    private fun currentPrecisionIndex(): Int =
        precisionOptions().indexOf(binding.spinnerPrecision.text.toString()).coerceAtLeast(0)

    private fun updateDateField(precisionIndex: Int) {
        binding.etDate.hint = null
        val current = binding.etDate.text.toString()
        when (precisionIndex) {
            0 -> {
                binding.tilDate.placeholderText = getString(R.string.dog_edit_birthdate_placeholder_full)
                if (!current.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) binding.etDate.setText("")
            }
            1 -> {
                binding.tilDate.placeholderText = getString(R.string.dog_edit_birthdate_placeholder_month_year)
                if (!current.matches(Regex("\\d{2}\\.\\d{4}"))) binding.etDate.setText("")
            }
            else -> {
                binding.tilDate.placeholderText = getString(R.string.dog_edit_birthdate_placeholder_year)
                if (!current.matches(Regex("\\d{4}"))) binding.etDate.setText("")
            }
        }
    }

    private fun setupDateField() {
        binding.etDate.setOnClickListener {
            when (currentPrecisionIndex()) {
                0 -> PartialDatePicker.showFull(this) { binding.etDate.setText(it) }
                1 -> PartialDatePicker.showMonthYear(this) { binding.etDate.setText(it) }
                else -> PartialDatePicker.showYear(this) { binding.etDate.setText(it) }
            }
        }
    }

    private fun setupButtons() {
        binding.btnPickPhoto.setOnClickListener { pickPhoto.launch(arrayOf("image/*")) }
        binding.btnRemovePhoto.setOnClickListener {
            discardUnsavedPhoto()
            currentPhotoUri = ""
            showPhoto()
        }
        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnDelete.visibility = if (editing == null) View.GONE else View.VISIBLE
    }

    private fun loadData() {
        val entry = editing
        val kindCode = TimelineEntryKind.normalize(entry?.kind ?: TimelineEntryKind.ACTIVITY)
        val kindIndex = TimelineEntryKind.ORDER.indexOf(kindCode)
        binding.spinnerKind.setText(binding.spinnerKind.adapter.getItem(kindIndex).toString(), false)

        val precisionIndex = when {
            entry == null -> 0
            entry.date.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}")) -> 0
            entry.date.matches(Regex("\\d{2}\\.\\d{4}")) -> 1
            else -> 2
        }
        binding.spinnerPrecision.setText(precisionOptions()[precisionIndex], false)
        binding.etDate.setText(entry?.date.orEmpty())
        updateDateField(precisionIndex)

        binding.etTitle.setText(entry?.title.orEmpty())
        binding.etPlace.setText(entry?.place.orEmpty())
        binding.etNote.setText(entry?.note.orEmpty())

        originalPhotoUri = entry?.photoUri.orEmpty()
        currentPhotoUri = originalPhotoUri
        showPhoto()
    }

    private fun showPhoto() {
        if (currentPhotoUri.isEmpty()) {
            binding.ivPhoto.visibility = View.GONE
            binding.btnRemovePhoto.visibility = View.GONE
        } else {
            binding.ivPhoto.visibility = View.VISIBLE
            binding.btnRemovePhoto.visibility = View.VISIBLE
            binding.ivPhoto.load(Uri.parse(currentPhotoUri)) {
                error(R.drawable.ic_dog_placeholder)
            }
        }
    }

    /** Löscht ein in dieser Sitzung neu kopiertes Foto, das noch nicht gespeichert wurde. */
    private fun discardUnsavedPhoto() {
        if (currentPhotoUri.isNotEmpty() && currentPhotoUri != originalPhotoUri) {
            TimelinePhotos.delete(this, currentPhotoUri)
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SPEICHERN / LÖSCHEN
    // ════════════════════════════════════════════════════════════════

    private fun save() {
        val title = binding.etTitle.text.toString().trim()
        if (title.isEmpty()) {
            binding.etTitle.error = getString(R.string.timeline_entry_title_required)
            return
        }

        val dateText = binding.etDate.text.toString().trim()
        val parsed = TogetherMath.parse(dateText)
        if (parsed == null) {
            Toast.makeText(this, getString(R.string.timeline_entry_date_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (TogetherMath.elapsed(parsed, TogetherMath.today()) == null) {
            Toast.makeText(this, getString(R.string.timeline_entry_date_future), Toast.LENGTH_SHORT).show()
            return
        }

        val entry = TimelineUserEntry(
            id = editing?.id ?: java.util.UUID.randomUUID().toString(),
            date = dateText,
            kind = TimelineEntryKind.ORDER[currentKindIndex()],
            title = title,
            place = binding.etPlace.text.toString().trim(),
            note = binding.etNote.text.toString().trim(),
            photoUri = currentPhotoUri
        )

        val fresh = DogProfileManager.getProfile(this, profile.id) ?: profile
        val updated = fresh.copy(
            timelineEntries = fresh.timelineEntries.filterNot { it.id == entry.id } + entry,
            lastModified = System.currentTimeMillis()
        )
        DogProfileManager.saveProfile(this, updated)

        // Ersetztes oder entferntes Originalfoto aufräumen
        if (originalPhotoUri.isNotEmpty() && originalPhotoUri != currentPhotoUri) {
            TimelinePhotos.delete(this, originalPhotoUri)
        }

        saved = true
        Toast.makeText(this, getString(R.string.timeline_entry_saved), Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun confirmDelete() {
        val entry = editing ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.timeline_entry_delete_confirm_title))
            .setMessage(getString(R.string.timeline_entry_delete_confirm_message, entry.title))
            .setPositiveButton(getString(R.string.timeline_entry_delete)) { _, _ -> delete(entry) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun delete(entry: TimelineUserEntry) {
        val fresh = DogProfileManager.getProfile(this, profile.id) ?: profile
        val updated = fresh.copy(
            timelineEntries = fresh.timelineEntries.filterNot { it.id == entry.id },
            lastModified = System.currentTimeMillis()
        )
        DogProfileManager.saveProfile(this, updated)
        TimelinePhotos.delete(this, entry.photoUri)
        // Ein in dieser Sitzung ersetztes, noch nicht gespeichertes Foto ebenfalls entfernen
        if (currentPhotoUri != entry.photoUri) TimelinePhotos.delete(this, currentPhotoUri)

        saved = true
        Toast.makeText(this, getString(R.string.timeline_entry_deleted), Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }
}
