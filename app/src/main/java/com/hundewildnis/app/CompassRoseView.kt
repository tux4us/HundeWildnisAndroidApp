package com.hundewildnis.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Eine hochwertig gezeichnete Kompassrose mit Gradeinteilung und integrierter Nadel.
 * Zeigt zusätzlich (falls vorhanden) die Richtung von Sonne, Mond und Polarstern
 * inkl. Großer-Wagen-Uhrzeiger an.
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

    // ── Himmelskörper-Darstellung ──
    private val skyIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val skyIconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val skyLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val wagenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#88CCE0FF")
    }

    // Chip-Hintergrund für die Beschriftung unter den Himmelskörper-Icons
    private val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC1a1a1a")
    }
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 20f
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val chipRect = RectF()

    // Alpha-Werte für "sichtbar" vs. "unter Horizont" (abgedunkelt, aber weiterhin Richtung erkennbar)
    private val ALPHA_VISIBLE = 255
    private val ALPHA_BELOW_HORIZON = 90

    private var skyData: SkyCalculator.SkyData? = null

    /** Muss von außen (Activity) bei jedem Berechnungs-Update aufgerufen werden. */
    fun updateSkyData(data: SkyCalculator.SkyData) {
        this.skyData = data
        invalidate()
    }

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

        // 5. Himmelskörper (Sonne, Mond, Polaris + Großer Wagen) — zwischen Ring und Nadel
        skyData?.let { data ->
            drawSunIcon(canvas, cx, cy, radius, data.sun)
            drawMoonIcon(canvas, cx, cy, radius, data.moon, data.moonIlluminationPercent)
            drawPolarisAndGreatBear(canvas, cx, cy, radius, data.polarisResult)
        }

        // 6. Die integrierte Kompassnadel
        drawNeedle(canvas, cx, cy, radius * 0.6f)
    }

    private fun drawCardinal(canvas: Canvas, cx: Float, cy: Float, r: Float, text: String, degrees: Int) {
        val angle = Math.toRadians(degrees.toDouble() - 90)
        val tx = cx + cos(angle).toFloat() * r
        val ty = cy + sin(angle).toFloat() * r + (cardinalPaint.textSize / 3f)

        cardinalPaint.color = if (text == "N") Color.RED else accentColor
        canvas.drawText(text, tx, ty, cardinalPaint)
    }

    /** Berechnet Mittelpunkt eines Himmelskörper-Icons am äußeren Rand der Rose für ein Azimut. */
    private fun iconCenter(cx: Float, cy: Float, radius: Float, azimuthDeg: Double): Pair<Float, Float> {
        val angle = Math.toRadians(azimuthDeg - 90.0)
        val iconR = radius * 1.08f // knapp außerhalb des äußeren Rings
        return (cx + cos(angle).toFloat() * iconR) to (cy + sin(angle).toFloat() * iconR)
    }

    /**
     * Zeichnet die Sonne als strahlenden Kreis (Vollkreis + radiale Strahlen), an der
     * Position ihres Azimuts. Unter dem Horizont wird sie abgedunkelt dargestellt,
     * bleibt aber sichtbar, damit die Richtung erkennbar ist.
     */
    private fun drawSunIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, pos: SkyCalculator.SkyPosition) {
        val (ix, iy) = iconCenter(cx, cy, radius, pos.azimuth)
        val alpha = if (pos.isVisible) ALPHA_VISIBLE else ALPHA_BELOW_HORIZON
        val sunColor = Color.parseColor("#FFC94A")
        val iconRadius = radius * 0.075f

        // Strahlen (8 Stück, radial nach außen)
        skyIconStrokePaint.color = sunColor
        skyIconStrokePaint.alpha = alpha
        skyIconStrokePaint.strokeWidth = 3.5f
        for (i in 0 until 8) {
            val rayAngle = Math.toRadians(i * 45.0)
            val innerR = iconRadius * 1.25f
            val outerR = iconRadius * 1.9f
            val x1 = ix + cos(rayAngle).toFloat() * innerR
            val y1 = iy + sin(rayAngle).toFloat() * innerR
            val x2 = ix + cos(rayAngle).toFloat() * outerR
            val y2 = iy + sin(rayAngle).toFloat() * outerR
            canvas.drawLine(x1, y1, x2, y2, skyIconStrokePaint)
        }
        skyIconStrokePaint.strokeWidth = 3f

        // Sonnenscheibe mit weichem Glow
        val glow = RadialGradient(
            ix, iy, iconRadius * 1.6f,
            intArrayOf(sunColor, Color.argb(0, 255, 201, 74)),
            floatArrayOf(0.4f, 1f), Shader.TileMode.CLAMP
        )
        skyIconPaint.shader = glow
        skyIconPaint.alpha = alpha
        canvas.drawCircle(ix, iy, iconRadius * 1.6f, skyIconPaint)
        skyIconPaint.shader = null

        skyIconPaint.color = sunColor
        skyIconPaint.alpha = alpha
        canvas.drawCircle(ix, iy, iconRadius, skyIconPaint)
        skyIconStrokePaint.color = Color.parseColor("#8B5A00")
        skyIconStrokePaint.alpha = alpha
        canvas.drawCircle(ix, iy, iconRadius, skyIconStrokePaint)
        skyIconStrokePaint.color = sunColor

        drawLabelChip(canvas, ix, iy + iconRadius * 2.6f, "Sonne", alpha)
    }

    /**
     * Zeichnet den Mond als Sichel/Scheibe entsprechend dem tatsächlichen Beleuchtungsgrad
     * (0% = Neumond/dunkel, 100% = Vollmond). Die Sichelform wird über ein einfaches
     * Zwei-Kreis-Verfahren angenähert (heller Grundkreis, dunkler Überlagerungskreis,
     * dessen Versatz den beleuchteten Anteil bestimmt) — optisch korrekt für die
     * Groborientierung, ohne exakte Mondphasen-Geometrie (Terminator-Ellipse) zu benötigen.
     */
    private fun drawMoonIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        pos: SkyCalculator.SkyPosition,
        illuminationPercent: Double
    ) {
        val (ix, iy) = iconCenter(cx, cy, radius, pos.azimuth)
        val alpha = if (pos.isVisible) ALPHA_VISIBLE else ALPHA_BELOW_HORIZON
        val moonLight = Color.parseColor("#E8E8E8")
        val moonDark = Color.parseColor("#2a2a2a")
        val iconRadius = radius * 0.075f

        val saved = canvas.save()
        // Kreisförmiger Clip, damit die Überlagerung nicht über den Mond hinausragt
        val clipPath = Path().apply { addCircle(ix, iy, iconRadius, Path.Direction.CW) }
        canvas.clipPath(clipPath)

        // Grundscheibe: voll beleuchtet
        skyIconPaint.shader = null
        skyIconPaint.color = moonLight
        skyIconPaint.alpha = alpha
        canvas.drawCircle(ix, iy, iconRadius, skyIconPaint)

        // Anteil, der im Schatten liegt (0 = Vollmond, kein Schatten; 1 = Neumond, ganz Schatten)
        val darkFraction = (1.0 - (illuminationPercent / 100.0)).coerceIn(0.0, 1.0)
        if (darkFraction > 0.01) {
            // Der Schattenkreis wird so weit über die helle Scheibe geschoben, dass
            // der sichtbare helle Rest proportional zur Beleuchtung ist (Sichel-Näherung).
            val offset = iconRadius * (2.0 * darkFraction - 1.0).toFloat() * -1f
            // offset: bei darkFraction=0 -> Schatten komplett außerhalb (kein Effekt)
            //         bei darkFraction=1 -> Schatten deckt Mond komplett ab
            //         bei darkFraction=0.5 -> Schatten genau mittig (Halbmond)
            val shadowCx = ix + (iconRadius * (1.0 - 2.0 * darkFraction)).toFloat()
            skyIconPaint.color = moonDark
            skyIconPaint.alpha = alpha
            canvas.drawCircle(shadowCx, iy, iconRadius * 1.02f, skyIconPaint)
        }

        canvas.restoreToCount(saved)

        skyIconStrokePaint.color = Color.parseColor("#888888")
        skyIconStrokePaint.alpha = alpha
        canvas.drawCircle(ix, iy, iconRadius, skyIconStrokePaint)

        drawLabelChip(canvas, ix, iy + iconRadius * 2.6f, "Mond", alpha)
    }

    /** Zeichnet einen abgerundeten dunklen Beschriftungs-Chip unter einem Icon. */
    private fun drawLabelChip(canvas: Canvas, cx: Float, chipCy: Float, text: String, alpha: Int) {
        val textWidth = chipTextPaint.measureText(text)
        val paddingH = 14f
        val paddingV = 6f
        val chipHeight = chipTextPaint.textSize + paddingV * 2

        chipRect.set(
            cx - textWidth / 2f - paddingH,
            chipCy - chipHeight / 2f,
            cx + textWidth / 2f + paddingH,
            chipCy + chipHeight / 2f
        )
        chipBgPaint.alpha = (alpha * 0.8f).toInt()
        canvas.drawRoundRect(chipRect, chipHeight / 2f, chipHeight / 2f, chipBgPaint)

        chipTextPaint.alpha = alpha
        canvas.drawText(text, cx, chipCy + chipTextPaint.textSize * 0.35f, chipTextPaint)
    }

    /**
     * Zeichnet Polaris als kleinen Stern am Ring plus eine schematische Miniatur-Darstellung
     * des Großen Wagens in seiner aktuellen "Uhrzeiger"-Position relativ zu Polaris.
     */
    private val starPath = Path()

    /** Zeichnet einen gefüllten 4-zackigen "Funkel"-Stern (klassisches Diamant-Stern-Symbol). */
    private fun drawSparkleStar(canvas: Canvas, cx: Float, cy: Float, outerR: Float, innerR: Float, paint: Paint) {
        starPath.reset()
        val points = 4
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outerR else innerR
            val angle = Math.toRadians((i * 45.0) - 90.0)
            val x = cx + cos(angle).toFloat() * r
            val y = cy + sin(angle).toFloat() * r
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        canvas.drawPath(starPath, paint)
    }

    private fun drawPolarisAndGreatBear(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        polarisResult: SkyCalculator.PolarisResult
    ) {
        val pos = polarisResult.polaris
        val (ix, iy) = iconCenter(cx, cy, radius, pos.azimuth)

        val alpha = if (pos.isVisible) ALPHA_VISIBLE else ALPHA_BELOW_HORIZON
        val polarisColor = Color.parseColor("#CFE8FF")
        val iconRadius = radius * 0.075f

        // Weicher Glow hinter dem Stern
        val glow = RadialGradient(
            ix, iy, iconRadius * 2.0f,
            intArrayOf(Color.argb((alpha * 0.5f).toInt(), 207, 232, 255), Color.argb(0, 207, 232, 255)),
            floatArrayOf(0.3f, 1f), Shader.TileMode.CLAMP
        )
        skyIconPaint.shader = glow
        canvas.drawCircle(ix, iy, iconRadius * 2.0f, skyIconPaint)
        skyIconPaint.shader = null

        // Funkelnder Hauptstern (großer Diamant) + kleiner Querstern für den "Sparkle"-Effekt
        skyIconPaint.color = polarisColor
        skyIconPaint.alpha = alpha
        drawSparkleStar(canvas, ix, iy, iconRadius * 1.3f, iconRadius * 0.35f, skyIconPaint)

        skyIconPaint.alpha = (alpha * 0.8f).toInt()
        drawSparkleStar(canvas, ix, iy, iconRadius * 0.75f * 1.3f, iconRadius * 0.75f * 0.35f, skyIconPaint)
        // (kleinerer, um 90° dazu gedachter Glanzpunkt entsteht optisch bereits durch die Diamantform)

        drawLabelChip(canvas, ix, iy + iconRadius * 2.6f, "Polaris", alpha)

        // Großer-Wagen-Uhrzeiger: kleines Zifferblatt-Symbol neben Polaris,
        // Zeiger zeigt auf die aktuelle "Stunde" (1-12) des Wagens relativ zu Polaris.
        val clockCx = ix
        val clockCy = iy - iconRadius * 3.2f
        val clockR = iconRadius * 1.4f

        wagenPaint.alpha = alpha
        canvas.drawCircle(clockCx, clockCy, clockR, wagenPaint)

        // Zeiger: 12 Uhr = oben (0°), im Uhrzeigersinn nach Stunde
        val clockAngleDeg = polarisResult.greatBearClockPosition * 30.0 - 90.0
        val clockAngleRad = Math.toRadians(clockAngleDeg)
        val pointerX = clockCx + cos(clockAngleRad).toFloat() * clockR * 0.85f
        val pointerY = clockCy + sin(clockAngleRad).toFloat() * clockR * 0.85f

        wagenPaint.strokeWidth = 4f
        canvas.drawLine(clockCx, clockCy, pointerX, pointerY, wagenPaint)
        wagenPaint.strokeWidth = 3f

        skyLabelPaint.textSize = 16f
        skyLabelPaint.color = Color.parseColor("#CCE0FF")
        skyLabelPaint.alpha = alpha
        canvas.drawText(
            "Wagen ${polarisResult.greatBearClockPosition}h",
            clockCx,
            clockCy - clockR - 8f,
            skyLabelPaint
        )
        skyLabelPaint.textSize = 26f
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