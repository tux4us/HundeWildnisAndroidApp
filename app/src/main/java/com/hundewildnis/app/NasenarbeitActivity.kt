package com.hundewildnis.app

import android.content.Intent
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
        supportActionBar?.title = getString(R.string.nasenarbeit_toolbar_title)
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

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nasenarbeit_info_dialog_title))
            .setMessage(getString(R.string.nasenarbeit_info_dialog_message))
            .setPositiveButton(getString(R.string.start_understood), null)
            .create().apply {
                show()
            }
    }

    private fun exportPdf(profile: DogProfile) {
        val sessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)
        if (sessions.isEmpty()) {
            Toast.makeText(this, getString(R.string.nasenarbeit_no_sessions_error), Toast.LENGTH_SHORT).show()
            return
        }
        val fortschritt = NasenarbeitManager.loadFortschrittForDog(this, profile.id)
        try {
            // Speichern mit "shareAfterExport" Option (ähnlich wie in anderen Activities)
            val file = NasenarbeitPDFGenerator.generateGesamtPDF(this, sessions, fortschritt, profile, saveToPublic = true)
            if (file != null) {
                Toast.makeText(this, getString(R.string.nasenarbeit_export_pdf_success), Toast.LENGTH_LONG).show()
                shareFile(file, "application/pdf")
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.nasenarbeit_generic_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportCsv(profile: DogProfile) {
        val sessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)
        if (sessions.isEmpty()) {
            Toast.makeText(this, getString(R.string.nasenarbeit_no_sessions_error), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = NasenarbeitCSVGenerator.exportSessions(this, profile, sessions)
            if (file != null) {
                Toast.makeText(this, getString(R.string.nasenarbeit_export_csv_success), Toast.LENGTH_LONG).show()
                shareFile(file, "text/csv")
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.nasenarbeit_generic_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
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
            .setTitle(getString(R.string.nasenarbeit_delete_data_title))
            .setMessage(getString(R.string.nasenarbeit_delete_data_message, profile.name))
            .setPositiveButton(getString(R.string.nasenarbeit_btn_delete_all)) { _, _ ->
                NasenarbeitManager.deleteAllDataForDog(this, profile.id)
                Toast.makeText(this, getString(R.string.nasenarbeit_delete_data_success), Toast.LENGTH_SHORT).show()
                loadProfiles()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().apply {
                show()
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