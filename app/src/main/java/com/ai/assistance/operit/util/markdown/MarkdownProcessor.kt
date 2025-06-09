package com.ai.assistance.operit.util.markdown

import com.ai.assistance.operit.util.stream.*
import com.ai.assistance.operit.util.stream.plugins.*
import com.ai.assistance.operit.util.stream.splitBy as streamSplitBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 字符串收集处理器 - 简单地将流收集为字符串 */
class StringCollectorProcessor : StreamProcessor<String, String> {
    override suspend fun process(stream: Stream<String>): String {
        val content = StringBuilder()
        stream.collect { content.append(it) }
        return content.toString()
    }
}

/** Markdown处理器类型枚举 */
enum class MarkdownProcessorType {
    // 块级处理器
    HEADER,
    BLOCK_QUOTE,
    CODE_BLOCK,
    ORDERED_LIST,
    UNORDERED_LIST,
    HORIZONTAL_RULE,

    // 内联处理器
    BOLD,
    ITALIC,
    INLINE_CODE,
    LINK,
    IMAGE,
    STRIKETHROUGH,
    UNDERLINE,

    // 纯文本
    PLAIN_TEXT
}

/** Markdown数据模型 */
data class MarkdownNode(
        val type: MarkdownProcessorType,
        val content: String,
        val children: MutableList<MarkdownNode> = mutableListOf()
)

/** 将字符串转换为字符流 */
fun String.toCharStream(): Stream<Char> {
    return stream {
        for (c in this@toCharStream) {
            emit(c)
        }
    }
}

/** Markdown结果处理器 - 生成MarkdownNode模型 */
class MarkdownNodeProcessor(private val type: MarkdownProcessorType) :
        StreamProcessor<String, MarkdownNode> {
    override suspend fun process(stream: Stream<String>): MarkdownNode =
            withContext(Dispatchers.Default) {
                val contentBuilder = StringBuilder()
                stream.collect { contentBuilder.append(it) }
                MarkdownNode(type, contentBuilder.toString())
            }
}

/** 递归Markdown处理器 - 在各级嵌套中应用不同的处理逻辑 */
object NestedMarkdownProcessor {

    /** 块级插件列表 */
    fun getBlockPlugins(): List<StreamPlugin> =
            listOf(
                    StreamMarkdownHeaderPlugin(),
                    StreamMarkdownFencedCodeBlockPlugin(),
                    StreamMarkdownBlockQuotePlugin(),
                    StreamMarkdownOrderedListPlugin(),
                    StreamMarkdownUnorderedListPlugin(),
                    StreamMarkdownHorizontalRulePlugin()
            )

    /** 内联插件列表 */
    fun getInlinePlugins(): List<StreamPlugin> =
            listOf(
                    StreamMarkdownBoldPlugin(includeAsterisks = false),
                    StreamMarkdownItalicPlugin(includeAsterisks = false),
                    StreamMarkdownInlineCodePlugin(),
                    StreamMarkdownLinkPlugin(),
                    StreamMarkdownImagePlugin(),
                    StreamMarkdownStrikethroughPlugin(),
                    StreamMarkdownUnderlinePlugin()
            )

    /**
     * 处理Markdown文本，生成嵌套的StreamGroup结构
     * @param text 原始Markdown文本
     * @return 包含嵌套结构的根StreamGroup
     */
    suspend fun process(text: String): StreamGroup<MarkdownProcessorType> {
        val charStream = text.toCharStream()
        return processWithPlugins(charStream, getBlockPlugins(), getInlinePlugins())
    }

    /**
     * 使用给定的插件列表处理字符流
     * @param charStream 原始字符流
     * @param blockPlugins 块级插件列表
     * @param inlinePlugins 内联插件列表
     * @return 处理后的StreamGroup结构
     */
    suspend fun processWithPlugins(
            charStream: Stream<Char>,
            blockPlugins: List<StreamPlugin>,
            inlinePlugins: List<StreamPlugin>
    ): StreamGroup<MarkdownProcessorType> {

        // 创建根组
        val rootBuilder =
                StreamGroupBuilder<MarkdownProcessorType>()
                        .tag(MarkdownProcessorType.PLAIN_TEXT)
                        .stream(streamOf("")) // 临时流，后面会被替换

        // 收集块级分组
        val blockGroups = mutableListOf<StreamGroup<MarkdownProcessorType>>()

        // 先用块级插件处理
        charStream.streamSplitBy(blockPlugins).collect { blockGroup ->
            val blockType = getTypeForPlugin(blockGroup.tag)
            val blockProcessor = MarkdownNodeProcessor(blockType)

            val contentBuffer = StringBuilder()

            // 收集块内容到buffer
            blockGroup.stream.collect { s: String ->
                contentBuffer.append(s)

                // 如果块已经结束或累积了足够多的内容，可以进行内联处理
                if (contentBuffer.isNotEmpty() && (s.endsWith("\n") || contentBuffer.length > 100)
                ) {
                    val content = contentBuffer.toString()
                    contentBuffer.clear()

                    // 如果是代码块，不做内联处理
                    if (blockType == MarkdownProcessorType.CODE_BLOCK) {
                        val codeStream = streamOf(content)
                        val codeGroup = StreamGroup(blockType, codeStream, blockProcessor)
                        blockGroups.add(codeGroup)
                    } else {
                        // 对块内容再次应用内联插件
                        val charContent = content.toCharStream()

                        // 创建内联处理的构建器
                        val inlineGroupBuilder =
                                StreamGroupBuilder<MarkdownProcessorType>()
                                        .tag(blockType)
                                        .processor(blockProcessor)

                        // 处理内联
                        processInlineContentSuspend(charContent, inlinePlugins) { inlineGroup ->
                            inlineGroupBuilder.addChild(inlineGroup)
                        }

                        // 此时inlineGroupBuilder还没有stream，需要设置
                        inlineGroupBuilder.stream(streamOf(content))

                        blockGroups.add(inlineGroupBuilder.build())
                    }
                }
            }

            // 处理剩余内容
            if (contentBuffer.isNotEmpty()) {
                val content = contentBuffer.toString()

                // 如果是代码块，不做内联处理
                if (blockType == MarkdownProcessorType.CODE_BLOCK) {
                    val codeStream = streamOf(content)
                    val codeGroup = StreamGroup(blockType, codeStream, blockProcessor)
                    blockGroups.add(codeGroup)
                } else {
                    // 对块内容再次应用内联插件
                    val charContent = content.toCharStream()

                    // 创建内联处理的构建器
                    val inlineGroupBuilder =
                            StreamGroupBuilder<MarkdownProcessorType>()
                                    .tag(blockType)
                                    .processor(blockProcessor)

                    // 处理内联
                    processInlineContentSuspend(charContent, inlinePlugins) { inlineGroup ->
                        inlineGroupBuilder.addChild(inlineGroup)
                    }

                    // 此时inlineGroupBuilder还没有stream，需要设置
                    inlineGroupBuilder.stream(streamOf(content))

                    blockGroups.add(inlineGroupBuilder.build())
                }
            }
        }

        // 将所有块级组添加为根组的子组
        blockGroups.forEach { rootBuilder.addChild(it) }

        // 获取整个文本作为根组的内容
        val fullText = StringBuilder()
        val textStream = charStream.map<Char, String> { char -> char.toString() }
        textStream.collect { str -> fullText.append(str) }

        return rootBuilder
                .stream(streamOf(fullText.toString()))
                .processor(StringCollectorProcessor())
                .build()
    }

