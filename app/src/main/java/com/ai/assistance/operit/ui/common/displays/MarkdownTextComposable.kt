package com.ai.assistance.operit.ui.common.displays

import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.scilab.forge.jlatexmath.ParseException
import ru.noties.jlatexmath.JLatexMathDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import android.graphics.drawable.Drawable
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.text.HtmlCompat

/**
 * An integrated Markdown and LaTeX renderer composable that renders both standard Markdown features
 * and LaTeX equations in the same content.
 *
 * This composable supports:
 * - All standard Markdown features (headings, lists, bold, italic, links, etc.)
 * - LaTeX equations embedded in Markdown content using:
 *   - Inline equations with `$...$` or `\\(...\\)`
 *   - Block equations with `$$...$$` or `\\[...\\]`
 *
 * The implementation uses Markwon for Markdown rendering and JLatexMath for LaTeX rendering,
 * combining them seamlessly in a single TextView.
 */
@Composable
fun MarkdownTextComposable(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    isSelectable: Boolean = true,
    onLinkClicked: ((String) -> Unit)? = null
) {
    // Check if we need to use the integrated renderer (text contains LaTeX)
    val containsLatex =
        remember(text) {
            text.contains("$") ||
                    text.contains("\\(") ||
                    text.contains("\\)") ||
                    text.contains("\\[") ||
                    text.contains("\\]") ||
                    text.contains("\\begin") ||
                    text.contains("\\frac") ||
                    text.contains("\\sum") ||
                    text.contains("\\int")
        }

    val customTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = if (fontSize != TextUnit.Unspecified) fontSize else MaterialTheme.typography.bodyMedium.fontSize,
        // Use null coalescence for textAlign as it's nullable
        textAlign = textAlign ?: MaterialTheme.typography.bodyMedium.textAlign
    )
    
    if (containsLatex) {
        // Use integrated renderer for content with LaTeX
        Log.d("MarkdownTextComposable", "Using integrated LaTeX renderer")
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
    } else {
        // Use standard Markdown renderer for content without LaTeX
        Log.d("MarkdownTextComposable", "Using standard Markdown renderer")
        MarkdownText(
            markdown = text,
            modifier = modifier.fillMaxWidth(),
            style = customTextStyle.copy(color = textColor),
            isTextSelectable = isSelectable,
            onLinkClicked = onLinkClicked
        )
    }
}

/**
 * A composable that renders both Markdown and LaTeX content in the same TextView.
 * This is used when the content contains LaTeX equations.
 */
@Composable
private fun IntegratedMarkdownLatexRenderer(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    isSelectable: Boolean = true,
    onLinkClicked: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val latexSize = with(density) { 
        if (fontSize != TextUnit.Unspecified) {
            fontSize.toPx() 
        } else {
            textStyle.fontSize.toPx()
        }
    }
    val colorInt = textColor.toArgb()

    // Create Markwon instance with all needed plugins
    val markwon = remember { 
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES))
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .apply {
                onLinkClicked?.let { linkClickHandler ->
                    usePlugin(object : AbstractMarkwonPlugin() {
                        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                            builder.linkResolver { _, link ->
                                linkClickHandler(link)
                            }
                        }
                    })
                }
            }
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                // Set up the TextView
                setTextColor(colorInt)
                setLinkTextColor(colorInt)
                if (textAlign != null) {
                    textAlignment = when (textAlign) {
                        TextAlign.Left, TextAlign.Start -> View.TEXT_ALIGNMENT_TEXT_START
                        TextAlign.Right, TextAlign.End -> View.TEXT_ALIGNMENT_TEXT_END
                        TextAlign.Center -> View.TEXT_ALIGNMENT_CENTER
                        else -> View.TEXT_ALIGNMENT_TEXT_START
                    }
                }
                
                if (fontSize != TextUnit.Unspecified) {
                    this.textSize = fontSize.value
                } else if (textStyle.fontSize != TextUnit.Unspecified) {
                    this.textSize = textStyle.fontSize.value
                }
                
                // Enable link clicking and text selection
                if (isSelectable) {
                    setTextIsSelectable(true)
                }
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            // Process the content to handle both Markdown and LaTeX
            val processed = processLatexInMarkdown(content, textView, colorInt, latexSize, markwon)
            textView.text = processed
        }
    )
}

