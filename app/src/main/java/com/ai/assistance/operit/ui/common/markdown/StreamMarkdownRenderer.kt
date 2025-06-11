package com.ai.assistance.operit.ui.common.markdown

import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.delay
import ru.noties.jlatexmath.JLatexMathDrawable
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "MarkdownRenderer"
private const val RENDER_INTERVAL_MS = 100L // 渲染间隔 0.1 秒
private const val FADE_IN_DURATION_MS = 800 // 淡入动画持续时间

// XML内容渲染器接口，用于自定义XML渲染
interface XmlContentRenderer {
    @Composable
    fun RenderXmlContent(
        xmlContent: String,
        modifier: Modifier,
        textColor: Color
    )
}

// 默认XML渲染器
class DefaultXmlRenderer : XmlContentRenderer {
    @Composable
    override fun RenderXmlContent(
        xmlContent: String, 
        modifier: Modifier, 
        textColor: Color
    ) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(8.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = "XML内容",
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = xmlContent,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = textColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 扩展函数：去除字符串首尾的所有空白字符（包括空格、制表符、换行符等） 与标准trim()相比，这个函数更明确地处理所有类型的空白字符 */
private fun String.trimAll(): String {
    return this.trim { it.isWhitespace() }
}

/** 高性能流式Markdown渲染组件 通过Jetpack Compose实现，支持流式渲染Markdown内容 使用Stream处理系统，实现高效的异步处理 */
@Composable
fun StreamMarkdownRenderer(
        markdownStream: Stream<Char>,
        modifier: Modifier = Modifier,
        textColor: Color = LocalContentColor.current,
        backgroundColor: Color = MaterialTheme.colorScheme.surface,
        onLinkClick: ((String) -> Unit)? = null,
        xmlRenderer: XmlContentRenderer = remember { DefaultXmlRenderer() }
) {
    // 原始数据收集列表
    val nodes = remember { mutableStateListOf<MarkdownNode>() }
    // 用于UI渲染的列表
    val renderNodes = remember { mutableStateListOf<MarkdownNode>() }
    // 节点动画状态映射表
    val nodeAnimationStates = remember { mutableStateMapOf<String, Boolean>() }
    // 跟踪流是否正在收集数据
    val isCollecting = remember { mutableStateOf(false) }
    // 用于防止流完成后操作
    val streamActive = remember { mutableStateOf(true) }

    // 当流实例变化时，获得一个稳定的渲染器ID
    val rendererId = remember(markdownStream) { "renderer-${System.identityHashCode(markdownStream)}" }

    // 定时渲染的副作用：仅在流收集中运行时启动
    LaunchedEffect(isCollecting.value, streamActive.value) {
        // 当流不再活跃或不再收集数据时，退出
        if (!isCollecting.value || !streamActive.value) {
            Log.d(TAG, "【渲染性能】定时渲染协程退出: isCollecting=${isCollecting.value}, streamActive=${streamActive.value}")
            return@LaunchedEffect
        }

        try {
            // 当isCollecting变为false时，此协程会被自动取消
            markdownStream.lock()
            while (streamActive.value) {
                markdownStream.unlock()
                markdownStream.lock()
                delay(RENDER_INTERVAL_MS)
                
                // 锁定后，`nodes` 列表是稳定的，可以安全地更新UI列表
                if (nodes.size > renderNodes.size) {
                    // 对新添加的节点应用淡入动画
                    for (i in renderNodes.size until nodes.size) {
                        val nodeKey = "node-$rendererId-$i-${nodes[i].type}"
                        nodeAnimationStates[nodeKey] = false // 标记为新节点，需要播放动画
                        
                        // 延迟一小段时间后启动淡入动画
                        renderNodes.add(nodes[i])
                    }
                }
                
                // 这将允许在锁定期间缓存的数据被处理并添加到`nodes`列表中。
                for (i in 0 until renderNodes.size) {
                    val nodeKey = "node-$rendererId-$i-${renderNodes[i].type}"
                    if (nodeAnimationStates.containsKey(nodeKey) && !nodeAnimationStates[nodeKey]!!) {
                        nodeAnimationStates[nodeKey] = true
                    }
                }
            }


        } catch (e: Exception) {
            Log.e(TAG, "【渲染性能】定时渲染协程异常: ${e.message}", e)
        }
    }

    // 处理Markdown流的变化
    LaunchedEffect(markdownStream) {
        Log.d(TAG, "【渲染性能】处理新的Markdown流: id=$rendererId")
        // 重置状态
        nodes.clear()
        renderNodes.clear()
        isCollecting.value = true
        streamActive.value = true

        try {
            markdownStream.streamSplitBy(NestedMarkdownProcessor.getBlockPlugins()).collect { blockGroup
                ->
                val blockType = NestedMarkdownProcessor.getTypeForPlugin(blockGroup.tag)

                // 对于水平分割线，内容无关紧要，直接添加节点
                if (blockType == MarkdownProcessorType.HORIZONTAL_RULE) {
                    nodes.add(MarkdownNode(type = blockType, initialContent = "---"))
                    return@collect
                }

                // 判断是否为LaTeX块，如果是，先作为文本节点处理
                val isLatexBlock = blockType == MarkdownProcessorType.BLOCK_LATEX
                // 临时类型：如果是LaTeX块，先作为纯文本处理
                val tempBlockType = if (isLatexBlock) MarkdownProcessorType.PLAIN_TEXT else blockType

                val isInlineContainer =
                        tempBlockType != MarkdownProcessorType.CODE_BLOCK &&
                                tempBlockType != MarkdownProcessorType.BLOCK_LATEX &&
                                tempBlockType != MarkdownProcessorType.XML_BLOCK

                // 为新块创建并添加节点
                val newNode = MarkdownNode(type = tempBlockType)
                nodes.add(newNode)
                val nodeIndex = nodes.lastIndex
                Log.d(TAG, "【渲染性能】添加新节点: 类型=${newNode.type}, 索引=$nodeIndex")

                if (isInlineContainer) {
                    // Stream-parse the block stream for inline elements
                    blockGroup.stream.streamSplitBy(NestedMarkdownProcessor.getInlinePlugins())
                            .collect { inlineGroup ->
                                val inlineType =
                                        NestedMarkdownProcessor.getTypeForPlugin(inlineGroup.tag)
                                var childNode: MarkdownNode? = null
                                var lastCharWasNewline = false // 跟踪上一个字符是否为换行符

                                inlineGroup.stream.collect { str ->
                                    // 检查是否为空白内容
                                    val isCurrentCharNewline = str == "\n" || str == "\r\n"

                                    // 处理连续换行符逻辑
                                    if (isCurrentCharNewline) {
                                        lastCharWasNewline = true
                                        return@collect
                                    }

                                    if (childNode == null) {
                                        childNode = MarkdownNode(type = inlineType)
                                        newNode.children.add(childNode!!)
                                        Log.d(TAG, "【渲染性能】添加内联子节点: 类型=${childNode!!.type}")
                                    }

                                    if (lastCharWasNewline) {
                                        // 更新父节点和子节点内容
                                        newNode.content.value += "\n" + str
                                        childNode!!.content.value += "\n" + str
                                        lastCharWasNewline = false
                                    } else {
                                        newNode.content.value += str
                                        childNode!!.content.value += str
                                    }

                                    // 更新lastCharWasNewline状态
                                    lastCharWasNewline = isCurrentCharNewline
                                }

                                // 优化：如果子节点内容经过trim后为空，则移除该子节点
                                if (childNode != null &&
                                                childNode!!.content.value.trimAll().isEmpty() &&
                                                inlineType == MarkdownProcessorType.PLAIN_TEXT
                                ) {
                                    val lastIndex = newNode.children.lastIndex
                                    if (lastIndex >= 0 && newNode.children[lastIndex] == childNode) {
                                        newNode.children.removeAt(lastIndex)
                                        Log.d(TAG, "【渲染性能】移除空内联节点")
                                    }
                                }
                            }
                } else {
                    // 对于没有内联格式的代码块，直接流式传输内容。
                    Log.d(TAG, "【渲染性能】处理无内联格式块: 类型=${tempBlockType}")
                    blockGroup.stream.collect { contentChunk -> newNode.content.value += contentChunk }
                }

                // 如果原始类型是LaTeX块，现在收集完毕，将其转换回LaTeX节点
                if (isLatexBlock) {
                    val latexContent = newNode.content.value
                    // 删除当前文本节点
                    nodes.removeAt(nodeIndex)
                    // 创建新的LaTeX节点
                    val latexNode = MarkdownNode(type = MarkdownProcessorType.BLOCK_LATEX)
                    latexNode.content.value = latexContent
                    nodes.add(latexNode)
                    Log.d(TAG, "【渲染性能】转换为LaTeX节点")
                }
            }
            
            // 收集完成
            Log.d(TAG, "【渲染性能】Markdown流处理完成，共生成 ${nodes.size} 个节点")
        } catch (e: Exception) {
            Log.e(TAG, "【渲染性能】Markdown流处理异常: ${e.message}", e)
        } finally {
            // 标记流不再活跃，防止后续操作
            streamActive.value = false
            isCollecting.value = false

            // 执行最终的渲染，确保所有节点都被显示
            if (nodes.size != renderNodes.size) {
                renderNodes.clear()
                renderNodes.addAll(nodes)
                Log.d(TAG, "【渲染性能】执行最终渲染，节点数: ${renderNodes.size}")
            }
        }
    }

    // 渲染Markdown内容
    Surface(modifier = modifier, color = Color.Transparent, shape = RoundedCornerShape(4.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            key(rendererId) {
                // 只渲染节点列表，内容更新不会触发重建
                renderNodes.forEachIndexed { index, node ->
                    // 使用节点的唯一标识符作为key，与内容无关
                    val nodeKey = "node-$rendererId-$index-${node.type}"
                    key(nodeKey) {
                        // 获取节点动画状态，默认为显示状态
                        val isVisible = nodeAnimationStates[nodeKey] ?: true
                        // 创建淡入动画
                        val alpha by animateFloatAsState(
                            targetValue = if (isVisible) 1f else 0f,
                            animationSpec = tween(durationMillis = FADE_IN_DURATION_MS),
                            label = "fadeIn"
                        )
                        
                        Box(modifier = Modifier.alpha(alpha)) {
                            StableMarkdownNodeRenderer(
                                node = node,
                                textColor = textColor,
                                modifier = Modifier,
                                onLinkClick = onLinkClick,
                                index = index,
                                xmlRenderer = xmlRenderer
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 稳定的Markdown节点渲染器 可以处理节点内容的更新而不重建组件 */
@Composable
fun StableMarkdownNodeRenderer(
        node: MarkdownNode,
        textColor: Color,
        modifier: Modifier = Modifier,
        onLinkClick: ((String) -> Unit)? = null,
        index: Int,
        xmlRenderer: XmlContentRenderer = DefaultXmlRenderer()
) {
    // 使用remember创建每个渲染器的唯一ID，用于调试
    val rendererId = remember { "node-${node.type}-$index-${System.identityHashCode(node)}" }

    // 通过观察state的方式获取内容和子节点，避免引起不必要的重组
    val content by remember { node.content }
    val children = remember { node.children }

    when (node.type) {
        // 块级元素
        MarkdownProcessorType.HEADER -> {
            val level = determineHeaderLevel(content)
            val headerText = content.trimStart('#', ' ')
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
                    text = headerText.trimAll(),
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
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .border(
                                                width = 2.dp,
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.15f
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
                                buildAnnotatedString {
                                    append(
                                            content.lines().joinToString("\n") {
                                                it.removePrefix("> ").removePrefix(">")
                                            }
                                    )
                                }
                            } else {
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
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        MarkdownProcessorType.CODE_BLOCK -> {
            Log.d(TAG, "【渲染性能】渲染代码块: id=$rendererId, 内容长度=${content.length}")

            // 提取代码内容和语言
            val codeLines = content.trimAll().lines()

            // 尝试从第一行提取语言标识符
            val firstLine = codeLines.firstOrNull() ?: ""
            val language =
                    if (firstLine.startsWith("```")) {
                        firstLine.removePrefix("```").trim()
                    } else {
                        ""
                    }

            // 提取纯代码内容
            val codeContent =
                    codeLines
                            .dropWhile { it.startsWith("```") }
                            .dropLastWhile { it.endsWith("```") }
                            .joinToString("\n")

            // 使用整个代码块的稳定标识符作为key
            // 这确保即使内容更新，组件也不会重建
            EnhancedCodeBlock(
                    code = codeContent,
                    language = language,
                    modifier = Modifier.fillMaxWidth()
            )
        }
        MarkdownProcessorType.ORDERED_LIST -> {
            val itemContent = content.trimAll()
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
                        if (children.isEmpty()) {
                            val streamingText =
                                    numberMatch?.let { itemContent.substring(it.range.last + 1) }
                                            ?: itemContent
                            buildAnnotatedString { append(streamingText) }
                        } else {
                            val modifiedChildren = children.toMutableList()
                            if (modifiedChildren.isNotEmpty()) {
                                val firstChild = modifiedChildren[0]
                                val newContent =
                                        numberMatch?.let {
                                            firstChild.content.value.substring(it.range.last + 1)
                                        }
                                                ?: firstChild.content.value
                                // 创建一个更新后的新节点替换旧节点
                                val newFirstChild = MarkdownNode(firstChild.type)
                                newFirstChild.content.value = newContent
                                newFirstChild.children.addAll(firstChild.children)
                                modifiedChildren[0] = newFirstChild
                            }
                            buildAnnotatedString {
                                appendStyledText(
                                        modifiedChildren,
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
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                )
            }
        }
        MarkdownProcessorType.UNORDERED_LIST -> {
            val itemContent = content.trimAll()
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
                        if (children.isEmpty()) {
                            val streamingText =
                                    markerMatch?.let { itemContent.substring(it.range.last + 1) }
                                            ?: itemContent
                            buildAnnotatedString { append(streamingText) }
                        } else {
                            val modifiedChildren = children.toMutableList()
                            if (modifiedChildren.isNotEmpty()) {
                                val firstChild = modifiedChildren[0]
                                val newContent =
                                        markerMatch?.let {
                                            firstChild.content.value.substring(it.range.last + 1)
                                        }
                                                ?: firstChild.content.value
                                val newFirstChild = MarkdownNode(firstChild.type)
                                newFirstChild.content.value = newContent
                                newFirstChild.children.addAll(firstChild.children)
                                modifiedChildren[0] = newFirstChild
                            }
                            buildAnnotatedString {
                                appendStyledText(
                                        modifiedChildren,
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
                        style = MaterialTheme.typography.bodyMedium,
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
                    color = Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                ) {
                    // 提取LaTeX内容，移除$$分隔符
                    val latexContent = content.trimAll().removeSurrounding("$$", "$$")

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
                                                                    14f *
                                                                            textView.resources
                                                                                    .displayMetrics
                                                                                    .density
                                                            )
                                                            .padding(4)
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
        MarkdownProcessorType.TABLE -> {
            Log.d(TAG, "【渲染性能】渲染表格: id=$rendererId")

            // 使用增强型表格组件
            EnhancedTableBlock(
                    tableContent = content,
                    textColor = textColor,
                    modifier = Modifier.fillMaxWidth()
            )
        }

        // 添加XML块渲染支持
        MarkdownProcessorType.XML_BLOCK -> {
            Log.d(TAG, "【渲染性能】渲染XML块: id=$rendererId, 内容长度=${content.length}")
            xmlRenderer.RenderXmlContent(
                xmlContent = content,
                modifier = Modifier.fillMaxWidth(),
                textColor = textColor
            )
        }

        // 添加对图片的专门处理
        MarkdownProcessorType.IMAGE -> {
            val imageContent = content.trimAll()
            if (isCompleteImageMarkdown(imageContent)) {
                // 使用更紧凑的图片渲染
                Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp) // 最小的垂直内边距
                ) {
                    MarkdownImageRenderer(
                            imageMarkdown = imageContent,
                            modifier = Modifier.fillMaxWidth(),
                            maxImageHeight = 140 // 更小的最大高度
                    )
                }
            } else {
                // 不完整的图片标记作为普通文本处理
                Text(
                        text = imageContent,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
        }

        // 内联元素通常不会直接渲染，只在父节点中应用样式
        MarkdownProcessorType.PLAIN_TEXT -> {
            if (content.trimAll().isEmpty()) {
                return
            }

            val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
            val inlineContent =
                    if (children.isEmpty()) {
                        buildAnnotatedString { append(content.trimAll()) }
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
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
            )
        }

        // 如果节点类型不在上述处理范围内，则作为普通文本处理
        else -> {
            if (content.trimAll().isEmpty()) {
                return
            }

            val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
            val inlineContent =
                    if (children.isEmpty()) {
                        buildAnnotatedString { append(content.trimAll()) }
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
                    style = MaterialTheme.typography.bodyMedium,
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
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(child.content.value) }
            }
            MarkdownProcessorType.ITALIC -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(child.content.value) }
            }
            MarkdownProcessorType.INLINE_CODE -> {
                withStyle(
                        SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color.LightGray.copy(alpha = 0.15f),
                                fontSize = 14.sp
                        )
                ) { append(child.content.value) }
            }
            MarkdownProcessorType.STRIKETHROUGH -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(child.content.value)
                }
            }
            MarkdownProcessorType.UNDERLINE -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(child.content.value)
                }
            }
            MarkdownProcessorType.LINK -> {
                val linkText = extractLinkText(child.content.value)
                val linkUrl = extractLinkUrl(child.content.value)

                pushStringAnnotation("URL", linkUrl)
                withStyle(
                        SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                        )
                ) { append(linkText) }
                pop()
            }
            MarkdownProcessorType.INLINE_LATEX -> {
                val latexContent = child.content.value.trimAll().removeSurrounding("$$", "$$")

                val context = LocalContext.current
                val density = LocalDensity.current
                val color = textColor.toArgb()
                val textSize = MaterialTheme.typography.bodyMedium.fontSize

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
                append(child.content.value)
            }
        }
    }
}

/** 确定标题级别 */
private fun determineHeaderLevel(content: String): Int {
    val headerMarkers = content.takeWhile { it == '#' }.count()
    val level = minOf(headerMarkers, 6) // 标题级别最高到6
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
    return result
}

/** 从图片Markdown中提取替代文本 例如：从 ![替代文本](https://example.com/image.jpg) 中提取 "替代文本" */
private fun extractImageAlt(imageContent: String): String {
    val startBracket = imageContent.indexOf('[')
    val endBracket = imageContent.indexOf(']')
    val result =
            if (startBracket != -1 && endBracket != -1 && startBracket < endBracket) {
                imageContent.substring(startBracket + 1, endBracket)
            } else {
                "图片"
            }
    return result
}

/**
 * 从图片Markdown中提取图片URL 例如：从 ![替代文本](https://example.com/image.jpg) 中提取
 * "https://example.com/image.jpg"
 */
private fun extractImageUrl(imageContent: String): String {
    val startParenthesis = imageContent.indexOf('(')
    val endParenthesis = imageContent.indexOf(')')
    val result =
            if (startParenthesis != -1 && endParenthesis != -1 && startParenthesis < endParenthesis
            ) {
                imageContent.substring(startParenthesis + 1, endParenthesis)
            } else {
                ""
            }
    return result
}
