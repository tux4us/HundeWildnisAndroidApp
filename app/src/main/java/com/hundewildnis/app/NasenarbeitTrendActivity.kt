package com.hundewildnis.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.hundewildnis.app.databinding.ActivityNasenarbeitTrendBinding
import com.hundewildnis.app.databinding.ItemNasenarbeitTrendInsightBinding

/**
 * Zeigt statistische Trend-Hinweise zu den Nasenarbeit-Sessions EINES Hundes an
 * (siehe NasenarbeitTrendAnalyzer). Rein lesend – keine neuen Daten, nur Auswertung
 * der bereits gespeicherten Sessions.
 */
class NasenarbeitTrendActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityNasenarbeitTrendBinding
    private lateinit var profile: DogProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNasenarbeitTrendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        val loadedProfile = intent.getParcelableExtra<DogProfile>("profile")
        if (loadedProfile == null) {
            Toast.makeText(this, getString(R.string.pulscheck_error_no_profile), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        profile = loadedProfile

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.nasenarbeit_trend_toolbar_title, profile.name)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        loadAndRender()
    }

    private fun loadAndRender() {
        val sessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)
        val result = NasenarbeitTrendAnalyzer.analyze(this, sessions)

        binding.layoutInsights.removeAllViews()

        if (!result.hasEnoughData) {
            binding.tvNotEnoughData.visibility = View.VISIBLE
            binding.tvNotEnoughData.text = getString(
                R.string.nasenarbeit_trend_not_enough_data,
                result.sessionCount,
                NasenarbeitTrendAnalyzer.MIN_SESSIONS_TOTAL
            )
            return
        }

        binding.tvNotEnoughData.visibility = View.GONE

        if (result.insights.isEmpty()) {
            binding.tvNotEnoughData.visibility = View.VISIBLE
            binding.tvNotEnoughData.text = getString(R.string.nasenarbeit_trend_no_patterns_found)
            return
        }

        val inflater = LayoutInflater.from(this)
        result.insights.forEach { insight ->
            val itemBinding = ItemNasenarbeitTrendInsightBinding.inflate(inflater, binding.layoutInsights, false)
            itemBinding.tvInsightText.text = insight.text
            itemBinding.tvInsightSeverityIcon.text = when (insight.severity) {
                NasenarbeitTrendAnalyzer.InsightSeverity.POSITIVE -> "✅"
                NasenarbeitTrendAnalyzer.InsightSeverity.WARNING -> "⚠️"
                NasenarbeitTrendAnalyzer.InsightSeverity.INFO -> "ℹ️"
            }
            binding.layoutInsights.addView(itemBinding.root)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onResume() { super.onResume(); loadAndRender() }
}
