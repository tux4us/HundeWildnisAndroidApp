package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ActivityTrainingDetailBinding
import com.hundewildnis.app.databinding.DialogSessionDetailBinding
import com.hundewildnis.app.databinding.ItemSessionHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class TrainingDetailActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityTrainingDetailBinding
    private lateinit var profile: DogProfile
    private lateinit var category: TrainingCategory
    private lateinit var manager: TrainingManager
    private lateinit var sessionAdapter: SessionHistoryAdapter

    private val sessions = mutableListOf<TrainingSession>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")!!
        val categoryId = intent.getIntExtra("category_id", 13)
        category = TrainingCategory.fromId(categoryId)!!

        manager = TrainingManager(this)

        setupUI()
        loadData()
        setupListeners()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = category.displayName(this)

        binding.tvDogName.text = profile.name
        binding.tvCategoryTitle.text = category.displayName(this)

        // RecyclerView mit Callbacks
        sessionAdapter = SessionHistoryAdapter(
            sessions = sessions,
            onViewClick = { session -> showSessionDetailDialog(session) },
            onDeleteClick = { session -> showDeleteSessionDialog(session) }
        )
        binding.rvSessionHistory.layoutManager = LinearLayoutManager(this)
        binding.rvSessionHistory.adapter = sessionAdapter
    }

    private fun setupListeners() {
        binding.btnAddSession.setOnClickListener {
            openAddSession()
        }
    }

    private fun loadData() {
        val progress = manager.getProgress(profile.id, category)
        displayProgress(progress)

        sessions.clear()
        sessions.addAll(manager.getSessionsForCategory(profile.id, category))
        sessionAdapter.notifyDataSetChanged()

        val dogCharacter = try {
            profile.toCharacter()
        } catch (e: Exception) {
            null
        }

        val recommendation = manager.getRecommendation(
            dogId = profile.id,
            category = category,
            dogCharacter = dogCharacter
        )
        displayRecommendation(recommendation)

        val analysis = manager.getProgressAnalysis(profile.id, category)
        displayAnalysis(analysis)
    }

    private fun displayProgress(progress: TrainingProgress) {
        binding.progressBar.progress = progress.progressPercentage
        binding.tvProgressPercent.text = "${progress.progressPercentage}%"
        binding.tvProgressLevel.text = progress.currentLevel?.displayName(this) ?: TrainingLevel.BEGINNER.displayName(this)

        binding.tvTotalSessions.text = getString(R.string.training_detail_total_sessions, progress.totalSessions)

        // ✅ Zeige Min oder Wiederholungen oder beides
        val effortText = if (progress.totalMinutes > 0 && progress.totalRepetitions > 0) {
            getString(R.string.training_detail_effort_both, progress.totalMinutes, progress.totalRepetitions)
        } else if (progress.totalRepetitions > 0) {
            getString(R.string.training_detail_effort_repetitions, progress.totalRepetitions)
        } else {
            getString(R.string.training_detail_effort_minutes, progress.totalMinutes)
        }
        binding.tvTotalMinutes.text = effortText

        binding.tvConsecutiveDays.text = getString(R.string.training_detail_consecutive_days, progress.consecutiveDays)

        val avgSuccess = String.format(Locale.GERMAN, "%.1f", progress.averageSuccess)
        binding.tvAverageSuccess.text = getString(R.string.training_detail_average_success, avgSuccess)

        val milestones = manager.getMilestones(progress.dogId, progress.category)
        if (milestones.isNotEmpty()) {
            val latest = milestones.last()
            binding.tvLatestMilestone.text = getString(R.string.training_detail_latest_milestone, latest.type.displayName(this))
        } else {
            binding.tvLatestMilestone.text = getString(R.string.training_detail_no_milestones)
        }
    }

    private fun displayRecommendation(recommendation: TrainingRecommendation) {
        binding.tvRecommendationTitle.text = "Trainerempfehlung"
        binding.tvRecommendation.text = recommendation.recommendation
        binding.tvRecommendationReason.text = recommendation.reasoning

        val color = when (recommendation.priority) {
            RecommendationPriority.HIGH -> android.graphics.Color.parseColor("#d32f2f")
            RecommendationPriority.MEDIUM -> android.graphics.Color.parseColor("#f57c00")
            RecommendationPriority.LOW -> android.graphics.Color.parseColor("#388e3c")
        }
        binding.cardRecommendation.setCardBackgroundColor(
            android.graphics.Color.argb(20,
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color)
            )
        )
    }

    private fun displayAnalysis(analysis: ProgressAnalysis) {
        binding.tvTrendIcon.text = analysis.trend.icon
        binding.tvTrendText.text = analysis.trend.displayName(this)

        val improvementText = when {
            analysis.improvement > 0 -> String.format(Locale.GERMAN, "+%.1f%%", analysis.improvement)
            analysis.improvement < 0 -> String.format(Locale.GERMAN, "%.1f%%", analysis.improvement)
            else -> "±0%"
        }
        binding.tvImprovement.text = "Entwicklung: $improvementText"

        val consistencyText = String.format(Locale.GERMAN, "%.0f%%", analysis.consistency)
        binding.tvConsistency.text = "Regelmäßigkeit: $consistencyText"
    }

    // ════════════════════════════════════════════════════════════
    // SESSION DETAIL DIALOG
    // ════════════════════════════════════════════════════════════

    private fun showSessionDetailDialog(session: TrainingSession) {
        val dialogBinding = DialogSessionDetailBinding.inflate(layoutInflater)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)

        // Daten füllen
        dialogBinding.tvDetailDate.text = dateFormat.format(Date(session.date))
        dialogBinding.tvDetailDuration.text = session.getEffortText() // ✅ Nutzt autom. Min oder Wiederholungen
        dialogBinding.tvDetailSuccess.text = session.getSuccessText()
        dialogBinding.tvDetailDistraction.text = session.distractionLevel.displayName(this)
        dialogBinding.tvDetailDifficulty.text = session.difficulty.displayName(this)

        // Optional: Wetter
        if (session.weatherData.isNotEmpty()) {
            dialogBinding.layoutWeather.visibility = View.VISIBLE
            dialogBinding.tvDetailWeather.text = session.weatherData
        }

        // Optional: GPS
        if (session.gpsLatitude != null && session.gpsLongitude != null) {
            dialogBinding.layoutGps.visibility = View.VISIBLE
            dialogBinding.tvDetailGps.text = session.getGpsShortFormat()
        }

        // Optional: Notizen
        if (session.notes.isNotEmpty()) {
            dialogBinding.layoutNotes.visibility = View.VISIBLE
            dialogBinding.tvDetailNotes.text = session.notes
        }

        // ✅ FIX: Button-Farben für Dark Mode
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isDark) {
            dialogBinding.btnExportPdf.setTextColor(android.graphics.Color.WHITE)
            dialogBinding.btnExportCsv.setTextColor(android.graphics.Color.WHITE)
            dialogBinding.btnClose.setTextColor(android.graphics.Color.WHITE)
        }

        // Dialog erstellen
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Button-Handler
        dialogBinding.btnExportPdf.setOnClickListener {
            exportSessionPdf(session)
            dialog.dismiss()
        }

        dialogBinding.btnExportCsv.setOnClickListener {
            exportSessionCsv(session)
            dialog.dismiss()
        }

        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ════════════════════════════════════════════════════════════
    // EXPORT FUNKTIONEN
    // ════════════════════════════════════════════════════════════

    private fun exportSessionPdf(session: TrainingSession) {
        val file = TrainingExportManager.exportSession(
            context = this,
            dog = profile,
            session = session,
            format = ExportFormat.PDF
        )

        if (file != null) {
            Toast.makeText(this, "✅ PDF gespeichert in Documents/HundeWildnis", Toast.LENGTH_SHORT).show()
            TrainingExportManager.shareFile(this, file)
        } else {
            Toast.makeText(this, "❌ Fehler beim Export", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportSessionCsv(session: TrainingSession) {
        val file = TrainingExportManager.exportSession(
            context = this,
            dog = profile,
            session = session,
            format = ExportFormat.CSV
        )

        if (file != null) {
            Toast.makeText(this, "✅ CSV gespeichert in Documents/HundeWildnis", Toast.LENGTH_SHORT).show()
            TrainingExportManager.shareFile(this, file)
        } else {
            Toast.makeText(this, "❌ Fehler beim Export", Toast.LENGTH_SHORT).show()
        }
    }

    // ════════════════════════════════════════════════════════════
    // DELETE FUNKTIONEN
    // ════════════════════════════════════════════════════════════

    private fun showDeleteSessionDialog(session: TrainingSession) {
        // ✅ DARK MODE FIX
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colorNormal = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#2d5016")

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)

        AlertDialog.Builder(this)
            .setTitle("⚠️ Session löschen?")
            .setMessage("Möchtest du diese Trainingseinheit wirklich löschen?\n\n" +
                    "📅 ${dateFormat.format(Date(session.date))}\n" +
                    "⏱️ ${session.durationMinutes} Minuten\n\n" +
                    "Der Fortschritt wird automatisch neu berechnet.")
            .setPositiveButton("Löschen") { _, _ ->
                deleteSession(session)
            }
            .setNegativeButton("Abbrechen", null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.RED)  // Rot bleibt immer Rot
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(colorNormal)  // ✅ FIX
            }
    }

    private fun deleteSession(session: TrainingSession) {
        val success = manager.deleteSession(this, session.id)

        if (success) {
            Toast.makeText(this, "✅ Session gelöscht", Toast.LENGTH_SHORT).show()
            loadData()
        } else {
            Toast.makeText(this, "❌ Fehler beim Löschen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAddSession() {
        val intent = Intent(this, AddSessionActivity::class.java)
        intent.putExtra("profile", profile)
        intent.putExtra("category_id", category.id)
        startActivity(intent)
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
// SESSION HISTORY ADAPTER
// ════════════════════════════════════════════════════════════════

class SessionHistoryAdapter(
    private val sessions: List<TrainingSession>,
    private val onViewClick: (TrainingSession) -> Unit,
    private val onDeleteClick: (TrainingSession) -> Unit
) : RecyclerView.Adapter<SessionHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemSessionHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSessionHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)

        holder.binding.tvSessionDate.text = dateFormat.format(Date(session.date))
        holder.binding.tvSessionDuration.text = "${session.durationMinutes} Min"

        val successText = when (session.successRating) {
            1 -> "🔴 Schwierigkeiten"
            2 -> "🟡 Teilweise"
            3 -> "🟢 Erfolgreich"
            4 -> "🟢🟢 Sehr gut"
            else -> "–"
        }
        holder.binding.tvSessionSuccess.text = successText

        holder.binding.tvSessionDetails.text = buildString {
            val ctx = holder.itemView.context
            append(session.difficulty.displayName(ctx))
            append(" • ${session.distractionLevel.displayName(ctx)}")
            if (session.notes.isNotEmpty()) {
                append("\n${session.notes}")
            }
        }

        // ✅ FIX: Button-Farben für Dark Mode
        val context = holder.itemView.context
        val isDark = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isDark) {
            holder.binding.btnViewSession.setTextColor(android.graphics.Color.WHITE)
            // btnDeleteSession bleibt rot (ist schon im XML gesetzt)
        }

        // Button Handler
        holder.binding.btnViewSession.setOnClickListener {
            onViewClick(session)
        }

        holder.binding.btnDeleteSession.setOnClickListener {
            onDeleteClick(session)
        }
    }

    override fun getItemCount() = sessions.size
}