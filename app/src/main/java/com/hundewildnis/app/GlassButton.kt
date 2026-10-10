package com.hundewildnis.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

/**
 * MaterialButton im Glas-Stil: Glanzverlauf unter dem Text und Randverlauf (hell von oben) in der Rollenfarbe.
 * strokeColor/strokeWidth/backgroundTint werden wie bisher gesetzt. Buttons ohne Füllung (Text-Buttons) bekommen
 * keinen Glanz, Buttons ohne Rand keinen Randverlauf.
 */
class GlassButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    private val painter = GlassPainter()
    private val minNeutralPx = (1f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private var rimColors: ColorStateList? = null
    private var rimWidthPx = 0
    private var ready = false

    init {
        rimWidthPx = super.getStrokeWidth()
        rimColors = super.getStrokeColor()
        super.setStrokeWidth(0)
        ready = true
    }

    override fun setStrokeWidth(strokeWidth: Int) {
        if (!ready) { super.setStrokeWidth(strokeWidth); return }
        rimWidthPx = strokeWidth
        invalidate()
    }

    override fun setStrokeColor(strokeColor: ColorStateList?) {
        if (!ready) { super.setStrokeColor(strokeColor); return }
        rimColors = strokeColor
        invalidate()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    private fun cornerPx(): Float {
        val c = cornerRadius
        return if (c >= 0) c.toFloat() else height / 2f
    }

    private fun hasFill(): Boolean {
        val tint = backgroundTintList ?: return false
        return Color.alpha(tint.getColorForState(drawableState, tint.defaultColor)) > 0
    }

    override fun onDraw(canvas: Canvas) {
        if (hasFill()) painter.drawSheen(canvas, width, height, cornerPx())
        super.onDraw(canvas)
        val colors = rimColors
        if (colors != null && rimWidthPx > 0) {
            val color = colors.getColorForState(drawableState, colors.defaultColor)
            painter.drawRim(canvas, width, height, cornerPx(), color, rimWidthPx, minNeutralPx)
        }
    }
}
