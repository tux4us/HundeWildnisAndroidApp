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
            Toast.makeText(this, "Anleitung nicht gefunden", Toast.LENGTH_SHORT).show()
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
            title = "🚨 Erste Hilfe"
            setDisplayHomeAsUpEnabled(true)
        }

        binding.btnEmergencyReport.setOnClickListener { openEmergencyReport() }
        binding.btnHlwTimer.setOnClickListener { openHLWTimer() }
        binding.btnTimer.setOnClickListener { openTimer() }
    }

    private fun displayInstruction() {
        // Header
        binding.tvTitle.text = instruction.title
        binding.tvUrgency.text = instruction.urgency.displayName

        val urgencyColor = android.graphics.Color.parseColor(instruction.urgency.color)
        binding.cardUrgency.setCardBackgroundColor(urgencyColor)

        // Symptome
        binding.tvSymptoms.text = instruction.symptoms.joinToString("\n") { "• $it" }
        binding.tvSymptoms.setTextColor(android.graphics.Color.parseColor("#B71C1C"))

        // Sofortmaßnahmen
        binding.tvImmediateActions.text = instruction.immediateActions.joinToString("\n")
        binding.tvImmediateActions.setTextColor(android.graphics.Color.parseColor("#1B5E20"))

        // Was TUN
        binding.tvDoList.text = instruction.doList.joinToString("\n")

        // Was NICHT tun
        binding.tvDontList.text = instruction.dontList.joinToString("\n")

        // Transport
        binding.tvTransportInfo.text = instruction.transportInfo

        // Spezielle Hinweise
        if (!instruction.specialNotes.isNullOrEmpty()) {
            binding.cardSpecialNotes.visibility = View.VISIBLE
            binding.tvSpecialNotes.text = instruction.specialNotes
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
            binding.btnTimer.text = "⏱️ Timer: ${instruction.timerMinutes} Min"
        } else {
            binding.btnTimer.visibility = View.GONE
        }
    }

    private fun openEmergencyReport() {
        if (profile == null) {
            Toast.makeText(this, "Kein Hundeprofil ausgewählt", Toast.LENGTH_SHORT).show()
            return
        }

        // Öffne bestehende EmergencyActivity mit vorausgefüllten Daten
        val intent = Intent(this, EmergencyActivity::class.java)
        intent.putExtra("profile", profile)
        intent.putExtra("prefill_symptom", instruction.title)
        intent.putExtra("prefill_notes", buildEmergencyNotes())
        intent.putExtra("emergency_type", emergencyType)
        intent.putExtra("first_aid_instructions", buildInstructionText())
        startActivity(intent)
    }

    private fun buildEmergencyNotes(): String {
        return buildString {
            append("NOTFALL: ${instruction.title}\n")
            append("Dringlichkeit: ${instruction.urgency.displayName}\n\n")
            append("SYMPTOME:\n")
            instruction.symptoms.take(3).forEach { append("• $it\n") }
        }
    }

    private fun buildInstructionText(): String {
        return buildString {
            append("═══ ERSTE-HILFE ANLEITUNG ═══\n\n")
            append("${instruction.title}\n")
            append("${instruction.urgency.displayName}\n\n")

            append("SOFORTMASSNAHMEN:\n")
            instruction.immediateActions.forEach { append("$it\n") }
            append("\n")

            append("WAS TUN:\n")
            instruction.doList.forEach { append("$it\n") }
            append("\n")

            append("WAS NICHT TUN:\n")
            instruction.dontList.forEach { append("$it\n") }
            append("\n")

            append("TRANSPORT:\n${instruction.transportInfo}\n")

            if (!instruction.specialNotes.isNullOrEmpty()) {
                append("\nWICHTIG:\n${instruction.specialNotes}")
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
        intent.putExtra("title", instruction.title)
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}