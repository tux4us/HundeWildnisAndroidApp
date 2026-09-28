package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.hundewildnis.app.databinding.ActivityTrainingDiaryBinding
import com.hundewildnis.app.databinding.ItemDogCardBinding

class TrainingDiaryActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityTrainingDiaryBinding
    private val profiles = mutableListOf<DogProfile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingDiaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadProfiles()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.diary_toolbar_title)
    }

    private fun loadProfiles() {
        val prefs = getSharedPreferences("dog_profiles", MODE_PRIVATE)
        val json = prefs.getString("profiles", null)

        if (json != null) {
            val type = object : com.google.gson.reflect.TypeToken<List<DogProfile>>() {}.type
            val loadedProfiles: List<DogProfile> = com.google.gson.Gson().fromJson(json, type)
            profiles.clear()
            profiles.addAll(loadedProfiles.take(3))
        }

        if (profiles.isEmpty()) {
            showNoDogDialog()
        } else {
            displayDogCards()
        }
    }

    private fun showNoDogDialog() {
        binding.layoutDogCards.visibility = View.GONE
        binding.tvNoDogs.visibility = View.VISIBLE

        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.diary_no_dog_dialog_title))
            .setMessage(getString(R.string.diary_no_dog_dialog_message))
            .setPositiveButton(getString(R.string.diary_no_dog_dialog_btn_master_data)) { _, _ ->
                val intent = Intent(this, DogProfileActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
            }
    }

    private fun displayDogCards() {
        binding.layoutDogCards.visibility = View.VISIBLE
        binding.tvNoDogs.visibility = View.GONE

        if (profiles.size >= 1) {
            setupDogCard(binding.dogCard1, profiles[0])
            binding.dogCard1.root.visibility = View.VISIBLE
        } else {
            binding.dogCard1.root.visibility = View.GONE
        }

        if (profiles.size >= 2) {
            setupDogCard(binding.dogCard2, profiles[1])
            binding.dogCard2.root.visibility = View.VISIBLE
        } else {
            binding.dogCard2.root.visibility = View.GONE
        }

        if (profiles.size >= 3) {
            setupDogCard(binding.dogCard3, profiles[2])
            binding.dogCard3.root.visibility = View.VISIBLE
        } else {
            binding.dogCard3.root.visibility = View.GONE
        }
    }

    private fun setupDogCard(cardBinding: ItemDogCardBinding, profile: DogProfile) {
        cardBinding.tvDogName.text = profile.name
        cardBinding.tvDogBreed.text = "${profile.breed}, ${GenderCode.displayName(this, profile.gender)}"

        if (profile.photoUri.isNotEmpty()) {
            cardBinding.ivDogPhoto.setImageURI(Uri.parse(profile.photoUri))
        } else {
            cardBinding.ivDogPhoto.setImageResource(R.drawable.ic_dog_placeholder)
        }

        // Statistik
        val manager = TrainingManager(this)
        val weeklyStats = manager.getWeeklyStats(profile.id)

        cardBinding.tvDogInfo.text = getString(R.string.diary_stats_this_week, weeklyStats.totalSessions, weeklyStats.totalMinutes)

        // ✅ FIX: Button-Farben für Dark Mode
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isDark) {
            cardBinding.btnExportPdf.setTextColor(Color.WHITE)
            cardBinding.btnExportCsv.setTextColor(Color.WHITE)
            // btnDeleteTrainingData bleibt rot (ist schon im XML gesetzt)
        }

        // ✅ Click auf Hunde-Info → Kategorien öffnen
        cardBinding.layoutDogInfo.setOnClickListener {
            openCategoryList(profile)
        }

        // ✅ PDF Export Button
        cardBinding.btnExportPdf.setOnClickListener {
            exportDogDiaryPdf(profile)
        }

        // ✅ CSV Export Button
        cardBinding.btnExportCsv.setOnClickListener {
            exportDogDiaryCsv(profile)
        }

        // ✅ Delete Button
        cardBinding.btnDeleteTrainingData.setOnClickListener {
            showDeleteAllTrainingDataDialog(profile)
        }
    }

    // ════════════════════════════════════════════════════════════
    // EXPORT FUNKTIONEN
    // ════════════════════════════════════════════════════════════

    private fun exportDogDiaryPdf(profile: DogProfile) {
        val file = TrainingExportManager.exportCompleteDiary(
            context = this,
            dog = profile,
            format = ExportFormat.PDF
        )

        if (file != null) {
            Toast.makeText(this, getString(R.string.diary_export_pdf_success), Toast.LENGTH_LONG).show()
            TrainingExportManager.shareFile(this, file)
        } else {
            Toast.makeText(this, getString(R.string.diary_export_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportDogDiaryCsv(profile: DogProfile) {
        val file = TrainingExportManager.exportCompleteDiary(
            context = this,
            dog = profile,
            format = ExportFormat.CSV
        )

        if (file != null) {
            Toast.makeText(this, getString(R.string.diary_export_csv_success), Toast.LENGTH_LONG).show()
            TrainingExportManager.shareFile(this, file)
        } else {
            Toast.makeText(this, getString(R.string.diary_export_error), Toast.LENGTH_SHORT).show()
        }
    }

    // ════════════════════════════════════════════════════════════
    // DELETE FUNKTIONEN
    // ════════════════════════════════════════════════════════════

    private fun showDeleteAllTrainingDataDialog(profile: DogProfile) {
        // ✅ DARK MODE FIX
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colorNormal = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.diary_delete_dialog_title))
            .setMessage(getString(R.string.diary_delete_dialog_message, profile.name))
            .setPositiveButton(getString(R.string.diary_delete_btn_all)) { _, _ ->
                deleteAllTrainingData(profile)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)  // Rot bleibt immer Rot
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(colorNormal)  // ✅ FIX
            }
    }

    private fun deleteAllTrainingData(profile: DogProfile) {
        val manager = TrainingManager(this)
        val success = manager.deleteDogTrainingData(this, profile.id)

        if (success) {
            Toast.makeText(this, getString(R.string.diary_delete_success, profile.name), Toast.LENGTH_LONG).show()
            loadProfiles()
        } else {
            Toast.makeText(this, getString(R.string.diary_delete_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCategoryList(profile: DogProfile) {
        val intent = Intent(this, CategoryListActivity::class.java)
        intent.putExtra("profile", profile)
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadProfiles()
    }
}