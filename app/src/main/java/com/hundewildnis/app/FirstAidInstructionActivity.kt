package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.hundewildnis.app.databinding.ActivityFirstAidInstructionBinding

// ════════════════════════════════════════════════════════════════
// ERSTE-HILFE ANLEITUNG ACTIVITY
// Zeigt die finale Schritt-für-Schritt Anleitung
// Neu erstellen: FirstAidInstructionActivity.kt
// ════════════════════════════════════════════════════════════════

class FirstAidInstructionActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityFirstAidInstructionBinding
    private var profile: DogProfile? = null
    private lateinit var emergencyType: String
    private lateinit var instruction: FirstAidInstruction

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFirstAidInstructionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")
        emergencyType = intent.getStringExtra("emergency_type") ?: ""

        val instructionData = FirstAidInstructions.getAll()[emergencyType]
        if (instructionData == null) {
            Toast.makeText(this, getString(R.string.first_aid_instruction_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        instruction = instructionData

        setupUI()
        displayInstruction()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.first_aid_instruction_toolbar_title)
            setDisplayHomeAsUpEnabled(true)
        }

        binding.btnEmergencyReport.setOnClickListener { openEmergencyReport() }
        binding.btnHlwTimer.setOnClickListener { openHLWTimer() }
        binding.btnTimer.setOnClickListener { openTimer() }
    }

    private fun displayInstruction() {
        // Header
        binding.tvTitle.text = instruction.title(this)
        binding.tvUrgency.text = instruction.urgency.displayName(this)

        val urgencyColor = android.graphics.Color.parseColor(instruction.urgency.color)
        binding.cardUrgency.setCardBackgroundColor(urgencyColor)

        // Symptome
        binding.tvSymptoms.text = instruction.symptoms(this).joinToString("\n") { "• $it" }
        binding.tvSymptoms.setTextColor(android.graphics.Color.parseColor("#B71C1C"))

        // Sofortmaßnahmen
        binding.tvImmediateActions.text = instruction.immediateActions(this).joinToString("\n")
        binding.tvImmediateActions.setTextColor(android.graphics.Color.parseColor("#1B5E20"))

        // Was TUN
        binding.tvDoList.text = instruction.doList(this).joinToString("\n")

        // Was NICHT tun
        binding.tvDontList.text = instruction.dontList(this).joinToString("\n")

        // Transport
        binding.tvTransportInfo.text = instruction.transportInfo(this)

        // Spezielle Hinweise
        val specialNotesText = instruction.specialNotes(this)
        if (!specialNotesText.isNullOrEmpty()) {
            binding.cardSpecialNotes.visibility = View.VISIBLE
            binding.tvSpecialNotes.text = specialNotesText
        } else {
            binding.cardSpecialNotes.visibility = View.GONE
        }

        // HLW Button
        if (instruction.needsHLW) {
            binding.btnHlwTimer.visibility = View.VISIBLE
        } else {
            binding.btnHlwTimer.visibility = View.GONE
        }

        // Timer Button
        if (instruction.needsTimer) {
            binding.btnTimer.visibility = View.VISIBLE
            binding.btnTimer.text = getString(R.string.first_aid_instruction_timer_button, instruction.timerMinutes)
        } else {
            binding.btnTimer.visibility = View.GONE
        }
    }

    private fun openEmergencyReport() {
        if (profile == null) {
            Toast.makeText(this, getString(R.string.first_aid_instruction_no_profile_selected), Toast.LENGTH_SHORT).show()
            return
        }

        // Öffne bestehende EmergencyActivity mit vorausgefüllten Daten
        val intent = Intent(this, EmergencyActivity::class.java)
        intent.putExtra("profile", profile)
        intent.putExtra("prefill_symptom", instruction.title(this))
        intent.putExtra("prefill_notes", buildEmergencyNotes())
        intent.putExtra("emergency_type", emergencyType)
        intent.putExtra("first_aid_instructions", buildInstructionText())
        startActivity(intent)
    }

    private fun buildEmergencyNotes(): String {
        return buildString {
            append(getString(R.string.first_aid_instruction_notes_emergency, instruction.title(this@FirstAidInstructionActivity))).append("\n")
            append(getString(R.string.first_aid_instruction_notes_urgency, instruction.urgency.displayName(this@FirstAidInstructionActivity))).append("\n\n")
            append(getString(R.string.first_aid_instruction_notes_symptoms)).append("\n")
            instruction.symptoms(this@FirstAidInstructionActivity).take(3).forEach { append("• $it\n") }
        }
    }

    private fun buildInstructionText(): String {
        return buildString {
            append(getString(R.string.first_aid_instruction_text_header)).append("\n\n")
            append(instruction.title(this@FirstAidInstructionActivity)).append("\n")
            append(instruction.urgency.displayName(this@FirstAidInstructionActivity)).append("\n\n")

            append(getString(R.string.first_aid_instruction_text_immediate_actions)).append("\n")
            instruction.immediateActions(this@FirstAidInstructionActivity).forEach { append("$it\n") }
            append("\n")

            append(getString(R.string.first_aid_instruction_text_do_list)).append("\n")
            instruction.doList(this@FirstAidInstructionActivity).forEach { append("$it\n") }
            append("\n")

            append(getString(R.string.first_aid_instruction_text_dont_list)).append("\n")
            instruction.dontList(this@FirstAidInstructionActivity).forEach { append("$it\n") }
            append("\n")

            append(getString(R.string.first_aid_instruction_text_transport, instruction.transportInfo(this@FirstAidInstructionActivity))).append("\n")

            val specialNotesText = instruction.specialNotes(this@FirstAidInstructionActivity)
            if (!specialNotesText.isNullOrEmpty()) {
                append("\n").append(getString(R.string.first_aid_instruction_text_important, specialNotesText))
            }
        }
    }

    private fun openHLWTimer() {
        val intent = Intent(this, HLWTimerActivity::class.java)
        startActivity(intent)
    }

    private fun openTimer() {
        // Öffne einfachen Timer
        val intent = Intent(this, SimpleTimerActivity::class.java)
        intent.putExtra("minutes", instruction.timerMinutes)
        intent.putExtra("title", instruction.title(this))
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}