package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.screens

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.tools.system.TermuxCommandExecutor
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.components.CommandInputArea
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.components.CommandOutputDisplay
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.components.FontSizeController
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.components.InteractiveInputDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalLine
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSessionManager
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.utils.TerminalColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "TerminalScreen"
private const val DEFAULT_FONT_SIZE = 14 // 默认字体大小
private const val MIN_FONT_SIZE = 8 // 最小字体大小
private const val MAX_FONT_SIZE = 24 // 最大字体大小

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 使用全局单例的会话管理器，而不是创建新实例
    val sessionManager = TerminalSessionManager

    // 确保至少有一个会话
    LaunchedEffect(Unit) {
        if (sessionManager.getSessionCount() == 0) {
            sessionManager.createSession("Terminal 1")
        }
    }

    // 终端状态
    var inputText by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    // 交互式输入对话框状态
    var showInputDialog by remember { mutableStateOf(false) }
    var interactivePrompt by remember { mutableStateOf("") }
    var interactiveInputText by remember { mutableStateOf("") }
    var currentExecutionId by remember { mutableStateOf(-1) }

    // 对象是否已完成组合
    var isComposed by remember { mutableStateOf(false) }

    // 字体大小状态
    var fontSize by remember { mutableStateOf(DEFAULT_FONT_SIZE) }

    // 字体大小控制器状态
    var showFontSizeControls by remember { mutableStateOf(false) }

    // 缩放手势反馈状态
    var isZooming by remember { mutableStateOf(false) }
    var zoomFeedbackScale by remember { mutableStateOf(1f) }

    val zoomScale by
            animateFloatAsState(
                    targetValue = if (isZooming) zoomFeedbackScale else 1f,
                    animationSpec = tween(durationMillis = 300),
                    label = "zoomAnimation"
            )

    // 标记组合完成
    DisposableEffect(Unit) {
        isComposed = true
        onDispose { isComposed = false }
    }

    // 自动获取焦点，但在组合完成后添加延迟
    LaunchedEffect(isComposed) {
        if (isComposed) {
            // 添加短暂延迟，确保组件完全渲染
            delay(300)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // 忽略可能的异常
            }
        }
    }

    // 交互式输入对话框
    if (showInputDialog) {
        InteractiveInputDialog(
                prompt = interactivePrompt,
                initialInput = interactiveInputText,
                onDismissRequest = { showInputDialog = false },
                onInputSubmit = { input ->
                    showInputDialog = false
                    interactiveInputText = ""

                    // 处理用户输入
                    scope.launch {
                        try {
                            val success =
                                    TermuxCommandExecutor.sendInputToCommand(
                                            context = context,
                                            executionId = currentExecutionId,
                                            input = input
                                    )

                            if (success) {
                                sessionManager.getActiveSession()?.let { session ->
                                    session.commandHistory.add(
                                            TerminalLine.Input(input, "User Input: ")
                                    )
                                }
                            } else {
                                sessionManager.getActiveSession()?.let { session ->
                                    session.commandHistory.add(TerminalLine.Output("[输入发送失败]"))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "发送用户输入时出错: ${e.message}", e)
                        }
                    }
                }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = TerminalColors.ParrotBg) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // 顶部工具栏
            Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // 会话选项卡区域
                Box(modifier = Modifier.weight(1f)) {
                    SessionTabsRow(
                            sessionManager = sessionManager,
                            onAddSession = {
                                sessionManager.createSession(
                                        "Terminal ${sessionManager.getSessionCount() + 1}"
                                )
                            }
                    )
                }

                // 字体大小调整按钮
                IconButton(
                        onClick = { showFontSizeControls = !showFontSizeControls },
                        modifier =
                                Modifier.size(40.dp)
                                        .padding(4.dp)
                                        .clip(shape = CircleShape)
                                        .background(TerminalColors.ParrotBgLight)
                ) {
                    Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "调整字体大小",
                            tint = TerminalColors.ParrotAccent
                    )
                }
            }

            // 字体大小控制器
            FontSizeController(
                    fontSize = fontSize,
                    onFontSizeChange = { fontSize = it },
                    isVisible = showFontSizeControls,
                    minFontSize = MIN_FONT_SIZE,
                    maxFontSize = MAX_FONT_SIZE,
                    defaultFontSize = DEFAULT_FONT_SIZE
            )

            // 活动会话内容
            sessionManager.getActiveSession()?.let { activeSession ->
                // 终端输出区域 - 使用组件
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .weight(1f)
                                        // 添加缩放手势检测
                                        .pointerInput(Unit) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                isZooming = true
                                                zoomFeedbackScale = zoom

                                                // 根据缩放手势调整字体大小
                                                if (zoom > 1.05f && fontSize < MAX_FONT_SIZE) {
                                                    fontSize =
                                                            (fontSize + 1).coerceAtMost(
                                                                    MAX_FONT_SIZE
                                                            )
                                                } else if (zoom < 0.95f && fontSize > MIN_FONT_SIZE
                                                ) {
                                                    fontSize =
                                                            (fontSize - 1).coerceAtLeast(
                                                                    MIN_FONT_SIZE
                                                            )
                                                }

                                                // 延迟关闭缩放状态
                                                scope.launch {
                                                    delay(300)
                                                    isZooming = false
                                                }
                                            }
                                        }
                ) {
                    // 使用命令输出显示组件
                    CommandOutputDisplay(
                            session = activeSession,
                            fontSize = fontSize,
                            isZooming = isZooming,
                            zoomScale = zoomScale
                    )
                }

                // 使用命令输入区域组件
                CommandInputArea(
                        context = context,
                        session = activeSession,
                        inputText = inputText,
                        onInputTextChange = { inputText = it },
                        fontSize = fontSize,
                        scope = scope,
                        focusRequester = focusRequester,
                        onCommandProcessed = {
                            // 清空输入并重置命令历史索引
                            inputText = TextFieldValue()
                            activeSession.resetCommandHistoryIndex()
                        },
                        setCurrentExecutionId = { id -> currentExecutionId = id },
                        setInteractivePrompt = { prompt -> interactivePrompt = prompt },
                        setInteractiveInputText = { text -> interactiveInputText = text },
                        setShowInputDialog = { show -> showInputDialog = show }
                )
            }
                    ?: run {
                        // 如果没有活动会话，显示创建新会话按钮
                        Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(72.dp).padding(8.dp),
                                        tint = TerminalColors.ParrotAccent
                                )

                                Text(
                                        "没有活动的终端会话",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                )

                                Button(
                                        onClick = { sessionManager.createSession("Terminal 1") },
                                        modifier = Modifier.padding(top = 8.dp),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                TerminalColors.ParrotAccent,
                                                        contentColor = Color.Black
                                                )
                                ) { Text("创建新终端会话") }
                            }
                        }
                    }
        }
    }
}

