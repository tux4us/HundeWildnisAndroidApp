package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.button.MaterialButton
import com.hundewildnis.app.databinding.ActivityIntervalTimerHistoryBinding
import java.io.File

// ════════════════════════════════════════════════════════════════
// INTERVALL-TIMER HISTORY – Verlaufsansicht
// Datei: IntervalTimerHistoryActivity.kt
// ════════════════════════════════════════════════════════════════

class IntervalTimerHistoryActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityIntervalTimerHistoryBinding
    private val sessions = mutableListOf<IntervalTimerSession>()
    private lateinit var adapter: SessionAdapter

    private var filterDogId: String? = null
    private var filterDogName: String? = null

    companion object {
        const val EXTRA_DOG_ID   = "dog_id"
        const val EXTRA_DOG_NAME = "dog_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntervalTimerHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        filterDogId   = intent.getStringExtra(EXTRA_DOG_ID)
        filterDogName = intent.getStringExtra(EXTRA_DOG_NAME)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        loadSessions()
    }

    override fun onResume() {
        super.onResume()
        loadSessions()   // Neu laden nach Rückkehr
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ════════════════════════════════════════════════════════════
    // SETUP
    // ════════════════════════════════════════════════════════════

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = if (filterDogName != null) "⏱️ Timer – ${filterDogName}" else "⏱️ Timer-Verlauf"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupRecyclerView() {
        adapter = SessionAdapter(sessions,
            onDelete = { session -> confirmDelete(session) },
            onShare  = { session -> shareSession(session) }
        )
        binding.recyclerSessions.layoutManager = LinearLayoutManager(this)
        binding.recyclerSessions.adapter = adapter
    }

    private fun setupButtons() {
        val colors = StartGroupColors.forGroup(this, 5) // Violett
        binding.gridHistoryActions.layoutManager = GridLayoutManager(this, 2)
        binding.gridHistoryActions.adapter = StartTileAdapter(
            tiles = listOf(
                StartTile("history_csv", R.drawable.ic_notes, "CSV"),
                StartTile("history_delete", R.drawable.ic_remove, "Löschen", isDanger = true)
            ),
            chipColorRes = colors.chipColorRes,
            accentColorRes = colors.accentColorRes,
            onTileClick = { id ->
                when(id) {
                    "history_csv" -> exportCsv()
                    "history_delete" -> confirmDeleteAll()
                }
            }
        )
    }

    // ════════════════════════════════════════════════════════════
    // DATEN
    // ════════════════════════════════════════════════════════════

    private fun loadSessions() {
        val all = IntervalTimerManager.getAllSessions(this)
        val filtered = if (filterDogId != null)
            all.filter { it.dogId == filterDogId }
        else
            all

        sessions.clear()
        sessions.addAll(filtered)
        adapter.notifyDataSetChanged()

        binding.tvEmpty.visibility =
            if (sessions.isEmpty()) View.VISIBLE else View.GONE
        binding.tvCount.text = "${sessions.size} Session(s)"
    }

    private fun confirmDelete(session: IntervalTimerSession) {
        AlertDialog.Builder(this)
            .setTitle("Session löschen?")
            .setMessage("${session.formattedDate()}\n${session.summaryLine()}")
            .setPositiveButton("Löschen") { _, _ ->
                IntervalTimerManager.deleteSession(this, session.id)
                loadSessions()
                Toast.makeText(this, "Gelöscht.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(android.graphics.Color.parseColor("#C0392B"))
            }
    }

    private fun confirmDeleteAll() {
        if (sessions.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Alle Sessions löschen?")
            .setMessage("${sessions.size} Einträge werden unwiderruflich gelöscht.")
            .setPositiveButton("Alle löschen") { _, _ ->
                if (filterDogId != null) {
                    IntervalTimerManager.deleteAllForDog(this, filterDogId!!)
                } else {
                    sessions.forEach { IntervalTimerManager.deleteSession(this, it.id) }
                }
                loadSessions()
                Toast.makeText(this, "Alle Sessions gelöscht.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(android.graphics.Color.parseColor("#C0392B"))
            }
    }

    private fun shareSession(session: IntervalTimerSession) {
        val text = buildString {
            appendLine("⏱️ Intervall-Timer – ${session.formattedDate()}")
            if (session.dogName.isNotEmpty()) appendLine("🐕 ${session.dogName}")
            appendLine(session.summaryLine())
            appendLine("💪 Effektive Aktivzeit: ${session.effectiveActiveMinutes} Min")
            if (session.notes.isNotEmpty()) appendLine("📝 ${session.notes}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Session teilen"))
    }

    private fun exportCsv() {
        if (sessions.isEmpty()) {
            Toast.makeText(this, "Keine Sessions vorhanden.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val csv  = IntervalTimerManager.toCsv(sessions)
            val name = "IntervalTimer_Export_${System.currentTimeMillis()}.csv"
            val file = File(cacheDir, name)
            file.writeText(csv)
            val uri  = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "CSV exportieren"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ════════════════════════════════════════════════════════════
    // ADAPTER
    // ════════════════════════════════════════════════════════════

    private class SessionAdapter(
        private val items: List<IntervalTimerSession>,
        private val onDelete: (IntervalTimerSession) -> Unit,
        private val onShare: (IntervalTimerSession) -> Unit
    ) : RecyclerView.Adapter<SessionAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate:    TextView      = view.findViewById(R.id.tvSessionDate)
            val tvDog:     TextView      = view.findViewById(R.id.tvSessionDog)
            val tvSummary: TextView      = view.findViewById(R.id.tvSessionSummary)
            val tvActive:  TextView      = view.findViewById(R.id.tvSessionActive)
            val tvStatus:  TextView      = view.findViewById(R.id.tvSessionStatus)
            val btnDelete: MaterialButton = view.findViewById(R.id.btnSessionDelete)
            val btnShare:  MaterialButton = view.findViewById(R.id.btnSessionShare)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_interval_session, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            holder.tvDate.text    = s.formattedDate()
            holder.tvDog.text     = if (s.dogName.isNotEmpty()) "🐕 ${s.dogName}" else ""
            holder.tvDog.visibility = if (s.dogName.isNotEmpty()) View.VISIBLE else View.GONE
            holder.tvSummary.text = "${s.completedRounds}/${s.config.rounds} Runden  ·  " +
                                    "${s.config.activeDurationSeconds}s aktiv / ${s.config.restDurationSeconds}s Pause"
            holder.tvActive.text  = "💪 ${s.effectiveActiveMinutes} Min aktive Übungszeit"
            holder.tvStatus.text  = if (s.abortedEarly) "⏹️ Abgebrochen" else "✅ Abgeschlossen"
            holder.tvStatus.setTextColor(
                if (s.abortedEarly)
                    android.graphics.Color.parseColor("#E57373")
                else
                    android.graphics.Color.parseColor("#81C784")
            )
            holder.btnDelete.setOnClickListener { onDelete(s) }
            holder.btnShare.setOnClickListener  { onShare(s)  }
        }
    }
}
