package com.hundewildnis.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.hundewildnis.app.databinding.ActivityDogProfileBinding
import java.io.File

class DogProfileActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityDogProfileBinding
    private lateinit var adapter: DogProfileAdapter

    private val editLauncher = registerForActivityResult(
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
            title = "🐕 Stammdaten"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        setupRecyclerView()
        setupAddButton()
        loadProfiles()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadProfiles() {
        val profiles = DogProfileManager.getAllProfiles(this)
        
        if (profiles.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            
            adapter = DogProfileAdapter(
                profiles = profiles,
                onView = { profile ->
                    val intent = Intent(this, DogViewActivity::class.java)
                    intent.putExtra("profile", profile)
                    startActivity(intent)
                },
                onEdit = { profile ->
                    val intent = Intent(this, DogEditActivity::class.java)
                    intent.putExtra("profile", profile)
                    editLauncher.launch(intent)
                },
                onDelete = { profile ->
                    confirmDelete(profile)
                },
                onShare = { profile, fields ->
                    shareProfile(profile, fields)
                },
                onQR = { profile, fields ->
                    val intent = Intent(this, QRCodeActivity::class.java)
                    intent.putExtra("profile", profile)
                    intent.putExtra("selectedFields", ArrayList(fields.toList()))
                    startActivity(intent)
                },
                onPDF = { profile, fields ->
                    generateAndOpenPDF(profile, fields)
                },
                onEmergency = { profile ->
                    val intent = Intent(this, FirstAidWizardActivity::class.java)
                    intent.putExtra("profile", profile)
                    startActivity(intent)
                }
            )
            binding.recyclerView.adapter = adapter
        }
    }

    private fun setupAddButton() {
        val addTiles = listOf(
            StartTile("add_dog", R.drawable.ic_add, "Neuen Hund anlegen")
        )
        val colors = StartGroupColors.forGroup(this, 1) // Ocker/Gelb

        binding.gridAddDog.adapter = StartTileAdapter(
            tiles = addTiles,
            chipColorRes = colors.chipColorRes,
            accentColorRes = colors.accentColorRes,
            onTileClick = {
                val intent = Intent(this, DogEditActivity::class.java)
                editLauncher.launch(intent)
            }
        )
    }

    private fun confirmDelete(profile: DogProfile) {
        AlertDialog.Builder(this)
            .setTitle("Hund löschen?")
            .setMessage("Möchtest Du ${profile.name} wirklich unwiderruflich löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                DogProfileManager.deleteProfile(this, profile.id)
                loadProfiles()
                Toast.makeText(this, "Gelöscht", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun shareProfile(profile: DogProfile, fields: Set<String>) {
        val text = profile.toFilteredString(fields)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Hundedaten: ${profile.name}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Teilen via"))
    }

    private fun generateAndOpenPDF(profile: DogProfile, fields: Set<String>) {
        binding.progressBar.visibility = View.VISIBLE
        
        // QR-Code für das PDF generieren (optional, hier leerer Bitmap wenn nicht gebraucht)
        val qrBitmap = try {
            QRCodeGenerator.generateQRCode(profile.toQRString(), 300)
        } catch (e: Exception) {
            null
        }

        try {
            val file = PDFGenerator.generateFilteredDogProfilePDF(
                this, profile, fields, qrBitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            )
            
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "PDF öffnen"))
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            binding.progressBar.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
