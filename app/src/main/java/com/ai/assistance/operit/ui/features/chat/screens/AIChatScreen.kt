package com.ai.assistance.operit.ui.features.chat.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.model.ChatMessage
import com.ai.assistance.operit.service.FloatingChatService
import com.ai.assistance.operit.ui.features.chat.components.ChatArea
import com.ai.assistance.operit.ui.features.chat.components.ChatHeader
import com.ai.assistance.operit.ui.features.chat.components.ChatHistorySelector
import com.ai.assistance.operit.ui.features.chat.components.ChatInputSection
import android.util.Log
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen() {
    val context = LocalContext.current
    
    // Initialize ViewModel without using viewModel() function
    val factory = ChatViewModelFactory(context)
    val viewModel = remember { factory.create(ChatViewModel::class.java) }
    
    // Collect state from ViewModel
    val apiKey by viewModel.apiKey.collectAsState()
    val apiEndpoint by viewModel.apiEndpoint.collectAsState()
    val modelName by viewModel.modelName.collectAsState()
    val isConfigured by viewModel.isConfigured.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val toolProgress by viewModel.toolProgress.collectAsState()
    val isProcessingInput by viewModel.isProcessingInput.collectAsState()
    val inputProcessingMessage by viewModel.inputProcessingMessage.collectAsState()
    val aiReferences by viewModel.aiReferences.collectAsState()
    val showChatHistorySelector by viewModel.showChatHistorySelector.collectAsState()
    val chatHistories by viewModel.chatHistories.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val popupMessage by viewModel.popupMessage.collectAsState()
    
    // Floating window mode state
    val isFloatingMode by viewModel.isFloatingMode.collectAsState()
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    
    // UI state
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val inputFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    
    // Modern chat UI colors - Cursor风格
    val backgroundColor = MaterialTheme.colorScheme.background
    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    // 只保留两个简单状态
    var autoScrollToBottom by remember { mutableStateOf(true) } // 是否自动滚动到底部
    var showScrollButton by remember { mutableStateOf(false) } // 是否显示滚动按钮
    // 添加一个防抖动标记，防止按钮频繁闪烁
    var isScrollStateChanging by remember { mutableStateOf(false) }
    // 跟踪上一次的滚动位置
    var lastScrollOffset by remember { mutableStateOf(0) }
    
    // 防抖动效果
    LaunchedEffect(isScrollStateChanging) {
        if (isScrollStateChanging) {
            delay(300) // 短暂延迟后重置状态
            isScrollStateChanging = false
        }
    }
    
    // 更简单直接的滚动状态监听 - 只监听用户主动向上滚动
    LaunchedEffect(Unit) {
        snapshotFlow { 
            Pair(
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress
            )
        }.collect { (currentOffset, isScrolling) ->
            // 只在用户主动滚动时判断
            if (isScrolling && !isScrollStateChanging) {
                // 检测是否是向上滚动(手指向下滑)
                val isScrollingUp = currentOffset < lastScrollOffset
                
                // 更新上次滚动位置
                lastScrollOffset = currentOffset
                
                // 如果用户向上滚动，禁用自动滚动并显示按钮
                if (!isScrollingUp) {
                    if (!showScrollButton) {
                        isScrollStateChanging = true
                        showScrollButton = true
                        autoScrollToBottom = false
                    }
                }
            }
        }
    }
    
    // 监听用户滚动到底部的情况
    LaunchedEffect(Unit) {
        snapshotFlow { !listState.canScrollForward }.collect { isAtBottom ->
            if (isAtBottom && !isScrollStateChanging && showScrollButton) {
                // 用户手动滚动到底部时，重新启用自动滚动并隐藏按钮
                isScrollStateChanging = true
                showScrollButton = false
                autoScrollToBottom = true
            }
        }
    }
    
    // 内容变化时的自动滚动 - 不使用size或index
    LaunchedEffect(chatHistory) {
        if (autoScrollToBottom && chatHistory.isNotEmpty()) {
            delay(50) // 短暂延迟确保布局完成
            try {
                // 直接使用极大值滚动，不关心具体位置
                listState.dispatchRawDelta(100000f)
            } catch (e: Exception) {
                Log.e("AIChatScreen", "自动滚动失败", e)
            }
        }
    }
    
    // 内容追加的自动滚动 - 不依赖于index或size
    LaunchedEffect(chatHistory.lastOrNull()?.content) {
        if (autoScrollToBottom && chatHistory.isNotEmpty()) {
            delay(10)
            try {
                // 直接使用极大值滚动，不关心具体位置
                listState.dispatchRawDelta(100000f)
            } catch (e: Exception) {
                Log.e("AIChatScreen", "内容追加滚动失败", e)
            }
        }
    }
    
    // Launch floating window service when floating mode is enabled
    LaunchedEffect(isFloatingMode, chatHistory.size) {
        if (isFloatingMode && canDrawOverlays.value) {
            try {
                // Start floating chat service
                val intent = Intent(context, FloatingChatService::class.java)
                
                // Filter out "think" messages which are not needed in the floating window
                val filteredMessages = chatHistory.filter { it.sender != "think" }
                
                // Convert to array of parcelables if needed
                val chatMessagesArray = filteredMessages.toTypedArray()
                intent.putExtra("CHAT_MESSAGES", chatMessagesArray)
                
                context.startService(intent)
                Log.d("AIChatScreen", "Started floating window service with ${filteredMessages.size} messages")
            } catch (e: Exception) {
                Log.e("AIChatScreen", "Error starting floating service", e)
                viewModel.toggleFloatingMode() // Turn off floating mode if it fails
                android.widget.Toast.makeText(
                    context,
                    "启动悬浮窗失败，请确保已授予悬浮窗权限",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } else if (!isFloatingMode) {
            // Stop floating chat service when floating mode is disabled
            context.stopService(Intent(context, FloatingChatService::class.java))
        }
    }
    
    // Show error in snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            delay(3000)
            viewModel.clearError()
        }
    }
    
    // Handle toast event
    val toastEvent by viewModel.toastEvent.collectAsState()
    
    toastEvent?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToastEvent()
        }
    }
    
    // Request focus to the input field when configured
    LaunchedEffect(isConfigured) {
        if (isConfigured) {
            delay(300)
            inputFocusRequester.requestFocus()
        }
    }
    
    // Save chat on app exit
    DisposableEffect(Unit) {
        onDispose {
            // This is handled by the ViewModel
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isConfigured) {
                ChatInputSection(
                    userMessage = userMessage,
                    onUserMessageChange = { viewModel.updateUserMessage(it) },
                    onSendMessage = { viewModel.sendUserMessage() },
                    onCancelMessage = { viewModel.cancelCurrentMessage() },
                    isLoading = isLoading,
                    isProcessingInput = isProcessingInput,
                    inputProcessingMessage = inputProcessingMessage,
                    focusRequester = inputFocusRequester
                )
            }
        },
        floatingActionButton = {
            if (isConfigured) {
                SmallFloatingActionButton(
                    onClick = { 
                        // Show reminder about system's small window feature
                        android.widget.Toast.makeText(
                            context,
                            "提示：您也可以使用系统自带的小窗功能，体验可能更佳。长按应用切换按钮或从最近任务中拖动可开启小窗模式。",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "小窗模式",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        if (!isConfigured) {
            // Configuration screen
            ConfigurationScreen(
                apiEndpoint = apiEndpoint,
                apiKey = apiKey,
                modelName = modelName,
                onApiEndpointChange = { viewModel.updateApiEndpoint(it) },
                onApiKeyChange = { viewModel.updateApiKey(it) },
                onModelNameChange = { viewModel.updateModelName(it) },
                onSaveConfig = { viewModel.saveApiSettings() },
                onError = { viewModel.clearError() },
                coroutineScope = coroutineScope
            )
        } else {
            // Chat screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Chat history selector (collapsible)
                    AnimatedVisibility(
                        visible = showChatHistorySelector,
                        enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                        exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    ) {
                        ChatHistorySelector(
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(top = 8.dp),
                            onNewChat = { viewModel.createNewChat() },
                            onSelectChat = { chatId -> viewModel.switchChat(chatId) },
                            onDeleteChat = { chatId -> viewModel.deleteChatHistory(chatId) },
                            chatHistories = chatHistories.sortedByDescending { it.createdAt },
                            currentId = currentChatId
                        )
                    }
                    
                    // Main chat area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Chat header
                        val currentChatTitle = chatHistories.find { it.id == currentChatId }?.title
                        
                        ChatHeader(
                            showChatHistorySelector = showChatHistorySelector,
                            onToggleChatHistorySelector = { viewModel.toggleChatHistorySelector() },
                            currentChatTitle = currentChatTitle
                        )
                        
                        // Chat area with messages
                        Box(modifier = Modifier.weight(1f)) {
                            ChatArea(
                                chatHistory = chatHistory,
                                listState = listState,
                                aiReferences = aiReferences,
                                toolProgress = toolProgress,
                                isLoading = isLoading,
                                userMessageColor = userMessageColor,
                                aiMessageColor = aiMessageColor,
                                userTextColor = userTextColor,
                                aiTextColor = aiTextColor,
                                systemMessageColor = systemMessageColor,
                                systemTextColor = systemTextColor,
                                thinkingBackgroundColor = thinkingBackgroundColor,
                                thinkingTextColor = thinkingTextColor,
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            // Scroll to bottom button - 简化
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 16.dp)
                            ) {
                                if (showScrollButton) {
                                    SmallFloatingActionButton(
                                        onClick = {
                                            // 点击按钮：启用自动滚动，隐藏按钮，并立即滚动到底部
                                            autoScrollToBottom = true
                                            showScrollButton = false
                                            
                                            coroutineScope.launch {
                                                if (chatHistory.isNotEmpty()) {
                                                    try {
                                                        // 不关心index，直接尝试滚动到底部
                                                        // 使用最大可能的滚动量
                                                        listState.dispatchRawDelta(100000f)
                                                        
                            
                                                    } catch (e: Exception) {
                                                        Log.e("AIChatScreen", "滚动到底部失败", e)
                                                    }
                                                }
                                            }
                                        },
                                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "滚动到底部",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Show popup message dialog when needed
    popupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPopupMessage() },
            title = { Text("提示") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPopupMessage() }) {
                    Text("确定")
                }
            }
        )
    }
    
    // Check for overlay permission on resume
    LaunchedEffect(Unit) {
        canDrawOverlays.value = Settings.canDrawOverlays(context)
        
        // If floating mode is on but no permission, turn it off
        if (isFloatingMode && !canDrawOverlays.value) {
            viewModel.toggleFloatingMode()
            android.widget.Toast.makeText(
                context,
                "未获得悬浮窗权限，已关闭悬浮窗模式",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}

// ViewModel Factory
class ChatViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}