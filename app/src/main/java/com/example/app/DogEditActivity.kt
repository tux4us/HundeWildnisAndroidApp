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
import com.hundewildnis.app.databinding.ActivityDogEditBinding
import java.text.SimpleDateFormat
import java.util.*

class DogEditActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityDogEditBinding
    private var profile: DogProfile? = null
    private var photoUri: String = ""
    
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    // Persistente Permission
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    photoUri = uri.toString()
                    binding.ivPhoto.setImageURI(uri)
                    binding.btnRemovePhoto.visibility = android.view.View.VISIBLE
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Foto-Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
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
            title = if (profile == null) "🐕 Neuer Hund" else "🐕 ${profile?.name} bearbeiten"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        setupSpinners()
        loadData()
        setupButtons()
        setupDatePickers()
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
    }

    private fun updateBirthDateField(position: Int) {
        // Zuerst den Hint vom EditText entfernen, um Überlagerungen zu vermeiden
        binding.etBirthDate.hint = null
        
        when (position) {
            0 -> { // Datum bekannt
                binding.tilBirthDate.visibility = View.VISIBLE
                binding.etBirthDate.isEnabled = true
                binding.tilBirthDate.placeholderText = "Tag.Monat.Jahr wählen"
                if (binding.etBirthDate.text?.contains(".") == false) binding.etBirthDate.setText("")
            }
            1 -> { // Monat/Jahr bekannt
                binding.tilBirthDate.visibility = View.VISIBLE
                binding.etBirthDate.isEnabled = true
                binding.tilBirthDate.placeholderText = "Monat.Jahr wählen"
                if (!binding.etBirthDate.text.toString().matches(Regex("\\d{2}\\.\\d{4}"))) binding.etBirthDate.setText("")
            }
            2 -> { // Jahr bekannt
                binding.tilBirthDate.visibility = View.VISIBLE
                binding.etBirthDate.isEnabled = true
                binding.tilBirthDate.placeholderText = "Jahr wählen"
                if (!binding.etBirthDate.text.toString().matches(Regex("\\d{4}"))) binding.etBirthDate.setText("")
            }
            3 -> { // Unbekannt
                binding.tilBirthDate.visibility = View.GONE
                binding.etBirthDate.setText("Unbekannt")
            }
        }
    }

    private fun loadData() {
        profile?.let { p ->
            binding.etName.setText(p.name)
            binding.etBreed.setText(p.breed)
            
            // Spinner setzen
            val genderIndex = when(p.gender) {
                "Rüde" -> 1
                "Hündin" -> 2
                else -> 0
            }
            binding.spinnerGender.setText(
                binding.spinnerGender.adapter.getItem(genderIndex).toString(),
                false
            )
            
            val neuteredIndex = when(p.neutered) {
                "Ja" -> 1
                "Nein" -> 2
                "Unbekannt" -> 3
                else -> 0
            }
            binding.spinnerNeutered.setText(
                binding.spinnerNeutered.adapter.getItem(neuteredIndex).toString(),
                false
            )

            // Geburtsdatum Typ erkennen
            val birthDate = p.birthDate
            val (typeIndex, displayDate) = when {
                birthDate == "Unbekannt" || birthDate.isEmpty() -> Pair(3, "Unbekannt")
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
            photoUri = ""
            binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
            binding.btnRemovePhoto.visibility = android.view.View.GONE
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }
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
                    showMonthYearPicker()
                }
                options[2] -> { // Jahr
                    showYearPicker()
                }
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

    private fun showMonthYearPicker() {
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
            .setTitle("Monat und Jahr wählen")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val month = String.format("%02d", monthPicker.value)
                binding.etBirthDate.setText("$month.${yearPicker.value}")
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showYearPicker() {
        val calendar = Calendar.getInstance()
        val yearPicker = NumberPicker(this)
        val currentYear = calendar.get(Calendar.YEAR)
        yearPicker.minValue = currentYear - 30
        yearPicker.maxValue = currentYear
        yearPicker.value = currentYear

        AlertDialog.Builder(this)
            .setTitle("Jahr wählen")
            .setView(yearPicker)
            .setPositiveButton("OK") { _, _ ->
                binding.etBirthDate.setText("${yearPicker.value}")
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun save() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Name erforderlich", Toast.LENGTH_SHORT).show()
            return
        }

        val savedProfile = (profile ?: DogProfile()).copy(
            name = name,
            breed = binding.etBreed.text.toString(),
            gender = binding.spinnerGender.text.toString(),
            neutered = binding.spinnerNeutered.text.toString(),
            birthDate = binding.etBirthDate.text.toString(),
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

        )

        if (DogProfileManager.saveProfile(this, savedProfile)) {
            Toast.makeText(this, "✅ $name gespeichert", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, "❌ Fehler beim Speichern", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
