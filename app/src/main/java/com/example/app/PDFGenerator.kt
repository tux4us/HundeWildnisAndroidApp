package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PDFGenerator {

    /**
     * ✅ NEUE FUNKTION: Generiert PDF mit ausgewählten Feldern
     */
    fun generateFilteredDogProfilePDF(
        context: Context,
        profile: DogProfile,
        selectedFields: Set<String>,
        qrBitmap: Bitmap
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        var yPos = 50f
        val leftMargin = 50f
        val rightMargin = 545f
        val lineHeight = 20f

        // ════════════════════════════════════════════════════════════
        // STYLES
        // ════════════════════════════════════════════════════════════
        val titlePaint = Paint().apply {
            color = Color.parseColor("#2d5016")
            textSize = 24f
            isFakeBoldText = true
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#2d5016")
            textSize = 18f
            isFakeBoldText = true
        }

        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isFakeBoldText = true
        }

        val valuePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 12f
        }

        // ════════════════════════════════════════════════════════════
        // TITEL
        // ════════════════════════════════════════════════════════════
        canvas.drawText("🐕 HUNDE-STAMMDATEN", leftMargin, yPos, titlePaint)
        yPos += 40f

        // ════════════════════════════════════════════════════════════
        // FOTO (falls ausgewählt)
        // ════════════════════════════════════════════════════════════
        if (selectedFields.contains("photoUri") && profile.photoUri.isNotEmpty()) {
            try {
                val photoBitmap = BitmapFactory.decodeStream(
                    context.contentResolver.openInputStream(Uri.parse(profile.photoUri))
                )
                val scaledPhoto = Bitmap.createScaledBitmap(photoBitmap, 150, 150, true)
                canvas.drawBitmap(scaledPhoto, leftMargin, yPos, null)
                yPos += 170f
            } catch (e: Exception) {
                // Foto konnte nicht geladen werden
            }
        }

        val allFields = profile.getAllFields()

        // ════════════════════════════════════════════════════════════
        // BASIS-DATEN
        // ════════════════════════════════════════════════════════════
        val basisFields = listOf("name", "breed", "gender", "neutered", "birthDate", "originCountry", "color")
        val basisSelected = basisFields.filter { selectedFields.contains(it) }

        if (basisSelected.isNotEmpty()) {
            canvas.drawText("📋 BASIS-DATEN", leftMargin, yPos, headerPaint)
            yPos += lineHeight + 5f

            basisSelected.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    if (value.isNotEmpty()) {
                        canvas.drawText("$label:", leftMargin, yPos, labelPaint)
                        canvas.drawText(value, leftMargin + 150f, yPos, valuePaint)
                        yPos += lineHeight
                    }
                }
            }
            yPos += 10f
        }

        // ════════════════════════════════════════════════════════════
        // IDENTIFIKATION
        // ════════════════════════════════════════════════════════════
        val idFields = listOf("chipNumber", "taxNumber", "tassoNumber", "petPassportCountry", "petPassportNumber")
        val idSelected = idFields.filter { selectedFields.contains(it) }

        if (idSelected.isNotEmpty()) {
            if (yPos > 750f) { // Neue Seite bei Bedarf
                pdfDocument.finishPage(page)
                val newPage = pdfDocument.startPage(pageInfo)
                val canvas = newPage.canvas  // ✅ RICHTIG - neue Variable!
                yPos = 50f
            }

            canvas.drawText("🔖 IDENTIFIKATION", leftMargin, yPos, headerPaint)
            yPos += lineHeight + 5f

            idSelected.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    if (value.isNotEmpty()) {
                        canvas.drawText("$label:", leftMargin, yPos, labelPaint)
                        canvas.drawText(value, leftMargin + 150f, yPos, valuePaint)
                        yPos += lineHeight
                    }
                }
            }
            yPos += 10f
        }

        // ════════════════════════════════════════════════════════════
        // GESUNDHEIT
        // ════════════════════════════════════════════════════════════
        val healthFields = listOf(
            "vaccinationStatus", "additionalVaccination", "lastDeworming", "lastFleaTick",
            "weight", "weightDate", "shoulderHeight", "bodyLength", "medicalNotes"
        )
        val healthSelected = healthFields.filter { selectedFields.contains(it) }

        if (healthSelected.isNotEmpty()) {
            if (yPos > 750f) {
                pdfDocument.finishPage(page)
                val newPage = pdfDocument.startPage(pageInfo)
                val canvas = newPage.canvas  // ✅ RICHTIG - neue Variable!
                yPos = 50f
            }

            canvas.drawText("💉 GESUNDHEIT", leftMargin, yPos, headerPaint)
            yPos += lineHeight + 5f

            healthSelected.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    if (value.isNotEmpty()) {
                        canvas.drawText("$label:", leftMargin, yPos, labelPaint)

                        // Mehrzeilige Werte (z.B. medicalNotes)
                        if (value.length > 40) {
                            val lines = value.chunked(40)
                            canvas.drawText(lines[0], leftMargin + 150f, yPos, valuePaint)
                            yPos += lineHeight
                            lines.drop(1).forEach { line ->
                                canvas.drawText(line, leftMargin + 150f, yPos, valuePaint)
                                yPos += lineHeight
                            }
                        } else {
                            canvas.drawText(value, leftMargin + 150f, yPos, valuePaint)
                            yPos += lineHeight
                        }
                    }
                }
            }
            yPos += 10f
        }

        // ════════════════════════════════════════════════════════════
        // KONTAKTE
        // ════════════════════════════════════════════════════════════
        val contactFields = listOf("veterinarian", "liabilityInsurance", "healthInsurance", "owner", "additionalEmergencyContact")
        val contactSelected = contactFields.filter { selectedFields.contains(it) }

        if (contactSelected.isNotEmpty()) {
            if (yPos > 750f) {
                pdfDocument.finishPage(page)
                val newPage = pdfDocument.startPage(pageInfo)
                val canvas = newPage.canvas  // ✅ RICHTIG - neue Variable!
                yPos = 50f
            }

            canvas.drawText("👨‍⚕️ KONTAKTE", leftMargin, yPos, headerPaint)
            yPos += lineHeight + 5f

            contactSelected.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    if (value.isNotEmpty()) {
                        canvas.drawText("$label:", leftMargin, yPos, labelPaint)

                        // Mehrzeilige Werte
                        if (value.length > 40) {
                            val lines = value.chunked(40)
                            canvas.drawText(lines[0], leftMargin + 150f, yPos, valuePaint)
                            yPos += lineHeight
                            lines.drop(1).forEach { line ->
                                canvas.drawText(line, leftMargin + 150f, yPos, valuePaint)
                                yPos += lineHeight
                            }
                        } else {
                            canvas.drawText(value, leftMargin + 150f, yPos, valuePaint)
                            yPos += lineHeight
                        }
                    }
                }
            }
            yPos += 10f
        }

        // ════════════════════════════════════════════════════════════
        // FUTTER & VERHALTEN
        // ════════════════════════════════════════════════════════════
        val miscFields = listOf("food", "behavior")
        val miscSelected = miscFields.filter { selectedFields.contains(it) }

        if (miscSelected.isNotEmpty()) {
            if (yPos > 750f) {
                pdfDocument.finishPage(page)
                val newPage = pdfDocument.startPage(pageInfo)
                val canvas = newPage.canvas  // ✅ RICHTIG - neue Variable!
                yPos = 50f
            }

            canvas.drawText("🍖 FUTTER & VERHALTEN", leftMargin, yPos, headerPaint)
            yPos += lineHeight + 5f

            miscSelected.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    if (value.isNotEmpty()) {
                        canvas.drawText("$label:", leftMargin, yPos, labelPaint)

                        // Mehrzeilige Werte
                        if (value.length > 40) {
                            val lines = value.chunked(40)
                            canvas.drawText(lines[0], leftMargin + 150f, yPos, valuePaint)
                            yPos += lineHeight
                            lines.drop(1).forEach { line ->
                                canvas.drawText(line, leftMargin + 150f, yPos, valuePaint)
                                yPos += lineHeight
                            }
                        } else {
                            canvas.drawText(value, leftMargin + 150f, yPos, valuePaint)
                            yPos += lineHeight
                        }
                    }
                }
            }
            yPos += 10f
        }

        // ════════════════════════════════════════════════════════════
        // QR-CODE (am Ende)
        // ════════════════════════════════════════════════════════════
        if (yPos > 650f) {
            pdfDocument.finishPage(page)
            val newPage = pdfDocument.startPage(pageInfo)
            val canvas = newPage.canvas  // ✅ RICHTIG - neue Variable!
            yPos = 50f
        }

        canvas.drawText("📱 QR-CODE", leftMargin, yPos, headerPaint)
        yPos += lineHeight + 10f

        val scaledQR = Bitmap.createScaledBitmap(qrBitmap, 200, 200, true)
        canvas.drawBitmap(scaledQR, leftMargin, yPos, null)
        yPos += 220f

        // Hinweis
        val hintPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
        }
        canvas.drawText("Scannen Sie diesen QR-Code mit einer QR-Scanner-App,", leftMargin, yPos, hintPaint)
        yPos += 15f
        canvas.drawText("um die ausgewählten Stammdaten anzuzeigen.", leftMargin, yPos, hintPaint)

        pdfDocument.finishPage(page)

        // ════════════════════════════════════════════════════════════
        // DATEI SPEICHERN
        // ════════════════════════════════════════════════════════════
        val fileName = "Stammdaten_${profile.name}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMAN).format(Date())}.pdf"
        val file = File(context.cacheDir, fileName)

        FileOutputStream(file).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }

        pdfDocument.close()

        return file
    }

    /**
     * ✅ LEGACY-FUNKTION: Alte Funktion für Abwärtskompatibilität
     * (Falls wo anders im Code noch verwendet)
     */
    fun generateDogProfilePDF(
        context: Context,
        profile: DogProfile,
        qrBitmap: Bitmap
    ): File {
        // Alle Felder auswählen
        val allFields = profile.getAllFields().keys
        return generateFilteredDogProfilePDF(context, profile, allFields, qrBitmap)
    }
}