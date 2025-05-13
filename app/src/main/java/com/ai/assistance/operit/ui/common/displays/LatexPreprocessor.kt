package com.ai.assistance.operit.ui.common.displays

/**
 * Utility class for preprocessing text with LaTeX expressions before rendering. Handles detection
 * and preparation of various LaTeX syntax patterns.
 */
object LatexPreprocessor {

    /**
     * Preprocesses content to ensure LaTeX expressions can be correctly rendered. Uses a single
     * character traversal to handle both Markdown links and LaTeX expressions.
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
        var latexStartType = "" // Records LaTeX start delimiter type: $$, $
        var latexStartPos = -1
        var latexContent = StringBuilder()

        // Markdown link states
        var isInsideMarkdownLink = false
        var isImageLink = false
        var squareBracketStartPos = -1
        var hasPotentialMdLink = false
        var linkDepth = 0
        var lastExclamationPos = -1

        // Temporary buffer for collecting potential Markdown link content
        val linkBuffer = StringBuilder()

        // Helper function: Process found LaTeX block
        fun processLatexBlock(startDelim: String, endDelim: String, content: String): String {
            // Clean and balance LaTeX content
            val cleanedContent =
                    content.replace("\n", " ") // Replace newlines with spaces
                            .replace("\\\\", " \\\\ ") // Ensure spaces around newline commands
                            .replace(
                                    Regex("\\s+"),
                                    " "
                            ) // Replace multiple whitespace with single space
                            .trim() // Remove leading and trailing whitespace
                            .let {
                                LatexCleaner.balanceLeftRightCommands(it)
                            } // Balance \left and \right commands

            // Return the processed LaTeX with original delimiters
            return startDelim + cleanedContent + endDelim
        }

        while (i < content.length) {
            val c = content[i]

            // Handle code blocks
            if (i + 2 < content.length && c == '`' && content[i + 1] == '`' && content[i + 2] == '`'
            ) {
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
                // Detect start of Markdown link or image
                if (c == '!') {
                    // 处理图片标记开始
                    lastExclamationPos = i
                    result.append(c)
                    
                    // 检查是否是图片Markdown语法 ![
                    if (i + 1 < content.length && content[i + 1] == '[') {
                        isImageLink = true
                    }
                    
                    i++
                    continue
                }

                if (c == '[') {
                    squareBracketStartPos = i
                    hasPotentialMdLink = (lastExclamationPos == i - 1)
                    isInsideMarkdownLink = true
                    isImageLink = hasPotentialMdLink
                    linkBuffer.clear()
                    linkBuffer.append('[')
                    i++
                    continue
                }

                // Detect various LaTeX delimiters - ONLY dollar sign notation
                // 确保不在图片Markdown标记内处理LaTeX
                if (!isImageLink && i + 1 < content.length && c == '$' && content[i + 1] == '$') {
                    isInsideLatexBlock = true
                    latexStartType = "$$"
                    latexStartPos = i
                    latexContent.clear()
                    i += 2
                    continue
                }

                if (!isImageLink && c == '$') {
                    isInsideLatexBlock = true
                    latexStartType = "$"
                    latexStartPos = i
                    latexContent.clear()
                    i++
                    continue
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
                    if (i + 1 < content.length && content[i + 1] == '(') {
                        linkBuffer.append(content[i + 1])
                        i += 2
                        linkDepth = 1

                        // Continue collecting until matching closing parenthesis is found
                        while (i < content.length && linkDepth > 0) {
                            val nextChar = content[i]
                            linkBuffer.append(nextChar)

                            if (nextChar == '(') linkDepth++ else if (nextChar == ')') linkDepth--

                            i++
                        }

                        // Link complete, add to result
                        result.append(linkBuffer.toString())

                        // Reset link state
                        isInsideMarkdownLink = false
                        lastExclamationPos = -1  // 重置感叹号位置，防止状态错误
                        isImageLink = false
                        squareBracketStartPos = -1
                        hasPotentialMdLink = false
                        linkBuffer.clear()
                        continue
                    } else {
                        // Not a link, might be LaTeX or other syntax
                        // Reset state and add collected content
                        isInsideMarkdownLink = false
                        isImageLink = false
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
                val endFound =
                        when (latexStartType) {
                            "$$" -> i + 1 < content.length && c == '$' && content[i + 1] == '$'
                            "$" -> c == '$'
                            else -> false
                        }

                if (endFound) {
                    // Determine end method based on delimiter type
                    val endDelim =
                            when (latexStartType) {
                                "$$" -> "$$"
                                "$" -> "$"
                                else -> ""
                            }

                    // Process LaTeX content
                    val processedLatex =
                            processLatexBlock(latexStartType, endDelim, latexContent.toString())
                    result.append(processedLatex)

                    // Update index position
                    i =
                            when (latexStartType) {
                                "$$" -> i + 2
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
