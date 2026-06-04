package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.hundewildnis.app.databinding.ActivityNasenarbeitBinding
import java.text.SimpleDateFormat
import java.util.*

class NasenarbeitActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityNasenarbeitBinding
    private val profiles = mutableListOf<DogProfile>()
    private lateinit var adapter: NasenarbeitDogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNasenarbeitBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUI()
        setupRecyclerView()
        loadProfiles()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🐾 Nasenarbeit"
    }

    private fun setupRecyclerView() {
        adapter = NasenarbeitDogAdapter(
            context = this,
            profiles = profiles,
            onDogClick = { profile -> openNasenarbeitForDog(profile) },
            onExportPdf = { profile -> exportPdf(profile) },
            onExportCsv = { profile -> exportCsv(profile) },
            onDeleteData = { profile -> confirmDeleteData(profile) }
        )
        binding.rvDogs.layoutManager = LinearLayoutManager(this)
        binding.rvDogs.adapter = adapter
    }

    private fun loadProfiles() {
        val loaded = DogProfileManager.getAllProfiles(this)
        profiles.clear()
        profiles.addAll(loaded)
        adapter.notifyDataSetChanged()

        if (profiles.isEmpty()) {
            binding.rvDogs.visibility = View.GONE
            binding.tvNoDogs.visibility = View.VISIBLE
        } else {
            binding.rvDogs.visibility = View.VISIBLE
            binding.tvNoDogs.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_nasenarbeit_info, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_info -> { showInfoDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showInfoDialog() {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colorNormal = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("Info: Nasenarbeit")
            .setMessage(
                "Nasenarbeit ist eine der natürlichsten Auslastungsformen für Hunde.\n\n" +
                "🐾 Verlorensuche:\nDer Hund folgt einer Geruchsspur, die durch Bodenverletzung und Eigengeruch entsteht. Er sucht nach 'verlorenen' Gegenständen des Halters.\n\n" +
                "🧑 Personensuche:\nDer Hund sucht eine bestimmte Zielperson anhand ihres Individualgeruchs. Dies erfordert eine Hilfsperson.\n\n" +
                "🔍 Freisuche:\nDer Hund sucht in einem zugewiesenen Gebiet frei nach Gegenständen oder Personen.\n\n" +
                "📊 Das Scoring:\nDie App berechnet Trainingsempfehlungen basierend auf deinem Fortschritt, dem Wetter (Temperatur/Feuchtigkeit) und der Bodenbeschaffenheit. Bei hoher Hitze oder starkem Wind wird die Schwierigkeit automatisch angepasst."
            )
            .setPositiveButton("Verstanden", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(colorNormal)
            }
    }

    private fun exportPdf(profile: DogProfile) {
        val sessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)
        if (sessions.isEmpty()) {
            Toast.makeText(this, "Keine Sessions vorhanden", Toast.LENGTH_SHORT).show()
            return
        }
        val fortschritt = NasenarbeitManager.loadFortschrittForDog(this, profile.id)
        try {
            // Speichern mit "shareAfterExport" Option (ähnlich wie in anderen Activities)
            val file = NasenarbeitPDFGenerator.generateGesamtPDF(this, sessions, fortschritt, profile, saveToPublic = true)
            if (file != null) {
                Toast.makeText(this, "✅ PDF gespeichert in Documents/HundeWildnis", Toast.LENGTH_LONG).show()
                shareFile(file, "application/pdf")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportCsv(profile: DogProfile) {
        val sessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)
        if (sessions.isEmpty()) {
            Toast.makeText(this, "Keine Sessions vorhanden", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = NasenarbeitCSVGenerator.exportSessions(this, profile, sessions)
            if (file != null) {
                Toast.makeText(this, "✅ CSV gespeichert in Documents/HundeWildnis", Toast.LENGTH_LONG).show()
                shareFile(file, "text/csv")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: java.io.File, mimeType: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Teilen..."))
    }

    private fun confirmDeleteData(profile: DogProfile) {
        val isDark = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colorNormal = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("⚠️ Trainingsdaten löschen?")
            .setMessage("Möchtest du ALLE Nasenarbeit-Trainingsdaten von ${profile.name} löschen?\n\nDie Stammdaten des Hundes bleiben erhalten.")
            .setPositiveButton("LÖSCHEN") { _, _ ->
                NasenarbeitManager.deleteAllDataForDog(this, profile.id)
                Toast.makeText(this, "Trainingsdaten gelöscht", Toast.LENGTH_SHORT).show()
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