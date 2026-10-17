package com.hundewildnis.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hundewildnis.app.databinding.ActivityDailyNotesBinding
import com.hundewildnis.app.databinding.ItemNoteSessionBinding
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Data Class für gespeicherte Notizen
 */
@Keep
data class NoteSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val title: String = "",
    val notes: String = "",
    val photoUris: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val weatherInfo: String? = null
)

class DailyNotesActivity : SecureBaseActivity(), LocationListener {

    private lateinit var binding: ActivityDailyNotesBinding
    private val photos = mutableListOf<Uri>()
    private lateinit var photoAdapter: PhotoAdapter
    private var currentPhotoUri: Uri? = null

    private lateinit var locationManager: LocationManager

    // Historie
    private val historyList = mutableListOf<NoteSession>()
    private lateinit var historyAdapter: HistoryAdapter
    private var currentSessionId: String? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var currentWeatherInfo: String? = null

    // NEU: Merkt sich, ob der Nutzer Wetterdaten angefordert hat, während noch kein Standort da war
    private var pendingWeatherRequest = false
    private var hasExplicitLocation = false   // NEU: Nur true, wenn GPS-Button bewusst gedrückt wurde

    // Permission Launcher
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else Toast.makeText(this, getString(R.string.start_camera_permission_required), Toast.LENGTH_SHORT).show()
    }

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            requestLocationUpdates()
            Toast.makeText(this, "📍 " + getString(R.string.location_access_allowed), Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoUri != null) importPhotos(listOf(currentPhotoUri!!))
    }

    // ✅ SINGLE PHOTO PICKER
    private val singlePhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            importPhotos(listOf(it))
        }
    }

    // ✅ MULTIPLE PHOTOS PICKER
    private val multiplePhotoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
        }
        if (uris.isNotEmpty()) importPhotos(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply { title = getString(R.string.notes_toolbar_title); setDisplayHomeAsUpEnabled(true) }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setupPhotoRecyclerView()
        setupHistoryRecyclerView()
        setupUI()
        loadHistory()

        startNewNote()
    }

    private fun setupUI() {
        setupGrids()
    }

    private fun setupGrids() {
        // Bedingungen (Wetter/GPS)
        val conditionTiles = listOf(
            StartTile("note_weather", R.drawable.ic_weather, getString(R.string.notes_tile_weather)),
            StartTile("note_gps", R.drawable.ic_navigation, getString(R.string.notes_tile_gps))
        )
        val colorsConditions = StartGroupColors.forGroup(this, 3) // Ocker
        binding.gridNoteConditions.layoutManager = GridLayoutManager(this, 2)
        binding.gridNoteConditions.adapter = StartTileAdapter(conditionTiles, colorsConditions.chipColorRes, colorsConditions.accentColorRes) { id ->
            if (id == "note_weather") addWeatherToNote() else addLocationToNote()
        }

        // Fotos
        val photoTiles = listOf(
            StartTile("note_camera", R.drawable.ic_microphone, getString(R.string.notes_tile_camera)), // microphone icon used as placeholder for camera if ic_camera is missing
            StartTile("note_gallery", R.drawable.ic_pages, getString(R.string.notes_tile_gallery))
        )
        val colorsPhotos = StartGroupColors.forGroup(this, 4) // Blau
        binding.gridNotePhotos.layoutManager = GridLayoutManager(this, 2)
        binding.gridNotePhotos.adapter = StartTileAdapter(photoTiles, colorsPhotos.chipColorRes, colorsPhotos.accentColorRes) { id ->
            if (id == "note_camera") showCameraDisclosure() else openMultiplePhotoPicker()
        }

        // Speichern
        val saveTiles = listOf(
            StartTile("note_save", R.drawable.ic_notes, getString(R.string.notes_tile_save))
        )
        val colorsSave = StartGroupColors.forGroup(this, 2) // Grün
        binding.gridNoteSave.layoutManager = GridLayoutManager(this, 1)
        binding.gridNoteSave.adapter = StartTileAdapter(saveTiles, colorsSave.chipColorRes, colorsSave.accentColorRes) { saveToHistory(true) }

        // Aktionen
        val actionTiles = listOf(
            StartTile("note_pdf", R.drawable.ic_notes, "PDF"),
            StartTile("note_gpx", R.drawable.ic_navigation, "GPX"),
            StartTile("note_share", R.drawable.ic_map_pin_share, getString(R.string.walking_speed_share_log)),
            StartTile("note_clear", R.drawable.ic_remove, getString(R.string.notes_tile_delete), isDanger = true)
        )
        val colorsActions = StartGroupColors.forGroup(this, 1) // Gelb
        binding.gridNoteActions.layoutManager = GridLayoutManager(this, 4)
        binding.gridNoteActions.adapter = StartTileAdapter(actionTiles, colorsActions.chipColorRes, colorsActions.accentColorRes) { id ->
            when(id) {
                "note_pdf" -> { saveToHistory(false); exportAsPdf(false) }
                "note_gpx" -> { saveToHistory(false); exportAsGpx() }
                "note_share" -> { saveToHistory(false); shareNotes() }
                "note_clear" -> confirmDeleteDraft()
            }
        }
    }

    private fun confirmDeleteDraft() {

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.notes_confirm_delete_draft_title))
            .setMessage(getString(R.string.notes_confirm_delete_draft_msg))
            .setPositiveButton(getString(R.string.start_delete)) { _, _ ->
                photos.forEach { deletePhotoFile(it) }
                startNewNote()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().apply {
            }
    }

    private fun openPhotoPicker() {
        singlePhotoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun openMultiplePhotoPicker() {
        multiplePhotoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // NEU: Wiederverwendbarer Dialog
    private fun showProminentDisclosure() {

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.notes_location_disclosure_title))
            .setMessage(getString(R.string.notes_location_disclosure_msg))
            .setPositiveButton(getString(R.string.start_notification_disclosure_accept)) { _, _ ->
                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .apply {
                show()
            }
    }

    private fun showCameraDisclosure() {
        val prefs = getSharedPreferences("daily_notes_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("camera_disclosure_shown", false)) {
            checkCameraPermissionAndOpen()
            return
        }


        AlertDialog.Builder(this)
            .setTitle(getString(R.string.notes_camera_disclosure_title))
            .setMessage(getString(R.string.notes_camera_disclosure_msg))
            .setPositiveButton(getString(R.string.start_notification_disclosure_accept)) { _, _ ->
                prefs.edit().putBoolean("camera_disclosure_shown", true).apply()
                checkCameraPermissionAndOpen()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
            .apply {
                show()
            }
    }

    // Angepasste Methode für GPS-Button
    private fun addLocationToNote() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showProminentDisclosure()
            return
        }
        pendingWeatherRequest = false
        hasExplicitLocation = true
        requestLocationUpdates()
        Toast.makeText(this, getString(R.string.notes_location_fetching), Toast.LENGTH_SHORT).show()
    }

    private fun addWeatherToNote() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showProminentDisclosure()
            return
        }

        if (currentLatitude == null || currentLongitude == null) {
            pendingWeatherRequest = true
            hasExplicitLocation = false        // Wichtig: Kein expliziter GPS-Eintrag
            requestLocationUpdates()
            Toast.makeText(this, getString(R.string.notes_location_fetching_weather), Toast.LENGTH_SHORT).show()
            return
        }
    }

    private fun requestLocationUpdates() {
        try {
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

            if (lastKnown != null) {
                // ✅ WICHTIG: NUR temporär speichern, wenn Wetter-Button!
                if (!hasExplicitLocation) {
                    // Temporär for Wetter-Abruf
                    val tempLat = lastKnown.latitude
                    val tempLon = lastKnown.longitude

                    if (pendingWeatherRequest) {
                        // Wetter abrufen mit temporären Koordinaten
                        lifecycleScope.launch {
                            fetchWeatherDataWithCoords(tempLat, tempLon)
                        }
                    }
                } else {
                    // GPS-Button → Daten speichern
                    currentLatitude = lastKnown.latitude
                    currentLongitude = lastKnown.longitude
                    updateDateDisplay(System.currentTimeMillis())
                    saveToHistory(false)
                }
                return
            }

            // Kein gecachter Standort → aktive Abfrage
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null)

        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.walking_speed_location_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLocationChanged(location: Location) {
        // ✅ WICHTIG: Unterscheiden zwischen GPS-Button und Wetter-Button!
        if (pendingWeatherRequest) {
            // Wetter-Button → nur temporär nutzen
            lifecycleScope.launch {
                fetchWeatherDataWithCoords(location.latitude, location.longitude)
            }
        } else {
            // GPS-Button → Daten dauerhaft speichern
            currentLatitude = location.latitude
            currentLongitude = location.longitude
            updateDateDisplay(System.currentTimeMillis())
            saveToHistory(false)
        }
    }

    private suspend fun fetchWeatherDataWithCoords(lat: Double, lon: Double) {
        pendingWeatherRequest = false

        try {
            val weather = withContext(Dispatchers.IO) {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_direction_10m"
                val response = URL(url).readText()
                val json = JSONObject(response)
                val current = json.getJSONObject("current")

                val temp = current.getDouble("temperature_2m")
                val hum = current.getInt("relative_humidity_2m")
                val precip = current.getDouble("precipitation")
                val code = current.getInt("weather_code")
                val windSpeed = current.getDouble("wind_speed_10m")
                val windDir = current.getInt("wind_direction_10m")

                val desc = getWeatherDescription(code)
                val windDirStr = getWindDirection(windDir)

                val tempStr = String.format(Locale.US, "%.1f", temp)
                val windStr = String.format(Locale.US, "%.1f", windSpeed)
                val precipStr = String.format(Locale.US, "%.1f", precip)

                getString(R.string.notes_weather_format, tempStr, desc, windStr, windDirStr, hum, precipStr)
            }

            withContext(Dispatchers.Main) {
                currentWeatherInfo = weather
                binding.tvWeatherInfo.text = weather
                binding.tvWeatherInfo.visibility = View.VISIBLE

                // ✅ GPS-Koordinaten NICHT speichern (bleiben null)
                // currentLatitude bleibt null
                // currentLongitude bleibt null

                saveToHistory(false)

                Toast.makeText(this@DailyNotesActivity, getString(R.string.notes_weather_added), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@DailyNotesActivity, getString(R.string.notes_weather_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startNewNote() {
        currentSessionId = UUID.randomUUID().toString()
        binding.etTitle.setText("")
        binding.etNotes.setText("")
        photos.clear()
        currentWeatherInfo = null
        currentLatitude = null // GPS optional machen
        currentLongitude = null
        pendingWeatherRequest = false
        hasExplicitLocation = false  // ✅ Diese Zeile hinzufügen!
        binding.tvWeatherInfo.visibility = View.GONE
        photoAdapter.notifyDataSetChanged()
        updatePhotoVisibility()
        updateDateDisplay(System.currentTimeMillis())
    }

    private fun updateDateDisplay(timestamp: Long) {
        val dateText = SimpleDateFormat("EEEE, dd. MMMM yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
        val locText = if (currentLatitude != null) "\n📍 ${String.format(Locale.US, "%.4f", currentLatitude)}, ${String.format(Locale.US, "%.4f", currentLongitude)}" else ""
        binding.tvDate.text = dateText + locText
    }

    private fun getWindDirection(degrees: Int): String {
        val directions = arrayOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
        return directions[((degrees + 22.5) / 45).toInt() % 8]
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> getString(R.string.notes_weather_clear_sky)
            1, 2, 3 -> getString(R.string.notes_weather_partly_cloudy)
            45, 48 -> getString(R.string.notes_weather_fog)
            51, 53, 55 -> getString(R.string.notes_weather_drizzle)
            61, 63, 65 -> getString(R.string.notes_weather_rain)
            71, 73, 75 -> getString(R.string.notes_weather_snow)
            80, 81, 82 -> getString(R.string.notes_weather_showers)
            95 -> getString(R.string.notes_weather_thunderstorm)
            else -> getString(R.string.notes_weather_changeable)
        }
    }

    // --- REST DER KLASSE (BILDER/EXPORT/HISTORIE) ---
    private fun setupPhotoRecyclerView() {
        photoAdapter = PhotoAdapter(photos) { position ->
            deletePhotoFile(photos[position])
            photoAdapter.removePhoto(position)
            updatePhotoVisibility()
            saveToHistory(false)
        }
        binding.rvPhotos.apply { layoutManager = GridLayoutManager(this@DailyNotesActivity, 3); adapter = photoAdapter }
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter = HistoryAdapter(historyList,
            onItemClick = { session -> displaySession(session) },
            onDeleteClick = { session -> confirmDelete(session) }
        )
        binding.rvHistory.apply { layoutManager = LinearLayoutManager(this@DailyNotesActivity); adapter = historyAdapter }
    }

    private fun addPhoto(uri: Uri) {
        photoAdapter.addPhoto(uri)
        updatePhotoVisibility()
    }

    /**
     * Übernimmt neue Fotos verkleinert (max. 1280 px) in den App-Speicher und hängt sie an die Notiz.
     * Lässt sich ein Foto nicht verkleinern, wird die ursprüngliche URI verwendet.
     */
    private fun importPhotos(sources: List<Uri>) {
        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                sources.map { PhotoCompressor.importPhoto(this@DailyNotesActivity, it) ?: it }
            }
            imported.forEach { addPhoto(it) }
            saveToHistory(false)
        }
    }

    private fun updatePhotoVisibility() {
        binding.rvPhotos.visibility = if (photos.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun deletePhotoFile(uri: Uri) {
        // Eigene, verkleinerte Kopie: Datei löschen. Sonst wie bisher über den Content-Provider.
        if (PhotoCompressor.deleteOwnPhoto(this, uri)) return
        try { contentResolver.delete(uri, null, null) } catch (e: Exception) {}
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        val fileName = "HundeWildnis_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, fileName); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HundeWildnis") }
            currentPhotoUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            currentPhotoUri?.let { cameraLauncher.launch(it) }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "HundeWildnis")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            currentPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            cameraLauncher.launch(currentPhotoUri!!)
        }
    }

    private fun saveHistoryToPrefs() {
        getSharedPreferences("notes_history", Context.MODE_PRIVATE).edit().putString("sessions", Gson().toJson(historyList)).apply()
    }

    private fun loadHistory() {
        val json = getSharedPreferences("notes_history", Context.MODE_PRIVATE).getString("sessions", null)
        if (json != null) {
            val type = object : TypeToken<List<NoteSession>>() {}.type
            val loaded: List<NoteSession> = Gson().fromJson(json, type)
            historyList.clear()
            historyList.addAll(loaded.sortedByDescending { it.date })
            historyAdapter.notifyDataSetChanged()
        }
    }

    private fun confirmDelete(session: NoteSession) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.notes_confirm_delete_history_title))
            .setMessage(getString(R.string.notes_confirm_delete_history_msg))
            .setPositiveButton(getString(R.string.start_delete)) { _, _ ->
                val index = historyList.indexOfFirst { it.id == session.id }
                if (index != -1) {
                    historyList.removeAt(index)
                    historyAdapter.notifyItemRemoved(index)
                    saveHistoryToPrefs()
                    if (currentSessionId == session.id) startNewNote()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show().apply {
            }
    }

    private fun displaySession(session: NoteSession) {
        currentSessionId = session.id
        binding.etTitle.setText(session.title)
        binding.etNotes.setText(session.notes)
        photos.clear()
        session.photoUris.forEach { photos.add(Uri.parse(it)) }
        photoAdapter.notifyDataSetChanged()
        updatePhotoVisibility()
        currentLatitude = session.latitude
        currentLongitude = session.longitude
        currentWeatherInfo = session.weatherInfo
        if (currentWeatherInfo != null) {
            binding.tvWeatherInfo.text = currentWeatherInfo
            binding.tvWeatherInfo.visibility = View.VISIBLE
        } else {
            binding.tvWeatherInfo.visibility = View.GONE
        }
        updateDateDisplay(session.date)
        binding.appbar.setExpanded(true)
    }

    private fun saveToHistory(isManualNew: Boolean) {
        val title = binding.etTitle.text.toString()
        val notes = binding.etNotes.text.toString()
        if (title.isEmpty() && notes.isEmpty() && photos.isEmpty()) return

        // Datum beibehalten bei Updates, sonst neu
        val existingDate = historyList.find { it.id == currentSessionId }?.date
        val session = NoteSession(
            id = currentSessionId ?: UUID.randomUUID().toString(),
            date = existingDate ?: System.currentTimeMillis(),
            title = title,
            notes = notes,
            photoUris = photos.map { it.toString() },
            latitude = currentLatitude,
            longitude = currentLongitude,
            weatherInfo = currentWeatherInfo
        )
        val index = historyList.indexOfFirst { it.id == session.id }
        if (index != -1) historyList[index] = session else historyList.add(0, session)

        // Immer chronologisch sortieren (neueste oben)
        historyList.sortByDescending { it.date }

        saveHistoryToPrefs()
        historyAdapter.notifyDataSetChanged()
        if (isManualNew) {
            startNewNote()
            Toast.makeText(this, getString(R.string.notes_saved_success), Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportAsPdf(share: Boolean) {
        val title = binding.etTitle.text.toString().ifEmpty { getString(R.string.notes_default_pdf_title) }
        val notes = binding.etNotes.text.toString()
        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
        val fileName = "Notiz_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(timestamp))}.pdf"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val appDir = getAppDocumentsFolder()
                val file = File(appDir, fileName)

                val writer = PdfWriter(FileOutputStream(file))
                val pdf = PdfDocument(writer)
                val document = Document(pdf)

                // ═══════════════════════════════════════════════════════
                // HEADER
                // ═══════════════════════════════════════════════════════
                document.add(Paragraph(getString(R.string.notes_pdf_header))
                    .setFontSize(20f)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER))

                document.add(Paragraph(getString(R.string.notes_pdf_created_at, dateStr))
                    .setTextAlignment(TextAlignment.CENTER))

                document.add(Paragraph(getString(R.string.notes_pdf_footer))
                    .setFontSize(10f)
                    .setTextAlignment(TextAlignment.CENTER))

                document.add(Paragraph("\n"))

                // ═══════════════════════════════════════════════════════
                // TITEL
                // ═══════════════════════════════════════════════════════
                if (title.isNotEmpty()) {
                    document.add(Paragraph(getString(R.string.notes_pdf_label_title, title)).setBold().setFontSize(16f))
                }

                // ═══════════════════════════════════════════════════════
                // STANDORT (mit OpenStreetMap-Link!)
                // ═══════════════════════════════════════════════════════
                if (currentLatitude != null && currentLongitude != null) {
                    val osmUrl = "https://www.openstreetmap.org/?mlat=$currentLatitude&mlon=$currentLongitude#map=18/$currentLatitude/$currentLongitude"
                    val latStr = String.format(Locale.US, "%.5f", currentLatitude)
                    val lonStr = String.format(Locale.US, "%.5f", currentLongitude)
                    document.add(Paragraph(getString(R.string.notes_pdf_label_location, latStr, lonStr)).setBold())
                    document.add(Paragraph(getString(R.string.notes_pdf_label_map, osmUrl))
                        .setFontColor(DeviceRgb(0, 0, 255))
                        .setFontSize(10f))
                }

                // ═══════════════════════════════════════════════════════
                // WETTER
                // ═══════════════════════════════════════════════════════
                if (currentWeatherInfo != null) {
                    document.add(Paragraph(getString(R.string.notes_pdf_label_weather, currentWeatherInfo)))
                }

                // ═══════════════════════════════════════════════════════
                // NOTIZEN
                // ═══════════════════════════════════════════════════════
                document.add(Paragraph("\n" + getString(R.string.notes_pdf_label_notes)).setBold())
                document.add(Paragraph(notes))

                // ═══════════════════════════════════════════════════════
                // FOTOS - ✅ INTELLIGENTE PLATZIERUNG (INTELLIGENTE SEITEN-NUTZUNG)
                // ═══════════════════════════════════════════════════════
                if (photos.isNotEmpty()) {
                    // ✅ Prüfe ob genug Platz auf aktueller Seite
                    // Berechnung: Header (~150pt) + Titel/GPS/Wetter (~100pt) + Notizen (~20pt pro Zeile)
                    val estimatedContentHeight = 150f + 100f + (notes.length / 60f * 20f)
                    val pageHeight = 842f  // A4 Höhe in Punkten
                    val availableHeight = pageHeight - estimatedContentHeight - 50f  // 50pt Margin

                    val needsNewPage = availableHeight < 200f  // Mindestens 200pt für Fotos nötig

                    if (needsNewPage) {
                        // Neue Seite für Fotos
                        document.add(AreaBreak())
                    }

                    document.add(Paragraph("\n" + getString(R.string.notes_pdf_label_photos)).setBold().setFontSize(14f))

                    // ✅ Fotos in 3-Spalten-Tabelle
                    val photoTable = Table(UnitValue.createPercentArray(floatArrayOf(33f, 33f, 33f)))
                        .useAllAvailableWidth()
                        .setMarginTop(10f)

                    for (uri in photos) {
                        try {
                            val inputStream = contentResolver.openInputStream(uri) ?: continue
                            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: continue
                            inputStream?.close()

                            // ✅ SKALIERUNG: Maximal 400x400 px (KLEINERE PDF-DATEIGRÖSSE)
                            val maxSize = 400
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

                            // ✅ KOMPRIMIERUNG: 70% JPEG (KLEINERE PDF-DATEIGRÖSSE, SCHNELLERE GENERIERUNG)
                            val stream = ByteArrayOutputStream()
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)

                            val img = Image(ImageDataFactory.create(stream.toByteArray()))
                                .setAutoScale(true)

                            val cell = Cell().add(img)
                            cell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                            cell.setPadding(5f)
                            photoTable.addCell(cell)

                            // ✅ MEMORY CLEANUP (WENIGER SPEICHER-VERBRAUCH)
                            if (scaledBitmap != originalBitmap) {
                                scaledBitmap.recycle()
                            }
                            originalBitmap.recycle()

                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    document.add(photoTable)
                }

                document.close()

                withContext(Dispatchers.Main) {
                    MediaScannerConnection.scanFile(
                        this@DailyNotesActivity,
                        arrayOf(file.absolutePath),
                        arrayOf("application/pdf"),
                        null
                    )

                    if (share) {
                        sharePdfFile(file)
                    } else {
                        Toast.makeText(
                            this@DailyNotesActivity,
                            getString(R.string.notes_pdf_saved_success, file.name),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@DailyNotesActivity,
                        getString(R.string.notes_pdf_error, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun exportAsGpx() {
        if (currentLatitude == null || currentLongitude == null) {
            Toast.makeText(this, getString(R.string.notes_gpx_error_no_location), Toast.LENGTH_SHORT).show()
            return
        }

        val title = binding.etTitle.text.toString().ifEmpty { getString(R.string.notes_default_pdf_title) }
        val timestamp = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timeStr = sdf.format(Date(timestamp))

        val fileName = "Notiz_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(timestamp))}.gpx"

        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="HundeWildnis" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata>
                <time>$timeStr</time>
              </metadata>
              <wpt lat="$currentLatitude" lon="$currentLongitude">
                <time>$timeStr</time>
                <name>$title</name>
                <desc>${binding.etNotes.text}</desc>
              </wpt>
            </gpx>
        """.trimIndent()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val appDir = getAppDocumentsFolder()
                val file = File(appDir, fileName)
                file.writeText(gpx)

                withContext(Dispatchers.Main) {
                    MediaScannerConnection.scanFile(this@DailyNotesActivity, arrayOf(file.absolutePath), arrayOf("application/gpx+xml"), null)
                    Toast.makeText(this@DailyNotesActivity, getString(R.string.notes_gpx_saved_success), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DailyNotesActivity, getString(R.string.walking_speed_export_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareNotes() {
        val title = binding.etTitle.text.toString().ifEmpty { getString(R.string.notes_default_pdf_title) }
        val notes = binding.etNotes.text.toString()
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())

        // ✅ VERBESSERTE FORMATIERUNG mit Leerzeilen zwischen Abschnitten
        val shareText = StringBuilder().apply {
            append("📝 HundeWildnis - " + getString(R.string.notes_default_pdf_title) + "\n")
            append(getString(R.string.notes_date_label) + ": $dateStr\n\n")

            if (title.isNotEmpty()) {
                append(getString(R.string.notes_title_label) + ": $title\n\n")
            }

            if (currentWeatherInfo != null) {
                append("🌤️ " + getString(R.string.notes_tile_weather) + ":\n$currentWeatherInfo\n\n")
            }

            if (currentLatitude != null && currentLongitude != null) {
                // ✅ OpenStreetMap-Link hinzugefügt
                val osmUrl = "https://www.openstreetmap.org/?mlat=$currentLatitude&mlon=$currentLongitude#map=18/$currentLatitude/$currentLongitude"
                append("📍 " + getString(R.string.notes_pdf_label_location) + ":\n")
                append(getString(R.string.nasenarbeit_pdf_label_coordinates) + ": $currentLatitude, $currentLongitude\n")
                append(getString(R.string.nasenarbeit_pdf_label_map) + ": $osmUrl\n\n")
            }

            append(getString(R.string.notes_pdf_label_notes) + ":\n$notes")
        }.toString()

        if (photos.isEmpty()) {
            // ✅ NUR TEXT - funktioniert überall
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.notes_share_chooser_title)))

        } else {
            // ✅ MIT BILDERN - Zeige Auswahl-Dialog für User-Kontrolle

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.notes_share_dialog_title))
                .setMessage(getString(R.string.notes_share_dialog_msg, photos.size))
                .setPositiveButton(getString(R.string.notes_share_btn_text_photos)) { _, _ ->
                    shareTextAndPhotos(shareText, title)
                }
                .setNeutralButton(getString(R.string.notes_share_btn_text_only)) { _, _ ->
                    val textIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        putExtra(Intent.EXTRA_SUBJECT, title)
                    }
                    startActivity(Intent.createChooser(textIntent, getString(R.string.notes_share_chooser_title)))
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .create()
                .apply {
                    show()
                }
        }
    }

    /**
     * ✅ NEUE HILFSFUNKTION: Füge diese Funktion NACH shareNotes() hinzu
     */
    private fun shareTextAndPhotos(shareText: String, title: String) {
        if (photos.size == 1) {
            // ✅ EIN BILD - Text als Caption
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, photos[0])
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.notes_share_with_photo_title)))

        } else {
            // ✅ MEHRERE BILDER - Zeige DIALOG statt Toast

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.notes_share_warning_title))
                .setMessage(getString(R.string.notes_share_warning_msg, photos.size))
                .setPositiveButton(getString(R.string.notes_share_warning_btn_continue)) { _, _ ->
                    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(photos))
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        putExtra(Intent.EXTRA_SUBJECT, title)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.notes_share_multi_photo_title, photos.size)))
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .create()
                .apply {
                    show()
                }
        }
    }

    private fun getAppDocumentsFolder(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val appDir = File(docsDir, "HundeWildnis")
        if (!appDir.exists()) appDir.mkdirs()
        return appDir
    }

    private fun sharePdfFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.notes_share_chooser_title)))
    }

    override fun onLocationChanged(locations: MutableList<Location>) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    // ADAPTER
    inner class HistoryAdapter(
        private val sessions: List<NoteSession>,
        private val onItemClick: (NoteSession) -> Unit,
        private val onDeleteClick: (NoteSession) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemNoteSessionBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemNoteSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]
            holder.binding.tvNoteDate.text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(session.date))
            holder.binding.tvNoteSnippet.text = if (session.title.isNotEmpty()) session.title else getString(R.string.notes_no_title)

            if (session.latitude != null && session.longitude != null) {
                holder.binding.tvNoteLocation.text = String.format(Locale.US, "📍 %.4f, %.4f", session.latitude, session.longitude)
                holder.binding.tvNoteLocation.visibility = View.VISIBLE
            } else {
                holder.binding.tvNoteLocation.visibility = View.GONE
            }

            holder.binding.noteClickArea.setOnClickListener { onItemClick(session) }
            holder.binding.btnDeleteNote.setOnClickListener { onDeleteClick(session) }
        }
        override fun getItemCount() = sessions.size
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}