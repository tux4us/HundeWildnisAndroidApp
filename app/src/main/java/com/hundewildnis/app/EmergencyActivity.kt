package com.hundewildnis.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hundewildnis.app.databinding.ActivityEmergencyReportBinding
import com.hundewildnis.app.databinding.ItemEmergencyReportBinding
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.result.PickVisualMediaRequest
import android.content.ContentValues
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.IOException

data class EmergencyReport(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val dogId: String = "",
    val dogName: String = "",
    val symptom: String = "",
    val notes: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val photoUris: List<String> = emptyList(),
    val pdfPath: String = "",
    val contentUri: String? = null
)

class EmergencyActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityEmergencyReportBinding
    private var profile: DogProfile? = null
    private val attachedPhotos = mutableListOf<Uri>()
    private var lastLocation: Location? = null

    private val historyList = mutableListOf<EmergencyReport>()
    private lateinit var historyAdapter: EmergencyHistoryAdapter

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path ->
                importPhotos(listOf(Uri.fromFile(File(path))), deleteSource = true)
            }
        }
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
        }
        importPhotos(uris)
    }

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            binding.switchGps.isChecked = true
            requestLocation()
        } else {
            binding.switchGps.isChecked = false
            Toast.makeText(this, getString(R.string.emergency_toast_location_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private var currentPhotoPath: String? = null
    private var locationListener: LocationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Profil aus Intent lesen ...
        val intentProfile = intent.getParcelableExtra<DogProfile>("profile")

        // ... dann IMMER frisch aus dem DogProfileManager laden.
        // Grund: DogProfileActivity übergibt die Profil-Instanz aus
        // ihrer gecachten Liste. Wenn der Nutzer zwischenzeitlich
        // in DogViewActivity Läufigkeitsdaten eingetragen hat, sind
        // diese im gecachten Objekt noch nicht vorhanden.
        // DogProfileManager liest aus SharedPreferences und hat immer
        // den aktuellen Stand.
        profile = intentProfile?.id
            ?.let { DogProfileManager.getProfile(this, it) }
            ?: intentProfile  // Fallback falls ID nicht auffindbar

        if (profile == null) {
            Toast.makeText(this, getString(R.string.emergency_toast_no_profile_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        setupListeners()
        setupHistory()
        loadCache()
        loadHistory()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        profile?.let { p ->
            binding.tvDogName.text = p.name
            binding.tvDogBreed.text = getString(R.string.emergency_dog_breed_format, p.breed, GenderCode.displayName(this, p.gender), p.weight)
            if (p.photoUri.isNotEmpty()) {
                binding.ivDogThumb.setImageURI(Uri.parse(p.photoUri))
            }
        }

        val symptoms = resources.getStringArray(R.array.emergency_symptoms)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, symptoms)
        binding.spinnerSymptoms.setAdapter(adapter)

        // ✅ Vitalwerte aus PulscheckActivity vorausfüllen
        val prefillNotes = intent.getStringExtra("prefill_notes")
        if (!prefillNotes.isNullOrBlank()) {
            binding.etNotes.setText(prefillNotes)
        }
    }

    private fun setupListeners() {
        setupGrids()

        binding.switchGps.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    requestLocation()
                } else {
                    showLocationDisclosure()
                }
            } else {
                stopLocationUpdates()
                lastLocation = null
                binding.tvGpsStatus.visibility = View.GONE
            }
        }

        binding.spinnerSymptoms.setOnItemClickListener { _, _, _, _ -> saveCache() }
        binding.etNotes.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { saveCache() }
        })
    }

    private fun setupGrids() {
        // Fotos
        val photoTiles = listOf(
            StartTile("emergency_camera", R.drawable.ic_microphone, getString(R.string.emergency_tile_camera)), // Placeholder
            StartTile("emergency_gallery", R.drawable.ic_pages, getString(R.string.emergency_tile_gallery))
        )
        val colorsPhotos = StartGroupColors.forGroup(this, 4) // Blau
        binding.gridEmergencyPhotos.layoutManager = GridLayoutManager(this, 2)
        binding.gridEmergencyPhotos.adapter = StartTileAdapter(photoTiles, colorsPhotos.chipColorRes, colorsPhotos.accentColorRes) { id ->
            if (id == "emergency_camera") showCameraDisclosure() else openPhotoPicker()
        }

        // Aktionen
        val actionTiles = listOf(
            StartTile("emergency_send", R.drawable.ic_emergency, getString(R.string.emergency_tile_send), isDanger = true),
            StartTile("emergency_reset", R.drawable.ic_remove, getString(R.string.emergency_tile_reset))
        )
        val colorsActions = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridEmergencyActions.layoutManager = GridLayoutManager(this, 1)
        binding.gridEmergencyActions.adapter = StartTileAdapter(actionTiles, colorsActions.chipColorRes, colorsActions.accentColorRes) { id ->
            if (id == "emergency_send") generateAndSendReport() else resetForm()
        }
    }

    private fun openPhotoPicker() {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun setupHistory() {
        historyAdapter = EmergencyHistoryAdapter(historyList,
            onItemClick = { report -> openPdf(report) },
            onDeleteClick = { report -> confirmDelete(report) }
        )
        binding.rvEmergencyHistory.apply {
            layoutManager = LinearLayoutManager(this@EmergencyActivity)
            adapter = historyAdapter
        }
    }

    private fun saveCache() {
        val prefs = getSharedPreferences("emergency_cache", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("symptom_${profile?.id}", binding.spinnerSymptoms.text.toString())
            putString("notes_${profile?.id}", binding.etNotes.text.toString())
            putStringSet("photos_${profile?.id}", attachedPhotos.map { it.toString() }.toSet())
            apply()
        }
    }

    private fun loadCache() {
        val prefs = getSharedPreferences("emergency_cache", Context.MODE_PRIVATE)
        val symptom = prefs.getString("symptom_${profile?.id}", "")
        val notes = prefs.getString("notes_${profile?.id}", "")
        val photos = prefs.getStringSet("photos_${profile?.id}", emptySet())

        if (!symptom.isNullOrEmpty()) binding.spinnerSymptoms.setText(symptom, false)
        // Nur aus Cache laden wenn kein prefill_notes aus PulscheckActivity gesetzt wurde
        val hasPrefill = !intent.getStringExtra("prefill_notes").isNullOrBlank()
        if (!hasPrefill && !notes.isNullOrEmpty()) binding.etNotes.setText(notes)
        photos?.forEach { uriString ->
            try { addPhoto(Uri.parse(uriString)) } catch (e: Exception) {}
        }
    }

    private fun clearCache() {
        val prefs = getSharedPreferences("emergency_cache", Context.MODE_PRIVATE)
        prefs.edit().apply {
            remove("symptom_${profile?.id}")
            remove("notes_${profile?.id}")
            remove("photos_${profile?.id}")
            apply()
        }
    }

    private fun saveHistory() {
        val prefs = getSharedPreferences("emergency_history", Context.MODE_PRIVATE)
        val json = Gson().toJson(historyList)
        prefs.edit().putString("reports", json).apply()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences("emergency_history", Context.MODE_PRIVATE)
        val json = prefs.getString("reports", null)
        if (json != null) {
            val type = object : TypeToken<List<EmergencyReport>>() {}.type
            val loaded: List<EmergencyReport> = Gson().fromJson(json, type)
            historyList.clear()
            historyList.addAll(loaded.filter { it.dogId == profile?.id })
            historyAdapter.notifyDataSetChanged()
        }
    }

    private fun confirmDelete(report: EmergencyReport) {

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.emergency_delete_report_title))
            .setMessage(getString(R.string.emergency_delete_report_message))
            .setPositiveButton(getString(R.string.emergency_delete_report_confirm)) { _, _ ->
                historyList.remove(report)
                historyAdapter.notifyDataSetChanged()
                saveHistory()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create().apply {
                show()
            }
    }

    private fun showLocationDisclosure() {

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.emergency_location_disclosure_title))
            .setMessage(getString(R.string.emergency_location_disclosure_message))
            .setPositiveButton(getString(R.string.emergency_disclosure_activate)) { _, _ -> requestLocation() }
            .setNegativeButton(getString(R.string.emergency_disclosure_decline)) { _, _ -> binding.switchGps.isChecked = false }
            .setOnCancelListener { binding.switchGps.isChecked = false }
            .create().apply {
                show()
            }
    }

    private fun showCameraDisclosure() {
        val prefs = getSharedPreferences("emergency_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("camera_disclosure_shown", false)) {
            checkCameraPermissionAndOpen()
            return
        }


        AlertDialog.Builder(this)
            .setTitle(getString(R.string.emergency_camera_disclosure_title))
            .setMessage(getString(R.string.emergency_camera_disclosure_message))
            .setPositiveButton(getString(R.string.emergency_disclosure_activate)) { _, _ ->
                prefs.edit().putBoolean("camera_disclosure_shown", true).apply()
                checkCameraPermissionAndOpen()
            }
            .setNegativeButton(getString(R.string.emergency_disclosure_decline), null)
            .create().apply {
                show()
            }
    }

    /**
     * Übernimmt neue Fotos verkleinert (max. 1280 px) in den App-Speicher.
     * Lässt sich ein Foto nicht verkleinern, wird die ursprüngliche URI verwendet.
     * @param deleteSource löscht die Quelldatei nach erfolgreicher Übernahme (Kamera-Zwischendatei)
     */
    private fun importPhotos(sources: List<Uri>, deleteSource: Boolean = false) {
        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                sources.map { src ->
                    val copy = PhotoCompressor.importPhoto(this@EmergencyActivity, src)
                    if (copy != null && deleteSource) {
                        try { src.path?.let { File(it).delete() } } catch (e: Exception) {}
                    }
                    copy ?: src
                }
            }
            imported.forEach { addPhoto(it) }
            saveCache()
        }
    }

    private fun addPhoto(uri: Uri) {
        if (attachedPhotos.contains(uri)) return
        attachedPhotos.add(uri)
        val iv = ImageView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(250, 250).apply {
                setMargins(8, 0, 8, 0)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageURI(uri)
            setOnClickListener {
                attachedPhotos.remove(uri)
                binding.layoutPhotos.removeView(this)
                PhotoCompressor.deleteOwnPhoto(this@EmergencyActivity, uri)
                saveCache()
            }
        }
        binding.layoutPhotos.addView(iv)
    }

    private fun resetForm() {
        binding.spinnerSymptoms.setText("", false)
        binding.etNotes.setText("")
        binding.switchGps.isChecked = false
        binding.layoutPhotos.removeAllViews()
        attachedPhotos.clear()
        lastLocation = null
        binding.tvGpsStatus.visibility = View.GONE
        clearCache()
    }

    private fun checkCameraPermissionAndOpen() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            openCamera()
        }
    }

    private fun openCamera() {
        val photoFile = File.createTempFile("EMERGENCY_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        currentPhotoPath = photoFile.absolutePath
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }
        cameraLauncher.launch(intent)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        stopLocationUpdates()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(this, getString(R.string.emergency_toast_enable_gps), Toast.LENGTH_LONG).show()
            binding.switchGps.isChecked = false
            return
        }

        binding.tvGpsStatus.visibility = View.VISIBLE
        binding.tvGpsStatus.text = getString(R.string.emergency_gps_status_searching)

        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) bestLocation = l
        }
        if (bestLocation != null) updateLocationUI(bestLocation)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (bestLocation == null || location.accuracy <= (bestLocation?.accuracy ?: Float.MAX_VALUE)) {
                    updateLocationUI(location)
                    bestLocation = location
                }
                if (location.accuracy < 30) locationManager.removeUpdates(this)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        locationListener = listener

        try {
            if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener)
            if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        } catch (e: Exception) {
            binding.tvGpsStatus.text = getString(R.string.emergency_gps_status_error)
        }
    }

    private fun updateLocationUI(location: Location) {
        lastLocation = location
        binding.tvGpsStatus.apply {
            visibility = View.VISIBLE
            text = getString(R.string.emergency_gps_status_captured_format, location.latitude, location.longitude)
        }
    }

    private fun stopLocationUpdates() {
        locationListener?.let {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.removeUpdates(it)
        }
        locationListener = null
    }

    private fun generateAndSendReport() {
        val symptom = binding.spinnerSymptoms.text.toString()
        if (symptom.isEmpty() || symptom == resources.getStringArray(R.array.emergency_symptoms)[0]) {
            Toast.makeText(this, getString(R.string.emergency_toast_select_symptom), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (pdfFile, contentUri) = createEmergencyPdf()

                val report = EmergencyReport(
                    timestamp = System.currentTimeMillis(),
                    dogId = profile?.id ?: "",
                    dogName = profile?.name ?: "",
                    symptom = symptom,
                    notes = binding.etNotes.text.toString(),
                    latitude = lastLocation?.latitude,
                    longitude = lastLocation?.longitude,
                    photoUris = attachedPhotos.map { it.toString() },
                    pdfPath = pdfFile?.absolutePath ?: "",
                    contentUri = contentUri?.toString()
                )

                withContext(Dispatchers.Main) {
                    historyList.add(0, report)
                    historyAdapter.notifyItemInserted(0)
                    saveHistory()

                    if (contentUri != null || pdfFile != null) {
                        Toast.makeText(this@EmergencyActivity, getString(R.string.emergency_toast_pdf_saved), Toast.LENGTH_LONG).show()
                    }

                    if (contentUri != null) shareReportViaUri(contentUri)
                    else if (pdfFile != null) shareReport(pdfFile)

                    clearCache()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EmergencyActivity, getString(R.string.emergency_toast_pdf_creation_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createEmergencyPdf(): Pair<File?, Uri?> {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "NOTFALL_${profile?.name}_$timestamp.pdf"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createPdfViaMediaStore(fileName)
        } else {
            val file = createPdfOldWay(fileName)
            Pair(file, null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createPdfViaMediaStore(fileName: String): Pair<File?, Uri?> {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/HundeWildnis")
        }

        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: throw IOException("MediaStore insert failed")

        contentResolver.openOutputStream(uri)?.use { outputStream ->
            val writer = PdfWriter(outputStream)
            val pdf = PdfDocument(writer)
            val document = Document(pdf)
            writePdfContent(document)
            document.close()
        } ?: throw IOException("Failed to open output stream")

        var filePath: String? = null
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (columnIndex >= 0) filePath = cursor.getString(columnIndex)
            }
        }

        val file = if (filePath != null) File(filePath) else null
        return Pair(file, uri)
    }

    private fun createPdfOldWay(fileName: String): File {
        val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "HundeWildnis")
        if (!docsDir.exists()) docsDir.mkdirs()
        val file = File(docsDir, fileName)
        val writer = PdfWriter(FileOutputStream(file))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        writePdfContent(document)
        document.close()
        return file
    }

    // ════════════════════════════════════════════════════════════════
    // PDF-INHALT
    // ════════════════════════════════════════════════════════════════

    private fun writePdfContent(document: Document) {
        document.add(Paragraph(getString(R.string.emergency_pdf_header_title))
            .setFontSize(22f)
            .setFontColor(DeviceRgb(176, 0, 32))
            .setTextAlignment(TextAlignment.CENTER))

        document.add(Paragraph(getString(R.string.emergency_pdf_created_at_format, SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())))
            .setTextAlignment(TextAlignment.CENTER))

        document.add(Paragraph(getString(R.string.emergency_pdf_footer_app_credit))
            .setFontSize(10f)
            .setTextAlignment(TextAlignment.CENTER))

        document.add(Paragraph("\n"))

        document.add(Paragraph(getString(R.string.emergency_pdf_medical_emergency_header))
            .setBold().setFontSize(16f))
        document.add(Paragraph(getString(R.string.emergency_pdf_symptom_format, binding.spinnerSymptoms.text)).setFontSize(14f))
        document.add(Paragraph(getString(R.string.emergency_pdf_additional_info_format, binding.etNotes.text)).setFontSize(12f))

        lastLocation?.let {
            val osmUrl = "https://www.openstreetmap.org/?mlat=${it.latitude}&mlon=${it.longitude}#map=18/${it.latitude}/${it.longitude}"
            document.add(Paragraph(getString(R.string.emergency_pdf_location_format, it.latitude, it.longitude)).setBold())
            document.add(Paragraph(getString(R.string.emergency_pdf_map_link_format, osmUrl)).setFontColor(DeviceRgb(0, 0, 255)))
        }

        document.add(Paragraph("\n"))

        // ── STAMMDATEN ───────────────────────────────────────────────
        document.add(Paragraph(getString(R.string.emergency_pdf_patient_data_header))
            .setBold().setFontSize(16f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(30f, 70f))).useAllAvailableWidth()

        profile?.let { p ->
            table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_name))))
            table.addCell(Cell().add(Paragraph(p.name)))

            table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_breed))))
            table.addCell(Cell().add(Paragraph(p.breed)))

            table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_birthdate))))
            table.addCell(Cell().add(Paragraph(p.birthDate)))

            table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_gender))))
            table.addCell(Cell().add(Paragraph(GenderCode.displayName(this, p.gender))))

            table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_neutered))))
            table.addCell(Cell().add(Paragraph(p.neutered.ifEmpty { getString(R.string.emergency_pdf_value_unknown) })))

            table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_weight))))
            table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_value_kg_format, p.weight))))

            if (p.shoulderHeight.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_shoulder_height))))
                table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_value_cm_format, p.shoulderHeight))))
            }
            if (p.bodyLength.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_body_length))))
                table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_value_cm_format, p.bodyLength))))
            }
            if (p.chipNumber.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_chip_number))))
                table.addCell(Cell().add(Paragraph(p.chipNumber)))
            }
            if (p.vaccinationStatus.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_vaccination_status))))
                table.addCell(Cell().add(Paragraph(p.vaccinationStatus)))
            }
            if (p.medicalNotes.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_medical_notes))))
                table.addCell(Cell().add(Paragraph(p.medicalNotes)))
            }
            if (p.owner.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_field_owner))))
                table.addCell(Cell().add(Paragraph(p.owner)))
            }
        }

        document.add(table)

        // ── LÄUFIGKEIT (nur Hündinnen, nicht kastriert) ──────────────
        profile?.let { p ->
            if ((p.gender == GenderCode.FEMALE || p.gender == "Hündin") && (p.neutered != NeuteredCode.YES && p.neutered != "Ja")) {
                document.add(Paragraph("\n"))
                writeHeatCyclePdfSection(document, p)
            }
        }

        // ── FOTOS ────────────────────────────────────────────────────
        if (attachedPhotos.isNotEmpty()) {
            document.add(AreaBreak())
            document.add(Paragraph(getString(R.string.emergency_pdf_photos_header))
                .setBold().setFontSize(16f))
            document.add(Paragraph("\n"))

            var photoTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
                .useAllAvailableWidth().setMarginTop(10f)
            var imageCounter = 0

            for (uri in attachedPhotos) {
                try {
                    val inputStream = contentResolver.openInputStream(uri) ?: continue
                    val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: continue
                    inputStream.close()

                    val maxSize = 800
                    val scaledBitmap = if (originalBitmap.width > maxSize || originalBitmap.height > maxSize) {
                        val scale = minOf(maxSize.toFloat() / originalBitmap.width, maxSize.toFloat() / originalBitmap.height)
                        Bitmap.createScaledBitmap(originalBitmap, (originalBitmap.width * scale).toInt(), (originalBitmap.height * scale).toInt(), true)
                    } else originalBitmap

                    val stream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)

                    val img = Image(ImageDataFactory.create(stream.toByteArray())).setAutoScale(true)
                    val cell = Cell().add(img)
                    cell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    cell.setPadding(10f)
                    photoTable.addCell(cell)
                    imageCounter++

                    if (scaledBitmap != originalBitmap) scaledBitmap.recycle()
                    originalBitmap.recycle()

                    if (imageCounter % 2 == 0 && imageCounter < attachedPhotos.size) {
                        document.add(photoTable)
                        document.add(AreaBreak())
                        photoTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
                            .useAllAvailableWidth().setMarginTop(10f)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (photoTable.getNumberOfRows() > 0) {
                if (imageCounter % 2 != 0) {
                    val emptyCell = Cell().add(Paragraph(""))
                    emptyCell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    photoTable.addCell(emptyCell)
                }
                document.add(photoTable)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // LÄUFIGKEIT — PDF-ABSCHNITT
    // Wird nur für intakte Hündinnen eingefügt.
    // ════════════════════════════════════════════════════════════════

    private fun writeHeatCyclePdfSection(document: Document, p: DogProfile) {
        val cycles = p.heatCycles

        document.add(Paragraph(getString(R.string.emergency_pdf_heat_cycle_header))
            .setBold().setFontSize(16f))

        val heatTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f))).useAllAvailableWidth()

        // ── Aktueller Status ─────────────────────────────────────────
        val activeCycle = cycles.firstOrNull { it.isActive }
        val statusText = when {
            activeCycle != null -> getString(R.string.emergency_pdf_heat_status_active_format, activeCycle.startDate)
            cycles.isEmpty()    -> getString(R.string.emergency_pdf_heat_status_no_data)
            else                -> getString(R.string.emergency_pdf_heat_status_not_active)
        }
        heatTable.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_heat_current_status_label)).setBold()))
        heatTable.addCell(Cell().add(Paragraph(statusText)))

        // ── Letzte Läufigkeit ────────────────────────────────────────
        val lastCycle = cycles.filter { !it.isActive }.maxByOrNull { it.startDate }
        if (lastCycle != null) {
            val lastInfo = buildString {
                append("${lastCycle.startDate}")
                if (lastCycle.endDate.isNotEmpty()) append(" – ${lastCycle.endDate} (${lastCycle.durationLabel})")
                append(getString(R.string.emergency_pdf_heat_intensity_suffix_format, lastCycle.intensity))
            }
            heatTable.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_heat_last_cycle_label)).setBold()))
            heatTable.addCell(Cell().add(Paragraph(lastInfo)))

            if (lastCycle.behaviorSigns.isNotEmpty()) {
                heatTable.addCell(Cell().add(Paragraph(getString(R.string.emergency_pdf_heat_behavior_changes_label)).setBold()))
                heatTable.addCell(Cell().add(Paragraph(lastCycle.behaviorSigns.joinToString(", "))))
            }
            if (lastCycle.symptoms.isNotEmpty()) {
                heatTable.addCell(Cell().add(Paragraph(getString(R.string.emergency_heat_cycle_symptoms_label)).setBold()))
                heatTable.addCell(Cell().add(Paragraph(lastCycle.symptoms)))
            }
            if (lastCycle.hasPseudoPregnancy) {
                val pseudoText = if (lastCycle.pseudoPregnancySigns.isNotEmpty()) {
                    lastCycle.pseudoPregnancySigns.joinToString(", ")
                } else getString(R.string.emergency_heat_cycle_observed)
                heatTable.addCell(Cell().add(Paragraph(getString(R.string.emergency_heat_cycle_pseudopregnancy_label)).setBold()))
                heatTable.addCell(Cell().add(Paragraph(pseudoText)))
            }
        }

        // ── Zyklusstatistik ──────────────────────────────────────────
        val analysis = HeatCycleManager.analyze(cycles)
        val avgText = when (analysis.pattern) {
            CyclePattern.UNKNOWN -> getString(R.string.emergency_heat_cycle_not_yet_calculable)
            else -> getString(R.string.emergency_heat_cycle_average, analysis.averageDays, analysis.pattern.descriptionShort(this)) +
                    if (analysis.zwischenlaeufe > 0)
                        getString(R.string.emergency_heat_cycle_filtered_intervals, analysis.zwischenlaeufe)
                    else ""
        }
        heatTable.addCell(Cell().add(Paragraph(avgText)))


        document.add(heatTable)

        // Hinweisbox wenn aktuell läufig — medizinisch relevant
        if (activeCycle != null) {
            document.add(Paragraph("\n"))
            document.add(Paragraph(
                getString(R.string.emergency_heat_cycle_vet_notice, activeCycle.startDate)
            ).setFontSize(11f)
                .setFontColor(DeviceRgb(176, 0, 32))
                .setBold())
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SHARE-TEXT — inkl. Läufigkeits-Kurzinfo
    // ════════════════════════════════════════════════════════════════

    private fun buildShareText(): String = buildString {
        append(getString(R.string.emergency_share_text_header)).append("\n")
        append(getString(R.string.emergency_share_text_dog_format, profile?.name)).append("\n")
        append(getString(R.string.emergency_share_text_symptom_format, binding.spinnerSymptoms.text)).append("\n")
        append(getString(R.string.emergency_share_text_info_format, binding.etNotes.text)).append("\n")

        lastLocation?.let {
            append(getString(R.string.emergency_share_text_location_format, it.latitude, it.longitude)).append("\n")
            append(getString(R.string.emergency_share_text_map_format,
                "https://www.openstreetmap.org/?mlat=${it.latitude}&mlon=${it.longitude}#map=18/${it.latitude}/${it.longitude}")).append("\n")
        }

        // Läufigkeit — nur für intakte Hündinnen, nur wenn Daten vorhanden
        profile?.let { p ->
            if ((p.gender == GenderCode.FEMALE || p.gender == "Hündin") && (p.neutered != NeuteredCode.YES && p.neutered != "Ja") && p.heatCycles.isNotEmpty()) {
                append("\n").append(getString(R.string.emergency_share_text_heat_cycle_header)).append("\n")
                val activeCycle = p.heatCycles.firstOrNull { it.isActive }
                if (activeCycle != null) {
                    append(getString(R.string.emergency_share_text_heat_cycle_active_format, activeCycle.startDate)).append("\n")
                } else {
                    val last = p.heatCycles.filter { !it.isActive }.maxByOrNull { it.startDate }
                    last?.let { append(getString(R.string.emergency_share_text_heat_cycle_last_format, it.startDate) +
                            (if (it.endDate.isNotEmpty()) " – ${it.endDate}" else "") + "\n") }
                }
                HeatCycleManager.averageCycleLengthDays(p.heatCycles)
                    ?.let { append(getString(R.string.emergency_share_text_heat_cycle_average_format, it)).append("\n") }
            }
        }

        append("\n").append(getString(R.string.emergency_share_text_footer))
    }

    private fun shareReport(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, buildShareText())
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.emergency_share_subject_format, profile?.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.emergency_share_chooser_title)))
    }

    private fun shareReportViaUri(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, buildShareText())
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.emergency_share_subject_format, profile?.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.emergency_share_chooser_title)))
    }

    // ════════════════════════════════════════════════════════════════
    // PDF ÖFFNEN
    // ════════════════════════════════════════════════════════════════

    private fun openPdf(report: EmergencyReport) {
        try {
            if (!report.contentUri.isNullOrEmpty()) {
                openPdfViaUri(Uri.parse(report.contentUri))
                return
            }
            if (report.pdfPath.isNotEmpty()) {
                val file = File(report.pdfPath)
                if (file.exists()) { openPdfViaUri(FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)); return }
            }
            Toast.makeText(this, getString(R.string.emergency_toast_pdf_not_found), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.emergency_toast_generic_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun openPdfViaUri(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val resInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            grantUriPermission(resolveInfo.activityInfo.packageName, uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.emergency_pdf_open_chooser_title)))
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ════════════════════════════════════════════════════════════════
    // HISTORY ADAPTER
    // ════════════════════════════════════════════════════════════════

    inner class EmergencyHistoryAdapter(
        private val reports: List<EmergencyReport>,
        private val onItemClick: (EmergencyReport) -> Unit,
        private val onDeleteClick: (EmergencyReport) -> Unit
    ) : RecyclerView.Adapter<EmergencyHistoryAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemEmergencyReportBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(ItemEmergencyReportBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val report = reports[position]
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(report.timestamp))
            holder.binding.tvReportDate.text = date
            holder.binding.tvReportSummary.text = getString(R.string.emergency_report_symptom_format, report.symptom)
            holder.binding.reportClickArea.setOnClickListener { onItemClick(report) }
            holder.binding.btnDeleteReport.setOnClickListener { onDeleteClick(report) }
        }

        override fun getItemCount() = reports.size
    }
}