/** 会话选项卡行 */
@Composable
private fun SessionTabsRow(sessionManager: TerminalSessionManager, onAddSession: () -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        // 显示所有会话选项卡
        items(sessionManager.sessions) { session ->
            val isActive = sessionManager.activeSessionId.value == session.id
            val sessionHasRootUser = session.currentUser == "root"

            Card(
                    modifier =
                            Modifier.padding(end = 8.dp)
                                    .clickable { sessionManager.switchSession(session.id) }
                                    .widthIn(min = 120.dp),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor =
                                            when {
                                                isActive && sessionHasRootUser ->
                                                        TerminalColors.ParrotRed // Root会话红色
                                                isActive ->
                                                        TerminalColors.ParrotAccent.copy(
                                                                alpha = 0.2f
                                                        ) // 活动会话的青蓝色背景
                                                sessionHasRootUser ->
                                                        TerminalColors.ParrotRedDark.copy(
                                                                alpha = 0.7f
                                                        ) // 非活动Root会话深红色
                                                else -> TerminalColors.ParrotBgLight
                                            }
                            ),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    elevation =
                            CardDefaults.cardElevation(
                                    defaultElevation = if (isActive) 4.dp else 1.dp
                            ),
                    border =
                            BorderStroke(
                                    width = 1.dp,
                                    color =
                                            if (isActive) TerminalColors.ParrotAccent
                                            else TerminalColors.ParrotAccent.copy(alpha = 0.3f)
                            )
            ) {
                Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text =
                                    if (sessionHasRootUser) "${session.name} (root)"
                                    else session.name,
                            fontSize = 14.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color =
                                    when {
                                        isActive && sessionHasRootUser -> Color.White
                                        isActive -> Color.White
                                        sessionHasRootUser -> Color.White.copy(alpha = 0.8f)
                                        else -> TerminalColors.ParrotAccent.copy(alpha = 0.7f)
                                    }
                    )

                    // 如果有多个会话，显示关闭按钮
                    if (sessionManager.getSessionCount() > 1) {
                        Box(
                                modifier = Modifier.size(24.dp).padding(start = 4.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                    onClick = { sessionManager.closeSession(session.id) },
                                    modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭会话",
                                        tint =
                                                if (isActive) Color.White
                                                else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 添加新会话按钮
        item {
            Card(
                    modifier = Modifier.padding(end = 4.dp).clickable { onAddSession() },
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = TerminalColors.ParrotBgLight.copy(alpha = 0.7f)
                            ),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    border =
                            BorderStroke(
                                    width = 1.dp,
                                    color = TerminalColors.ParrotAccent.copy(alpha = 0.3f)
                            )
            ) {
                Box(
                        modifier =
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp).size(32.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加新会话",
                            tint = TerminalColors.ParrotAccent
                    )
                }
            }
        }
    }
}
