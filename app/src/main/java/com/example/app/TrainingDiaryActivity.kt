package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
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
        supportActionBar?.title = "🦮🗓 Trainingstagebuch"
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
            .setTitle("Keine Hunde angelegt")
            .setMessage("Bitte legen Sie zuerst mindestens einen Hund in den Stammdaten an, um das Trainingstagebuch nutzen zu können.")
            .setPositiveButton("Zu Stammdaten") { _, _ ->
                val intent = Intent(this, DogProfileActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Zurück") { _, _ ->
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

        // Karte 1
        if (profiles.size >= 1) {
            setupDogCard(binding.dogCard1, profiles[0])
            binding.dogCard1.root.visibility = View.VISIBLE
        } else {
            binding.dogCard1.root.visibility = View.GONE
        }

        // Karte 2
        if (profiles.size >= 2) {
            setupDogCard(binding.dogCard2, profiles[1])
            binding.dogCard2.root.visibility = View.VISIBLE
        } else {
            binding.dogCard2.root.visibility = View.GONE
        }

        // Karte 3
        if (profiles.size >= 3) {
            setupDogCard(binding.dogCard3, profiles[2])
            binding.dogCard3.root.visibility = View.VISIBLE
        } else {
            binding.dogCard3.root.visibility = View.GONE
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

        // Zeige Mini-Statistik
        val manager = TrainingManager(this)
        val weeklyStats = manager.getWeeklyStats(profile.id)

        cardBinding.tvDogInfo.text = buildString {
            append("Diese Woche:\n")
            append("${weeklyStats.totalSessions} Trainingseinheiten\n")
            append("${weeklyStats.totalMinutes} Minuten")
        }

        cardBinding.root.setOnClickListener {
            openCategoryList(profile)
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
        loadProfiles()  // Refresh wenn man zurückkommt
    }
}
