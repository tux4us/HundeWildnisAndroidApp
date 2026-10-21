package com.hundewildnis.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

// ════════════════════════════════════════════════════════════════
// PDF-LAYOUT
//
// Kleiner Layout-Baustein auf Basis der Plattform-API
// android.graphics.pdf.PdfDocument. Er ersetzt iText (AGPL) und
// benötigt keine Fremdbibliothek.
//
// Umfang (entspricht dem, was die PDF-Exporte der App brauchen):
//  • Absätze mit Zeilenumbruch, Seitenumbruch zwischen Zeilen,
//    Schriftgröße, Fett, Farbe, Ausrichtung, Außen-/Innenabstand,
//    gemischte Textabschnitte (PdfText)
//  • Tabellen mit Prozent- oder Punktbreiten, Spaltenübergreifung
//    (colspan), Hintergrund, Rahmen je Seite, Kopfzeilen, die auf
//    Folgeseiten wiederholt werden; sehr hohe Zeilen werden
//    zeilenweise über Seiten geteilt
//  • Bilder (feste Breite, Prozentbreite, automatische Anpassung,
//    Einpassen in ein Feld), auch innerhalb von Tabellenzellen
//  • Seitenumbruch
//
// Seitenformat A4 (595 × 842 pt), Standardrand 36 pt, wie bisher.
// Die Einheit aller Maße ist Punkt (1/72 Zoll).
// ════════════════════════════════════════════════════════════════

private const val PDF_PAGE_WIDTH = 595
private const val PDF_PAGE_HEIGHT = 842
private const val PDF_LINE_SPACING = 1.1f
private const val PDF_BLANK_LINE_FACTOR = 1.25f
private const val PDF_DEFAULT_CELL_PADDING = 2f

enum class PdfAlign { LEFT, CENTER, RIGHT }

class PdfBorder(val color: Int, val width: Float) {
    companion object {
        /** Kein Rahmen. */
        val NONE = PdfBorder(Color.TRANSPARENT, 0f)
    }
}

/** Marker für Elemente, die einem Dokument oder einer Zelle hinzugefügt werden können. */
interface PdfElement

/** Seitenumbruch. */
class PdfPageBreak : PdfElement

// ────────────────────────────────────────────────────────────────
// Blöcke (interne Darstellung eines gesetzten Elements)
// ────────────────────────────────────────────────────────────────

internal abstract class PdfBlock {
    /** Gesamthöhe inklusive Abständen. */
    abstract val height: Float

    /** Mögliche Trennstellen (Unterkanten von Zeilen), aufsteigend, relativ zur Oberkante. Letzter Wert = height. */
    abstract val breakpoints: FloatArray

    /**
     * Zeichnet den sichtbaren Ausschnitt [from, to) des Blocks. [top] ist die Position der
     * Oberkante des gesamten Blocks auf der Seite (kann über dem sichtbaren Bereich liegen).
     */
    abstract fun draw(canvas: Canvas, x: Float, top: Float, from: Float, to: Float)
}

internal class PdfSpacerBlock(override val height: Float) : PdfBlock() {
    override val breakpoints: FloatArray = floatArrayOf(height)
    override fun draw(canvas: Canvas, x: Float, top: Float, from: Float, to: Float) {}
}

internal class PdfTextBlock(
    private val layout: StaticLayout,
    private val before: Float,
    private val after: Float,
    private val inset: Float,
    private val boxWidth: Float
) : PdfBlock() {

    override val height: Float = before + inset + layout.height + inset + after

    override val breakpoints: FloatArray = FloatArray(layout.lineCount) { i ->
        if (i == layout.lineCount - 1) {
            before + inset + layout.height + inset + after
        } else {
            before + inset + layout.getLineBottom(i)
        }
    }

    override fun draw(canvas: Canvas, x: Float, top: Float, from: Float, to: Float) {
        canvas.save()
        canvas.clipRect(x, top + from, x + boxWidth, top + to)
        canvas.translate(x + inset, top + before + inset)
        layout.draw(canvas)
        canvas.restore()
    }
}

