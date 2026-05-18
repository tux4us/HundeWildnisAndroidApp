package com.hundewildnis.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
    val timestamp: Long,
    val dogId: String,
    val dogName: String,
    val symptom: String,
    val notes: String,
    val latitude: Double?,
    val longitude: Double?,
    val photoUris: List<String>,
    val pdfPath: String,
    val contentUri: String? = null  // ← NEU!
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
                val uri = Uri.fromFile(File(path))
                addPhoto(uri)
            }
        }
    }

    // ✅ NEUEN LAUNCHER HINZUFÜGEN:
    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris: List<Uri> ->
        uris.forEach { uri -> addPhoto(uri) }
    }

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            binding.switchGps.isChecked = true
            requestLocation()
        } else {
            binding.switchGps.isChecked = false
            Toast.makeText(this, "Standortberechtigung erforderlich für GPS-Daten", Toast.LENGTH_SHORT).show()
        }
    }

    private var currentPhotoPath: String? = null
    private var locationListener: LocationListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmergencyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        profile = intent.getParcelableExtra("profile")
        if (profile == null) {
            Toast.makeText(this, "Fehler: Kein Profil gefunden", Toast.LENGTH_SHORT).show()
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
            binding.tvDogBreed.text = "${p.breed}, ${p.gender}, ${p.weight}kg"
            if (p.photoUri.isNotEmpty()) {
                binding.ivDogThumb.setImageURI(Uri.parse(p.photoUri))
            }
        }

        val symptoms = resources.getStringArray(R.array.emergency_symptoms)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, symptoms)
        binding.spinnerSymptoms.setAdapter(adapter)
    }

    private fun setupListeners() {
        binding.btnCamera.setOnClickListener { showCameraDisclosure() }
        binding.btnGallery.setOnClickListener { openPhotoPicker() }
        binding.btnReset.setOnClickListener { resetForm() }
        binding.btnSend.setOnClickListener { generateAndSendReport() }

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

        // Cache listeners
        binding.spinnerSymptoms.setOnItemClickListener { _, _, _, _ -> saveCache() }
        binding.etNotes.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { saveCache() }
        })
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
        if (!notes.isNullOrEmpty()) binding.etNotes.setText(notes)
        photos?.forEach { uriString ->
            try {
                addPhoto(Uri.parse(uriString))
            } catch (e: Exception) {}
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
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("Meldung löschen?")
            .setMessage("Möchtest Du diese Notfallmeldung dauerhaft löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                historyList.remove(report)
                historyAdapter.notifyDataSetChanged()
                saveHistory()
            }
            .setNegativeButton("Abbrechen", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
            }
    }

    private fun showLocationDisclosure() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("📍 Standortzugriff für Notfallmeldung")
            .setMessage("HUNDEWILDNIS - App benötigt Zugriff auf Deinen Standort für folgende Zwecke:\n\n" +
                    "• NOTFALLMELDUNG:\nUm Deine Sicherheit und die Deines Hundes zu erhöhen, kann HundeWildnis Deinen aktuellen GPS-Standort erfassen und in die Notfallmeldung (PDF) einfügen.\n" +
                    "Dies ermöglicht es Tierärzten oder Rettungskräften Euch im Ernstfall schneller zu finden.\n" +
                    "• Die Erfassung erfolgt NUR, wenn Du diesen Schalter aktivierst.\n" +
                    "• Die Daten werden NUR lokal in das PDF-Dokument geschrieben und dem Empfänger der Notfallmeldung übermittelt.\n" +
                    "• Es erfolgt KEINE Übermittlung an HundeWildnis oder Dritte.\n\n\n" +
                    "• TEMPO-MESSUNG:\nUm Deine Geh-Geschwindigkeit im Training präzise zu ermitteln und Dir zu Deiner Auswertung zur Verfügung zu stellen.\n\n" +
                    "• NAVIGATION:\nDamit Du Standort auf der Karte, Routenberechnung zum Ziel und Anzeige von relevanten Orten in der Nähe aufrufen und nutzen kannst.\n\n" +
                    "• STANDORT WETTER:\nDamit Du Wetterdaten und Wettervorhersage an Deinem aktuellen Standort aufrufen und nutzen kannst.\n\n" +
                    "• TAGESNOTIZEN:\nDu kannst Deine Tagesnotizen optional mit Wetterdaten und optional mit den GPS-Daten Deines Standortes ergänzen, um später nachvollziehen zu können, unter welchen Bedingungen Du Deine Beobachtungen zum Training gemacht hast.\n\n" +
                    "WICHTIG:\nDamit die Messung auch bei ausgeschaltetem Bildschirm, wenn die App in den Hintergrund geschoben ist oder in der Tasche ist, stabil funktioniert weil Du während des Trainings und bei Aktivität im Gelände die Hände frei haben musst, nutzt die App einen dauerhaften Dienst (Foreground Service). Dieser wird durch die Benachrichtigung in Deiner Statusleiste angezeigt.\n\n" +
                    "Ohne Aktivierung der Standortdienste, als technische Voraussetzung, kannst Du die Funktionen nicht wie beschrieben nutzen.\n\nDATENSCHUTZ:\nDeine Daten werden nur lokal verarbeitet, ausschliesslich auf Deinem Gerät gespeichert und nur von Dir allein verwaltet und nur auf deinen Wunsch zum Teilenfunktion freigegeben.\n\nZur Nutzung von Navigation und Standortwetter wird der aktuelle Standort anonymisiert an freie Server mitgeteilt, um die gewünschten Daten abzurufen und Dir zur Verfügung zu stellen.\n\nDu kannst die Berechtigungen für Standort jederzeit überprüfen in den Einstellungen:\n\nDein Gerät - Einstellungen - Apps - HundeWildnis - Berechtigungen - Standort verwalten - Zugriff nur während der Nutzung der App zulassen.\n\nDort kann die Berechtigung zum Standort jederzeit dauerhaft geändert, aktiviert oder deaktiviert werden")
            .setPositiveButton("AKTIVIEREN") { _, _ ->
                requestLocation()
            }
            .setNegativeButton("ABLEHNEN") { _, _ ->
                binding.switchGps.isChecked = false
            }
            .setOnCancelListener {
                binding.switchGps.isChecked = false
            }
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
            }
    }

    // ✅ NEUER NAME (nur für Kamera):
    private fun showCameraDisclosure() {
        val prefs = getSharedPreferences("emergency_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("camera_disclosure_shown", false)) {
            checkCameraPermissionAndOpen()
            return
        }

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("📸 Zugriff auf Kamera")
            .setMessage("HundeWildnis benötigt Zugriff auf Deine Kamera für folgende Zwecke:\n\n" +
                    "• NOTFALL-DOKUMENTATION:\n\nDamit Du aktuelle Fotos vom Zustand Deines Hundes oder der Verletzung direkt in die Notfallmeldung (PDF) einfügen kannst.\n\n" +
                    "Dies hilft Tierärzten, die Situation vorab besser einzuschätzen.\n\n" +
                    "DATENSCHUTZ:\n• Deine Fotos werden NUR lokal in das PDF-Dokument eingebunden.\n" +
                    "• Es erfolgt KEINE Übermittlung an andere Server.\n" +
                    "• Nur Du allein entscheidest im Sendevorgang über das Teilen der Notfalldaten incl. beigefügter Fotos.\n" +
                    "• Der Zugriff erfolgt NUR nach Deiner expliziten Auslösung.\n\n" +
                    "Möchtest Du den Zugriff auf die Kamera jetzt aktivieren?")
            .setPositiveButton("AKTIVIEREN") { _, _ ->
                prefs.edit().putBoolean("camera_disclosure_shown", true).apply()
                checkCameraPermissionAndOpen()
            }
            .setNegativeButton("ABLEHNEN", null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
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
            Toast.makeText(this, "Bitte aktiviere GPS in Deinen Einstellungen", Toast.LENGTH_LONG).show()
            binding.switchGps.isChecked = false
            return
        }

        binding.tvGpsStatus.visibility = View.VISIBLE
        binding.tvGpsStatus.text = "⏳ Suche Standort..."

        val providers = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l = locationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                bestLocation = l
            }
        }
        
        if (bestLocation != null) {
            updateLocationUI(bestLocation)
        }

        // Always register listener to get a fresh/better location
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (bestLocation == null || location.accuracy <= (bestLocation?.accuracy ?: Float.MAX_VALUE)) {
                    updateLocationUI(location)
                    bestLocation = location
                }
                // Stop updates once we have a decent accuracy (optional)
                if (location.accuracy < 30) {
                    locationManager.removeUpdates(this)
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        locationListener = listener
        
        try {
            if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener)
            }
            if (locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
            }
        } catch (e: Exception) {
            binding.tvGpsStatus.text = "⚠️ Fehler beim Erfassen des Standorts"
        }
    }

    private fun updateLocationUI(location: Location) {
        lastLocation = location
        binding.tvGpsStatus.apply {
            visibility = View.VISIBLE
            text = "📍 Standort erfasst: ${location.latitude}, ${location.longitude}"
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
            Toast.makeText(this, "Bitte wählen Sie ein Symptom aus!", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@EmergencyActivity, "✅ PDF gespeichert in Documents/HundeWildnis", Toast.LENGTH_LONG).show()
                    }

                    if (contentUri != null) {
                        shareReportViaUri(contentUri)
                    } else if (pdfFile != null) {
                        shareReport(pdfFile)
                    }

                    clearCache()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EmergencyActivity, "Fehler beim Erstellen der PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ✅ OPTIMIERTE createEmergencyPdf() FUNKTION
    // Ersetze die Funktion in EmergencyActivity.kt
    // ════════════════════════════════════════════════════════════════

    private fun createEmergencyPdf(): Pair<File?, Uri?> {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMAN).format(Date())
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
                if (columnIndex >= 0) {
                    filePath = cursor.getString(columnIndex)
                }
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

    private fun writePdfContent(document: Document) {
        document.add(Paragraph("🚨 NOTFALLMELDUNG - HUND")
            .setFontSize(22f)
            .setFontColor(DeviceRgb(176, 0, 32))
            .setTextAlignment(TextAlignment.CENTER))

        document.add(Paragraph("Erstellt am: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date())}")
            .setTextAlignment(TextAlignment.CENTER))

        document.add(Paragraph("Erstellt und abgesendet mit HundeWildnis - Android App von www.wildnislehrer.de")
            .setFontSize(10f)
            .setTextAlignment(TextAlignment.CENTER))

        document.add(Paragraph("\n"))

        document.add(Paragraph("MEDIZINISCHER NOTFALL:")
            .setBold()
            .setFontSize(16f))

        document.add(Paragraph("Symptom: ${binding.spinnerSymptoms.text}")
            .setFontSize(14f))

        document.add(Paragraph("Zusatz-Info: ${binding.etNotes.text}")
            .setFontSize(12f))

        lastLocation?.let {
            val osmUrl = "https://www.openstreetmap.org/?mlat=${it.latitude}&mlon=${it.longitude}#map=18/${it.latitude}/${it.longitude}"
            document.add(Paragraph("Standort (GPS): Lat ${it.latitude}, Lon ${it.longitude}")
                .setBold())
            document.add(Paragraph("Karte (OpenStreetMap): $osmUrl")
                .setFontColor(DeviceRgb(0, 0, 255)))
        }

        document.add(Paragraph("\n"))

        document.add(Paragraph("STAMMDATEN DES PATIENTEN:")
            .setBold()
            .setFontSize(16f))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(30f, 70f))).useAllAvailableWidth()

        profile?.let { p ->
            table.addCell(Cell().add(Paragraph("Name:")))
            table.addCell(Cell().add(Paragraph(p.name)))

            table.addCell(Cell().add(Paragraph("Rasse:")))
            table.addCell(Cell().add(Paragraph(p.breed)))

            table.addCell(Cell().add(Paragraph("Geburtsdatum:")))
            table.addCell(Cell().add(Paragraph(p.birthDate)))

            table.addCell(Cell().add(Paragraph("Geschlecht:")))
            table.addCell(Cell().add(Paragraph(p.gender)))

            table.addCell(Cell().add(Paragraph("Gewicht:")))
            table.addCell(Cell().add(Paragraph("${p.weight} kg")))

            if (p.shoulderHeight.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph("Schulterhöhe:")))
                table.addCell(Cell().add(Paragraph("${p.shoulderHeight} cm")))
            }

            if (p.bodyLength.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph("Körperlänge:")))
                table.addCell(Cell().add(Paragraph("${p.bodyLength} cm")))
            }

            if (p.chipNumber.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph("Chip-Nr:")))
                table.addCell(Cell().add(Paragraph(p.chipNumber)))
            }

            if (p.vaccinationStatus.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph("Impfstatus:")))
                table.addCell(Cell().add(Paragraph(p.vaccinationStatus)))
            }

            if (p.medicalNotes.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph("Vorerkrankungen:")))
                table.addCell(Cell().add(Paragraph(p.medicalNotes)))
            }

            if (p.owner.isNotEmpty()) {
                table.addCell(Cell().add(Paragraph("Besitzer:")))
                table.addCell(Cell().add(Paragraph(p.owner)))
            }
        }

        document.add(table)

        if (attachedPhotos.isNotEmpty()) {
            document.add(AreaBreak())

            document.add(Paragraph("FOTOS VOM NOTFALL / PATIENTEN:")
                .setBold()
                .setFontSize(16f))

            document.add(Paragraph("\n"))

            var photoTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
                .useAllAvailableWidth()
                .setMarginTop(10f)

            var imageCounter = 0

            for (uri in attachedPhotos) {
                try {
                    val inputStream = contentResolver.openInputStream(uri) ?: continue
                    val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: continue
                    inputStream?.close()

                    val maxSize = 800
                    val scaledBitmap = if (originalBitmap.width > maxSize || originalBitmap.height > maxSize) {
                        val scale = minOf(
                            maxSize.toFloat() / originalBitmap.width,
                            maxSize.toFloat() / originalBitmap.height
                        )
                        val newWidth = (originalBitmap.width * scale).toInt()
                        val newHeight = (originalBitmap.height * scale).toInt()
                        Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                    } else {
                        originalBitmap
                    }

                    val stream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)

                    val img = Image(ImageDataFactory.create(stream.toByteArray()))
                        .setAutoScale(true)

                    val cell = Cell().add(img)
                    cell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    cell.setPadding(10f)
                    photoTable.addCell(cell)

                    imageCounter++

                    if (scaledBitmap != originalBitmap) {
                        scaledBitmap.recycle()
                    }
                    originalBitmap.recycle()

                    if (imageCounter % 2 == 0 && imageCounter < attachedPhotos.size) {
                        document.add(photoTable)
                        document.add(AreaBreak())

                        photoTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
                            .useAllAvailableWidth()
                            .setMarginTop(10f)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
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

    private fun shareReport(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        
        val shareText = StringBuilder().apply {
            append("🚨 NOTFALLMELDUNG 🚨\n")
            append("Hund: ${profile?.name}\n")
            append("Symptom: ${binding.spinnerSymptoms.text}\n")
            append("Info: ${binding.etNotes.text}\n")
            lastLocation?.let { 
                append("Standort (GPS): Lat ${it.latitude}, Lon ${it.longitude}\n")
                append("Karte (OSM): https://www.openstreetmap.org/?mlat=${it.latitude}&mlon=${it.longitude}#map=18/${it.latitude}/${it.longitude}\n")
            }
            append("\nMedizinische Daten im PDF-Anhang.")
        }.toString()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "🚨 NOTFALLMELDUNG: ${profile?.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Notfallmeldung teilen via..."))
    }

    private fun shareReportViaUri(uri: Uri) {
        val shareText = StringBuilder().apply {
            append("🚨 NOTFALLMELDUNG 🚨\n")
            append("Hund: ${profile?.name}\n")
            append("Symptom: ${binding.spinnerSymptoms.text}\n")
            append("Info: ${binding.etNotes.text}\n")
            lastLocation?.let {
                append("Standort (GPS): Lat ${it.latitude}, Lon ${it.longitude}\n")
                append("Karte (OSM): https://www.openstreetmap.org/?mlat=${it.latitude}&mlon=${it.longitude}#map=18/${it.latitude}/${it.longitude}\n")
            }
            append("\nMedizinische Daten im PDF-Anhang.")
        }.toString()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "🚨 NOTFALLMELDUNG: ${profile?.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Notfallmeldung teilen via..."))
    }

    private fun openPdf(report: EmergencyReport) {
        try {
            if (!report.contentUri.isNullOrEmpty()) {
                val uri = Uri.parse(report.contentUri)
                openPdfViaUri(uri)
                return
            }

            if (report.pdfPath.isNotEmpty()) {
                val file = File(report.pdfPath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                    openPdfViaUri(uri)
                    return
                }
            }

            Toast.makeText(this, "PDF nicht gefunden", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
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
            val packageName = resolveInfo.activityInfo.packageName
            grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        startActivity(Intent.createChooser(intent, "PDF öffnen"))
    }

    private fun openPdf(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "PDF nicht gefunden: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            val resInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

            for (resolveInfo in resInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                grantUriPermission(
                    pkgName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            startActivity(Intent.createChooser(intent, "PDF öffnen"))

        } catch (e: Exception) {
            Toast.makeText(this, "Fehler: ${e.message}\n\nPfad: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    inner class EmergencyHistoryAdapter(
        private val reports: List<EmergencyReport>,
        private val onItemClick: (EmergencyReport) -> Unit,
        private val onDeleteClick: (EmergencyReport) -> Unit
    ) : RecyclerView.Adapter<EmergencyHistoryAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemEmergencyReportBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemEmergencyReportBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val report = reports[position]
            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(report.timestamp))
            holder.binding.tvReportDate.text = date
            holder.binding.tvReportSummary.text = "Symptom: ${report.symptom}"
            holder.binding.reportClickArea.setOnClickListener { onItemClick(report) }
            holder.binding.btnDeleteReport.setOnClickListener { onDeleteClick(report) }
        }

        override fun getItemCount() = reports.size
    }
}
