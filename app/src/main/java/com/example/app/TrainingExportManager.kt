package com.hundewildnis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════
// TRAINING EXPORT MANAGER - PDF & CSV Export
// ════════════════════════════════════════════════════════════════

object TrainingExportManager {
    
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    private val fileNameFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.GERMAN)
    
    // Farben (wie Emergency PDFs)
    private val colorPrimary = DeviceRgb(45, 80, 22)  // #2d5016
    private val colorSecondary = DeviceRgb(122, 146, 101)  // #7a9265
    
    // ════════════════════════════════════════════════════════════
    // 1. GESAMT-TAGEBUCH EXPORT (alle Kategorien eines Hundes)
    // ════════════════════════════════════════════════════════════

    fun exportCompleteDiary(
        context: Context,
        dog: DogProfile,
        format: ExportFormat
    ): File? {
        // ✅ Nutze companion object Funktionen
        val allSessions = TrainingManager.getAllSessions(context).filter { it.dogId == dog.id }
        val allProgress = TrainingManager.getAllProgress(context).filter { it.dogId == dog.id }
        val allMilestones = TrainingManager.getAllMilestones(context).filter { it.dogId == dog.id }

        val fileName = "Trainingstagebuch_${dog.name}_${fileNameFormat.format(Date())}"

        return when (format) {
            ExportFormat.PDF -> exportCompleteDiaryPDF(context, dog, allSessions, allProgress, allMilestones, fileName)
            ExportFormat.CSV -> exportCompleteDiaryCSV(context, dog, allSessions, fileName)
        }
    }
    
    private fun exportCompleteDiaryPDF(
        context: Context,
        dog: DogProfile,
        sessions: List<TrainingSession>,
        progressList: List<TrainingProgress>,
        milestones: List<Milestone>,
        fileName: String
    ): File? {
        return try {
            val file = createTempFile(context, "$fileName.pdf")
            val writer = PdfWriter(file)
            val pdfDoc = PdfDocument(writer)
            val document = Document(pdfDoc)
            
            // Header
            document.add(
                Paragraph("🦮 TRAININGSTAGEBUCH")
                    .setFontSize(20f)
                    .setBold()
                    .setFontColor(colorPrimary)
                    .setTextAlignment(TextAlignment.CENTER)
            )
            
            document.add(
                Paragraph("${dog.name}")
                    .setFontSize(16f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20f)
            )
            
            // Übersicht
            document.add(Paragraph("📊 ÜBERSICHT").setBold().setFontSize(14f).setFontColor(colorPrimary))
            document.add(Paragraph("Erstellt: ${dateFormat.format(Date())}").setFontSize(10f))
            document.add(Paragraph("Gesamt-Sessions: ${sessions.size}").setFontSize(10f))
            document.add(Paragraph("Trainierte Kategorien: ${progressList.size}").setFontSize(10f))
            document.add(Paragraph("Erreichte Meilensteine: ${milestones.size}").setFontSize(10f).setMarginBottom(15f))
            
            // Fortschritt pro Kategorie
            document.add(Paragraph("📈 FORTSCHRITT PRO KATEGORIE").setBold().setFontSize(14f).setFontColor(colorPrimary))
            
            progressList.sortedByDescending { it.progressPercentage }.forEach { progress ->
                document.add(
                    Paragraph("${progress.category.displayName}: ${progress.progressPercentage}% (${progress.currentLevel?.displayName ?: TrainingLevel.BEGINNER.displayName})")
                        .setFontSize(11f)
                        .setMarginBottom(3f)
                )
                document.add(
                    Paragraph("${progress.totalSessions} Sessions • ${progress.totalMinutes} Min • Ø ${String.format("%.1f", progress.averageSuccess)}/4.0")
                        .setFontSize(9f)
                        .setMarginBottom(8f)
                        .setFontColor(ColorConstants.DARK_GRAY)
                )
            }
            
            // Sessions gruppiert nach Kategorie
            document.add(Paragraph("\n📋 ALLE TRAININGSEINHEITEN").setBold().setFontSize(14f).setFontColor(colorPrimary))
            
            sessions.groupBy { it.category }.forEach { (category, categorySessions) ->
                document.add(Paragraph("\n${category.displayName}").setBold().setFontSize(12f).setFontColor(colorSecondary))
                
                categorySessions.sortedByDescending { it.date }.forEach { session ->
                    addSessionToDocument(document, session)
                }
            }
            
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun exportCompleteDiaryCSV(
        context: Context,
        dog: DogProfile,
        sessions: List<TrainingSession>,
        fileName: String
    ): File? {
        return try {
            val file = createTempFile(context, "$fileName.csv")
            val csv = StringBuilder()
            
            // Header
            csv.append("Datum,Kategorie,Dauer (Min),Erfolg,Ablenkung,Schwierigkeit,Wetter,GPS,Notizen\n")
            
            // Sessions
            sessions.sortedByDescending { it.date }.forEach { session ->
                csv.append("\"${dateFormat.format(Date(session.date))}\",")
                csv.append("\"${session.category.displayName}\",")
                csv.append("${session.durationMinutes},")
                csv.append("\"${session.getSuccessText()}\",")
                csv.append("\"${session.distractionLevel.displayName}\",")
                csv.append("\"${session.difficulty.displayName}\",")
                csv.append("\"${session.weatherData}\",")
                csv.append("\"${session.getGpsShortFormat()}\",")
                csv.append("\"${session.notes.replace("\"", "\"\"")}\"\n")
            }
            
            file.writeText(csv.toString())
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // ════════════════════════════════════════════════════════════
    // 2. KATEGORIE EXPORT (eine Kategorie eines Hundes)
    // ════════════════════════════════════════════════════════════
    
    fun exportCategory(
        context: Context,
        dog: DogProfile,
        category: TrainingCategory,
        format: ExportFormat
    ): File? {
        val sessions = TrainingManager.getSessionsForCategory(context, dog.id, category)
        val progress = TrainingManager.getProgress(context, dog.id, category)

        val fileName = "Training_${dog.name}_${category.displayName}_${fileNameFormat.format(Date())}"

        return when (format) {
            ExportFormat.PDF -> exportCategoryPDF(context, dog, category, sessions, progress, fileName)
            ExportFormat.CSV -> exportCategoryCSV(context, dog, category, sessions, fileName)
        }
    }
    
    private fun exportCategoryPDF(
        context: Context,
        dog: DogProfile,
        category: TrainingCategory,
        sessions: List<TrainingSession>,
        progress: TrainingProgress,
        fileName: String
    ): File? {
        return try {
            val file = createTempFile(context, "$fileName.pdf")
            val writer = PdfWriter(file)
            val pdfDoc = PdfDocument(writer)
            val document = Document(pdfDoc)
            
            // Header
            document.add(
                Paragraph("📋 ${category.displayName}")
                    .setFontSize(18f)
                    .setBold()
                    .setFontColor(colorPrimary)
                    .setTextAlignment(TextAlignment.CENTER)
            )
            
            document.add(
                Paragraph("${dog.name}")
                    .setFontSize(14f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20f)
            )
            
            // Fortschritt
            document.add(Paragraph("📊 FORTSCHRITT").setBold().setFontSize(14f).setFontColor(colorPrimary))
            document.add(Paragraph("Trainingsstand: ${progress.progressPercentage}% (${progress.currentLevel?.displayName ?: TrainingLevel.BEGINNER.displayName})").setFontSize(11f))
            document.add(Paragraph("Gesamt-Sessions: ${progress.totalSessions}").setFontSize(10f))
            document.add(Paragraph("Trainingszeit: ${progress.totalMinutes} Minuten").setFontSize(10f))
            document.add(Paragraph("Durchschnitt: ${String.format("%.1f", progress.averageSuccess)}/4.0").setFontSize(10f))
            document.add(Paragraph("Aufeinanderfolgende Tage: ${progress.consecutiveDays}").setFontSize(10f).setMarginBottom(15f))
            
            // Sessions
            document.add(Paragraph("📝 TRAININGSEINHEITEN").setBold().setFontSize(14f).setFontColor(colorPrimary))
            sessions.sortedByDescending { it.date }.forEach { session ->
                addSessionToDocument(document, session)
            }
            
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun exportCategoryCSV(
        context: Context,
        dog: DogProfile,
        category: TrainingCategory,
        sessions: List<TrainingSession>,
        fileName: String
    ): File? {
        return try {
            val file = createTempFile(context, "$fileName.csv")
            val csv = StringBuilder()
            
            csv.append("Datum,Dauer (Min),Erfolg,Ablenkung,Schwierigkeit,Wetter,GPS,Notizen\n")
            
            sessions.sortedByDescending { it.date }.forEach { session ->
                csv.append("\"${dateFormat.format(Date(session.date))}\",")
                csv.append("${session.durationMinutes},")
                csv.append("\"${session.getSuccessText()}\",")
                csv.append("\"${session.distractionLevel.displayName}\",")
                csv.append("\"${session.difficulty.displayName}\",")
                csv.append("\"${session.weatherData}\",")
                csv.append("\"${session.getGpsShortFormat()}\",")
                csv.append("\"${session.notes.replace("\"", "\"\"")}\"\n")
            }
            
            file.writeText(csv.toString())
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // ════════════════════════════════════════════════════════════
    // 3. EINZELNE SESSION EXPORT
    // ════════════════════════════════════════════════════════════
    
    fun exportSession(
        context: Context,
        dog: DogProfile,
        session: TrainingSession,
        format: ExportFormat
    ): File? {
        val fileName = "Training_${dog.name}_${session.category.displayName}_${fileNameFormat.format(Date(session.date))}"
        
        return when (format) {
            ExportFormat.PDF -> exportSessionPDF(context, dog, session, fileName)
            ExportFormat.CSV -> exportSessionCSV(context, session, fileName)
        }
    }
    
    private fun exportSessionPDF(
        context: Context,
        dog: DogProfile,
        session: TrainingSession,
        fileName: String
    ): File? {
        return try {
            val file = createTempFile(context, "$fileName.pdf")
            val writer = PdfWriter(file)
            val pdfDoc = PdfDocument(writer)
            val document = Document(pdfDoc)
            
            document.add(
                Paragraph("📝 TRAININGSEINHEIT")
                    .setFontSize(18f)
                    .setBold()
                    .setFontColor(colorPrimary)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20f)
            )
            
            addSessionToDocument(document, session, detailed = true)
            
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun exportSessionCSV(
        context: Context,
        session: TrainingSession,
        fileName: String
    ): File? {
        return try {
            val file = createTempFile(context, "$fileName.csv")
            val csv = StringBuilder()
            
            csv.append("Feld,Wert\n")
            csv.append("\"Datum\",\"${dateFormat.format(Date(session.date))}\"\n")
            csv.append("\"Kategorie\",\"${session.category.displayName}\"\n")
            csv.append("\"Dauer\",\"${session.durationMinutes} Min\"\n")
            csv.append("\"Erfolg\",\"${session.getSuccessText()}\"\n")
            csv.append("\"Ablenkung\",\"${session.distractionLevel.displayName}\"\n")
            csv.append("\"Schwierigkeit\",\"${session.difficulty.displayName}\"\n")
            if (session.weatherData.isNotEmpty()) {
                csv.append("\"Wetter\",\"${session.weatherData}\"\n")
            }
            if (session.gpsLatitude != null && session.gpsLongitude != null) {
                csv.append("\"GPS\",\"${session.getGpsShortFormat()}\"\n")
                csv.append("\"OpenStreetMap\",\"${session.getOpenStreetMapLink()}\"\n")
            }
            if (session.notes.isNotEmpty()) {
                csv.append("\"Notizen\",\"${session.notes.replace("\"", "\"\"")}\"\n")
            }
            
            file.writeText(csv.toString())
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // ════════════════════════════════════════════════════════════
    // HILFSFUNKTIONEN
    // ════════════════════════════════════════════════════════════
    
    private fun addSessionToDocument(document: Document, session: TrainingSession, detailed: Boolean = false) {
        // ✅ Fix: Absolute Breite statt 100% (verhindert Überlauf bei langen Inhalten)
        val table = Table(UnitValue.createPointArray(floatArrayOf(100f, 400f)))
            .setMarginBottom(10f)
        
        table.addCell(createCell("Datum:", true))
        table.addCell(createCell(dateFormat.format(Date(session.date))))
        
        if (detailed) {
            table.addCell(createCell("Kategorie:", true))
            table.addCell(createCell(session.category.displayName))
        }
        
        table.addCell(createCell("Aufwand:", true))
        table.addCell(createCell(session.getEffortText()))
        
        table.addCell(createCell("Erfolg:", true))
        table.addCell(createCell("${session.getSuccessIcon()} ${session.getSuccessText()}"))
        
        table.addCell(createCell("Ablenkung:", true))
        table.addCell(createCell(session.distractionLevel.displayName))
        
        table.addCell(createCell("Schwierigkeit:", true))
        table.addCell(createCell(session.difficulty.displayName))
        
        if (session.weatherData.isNotEmpty()) {
            table.addCell(createCell("Wetter:", true))
            table.addCell(createCell(session.weatherData))
        }
        
        if (session.gpsLatitude != null && session.gpsLongitude != null) {
            table.addCell(createCell("Standort:", true))
            val locationText = if (session.gpsLocationName.isNotEmpty()) {
                "${session.gpsLocationName}\n${session.getGpsShortFormat()}"
            } else {
                session.getGpsShortFormat()
            }
            table.addCell(createCell(locationText))
            
            session.getOpenStreetMapLink()?.let { link ->
                table.addCell(createCell("Karte:", true))
                // ✅ Fix: Link-Text verkleinern falls zu lang
                table.addCell(createCell(link, fontSize = 8f))
            }
        }
        
        if (session.notes.isNotEmpty()) {
            table.addCell(createCell("Notizen:", true))
            // ✅ Fix: Notizen-Zelle bekommt automatischen Zeilenumbruch
            table.addCell(createCell(session.notes))
        }
        
        document.add(table)
    }
    
    private fun createCell(text: String, bold: Boolean = false, fontSize: Float = 10f): Cell {
        val paragraph = Paragraph(text).setFontSize(fontSize)
        if (bold) paragraph.setBold()
        
        return Cell().add(paragraph)
            .setPadding(5f)
            .setKeepTogether(true) // Verhindert unschöne Seitenumbrüche innerhalb einer Zeile
    }

    private fun createTempFile(context: Context, fileName: String): File {
        // ✅ Scoped Storage kompatibel: Erst im App-Ordner erstellen
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "HundeWildnis")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    /**
     * Kopiert die Datei in den öffentlichen Documents-Ordner
     */
    private fun saveToPublicDocuments(context: Context, file: File) {
        try {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (file.extension == "pdf") "application/pdf" else "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/HundeWildnis")
                }
            }

            val uri = resolver.insert(android.provider.MediaStore.Files.getContentUri("external"), contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                // MediaScanner benachrichtigen
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(android.os.Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath + "/HundeWildnis/" + file.name),
                    null,
                    null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Teilen-Intent erstellen
     */
    fun shareFile(context: Context, file: File) {
        // Erst öffentlich speichern für den User
        saveToPublicDocuments(context, file)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "pdf") "application/pdf" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Training teilen"))
    }
}

enum class ExportFormat {
    PDF, CSV
}
