package com.hundewildnis.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/**
 * Gemeinsame Zeichenlogik des Glas-Stils (Vorlage: MiriquidiLauncher, `GlassStyle.kt`):
 *  - Glanz: oben 12 % Weiß, ab 45 % der Höhe transparent, unten 12 % Schwarz.
 *  - Randverlauf: oben kräftig, nach unten auf ca. 20 % (neutral) bzw. 45 % (Rollenfarbe) abnehmend,
 *    als fiele das Licht von oben auf die Kante.
 * Die Füllung selbst (neutrales Glas-Grau) kommt weiterhin vom Hintergrund der View.
 */
internal class GlassPainter {

    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    private var sheenHeight = -1
    private var rimHeight = -1
    private var rimColor = 0

    fun drawSheen(canvas: Canvas, width: Int, height: Int, radius: Float) {
        if (width <= 0 || height <= 0) return
        if (sheenHeight != height) {
            sheenHeight = height
            sheenPaint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(SHEEN_TOP, SHEEN_TOP_FADE, SHEEN_BOTTOM_FADE, SHEEN_BOTTOM),
                floatArrayOf(0f, 0.45f, 0.46f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        val r = radius.coerceAtMost(minOf(width, height) / 2f)
        canvas.drawRoundRect(rect, r, r, sheenPaint)
    }

    /** [color] ist die Randfarbe der View (Rollenfarbe oder weiß/transparent = neutral). */
    fun drawRim(canvas: Canvas, width: Int, height: Int, radius: Float, color: Int, strokePx: Int, minNeutralPx: Int) {
        if (width <= 0 || height <= 0 || strokePx <= 0 || Color.alpha(color) == 0) return
        val neutral = isNeutral(color)
        val w = if (neutral) maxOf(strokePx, minNeutralPx) else strokePx
        if (rimHeight != height || rimColor != color) {
            rimHeight = height
            rimColor = color
            val top: Int
            val bottom: Int
            if (neutral) {
                top = 0x99FFFFFF.toInt()
                bottom = 0x33FFFFFF
            } else {
                top = color
                bottom = Color.argb((Color.alpha(color) * 0.45f).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            }
            rimPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        rimPaint.strokeWidth = w.toFloat()
        val half = w / 2f
        rect.set(half, half, width - half, height - half)
        val r = (radius - half).coerceAtLeast(0f).coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
        canvas.drawRoundRect(rect, r, r, rimPaint)
    }

    companion object {
        private val SHEEN_TOP = Color.argb(31, 255, 255, 255)        // 12 %
        private val SHEEN_TOP_FADE = Color.argb(0, 255, 255, 255)
        private val SHEEN_BOTTOM_FADE = Color.argb(0, 0, 0, 0)
        private val SHEEN_BOTTOM = Color.argb(31, 0, 0, 0)           // 12 %

        /** Weiße oder (fast) transparente Randfarben gelten als neutral (kein Rollenrand). */
        fun isNeutral(color: Int) =
            Color.red(color) == 255 && Color.green(color) == 255 && Color.blue(color) == 255
    }
}
