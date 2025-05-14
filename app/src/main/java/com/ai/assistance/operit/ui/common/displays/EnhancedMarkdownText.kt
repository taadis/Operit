package com.ai.assistance.operit.ui.common.displays

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified

/**
 * An enhanced markdown renderer that uses the standard Markdown renderer but replaces
 * code blocks with a specialized CodeBlockWithCopyButton component.
 *
 * This component adds copy buttons to code blocks while retaining all other markdown features.
 */
@Composable
fun EnhancedMarkdownText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = Unspecified,
    textAlign: TextAlign? = null,
    isSelectable: Boolean = true,
    onLinkClicked: ((String) -> Unit)? = null,
    onCodeCopied: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Parse the markdown text to extract code blocks
    val parsed = remember(text) { parseMarkdownWithCodeBlocks(text) }
    
    Column(modifier = modifier.fillMaxWidth()) {
        parsed.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> {
                    // Regular markdown text
                    MarkdownTextComposable(
                        text = segment.content,
                        textColor = textColor,
                        fontSize = fontSize,
                        textAlign = textAlign,
                        isSelectable = isSelectable,
                        onLinkClicked = onLinkClicked
                    )
                }
                is MarkdownSegment.CodeBlock -> {
                    // Code block with copy button
                    CodeBlockWithCopyButton(
                        code = segment.code,
                        language = segment.language,
                        textColor = textColor,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        onCopy = onCodeCopied
                    )
                }
            }
        }
    }
}

/**
 * Parses markdown text and extracts code blocks.
 * 
 * @param text The markdown text to parse
 * @return A list of markdown segments (text or code blocks)
 */
private fun parseMarkdownWithCodeBlocks(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    
    // Regex to find code blocks - matches ```[language](code)```
    val codeBlockRegex = Regex("```(?:(\\w*)\\n)?([\\s\\S]*?)```", RegexOption.MULTILINE)
    
    var lastEndIndex = 0
    val matcher = codeBlockRegex.findAll(text)
    
    for (match in matcher) {
        val startIndex = match.range.first
        
        // Add text before this code block
        if (startIndex > lastEndIndex) {
            val textBefore = text.substring(lastEndIndex, startIndex)
            if (textBefore.isNotEmpty()) {
                segments.add(MarkdownSegment.Text(textBefore))
            }
        }
        
        // Extract code block info
        val language = match.groupValues[1].trim()
        val code = match.groupValues[2].trim()
        
        // Add code block
        segments.add(MarkdownSegment.CodeBlock(code, language))
        
        // Update last end index
        lastEndIndex = match.range.last + 1
    }
    
    // Add any remaining text
    if (lastEndIndex < text.length) {
        val remainingText = text.substring(lastEndIndex)
        if (remainingText.isNotEmpty()) {
            segments.add(MarkdownSegment.Text(remainingText))
        }
    }
    
    return segments
}

/** Represents a segment of markdown content */
sealed class MarkdownSegment {
    /** Regular markdown text */
    data class Text(val content: String) : MarkdownSegment()
    
    /** Code block with optional language */
    data class CodeBlock(val code: String, val language: String) : MarkdownSegment()
} 