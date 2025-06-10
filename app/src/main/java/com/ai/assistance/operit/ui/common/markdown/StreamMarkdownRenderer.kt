package com.ai.assistance.operit.ui.common.markdown

import android.util.Log
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
import androidx.compose.runtime.key
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
import com.ai.assistance.operit.util.stream.splitBy as streamSplitBy
import com.ai.assistance.operit.util.stream.stream
import ru.noties.jlatexmath.JLatexMathDrawable

private const val TAG = "StreamMarkdownRenderer"
private const val PARSER_TAG = "MD-Parser"
private const val RENDER_TAG = "MD-Render"

/** 高性能流式Markdown渲染组件 通过Jetpack Compose实现，支持流式渲染Markdown内容 使用Stream处理系统，实现高效的异步处理 */
@Composable
fun StreamMarkdownRenderer(
        markdownStream: Stream<Char>,
        modifier: Modifier = Modifier,
        textColor: Color = LocalContentColor.current,
        backgroundColor: Color = MaterialTheme.colorScheme.surface,
        onLinkClick: ((String) -> Unit)? = null
) {
    val nodes = remember { mutableStateListOf<MarkdownNode>() }

    LaunchedEffect(markdownStream) {
        Log.d(PARSER_TAG, "【初始化】开始处理Markdown流")
        nodes.clear() // 在流实例更改时清除节点
        Log.d(PARSER_TAG, "【初始化】流实例更改，清除节点列表")

        markdownStream.streamSplitBy(NestedMarkdownProcessor.getBlockPlugins()).collect { blockGroup
            ->
            val blockType = NestedMarkdownProcessor.getTypeForPlugin(blockGroup.tag)
            Log.d(
                    PARSER_TAG,
                    "══════════════════START BLOCK TYPE: $blockType══════════════════════"
            )
            Log.d(PARSER_TAG, "【解析】发现块类型: $blockType [tag=${blockGroup.tag}]")

            // 对于水平分割线，内容无关紧要，直接添加节点
            if (blockType == MarkdownProcessorType.HORIZONTAL_RULE) {
                Log.d(PARSER_TAG, "【解析】处理水平分割线 - 直接添加节点")
                nodes.add(MarkdownNode(type = blockType, initialContent = "---"))
                return@collect
            }

            val isInlineContainer =
                    blockType != MarkdownProcessorType.CODE_BLOCK &&
                            blockType != MarkdownProcessorType.BLOCK_LATEX
            Log.d(
                    PARSER_TAG,
                    "【解析】▶ 内联判定结果: isInlineContainer=$isInlineContainer (${if(isInlineContainer) "可以" else "不可以"}包含内联元素)"
            )

            // 为新块创建并添加节点
            val newNode =
                    MarkdownNode(
                            type = blockType,
                            initialContent = "",
                            initialChildren = mutableListOf()
                    )
            nodes.add(newNode)
            Log.d(PARSER_TAG, "【解析】创建新节点: index=${nodes.size - 1}, type=$blockType")

            if (isInlineContainer) {
                Log.d(PARSER_TAG, "【解析】⟢ 开始处理可包含内联元素的块: 直接从流中解析内联元素")

                val contentBuilderForNode = StringBuilder()

                // Directly parse the block stream for inline elements
                blockGroup.stream.streamSplitBy(NestedMarkdownProcessor.getInlinePlugins())
                        .collect { inlineGroup ->
                            val inlineType =
                                    NestedMarkdownProcessor.getTypeForPlugin(inlineGroup.tag)
                            val inlineContentBuilder = StringBuilder()
                            inlineGroup.stream.collect { str -> inlineContentBuilder.append(str) }
                            val rawInlineContent = inlineContentBuilder.toString()
                            Log.d(
                                    PARSER_TAG,
                                    "【解析】内联元素: $inlineType, 内容=\"${if(rawInlineContent.length > 20) rawInlineContent.substring(0, 17) + "..." else rawInlineContent}\""
                            )
                            contentBuilderForNode.append(rawInlineContent)

                            var contentForChildNode = rawInlineContent
                            if (inlineType == MarkdownProcessorType.PLAIN_TEXT) {
                                contentForChildNode = contentForChildNode.trim()
                            }

                            if (contentForChildNode.isNotEmpty()) {
                                val childNode =
                                        MarkdownNode(
                                                type = inlineType,
                                                initialContent = contentForChildNode
                                        )
                                newNode.children.add(childNode)
                                Log.d(
                                        PARSER_TAG,
                                        "【解析】⊕ 添加内联子节点到节点 ${nodes.size-1}: type=${childNode.type}, content=\"${if (childNode.content.value.length > 20) childNode.content.value.substring(0, 17) + "..." else childNode.content.value}\""
                                )
                            } else {
                                Log.v(PARSER_TAG, "【解析】- 跳过空的内联子节点: type=$inlineType")
                            }
                        }

                val fullContent = contentBuilderForNode.toString()
                if (fullContent.isEmpty()) {
                    Log.d(PARSER_TAG, "【解析】块内容为空，跳过处理")
                    return@collect
                }
                newNode.content.value = fullContent

                Log.d(PARSER_TAG, "【解析】块内容收集与内联解析完成, 总长度: ${fullContent.length} 字符")
                Log.d(
                        PARSER_TAG,
                        "【解析】更新父节点 (index=${nodes.size-1}): content=\"${if (fullContent.length > 30) fullContent.substring(0, 27) + "..." else fullContent}\", children=${newNode.children.size}个"
                )
                Log.d(PARSER_TAG, "【解析】⟣ 内联元素解析完成")
            } else {
                // 对于没有内联格式的代码块，直接流式传输内容。
                Log.d(PARSER_TAG, "【解析】⟢ 开始处理代码块（不解析内联元素）")
                blockGroup.stream.collect { contentChunk ->
                    newNode.content.value += contentChunk
                    Log.v(
                            PARSER_TAG,
                            "【解析】追加代码块内容: \"${if(contentChunk.length > 30) contentChunk.substring(0, 27) + "..." else contentChunk}\""
                    )
                }
                Log.d(PARSER_TAG, "【解析】⟣ 代码块处理完成")
            }
            Log.d(PARSER_TAG, "═══════════════════END BLOCK TYPE: $blockType═════════════════════")
        }
        Log.d(PARSER_TAG, "【解析完成】Markdown流处理完成，共生成 ${nodes.size} 个节点")
    }

    // 渲染Markdown内容
    Surface(modifier = modifier, color = backgroundColor, shape = RoundedCornerShape(4.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            nodes.forEachIndexed { index, node ->
                key(index, node.type, node.content.value.hashCode(), node.children.size) {
                    Log.v(RENDER_TAG, "【渲染】节点: index=$index, type=${node.type}")
                    MarkdownNodeRenderer(
                            node = node,
                            textColor = textColor,
                            onLinkClick = onLinkClick
                    )
                }
            }
        }
    }
}

