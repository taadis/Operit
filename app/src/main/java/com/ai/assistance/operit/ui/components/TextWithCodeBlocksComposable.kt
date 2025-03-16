package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer

/**
 * A composable function that renders text content with proper formatting for code blocks.
 * Detects Markdown-style code blocks (```code```) and renders them using CodeBlockComposable.
 */
@Composable
fun TextWithCodeBlocksComposable(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    // Normalize content, removing excess whitespace
    val normalizedContent = text
        .replace(Regex("\n\\s*\n\\s*\n+"), "\n\n") // Replace multiple blank lines with single one
        .replace(Regex("^\\s*\n+"), "") // Remove leading blank lines
        .replace(Regex("\n+\\s*$"), "") // Remove trailing blank lines
    
    // Parse code blocks
    val codeBlockPattern = Regex("```([\\s\\S]*?)```")
    val segments = codeBlockPattern.split(normalizedContent)
    val codeBlocks = codeBlockPattern.findAll(normalizedContent).map { it.groupValues[1] }.toList()
    
    Column(modifier = modifier) {
        segments.forEachIndexed { index, textSegment ->
            // Add text segment if not empty
            if (textSegment.isNotBlank()) {
                SelectionContainer {
                    Text(
                        text = textSegment,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // Add spacing if not the last segment
                if (index < segments.size - 1 || index < codeBlocks.size) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // Add code block if available
            if (index < codeBlocks.size) {
                CodeBlockComposable(
                    code = codeBlocks[index]
                )
                
                // Add spacing if not the last element
                if (index < segments.size - 1 || index < codeBlocks.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
} 