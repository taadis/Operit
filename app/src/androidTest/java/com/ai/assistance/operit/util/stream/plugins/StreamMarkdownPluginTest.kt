package com.ai.assistance.operit.util.stream.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.stream.asCharStream
import com.ai.assistance.operit.util.stream.splitBy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamMarkdownPluginTest {

    // --- 测试基本的 Markdown 粗体插件 ---
    @Test
    fun testBoldPlugin() = runBlocking {
        val boldText = "这是一段**粗体文字**普通文字"
        val stream = boldText.asCharStream()
        val plugin = StreamMarkdownBoldPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("这是一段", groups[0].content)

        // 第二组：粗体文本
        assertSame(plugin, groups[1].tag)
        assertEquals("**粗体文字**", groups[1].content)

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("普通文字", groups[2].content)
    }

    // --- 测试基本的 Markdown 斜体插件 ---
    @Test
    fun testItalicPlugin() = runBlocking {
        val italicText = "普通文字*斜体内容*后面的文字"
        val stream = italicText.asCharStream()
        val plugin = StreamMarkdownItalicPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("普通文字", groups[0].content)

        // 第二组：斜体文本
        assertSame(plugin, groups[1].tag)
        assertEquals("*斜体内容*", groups[1].content)

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("后面的文字", groups[2].content)
    }

    // --- 测试行内代码插件 ---
    @Test
    fun testInlineCodePlugin() = runBlocking {
        val codeText = "这是`行内代码`示例"
        val stream = codeText.asCharStream()
        val plugin = StreamMarkdownInlineCodePlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)
        assertNull(groups[0].tag)
        assertEquals("这是", groups[0].content)

        assertSame(plugin, groups[1].tag)
        assertEquals("`行内代码`", groups[1].content)

        assertNull(groups[2].tag)
        assertEquals("示例", groups[2].content)
    }

    // --- 测试代码块插件 ---
    @Test
    fun testFencedCodeBlockPlugin() = runBlocking {
        val codeBlock =
                """
            前置文本
            ```
            代码块内容
            多行代码
            ```
            后置文本
        """.trimIndent()

        val stream = codeBlock.asCharStream()
        val plugin = StreamMarkdownFencedCodeBlockPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：前置文本
        assertNull(groups[0].tag)
        assertTrue(groups[0].content.contains("前置文本"))

        // 第二组：代码块
        assertSame(plugin, groups[1].tag)
        assertTrue(groups[1].content.contains("```"))
        assertTrue(groups[1].content.contains("代码块内容"))
        assertTrue(groups[1].content.contains("多行代码"))

        // 第三组：后置文本
        assertNull(groups[2].tag)
        assertTrue(groups[2].content.contains("后置文本"))
    }

    // --- 测试标题插件 ---
    @Test
    fun testHeaderPlugin() = runBlocking {
        val headerText =
                """
            # 一级标题
            普通文字
            ## 二级标题
            更多内容
        """.trimIndent()

        val stream = headerText.asCharStream()
        val plugin = StreamMarkdownHeaderPlugin()

        val groups = collectGroups(stream, plugin)

        assertEquals(4, groups.size)

        // 第一组：一级标题
        assertSame(plugin, groups[0].tag)
        assertTrue(groups[0].content.contains("# 一级标题"))

        // 第二组：普通文字
        assertNull(groups[1].tag)
        assertEquals("普通文字\n", groups[1].content)

        // 第三组：二级标题
        assertSame(plugin, groups[2].tag)
        assertTrue(groups[2].content.contains("## 二级标题"))

        // 第四组：后续内容
        assertNull(groups[3].tag)
        assertEquals("更多内容", groups[3].content)
    }

    // --- 测试多个插件共同工作 ---
    @Test
    fun testMultipleMarkdownPlugins() = runBlocking {
        val markdownText =
                """
            # 标题
            
            这是**粗体**和*斜体*，还有`代码`。
            
            ```
            代码块
            ```
        """.trimIndent()

        val stream = markdownText.asCharStream()
        // 注意顺序很重要，长分隔符的插件必须排在前面
        val plugins =
                listOf(
                        StreamMarkdownFencedCodeBlockPlugin(),
                        StreamMarkdownItalicPlugin(),
                        StreamMarkdownBoldPlugin(),
                        StreamMarkdownInlineCodePlugin(),
                        StreamMarkdownHeaderPlugin()
                )

        val groupList = mutableListOf<GroupInfo>()
        stream.splitBy(plugins).collect { group ->
            val content = StringBuilder()
            group.stream.collect { content.append(it) }
            groupList.add(GroupInfo(group.tag, content.toString()))
        }

        // 验证至少所有插件类型都被找到
        assertTrue("应找到标题插件", groupList.any { it.tag is StreamMarkdownHeaderPlugin })
        assertTrue("应找到粗体插件", groupList.any { it.tag is StreamMarkdownBoldPlugin })
        assertTrue("应找到斜体插件", groupList.any { it.tag is StreamMarkdownItalicPlugin })
        assertTrue("应找到行内代码插件", groupList.any { it.tag is StreamMarkdownInlineCodePlugin })
        assertTrue("应找到代码块插件", groupList.any { it.tag is StreamMarkdownFencedCodeBlockPlugin })
    }

    // --- 测试不包含分隔符 ---
    @Test
    fun testPluginsWithoutDelimiters() = runBlocking {
        val boldText = "这是一段**粗体文字**普通文字"
        val stream = boldText.asCharStream()
        // 不包含分隔符
        val plugin = StreamMarkdownBoldPlugin(includeAsterisks = false)

        val groups = collectGroups(stream, plugin)

        assertEquals(3, groups.size)

        // 第一组：普通文本
        assertNull(groups[0].tag)
        assertEquals("这是一段", groups[0].content)

        // 第二组：粗体文本（不含分隔符）
        assertSame(plugin, groups[1].tag)
        assertEquals("粗体文字", groups[1].content) // 不含**

        // 第三组：普通文本
        assertNull(groups[2].tag)
        assertEquals("普通文字", groups[2].content)
    }

    // --- 测试异常情况 ---
    @Test
    fun testEdgeCases() = runBlocking {
        // 1. 测试不闭合的粗体
        val unclosedBold = "这是**不闭合的粗体"
        val boldPlugin = StreamMarkdownBoldPlugin()

        val unclosedGroups = collectGroups(unclosedBold.asCharStream(), boldPlugin)

        assertEquals(2, unclosedGroups.size)
        assertNull(unclosedGroups[0].tag)

        // 2. 测试换行中断的行内代码
        val newlineInCode = "这是`行内\n代码`测试"
        val codePlugin = StreamMarkdownInlineCodePlugin()

        val newlineGroups = collectGroups(newlineInCode.asCharStream(), codePlugin)
        // 行内代码中断，判定为4
        assertEquals(4, newlineGroups.size)
        assertNull(newlineGroups[0].tag)

        // 3. 测试过长的标题（超过6个#）
        val tooLongHeader = "####### 这不是有效标题"
        val headerPlugin = StreamMarkdownHeaderPlugin()

        val headerGroups = collectGroups(tooLongHeader.asCharStream(), headerPlugin)
        // 不应被识别为标题
        println(headerGroups[0].content)
        assertEquals(1, headerGroups.size)
        assertNull(headerGroups[0].tag)
    }

    // --- Helper function ---
    private suspend fun collectGroups(
            stream: com.ai.assistance.operit.util.stream.Stream<Char>,
            plugin: StreamPlugin
    ): List<GroupInfo> {
        val groupList = mutableListOf<GroupInfo>()
        stream.splitBy(listOf(plugin)).collect { group ->
            val content = StringBuilder()
            group.stream.collect { content.append(it) }
            groupList.add(GroupInfo(group.tag, content.toString()))
        }
        return groupList
    }

    private data class GroupInfo(val tag: StreamPlugin?, val content: String)
}
