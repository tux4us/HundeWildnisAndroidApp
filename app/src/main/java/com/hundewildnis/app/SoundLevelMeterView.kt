package com.hundewildnis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * Analoges Pegelmesser-Zifferblatt (VU-Meter-Optik) für den Lautstärke-Trainer.
 *
 * Zeigt den geschätzten dB(A)-Wert als Zeigerausschlag auf einer Halbkreis-Skala
 * mit farbigen Sprechweise-Zonen (A1 grün bis A4 rot), zusammen mit dem
 * Zahlenwert und der Stufen-Bezeichnung in der Mitte.
 *
 * Die Skala ist bewusst NICHT linear-normiert im Sinne eines Präzisionsmessgeräts,
 * sondern eine visuelle Orientierungshilfe innerhalb des Bereichs SCALE_MIN..SCALE_MAX.
 */
class SoundLevelMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val SCALE_MIN = 30f
        private const val SCALE_MAX = 100f

        // Skalenwinkel: von 180° (links, Minimum) bis 360°/0° (rechts, Maximum) -> Halbkreis oben
        private const val ANGLE_START = 180f
        private const val ANGLE_SWEEP = 180f

        private const val ANIM_DURATION_MS = 300L
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
    }
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()

    /** Aktuell dargestellter Zeigerwert in dB(A), oder null solange nicht kalibriert / keine Stimme. */
    private var displayValue: Float? = null
    private var animatedAngleFraction = 0f
    private var needleAnimator: ValueAnimator? = null

    private var valueLabel: String = "-- dB(A)"
    private var levelLabel: String = "Kalibrierung erforderlich"
    private var needleColor: Int = Color.WHITE

    init {
        // Farben aus den vier Sprechweise-Zonen, konsistent mit SpeechLevel-Grenzen (60/70/80 dB)
    }

    /**
     * Aktualisiert den angezeigten Wert. amplitude-basierter dB-Wert oder null,
     * wenn keine Kalibrierung/keine Stimme vorliegt (Nadel bleibt am linken Anschlag).
     *
     * @param overrideLabel Wenn gesetzt, wird dieses Label statt der automatisch aus dem
     *                       dB-Wert abgeleiteten Sprechweise-Stufe angezeigt (z. B. für die
     *                       Anzeige einer historischen Session).
     */
    fun setValue(db: Float?, calibrated: Boolean, overrideLabel: String? = null) {
        displayValue = db

        valueLabel = if (db != null) "${db.toInt()} dB(A)" else "-- dB(A)"
        levelLabel = overrideLabel ?: when {
            !calibrated -> context.getString(R.string.sound_level_meter_calibration_required)
            db == null -> context.getString(R.string.sound_level_meter_waiting_for_voice)
            else -> SoundLevelEstimator.classify(db).label(context)
        }
        needleColor = if (db != null) zoneColorForDb(db) else Color.GRAY

        val targetFraction = if (db != null) {
            ((db - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)).coerceIn(0f, 1f)
        } else {
            0f
        }
        animateTo(targetFraction)
        invalidate()
    }

    private fun animateTo(targetFraction: Float) {
        needleAnimator?.cancel()
        needleAnimator = ValueAnimator.ofFloat(animatedAngleFraction, targetFraction).apply {
            duration = ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedAngleFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun zoneColorForDb(db: Float): Int {
        return when (SoundLevelEstimator.classify(db)) {
            SpeechLevel.A1_LEISE -> Color.parseColor("#4CAF50")       // grün
            SpeechLevel.A2_GESPRAECH -> Color.parseColor("#9FD66A")   // hellgrün (Markenfarbe)
            SpeechLevel.A3_ANGEHOBEN -> Color.parseColor("#FFB300")   // amber
            SpeechLevel.A4_SCHARF -> Color.parseColor("#E53935")      // rot
        }
    }

    /** Layout-Kennzahlen, aus der Breite abgeleitet – von onMeasure und onDraw gemeinsam genutzt,
     *  damit die gemessene Höhe garantiert zur tatsächlich gezeichneten Höhe passt. */
    private data class Layout(
        val strokeWidth: Float,
        val padding: Float,
        val radius: Float,
        val valueTextSize: Float,
        val labelTextSize: Float,
        val gapArcToValue: Float,
        val gapValueToLabel: Float,
        val labelDescent: Float
    ) {
        val cy get() = padding + radius
        val totalHeight get() = padding + radius * 2f + gapArcToValue + gapValueToLabel + labelDescent
    }

    private fun computeLayout(w: Float): Layout {
        val strokeWidth = w * 0.06f
        val padding = strokeWidth
        val radius = (w - padding * 2) / 2f
        val valueTextSize = w * 0.11f
        val labelTextSize = w * 0.05f
        return Layout(
            strokeWidth = strokeWidth,
            padding = padding,
            radius = radius,
            valueTextSize = valueTextSize,
            labelTextSize = labelTextSize,
            gapArcToValue = valueTextSize * 0.55f,
            gapValueToLabel = labelTextSize * 1.15f,
            labelDescent = labelTextSize * 0.3f
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val layout = computeLayout(w)
        val strokeWidth = layout.strokeWidth
        val radius = layout.radius
        val cx = w / 2f
        val cy = layout.cy

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Hintergrund-Skala (dezent, in Textfarbe mit geringer Deckkraft)
        arcPaint.strokeWidth = strokeWidth
        arcPaint.color = Color.argb(40, 255, 255, 255)
        canvas.drawArc(arcRect, ANGLE_START, ANGLE_SWEEP, false, arcPaint)

        // Farbzonen entlang der Skala (A1–A4), je nach dB-Grenzen anteilig am Bogen
        drawZone(canvas, arcRect, strokeWidth, 30f, 60f, Color.parseColor("#4CAF50"))
        drawZone(canvas, arcRect, strokeWidth, 60f, 70f, Color.parseColor("#9FD66A"))
        drawZone(canvas, arcRect, strokeWidth, 70f, 80f, Color.parseColor("#FFB300"))
        drawZone(canvas, arcRect, strokeWidth, 80f, 100f, Color.parseColor("#E53935"))

        // Ticks bei 30/40/50/60/70/80/90/100 dB
        tickPaint.color = Color.argb(160, 255, 255, 255)
        var tickDb = SCALE_MIN
        while (tickDb <= SCALE_MAX) {
            drawTick(canvas, cx, cy, radius, strokeWidth, tickDb)
            tickDb += 10f
        }

        // Zeiger
        val angleDeg = ANGLE_START + ANGLE_SWEEP * animatedAngleFraction
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val needleLength = radius - strokeWidth * 0.9f
        val tipX = cx + (needleLength * cos(angleRad)).toFloat()
        val tipY = cy + (needleLength * sin(angleRad)).toFloat()

        needlePaint.strokeWidth = strokeWidth * 0.18f
        needlePaint.color = needleColor
        canvas.drawLine(cx, cy, tipX, tipY, needlePaint)

        hubPaint.color = needleColor
        canvas.drawCircle(cx, cy, strokeWidth * 0.35f, hubPaint)

        // Zahlenwert + Stufen-Label unterhalb des Bogens
        valueTextPaint.textSize = layout.valueTextSize
        valueTextPaint.color = Color.WHITE
        val valueBaselineY = cy + radius + layout.gapArcToValue
        canvas.drawText(valueLabel, cx, valueBaselineY, valueTextPaint)

        labelTextPaint.textSize = layout.labelTextSize
        labelTextPaint.color = Color.argb(220, 255, 255, 255)
        val labelBaselineY = valueBaselineY + layout.gapValueToLabel
        canvas.drawText(levelLabel, cx, labelBaselineY, labelTextPaint)
    }

    private fun drawZone(canvas: Canvas, rect: RectF, strokeWidth: Float, fromDb: Float, toDb: Float, color: Int) {
        val startFraction = ((fromDb - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)).coerceIn(0f, 1f)
        val endFraction = ((toDb - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)).coerceIn(0f, 1f)
        val zonePaint = Paint(arcPaint).apply {
            this.color = color
            this.strokeWidth = strokeWidth * 0.35f
        }
        val zoneRect = RectF(
            rect.left + strokeWidth * 0.32f,
            rect.top + strokeWidth * 0.32f,
            rect.right - strokeWidth * 0.32f,
            rect.bottom - strokeWidth * 0.32f
        )
        canvas.drawArc(
            zoneRect,
            ANGLE_START + ANGLE_SWEEP * startFraction,
            ANGLE_SWEEP * (endFraction - startFraction),
            false,
            zonePaint
        )
    }

    private fun drawTick(canvas: Canvas, cx: Float, cy: Float, radius: Float, strokeWidth: Float, db: Float) {
        val fraction = ((db - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)).coerceIn(0f, 1f)
        val angleRad = Math.toRadians((ANGLE_START + ANGLE_SWEEP * fraction).toDouble())
        val outerR = radius - strokeWidth * 1.05f
        val innerR = outerR - strokeWidth * 0.4f
        val x1 = cx + (innerR * cos(angleRad)).toFloat()
        val y1 = cy + (innerR * sin(angleRad)).toFloat()
        val x2 = cx + (outerR * cos(angleRad)).toFloat()
        val y2 = cy + (outerR * sin(angleRad)).toFloat()
        canvas.drawLine(x1, y1, x2, y2, tickPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = computeLayout(width.toFloat()).totalHeight.toInt()
        setMeasuredDimension(width, height)
    }
}