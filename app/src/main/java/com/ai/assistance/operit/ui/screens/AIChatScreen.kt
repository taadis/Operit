package com.ai.assistance.operit.ui.screens

import android.content.Context
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

// Constants
private const val CHAT_HISTORY_PAGE_SIZE = 20

// ChatHistorySelector as a top-level composable
@Composable
fun ChatHistorySelector(
    modifier: Modifier = Modifier,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    chatHistories: List<ChatHistory>,
    currentId: String?
) {
    // State to track how many items to load
    var itemsToLoad by remember { mutableStateOf(CHAT_HISTORY_PAGE_SIZE) }
    val historySelectorListState = rememberLazyListState()
    
    // Listen for scroll events to load more items when approaching the end
    LaunchedEffect(historySelectorListState) {
        snapshotFlow { historySelectorListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisibleIndex ->
                val totalItemsCount = chatHistories.size
                if (totalItemsCount > 0 && lastVisibleIndex >= itemsToLoad - 5 && itemsToLoad < totalItemsCount) {
                    // Load the next page when user is 5 items from the end
                    itemsToLoad = (itemsToLoad + CHAT_HISTORY_PAGE_SIZE).coerceAtMost(totalItemsCount)
                }
            }
    }
    
    Column(modifier = modifier) {
        // 标题
        Text(
            text = "对话历史",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        // 新建对话按钮
        Button(
            onClick = { onNewChat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建对话")
            Spacer(modifier = Modifier.width(8.dp))
            Text("新建对话")
        }

        
        Spacer(modifier = Modifier.height(8.dp))
        Divider()
        Spacer(modifier = Modifier.height(8.dp))
        
        // 对话历史列表
        LazyColumn(
            state = historySelectorListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Only load a subset of items for better performance
            val itemsToShow = chatHistories.take(itemsToLoad)
            
            items(
                items = itemsToShow,
                key = { it.id }
            ) { history ->
                val isSelected = history.id == currentId
                
                // Simplify by calculating colors directly in composable context
                val surfaceColor = if (isSelected) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surface
                
                val textColor = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurface
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectChat(history.id) },
                    color = surfaceColor,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = history.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

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
    var aiService by remember { mutableStateOf<AIService?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var chatMemory by remember { mutableStateOf(mutableListOf<Pair<String, String>>()) }
    
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
        
        // 清理所有历史记录中的 thinking 消息
        chatHistoryManager.cleanUpThinkingMessages()
        
        // Initialize AIService if we have stored credentials
        if (storedApiKey.isNotBlank()) {
            aiService = AIService(storedApiEndpoint, storedApiKey, storedModelName)
            
            // If we're not already configured, set up the chat
            if(currentChatId != null) {
                isConfigured = true
                chatHistory = chatHistories.find { it.id == currentChatId }?.messages ?: emptyList()
            }else if (!isConfigured) {
                isConfigured = true
                val welcomeMessage = ChatMessage(
                    "system",
                    "AI Assistant v1.0\n欢迎使用AI助手！请在下方输入您的问题。"
                )
                chatHistory = listOf(welcomeMessage)
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
                
                // 过滤掉所有 "think" 类型的消息再保存
                val filteredMessages = chatHistory.filter { it.sender != "think" }
                
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
        
        val userMsg = ChatMessage("user", trimmedMessage)
        chatHistory = chatHistory + userMsg
        val tempMessage = trimmedMessage
        userMessage = ""
        focusManager.clearFocus()
        
        // 添加消息到记忆
        chatMemory.add(Pair("user", trimmedMessage))
        if (chatMemory.size > 10) {
            chatMemory.removeAt(0) // 保持最近10条对话
        }
        
        // Add AI "thinking" message
        var thinkingContent = "正在思考..."
        val processingMsg = ChatMessage("think", thinkingContent)
        chatHistory = chatHistory + processingMsg
        
        // 记录是否已经有AI回复
        var hasAiResponse = false
        
        // Call AI service
        coroutineScope.launch {
            isLoading = true
            try {
                // 使用新的流式响应API，传递聊天记忆
                aiService?.sendMessage(
                    message = tempMessage,
                    onPartialResponse = { content, thinking ->
                        if (thinking != null) {
                            // 更新思考内容
                            thinkingContent = thinking
                            
                            // 查找并更新thinking消息
                            val thinkIndex = chatHistory.indexOfLast { it.sender == "think" }
                            if (thinkIndex >= 0) {
                                chatHistory = chatHistory.toMutableList().apply {
                                    set(thinkIndex, ChatMessage("think", thinking))
                                }
                            }
                        }
                        
                        // 更新AI回复内容
                        if (content.isNotEmpty()) {
                            val trimmedContent = content.trim()
                            if (trimmedContent.isNotBlank()) {
                                if (hasAiResponse) {
                                    // 已经有AI回复，更新内容
                                    val aiIndex = chatHistory.indexOfLast { it.sender == "ai" }
                                    if (aiIndex >= 0) {
                                        chatHistory = chatHistory.toMutableList().apply {
                                            set(aiIndex, ChatMessage("ai", trimmedContent))
                                        }
                                    }
                                } else {
                                    // 第一次收到AI回复，删除thinking消息并添加AI消息
                                    hasAiResponse = true
                                    // 过滤掉thinking消息
                                    chatHistory = chatHistory.filter { it.sender != "think" } + ChatMessage("ai", trimmedContent)
                                    
                                    // 添加消息到记忆
                                    chatMemory.add(Pair("ai", trimmedContent))
                                    if (chatMemory.size > 10) {
                                        chatMemory.removeAt(0)
                                    }
                                }
                            }
                        }
                    },
                    chatHistory = chatMemory.toList(),
                    onComplete = {
                        // AI回复完全完成后再保存对话历史
                        saveCurrentChat()
                    }
                )
                
            } catch (e: Exception) {
                errorMessage = e.message
                // 出错时，保留思考内容，并添加错误消息
                chatHistory = chatHistory + ChatMessage("ai", "错误: ${e.message}")
                
                // 即使出错也保存对话历史
                saveCurrentChat()
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
            
            val newChat = chatHistoryManager.createNewChat()
            chatHistory = newChat.messages
        }
    }
    
    // 切换对话
    fun switchChat(chatId: String) {
        coroutineScope.launch {
            // 保存当前对话
            saveCurrentChat()
            
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
                            enabled = userMessage.isNotBlank() && !isLoading,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (userMessage.isNotBlank() && !isLoading)
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
                                    
                                    aiService = AIService(apiEndpoint, apiKey, modelName)
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
                            onSelectChat = { switchChat(it) },
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
                                    thinkingTextColor = thinkingTextColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
                
                // 添加加载指示器到Box的底部（如原有代码）
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

@Composable
fun CursorStyleChatMessage(
    message: ChatMessage,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color
) {
    when (message.sender) {
        "user" -> {
            // 用户提问 - Cursor IDE 风格
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = userMessageColor
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Prompt",
                        style = MaterialTheme.typography.labelSmall,
                        color = userTextColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = message.content,
                        color = userTextColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        "ai" -> {
            // AI 回复 - Cursor IDE 风格
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = aiMessageColor
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Response",
                        style = MaterialTheme.typography.labelSmall,
                        color = aiTextColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 解析内容中可能的代码块
                    val codeBlockPattern = "```([\\s\\S]*?)```".toRegex()
                    val segments = codeBlockPattern.split(message.content)
                    val matches = codeBlockPattern.findAll(message.content).map { it.groupValues[1] }.toList()
                    
                    segments.forEachIndexed { index, text ->
                        if (text.isNotEmpty()) {
                            Text(
                                text = text,
                        color = aiTextColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                        }
                        
                        if (index < matches.size) {
                            // 代码块
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = matches[index],
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        "think" -> {
            // 思考过程 - Cursor IDE 风格的折叠面板
            var expanded by remember { mutableStateOf(true) }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = thinkingBackgroundColor
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                            text = "Thinking...",
                        style = MaterialTheme.typography.labelMedium,
                        color = thinkingTextColor
                    )
                    
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "折叠" else "展开",
                        tint = thinkingTextColor
                    )
                }
                
                    AnimatedVisibility(visible = expanded) {
                        Text(
                            text = message.content,
                            color = thinkingTextColor,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            }
        }
        "system" -> {
            // 系统消息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = systemMessageColor
                ),
                shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = message.content,
                        color = systemTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                    )
            }
        }
    }
} 