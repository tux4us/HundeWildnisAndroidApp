package com.hundewildnis.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.hundewildnis.app.databinding.ActivityDogProfileBinding

class DogProfileActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityDogProfileBinding
    private lateinit var adapter: DogProfileAdapter
    private val profiles = mutableListOf<DogProfile>()

    // Moderne ActivityResult API
    private val editDogLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadProfiles()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDogProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "🐾 Hunde-Stammdaten"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        setupRecyclerView()
        setupButtons()
        loadProfiles()
    }

    private fun setupRecyclerView() {
        adapter = DogProfileAdapter(
            profiles = profiles,
            onView = { profile -> viewProfile(profile) },
            onEdit = { profile -> editProfile(profile) },
            onDelete = { profile -> deleteProfile(profile) },
            onShare = { profile -> shareProfile(profile) },
            onQR = { profile -> showQRCode(profile) },
            onPDF = { profile -> generatePDF(profile) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnAddDog.setOnClickListener {
            if (DogProfileManager.canAddMore(this)) {
                val intent = Intent(this, DogEditActivity::class.java)
                editDogLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Maximal 3 Hunde möglich", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadProfiles() {
        profiles.clear()
        profiles.addAll(DogProfileManager.getAllProfiles(this))
        adapter.notifyDataSetChanged()

        updateEmptyState()
        updateAddButtonState()
    }

    private fun updateEmptyState() {
        if (profiles.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun updateAddButtonState() {
        val remaining = DogProfileManager.getRemainingSlots(this)
        binding.btnAddDog.text = if (remaining > 0) {
            "➕ Hund hinzufügen ($remaining von 3)"
        } else {
            "🚫 Maximum erreicht (3/3)"
        }
        binding.btnAddDog.isEnabled = remaining > 0
    }

    private fun viewProfile(profile: DogProfile) {
        val intent = Intent(this, DogViewActivity::class.java)
        intent.putExtra("profile", profile)
        startActivity(intent)
    }

    private fun editProfile(profile: DogProfile) {
        val intent = Intent(this, DogEditActivity::class.java)
        intent.putExtra("profile", profile)
        editDogLauncher.launch(intent)
    }

    private fun deleteProfile(profile: DogProfile) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        val dialog = AlertDialog.Builder(this)
            .setTitle("Hund löschen?")
            .setMessage("Möchtest du ${profile.name} wirklich löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                DogProfileManager.deleteProfile(this, profile.id)
                loadProfiles()
                Toast.makeText(this, "${profile.name} gelöscht", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .create()
        
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
    }

    private fun shareProfile(profile: DogProfile) {
        val shareText = profile.toQRString()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Stammdaten: ${profile.name}")
        }
        startActivity(Intent.createChooser(intent, "Teilen über"))
    }

    private fun showQRCode(profile: DogProfile) {
        val intent = Intent(this, QRCodeActivity::class.java)
        intent.putExtra("profile", profile)
        startActivity(intent)
    }

    private fun generatePDF(profile: DogProfile) {
        binding.progressBar.visibility = View.VISIBLE

        try {
            val qrBitmap = QRCodeGenerator.generateDogProfileQR(profile, 512)
            if (qrBitmap == null) {
                Toast.makeText(this, "QR-Code Fehler", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                return
            }

            val pdfFile = PDFGenerator.generateDogProfilePDF(this, profile, qrBitmap)

            // ✅ FIX: PDF in Documents/HundeWildnis kopieren
            val documentsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOCUMENTS
            )
            val appDir = java.io.File(documentsDir, "HundeWildnis")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }

            val targetFile = java.io.File(appDir, pdfFile.name)
            pdfFile.copyTo(targetFile, overwrite = true)

            // MediaScanner benachrichtigen
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(targetFile.absolutePath),
                arrayOf("application/pdf"),
                null
            )

            // PDF öffnen (wie bisher)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                targetFile  // ✅ Nutze targetFile statt pdfFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "PDF öffnen"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "PDF Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
