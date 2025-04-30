package com.ai.assistance.operit.ui.common.displays

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.Log
import android.widget.TextView
import org.scilab.forge.jlatexmath.ParseException
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Utility class for rendering LaTeX expressions in text.
 * Handles detection, parsing, and rendering of LaTeX formulas.
 */
object LatexRenderer {
    
    /**
     * Renders LaTeX expressions within rendered Markdown content.
     * Uses single character traversal instead of regex for better performance.
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
        var markdownLinkStart = -1
        var markdownLinkDepth = 0
        
        // Single-pass identification of all content
        while (i < text.length) {
            val c = text[i]
            
            // Handle code blocks
            if (i + 2 < text.length && c == '`' && text[i+1] == '`' && text[i+2] == '`') {
                isInsideCodeBlock = !isInsideCodeBlock
                i += 3
                continue
            }
            
            // Handle inline code
            if (!isInsideCodeBlock && c == '`') {
                isInsideInlineCode = !isInsideInlineCode
                i++
                continue
            }
            
            // Inside code block, skip processing
            if (isInsideCodeBlock || isInsideInlineCode) {
                i++
                continue
            }
            
            // Handle Markdown links, avoid identifying LaTeX inside links
            if (!isInsideMarkdownLink) {
                // Detect link start
                if (c == '[') {
                    // Record possible link start position
                    markdownLinkStart = i
                    
                    // Find corresponding close bracket
                    var j = i + 1
                    var bracketDepth = 1
                    var foundClosingBracket = false
                    
                    while (j < text.length && bracketDepth > 0) {
                        when (text[j]) {
                            '[' -> bracketDepth++
                            ']' -> {
                                bracketDepth--
                                if (bracketDepth == 0) {
                                    // If followed by (, confirm as Markdown link
                                    if (j + 1 < text.length && text[j + 1] == '(') {
                                        foundClosingBracket = true
                                        isInsideMarkdownLink = true
                                        markdownLinkDepth = 0
                                        i = j + 2 // Skip ](
                                        break
                                    }
                                }
                            }
                        }
                        j++
                    }
                    
                    if (foundClosingBracket) {
                        continue
                    }
                }
                
                // Detect various LaTeX delimiters
                when {
                    // $$...$$
                    i + 1 < text.length && c == '$' && text[i+1] == '$' -> {
                        val startPos = i
                        i += 2 // Skip start delimiter
                        
                        // Find corresponding end delimiter
                        while (i + 1 < text.length) {
                            if (text[i] == '$' && text[i+1] == '$') {
                                // Found end delimiter, record LaTeX block
                                foundLatexBlocks.add(
                                    LatexDelimiterInfo(
                                        startPos, i + 2,
                                        "$$", "$$", true
                                    )
                                )
                                i += 2 // Skip end delimiter
                                break
                            }
                            i++
                        }
                        continue
                    }
                    
                    // $...$
                    c == '$' -> {
                        val startPos = i
                        i++ // Skip start delimiter
                        
                        // Find corresponding end delimiter
                        while (i < text.length) {
                            if (text[i] == '$') {
                                // Found end delimiter, extract content and check if it's really LaTeX
                                val content = text.substring(startPos + 1, i)
                                if (content.isNotBlank()) {
                                    foundLatexBlocks.add(
                                        LatexDelimiterInfo(
                                            startPos, i + 1,
                                            "$", "$", false
                                        )
                                    )
                                }
                                i++ // Skip end delimiter
                                break
                            }
                            i++
                        }
                        continue
                    }
                    
                    // \[...\]
                    i + 1 < text.length && c == '\\' && text[i+1] == '[' -> {
                        val startPos = i
                        i += 2 // Skip start delimiter
                        
                        // Find corresponding end delimiter
                        while (i + 1 < text.length) {
                            if (text[i] == '\\' && text[i+1] == ']') {
                                foundLatexBlocks.add(
                                    LatexDelimiterInfo(
                                        startPos, i + 2,
                                        "\\[", "\\]", true
                                    )
                                )
                                i += 2 // Skip end delimiter
                                break
                            }
                            i++
                        }
                        continue
                    }
                    
                    // \(...\)
                    i + 1 < text.length && c == '\\' && text[i+1] == '(' -> {
                        val startPos = i
                        i += 2 // Skip start delimiter
                        
                        // Find corresponding end delimiter
                        while (i + 1 < text.length) {
                            if (text[i] == '\\' && text[i+1] == ')') {
                                foundLatexBlocks.add(
                                    LatexDelimiterInfo(
                                        startPos, i + 2,
                                        "\\(", "\\)", false
                                    )
                                )
                                i += 2 // Skip end delimiter
                                break
                            }
                            i++
                        }
                        continue
                    }
                    
                    // (...) - might be LaTeX expression
                    c == '(' -> {
                        // Check if might be LaTeX expression
                        val startPos = i
                        var isLikelyLatex = false
                        
                        // Look at first 30 characters inside parentheses to determine if might be LaTeX
                        var peekEndPos = Math.min(i + 30, text.length)
                        var parenDepth = 1
                        var closingParenPos = -1
                        
                        // First find possible closing parenthesis position
                        for (j in i + 1 until peekEndPos) {
                            when (text[j]) {
                                '(' -> parenDepth++
                                ')' -> {
                                    parenDepth--
                                    if (parenDepth == 0) {
                                        closingParenPos = j
                                        // Adjust preview end position
                                        peekEndPos = closingParenPos
                                        break
                                    }
                                }
                            }
                        }
                        
                        if (closingParenPos != -1) {
                            // Get content inside parentheses for checking
                            val previewContent = text.substring(i + 1, peekEndPos)
                            
                            // Check if it contains LaTeX-related features
                            isLikelyLatex = previewContent.contains("\\") ||  // LaTeX commands
                                            Regex("[a-z]_[0-9a-zA-Z]").containsMatchIn(previewContent) ||  // Subscripts
                                            Regex("[a-z]\\^[0-9a-zA-Z]").containsMatchIn(previewContent) || // Superscripts
                                            (previewContent.contains("{") && previewContent.contains("}")) || // Braces
                                            (previewContent.contains("^") || previewContent.contains("_")) // Sub/superscript symbols
                            
                            // If it contains common math symbol patterns, more likely to be LaTeX
                            if (previewContent.contains("=") || 
                                previewContent.contains("+") || 
                                previewContent.contains("-") ||
                                Regex("[a-zA-Z]'").containsMatchIn(previewContent)) { // Derivative symbol
                                
                                // Extra check: for short formulas, if there's only one equals sign and alphanumerics, might be an assignment rather than LaTeX
                                val isSimpleAssignment = previewContent.length < 5 && 
                                                       previewContent.count { it == '=' } == 1 &&
                                                       !previewContent.contains("\\") &&
                                                       !previewContent.contains("^") &&
                                                       !previewContent.contains("_")
                                                        
                                if (!isSimpleAssignment) {
                                    isLikelyLatex = true
                                }
                            }
                            
                            // If it contains fraction form, likely LaTeX
                            if (previewContent.contains("/") && 
                                !previewContent.contains("//") && 
                                !previewContent.contains("http")) {
                                isLikelyLatex = true
                            }
                            
                            // If it contains consecutive spaces, not likely LaTeX
                            if (previewContent.contains("  ") || previewContent.contains(": ")) {
                                isLikelyLatex = false
                            }
                            
                            // Determine if likely LaTeX based on subsequent characters
                            val nextChar = text[i + 1]
                            val mightBeLaTeXByFirstChar = nextChar == '\\' || nextChar == '{' || 
                                                          "^_{}\\".contains(nextChar) ||
                                                          (nextChar.isLetter() && nextChar.isLowerCase())
                            
                            if (isLikelyLatex || mightBeLaTeXByFirstChar) {
                                i++ // Skip start delimiter
                                
                                // Find corresponding close parenthesis, considering nesting
                                var depth = 1
                                
                                while (i < text.length && depth > 0) {
                                    when (text[i]) {
                                        '(' -> depth++
                                        ')' -> {
                                            depth--
                                            if (depth == 0) {
                                                // Final confirmation of content as LaTeX
                                                val parenContent = text.substring(startPos + 1, i)
                                                if (LatexDetector.isLikelyLatexContent(parenContent)) {
                                                    foundLatexBlocks.add(
                                                        LatexDelimiterInfo(
                                                            startPos, i + 1,
                                                            "(", ")", false
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    i++
                                }
                                continue
                            }
                        }
                    }
                    
                    // [...] - special format, might be LaTeX or other syntax
                    c == '[' -> {
                        val startPos = i
                        i++ // Skip start delimiter
                        
                        // Determine if might be part of a link
                        var depth = 1
                        var isLikelyLink = false
                        var closingBracketPos = -1
                        
                        // Find corresponding close bracket
                        while (i < text.length && depth > 0) {
                            when (text[i]) {
                                '[' -> depth++
                                ']' -> {
                                    depth--
                                    if (depth == 0) {
                                        closingBracketPos = i
                                        // Check if followed by (, if so might be a link
                                        if (i + 1 < text.length && text[i+1] == '(') {
                                            isLikelyLink = true
                                        }
                                    }
                                }
                            }
                            i++
                        }
                        
                        // If not a link, check if might be LaTeX
                        if (!isLikelyLink && closingBracketPos != -1) {
                            val bracketContent = text.substring(startPos + 1, closingBracketPos)
                            if (LatexDetector.isLikelyLatexContent(bracketContent)) {
                                foundLatexBlocks.add(
                                    LatexDelimiterInfo(
                                        startPos, closingBracketPos + 1,
                                        "[", "]", false
                                    )
                                )
                            }
                        }
                        continue
                    }
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
            
            // Extract LaTeX expression
            val expr = text.substring(start, end)
            
            try {
                // Get formula content (remove delimiters)
                val formulaContent = text.substring(
                    start + block.startDelimiter.length,
                    end - block.endDelimiter.length
                )
                
                // Extra safety check: ensure formula content is valid
                val cleanedFormula = LatexCleaner.balanceLeftRightCommands(formulaContent.trim())
                
                // Create LaTeX renderer
                val builder = JLatexMathDrawable.builder(cleanedFormula)
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
                
                val drawable = builder.build()
                
                // Set drawing bounds
                drawable.setBounds(
                    0, 
                    0, 
                    drawable.intrinsicWidth, 
                    drawable.intrinsicHeight
                )
                
                // Replace text with LaTeX drawn Span
                spannableContent.setSpan(
                    LatexDrawableSpan(drawable, block.isBlockFormula),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                // Mark range as processed
                processedRanges.add(range)
                
                // Log
                Log.d("MarkdownLatex", "Rendered LaTeX formula: '${cleanedFormula.take(20)}${if (cleanedFormula.length > 20) "..." else ""}'")
                
            } catch (e: ParseException) {
                // Special handling for ParseException - try to fix formula and re-render
                try {
                    Log.w("MarkdownLatex", "LaTeX parse error: ${e.message} Attempting to fix formula...")
                    
                    // Extract original formula content
                    val originalContent = text.substring(
                        start + block.startDelimiter.length,
                        end - block.endDelimiter.length
                    )
                    
                    // Try more aggressive repair methods
                    val fixedContent = LatexCleaner.repairLatexExpression(originalContent, e.message ?: "")
                    
                    // Create fixed LaTeX renderer
                    val builder = JLatexMathDrawable.builder(fixedContent)
                        .textSize(textSize)
                        .color(textColor)
                    
                    if (block.isBlockFormula) {
                        builder.padding(12)
                            .background(0x10000000)
                            .align(JLatexMathDrawable.ALIGN_CENTER)
                    } else {
                        builder.padding(4)
                    }
                    
                    val drawable = builder.build()
                    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                    
                    // Replace text with fixed LaTeX Span
                    spannableContent.setSpan(
                        LatexDrawableSpan(drawable, block.isBlockFormula),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    // Mark range as processed
                    processedRanges.add(range)
                    
                    Log.d("MarkdownLatex", "Successfully fixed and rendered LaTeX formula: '$fixedContent'")
                    
                } catch (e2: Exception) {
                    // If fix also fails, log error and continue with other expressions
                    Log.e("MarkdownLatex", "Failed to fix LaTeX: ${e2.message}", e2)
                    
                    // Show original text instead of rendered LaTeX
                    // No operation here, will keep original text
                }
            } catch (e: Exception) {
                // Handle other error types
                Log.e("MarkdownLatex", "Error rendering LaTeX: ${e.message} in '${expr.take(30)}...'", e)
                // Keep original text
            }
        }
        
        return spannableContent
    }
} 