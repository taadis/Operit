package com.ai.assistance.operit.ui.features.terminal.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private const val TAG = "TerminalScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 创建会话管理器
    val sessionManager = remember { TerminalSessionManager() }
    
    // 确保至少有一个会话
    LaunchedEffect(Unit) {
        if (sessionManager.getSessionCount() == 0) {
            sessionManager.createSession("Terminal 1")
        }
    }
    
    // 终端状态
    var inputText by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    
    // 对象是否已完成组合
    var isComposed by remember { mutableStateOf(false) }
    
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
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // 会话选项卡
            SessionTabsRow(
                sessionManager = sessionManager,
                onAddSession = {
                    sessionManager.createSession("Terminal ${sessionManager.getSessionCount() + 1}")
                }
            )
            
            // 活动会话内容
            sessionManager.getActiveSession()?.let { activeSession ->
                val scrollState = rememberLazyListState()
                
                // 当有新的输出时，自动滚动到底部
                // 使用命令历史大小作为key，确保新行添加时触发滚动
                LaunchedEffect(activeSession.commandHistory.size) {
                    if (activeSession.commandHistory.isNotEmpty()) {
                        scrollState.animateScrollToItem(activeSession.commandHistory.size - 1)
                    }
                }
                
                // 终端输出区域
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF000000)),
                    state = scrollState
                ) {
                    itemsIndexed(
                        items = activeSession.commandHistory
                    ) { index, line ->
                        val textColor = when (line) {
                            is TerminalLine.Input -> Color(0xFF00FF00) // 命令为绿色
                            is TerminalLine.Output -> Color(0xFFFFFFFF) // 输出为白色
                        }
                        
                        Text(
                            text = when (line) {
                                is TerminalLine.Input -> "${line.prompt}${line.text}"
                                is TerminalLine.Output -> line.text
                            },
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp)
                        )
                    }
                }
            
                // 输入区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF000000))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeSession.getPrompt(),
                        color = Color(0xFF00FF00),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .padding(vertical = 0.dp, horizontal = 6.dp)
                            .onKeyEvent { keyEvent ->
                                // 处理上下方向键浏览命令历史
                                when {
                                    keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyUp -> {
                                        activeSession.getPreviousCommand()?.let {
                                            inputText = TextFieldValue(it)
                                        }
                                        true
                                    }
                                    keyEvent.key == Key.DirectionDown && keyEvent.type == KeyEventType.KeyUp -> {
                                        activeSession.getNextCommand()?.let {
                                            inputText = TextFieldValue(it)
                                        }
                                        true
                                    }
                                    keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp -> {
                                        val command = inputText.text.trim()
                                        if (command.isNotEmpty()) {
                                            scope.launch {
                                                handleCommand(
                                                    context = context,
                                                    command = command,
                                                    session = activeSession,
                                                    scope = scope,
                                                    onCommandProcessed = {
                                                        // 清空输入并重置命令历史索引
                                                        inputText = TextFieldValue()
                                                        activeSession.resetCommandHistoryIndex()
                                                    }
                                                )
                                            }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val command = inputText.text.trim()
                                if (command.isNotEmpty()) {
                                    scope.launch {
                                        handleCommand(
                                            context = context,
                                            command = command,
                                            session = activeSession,
                                            scope = scope,
                                            onCommandProcessed = {
                                                // 清空输入并重置命令历史索引
                                                inputText = TextFieldValue()
                                                activeSession.resetCommandHistoryIndex()
                                            }
                                        )
                                    }
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color.DarkGray,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedContainerColor = Color(0xFF000000),
                            unfocusedContainerColor = Color(0xFF000000)
                        ),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        singleLine = true
                    )
                }
            } ?: run {
                // 如果没有活动会话，显示创建新会话按钮
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            sessionManager.createSession("Terminal 1")
                        }
                    ) {
                        Text("创建新终端会话")
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
    onCommandProcessed: () -> Unit
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
                            session.commandHistory.add(output)
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
                    .padding(end = 4.dp)
                    .clickable {
                        sessionManager.switchSession(session.id)
                    }
                    .widthIn(min = 100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isActive && sessionHasRootUser -> Color(0xFFBF0000) // 红色背景表示 root 会话
                        isActive -> MaterialTheme.colorScheme.primaryContainer
                        sessionHasRootUser -> Color(0xFF700000) // 深红色背景表示非活动的 root 会话
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (sessionHasRootUser) "${session.name} (root)" else session.name,
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // 如果有多个会话，显示关闭按钮
                    if (sessionManager.getSessionCount() > 1) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(start = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    sessionManager.closeSession(session.id)
                                },
                                modifier = Modifier.size(16.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭会话",
                                    tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加新会话",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
} 