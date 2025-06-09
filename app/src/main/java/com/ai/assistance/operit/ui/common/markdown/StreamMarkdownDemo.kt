package com.ai.assistance.operit.ui.common.markdown

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.ai.assistance.operit.util.stream.asStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow

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
    // 为渲染器创建通道和流
    val (channel, markdownStream) =
            remember(streamKey) {
                val ch = Channel<Char>(Channel.UNLIMITED)
                val stream = ch.consumeAsFlow().asStream()
                ch to stream
            }

    // 在Composable销毁时关闭通道以防泄漏
    DisposableEffect(channel) { onDispose { channel.close() } }

    // 模拟的Markdown内容
    val fullMarkdownContent = remember {
        """
        `你好啊`

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

    // 模拟流式发送文本 (打字机效果)
    LaunchedEffect(isStreaming, speedFactor, streamKey) {
        if (isStreaming && processedChars < totalChars) {
            hasStarted = true
            var index = processedChars
            val chars = fullMarkdownContent.toCharArray()

            while (index < chars.size && isStreaming) {
                val char = chars[index]
                channel.send(char) // 发送到通道
                processedChars = index + 1

                // 根据字符类型调整延迟时间，模拟真实打字效果
                val baseDelay = (50 / speedFactor).toLong()
                val charDelay =
                        when {
                            char == '\n' -> baseDelay * 3
                            char in " .,!?;:\"'" -> baseDelay * 2
                            else -> baseDelay
                        }

                delay(charDelay)
                index++

                // 自动滚动到底部
                if (autoScroll) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }

            // 流式传输完成
            if (processedChars >= totalChars) {
                isStreaming = false
            }
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("流式Markdown渲染演示") },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                            }
                        }
                )
            }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            // 控制面板
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                            text = "控制面板",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 播放控制
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(onClick = { isStreaming = !isStreaming }) {
                            Icon(
                                    if (isStreaming) Icons.Default.Pause
                                    else Icons.Default.PlayArrow,
                                    contentDescription = if (isStreaming) "暂停" else "开始"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isStreaming) "暂停" else if (hasStarted) "继续" else "开始")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                                onClick = {
                                    isStreaming = false
                                    processedChars = 0
                                    hasStarted = false
                                    streamKey++ // 强制重建流
                                },
                                enabled = hasStarted
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "重置")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("重置")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 速度控制
                    Text(
                            "速度: ${String.format("%.1f", speedFactor)}x",
                            style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                            value = speedFactor,
                            onValueChange = { speedFactor = it },
                            valueRange = 0.1f..5f,
                            steps = 48
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 自动滚动开关
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                                "自动滚动",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                        )
                        Switch(checked = autoScroll, onCheckedChange = { autoScroll = it })
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 进度指示器
                    Text(
                            "进度: ${processedChars}/${totalChars} 字符 (${(processedChars.toFloat() / totalChars.toFloat() * 100).toInt()}%)",
                            style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 状态指示器
            Text(
                    text =
                            "状态: ${if (isStreaming) "流式传输中 (${String.format("%.1f", speedFactor)}x速度)" else if (processedChars >= totalChars) "传输完成" else if (hasStarted) "已暂停" else "准备就绪"}",
                    color =
                            when {
                                isStreaming -> MaterialTheme.colorScheme.primary
                                processedChars >= totalChars -> Color(0xFF388E3C) // Green
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            },
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 主内容
            Box(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)) {
                StreamMarkdownRenderer(
                        markdownStream = markdownStream,
                        modifier = Modifier.fillMaxWidth(),
                        onLinkClick = { url -> /* 处理链接点击 */ }
                )
            }
        }
    }
}
