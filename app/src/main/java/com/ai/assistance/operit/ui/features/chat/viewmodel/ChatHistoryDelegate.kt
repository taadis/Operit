package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 委托类，负责管理聊天历史相关功能 */
class ChatHistoryDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val onChatHistoryLoaded: (List<ChatMessage>) -> Unit,
        private val onTokenStatisticsLoaded: (Int, Int) -> Unit,
        private val resetPlanItems: () -> Unit,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val ensureAiServiceAvailable: () -> Unit = {}, // 确保AI服务可用的回调
        private val getTokenCounts: () -> Pair<Int, Int> = { Pair(0, 0) } // 获取当前token统计数据的回调
) {
    companion object {
        private const val TAG = "ChatHistoryDelegate"
        // 聊天总结的消息数量阈值和间隔
        private const val SUMMARY_CHUNK_SIZE = 5
    }

    private val chatHistoryManager = ChatHistoryManager.getInstance(context)
    private val isInitialized = AtomicBoolean(false)
    private val historyUpdateMutex = Mutex()

    // API设置，用于获取内存优化设置
    private val apiPreferences = ApiPreferences(context)

    // State flows
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _showChatHistorySelector = MutableStateFlow(false)
    val showChatHistorySelector: StateFlow<Boolean> = _showChatHistorySelector.asStateFlow()

    private val _chatHistories = MutableStateFlow<List<ChatHistory>>(emptyList())
    val chatHistories: StateFlow<List<ChatHistory>> = _chatHistories.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    // 跟踪是否已经创建了摘要
    private var summarizationPerformed = false

    init {
        initialize()
    }

    private fun initialize() {
        if (!isInitialized.compareAndSet(false, true)) {
            return
        }

        viewModelScope.launch {
            chatHistoryManager.chatHistoriesFlow.collect { histories ->
                _chatHistories.value = histories
            }
        }

        viewModelScope.launch {
            val initialChatId = chatHistoryManager.currentChatIdFlow.first()
            if (initialChatId != null) {
                _currentChatId.value = initialChatId
                loadChatMessages(initialChatId)
            } else {
                createNewChat()
            }
        }
    }

    private suspend fun loadChatMessages(chatId: String) {
        try {
            // 直接从数据库加载消息
            val messages = chatHistoryManager.loadChatMessages(chatId)
            Log.d(TAG, "加载聊天 $chatId 的消息：${messages.size} 条")

            // 无论消息是否为空，都更新聊天历史
            _chatHistory.value = messages
            onChatHistoryLoaded(messages)

            // 查找聊天元数据，更新token统计
            val selectedChat = _chatHistories.value.find { it.id == chatId }
            if (selectedChat != null) {
                onTokenStatisticsLoaded(selectedChat.inputTokens, selectedChat.outputTokens)

                // 清空并重新提取计划项
                resetPlanItems()
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载聊天消息失败", e)
        }
    }

    /** 检查是否应该创建新聊天，确保同步 */
    fun checkIfShouldCreateNewChat(): Boolean {
        // 只有当历史记录和当前对话ID都已加载，且未创建过初始对话时才检查
        if (!isInitialized.get() || _currentChatId.value == null) {
            return false
        }
        return true
    }

    /** 创建新的聊天 */
    fun createNewChat() {
        viewModelScope.launch {
            val (inputTokens, outputTokens) = getTokenCounts()
            saveCurrentChat(inputTokens, outputTokens)

            val newChat = chatHistoryManager.createNewChat()
            _currentChatId.value = newChat.id
            _chatHistory.value = newChat.messages

            onChatHistoryLoaded(newChat.messages)
            onTokenStatisticsLoaded(0, 0)
            resetPlanItems()
        }
    }

    /** 切换聊天 */
    fun switchChat(chatId: String) {
        viewModelScope.launch {
            val (inputTokens, outputTokens) = getTokenCounts()
            saveCurrentChat(inputTokens, outputTokens)

            chatHistoryManager.setCurrentChatId(chatId)
            _currentChatId.value = chatId

            loadChatMessages(chatId)
        }
    }

    /** 删除聊天历史 */
    fun deleteChatHistory(chatId: String) {
        viewModelScope.launch {
            if (chatId == _currentChatId.value) {
                chatHistoryManager.deleteChatHistory(chatId)
                createNewChat()
            } else {
                chatHistoryManager.deleteChatHistory(chatId)
            }
        }
    }

    /** 清空当前聊天 */
    fun clearCurrentChat() {
        viewModelScope.launch {
            _currentChatId.value?.let { chatHistoryManager.deleteChatHistory(it) }
            createNewChat()
        }
    }

    /** 保存当前聊天到持久存储 */
    fun saveCurrentChat(inputTokens: Int = 0, outputTokens: Int = 0) {
        viewModelScope.launch {
            _currentChatId.value?.let { chatId ->
                if (_chatHistory.value.isNotEmpty()) {
                    chatHistoryManager.updateChatTokenCounts(chatId, inputTokens, outputTokens)
                }
            }
        }
    }

    /** 更新聊天标题 */
    fun updateChatTitle(title: String) {
        viewModelScope.launch {
            _currentChatId.value?.let { chatId ->
                // 更新数据库
                chatHistoryManager.updateChatTitle(chatId, title)

                // 更新UI状态
                val updatedHistories = _chatHistories.value.map {
                    if (it.id == chatId) {
                        it.copy(title = title, updatedAt = LocalDateTime.now())
                    } else {
                        it
                    }
                }
                _chatHistories.value = updatedHistories
            }
        }
    }

    /** 根据第一条用户消息生成聊天标题 */
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

    /** 添加消息到当前聊天 */
    fun addMessageToChat(message: ChatMessage) {
        viewModelScope.launch {
            historyUpdateMutex.withLock {
                val chatId = _currentChatId.value ?: return@withLock

                val currentMessages = _chatHistory.value
                val existingMessageIndex = currentMessages.indexOfFirst { it.timestamp == message.timestamp }

                if (existingMessageIndex != -1) {
                    chatHistoryManager.updateMessage(chatId, message)
                    
                } else {
                    val newMessages = currentMessages + message
                    _chatHistory.value = newMessages
                    onChatHistoryLoaded(newMessages) // 通知UI更新
                    chatHistoryManager.addMessage(chatId, message)
                }
            }
        }
    }

    /** 更新整个聊天历史 用于编辑或回档等操作 */
    fun updateChatHistory(newHistory: List<ChatMessage>) {
        _chatHistory.value = newHistory.toList()
        onChatHistoryLoaded(_chatHistory.value)
    }

    /** 检查是否应该生成总结 */
    fun shouldGenerateSummary(messages: List<ChatMessage>): Boolean {
        // 获取用户和AI消息
        val userAiMessages = messages.filter { it.sender == "user" || it.sender == "ai" }

        // 检查最近的消息中是否已经有总结消息
        val recentMessages =
                messages
                        .filter {
                            it.sender == "user" || it.sender == "ai" || it.sender == "summary"
                        }
                        .takeLast(minOf(SUMMARY_CHUNK_SIZE * 2, messages.size))
        val hasRecentSummary = recentMessages.any { it.sender == "summary" }

        // 如果最近已经有总结消息，不需要再生成
        if (hasRecentSummary) {
            Log.d(TAG, "最近消息中已有总结，跳过总结生成")
            return false
        }

        // 如果消息数量不足，不需要总结
        if (userAiMessages.size < SUMMARY_CHUNK_SIZE * 2) {
            Log.d(TAG, "消息数量不足，不生成总结. 当前消息数: ${userAiMessages.size}")
            return false
        }
        return true
    }

    /** 生成记忆总结 */
    suspend fun summarizeMemory(messages: List<ChatMessage>) {
        try {
            Log.d(TAG, "开始生成记忆总结...")
            val messagesForSummary = messages.filter { it.sender == "user" || it.sender == "ai" }
            val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
            val previousSummary =
                    if (lastSummaryIndex != -1) {
                        messages[lastSummaryIndex].content.trim()
                    } else {
                        null
                    }
            val messagesToSummarize =
                    if (lastSummaryIndex == -1) {
                        messagesForSummary.take(SUMMARY_CHUNK_SIZE*2)
                    } else {
                        val messagesAfterLastSummary = mutableListOf<ChatMessage>()
                        for (i in (lastSummaryIndex + 1) until messages.size) {
                            if (messages[i].sender == "user" || messages[i].sender == "ai") {
                                messagesAfterLastSummary.add(messages[i])
                                if (messagesAfterLastSummary.size >= SUMMARY_CHUNK_SIZE*2) {
                                    break
                                }
                            }
                        }
                        messagesAfterLastSummary
                    }

            if (messagesToSummarize.isEmpty()) {
                Log.d(TAG, "没有新消息需要总结")
                return
            }

            Log.d(TAG, "将总结 ${messagesToSummarize.size} 条消息")

            // 确保AI服务可用
            ensureAiServiceAvailable()

            // 等待一段时间以允许创建AI服务
            kotlinx.coroutines.delay(100)

            // 获取API服务实例
            val enhancedAiService = getEnhancedAiService()
            if (enhancedAiService == null) {
                Log.e(TAG, "AI服务不可用，无法生成总结")
                return
            }

            try {
                // 将消息转换为AI可以处理的格式
                val conversationToSummarize =
                        messagesToSummarize.mapIndexed { index, message ->
                            val role = if (message.sender == "user") "user" else "assistant"
                            Pair(role, "#${index + 1}: ${message.content}")
                        }

                Log.d(TAG, "开始使用AI生成对话总结：总结 ${messagesToSummarize.size} 条消息")

                // 如果有上一条摘要，传入它作为上下文
                val summary =
                        if (previousSummary != null) {
                            Log.d(TAG, "使用上一条摘要作为上下文生成新的总结")
                            enhancedAiService.generateSummary(
                                    conversationToSummarize,
                                    previousSummary
                            )
                        } else {
                            enhancedAiService.generateSummary(conversationToSummarize)
                        }

                Log.d(TAG, "AI生成总结完成: ${summary.take(50)}...")

                if (summary.isBlank()) {
                    Log.e(TAG, "AI生成的总结内容为空，放弃本次总结")
                    return
                }

                // 创建总结消息
                val summaryMessage =
                        ChatMessage(
                                sender = "summary",
                                content = summary.trim(),
                                timestamp = System.currentTimeMillis()
                        )

                // 获取当前最新的消息列表
                val currentMessages = _chatHistory.value
                val newMessages = currentMessages.toMutableList()
                val insertPosition = findProperSummaryPosition(newMessages)
                Log.d(TAG, "计算出的总结插入位置: $insertPosition，总消息数量: ${newMessages.size}")

                // 在确定的位置插入总结消息
                newMessages.add(insertPosition, summaryMessage)
                Log.d(TAG, "在索引 $insertPosition 处添加总结消息，总消息数量: ${newMessages.size}")

                // 更新消息列表
                _chatHistory.value = newMessages
                // 通知ViewModel聊天历史已更新
                onChatHistoryLoaded(_chatHistory.value)

                // 更新数据库
                _currentChatId.value?.let { currentChatId ->
                    chatHistoryManager.addMessage(currentChatId, summaryMessage, insertPosition)
                }

                val totalMessages = newMessages.count { it.sender == "user" || it.sender == "ai" }
                Log.d(TAG, "总结完成，总消息数 $totalMessages")
            } catch (e: Exception) {
                Log.e(TAG, "AI生成总结失败", e)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "记忆总结失败", e)
        }
    }

    /** 找到合适的总结插入位置 对于第一次总结，放在前SUMMARY_CHUNK_SIZE条消息后面 对于后续总结，放在上一次总结之后的SUMMARY_CHUNK_SIZE条消息后面 */
    private fun findProperSummaryPosition(messages: List<ChatMessage>): Int {
        var lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
        if (lastSummaryIndex == -1) {
            lastSummaryIndex = 0
        }
            var userAiCount = 0
            for (i in messages.indices) {
                if (messages[i].sender == "ai") {
                    userAiCount++
                    if (userAiCount == SUMMARY_CHUNK_SIZE) {
                        return i + 1
                    }
                }
            }
            return messages.size
        
    }

    /** 切换是否显示聊天历史选择器 */
    fun toggleChatHistorySelector() {
        _showChatHistorySelector.value = !_showChatHistorySelector.value
    }

    /** 显示或隐藏聊天历史选择器 */
    fun showChatHistorySelector(show: Boolean) {
        _showChatHistorySelector.value = show
    }

    /** 获取当前聊天历史的内存记录 只包含user和ai消息，不包含系统消息和思考消息 */
    fun getMemory(includePlanInfo: Boolean = true): List<Pair<String, String>> {
        return _chatHistory.value.filter { it.sender == "user" || it.sender == "ai" }.map {
            val role = if (it.sender == "ai") "assistant" else "user"
            Pair(role, it.content)
        }
    }

    /** 获取EnhancedAIService实例 */
    private fun getEnhancedAiService(): EnhancedAIService? {
        // 使用构造函数中传入的callback获取EnhancedAIService实例
        return getEnhancedAiService.invoke()
    }

    /** 通过回调获取当前token统计数据 */
    private fun getCurrentTokenCounts(): Pair<Int, Int> {
        // 使用构造函数中传入的回调获取当前token统计数据
        return getTokenCounts()
    }
}
