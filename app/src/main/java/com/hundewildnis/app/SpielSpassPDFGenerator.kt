package com.hundewildnis.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * SpielSpassPDFGenerator – iText7.
 * Erstellt eine Gesamt-Übersicht (Statistik + vollständige Session-Tabelle)
 * für einen Hund, analog NasenarbeitPDFGenerator.generateGesamtPDF().
 */
object SpielSpassPDFGenerator {

    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)

    private val GREEN = DeviceRgb(45, 80, 22)   // #2d5016
    private val GRAY = DeviceRgb(100, 100, 100)

    fun generateGesamtPDF(
        context: Context,
        sessions: List<SpielSession>,
        profile: DogProfile,
        saveToPublic: Boolean = false
    ): File? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMAN).format(Date())
        val fileName = "SpielSpass_${profile.name}_$timestamp.pdf"

        return if (saveToPublic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportPdfViaMediaStore(context, fileName) { doc -> writeContent(context, doc, sessions, profile) }
            } else {
                exportPdfOldWay(fileName) { doc -> writeContent(context, doc, sessions, profile) }
            }
        } else {
            val dir = File(context.filesDir, "spiel_spass_pdfs").also { it.mkdirs() }
            val file = File(dir, fileName)
            try {
                val doc = Document(PdfDocument(PdfWriter(file)))
                writeContent(context, doc, sessions, profile)
                doc.close()
                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun writeContent(context: Context, doc: Document, sessions: List<SpielSession>, profile: DogProfile) {
        val stats = SpielSpassManager.getStatistikForDog(context, profile.id)

        doc.add(Paragraph(context.getString(R.string.spiel_spass_pdf_title)).setBold().setFontSize(18f).setFontColor(GREEN))
        doc.add(Paragraph("${profile.name} · ${profile.breed}").setFontSize(12f).setFontColor(GRAY))
        doc.add(Paragraph(" "))

        // Statistik-Zusammenfassung
        doc.add(Paragraph(context.getString(R.string.spiel_spass_pdf_section_summary)).setBold().setFontSize(13f))
        val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f))).useAllAvailableWidth()
        summaryTable.addCell(labelCell(context.getString(R.string.spiel_spass_stats_total_sessions)))
        summaryTable.addCell(valueCell("${stats.gesamtSessions}"))
        if (stats.durchschnittsBewertung > 0) {
            summaryTable.addCell(labelCell(context.getString(R.string.spiel_spass_stats_avg_rating, stats.durchschnittsBewertung)))
            summaryTable.addCell(valueCell(""))
        }
        doc.add(summaryTable)
        doc.add(Paragraph(" "))

        // Nach Örtlichkeit
        if (stats.sessionsProOrt.isNotEmpty()) {
            doc.add(Paragraph(context.getString(R.string.spiel_spass_stats_by_ort)).setBold().setFontSize(13f))
            val ortTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f))).useAllAvailableWidth()
            stats.sessionsProOrt.entries.sortedByDescending { it.value }.forEach { (ortName, count) ->
                val ort = try { SpielOrt.valueOf(ortName) } catch (e: Exception) { null }
                ortTable.addCell(labelCell(ort?.displayName(context) ?: ortName))
                ortTable.addCell(valueCell("$count"))
            }
            doc.add(ortTable)
            doc.add(Paragraph(" "))
        }

        // Verlauf
        doc.add(Paragraph(context.getString(R.string.spiel_spass_stats_history)).setBold().setFontSize(13f))
        val historyTable = Table(UnitValue.createPercentArray(floatArrayOf(1.2f, 2f, 1f, 0.6f))).useAllAvailableWidth()
        historyTable.addHeaderCell(headerCell(context.getString(R.string.spiel_spass_pdf_col_date)))
        historyTable.addHeaderCell(headerCell(context.getString(R.string.spiel_spass_pdf_col_activity)))
        historyTable.addHeaderCell(headerCell(context.getString(R.string.spiel_spass_pdf_col_location)))
        historyTable.addHeaderCell(headerCell(context.getString(R.string.spiel_spass_pdf_col_rating)))

        sessions.sortedByDescending { it.datum }.forEach { session ->
            val aktivitaet = SpielAktivitaetCatalog.findById(session.aktivitaetId)
            historyTable.addCell(valueCell(df.format(Date(session.datum))))
            historyTable.addCell(valueCell(aktivitaet?.name(context) ?: session.aktivitaetId))
            historyTable.addCell(valueCell(session.ort.displayName(context)))
            historyTable.addCell(valueCell(session.getBewertungIcon()))
        }
        doc.add(historyTable)
    }

    private fun labelCell(text: String) = Cell().add(Paragraph(text).setFontSize(10f).setFontColor(GRAY)).setBorder(null)
    private fun valueCell(text: String) = Cell().add(Paragraph(text).setFontSize(10f)).setBorder(null)
    private fun headerCell(text: String) = Cell().add(Paragraph(text).setBold().setFontSize(10f).setFontColor(GREEN)).setBorder(null)

    private fun exportPdfViaMediaStore(context: Context, fileName: String, writer: (Document) -> Unit): File? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/HundeWildnis")
        }
        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null

        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                val doc = Document(PdfDocument(PdfWriter(output)))
                writer(doc)
                doc.close()
            }
        } catch (e: Exception) {
            return null
        }

        var filePath: String? = null
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (columnIndex >= 0) filePath = cursor.getString(columnIndex)
            }
        }
        return filePath?.let { File(it) }
    }

    private fun exportPdfOldWay(fileName: String, writer: (Document) -> Unit): File? {
        val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "HundeWildnis")
        if (!docsDir.exists()) docsDir.mkdirs()
        val file = File(docsDir, fileName)
        return try {
            val doc = Document(PdfDocument(PdfWriter(file)))
            writer(doc)
            doc.close()
            file
        } catch (e: Exception) {
            null
        }
    }
}
