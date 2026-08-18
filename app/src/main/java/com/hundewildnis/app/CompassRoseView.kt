package com.hundewildnis.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Eine hochwertig gezeichnete Kompassrose mit Gradeinteilung und integrierter Nadel.
 */
class CompassRoseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val accentColor = Color.parseColor("#8fad6f")
    private val secondaryColor = Color.parseColor("#CCCCCC")

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = accentColor
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = secondaryColor
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textSize = 52f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val needlePath = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        val radius = (minOf(w, h) / 2f) * 0.9f
        if (radius > 0) {
            val gradient = RadialGradient(cx, cy, radius,
                Color.parseColor("#442d5016"), Color.parseColor("#11000000"), Shader.TileMode.CLAMP)
            fillPaint.shader = gradient
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) * 0.9f

        // 1. Subtile Hintergrund-Füllung
        canvas.drawCircle(cx, cy, radius, fillPaint)

        // 2. Äußerer Ring und innerer Ring
        canvas.drawCircle(cx, cy, radius, circlePaint)
        circlePaint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, radius * 0.65f, circlePaint)
        circlePaint.strokeWidth = 4f

        // 3. Markierungen und Zahlen
        for (i in 0 until 360 step 5) {
            val angle = Math.toRadians(i.toDouble() - 90)
            val isMajor = i % 30 == 0
            val isCardinal = i % 90 == 0
            
            val startR = if (isCardinal) radius * 0.82f else if (isMajor) radius * 0.85f else radius * 0.90f
            val endR = radius * 0.95f
            
            val stopX = cx + cos(angle).toFloat() * endR
            val stopY = cy + sin(angle).toFloat() * endR
            val startX = cx + cos(angle).toFloat() * startR
            val startY = cy + sin(angle).toFloat() * startR

            tickPaint.strokeWidth = if (isMajor) 5f else 3f
            tickPaint.color = if (isCardinal) accentColor else secondaryColor
            canvas.drawLine(startX, startY, stopX, stopY, tickPaint)

            // Zahlen bei 30er Schritten (außer Himmelsrichtungen)
            if (isMajor && !isCardinal) {
                val textR = radius * 0.72f
                val tx = cx + cos(angle).toFloat() * textR
                val ty = cy + sin(angle).toFloat() * textR + (textPaint.textSize / 3f)
                canvas.drawText(i.toString(), tx, ty, textPaint)
            }
        }

        // 4. Himmelsrichtungen (N, O, S, W)
        drawCardinal(canvas, cx, cy, radius * 0.70f, "N", 0)
        drawCardinal(canvas, cx, cy, radius * 0.70f, "O", 90)
        drawCardinal(canvas, cx, cy, radius * 0.70f, "S", 180)
        drawCardinal(canvas, cx, cy, radius * 0.70f, "W", 270)
        
        // 4b. Zwischen-Himmelsrichtungen (kleiner)
        cardinalPaint.textSize = 30f
        drawCardinal(canvas, cx, cy, radius * 0.70f, "NO", 45)
        drawCardinal(canvas, cx, cy, radius * 0.70f, "SO", 135)
        drawCardinal(canvas, cx, cy, radius * 0.70f, "SW", 225)
        drawCardinal(canvas, cx, cy, radius * 0.70f, "NW", 315)
        cardinalPaint.textSize = 52f

        // 5. Die integrierte Kompassnadel
        drawNeedle(canvas, cx, cy, radius * 0.6f)
    }

    private fun drawCardinal(canvas: Canvas, cx: Float, cy: Float, r: Float, text: String, degrees: Int) {
        val angle = Math.toRadians(degrees.toDouble() - 90)
        val tx = cx + cos(angle).toFloat() * r
        val ty = cy + sin(angle).toFloat() * r + (cardinalPaint.textSize / 3f)
        
        cardinalPaint.color = if (text == "N") Color.RED else accentColor
        canvas.drawText(text, tx, ty, cardinalPaint)
    }

    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, length: Float) {
        val width = length * 0.15f
        
        // Nord-Teil (Rot)
        needlePaint.color = Color.RED
        needlePath.reset()
        needlePath.moveTo(cx, cy - length)
        needlePath.lineTo(cx - width, cy)
        needlePath.lineTo(cx + width, cy)
        needlePath.close()
        canvas.drawPath(needlePath, needlePaint)
        
        // Süd-Teil (Grau/Silber)
        needlePaint.color = Color.LTGRAY
        needlePath.reset()
        needlePath.moveTo(cx, cy + length)
        needlePath.lineTo(cx - width, cy)
        needlePath.lineTo(cx + width, cy)
        needlePath.close()
        canvas.drawPath(needlePath, needlePaint)
        
        // Mittelpunkt
        needlePaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, width * 0.4f, needlePaint)
        needlePaint.style = Paint.Style.STROKE
        needlePaint.color = Color.DKGRAY
        needlePaint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, width * 0.4f, needlePaint)
        needlePaint.style = Paint.Style.FILL
    }
}
