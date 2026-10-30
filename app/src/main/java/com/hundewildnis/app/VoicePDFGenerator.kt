package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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

        val document = PdfDoc(FileOutputStream(pdfFile))

        try {
            val greenColor = Color.rgb(45, 80, 22)
            val grayColor = Color.rgb(128, 128, 128)

            // Titel
            document.add(
                PdfParagraph("KOMMUNIKATIONS-ANALYSE")
                    .setFontSize(24f)
                    .setBold()
                    .setFontColor(greenColor)
                    .setTextAlignment(PdfAlign.CENTER)
                    .setMarginBottom(20f)
            )

            // Datum
            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))
            document.add(
                PdfParagraph("Datum: $dateStr")
                    .setFontSize(12f)
                    .setTextAlignment(PdfAlign.CENTER)
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

            val table = PdfTable.percent(floatArrayOf(30f, 25f, 25f, 20f))
                .useAllAvailableWidth()
                .setMarginBottom(10f)

            // Header
            listOf("Frequenz", "Treffer", "Verweildauer", "Anteil").forEach { header ->
                table.addCell(
                    PdfCell()
                        .add(PdfParagraph(header).setBold())
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

                table.addCell(PdfParagraph(freq).setPadding(5f))
                table.addCell(PdfParagraph("${hits}×").setPadding(5f))
                table.addCell(PdfParagraph(formatDuration(timeSeconds)).setPadding(5f))
                table.addCell(PdfParagraph("$percentage%").setPadding(5f))
            }

            document.add(table)

            // Chart (wenn vorhanden)
            if (chartBitmap != null) {
                addSectionTitle(document, "FREQUENZ-VERLAUF", greenColor)

                val chartBytes = ByteArrayOutputStream()
                chartBitmap.compress(Bitmap.CompressFormat.PNG, 100, chartBytes)
                val chartImage = PdfImage(chartBytes.toByteArray())
                    .setWidth(495f)
                    .setHorizontalAlignment(PdfAlign.CENTER)
                    .setMarginBottom(20f)
                document.add(chartImage)
            }

            // Footer
            document.add(
                PdfParagraph("Erstellt mit HundeWildnis App")
                    .setFontSize(10f)
                    .setFontColor(grayColor)
                    .setTextAlignment(PdfAlign.CENTER)
                    .setMarginTop(30f)
            )

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            try {
                document.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return pdfFile
    }

    private fun addSectionTitle(document: PdfDoc, title: String, color: Int) {
        val paragraph = PdfParagraph(title)
            .setFontSize(14f)
            .setBold()
            .setFontColor(color)
            .setMarginTop(15f)
            .setMarginBottom(10f)
        document.add(paragraph)
    }

    private fun addDataTable(document: PdfDoc, data: List<Pair<String, String>>) {
        val table = PdfTable.percent(floatArrayOf(35f, 65f))
            .useAllAvailableWidth()
            .setMarginBottom(10f)

        data.forEach { (label, value) ->
            table.addCell(
                PdfCell()
                    .add(PdfParagraph(label).setBold())
                    .setPadding(5f)
            )
            table.addCell(
                PdfCell()
                    .add(PdfParagraph(value))
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
