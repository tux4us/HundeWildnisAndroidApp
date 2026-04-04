package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ActivityTrainingDetailBinding
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
        supportActionBar?.title = category.displayName
        
        binding.tvDogName.text = profile.name
        binding.tvCategoryTitle.text = category.displayName
        
        // RecyclerView für Historie
        sessionAdapter = SessionHistoryAdapter(sessions)
        binding.rvSessionHistory.layoutManager = LinearLayoutManager(this)
        binding.rvSessionHistory.adapter = sessionAdapter
    }

    private fun setupListeners() {
        binding.btnAddSession.setOnClickListener {
            openAddSession()
        }
    }

    private fun loadData() {
        // Lade Fortschritt
        val progress = manager.getProgress(profile.id, category)
        displayProgress(progress)

        // Lade Sessions
        sessions.clear()
        sessions.addAll(manager.getSessionsForCategory(profile.id, category))
        sessionAdapter.notifyDataSetChanged()

        // ✅ Empfehlung mit Hundecharakter
        val dogCharacter = try {
            profile.toCharacter()  // Wenn DogProfile erweitert wurde
        } catch (e: Exception) {
            null  // Falls toCharacter() fehlt, null verwenden
        }

        val recommendation = manager.getRecommendation(
            dogId = profile.id,
            category = category,
            dogCharacter = dogCharacter
        )
        displayRecommendation(recommendation)

        // Lade Trend-Analyse
        val analysis = manager.getProgressAnalysis(profile.id, category)
        displayAnalysis(analysis)
    }

    private fun displayProgress(progress: TrainingProgress) {
        // Fortschrittsbalken
        binding.progressBar.progress = progress.progressPercentage
        binding.tvProgressPercent.text = "${progress.progressPercentage}%"
        binding.tvProgressLevel.text = progress.currentLevel.displayName

        // Statistiken
        binding.tvTotalSessions.text = "${progress.totalSessions} Einheiten"
        binding.tvTotalMinutes.text = "${progress.totalMinutes} Min"
        binding.tvConsecutiveDays.text = "${progress.consecutiveDays} Tage"

        // Durchschnitte
        val avgSuccess = String.format(Locale.GERMAN, "%.1f", progress.averageSuccess)
        binding.tvAverageSuccess.text = "Ø $avgSuccess / 4.0"

        // ✅ Meilensteine (aus Manager holen, nicht aus Progress!)
        val milestones = manager.getMilestones(progress.dogId, progress.category)
        if (milestones.isNotEmpty()) {
            val latest = milestones.last()
            binding.tvLatestMilestone.text = "🎯 ${latest.type.displayName}"
        } else {
            binding.tvLatestMilestone.text = "Noch keine Meilensteine"
        }
    }

    private fun displayRecommendation(recommendation: TrainingRecommendation) {
        binding.tvRecommendationTitle.text = "Trainerempfehlung"
        binding.tvRecommendation.text = recommendation.recommendation
        binding.tvRecommendationReason.text = recommendation.reasoning
        
        // Farbe basierend auf Priorität
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
        binding.tvTrendText.text = analysis.trend.displayName
        
        val improvementText = when {
            analysis.improvement > 0 -> String.format(Locale.GERMAN, "+%.1f%%", analysis.improvement)
            analysis.improvement < 0 -> String.format(Locale.GERMAN, "%.1f%%", analysis.improvement)
            else -> "±0%"
        }
        binding.tvImprovement.text = "Entwicklung: $improvementText"
        
        val consistencyText = String.format(Locale.GERMAN, "%.0f%%", analysis.consistency)
        binding.tvConsistency.text = "Regelmäßigkeit: $consistencyText"
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
        loadData()  // Aktualisiere wenn man zurückkommt
    }
}

// ════════════════════════════════════════════════════════════════
// ADAPTER FÜR SESSION-HISTORIE
// ════════════════════════════════════════════════════════════════

class SessionHistoryAdapter(
    private val sessions: List<TrainingSession>
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
        
        // Erfolgs-Rating als Emoji/Text
        val successText = when (session.successRating) {
            1 -> "🔴 Schwierigkeiten"
            2 -> "🟡 Teilweise"
            3 -> "🟢 Erfolgreich"
            4 -> "🟢🟢 Sehr gut"
            else -> "–"
        }
        holder.binding.tvSessionSuccess.text = successText
        
        holder.binding.tvSessionDetails.text = buildString {
            append("${session.difficulty.displayName}")
            append(" • ${session.distractionLevel.displayName}")
            if (session.notes.isNotEmpty()) {
                append("\n${session.notes}")
            }
        }
    }

    override fun getItemCount() = sessions.size
}
