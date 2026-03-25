package com.hundewildnis.app

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
            
            binding.etBirthDate.setText(p.birthDate)
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
            binding.etInsurance.setText(p.insurance)
            binding.etOwner.setText(p.owner)
            binding.etEmergency.setText(p.emergencyContact)
            binding.etFood.setText(p.food)
            binding.etBehavior.setText(p.behavior)
            
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
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
        
        binding.etBirthDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    binding.etBirthDate.setText(dateFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.etWeightDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    binding.etWeightDate.setText(dateFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
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
            insurance = binding.etInsurance.text.toString(),
            owner = binding.etOwner.text.toString(),
            emergencyContact = binding.etEmergency.text.toString(),
            food = binding.etFood.text.toString(),
            behavior = binding.etBehavior.text.toString(),
            photoUri = photoUri
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
