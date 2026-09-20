package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import com.hundewildnis.app.databinding.ActivityVitalListBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VitalListActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityVitalListBinding
    private lateinit var adapter: VitalListAdapter

    private var dogId: String = ""
    private var dogName: String = ""
    private var shoulderHeightCm: Int? = null
    private val records = mutableListOf<VitalRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVitalListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dogId = intent.getStringExtra("dogId") ?: ""
        dogName = intent.getStringExtra("dogName") ?: getString(R.string.dog_view_title_fallback)

        // Schulterhöhe für Puls-Bewertung laden
        val profile = DogProfileManager.getProfile(this, dogId)
        shoulderHeightCm = profile?.shoulderHeight?.trim()?.toIntOrNull()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.vital_list_toolbar_title_format, dogName)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupRecyclerView()
        setupButtons()
        loadRecords()
    }

    // ════════════════════════════════════════════════════════════════
    // SETUP
    // ════════════════════════════════════════════════════════════════

    private fun setupRecyclerView() {
        adapter = VitalListAdapter(
            context = this,
            records = records,
            shoulderHeightCm = shoulderHeightCm,
            onEdit = { record -> openDetail(record) },
            onShare = { record -> shareRecord(record) },
            onDelete = { record -> confirmDelete(record) }
        )
        binding.rvVitalRecords.layoutManager = LinearLayoutManager(this)
        binding.rvVitalRecords.adapter = adapter
    }

    private fun setupButtons() {
        val actions = listOf(
            StartTile("vital_csv", R.drawable.ic_notes, getString(R.string.vital_list_tile_csv_export)),
            StartTile("vital_share", R.drawable.ic_map_pin_share, getString(R.string.vital_list_tile_share_list))
        )
        val colors = StartGroupColors.forGroup(this, 3) // Ocker/Braun
        binding.gridVitalActions.layoutManager = GridLayoutManager(this, 2)
        binding.gridVitalActions.adapter = StartTileAdapter(actions, colors.chipColorRes, colors.accentColorRes) { id ->
            if (id == "vital_csv") exportCsv() else shareAll()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // DATEN LADEN
    // ════════════════════════════════════════════════════════════════

    private fun loadRecords() {
        val loaded = VitalManager.getForDog(this, dogId)
        records.clear()
        records.addAll(loaded)
        adapter.notifyDataSetChanged()

        if (records.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvVitalRecords.visibility = View.GONE
            binding.cardStats.visibility = View.GONE
            binding.gridVitalActions.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvVitalRecords.visibility = View.VISIBLE
            binding.cardStats.visibility = View.VISIBLE
            binding.gridVitalActions.visibility = View.VISIBLE
            updateStats()
        }
    }

    private fun updateStats() {
        if (records.isEmpty()) return

        val latest = records.first()
        val count = records.size
        val avgPulse = records.map { it.pulse }.average().toInt()

        val sb = StringBuilder()
        sb.appendLine(resources.getQuantityString(R.plurals.vital_list_stats_total, count, count))
        sb.appendLine(getString(R.string.vital_list_stats_last_measurement, latest.formattedDate()))
        sb.appendLine(getString(R.string.vital_list_stats_last_pulse, latest.pulse))
        sb.append(getString(R.string.vital_list_stats_avg_pulse, avgPulse))

        latest.respiration?.let { sb.appendLine().append(getString(R.string.vital_list_stats_last_respiration, it)) }
        latest.temperature?.let { sb.appendLine().append(getString(R.string.vital_list_stats_last_temperature, "%.1f".format(it))) }

        binding.tvStatsContent.text = sb.toString()
    }

    // ════════════════════════════════════════════════════════════════
    // AKTIONEN
    // ════════════════════════════════════════════════════════════════

    private fun openDetail(record: VitalRecord) {
        val intent = Intent(this, VitalDetailActivity::class.java)
        intent.putExtra("recordId", record.id)
        intent.putExtra("dogId", dogId)
        intent.putExtra("dogName", dogName)
        startActivity(intent)
    }

    private fun shareRecord(record: VitalRecord) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.vital_detail_share_subject_format, record.dogName, record.formattedDate()))
            putExtra(Intent.EXTRA_TEXT, record.toShareText(this@VitalListActivity))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.vital_detail_share_chooser_title)))
    }

    private fun confirmDelete(record: VitalRecord) {
        val isDark = isNightMode()
        val color = if (isDark) Color.WHITE else Color.parseColor("#B00020")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.vital_list_delete_dialog_title))
            .setMessage(getString(R.string.vital_list_delete_dialog_message, record.formattedDate()))
            .setPositiveButton(getString(R.string.vital_list_delete_dialog_confirm)) { _, _ ->
                VitalManager.delete(this, record.id)
                adapter.removeRecord(record)
                Toast.makeText(this, getString(R.string.vital_list_toast_deleted), Toast.LENGTH_SHORT).show()
                if (records.isEmpty()) loadRecords()
                else updateStats()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#B00020"))
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(color)
            }
    }

    private fun exportCsv() {
        try {
            val csv = VitalManager.toCsvString(this, dogId)
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val fileName = "Vitalwerte_${dogName}_$dateStr.csv"
            val file = File(cacheDir, fileName)
            // UTF-8 BOM für Excel
            file.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            file.appendText(csv, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.vital_list_csv_share_subject, dogName, dateStr))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.vital_list_csv_chooser_title)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.vital_list_toast_export_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun shareAll() {
        if (records.isEmpty()) return
        val text = buildString {
            appendLine(getString(R.string.vital_list_share_all_header, dogName))
            appendLine(getString(R.string.vital_list_share_all_exported, SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())))
            appendLine(getString(R.string.vital_list_share_all_count, records.size))
            appendLine()
            records.forEach { record ->
                appendLine("──────────────────")
                appendLine(record.toShareText(this@VitalListActivity))
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.vital_list_share_all_subject, dogName))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.vital_list_share_all_chooser_title)))
    }

    // ════════════════════════════════════════════════════════════════
    // UTILS
    // ════════════════════════════════════════════════════════════════

    private fun isNightMode(): Boolean {
        return (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    override fun onResume() {
        super.onResume()
        // Nach Rückkehr aus Detail-Activity Daten neu laden
        loadRecords()
    }
}
