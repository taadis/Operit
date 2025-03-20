package com.ai.assistance.operit.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.AiReference
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.api.InputProcessingState
import com.ai.assistance.operit.data.ApiPreferences
import com.ai.assistance.operit.data.ChatHistoryManager
import com.ai.assistance.operit.model.ChatHistory
import com.ai.assistance.operit.model.ChatMessage
import com.ai.assistance.operit.model.ConversationMarkupManager
import com.ai.assistance.operit.model.ToolExecutionProgress
import com.ai.assistance.operit.model.ToolExecutionState
import com.ai.assistance.operit.service.FloatingChatService
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.NetworkUtils
import com.ai.assistance.operit.util.UserPreferenceAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

// 简单的聊天记忆类，用于存储对话内容
class ChatMemory {
    private val messages = mutableListOf<Pair<String, String>>()
    
    fun add(role: String, content: String) {
        messages.add(Pair(role, content))
    }
    
    fun clear() {
        messages.clear()
    }
    
    fun getAll(): List<Pair<String, String>> {
        return messages.toList()
    }
}

class ChatViewModel(
    private val context: Context
) : ViewModel() {
    
    // Preferences and managers
    private val apiPreferences = ApiPreferences(context)
    private val chatHistoryManager = ChatHistoryManager(context)
    private val chatMemory = ChatMemory()
    
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
                        is InputProcessingState.Idle -> {
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
                        is InputProcessingState.Completed -> {
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
                        is InputProcessingState.Idle -> {
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
                        is InputProcessingState.Completed -> {
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
    
    private fun saveCurrentChat() {
        // Only save if there's at least one user message
        if (_chatHistory.value.size > 1) {
            viewModelScope.launch {
                // Use current chat ID or create a new one
                val chatId = _currentChatId.value ?: UUID.randomUUID().toString()
                
                val title = _chatHistory.value.firstOrNull { it.sender == "user" }?.content?.take(20) ?: "新对话"
                
                // 查找是否已有该ID的聊天历史，以保留原始createdAt
                val existingChat = _chatHistories.value.find { it.id == chatId }
                
                val history = if (existingChat != null) {
                    // 如果是更新现有聊天，则保留原始的createdAt，只更新updatedAt
                    existingChat.copy(
                        title = "$title...",
                        messages = _chatHistory.value,
                        updatedAt = java.time.LocalDateTime.now()
                    )
                } else {
                    // 如果是新聊天，则创建新的ChatHistory对象
                    ChatHistory(
                        id = chatId,
                        title = "$title...",
                        messages = _chatHistory.value
                    )
                }
                
                chatHistoryManager.saveChatHistory(history)
                
                // Set current chat ID if not already set
                if (_currentChatId.value == null) {
                    chatHistoryManager.setCurrentChatId(chatId)
                }
            }
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
                        
                        // 分析用户偏好
                        UserPreferenceAnalyzer.analyzeWithAI(_chatHistory.value, service)
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
    
    fun createNewChat() {
        viewModelScope.launch {
            try {
                // Save current chat before creating a new one
                saveCurrentChat()
                
                // Clear references
                enhancedAiService?.clearReferences()
                
                // Clear chat memory
                chatMemory.clear()
                
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
            
            // Clear chat memory
            chatMemory.clear()
            
            // Switch to selected chat
            chatHistoryManager.setCurrentChatId(chatId)
            val selectedChat = _chatHistories.value.find { it.id == chatId }
            if (selectedChat != null) {
                _chatHistory.value = selectedChat.messages
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
                
                // 保留所有直到最后一个用户消息的内容（包括该用户消息）
                val finalHistory = if (lastUserMessageIndex >= 0) {
                    historyWithoutThinking.subList(0, lastUserMessageIndex + 1)
                } else {
                    historyWithoutThinking
                }
                
                // 更新聊天历史
                _chatHistory.value = finalHistory
                
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
                    val finalContent = if (ConversationMarkupManager.containsTaskCompletion(trimmedContent)) {
                        ConversationMarkupManager.createTaskCompletionContent(trimmedContent)
                    } else {
                        trimmedContent
                    }
                    
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
        return ChatUtils.prepareMessagesForApi(
            _chatHistory.value,
            setOf("user", "ai", "system")
        )
    }
    
    /**
     * 向对话中发送用户消息
     */
    fun sendUserMessage() {
        val message = _userMessage.value.trim()
        if (message.isBlank() || _isLoading.value) return
        
        // 检查网络连接
        if (!NetworkUtils.isNetworkAvailable(context)) {
            _errorMessage.value = "网络不可用，请检查网络连接后重试"
            return
        }
        
        // 清空输入框
        _userMessage.value = ""
        
        // 标记为加载状态
        _isLoading.value = true
        
        // 分析用户偏好（在后台静默执行，不影响主流程）
        UserPreferenceAnalyzer.analyzeUserInput(message)
        
        // 先添加用户消息
        _chatHistory.value = _chatHistory.value + ChatMessage("user", message)
        
        // 只有在网络请求开始时才添加思考消息
        viewModelScope.launch {
            try {
                enhancedAiService?.let { service ->
                    // 添加思考消息
                    if(_showThinking.value) {
                        _chatHistory.value = _chatHistory.value + ChatMessage("think", "思考中...")
                    }
                    
                    // 发送消息
                    sendMessage(message)
                } ?: run {
                    // 如果服务不可用，显示错误
                    _errorMessage.value = "AI服务未初始化"
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "发送消息失败: ${e.message}"
                _isLoading.value = false
            }
        }
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
} 