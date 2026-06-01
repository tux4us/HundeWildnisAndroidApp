package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
        session = intent.getParcelableExtra("session")!!
        @Suppress("DEPRECATION")
        profile = intent.getParcelableExtra("profile")!!

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Session ${df.format(Date(session.datum))}"

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
        binding.tvDisziplin.text  = "${session.durchgefuehrteDisziplin.icon} ${session.durchgefuehrteDisziplin.displayName}"
        binding.tvEmpfohlen.text  = "Empfohlen: ${session.empfohleneDisziplin.icon} ${session.empfohleneDisziplin.displayName}"
        binding.tvBewertung.text  = "⭐".repeat(session.erfolgsbewertung)

        // Scores
        binding.tvScores.text = buildString {
            append("Score 🐾 "); append(fmt(session.scoreVerlorensuche))
            append("  🧑 ");     append(fmt(session.scorePersonensuche))
            append("  🔍 ");     append(fmt(session.scoreFreisuche))
        }

        // Ergebnisse
        binding.tvErgebnisse.text = buildString {
            session.durchgefuehrteSchritte?.let { append("Strecke: $it Schritte\n") }
            session.durchgefuehrteHa?.let { append("Fläche: ${String.format("%.1f", it)} ha\n") }
            session.suchdauerMinuten?.let { append("Dauer: ${it} Min.\n") }
            session.gefundeneGegenstaende?.let { g ->
                session.gegenstaendeGesamt?.let { ges ->
                    append("Gegenstände: $g von $ges gefunden\n")
                }
            }
            session.personenfundErfolgreich?.let {
                append("Personenfund: ${if (it) "✅ Erfolgreich" else "❌ Nicht gefunden"}\n")
            }
        }.trimEnd()

        // Wetter
        binding.tvWetter.text = buildString {
            append("${p.temperaturCelsius}°C  Bf ${p.beaufort}  Feuchte ${p.luftfeuchtigkeit}%\n")
            val windDir = listOf("Rückenwind", "Seitenwind", "Gegenwind")[p.windrichtung]
            append("Wind: $windDir")
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
                android.widget.Toast.makeText(this, "✅ PDF gespeichert in Documents/HundeWildnis", android.widget.Toast.LENGTH_LONG).show()
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Session als PDF teilen"))
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "PDF-Fehler: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
