package com.hundewildnis.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object NasenarbeitPDFGenerator {

    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    private val dfDate = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)

    // ════════════════════════════════════════════════════════════
    // SESSION PDF
    // ════════════════════════════════════════════════════════════

    fun generateSessionPDF(
        context: Context,
        session: NasenarbeitSession,
        profile: DogProfile
    ): File {
        val doc = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = doc.startPage(info)
        var canvas = page.canvas
        var y = 50f
        val lm = 50f

        val paint = buildPaints()

        fun nl(extra: Float = 0f) { y += 18f + extra }
        fun ensureSpace(needed: Float) {
            if (y + needed > 800f) {
                doc.finishPage(page)
                page = doc.startPage(info); canvas = page.canvas; y = 50f
            }
        }

        // Titel
        canvas.drawText("🐾 NASENARBEIT – TRAININGSAUSWERTUNG", lm, y, paint.title); nl(8f)
        canvas.drawLine(lm, y, 545f, y, paint.divider); nl(4f)

        // Hund
        canvas.drawText("Hund: ${profile.name}", lm, y, paint.header); nl()
        canvas.drawText("Rasse: ${profile.breed}", lm, y, paint.value); nl()

        nl(4f); canvas.drawLine(lm, y, 545f, y, paint.divider); nl(6f)

        // Session Info
        canvas.drawText("Session", lm, y, paint.header); nl()
        canvas.drawText("Datum:", lm, y, paint.label)
        canvas.drawText(df.format(Date(session.datum)), 200f, y, paint.value); nl()
        canvas.drawText("Disziplin:", lm, y, paint.label)
        canvas.drawText("${session.durchgefuehrteDisziplin.icon} ${session.durchgefuehrteDisziplin.displayName}", 200f, y, paint.value); nl()
        canvas.drawText("Empfohlen:", lm, y, paint.label)
        canvas.drawText("${session.empfohleneDisziplin.icon} ${session.empfohleneDisziplin.displayName}", 200f, y, paint.value); nl()
        canvas.drawText("Bewertung:", lm, y, paint.label)
        canvas.drawText("⭐".repeat(session.erfolgsbewertung), 200f, y, paint.value); nl()

        nl(4f); canvas.drawLine(lm, y, 545f, y, paint.divider); nl(6f)

        // Ergebnisse
        canvas.drawText("Ergebnisse", lm, y, paint.header); nl()
        session.durchgefuehrteSchritte?.let {
            canvas.drawText("Streckenlänge:", lm, y, paint.label)
            canvas.drawText("$it Schritte", 200f, y, paint.value); nl()
        }
        session.durchgefuehrteHa?.let {
            canvas.drawText("Fläche:", lm, y, paint.label)
            canvas.drawText("${String.format("%.1f", it)} ha", 200f, y, paint.value); nl()
        }
        session.suchdauerMinuten?.let {
            canvas.drawText("Suchdauer:", lm, y, paint.label)
            canvas.drawText("$it Minuten", 200f, y, paint.value); nl()
        }
        session.gefundeneGegenstaende?.let { g ->
            session.gegenstaendeGesamt?.let { ges ->
                canvas.drawText("Gegenstände:", lm, y, paint.label)
                canvas.drawText("$g von $ges gefunden", 200f, y, paint.value); nl()
            }
        }
        session.personenfundErfolgreich?.let {
            canvas.drawText("Personenfund:", lm, y, paint.label)
            canvas.drawText(if (it) "Erfolgreich" else "Nicht erfolgreich", 200f, y, paint.value); nl()
        }

        nl(4f); canvas.drawLine(lm, y, 545f, y, paint.divider); nl(6f)

        // Scoring
        canvas.drawText("Scoring-Ergebnis", lm, y, paint.header); nl()
        canvas.drawText("🐾 Verlorensuche:", lm, y, paint.label)
        canvas.drawText(fmtScore(session.scoreVerlorensuche), 200f, y, paint.value); nl()
        canvas.drawText("🧑 Personensuche:", lm, y, paint.label)
        canvas.drawText(fmtScore(session.scorePersonensuche), 200f, y, paint.value); nl()
        canvas.drawText("🔍 Freisuche:", lm, y, paint.label)
        canvas.drawText(fmtScore(session.scoreFreisuche), 200f, y, paint.value); nl()

        nl(4f); canvas.drawLine(lm, y, 545f, y, paint.divider); nl(6f)

        // Wetterbedingungen
        ensureSpace(80f)
        canvas.drawText("Wetterbedingungen", lm, y, paint.header); nl()
        val p = session.parameter
        canvas.drawText("Temperatur:", lm, y, paint.label)
        canvas.drawText("${p.temperaturCelsius}°C", 200f, y, paint.value); nl()
        canvas.drawText("Beaufort:", lm, y, paint.label)
        canvas.drawText("Bf ${p.beaufort}", 200f, y, paint.value); nl()
        canvas.drawText("Luftfeuchtigkeit:", lm, y, paint.label)
        canvas.drawText("${p.luftfeuchtigkeit}%", 200f, y, paint.value); nl()
        canvas.drawText("Windrichtung:", lm, y, paint.label)
        canvas.drawText(listOf("Rückenwind","Seitenwind","Gegenwind")[p.windrichtung], 200f, y, paint.value); nl()

        nl(4f); canvas.drawLine(lm, y, 545f, y, paint.divider); nl(6f)

        // Level
        ensureSpace(60f)
        canvas.drawText("Trainingslevel", lm, y, paint.header); nl()
        canvas.drawText("🐾 Verlorensuche:", lm, y, paint.label)
        canvas.drawText(session.levelVerlorensuche, 200f, y, paint.value); nl()
        canvas.drawText("🧑 Personensuche:", lm, y, paint.label)
        canvas.drawText(session.levelPersonensuche, 200f, y, paint.value); nl()
        canvas.drawText("🔍 Freisuche:", lm, y, paint.label)
        canvas.drawText(session.levelFreisuche, 200f, y, paint.value); nl()

        // Notizen
        if (session.notizen.isNotEmpty()) {
            ensureSpace(50f)
            nl(4f); canvas.drawLine(lm, y, 545f, y, paint.divider); nl(6f)
            canvas.drawText("Notizen", lm, y, paint.header); nl()
            canvas.drawText(session.notizen.take(300), lm, y, paint.value)
        }

        // Footer
        y = 810f
        canvas.drawText(
            "HundeWildnis App · Nasenarbeit · ${dfDate.format(Date())}",
            lm, y, paint.footer
        )

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN).format(Date(session.datum))
        val safeName = profile.name.replace("\\s+".toRegex(), "_").filter { it.isLetterOrDigit() || it == '_' }
        val filename = "nasenarbeit_session_${timestamp}_${safeName}.pdf"

        doc.finishPage(page)
        return saveDoc(context, doc, filename)
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
        val doc = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = doc.startPage(info)
        var canvas = page.canvas
        var y = 50f
        val lm = 50f
        var pageNum = 1

        val paint = buildPaints()

        fun nl(extra: Float = 0f) { y += 18f + extra }
        fun newPage() {
            doc.finishPage(page); pageNum++
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            )
            canvas = page.canvas; y = 50f
        }
        fun ensureSpace(needed: Float) { if (y + needed > 800f) newPage() }

        // Titel
        canvas.drawText("🐾 NASENARBEIT – GESAMTAUSWERTUNG", lm, y, paint.title); nl(8f)
        canvas.drawLine(lm, y, 545f, y, paint.divider); nl(6f)

        // Hund
        canvas.drawText("${profile.name}  •  ${profile.breed}", lm, y, paint.header); nl(8f)

        // Statistik
        canvas.drawLine(lm, y, 545f, y, paint.divider); nl(6f)
        canvas.drawText("Übersicht", lm, y, paint.header); nl()
        canvas.drawText("🐾 Verlorensuche:", lm, y, paint.label)
        canvas.drawText("${fortschritt.sessionsVerlorensuche} Sessions – ${fortschritt.levelVerlorensuche.displayName}", 220f, y, paint.value); nl()
        canvas.drawText("🧑 Personensuche:", lm, y, paint.label)
        canvas.drawText("${fortschritt.sessionsPersonensuche} Sessions – ${fortschritt.levelPersonensuche.displayName}", 220f, y, paint.value); nl()
        canvas.drawText("🔍 Freisuche:", lm, y, paint.label)
        canvas.drawText("${fortschritt.sessionsFreisuche} Sessions – ${fortschritt.levelFreisuche.displayName}", 220f, y, paint.value); nl()
        canvas.drawText("Gesamt:", lm, y, paint.label)
        canvas.drawText("${fortschritt.gesamtSessions} Sessions", 220f, y, paint.value); nl()
        if (fortschritt.maxSchritte > 0) {
            canvas.drawText("Max. Strecke:", lm, y, paint.label)
            canvas.drawText("${fortschritt.maxSchritte} Schritte", 220f, y, paint.value); nl()
        }
        if (fortschritt.maxHa > 0.0) {
            canvas.drawText("Max. Fläche:", lm, y, paint.label)
            canvas.drawText("${String.format("%.1f", fortschritt.maxHa)} ha", 220f, y, paint.value); nl()
        }
        if (fortschritt.letzteSession > 0) {
            canvas.drawText("Letzte Session:", lm, y, paint.label)
            canvas.drawText(dfDate.format(Date(fortschritt.letzteSession)), 220f, y, paint.value); nl()
        }

        // Sessionliste
        nl(6f); canvas.drawLine(lm, y, 545f, y, paint.divider); nl(6f)
        canvas.drawText("Alle Sessions (${sessions.size})", lm, y, paint.header); nl(4f)

        sessions.forEach { s ->
            ensureSpace(22f)
            val line = buildString {
                append(df.format(Date(s.datum)))
                append("  ${s.durchgefuehrteDisziplin.icon} ${s.durchgefuehrteDisziplin.displayName}")
                s.durchgefuehrteSchritte?.let { append("  ${it} Schr.") }
                s.durchgefuehrteHa?.let { append("  ${String.format("%.1f",it)} ha") }
                append("  ⭐".repeat(s.erfolgsbewertung))
            }
            canvas.drawText(line, lm, y, paint.small); nl()
        }

        // Footer
        y = 810f
        canvas.drawText(
            "HundeWildnis App · Nasenarbeit Gesamtauswertung · ${dfDate.format(Date())}",
            lm, y, paint.footer
        )

        val safeName = profile.name.replace("\\s+".toRegex(), "_").filter { it.isLetterOrDigit() || it == '_' }
        val filename = "nasenarbeit_gesamt_${safeName}.pdf"

        doc.finishPage(page)
        return saveDoc(context, doc, filename)
    }

    // ── HELPERS ────────────────────────────────────────────────

    private fun fmtScore(d: Double) = if (d > -999.0) String.format("%.1f Punkte", d) else "Nicht geeignet"

    private fun saveDoc(context: Context, doc: PdfDocument, filename: String): File {
        // Speichern in Documents/HundeWildnis als zentrales Zielverzeichnis
        val docDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        val dir = File(docDir, "HundeWildnis").also { it.mkdirs() }
        val file = File(dir, filename)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private data class Paints(
        val title: Paint, val header: Paint, val label: Paint,
        val value: Paint, val divider: Paint, val footer: Paint, val small: Paint
    )

    private fun buildPaints() = Paints(
        title = Paint().apply { color = Color.parseColor("#2d5016"); textSize = 18f; isFakeBoldText = true },
        header = Paint().apply { color = Color.parseColor("#2d5016"); textSize = 14f; isFakeBoldText = true },
        label = Paint().apply { color = Color.BLACK; textSize = 11f; isFakeBoldText = true },
        value = Paint().apply { color = Color.DKGRAY; textSize = 11f },
        divider = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f },
        footer = Paint().apply { color = Color.GRAY; textSize = 9f },
        small = Paint().apply { color = Color.DKGRAY; textSize = 10f }
    )
}
