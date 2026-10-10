package com.hundewildnis.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView

/**
 * MaterialCardView im Glas-Stil: Füllung wie bisher (cardBackgroundColor), zusätzlich Glanzverlauf und ein
 * Randverlauf (hell von oben). Rand-Farbe und -Stärke werden weiterhin über strokeColor/strokeWidth gesetzt
 * (XML und Code unverändert); die View zeichnet den Rand aber selbst mit Verlauf statt als einfarbige Linie.
 */
class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val painter = GlassPainter()
    private val minNeutralPx = (1f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private var rimColor = Color.TRANSPARENT
    private var rimWidthPx = 0
    private var ready = false

    init {
        // Vom Style/XML gesetzten Rand übernehmen und den einfarbigen Standardrand abschalten
        rimWidthPx = super.getStrokeWidth()
        rimColor = super.getStrokeColor()
        super.setStrokeWidth(0)
        ready = true
        setWillNotDraw(false)
    }

    override fun setStrokeWidth(strokeWidth: Int) {
        if (!ready) { super.setStrokeWidth(strokeWidth); return }
        rimWidthPx = strokeWidth
        invalidate()
    }

    override fun setStrokeColor(color: Int) {
        if (!ready) { super.setStrokeColor(color); return }
        rimColor = color
        invalidate()
    }

    override fun setStrokeColor(strokeColor: ColorStateList?) {
        if (!ready) { super.setStrokeColor(strokeColor); return }
        rimColor = strokeColor?.getColorForState(drawableState, strokeColor.defaultColor) ?: Color.TRANSPARENT
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        painter.drawSheen(canvas, width, height, radius)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        painter.drawRim(canvas, width, height, radius, rimColor, rimWidthPx, minNeutralPx)
    }
}
