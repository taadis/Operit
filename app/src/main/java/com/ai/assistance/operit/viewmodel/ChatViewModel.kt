package com.ai.assistance.operit.viewmodel

import android.content.Context
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
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val context: Context
) : ViewModel() {
    
    // Preferences and managers
    private val apiPreferences = ApiPreferences(context)
    private val chatHistoryManager = ChatHistoryManager(context)
    
    // API service
    private var enhancedAiService: EnhancedAIService? = null
    
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
    
    private val chatMemory = mutableListOf<Pair<String, String>>()
    
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
            }
        }
        
        viewModelScope.launch {
            chatHistoryManager.currentChatIdFlow.collect { chatId ->
                _currentChatId.value = chatId
                if (chatId != null) {
                    val selectedChat = _chatHistories.value.find { it.id == chatId }
                    if (selectedChat != null) {
                        _chatHistory.value = selectedChat.messages
                    }
                }
            }
        }
    }
    
    private fun checkAndInitializeService() {
        val key = _apiKey.value
        val endpoint = _apiEndpoint.value
        val model = _modelName.value
        
        if (key.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank()) {
            if (enhancedAiService == null) {
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
                
                // Initialize chat if needed
                if (_currentChatId.value == null) {
                    createNewChat()
                }
            }
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
            
            // Create a new chat history when first configuring
            if (_currentChatId.value == null) {
                createNewChat()
            }
        }
    }
    
    private fun saveCurrentChat() {
        // Only save if there's at least one user message
        if (_chatHistory.value.size > 1) {
            viewModelScope.launch {
                // Use current chat ID or create a new one
                val chatId = _currentChatId.value ?: UUID.randomUUID().toString()
                
                val title = _chatHistory.value.firstOrNull { it.sender == "user" }?.content?.take(20) ?: "新对话"
                
                val history = ChatHistory(
                    id = chatId,
                    title = "$title...",
                    messages = _chatHistory.value
                )
                chatHistoryManager.saveChatHistory(history)
                
                // Set current chat ID if not already set
                if (_currentChatId.value == null) {
                    chatHistoryManager.setCurrentChatId(chatId)
                }
            }
        }
    }
    
    fun sendUserMessage() {
        val message = _userMessage.value.trim()
        if (message.isBlank() || _isLoading.value) return
        
        // Check network connectivity
        if (!NetworkUtils.isNetworkAvailable(context)) {
            _errorMessage.value = "网络不可用，请检查网络连接后重试"
            return
        }
        
        _userMessage.value = ""
        var hasAiResponse = false
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Prepare chat history for the API
                val currentChatHistory = ChatUtils.prepareMessagesForApi(
                    _chatHistory.value,
                    setOf("user", "ai", "system")
                )
                
                // Add user message to chat history
                _chatHistory.value = _chatHistory.value + ChatMessage("user", message)
                
                // Add thinking message
                _chatHistory.value = _chatHistory.value + ChatMessage("think", "思考中...")
                
                enhancedAiService?.sendMessage(
                    message = message,
                    onPartialResponse = { content, thinking ->
                        if (thinking != null) {
                            // Update thinking message
                            val thinkIndex = _chatHistory.value.indexOfLast { it.sender == "think" }
                            if (thinkIndex >= 0) {
                                val updatedHistory = _chatHistory.value.toMutableList()
                                updatedHistory[thinkIndex] = ChatMessage("think", thinking)
                                _chatHistory.value = updatedHistory
                            }
                        }
                        
                        // Update AI response content
                        if (content.isNotEmpty()) {
                            val trimmedContent = content.trim()
                            if (trimmedContent.isNotBlank()) {
                                if (hasAiResponse) {
                                    // Update existing AI response
                                    val aiIndex = _chatHistory.value.indexOfLast { it.sender == "ai" }
                                    if (aiIndex >= 0) {
                                        val finalContent = if (ConversationMarkupManager.containsTaskCompletion(trimmedContent)) {
                                            ConversationMarkupManager.createTaskCompletionContent(trimmedContent)
                                        } else {
                                            trimmedContent
                                        }
                                        
                                        val updatedHistory = _chatHistory.value.toMutableList()
                                        updatedHistory[aiIndex] = ChatMessage("ai", finalContent)
                                        _chatHistory.value = updatedHistory
                                    }
                                } else {
                                    // Add new AI response
                                    hasAiResponse = true
                                    
                                    val finalContent = if (ConversationMarkupManager.containsTaskCompletion(trimmedContent)) {
                                        ConversationMarkupManager.createTaskCompletionContent(trimmedContent)
                                    } else {
                                        trimmedContent
                                    }
                                    
                                    _chatHistory.value = _chatHistory.value + ChatMessage("ai", finalContent)
                                }
                            }
                        }
                    },
                    chatHistory = currentChatHistory,
                    onComplete = {
                        // Save chat history when AI response is complete
                        saveCurrentChat()
                    }
                ) ?: run {
                    _errorMessage.value = "增强AI服务未初始化，请检查设置"
                    _isProcessingInput.value = false
                }
                
            } catch (e: Exception) {
                _errorMessage.value = e.message
                
                // Add error message to chat
                val errorMessage = ConversationMarkupManager.createToolErrorStatus("ai_service", "错误: ${e.message}")
                _chatHistory.value = _chatHistory.value + ChatMessage("ai", errorMessage)
                
                // Save chat history even on error
                saveCurrentChat()
                
                _isProcessingInput.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun createNewChat() {
        viewModelScope.launch {
            // Save current chat first
            saveCurrentChat()
            
            // Check if previous chat is empty
            if (_currentChatId.value != null) {
                val currentChat = _chatHistories.value.find { it.id == _currentChatId.value }
                if (currentChat != null && 
                    currentChat.messages.none { it.sender == "user" }) {
                    // If previous chat is empty, just use it
                    return@launch
                }
            }
            
            // Clear references
            enhancedAiService?.clearReferences()
            
            // Clear chat memory
            chatMemory.clear()
            
            // Create new chat
            val newChat = chatHistoryManager.createNewChat()
            _chatHistory.value = newChat.messages
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
} 