package com.ai.assistance.operit.core.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.AiReference
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ToolExecutionProgress
import com.ai.assistance.operit.data.model.ToolExecutionState
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.LocalDateTime

class ChatViewModel(
    private val context: Context
) : ViewModel() {
    
    // Preferences and managers
    private val apiPreferences = ApiPreferences(context)
    private val chatHistoryManager = ChatHistoryManager(context)
    
    // API service
    private var enhancedAiService: EnhancedAIService? = null
    
    // 防止重复创建聊天
    private var initialChatCreated = false
    
    // 加载状态跟踪
    private var historiesLoaded = false
    private var currentChatIdLoaded = false
    
    // State flows
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey
    
    private val _apiEndpoint = MutableStateFlow(ApiPreferences.DEFAULT_API_ENDPOINT)
    val apiEndpoint: StateFlow<String> = _apiEndpoint
    
    private val _modelName = MutableStateFlow(ApiPreferences.DEFAULT_MODEL_NAME)
    val modelName: StateFlow<String> = _modelName
    
    private val _isConfigured = MutableStateFlow(false)
    val isConfigured: StateFlow<Boolean> = _isConfigured
    
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory
    
    private val _userMessage = MutableStateFlow("")
    val userMessage: StateFlow<String> = _userMessage
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    
    private val _toolProgress = MutableStateFlow(ToolExecutionProgress(state = ToolExecutionState.IDLE))
    val toolProgress: StateFlow<ToolExecutionProgress> = _toolProgress
    
    private val _isProcessingInput = MutableStateFlow(false)
    val isProcessingInput: StateFlow<Boolean> = _isProcessingInput
    
    private val _inputProcessingMessage = MutableStateFlow("")
    val inputProcessingMessage: StateFlow<String> = _inputProcessingMessage
    
    private val _aiReferences = MutableStateFlow<List<AiReference>>(emptyList())
    val aiReferences: StateFlow<List<AiReference>> = _aiReferences
    
    private val _showChatHistorySelector = MutableStateFlow(false)
    val showChatHistorySelector: StateFlow<Boolean> = _showChatHistorySelector
    
    private val _chatHistories = MutableStateFlow<List<ChatHistory>>(emptyList())
    val chatHistories: StateFlow<List<ChatHistory>> = _chatHistories
    
    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId
    
    // Add a new state flow for popup messages
    private val _popupMessage = MutableStateFlow<String?>(null)
    val popupMessage: StateFlow<String?> = _popupMessage.asStateFlow()
    
    // Adding a new toast event flow
    private val _toastEvent = MutableStateFlow<String?>(null)
    val toastEvent: StateFlow<String?> = _toastEvent.asStateFlow()
    
    // State for show thinking preference
    private val _showThinking = MutableStateFlow(ApiPreferences.DEFAULT_SHOW_THINKING)
    val showThinking: StateFlow<Boolean> = _showThinking.asStateFlow()
    
    // State for floating window mode
    private val _isFloatingMode = MutableStateFlow(false)
    val isFloatingMode: StateFlow<Boolean> = _isFloatingMode.asStateFlow()
    
    // State for chat statistics
    private val _contextWindowSize = MutableStateFlow(0)
    val contextWindowSize: StateFlow<Int> = _contextWindowSize.asStateFlow()
    
    private val _inputTokenCount = MutableStateFlow(0)
    val inputTokenCount: StateFlow<Int> = _inputTokenCount.asStateFlow()
    
    private val _outputTokenCount = MutableStateFlow(0)
    val outputTokenCount: StateFlow<Int> = _outputTokenCount.asStateFlow()
    
    // 添加累计token的记录
    private var cumulativeInputTokens = 0
    private var cumulativeOutputTokens = 0
    
    private val _memoryOptimization = MutableStateFlow(ApiPreferences.DEFAULT_MEMORY_OPTIMIZATION)
    val memoryOptimization: StateFlow<Boolean> = _memoryOptimization.asStateFlow()
    
    // Add a reference to the floating service
    private var floatingService: FloatingChatService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            floatingService = (service as? FloatingChatService.LocalBinder)?.getService()
            // Sync current messages when service connects
            _chatHistory.value.let { messages ->
                floatingService?.updateChatMessages(messages)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            floatingService = null
        }
    }
    
    init {
        // Load API settings
        viewModelScope.launch {
            apiPreferences.apiKeyFlow.collect { key ->
                _apiKey.value = key
                checkAndInitializeService()
            }
        }
        
        viewModelScope.launch {
            apiPreferences.apiEndpointFlow.collect { endpoint ->
                _apiEndpoint.value = endpoint
                checkAndInitializeService()
            }
        }
        
        viewModelScope.launch {
            apiPreferences.modelNameFlow.collect { model ->
                _modelName.value = model
                checkAndInitializeService()
            }
        }
        
        // Collect chat histories
        viewModelScope.launch {
            chatHistoryManager.chatHistoriesFlow.collect { histories ->
                _chatHistories.value = histories
                historiesLoaded = true
                checkIfShouldCreateNewChat()
            }
        }
        
        viewModelScope.launch {
            chatHistoryManager.currentChatIdFlow.collect { chatId ->
                _currentChatId.value = chatId
                currentChatIdLoaded = true
                checkIfShouldCreateNewChat()
                
                if (chatId != null) {
                    val selectedChat = _chatHistories.value.find { it.id == chatId }
                    if (selectedChat != null) {
                        _chatHistory.value = selectedChat.messages
                        // 恢复选中聊天的token计数
                        cumulativeInputTokens = selectedChat.inputTokens
                        cumulativeOutputTokens = selectedChat.outputTokens
                        _inputTokenCount.value = cumulativeInputTokens
                        _outputTokenCount.value = cumulativeOutputTokens
                    }
                }
            }
        }
        
        // Collect show thinking preference
        viewModelScope.launch {
            apiPreferences.showThinkingFlow.collect { showThinkingValue ->
                _showThinking.value = showThinkingValue
            }
        }
        
        // Collect memory optimization setting
        viewModelScope.launch {
            apiPreferences.memoryOptimizationFlow.collect { memoryOptimizationValue ->
                _memoryOptimization.value = memoryOptimizationValue
            }
        }
        
        // Update floating window when chat history changes
        viewModelScope.launch {
            _chatHistory.collect { messages ->
                floatingService?.updateChatMessages(messages)
            }
        }
    }
    
    private fun checkAndInitializeService() {
        val key = _apiKey.value
        val endpoint = _apiEndpoint.value
        val model = _modelName.value
        
        if (key.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank()) {
            // 始终重建服务以确保使用最新设置
            // 之前的代码只在服务为 null 时才创建，现在我们总是重建它
            enhancedAiService = EnhancedAIService(endpoint, key, model, context)
            
            // Set up tool progress collection
            viewModelScope.launch {
                enhancedAiService?.getToolProgressFlow()?.collect { progress ->
                    _toolProgress.value = progress
                }
            }
            
            // Set up references collection
            viewModelScope.launch {
                enhancedAiService?.references?.collect { refs ->
                    _aiReferences.value = refs
                }
            }
            
            // Set up input processing state
            viewModelScope.launch {
                enhancedAiService?.inputProcessingState?.collect { state ->
                    when(state) {
                        InputProcessingState.Idle -> {
                            _isProcessingInput.value = false
                            _inputProcessingMessage.value = ""
                        }
                        is InputProcessingState.Processing -> {
                            _isProcessingInput.value = true
                            _inputProcessingMessage.value = state.message
                        }
                        is InputProcessingState.Connecting -> {
                            _isProcessingInput.value = true
                            _inputProcessingMessage.value = state.message
                        }
                        is InputProcessingState.Receiving -> {
                            _isProcessingInput.value = true
                            _inputProcessingMessage.value = state.message
                        }
                        InputProcessingState.Completed -> {
                            _isProcessingInput.value = false
                        }
                    }
                }
            }
            
            _isConfigured.value = true
            
            // 标记为已配置，然后检查是否应该创建新对话
            checkIfShouldCreateNewChat()
        }
    }
    
    fun updateUserMessage(message: String) {
        _userMessage.value = message
    }
    
    fun updateApiKey(key: String) {
        _apiKey.value = key
    }
    
    fun updateApiEndpoint(endpoint: String) {
        _apiEndpoint.value = endpoint
    }
    
    fun updateModelName(model: String) {
        _modelName.value = model
    }
    
    fun toggleChatHistorySelector() {
        _showChatHistorySelector.value = !_showChatHistorySelector.value
    }
    
    fun saveApiSettings() {
        viewModelScope.launch {
            apiPreferences.saveApiSettings(_apiKey.value, _apiEndpoint.value, _modelName.value)
            
            enhancedAiService = EnhancedAIService(_apiEndpoint.value, _apiKey.value, _modelName.value, context)
            
            // Set up tool progress collection
            viewModelScope.launch {
                enhancedAiService?.getToolProgressFlow()?.collect { progress ->
                    _toolProgress.value = progress
                }
            }
            
            // Set up references collection
            viewModelScope.launch {
                enhancedAiService?.references?.collect { refs ->
                    _aiReferences.value = refs
                }
            }
            
            // Set up input processing state
            viewModelScope.launch {
                enhancedAiService?.inputProcessingState?.collect { state ->
                    when(state) {
                        InputProcessingState.Idle -> {
                            _isProcessingInput.value = false
                            _inputProcessingMessage.value = ""
                        }
                        is InputProcessingState.Processing -> {
                            _isProcessingInput.value = true
                            _inputProcessingMessage.value = state.message
                        }
                        is InputProcessingState.Connecting -> {
                            _isProcessingInput.value = true
                            _inputProcessingMessage.value = state.message
                        }
                        is InputProcessingState.Receiving -> {
                            _isProcessingInput.value = true
                            _inputProcessingMessage.value = state.message
                        }
                        InputProcessingState.Completed -> {
                            _isProcessingInput.value = false
                        }
                    }
                }
            }
            
            _isConfigured.value = true
            
            // 标记为已配置，然后检查是否应该创建新对话
            checkIfShouldCreateNewChat()
        }
    }
    
    /**
     * Save current chat to persistent storage
     */
    fun saveCurrentChat() {
        viewModelScope.launch {
            try {
                val currentId = _currentChatId.value ?: UUID.randomUUID().toString()
                
                // 仅在有消息时保存
                if (_chatHistory.value.isNotEmpty()) {
                    // 查找是否已有该ID的聊天历史，以保留原始createdAt
                    val existingChat = _chatHistories.value.find { it.id == currentId }
                    
                    val history = if (existingChat != null) {
                        // 如果是更新现有聊天，则保留原始的createdAt，只更新updatedAt
                        existingChat.copy(
                            title = generateChatTitle(),
                            messages = _chatHistory.value,
                            updatedAt = LocalDateTime.now(),
                            inputTokens = cumulativeInputTokens,
                            outputTokens = cumulativeOutputTokens
                        )
                    } else {
                        // 如果是新聊天，则创建新的ChatHistory对象
                        ChatHistory(
                            id = currentId,
                            title = generateChatTitle(),
                            messages = _chatHistory.value,
                            inputTokens = cumulativeInputTokens,
                            outputTokens = cumulativeOutputTokens
                        )
                    }
                    
                    chatHistoryManager.saveChatHistory(history)
                    
                    // Set current chat ID if not already set
                    if (_currentChatId.value == null) {
                        chatHistoryManager.setCurrentChatId(currentId)
                        _currentChatId.value = currentId
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to save chat history", e)
            }
        }
    }
    
    /**
     * 根据第一条用户消息生成聊天标题
     */
    private fun generateChatTitle(): String {
        val firstUserMessage = _chatHistory.value.firstOrNull { it.sender == "user" }?.content
        return if (firstUserMessage != null) {
            // 截取前20个字符作为标题，并添加省略号
            if (firstUserMessage.length > 20) {
                "${firstUserMessage.take(20)}..."
            } else {
                firstUserMessage
            }
        } else {
            "新对话"
        }
    }
    
    fun sendMessage(message: String) {
        if (message.trim().isEmpty()) {
            return
        }
        
        viewModelScope.launch {
            try {
                // 重置错误信息
                _errorMessage.value = null
                
                // 注意：用户消息已经在sendUserMessage方法中添加，这里不再添加
                
                // 检查服务实例是否存在
                val service = enhancedAiService ?: run {
                    // 无法发送，显示错误信息
                    _errorMessage.value = "AI服务未初始化"
                    _isLoading.value = false
                    return@launch
                }
                
                // 准备聊天历史
                val history = prepareChatHistory()
                
                // 发送消息到AI服务
                service.sendMessage(
                    message = message,
                    onPartialResponse = { content, thinking ->
                        handleAIResponse(content, thinking)
                    },
                    chatHistory = history,
                    onComplete = {
                        // 消息处理完成
                        _isLoading.value = false
                        
                        // 保存当前对话
                        saveCurrentChat()
                        
                        // 用户偏好分析已集成到问题库保存流程中，此处不再单独调用
                        // UserPreferenceAnalyzer.analyzeWithAI(_chatHistory.value, service)
                    }
                )
            } catch (e: Exception) {
                if(e.message?.contains("CANCEL") != true) {
                    // 处理异常
                    _errorMessage.value = "发送消息失败: ${e.message}"
                    _isLoading.value = false
                    
                    Log.e("ChatViewModel", "发送消息失败", e)
                }
            }
        }
    }
    
    /**
     * 向对话中发送用户消息
     */
    fun sendUserMessage() {
        if (_userMessage.value.isBlank() || _isLoading.value) {
            return
        }
        
        val message = _userMessage.value
        _userMessage.value = ""
        _isLoading.value = true
        
        // If no current chat id, create a new one
        if (_currentChatId.value == null) {
            createNewChat()
        }
        
        // Add user message to chat history
        addMessageToChat(ChatMessage(sender = "user", content = message))
        
        // Use viewModelScope to launch coroutine
        viewModelScope.launch {
            try {
                // Check network connectivity
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    _errorMessage.value = "网络连接不可用，请检查网络设置"
                    _isLoading.value = false
                    return@launch
                }
                
                val history = getMemory()
                enhancedAiService?.sendMessage(
                    message = message,
                    onPartialResponse = { content, thinking ->
                        handlePartialResponse(content, thinking)
                    },
                    chatHistory = history,
                    onComplete = {
                        handleResponseComplete()
                        
                        // Update token counts and context window size when response is complete
                        updateChatStatistics()
                    }
                )
                
                // No need to add user message to memory as it's already in chat history from addMessageToChat
                
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending message", e)
                _errorMessage.value = "发送消息失败: ${e.message}"
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Update chat statistics including token counts and context window size
     */
    private fun updateChatStatistics() {
        enhancedAiService?.let { service ->
            try {
                // 从AI服务获取最新的token统计 - 直接通过API获取
                val currentInputTokens = service.getCurrentInputTokenCount()
                val currentOutputTokens = service.getCurrentOutputTokenCount()
                
                // 更新上下文窗口大小 - 这是当前请求发送的token总数（包括系统提示词）
                _contextWindowSize.value = currentInputTokens
                
                // 更新累计token数
                cumulativeInputTokens += currentInputTokens  
                cumulativeOutputTokens += currentOutputTokens
                
                // 更新UI显示的累计值
                _inputTokenCount.value = cumulativeInputTokens
                _outputTokenCount.value = cumulativeOutputTokens
                
                Log.d("ChatViewModel", "Token stats updated - Context: $currentInputTokens, " +
                    "Cumulative Input: $cumulativeInputTokens, Output: $cumulativeOutputTokens")
                
                // 保存当前聊天历史，确保token计数被持久化
                saveCurrentChat()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error accessing token counts: ${e.message}", e)
                // 出错时不重置计数器，保持现有值
            }
        }
    }
    
    /**
     * 创建新的聊天会话时，重置token统计
     */
    private fun resetTokenStatistics() {
        cumulativeInputTokens = 0
        cumulativeOutputTokens = 0
        _inputTokenCount.value = 0
        _outputTokenCount.value = 0
        _contextWindowSize.value = 0
        
        // 如果服务已初始化，同时重置服务中的token计数
        enhancedAiService?.resetTokenCounters()
        
        Log.d("ChatViewModel", "Token statistics reset for new chat")
    }
    
    fun createNewChat() {
        viewModelScope.launch {
            try {
                // Save current chat before creating a new one
                saveCurrentChat()
                
                // Clear references
                enhancedAiService?.clearReferences()
                
                // Reset token statistics for new chat
                resetTokenStatistics()
                
                // Create a new chat
                val newChat = chatHistoryManager.createNewChat()
                
                // Update UI state
                _chatHistory.value = newChat.messages
            } catch (e: Exception) {
                _errorMessage.value = "创建新对话失败: ${e.message}"
            }
        }
    }
    
    fun switchChat(chatId: String) {
        viewModelScope.launch {
            // Save current chat
            saveCurrentChat()
            
            // Clear references
            enhancedAiService?.clearReferences()
            
            // Switch to selected chat
            chatHistoryManager.setCurrentChatId(chatId)
            val selectedChat = _chatHistories.value.find { it.id == chatId }
            if (selectedChat != null) {
                _chatHistory.value = selectedChat.messages
                
                // 恢复选中聊天的token计数
                cumulativeInputTokens = selectedChat.inputTokens
                cumulativeOutputTokens = selectedChat.outputTokens
                _inputTokenCount.value = cumulativeInputTokens
                _outputTokenCount.value = cumulativeOutputTokens
                
                // 更新AI服务的token计数，确保下一次请求正确追加
                enhancedAiService?.resetTokenCounters()
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun cancelCurrentMessage() {
        viewModelScope.launch {
            try {
                // 首先设置标志，避免其他操作继续处理
                _isLoading.value = false
                _isProcessingInput.value = false
                _inputProcessingMessage.value = ""
                
                // 添加一个系统消息表示正在取消
                // _chatHistory.value = _chatHistory.value + ChatMessage(
                //     sender = "system",
                //     content = "正在取消当前对话..."
                // )
                
                // 取消当前的AI响应 - 使用try-catch专门捕获取消过程中的错误
                try {
                    enhancedAiService?.cancelConversation()
                    Log.d("ChatViewModel", "成功取消AI对话")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "取消对话时发生错误", e)
                    // 即使出错也继续后续处理
                }
                
                // 移除思考中消息和最后一个AI回复（如果有）
                val updatedHistory = _chatHistory.value.toMutableList()
                
                // 首先移除所有思考中消息和取消中消息
                val historyWithoutThinking = updatedHistory.filterNot { 
                    it.sender == "think" || 
                    (it.sender == "system" && it.content == "正在取消当前对话...") 
                }
                
                // 找到最后一个用户消息的索引
                val lastUserMessageIndex = historyWithoutThinking.indexOfLast { it.sender == "user" }
                

                // 不会清理聊天记录，直接等待下次对话
                // val finalHistory = if (lastUserMessageIndex >= 0) {
                //     historyWithoutThinking.subList(0, lastUserMessageIndex + 1)
                // } else {
                //     historyWithoutThinking
                // }
                
                // // 更新聊天历史
                // _chatHistory.value = finalHistory
                
                // 触发弹窗显示
                showToast("已取消当前对话")
                
                // 延迟保存，确保UI已更新
                kotlinx.coroutines.delay(300)
                
                // 保存当前对话
                saveCurrentChat()
                
                Log.d("ChatViewModel", "取消流程完成")
            } catch (e: Exception) {
                _errorMessage.value = "取消对话失败: ${e.message}"
                Log.e("ChatViewModel", "取消对话过程中发生错误", e)
            }
        }
    }
    
    // Add a method to clear the popup message after it's been shown
    fun clearPopupMessage() {
        _popupMessage.value = null
    }
    
    fun popupMessage(message: String) {
        _popupMessage.value = message
    }
    
    fun deleteChatHistory(chatId: String) {
        viewModelScope.launch {
            try {
                // 如果要删除的是当前聊天，先创建一个新的聊天
                if (chatId == _currentChatId.value) {
                    val newChat = chatHistoryManager.createNewChat()
                    _chatHistory.value = newChat.messages
                }
                
                // 删除聊天历史
                chatHistoryManager.deleteChatHistory(chatId)
            } catch (e: Exception) {
                _errorMessage.value = "删除聊天历史失败: ${e.message}"
            }
        }
    }
    
    // 检查是否应该创建新聊天，确保同步
    private fun checkIfShouldCreateNewChat() {
        // 只有当历史记录和当前对话ID都已加载，且未创建过初始对话时才检查
        if (!historiesLoaded || !currentChatIdLoaded || initialChatCreated) {
            return
        }
        
        // 如果没有当前对话ID，说明需要创建一个新对话
        if (_currentChatId.value == null && _isConfigured.value) {
            initialChatCreated = true
            viewModelScope.launch {
                createNewChat()
            }
        } else {
            // 即使有当前对话ID，也标记为已创建初始对话，避免重复创建
            initialChatCreated = true
        }
    }
    
    /**
     * 处理AI响应的回调方法
     */
    private fun handleAIResponse(content: String, thinking: String?) {
        try {
            // 如果正在加载过程中，才处理消息
            if (!_isLoading.value) {
                Log.d("ChatViewModel", "已取消加载，跳过响应处理")
                return
            }
            
            if (thinking != null && _showThinking.value) {
                // 更新或添加思考消息
                val thinkIndex = _chatHistory.value.indexOfLast { it.sender == "think" }
                if (thinkIndex >= 0) {
                    try {
                        // 更新现有思考消息
                        val updatedHistory = _chatHistory.value.toMutableList()
                        updatedHistory[thinkIndex] = ChatMessage("think", thinking)
                        _chatHistory.value = updatedHistory
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "更新思考消息时出错", e)
                        // 出错时尝试添加新思考消息
                        _chatHistory.value = _chatHistory.value + ChatMessage("think", thinking)
                    }
                } else {
                    // 添加新的思考消息
                    _chatHistory.value = _chatHistory.value + ChatMessage("think", thinking)
                }
            }
            
            // 处理AI响应内容
            if (content.isNotEmpty()) {
                val trimmedContent = content.trim()
                if (trimmedContent.isNotBlank()) {
                    // 检查最后一条用户消息后是否已有AI回复
                    val lastUserIndex = _chatHistory.value.indexOfLast { it.sender == "user" }
                    val lastAiIndex = _chatHistory.value.indexOfLast { it.sender == "ai" }
                    
                    // 处理任务完成标记
                    val finalContent = trimmedContent
                    
                    try {
                        // 只有当这是当前用户消息的第一个AI回复时才更新现有消息，否则创建新消息
                        if (lastAiIndex > lastUserIndex) {
                            // 这是本轮对话中的后续响应，更新已有AI回复
                            val updatedHistory = _chatHistory.value.toMutableList()
                            updatedHistory[lastAiIndex] = ChatMessage("ai", finalContent)
                            _chatHistory.value = updatedHistory
                        } else {
                            // 这是本轮对话的第一个AI回复，创建新消息
                            _chatHistory.value = _chatHistory.value + ChatMessage("ai", finalContent)
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "更新AI响应消息时出错", e)
                        // 出错时尝试添加新AI消息
                        _chatHistory.value = _chatHistory.value + ChatMessage("ai", finalContent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "处理AI响应时发生未处理错误", e)
            // 确保错误不会中断UI更新
        }
    }
    
    /**
     * 准备聊天历史供API使用
     */
    private fun prepareChatHistory(): List<Pair<String, String>> {
        // 使用getMemory代替直接调用ChatUtils
        return getMemory()
    }
    
    /**
     * Handle partial AI responses
     */
    private fun handlePartialResponse(content: String, thinking: String?) {
        handleAIResponse(content, thinking)
    }

    /**
     * Handle completion of AI response
     */
    private fun handleResponseComplete() {
        _isLoading.value = false
        
        // No need to add AI reply to memory as it's already in chat history
        
        // 更新token计数已经在调用处通过updateChatStatistics()完成
        // 该方法内部会保存聊天历史，所以这里不再重复调用saveCurrentChat()
        
        Log.d("ChatViewModel", "AI response complete, loading state reset")
    }
    
    /**
     * Add a message to the chat history
     */
    private fun addMessageToChat(message: ChatMessage) {
        _chatHistory.value = _chatHistory.value + message
    }

    private fun showToast(message: String) {
        _toastEvent.value = message
    }
    
    fun clearToastEvent() {
        _toastEvent.value = null
    }
    
    fun toggleFloatingMode() {
        val newMode = !_isFloatingMode.value
        _isFloatingMode.value = newMode
        
        if (newMode) {
            // Bind to the service when floating mode is enabled
            val intent = Intent(context, FloatingChatService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            // Unbind from service when floating mode is disabled
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error unbinding service", e)
            }
            floatingService = null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Ensure service is unbound when ViewModel is cleared
        if (_isFloatingMode.value) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error unbinding service in onCleared", e)
            }
        }
    }

    /**
     * 从当前聊天历史构建内存记录用于发送到AI服务
     * 只包含user和ai消息，不包含系统消息和思考消息
     */
    private fun getMemory(): List<Pair<String, String>> {
        return _chatHistory.value
            .filter { it.sender == "user" || it.sender == "ai" }
            .map { message ->
                val role = when(message.sender) {
                    "ai" -> "assistant"
                    else -> message.sender
                }
                Pair(role, message.content)
            }
    }
} 