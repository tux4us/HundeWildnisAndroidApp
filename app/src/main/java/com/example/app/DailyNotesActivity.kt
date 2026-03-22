package com.hundewildnis.app

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hundewildnis.app.SecureBaseActivity
import com.hundewildnis.app.R
import com.hundewildnis.app.databinding.ActivityDailyNotesBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * DailyNotesActivity - Tagesnotizen erstellen und teilen
 */
class DailyNotesActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityDailyNotesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDailyNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Tagesnotizen"
            setDisplayHomeAsUpEnabled(true)
        }

        setupUI()
    }

    private fun setupUI() {
        // Aktuelles Datum anzeigen
        val dateFormat = SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.GERMAN)
        binding.tvDate.text = dateFormat.format(Date())

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
            Toast.makeText(this, "Notizen gelöscht", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportAsPdf() {
        val notes = binding.etNotes.text.toString()

        if (notes.isEmpty()) {
            Toast.makeText(this, "Bitte erst Notizen eingeben", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // PDF Dokument erstellen
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = pdfDocument.startPage(pageInfo)

            val canvas = page.canvas
            val paint = android.graphics.Paint()
            paint.textSize = 14f
            paint.color = android.graphics.Color.BLACK

            // Titel
            paint.textSize = 20f
            paint.isFakeBoldText = true
            canvas.drawText("Tagesnotizen", 50f, 50f, paint)

            // Datum
            paint.textSize = 14f
            paint.isFakeBoldText = false
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
            canvas.drawText(dateFormat.format(Date()), 50f, 80f, paint)

            // Notizen (mit Zeilenumbruch)
            val lines = notes.split("\n")
            var y = 120f
            for (line in lines) {
                if (y > 800) break // Seitenende
                canvas.drawText(line, 50f, y, paint)
                y += 20f
            }

            pdfDocument.finishPage(page)

            // Datei speichern
            val fileName = "Tagesnotizen_${SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).format(Date())}.pdf"
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            Toast.makeText(this, "PDF erstellt: ${file.name}", Toast.LENGTH_LONG).show()

            // PDF öffnen
            openPdf(file)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Fehler beim Erstellen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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

    private fun shareNotes() {
        val notes = binding.etNotes.text.toString()

        if (notes.isEmpty()) {
            Toast.makeText(this, "Bitte erst Notizen eingeben", Toast.LENGTH_SHORT).show()
            return
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
        val shareText = "Tagesnotizen vom ${dateFormat.format(Date())}\n\n$notes"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Tagesnotizen")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        startActivity(Intent.createChooser(intent, "Notizen teilen über"))
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
