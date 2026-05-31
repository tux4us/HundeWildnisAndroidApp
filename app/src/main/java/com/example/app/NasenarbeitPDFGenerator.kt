package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
        profile: DogProfile
    ): File {
        val dir  = File(context.filesDir, "nasenarbeit_pdfs").also { it.mkdirs() }
        val file = File(dir, "nasenarbeit_session_${session.id.take(8)}.pdf")

        val writer = PdfWriter(file)
        val pdf    = PdfDocument(writer)
        val doc    = Document(pdf)

        // ── Titel ─────────────────────────────────────────────
        doc.add(Paragraph("🐾 NASENARBEIT – TRAININGSAUSWERTUNG")
            .setBold().setFontSize(18f).setFontColor(GREEN))
        doc.add(Paragraph("${profile.name}  ·  ${profile.breed}  ·  ${profile.gender}")
            .setFontSize(12f).setFontColor(GRAY))
        doc.add(Paragraph(" "))

        // ── Session Info ──────────────────────────────────────
        addSectionTitle(doc, "Session")
        val tbl = twoColTable()
        tbl.addCell(labelCell("Datum"))
        tbl.addCell(valueCell(df.format(Date(session.datum))))
        tbl.addCell(labelCell("Disziplin"))
        tbl.addCell(valueCell("${session.durchgefuehrteDisziplin.icon} ${session.durchgefuehrteDisziplin.displayName}"))
        tbl.addCell(labelCell("Empfohlen"))
        tbl.addCell(valueCell("${session.empfohleneDisziplin.icon} ${session.empfohleneDisziplin.displayName}"))
        tbl.addCell(labelCell("Bewertung"))
        tbl.addCell(valueCell("⭐".repeat(session.erfolgsbewertung)))
        doc.add(tbl)
        doc.add(Paragraph(" "))

        // ── Ergebnisse ────────────────────────────────────────
        addSectionTitle(doc, "Trainingsergebnisse")
        val res = twoColTable()
        session.durchgefuehrteSchritte?.let { res.addCell(labelCell("Strecke")); res.addCell(valueCell("$it Schritte")) }
        session.durchgefuehrteHa?.let { res.addCell(labelCell("Fläche")); res.addCell(valueCell("${String.format("%.1f", it)} ha")) }
        session.suchdauerMinuten?.let { res.addCell(labelCell("Suchdauer")); res.addCell(valueCell("$it Min.")) }
        session.gefundeneGegenstaende?.let { g ->
            session.gegenstaendeGesamt?.let { ges ->
                res.addCell(labelCell("Gegenstände")); res.addCell(valueCell("$g von $ges gefunden"))
            }
        }
        session.personenfundErfolgreich?.let {
            res.addCell(labelCell("Personenfund"))
            res.addCell(valueCell(if (it) "✅ Erfolgreich" else "❌ Nicht gefunden"))
        }
        doc.add(res)
        doc.add(Paragraph(" "))

        // ── Scoring ───────────────────────────────────────────
        addSectionTitle(doc, "Scoring")
        val sc = twoColTable()
        sc.addCell(labelCell("🐾 Verlorensuche"))
        sc.addCell(valueCell(fmtScore(session.scoreVerlorensuche)))
        sc.addCell(labelCell("🧑 Personensuche"))
        sc.addCell(valueCell(fmtScore(session.scorePersonensuche)))
        sc.addCell(labelCell("🔍 Freisuche"))
        sc.addCell(valueCell(fmtScore(session.scoreFreisuche)))
        doc.add(sc)
        doc.add(Paragraph(" "))

        // ── Wetter ───────────────────────────────────────────
        addSectionTitle(doc, "Wetterbedingungen")
        val p = session.parameter
        val wx = twoColTable()
        wx.addCell(labelCell("Temperatur")); wx.addCell(valueCell("${p.temperaturCelsius}°C"))
        wx.addCell(labelCell("Beaufort"));   wx.addCell(valueCell("Bf ${p.beaufort}"))
        wx.addCell(labelCell("Luftfeuchte")); wx.addCell(valueCell("${p.luftfeuchtigkeit}%"))
        wx.addCell(labelCell("Windrichtung")); wx.addCell(valueCell(listOf("Rückenwind","Seitenwind","Gegenwind")[p.windrichtung]))
        doc.add(wx)
        doc.add(Paragraph(" "))

        // ── Trainingslevel ───────────────────────────────────
        addSectionTitle(doc, "Trainingslevel")
        val lv = twoColTable()
        lv.addCell(labelCell("🐾 Verlorensuche")); lv.addCell(valueCell(session.levelVerlorensuche))
        lv.addCell(labelCell("🧑 Personensuche")); lv.addCell(valueCell(session.levelPersonensuche))
        lv.addCell(labelCell("🔍 Freisuche"));     lv.addCell(valueCell(session.levelFreisuche))
        doc.add(lv)
        doc.add(Paragraph(" "))

        // ── Notizen ───────────────────────────────────────────
        if (session.notizenTitel.isNotEmpty() || session.notizen.isNotEmpty()) {
            addSectionTitle(doc, "Notizen")
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
            addSectionTitle(doc, "Trainingsstandort")
            val gps = twoColTable()
            gps.addCell(labelCell("Koordinaten"))
            gps.addCell(valueCell("${String.format("%.5f", session.gpsLat)}, ${String.format("%.5f", session.gpsLon)}"))
            gps.addCell(labelCell("Karte"))
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
                doc.add(Paragraph("Kartenkachel: $osmUrl")
                    .setFontSize(10f).setFontColor(BLUE))
            }
            doc.add(Paragraph(" "))
        }

        // ── Fotos ─────────────────────────────────────────────
        if (session.fotoUris.isNotEmpty()) {
            doc.add(AreaBreak())
            addSectionTitle(doc, "Fotos (${session.fotoUris.size})")
            doc.add(Paragraph(" "))
            addPhotosGrid(context, doc, session.fotoUris)
        }

        // ── Footer ───────────────────────────────────────────
        doc.add(Paragraph(" "))
        doc.add(Paragraph("HundeWildnis App · Nasenarbeit · ${dfDate.format(Date())}")
            .setFontSize(8f).setFontColor(GRAY).setTextAlignment(TextAlignment.CENTER))

        doc.close()
        return file
    }

    // ════════════════════════════════════════════════════════════
    // GESAMT PDF
    // ════════════════════════════════════════════════════════════

    fun generateGesamtPDF(
        context: Context,
        sessions: List<NasenarbeitSession>,
        fortschritt: NasenarbeitFortschritt,
        profile: DogProfile
    ): File {
        val dir  = File(context.filesDir, "nasenarbeit_pdfs").also { it.mkdirs() }
        val file = File(dir, "nasenarbeit_gesamt_${profile.id.take(8)}.pdf")

        val writer = PdfWriter(file)
        val pdf    = PdfDocument(writer)
        val doc    = Document(pdf)

        // ── Titel ─────────────────────────────────────────────
        doc.add(Paragraph("🐾 NASENARBEIT – GESAMTAUSWERTUNG")
            .setBold().setFontSize(18f).setFontColor(GREEN))
        doc.add(Paragraph("${profile.name}  ·  ${profile.breed}")
            .setFontSize(12f).setFontColor(GRAY))
        doc.add(Paragraph(" "))

        // ── Übersicht ─────────────────────────────────────────
        addSectionTitle(doc, "Übersicht")
        val ov = twoColTable()
        ov.addCell(labelCell("🐾 Verlorensuche"))
        ov.addCell(valueCell("${fortschritt.sessionsVerlorensuche} Sessions – ${fortschritt.levelVerlorensuche.displayName}"))
        ov.addCell(labelCell("🧑 Personensuche"))
        ov.addCell(valueCell("${fortschritt.sessionsPersonensuche} Sessions – ${fortschritt.levelPersonensuche.displayName}"))
        ov.addCell(labelCell("🔍 Freisuche"))
        ov.addCell(valueCell("${fortschritt.sessionsFreisuche} Sessions – ${fortschritt.levelFreisuche.displayName}"))
        ov.addCell(labelCell("Gesamt"))
        ov.addCell(valueCell("${fortschritt.gesamtSessions} Sessions"))
        if (fortschritt.maxSchritte > 0) {
            ov.addCell(labelCell("Max. Strecke")); ov.addCell(valueCell("${fortschritt.maxSchritte} Schritte"))
        }
        if (fortschritt.maxHa > 0.0) {
            ov.addCell(labelCell("Max. Fläche")); ov.addCell(valueCell("${String.format("%.1f", fortschritt.maxHa)} ha"))
        }
        if (fortschritt.letzteSession > 0) {
            ov.addCell(labelCell("Letzte Session")); ov.addCell(valueCell(dfDate.format(Date(fortschritt.letzteSession))))
        }
        doc.add(ov)
        doc.add(Paragraph(" "))

        // ── Sessionliste ──────────────────────────────────────
        addSectionTitle(doc, "Alle Sessions (${sessions.size})")
        doc.add(Paragraph(" "))

        sessions.forEach { s ->
            val line = Paragraph().apply {
                add(Text(df.format(Date(s.datum))).setFontSize(10f))
                add(Text("  ${s.durchgefuehrteDisziplin.icon} ${s.durchgefuehrteDisziplin.displayName}").setFontSize(10f).setBold())
                s.durchgefuehrteSchritte?.let { add(Text("  $it Schr.").setFontSize(10f)) }
                s.durchgefuehrteHa?.let { add(Text("  ${String.format("%.1f",it)} ha").setFontSize(10f)) }
                add(Text("  ${"⭐".repeat(s.erfolgsbewertung)}").setFontSize(10f))
            }
            doc.add(line)
        }

        // ── Footer ───────────────────────────────────────────
        doc.add(Paragraph(" "))
        doc.add(Paragraph("HundeWildnis App · Nasenarbeit Gesamtauswertung · ${dfDate.format(Date())}")
            .setFontSize(8f).setFontColor(GRAY).setTextAlignment(TextAlignment.CENTER))

        doc.close()
        return file
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

    private fun fmtScore(d: Double) =
        if (d > -999.0) String.format("%.1f Punkte", d) else "Nicht geeignet"

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