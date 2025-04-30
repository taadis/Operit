package com.ai.assistance.operit.ui.common.displays

import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.View
import android.widget.TextView
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
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import androidx.compose.ui.viewinterop.AndroidView
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

/**
 * A composable that renders both Markdown and LaTeX content in the same TextView.
 * This handles all markdown content, with or without LaTeX equations.
 * Uses efficient single-pass algorithms for processing.
 */
@Composable
internal fun IntegratedMarkdownLatexRenderer(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    fontSize: TextUnit = Unspecified,
    textAlign: TextAlign? = null,
    isSelectable: Boolean = true,
    onLinkClicked: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val latexSize = with(density) { 
        if (fontSize != Unspecified) {
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
                setLineSpacing(0f, 1.2f) // Set line spacing multiplier to 1.2x
                if (textAlign != null) {
                    textAlignment = when (textAlign) {
                        TextAlign.Left, TextAlign.Start -> View.TEXT_ALIGNMENT_TEXT_START
                        TextAlign.Right, TextAlign.End -> View.TEXT_ALIGNMENT_TEXT_END
                        TextAlign.Center -> View.TEXT_ALIGNMENT_CENTER
                        else -> View.TEXT_ALIGNMENT_TEXT_START
                    }
                }
                
                if (fontSize != Unspecified) {
                    this.textSize = fontSize.value
                } else if (textStyle.fontSize != Unspecified) {
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
            try {
                // Process content in a single pass, handling both Markdown links and LaTeX
                val preprocessed = LatexPreprocessor.preprocessLatexInMarkdown(content)
                
                // Let Markwon render the processed content
                val renderedMarkdown = markwon.toMarkdown(preprocessed)
                
                // Find and render LaTeX expressions in a single pass
                val finalSpannable = LatexRenderer.renderLatexInMarkdown(
                    renderedMarkdown, 
                    textView, 
                    colorInt, 
                    latexSize
                )
                
                textView.text = finalSpannable
                
            } catch (e: Exception) {
                Log.e("MarkdownLatex", "Error rendering content: ${e.message}", e)
                // If an error occurs, at least show the original text
                textView.text = markwon.toMarkdown(content)
            }
        }
    )
} 