package com.ai.assistance.operit.ui.common.displays

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/**
 * Custom span for displaying LaTeX formulas as bitmaps. Handles both inline and block formulas with
 * different layout strategies. Using bitmap instead of drawable improves performance by reducing
 * CPU calculations.
 */
class LatexBitmapSpan(private val bitmap: Bitmap, private val isBlockFormula: Boolean = false) :
        ReplacementSpan() {

    override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
    ): Int {
        val width = bitmap.width
        val height = bitmap.height

        if (fm != null) {
            val fontHeight = fm.descent - fm.ascent

            if (isBlockFormula) {
                // Block formula uses full height and adds space above/below
                fm.ascent = -height
                fm.descent = 0
                fm.top = fm.ascent
                fm.bottom = fm.descent
            } else {
                // Inline formula vertically centered
                val centerY = fm.ascent + fontHeight / 2
                fm.ascent = centerY - height / 2
                fm.descent = centerY + height / 2
                fm.top = fm.ascent
                fm.bottom = fm.descent
            }
        }

        return width
    }

    override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
    ) {
        canvas.save()

        if (isBlockFormula) {
            // Block formula drawn on its own line
            canvas.translate(x, top.toFloat())
        } else {
            // Inline formula vertically centered
            val fm = paint.fontMetricsInt
            val fontHeight = fm.descent - fm.ascent
            val centerY = y + fm.descent - fontHeight / 2
            val imageHeight = bitmap.height
            val transY = centerY - imageHeight / 2

            canvas.translate(x, transY.toFloat())
        }

        // Draw bitmap directly to canvas
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        canvas.restore()
    }
}
