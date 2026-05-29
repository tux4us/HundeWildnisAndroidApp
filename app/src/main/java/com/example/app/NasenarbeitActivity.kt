package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.hundewildnis.app.databinding.ActivityNasenarbeitBinding
import com.hundewildnis.app.databinding.ItemDogCardBinding
import java.text.SimpleDateFormat
import java.util.*

class NasenarbeitActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityNasenarbeitBinding
    private val profiles = mutableListOf<DogProfile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNasenarbeitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUI()
        loadProfiles()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🐾 Nasenarbeit"
    }

    private fun loadProfiles() {
        val prefs = getSharedPreferences("dog_profiles", MODE_PRIVATE)
        val json = prefs.getString("profiles", null)
        if (json != null) {
            val type = object : com.google.gson.reflect.TypeToken<List<DogProfile>>() {}.type
            val loaded: List<DogProfile> = com.google.gson.Gson().fromJson(json, type)
            profiles.clear()
            profiles.addAll(loaded.take(3))
        }
        if (profiles.isEmpty()) showNoDogDialog() else displayDogCards()
    }

    private fun showNoDogDialog() {
        binding.layoutDogCards.visibility = View.GONE
        binding.tvNoDogs.visibility = View.VISIBLE
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        AlertDialog.Builder(this)
            .setTitle("Keine Hunde angelegt")
            .setMessage("Bitte lege zuerst mindestens einen Hund in den Stammdaten an.")
            .setPositiveButton("Zu Stammdaten") { _, _ ->
                startActivity(Intent(this, DogProfileActivity::class.java))
                finish()
            }
            .setNegativeButton("Zurück") { _, _ -> finish() }
            .setCancelable(false)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
            }
    }

    private fun displayDogCards() {
        binding.layoutDogCards.visibility = View.VISIBLE
        binding.tvNoDogs.visibility = View.GONE

        listOf(
            Pair(binding.dogCard1, 0),
            Pair(binding.dogCard2, 1),
            Pair(binding.dogCard3, 2)
        ).forEach { (card, idx) ->
            if (idx < profiles.size) {
                setupDogCard(card, profiles[idx])
                card.root.visibility = View.VISIBLE
            } else {
                card.root.visibility = View.GONE
            }
        }
    }

    private fun setupDogCard(cardBinding: ItemDogCardBinding, profile: DogProfile) {
        cardBinding.tvDogName.text = profile.name
        cardBinding.tvDogBreed.text = "${profile.breed}, ${profile.gender}"

        if (profile.photoUri.isNotEmpty()) {
            cardBinding.ivDogPhoto.setImageURI(Uri.parse(profile.photoUri))
        } else {
            cardBinding.ivDogPhoto.setImageResource(R.drawable.ic_dog_placeholder)
        }

        // Nasenarbeit-Statistik
        val fortschritt = NasenarbeitManager.loadFortschrittForDog(this, profile.id)
        val sessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)
        val lastDate = if (fortschritt.letzteSession > 0) {
            SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
                .format(Date(fortschritt.letzteSession))
        } else "Noch kein Training"

        cardBinding.tvDogInfo.text = buildString {
            append("Nasenarbeit:\n")
            append("  🐾 Verlorensuche: ${fortschritt.sessionsVerlorensuche} Sessions")
            append(" (${fortschritt.levelVerlorensuche.displayName})\n")
            append("  🧑 Personensuche: ${fortschritt.sessionsPersonensuche} Sessions")
            append(" (${fortschritt.levelPersonensuche.displayName})\n")
            append("  🔍 Freisuche: ${fortschritt.sessionsFreisuche} Sessions")
            append(" (${fortschritt.levelFreisuche.displayName})\n")
            append("  Gesamt: ${fortschritt.gesamtSessions} | Letzte Einheit: $lastDate")
        }

        // ✅ layoutDogInfo fängt den Klick ab (wie TrainingDiaryActivity)
        cardBinding.layoutDogInfo.setOnClickListener { openNasenarbeitForDog(profile) }

        // ✅ PDF Export
        cardBinding.btnExportPdf.setOnClickListener { exportPdf(profile) }

        // ✅ CSV Export
        cardBinding.btnExportCsv.setOnClickListener { exportCsv(profile) }

        // ✅ Daten löschen
        cardBinding.btnDeleteTrainingData.setOnClickListener { confirmDeleteData(profile) }
    }

    private fun exportPdf(profile: DogProfile) {
        val sessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)
        if (sessions.isEmpty()) {
            android.widget.Toast.makeText(this, "Keine Sessions vorhanden", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val fortschritt = NasenarbeitManager.loadFortschrittForDog(this, profile.id)
        try {
            val file = NasenarbeitPDFGenerator.generateGesamtPDF(this, sessions, fortschritt, profile)
            android.widget.Toast.makeText(this, "PDF gespeichert: ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Fehler: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportCsv(profile: DogProfile) {
        val sessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)
        if (sessions.isEmpty()) {
            android.widget.Toast.makeText(this, "Keine Sessions vorhanden", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val file = NasenarbeitCSVGenerator.exportSessions(this, profile, sessions)
        if (file != null) {
            android.widget.Toast.makeText(this, "CSV gespeichert: ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
        } else {
            android.widget.Toast.makeText(this, "Fehler beim Export", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteData(profile: DogProfile) {
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colorNormal = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("⚠️ Nasenarbeit-Daten löschen?")
            .setMessage("Möchtest du ALLE Nasenarbeit-Trainingsdaten von ${profile.name} löschen?\n\n" +
                    "Diese Aktion kann nicht rückgängig gemacht werden.")
            .setPositiveButton("LÖSCHEN") { _, _ ->
                val allSessions = NasenarbeitManager.loadSessions(this)
                val filtered = allSessions.filter { it.dogId != profile.id }
                NasenarbeitManager.saveSessions(this, filtered)

                val allFortschritt = NasenarbeitManager.loadFortschritt(this)
                val filteredFort = allFortschritt.filter { it.dogId != profile.id }
                val prefs = getSharedPreferences("nasenarbeit_data", MODE_PRIVATE)
                prefs.edit().putString("fortschritt", com.google.gson.Gson().toJson(filteredFort)).apply()

                android.widget.Toast.makeText(this, "Daten gelöscht", android.widget.Toast.LENGTH_SHORT).show()
                loadProfiles()
            }
            .setNegativeButton("Abbrechen", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(colorNormal)
            }
    }

    private fun openNasenarbeitForDog(profile: DogProfile) {
        val intent = Intent(this, NasenarbeitHundActivity::class.java)
        intent.putExtra("profile", profile)
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onResume() {
        super.onResume()
        loadProfiles()
    }
}