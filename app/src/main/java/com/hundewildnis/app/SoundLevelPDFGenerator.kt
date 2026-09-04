package com.hundewildnis.app

import android.content.Context
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

object SoundLevelPDFGenerator {

    fun generateSessionPDF(context: Context, session: SoundLevelSession): File {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN)
        val fileName = "Lautstaerketrainer_${dateFormat.format(Date(session.startTime))}.pdf"
        val pdfFile = File(context.cacheDir, fileName)

        var document: Document? = null
        var pdfDocument: PdfDocument? = null

        try {
            val writer = PdfWriter(FileOutputStream(pdfFile))
            pdfDocument = PdfDocument(writer)
            document = Document(pdfDocument)

            val greenColor = DeviceRgb(45, 80, 22)
            val grayColor = DeviceRgb(128, 128, 128)

            document.add(
                Paragraph("LAUTSTÄRKE-TRAINER – AUSWERTUNG")
                    .setFontSize(24f)
                    .setBold()
                    .setFontColor(greenColor)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20f)
            )

            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))
            document.add(
                Paragraph("Datum: $dateStr")
                    .setFontSize(12f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20f)
            )

            addSectionTitle(document, "ÜBERSICHT", greenColor)
            val duration = session.totalDuration / 1000
            addDataTable(document, listOf(
                "Dauer" to formatDuration(duration),
                "Ø Pegel" to "${session.avgEstimatedDb.toInt()} dB(A)",
                "Minimum" to "${session.minEstimatedDb.toInt()} dB(A)",
                "Maximum" to "${session.maxEstimatedDb.toInt()} dB(A)",
                "Dominante Stufe" to session.getDominantLevel().label
            ))

            addSectionTitle(document, "VERTEILUNG SPRECHWEISE-STUFEN", greenColor)
            val table = Table(UnitValue.createPercentArray(floatArrayOf(45f, 20f, 20f, 15f)))
                .useAllAvailableWidth()
                .setMarginBottom(10f)

            listOf("Stufe", "Bereich", "Datenpunkte", "Anteil").forEach { header ->
                table.addCell(Cell().add(Paragraph(header).setBold()).setPadding(5f))
            }

            listOf(
                SpeechLevel.A1_LEISE to session.countA1,
                SpeechLevel.A2_GESPRAECH to session.countA2,
                SpeechLevel.A3_ANGEHOBEN to session.countA3,
                SpeechLevel.A4_SCHARF to session.countA4
            ).forEach { (level, count) ->
                table.addCell(Paragraph(level.label).setPadding(5f))
                table.addCell(Paragraph(level.rangeLabel).setPadding(5f))
                table.addCell(Paragraph("$count").setPadding(5f))
                table.addCell(Paragraph("${session.getPercentage(count)}%").setPadding(5f))
            }
            document.add(table)

            document.add(
                Paragraph(
                    "Hinweis: Geschätzter Wert auf Basis einer einmaligen Nutzerkalibrierung. " +
                        "Kein kalibriertes Messgerät nach DIN EN 61672 – dient nur der eigenen " +
                        "Vergleichbarkeit über mehrere Sessions. Die Wirkung auf den Hund wird " +
                        "dadurch nicht bewertet; entscheidend sind Tonhöhe, Plötzlichkeit, " +
                        "Vorhersagbarkeit und die Beobachtung des Hundeverhaltens."
                )
                    .setFontSize(9f)
                    .setFontColor(grayColor)
                    .setMarginTop(10f)
                    .setMarginBottom(10f)
            )

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
            try { document?.close() } catch (e: Exception) { e.printStackTrace() }
            try { pdfDocument?.close() } catch (e: Exception) { e.printStackTrace() }
        }

        return pdfFile
    }

    private fun addSectionTitle(document: Document, title: String, color: DeviceRgb) {
        document.add(
            Paragraph(title)
                .setFontSize(14f)
                .setBold()
                .setFontColor(color)
                .setMarginTop(15f)
                .setMarginBottom(10f)
        )
    }

    private fun addDataTable(document: Document, data: List<Pair<String, String>>) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f)))
            .useAllAvailableWidth()
            .setMarginBottom(10f)

        data.forEach { (label, value) ->
            table.addCell(Cell().add(Paragraph(label).setBold()).setPadding(5f))
            table.addCell(Cell().add(Paragraph(value)).setPadding(5f))
        }
        document.add(table)
    }

    private fun formatDuration(seconds: Long): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%d:%02d Min", min, sec)
    }
}
