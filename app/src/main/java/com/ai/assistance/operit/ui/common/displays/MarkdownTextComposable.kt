package com.ai.assistance.operit.ui.common.displays

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified

/**
 * An integrated Markdown and LaTeX renderer composable that renders both standard Markdown features
 * and LaTeX equations in the same content.
 *
 * This composable supports:
 * - All standard Markdown features (headings, lists, bold, italic, links, etc.)
 * - Markdown images using the standard syntax: `![alt text](image_url)` (loaded using Coil)
 * - LaTeX equations embedded in Markdown content using:
 * - Inline equations with `$...$`
 * - Block equations with `$$...$$`
 * - Math expressions with recognized symbols like subscripts, integrals, etc.
 *
 * The implementation uses Markwon for Markdown rendering, JLatexMath for LaTeX rendering, and Coil
 * for efficient image loading with caching support.
 */
@Composable
fun MarkdownTextComposable(
        text: String,
        textColor: Color,
        modifier: Modifier = Modifier,
        fontSize: TextUnit = Unspecified,
        textAlign: TextAlign? = null,
        isSelectable: Boolean = true,
        onLinkClicked: ((String) -> Unit)? = null
) {
        val customTextStyle =
                MaterialTheme.typography.bodyMedium.copy(
                        fontSize =
                                if (fontSize != Unspecified) fontSize
                                else MaterialTheme.typography.bodyMedium.fontSize,
                        textAlign = textAlign ?: MaterialTheme.typography.bodyMedium.textAlign
                )

        // Use the integrated renderer for both Markdown and LaTeX
        IntegratedMarkdownLatexRenderer(
                content = text,
                textColor = textColor,
                modifier = modifier.fillMaxWidth(),
                textStyle = customTextStyle,
                fontSize = fontSize,
                textAlign = textAlign,
                isSelectable = isSelectable,
                onLinkClicked = onLinkClicked
        )
}
