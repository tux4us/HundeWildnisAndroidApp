package com.hundewildnis.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Zeichnet die aktuelle Mondphase geometrisch korrekt per Canvas.
 * Ersatz für Unicode-Emojis, die auf Android oft als schmale Sichel aussehen.
 *
 * Verwendung in XML:
 *   <com.hundewildnis.app.MoonPhaseView
 *       android:id="@+id/moonPhaseView"
 *       android:layout_width="80dp"
 *       android:layout_height="80dp" />
 *
 * Im Kotlin-Code:
 *   binding.moonPhaseView.moonAge = moonInfo.age
 */
class MoonPhaseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Mondalter in Tagen (0–29,53). Triggert Neuzeichnung bei Änderung. */
    var moonAge: Double = 14.0
        set(value) { field = value; invalidate() }

    // ── Farben ────────────────────────────────────────────────────────────
    // Helle Seite (beleuchteter Mond)
    private val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0E060")
    }
    // Dunkle Seite (Mondschatten)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1E30")
    }
    // Rand
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color   = Color.parseColor("#5a5a8a")
        style   = Paint.Style.STROKE
        strokeWidth = 3f
    }
    // Subtile Textur / Oberfläche (dunkle Flecken auf der Tagseite)
    private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D8C840")
        alpha = 80
    }

    private val SYNODIC = 29.53059   // mittlere synodische Periode

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width  / 2f
        val cy = height / 2f
        val r  = minOf(cx, cy) - borderPaint.strokeWidth

        // Phase 0..1  (0 = Neumond, 0.25 = Erstes Viertel, 0.5 = Vollmond)
        val phase = (moonAge % SYNODIC) / SYNODIC
        // Phasenwinkel φ in Bogenmaß (0..2π)
        val phi = phase * 2.0 * Math.PI

        // ── 1. Dunkel-Scheibe als Basis ────────────────────────────────
        canvas.drawCircle(cx, cy, r, shadowPaint)

        // ── 2. Alles auf den Mondkreis clippen ─────────────────────────
        canvas.save()
        val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        canvas.clipPath(clip)

        // x-Radius des Terminators (Ellipse): |cos(φ)| · r
        // φ = 0    → r   (Neumond: Terminator = voller Halbkreis, keine Lichtseite sichtbar)
        // φ = π/2  → 0   (Erstes Viertel: Terminator = gerade Linie, genau halb)
        // φ = π    → r   (Vollmond: Terminator auf der anderen Seite)
        val terminatorR = (Math.abs(Math.cos(phi)) * r).toFloat()

        if (phase < 0.5) {
            // ══ ZUNEHMEND: rechte Seite leuchtet ══════════════════════

            // Rechten Halbkreis beleuchtet zeichnen
            canvas.drawPath(rightHalf(cx, cy, r), litPaint)

            if (phi < Math.PI / 2.0) {
                // SICHEL zunehmend (0 < φ < π/2):
                // Dunkle Terminator-Ellipse überdeckt den größten Teil des rechten Halbkreises.
                // Ergebnis: schmale helle Sichel am rechten Rand.
                canvas.drawOval(
                    RectF(cx - terminatorR, cy - r, cx + terminatorR, cy + r),
                    shadowPaint
                )
            } else {
                // GIBBOUS zunehmend (π/2 < φ < π):
                // Helle Terminator-Ellipse dehnt das Licht in den linken dunklen Bereich aus.
                // Ergebnis: mehr als die Hälfte beleuchtet.
                canvas.drawOval(
                    RectF(cx - terminatorR, cy - r, cx + terminatorR, cy + r),
                    litPaint
                )
            }

        } else {
            // ══ ABNEHMEND: linke Seite leuchtet ═══════════════════════

            // Linken Halbkreis beleuchtet zeichnen
            canvas.drawPath(leftHalf(cx, cy, r), litPaint)

            if (phi < 3.0 * Math.PI / 2.0) {
                // GIBBOUS abnehmend (π < φ < 3π/2):
                // Helle Terminator-Ellipse dehnt Licht in den rechten Bereich aus.
                canvas.drawOval(
                    RectF(cx - terminatorR, cy - r, cx + terminatorR, cy + r),
                    litPaint
                )
            } else {
                // SICHEL abnehmend (3π/2 < φ < 2π):
                // Dunkle Terminator-Ellipse überdeckt den größten Teil des linken Halbkreises.
                canvas.drawOval(
                    RectF(cx - terminatorR, cy - r, cx + terminatorR, cy + r),
                    shadowPaint
                )
            }
        }

        // ── 3. Subtile Mondflecken auf der beleuchteten Seite ─────────
        if (phase > 0.05 && phase < 0.95) {
            drawMoonSpots(canvas, cx, cy, r, phase.toFloat())
        }

        canvas.restore()

        // ── 4. Rand ────────────────────────────────────────────────────
        canvas.drawCircle(cx, cy, r, borderPaint)
    }

    /** Rechter Halbkreis (Tortenstück) als Path */
    private fun rightHalf(cx: Float, cy: Float, r: Float): Path {
        return Path().apply {
            moveTo(cx, cy)
            lineTo(cx, cy - r)
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 180f)
            close()
        }
    }

    /** Linker Halbkreis (Tortenstück) als Path */
    private fun leftHalf(cx: Float, cy: Float, r: Float): Path {
        return Path().apply {
            moveTo(cx, cy)
            lineTo(cx, cy - r)
            arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, -180f)
            close()
        }
    }

    /** Kleine dunkle Flecken für einen realistischeren Look */
    private fun drawMoonSpots(canvas: Canvas, cx: Float, cy: Float, r: Float, phase: Float) {
        // Feste Positionen der "Mare" (Mondmeere) – normiert auf r=1
        val spots = listOf(
            Triple(0.15f,  -0.20f, 0.18f),   // Mare Serenitatis
            Triple(-0.10f, -0.30f, 0.12f),   // Mare Imbrium (links oben)
            Triple(0.25f,   0.15f, 0.10f),   // Mare Crisium
            Triple(-0.05f,  0.10f, 0.14f),   // Mare Nubium
            Triple(0.05f,  -0.05f, 0.09f)    // Mare Tranquillitatis
        )
        for ((nx, ny, nr) in spots) {
            canvas.drawCircle(cx + nx * r, cy + ny * r, nr * r, texturePaint)
        }
    }
}
