package com.ai.assistance.operit.ui.common.displays

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ReplacementSpan

/**
 * Custom span for displaying LaTeX formulas as drawables.
 * Handles both inline and block formulas with different layout strategies.
 */
class LatexDrawableSpan(
    private val drawable: Drawable, 
    private val isBlockFormula: Boolean = false
) : ReplacementSpan() {
    
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val rect = drawable.bounds
        
        if (fm != null) {
            val fontHeight = fm.descent - fm.ascent
            val imageHeight = rect.height()
            
            if (isBlockFormula) {
                // Block formula uses full height and adds space above/below
                fm.ascent = -imageHeight
                fm.descent = 0
                fm.top = fm.ascent
                fm.bottom = fm.descent
            } else {
                // Inline formula vertically centered
                val centerY = fm.ascent + fontHeight / 2
                fm.ascent = centerY - imageHeight / 2
                fm.descent = centerY + imageHeight / 2
                fm.top = fm.ascent
                fm.bottom = fm.descent
            }
        }
        
        return rect.width()
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
            val imageHeight = drawable.bounds.height()
            val transY = centerY - imageHeight / 2
            
            canvas.translate(x, transY.toFloat()) // Ensure transY is Float type
        }
        
        drawable.draw(canvas)
        canvas.restore()
    }
} 