/** 渲染单个Markdown节点 */
@Composable
fun MarkdownNodeRenderer(
        node: MarkdownNode,
        textColor: Color,
        modifier: Modifier = Modifier,
        onLinkClick: ((String) -> Unit)? = null
) {
    val content by node.content
    val children = node.children

    Log.d(RENDER_TAG, "【渲染】◆ 节点: type=${node.type}, 内容长度=${content.length}, 子节点数量=${children.size}")

    when (node.type) {
        // 块级元素
        MarkdownProcessorType.HEADER -> {
            val level = determineHeaderLevel(content)
            val headerText = content.trimStart('#', ' ')
            Log.d(RENDER_TAG, "【渲染】标题: 级别=$level, 文本=\"$headerText\"")
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
            Log.d(RENDER_TAG, "【渲染】引用块: 子节点数量=${children.size}")
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
                            if (children.isEmpty()) {
                                Log.v(RENDER_TAG, "【渲染】引用块无子节点，直接使用内容")
                                buildAnnotatedString {
                                    append(
                                            content.lines().joinToString("\n") {
                                                it.removePrefix("> ").removePrefix(">")
                                            }
                                    )
                                }
                            } else {
                                Log.v(RENDER_TAG, "【渲染】引用块有子节点，应用样式: ${children.size}个")
                                buildAnnotatedString {
                                    appendStyledText(
                                            children,
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
            Log.d(RENDER_TAG, "【渲染】代码块: 内容长度=${content.length}")
            Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                        text =
                                content.trim().lines().joinToString("\n") {
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
            val itemContent = content.trim()
            val numberMatch = Regex("""^(\d+)\.\s*""").find(itemContent)
            val numberStr = numberMatch?.groupValues?.getOrNull(1) ?: ""
            Log.d(RENDER_TAG, "【渲染】有序列表项: 序号=$numberStr, 内容长度=${itemContent.length}")

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
                        if (children.isEmpty()) {
                            Log.v(RENDER_TAG, "【渲染】列表项无子节点，直接使用内容")
                            val streamingText =
                                    numberMatch?.let { itemContent.substring(it.range.last + 1) }
                                            ?: itemContent
                            buildAnnotatedString { append(streamingText) }
                        } else {
                            Log.v(RENDER_TAG, "【渲染】列表项有子节点，应用样式: ${children.size}个")
                            buildAnnotatedString {
                                appendStyledText(children, textColor, onLinkClick, inlineContentMap)
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
            val itemContent = content.trim()
            val markerMatch = Regex("""^[-*+]\s+""").find(itemContent)
            Log.d(RENDER_TAG, "【渲染】无序列表项: 内容长度=${itemContent.length}")

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
                        if (children.isEmpty()) {
                            Log.v(RENDER_TAG, "【渲染】列表项无子节点，直接使用内容")
                            val streamingText =
                                    markerMatch?.let { itemContent.substring(it.range.last + 1) }
                                            ?: itemContent
                            buildAnnotatedString { append(streamingText) }
                        } else {
                            Log.v(RENDER_TAG, "【渲染】列表项有子节点，应用样式: ${children.size}个")
                            buildAnnotatedString {
                                appendStyledText(children, textColor, onLinkClick, inlineContentMap)
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
            Log.d(RENDER_TAG, "【渲染】水平分割线")
            Divider(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
        MarkdownProcessorType.BLOCK_LATEX -> {
            Log.d(RENDER_TAG, "【渲染】块级LaTeX公式: 内容长度=${content.length}")
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
                    val latexContent = content.trim().removeSurrounding("$$", "$$")

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
                                    textView.text = e.message ?: "渲染失败"
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
            if (content.trim().isEmpty()) {
                Log.v(RENDER_TAG, "【渲染】纯文本内容为空，跳过渲染")
                return
            }
            Log.d(
                    RENDER_TAG,
                    "【渲染】纯文本: 内容=\"${if(content.length > 30) content.substring(0, 27) + "..." else content}\""
            )

            val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
            val inlineContent =
                    if (children.isEmpty()) {
                        buildAnnotatedString { append(content.trim()) }
                    } else {
                        buildAnnotatedString {
                            appendStyledText(children, textColor, onLinkClick, inlineContentMap)
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
            if (content.trim().isEmpty()) {
                Log.v(RENDER_TAG, "【渲染】其他类型内容为空，跳过渲染: type=${node.type}")
                return
            }
            Log.d(RENDER_TAG, "【渲染】其他类型: type=${node.type}, 内容长度=${content.length}")

            val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
            val inlineContent =
                    if (children.isEmpty()) {
                        buildAnnotatedString { append(content.trim()) }
                    } else {
                        buildAnnotatedString {
                            appendStyledText(children, textColor, onLinkClick, inlineContentMap)
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
    Log.d(RENDER_TAG, "【渲染】◇ 开始应用文本样式，处理 ${children.size} 个子节点")

    // 如果一个块有子节点，那么这些子节点就代表了全部内容。
    // 我们只需按顺序渲染每个子节点，并应用其特定的样式。
    children.forEach { child ->
        val childContent by child.content
        when (child.type) {
            MarkdownProcessorType.BOLD -> {
                Log.v(RENDER_TAG, "【渲染】应用粗体样式: \"${childContent}\"")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(childContent) }
            }
            MarkdownProcessorType.ITALIC -> {
                Log.v(RENDER_TAG, "【渲染】应用斜体样式: \"${childContent}\"")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(childContent) }
            }
            MarkdownProcessorType.INLINE_CODE -> {
                Log.v(RENDER_TAG, "【渲染】应用内联代码样式: \"${childContent}\"")
                withStyle(
                        SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color.LightGray.copy(alpha = 0.3f),
                                fontSize = 14.sp
                        )
                ) { append(childContent) }
            }
            MarkdownProcessorType.STRIKETHROUGH -> {
                Log.v(RENDER_TAG, "【渲染】应用删除线样式: \"${childContent}\"")
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(childContent)
                }
            }
            MarkdownProcessorType.UNDERLINE -> {
                Log.v(RENDER_TAG, "【渲染】应用下划线样式: \"${childContent}\"")
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(childContent)
                }
            }
            MarkdownProcessorType.LINK -> {
                val linkText = extractLinkText(childContent)
                val linkUrl = extractLinkUrl(childContent)
                Log.v(RENDER_TAG, "【渲染】应用链接样式: 文本=\"$linkText\", URL=\"$linkUrl\"")

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
                Log.v(RENDER_TAG, "【渲染】图片处理: \"${childContent}\" (暂不实现)")
                // 图片处理暂不实现
                append(childContent)
            }
            MarkdownProcessorType.INLINE_LATEX -> {
                val latexContent = childContent.trim().removeSurrounding("$$", "$$")
                Log.v(RENDER_TAG, "【渲染】应用内联LaTeX样式: \"$latexContent\"")

                val context = LocalContext.current
                val density = LocalDensity.current
                val color = textColor.toArgb()
                val textSize = MaterialTheme.typography.bodyLarge.fontSize

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
                Log.v(
                        RENDER_TAG,
                        "【渲染】应用默认样式(${child.type}): \"${if(childContent.length > 20) childContent.substring(0, 17) + "..." else childContent}\""
                )
                append(childContent)
            }
        }
    }
    Log.d(RENDER_TAG, "【渲染】◇ 文本样式应用完成")
}

/** 确定标题级别 */
private fun determineHeaderLevel(content: String): Int {
    val headerMarkers = content.takeWhile { it == '#' }.count()
    val level = minOf(headerMarkers, 6) // 标题级别最高到6
    Log.v(
            RENDER_TAG,
            "【渲染】确定标题级别: 原始标记数=$headerMarkers, 最终级别=$level, 原始内容=\"${content.take(20)}${if(content.length > 20) "..." else ""}\""
    )
    return level
}

/** 从链接Markdown中提取链接文本 例如：从 [链接文本](https://example.com) 中提取 "链接文本" */
private fun extractLinkText(linkContent: String): String {
    val startBracket = linkContent.indexOf('[')
    val endBracket = linkContent.indexOf(']')
    val result =
            if (startBracket != -1 && endBracket != -1 && startBracket < endBracket) {
                linkContent.substring(startBracket + 1, endBracket)
            } else {
                linkContent
            }
    Log.v(RENDER_TAG, "【渲染】提取链接文本: 原始=\"$linkContent\", 提取结果=\"$result\"")
    return result
}

/** 从链接Markdown中提取链接URL 例如：从 [链接文本](https://example.com) 中提取 "https://example.com" */
private fun extractLinkUrl(linkContent: String): String {
    val startParenthesis = linkContent.indexOf('(')
    val endParenthesis = linkContent.indexOf(')')
    val result =
            if (startParenthesis != -1 && endParenthesis != -1 && startParenthesis < endParenthesis
            ) {
                linkContent.substring(startParenthesis + 1, endParenthesis)
            } else {
                ""
            }
    Log.v(RENDER_TAG, "【渲染】提取链接URL: 原始=\"$linkContent\", 提取结果=\"$result\"")
    return result
}
