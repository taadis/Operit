package com.ai.assistance.operit.ui.common.displays

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.Log
import android.widget.TextView
import java.util.concurrent.Executors
import org.scilab.forge.jlatexmath.ParseException
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Utility class for rendering LaTeX expressions in text. Handles detection, parsing, and rendering
 * of LaTeX formulas.
 */
object LatexRenderer {
    // 使用线程池进行异步渲染
    private val renderExecutor = Executors.newFixedThreadPool(2)

    // 是否使用位图缓存渲染（可配置）
    private var useBitmapRendering = true

    /** 设置是否使用位图渲染 */
    fun setUseBitmapRendering(enabled: Boolean) {
        useBitmapRendering = enabled
        Log.d("LatexRenderer", "Bitmap rendering ${if(enabled) "enabled" else "disabled"}")
    }

    /**
     * Renders LaTeX expressions within rendered Markdown content. Uses single character traversal
     * instead of regex for better performance.
     */
    fun renderLatexInMarkdown(
            markdownContent: CharSequence,
            textView: TextView,
            @androidx.annotation.ColorInt textColor: Int,
            textSize: Float
    ): Spannable {
        val text = markdownContent.toString()
        val spannableContent = SpannableStringBuilder(markdownContent)

        // Track LaTeX delimiter structure
        data class LatexDelimiterInfo(
                val startIndex: Int,
                val endIndex: Int,
                val startDelimiter: String,
                val endDelimiter: String,
                val isBlockFormula: Boolean
        )

        // Save all found LaTeX delimiters
        val foundLatexBlocks = mutableListOf<LatexDelimiterInfo>()

        // Set of processed ranges to prevent duplicate processing
        val processedRanges = mutableSetOf<IntRange>()

        // State tracking variables
        var i = 0
        var isInsideCodeBlock = false
        var isInsideInlineCode = false
        var isInsideMarkdownLink = false
        var isImageLink = false
        var markdownLinkStart = -1
        var markdownLinkDepth = 0

        // Single-pass identification of all content
        while (i < text.length) {
            val c = text[i]

            // Skip LaTeX detection inside code blocks
            if (i + 2 < text.length && c == '`' && text[i + 1] == '`' && text[i + 2] == '`') {
                isInsideCodeBlock = !isInsideCodeBlock
                i += 3
                continue
            }

            if (!isInsideCodeBlock && c == '`') {
                isInsideInlineCode = !isInsideInlineCode
                i++
                continue
            }

            // Inside code block, skip LaTeX detection
            if (isInsideCodeBlock || isInsideInlineCode) {
                i++
                continue
            }

            // Handle Markdown links and LaTeX blocks
            if (!isInsideMarkdownLink) {
                // First check for image links
                if (c == '!') {
                    if (i + 1 < text.length && text[i + 1] == '[') {
                        isImageLink = true
                        i += 2
                        continue
                    }
                    i++
                    continue
                }

                // Check for normal links
                if (c == '[') {
                    if (i > 0 && text[i - 1] == '!') {
                        // 已经处理过的图片标记，跳过
                        isImageLink = true
                    }
                    markdownLinkStart = i
                    isInsideMarkdownLink = true
                    i++
                    continue
                }

                // Detect LaTeX with dollar signs - ONLY dollar sign notation
                // 确保不是图片链接
                if (!isImageLink && i + 1 < text.length && c == '$' && text[i + 1] == '$') {
                    val startPos = i
                    i += 2 // Skip $$

                    // Find closing $$
                    while (i + 1 < text.length && !(text[i] == '$' && text[i + 1] == '$')) {
                        i++
                    }

                    if (i + 1 < text.length) {
                        // Found closing $$
                        foundLatexBlocks.add(LatexDelimiterInfo(startPos, i + 2, "$$", "$$", true))
                        i += 2 // Skip $$
                    } else {
                        // Unclosed block, treat as text
                        i = startPos + 2
                    }
                    continue
                }

                if (!isImageLink && c == '$') {
                    val startPos = i
                    i++ // Skip $

                    // Find closing $, making sure it's not escaped
                    while (i < text.length && text[i] != '$') {
                        i++
                    }

                    if (i < text.length) {
                        // Found closing $
                        foundLatexBlocks.add(LatexDelimiterInfo(startPos, i + 1, "$", "$", false))
                        i++ // Skip $
                    } else {
                        // Unclosed block, treat as text
                        i = startPos + 1
                    }
                    continue
                }
            } else {
                // Inside Markdown link, track parenthesis depth
                if (c == '(') {
                    markdownLinkDepth++
                } else if (c == ')') {
                    markdownLinkDepth--
                    if (markdownLinkDepth == 0) {
                        // Link ended
                        isInsideMarkdownLink = false
                        isImageLink = false
                    }
                }
            }

            i++
        }

        // Sort all found LaTeX blocks by start position
        val sortedBlocks = foundLatexBlocks.sortedBy { it.startIndex }

        // Process all found LaTeX blocks
        for (block in sortedBlocks) {
            val start = block.startIndex
            val end = block.endIndex
            val range = start until end

            // Check if this range or overlapping range already processed
            if (processedRanges.any { it.intersect(range).isNotEmpty() }) {
                continue
            }

            // Skip if this is an image URL
            if (start > 0 && text.substring(Math.max(0, start - 10), start).contains("![")) {
                continue
            }

            // Extract LaTeX expression
            val expr = text.substring(start, end)

            try {
                // Get formula content (remove delimiters)
                val formulaContent =
                        text.substring(
                                start + block.startDelimiter.length,
                                end - block.endDelimiter.length
                        )

                // Extra safety check: ensure formula content is valid
                val cleanedFormula = LatexCleaner.balanceLeftRightCommands(formulaContent.trim())

                // Create LaTeX renderer builder
                val builder =
                        JLatexMathDrawable.builder(cleanedFormula)
                                .textSize(textSize)
                                .color(textColor)

                // Add extra styling for block-level formulas
                if (block.isBlockFormula) {
                    builder.padding(12) // Block formulas use larger padding
                            .background(0x10000000) // Slight background color
                            .align(JLatexMathDrawable.ALIGN_CENTER) // Center alignment
                } else {
                    builder.padding(4) // Inline formulas use smaller padding
                }

                if (useBitmapRendering) {
                    // 使用位图缓存取代drawable
                    val bitmap = LatexCache.getLatexBitmap(cleanedFormula, builder)

                    // Replace text with LaTeX bitmap Span
                    spannableContent.setSpan(
                            LatexBitmapSpan(bitmap, block.isBlockFormula),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    // 传统的drawable渲染方式
                    // Get drawable from cache or create new one
                    val drawable = LatexCache.getDrawable(cleanedFormula, builder)

                    // Set drawing bounds
                    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

                    // Replace text with LaTeX drawn Span
                    spannableContent.setSpan(
                            LatexDrawableSpan(drawable, block.isBlockFormula),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                // Mark range as processed
                processedRanges.add(range)

                // Log
                Log.d(
                        "MarkdownLatex",
                        "Rendered LaTeX formula with ${if(useBitmapRendering) "bitmap" else "drawable"}: " +
                                "'${cleanedFormula.take(20)}${if (cleanedFormula.length > 20) "..." else ""}'"
                )
            } catch (e: ParseException) {
                // Special handling for ParseException - try to fix formula and re-render
                try {
                    Log.w(
                            "MarkdownLatex",
                            "LaTeX parse error: ${e.message} Attempting to fix formula..."
                    )

                    // Extract original formula content
                    val originalContent =
                            text.substring(
                                    start + block.startDelimiter.length,
                                    end - block.endDelimiter.length
                            )

                    // Try more aggressive repair methods
                    val fixedContent =
                            LatexCleaner.repairLatexExpression(originalContent, e.message ?: "")

                    // Create fixed LaTeX renderer builder
                    val builder =
                            JLatexMathDrawable.builder(fixedContent)
                                    .textSize(textSize)
                                    .color(textColor)

                    if (block.isBlockFormula) {
                        builder.padding(12)
                                .background(0x10000000)
                                .align(JLatexMathDrawable.ALIGN_CENTER)
                    } else {
                        builder.padding(4)
                    }

                    if (useBitmapRendering) {
                        // 使用位图缓存取代drawable
                        val bitmap = LatexCache.getLatexBitmap(fixedContent, builder)

                        // Replace text with LaTeX bitmap Span
                        spannableContent.setSpan(
                                LatexBitmapSpan(bitmap, block.isBlockFormula),
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else {
                        // 传统的drawable渲染方式
                        val drawable = LatexCache.getDrawable(fixedContent, builder)
                        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)

                        // Replace text with fixed LaTeX Span
                        spannableContent.setSpan(
                                LatexDrawableSpan(drawable, block.isBlockFormula),
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }

                    // Mark range as processed
                    processedRanges.add(range)

                    Log.d(
                            "MarkdownLatex",
                            "Successfully fixed and rendered LaTeX formula: '$fixedContent'"
                    )
                } catch (e2: Exception) {
                    // If fix also fails, log error and continue with other expressions
                    Log.e("MarkdownLatex", "Failed to fix LaTeX: ${e2.message}", e2)

                    // Show original text instead of rendered LaTeX
                    // No operation here, will keep original text
                }
            } catch (e: Exception) {
                // Handle other error types
                Log.e(
                        "MarkdownLatex",
                        "Error rendering LaTeX: ${e.message} in '${expr.take(30)}...'",
                        e
                )
                // Keep original text
            }
        }

        return spannableContent
    }
}