/**
 * Process both Markdown and LaTeX content, rendering them appropriately.
 */
private fun processLatexInMarkdown(
    content: String, 
    textView: TextView, 
    @androidx.annotation.ColorInt textColor: Int,
    textSize: Float,
    markwon: Markwon
): Spannable {
    // First let Markwon process the markdown
    val markdownSpannable = SpannableStringBuilder(markwon.toMarkdown(content))
    
    // Find LaTeX expressions in the content
    val regexPatterns = listOf(
        Regex("\\$\\$(.*?)\\$\\$", RegexOption.DOT_MATCHES_ALL),  // Block equations with $$..$$
        Regex("\\\\\\[(.*?)\\\\\\]", RegexOption.DOT_MATCHES_ALL), // Block equations with \[..\]
        Regex("\\\\\\((.*?)\\\\\\)", RegexOption.DOT_MATCHES_ALL), // Inline equations with \(..\)
        Regex("\\$(.*?)\\$") // Inline equations with $..$
    )
    
    // Keep track of all LaTeX spans to replace
    val latexSpans = mutableListOf<Triple<Int, Int, String>>()
    
    // Find all LaTeX expressions in the text
    for (pattern in regexPatterns) {
        val matcher = pattern.findAll(markdownSpannable)
        for (match in matcher) {
            val start = match.range.first
            val end = match.range.last + 1
            val latexExpr = match.value
            latexSpans.add(Triple(start, end, latexExpr))
        }
    }
    
    // Sort spans by start position (in reverse order to avoid invalidating indices)
    latexSpans.sortByDescending { it.first }
    
    // Replace each LaTeX expression with a JLatexMathDrawable
    for ((start, end, latexExpr) in latexSpans) {
        try {
            // Create JLatexMathDrawable for the LaTeX expression
            val drawable = JLatexMathDrawable.builder(latexExpr)
                .textSize(textSize)
                .color(textColor)
                .build()
            
            // Set bounds for the drawable
            drawable.setBounds(
                0, 
                0, 
                drawable.intrinsicWidth, 
                drawable.intrinsicHeight
            )
            
            // Replace the LaTeX expression with a custom drawable span
            markdownSpannable.setSpan(
                LatexDrawableSpan(drawable),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } catch (e: ParseException) {
            Log.e("MarkdownLatex", "Error parsing LaTeX: ${e.message}")
        } catch (e: Exception) {
            Log.e("MarkdownLatex", "Error rendering LaTeX: ${e.message}")
        }
    }
    
    return markdownSpannable
}

/**
 * A ReplacementSpan implementation for displaying LaTeX equations
 * using JLatexMathDrawable and aligning them vertically with text.
 */
private class LatexDrawableSpan(private val drawable: Drawable) : ReplacementSpan() {
    
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
            
            // Center the image vertically with the text
            val centerY = fm.ascent + fontHeight / 2
            
            fm.ascent = centerY - imageHeight / 2
            fm.descent = centerY + imageHeight / 2
            fm.top = fm.ascent
            fm.bottom = fm.descent
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
        
        // Calculate vertical position to center the image
        val fm = paint.fontMetricsInt
        val fontHeight = fm.descent - fm.ascent
        val centerY = y + fm.descent - fontHeight / 2
        val imageHeight = drawable.bounds.height()
        val transY = centerY - imageHeight / 2
        
        // Draw at the calculated position
        canvas.translate(x, transY.toFloat())
        drawable.draw(canvas)
        canvas.restore()
    }
}
