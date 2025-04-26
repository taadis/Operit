package com.ai.assistance.operit.ui.common.displays

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.jeziellago.compose.markdowntext.MarkdownText
import live.pw.renderX.RenderX

/**
 * A Markdown renderer composable that uses the compose-markdown library to render Markdown text.
 * This composable supports all standard Markdown features including:
 * - Headings (# Heading)
 * - Lists (- item)
 * - Bold and Italic (*bold*, _italic_)
 * - Links [text](url)
 * - Code blocks (```code```)
 * ```
 * ```
 * ```
 * ```
 * - Inline code (`code`)
 * - Blockquotes (> quote)
 * - Tables
 * - LaTeX equations ($equation$)
 * ```
 * ```
 * ```
 * ```
 */
@Composable
fun MarkdownTextComposable(
        text: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        isSelectable: Boolean = true
) {
    // Check if text contains LaTeX formulas
    val containsLatex =
            remember(text) {
                text.contains("\\$") ||
                        text.contains("\\(") ||
                        text.contains("\\[") ||
                        text.contains("\\begin") ||
                        text.contains("\\frac") ||
                        text.contains("\\sum")
            }

    // 创建自定义文本样式
    val customTextStyle = MaterialTheme.typography.bodyMedium

    // 如果检测到LaTeX，直接使用RenderX进行渲染
    if (containsLatex) {
        Log.d("MarkdownTextComposable", "Found LaTeX content in text, using LaTeX renderer")

        // 直接使用RenderX渲染LaTeX内容，不转换为HTML或处理Markdown
        Box(modifier = modifier.fillMaxWidth()) { 
            RenderX(
                latex = text,
                textColor = textColor.toArgb()  // Pass the text color to RenderX
            ) 
        }
    } else {
        // 仅对不包含LaTeX的内容使用Markdown渲染
        Log.d("MarkdownTextComposable", "Using standard Markdown renderer")
        MarkdownText(
                markdown = text,
                modifier = modifier.fillMaxWidth(),
                color = textColor,
                style = customTextStyle,
                isTextSelectable = isSelectable
        )
    }
}
