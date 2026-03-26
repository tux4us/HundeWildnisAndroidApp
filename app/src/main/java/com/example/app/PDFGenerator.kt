package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import java.io.ByteArrayOutputStream
import java.io.File

object PDFGenerator {

    fun generateDogProfilePDF(context: Context, profile: DogProfile, qrBitmap: Bitmap): File {
        val fileName = "Hund_${profile.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)

        // WICHTIG: PdfWriter & PdfDocument korrekt initialisieren
        val writer = PdfWriter(file)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)

        try {
            // === TITEL ===
            val titleColor = DeviceRgb(45, 80, 22) // #2d5016
            val title = Paragraph("HUNDE-STAMMDATEN")
                .setFontSize(24f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(titleColor)
                .setMarginBottom(10f)
            document.add(title)

            // === FOTO ===
            if (profile.photoUri.isNotEmpty()) {
                try {
                    val photoUri = Uri.parse(profile.photoUri)
                    val inputStream = context.contentResolver.openInputStream(photoUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                    val imageData = ImageDataFactory.create(stream.toByteArray())
                    val image = Image(imageData).setWidth(200f)

                    val imageCenter = Paragraph().add(image).setTextAlignment(TextAlignment.CENTER)
                    document.add(imageCenter)
                    document.add(Paragraph("\n"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // === BASIS-DATEN ===
            addSection(document, "BASIS-DATEN")
            addField(document, "Name", profile.name)
            addField(document, "Rasse", profile.breed)
            addField(document, "Geschlecht", profile.gender)
            addField(document, "Kastriert", profile.neutered)
            addField(document, "Geburtsdatum", profile.birthDate)
            addField(document, "Farbe", profile.color)
            document.add(Paragraph("\n"))

            // === IDENTIFIKATION ===
            addSection(document, "IDENTIFIKATION")
            addField(document, "Chip-Nummer", profile.chipNumber)
            addField(document, "Steuernummer", profile.taxNumber)
            addField(document, "TASSO-Nummer", profile.tassoNumber)
            document.add(Paragraph("\n"))

            // === GESUNDHEIT ===
            addSection(document, "GESUNDHEIT")
            addField(document, "Impfstatus", profile.vaccinationStatus)
            addField(document, "Letzte Entwurmung", profile.lastDeworming)
            addField(document, "Letzte Floh-/Zeckenprophylaxe", profile.lastFleaTick)
            if (profile.weight.isNotEmpty()) {
                addField(document, "Gewicht", "${profile.weight} kg (${profile.weightDate})")
            }
            addField(document, "Medizinische Hinweise", profile.medicalNotes)
            document.add(Paragraph("\n"))

            // === KONTAKTE ===
            addSection(document, "KONTAKTE")
            addField(document, "Tierarzt", profile.veterinarian)
            addField(document, "Versicherung", profile.insurance)
            addField(document, "Besitzer", profile.owner)
            addField(document, "Notfallkontakt", profile.emergencyContact)
            document.add(Paragraph("\n"))

            // === FUTTER & VERHALTEN ===
            addSection(document, "FUTTER & VERHALTEN")
            addField(document, "Futter", profile.food)
            addField(document, "Besondere Verhaltensweisen", profile.behavior)
            document.add(Paragraph("\n"))

            // === QR-CODE ===
            addSection(document, "QR-CODE")
            document.add(Paragraph("Alle Daten als QR-Code:").setFontSize(10f).setMarginLeft(10f))

            val qrStream = ByteArrayOutputStream()
            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, qrStream)
            val qrImageData = ImageDataFactory.create(qrStream.toByteArray())
            val qrImage = Image(qrImageData).setWidth(250f)

            val qrCenter = Paragraph().add(qrImage).setTextAlignment(TextAlignment.CENTER)
            document.add(qrCenter)

            // === FOOTER ===
            document.add(Paragraph("\n"))
            val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMAN)
            val footer = Paragraph("Erstellt mit HundeWildnis App - ${dateFormat.format(java.util.Date())}")
                .setFontSize(8f)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
            document.add(footer)

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            // WICHTIG: Dokument schließen
            document.close()
        }

        return file
    }

    private fun addSection(document: Document, title: String) {
        val sectionColor = DeviceRgb(45, 80, 22)
        val section = Paragraph(title)
            .setFontSize(14f)
            .setBold()
            .setFontColor(sectionColor)
            .setMarginBottom(5f)
        document.add(section)
    }

    private fun addField(document: Document, label: String, value: String) {
        if (value.isNotEmpty()) {
            val field = Paragraph("$label: $value")
                .setFontSize(11f)
                .setMarginLeft(10f)
                .setMarginBottom(3f)
            document.add(field)
        }
    }
}
