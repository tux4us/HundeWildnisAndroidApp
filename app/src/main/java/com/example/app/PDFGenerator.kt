package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PDFGenerator {

    private const val TAG = "PDFGenerator"

    /**
     * Generiert PDF mit ausgewählten Feldern
     */
    fun generateFilteredDogProfilePDF(
        context: Context,
        profile: DogProfile,
        selectedFields: Set<String>,
        qrBitmap: Bitmap
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        
        var currentPage = pdfDocument.startPage(pageInfo)
        var canvas = currentPage.canvas
        
        var yPos = 50f
        val leftMargin = 50f
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

        // Helper zum Seitenumbruch
        fun ensureSpace(needed: Float) {
            if (yPos + needed > 780f) {
                pdfDocument.finishPage(currentPage)
                currentPage = pdfDocument.startPage(pageInfo)
                canvas = currentPage.canvas
                yPos = 50f
            }
        }

        // ════════════════════════════════════════════════════════════
        // TITEL
        // ════════════════════════════════════════════════════════════
        ensureSpace(40f)
        canvas.drawText("🐕 HUNDE-STAMMDATEN", leftMargin, yPos, titlePaint)
        yPos += 40f

        // ════════════════════════════════════════════════════════════
        // FOTO
        // ════════════════════════════════════════════════════════════
        if (selectedFields.contains("photoUri") && profile.photoUri.isNotEmpty()) {
            try {
                val inputStream = context.contentResolver.openInputStream(Uri.parse(profile.photoUri))
                val photoBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                if (photoBitmap != null) {
                    ensureSpace(170f)
                    val ratio = photoBitmap.width.toFloat() / photoBitmap.height.toFloat()
                    val targetWidth = 150f
                    val targetHeight = targetWidth / ratio
                    
                    val scaledPhoto = Bitmap.createScaledBitmap(photoBitmap, targetWidth.toInt(), targetHeight.toInt(), true)
                    canvas.drawBitmap(scaledPhoto, leftMargin, yPos, null)
                    yPos += targetHeight + 20f
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Laden des Hundefotos: ${e.message}")
            }
        }

        val allFields = profile.getAllFields()

        // Kategorien und Felder
        val sections = listOf(
            Pair("📋 BASIS-DATEN", listOf("name", "breed", "gender", "neutered", "birthDate", "originCountry", "color")),
            Pair("🔖 IDENTIFIKATION", listOf("chipNumber", "taxNumber", "tassoNumber", "petPassportCountry", "petPassportNumber")),
            Pair("💉 GESUNDHEIT", listOf("vaccinationStatus", "additionalVaccination", "lastDeworming", "lastFleaTick", "weight", "weightDate", "shoulderHeight", "bodyLength", "medicalNotes")),
            Pair("👨‍⚕️ KONTAKTE", listOf("veterinarian", "liabilityInsurance", "healthInsurance", "owner", "additionalEmergencyContact")),
            Pair("🍖 FUTTER & VERHALTEN", listOf("food", "behavior"))
        )

        sections.forEach { (title, fieldKeys) ->
            val selectedInSection = fieldKeys.filter { selectedFields.contains(it) && allFields[it]?.second?.isNotEmpty() == true }
            
            if (selectedInSection.isNotEmpty()) {
                ensureSpace(lineHeight + 10f + (selectedInSection.size * lineHeight))
                
                canvas.drawText(title, leftMargin, yPos, headerPaint)
                yPos += lineHeight + 5f

                selectedInSection.forEach { key ->
                    allFields[key]?.let { (label, value) ->
                        canvas.drawText("$label:", leftMargin, yPos, labelPaint)
                        
                        // Textumbruch für lange Werte
                        if (value.length > 45) {
                            val lines = value.chunked(45)
                            lines.forEach { line ->
                                ensureSpace(lineHeight)
                                canvas.drawText(line, leftMargin + 160f, yPos, valuePaint)
                                if (line != lines.last()) yPos += lineHeight
                            }
                        } else {
                            canvas.drawText(value, leftMargin + 160f, yPos, valuePaint)
                        }
                        yPos += lineHeight
                    }
                }
                yPos += 10f
            }
        }

        // ════════════════════════════════════════════════════════════
        // QR-CODE
        // ════════════════════════════════════════════════════════════
        ensureSpace(250f)
        canvas.drawText("📱 QR-CODE", leftMargin, yPos, headerPaint)
        yPos += lineHeight + 10f

        val scaledQR = Bitmap.createScaledBitmap(qrBitmap, 180, 180, true)
        canvas.drawBitmap(scaledQR, leftMargin, yPos, null)
        yPos += 200f

        val hintPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
        }
        canvas.drawText("Scannen Sie diesen QR-Code mit einer QR-Scanner-App,", leftMargin, yPos, hintPaint)
        yPos += 15f
        canvas.drawText("um die ausgewählten Stammdaten anzuzeigen.", leftMargin, yPos, hintPaint)

        // Footer
        ensureSpace(20f)
        val footerPaint = Paint().apply {
            color = Color.LTGRAY
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date())
        canvas.drawText("Erstellt am $dateStr mit HundeWildnis App - www.wildnislehrer.de", 297f, 820f, footerPaint)

        pdfDocument.finishPage(currentPage)

        // Speichern
        val fileName = "Stammdaten_${profile.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)

        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    fun generateDogProfilePDF(context: Context, profile: DogProfile, qrBitmap: Bitmap): File {
        return generateFilteredDogProfilePDF(context, profile, profile.getAllFields().keys, qrBitmap)
    }
}
