package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.markdown.NestedMarkdownProcessor
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamGroup
import com.ai.assistance.operit.util.stream.map
import com.ai.assistance.operit.util.stream.plugins.StreamPlugin
import com.ai.assistance.operit.util.stream.splitBy as streamSplitBy
import com.ai.assistance.operit.util.stream.stream

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
        nodes.clear() // 在流实例更改时清除节点

        markdownStream.streamSplitBy(NestedMarkdownProcessor.getBlockPlugins()).collect { blockGroup ->
            val blockType = NestedMarkdownProcessor.getTypeForPlugin(blockGroup.tag)

            // 对于水平分割线，内容无关紧要，直接添加节点
            if (blockType == MarkdownProcessorType.HORIZONTAL_RULE) {
                nodes.add(MarkdownNode(type = blockType, content = "---"))
                return@collect
            }

            val isInlineContainer = blockType != MarkdownProcessorType.CODE_BLOCK

            // 为新块创建并添加节点
            val newNode = MarkdownNode(type = blockType, content = "", children = mutableListOf())
            val nodeIndex = nodes.size
            nodes.add(newNode)

            if (isInlineContainer) {
                val contentBuilder = StringBuilder()
                blockGroup.stream.collect { contentChunk ->
                    val currentNode = nodes[nodeIndex]
                    nodes[nodeIndex] = currentNode.copy(content = currentNode.content + contentChunk)
                    contentBuilder.append(contentChunk)
                }
                
                val blockContent = contentBuilder.toString()
                if (blockContent.isEmpty()) return@collect

                var contentForInlineParsing = blockContent
                when (blockType) {
                    MarkdownProcessorType.ORDERED_LIST -> {
                        val markerMatch = Regex("""^(\d+)\.\s*""").find(blockContent)
                        contentForInlineParsing = markerMatch?.let { blockContent.substring(it.range.last + 1) } ?: blockContent
                    }
                    MarkdownProcessorType.UNORDERED_LIST -> {
                        val markerMatch = Regex("""^[-*+]\s+""").find(blockContent)
                        contentForInlineParsing = markerMatch?.let { blockContent.substring(it.range.last + 1) } ?: blockContent
                    }
                    MarkdownProcessorType.BLOCK_QUOTE -> {
                        contentForInlineParsing = blockContent.lines().joinToString("\n") { it.removePrefix("> ").removePrefix(">") }
                    }
                    else -> { /* No change needed */ }
                }

                val inlineChildren = mutableListOf<MarkdownNode>()
                val charStream = stream {
                    for (char in contentForInlineParsing) {
                        emit(char)
                    }
                }

                charStream.streamSplitBy(NestedMarkdownProcessor.getInlinePlugins()).collect { inlineGroup ->
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
                    nodes[nodeIndex] = nodes[nodeIndex].copy(children = inlineChildren)
                }

            } else {
                // 对于没有内联格式的代码块，直接流式传输内容。
                blockGroup.stream.collect { contentChunk ->
                    val currentNode = nodes[nodeIndex]
                    nodes[nodeIndex] = currentNode.copy(content = currentNode.content + contentChunk)
                }
            }
        }
    }

    // 渲染Markdown内容
    Surface(modifier = modifier, color = backgroundColor, shape = RoundedCornerShape(4.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            nodes.forEach { node ->
                MarkdownNodeRenderer(node = node, textColor = textColor, onLinkClick = onLinkClick)
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
    when (node.type) {
        // 块级元素
        MarkdownProcessorType.HEADER -> {
            val level = determineHeaderLevel(node.content)
            val headerText = node.content.trimStart('#', ' ')
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
                    val inlineContent = if (node.children.isEmpty()) {
                        buildAnnotatedString { append(node.content.lines().joinToString("\n") { it.removePrefix("> ").removePrefix(">") }) }
                    } else {
                        buildAnnotatedString {
                            appendStyledText(node.children, textColor, onLinkClick)
                        }
                    }
                    
                    Text(
                            text = inlineContent,
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
                                node.content.trim().lines().joinToString("\n") {
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
            val itemContent = node.content.trim()
            val numberMatch = Regex("""^(\d+)\.\s*""").find(itemContent)
            val numberStr = numberMatch?.groupValues?.getOrNull(1) ?: ""
            
            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 1.dp), verticalAlignment = Alignment.Top) {
                Text(
                        text = "$numberStr.",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                )
                
                val inlineContent = if (node.children.isEmpty()) {
                    val streamingText = numberMatch?.let { itemContent.substring(it.range.last + 1) } ?: itemContent
                    buildAnnotatedString { append(streamingText) }
                } else {
                    buildAnnotatedString {
                        appendStyledText(node.children, textColor, onLinkClick)
                    }
                }

                Text(
                        text = inlineContent,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                )
            }
        }
        MarkdownProcessorType.UNORDERED_LIST -> {
            val itemContent = node.content.trim()
            val markerMatch = Regex("""^[-*+]\s+""").find(itemContent)

            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 1.dp), verticalAlignment = Alignment.Top) {
                Text(
                        text = "•",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                )
                val inlineContent = if (node.children.isEmpty()) {
                    val streamingText = markerMatch?.let { itemContent.substring(it.range.last + 1) } ?: itemContent
                    buildAnnotatedString { append(streamingText) }
                } else {
                    buildAnnotatedString {
                        appendStyledText(node.children, textColor, onLinkClick)
                    }
                }

                Text(
                        text = inlineContent,
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

        // 内联元素通常不会直接渲染，只在父节点中应用样式
        MarkdownProcessorType.PLAIN_TEXT -> {
            if (node.content.trim().isEmpty()) return

            val inlineContent = if (node.children.isEmpty()) {
                buildAnnotatedString { append(node.content.trim()) }
            } else {
                buildAnnotatedString {
                    appendStyledText(node.children, textColor, onLinkClick)
                }
            }

            if (inlineContent.isEmpty()) return

            Text(
                    text = inlineContent,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
            )
        }

        // 如果节点类型不在上述处理范围内，则作为普通文本处理
        else -> {
            if (node.content.trim().isEmpty()) return

            val inlineContent = if (node.children.isEmpty()) {
                buildAnnotatedString { append(node.content.trim()) }
            } else {
                buildAnnotatedString {
                    appendStyledText(node.children, textColor, onLinkClick)
                }
            }

            if (inlineContent.isEmpty()) return

            Text(
                    text = inlineContent,
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
        onLinkClick: ((String) -> Unit)?
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
            else -> {
                // 默认情况，通常是 PLAIN_TEXT
                append(child.content)
            }
        }
    }
}

/** 确定标题级别 */
private fun determineHeaderLevel(content: String): Int {
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
