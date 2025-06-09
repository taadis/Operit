package com.ai.assistance.operit.ui.common.markdown

import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Divider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.common.displays.LatexCache
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.markdown.NestedMarkdownProcessor
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import ru.noties.jlatexmath.JLatexMathDrawable

data class DisplayableMarkdownNode(
        val staticNode: MarkdownNode,
        val streamingContent: StringBuilder = StringBuilder(staticNode.content),
        val version: Int = 0
)

/** 高性能流式Markdown渲染组件 通过Jetpack Compose实现，支持流式渲染Markdown内容 使用Stream处理系统，实现高效的异步处理 */
@Composable
fun StreamMarkdownRenderer(
        markdownStream: Stream<Char>,
        modifier: Modifier = Modifier,
        textColor: Color = LocalContentColor.current,
        backgroundColor: Color = MaterialTheme.colorScheme.surface,
        onLinkClick: ((String) -> Unit)? = null
) {
    val nodes = remember { mutableStateListOf<DisplayableMarkdownNode>() }

    LaunchedEffect(markdownStream) {
        nodes.clear() // 在流实例更改时清除节点

        markdownStream.splitBy(NestedMarkdownProcessor.getBlockPlugins()).collect { blockGroup
            ->
            val blockType = NestedMarkdownProcessor.getTypeForPlugin(blockGroup.tag)

            // 对于水平分割线，内容无关紧要，直接添加节点
            if (blockType == MarkdownProcessorType.HORIZONTAL_RULE) {
                nodes.add(DisplayableMarkdownNode(MarkdownNode(type = blockType, content = "---")))
                return@collect
            }

            val isInlineContainer = blockType != MarkdownProcessorType.CODE_BLOCK

            // 为新块创建并添加节点
            val newNode = MarkdownNode(type = blockType, content = "", children = mutableListOf())
            val nodeIndex = nodes.size
            nodes.add(DisplayableMarkdownNode(newNode))

            streamContentWithThrottledUpdates(blockGroup.stream, nodes, nodeIndex)

            if (isInlineContainer) {
                val blockContent = nodes[nodeIndex].streamingContent.toString()
                if (blockContent.isEmpty()) return@collect

                var contentForInlineParsing = blockContent
                when (blockType) {
                    MarkdownProcessorType.ORDERED_LIST -> {
                        val markerMatch = Regex("""^(\d+)\.\s*""").find(blockContent)
                        contentForInlineParsing =
                                markerMatch?.let { blockContent.substring(it.range.last + 1) }
                                        ?: blockContent
                    }
                    MarkdownProcessorType.UNORDERED_LIST -> {
                        val markerMatch = Regex("""^[-*+]\s+""").find(blockContent)
                        contentForInlineParsing =
                                markerMatch?.let { blockContent.substring(it.range.last + 1) }
                                        ?: blockContent
                    }
                    MarkdownProcessorType.BLOCK_QUOTE -> {
                        contentForInlineParsing =
                                blockContent.lines().joinToString("\n") {
                                    it.removePrefix("> ").removePrefix(">")
                                }
                    }
                    else -> {
                        /* No change needed */
                    }
                }

                val inlineChildren = mutableListOf<MarkdownNode>()
                val charStream = stream {
                    for (char in contentForInlineParsing) {
                        emit(char)
                    }
                }

                charStream.splitBy(NestedMarkdownProcessor.getInlinePlugins()).collect {
                        inlineGroup ->
                    val inlineType = NestedMarkdownProcessor.getTypeForPlugin(inlineGroup.tag)
                    val inlineContentBuilder = StringBuilder()
                    inlineGroup.stream.collect { str -> inlineContentBuilder.append(str) }
                    var inlineContent = inlineContentBuilder.toString()

                    if (inlineType == MarkdownProcessorType.PLAIN_TEXT) {
                        inlineContent = inlineContent.trim()
                    }

                    if (inlineContent.isNotEmpty()) {
                        inlineChildren.add(MarkdownNode(type = inlineType, content = inlineContent))
                    }
                }

                if (inlineChildren.isNotEmpty()) {
                    val finalNode =
                            nodes[nodeIndex].staticNode.copy(
                                    children = inlineChildren,
                                    content = blockContent
                            )
                    nodes[nodeIndex] = DisplayableMarkdownNode(finalNode)
                }
            } else {
                // 对于没有内联格式的代码块，直接流式传输内容。
                val finalNode =
                        nodes[nodeIndex].staticNode.copy(
                                content = nodes[nodeIndex].streamingContent.toString()
                        )
                nodes[nodeIndex] = DisplayableMarkdownNode(finalNode)
            }
        }
    }

    // 渲染Markdown内容
    Surface(modifier = modifier, color = backgroundColor, shape = RoundedCornerShape(4.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            nodes.forEach { displayableNode ->
                MarkdownNodeRenderer(
                        node = displayableNode.staticNode,
                        streamingContent = displayableNode.streamingContent,
                        textColor = textColor,
                        onLinkClick = onLinkClick
                )
            }
        }
    }
}

