package com.hundewildnis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.UnitValue
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ════════════════════════════════════════════════════════════════
// TIMELINE — EXPORT (CSV und PDF)
//
// Beide Exporter bekommen die bereits gefilterte Ereignisliste der
// Timeline (neueste zuerst). Die Dateien entstehen im Cache-Ordner
// (cache/timeline_export) und werden über den Teilen-Dialog weitergegeben;
// dafür ist keine Speicherberechtigung nötig. Vorherige Exporte werden
// bei jedem Lauf gelöscht.
// ════════════════════════════════════════════════════════════════

internal object TimelineExportSupport {

    fun exportDir(context: Context): File {
        val dir = File(context.cacheDir, "timeline_export")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        return dir
    }

    fun fileName(profile: DogProfile, extension: String): String {
        val safeName = profile.name.replace("\\s+".toRegex(), "_").filter { it.isLetterOrDigit() || it == '_' }
            .ifEmpty { "Hund" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date())
        return "Timeline_${safeName}_$stamp.$extension"
    }

    /** Datumstext: bei ungenauem Datum wie eingegeben, sonst dd.MM.yyyy. */
    fun dateText(e: TimelineEvent): String =
        e.dateText ?: SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(Date(e.timestamp))

    fun timeText(e: TimelineEvent): String =
        if (e.hasTime) SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(e.timestamp)) else ""

    fun isoDate(e: TimelineEvent): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(e.timestamp))

    /** Detailtext ohne Symbole: bevorzugt den Klartext, sonst den bereinigten Anzeigetext. */
    fun plainDetail(e: TimelineEvent): String =
        e.plain.ifEmpty { stripSymbols(e.subtitle) }.replace("\n", " | ")

    /**
     * Entfernt Emoji und Symbolzeichen. Die Standardschrift im PDF (Helvetica) kann sie nicht
     * darstellen; sie würden als Lücken erscheinen.
     */
    fun stripSymbols(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            val drop = cp >= 0x1F000 ||
                    cp in 0x2190..0x2BFF ||
                    cp in 0xFE00..0xFE0F ||
                    cp == 0x200D
            if (!drop) sb.appendCodePoint(cp)
        }
        return sb.toString().replace(Regex("[ \\t]{2,}"), " ").trim()
    }

    fun userEntry(profile: DogProfile, e: TimelineEvent): TimelineUserEntry? =
        if (e.category == TimelineCategory.EIGENE) profile.timelineEntries.find { it.id == e.payload } else null
}

// ────────────────────────────────────────────────────────────────
// CSV
// ────────────────────────────────────────────────────────────────

object TimelineCsvExporter {

    private const val SEP = ";"

    /**
     * Semikolon-getrennt, UTF-8 mit BOM (Excel erkennt Umlaute), Felder nach RFC 4180 in
     * Anführungszeichen. Textfelder, die mit = + - @ beginnen, erhalten ein vorangestelltes
     * Hochkomma, damit Tabellenprogramme sie nicht als Formel auswerten.
     */
    fun export(context: Context, profile: DogProfile, events: List<TimelineEvent>): File {
        val lines = mutableListOf<String>()
        lines += context.getString(R.string.timeline_csv_header)

        for (e in events) {
            val entry = TimelineExportSupport.userEntry(profile, e)
            val kind = entry?.let { TimelineEntryKind.label(context, TimelineEntryKind.normalize(it.kind)) }.orEmpty()
            val details = if (entry != null) "" else TimelineExportSupport.plainDetail(e)
            val photo = if (entry != null && entry.photoUri.isNotEmpty()) {
                context.getString(R.string.timeline_csv_yes)
            } else {
                ""
            }

            lines += listOf(
                field(TimelineExportSupport.dateText(e)),
                field(TimelineExportSupport.timeText(e)),
                field(TimelineExportSupport.isoDate(e)),
                field(e.category.label(context)),
                field(kind, text = true),
                field(e.title, text = true),
                field(details, text = true),
                field(entry?.place.orEmpty(), text = true),
                field(entry?.note.orEmpty(), text = true),
                field(photo),
                coordinate(e.lat, e.lon, first = true),
                coordinate(e.lat, e.lon, first = false)
            ).joinToString(SEP)
        }

        val file = File(TimelineExportSupport.exportDir(context), TimelineExportSupport.fileName(profile, "csv"))
        file.writeBytes(("﻿" + lines.joinToString("\r\n") + "\r\n").toByteArray(Charsets.UTF_8))
        return file
    }

