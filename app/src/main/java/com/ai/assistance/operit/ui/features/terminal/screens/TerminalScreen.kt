package com.ai.assistance.operit.ui.features.terminal.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.TermuxCommandExecutor
import com.ai.assistance.operit.ui.features.terminal.model.TerminalLine
import com.ai.assistance.operit.ui.features.terminal.model.TerminalSession
import com.ai.assistance.operit.ui.features.terminal.model.TerminalSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ai.assistance.operit.ui.features.terminal.components.*
import com.ai.assistance.operit.ui.features.terminal.utils.TerminalColors

private const val TAG = "TerminalScreen"
private const val DEFAULT_FONT_SIZE = 14 // 默认字体大小
private const val MIN_FONT_SIZE = 8 // 最小字体大小
private const val MAX_FONT_SIZE = 24 // 最大字体大小

// ParrotOS 主题颜色
private val ParrotBg = Color(0xFF0A1017)  // 深蓝黑色背景
private val ParrotBgLight = Color(0xFF112130)  // 稍浅的背景色
private val ParrotAccent = Color(0xFF05D9E8)  // 青蓝色强调色
private val ParrotAccentDark = Color(0xFF01C0CF)  // 深青蓝色
private val ParrotRed = Color(0xFFFF073A)  // 鲜红色
private val ParrotRedDark = Color(0xFFD90731)  // 深红色
private val ParrotGreen = Color(0xFF01E472)  // 鲜绿色
private val ParrotYellow = Color(0xFFE8CD05)  // 黄色
private val ParrotPurple = Color(0xFF9D02E8)  // 紫色
private val ParrotOrange = Color(0xFFFF6F00)  // 橙色

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
    
    val zoomScale by animateFloatAsState(
        targetValue = if (isZooming) zoomFeedbackScale else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "zoomAnimation"
    )
    
    // 标记组合完成
    DisposableEffect(Unit) {
        isComposed = true
        onDispose {
            isComposed = false
        }
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
            onDismissRequest = { 
                showInputDialog = false
            },
            onInputSubmit = { input ->
                showInputDialog = false
                interactiveInputText = ""
                
                // 处理用户输入
                scope.launch {
                    try {
                        val success = com.ai.assistance.operit.TermuxCommandExecutor.sendInputToCommand(
                            context = context,
                            executionId = currentExecutionId,
                            input = input
                        )
                        
                        if (success) {
                            sessionManager.getActiveSession()?.let { session ->
                                session.commandHistory.add(com.ai.assistance.operit.ui.features.terminal.model.TerminalLine.Input(input, "User Input: "))
                            }
                        } else {
                            sessionManager.getActiveSession()?.let { session ->
                                session.commandHistory.add(com.ai.assistance.operit.ui.features.terminal.model.TerminalLine.Output("[输入发送失败]"))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "发送用户输入时出错: ${e.message}", e)
                    }
                }
            }
        )
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TerminalColors.ParrotBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // 顶部工具栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 会话选项卡区域
                Box(modifier = Modifier.weight(1f)) {
                    SessionTabsRow(
                        sessionManager = sessionManager,
                        onAddSession = {
                            sessionManager.createSession("Terminal ${sessionManager.getSessionCount() + 1}")
                        }
                    )
                }
                
                // 字体大小调整按钮
                IconButton(
                    onClick = { showFontSizeControls = !showFontSizeControls },
                    modifier = Modifier
                        .size(40.dp)
                        .padding(4.dp)
                        .clip(shape = androidx.compose.foundation.shape.CircleShape)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // 添加缩放手势检测
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                isZooming = true
                                zoomFeedbackScale = zoom
                                
                                // 根据缩放手势调整字体大小
                                if (zoom > 1.05f && fontSize < MAX_FONT_SIZE) {
                                    fontSize = (fontSize + 1).coerceAtMost(MAX_FONT_SIZE)
                                } else if (zoom < 0.95f && fontSize > MIN_FONT_SIZE) {
                                    fontSize = (fontSize - 1).coerceAtLeast(MIN_FONT_SIZE)
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
            } ?: run {
                // 如果没有活动会话，显示创建新会话按钮
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .padding(8.dp),
                            tint = TerminalColors.ParrotAccent
                        )
                        
                        Text(
                            "没有活动的终端会话",
                            style = MaterialTheme.typography.titleMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                        )
                        
                        Button(
                            onClick = {
                                sessionManager.createSession("Terminal 1")
                            },
                            modifier = Modifier.padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TerminalColors.ParrotAccent,
                                contentColor = androidx.compose.ui.graphics.Color.Black
                            )
                        ) {
                            Text("创建新终端会话")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 处理命令输入
 */
private suspend fun handleCommand(
    context: Context,
    command: String,
    session: TerminalSession,
    scope: CoroutineScope,
    onCommandProcessed: () -> Unit,
    setCurrentExecutionId: (Int) -> Unit,
    setInteractivePrompt: (String) -> Unit,
    setInteractiveInputText: (String) -> Unit,
    setShowInputDialog: (Boolean) -> Unit
) {
    // 将命令添加到历史，保存当前提示符
    val currentPrompt = session.getPrompt()
    session.commandHistory.add(TerminalLine.Input(command, currentPrompt))
    
    // 特殊命令处理
    when (command) {
        "exit" -> {
            // 通知用户应该按返回键返回
            Toast.makeText(context, "请按返回键返回上一页", Toast.LENGTH_SHORT).show()
            onCommandProcessed()
        }
        else -> {
            // 立即返回，不阻塞UI
            onCommandProcessed()
            
            // 用于安全修改命令历史的互斥锁
            val historyMutex = kotlinx.coroutines.sync.Mutex()
            
            // 使用Termux执行命令，返回输出流
            val outputFlow = session.executeCommand(
                context = context,
                command = command,
                scope = scope
            )
            
            // 收集输出流并更新UI
            try {
                outputFlow.collect { output ->
                    // 使用scope.launch确保在主线程更新UI状态
                    withContext(Dispatchers.Main) {
                        // 为避免并发修改，使用互斥锁保护
                        historyMutex.withLock {
                            Log.d("TerminalScreen", "收到命令输出: ${output.text}")
                            
                            // 检查是否包含交互式提示标记
                            if (output.text.startsWith("INTERACTIVE_PROMPT:")) {
                                // 提取提示信息
                                val executionId = output.text.substringAfter("ID:").substringBefore(":PROMPT:")
                                val prompt = output.text.substringAfter(":PROMPT:")
                                
                                Log.d("TerminalScreen", "检测到交互式提示: ID=$executionId, 提示=$prompt")
                                
                                // 显示交互式输入对话框
                                session.commandHistory.add(TerminalLine.Output("等待用户输入..."))
                                
                                try {
                                    val id = executionId.toInt()
                                    setCurrentExecutionId(id)
                                    setInteractivePrompt(prompt)
                                    setInteractiveInputText("y") // 默认为 yes
                                    setShowInputDialog(true)
                                } catch (e: NumberFormatException) {
                                    session.commandHistory.add(TerminalLine.Output("[无法解析执行ID: $executionId]"))
                                }
                            } 
                            // 检查是否是用户切换消息
                            else if (output.text.startsWith("USER_SWITCHED:")) {
                                // 这是一个特殊的用户切换消息，我们只记录实际的切换消息
                                // 不添加这条特殊消息到历史记录中
                                Log.d("TerminalScreen", "检测到用户切换: ${output.text}")
                            }
                            else {
                                // 检查是否可能是交互式提示但未被前面的代码识别
                                val output_text = output.text.trim()
                                if (output_text.contains("[Y/n]") || output_text.contains("[y/N]") || 
                                    output_text.contains("(yes/no)") || output_text.contains("yes/no") ||
                                    output_text.contains("是否继续") || output_text.contains("Press Enter")) {
                                    
                                    Log.d("TerminalScreen", "可能存在未捕获的交互式提示: ${output_text}")
                                }
                                
                                // 普通输出
                                session.commandHistory.add(output)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // 处理流收集过程中的异常
                Log.e("TerminalScreen", "收集命令输出时出错: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    historyMutex.withLock {
                        session.commandHistory.add(TerminalLine.Output("[输出收集失败: ${e.message}]"))
                    }
                }
            }
        }
    }
}

/**
 * 会话选项卡行
 */
@Composable
private fun SessionTabsRow(
    sessionManager: TerminalSessionManager,
    onAddSession: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // 显示所有会话选项卡
        items(sessionManager.sessions) { session ->
            val isActive = sessionManager.activeSessionId.value == session.id
            val sessionHasRootUser = session.currentUser == "root"
            
            Card(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable {
                        sessionManager.switchSession(session.id)
                    }
                    .widthIn(min = 120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isActive && sessionHasRootUser -> ParrotRed // Root会话红色
                        isActive -> ParrotAccent.copy(alpha = 0.2f) // 活动会话的青蓝色背景
                        sessionHasRootUser -> ParrotRedDark.copy(alpha = 0.7f) // 非活动Root会话深红色
                        else -> ParrotBgLight
                    }
                ),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isActive) 4.dp else 1.dp
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isActive) ParrotAccent else ParrotAccent.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (sessionHasRootUser) "${session.name} (root)" else session.name,
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isActive && sessionHasRootUser -> Color.White
                            isActive -> Color.White
                            sessionHasRootUser -> Color.White.copy(alpha = 0.8f)
                            else -> ParrotAccent.copy(alpha = 0.7f)
                        }
                    )
                    
                    // 如果有多个会话，显示关闭按钮
                    if (sessionManager.getSessionCount() > 1) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(start = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    sessionManager.closeSession(session.id)
                                },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭会话",
                                    tint = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
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
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable { onAddSession() },
                colors = CardDefaults.cardColors(
                    containerColor = ParrotBgLight.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = ParrotAccent.copy(alpha = 0.3f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加新会话",
                        tint = ParrotAccent
                    )
                }
            }
        }
    }
}

