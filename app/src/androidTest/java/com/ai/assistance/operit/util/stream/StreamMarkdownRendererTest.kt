package com.ai.assistance.operit.util.stream

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownDemoScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

/**
 * 流式Markdown渲染器的演示测试
 * 展示字节一个一个发送的效果和发送间隔
 */
@RunWith(AndroidJUnit4::class)
class StreamMarkdownRendererTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 测试流式Markdown渲染
     * 字节一个一个发送，展示流式效果
     */
    @Test
    fun testStreamMarkdownRendering() {
        // 设置测试的超时时间
        composeRule.mainClock.autoAdvance = true

        // 启动测试UI
        composeRule.setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                StreamMarkdownDemoScreen()
            }
        }

        // 验证界面基础元素存在
        composeRule.onNodeWithText("控制面板").assertExists()
        composeRule.onNodeWithText("开始/继续").assertExists()
        
        // 验证状态指示器存在
        composeRule.onNodeWithText("状态: 已暂停", substring = true).assertExists()
        
        // 点击开始按钮
        composeRule.onNodeWithText("开始/继续").performClick()
        
        // 等待状态变化，而不是直接检查内容
        composeRule.waitUntil(5000) {
            try {
                composeRule.onNodeWithText("状态: 流式传输中", substring = true).assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        // 验证速度指示器存在
        composeRule.onNodeWithText("速度:", substring = true).assertExists()
        
        // 验证进度指示器存在
        composeRule.onNodeWithText("进度:", substring = true).assertExists()
    }
}

/**
 * 流式Markdown演示界面
 * 模拟字节一个一个发送的效果
 */
@Composable
private fun StreamMarkdownDemoScreen() {
    val markdownContent = remember {
        MutableStateFlow("")
    }
    
    var currentContent by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(true) }
    var currentSpeed by remember { mutableStateOf("中速") }
    
    val scrollState = rememberScrollState()
    
    // 模拟的Markdown内容
    val fullMarkdownContent = """
        # 流式Markdown演示
        
        这是一个**流式Markdown**渲染的演示，字符正在*一个一个*地发送。
        
        ## 支持的功能
        
        1. 标题渲染
        2. **粗体**文本
        3. *斜体*文本
        4. ~~删除线~~文本
        5. `行内代码`显示
        
        > 这是一个引用块，也支持**富文本**格式。
        
        ### 代码块演示
        
        ```kotlin
        fun main() {
            println("Hello, Markdown!")
        }
        ```
        
        #### 列表演示
        
        - 无序列表项1
        - 无序列表项2
          - 嵌套列表项
        
        -----
        
        这是一个[链接示例](https://example.com)。
    """.trimIndent()
    
    // 模拟流式发送文本
    LaunchedEffect(Unit) {
        var index = 0
        val chars = fullMarkdownContent.toCharArray()
        
        // 模拟不同的发送速度
        val speeds = mapOf(
            "慢速" to 150.milliseconds,
            "中速" to 50.milliseconds,
            "快速" to 10.milliseconds,
            "超快" to 2.milliseconds
        )
        
        // 开始流式发送
        while (index < chars.size && isStreaming) {
            val char = chars[index].toString()
            markdownContent.update { it + char }
            currentContent = markdownContent.value
            
            // 根据字符类型调整延迟时间，模拟真实打字效果
            val baseDelay = speeds[currentSpeed] ?: 50.milliseconds
            val charDelay = when {
                char == "\n" -> baseDelay * 3
                char in " .,!?;:\"'" -> baseDelay * 2
                else -> baseDelay
            }
            
            delay(charDelay)
            index++
            
            // 每20个字符更换一次速度，以展示不同发送间隔的效果
            if (index % 20 == 0) {
                val speedKeys = speeds.keys.toList()
                val nextSpeedIndex = (speedKeys.indexOf(currentSpeed) + 1) % speedKeys.size
                currentSpeed = speedKeys[nextSpeedIndex]
            }
        }
        
        // 流式传输完成
        isStreaming = false
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // 状态指示器
            Text(
                text = "状态: ${if (isStreaming) "流式传输中($currentSpeed)" else "传输完成"}",
                color = if (isStreaming) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            
            // 主内容
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp)
                    .verticalScroll(scrollState)
            ) {
                StreamMarkdownRenderer(
                    markdownText = currentContent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
} 