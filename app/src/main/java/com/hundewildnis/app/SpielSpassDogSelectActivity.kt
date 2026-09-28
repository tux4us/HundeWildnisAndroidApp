package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ActivitySpielSpassDogSelectBinding
import com.hundewildnis.app.databinding.ItemSpielSpassDogBinding

/**
 * Erste Activity des "Spiel & Spaß"-Features.
 * Zeigt vorhandene Hundeprofile; ohne Profil wird zunächst die Anlage
 * eines Hundes gefordert (analog NasenarbeitActivity / TrainingDiaryActivity).
 */
class SpielSpassDogSelectActivity : SecureBaseActivity() {

    private lateinit var binding: ActivitySpielSpassDogSelectBinding
    private val profiles = mutableListOf<DogProfile>()
    private lateinit var adapter: SpielSpassDogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpielSpassDogSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.spiel_spass_toolbar_title)
        binding.tvSubtitle.text = getString(R.string.spiel_spass_dog_select_subtitle)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_spiel_spass_stats, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_spiel_spass_stats -> {
                startActivity(Intent(this, SpielSpassStatsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        adapter = SpielSpassDogAdapter(
            context = this,
            profiles = profiles,
            onDogClick = { profile -> openFilter(profile) }
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

    private fun openFilter(profile: DogProfile) {
        val intent = Intent(this, SpielSpassFilterActivity::class.java)
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

// ════════════════════════════════════════════════════════════════
// ADAPTER FÜR HUNDEAUSWAHL
// ════════════════════════════════════════════════════════════════

class SpielSpassDogAdapter(
    private val context: android.content.Context,
    private val profiles: List<DogProfile>,
    private val onDogClick: (DogProfile) -> Unit
) : RecyclerView.Adapter<SpielSpassDogAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemSpielSpassDogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSpielSpassDogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = profiles[position]
        val binding = holder.binding

        binding.tvDogName.text = profile.name
        binding.tvDogBreed.text = profile.breed

        if (profile.photoUri.isNotEmpty()) {
            binding.ivDogPhoto.setImageURI(Uri.parse(profile.photoUri))
        } else {
            binding.ivDogPhoto.setImageResource(R.drawable.ic_dog_placeholder)
        }

        val stats = SpielSpassManager.getStatistikForDog(context, profile.id)
        binding.tvDogStats.text = context.getString(R.string.spiel_spass_stats_total_sessions) + ": ${stats.gesamtSessions}"

        // In der Hundeauswahl werden nur Foto/Name/Klick benötigt – die
        // Export-/Löschen-Buttons des gemeinsamen item_spiel_spass_dog.xml
        // sind hier ohne Funktion und werden ausgeblendet.
        binding.layoutButtons.visibility = View.GONE

        binding.root.setOnClickListener { onDogClick(profile) }
    }

    override fun getItemCount() = profiles.size
}
