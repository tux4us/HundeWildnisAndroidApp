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

/**
 * PDFGenerator - Erstellt PDF-Dokumente für Hunde-Stammdaten
 *
 * ✅ KORRIGIERT: Verwendet KEINE deprecated iText-Methoden mehr!
 */
object PDFGenerator {

    /**
     * Generiert ein PDF mit allen Stammdaten eines Hundes
     *
     * @param context Android Context
     * @param profile Hunde-Profil
     * @param qrBitmap QR-Code als Bitmap
     * @return PDF-Datei
     */
    fun generateDogProfilePDF(
        context: Context,
        profile: DogProfile,
        qrBitmap: Bitmap
    ): File {
        // PDF-Datei erstellen
        val pdfFile = File(context.cacheDir, "Stammdaten_${profile.name}.pdf")

        var document: Document? = null
        var pdfDocument: PdfDocument? = null

        try {
            // PDF-Writer und Document erstellen
            val writer = PdfWriter(FileOutputStream(pdfFile))
            pdfDocument = PdfDocument(writer)
            document = Document(pdfDocument)

            // ✅ KORRIGIERT: Keine deprecated Methoden mehr!

            // Farben definieren
            val greenColor = DeviceRgb(45, 80, 22) // #2d5016
            val grayColor = DeviceRgb(128, 128, 128)

            // Titel
            val title = Paragraph("HUNDE-STAMMDATEN")
                .setFontSize(24f)
                .setBold()
                .setFontColor(greenColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20f)
            document.add(title)

            // QR-Code hinzufügen
            val qrBytes = ByteArrayOutputStream()
            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, qrBytes)
            val qrImage = Image(ImageDataFactory.create(qrBytes.toByteArray()))
                .setWidth(150f)
                .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
                .setMarginBottom(20f)
            document.add(qrImage)

            // BASIS-DATEN
            addSectionTitle(document, "BASIS-DATEN", greenColor)
            addDataTable(document, listOf(
                "Name" to profile.name,
                "Rasse" to profile.breed,
                "Geschlecht" to profile.gender,
                "Kastriert" to profile.neutered,
                "Geburtsdatum" to profile.birthDate,
                "Farbe" to profile.color
            ))

            // IDENTIFIKATION
            addSectionTitle(document, "IDENTIFIKATION", greenColor)
            addDataTable(document, listOf(
                "Chip-Nummer" to profile.chipNumber,
                "Steuernummer" to profile.taxNumber,
                "TASSO-Nummer" to profile.tassoNumber
            ))

            // GESUNDHEIT
            addSectionTitle(document, "GESUNDHEIT", greenColor)
            addDataTable(document, listOf(
                "Impfstatus" to profile.vaccinationStatus,
                "Letzte Entwurmung" to profile.lastDeworming,
                "Letzte Floh/Zecke" to profile.lastFleaTick,
                "Gewicht" to "${profile.weight} (${profile.weightDate})",
                "Medizinische Hinweise" to profile.medicalNotes
            ))

            // KONTAKTE
            addSectionTitle(document, "KONTAKTE", greenColor)
            addDataTable(document, listOf(
                "Tierarzt" to profile.veterinarian,
                "Versicherung" to profile.insurance,
                "Besitzer" to profile.owner,
                "Notfallkontakt" to profile.emergencyContact
            ))

            // FUTTER & VERHALTEN
            addSectionTitle(document, "FUTTER & VERHALTEN", greenColor)
            addDataTable(document, listOf(
                "Futter" to profile.food,
                "Verhalten" to profile.behavior
            ))

            // Footer
            val footer = Paragraph("Erstellt mit HundeWildnis App")
                .setFontSize(10f)
                .setFontColor(grayColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(30f)
            document.add(footer)

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            // ✅ WICHTIG: Dokumente schließen in finally-Block
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

    /**
     * Fügt einen Abschnitts-Titel hinzu
     */
    private fun addSectionTitle(document: Document, title: String, color: DeviceRgb) {
        val paragraph = Paragraph(title)
            .setFontSize(14f)
            .setBold()
            .setFontColor(color)
            .setMarginTop(15f)
            .setMarginBottom(10f)
        document.add(paragraph)
    }

    /**
     * Fügt eine Daten-Tabelle hinzu
     */
    private fun addDataTable(document: Document, data: List<Pair<String, String>>) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f)))
            .useAllAvailableWidth()
            .setMarginBottom(10f)

        data.forEach { (label, value) ->
            // Label-Zelle
            val labelCell = com.itextpdf.layout.element.Cell()
                .add(Paragraph(label).setBold())
                .setPadding(5f)
            table.addCell(labelCell)

            // Wert-Zelle
            val valueCell = com.itextpdf.layout.element.Cell()
                .add(Paragraph(if (value.isEmpty()) "-" else value))
                .setPadding(5f)
            table.addCell(valueCell)
        }

        document.add(table)
    }
}