    /** 处理内联内容（挂起函数版） */
    private suspend fun processInlineContentSuspend(
            charContent: Stream<Char>,
            inlinePlugins: List<StreamPlugin>,
            onGroupFound: (StreamGroup<MarkdownProcessorType>) -> Unit
    ) {
        charContent.streamSplitBy(inlinePlugins).collect { inlineGroup ->
            val inlineType = getTypeForPlugin(inlineGroup.tag)
            val inlineProcessor = MarkdownNodeProcessor(inlineType)

            val contentBuilder = StringBuilder()
            inlineGroup.stream.collect { str -> contentBuilder.append(str) }
            val content = contentBuilder.toString()

            val newGroup = StreamGroup(inlineType, streamOf(content), inlineProcessor)

            onGroupFound(newGroup)
        }
    }

    /** 根据插件获取对应的Markdown处理器类型 */
    internal fun getTypeForPlugin(plugin: StreamPlugin?): MarkdownProcessorType {
        return when (plugin) {
            is StreamMarkdownHeaderPlugin -> MarkdownProcessorType.HEADER
            is StreamMarkdownBlockQuotePlugin -> MarkdownProcessorType.BLOCK_QUOTE
            is StreamMarkdownFencedCodeBlockPlugin -> MarkdownProcessorType.CODE_BLOCK
            is StreamMarkdownOrderedListPlugin -> MarkdownProcessorType.ORDERED_LIST
            is StreamMarkdownUnorderedListPlugin -> MarkdownProcessorType.UNORDERED_LIST
            is StreamMarkdownHorizontalRulePlugin -> MarkdownProcessorType.HORIZONTAL_RULE
            is StreamMarkdownBoldPlugin -> MarkdownProcessorType.BOLD
            is StreamMarkdownItalicPlugin -> MarkdownProcessorType.ITALIC
            is StreamMarkdownInlineCodePlugin -> MarkdownProcessorType.INLINE_CODE
            is StreamMarkdownLinkPlugin -> MarkdownProcessorType.LINK
            is StreamMarkdownImagePlugin -> MarkdownProcessorType.IMAGE
            is StreamMarkdownStrikethroughPlugin -> MarkdownProcessorType.STRIKETHROUGH
            is StreamMarkdownUnderlinePlugin -> MarkdownProcessorType.UNDERLINE
            else -> MarkdownProcessorType.PLAIN_TEXT
        }
    }
}

/** UI绑定器 - 将处理后的StreamGroup绑定到UI渲染组件 这里使用抽象类型T表示UI组件，你可以实现具体的渲染逻辑 */
class MarkdownUIBinder<T>(
        private val component: T,
        private val renderStrategy: suspend (T, MarkdownNode) -> Unit
) {
    /** 绑定StreamGroup到UI组件 */
    suspend fun bind(group: StreamGroup<MarkdownProcessorType>) {
        // 递归处理所有组
        val nodes = processGroupToNodes(group)
        renderStrategy(component, nodes)
    }

    /** 将StreamGroup转换为MarkdownNode树 */
    private suspend fun processGroupToNodes(
            group: StreamGroup<MarkdownProcessorType>
    ): MarkdownNode {
        // 处理当前组
        val content = StringBuilder()
        group.stream.collect { content.append(it) }

        val node = MarkdownNode(group.tag, content.toString())

        // 递归处理子组
        for (child in group.children) {
            @Suppress("UNCHECKED_CAST") val childGroup = child as StreamGroup<MarkdownProcessorType>
            val childNode = processGroupToNodes(childGroup)
            node.children.add(childNode)
        }

        return node
    }
}