internal class PdfImageBlock(
    private val bitmap: Bitmap,
    private val drawWidth: Float,
    private val drawHeight: Float,
    private val xOffset: Float,
    private val before: Float,
    private val after: Float
) : PdfBlock() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override val height: Float = before + drawHeight + after
    override val breakpoints: FloatArray = floatArrayOf(height)

    override fun draw(canvas: Canvas, x: Float, top: Float, from: Float, to: Float) {
        canvas.save()
        canvas.clipRect(x, top + from, x + xOffset + drawWidth + 1f, top + to)
        val target = RectF(x + xOffset, top + before, x + xOffset + drawWidth, top + before + drawHeight)
        canvas.drawBitmap(bitmap, null, target, paint)
        canvas.restore()
    }
}

// ────────────────────────────────────────────────────────────────
// Text und Absatz
// ────────────────────────────────────────────────────────────────

/** Textabschnitt innerhalb eines Absatzes (z. B. fett oder kleiner als der Rest). */
class PdfText(val text: String) {
    internal var sizeOverride: Float? = null
    internal var boldFlag: Boolean = false
    internal var colorOverride: Int? = null

    fun setFontSize(size: Float): PdfText {
        sizeOverride = size
        return this
    }

    fun setBold(): PdfText {
        boldFlag = true
        return this
    }

    fun setFontColor(color: Int): PdfText {
        colorOverride = color
        return this
    }
}

class PdfParagraph(text: String = "") : PdfElement {

    private val runs = ArrayList<PdfText>()
    private var sizePt: Float = 12f
    private var boldFlag: Boolean = false
    private var italicFlag: Boolean = false
    private var colorInt: Int = Color.BLACK
    private var alignment: PdfAlign = PdfAlign.LEFT
    private var topMargin: Float = 0f
    private var bottomMargin: Float = 0f
    private var innerPadding: Float = 0f

    init {
        if (text.isNotEmpty()) runs.add(PdfText(text))
    }

    fun add(run: PdfText): PdfParagraph {
        runs.add(run)
        return this
    }

    fun setFontSize(size: Float): PdfParagraph {
        sizePt = size
        return this
    }

    fun setBold(): PdfParagraph {
        boldFlag = true
        return this
    }

    fun setItalic(): PdfParagraph {
        italicFlag = true
        return this
    }

    fun setFontColor(color: Int): PdfParagraph {
        colorInt = color
        return this
    }

    fun setTextAlignment(align: PdfAlign): PdfParagraph {
        alignment = align
        return this
    }

    fun setMarginTop(value: Float): PdfParagraph {
        topMargin = value
        return this
    }

    fun setMarginBottom(value: Float): PdfParagraph {
        bottomMargin = value
        return this
    }

    fun setPadding(value: Float): PdfParagraph {
        innerPadding = value
        return this
    }

