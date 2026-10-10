package com.hundewildnis.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ActivitySpielSpassStatsBinding
import com.hundewildnis.app.databinding.ItemSpielSpassDogBinding
import com.hundewildnis.app.databinding.ItemSpielSpassSessionBinding

/**
 * Verlauf & Statistik für "Spiel & Spaß". Erste Ebene: Hundeauswahl
 * (analog NasenarbeitActivity), zweite Ebene: Detail je Hund inklusive
 * Verlaufsliste und Export.
 */
class SpielSpassStatsActivity : SecureBaseActivity() {

    private lateinit var binding: ActivitySpielSpassStatsBinding
    private val profiles = mutableListOf<DogProfile>()
    private lateinit var adapter: SpielSpassStatsDogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpielSpassStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.spiel_spass_stats_toolbar_title)
    }

    private fun setupRecyclerView() {
        adapter = SpielSpassStatsDogAdapter(
            context = this,
            profiles = profiles,
            onDogClick = { profile -> openDogDetail(profile) },
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

    private fun openDogDetail(profile: DogProfile) {
        val intent = Intent(this, SpielSpassHistoryActivity::class.java)
        intent.putExtra("profile", profile)
        startActivity(intent)
    }

    private fun exportPdf(profile: DogProfile) {
        val sessions = SpielSpassManager.getSessionsForDog(this, profile.id)
        if (sessions.isEmpty()) {
            Toast.makeText(this, getString(R.string.spiel_spass_stats_no_sessions_error), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = SpielSpassPDFGenerator.generateGesamtPDF(this, sessions, profile, saveToPublic = true)
            if (file != null) {
                Toast.makeText(this, getString(R.string.spiel_spass_stats_export_pdf_success), Toast.LENGTH_LONG).show()
                shareFile(file, "application/pdf")
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.spiel_spass_stats_generic_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportCsv(profile: DogProfile) {
        val sessions = SpielSpassManager.getSessionsForDog(this, profile.id)
        if (sessions.isEmpty()) {
            Toast.makeText(this, getString(R.string.spiel_spass_stats_no_sessions_error), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = SpielSpassCSVGenerator.exportSessions(this, profile, sessions)
            if (file != null) {
                Toast.makeText(this, getString(R.string.spiel_spass_stats_export_csv_success), Toast.LENGTH_LONG).show()
                shareFile(file, "text/csv")
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.spiel_spass_stats_generic_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: java.io.File, mimeType: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.walking_speed_share_log)))
    }

    private fun confirmDeleteData(profile: DogProfile) {

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.spiel_spass_stats_delete_all_title))
            .setMessage(getString(R.string.spiel_spass_stats_delete_all_message, profile.name))
            .setPositiveButton(getString(R.string.start_delete)) { _, _ ->
                SpielSpassManager.deleteAllDataForDog(this, profile.id)
                loadProfiles()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().apply {
                show()
            }
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
// ADAPTER: HUNDE MIT STATISTIK-KURZFASSUNG
// ════════════════════════════════════════════════════════════════

class SpielSpassStatsDogAdapter(
    private val context: android.content.Context,
    private val profiles: List<DogProfile>,
    private val onDogClick: (DogProfile) -> Unit,
    private val onExportPdf: (DogProfile) -> Unit,
    private val onExportCsv: (DogProfile) -> Unit,
    private val onDeleteData: (DogProfile) -> Unit
) : RecyclerView.Adapter<SpielSpassStatsDogAdapter.ViewHolder>() {

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
        val lastDateText = if (stats.letzteSession > 0) {
            java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN).format(java.util.Date(stats.letzteSession))
        } else "–"

        binding.tvDogStats.text = buildString {
            append(context.getString(R.string.spiel_spass_stats_total_sessions)).append(": ${stats.gesamtSessions}\n")
            append(context.getString(R.string.spiel_spass_stats_last_session, lastDateText))
        }

        binding.root.setOnClickListener { onDogClick(profile) }
        binding.btnExportPdf.setOnClickListener { onExportPdf(profile) }
        binding.btnExportCsv.setOnClickListener { onExportCsv(profile) }
        binding.btnDeleteData.setOnClickListener { onDeleteData(profile) }
    }

    override fun getItemCount() = profiles.size
}
