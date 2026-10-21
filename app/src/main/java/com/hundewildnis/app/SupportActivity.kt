package com.hundewildnis.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import com.hundewildnis.app.databinding.ActivitySupportBinding
import com.google.android.material.button.MaterialButton

/**
 * SupportActivity - Finanzielle Unterstützung
 * Zeigt verschiedene Optionen zur Unterstützung der App und des Entwicklers
 */
class SupportActivity : SecureBaseActivity() {

    private lateinit var binding: ActivitySupportBinding

    // ✅ Wero Payment Link
    private val WERO_PAYMENT_LINK = "https://share.weropay.eu/p/1/c/XX1aXbaMp9"

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
        setupGrids()
    }

    private fun setupGrids() {
        // Geld
        val moneyTiles = listOf(
            StartTile("support_wero", R.drawable.ic_signal, "Wero (direkt)"),
            StartTile("support_paypal", R.drawable.ic_signal, "PayPal"),
            StartTile("support_bank", R.drawable.ic_signal, "Banküberweisung"),
            StartTile("support_sumup", R.drawable.ic_signal, "Zahlungslink")
        )

        val colorsMoney = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridSupportMoney.layoutManager = GridLayoutManager(this, 2)
        binding.gridSupportMoney.adapter = StartTileAdapter(
            tiles = moneyTiles,
            chipColorRes = colorsMoney.chipColorRes,
            accentColorRes = colorsMoney.accentColorRes,
            onTileClick = ::handleTileClick
        )

        // Kostenlos
        val freeTiles = listOf(
            StartTile("support_rate", R.drawable.ic_star_filled, "App bewerten"),
            StartTile("support_share", R.drawable.ic_map_pin_share, "App teilen")
        )

        val colorsFree = StartGroupColors.forGroup(this, 0) // Gelb
        binding.gridSupportFree.layoutManager = GridLayoutManager(this, 2)
        binding.gridSupportFree.adapter = StartTileAdapter(
            tiles = freeTiles,
            chipColorRes = colorsFree.chipColorRes,
            accentColorRes = colorsFree.accentColorRes,
            onTileClick = ::handleTileClick
        )
    }

    private fun handleTileClick(tileId: String) {
        when (tileId) {
            "support_wero" -> showWeroPayment()
            "support_paypal" -> openUrl("https://www.paypal.com/paypalme/wildnishunde")
            "support_bank" -> showBankDetails()
            "support_sumup" -> showSumUpInfo()
            "support_rate" -> rateApp()
            "support_share" -> shareApp()
        }
    }

    // ✅ PERFEKT: Wero mit BEIDEN Optionen!
    private fun showWeroPayment() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wero_qr, null)

        val btnWeroLink = dialogView.findViewById<MaterialButton>(R.id.btnWeroLink)
        val btnToggleQR = dialogView.findViewById<MaterialButton>(R.id.btnToggleQR)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseDialog)
        val qrSection = dialogView.findViewById<LinearLayout>(R.id.layoutQRCodeSection)
        val imageView = dialogView.findViewById<ImageView>(R.id.ivWeroQR)

        // QR-Code laden
        try {
            val inputStream = assets.open("wero_qr_code.jpg")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            imageView.setImageBitmap(bitmap)
            inputStream.close()
        } catch (e: Exception) {
            // QR-Code konnte nicht geladen werden, aber Link funktioniert noch
            btnToggleQR.isEnabled = false
            btnToggleQR.text = "QR-Code nicht verfügbar"
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // ✅ Wero Link Button (Haupt-Option)
        btnWeroLink.setOnClickListener {
            openWeroLink()
            dialog.dismiss()
        }

        // ✅ QR-Code Ein/Ausklappen
        var isQRVisible = false
        btnToggleQR.setOnClickListener {
            isQRVisible = !isQRVisible

            if (isQRVisible) {
                qrSection.visibility = View.VISIBLE
                btnToggleQR.text = "QR-Code ausblenden ▲"
            } else {
                qrSection.visibility = View.GONE
                btnToggleQR.text = "QR-Code anzeigen 📸"
            }
        }

        // ✅ Schließen Button
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

    }

    private fun openWeroLink() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WERO_PAYMENT_LINK))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Konnte Wero-Seite nicht öffnen. Bitte prüfe deine Internetverbindung.",
                Toast.LENGTH_SHORT
            ).show()
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
        val dialog = AlertDialog.Builder(this)
            .setTitle("🏦 📲 Banküberweisung")
            .setMessage("""
                IBAN: DE10 8505 0300 3022 0011 68
                BIC: OSDDDE81XXX
                Empfänger: HundeWildnisSchule Ulbricht
                Verwendungszweck: App-Spende-HundeWildnisApp
                
                DATENSCHUTZ:
                Die Überweisung erfolgt direkt über Deine Bank. Wir erhalten nur die Zahlung und den angegebenen Verwendungszweck.
            """.trimIndent())
            .setPositiveButton("IBAN kopieren") { _, _ ->
                copyToClipboard("DE10 8505 0300 3022 0011 68", "IBAN")
            }
            .setNegativeButton("Schließen", null)
            .create()

        dialog.show()
    }

    private fun showSumUpInfo() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("💳 Sichere Zahlung via SumUp")
            .setMessage("""
                Du wirst zu unserem sicheren Zahlungsdienstleister SumUp weitergeleitet.
                
                Dort kannst du bequem zahlen mit:
                • Google Pay
                • Kreditkarte (Visa, Mastercard)
                • Debitkarte
                
                DATENSCHUTZ:
                Deine Zahlungsdaten werden sicher verschlüsselt und NICHT von uns gespeichert. SumUp verarbeitet die Zahlung nach EU-DSGVO Standards.
                
                Möchtest du fortfahren?
            """.trimIndent())
            .setPositiveButton("Ja, zu SumUp") { _, _ ->
                openUrl("https://pay.sumup.com/b2c/QMA56J4J")
            }
            .setNegativeButton("Abbrechen", null)
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
            // Fallback (kein Store installiert): Projektseite im Browser öffnen
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wildnislehrer.com/hundewildnisapp/")
            }
            startActivity(intent)
        }
    }

    private fun shareApp() {
        val shareText = """
            🐕🌲 HundeWildnis App
            
            Die perfekte Begleiter-App für entspannte Spaziergänge mit deinem Hund!
            
            Mehr Infos und Download:
            https://wildnislehrer.com/hundewildnisapp/
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