package com.hundewildnis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.hundewildnis.app.databinding.ActivityDailyNotesBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import android.content.ContentValues
import android.provider.MediaStore
import androidx.annotation.RequiresApi

/**
 * DailyNotesActivity - Tagesnotizen mit Fotos erstellen und teilen
 */
class DailyNotesActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityDailyNotesBinding
    private val photos = mutableListOf<Uri>()
    private lateinit var photoAdapter: PhotoAdapter
    private var currentPhotoUri: Uri? = null

    // Permission Launcher für Kamera (Android 13+)
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openCamera()
        } else {
            Toast.makeText(this, "⚠️ Kamera-Berechtigung erforderlich", Toast.LENGTH_SHORT).show()
        }
    }

    // Kamera Launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            addPhoto(currentPhotoUri!!)
            Toast.makeText(this, "📸 Foto aufgenommen", Toast.LENGTH_SHORT).show()
        }
    }

    // Galerie Launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            addPhoto(it)
            Toast.makeText(this, "🖼️ Foto hinzugefügt", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDailyNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Tagesnotizen"
            setDisplayHomeAsUpEnabled(true)
        }

        setupPhotoRecyclerView()
        setupUI()
    }

    /**
     * Färbt Dialog-Buttons weiß (Dark Mode) oder grün (Light Mode)
     */
    private fun styleDialogButtons(dialog: AlertDialog) {
        dialog.window?.decorView?.post {
            val isDarkTheme = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#2d5016")

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(buttonColor)
        }
    }

    private fun setupPhotoRecyclerView() {
        photoAdapter = PhotoAdapter(photos) { position ->
            // Foto löschen
            photoAdapter.removePhoto(position)
            updatePhotoVisibility()
            Toast.makeText(this, "Foto entfernt", Toast.LENGTH_SHORT).show()
        }

        binding.rvPhotos.apply {
            layoutManager = GridLayoutManager(this@DailyNotesActivity, 3)
            adapter = photoAdapter
        }
    }

    private fun setupUI() {
        // Aktuelles Datum anzeigen
        val dateFormat = SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.GERMAN)
        binding.tvDate.text = dateFormat.format(Date())

        // Kamera Button
        binding.btnCamera.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        // Galerie Button
        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // Als PDF exportieren
        binding.btnExportPdf.setOnClickListener {
            exportAsPdf()
        }

        // Teilen
        binding.btnShare.setOnClickListener {
            shareNotes()
        }

        // Löschen
        binding.btnClear.setOnClickListener {
            binding.etNotes.setText("")
            photos.clear()
            photoAdapter.notifyDataSetChanged()
            updatePhotoVisibility()
            Toast.makeText(this, "Notizen gelöscht", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkCameraPermissionAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    openCamera()
                }
                else -> {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } else {
            openCamera()
        }
    }

    private fun openCamera() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: MediaStore verwenden
                val fileName = "HundeWildnis_${System.currentTimeMillis()}.jpg"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HundeWildnis")
                }

                currentPhotoUri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                currentPhotoUri?.let {
                    cameraLauncher.launch(it)
                } ?: run {
                    Toast.makeText(this, "❌ Fehler beim Erstellen der Foto-Datei", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Android 9 und älter: Direkter Zugriff
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "HundeWildnis"
                )
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs()
                }

                val photoFile = File(
                    picturesDir,
                    "HundeWildnis_${System.currentTimeMillis()}.jpg"
                )

                currentPhotoUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    photoFile
                )

                currentPhotoUri?.let {
                    cameraLauncher.launch(it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ Kamera-Fehler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addPhoto(uri: Uri) {
        if (photos.size >= 5) {
            Toast.makeText(this, "⚠️ Maximal 5 Fotos möglich", Toast.LENGTH_SHORT).show()
            return
        }

        photoAdapter.addPhoto(uri)
        updatePhotoVisibility()
    }

    private fun updatePhotoVisibility() {
        binding.rvPhotos.visibility = if (photos.isEmpty()) {
            android.view.View.GONE
        } else {
            android.view.View.VISIBLE
        }
    }

    private fun exportAsPdf() {
        val notes = binding.etNotes.text.toString()

        if (notes.isEmpty() && photos.isEmpty()) {
            Toast.makeText(this, "Bitte erst Notizen oder Fotos hinzufügen", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDocument = PdfDocument()
            var pageNumber = 1
            val paint = android.graphics.Paint()

            // Seite 1: Notizen
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber++).create() // A4
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Titel
            paint.textSize = 20f
            paint.isFakeBoldText = true
            paint.color = android.graphics.Color.BLACK
            canvas.drawText("Tagesnotizen", 50f, 50f, paint)

            // Datum
            paint.textSize = 14f
            paint.isFakeBoldText = false
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
            canvas.drawText(dateFormat.format(Date()), 50f, 80f, paint)

            // Notizen
            if (notes.isNotEmpty()) {
                val lines = notes.split("\n")
                var y = 120f
                for (line in lines) {
                    if (y > 800) break
                    canvas.drawText(line, 50f, y, paint)
                    y += 20f
                }
            }

            pdfDocument.finishPage(page)

            // Fotos hinzufügen (je 2 pro Seite)
            var photoIndex = 0
            while (photoIndex < photos.size) {
                val photoPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber++).create()
                val photoPage = pdfDocument.startPage(photoPageInfo)
                val photoCanvas = photoPage.canvas

                // Foto 1
                if (photoIndex < photos.size) {
                    val bitmap1 = loadBitmapFromUri(photos[photoIndex])
                    bitmap1?.let {
                        val scaled1 = scaleBitmapProportionally(it, 495, 350)
                        val x = 50f + (495 - scaled1.width) / 2f  // Zentriert
                        photoCanvas.drawBitmap(scaled1, x, 50f, null)
                        scaled1.recycle()
                        it.recycle()
                    }
                    photoIndex++
                }

                // Foto 2
                if (photoIndex < photos.size) {
                    val bitmap2 = loadBitmapFromUri(photos[photoIndex])
                    bitmap2?.let {
                        val scaled2 = scaleBitmapProportionally(it, 495, 350)
                        val x = 50f + (495 - scaled2.width) / 2f  // Zentriert
                        photoCanvas.drawBitmap(scaled2, x, 450f, null)
                        scaled2.recycle()
                        it.recycle()
                    }
                    photoIndex++
                }

                pdfDocument.finishPage(photoPage)
            }

            // ✅ NEU: Speichern in öffentlichen Documents-Ordner
            val fileName = "Tagesnotizen_${SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.GERMAN).format(Date())}.pdf"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: MediaStore
                savePdfToMediaStore(pdfDocument, fileName)
            } else {
                // Android 9 und älter: Direkter Zugriff
                val documentsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "HundeWildnis"
                )
                if (!documentsDir.exists()) {
                    documentsDir.mkdirs()
                }

                val file = File(documentsDir, fileName)
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                showPdfDialog(file)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ Fehler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Speichert PDF in öffentlichen Documents-Ordner (Android 10+)
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun savePdfToMediaStore(pdfDocument: PdfDocument, fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/HundeWildnis")
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            pdfDocument.close()

            // Dialog zeigen
            val dialog = AlertDialog.Builder(this)
                .setTitle("✓ PDF erstellt")
                .setMessage("Gespeichert in:\nDocuments/HundeWildnis/$fileName")
                .setPositiveButton("OK", null)
                .create()

            dialog.show()
            styleDialogButtons(dialog)

            Toast.makeText(this, "✓ PDF in Documents/HundeWildnis gespeichert", Toast.LENGTH_LONG).show()
        } ?: run {
            Toast.makeText(this, "❌ Fehler beim Speichern", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Zeigt Dialog für gespeicherte PDF (Android 9 und älter)
     */
    private fun showPdfDialog(file: File) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("✓ PDF erstellt")
            .setMessage("Gespeichert als:\n${file.name}\n\nPfad:\n${file.absolutePath}")
            .setPositiveButton("Öffnen") { _, _ ->
                openPdf(file)
            }
            .setNegativeButton("OK", null)
            .create()

        dialog.show()
        styleDialogButtons(dialog)
    }

    /**
     * Öffnet PDF-Datei (für Android 9 und älter)
     */
    private fun openPdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
            }

            startActivity(Intent.createChooser(intent, "PDF öffnen mit"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Kein PDF-Viewer gefunden", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Skaliert Bitmap proportional in maximale Größe
     */
    private fun scaleBitmapProportionally(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Berechne Skalierungsfaktor (behalte Aspect Ratio)
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun shareNotes() {
        val notes = binding.etNotes.text.toString()

        if (notes.isEmpty() && photos.isEmpty()) {
            Toast.makeText(this, "Bitte erst Notizen oder Fotos hinzufügen", Toast.LENGTH_SHORT).show()
            return
        }

        // Wenn Fotos vorhanden: Erst PDF erstellen, dann teilen
        if (photos.isNotEmpty()) {
            sharePdf()
        } else {
            // Nur Text teilen
            shareText(notes)
        }
    }

    private fun shareText(notes: String) {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
        val shareText = "Tagesnotizen vom ${dateFormat.format(Date())}\n\n$notes"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Tagesnotizen")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        startActivity(Intent.createChooser(intent, "Notizen teilen über"))
    }

    private fun sharePdf() {
        val notes = binding.etNotes.text.toString()

        try {
            // Temporäre PDF erstellen
            val pdfDocument = PdfDocument()
            var pageNumber = 1
            val paint = android.graphics.Paint()

            // Seite mit Notizen
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber++).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            paint.textSize = 20f
            paint.isFakeBoldText = true
            paint.color = android.graphics.Color.BLACK
            canvas.drawText("Tagesnotizen", 50f, 50f, paint)

            paint.textSize = 14f
            paint.isFakeBoldText = false
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
            canvas.drawText(dateFormat.format(Date()), 50f, 80f, paint)

            if (notes.isNotEmpty()) {
                val lines = notes.split("\n")
                var y = 120f
                for (line in lines) {
                    if (y > 800) break
                    canvas.drawText(line, 50f, y, paint)
                    y += 20f
                }
            }

            pdfDocument.finishPage(page)

            // Fotos hinzufügen
            var photoIndex = 0
            while (photoIndex < photos.size) {
                val photoPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber++).create()
                val photoPage = pdfDocument.startPage(photoPageInfo)
                val photoCanvas = photoPage.canvas

                if (photoIndex < photos.size) {
                    val bitmap1 = loadBitmapFromUri(photos[photoIndex])
                    bitmap1?.let {
                        val scaled1 = scaleBitmapProportionally(it, 495, 350)
                        val x = 50f + (495 - scaled1.width) / 2f  // Zentriert
                        photoCanvas.drawBitmap(scaled1, x, 50f, null)
                        scaled1.recycle()
                        it.recycle()
                    }
                    photoIndex++
                }

                if (photoIndex < photos.size) {
                    val bitmap2 = loadBitmapFromUri(photos[photoIndex])
                    bitmap2?.let {
                        val scaled2 = scaleBitmapProportionally(it, 495, 350)
                        val x = 50f + (495 - scaled2.width) / 2f  // Zentriert
                        photoCanvas.drawBitmap(scaled2, x, 450f, null)
                        scaled2.recycle()
                        it.recycle()
                    }
                    photoIndex++
                }

                pdfDocument.finishPage(photoPage)
            }

            // PDF speichern
            val fileName = "Tagesnotizen_${SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.GERMAN).format(Date())}.pdf"
            val file = File(cacheDir, fileName)

            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            // PDF teilen
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Tagesnotizen")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "PDF teilen über"))

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ Fehler beim Teilen: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
