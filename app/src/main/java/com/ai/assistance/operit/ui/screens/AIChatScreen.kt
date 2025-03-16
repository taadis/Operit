package com.ai.assistance.operit.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.AIService
import com.ai.assistance.operit.model.ChatMessage
import com.ai.assistance.operit.model.ChatHistory
import com.ai.assistance.operit.data.ApiPreferences
import com.ai.assistance.operit.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import com.ai.assistance.operit.data.ChatHistoryManager
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import java.util.*
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.ui.components.ToolProgressBar
import com.ai.assistance.operit.ui.components.ReferencesDisplay
import com.ai.assistance.operit.model.ToolExecutionProgress
import com.ai.assistance.operit.model.ToolExecutionState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import com.ai.assistance.operit.ui.components.SimpleLinearProgressIndicator
import com.ai.assistance.operit.ui.components.SimpleAnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Autorenew
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.ui.components.ChatHistorySelector
import com.ai.assistance.operit.ui.components.CursorStyleChatMessage
import com.ai.assistance.operit.ui.components.ToolExecutionBox
import com.ai.assistance.operit.model.ConversationMarkupManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen() {
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
    
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences(context) }
    val chatHistoryManager = remember { ChatHistoryManager(context) }
    val scope = rememberCoroutineScope()
    
    // 历史聊天显示状态
    var showChatHistorySelector by remember { mutableStateOf(false) }
    
    // Load stored API settings
    val storedApiKey = apiPreferences.apiKeyFlow.collectAsState(initial = "").value
    val storedApiEndpoint = apiPreferences.apiEndpointFlow.collectAsState(initial = ApiPreferences.DEFAULT_API_ENDPOINT).value
    val storedModelName = apiPreferences.modelNameFlow.collectAsState(initial = ApiPreferences.DEFAULT_MODEL_NAME).value
    
    var apiKey by remember { mutableStateOf(storedApiKey) }
    var apiEndpoint by remember { mutableStateOf(storedApiEndpoint) }
    var modelName by remember { mutableStateOf(storedModelName) }
    var userMessage by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isConfigured by remember { mutableStateOf(storedApiKey.isNotBlank()) }
    var enhancedAiService by remember { mutableStateOf<EnhancedAIService?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var chatMemory by remember { mutableStateOf(mutableListOf<Pair<String, String>>()) }
    
    // Tool progress state
    var toolProgress by remember { mutableStateOf(ToolExecutionProgress(state = ToolExecutionState.IDLE)) }
    
    // Input processing state
    var isProcessingInput by remember { mutableStateOf(false) }
    var inputProcessingMessage by remember { mutableStateOf("") }
    
    // References from AI responses
    var aiReferences by remember { mutableStateOf(listOf<com.ai.assistance.operit.api.AiReference>()) }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val inputFocusRequester = remember { FocusRequester() }

    // Collect chat histories
    val chatHistories by chatHistoryManager.chatHistoriesFlow.collectAsState(initial = emptyList())
    val currentChatId by chatHistoryManager.currentChatIdFlow.collectAsState(initial = null)
    
    // Memoize the sorted chat histories to prevent resorting on each recomposition
    val sortedChatHistories by remember(chatHistories) {
        derivedStateOf {
            chatHistories.sortedByDescending { it.updatedAt }
        }
    }
    
    // Get string resources needed for non-composable functions
    val networkUnavailableMessage = stringResource(id = R.string.network_unavailable)
    
    // Update local state when preferences change
    LaunchedEffect(storedApiKey, storedApiEndpoint, storedModelName) {
        apiKey = storedApiKey
        apiEndpoint = storedApiEndpoint
        modelName = storedModelName
        
        
        // Initialize EnhancedAIService if we have stored credentials
        if (storedApiKey.isNotBlank()) {
            enhancedAiService = EnhancedAIService(storedApiEndpoint, storedApiKey, storedModelName, context)
            
            // If we're not already configured, set up the chat
            if(currentChatId != null) {
                isConfigured = true
                chatHistory = chatHistories.find { it.id == currentChatId }?.messages ?: emptyList()
            }else if (!isConfigured) {
                isConfigured = true
            }
            
            // Collect tool progress updates
            enhancedAiService?.let { service ->
                scope.launch {
                    service.getToolProgressFlow().collect { progress ->
                        toolProgress = progress
                    }
                }
                
                // Collect references
                scope.launch {
                    service.references.collect { refs ->
                        aiReferences = refs
                    }
                }
                
                // Collect input processing state
                scope.launch {
                    service.inputProcessingState.collect { state ->
                        when(state) {
                            is com.ai.assistance.operit.api.InputProcessingState.Idle -> {
                                isProcessingInput = false
                                inputProcessingMessage = ""
                            }
                            is com.ai.assistance.operit.api.InputProcessingState.Processing -> {
                                isProcessingInput = true
                                inputProcessingMessage = state.message
                            }
                            is com.ai.assistance.operit.api.InputProcessingState.Completed -> {
                                isProcessingInput = false
                            }
                        }
                    }
                }
            }
        }
    }
    
    val modernTextStyle = TextStyle(
        fontSize = 14.sp
    )
    
    // AI response placeholder
    val aiResponsePlaceholder = stringResource(id = R.string.ai_response_placeholder)
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(
                index = chatHistory.size - 1,
                scrollOffset = 0 // Ensure full visibility of the last item
            )
        }
    }
    
    // Function to handle sending messages
    fun saveCurrentChat() {
        // 只有聊天消息大于1条时才保存（避免保存空对话）
        if (chatHistory.size > 1) {
            coroutineScope.launch {
                // 有currentChatId时使用它，没有时创建新ID
                val chatId = currentChatId ?: UUID.randomUUID().toString()
                
                val title = chatHistory.firstOrNull { it.sender == "user" }?.content?.take(20) ?: "新对话"
                
                // 保留思考消息，不再过滤
                val filteredMessages = chatHistory
                
                val history = ChatHistory(
                    id = chatId,
                    title = "$title...",
                    messages = filteredMessages
                )
                chatHistoryManager.saveChatHistory(history)
                
                // 如果没有currentChatId，设置一个
                if (currentChatId == null) {
                    chatHistoryManager.setCurrentChatId(chatId)
                }
            }
        }
    }

    fun sendUserMessage(message: String) {
        // 验证消息内容是否为空
        val trimmedMessage = message.trim()
        if (trimmedMessage.isBlank() || isLoading) return
        
        // Check network connectivity
        if (!NetworkUtils.isNetworkAvailable(context)) {
            errorMessage = networkUnavailableMessage
            return
        }
        userMessage = ""
        focusManager.clearFocus()
        // 记录是否已经有AI回复
        var hasAiResponse = false
        
        // Call enhanced AI service
        coroutineScope.launch {
            isLoading = true
            try {
                // 从当前UI界面构建聊天历史，使用工具函数统一处理
                val currentChatHistory = ChatUtils.prepareMessagesForApi(
                    chatHistory,
                    setOf("user", "ai", "system") // 只保留用户、系统和AI消息
                )
                chatHistory = chatHistory + ChatMessage("user", trimmedMessage)
                
                // 使用ConversationMarkupManager创建思考状态
                chatHistory = chatHistory + ChatMessage("think", ConversationMarkupManager.createThinkingStatus())
                
                enhancedAiService?.sendMessage(
                    message = trimmedMessage,
                    onPartialResponse = { content, thinking ->
                        if (thinking != null) {
                            // 查找并更新thinking消息
                            val thinkIndex = chatHistory.indexOfLast { it.sender == "think" }
                            if (thinkIndex >= 0) {
                                // 使用标准的思考格式
                                val thinkingContent = ConversationMarkupManager.appendThinkingStatus(thinking)
                                chatHistory = chatHistory.toMutableList().apply {
                                    set(thinkIndex, ChatMessage("think", thinkingContent))
                                }
                            }
                        }
                        
                        // 更新AI回复内容 - 确保思考内容与回复内容分开
                        if (content.isNotEmpty()) {
                            val trimmedContent = content.trim()
                            if (trimmedContent.isNotBlank()) {
                                if (hasAiResponse) {
                                    // 已经有AI回复，更新内容
                                    val aiIndex = chatHistory.indexOfLast { it.sender == "ai" }
                                    if (aiIndex >= 0) {
                                        // 检查是否有任务完成标记，有则添加完成状态
                                        val finalContent = if (ConversationMarkupManager.containsTaskCompletion(trimmedContent)) {
                                            ConversationMarkupManager.createTaskCompletionContent(trimmedContent)
                                        } else {
                                            trimmedContent
                                        }
                                        
                                        chatHistory = chatHistory.toMutableList().apply {
                                            set(aiIndex, ChatMessage("ai", finalContent))
                                        }
                                    }
                                } else {
                                    // 第一次收到AI回复，添加AI消息但保留thinking消息
                                    hasAiResponse = true
                                    
                                    // 检查是否有任务完成标记
                                    val finalContent = if (ConversationMarkupManager.containsTaskCompletion(trimmedContent)) {
                                        ConversationMarkupManager.createTaskCompletionContent(trimmedContent)
                                    } else {
                                        trimmedContent
                                    }
                                    
                                    // 添加新的AI消息，而不是替换thinking消息
                                    chatHistory = chatHistory + ChatMessage("ai", finalContent)
                                }
                            }
                        }
                    },
                    chatHistory = currentChatHistory,
                    onComplete = {
                        // AI回复完全完成后再保存对话历史
                        saveCurrentChat()
                    }
                ) ?: run {
                    // fallback to regular AIService if enhanced service isn't initialized
                    errorMessage = "增强AI服务未初始化，请检查设置"
                    
                    // 确保重置处理状态
                    isProcessingInput = false
                }
                
            } catch (e: Exception) {
                errorMessage = e.message
                // 出错时，保留思考内容，并添加错误消息
                val errorMessage = ConversationMarkupManager.createToolErrorStatus("ai_service", "错误: ${e.message}")
                chatHistory = chatHistory + ChatMessage("ai", errorMessage)
                
                // 即使出错也保存对话历史
                saveCurrentChat()
                
                // 确保重置处理状态
                isProcessingInput = false
            } finally {
                isLoading = false
                // Request focus back to input field
                delay(500)
                inputFocusRequester.requestFocus()
            }
        }
    }
    
    // Show error in snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            delay(3000)
            errorMessage = null
        }
    }
    
    // 新建对话
    fun createNewChat() {
        coroutineScope.launch {
            // 保存当前对话
            saveCurrentChat()
            
            // 检查上一个对话是否为空对话
            if (currentChatId != null) {
                val currentChat = chatHistories.find { it.id == currentChatId }
                if (currentChat != null && 
                    currentChat.messages.none { it.sender == "user" }) {
                    // 如果上一个对话是空对话，直接使用它，不创建新对话
                    return@launch
                }
            }
            
            // Clear references when starting a new chat
            enhancedAiService?.clearReferences()
            
            val newChat = chatHistoryManager.createNewChat()
            chatHistory = newChat.messages
        }
    }
    
    // 切换对话
    fun switchChat(chatId: String) {
        coroutineScope.launch {
            // 保存当前对话
            saveCurrentChat()
            
            // Clear references when switching chats
            enhancedAiService?.clearReferences()
            
            // 清除内存中的聊天历史记录，避免历史混淆
            chatMemory.clear()
            
            chatHistoryManager.setCurrentChatId(chatId)
            val selectedChat = chatHistories.find { it.id == chatId }
            if (selectedChat != null) {
                chatHistory = selectedChat.messages
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isConfigured) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.shadow(4.dp)
                ) {
                    Column {
                        // Input processing indicator
                        SimpleAnimatedVisibility(visible = isProcessingInput) {
                            SimpleLinearProgressIndicator(
                                progress = 1f, // Indeterminate mode using full width
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (inputProcessingMessage.isNotBlank()) {
                                Text(
                                    text = inputProcessingMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = userMessage,
                                onValueChange = { userMessage = it },
                                placeholder = { Text("请输入您的问题...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(inputFocusRequester),
                                textStyle = modernTextStyle,
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { sendUserMessage(userMessage) }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            IconButton(
                                onClick = { sendUserMessage(userMessage) },
                                enabled = userMessage.isNotBlank() && !isLoading && !isProcessingInput,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (userMessage.isNotBlank() && !isLoading && !isProcessingInput)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                            ) {
                                Text(
                                    text = "发送",
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (!isConfigured) {
            // 配置页面
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .shadow(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "配置AI助手",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        
                        OutlinedTextField(
                            value = apiEndpoint,
                            onValueChange = { apiEndpoint = it },
                            label = { Text("API接口地址") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            textStyle = modernTextStyle,
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API密钥") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            textStyle = modernTextStyle,
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("模型名称") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            textStyle = modernTextStyle,
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        Button(
                            onClick = { 
                                if (apiEndpoint.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) {
                                    // Save API settings to preferences
                                    coroutineScope.launch {
                                        apiPreferences.saveApiSettings(apiKey, apiEndpoint, modelName)
                                        
                                        // Create a new chat history when first configuring
                                        val newChat = chatHistoryManager.createNewChat()
                                        chatHistory = newChat.messages
                                    }
                                    
                                    enhancedAiService = EnhancedAIService(apiEndpoint, apiKey, modelName, context)
                                    
                                    // Set up tool progress collection
                                    coroutineScope.launch {
                                        enhancedAiService?.getToolProgressFlow()?.collect { progress ->
                                            toolProgress = progress
                                        }
                                    }
                                    
                                    // Set up references collection
                                    coroutineScope.launch {
                                        enhancedAiService?.references?.collect { refs ->
                                            aiReferences = refs
                                        }
                                    }
                                    
                                    isConfigured = true
                                } else {
                                    errorMessage = "请输入API密钥、接口地址和模型名称"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text("保存并开始使用")
                        }
                    }
                }
            }
        } else {
            // 聊天界面
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 聊天历史选择器（可折叠）
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
                            onNewChat = { createNewChat() },
                            onSelectChat = { chatId -> switchChat(chatId) },
                            chatHistories = sortedChatHistories,
                            currentId = currentChatId
                        )
                    }
                    
                    // 主聊天区域
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // 历史切换按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showChatHistorySelector = !showChatHistorySelector },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text(if (showChatHistorySelector) "隐藏历史" else "显示历史")
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            if (currentChatId != null) {
                                val currentChat = chatHistories.find { it.id == currentChatId }
                                if (currentChat != null) {
                                    Text(
                                        text = "当前对话: ${currentChat.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        // References display
                        ReferencesDisplay(
                            references = aiReferences,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Tool progress bar
                        ToolProgressBar(
                            toolProgress = toolProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // 聊天消息列表
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            items(chatHistory) { message ->
                                CursorStyleChatMessage(
                                    message = message,
                                    userMessageColor = userMessageColor,
                                    aiMessageColor = aiMessageColor,
                                    userTextColor = userTextColor,
                                    aiTextColor = aiTextColor,
                                    systemMessageColor = systemMessageColor,
                                    systemTextColor = systemTextColor,
                                    thinkingBackgroundColor = thinkingBackgroundColor,
                                    thinkingTextColor = thinkingTextColor,
                                    // 添加工具执行的相关属性，确保能正确处理标记
                                    supportToolMarkup = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
                
                // 添加加载指示器到Box的底部
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 72.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "正在处理...",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Request focus to the input field when configured
    LaunchedEffect(isConfigured) {
        if (isConfigured) {
            delay(300)
            inputFocusRequester.requestFocus()
        }
    }

    // 确保在组件销毁时保存当前聊天
    DisposableEffect(Unit) {
        onDispose {
            saveCurrentChat()
        }
    }
}

