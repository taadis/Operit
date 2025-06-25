package com.ai.assistance.operit.ui.floating.ui.fullscreen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.PromptFunctionType
import com.ai.assistance.operit.ui.floating.FloatContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun FloatingFullscreenMode(floatContext: FloatContext) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isProcessingSpeech by remember { mutableStateOf(false) }
    var timeoutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var userMessage by remember { mutableStateOf("") }
    var aiMessage by remember { mutableStateOf("长按麦克风开始说话") }
    val coroutineScope = rememberCoroutineScope()
    var activeMessage by remember { mutableStateOf<ChatMessage?>(null) }

    // 创建语音识别和TTS服务
    val speechService = remember {
        SpeechServiceFactory.getInstance(
                context,
                SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN
        )
    }

    val voiceService = remember { VoiceServiceFactory.getInstance(context) }

    // 监听语音识别结果
    LaunchedEffect(speechService) {
        speechService.recognitionResultFlow.collectLatest { result ->
            if (result.isFinal) {
                timeoutJob?.cancel()
                isProcessingSpeech = false
                isRecording = false
                if (result.text.isNotBlank()) {
                    userMessage = result.text
                    floatContext.onSendMessage?.invoke(result.text, PromptFunctionType.VOICE)
                    aiMessage = "思考中..."
                } else {
                    aiMessage = "没有听清，请再试一次"
                    voiceService.speak(aiMessage, rate = 1.5f)
                }
            } else if (result.text.isNotBlank()) {
                // Partial result
                userMessage = result.text
            }
        }
    }

    // 初始化语音服务
    LaunchedEffect(Unit) {
        speechService.initialize()
        voiceService.initialize()
    }

    // 监听最新的AI消息
    LaunchedEffect(floatContext.messages) {
        val lastMessage = floatContext.messages.lastOrNull()

        // 只处理新消息
        if (lastMessage === activeMessage || lastMessage == null) return@LaunchedEffect
        activeMessage = lastMessage

        // 停止当前可能在播放的语音，为新消息做准备
        voiceService.stop()

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

                        val sentenceEndChars = listOf('.', '。', '!', '！', '?', '？', '\n')
                        val sentenceBuffer = StringBuilder()
                        var isFirstSentence = true

                        processedStream.collect { char ->
                            aiMessage += char

                            // 将字符添加到句子缓冲区
                            sentenceBuffer.append(char)

                            // 如果遇到句子结束符号，或者缓冲区已经足够长，则执行TTS
                            if (char in sentenceEndChars || sentenceBuffer.length >= 50) {
                                val sentenceToSpeak = sentenceBuffer.toString().trim()
                                if (sentenceToSpeak.isNotBlank()) {
                                    // 第一句中断播放，后续句子加入队列
                                    voiceService.speak(sentenceToSpeak, interrupt = isFirstSentence, rate = 1.5f)
                                    isFirstSentence = false
                                }
                                sentenceBuffer.clear()
                            }
                        }

                        // 处理最后剩余的文本
                        val finalSentence = sentenceBuffer.toString().trim()
                        if (finalSentence.isNotBlank()) {
                            voiceService.speak(finalSentence, interrupt = isFirstSentence, rate = 1.5f)
                        }
                    }
                            ?: run {
                                // 如果没有流，则显示静态内容
                                aiMessage = lastMessage.content
                                // 一次性使用TTS播放AI回复
                                if (aiMessage.isNotBlank()) {
                                    voiceService.speak(aiMessage, rate = 1.5f)
                                }
                            }
                }
            }
        }
    }

    // 清理资源
    DisposableEffect(speechService, voiceService) {
        onDispose {
            timeoutJob?.cancel()
            coroutineScope.launch {
                speechService.cancelRecognition()
                voiceService.stop()
            }
        }
    }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.6f to Color.Black.copy(alpha = 0.7f),
                                        1.0f to Color.Black.copy(alpha = 0.9f)
                                    )
                                )
                            )
    ) {
        // 居中消息区域
        LazyColumn(
                modifier =
                        Modifier.align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 160.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            // 用户消息
            if (userMessage.isNotEmpty()) {
                item {
                    Text(
                            text = userMessage,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // AI消息
            item {
                Text(
                        text = aiMessage,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        modifier = Modifier.animateContentSize() // 内容变化时有动画
                )
            }
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
                                                                    if (isRecording || isProcessingSpeech) {
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
                                                    coroutineScope.launch {
                                                        timeoutJob?.cancel()
                                                        isRecording = true
                                                        userMessage = ""
                                                        aiMessage = "正在聆听..."
                                                        
                                                        // 立即停止当前语音输出
                                                        voiceService.stop()
                                                        
                                                        // 在开始新的语音识别前，先取消可能正在进行的对话
                                                        // 检查最后一条消息，确定AI是否在工作中
                                                        val lastMessage = floatContext.messages.lastOrNull()
                                                        val isAiCurrentlyWorking = lastMessage?.sender == "think" ||
                                                                (lastMessage?.sender == "ai" && lastMessage.contentStream != null)

                                                        if (isAiCurrentlyWorking) {
                                                            floatContext.onCancelMessage?.invoke()
                                                        }
                                                        
                                                        // 开始语音识别
                                                        speechService.startRecognition(
                                                                languageCode = "zh-CN",
                                                                continuousMode = false,
                                                                partialResults = true
                                                        )
                                                    }
                                                },
                                                onPress = {
                                                    try {
                                                        awaitRelease()
                                                    } finally {
                                                        if (isRecording) {
                                                            timeoutJob = coroutineScope.launch {
                                                                speechService.stopRecognition()
                                                                isRecording = false
                                                                isProcessingSpeech = true
                                                                aiMessage = "识别中..."

                                                                // 添加超时机制
                                                                delay(2000) // 等待2秒

                                                                // 如果2秒后仍在处理，则手动结束
                                                                if (isProcessingSpeech) {
                                                                    isProcessingSpeech = false
                                                                    if (userMessage.isNotBlank()) {
                                                                        // 如果有部分结果，直接发送
                                                                        floatContext.onSendMessage?.invoke(userMessage, PromptFunctionType.VOICE)
                                                                        aiMessage = "思考中..."
                                                                    } else {
                                                                        // 否则提示未听清
                                                                        aiMessage = "没有听清，请再试一次"
                                                                        voiceService.speak(aiMessage, rate = 1.5f)
                                                                    }
                                                                }
                                                            }
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
