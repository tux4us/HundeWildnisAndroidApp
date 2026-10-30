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
            StartTile("support_paypal", R.drawable.ic_signal, "PayPal")
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
            "support_paypal" -> openUrl("https://www.paypal.com/paypalme/wildnishunde")
            "support_rate" -> rateApp()
            "support_share" -> shareApp()
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

    private fun rateApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback (kein Store installiert): Projektseite im Browser öffnen
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=com.hundewildnis.app")
            }
            startActivity(intent)
        }
    }

    private fun shareApp() {
        val shareText = """
            🐕🌲 HundeWildnis App
            
            Die perfekte Begleiter-App für entspannte Spaziergänge mit deinem Hund!
            
            Mehr Infos und Download:
            https://play.google.com/store/apps/details?id=com.hundewildnis.app
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