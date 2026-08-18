package com.hundewildnis.app

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.load
import com.google.android.material.card.MaterialCardView
import androidx.recyclerview.widget.GridLayoutManager
import com.hundewildnis.app.databinding.ActivityPlantDetailBinding
import java.io.File

// ════════════════════════════════════════════════════════════════
// GEFAHRENPFLANZEN-SCOUT – Detailansicht mit Foto-Slots
// Datei: PlantDetailActivity.kt
// ════════════════════════════════════════════════════════════════

class PlantDetailActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityPlantDetailBinding
    private lateinit var plant: ToxicPlant

    private var activeSlot = -1
    private var currentCameraUri: Uri? = null

    // ── Activity Result Launcher ─────────────────────────────────
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera() else
                Toast.makeText(this, "Kamera-Berechtigung erforderlich", Toast.LENGTH_SHORT).show()
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && currentCameraUri != null) savePhotoToSlot(currentCameraUri!!)
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            uri?.let { savePhotoToSlot(it) }
        }

    companion object {
        const val EXTRA_PLANT_ID = "plant_id"
    }

    // ════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlantDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val plantId = intent.getStringExtra(EXTRA_PLANT_ID) ?: run { finish(); return }
        plant = ToxicPlantDatabase.getById(plantId) ?: run { finish(); return }

        setupToolbar(plant)
        renderHeader(plant)
        renderIdentification(plant)
        renderSymptoms(plant)
        renderFirstAid(plant)
        renderAdditionalInfo(plant)
        setupPhotoSlots()
        setupEmergencyButton(plant)
    }

    override fun onResume() {
        super.onResume()
        renderPhotoSlots()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ════════════════════════════════════════════════════════════
    // FOTO-SLOTS
    // ════════════════════════════════════════════════════════════

    private val slotViews: List<ImageView> by lazy {
        listOf(binding.imgSlot0, binding.imgSlot1, binding.imgSlot2)
    }
    private val slotOverlays: List<View> by lazy {
        listOf(binding.overlaySlot0, binding.overlaySlot1, binding.overlaySlot2)
    }
    private val slotDeleteBtns: List<View> by lazy {
        listOf(binding.btnDeleteSlot0, binding.btnDeleteSlot1, binding.btnDeleteSlot2)
    }

    private fun setupPhotoSlots() {
        slotViews.forEachIndexed { idx, imageView ->
            imageView.setOnClickListener { showPhotoMenu(idx) }
            slotOverlays[idx].setOnClickListener { showPhotoMenu(idx) }
            slotDeleteBtns[idx].setOnClickListener { deletePhotoAtSlot(idx) }
        }
        renderPhotoSlots()
    }

    private fun renderPhotoSlots() {
        val photos = PlantPhotoManager.getPhotos(this, plant.id)
        slotViews.forEachIndexed { idx, imageView ->
            val path = photos.getOrNull(idx)
            if (path != null && File(path).exists()) {
                imageView.load(File(path)) {
                    crossfade(true)
                    placeholder(android.R.drawable.ic_menu_camera)
                    error(android.R.drawable.ic_menu_report_image)
                }
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.clearColorFilter()
                slotOverlays[idx].visibility = View.GONE
                slotDeleteBtns[idx].visibility = View.VISIBLE
            } else {
                renderEmptySlot(idx)
            }
        }
        val count = photos.count { it != null }
        binding.tvPhotoCount.text = "$count / ${PlantPhotoManager.MAX_PHOTOS} eigene Fotos"
    }

    private fun renderEmptySlot(idx: Int) {
        slotViews[idx].setImageResource(android.R.drawable.ic_menu_camera)
        slotViews[idx].scaleType = ImageView.ScaleType.CENTER
        slotViews[idx].setColorFilter(Color.parseColor("#555555"))
        slotOverlays[idx].visibility = View.VISIBLE
        slotDeleteBtns[idx].visibility = View.GONE
    }

    private fun showPhotoMenu(slotIndex: Int) {
        activeSlot = slotIndex
        val photos = PlantPhotoManager.getPhotos(this, plant.id)
        val hasPhoto = photos.getOrNull(slotIndex) != null
        val options = if (hasPhoto)
            arrayOf("📷 Neu aufnehmen (ersetzt)", "🖼️ Aus Galerie (ersetzt)", "Abbrechen")
        else
            arrayOf("📷 Kamera", "🖼️ Galerie", "Abbrechen")

        AlertDialog.Builder(this)
            .setTitle("Foto Slot ${slotIndex + 1}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraAndOpen()
                    1 -> openGallery()
                }
            }.show()
    }

    private fun checkCameraAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) openCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        val fileName = "PlantScout_${plant.id}_${System.currentTimeMillis()}.jpg"
        currentCameraUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HundeWildnis")
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } else {
            val file = File(externalCacheDir, fileName)
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        }
        currentCameraUri?.let { cameraLauncher.launch(it) }
    }

    private fun openGallery() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun savePhotoToSlot(sourceUri: Uri) {
        if (activeSlot == -1) return
        
        val success = PlantPhotoManager.savePhotoAtSlot(this, plant.id, activeSlot, sourceUri)
        if (success) renderPhotoSlots()
        else Toast.makeText(this, "Foto konnte nicht gespeichert werden.", Toast.LENGTH_SHORT).show()
    }

    private fun deletePhotoAtSlot(slotIndex: Int) {
        val photos = PlantPhotoManager.getPhotos(this, plant.id)
        val path = photos.getOrNull(slotIndex) ?: return
        
        AlertDialog.Builder(this)
            .setTitle("Foto löschen?")
            .setMessage("Das Foto wird dauerhaft gelöscht.")
            .setPositiveButton("Löschen") { _, _ ->
                PlantPhotoManager.removePhotoAtSlot(this, plant.id, slotIndex)
                renderPhotoSlots()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setTextColor(Color.parseColor("#C0392B"))
            }
    }

    // ════════════════════════════════════════════════════════════
    // RENDERING
    // ════════════════════════════════════════════════════════════

    private fun setupToolbar(plant: ToxicPlant) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply { title = plant.name; setDisplayHomeAsUpEnabled(true) }
        val bgColor = when (plant.toxicityLevel) {
            PlantToxicityLevel.DEADLY           -> "#7B0000"
            PlantToxicityLevel.HIGHLY_TOXIC     -> "#C62828"
            PlantToxicityLevel.MODERATELY_TOXIC -> "#BF360C"
            PlantToxicityLevel.SLIGHTLY_TOXIC   -> "#E65100"
        }
        binding.toolbar.setBackgroundColor(Color.parseColor(bgColor))
    }

    private fun renderHeader(plant: ToxicPlant) {
        binding.tvPlantName.text     = plant.name
        binding.tvLatinName.text     = plant.latinName
        binding.tvToxicityIcon.text  = plant.toxicityLevel.icon
        binding.tvToxicityLabel.text = plant.toxicityLevel.displayName
        binding.cardToxicity.setCardBackgroundColor(Color.parseColor(plant.toxicityLevel.color))
        binding.tvAltNames.visibility =
            if (plant.alternativeNames.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvAltNames.text =
            "Auch bekannt als: ${plant.alternativeNames.joinToString(", ")}"
        binding.tvToxicParts.text  = "☠️ Giftige Teile: ${plant.toxicParts.joinToString(", ")}"
        binding.tvBodySystems.text = "🫀 Betroffen: ${plant.affectedSystems.joinToString(", ") { it.displayName }}"
        binding.cardSeasonHint.visibility =
            if (plant.isCurrentlySeasonal()) View.VISIBLE else View.GONE
        binding.tvSeasonHint.text = "⚠️ Aktuell relevant – ${ToxicPlantDatabase.currentMonth()}"
    }

    private fun renderIdentification(plant: ToxicPlant) {
        addBulletList(binding.layoutIdentification, plant.identificationFeatures, "#4CAF50", "#CCCCCC")
        binding.tvHabitat.text = "📍 Vorkommen: ${plant.habitat.joinToString(", ")}"
    }

    private fun renderSymptoms(plant: ToxicPlant) {
        addBulletList(binding.layoutSymptoms, plant.symptoms,
            String.format("#%06X", 0xFFFFFF and Color.parseColor(plant.toxicityLevel.color)), "#DDDDDD")
    }

    private fun renderFirstAid(plant: ToxicPlant) {
        binding.layoutFirstAid.removeAllViews()
        plant.firstAidSteps.forEachIndexed { idx, step ->
            binding.layoutFirstAid.addView(TextView(this).apply {
                text = "${idx + 1}. $step"; textSize = 14f
                setTextColor(Color.parseColor("#DDDDDD")); setPadding(0, 8, 0, 8)
            })
        }
        binding.cardVetRequired.visibility = if (plant.mustCallVet) View.VISIBLE else View.GONE
    }

    private fun renderAdditionalInfo(plant: ToxicPlant) {
        plant.confusionRisk?.let {
            binding.cardConfusion.visibility = View.VISIBLE
            binding.tvConfusionRisk.text = "⚠️ Verwechslungsgefahr: $it"
        } ?: run { binding.cardConfusion.visibility = View.GONE }
        plant.note?.let {
            binding.cardNote.visibility = View.VISIBLE
            binding.tvNote.text = "ℹ️ $it"
        } ?: run { binding.cardNote.visibility = View.GONE }
    }

    private fun setupEmergencyButton(plant: ToxicPlant) {
        val colors = StartGroupColors.forGroup(this, 3) // Braun/Ocker (ignoriert bei isDanger)
        binding.gridOpenEmergency.layoutManager = GridLayoutManager(this, 1)
        binding.gridOpenEmergency.adapter = StartTileAdapter(
            tiles = listOf(StartTile("open_emergency", R.drawable.ic_emergency, "🚨 Notfall-Assistent öffnen", isDanger = true)),
            chipColorRes = colors.chipColorRes,
            accentColorRes = colors.accentColorRes,
            onTileClick = {
                startActivity(Intent(this, FirstAidWizardActivity::class.java).apply {
                    putExtra("preselect_main", "ACCIDENT")
                    putExtra("preselect_sub", "POISONING_KNOWN")
                    putExtra("poison_name", plant.name)
                })
            }
        )
    }

    private fun addBulletList(
        container: LinearLayout, items: List<String>,
        bulletColor: String, textColor: String
    ) {
        container.removeAllViews()
        items.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 4)
            }
            row.addView(TextView(this).apply {
                text = "◆"; textSize = 8f
                setTextColor(Color.parseColor(bulletColor)); setPadding(0, 10, 12, 0)
            })
            row.addView(TextView(this).apply {
                this.text = item; textSize = 14f
                setTextColor(Color.parseColor(textColor))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            container.addView(row)
        }
    }
}
