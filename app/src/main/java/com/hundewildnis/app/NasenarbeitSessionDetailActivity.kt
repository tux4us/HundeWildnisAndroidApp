package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hundewildnis.app.databinding.ActivityNasenarbeitSessionDetailBinding
import java.text.SimpleDateFormat
import java.util.*

class NasenarbeitSessionDetailActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityNasenarbeitSessionDetailBinding
    private lateinit var session: NasenarbeitSession
    private lateinit var profile: DogProfile
    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNasenarbeitSessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        val loadedSession = intent.getParcelableExtra<NasenarbeitSession>("session")
        @Suppress("DEPRECATION")
        val loadedProfile = intent.getParcelableExtra<DogProfile>("profile")
        if (loadedSession == null || loadedProfile == null) {
            Toast.makeText(this, getString(R.string.pulscheck_error_no_profile), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        session = loadedSession
        profile = loadedProfile

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.nasenarbeit_session_detail_toolbar_title, df.format(Date(session.datum)))

        displaySession()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_nasenarbeit_session, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share_pdf -> { sharePDF(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun displaySession() {
        val p = session.parameter

        binding.tvDatum.text      = df.format(Date(session.datum))
        binding.tvDisziplin.text  = "${session.durchgefuehrteDisziplin.icon} ${session.durchgefuehrteDisziplin.displayName(this)}"
        binding.tvEmpfohlen.text  = getString(
            R.string.nasenarbeit_session_detail_recommended,
            session.empfohleneDisziplin.icon,
            session.empfohleneDisziplin.displayName(this)
        )
        binding.tvBewertung.text  = "⭐".repeat(session.erfolgsbewertung)

        // Scores
        binding.tvScores.text = buildString {
            append(getString(R.string.nasenarbeit_session_detail_score_label)); append(" 🐾 "); append(fmt(session.scoreVerlorensuche))
            append("  🧑 ");     append(fmt(session.scorePersonensuche))
            append("  🔍 ");     append(fmt(session.scoreFreisuche))
        }

        // Ergebnisse
        binding.tvErgebnisse.text = buildString {
            session.durchgefuehrteSchritte?.let { append(getString(R.string.nasenarbeit_session_detail_route, it.toString())) }
            session.durchgefuehrteHa?.let { append(getString(R.string.nasenarbeit_session_detail_area, String.format("%.1f", it))) }
            session.suchdauerMinuten?.let { append(getString(R.string.nasenarbeit_session_detail_duration, it.toString())) }
            session.gefundeneGegenstaende?.let { g ->
                session.gegenstaendeGesamt?.let { ges ->
                    append(getString(R.string.nasenarbeit_session_detail_items_found, g.toString(), ges.toString()))
                }
            }
            session.personenfundErfolgreich?.let {
                val statusText = if (it) getString(R.string.nasenarbeit_session_detail_person_found_success)
                else getString(R.string.nasenarbeit_session_detail_person_found_failure)
                append(getString(R.string.nasenarbeit_session_detail_person_found_label, statusText))
            }
        }.trimEnd()

        // Wetter
        binding.tvWetter.text = buildString {
            append("${p.temperaturCelsius}°C  Bf ${p.beaufort}  Feuchte ${p.luftfeuchtigkeit}%\n")
            val windDir = resources.getStringArray(R.array.nasenarbeit_wind_directions)[p.windrichtung]
            append(getString(R.string.nasenarbeit_session_detail_wind_label, windDir))
        }

        // Level
        binding.tvLevel.text = buildString {
            append("🐾 ${session.levelVerlorensuche}  ")
            append("🧑 ${session.levelPersonensuche}  ")
            append("🔍 ${session.levelFreisuche}")
        }

        // Notizen
        if (session.notizen.isNotEmpty()) {
            binding.tvNotizen.text = session.notizen
        } else {
            binding.tvNotizenLabel.visibility = android.view.View.GONE
            binding.tvNotizen.visibility = android.view.View.GONE
        }
    }

    private fun fmt(d: Double) = if (d > -999) String.format("%.1f", d) else "❌"

    private fun sharePDF() {
        try {
            val file = NasenarbeitPDFGenerator.generateSessionPDF(this, session, profile, saveToPublic = true)
            if (file != null) {
                android.widget.Toast.makeText(this, getString(R.string.nasenarbeit_session_detail_pdf_saved), android.widget.Toast.LENGTH_LONG).show()
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.nasenarbeit_session_detail_pdf_share_chooser)))
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, getString(R.string.nasenarbeit_session_detail_pdf_error, e.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}