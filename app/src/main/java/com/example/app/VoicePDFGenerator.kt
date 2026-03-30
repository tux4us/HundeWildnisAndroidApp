package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object VoicePDFGenerator {

    fun generateSessionPDF(
        context: Context,
        session: VoiceSession,
        chartBitmap: Bitmap?
    ): File {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN)
        val fileName = "Kommunikation_${dateFormat.format(Date(session.startTime))}.pdf"
        val pdfFile = File(context.cacheDir, fileName)

        var document: Document? = null
        var pdfDocument: PdfDocument? = null

        try {
            val writer = PdfWriter(FileOutputStream(pdfFile))
            pdfDocument = PdfDocument(writer)
            document = Document(pdfDocument)

            val greenColor = DeviceRgb(45, 80, 22)
            val grayColor = DeviceRgb(128, 128, 128)

            // Titel
            document.add(
                Paragraph("KOMMUNIKATIONS-ANALYSE")
                    .setFontSize(24f)
                    .setBold()
                    .setFontColor(greenColor)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20f)
            )

            // Datum
            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))
            document.add(
                Paragraph("Datum: $dateStr")
                    .setFontSize(12f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20f)
            )

            // ÜBERSICHT
            addSectionTitle(document, "ÜBERSICHT", greenColor)

            val duration = session.totalDuration / 1000
            val voiceDuration = session.voiceDuration / 1000
            val pauseDuration = session.pauseDuration / 1000
            val efficiency = session.getEfficiency().toInt()

            addDataTable(document, listOf(
                "Gesamtdauer" to formatDuration(duration),
                "Sprechzeit" to formatDuration(voiceDuration),
                "Pausenzeit" to formatDuration(pauseDuration),
                "Effizienz" to "$efficiency%",
                "Trainingsmodus" to when(session.mode) {
                    TrainingMode.FREE -> "Freies Training"
                    TrainingMode.FOCUS -> "Fokus: ${session.focusFrequency?.name ?: "?"}"
                }
            ))

            // FREQUENZ-STATISTIK
            addSectionTitle(document, "FREQUENZ-STATISTIK", greenColor)
            addDataTable(document, listOf(
                "Durchschnitt" to "${session.avgFrequency.toInt()} Hz",
                "Minimum" to "${session.minFrequency.toInt()} Hz",
                "Maximum" to "${session.maxFrequency.toInt()} Hz",
                "Beste Frequenz" to (session.getBestFrequency()?.name ?: "Keine Treffer")
            ))

            // TREFFER & VERWEILDAUER
            addSectionTitle(document, "TREFFER & VERWEILDAUER", greenColor)

            val table = Table(UnitValue.createPercentArray(floatArrayOf(30f, 25f, 25f, 20f)))
                .useAllAvailableWidth()
                .setMarginBottom(10f)

            // Header
            listOf("Frequenz", "Treffer", "Verweildauer", "Anteil").forEach { header ->
                table.addCell(
                    com.itextpdf.layout.element.Cell()
                        .add(Paragraph(header).setBold())
                        .setPadding(5f)
                )
            }

            // Data
            listOf(
                Triple("108 Hz - Tiefenentspannung", session.hits108Hz, session.time108Hz),
                Triple("216 Hz - Beruhigung", session.hits216Hz, session.time216Hz),
                Triple("324 Hz - Aufmerksamkeit", session.hits324Hz, session.time324Hz),
                Triple("432 Hz - Harmonie", session.hits432Hz, session.time432Hz),
                Triple("648 Hz - Motivation", session.hits648Hz, session.time648Hz)
            ).forEach { (freq, hits, time) ->
                val timeSeconds = time / 1000
                val percentage = if (session.voiceDuration > 0) {
                    (time.toFloat() / session.voiceDuration.toFloat() * 100f).toInt()
                } else 0

                table.addCell(Paragraph(freq).setPadding(5f))
                table.addCell(Paragraph("${hits}×").setPadding(5f))
                table.addCell(Paragraph(formatDuration(timeSeconds)).setPadding(5f))
                table.addCell(Paragraph("$percentage%").setPadding(5f))
            }

            document.add(table)

            // Chart (wenn vorhanden)
            if (chartBitmap != null) {
                addSectionTitle(document, "FREQUENZ-VERLAUF", greenColor)

                val chartBytes = ByteArrayOutputStream()
                chartBitmap.compress(Bitmap.CompressFormat.PNG, 100, chartBytes)
                val chartImage = Image(ImageDataFactory.create(chartBytes.toByteArray()))
                    .setWidth(495f)
                    .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
                    .setMarginBottom(20f)
                document.add(chartImage)
            }

            // Footer
            document.add(
                Paragraph("Erstellt mit HundeWildnis App von www.wildnislehrer.de")
                    .setFontSize(10f)
                    .setFontColor(grayColor)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(30f)
            )

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            try {
                document?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                pdfDocument?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return pdfFile
    }

    private fun addSectionTitle(document: Document, title: String, color: DeviceRgb) {
        val paragraph = Paragraph(title)
            .setFontSize(14f)
            .setBold()
            .setFontColor(color)
            .setMarginTop(15f)
            .setMarginBottom(10f)
        document.add(paragraph)
    }

    private fun addDataTable(document: Document, data: List<Pair<String, String>>) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f)))
            .useAllAvailableWidth()
            .setMarginBottom(10f)

        data.forEach { (label, value) ->
            table.addCell(
                com.itextpdf.layout.element.Cell()
                    .add(Paragraph(label).setBold())
                    .setPadding(5f)
            )
            table.addCell(
                com.itextpdf.layout.element.Cell()
                    .add(Paragraph(value))
                    .setPadding(5f)
            )
        }

        document.add(table)
    }

    private fun formatDuration(seconds: Long): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%d:%02d Min", min, sec)
    }
}