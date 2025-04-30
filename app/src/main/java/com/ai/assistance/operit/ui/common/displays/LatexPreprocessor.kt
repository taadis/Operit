package com.ai.assistance.operit.ui.common.displays

/**
 * Utility class for preprocessing text with LaTeX expressions before rendering.
 * Handles detection and preparation of various LaTeX syntax patterns.
 */
object LatexPreprocessor {
    
    /**
     * Preprocesses content to ensure LaTeX expressions can be correctly rendered.
     * Uses a single character traversal to handle both Markdown links and LaTeX expressions.
     */
    fun preprocessLatexInMarkdown(content: String): String {
        val result = StringBuilder(content.length + 50) // Pre-allocate extra space
        
        // Store various states during processing
        var i = 0
        
        // Code block states
        var isInsideCodeBlock = false
        var isInsideInlineCode = false
        
        // LaTeX states
        var isInsideLatexBlock = false
        var latexStartType = "" // Records LaTeX start delimiter type: $$, $, \[, \(, [
        var latexStartPos = -1
        var latexContent = StringBuilder()
        
        // Markdown link states
        var isInsideMarkdownLink = false
        var squareBracketStartPos = -1
        var hasPotentialMdLink = false
        var linkDepth = 0
        var lastExclamationPos = -1
        
        // Temporary buffer for collecting potential Markdown link content
        val linkBuffer = StringBuilder()
        
        // Helper function: Process found LaTeX block
        fun processLatexBlock(startDelim: String, endDelim: String, content: String): String {
            // Check if it's really LaTeX based on delimiter type
            if ((startDelim == "[" && endDelim == "]") || (startDelim == "(" && endDelim == ")")) {
                if (!LatexDetector.isLikelyLatexContent(content)) {
                    return "$startDelim$content$endDelim" // Return unchanged
                }
            }
            
            // Clean and balance LaTeX content
            val cleanedContent = content
                .replace("\n", " ") // Replace newlines with spaces
                .replace("\\\\", " \\\\ ") // Ensure spaces around newline commands
                .replace(Regex("\\s+"), " ") // Replace multiple whitespace with single space
                .trim() // Remove leading and trailing whitespace
                .let { LatexCleaner.balanceLeftRightCommands(it) } // Balance \left and \right commands
            
            // Choose appropriate output format based on LaTeX type
            return when (startDelim) {
                "$$" -> "$$${cleanedContent}$$"
                "$" -> "$${cleanedContent}$"
                "\\[" -> "\\[${cleanedContent}\\]"
                "\\(" -> "\\(${cleanedContent}\\)" // Ensure using original backslash start delimiter
                "[" -> {
                    if (cleanedContent.contains("\\begin{cases}") || cleanedContent.contains("\\end{cases}")) {
                        "$$${cleanedContent}$$" // Use block formula for complex cases
                    } else {
                        "$${cleanedContent}$" // Use inline formula for simple expressions
                    }
                }
                "(" -> {
                    // Convert parentheses format to standard LaTeX using inline formula format
                    "\\(${cleanedContent}\\)" // Use \( \) format instead of $ $ format
                }
                else -> "$${cleanedContent}$" // Default to inline formula format
            }
        }
        
        // Single-pass processing of all content
        while (i < content.length) {
            val c = content[i]
            
            // Handle code blocks
            if (i + 2 < content.length && c == '`' && content[i+1] == '`' && content[i+2] == '`') {
                // If inside a LaTeX block, end and process LaTeX first
                if (isInsideLatexBlock) {
                    // Unclosed LaTeX block, add original content
                    result.append(content.substring(latexStartPos, i))
                    isInsideLatexBlock = false
                    latexStartType = ""
                    latexStartPos = -1
                    latexContent.clear()
                }
                
                isInsideCodeBlock = !isInsideCodeBlock
                result.append("```")
                i += 3
                continue
            }
            
            // Handle inline code
            if (!isInsideCodeBlock && c == '`') {
                // If inside a LaTeX block, end and process LaTeX first
                if (isInsideLatexBlock) {
                    // Unclosed LaTeX block, add original content
                    result.append(content.substring(latexStartPos, i))
                    isInsideLatexBlock = false
                    latexStartType = ""
                    latexStartPos = -1
                    latexContent.clear()
                }
                
                isInsideInlineCode = !isInsideInlineCode
                result.append('`')
                i++
                continue
            }
            
            // Inside code block, copy as is
            if (isInsideCodeBlock || isInsideInlineCode) {
                result.append(c)
                i++
                continue
            }
            
            // Handle Markdown links and LaTeX blocks
            if (!isInsideMarkdownLink && !isInsideLatexBlock) {
                // Detect start of Markdown link
                if (c == '!') {
                    lastExclamationPos = i
                    result.append(c)
                    i++
                    continue
                }
                
                if (c == '[') {
                    squareBracketStartPos = i
                    hasPotentialMdLink = (lastExclamationPos == i - 1)
                    isInsideMarkdownLink = true
                    linkBuffer.clear()
                    linkBuffer.append(c)
                    i++
                    continue
                }
                
                // Detect various LaTeX delimiters
                if (i + 1 < content.length && c == '$' && content[i+1] == '$') {
                    isInsideLatexBlock = true
                    latexStartType = "$$"
                    latexStartPos = i
                    latexContent.clear()
                    i += 2
                    continue
                }
                
                if (c == '$') {
                    isInsideLatexBlock = true
                    latexStartType = "$"
                    latexStartPos = i
                    latexContent.clear()
                    i++
                    continue
                }
                
                if (i + 1 < content.length && c == '\\' && content[i+1] == '[') {
                    isInsideLatexBlock = true
                    latexStartType = "\\["
                    latexStartPos = i
                    latexContent.clear()
                    i += 2
                    continue
                }
                
                if (i + 1 < content.length && c == '\\' && content[i+1] == '(') {
                    isInsideLatexBlock = true
                    latexStartType = "\\("
                    latexStartPos = i
                    latexContent.clear()
                    i += 2 // Skip \( two characters
                    continue
                }
                
                // Special case: [...] square brackets, could be LaTeX or other syntax
                if (c == '[' && !isInsideMarkdownLink) {
                    isInsideLatexBlock = true
                    latexStartType = "["
                    latexStartPos = i
                    latexContent.clear()
                    i++
                    continue
                }
                
                // Handle parentheses, which might be LaTeX
                if (c == '(' && i + 1 < content.length) {
                    val parenStartPos = i
                    var isLikelyLatex = false
                    
                    // Look at the first 30 characters inside the parentheses to determine if it might be LaTeX
                    var peekEndPos = Math.min(i + 30, content.length)
                    var parenDepth = 1
                    var closingParenPos = -1
                    
                    // First find possible closing parenthesis position
                    for (j in i + 1 until peekEndPos) {
                        when (content[j]) {
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
                        val previewContent = content.substring(i + 1, peekEndPos)
                        
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
                    }
                    
                    // Determine if likely LaTeX based on subsequent characters
                    val nextChar = content[i + 1]
                    val mightBeLaTeXByFirstChar = nextChar == '\\' || nextChar == '{' || 
                                                "^_{}\\".contains(nextChar) ||
                                                (nextChar.isLetter() && nextChar.isLowerCase())
                    
                    if (isLikelyLatex || mightBeLaTeXByFirstChar) {
                        isInsideLatexBlock = true
                        latexStartType = "("
                        latexStartPos = i
                        latexContent.clear()
                        i++
                        continue
                    }
                }
                
                // Regular character, add directly
                result.append(c)
                i++
                continue
            }
            
            // Handle Markdown link content
            if (isInsideMarkdownLink) {
                linkBuffer.append(c)
                
                // Detect link end
                if (c == ']') {
                    // Check if next character is '(', confirming as Markdown link
                    if (i + 1 < content.length && content[i+1] == '(') {
                        linkBuffer.append(content[i+1])
                        i += 2
                        linkDepth = 1
                        
                        // Continue collecting until matching closing parenthesis is found
                        while (i < content.length && linkDepth > 0) {
                            val nextChar = content[i]
                            linkBuffer.append(nextChar)
                            
                            if (nextChar == '(') linkDepth++
                            else if (nextChar == ')') linkDepth--
                            
                            i++
                        }
                        
                        // Link complete, add to result
                        result.append(linkBuffer.toString())
                        
                        // Reset link state
                        isInsideMarkdownLink = false
                        squareBracketStartPos = -1
                        hasPotentialMdLink = false
                        linkBuffer.clear()
                        continue
                    } else {
                        // Not a link, might be LaTeX or other syntax
                        // Reset state and add collected content
                        isInsideMarkdownLink = false
                        result.append(linkBuffer.toString())
                        linkBuffer.clear()
                        i++
                        continue
                    }
                }
                
                i++
                continue
            }
            
            // Handle LaTeX block content
            if (isInsideLatexBlock) {
                // Check if corresponding end delimiter is reached
                val endFound = when (latexStartType) {
                    "$$" -> i + 1 < content.length && c == '$' && content[i+1] == '$'
                    "$" -> c == '$'
                    "\\[" -> i + 1 < content.length && c == '\\' && content[i+1] == ']'
                    "\\(" -> i + 1 < content.length && c == '\\' && content[i+1] == ')'
                    "[" -> c == ']'
                    "(" -> c == ')'
                    else -> false
                }
                
                if (endFound) {
                    // Determine end method based on delimiter type
                    val endDelim = when (latexStartType) {
                        "$$" -> "$$"
                        "$" -> "$"
                        "\\[" -> "\\]"
                        "\\(" -> "\\)"
                        "[" -> "]"
                        "(" -> ")"
                        else -> ""
                    }
                    
                    // Process LaTeX content
                    val processedLatex = processLatexBlock(latexStartType, endDelim, latexContent.toString())
                    result.append(processedLatex)
                    
                    // Update index position
                    i = when (latexStartType) {
                        "$$", "\\[", "\\(" -> i + 2
                        else -> i + 1
                    }
                    
                    // Reset LaTeX state
                    isInsideLatexBlock = false
                    latexStartType = ""
                    latexStartPos = -1
                    latexContent.clear()
                    continue
                }
                
                // Inside LaTeX, collect content
                latexContent.append(c)
                i++
                continue
            }
        }
        
        // Handle any unclosed blocks
        if (isInsideLatexBlock && latexStartPos != -1) {
            // Add original content of unclosed LaTeX block
            result.append(content.substring(latexStartPos))
        }
        
        if (isInsideMarkdownLink && linkBuffer.isNotEmpty()) {
            // Add unclosed Markdown link
            result.append(linkBuffer.toString())
        }
        
        return result.toString()
    }
} 