    /** Dezimalgrad mit Punkt, unabhängig von der Gerätesprache; leer ohne Standort. */
    private fun coordinate(lat: Double?, lon: Double?, first: Boolean): String {
        if (!GeoCoordinates.isValid(lat, lon)) return ""
        return String.format(java.util.Locale.ROOT, "%.6f", if (first) lat else lon)
    }

    private fun field(value: String, text: Boolean = false): String {
        var v = value.replace("\r\n", "\n").replace('\r', '\n')
        if (text && v.isNotEmpty() && v[0] in "=+-@") v = "'$v"
        val needsQuotes = v.any { it == ';' || it == '"' || it == '\n' }
        return if (needsQuotes) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}

// ────────────────────────────────────────────────────────────────
// PDF
// ────────────────────────────────────────────────────────────────

object TimelinePdfExporter {

    private val GREEN = DeviceRgb(45, 80, 22)
    private val GRAY = DeviceRgb(100, 100, 100)
    private val MONTH_BG = DeviceRgb(236, 240, 232)
    private val LINE = DeviceRgb(210, 210, 210)

    private const val PHOTO_MAX_PX = 480
    private const val PHOTO_BOX_PT = 150f

    /**
     * @param filterText Beschreibung der aktiven Filter, wird unter dem Titel ausgegeben.
     */
    fun export(
        context: Context,
        profile: DogProfile,
        events: List<TimelineEvent>,
        filterText: String
    ): File {
        val file = File(TimelineExportSupport.exportDir(context), TimelineExportSupport.fileName(profile, "pdf"))
        val doc = Document(PdfDocument(PdfWriter(file)))
        try {
            doc.setMargins(36f, 36f, 36f, 36f)
            writeHeader(context, doc, profile, events.size, filterText)
            writeTable(context, doc, profile, events)
            writeFooter(context, doc)
        } finally {
            doc.close()
        }
        return file
    }

    private fun clean(s: String) = TimelineExportSupport.stripSymbols(s)

    private fun writeHeader(context: Context, doc: Document, profile: DogProfile, count: Int, filterText: String) {
        doc.add(
            Paragraph(clean(context.getString(R.string.timeline_pdf_title_format, profile.name)))
                .setBold().setFontSize(18f).setFontColor(GREEN)
        )
        val subtitle = listOf(profile.breed).filter { it.isNotBlank() }.joinToString(" · ")
        if (subtitle.isNotEmpty()) doc.add(Paragraph(clean(subtitle)).setFontSize(11f).setFontColor(GRAY))

        TogetherMath.compute(profile)?.let { info ->
            val display = TogetherFormatter.format(context, info)
            doc.add(Paragraph(clean("${display.sinceLine} – ${display.headline}")).setFontSize(10f).setFontColor(GRAY))
        }

        doc.add(Paragraph(clean(filterText)).setFontSize(10f).setFontColor(GRAY))
        doc.add(
            Paragraph(context.getString(R.string.timeline_pdf_entries_format, count))
                .setFontSize(10f).setFontColor(GRAY).setMarginBottom(10f)
        )
    }

