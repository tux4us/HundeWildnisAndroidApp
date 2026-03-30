package com.hundewildnis.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
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
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data Class für gespeicherte Notizen - @Keep verhindert Obfuskierung durch R8/ProGuard
 */
@Keep
data class NoteSession(
    val id: String,
    val date: Long,
    val title: String = "",
    val notes: String,
    val photoUris: List<String>,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val weatherInfo: String? = null
)

/**
 * DailyNotesActivity - Tagesnotizen mit Fotos, GPS, Wetter und Historie
 */
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

    // Permission Launcher
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else Toast.makeText(this, "⚠️ Kamera-Berechtigung erforderlich", Toast.LENGTH_SHORT).show()
    }

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) requestLocationUpdates()
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoUri != null) { addPhoto(currentPhotoUri!!); saveToHistory(false) }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { addPhoto(it); saveToHistory(false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply { title = "Tagesnotizen"; setDisplayHomeAsUpEnabled(true) }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setupPhotoRecyclerView()
        setupHistoryRecyclerView()
        setupUI()
        loadHistory()
        
        startNewNote()
        checkLocationPermission()
    }

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

    private fun confirmDelete(session: NoteSession) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        val dialog = AlertDialog.Builder(this)
            .setTitle("Notiz löschen?")
            .setMessage("Möchtest Du diese Notiz dauerhaft aus der Historie entfernen?")
            .setPositiveButton("Löschen") { _, _ ->
                val index = historyList.indexOfFirst { it.id == session.id }
                if (index != -1) {
                    session.photoUris.forEach { deletePhotoFile(Uri.parse(it)) }
                    historyList.removeAt(index)
                    historyAdapter.notifyItemRemoved(index)
                    saveHistoryToPrefs()
                    if (currentSessionId == session.id) startNewNote()
                    Toast.makeText(this, "Eintrag gelöscht", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(color)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(color)
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

        val session = NoteSession(
            id = currentSessionId ?: UUID.randomUUID().toString(),
            date = System.currentTimeMillis(),
            title = title,
            notes = notes,
            photoUris = photos.map { it.toString() },
            latitude = currentLatitude,
            longitude = currentLongitude,
            weatherInfo = currentWeatherInfo
        )

        val index = historyList.indexOfFirst { it.id == session.id }
        if (index != -1) historyList[index] = session else historyList.add(0, session)
        
        saveHistoryToPrefs()
        historyAdapter.notifyDataSetChanged()

        if (isManualNew) {
            startNewNote()
            Toast.makeText(this, "✅ Notiz gesichert", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startNewNote() {
        currentSessionId = UUID.randomUUID().toString()
        binding.etTitle.setText("")
        binding.etNotes.setText("")
        photos.clear()
        currentWeatherInfo = null
        binding.tvWeatherInfo.visibility = View.GONE
        photoAdapter.notifyDataSetChanged()
        updatePhotoVisibility()
        updateDateDisplay(System.currentTimeMillis())
        requestLocationUpdates()
    }

    private fun updateDateDisplay(timestamp: Long) {
        val dateText = SimpleDateFormat("EEEE, dd. MMMM yyyy HH:mm", Locale.GERMAN).format(Date(timestamp))
        val locText = if (currentLatitude != null) "\n📍 ${String.format(Locale.US, "%.4f", currentLatitude)}, ${String.format(Locale.US, "%.4f", currentLongitude)}" else ""
        binding.tvDate.text = dateText + locText
    }

    private fun saveHistoryToPrefs() {
        getSharedPreferences("notes_history", Context.MODE_PRIVATE).edit().putString("sessions", Gson().toJson(historyList)).apply()
    }

    private fun loadHistory() {
        val json = getSharedPreferences("notes_history", Context.MODE_PRIVATE).getString("sessions", null)
        if (json != null) {
            val type = object : TypeToken<List<NoteSession>>() {}.type
            val loaded: List<NoteSession> = Gson().fromJson(json, type)
            historyList.clear(); historyList.addAll(loaded)
            historyAdapter.notifyDataSetChanged()
        }
    }

    private fun setupUI() {
        binding.btnCamera.setOnClickListener { checkCameraPermissionAndOpen() }
        binding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.btnSaveNew.setOnClickListener { saveToHistory(true) }
        binding.btnExportPdf.setOnClickListener { saveToHistory(false); exportAsPdf(false) }
        binding.btnExportGpx.setOnClickListener { saveToHistory(false); exportAsGpx() }
        binding.btnShare.setOnClickListener { saveToHistory(false); shareNotes() }
        binding.btnAddWeather.setOnClickListener { addWeatherToNote() }
        
        binding.btnClear.setOnClickListener {
            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
            
            val dialog = AlertDialog.Builder(this)
                .setTitle("Entwurf löschen?")
                .setMessage("Möchtest du den aktuellen Entwurf wirklich löschen?")
                .setPositiveButton("Löschen") { _, _ -> 
                    photos.forEach { deletePhotoFile(it) }
                    startNewNote() 
                }
                .setNegativeButton("Abbrechen", null)
                .show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(color)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(color)
        }
    }

    private fun addWeatherToNote() {
        if (currentLatitude == null) {
            Toast.makeText(this, "Warte auf GPS-Standort...", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnAddWeather.isEnabled = false
        lifecycleScope.launch {
            try {
                val weather = withContext(Dispatchers.IO) {
                    val url = "https://api.open-meteo.com/v1/forecast?latitude=$currentLatitude&longitude=$currentLongitude&current=temperature_2m,relative_humidity_2m,precipitation,weather_code,wind_speed_10m,wind_direction_10m"
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
                    
                    "🌡️ ${String.format(Locale.GERMAN, "%.1f", temp)}°C, $desc\n💨 ${String.format(Locale.GERMAN, "%.1f", windSpeed)} km/h ($windDirStr), 💧 $hum%, 🌧️ ${String.format(Locale.GERMAN, "%.1f", precip)}mm"
                }
                currentWeatherInfo = weather
                binding.tvWeatherInfo.text = weather
                binding.tvWeatherInfo.visibility = View.VISIBLE
                saveToHistory(false)
                Toast.makeText(this@DailyNotesActivity, "Wetterdaten hinzugefügt", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DailyNotesActivity, "Fehler beim Wetter-Abruf", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnAddWeather.isEnabled = true
            }
        }
    }

    private fun getWindDirection(degrees: Int): String {
        val directions = arrayOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
        return directions[((degrees + 22.5) / 45).toInt() % 8]
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Klarer Himmel"; 1, 2, 3 -> "Leicht bewölkt"; 45, 48 -> "Nebel"
            51, 53, 55 -> "Nieselregen"; 61, 63, 65 -> "Regen"; 71, 73, 75 -> "Schneefall"
            80, 81, 82 -> "Regenschauer"; 95 -> "Gewitter"
            else -> "Wechselhaft"
        }
    }

    private fun checkLocationPermission() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("📍 Standortdienste aktivieren")
                .setMessage("Diese App benötigt Zugriff auf Deinen Standort für folgende Funktionen:\n\n" +
                        "• Tempo-Messung: Um Deine Geh-Geschwindigkeit im Training präzise zu ermitteln.\n" +
                        "• Navigation: Damit Du Standort und Navigation aufrufen und nutzen kannst.\n" +
                        "• Standort-Infos: Für GPS-Daten in Deinen Notizen, um diese mit Deinem Standort zu verknüpfen.\n\n" +
                        "Ohne Aktivierung der Standortdienste, als technische Voraussetzung, kannst Du diese Funktionen nicht nutzen. Deine Daten werden nur lokal verarbeitet, ausschliesslich auf Deinem Gerät gespeichert und nur von Dir allein verwaltet und auf Wunsch zum Teilen freigegeben.")
                .setPositiveButton("Verstanden") { _, _ ->
                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                .setNegativeButton("Abbrechen", null)
                .create()
                .apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)
                }
        } else {
            requestLocationUpdates()
        }
    }

    private fun requestLocationUpdates() {
        try {
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            lastKnown?.let {
                currentLatitude = it.latitude
                currentLongitude = it.longitude
                updateDateDisplay(System.currentTimeMillis())
            }
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null)
        } catch (e: SecurityException) {}
    }

    override fun onLocationChanged(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        updateDateDisplay(System.currentTimeMillis())
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
            currentPhotoUri?.let { cameraLauncher.launch(it) }
        }
    }

    private fun addPhoto(uri: Uri) {
        if (photos.size >= 5) { Toast.makeText(this, "⚠️ Max. 5 Fotos", Toast.LENGTH_SHORT).show(); return }
        
        val internalUri = copyUriToInternal(uri)
        if (internalUri != null) {
            photoAdapter.addPhoto(internalUri)
            updatePhotoVisibility()
        } else {
            Toast.makeText(this, "Fehler beim Verarbeiten des Bildes", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyUriToInternal(uri: Uri): Uri? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val folder = File(filesDir, "note_photos")
            if (!folder.exists()) folder.mkdirs()
            
            val file = File(folder, "img_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun deletePhotoFile(uri: Uri) {
        try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: return)
                if (file.exists() && file.absolutePath.contains("note_photos")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePhotoVisibility() { binding.rvPhotos.visibility = if (photos.isEmpty()) View.GONE else View.VISIBLE }

    private fun exportAsPdf(shareAfterExport: Boolean) {
        val title = binding.etTitle.text.toString()
        val notes = binding.etNotes.text.toString()
        if (title.isEmpty() && notes.isEmpty() && photos.isEmpty()) return
        
        lifecycleScope.launch {
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    generatePdfFile(title, notes)
                }

                if (shareAfterExport) {
                    shareFile(pdfFile, "application/pdf", "Notiz teilen")
                } else {
                    saveFileToDocuments(pdfFile, "application/pdf")
                }
            } catch (e: Exception) {
                Toast.makeText(this@DailyNotesActivity, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generatePdfFile(title: String, notes: String): File {
        val pdf = PdfDocument()
        var pageNum = 1
        val paint = android.graphics.Paint()
        val info = PdfDocument.PageInfo.Builder(595, 842, pageNum++).create()
        var page = pdf.startPage(info)
        var canvas = page.canvas
        
        val margin = 50f
        val maxWidth = 595f - (2 * margin)
        
        paint.textSize = 20f; paint.isFakeBoldText = true
        canvas.drawText(if(title.isNotEmpty()) title else "Tagesnotiz", margin, 50f, paint)
        
        paint.textSize = 12f; paint.isFakeBoldText = false
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
        canvas.drawText("Erstellt am: ${sdf.format(Date())}", margin, 75f, paint)
        
        if (currentLatitude != null) {
            canvas.drawText("📍 Standort: $currentLatitude, $currentLongitude", margin, 95f, paint)
        }
        
        var currentY = 115f
        currentWeatherInfo?.let { info ->
            info.split("\n").forEach { line ->
                canvas.drawText(line, margin, currentY, paint)
                currentY += 20f
            }
        }

        currentY += 10f
        if (notes.isNotEmpty()) {
            paint.textSize = 12f
            notes.split("\n").forEach { paragraph ->
                var line = paragraph
                while (line.isNotEmpty() && currentY < 800) {
                    val count = paint.breakText(line, true, maxWidth, null)
                    if (count <= 0) break
                    var end = if (count < line.length) (line.substring(0, count).lastIndexOf(' ').takeIf { it != -1 } ?: count) else count
                    canvas.drawText(line.substring(0, end).trim(), margin, currentY, paint)
                    currentY += 20f
                    line = line.substring(end).trim()
                }
            }
        }
        
        var photoIdx = 0
        if (photos.isNotEmpty() && (842f - currentY) > 150f) {
            val targetHeight = 250f
            val targetWidth = if (photos.size == 1) maxWidth else (maxWidth / 2f) - 5f
            var currentX = margin
            while (photoIdx < photos.size && (currentY + targetHeight) < 820f) {
                loadBitmap(photos[photoIdx])?.let { bitmap ->
                    val scaled = scaleBitmap(bitmap, targetWidth.toInt(), targetHeight.toInt())
                    canvas.drawBitmap(scaled, currentX, currentY, null)
                    if (photos.size > 1 && currentX == margin && (photoIdx + 1) < photos.size) currentX += targetWidth + 10f
                    else { currentX = margin; currentY += scaled.height + 10f }
                }
                photoIdx++
            }
        }
        pdf.finishPage(page)
        
        while (photoIdx < photos.size) {
            val pPage = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum++).create())
            repeat(2) { i ->
                if (photoIdx < photos.size) {
                    loadBitmap(photos[photoIdx])?.let { bitmap ->
                        val scaled = scaleBitmap(bitmap, 495, 350)
                        pPage.canvas.drawBitmap(scaled, margin + (495 - scaled.width) / 2f, if (i == 0) 50f else 450f, null)
                    }
                    photoIdx++
                }
            }
            pdf.finishPage(pPage)
        }

        val fileName = "Notiz_${if(title.isNotEmpty()) title.replace(" ","_") else "Tagesbericht"}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN).format(Date())}.pdf"
        val tempFile = File(cacheDir, fileName)
        pdf.writeTo(FileOutputStream(tempFile))
        pdf.close()
        return tempFile
    }

    private fun exportAsGpx() {
        val title = binding.etTitle.text.toString()
        val notes = binding.etNotes.text.toString()
        if (currentLatitude == null) { Toast.makeText(this, "Keine GPS-Daten verfügbar", Toast.LENGTH_SHORT).show(); return }
        
        lifecycleScope.launch {
            try {
                val gpxFile = withContext(Dispatchers.IO) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    val gpxContent = StringBuilder().apply {
                        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                        append("<gpx version=\"1.1\" creator=\"HundeWildnis\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
                        append("  <wpt lat=\"$currentLatitude\" lon=\"$currentLongitude\">\n")
                        append("    <time>${sdf.format(Date())}</time>\n")
                        append("    <name>${if(title.isNotEmpty()) title else "HundeWildnis Notiz"}</name>\n")
                        val desc = StringBuilder(notes)
                        if (currentWeatherInfo != null) desc.append("\n\nWetter: $currentWeatherInfo")
                        append("    <desc>${desc.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</desc>\n")
                        append("    <cmt>Fotos: ${photos.size}</cmt>\n")
                        append("  </wpt>\n")
                        append("</gpx>")
                    }.toString()

                    val fileName = "Notiz_${if(title.isNotEmpty()) title.replace(" ","_") else "Bericht"}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN).format(Date())}.gpx"
                    val tempFile = File(cacheDir, fileName)
                    tempFile.writeText(gpxContent)
                    tempFile
                }
                saveFileToDocuments(gpxFile, "application/gpx+xml")
            } catch (e: Exception) {
                Toast.makeText(this@DailyNotesActivity, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun saveFileToDocuments(tempFile: File, mimeType: String) {
        withContext(Dispatchers.IO) {
            try {
                val appDirName = "HundeWildnis"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, tempFile.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$appDirName")
                    }
                    val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { output ->
                            tempFile.inputStream().use { input -> input.copyTo(output) }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@DailyNotesActivity, "Datei gespeichert in Documents/$appDirName", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    val appDir = File(documentsDir, appDirName).apply { if (!exists()) mkdirs() }
                    val targetFile = File(appDir, tempFile.name)
                    tempFile.copyTo(targetFile, overwrite = true)
                    
                    MediaScannerConnection.scanFile(this@DailyNotesActivity, arrayOf(targetFile.absolutePath), arrayOf(mimeType), null)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DailyNotesActivity, "Datei gespeichert in Documents/$appDirName", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@DailyNotesActivity, "Fehler beim Speichern: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun shareFile(file: File, mimeType: String, chooserTitle: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, chooserTitle))
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler beim Teilen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? = try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
    private fun scaleBitmap(b: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val r = minOf(maxW.toFloat() / b.width, maxH.toFloat() / b.height)
        return Bitmap.createScaledBitmap(b, (b.width * r).toInt(), (b.height * r).toInt(), true)
    }

    private fun shareNotes() {
        val notes = binding.etNotes.text.toString()
        if (notes.isEmpty() && photos.isEmpty()) return
        if (photos.isNotEmpty()) {
            exportAsPdf(true) 
        } else {
            val intent = Intent(Intent.ACTION_SEND).apply { 
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Tagesnotizen vom ${SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date())}\n\n$notes") 
            }
            startActivity(Intent.createChooser(intent, "Notizen teilen"))
        }
    }

    inner class HistoryAdapter(private val sessions: List<NoteSession>, private val onItemClick: (NoteSession) -> Unit, private val onDeleteClick: (NoteSession) -> Unit) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        inner class ViewHolder(val binding: ItemNoteSessionBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemNoteSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val s = sessions[position]
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
            holder.binding.tvNoteDate.text = sdf.format(Date(s.date))
            if (s.title.isNotEmpty()) holder.binding.tvNoteSnippet.text = "📌 ${s.title}"
            else holder.binding.tvNoteSnippet.text = s.notes.take(40).replace("\n", " ") + "..."
            if (s.latitude != null) {
                holder.binding.tvNoteLocation.visibility = View.VISIBLE
                holder.binding.tvNoteLocation.text = String.format(Locale.US, "📍 %.4f, %.4f", s.latitude, s.longitude)
            } else holder.binding.tvNoteLocation.visibility = View.GONE
            holder.binding.noteClickArea.setOnClickListener { onItemClick(s) }
            holder.binding.btnDeleteNote.setOnClickListener { onDeleteClick(s) }
        }
        override fun getItemCount() = sessions.size
    }

    override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
    override fun onProviderEnabled(p: String) {}
    override fun onProviderDisabled(p: String) {}
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
