package com.hundewildnis.app

import android.net.Uri
import android.os.Bundle
import com.hundewildnis.app.databinding.ActivityDogViewBinding

class DogViewActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityDogViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDogViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val profile = intent.getParcelableExtra<DogProfile>("profile")

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "🐕 ${profile?.name ?: "Hund"}"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        profile?.let { displayProfile(it) }
    }

    private fun displayProfile(p: DogProfile) {
        // Foto
        if (p.photoUri.isNotEmpty()) {
            try {
                binding.ivPhoto.setImageURI(Uri.parse(p.photoUri))
            } catch (e: Exception) {
                binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
            }
        } else {
            binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
        }

        // Basis-Daten
        binding.tvName.text = p.name
        binding.tvBreed.text = p.breed
        binding.tvGender.text = p.gender
        binding.tvNeutered.text = "Kastriert: ${p.neutered}"
        binding.tvBirthDate.text = "Geboren: ${p.birthDate}"
        binding.tvColor.text = "Farbe: ${p.color}"
        
        // Identifikation
        binding.tvChip.text = "Chip: ${p.chipNumber.ifEmpty { "-" }}"
        binding.tvTax.text = "Steuernummer: ${p.taxNumber.ifEmpty { "-" }}"
        binding.tvTasso.text = "TASSO: ${p.tassoNumber.ifEmpty { "-" }}"
        
        // Gesundheit
        binding.tvVaccination.text = "Impfstatus: ${p.vaccinationStatus.ifEmpty { "-" }}"
        binding.tvDeworming.text = "Entwurmung: ${p.lastDeworming.ifEmpty { "-" }}"
        binding.tvFleaTick.text = "Floh/Zecke: ${p.lastFleaTick.ifEmpty { "-" }}"
        binding.tvWeight.text = if (p.weight.isNotEmpty()) {
            "Gewicht: ${p.weight} kg (${p.weightDate})"
        } else {
            "Gewicht: -"
        }
        binding.tvMedical.text = "Hinweise: ${p.medicalNotes.ifEmpty { "-" }}"
        
        // Kontakte
        binding.tvVet.text = "Tierarzt: ${p.veterinarian.ifEmpty { "-" }}"
        binding.tvInsurance.text = "Versicherung: ${p.liabilityInsurance.ifEmpty { "-" }}"
        binding.tvOwner.text = "Besitzer: ${p.owner.ifEmpty { "-" }}"
        binding.tvEmergency.text = "Notfall: ${p.additionalEmergencyContact.ifEmpty { "-" }}"
        
        // Futter & Verhalten
        binding.tvFood.text = "Futter: ${p.food.ifEmpty { "-" }}"
        binding.tvBehavior.text = "Verhalten: ${p.behavior.ifEmpty { "-" }}"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