    private fun writeTable(context: Context, doc: Document, profile: DogProfile, events: List<TimelineEvent>) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(1.5f, 1.6f, 6f))).useAllAvailableWidth()
        table.addHeaderCell(headerCell(context.getString(R.string.timeline_pdf_col_date)))
        table.addHeaderCell(headerCell(context.getString(R.string.timeline_pdf_col_category)))
        table.addHeaderCell(headerCell(context.getString(R.string.timeline_pdf_col_entry)))

        val locale = context.resources.configuration.locales.get(0)
        val monthFormat = SimpleDateFormat("LLLL yyyy", locale)
        val cal = Calendar.getInstance()
        var lastKey = -1

        for (e in events) {
            cal.timeInMillis = e.timestamp
            val key = cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)
            if (key != lastKey) {
                val label = monthFormat.format(Date(e.timestamp))
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                table.addCell(
                    Cell(1, 3).add(Paragraph(label).setBold().setFontSize(11f).setFontColor(GREEN))
                        .setBackgroundColor(MONTH_BG).setBorder(Border.NO_BORDER).setPaddingTop(6f)
                )
                lastKey = key
            }

            val dateLine = TimelineExportSupport.timeText(e).let { time ->
                if (time.isEmpty()) TimelineExportSupport.dateText(e) else "${TimelineExportSupport.dateText(e)}\n$time"
            }
            table.addCell(bodyCell(Paragraph(dateLine).setFontSize(9f)))
            table.addCell(bodyCell(Paragraph(e.category.label(context)).setFontSize(9f).setFontColor(GRAY)))
            table.addCell(entryCell(context, profile, e))
        }
        doc.add(table)
    }

    private fun entryCell(context: Context, profile: DogProfile, e: TimelineEvent): Cell {
        val cell = bodyCell(Paragraph(clean(e.title)).setBold().setFontSize(10f))
        val entry = TimelineExportSupport.userEntry(profile, e)

        if (entry != null) {
            val meta = listOf(
                TimelineEntryKind.label(context, TimelineEntryKind.normalize(entry.kind)),
                entry.place
            ).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) cell.add(Paragraph(clean(meta)).setFontSize(9f).setFontColor(GRAY))
            if (entry.note.isNotBlank()) cell.add(Paragraph(clean(entry.note)).setFontSize(9.5f))
            photoImage(context, entry.photoUri)?.let { cell.add(it) }
        } else {
            val detail = TimelineExportSupport.plainDetail(e)
            if (detail.isNotBlank()) cell.add(Paragraph(clean(detail)).setFontSize(9f).setFontColor(GRAY))
        }
        if (GeoCoordinates.isValid(e.lat, e.lon)) {
            cell.add(
                Paragraph(context.getString(R.string.timeline_pdf_location_format, GeoCoordinates.format(e.lat!!, e.lon!!)))
                    .setFontSize(8.5f).setFontColor(GRAY)
            )
        }
        return cell
    }

    private fun bodyCell(p: Paragraph): Cell =
        Cell().add(p).setBorder(Border.NO_BORDER).setBorderBottom(SolidBorder(LINE, 0.5f)).setPaddingTop(4f).setPaddingBottom(4f)

    private fun headerCell(text: String): Cell =
        Cell().add(Paragraph(text).setBold().setFontSize(9f).setFontColor(GREEN))
            .setBorder(Border.NO_BORDER).setBorderBottom(SolidBorder(GREEN, 1f))

    private fun writeFooter(context: Context, doc: Document) {
        val created = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.ROOT).format(Date())
        doc.add(
            Paragraph(context.getString(R.string.timeline_pdf_created_format, created))
                .setFontSize(8f).setFontColor(GRAY).setMarginTop(12f)
        )
    }

    /**
     * Verkleinertes Foto als JPEG. Die EXIF-Ausrichtung wird angewendet, weil die Fotos
     * unverändert kopiert werden (BitmapFactory ignoriert EXIF). Bei einem Fehler oder
     * fehlender Datei wird kein Bild eingefügt.
     */
    private fun photoImage(context: Context, uriString: String): Image? {
        if (uriString.isBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // decodeStream liefert mit inJustDecodeBounds immer null; deshalb nur den Stream prüfen
            val boundsStream = resolver.openInputStream(uri) ?: return null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > PHOTO_MAX_PX * 2 || bounds.outHeight / sample > PHOTO_MAX_PX * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            val rotation = resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
            if (rotation != 0) {
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true
                )
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }

            val scale = minOf(1f, PHOTO_MAX_PX.toFloat() / maxOf(bitmap.width, bitmap.height))
            if (scale < 1f) {
                val scaled = Bitmap.createScaledBitmap(
                    bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }

            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
            bitmap.recycle()
            Image(ImageDataFactory.create(bytes)).scaleToFit(PHOTO_BOX_PT, PHOTO_BOX_PT).setMarginTop(4f)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun exifRotation(input: java.io.InputStream): Int =
        when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
}
// ────────────────────────────────────────────────────────────────
// GPX
// ────────────────────────────────────────────────────────────────

object TimelineGpxExporter {

    /**
     * GPX 1.1 mit einem Wegpunkt je Timeline-Eintrag, der einen Standort hat (älteste zuerst).
     * Einträge ohne Standort werden übersprungen. Zeitangabe in UTC. Die Datei lässt sich in
     * Navigations- und Karten-Apps (z. B. OsmAnd, Organic Maps, Komoot, Garmin) importieren.
     */
    fun export(context: Context, profile: DogProfile, events: List<TimelineEvent>): File {
        val located = events
            .filter { GeoCoordinates.isValid(it.lat, it.lon) }
            .sortedBy { it.timestamp }

        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        val xml = StringBuilder()
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        xml.append("<gpx version=\"1.1\" creator=\"HundeWildnis App\" ")
        xml.append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        xml.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        xml.append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        xml.append("  <metadata>\n")
        xml.append("    <name>").append(esc(TimelineExportSupport.stripSymbols(
            context.getString(R.string.timeline_pdf_title_format, profile.name)))).append("</name>\n")
        xml.append("    <time>").append(utc.format(Date())).append("</time>\n")
        xml.append("  </metadata>\n")

        for (e in located) {
            val entry = TimelineExportSupport.userEntry(profile, e)
            val name = "${TimelineExportSupport.dateText(e)} – ${TimelineExportSupport.stripSymbols(e.title)}"

            val desc = buildList<String> {
                if (entry != null) {
                    add(TimelineEntryKind.label(context, TimelineEntryKind.normalize(entry.kind)))
                    if (entry.place.isNotBlank()) add(entry.place)
                    if (entry.note.isNotBlank()) add(entry.note)
                } else {
                    TimelineExportSupport.plainDetail(e).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }.joinToString(" | ") { TimelineExportSupport.stripSymbols(it).replace("\n", " ") }

            xml.append("  <wpt lat=\"").append(String.format(Locale.ROOT, "%.6f", e.lat))
            xml.append("\" lon=\"").append(String.format(Locale.ROOT, "%.6f", e.lon)).append("\">\n")
            xml.append("    <time>").append(utc.format(Date(e.timestamp))).append("</time>\n")
            xml.append("    <name>").append(esc(name)).append("</name>\n")
            if (desc.isNotEmpty()) xml.append("    <desc>").append(esc(desc)).append("</desc>\n")
            xml.append("    <type>").append(esc(e.category.label(context))).append("</type>\n")
            xml.append("  </wpt>\n")
        }
        xml.append("</gpx>\n")

        val file = File(TimelineExportSupport.exportDir(context), TimelineExportSupport.fileName(profile, "gpx"))
        file.writeText(xml.toString(), Charsets.UTF_8)
        return file
    }

    /** Escapet XML-Sonderzeichen und entfernt Steuerzeichen, die in XML 1.0 nicht erlaubt sind. */
    private fun esc(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
