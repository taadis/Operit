package com.ai.assistance.operit.ui.common.displays

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.em
import dev.jeziellago.compose.markdowntext.MarkdownText
import live.pw.renderX.RenderX
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

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
 * - Inline code (`code`)
 * - Blockquotes (> quote)
 * - Tables
 * - LaTeX equations ($equation$)
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

    // 是否尝试使用RenderX渲染
    var useRenderX by remember { mutableStateOf(containsLatex) }

    // 创建自定义文本样式，使用相对行高
    val customTextStyle =
        MaterialTheme.typography.bodyMedium

    // 如果检测到LaTeX且需要使用RenderX
    if (containsLatex && useRenderX) {
        Log.d("MarkdownTextComposable", "Found LaTeX content in text")

        // 将文本转换为适合RenderX渲染的格式（包括Markdown到HTML的转换）
        val formattedText = prepareTextForRenderX(text)
        Log.d("MarkdownTextComposable", "Formatted text: $formattedText")

        // 使用RenderX渲染内容，发生错误时会由Android系统处理
        Box(modifier = modifier.fillMaxWidth()) { RenderX(latex = formattedText) }
    } else {
        // 使用原来的Markdown渲染，但使用自定义样式优化行间距
        MarkdownText(
            markdown = text,
            modifier = modifier.fillMaxWidth(),
            color = textColor,
            style = customTextStyle,
            isTextSelectable = isSelectable
        )
    }
}

/** 准备文本以便RenderX正确渲染 将Markdown转换为HTML，同时保留LaTeX公式 */
private fun prepareTextForRenderX(text: String): String {
    // 如果文本已经是HTML格式，直接使用
    if (text.trim().startsWith("<html>") && text.trim().endsWith("</html>")) {
        return text
    }

    // 特殊处理LaTeX公式，防止它们在Markdown解析过程中被破坏
    // 先将LaTeX公式替换为安全标记
    val latexPlaceholders = mutableMapOf<String, String>()
    var protectedText = text

    // 保护内联LaTeX：$...$
    val inlineLatexPattern = "\\$(.*?)\\$".toRegex()
    var counter = 0
    protectedText =
        inlineLatexPattern.replace(protectedText) { matchResult ->
            val placeholder = "LATEX_PLACEHOLDER_${counter++}"
            latexPlaceholders[placeholder] = matchResult.value
            placeholder
        }

    // 保护块级LaTeX：$$...$$
    val blockLatexPattern = "\\$\\$(.*?)\\$\\$".toRegex(RegexOption.DOT_MATCHES_ALL)
    protectedText =
        blockLatexPattern.replace(protectedText) { matchResult ->
            val placeholder = "LATEX_PLACEHOLDER_${counter++}"
            latexPlaceholders[placeholder] = matchResult.value
            placeholder
        }

    // 保护其他LaTeX标记
    val otherLatexPatterns =
        listOf(
            "\\\\\\((.*?)\\\\\\)".toRegex(RegexOption.DOT_MATCHES_ALL),
            "\\\\\\[(.*?)\\\\\\]".toRegex(RegexOption.DOT_MATCHES_ALL),
            "\\\\begin\\{(.*?)\\}(.*?)\\\\end\\{\\1\\}".toRegex(
                RegexOption.DOT_MATCHES_ALL
            )
        )

    for (pattern in otherLatexPatterns) {
        protectedText =
            pattern.replace(protectedText) { matchResult ->
                val placeholder = "LATEX_PLACEHOLDER_${counter++}"
                latexPlaceholders[placeholder] = matchResult.value
                placeholder
            }
    }

    // 将Markdown转换为HTML
    val html = markdownToHtml(protectedText)

    // 恢复LaTeX公式
    var finalHtml = html
    for ((placeholder, latexFormula) in latexPlaceholders) {
        finalHtml = finalHtml.replace(placeholder, latexFormula)
    }

    // 包装在样式中，使用更大的行间距来提高可读性
    return """
        <div style="font-family: Arial, sans-serif; line-height: 2.0; letter-spacing: 0.01em; word-spacing: 0.05em;">
            $finalHtml
        </div>
        """.trimIndent()
}

/** 将Markdown文本转换为HTML */
private fun markdownToHtml(markdown: String): String {
    val flavour = CommonMarkFlavourDescriptor()
    val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
    val html = HtmlGenerator(markdown, parsedTree, flavour).generateHtml()

    // 处理代码块样式
    var styledHtml =
        html.replace(
            "<pre><code>",
            "<pre style=\"background-color: #f5f5f5; padding: 10px; border-radius: 5px;\"><code>"
        )

    // 增强HTML标题样式
    var enhancedHtml = styledHtml

    // 为h1-h6添加适当的行高样式
    for (i in 1..6) {
        enhancedHtml =
            enhancedHtml.replace(
                "<h$i>",
                "<h$i style=\"line-height: 1.5; margin-top: 1.2em; margin-bottom: 0.7em;\">"
            )
    }

    // 优化段落和列表样式
    enhancedHtml = enhancedHtml.replace("<p>", "<p style=\"margin-bottom: 1.2em;\">")
    enhancedHtml = enhancedHtml.replace("<li>", "<li style=\"margin-bottom: 0.5em;\">")

    // 增强表格样式
    enhancedHtml =
        enhancedHtml.replace(
            "<table>",
            "<table style=\"border-collapse: collapse; width: 100%; margin: 1em 0;\">"
        )
    enhancedHtml =
        enhancedHtml.replace(
            "<th>",
            "<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left; background-color: #f2f2f2;\">"
        )
    enhancedHtml =
        enhancedHtml.replace("<td>", "<td style=\"border: 1px solid #ddd; padding: 8px;\">")

    return enhancedHtml
}
