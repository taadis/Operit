package com.ai.assistance.operit.ui.floating.ui.fullscreen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.floating.FloatContext
import kotlinx.coroutines.launch

@Composable
fun FloatingFullscreenMode(floatContext: FloatContext) {
    var isRecording by remember { mutableStateOf(false) }
    var userMessage by remember { mutableStateOf("") }
    var aiMessage by remember { mutableStateOf("长按麦克风开始说话") }
    val coroutineScope = rememberCoroutineScope()
    var activeMessage by remember { mutableStateOf<ChatMessage?>(null) }

    // 监听最新的AI消息
    LaunchedEffect(floatContext.messages) {
        val lastMessage = floatContext.messages.lastOrNull()

        // 只处理新消息
        if (lastMessage === activeMessage || lastMessage == null) return@LaunchedEffect
        activeMessage = lastMessage

        when (lastMessage.sender) {
            "think" -> {
                aiMessage = "思考中..."
            }
            "ai" -> {
                // 启动协程来收集流，使UI保持响应性
                coroutineScope.launch {
                    aiMessage = "" // 清除之前的文本以进行流式处理
                    lastMessage.contentStream?.let { stream ->
                        val processedStream = XmlTextProcessor.processStreamToText(stream)
                        processedStream.collect { char -> aiMessage += char }
                    }
                            ?: run {
                                // 如果没有流，则显示静态内容
                                aiMessage = lastMessage.content
                            }
                }
            }
        }
    }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null, // 无涟漪效果
                                    onClick = {
                                        floatContext.onModeChange(floatContext.previousMode)
                                    }
                            )
    ) {
        // 居中消息区域
        Column(
                modifier =
                        Modifier.align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            // 用户消息
            if (userMessage.isNotEmpty()) {
                Text(
                        text = userMessage,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // AI消息
            Text(
                    text = aiMessage,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    modifier = Modifier.animateContentSize() // 内容变化时有动画
            )
        }

        // 底部控制栏
        Box(
                modifier =
                        Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 64.dp, start = 32.dp, end = 32.dp)
        ) {
            // 麦克风按钮
            Box(
                    modifier =
                            Modifier.align(Alignment.Center)
                                    .size(80.dp)
                                    .shadow(elevation = 8.dp, shape = CircleShape)
                                    .clip(CircleShape)
                                    .background(
                                            brush =
                                                    Brush.radialGradient(
                                                            colors =
                                                                    if (isRecording) {
                                                                        listOf(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .secondary,
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .secondaryContainer
                                                                        )
                                                                    } else {
                                                                        listOf(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.8f
                                                                                        ),
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                        )
                                                                    }
                                                    )
                                    )
                                    .clickable(enabled = false, onClick = {}) // 为了让Box消费事件
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                                onLongPress = {
                                                    isRecording = true
                                                    userMessage = ""
                                                    aiMessage = "正在聆听..."
                                                },
                                                onPress = {
                                                    try {
                                                        awaitRelease()
                                                    } finally {
                                                        if (isRecording) {
                                                            val simulatedVoiceInput = "你好"
                                                            userMessage = simulatedVoiceInput
                                                            floatContext.onSendMessage?.invoke(
                                                                    simulatedVoiceInput
                                                            )
                                                            isRecording = false
                                                            aiMessage = "思考中..."
                                                        }
                                                    }
                                                }
                                        )
                                    },
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "按住说话",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                )
            }

            // 返回按钮
            IconButton(
                    onClick = { floatContext.onModeChange(floatContext.previousMode) },
                    modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "返回",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
