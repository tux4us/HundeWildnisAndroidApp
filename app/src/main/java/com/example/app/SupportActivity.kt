package com.hundewildnis.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import com.hundewildnis.app.databinding.ActivitySupportBinding

/**
 * SupportActivity - Finanzielle Unterstützung
 * Zeigt verschiedene Optionen zur Unterstützung der App und des Entwicklers
 */
class SupportActivity : SecureBaseActivity() {

    private lateinit var binding: ActivitySupportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Unterstützung"
            setDisplayHomeAsUpEnabled(true)
        }

        setupButtons()
    }

    private fun setupButtons() {
        // PayPal
        binding.btnPaypal.setOnClickListener {
            openUrl("https://www.paypal.com/paypalme/wildnishunde")
        }

        // Banküberweisung
        binding.btnBankTransfer.setOnClickListener {
            showBankDetails()
        }

        // Sumup
        binding.btnPatreon.setOnClickListener {
            openUrl("https://pay.sumup.com/b2c/QMA56J4J")
        }

        // App bewerten
        binding.btnRateApp.setOnClickListener {
            rateApp()
        }

        // Teilen
        binding.btnShareApp.setOnClickListener {
            shareApp()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBankDetails() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("💳 Banküberweisung")
            .setMessage("""
                IBAN: DE10 8505 0300 3022 0011 68
                BIC: OSDDDE81XXX
                Empfänger: HundeWildnisSchule Ulbricht
                Verwendungszweck: App-Spende-HundeWildnisApp
                
                (Lange drücken um IBAN zu kopieren)
            """.trimIndent())
            .setPositiveButton("IBAN kopieren") { _, _ ->
                copyToClipboard("DE10 8505 0300 3022 0011 68", "IBAN")
            }
            .setNegativeButton("Schließen", null)
            .create()

        dialog.show()
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "✓ $label kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun rateApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: Browser öffnen
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            }
            startActivity(intent)
        }
    }

    private fun shareApp() {
        val shareText = """
            🐕🌲 HundeWildnis App
            
            Die perfekte Begleiter-App für entspannte Spaziergänge mit deinem Hund!
            
            Jetzt herunterladen:
            https://play.google.com/store/apps/details?id=$packageName
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        startActivity(Intent.createChooser(intent, "HundeWildnis App teilen"))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
