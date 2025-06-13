package com.ai.assistance.operit.ui.features.toolbox.screens

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.chat.components.part.CustomXmlRenderer
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.util.stream.asStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow

private const val TAG = "StreamMarkdownDemo"

/** 流式Markdown演示屏幕 展示流式渲染的效果，包括字节一个一个发送时的表现和发送间隔控制 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamMarkdownDemoScreen(onBackClick: () -> Unit = {}) {
    // 状态
    var isStreaming by remember { mutableStateOf(false) }
    var speedFactor by remember { mutableStateOf(1.0f) }
    var hasStarted by remember { mutableStateOf(false) }
    var totalChars by remember { mutableStateOf(0) }
    var processedChars by remember { mutableStateOf(0) }
    var autoScroll by remember { mutableStateOf(true) }

    val scrollState = rememberScrollState()

    // 用于重置渲染器的键
    var streamKey by remember { mutableStateOf(0) }

    // 为渲染器创建通道和流，使用key确保重置时能重新创建
    val (channel, markdownStream) =
            remember(streamKey) {
                Log.d(TAG, "创建新的Channel和Stream实例 (key=$streamKey)")
                val ch = Channel<Char>(Channel.UNLIMITED)
                val stream = ch.consumeAsFlow().asStream()
                ch to stream
            }

    // 缓存渲染器和事件处理器，防止不必要的重组
    val customXmlRenderer = remember { CustomXmlRenderer() }
    val linkClickHandler = remember<(String) -> Unit> { { /* 处理链接点击 */ } }

    // 在Composable销毁或streamKey改变时关闭通道以防泄漏
    DisposableEffect(streamKey) {
        onDispose {
            Log.d(TAG, "关闭Channel (key=$streamKey)")
            channel.close()
        }
    }

    // 模拟的Markdown内容
    val fullMarkdownContent = remember {
        """
        1. `你好啊`

        `你好呀`

        我是纯文本

        ```kotlin
        fun main() {
            println("Hello, World!")
        }
        ```

        ## XML渲染器测试

        ### 思考内容测试

        <think>
        这是AI的思考过程，可以折叠和展开。
        1. 首先分析问题
        2. 然后寻找解决方案
        3. 最后给出答案
        </think>

        ### 工具调用与结果测试

        #### 文件信息查询

        <tool name="file_info">
        <param name="path">/sdcard/Download/example.txt</param>
        </tool>

        <tool_result name="file_info" status="success">
        <content>
        文件大小: 1.2MB
        创建时间: 2023-06-15 14:30:22
        最后修改: 2023-06-16 09:45:11
        权限: rw-r--r--
        </content>
        </tool_result>

        #### 搜索功能

        <tool name="search">
        <param name="query">android development tutorial</param>
        </tool>

        <tool_result name="search" status="success">
        <content>
        找到 15 条相关结果:
        1. Android开发入门教程 - developer.android.com
        2. Android Studio使用指南 - android.developers.blog
        3. Jetpack Compose基础 - kotlinlang.org
        </content>
        </tool_result>

        #### 文件写入

        <tool name="write_file">
        <param name="path">/sdcard/Download/Operit/workspace/example.txt</param>
        <param name="content">这是一个很长的文件内容示例，用于测试DetailedToolDisplay组件的渲染效果。
        文件内容可以包含多行文本，甚至可以包含一些代码片段：
        ```kotlin
        fun main() {
            println("Hello, World!")
            val numbers = listOf(1, 2, 3, 4, 5)
            numbers.forEach { println(it) }
        }
        ```
        这样我们就可以测试工具调用的长内容显示效果了。长内容会使用卡片式布局展示，而不是单行显示。
        这对于包含大量参数或参数内容较长的工具调用非常有用。</param>
        <param name="append">false</param>
        </tool>

        <tool_result name="write_file" status="success">
        <content>
        文件写入成功，共写入 325 字节
        </content>
        </tool_result>

        #### 网络请求

        <tool name="http_request">
        <param name="url">https://api.example.com/data</param>
        <param name="method">GET</param>
        </tool>

        <tool_result name="http_request" status="error">
        <content>
        <error>请求失败：无法连接到服务器 (错误码: 404)</error>
        </content>
        </tool_result>

        ### 其他状态信息测试

        <status type="completion"></status>

        <status type="wait_for_user_need"></status>

        <status type="warning"></status>

        ### 计划测试
        <plan_item id="1" status="todo">
        计划分析项目结构
        </plan_item>

        <plan_item id="2" status="in_progress">
        正在编写核心功能
        </plan_item>

        <plan_update id="2" status="in_progress">
        已完成50%的核心功能
        </plan_update>

        <plan_item id="3" status="completed">
        完成用户界面设计
        </plan_item>

        <plan_item id="4" status="failed">
        尝试优化性能但失败了
        </plan_item>

        <xml>
            <name>张三</name>
            <age>20</age>
        </xml>
        
        ## 图片示例
        
        ![Android机器人](https://developer.android.com/static/images/brand/Android_Robot.png)
        
        ![大尺寸风景图](https://images.pexels.com/photos/2662116/pexels-photo-2662116.jpeg)

        | 表头1 | 表头2 | 表头3 | 表头4 | 表头5 |
        |-------|-------|-------|-------|-------|
        | 内容1 | 内容2 | 内容3 | 2023-01-01 | 这是一个很长很长的单元格内容，用于测试水平滚动功能 |
        | 内容4 | 内容5 | 内容6 | 2023-02-15 | 另一个长内容 |
        | 较长的单元格内容 | 数值 42 | 2023-05-15 | 是的 | 不超过20个字符 |

        ```mermaid
        graph TD;
            A[开始] --> B[处理];
            B --> C{是否成功?};
            C -->|是| D[完成];
            C -->|否| E[重试];
            E --> B;
        ```

        $$
        \begin{aligned}
        \nabla \times \vec{E} &= -\frac{\partial \vec{B}}{\partial t} \\
        \nabla \times \vec{H} &= \vec{J} + \frac{\partial \vec{D}}{\partial t} \\
        \nabla \cdot \vec{D} &= \rho \\
        \nabla \cdot \vec{B} &= 0
        \end{aligned}
        $$
        
        """.trimIndent()
    }

    // 设置总字符数
    LaunchedEffect(fullMarkdownContent) { totalChars = fullMarkdownContent.length }

    // 启动流的协程
    LaunchedEffect(isStreaming, streamKey) {
        if (isStreaming) {
            hasStarted = true
            processedChars = 0
            totalChars = fullMarkdownContent.length
            try {
                for (char in fullMarkdownContent) {
                    if (!isStreaming) break // 检查暂停状态
                    channel.send(char)
                    processedChars++
                    val delayMillis = (10 / speedFactor).toLong()
                    delay(delayMillis)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "流传输被取消")
            } catch (e: Exception) {
                Log.e(TAG, "流传输异常", e)
            } finally {
                if (!channel.isClosedForSend) {
                    Log.d(TAG, "流传输完成，关闭Channel")
                    channel.close()
                }
                isStreaming = false
            }
        }
    }

    Scaffold(
            topBar = {
                // ... TopAppBar can be added here if needed
            }
    ) { padding ->
        Column(
                modifier =
                Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState)
        ) {
            // 控制面板
            ControlPanel(
                    isStreaming = isStreaming,
                    hasStarted = hasStarted,
                    onStreamToggle = { isStreaming = !isStreaming },
                    onReset = {
                        isStreaming = false
                        hasStarted = false
                        streamKey++ // 改变key以重置流
                    },
                    speedFactor = speedFactor,
                    onSpeedChange = { speedFactor = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 流式渲染区域
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            text = "流式渲染 (Streaming)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StreamMarkdownRenderer(
                            markdownStream = markdownStream,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            onLinkClick = linkClickHandler,
                            xmlRenderer = customXmlRenderer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 新增：静态渲染区域
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            text = "静态渲染 (Static String)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 使用新的基于字符串的渲染器
                    StreamMarkdownRenderer(
                            content = fullMarkdownContent,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            onLinkClick = linkClickHandler,
                            xmlRenderer = customXmlRenderer
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    isStreaming: Boolean,
    hasStarted: Boolean,
    onStreamToggle: () -> Unit,
    onReset: () -> Unit,
    speedFactor: Float,
    onSpeedChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onStreamToggle) {
                Icon(
                        if (isStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isStreaming) "Pause" else "Play"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isStreaming) "暂停" else if (hasStarted) "继续" else "开始")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onReset) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset")
                Spacer(modifier = Modifier.width(8.dp))
                Text("重置")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("速度 (x${String.format("%.1f", speedFactor)})", fontSize = 14.sp)
        Slider(
                value = speedFactor,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..10f,
                steps = 18
        )
    }
}