/**
 * 交互式输入对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InteractiveInputDialog(
    prompt: String,
    initialInput: String,
    onDismissRequest: () -> Unit,
    onInputSubmit: (String) -> Unit
) {
    var inputText by remember { mutableStateOf(initialInput) }
    
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ParrotBg,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, ParrotAccent.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "命令需要输入",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = ParrotAccent,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 提示文本
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .fillMaxWidth()
                )
                
                // 输入框
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { 
                            // 简化处理，只发送干净的文本
                            val cleanInput = inputText.trim()
                            onInputSubmit(cleanInput) 
                        }
                    ),
                    label = { Text("输入响应", color = Color.White.copy(alpha = 0.7f)) },
                    trailingIcon = {
                        IconButton(
                            onClick = { 
                                // 简化处理，只发送干净的文本
                                val cleanInput = inputText.trim()
                                onInputSubmit(cleanInput) 
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "发送", tint = ParrotAccent)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = ParrotAccent,
                        focusedBorderColor = ParrotAccent,
                        unfocusedBorderColor = ParrotAccent.copy(alpha = 0.5f),
                        focusedContainerColor = ParrotBgLight,
                        unfocusedContainerColor = ParrotBgLight
                    ),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace
                    )
                )
                
                // 按钮行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 快捷按钮 - 改为发送完整单词
                    Button(
                        onClick = { onInputSubmit("yes") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ParrotGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("是 (Y)", fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { onInputSubmit("no") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ParrotRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("否 (N)", fontWeight = FontWeight.Bold)
                    }
                }
                
                Button(
                    onClick = { 
                        // 清理输入文本并发送
                        val cleanInput = inputText.trim()
                        onInputSubmit(cleanInput) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ParrotAccent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("发送自定义输入", fontWeight = FontWeight.Bold)
                }
                
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Text("取消")
                }
            }
        }
    }
}

// 为终端提示符添加闪烁效果
@Composable
private fun BlinkingCursor(fontSize: Int) {
    val alpha = remember { androidx.compose.animation.core.Animatable(1f) }
    
    LaunchedEffect(Unit) {
        while(true) {
            alpha.animateTo(
                targetValue = 0.2f,
                animationSpec = tween(600, easing = androidx.compose.animation.core.LinearEasing)
            )
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(600, easing = androidx.compose.animation.core.LinearEasing)
            )
        }
    }
    
    Text(
        text = "▌",
        color = ParrotAccent.copy(alpha = alpha.value),
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 2.dp)
    )
}

// 为命令历史中添加突出效果的特殊关键字
private fun highlightCommandText(command: String): AnnotatedString {
    val builderFactory = AnnotatedString.Builder(command)
    
    // 关键词和颜色映射
    val keywords = mapOf(
        "sudo" to ParrotRed,
        "apt" to ParrotYellow,
        "install" to ParrotOrange,
        "update" to ParrotOrange,
        "ls" to ParrotPurple,
        "cd" to ParrotPurple,
        "pwd" to ParrotPurple,
        "cat" to ParrotPurple,
        "grep" to ParrotPurple,
        "git" to ParrotAccent,
        "python" to ParrotGreen,
        "rm" to ParrotRed,
        "mkdir" to ParrotPurple,
        "chmod" to ParrotRed,
        "chown" to ParrotRed,
        "ssh" to ParrotAccent,
        "nmap" to ParrotRed,
        "ping" to ParrotYellow
    )
    
    // 检查命令中的关键词，并高亮它们
    keywords.forEach { (keyword, color) ->
        // 查找完整的关键字（前后有空格或位于开头/结尾）
        var startIndex = 0
        while (true) {
            val wordStartIndex = command.indexOf(keyword, startIndex)
            if (wordStartIndex == -1) break
            
            val wordEndIndex = wordStartIndex + keyword.length
            val isValidWord = (wordStartIndex == 0 || command[wordStartIndex - 1].isWhitespace()) && 
                             (wordEndIndex >= command.length || command[wordEndIndex].isWhitespace() || command[wordEndIndex] == ':')
            
            if (isValidWord) {
                builderFactory.addStyle(
                    style = SpanStyle(
                        color = color,
                        fontWeight = FontWeight.Bold
                    ),
                    start = wordStartIndex,
                    end = wordEndIndex
                )
            }
            
            startIndex = wordEndIndex
            if (startIndex >= command.length) break
        }
    }
    
    // 高亮选项(以 - 或 -- 开头)
    val optionPattern = Regex("(\\s|^)(-{1,2}[\\w-]+)")
    optionPattern.findAll(command).forEach { matchResult ->
        val option = matchResult.groups[2]!!
        builderFactory.addStyle(
            style = SpanStyle(
                color = ParrotYellow,
                fontWeight = FontWeight.Bold
            ),
            start = option.range.first,
            end = option.range.last + 1
        )
    }
    
    // 高亮路径(/开头或包含/ 的词)
    val pathPattern = Regex("(\\s|^)(/{1,2}[\\w./\\-_]+)")
    pathPattern.findAll(command).forEach { matchResult ->
        val path = matchResult.groups[2]!!
        builderFactory.addStyle(
            style = SpanStyle(
                color = ParrotPurple,
                fontWeight = FontWeight.Bold
            ),
            start = path.range.first,
            end = path.range.last + 1
        )
    }
    
    return builderFactory.toAnnotatedString()
}