package com.hundewildnis.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.hundewildnis.app.databinding.ActivityQrcodeBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class QRCodeActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityQrcodeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val profile = intent.getParcelableExtra<DogProfile>("profile")
        val selectedFieldsArray = intent.getStringArrayExtra("selectedFields")

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(
                R.string.qrcode_activity_title,
                profile?.name ?: getString(R.string.qrcode_activity_default_dog_name)
            )
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        profile?.let {
            if (selectedFieldsArray != null) {
                val selectedFields = selectedFieldsArray.toSet()
                displayQRCode(it, selectedFields)
            } else {
                // Legacy: Alle Felder
                displayQRCode(it, it.getAllFields().keys)
            }
        }
    }

    private fun displayQRCode(profile: DogProfile, selectedFields: Set<String>) {
        // ✅ ProgressBar anzeigen
        binding.progressBar.visibility = View.VISIBLE
        binding.ivQrCode.visibility = View.GONE
        binding.gridQrActions.visibility = View.GONE

        // QR-Code generieren (asynchron wäre besser, aber für kleine Codes OK)
        val qrText = profile.toFilteredString(selectedFields)
        val qrBitmap = QRCodeGenerator.generateQRCode(qrText, 512)

        if (qrBitmap != null) {
            // ✅ QR-Code anzeigen
            binding.progressBar.visibility = View.GONE
            binding.ivQrCode.visibility = View.VISIBLE
            binding.ivQrCode.setImageBitmap(qrBitmap)

            setupGrids(qrBitmap, profile.name)
        } else {
            // ✅ Fehler
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, getString(R.string.qrcode_activity_error_generation_failed), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupGrids(qrBitmap: Bitmap, dogName: String) {
        binding.gridQrActions.visibility = View.VISIBLE
        val tiles = listOf(
            StartTile("qr_share", R.drawable.ic_map_pin_share, getString(R.string.qrcode_activity_tile_share)),
            StartTile("qr_save", R.drawable.ic_notes, getString(R.string.qrcode_activity_tile_save))
        )
        val colors = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridQrActions.layoutManager = GridLayoutManager(this, 2)
        binding.gridQrActions.adapter = StartTileAdapter(tiles, colors.chipColorRes, colors.accentColorRes) { id ->
            if (id == "qr_share") shareQRCode(qrBitmap, dogName) else saveQRCodeToGallery(qrBitmap, dogName)
        }
    }

    private fun shareQRCode(bitmap: Bitmap, dogName: String) {
        try {
            // Temporäre Datei erstellen
            val file = File(cacheDir, "QR_${dogName}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // FileProvider URI erstellen
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            // Share Intent
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, getString(R.string.qrcode_activity_share_text, dogName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, getString(R.string.qrcode_activity_share_chooser_title)))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.qrcode_activity_error_sharing, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveQRCodeToGallery(bitmap: Bitmap, dogName: String) {
        try {
            val fileName = "QR_${dogName}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMAN).format(Date())}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HundeWildnis")
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "HundeWildnis")
                if (!dir.exists()) dir.mkdirs()

                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            }

            Toast.makeText(this, getString(R.string.qrcode_activity_saved_success), Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.qrcode_activity_error_saving, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}