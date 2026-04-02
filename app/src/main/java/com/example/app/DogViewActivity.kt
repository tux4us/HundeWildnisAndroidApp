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
        binding.tvOriginCountry.text = "Herkunft: ${p.originCountry.ifEmpty { "-" }}"
        binding.tvColor.text = "Farbe: ${p.color}"
        
        // Identifikation
        binding.tvChip.text = "Chip: ${p.chipNumber.ifEmpty { "-" }}"
        binding.tvTax.text = "Steuernummer: ${p.taxNumber.ifEmpty { "-" }}"
        binding.tvTasso.text = "TASSO: ${p.tassoNumber.ifEmpty { "-" }}"
        binding.tvPetPassport.text = "Heimtierausweis: ${p.petPassportCountry} ${p.petPassportNumber}".trim().ifEmpty { "-" }
        
        // Gesundheit
        binding.tvVaccination.text = "Impfstatus: ${p.vaccinationStatus.ifEmpty { "-" }}"
        binding.tvAdditionalVaccination.text = "Weitere Impfungen: ${p.additionalVaccination.ifEmpty { "-" }}"
        binding.tvDeworming.text = "Entwurmung: ${p.lastDeworming.ifEmpty { "-" }}"
        binding.tvFleaTick.text = "Floh/Zecke: ${p.lastFleaTick.ifEmpty { "-" }}"
        binding.tvWeight.text = if (p.weight.isNotEmpty()) {
            "Gewicht: ${p.weight} kg (${p.weightDate})"
        } else {
            "Gewicht: -"
        }
        binding.tvShoulderHeight.text = "Schulterhöhe: ${p.shoulderHeight.ifEmpty { "-" }} cm"
        binding.tvBodyLength.text = "Körperlänge: ${p.bodyLength.ifEmpty { "-" }} cm"
        binding.tvMedical.text = "Hinweise: ${p.medicalNotes.ifEmpty { "-" }}"
        
        // Kontakte
        binding.tvVet.text = "Tierarzt: ${p.veterinarian.ifEmpty { "-" }}"
        binding.tvInsurance.text = "Haftpflicht: ${p.liabilityInsurance.ifEmpty { "-" }}"
        binding.tvHealthInsurance.text = "Krankenvers.: ${p.healthInsurance.ifEmpty { "-" }}"
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
