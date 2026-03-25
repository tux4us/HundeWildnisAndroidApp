package com.hundewildnis.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hundewildnis.app.databinding.ActivityQrcodeBinding
import java.io.File
import java.io.FileOutputStream

class QRCodeActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityQrcodeBinding
    private var qrBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val profile = intent.getParcelableExtra<DogProfile>("profile")

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "📱 QR-Code: ${profile?.name}"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        profile?.let { generateQR(it) }

        binding.btnShare.setOnClickListener { shareQR() }
    }

    private fun generateQR(profile: DogProfile) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        
        try {
            qrBitmap = QRCodeGenerator.generateDogProfileQR(profile, 800)
            
            if (qrBitmap != null) {
                binding.ivQrCode.setImageBitmap(qrBitmap)
                binding.btnShare.isEnabled = true
            } else {
                Toast.makeText(this, "QR-Code konnte nicht generiert werden", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "QR-Code Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            binding.progressBar.visibility = android.view.View.GONE
        }
    }

    private fun shareQR() {
        qrBitmap?.let { bitmap ->
            try {
                val file = File(cacheDir, "qr_code_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(intent, "QR-Code teilen über"))
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Teilen fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "Kein QR-Code zum Teilen", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
