package com.hundewildnis.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ActivitySpielSpassHistoryBinding
import com.hundewildnis.app.databinding.ItemSpielSpassSessionBinding

/**
 * Statistik-Übersicht und Verlaufsliste für einen einzelnen Hund.
 */
class SpielSpassHistoryActivity : SecureBaseActivity() {

    private lateinit var binding: ActivitySpielSpassHistoryBinding
    private lateinit var profile: DogProfile
    private val sessions = mutableListOf<SpielSession>()
    private lateinit var adapter: SpielSpassSessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpielSpassHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")!!

        setupUI()
        setupRecyclerView()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = profile.name
    }

    private fun setupRecyclerView() {
        adapter = SpielSpassSessionAdapter(sessions) { session -> confirmDeleteSession(session) }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun loadData() {
        sessions.clear()
        sessions.addAll(SpielSpassManager.getSessionsForDog(this, profile.id))
        adapter.notifyDataSetChanged()

        val stats = SpielSpassManager.getStatistikForDog(this, profile.id)
        binding.tvTotalSessions.text = "${stats.gesamtSessions}"

        binding.tvByOrt.text = stats.sessionsProOrt.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { (ortName, count) ->
                val ort = try { SpielOrt.valueOf(ortName) } catch (e: Exception) { null }
                "${ort?.let { "${it.icon} ${it.displayName(this)}" } ?: ortName}: $count"
            }

        binding.tvByFokus.text = stats.sessionsProFokus.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { (fokusName, count) ->
                val fokus = try { SpielFokus.valueOf(fokusName) } catch (e: Exception) { null }
                "${fokus?.let { "${it.icon} ${it.displayName(this)}" } ?: fokusName}: $count"
            }

        binding.tvTopActivities.text = stats.topAktivitaeten.joinToString("\n") { (id, count) ->
            val name = SpielAktivitaetCatalog.findById(id)?.name(this) ?: id
            "$name: $count×"
        }

        if (stats.durchschnittsBewertung > 0) {
            binding.tvAvgRating.visibility = View.VISIBLE
            binding.tvAvgRating.text = getString(R.string.spiel_spass_stats_avg_rating, stats.durchschnittsBewertung)
        } else {
            binding.tvAvgRating.visibility = View.GONE
        }

        binding.layoutStatsContent.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDeleteSession(session: SpielSession) {

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.spiel_spass_stats_delete_session_title))
            .setMessage(getString(R.string.spiel_spass_stats_delete_session_message))
            .setPositiveButton(getString(R.string.start_delete)) { _, _ ->
                SpielSpassManager.deleteSession(this, session.id)
                loadData()
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
        loadData()
    }
}

// ════════════════════════════════════════════════════════════════
// ADAPTER: VERLAUFSLISTE
// ════════════════════════════════════════════════════════════════

class SpielSpassSessionAdapter(
    private val sessions: List<SpielSession>,
    private val onLongClickDelete: (SpielSession) -> Unit
) : RecyclerView.Adapter<SpielSpassSessionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemSpielSpassSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSpielSpassSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        val context = holder.itemView.context
        val aktivitaet = SpielAktivitaetCatalog.findById(session.aktivitaetId)

        holder.binding.tvSessionName.text = aktivitaet?.name(context) ?: session.aktivitaetId
        holder.binding.tvSessionDate.text = session.getFormattedDate()
        holder.binding.tvSessionOrt.text = "${session.ort.icon} ${session.ort.displayName(context)}"
        holder.binding.tvSessionRating.text = session.getBewertungIcon()

        if (session.notizen.isNotEmpty()) {
            holder.binding.tvSessionNotes.visibility = View.VISIBLE
            holder.binding.tvSessionNotes.text = session.notizen
        } else {
            holder.binding.tvSessionNotes.visibility = View.GONE
        }

        holder.binding.root.setOnLongClickListener {
            onLongClickDelete(session)
            true
        }
    }

    override fun getItemCount() = sessions.size
}
