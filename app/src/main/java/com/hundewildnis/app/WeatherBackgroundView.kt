package com.hundewildnis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animierter, bewusst dezenter und dunkler Wetter-Hintergrund für [WeatherActivity].
 *
 * Eingabe sind ausschließlich Werte, die der Wetterabruf ohnehin liefert (Open-Meteo-Form):
 * WMO-Wettercode, Tag/Nacht, Bewölkung, Windgeschwindigkeit (km/h) und Windrichtung (°).
 * Gezeichnet wird per Canvas, ohne Bibliothek und ohne Netzwerkzugriff:
 *  - Himmelsverlauf (Tag/Nacht, je nach Bewölkung und Wetterart)
 *  - Sonne bzw. Mond mit Glow, Sterne mit leichtem Funkeln
 *  - treibende Wolken (Anzahl/Deckkraft nach Bewölkung, Richtung/Tempo nach Wind)
 *  - Nebelschichten
 *  - Regen und Schnee (Dichte nach Intensität, Neigung nach Wind)
 *  - Blitzaufhellung bei Gewitter
 *
 * Die Animation läuft nur, solange [onHostResume] aktiv ist, die View sichtbar ist, der Nutzer sie
 * nicht abgeschaltet hat ([setAnimationEnabled]) und die System-Animationen nicht deaktiviert sind.
 * Sonst wird ein ruhiges Standbild ohne Niederschlagspartikel und ohne Blitz gezeichnet.
 * Die Bildrate ist auf ca. 30 fps begrenzt.
 */
class WeatherBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Szenenbeschreibung ────────────────────────────────────────────────

    private class Scene(
        val skyTop: Int,
        val skyBottom: Int,
        val cloudFrac: Float,     // 0..1 Bewölkungsgrad
        val cloudTint: Int,
        val fog: Float,           // 0..1
        val rain: Float,          // 0..1 Intensität
        val snow: Float,          // 0..1 Intensität
        val sun: Boolean,
        val moon: Boolean,
        val stars: Float,         // 0..1 Sichtbarkeit
        val lightning: Boolean,
        val slant: Float,         // horizontale Verschiebung je vertikaler Einheit (Niederschlag)
        val drift: Float          // Wolkentempo mit Vorzeichen (Bildbreiten pro Sekunde)
    )

    private enum class Kind { CLEAR, CLOUD, FOG, RAIN, SNOW, STORM }

    private val dp = resources.displayMetrics.density
    private val rnd = Random(42)

    private var scene = Scene(0xFF10151C.toInt(), 0xFF1A222C.toInt(), 0f, 0xFFAFC0D4.toInt(),
        0f, 0f, 0f, sun = false, moon = false, stars = 0f, lightning = false, slant = 0f, drift = 0.01f)

    // Übergang zwischen zwei Szenen
    private var fromTop = scene.skyTop
    private var fromBottom = scene.skyBottom
    private var fade = 1f

    // Steuerung
    private var hostActive = false
    private var userAnimated = true
    private var lastNanos = 0L
    private var timeSec = 0f

    // ── Partikel (einmalig angelegt, keine Allokation beim Zeichnen) ──────

    private val rainX = FloatArray(MAX_RAIN) { rnd.nextFloat() }
    private val rainY = FloatArray(MAX_RAIN) { rnd.nextFloat() }
    private val rainSp = FloatArray(MAX_RAIN) { 0.75f + rnd.nextFloat() * 0.5f }
    private val rainD = FloatArray(MAX_RAIN) { rnd.nextFloat() }
    private val rainFarPts = FloatArray(MAX_RAIN * 4)
    private val rainNearPts = FloatArray(MAX_RAIN * 4)

    private val snowX = FloatArray(MAX_SNOW) { rnd.nextFloat() }
    private val snowY = FloatArray(MAX_SNOW) { rnd.nextFloat() }
    private val snowSp = FloatArray(MAX_SNOW) { 0.7f + rnd.nextFloat() * 0.6f }
    private val snowPhase = FloatArray(MAX_SNOW) { rnd.nextFloat() * 6.2832f }
    private val snowFreq = FloatArray(MAX_SNOW) { 0.5f + rnd.nextFloat() * 0.9f }
    private val snowPts = Array(3) { FloatArray(MAX_SNOW * 2) }
    private val snowCnt = IntArray(3)

    private val cloudX = FloatArray(MAX_CLOUDS) { -0.3f + rnd.nextFloat() * 1.5f }
    private val cloudY = FloatArray(MAX_CLOUDS) { 0.05f + rnd.nextFloat() * 0.40f }
    private val cloudW = FloatArray(MAX_CLOUDS) { 0.22f + rnd.nextFloat() * 0.20f }
    private val cloudSp = FloatArray(MAX_CLOUDS) { 0.6f + rnd.nextFloat() * 0.8f }

    private val fogX = FloatArray(FOG_LAYERS) { rnd.nextFloat() * 1.4f - 0.2f }
    private val fogY = FloatArray(FOG_LAYERS) { 0.30f + it * (0.65f / FOG_LAYERS) + rnd.nextFloat() * 0.05f }
    private val fogSp = FloatArray(FOG_LAYERS) { 0.5f + rnd.nextFloat() * 0.9f }

    private val starX = FloatArray(MAX_STARS) { rnd.nextFloat() }
    private val starY = FloatArray(MAX_STARS) { rnd.nextFloat() * 0.55f }
    private val starPts = Array(2) { FloatArray(MAX_STARS * 2) }
    private val starPhase = FloatArray(2) { rnd.nextFloat() * 6.2832f }

    private var flashT = -1f
    private var nextFlashIn = 3f

    // ── Paints ─────────────────────────────────────────────────────────────

    private val skyPaint = Paint()
    private var skyShader: Shader? = null
    private var skyKeyTop = 0
    private var skyKeyBottom = 0
    private var skyKeyH = 0f

    private val scrimPaint = Paint()
    private var scrimKeyH = 0f

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flashPaint = Paint().apply { color = Color.WHITE }

    private val rainFarPaint = linePaint(1.0f)
    private val rainNearPaint = linePaint(1.6f)
    private val snowPaints = arrayOf(dotPaint(2.2f), dotPaint(3.2f), dotPaint(4.4f))
    private val starPaints = arrayOf(dotPaint(1.5f), dotPaint(2.1f))

    private var cloudShaderTint = 0

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        corePaint.style = Paint.Style.FILL
    }

    private fun linePaint(widthDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = widthDp * dp
        color = 0xFFB8C8E0.toInt()
    }

    private fun dotPaint(sizeDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = sizeDp * dp
        color = Color.WHITE
    }

    // ── Öffentliche Schnittstelle ──────────────────────────────────────────

    /**
     * Setzt die darzustellende Wetterlage.
     * @param weatherCode WMO-Wettercode (Open-Meteo-Form)
     * @param isDay Tag/Nacht
     * @param cloudCoverPercent Bewölkung in % oder null, wenn nicht bekannt
     * @param windKmh Windgeschwindigkeit in km/h
     * @param windDirDeg Windrichtung in Grad (meteorologisch: Richtung, aus der der Wind weht)
     */
    fun setCondition(weatherCode: Int, isDay: Boolean, cloudCoverPercent: Int?, windKmh: Double, windDirDeg: Double?) {
        val next = buildScene(weatherCode, isDay, cloudCoverPercent, windKmh, windDirDeg)
        val c = smooth(fade)
        fromTop = lerpColor(fromTop, scene.skyTop, c)
        fromBottom = lerpColor(fromBottom, scene.skyBottom, c)
        scene = next
        fade = if (canAnimate()) 0f else 1f
        updateCloudShader()
        invalidate()
    }

    /** Nutzereinstellung: false = ruhiges Standbild ohne Bewegung. */
    fun setAnimationEnabled(enabled: Boolean) {
        userAnimated = enabled
        lastNanos = 0L
        invalidate()
    }

    fun onHostResume() {
        hostActive = true
        lastNanos = 0L
        invalidate()
    }

    fun onHostPause() {
        hostActive = false
        lastNanos = 0L
    }

    // ── Szene aufbauen ─────────────────────────────────────────────────────

    private fun buildScene(code: Int, isDay: Boolean, cloudCover: Int?, windKmh: Double, windDir: Double?): Scene {
        var kind = Kind.CLEAR
        var rain = 0f
        var snow = 0f
        var fog = 0f
        var lightning = false
        // Bewölkung: bei Niederschlag/Nebel fest, sonst nach Messwert (Fallback: nach Code)
        var cloudFrac = when (code) {
            0 -> 0.05f
            1 -> 0.2f
            2 -> 0.5f
            3 -> 1f
            else -> 0.9f
        }
        when (code) {
            0, 1, 2, 3 -> {
                if (cloudCover != null && cloudCover >= 0) cloudFrac = (cloudCover / 100f).coerceIn(0f, 1f)
                kind = if (cloudFrac >= 0.5f) Kind.CLOUD else Kind.CLEAR
            }
            45, 48 -> { kind = Kind.FOG; fog = 1f; cloudFrac = 0.5f }
            51 -> { kind = Kind.RAIN; rain = 0.2f }
            53 -> { kind = Kind.RAIN; rain = 0.3f }
            55, 56 -> { kind = Kind.RAIN; rain = 0.4f }
            57 -> { kind = Kind.RAIN; rain = 0.45f }
            61 -> { kind = Kind.RAIN; rain = 0.5f }
            63, 66 -> { kind = Kind.RAIN; rain = 0.7f }
            65, 67 -> { kind = Kind.RAIN; rain = 1f }
            80 -> { kind = Kind.RAIN; rain = 0.45f; cloudFrac = 0.8f }
            81 -> { kind = Kind.RAIN; rain = 0.7f; cloudFrac = 0.85f }
            82 -> { kind = Kind.RAIN; rain = 1f }
            71, 77 -> { kind = Kind.SNOW; snow = 0.35f }
            73, 85 -> { kind = Kind.SNOW; snow = 0.6f }
            75, 86 -> { kind = Kind.SNOW; snow = 1f }
            95, 96, 99 -> { kind = Kind.STORM; rain = 0.9f; lightning = true; cloudFrac = 1f }
            else -> {
                if (cloudCover != null && cloudCover >= 0) cloudFrac = (cloudCover / 100f).coerceIn(0f, 1f)
                kind = if (cloudFrac >= 0.5f) Kind.CLOUD else Kind.CLEAR
            }
        }

        // Himmelsfarben (bewusst gedämpft und dunkel, passend zum Glas-Design)
        val clearDay = 0xFF17304D.toInt() to 0xFF2D4C70.toInt()
        val cloudDay = 0xFF1F2B39.toInt() to 0xFF3A4857.toInt()
        var (top, bottom) = when (kind) {
            Kind.CLEAR, Kind.CLOUD -> {
                val t = (cloudFrac * 1.2f).coerceIn(0f, 1f)
                lerpColor(clearDay.first, cloudDay.first, t) to lerpColor(clearDay.second, cloudDay.second, t)
            }
            Kind.FOG -> 0xFF252E38.toInt() to 0xFF454F59.toInt()
            Kind.RAIN -> 0xFF17202B.toInt() to 0xFF2E3A48.toInt()
            Kind.SNOW -> 0xFF202B39.toInt() to 0xFF3E4C5C.toInt()
            Kind.STORM -> 0xFF10141D.toInt() to 0xFF252E3B.toInt()
        }
        if (!isDay) {
            if (kind == Kind.CLEAR || kind == Kind.CLOUD) {
                val t = (cloudFrac * 1.2f).coerceIn(0f, 1f)
                val nightClear = 0xFF070B16.toInt() to 0xFF16233F.toInt()
                val nightCloud = lerpColor(cloudDay.first, 0xFF03060B.toInt(), 0.5f) to
                        lerpColor(cloudDay.second, 0xFF03060B.toInt(), 0.5f)
                top = lerpColor(nightClear.first, nightCloud.first, t)
                bottom = lerpColor(nightClear.second, nightCloud.second, t)
            } else {
                top = lerpColor(top, 0xFF03060B.toInt(), 0.5f)
                bottom = lerpColor(bottom, 0xFF03060B.toInt(), 0.5f)
            }
        }

        var tint = when (kind) {
            Kind.CLEAR, Kind.CLOUD -> 0xFFAFC0D4.toInt()
            Kind.FOG -> 0xFFB4BEC8.toInt()
            Kind.RAIN -> 0xFF7C8A9C.toInt()
            Kind.SNOW -> 0xFFC4CFDC.toInt()
            Kind.STORM -> 0xFF5E6978.toInt()
        }
        if (!isDay) tint = lerpColor(tint, Color.BLACK, 0.45f)

        // Wind: Neigung des Niederschlags und Wolkenrichtung. Richtung = Herkunft, Bewegung zeigt in die Gegenrichtung.
        val dirRad = Math.toRadians(windDir ?: 270.0)
        var sign = (-sin(dirRad)).toFloat()
        if (abs(sign) < 0.15f) sign = if (sign < 0f) -0.15f else 0.15f
        val windFrac = (windKmh / 50.0).coerceIn(0.0, 1.0).toFloat()
        val slant = sign * windFrac * 0.55f
        val drift = sign * (0.008f + 0.03f * windFrac)

        val clearish = cloudFrac < 0.6f && kind == Kind.CLEAR
        return Scene(
            skyTop = top, skyBottom = bottom,
            cloudFrac = cloudFrac, cloudTint = tint,
            fog = fog, rain = rain, snow = snow,
            sun = isDay && clearish,
            moon = !isDay && clearish,
            stars = if (!isDay) (1f - cloudFrac).coerceIn(0f, 1f) else 0f,
            lightning = lightning,
            slant = slant, drift = drift
        )
    }

    private fun updateCloudShader() {
        if (cloudShaderTint == scene.cloudTint && cloudPaint.shader != null) return
        cloudShaderTint = scene.cloudTint
        val t = scene.cloudTint
        cloudPaint.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(t, (t and 0x00FFFFFF) or 0x99000000.toInt(), t and 0x00FFFFFF),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // ── Steuerung der Animation ────────────────────────────────────────────

    private fun systemAnimationsOn(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            try {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
            } catch (e: Exception) { true }
        }

    private fun canAnimate(): Boolean = hostActive && userAnimated && systemAnimationsOn() && isShown

    // ── Zeichnen ───────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val animate = canAnimate()
        val now = System.nanoTime()
        val dt = if (animate && lastNanos != 0L) ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f) else 0f
        lastNanos = if (animate) now else 0L
        if (animate) {
            timeSec += dt
            if (timeSec > 100000f) timeSec = 0f
        }
        if (!animate) fade = 1f else if (fade < 1f) fade = (fade + dt / 0.9f).coerceAtMost(1f)
        val c = smooth(fade)

        update(dt, animate)

        drawSky(canvas, w, h, c)
        drawStars(canvas, w, h, c, animate)
        drawSunMoon(canvas, w, h, c, animate)
        drawClouds(canvas, w, h, c)
        drawFog(canvas, w, h, c)
        if (animate) {
            drawRain(canvas, w, h, c)
            drawSnow(canvas, w, h, c)
        }
        drawFlash(canvas, w, h)
        drawScrim(canvas, w, h)

        if (animate) postInvalidateDelayed(FRAME_MS)
    }

    private fun update(dt: Float, animate: Boolean) {
        if (!animate || dt <= 0f) return
        val s = scene
        val aspect = if (width > 0) height.toFloat() / width else 1f

        for (i in 0 until MAX_CLOUDS) {
            var x = cloudX[i] + s.drift * cloudSp[i] * dt
            if (x > 1.4f) x -= 1.8f else if (x < -0.4f) x += 1.8f
            cloudX[i] = x
        }
        for (i in 0 until FOG_LAYERS) {
            var x = fogX[i] + s.drift * 0.6f * fogSp[i] * dt
            if (x > 1.2f) x -= 1.4f else if (x < -0.2f) x += 1.4f
            fogX[i] = x
        }
        if (s.rain > 0f) {
            val n = rainCount()
            val fall = 1.0f + 0.9f * s.rain
            for (i in 0 until n) {
                var y = rainY[i] + fall * rainSp[i] * dt
                var x = rainX[i] + s.slant * fall * rainSp[i] * dt * aspect
                if (y > 1.05f) { y -= 1.1f; x = rnd.nextFloat() }
                if (x > 1.05f) x -= 1.1f else if (x < -0.05f) x += 1.1f
                rainY[i] = y
                rainX[i] = x
            }
        }
        if (s.snow > 0f) {
            val n = snowCount()
            val fall = 0.05f + 0.07f * s.snow
            for (i in 0 until n) {
                var y = snowY[i] + fall * snowSp[i] * dt
                var x = snowX[i] + s.slant * 0.12f * snowSp[i] * dt
                if (y > 1.03f) { y -= 1.06f; x = rnd.nextFloat() }
                if (x > 1.05f) x -= 1.1f else if (x < -0.05f) x += 1.1f
                snowY[i] = y
                snowX[i] = x
            }
        }
        if (s.lightning) {
            if (flashT >= 0f) {
                flashT += dt
                if (flashT > 0.30f) flashT = -1f
            } else {
                nextFlashIn -= dt
                if (nextFlashIn <= 0f) {
                    flashT = 0f
                    nextFlashIn = 4f + rnd.nextFloat() * 7f
                }
            }
        } else {
            flashT = -1f
        }
    }

    private fun rainCount() = (MAX_RAIN * scene.rain).toInt().coerceIn(if (scene.rain > 0f) 25 else 0, MAX_RAIN)
    private fun snowCount() = (MAX_SNOW * scene.snow).toInt().coerceIn(if (scene.snow > 0f) 20 else 0, MAX_SNOW)

    private fun drawSky(canvas: Canvas, w: Float, h: Float, c: Float) {
        val top = lerpColor(fromTop, scene.skyTop, c)
        val bottom = lerpColor(fromBottom, scene.skyBottom, c)
        if (skyShader == null || top != skyKeyTop || bottom != skyKeyBottom || h != skyKeyH) {
            skyShader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
            skyKeyTop = top; skyKeyBottom = bottom; skyKeyH = h
            skyPaint.shader = skyShader
        }
        canvas.drawRect(0f, 0f, w, h, skyPaint)
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float, c: Float, animate: Boolean) {
        val vis = scene.stars * c
        if (vis <= 0.02f) return
        var a = 0
        var b = 0
        for (i in 0 until MAX_STARS) {
            if (i and 1 == 0) { starPts[0][a++] = starX[i] * w; starPts[0][a++] = starY[i] * h }
            else { starPts[1][b++] = starX[i] * w; starPts[1][b++] = starY[i] * h }
        }
        val t = if (animate) timeSec else 0f
        val alphaA = 0.55f + 0.45f * sin(t * 1.1f + starPhase[0])
        val alphaB = 0.55f + 0.45f * sin(t * 0.8f + starPhase[1])
        starPaints[0].alpha = (200 * vis * alphaA).toInt().coerceIn(0, 255)
        starPaints[1].alpha = (200 * vis * alphaB).toInt().coerceIn(0, 255)
        canvas.drawPoints(starPts[0], 0, a, starPaints[0])
        canvas.drawPoints(starPts[1], 0, b, starPaints[1])
    }

    private fun drawSunMoon(canvas: Canvas, w: Float, h: Float, c: Float, animate: Boolean) {
        if (!scene.sun && !scene.moon) return
        val cx = w * 0.80f
        val cy = h * 0.13f + 24f * dp
        val vis = c * (1f - scene.cloudFrac * 0.7f)
        val pulse = if (animate) 0.9f + 0.1f * sin(timeSec * 0.6f) else 1f
        val glowColor = if (scene.sun) 0xFFFFC870.toInt() else 0xFFA9C4FF.toInt()
        val glowAlpha = if (scene.sun) 0x66 else 0x30
        glowPaint.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf((glowColor and 0x00FFFFFF) or (glowAlpha shl 24), glowColor and 0x00FFFFFF),
            null, Shader.TileMode.CLAMP
        )
        glowPaint.alpha = (255 * vis * pulse).toInt().coerceIn(0, 255)
        val r = w * (if (scene.sun) 0.55f else 0.38f) * pulse
        canvas.save(); canvas.translate(cx, cy); canvas.scale(r, r)
        canvas.drawCircle(0f, 0f, 1f, glowPaint)
        canvas.restore()

        corePaint.color = if (scene.sun) 0xFFFFE2A8.toInt() else 0xFFE8EEF8.toInt()
        corePaint.alpha = ((if (scene.sun) 150 else 170) * vis).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, w * (if (scene.sun) 0.05f else 0.032f), corePaint)
    }

    private fun drawBlob(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(rx, ry)
        canvas.drawCircle(0f, 0f, 1f, cloudPaint)
        canvas.restore()
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, c: Float) {
        val frac = scene.cloudFrac
        if (frac < 0.04f) return
        updateCloudShader()
        val n = ceil(frac * MAX_CLOUDS).toInt().coerceIn(1, MAX_CLOUDS)
        cloudPaint.alpha = ((35 + 70 * frac) * c).toInt().coerceIn(0, 255)
        for (i in 0 until n) {
            val x = cloudX[i] * w
            val y = cloudY[i] * h
            val cw = cloudW[i] * w
            drawBlob(canvas, x, y, cw, cw * 0.38f)
            drawBlob(canvas, x - cw * 0.55f, y + cw * 0.08f, cw * 0.70f, cw * 0.30f)
            drawBlob(canvas, x + cw * 0.60f, y + cw * 0.10f, cw * 0.65f, cw * 0.28f)
        }
    }

    private fun drawFog(canvas: Canvas, w: Float, h: Float, c: Float) {
        if (scene.fog <= 0f) return
        updateCloudShader()
        cloudPaint.alpha = (45 * scene.fog * c).toInt().coerceIn(0, 255)
        for (i in 0 until FOG_LAYERS) {
            drawBlob(canvas, fogX[i] * w, fogY[i] * h, w * 0.9f, h * 0.09f)
        }
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float, c: Float) {
        if (scene.rain <= 0f) return
        val n = rainCount()
        var f = 0
        var nr = 0
        for (i in 0 until n) {
            val px = rainX[i] * w
            val py = rainY[i] * h
            val len = (10f + 14f * rainD[i]) * dp
            val tx = px - scene.slant * len
            val ty = py - len
            if (i and 1 == 0) {
                rainFarPts[f++] = px; rainFarPts[f++] = py; rainFarPts[f++] = tx; rainFarPts[f++] = ty
            } else {
                rainNearPts[nr++] = px; rainNearPts[nr++] = py; rainNearPts[nr++] = tx; rainNearPts[nr++] = ty
            }
        }
        rainFarPaint.alpha = (70 * c).toInt().coerceIn(0, 255)
        rainNearPaint.alpha = (110 * c).toInt().coerceIn(0, 255)
        canvas.drawLines(rainFarPts, 0, f, rainFarPaint)
        canvas.drawLines(rainNearPts, 0, nr, rainNearPaint)
    }

    private fun drawSnow(canvas: Canvas, w: Float, h: Float, c: Float) {
        if (scene.snow <= 0f) return
        val n = snowCount()
        val cnt = snowCnt
        cnt[0] = 0; cnt[1] = 0; cnt[2] = 0
        val amp = 14f * dp
        for (i in 0 until n) {
            val g = i % 3
            val x = snowX[i] * w + sin(timeSec * snowFreq[i] + snowPhase[i]) * amp
            val pts = snowPts[g]
            pts[cnt[g]++] = x
            pts[cnt[g]++] = snowY[i] * h
        }
        for (g in 0 until 3) {
            snowPaints[g].alpha = (150 * c).toInt().coerceIn(0, 255)
            canvas.drawPoints(snowPts[g], 0, cnt[g], snowPaints[g])
        }
    }

    private fun drawFlash(canvas: Canvas, w: Float, h: Float) {
        if (flashT < 0f) return
        val a = when {
            flashT < 0.07f -> 1f - flashT / 0.07f * 0.4f
            flashT < 0.16f -> 0.1f
            flashT < 0.30f -> 0.7f * (1f - (flashT - 0.16f) / 0.14f)
            else -> 0f
        }
        flashPaint.alpha = (255 * 0.28f * a).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, flashPaint)
    }

    /** Feste Abdunklung nach unten, damit Kartentexte auch bei hellen Szenen lesbar bleiben. */
    private fun drawScrim(canvas: Canvas, w: Float, h: Float) {
        if (scrimKeyH != h) {
            scrimPaint.shader = LinearGradient(0f, 0f, 0f, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP)
            scrimKeyH = h
        }
        canvas.drawRect(0f, 0f, w, h, scrimPaint)
    }

    // ── Lebenszyklus ───────────────────────────────────────────────────────

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        lastNanos = 0L
        if (visibility == VISIBLE) invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lastNanos = 0L
    }

    // ── Hilfsfunktionen ────────────────────────────────────────────────────

    private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val ar = Color.alpha(a); val rr = Color.red(a); val gr = Color.green(a); val br = Color.blue(a)
        return Color.argb(
            (ar + (Color.alpha(b) - ar) * k).toInt(),
            (rr + (Color.red(b) - rr) * k).toInt(),
            (gr + (Color.green(b) - gr) * k).toInt(),
            (br + (Color.blue(b) - br) * k).toInt()
        )
    }

    private companion object {
        const val MAX_RAIN = 220
        const val MAX_SNOW = 140
        const val MAX_CLOUDS = 8
        const val FOG_LAYERS = 6
        const val MAX_STARS = 70
        const val FRAME_MS = 33L
    }
}
