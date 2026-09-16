package com.hundewildnis.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * NasenarbeitPDFGenerator – iText7
 * Features: Notizen (Titel + Text) · Fotos (Galerie + Kamera) · GPS-Kartenkachel (OSM Static)
 */
object NasenarbeitPDFGenerator {

    private val df     = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    private val dfDate = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)

    private val GREEN = DeviceRgb(45, 80, 22)       // #2d5016
    private val GRAY  = DeviceRgb(100, 100, 100)
    private val BLUE  = DeviceRgb(0, 80, 180)

    // ════════════════════════════════════════════════════════════
    // SESSION PDF
    // ════════════════════════════════════════════════════════════

    fun generateSessionPDF(
        context: Context,
        session: NasenarbeitSession,
        profile: DogProfile,
        saveToPublic: Boolean = false
    ): File? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMAN).format(Date(session.datum))
        val fileName = "Nasenarbeit_${profile.name}_$timestamp.pdf"

        if (saveToPublic) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportPdfViaMediaStore(context, fileName) { doc -> writeSessionContent(context, doc, session, profile) }
            } else {
                exportPdfOldWay(fileName) { doc -> writeSessionContent(context, doc, session, profile) }
            }
        }

        val dir  = File(context.filesDir, "nasenarbeit_pdfs").also { it.mkdirs() }
        val file = File(dir, "nasenarbeit_session_${session.id.take(8)}.pdf")

        try {
            val writer = PdfWriter(file)
            val pdf    = PdfDocument(writer)
            val doc    = Document(pdf)
            writeSessionContent(context, doc, session, profile)
            doc.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun writeSessionContent(
        context: Context,
        doc: Document,
        session: NasenarbeitSession,
        profile: DogProfile
    ) {
        // ── Titel ─────────────────────────────────────────────
        doc.add(Paragraph(context.getString(R.string.nasenarbeit_pdf_title))
            .setBold().setFontSize(18f).setFontColor(GREEN))
        doc.add(Paragraph("${profile.name}  ·  ${profile.breed}  ·  ${profile.gender}")
            .setFontSize(12f).setFontColor(GRAY))
        doc.add(Paragraph(" "))

        // ── Session Info ──────────────────────────────────────
        addSectionTitle(doc, context.getString(R.string.nasenarbeit_pdf_section_session))
        val tbl = twoColTable()
        tbl.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_date)))
        tbl.addCell(valueCell(df.format(Date(session.datum))))
        tbl.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_discipline)))
        tbl.addCell(valueCell("${session.durchgefuehrteDisziplin.icon} ${session.durchgefuehrteDisziplin.displayName(context)}"))
        tbl.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_recommended)))
        tbl.addCell(valueCell("${session.empfohleneDisziplin.icon} ${session.empfohleneDisziplin.displayName(context)}"))
        tbl.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_rating)))
        tbl.addCell(valueCell("⭐".repeat(session.erfolgsbewertung)))
        doc.add(tbl)
        doc.add(Paragraph(" "))

        // ── Ergebnisse ────────────────────────────────────────
        addSectionTitle(doc, context.getString(R.string.nasenarbeit_pdf_section_results))
        val res = twoColTable()
        session.durchgefuehrteSchritte?.let { res.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_route))); res.addCell(valueCell("$it " + context.getString(R.string.nasenarbeit_map_result_steps_unit))) }
        session.durchgefuehrteHa?.let { res.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_area))); res.addCell(valueCell("${String.format(Locale.US, "%.1f", it)} ha")) }
        session.suchdauerMinuten?.let { res.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_duration))); res.addCell(valueCell("$it " + context.getString(R.string.nasenarbeit_session_detail_duration_unit))) }
        session.gefundeneGegenstaende?.let { g ->
            session.gegenstaendeGesamt?.let { ges ->
                res.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_items))); res.addCell(valueCell(context.getString(R.string.nasenarbeit_session_detail_items_found, g, ges)))
            }
        }
        session.personenfundErfolgreich?.let {
            res.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_person_found)))
            res.addCell(valueCell(if (it) "✅ " + context.getString(R.string.nasenarbeit_session_detail_person_found_success) else "❌ " + context.getString(R.string.nasenarbeit_session_detail_person_found_failure)))
        }
        doc.add(res)
        doc.add(Paragraph(" "))

        // ── Scoring ───────────────────────────────────────────
        addSectionTitle(doc, context.getString(R.string.nasenarbeit_pdf_section_scoring))
        val sc = twoColTable()
        sc.addCell(labelCell(NasenarbeitDisziplin.VERLORENSUCHE.icon + " " + context.getString(R.string.nasenarbeit_disziplin_verlorensuche)))
        sc.addCell(valueCell(fmtScore(session.scoreVerlorensuche, context)))
        sc.addCell(labelCell(NasenarbeitDisziplin.PERSONENSUCHE.icon + " " + context.getString(R.string.nasenarbeit_disziplin_personensuche)))
        sc.addCell(valueCell(fmtScore(session.scorePersonensuche, context)))
        sc.addCell(labelCell(NasenarbeitDisziplin.FREISUCHE.icon + " " + context.getString(R.string.nasenarbeit_disziplin_freisuche)))
        sc.addCell(valueCell(fmtScore(session.scoreFreisuche, context)))
        doc.add(sc)
        doc.add(Paragraph(" "))

        // ── Wetter ───────────────────────────────────────────
        addSectionTitle(doc, context.getString(R.string.nasenarbeit_pdf_section_weather))
        val p = session.parameter
        val wx = twoColTable()
        wx.addCell(labelCell(context.getString(R.string.nasenarbeit_beratung_wetter_temp))); wx.addCell(valueCell("${p.temperaturCelsius}°C"))
        wx.addCell(labelCell(context.getString(R.string.nasenarbeit_beratung_wetter_beaufort)));   wx.addCell(valueCell("Bf ${p.beaufort}"))
        wx.addCell(labelCell(context.getString(R.string.nasenarbeit_beratung_wetter_humidity))); wx.addCell(valueCell("${p.luftfeuchtigkeit}%"))
        wx.addCell(labelCell(context.getString(R.string.nasenarbeit_session_detail_wind_label))); wx.addCell(valueCell(listOf(context.getString(R.string.nasenarbeit_session_detail_wind_tailwind),context.getString(R.string.nasenarbeit_session_detail_wind_crosswind),context.getString(R.string.nasenarbeit_session_detail_wind_headwind))[p.windrichtung]))
        doc.add(wx)
        doc.add(Paragraph(" "))

        // ── Trainingslevel ───────────────────────────────────
        addSectionTitle(doc, context.getString(R.string.nasenarbeit_pdf_section_level))
        val lv = twoColTable()
        lv.addCell(labelCell(NasenarbeitDisziplin.VERLORENSUCHE.icon + " " + context.getString(R.string.nasenarbeit_disziplin_verlorensuche))); lv.addCell(valueCell(session.levelVerlorensuche))
        lv.addCell(labelCell(NasenarbeitDisziplin.PERSONENSUCHE.icon + " " + context.getString(R.string.nasenarbeit_disziplin_personensuche))); lv.addCell(valueCell(session.levelPersonensuche))
        lv.addCell(labelCell(NasenarbeitDisziplin.FREISUCHE.icon + " " + context.getString(R.string.nasenarbeit_disziplin_freisuche)));     lv.addCell(valueCell(session.levelFreisuche))
        doc.add(lv)
        doc.add(Paragraph(" "))

        // ── Notizen ───────────────────────────────────────────
        if (session.notizenTitel.isNotEmpty() || session.notizen.isNotEmpty()) {
            addSectionTitle(doc, context.getString(R.string.nasenarbeit_pdf_section_notes))
            if (session.notizenTitel.isNotEmpty()) {
                doc.add(Paragraph(session.notizenTitel).setBold().setFontSize(13f))
            }
            if (session.notizen.isNotEmpty()) {
                doc.add(Paragraph(session.notizen).setFontSize(12f))
            }
            doc.add(Paragraph(" "))
        }

        // ── GPS + Karte ───────────────────────────────────────
        if (session.gpsLat != 0.0 && session.gpsLon != 0.0) {
            addSectionTitle(doc, context.getString(R.string.nasenarbeit_pdf_section_location))
            val gps = twoColTable()
            gps.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_coordinates)))
            gps.addCell(valueCell("${String.format("%.5f", session.gpsLat)}, ${String.format("%.5f", session.gpsLon)}"))
            gps.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_map)))
            val osmUrl = "https://www.openstreetmap.org/?mlat=${session.gpsLat}&mlon=${session.gpsLon}#map=16/${session.gpsLat}/${session.gpsLon}"
            gps.addCell(valueCell(osmUrl))
            doc.add(gps)

            // Static map tile via OpenStreetMap tile server
            val mapBitmap = fetchOsmTile(context, session.gpsLat, session.gpsLon, zoom = 15)
            if (mapBitmap != null) {
                doc.add(Paragraph(" "))
                val stream = ByteArrayOutputStream()
                mapBitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
                val mapImg = Image(ImageDataFactory.create(stream.toByteArray()))
                    .setWidth(UnitValue.createPercentValue(80f))
                    .setHorizontalAlignment(HorizontalAlignment.CENTER)
                doc.add(mapImg)
                doc.add(Paragraph("© OpenStreetMap contributors")
                    .setFontSize(8f).setFontColor(GRAY).setTextAlignment(TextAlignment.CENTER))
                mapBitmap.recycle()
            } else {
                doc.add(Paragraph(context.getString(R.string.nasenarbeit_pdf_label_map_tile) + ": $osmUrl")
                    .setFontSize(10f).setFontColor(BLUE))
            }
            doc.add(Paragraph(" "))
        }

        // ── Fotos ─────────────────────────────────────────────
        if (session.fotoUris.isNotEmpty()) {
            doc.add(AreaBreak())
            addSectionTitle(doc, context.getString(R.string.nasenarbeit_pdf_section_photos, session.fotoUris.size))
            doc.add(Paragraph(" "))
            addPhotosGrid(context, doc, session.fotoUris)
        }

        // ── Footer ───────────────────────────────────────────
        doc.add(Paragraph(" "))
        doc.add(Paragraph("HundeWildnis App · " + context.getString(R.string.training_category_nasenarbeit) + " · ${dfDate.format(Date())}")
            .setFontSize(8f).setFontColor(GRAY).setTextAlignment(TextAlignment.CENTER))
    }

    // ════════════════════════════════════════════════════════════
    // GESAMT PDF
    // ════════════════════════════════════════════════════════════

    fun generateGesamtPDF(
        context: Context,
        sessions: List<NasenarbeitSession>,
        fortschritt: NasenarbeitFortschritt,
        profile: DogProfile,
        saveToPublic: Boolean = false
    ): File? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMAN).format(Date())
        val fileName = "Nasenarbeit_Gesamt_${profile.name}_$timestamp.pdf"

        if (saveToPublic) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportPdfViaMediaStore(context, fileName) { doc -> writeGesamtContent(context, doc, sessions, fortschritt, profile) }
            } else {
                exportPdfOldWay(fileName) { doc -> writeGesamtContent(context, doc, sessions, fortschritt, profile) }
            }
        }

        val dir  = File(context.filesDir, "nasenarbeit_pdfs").also { it.mkdirs() }
        val file = File(dir, "nasenarbeit_gesamt_${profile.id.take(8)}.pdf")

        try {
            val writer = PdfWriter(file)
            val pdf    = PdfDocument(writer)
            val doc    = Document(pdf)
            writeGesamtContent(context, doc, sessions, fortschritt, profile)
            doc.close()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun writeGesamtContent(
        context: Context,
        doc: Document,
        sessions: List<NasenarbeitSession>,
        fortschritt: NasenarbeitFortschritt,
        profile: DogProfile
    ) {
        // ── Titel ─────────────────────────────────────────────
        doc.add(Paragraph(context.getString(R.string.nasenarbeit_pdf_total_title))
            .setBold().setFontSize(18f).setFontColor(GREEN))
        doc.add(Paragraph("${profile.name}  ·  ${profile.breed}")
            .setFontSize(12f).setFontColor(GRAY))
        doc.add(Paragraph(" "))

        // ── Übersicht ─────────────────────────────────────────
        addSectionTitle(doc, context.getString(R.string.nasenarbeit_pdf_section_overview))
        val ov = twoColTable()
        ov.addCell(labelCell(NasenarbeitDisziplin.VERLORENSUCHE.icon + " " + context.getString(R.string.nasenarbeit_disziplin_verlorensuche)))
        ov.addCell(valueCell(context.getString(R.string.nasenarbeit_pdf_summary_line, fortschritt.sessionsVerlorensuche, fortschritt.levelVerlorensuche.displayName(context))))
        ov.addCell(labelCell(NasenarbeitDisziplin.PERSONENSUCHE.icon + " " + context.getString(R.string.nasenarbeit_disziplin_personensuche)))
        ov.addCell(valueCell(context.getString(R.string.nasenarbeit_pdf_summary_line, fortschritt.sessionsPersonensuche, fortschritt.levelPersonensuche.displayName(context))))
        ov.addCell(labelCell(NasenarbeitDisziplin.FREISUCHE.icon + " " + context.getString(R.string.nasenarbeit_disziplin_freisuche)))
        ov.addCell(valueCell(context.getString(R.string.nasenarbeit_pdf_summary_line, fortschritt.sessionsFreisuche, fortschritt.levelFreisuche.displayName(context))))
        ov.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_total)))
        ov.addCell(valueCell(context.getString(R.string.nasenarbeit_dog_adapter_sessions_count, fortschritt.gesamtSessions)))
        if (fortschritt.maxSchritte > 0) {
            ov.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_max_route))); ov.addCell(valueCell("${fortschritt.maxSchritte} " + context.getString(R.string.nasenarbeit_map_result_steps_unit)))
        }
        if (fortschritt.maxHa > 0.0) {
            ov.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_max_area))); ov.addCell(valueCell("${String.format(Locale.US, "%.1f", fortschritt.maxHa)} ha"))
        }
        if (fortschritt.letzteSession > 0) {
            ov.addCell(labelCell(context.getString(R.string.nasenarbeit_pdf_label_last_session))); ov.addCell(valueCell(dfDate.format(Date(fortschritt.letzteSession))))
        }
        doc.add(ov)
        doc.add(Paragraph(" "))

        // ── Sessionliste ──────────────────────────────────────
        addSectionTitle(doc, context.getString(R.string.nasenarbeit_pdf_section_all_sessions, sessions.size))
        doc.add(Paragraph(" "))

        sessions.forEach { s ->
            val line = Paragraph().apply {
                add(Text(df.format(Date(s.datum))).setFontSize(10f))
                add(Text("  ${s.durchgefuehrteDisziplin.icon} ${s.durchgefuehrteDisziplin.displayName(context)}").setFontSize(10f).setBold())
                s.durchgefuehrteSchritte?.let { add(Text("  $it " + context.getString(R.string.nasenarbeit_map_result_steps_unit)).setFontSize(10f)) }
                s.durchgefuehrteHa?.let { add(Text("  ${String.format(Locale.US, "%.1f",it)} ha").setFontSize(10f)) }
                add(Text("  ${"⭐".repeat(s.erfolgsbewertung)}").setFontSize(10f))
            }
            doc.add(line)
        }

        // ── Footer ───────────────────────────────────────────
        doc.add(Paragraph(" "))
        doc.add(Paragraph("HundeWildnis App · " + context.getString(R.string.nasenarbeit_pdf_total_title) + " · ${dfDate.format(Date())}")
            .setFontSize(8f).setFontColor(GRAY).setTextAlignment(TextAlignment.CENTER))
    }

    // ════════════════════════════════════════════════════════════
    // PUBLIC EXPORT HELPERS
    // ────────────────────────────────────────────────────────────

    private fun exportPdfViaMediaStore(context: Context, fileName: String, writerFunc: (Document) -> Unit): File? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/HundeWildnis")
        }

        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: return null

        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val writer = PdfWriter(outputStream)
                val pdf = PdfDocument(writer)
                val document = Document(pdf)
                writerFunc(document)
                document.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        var filePath: String? = null
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (columnIndex >= 0) filePath = cursor.getString(columnIndex)
            }
        }
        return if (filePath != null) File(filePath) else null
    }

    private fun exportPdfOldWay(fileName: String, writerFunc: (Document) -> Unit): File? {
        val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "HundeWildnis")
        if (!docsDir.exists()) docsDir.mkdirs()
        val file = File(docsDir, fileName)
        return try {
            val writer = PdfWriter(FileOutputStream(file))
            val pdf = PdfDocument(writer)
            val document = Document(pdf)
            writerFunc(document)
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ════════════════════════════════════════════════════════════
    // FOTO GRID
    // ════════════════════════════════════════════════════════════

    private fun addPhotosGrid(context: Context, doc: Document, uris: List<String>) {
        val maxSize = 900
        var photoTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
        var count = 0

        for (uriString in uris) {
            try {
                val uri = Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                val orig = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (orig == null) continue

                val scaled = if (orig.width > maxSize || orig.height > maxSize) {
                    val scale = minOf(maxSize.toFloat() / orig.width, maxSize.toFloat() / orig.height)
                    Bitmap.createScaledBitmap(orig, (orig.width * scale).toInt(), (orig.height * scale).toInt(), true)
                } else orig

                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val img = Image(ImageDataFactory.create(stream.toByteArray())).setAutoScale(true)

                val cell = Cell().add(img)
                cell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                cell.setPadding(8f)
                photoTable.addCell(cell)
                count++

                if (scaled != orig) scaled.recycle()
                orig.recycle()

                if (count % 2 == 0 && count < uris.size) {
                    doc.add(photoTable)
                    doc.add(AreaBreak())
                    photoTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Letztes leeres Feld auffüllen wenn ungerade
        if (count % 2 != 0) {
            val empty = Cell().add(Paragraph(""))
            empty.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            photoTable.addCell(empty)
        }
        if (count > 0) doc.add(photoTable)
    }

    // ════════════════════════════════════════════════════════════
    // OSM STATIC MAP TILE
    // ════════════════════════════════════════════════════════════

    /**
     * Lädt eine zusammengesetzte OSM-Kartenkachel (3×3 Tiles → 768×768px)
     * um den Trainingsstandort herum und zeichnet einen roten Marker drauf.
     */
    private fun fetchOsmTile(context: Context, lat: Double, lon: Double, zoom: Int): Bitmap? {
        return try {
            // Tile-Koordinaten berechnen
            val n = Math.pow(2.0, zoom.toDouble())
            val tileX = ((lon + 180.0) / 360.0 * n).toInt()
            val tileY = ((1.0 - Math.log(
                Math.tan(Math.toRadians(lat)) + 1.0 / Math.cos(Math.toRadians(lat))
            ) / Math.PI) / 2.0 * n).toInt()

            val tileSize = 256
            val grid = 3 // 3×3 tiles
            val combined = Bitmap.createBitmap(tileSize * grid, tileSize * grid, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(combined)

            for (dy in 0 until grid) {
                for (dx in 0 until grid) {
                    val tx = tileX - grid / 2 + dx
                    val ty = tileY - grid / 2 + dy
                    val url = java.net.URL("https://tile.openstreetmap.org/$zoom/$tx/$ty.png")
                    val conn = url.openConnection().also {
                        it.setRequestProperty("User-Agent", "HundeWildnis/1.0 Android")
                        it.connectTimeout = 5000
                        it.readTimeout = 5000
                    }
                    val bmp = BitmapFactory.decodeStream(conn.getInputStream())
                    if (bmp != null) {
                        canvas.drawBitmap(bmp, (dx * tileSize).toFloat(), (dy * tileSize).toFloat(), null)
                        bmp.recycle()
                    }
                }
            }

            // Roter Marker in der Mitte
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.RED
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            val cx = (tileSize * grid / 2).toFloat()
            val cy = (tileSize * grid / 2).toFloat()
            canvas.drawCircle(cx, cy, 14f, paint)
            paint.apply { color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f }
            canvas.drawCircle(cx, cy, 14f, paint)

            combined
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private fun fmtScore(d: Double, context: Context) =
        if (d > -999.0) String.format(Locale.US, "%.1f " + context.getString(R.string.nasenarbeit_pdf_points_unit), d) else context.getString(R.string.nasenarbeit_suit_not_suitable_label)

    private fun addSectionTitle(doc: Document, title: String) {
        doc.add(Paragraph(title).setBold().setFontSize(13f).setFontColor(GREEN)
            .setMarginBottom(4f))
    }

    private fun twoColTable(): Table =
        Table(UnitValue.createPercentArray(floatArrayOf(35f, 65f)))
            .useAllAvailableWidth()
            .setMarginBottom(4f)

    private fun labelCell(text: String): Cell =
        Cell().add(Paragraph(text).setFontSize(11f).setBold())
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPaddingTop(3f).setPaddingBottom(3f)

    private fun valueCell(text: String): Cell =
        Cell().add(Paragraph(text).setFontSize(11f).setFontColor(GRAY))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPaddingTop(3f).setPaddingBottom(3f)
}