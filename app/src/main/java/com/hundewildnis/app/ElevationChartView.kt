package com.hundewildnis.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Custom View: Zeichnet das Höhenprofil einer Route als
 * gefülltes Flächendiagramm mit Gradient.
 *
 * Verwendung:
 *   elevationChart.setElevationData(routeData.elevationPoints)
 *   elevationChart.setCurrentPosition(index)   // aktualisiert Positionsmarker
 */
class ElevationChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var elevationPoints = listOf<Double>()
    private var currentIndex = 0

    // ─── Paints ────────────────────────────────────────────
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8FC357")
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22FFFFFF")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.FILL
    }

    private val markerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textSize = 26f
        textAlign = Paint.Align.RIGHT
    }

    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B2B2AE")
        textSize = 30f
        textAlign = Paint.Align.CENTER
    }

    // ─── API ───────────────────────────────────────────────

    fun setElevationData(points: List<Double>, currentIdx: Int = 0) {
        elevationPoints = points
        currentIndex = currentIdx.coerceIn(0, (points.size - 1).coerceAtLeast(0))
        invalidate()
    }

    fun setCurrentPosition(idx: Int) {
        currentIndex = idx.coerceIn(0, (elevationPoints.size - 1).coerceAtLeast(0))
        invalidate()
    }

    // ─── Drawing ───────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (elevationPoints.size < 2) {
            canvas.drawText(
                if (elevationPoints.isEmpty()) "Höhendaten laden..." else "Zu wenig Datenpunkte",
                width / 2f,
                height / 2f + placeholderPaint.textSize / 3,
                placeholderPaint
            )
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()

        val minEl = elevationPoints.min()
        val maxEl = elevationPoints.max()
        val range = (maxEl - minEl).coerceAtLeast(10.0)
        val midEl = (minEl + maxEl) / 2.0

        val maxLabel   = "${maxEl.toInt()}m"
        val midLabel   = "${midEl.toInt()}m"
        val minLabel   = "${minEl.toInt()}m"
        val labelWidth = maxOf(
            labelPaint.measureText(maxLabel),
            labelPaint.measureText(midLabel),
            labelPaint.measureText(minLabel)
        )
        val padLeft = labelWidth + 8f
        val padRight  = 10f
        val padTop    = 16f
        val padBottom = 8f

        val chartW = w - padLeft - padRight
        val chartH = h - padTop - padBottom

        val n = elevationPoints.size

        fun getX(i: Int)  = padLeft + (i.toFloat() / (n - 1)) * chartW
        fun getY(el: Double) = padTop + chartH * (1.0 - (el - minEl) / range).toFloat()

        // Horizontale Gitternetzlinien
        for (fraction in listOf(0f, 0.33f, 0.66f, 1f)) {
            val y = padTop + chartH * fraction
            canvas.drawLine(padLeft, y, padLeft + chartW, y, gridPaint)
        }

        // Gefüllte Fläche unterhalb der Linie
        val shader = LinearGradient(
            0f, padTop,
            0f, padTop + chartH,
            Color.parseColor("#AA8FC357"),
            Color.parseColor("#108FC357"),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = shader

        val fillPath = Path().apply {
            moveTo(getX(0), padTop + chartH)
            lineTo(getX(0), getY(elevationPoints[0]))
            for (i in 1 until n) lineTo(getX(i), getY(elevationPoints[i]))
            lineTo(getX(n - 1), padTop + chartH)
            close()
        }
        canvas.drawPath(fillPath, fillPaint)

        // Profillinie
        val linePath = Path().apply {
            moveTo(getX(0), getY(elevationPoints[0]))
            for (i in 1 until n) lineTo(getX(i), getY(elevationPoints[i]))
        }
        canvas.drawPath(linePath, linePaint)

        // Aktueller Positionsmarker (blauer Kreis)
        if (currentIndex < n) {
            val cx = getX(currentIndex)
            val cy = getY(elevationPoints[currentIndex])
            canvas.drawCircle(cx, cy, 9f, markerPaint)
            canvas.drawCircle(cx, cy, 9f, markerRingPaint)
        }

        // Y-Achsen-Beschriftung (Max / Mitte / Min)
        canvas.drawText("${maxEl.toInt()}m", padLeft - 4f, padTop + 10f,      labelPaint)
        canvas.drawText("${midEl.toInt()}m", padLeft - 4f, padTop + chartH / 2 + 8f, labelPaint)
        canvas.drawText("${minEl.toInt()}m", padLeft - 4f, padTop + chartH + 5f, labelPaint)
    }
}
