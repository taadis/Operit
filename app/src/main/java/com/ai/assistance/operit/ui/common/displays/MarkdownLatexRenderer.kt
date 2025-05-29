package com.ai.assistance.operit.ui.common.displays

import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.ai.assistance.operit.core.application.OperitApplication
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin

// Track rendering times
private var renderCount = 0
private val startTimes = mutableMapOf<String, Long>()

/**
 * A composable that renders both Markdown and LaTeX content in the same TextView. This handles all
 * markdown content, with or without LaTeX equations. Uses efficient single-pass algorithms for
 * processing with caching support.
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
    val latexSize =
            with(density) {
                if (fontSize != Unspecified) {
                    fontSize.toPx()
                } else {
                    textStyle.fontSize.toPx()
                }
            }
    val colorInt = textColor.toArgb()

    // 创建一个Handler用于延迟刷新
    val handler = remember { Handler(Looper.getMainLooper()) }

    // 在组件销毁时清理Handler
    DisposableEffect(Unit) { onDispose { handler.removeCallbacksAndMessages(null) } }

    // 使用全局ImageLoader代替创建新实例
    val globalImageLoader = OperitApplication.globalImageLoader

    // Generate a unique ID for this render operation (for timing)
    val renderId = "render_${++renderCount}"

    // Create Markwon instance with all needed plugins
    val markwon = remember {
        Markwon.builder(context)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES))
                .usePlugin(SoftBreakAddsNewLinePlugin.create())
                // 添加专门处理点击链接的插件
                .usePlugin(
                        object : AbstractMarkwonPlugin() {
                            override fun configureConfiguration(
                                    builder: MarkwonConfiguration.Builder
                            ) {
                                builder.linkResolver { view, link -> onLinkClicked?.invoke(link) }
                            }
                        }
                )
                // 使用ImagesPlugin配置
                .usePlugin(ImagesPlugin.create())
                // 使用CoilImagesPlugin与系统缓存
                .usePlugin(CoilImagesPlugin.create(context, globalImageLoader))
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

                    // 防止布局闪烁
                    setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)

                    // 关闭动画过渡
                    isHorizontalFadingEdgeEnabled = false
                    isVerticalFadingEdgeEnabled = false

                    if (textAlign != null) {
                        textAlignment =
                                when (textAlign) {
                                    TextAlign.Left, TextAlign.Start ->
                                            View.TEXT_ALIGNMENT_TEXT_START
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
                    // Start timing this render
                    startTimes[renderId] = System.currentTimeMillis()

                    // Periodically log cache statistics (every 10 renders)
                    if (renderCount % 10 == 0) {
                        Log.d("LatexCache", "Cache statistics: ${LatexCache.getStats()}")
                    }

                    // 取消所有进行中的图片加载
                    AsyncDrawableScheduler.unschedule(textView)
                    handler.removeCallbacksAndMessages(null)

                    // Use the contentHashCode as a simple cache key
                    val contentKey = content.hashCode().toString()

                    // 生成内容哈希，用于简单缓存检查
                    // Log.d("MarkdownLatex", "Rendering content with key: $contentKey")

                    // 处理LaTeX
                    val preprocessed = LatexPreprocessor.preprocessLatexInMarkdown(content)

                    // 使用Markwon渲染，保留图片支持
                    val renderedMarkdown = markwon.toMarkdown(preprocessed)

                    // 应用LaTeX渲染
                    val finalSpannable =
                            LatexRenderer.renderLatexInMarkdown(
                                    renderedMarkdown,
                                    textView,
                                    colorInt,
                                    latexSize
                            )

                    // 设置文本
                    textView.text = finalSpannable

                    // 安排加载图片，但只做一次
                    AsyncDrawableScheduler.schedule(textView)

                    // Log the render time
                    val duration = System.currentTimeMillis() - startTimes.getOrDefault(renderId, 0)
                    // Log.d("MarkdownLatex", "Rendered in $duration ms")
                } catch (e: Exception) {
                    Log.e("MarkdownLatex", "Error rendering content: ${e.message}", e)
                    // 如果发生错误，至少显示原始文本
                    try {
                        // 直接使用Markwon进行最基本的渲染，跳过LaTeX处理
                        textView.text = markwon.toMarkdown(content)
                        // 即使发生错误，也尝试加载图片
                        AsyncDrawableScheduler.schedule(textView)
                    } catch (e2: Exception) {
                        Log.e("MarkdownLatex", "Failed even in basic rendering: ${e2.message}", e2)
                        // 完全失败的情况下，使用纯文本
                        textView.text = content
                    }
                }
            }
    )
}
