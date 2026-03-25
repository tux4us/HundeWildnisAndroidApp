package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
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
        
        val writer = PdfWriter(file)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc)
        
        // === TITEL ===
        document.add(
            Paragraph("🐕 HUNDE-STAMMDATEN")
                .setFontSize(24f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.DARK_GRAY)
        )
        document.add(Paragraph("\n"))
        
        // === FOTO ===
        if (profile.photoUri.isNotEmpty()) {
            try {
                val photoUri = Uri.parse(profile.photoUri)
                val inputStream = context.contentResolver.openInputStream(photoUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val imageData = ImageDataFactory.create(stream.toByteArray())
                val image = Image(imageData)
                    .setWidth(200f)
                    .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
                
                document.add(image)
                document.add(Paragraph("\n"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // === BASIS-DATEN ===
        document.add(Paragraph("📋 BASIS-DATEN").setFontSize(16f).setBold())
        addField(document, "Name", profile.name)
        addField(document, "Rasse", profile.breed)
        addField(document, "Geschlecht", profile.gender)
        addField(document, "Kastriert", profile.neutered)
        addField(document, "Geburtsdatum", profile.birthDate)
        addField(document, "Farbe", profile.color)
        document.add(Paragraph("\n"))
        
        // === IDENTIFIKATION ===
        document.add(Paragraph("🔖 IDENTIFIKATION").setFontSize(16f).setBold())
        addField(document, "Chip-Nummer", profile.chipNumber)
        addField(document, "Steuernummer", profile.taxNumber)
        addField(document, "TASSO-Nummer", profile.tassoNumber)
        document.add(Paragraph("\n"))
        
        // === GESUNDHEIT ===
        document.add(Paragraph("💉 GESUNDHEIT").setFontSize(16f).setBold())
        addField(document, "Impfstatus", profile.vaccinationStatus)
        addField(document, "Letzte Entwurmung", profile.lastDeworming)
        addField(document, "Letzte Floh-/Zeckenprophylaxe", profile.lastFleaTick)
        addField(document, "Gewicht", "${profile.weight} (${profile.weightDate})")
        addField(document, "Medizinische Hinweise", profile.medicalNotes)
        document.add(Paragraph("\n"))
        
        // === KONTAKTE ===
        document.add(Paragraph("👨‍⚕️ KONTAKTE").setFontSize(16f).setBold())
        addField(document, "Tierarzt", profile.veterinarian)
        addField(document, "Versicherung", profile.insurance)
        addField(document, "Besitzer", profile.owner)
        addField(document, "Notfallkontakt", profile.emergencyContact)
        document.add(Paragraph("\n"))
        
        // === FUTTER & VERHALTEN ===
        document.add(Paragraph("🍖 FUTTER & VERHALTEN").setFontSize(16f).setBold())
        addField(document, "Futter", profile.food)
        addField(document, "Besondere Verhaltensweisen", profile.behavior)
        document.add(Paragraph("\n"))
        
        // === QR-CODE ===
        document.add(Paragraph("📱 QR-CODE").setFontSize(16f).setBold())
        document.add(Paragraph("Alle Daten als QR-Code:").setFontSize(10f))
        
        val qrStream = ByteArrayOutputStream()
        qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, qrStream)
        val qrImageData = ImageDataFactory.create(qrStream.toByteArray())
        val qrImage = Image(qrImageData)
            .setWidth(250f)
            .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
        
        document.add(qrImage)
        
        // === FOOTER ===
        document.add(Paragraph("\n"))
        document.add(
            Paragraph("Erstellt mit HundeWildnis App • ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMAN).format(java.util.Date())}")
                .setFontSize(8f)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
        )
        
        document.close()
        return file
    }
    
    private fun addField(document: Document, label: String, value: String) {
        if (value.isNotEmpty()) {
            document.add(
                Paragraph("$label: $value")
                    .setFontSize(11f)
                    .setMarginLeft(10f)
            )
        }
    }
}
