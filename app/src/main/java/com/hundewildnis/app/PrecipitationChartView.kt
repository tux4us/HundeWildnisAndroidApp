package com.hundewildnis.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class PrecipitationChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint().apply {
        color = Color.parseColor("#2196F3") // Blau für Regen
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, 600f,
            intArrayOf(
                Color.parseColor("#4D2196F3"), // Halbtransparent Blau oben
                Color.parseColor("#00000000")  // Transparent unten
            ),
            null,
            Shader.TileMode.CLAMP
        )
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#33FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#999999")
        textSize = 28f
        isAntiAlias = true
    }

    private var precipitation = listOf<Double>()
    private var times = listOf<String>()

    fun setData(precip: List<Double>, timeLabels: List<String>) {
        precipitation = precip
        times = timeLabels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (precipitation.isEmpty()) return

        val padding = 60f
        val chartWidth = width - 2 * padding
        val chartHeight = height - 2 * padding

        // Min/Max Niederschlag (Min ist immer 0)
        val maxPrecip = max(1.0, precipitation.maxOrNull() ?: 0.0)
        val precipRange = maxPrecip

        // Grid zeichnen (horizontal)
        for (i in 0..4) {
            val y = padding + (chartHeight * i / 4)
            canvas.drawLine(padding, y, width - padding, y, gridPaint)

            // Niederschlag-Label
            val amount = maxPrecip - (precipRange * i / 4)
            canvas.drawText(
                String.format("%.1f mm", amount),
                10f,
                y + 10f,
                textPaint
            )
        }

        // Datenpunkte verbinden
        val path = Path()
        val fillPath = Path()

        val pointSpacing = chartWidth / (precipitation.size - 1).coerceAtLeast(1)

        precipitation.forEachIndexed { index, amount ->
            val x = padding + (index * pointSpacing)
            val y = padding + chartHeight - 
                    (amount / maxPrecip * chartHeight).toFloat()

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height - padding)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            // Zeit-Label (jede 3. Stunde)
            if (index % 3 == 0 && index < times.size) {
                canvas.drawText(
                    times[index],
                    x - 20f,
                    height - padding + 40f,
                    textPaint
                )
            }
        }

        // Fill-Pfad schließen
        fillPath.lineTo(width - padding, height - padding)
        fillPath.close()

        // Zeichnen
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