/**
 * 从流中收集内容，并以节流方式更新UI，以实现平滑的 "打字机 "效果，而不会出现性能问题。
 * @param stream 要从中收集字符的源流。
 * @param nodes 要更新的显示节点的可变列表。
 * @param nodeIndex 要在列表中更新的节点的索引。
 * @param updateInterval 强制UI更新之间的最小毫秒间隔。
 */
private suspend fun streamContentWithThrottledUpdates(
        stream: Stream<String>,
        nodes: MutableList<DisplayableMarkdownNode>,
        nodeIndex: Int,
        updateInterval: Long = 50L // 每秒20次更新
) {
    var lastUpdateTime = System.currentTimeMillis()
    stream.collect { contentChunk ->
        nodes[nodeIndex].streamingContent.append(contentChunk)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime > updateInterval) {
            val currentNode = nodes[nodeIndex]
            nodes[nodeIndex] = currentNode.copy(version = currentNode.version + 1)
            lastUpdateTime = currentTime
        }
    }
    // 执行最终更新以确保显示最后一块文本。
    val currentNode = nodes[nodeIndex]
    nodes[nodeIndex] = currentNode.copy(version = currentNode.version + 1)
}

/** 渲染单个Markdown节点 */
@Composable
fun MarkdownNodeRenderer(
        node: MarkdownNode,
        streamingContent: StringBuilder,
        textColor: Color,
        modifier: Modifier = Modifier,
        onLinkClick: ((String) -> Unit)? = null
) {
    when (node.type) {
        // 块级元素
        MarkdownProcessorType.HEADER -> {
            val level = determineHeaderLevel(streamingContent.toString())
            val headerText = streamingContent.toString().trimStart('#', ' ')
            val headerStyle =
                    when (level) {
                        1 -> MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp)
                        2 -> MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp)
                        3 -> MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp)
                        4 -> MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp)
                        5 -> MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp)
                        else -> MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp)
                    }

            Text(
                    text = headerText.trim(),
                    style = headerStyle,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(
                                            bottom = if (level <= 2) 4.dp else 2.dp,
                                            top =
                                                    when (level) {
                                                        1 -> 14.dp
                                                        2 -> 12.dp
                                                        3 -> 10.dp
                                                        else -> 8.dp
                                                    }
                                    )
            )
        }
        MarkdownProcessorType.BLOCK_QUOTE -> {
            Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .border(
                                                width = 2.dp,
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.3f
                                                        ),
                                                shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(
                                                start = 8.dp,
                                                top = 8.dp,
                                                end = 8.dp,
                                                bottom = 8.dp
                                        )
                ) {
                    val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
                    val inlineContent =
                            if (node.children.isEmpty()) {
                                buildAnnotatedString {
                                    append(
                                            streamingContent.toString().lines().joinToString("\n") {
                                                it.removePrefix("> ").removePrefix(">")
                                            }
                                    )
                                }
                            } else {
                                buildAnnotatedString {
                                    appendStyledText(
                                            node.children,
                                            textColor,
                                            onLinkClick,
                                            inlineContentMap
                                    )
                                }
                            }

                    Text(
                            text = inlineContent,
                            inlineContent = inlineContentMap,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        MarkdownProcessorType.CODE_BLOCK -> {
            Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                        text =
                                streamingContent.toString().trim().lines().joinToString("\n") {
                                    it.removePrefix("```").removeSuffix("```")
                                },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
        MarkdownProcessorType.ORDERED_LIST -> {
            val itemContent = streamingContent.toString().trim()
            val numberMatch = Regex("""^(\d+)\.\s*""").find(itemContent)
            val numberStr = numberMatch?.groupValues?.getOrNull(1) ?: ""

            Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.Top
            ) {
                Text(
                        text = "$numberStr.",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                )

                val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
                val inlineContent =
                        if (node.children.isEmpty()) {
                            val streamingText =
                                    numberMatch?.let { itemContent.substring(it.range.last + 1) }
                                            ?: itemContent
                            buildAnnotatedString { append(streamingText) }
                        } else {
                            buildAnnotatedString {
                                appendStyledText(
                                        node.children,
                                        textColor,
                                        onLinkClick,
                                        inlineContentMap
                                )
                            }
                        }

                Text(
                        text = inlineContent,
                        inlineContent = inlineContentMap,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                )
            }
        }
        MarkdownProcessorType.UNORDERED_LIST -> {
            val itemContent = streamingContent.toString().trim()
            val markerMatch = Regex("""^[-*+]\s+""").find(itemContent)

            Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.Top
            ) {
                Text(
                        text = "•",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                )
                val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
                val inlineContent =
                        if (node.children.isEmpty()) {
                            val streamingText =
                                    markerMatch?.let { itemContent.substring(it.range.last + 1) }
                                            ?: itemContent
                            buildAnnotatedString { append(streamingText) }
                        } else {
                            buildAnnotatedString {
                                appendStyledText(
                                        node.children,
                                        textColor,
                                        onLinkClick,
                                        inlineContentMap
                                )
                            }
                        }

                Text(
                        text = inlineContent,
                        inlineContent = inlineContentMap,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                )
            }
        }
        MarkdownProcessorType.HORIZONTAL_RULE -> {
            Divider(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
        MarkdownProcessorType.BLOCK_LATEX -> {
            // 块级LaTeX公式渲染
            Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                ) {
                    // 提取LaTeX内容，移除$$分隔符
                    val latexContent =
                            streamingContent.toString().trim().removeSurrounding("$$", "$$")

                    // 使用AndroidView和JLatexMath渲染LaTeX公式
                    AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    // 设置初始空白状态
                                    text = ""
                                }
                            },
                            update = { textView ->
                                // 在update回调中渲染LaTeX公式
                                try {
                                    val drawable =
                                            LatexCache.getDrawable(
                                                    latexContent,
                                                    JLatexMathDrawable.builder(latexContent)
                                                            .textSize(
                                                                    18f *
                                                                            textView.resources
                                                                                    .displayMetrics
                                                                                    .density
                                                            )
                                                            .padding(8)
                                                            .background(0x10000000)
                                                            .align(JLatexMathDrawable.ALIGN_CENTER)
                                                            .color(textColor.toArgb())
                                            )

                                    // 设置边界并添加到TextView
                                    drawable.setBounds(
                                            0,
                                            0,
                                            drawable.intrinsicWidth,
                                            drawable.intrinsicHeight
                                    )
                                    textView.setCompoundDrawables(null, drawable, null, null)
                                } catch (e: Exception) {
                                    // 渲染失败时回退到纯文本显示
                                    textView.text = latexContent
                                    textView.setTextColor(textColor.toArgb())
                                    textView.textSize = 16f
                                    textView.typeface = android.graphics.Typeface.MONOSPACE
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 内联元素通常不会直接渲染，只在父节点中应用样式
        MarkdownProcessorType.PLAIN_TEXT -> {
            if (streamingContent.trim().isEmpty()) return

            val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
            val inlineContent =
                    if (node.children.isEmpty()) {
                        buildAnnotatedString { append(streamingContent.toString().trim()) }
                    } else {
                        buildAnnotatedString {
                            appendStyledText(
                                    node.children,
                                    textColor,
                                    onLinkClick,
                                    inlineContentMap
                            )
                        }
                    }

            if (inlineContent.isEmpty()) return

            Text(
                    text = inlineContent,
                    inlineContent = inlineContentMap,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
            )
        }

        // 如果节点类型不在上述处理范围内，则作为普通文本处理
        else -> {
            if (streamingContent.trim().isEmpty()) return

            val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
            val inlineContent =
                    if (node.children.isEmpty()) {
                        buildAnnotatedString { append(streamingContent.toString().trim()) }
                    } else {
                        buildAnnotatedString {
                            appendStyledText(
                                    node.children,
                                    textColor,
                                    onLinkClick,
                                    inlineContentMap
                            )
                        }
                    }

            if (inlineContent.isEmpty()) return

            Text(
                    text = inlineContent,
                    inlineContent = inlineContentMap,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
            )
        }
    }
}

/** 将文本及其子节点添加到AnnotatedString中，应用适当的样式 */
@Composable
private fun AnnotatedString.Builder.appendStyledText(
        children: List<MarkdownNode>,
        textColor: Color,
        onLinkClick: ((String) -> Unit)?,
        inlineContentMap: MutableMap<String, InlineTextContent>
) {
    // 如果一个块有子节点，那么这些子节点就代表了全部内容。
    // 我们只需按顺序渲染每个子节点，并应用其特定的样式。
    children.forEach { child ->
        when (child.type) {
            MarkdownProcessorType.BOLD -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(child.content) }
            }
            MarkdownProcessorType.ITALIC -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(child.content) }
            }
            MarkdownProcessorType.INLINE_CODE -> {
                withStyle(
                        SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color.LightGray.copy(alpha = 0.3f),
                                fontSize = 14.sp
                        )
                ) { append(child.content) }
            }
            MarkdownProcessorType.STRIKETHROUGH -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(child.content)
                }
            }
            MarkdownProcessorType.UNDERLINE -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(child.content)
                }
            }
            MarkdownProcessorType.LINK -> {
                val linkText = extractLinkText(child.content)
                val linkUrl = extractLinkUrl(child.content)

                pushStringAnnotation("URL", linkUrl)
                withStyle(
                        SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                        )
                ) { append(linkText) }
                pop()
            }
            MarkdownProcessorType.IMAGE -> {
                // 图片处理暂不实现
                append(child.content)
            }
            MarkdownProcessorType.INLINE_LATEX -> {
                val context = LocalContext.current
                val density = LocalDensity.current
                val color = textColor.toArgb()
                val textSize = MaterialTheme.typography.bodyLarge.fontSize

                // 移除$分隔符
                val latexContent = child.content.trim().removeSurrounding("$", "$")
                if (latexContent.isBlank()) return@forEach

                val drawable =
                        remember(latexContent, textColor, textSize) {
                            LatexCache.getDrawable(
                                    latexContent,
                                    JLatexMathDrawable.builder(latexContent)
                                            .textSize(with(density) { textSize.toPx() })
                                            .padding(4)
                                            .color(color)
                                            .align(JLatexMathDrawable.ALIGN_LEFT)
                            )
                        }

                val width = with(density) { drawable.intrinsicWidth.toSp() }
                val height = with(density) { drawable.intrinsicHeight.toSp() }

                val inlineContentId = "ilatex_${latexContent.hashCode()}_${System.nanoTime()}"

                appendInlineContent(inlineContentId, "[latex]")

                inlineContentMap[inlineContentId] =
                        InlineTextContent(
                                Placeholder(
                                        width = width,
                                        height = height,
                                        placeholderVerticalAlign =
                                                PlaceholderVerticalAlign.TextCenter
                                )
                        ) {
                            AndroidView(
                                    factory = { ctx ->
                                        ImageView(ctx).apply { setImageDrawable(drawable) }
                                    }
                            )
                        }
            }
            else -> {
                // 默认情况，通常是 PLAIN_TEXT
                append(child.content)
            }
        }
    }
}

/** 确定标题级别 */
private fun determineHeaderLevel(content: String): Int {
    if (content.isEmpty()) return 0
    val headerMarkers = content.takeWhile { it == '#' }.count()
    return minOf(headerMarkers, 6) // 标题级别最高到6
}

/** 从链接Markdown中提取链接文本 例如：从 [链接文本](https://example.com) 中提取 "链接文本" */
private fun extractLinkText(linkContent: String): String {
    val startBracket = linkContent.indexOf('[')
    val endBracket = linkContent.indexOf(']')
    return if (startBracket != -1 && endBracket != -1 && startBracket < endBracket) {
        linkContent.substring(startBracket + 1, endBracket)
    } else {
        linkContent
    }
}

/** 从链接Markdown中提取链接URL 例如：从 [链接文本](https://example.com) 中提取 "https://example.com" */
private fun extractLinkUrl(linkContent: String): String {
    val startParenthesis = linkContent.indexOf('(')
    val endParenthesis = linkContent.indexOf(')')
    return if (startParenthesis != -1 && endParenthesis != -1 && startParenthesis < endParenthesis
    ) {
        linkContent.substring(startParenthesis + 1, endParenthesis)
    } else {
        ""
    }
}
