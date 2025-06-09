package com.ai.assistance.operit.util.stream

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.markdown.*
import com.ai.assistance.operit.util.stream.plugins.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 字符串收集处理器 - 简单地将流收集为字符串 */
class StringCollectorProcessor : StreamProcessor<String, String> {
    override suspend fun process(stream: Stream<String>): String {
        val content = StringBuilder()
        stream.collect { content.append(it) }
        return content.toString()
    }
}

@RunWith(AndroidJUnit4::class)
class NestedMarkdownProcessorTest {

    @Test
    fun testNestedProcessing() = runBlocking {
        val markdown =
                """
            # 标题
            
            这是一段包含**粗体**和*斜体*文字。
            
            > 引用块内容
            > 引用的第二行包含`内联代码`
            
            - 列表项1
            - 列表项2包含[链接](https://example.com)
            
            ```
            代码块内容
            不解析嵌套格式
            ```
            
            1. 有序列表项1
            2. 有序列表项2
        """.trimIndent()

        // 执行嵌套处理
        val rootGroup = NestedMarkdownProcessor.process(markdown)

        // 验证根组
        assertNotNull("根组不应为空", rootGroup)
        assertEquals("根组标签应为PLAIN_TEXT", MarkdownProcessorType.PLAIN_TEXT, rootGroup.tag)
        assertTrue("根组应有子组", rootGroup.children.isNotEmpty())

        // 验证至少存在一个标题组
        val hasHeader =
                rootGroup.children.any {
                    (it as StreamGroup<*>).tag == MarkdownProcessorType.HEADER
                }
        assertTrue("应该存在标题组", hasHeader)

        // 构建节点树
        val rootNode = buildNodeTree(rootGroup)

        // 验证节点树
        assertEquals(MarkdownProcessorType.PLAIN_TEXT, rootNode.type)
        assertTrue("节点树应有子节点", rootNode.children.isNotEmpty())

        // 验证特定类型的节点存在
        val nodeTypes = rootNode.children.map { it.type }.toSet()
        assertTrue("应存在标题节点", MarkdownProcessorType.HEADER in nodeTypes)
        assertTrue("应存在引用块节点", MarkdownProcessorType.BLOCK_QUOTE in nodeTypes)
        assertTrue("应存在无序列表节点", MarkdownProcessorType.UNORDERED_LIST in nodeTypes)
        assertTrue("应存在代码块节点", MarkdownProcessorType.CODE_BLOCK in nodeTypes)
        assertTrue("应存在有序列表节点", MarkdownProcessorType.ORDERED_LIST in nodeTypes)
    }

    @Test
    fun testNestedListsWithInlineFormats() = runBlocking {
        val markdown =
                """
            - 这是包含**粗体**的列表项
            - 这是包含*斜体*的列表项
            - 这是包含`代码`的列表项
        """.trimIndent()

        // 处理Markdown
        val result = processNestedList(markdown)

        // 验证结果包含完整内容
        assertTrue("结果应包含粗体文本", result.contains("**粗体**"))
        assertTrue("结果应包含斜体文本", result.contains("*斜体*"))
        assertTrue("结果应包含代码文本", result.contains("`代码`"))
    }

    /** 构建节点树的辅助方法 */
    private suspend fun buildNodeTree(group: StreamGroup<MarkdownProcessorType>): MarkdownNode {
        // 收集当前组的内容
        val content = StringBuilder()
        group.stream.collect { content.append(it) }

        val node = MarkdownNode(group.tag, content.toString())

        // 递归处理所有子组
        for (child in group.children) {
            @Suppress("UNCHECKED_CAST") val childGroup = child as StreamGroup<MarkdownProcessorType>
            val childNode = buildNodeTree(childGroup)
            node.children.add(childNode)
        }

        return node
    }

    /** 处理嵌套列表的辅助方法 */
    private suspend fun processNestedList(markdown: String): String {
        val charStream = markdown.toCharStream()

        // 先识别列表项
        val blockPlugins =
                listOf(StreamMarkdownOrderedListPlugin(), StreamMarkdownUnorderedListPlugin())

        // 列表项内可能包含的内联格式
        val inlinePlugins =
                listOf(
                        StreamMarkdownBoldPlugin(),
                        StreamMarkdownItalicPlugin(),
                        StreamMarkdownInlineCodePlugin(),
                        StreamMarkdownLinkPlugin()
                )

        // 处理流
        val result =
                NestedMarkdownProcessor.processWithPlugins(charStream, blockPlugins, inlinePlugins)

        // 使用StringCollectorProcessor处理结果
        val processor = StringCollectorProcessor()
        return processor.process(result.stream)
    }
}
