package com.hundewildnis.app

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hundewildnis.app.databinding.ActivityDogEditBinding
import java.text.SimpleDateFormat
import java.util.*

class DogEditActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityDogEditBinding
    private var profile: DogProfile? = null
    private var photoUri: String = ""

    /** In dieser Sitzung neu importiertes (verkleinertes) Foto, solange noch nicht gespeichert. */
    private var sessionPhoto: String = ""
    private var saved = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Foto verkleinert (max. 1280 px) in den App-Speicher übernehmen
                lifecycleScope.launch {
                    val imported = withContext(Dispatchers.IO) { PhotoCompressor.importPhoto(this@DogEditActivity, uri) }
                    if (imported != null) {
                        discardSessionPhoto()
                        sessionPhoto = imported.toString()
                        photoUri = sessionPhoto
                        binding.ivPhoto.setImageURI(imported)
                        binding.btnRemovePhoto.visibility = android.view.View.VISIBLE
                    } else {
                        Toast.makeText(
                            this@DogEditActivity,
                            getString(R.string.dog_edit_photo_error, ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDogEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = if (profile == null) getString(R.string.dog_edit_title_new) else getString(R.string.dog_edit_title_edit_format, profile?.name)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        setupSpinners()
        loadData()
        setupButtons()
        setupDatePickers()
        setupCharacterSliders()
    }

    private fun setupSpinners() {
        // Geschlecht
        val genderAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.gender_options,
            android.R.layout.simple_dropdown_item_1line
        )
        binding.spinnerGender.setAdapter(genderAdapter)

        // Kastrationsstatus
        val neuteredAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.neutered_options,
            android.R.layout.simple_dropdown_item_1line
        )
        binding.spinnerNeutered.setAdapter(neuteredAdapter)

        // Geburtsdatum Typ
        val birthDateTypeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.birthdate_type_options,
            android.R.layout.simple_dropdown_item_1line
        )
        binding.spinnerBirthDateType.setAdapter(birthDateTypeAdapter)
        binding.spinnerBirthDateType.setOnItemClickListener { _, _, position, _ ->
            updateBirthDateField(position)
        }

        // Gemeinsame Zeit: Art des Beginns
        binding.spinnerTogetherType.setAdapter(
            ArrayAdapter.createFromResource(
                this,
                R.array.together_since_type_options,
                android.R.layout.simple_dropdown_item_1line
            )
        )
        binding.spinnerTogetherType.setOnItemClickListener { _, _, position, _ ->
            updateTogetherFields(position, currentTogetherPrecisionIndex())
        }

        // Gemeinsame Zeit: Genauigkeit (gleiche Auswahl wie beim Geburtsdatum)
        binding.spinnerTogetherPrecision.setAdapter(
            ArrayAdapter.createFromResource(
                this,
                R.array.birthdate_type_options,
                android.R.layout.simple_dropdown_item_1line
            )
        )
        binding.spinnerTogetherPrecision.setOnItemClickListener { _, _, position, _ ->
            updateTogetherFields(currentTogetherTypeIndex(), position)
        }
    }

    private fun setupCharacterSliders() {
        // Quick-Profil Button
        binding.btnQuickProfile.setOnClickListener {
            showQuickProfileDialog()
        }

        // Lade gespeicherte Werte (falls Hund bearbeitet wird)
        if (profile != null) {
            binding.sliderTemperament.value = profile!!.characterTemperament.toFloat()
            binding.sliderConfidence.value = profile!!.characterConfidence.toFloat()
            binding.sliderSocial.value = profile!!.characterSocial.toFloat()
            binding.sliderExcitability.value = profile!!.characterExcitability.toFloat()
            binding.sliderLearning.value = profile!!.characterLearning.toFloat()
            binding.sliderFrustration.value = profile!!.characterFrustration.toFloat()
            binding.etCharacterNotes.setText(profile!!.characterNotes)
        }

        // Feld liegt weit unten im Formular und wird beim Fokussieren von der
        // Tastatur verdeckt. Statt die Tastaturhöhe zu berechnen (fragil,
        // von Gerät/Timing abhängig), gibt es am Ende des Layouts einen
        // großzügigen Spacer (400dp, siehe activity_dog_edit.xml). Dadurch
        // reicht simples Scrollen ans Seitenende, um das Feld sicher über
        // jeder Tastatur zu platzieren.
        binding.etCharacterNotes.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollViewDogEdit.post {
                    binding.scrollViewDogEdit.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    private fun showQuickProfileDialog() {
        val profiles = QuickCharacterProfile.values()
        val profileNames = profiles.map { "${it.displayName(this)}\n${it.description(this)}" }.toTypedArray()


        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.dog_edit_quick_profile_dialog_title))
            .setItems(profileNames) { _, which ->
                applyQuickProfile(profiles[which])
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .apply {
                show()
            }
    }

    private fun applyQuickProfile(profile: QuickCharacterProfile) {
        binding.sliderTemperament.value = profile.temperament.toFloat()
        binding.sliderConfidence.value = profile.confidence.toFloat()
        binding.sliderSocial.value = profile.social.toFloat()
        binding.sliderExcitability.value = profile.excitability.toFloat()
        binding.sliderLearning.value = profile.learning.toFloat()
        binding.sliderFrustration.value = profile.frustration.toFloat()

        android.widget.Toast.makeText(
            this,
            getString(R.string.dog_edit_quick_profile_applied, profile.displayName(this)),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateBirthDateField(position: Int) {
        // Zuerst den Hint vom EditText entfernen, um Überlagerungen zu vermeiden
        binding.etBirthDate.hint = null

        when (position) {
            0 -> { // Datum bekannt
                binding.tilBirthDate.visibility = View.VISIBLE
                binding.etBirthDate.isEnabled = true
                binding.tilBirthDate.placeholderText = getString(R.string.dog_edit_birthdate_placeholder_full)
                if (binding.etBirthDate.text?.contains(".") == false) binding.etBirthDate.setText("")
            }
            1 -> { // Monat/Jahr bekannt
                binding.tilBirthDate.visibility = View.VISIBLE
                binding.etBirthDate.isEnabled = true
                binding.tilBirthDate.placeholderText = getString(R.string.dog_edit_birthdate_placeholder_month_year)
                if (!binding.etBirthDate.text.toString().matches(Regex("\\d{2}\\.\\d{4}"))) binding.etBirthDate.setText("")
            }
            2 -> { // Jahr bekannt
                binding.tilBirthDate.visibility = View.VISIBLE
                binding.etBirthDate.isEnabled = true
                binding.tilBirthDate.placeholderText = getString(R.string.dog_edit_birthdate_placeholder_year)
                if (!binding.etBirthDate.text.toString().matches(Regex("\\d{4}"))) binding.etBirthDate.setText("")
            }
            3 -> { // Unbekannt
                binding.tilBirthDate.visibility = View.GONE
                binding.etBirthDate.setText(getString(R.string.dog_edit_birthdate_unknown))
            }
        }
    }

    private fun currentTogetherTypeIndex(): Int {
        val options = resources.getStringArray(R.array.together_since_type_options)
        return options.indexOf(binding.spinnerTogetherType.text.toString()).coerceAtLeast(0)
    }

    private fun currentTogetherPrecisionIndex(): Int {
        val options = resources.getStringArray(R.array.birthdate_type_options)
        return options.indexOf(binding.spinnerTogetherPrecision.text.toString()).coerceAtLeast(0)
    }

    /**
     * Blendet die Felder für den Beginn der gemeinsamen Zeit passend ein/aus.
     * typeIndex 0 = seit Geburt: kein eigenes Datum, das Geburtsdatum gilt.
     * precisionIndex wie beim Geburtsdatum: 0 Datum, 1 Monat/Jahr, 2 Jahr, 3 Unbekannt.
     */
    private fun updateTogetherFields(typeIndex: Int, precisionIndex: Int) {
        binding.etTogetherSince.hint = null

        if (typeIndex == 0) {
            binding.tilTogetherPrecision.visibility = View.GONE
            binding.tilTogetherSince.visibility = View.GONE
            return
        }

        binding.tilTogetherPrecision.visibility = View.VISIBLE
        val current = binding.etTogetherSince.text.toString()
        when (precisionIndex) {
            0 -> {
                binding.tilTogetherSince.visibility = View.VISIBLE
                binding.tilTogetherSince.placeholderText = getString(R.string.dog_edit_birthdate_placeholder_full)
                if (!current.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}"))) binding.etTogetherSince.setText("")
            }
            1 -> {
                binding.tilTogetherSince.visibility = View.VISIBLE
                binding.tilTogetherSince.placeholderText = getString(R.string.dog_edit_birthdate_placeholder_month_year)
                if (!current.matches(Regex("\\d{2}\\.\\d{4}"))) binding.etTogetherSince.setText("")
            }
            2 -> {
                binding.tilTogetherSince.visibility = View.VISIBLE
                binding.tilTogetherSince.placeholderText = getString(R.string.dog_edit_birthdate_placeholder_year)
                if (!current.matches(Regex("\\d{4}"))) binding.etTogetherSince.setText("")
            }
            else -> {
                binding.tilTogetherSince.visibility = View.GONE
                binding.etTogetherSince.setText("")
            }
        }
    }

    private fun loadData() {
        profile?.let { p ->
            binding.etName.setText(p.name)
            binding.etBreed.setText(p.breed)

            // Spinner setzen (Index anhand des stabilen internen Codes, nicht des Anzeigetexts)
            val genderIndex = when(p.gender) {
                GenderCode.MALE -> 1
                GenderCode.FEMALE -> 2
                else -> 0
            }
            binding.spinnerGender.setText(
                binding.spinnerGender.adapter.getItem(genderIndex).toString(),
                false
            )

            val neuteredIndex = when(p.neutered) {
                NeuteredCode.YES -> 1
                NeuteredCode.NO -> 2
                NeuteredCode.UNKNOWN -> 3
                else -> 0
            }
            binding.spinnerNeutered.setText(
                binding.spinnerNeutered.adapter.getItem(neuteredIndex).toString(),
                false
            )

            // Geburtsdatum Typ erkennen
            // Hinweis: "Unbekannt"/"Unknown" werden hier nur erkannt (Altbestand aus beiden
            // Sprachen), die Anzeige erfolgt anschließend stets über getString(...).
            val birthDate = p.birthDate
            val (typeIndex, displayDate) = when {
                birthDate == "Unbekannt" || birthDate == "Unknown" || birthDate.isEmpty() ->
                    Pair(3, getString(R.string.dog_edit_birthdate_unknown))
                birthDate.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}")) -> Pair(0, birthDate)
                birthDate.matches(Regex("\\d{2}\\.\\d{4}")) -> Pair(1, birthDate)
                birthDate.matches(Regex("\\d{4}")) -> Pair(2, birthDate)
                else -> Pair(0, birthDate)
            }

            binding.spinnerBirthDateType.setText(
                binding.spinnerBirthDateType.adapter.getItem(typeIndex).toString(),
                false
            )
            binding.etBirthDate.setText(displayDate)
            updateBirthDateField(typeIndex)

            // Gemeinsame Zeit
            val togetherTypeIndex = TogetherSinceType.toIndex(p.togetherSinceType)
            val togetherPrecisionIndex = when {
                p.togetherSince.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}")) -> 0
                p.togetherSince.matches(Regex("\\d{2}\\.\\d{4}")) -> 1
                p.togetherSince.matches(Regex("\\d{4}")) -> 2
                togetherTypeIndex == 0 -> 0
                else -> 3
            }
            binding.spinnerTogetherType.setText(
                binding.spinnerTogetherType.adapter.getItem(togetherTypeIndex).toString(),
                false
            )
            binding.spinnerTogetherPrecision.setText(
                binding.spinnerTogetherPrecision.adapter.getItem(togetherPrecisionIndex).toString(),
                false
            )
            binding.etTogetherSince.setText(p.togetherSince)
            updateTogetherFields(togetherTypeIndex, togetherPrecisionIndex)

            binding.etColor.setText(p.color)
            binding.etChipNumber.setText(p.chipNumber)
            binding.etTaxNumber.setText(p.taxNumber)
            binding.etTassoNumber.setText(p.tassoNumber)
            binding.etVaccination.setText(p.vaccinationStatus)
            binding.etDeworming.setText(p.lastDeworming)
            binding.etFleaTick.setText(p.lastFleaTick)
            binding.etWeight.setText(p.weight)
            binding.etWeightDate.setText(p.weightDate)
            binding.etMedicalNotes.setText(p.medicalNotes)
            binding.etVeterinarian.setText(p.veterinarian)
            binding.etLiabilityInsurance.setText(p.liabilityInsurance)
            binding.etOwner.setText(p.owner)
            binding.etAdditionalEmergency.setText(p.additionalEmergencyContact)
            binding.etFood.setText(p.food)
            binding.etBehavior.setText(p.behavior)

            binding.etOriginCountry.setText(p.originCountry)
            binding.etPetPassportCountry.setText(p.petPassportCountry)
            binding.etPetPassportNumber.setText(p.petPassportNumber)
            binding.etAdditionalVaccination.setText(p.additionalVaccination)
            binding.etShoulderHeight.setText(p.shoulderHeight)
            binding.etBodyLength.setText(p.bodyLength)
            binding.etHealthInsurance.setText(p.healthInsurance)

            if (p.photoUri.isNotEmpty()) {
                photoUri = p.photoUri
                try {
                    binding.ivPhoto.setImageURI(Uri.parse(photoUri))
                    binding.btnRemovePhoto.visibility = android.view.View.VISIBLE
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun setupButtons() {
        binding.btnAddPhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            pickImage.launch(intent)
        }

        binding.btnRemovePhoto.setOnClickListener {
            discardSessionPhoto()
            photoUri = ""
            binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
            binding.btnRemovePhoto.visibility = android.view.View.GONE
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    /** Löscht das in dieser Sitzung importierte, noch nicht gespeicherte Foto. */
    private fun discardSessionPhoto() {
        if (sessionPhoto.isNotEmpty()) {
            PhotoCompressor.deleteOwnPhoto(this, sessionPhoto)
            sessionPhoto = ""
        }
    }

    override fun onDestroy() {
        if (isFinishing && !saved) discardSessionPhoto()
        super.onDestroy()
    }

    private fun setupDatePickers() {
        val calendar = Calendar.getInstance()

        binding.etBirthDate.setOnClickListener {
            val type = binding.spinnerBirthDateType.text.toString()
            val options = resources.getStringArray(R.array.birthdate_type_options)

            when (type) {
                options[0] -> { // Datum bekannt
                    DatePickerDialog(
                        this,
                        { _, year, month, day ->
                            calendar.set(year, month, day)
                            binding.etBirthDate.setText(SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(calendar.time))
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
                options[1] -> { // Monat/Jahr
                    showMonthYearPicker(binding.etBirthDate, false)
                }
                options[2] -> { // Jahr
                    showYearPicker(binding.etBirthDate)
                }
            }
        }

        // Beginn der gemeinsamen Zeit: Datum darf nicht in der Zukunft liegen
        binding.etTogetherSince.setOnClickListener {
            val options = resources.getStringArray(R.array.birthdate_type_options)
            when (options.getOrNull(currentTogetherPrecisionIndex())) {
                options[0] -> {
                    val c = Calendar.getInstance()
                    DatePickerDialog(
                        this,
                        { _, year, month, day ->
                            val picked = Calendar.getInstance().apply { set(year, month, day) }
                            binding.etTogetherSince.setText(SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(picked.time))
                        },
                        c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH),
                        c.get(Calendar.DAY_OF_MONTH)
                    ).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                        show()
                    }
                }
                options[1] -> showMonthYearPicker(binding.etTogetherSince, true)
                options[2] -> showYearPicker(binding.etTogetherSince)
            }
        }

        binding.etWeightDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    binding.etWeightDate.setText(SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun showMonthYearPicker(target: EditText, limitToToday: Boolean) {
        val calendar = Calendar.getInstance()
        val view = layoutInflater.inflate(R.layout.dialog_month_year_picker, null)
        val monthPicker = view.findViewById<NumberPicker>(R.id.monthPicker)
        val yearPicker = view.findViewById<NumberPicker>(R.id.yearPicker)

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = calendar.get(Calendar.MONTH) + 1

        val currentYear = calendar.get(Calendar.YEAR)
        yearPicker.minValue = currentYear - 30
        yearPicker.maxValue = currentYear
        yearPicker.value = currentYear


        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dog_edit_month_year_dialog_title))
            .setView(view)
            .setPositiveButton(getString(R.string.dog_edit_ok_button)) { _, _ ->
                var monthValue = monthPicker.value
                if (limitToToday && yearPicker.value == currentYear) {
                    monthValue = minOf(monthValue, calendar.get(Calendar.MONTH) + 1)
                }
                val month = String.format(Locale.getDefault(), "%02d", monthValue)
                target.setText("$month.${yearPicker.value}")
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().apply {
                show()
            }
    }

    private fun showYearPicker(target: EditText) {
        val calendar = Calendar.getInstance()
        val yearPicker = NumberPicker(this)
        val currentYear = calendar.get(Calendar.YEAR)
        yearPicker.minValue = currentYear - 30
        yearPicker.maxValue = currentYear
        yearPicker.value = currentYear


        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dog_edit_year_dialog_title))
            .setView(yearPicker)
            .setPositiveButton(getString(R.string.dog_edit_ok_button)) { _, _ ->
                target.setText("${yearPicker.value}")
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().apply {
                show()
            }
    }

    /** Bildet den angezeigten (ggf. übersetzten) Spinner-Text über seinen Array-Index auf den stabilen Code ab. */
    private fun genderTextToCode(text: String): String {
        val options = resources.getStringArray(R.array.gender_options)
        return when (options.indexOf(text)) {
            1 -> GenderCode.MALE
            2 -> GenderCode.FEMALE
            else -> ""
        }
    }

    /** Bildet den angezeigten (ggf. übersetzten) Spinner-Text über seinen Array-Index auf den stabilen Code ab. */
    private fun neuteredTextToCode(text: String): String {
        val options = resources.getStringArray(R.array.neutered_options)
        return when (options.indexOf(text)) {
            1 -> NeuteredCode.YES
            2 -> NeuteredCode.NO
            3 -> NeuteredCode.UNKNOWN
            else -> ""
        }
    }

    /** Seit Geburt wird als leerer Code gespeichert (Geburtsdatum gilt). */
    private fun togetherTypeToSave(): String {
        val code = TogetherSinceType.fromIndex(currentTogetherTypeIndex())
        return if (code == TogetherSinceType.BIRTH) "" else code
    }

    /** Nur ein gültig formatiertes Datum wird gespeichert; sonst leer (Fallback auf Geburtsdatum). */
    private fun togetherSinceToSave(): String {
        if (currentTogetherTypeIndex() == 0) return ""
        val text = binding.etTogetherSince.text.toString().trim()
        return if (TogetherMath.parse(text) != null) text else ""
    }

    private fun save() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, getString(R.string.dog_edit_name_required_toast), Toast.LENGTH_SHORT).show()
            return
        }

        val savedProfile = (profile ?: DogProfile()).copy(
            name = name,
            breed = binding.etBreed.text.toString(),
            gender = genderTextToCode(binding.spinnerGender.text.toString()),
            neutered = neuteredTextToCode(binding.spinnerNeutered.text.toString()),
            birthDate = binding.etBirthDate.text.toString(),
            togetherSinceType = togetherTypeToSave(),
            togetherSince = togetherSinceToSave(),
            color = binding.etColor.text.toString(),
            chipNumber = binding.etChipNumber.text.toString(),
            taxNumber = binding.etTaxNumber.text.toString(),
            tassoNumber = binding.etTassoNumber.text.toString(),
            vaccinationStatus = binding.etVaccination.text.toString(),
            lastDeworming = binding.etDeworming.text.toString(),
            lastFleaTick = binding.etFleaTick.text.toString(),
            weight = binding.etWeight.text.toString(),
            weightDate = binding.etWeightDate.text.toString(),
            medicalNotes = binding.etMedicalNotes.text.toString(),
            veterinarian = binding.etVeterinarian.text.toString(),
            liabilityInsurance = binding.etLiabilityInsurance.text.toString(),
            owner = binding.etOwner.text.toString(),
            additionalEmergencyContact = binding.etAdditionalEmergency.text.toString(),
            food = binding.etFood.text.toString(),
            behavior = binding.etBehavior.text.toString(),
            photoUri = photoUri,
            originCountry = binding.etOriginCountry.text.toString(),
            petPassportCountry = binding.etPetPassportCountry.text.toString(),
            petPassportNumber = binding.etPetPassportNumber.text.toString(),
            additionalVaccination = binding.etAdditionalVaccination.text.toString(),
            shoulderHeight = binding.etShoulderHeight.text.toString(),
            bodyLength = binding.etBodyLength.text.toString(),
            healthInsurance = binding.etHealthInsurance.text.toString(),
            characterTemperament = binding.sliderTemperament.value.toInt(),
            characterConfidence = binding.sliderConfidence.value.toInt(),
            characterSocial = binding.sliderSocial.value.toInt(),
            characterExcitability = binding.sliderExcitability.value.toInt(),
            characterLearning = binding.sliderLearning.value.toInt(),
            characterFrustration = binding.sliderFrustration.value.toInt(),
            characterNotes = binding.etCharacterNotes.text.toString(),
            lastModified = System.currentTimeMillis()
        )

        if (DogProfileManager.saveProfile(this, savedProfile)) {
            saved = true
            // Ersetztes oder entferntes Foto aus dem App-Speicher löschen
            val previousPhoto = profile?.photoUri.orEmpty()
            if (previousPhoto.isNotEmpty() && previousPhoto != photoUri) {
                PhotoCompressor.deleteOwnPhoto(this, previousPhoto)
            }
            Toast.makeText(this, getString(R.string.dog_edit_saved_toast, name), Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, getString(R.string.dog_edit_save_error_toast), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}