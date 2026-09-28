package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import com.google.android.material.chip.Chip
import com.hundewildnis.app.databinding.ActivitySpielSpassFilterBinding

/**
 * Zweiter Schritt: Auswahl der Örtlichkeit und weiterer Filterparameter
 * (Zeitbudget, Material, Fokus, Alter/Fitness-Einschränkungen).
 * Energielevel-Vorbelegung erfolgt implizit über DogProfile.characterExcitability,
 * wird hier aber nicht als Hartfilter verwendet, da Auswahl frei bleiben soll.
 */
class SpielSpassFilterActivity : SecureBaseActivity() {

    private lateinit var binding: ActivitySpielSpassFilterBinding
    private lateinit var profile: DogProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpielSpassFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")!!

        setupUI()
        setupChips()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.spiel_spass_filter_toolbar_title)
    }

    private fun setupChips() {
        // Örtlichkeit (Einfachauswahl)
        SpielOrt.values().forEach { ort ->
            val chip = Chip(this).apply {
                text = "${ort.icon} ${ort.displayName(context)}"
                isCheckable = true
                tag = ort
            }
            binding.chipGroupOrt.addView(chip)
        }
        binding.chipGroupOrt.isSingleSelection = true
        (binding.chipGroupOrt.getChildAt(0) as? Chip)?.isChecked = true

        // Zeitbudget (Einfachauswahl)
        SpielZeitbudget.values().forEach { zeit ->
            val chip = Chip(this).apply {
                text = zeit.displayName(context)
                isCheckable = true
                tag = zeit
            }
            binding.chipGroupZeit.addView(chip)
        }
        binding.chipGroupZeit.isSingleSelection = true

        // Material (Mehrfachauswahl) – KEINS ist implizit immer erlaubt, daher nicht als Chip anzeigen
        SpielMaterial.values().filter { it != SpielMaterial.KEINS }.forEach { material ->
            val chip = Chip(this).apply {
                text = material.displayName(context)
                isCheckable = true
                isChecked = true // standardmäßig alles verfügbar, Nutzer schränkt bei Bedarf ein
                tag = material
            }
            binding.chipGroupMaterial.addView(chip)
        }

        // Fokus (Mehrfachauswahl, optional)
        SpielFokus.values().forEach { fokus ->
            val chip = Chip(this).apply {
                text = "${fokus.icon} ${fokus.displayName(context)}"
                isCheckable = true
                tag = fokus
            }
            binding.chipGroupFokus.addView(chip)
        }

        binding.btnSubmit.setOnClickListener { submitFilter() }
    }

    private fun selectedOrt(): SpielOrt {
        for (i in 0 until binding.chipGroupOrt.childCount) {
            val chip = binding.chipGroupOrt.getChildAt(i) as Chip
            if (chip.isChecked) return chip.tag as SpielOrt
        }
        return SpielOrt.GARTEN
    }

    private fun selectedZeitbudget(): SpielZeitbudget? {
        for (i in 0 until binding.chipGroupZeit.childCount) {
            val chip = binding.chipGroupZeit.getChildAt(i) as Chip
            if (chip.isChecked) return chip.tag as SpielZeitbudget
        }
        return null
    }

    private fun selectedMaterial(): Set<SpielMaterial> {
        val result = mutableSetOf(SpielMaterial.KEINS)
        for (i in 0 until binding.chipGroupMaterial.childCount) {
            val chip = binding.chipGroupMaterial.getChildAt(i) as Chip
            if (chip.isChecked) result.add(chip.tag as SpielMaterial)
        }
        return result
    }

    private fun selectedFokus(): Set<SpielFokus> {
        val result = mutableSetOf<SpielFokus>()
        for (i in 0 until binding.chipGroupFokus.childCount) {
            val chip = binding.chipGroupFokus.getChildAt(i) as Chip
            if (chip.isChecked) result.add(chip.tag as SpielFokus)
        }
        return result
    }

    private fun submitFilter() {
        val ort = selectedOrt()
        val ergebnisse = SpielAktivitaetCatalog.filter(
            ort = ort,
            zeitbudget = selectedZeitbudget(),
            verfuegbaresMaterial = selectedMaterial(),
            gewuenschterFokus = selectedFokus(),
            welpeOderJunghund = binding.checkWelpe.isChecked,
            senior = binding.checkSenior.isChecked,
            gelenkproblem = binding.checkGelenkproblem.isChecked
        )

        val intent = Intent(this, SpielSpassResultActivity::class.java).apply {
            putExtra("profile", profile)
            putExtra("ort", ort.name)
            putStringArrayListExtra("aktivitaetIds", ArrayList(ergebnisse.map { it.id }))
        }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
