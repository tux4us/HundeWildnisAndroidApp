package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.hundewildnis.app.databinding.ActivityFirstAidWizardBinding

// ════════════════════════════════════════════════════════════════
// ERSTE-HILFE WIZARD ACTIVITY
// Neu erstellen: FirstAidWizardActivity.kt
// ════════════════════════════════════════════════════════════════

class FirstAidWizardActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityFirstAidWizardBinding
    private var profile: DogProfile? = null

    private var currentStep = WizardStep.MAIN_CATEGORY
    private var selectedMainCategory: EmergencyMainCategory? = null
    private var selectedEmergencyType: String? = null
    private val symptomCheckResults = mutableMapOf<String, Boolean>()

    enum class WizardStep {
        MAIN_CATEGORY,      // Unfall oder Erkrankung?
        SUB_CATEGORY,       // Welche Art?
        SYMPTOM_CHECK,      // Schnell-Check
        INSTRUCTIONS        // Anleitung
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFirstAidWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")

        setupUI()
        showMainCategories()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "🚨 Erste Hilfe"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.btnBack.setOnClickListener { goBack() }
    }

    private fun showMainCategories() {
        currentStep = WizardStep.MAIN_CATEGORY

        binding.tvStepTitle.text = "WAS IST PASSIERT?"
        binding.tvStepSubtitle.text = "Wähle die Hauptkategorie"

        binding.btnBack.visibility = View.GONE
        binding.cardContent.visibility = View.VISIBLE

        // Grid Layout für 2 große Buttons
        binding.rvOptions.layoutManager = GridLayoutManager(this, 2)

        val adapter = MainCategoryAdapter(
            categories = EmergencyMainCategory.values().toList(),
            onCategoryClick = { category ->
                selectedMainCategory = category
                showSubCategories(category)
            }
        )
        binding.rvOptions.adapter = adapter
    }

    private fun showSubCategories(mainCategory: EmergencyMainCategory) {
        currentStep = WizardStep.SUB_CATEGORY

        binding.tvStepTitle.text = mainCategory.displayName(this)
        binding.tvStepSubtitle.text = getString(R.string.first_aid_wizard_subcategory_question, mainCategory.displayName(this).lowercase())

        binding.btnBack.visibility = View.VISIBLE

        // Liste Layout
        binding.rvOptions.layoutManager = LinearLayoutManager(this)

        when (mainCategory) {
            EmergencyMainCategory.ACCIDENT -> {
                val adapter = AccidentTypeAdapter(
                    types = AccidentType.values().toList(),
                    onTypeClick = { type ->
                        selectedEmergencyType = type.name
                        showSymptomCheck(type.name)
                    }
                )
                binding.rvOptions.adapter = adapter
            }
            EmergencyMainCategory.ILLNESS -> {
                val adapter = IllnessTypeAdapter(
                    types = IllnessType.values().toList(),
                    onTypeClick = { type ->
                        selectedEmergencyType = type.name
                        showSymptomCheck(type.name)
                    }
                )
                binding.rvOptions.adapter = adapter
            }
        }
    }

    private fun showSymptomCheck(emergencyType: String) {
        currentStep = WizardStep.SYMPTOM_CHECK

        val instruction = FirstAidInstructions.getAll()[emergencyType]
        if (instruction == null) {
            showInstructions(emergencyType)
            return
        }

        val questions = FirstAidInstructions.getSymptomChecks(this, emergencyType)
        if (questions.isEmpty()) {
            showInstructions(emergencyType)
            return
        }

        binding.tvStepTitle.text = getString(R.string.first_aid_wizard_quick_check)
        binding.tvStepSubtitle.text = instruction.title(this)
        binding.btnBack.visibility = View.VISIBLE

        binding.rvOptions.layoutManager = LinearLayoutManager(this)

        val adapter = SymptomCheckAdapter(
            context = this,
            questions = questions,
            onAllAnswered = { results ->
                symptomCheckResults.clear()
                symptomCheckResults.putAll(results)
                showInstructions(emergencyType)
            }
        )
        binding.rvOptions.adapter = adapter
    }

    private fun showInstructions(emergencyType: String) {
        currentStep = WizardStep.INSTRUCTIONS

        val instruction = FirstAidInstructions.getAll()[emergencyType]
        if (instruction == null) {
            Toast.makeText(this, getString(R.string.first_aid_instruction_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Öffne Anleitung in separater Activity
        val intent = Intent(this, FirstAidInstructionActivity::class.java)
        intent.putExtra("profile", profile)
        intent.putExtra("emergency_type", emergencyType)
        intent.putExtra("main_category", selectedMainCategory?.name)
        intent.putExtra("symptom_results", HashMap(symptomCheckResults))
        startActivity(intent)

        // Schließe Wizard (Anleitung bleibt offen)
        finish()
    }

    private fun goBack() {
        when (currentStep) {
            WizardStep.SUB_CATEGORY -> showMainCategories()
            WizardStep.SYMPTOM_CHECK -> {
                selectedMainCategory?.let { showSubCategories(it) }
            }
            WizardStep.INSTRUCTIONS -> {
                selectedEmergencyType?.let { showSymptomCheck(it) }
            }
            else -> finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}