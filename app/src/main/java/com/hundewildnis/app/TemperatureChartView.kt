// ════════════════════════════════════════════════════════════════
// TEMPERATUR-VERLAUFSGRAFIK (Custom View)
// Ohne externe Libraries - nutzt nur Android Canvas
// ════════════════════════════════════════════════════════════════

package com.hundewildnis.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class TemperatureChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint().apply {
        color = Color.parseColor("#8fad6f") // Deine App-Farbe
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, 600f,
            intArrayOf(
                Color.parseColor("#4D8fad6f"), // Halbtransparent oben
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

    private var temperatures = listOf<Double>()
    private var times = listOf<String>()

    fun setData(temps: List<Double>, timeLabels: List<String>) {
        temperatures = temps
        times = timeLabels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (temperatures.isEmpty()) return

        val padding = 60f
        val chartWidth = width - 2 * padding
        val chartHeight = height - 2 * padding

        // Min/Max Temperatur
        val minTemp = temperatures.minOrNull() ?: 0.0
        val maxTemp = temperatures.maxOrNull() ?: 30.0
        val tempRange = maxTemp - minTemp

        // Grid zeichnen (horizontal)
        for (i in 0..4) {
            val y = padding + (chartHeight * i / 4)
            canvas.drawLine(padding, y, width - padding, y, gridPaint)

            // Temperatur-Label
            val temp = maxTemp - (tempRange * i / 4)
            canvas.drawText(
                String.format("%.0f°", temp),
                10f,
                y + 10f,
                textPaint
            )
        }

        // Datenpunkte verbinden
        val path = Path()
        val fillPath = Path()

        val pointSpacing = chartWidth / (temperatures.size - 1).coerceAtLeast(1)

        temperatures.forEachIndexed { index, temp ->
            val x = padding + (index * pointSpacing)
            val y = padding + chartHeight - 
                    ((temp - minTemp) / tempRange * chartHeight).toFloat()

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

// ════════════════════════════════════════════════════════════════
// VERWENDUNG IN activity_weather.xml
// ════════════════════════════════════════════════════════════════

/*
<com.hundewildnis.app.TemperatureChartView
    android:id="@+id/temperatureChart"
    android:layout_width="match_parent"
    android:layout_height="200dp"
    android:layout_marginTop="16dp"
    android:background="#1a1a1a" />
*/

// ════════════════════════════════════════════════════════════════
// VERWENDUNG IN WeatherActivity.kt
// ════════════════════════════════════════════════════════════════

/*
private fun displayTemperatureChart(data: JSONObject) {
    val hourly = data.getJSONObject("hourly")
    val times = hourly.getJSONArray("time")
    val temperatures = hourly.getJSONArray("temperature_2m")

    val temps = mutableListOf<Double>()
    val labels = mutableListOf<String>()

    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.GERMAN)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMAN)

    // 24 Stunden
    for (i in 0 until 24.coerceAtMost(temperatures.length())) {
        temps.add(temperatures.getDouble(i))

        val timeStr = times.getString(i)
        val date = dateFormat.parse(timeStr)
        labels.add(date?.let { timeFormat.format(it) } ?: "")
    }

    binding.temperatureChart.setData(temps, labels)
}
*/
