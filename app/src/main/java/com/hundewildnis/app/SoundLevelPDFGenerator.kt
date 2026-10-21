package com.hundewildnis.app

import android.content.Context
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object SoundLevelPDFGenerator {

    fun generateSessionPDF(context: Context, session: SoundLevelSession): File {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN)
        val fileName = "Lautstaerketrainer_${dateFormat.format(Date(session.startTime))}.pdf"
        val pdfFile = File(context.cacheDir, fileName)

        val document = PdfDoc(FileOutputStream(pdfFile))

        try {
            val greenColor = Color.rgb(45, 80, 22)
            val grayColor = Color.rgb(128, 128, 128)

            document.add(
                PdfParagraph(context.getString(R.string.sound_level_pdf_title))
                    .setFontSize(24f)
                    .setBold()
                    .setFontColor(greenColor)
                    .setTextAlignment(PdfAlign.CENTER)
                    .setMarginBottom(20f)
            )

            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))
            document.add(
                PdfParagraph(context.getString(R.string.sound_level_pdf_date, dateStr))
                    .setFontSize(12f)
                    .setTextAlignment(PdfAlign.CENTER)
                    .setMarginBottom(20f)
            )

            addSectionTitle(document, context.getString(R.string.sound_level_pdf_overview), greenColor)
            val duration = session.totalDuration / 1000
            addDataTable(document, listOf(
                context.getString(R.string.sound_level_pdf_duration) to formatDuration(duration),
                context.getString(R.string.sound_level_pdf_avg_level) to "${session.avgEstimatedDb.toInt()} dB(A)",
                context.getString(R.string.sound_level_pdf_minimum) to "${session.minEstimatedDb.toInt()} dB(A)",
                context.getString(R.string.sound_level_pdf_maximum) to "${session.maxEstimatedDb.toInt()} dB(A)",
                context.getString(R.string.sound_level_pdf_dominant_level) to session.getDominantLevel().label(context)
            ))

            addSectionTitle(document, context.getString(R.string.sound_level_pdf_distribution), greenColor)
            val table = PdfTable.percent(floatArrayOf(45f, 20f, 20f, 15f))
                .useAllAvailableWidth()
                .setMarginBottom(10f)

            listOf(
                context.getString(R.string.sound_level_pdf_column_level),
                context.getString(R.string.sound_level_pdf_column_range),
                context.getString(R.string.sound_level_pdf_column_data_points),
                context.getString(R.string.sound_level_pdf_column_share)
            ).forEach { header ->
                table.addCell(PdfCell().add(PdfParagraph(header).setBold()).setPadding(5f))
            }

            listOf(
                SpeechLevel.A1_LEISE to session.countA1,
                SpeechLevel.A2_GESPRAECH to session.countA2,
                SpeechLevel.A3_ANGEHOBEN to session.countA3,
                SpeechLevel.A4_SCHARF to session.countA4
            ).forEach { (level, count) ->
                table.addCell(PdfParagraph(level.label(context)).setPadding(5f))
                table.addCell(PdfParagraph(level.rangeLabel).setPadding(5f))
                table.addCell(PdfParagraph("$count").setPadding(5f))
                table.addCell(PdfParagraph("${session.getPercentage(count)}%").setPadding(5f))
            }
            document.add(table)

            document.add(
                PdfParagraph(context.getString(R.string.sound_level_pdf_disclaimer))
                    .setFontSize(9f)
                    .setFontColor(grayColor)
                    .setMarginTop(10f)
                    .setMarginBottom(10f)
            )

            document.add(
                PdfParagraph(context.getString(R.string.sound_level_pdf_footer))
                    .setFontSize(10f)
                    .setFontColor(grayColor)
                    .setTextAlignment(PdfAlign.CENTER)
                    .setMarginTop(30f)
            )

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            try { document.close() } catch (e: Exception) { e.printStackTrace() }
        }

        return pdfFile
    }

    private fun addSectionTitle(document: PdfDoc, title: String, color: Int) {
        document.add(
            PdfParagraph(title)
                .setFontSize(14f)
                .setBold()
                .setFontColor(color)
                .setMarginTop(15f)
                .setMarginBottom(10f)
        )
    }

    private fun addDataTable(document: PdfDoc, data: List<Pair<String, String>>) {
        val table = PdfTable.percent(floatArrayOf(35f, 65f))
            .useAllAvailableWidth()
            .setMarginBottom(10f)

        data.forEach { (label, value) ->
            table.addCell(PdfCell().add(PdfParagraph(label).setBold()).setPadding(5f))
            table.addCell(PdfCell().add(PdfParagraph(value)).setPadding(5f))
        }
        document.add(table)
    }

    private fun formatDuration(seconds: Long): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%d:%02d Min", min, sec)
    }
}
