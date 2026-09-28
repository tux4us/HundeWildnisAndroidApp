package com.hundewildnis.app

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.hundewildnis.app.databinding.ActivitySpielSpassDetailBinding
import com.hundewildnis.app.databinding.DialogSpielSpassLogBinding

/**
 * Detailansicht einer einzelnen Aktivität mit Beschreibung, Metadaten
 * und der Möglichkeit, die Durchführung zu protokollieren.
 */
class SpielSpassDetailActivity : SecureBaseActivity() {

    private lateinit var binding: ActivitySpielSpassDetailBinding
    private lateinit var profile: DogProfile
    private lateinit var aktivitaet: SpielAktivitaet
    private var ausgewaehlterOrt: SpielOrt = SpielOrt.GARTEN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpielSpassDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")!!
        val aktivitaetId = intent.getStringExtra("aktivitaetId") ?: ""
        aktivitaet = SpielAktivitaetCatalog.findById(aktivitaetId)
            ?: run { finish(); return }
        intent.getStringExtra("ort")?.let {
            ausgewaehlterOrt = try { SpielOrt.valueOf(it) } catch (e: Exception) { aktivitaet.orte.first() }
        } ?: run { ausgewaehlterOrt = aktivitaet.orte.first() }

        setupUI()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = aktivitaet.name(this)

        binding.tvBeschreibung.text = aktivitaet.beschreibung(this)
        binding.tvDauerValue.text = aktivitaet.dauerText(this)
        binding.tvMaterialValue.text = aktivitaet.material.displayName(this)
        binding.tvFokusValue.text = aktivitaet.fokus.joinToString(", ") { it.displayName(this) }
        binding.tvOrteValue.text = aktivitaet.orte.joinToString(", ") { it.displayName(this) }

        val hinweis = aktivitaet.hinweis(this)
        if (hinweis != null) {
            binding.layoutHinweis.visibility = View.VISIBLE
            binding.tvHinweisValue.text = hinweis
        } else {
            binding.layoutHinweis.visibility = View.GONE
        }

        binding.btnLog.setOnClickListener { showLogDialog() }
    }

    private fun showLogDialog() {
        val dialogBinding = DialogSpielSpassLogBinding.inflate(LayoutInflater.from(this))
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        dialogBinding.editDauer.setText(
            if (aktivitaet.minDauerMinuten == aktivitaet.maxDauerMinuten)
                aktivitaet.minDauerMinuten.toString()
            else ""
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.spiel_spass_log_dialog_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.spiel_spass_log_save)) { _, _ ->
                saveSession(dialogBinding)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
    }

    private fun saveSession(dialogBinding: DialogSpielSpassLogBinding) {
        val bewertung = when (dialogBinding.radioGroupBewertung.checkedRadioButtonId) {
            R.id.radioBewertung1 -> 1
            R.id.radioBewertung2 -> 2
            R.id.radioBewertung3 -> 3
            R.id.radioBewertung4 -> 4
            else -> 0
        }
        val dauer = dialogBinding.editDauer.text?.toString()?.trim()?.toIntOrNull()
        val notizen = dialogBinding.editNotizen.text?.toString()?.trim().orEmpty()

        val session = SpielSession(
            dogId = profile.id,
            aktivitaetId = aktivitaet.id,
            ort = ausgewaehlterOrt,
            dauerMinuten = dauer,
            bewertung = bewertung,
            notizen = notizen
        )
        SpielSpassManager.saveSession(this, session)
        Toast.makeText(this, getString(R.string.spiel_spass_log_success), Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