    internal fun toBlock(availWidth: Float): PdfBlock {
        val fullText = runs.joinToString("") { it.text }

        // Leere bzw. nur aus Leerraum bestehende Absätze dienen als Abstand
        if (fullText.isBlank()) {
            var lines = 0
            for (ch in fullText) {
                if (ch == '\n') lines++
            }
            if (lines < 1) lines = 1
            return PdfSpacerBlock(topMargin + bottomMargin + 2f * innerPadding + lines * sizePt * PDF_BLANK_LINE_FACTOR)
        }

        val textWidth = (availWidth - 2f * innerPadding).toInt().coerceAtLeast(1)

        val spanned = SpannableStringBuilder()
        for (run in runs) {
            val start = spanned.length
            spanned.append(run.text)
            val end = spanned.length
            if (end > start) {
                val runSize = run.sizeOverride
                if (runSize != null && runSize != sizePt) {
                    spanned.setSpan(RelativeSizeSpan(runSize / sizePt), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (run.boldFlag && !boldFlag) {
                    spanned.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val runColor = run.colorOverride
                if (runColor != null) {
                    spanned.setSpan(ForegroundColorSpan(runColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = sizePt
        paint.color = colorInt
        val style = when {
            boldFlag && italicFlag -> Typeface.BOLD_ITALIC
            boldFlag -> Typeface.BOLD
            italicFlag -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, style)

        val layoutAlignment = when (alignment) {
            PdfAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            PdfAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            PdfAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
        }

        val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, textWidth)
            .setAlignment(layoutAlignment)
            .setLineSpacing(0f, PDF_LINE_SPACING)
            .setIncludePad(false)
            .build()

        return PdfTextBlock(layout, topMargin, bottomMargin, innerPadding, availWidth)
    }
}

// ────────────────────────────────────────────────────────────────
// Bild
// ────────────────────────────────────────────────────────────────

class PdfImage(internal val bmp: Bitmap) : PdfElement {

    /** Erzeugt das Bild aus kodierten Daten (JPEG, PNG). */
    constructor(bytes: ByteArray) : this(decodeImage(bytes))

    private var fixedWidth: Float? = null
    private var percentWidth: Float? = null
    private var autoFit: Boolean = false
    private var fitBoxWidth: Float? = null
    private var fitBoxHeight: Float? = null
    private var alignment: PdfAlign = PdfAlign.LEFT
    private var topMargin: Float = 0f
    private var bottomMargin: Float = 0f

    fun setWidth(points: Float): PdfImage {
        fixedWidth = points
        return this
    }

    fun setWidthPercent(percent: Float): PdfImage {
        percentWidth = percent
        return this
    }

    /** Passt das Bild an die verfügbare Breite an. */
    fun setAutoScale(enabled: Boolean): PdfImage {
        autoFit = enabled
        return this
    }

    /** Skaliert das Bild unter Beibehaltung des Seitenverhältnisses in das angegebene Feld. */
    fun scaleToFit(boxWidth: Float, boxHeight: Float): PdfImage {
        fitBoxWidth = boxWidth
        fitBoxHeight = boxHeight
        return this
    }

    fun setHorizontalAlignment(align: PdfAlign): PdfImage {
        alignment = align
        return this
    }

    fun setMarginTop(value: Float): PdfImage {
        topMargin = value
        return this
    }

    fun setMarginBottom(value: Float): PdfImage {
        bottomMargin = value
        return this
    }

    internal fun toBlock(availWidth: Float, maxHeight: Float): PdfBlock {
        val bw = bmp.width.toFloat().coerceAtLeast(1f)
        val bh = bmp.height.toFloat().coerceAtLeast(1f)

        var w: Float
        var h: Float

        val boxW = fitBoxWidth
        val boxH = fitBoxHeight
        val fixed = fixedWidth
        val percent = percentWidth

        if (boxW != null && boxH != null) {
            val scale = minOf(boxW / bw, boxH / bh)
            w = bw * scale
            h = bh * scale
        } else if (fixed != null) {
            w = fixed
            h = w * bh / bw
        } else if (percent != null) {
            w = availWidth * percent / 100f
            h = w * bh / bw
        } else if (autoFit) {
            w = availWidth
            h = w * bh / bw
        } else {
            w = minOf(bw, availWidth)
            h = w * bh / bw
        }

        if (w > availWidth) {
            val scale = availWidth / w
            w *= scale
            h *= scale
        }
        val maxH = (maxHeight - topMargin - bottomMargin).coerceAtLeast(10f)
        if (h > maxH) {
            val scale = maxH / h
            w *= scale
            h *= scale
        }

        val xOffset = when (alignment) {
            PdfAlign.CENTER -> (availWidth - w) / 2f
            PdfAlign.RIGHT -> availWidth - w
            PdfAlign.LEFT -> 0f
        }
        return PdfImageBlock(bmp, w, h, xOffset, topMargin, bottomMargin)
    }
}

private fun decodeImage(bytes: ByteArray): Bitmap =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalArgumentException("Bilddaten konnten nicht gelesen werden")

// ────────────────────────────────────────────────────────────────
// Zelle und Tabelle
// ────────────────────────────────────────────────────────────────

/**
 * Tabellenzelle. [rowspan] wird nicht unterstützt (nur der Vollständigkeit halber vorhanden),
 * [colspan] fasst Spalten zusammen.
 */
class PdfCell(rowspan: Int = 1, colspan: Int = 1) {

    internal val elements = ArrayList<PdfElement>()
    internal val colspanCount: Int = if (colspan < 1) 1 else colspan
    internal var padTop: Float = PDF_DEFAULT_CELL_PADDING
    internal var padBottom: Float = PDF_DEFAULT_CELL_PADDING
    internal var padLeft: Float = PDF_DEFAULT_CELL_PADDING
    internal var padRight: Float = PDF_DEFAULT_CELL_PADDING
    internal var borderTop: PdfBorder = PdfBorder(Color.BLACK, 0.5f)
    internal var borderRight: PdfBorder = PdfBorder(Color.BLACK, 0.5f)
    internal var borderBottom: PdfBorder = PdfBorder(Color.BLACK, 0.5f)
    internal var borderLeft: PdfBorder = PdfBorder(Color.BLACK, 0.5f)
    internal var backgroundInt: Int? = null

    fun add(element: PdfElement): PdfCell {
        elements.add(element)
        return this
    }

    fun setPadding(value: Float): PdfCell {
        padTop = value
        padBottom = value
        padLeft = value
        padRight = value
        return this
    }

    fun setPaddingTop(value: Float): PdfCell {
        padTop = value
        return this
    }

    fun setPaddingBottom(value: Float): PdfCell {
        padBottom = value
        return this
    }

    /** Setzt den Rahmen aller vier Seiten; null entspricht [PdfBorder.NONE]. */
    fun setBorder(border: PdfBorder?): PdfCell {
        val b = border ?: PdfBorder.NONE
        borderTop = b
        borderRight = b
        borderBottom = b
        borderLeft = b
        return this
    }

    fun setBorderBottom(border: PdfBorder): PdfCell {
        borderBottom = border
        return this
    }

    fun setBackgroundColor(color: Int): PdfCell {
        backgroundInt = color
        return this
    }

    /** Wird nicht benötigt: Zeilen werden ohnehin nur geteilt, wenn sie höher als eine Seite sind. */
    fun setKeepTogether(keep: Boolean): PdfCell = this

    internal fun layoutBlocks(innerWidth: Float, maxHeight: Float): List<PdfBlock> {
        val blocks = ArrayList<PdfBlock>()
        for (element in elements) {
            when (element) {
                is PdfParagraph -> blocks.add(element.toBlock(innerWidth))
                is PdfImage -> blocks.add(element.toBlock(innerWidth, maxHeight))
                else -> {}
            }
        }
        return blocks
    }
}

class PdfTable private constructor(
    private val columnSizes: FloatArray,
    private val percentBased: Boolean
) : PdfElement {

    companion object {
        /** Spaltenbreiten als Verhältniswerte der verfügbaren Breite (wie 'createPercentArray'). */
        fun percent(widths: FloatArray): PdfTable = PdfTable(widths, true)

        /** Spaltenbreiten in Punkt (wie 'createPointArray'). */
        fun points(widths: FloatArray): PdfTable = PdfTable(widths, false)
    }

    private val bodyCells = ArrayList<PdfCell>()
    private val headerCells = ArrayList<PdfCell>()
    internal var topMargin: Float = 0f
    internal var bottomMargin: Float = 0f

    /** Prozenttabellen nutzen immer die volle Breite; vorhanden für gleichen Aufruf wie bisher. */
    fun useAllAvailableWidth(): PdfTable = this

    fun setMarginTop(value: Float): PdfTable {
        topMargin = value
        return this
    }

    fun setMarginBottom(value: Float): PdfTable {
        bottomMargin = value
        return this
    }

    fun addCell(cell: PdfCell): PdfTable {
        bodyCells.add(cell)
        return this
    }

    fun addCell(paragraph: PdfParagraph): PdfTable {
        bodyCells.add(PdfCell().add(paragraph))
        return this
    }

    fun addHeaderCell(cell: PdfCell): PdfTable {
        headerCells.add(cell)
        return this
    }

    fun getNumberOfRows(): Int = buildRows(bodyCells).size

    internal fun columnWidths(available: Float): FloatArray {
        val count = columnSizes.size
        var total = 0f
        for (v in columnSizes) total += v
        if (total <= 0f) return FloatArray(count) { available / count }
        return if (percentBased) {
            FloatArray(count) { i -> available * columnSizes[i] / total }
        } else {
            val factor = if (total > available) available / total else 1f
            FloatArray(count) { i -> columnSizes[i] * factor }
        }
    }

    internal fun bodyRows(): List<List<Pair<PdfCell, Int>>> = buildRows(bodyCells)

    internal fun headerRows(): List<List<Pair<PdfCell, Int>>> = buildRows(headerCells)

    /** Verteilt die Zellen zeilenweise auf die Spalten; Ergebnis: (Zelle, Startspalte) je Zeile. */
    private fun buildRows(cells: List<PdfCell>): List<List<Pair<PdfCell, Int>>> {
        val columns = columnSizes.size
        val rows = ArrayList<List<Pair<PdfCell, Int>>>()
        var current = ArrayList<Pair<PdfCell, Int>>()
        var column = 0
        for (cell in cells) {
            val span = if (cell.colspanCount > columns) columns else cell.colspanCount
            if (column + span > columns) {
                rows.add(current)
                current = ArrayList()
                column = 0
            }
            current.add(Pair(cell, column))
            column += span
            if (column >= columns) {
                rows.add(current)
                current = ArrayList()
                column = 0
            }
        }
        if (current.isNotEmpty()) rows.add(current)
        return rows
    }
}

// ────────────────────────────────────────────────────────────────
// Dokument
// ────────────────────────────────────────────────────────────────

internal class PdfCellLayout(
    val cell: PdfCell,
    val x: Float,
    val width: Float,
    val blocks: List<PdfBlock>
)

internal class PdfRowLayout(
    val cells: List<PdfCellLayout>,
    val height: Float
)

/**
 * PDF-Dokument. Elemente werden beim Hinzufügen sofort gesetzt; [close] schreibt die Datei.
 * Entspricht im Aufruf dem bisherigen 'Document(PdfDocument(PdfWriter(...)))'.
 */
class PdfDoc(private val out: OutputStream) {

    constructor(file: File) : this(FileOutputStream(file))

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var pageNumber = 0
    private var y = 0f
    private var freshY = 0f
    private var closed = false

    private var marginTop = 36f
    private var marginRight = 36f
    private var marginBottom = 36f
    private var marginLeft = 36f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val contentWidth: Float
        get() = PDF_PAGE_WIDTH - marginLeft - marginRight

    private val contentHeight: Float
        get() = PDF_PAGE_HEIGHT - marginTop - marginBottom

    private val bottomLimit: Float
        get() = PDF_PAGE_HEIGHT - marginBottom

    fun setMargins(top: Float, right: Float, bottom: Float, left: Float): PdfDoc {
        marginTop = top
        marginRight = right
        marginBottom = bottom
        marginLeft = left
        return this
    }

    fun add(element: PdfElement): PdfDoc {
        when (element) {
            is PdfParagraph -> placeBlock(element.toBlock(contentWidth), marginLeft)
            is PdfImage -> placeBlock(element.toBlock(contentWidth, contentHeight), marginLeft)
            is PdfTable -> placeTable(element)
            is PdfPageBreak -> {
                if (page != null && !atPageTop()) newPage()
            }
            else -> {}
        }
        return this
    }

    /** Schließt das Dokument, schreibt es in den Ausgabestrom und schließt diesen. */
    fun close() {
        if (closed) return
        closed = true
        try {
            if (page == null && pageNumber == 0) newPage()
            finishPage()
            doc.writeTo(out)
        } finally {
            try {
                doc.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                out.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Seiten ─────────────────────────────────────────────────

    private fun newPage() {
        finishPage()
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, pageNumber).create()
        val started = doc.startPage(info)
        page = started
        canvas = started.canvas
        y = marginTop
        freshY = marginTop
    }

    private fun finishPage() {
        val current = page
        if (current != null) doc.finishPage(current)
        page = null
        canvas = null
    }

    private fun ensurePage() {
        if (page == null) newPage()
    }

    private fun atPageTop(): Boolean = page != null && y <= marginTop + 0.5f

    // ── Absätze und Bilder ─────────────────────────────────────

    private fun placeBlock(block: PdfBlock, x: Float) {
        ensurePage()
        var offset = 0f
        var guard = 0
        while (guard < 10000) {
            guard++
            val remaining = bottomLimit - y
            if (block.height - offset <= remaining + 0.01f) {
                drawBlock(block, x, offset, block.height)
                y += block.height - offset
                return
            }
            val limit = offset + remaining
            var cut = -1f
            for (b in block.breakpoints) {
                if (b > offset + 0.5f && b <= limit + 0.01f) cut = b
            }
            if (cut < 0f) {
                if (atPageTop()) {
                    cut = limit
                } else {
                    newPage()
                    continue
                }
            }
            drawBlock(block, x, offset, cut)
            offset = cut
            newPage()
        }
    }

    private fun drawBlock(block: PdfBlock, x: Float, from: Float, to: Float) {
        val c = canvas ?: return
        block.draw(c, x, y - from, from, to)
    }

    // ── Tabellen ───────────────────────────────────────────────

    private fun placeTable(table: PdfTable) {
        ensurePage()
        val colW = table.columnWidths(contentWidth)
        val colX = FloatArray(colW.size)
        var acc = marginLeft
        for (i in colW.indices) {
            colX[i] = acc
            acc += colW[i]
        }

        val headerRows = layoutRows(table.headerRows(), colW, colX)
        val bodyRows = layoutRows(table.bodyRows(), colW, colX)
        var headerHeight = 0f
        for (r in headerRows) headerHeight += r.height

        if (y + table.topMargin >= bottomLimit) newPage() else y += table.topMargin

        var needHeader = headerRows.isNotEmpty()
        for (row in bodyRows) {
            if (needHeader) {
                val minNeeded = headerHeight + minOf(row.height, 40f)
                if (bottomLimit - y < minNeeded && !atPageTop()) newPage()
                val headerAtPageTop = atPageTop()
                drawRows(headerRows)
                // Beginnt die Tabelle oben auf der Seite, liegt der Inhalt unterhalb der Kopfzeile
                if (headerAtPageTop) freshY = y
                needHeader = false
            }
            placeRow(row, headerRows, headerHeight)
        }
        if (needHeader) drawRows(headerRows)

        y += table.bottomMargin
    }

    private fun drawRows(rows: List<PdfRowLayout>) {
        for (row in rows) {
            drawRowSlice(row, 0f, row.height)
            y += row.height
        }
    }

    private fun newPageWithHeader(headerRows: List<PdfRowLayout>) {
        newPage()
        if (headerRows.isNotEmpty()) drawRows(headerRows)
        freshY = y
    }

    private fun placeRow(row: PdfRowLayout, headerRows: List<PdfRowLayout>, headerHeight: Float) {
        val freshPageSpace = contentHeight - headerHeight
        var offset = 0f
        var guard = 0
        while (guard < 10000) {
            guard++
            val remaining = bottomLimit - y
            if (row.height - offset <= remaining + 0.01f) {
                drawRowSlice(row, offset, row.height)
                y += row.height - offset
                return
            }

            // Zeile passt nicht mehr auf die Seite, würde aber auf eine leere Folgeseite passen
            if (offset == 0f && row.height <= freshPageSpace + 0.01f && y > freshY + 0.5f) {
                newPageWithHeader(headerRows)
                continue
            }

            // Zeile ist höher als eine Seite (oder wurde bereits angebrochen): zeilenweise teilen
            val limit = offset + remaining
            var cut = chooseRowCut(row, offset, limit)
            if (cut < 0f) {
                if (y <= freshY + 0.5f) {
                    cut = limit
                } else {
                    newPageWithHeader(headerRows)
                    continue
                }
            }
            drawRowSlice(row, offset, cut)
            offset = cut
            newPageWithHeader(headerRows)
        }
    }

    private fun layoutRows(
        rows: List<List<Pair<PdfCell, Int>>>,
        colW: FloatArray,
        colX: FloatArray
    ): List<PdfRowLayout> {
        val result = ArrayList<PdfRowLayout>()
        for (row in rows) {
            val cells = ArrayList<PdfCellLayout>()
            var rowHeight = 0f
            for (entry in row) {
                val cell = entry.first
                val col = entry.second
                val maxSpan = colW.size - col
                val span = if (cell.colspanCount > maxSpan) maxSpan else cell.colspanCount
                var width = 0f
                for (i in col until col + span) width += colW[i]
                val inner = (width - cell.padLeft - cell.padRight).coerceAtLeast(1f)
                val blocks = cell.layoutBlocks(inner, contentHeight)
                var contentH = 0f
                for (b in blocks) contentH += b.height
                val total = cell.padTop + contentH + cell.padBottom
                if (total > rowHeight) rowHeight = total
                cells.add(PdfCellLayout(cell, colX[col], width, blocks))
            }
            result.add(PdfRowLayout(cells, rowHeight))
        }
        return result
    }

    /** Wählt die größte Trennstelle im Bereich (offset, limit], die keine Textzeile durchschneidet. */
    private fun chooseRowCut(row: PdfRowLayout, offset: Float, limit: Float): Float {
        var best = -1f
        for (cl in row.cells) {
            var acc = cl.cell.padTop
            for (b in cl.blocks) {
                for (bp in b.breakpoints) {
                    val candidate = acc + bp
                    if (candidate > offset + 0.5f && candidate <= limit + 0.01f && candidate > best &&
                        !cutsThroughLine(row, candidate)
                    ) {
                        best = candidate
                    }
                }
                acc += b.height
            }
        }
        return best
    }

    private fun cutsThroughLine(row: PdfRowLayout, position: Float): Boolean {
        for (cl in row.cells) {
            var previous = cl.cell.padTop
            var acc = cl.cell.padTop
            for (b in cl.blocks) {
                for (bp in b.breakpoints) {
                    val end = acc + bp
                    if (position > previous + 0.01f && position < end - 0.01f) return true
                    previous = end
                }
                acc += b.height
            }
        }
        return false
    }

    /** Zeichnet den Ausschnitt [from, to) einer Zeile, deren Oberkante (bei from = 0) auf y liegt. */
    private fun drawRowSlice(row: PdfRowLayout, from: Float, to: Float) {
        val c = canvas ?: return
        val originTop = y - from
        val sliceHeight = to - from

        for (cl in row.cells) {
            val cell = cl.cell

            val background = cell.backgroundInt
            if (background != null) {
                fillPaint.color = background
                c.drawRect(cl.x, y, cl.x + cl.width, y + sliceHeight, fillPaint)
            }

            var acc = cell.padTop
            for (b in cl.blocks) {
                val blockTop = acc
                val blockBottom = acc + b.height
                val visibleFrom = maxOf(from, blockTop)
                val visibleTo = minOf(to, blockBottom)
                if (visibleTo > visibleFrom) {
                    b.draw(c, cl.x + cell.padLeft, originTop + blockTop, visibleFrom - blockTop, visibleTo - blockTop)
                }
                acc = blockBottom
            }

            val isFirstSlice = from <= 0.01f
            val isLastSlice = to >= row.height - 0.01f
            if (isFirstSlice) drawLine(c, cell.borderTop, cl.x, y, cl.x + cl.width, y)
            if (isLastSlice) drawLine(c, cell.borderBottom, cl.x, y + sliceHeight, cl.x + cl.width, y + sliceHeight)
            drawLine(c, cell.borderLeft, cl.x, y, cl.x, y + sliceHeight)
            drawLine(c, cell.borderRight, cl.x + cl.width, y, cl.x + cl.width, y + sliceHeight)
        }
    }

    private fun drawLine(c: Canvas, border: PdfBorder, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (border.width <= 0f || Color.alpha(border.color) == 0) return
        strokePaint.color = border.color
        strokePaint.strokeWidth = border.width
        c.drawLine(x1, y1, x2, y2, strokePaint)
    }
}
