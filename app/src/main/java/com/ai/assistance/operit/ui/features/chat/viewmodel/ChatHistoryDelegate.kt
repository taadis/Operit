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
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 委托类，负责管理聊天历史相关功能 */
class ChatHistoryDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val onChatHistoryLoaded: (List<ChatMessage>) -> Unit,
        private val onTokenStatisticsLoaded: (Int, Int) -> Unit,
        private val resetPlanItems: () -> Unit,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val ensureAiServiceAvailable: () -> Unit = {} // 确保AI服务可用的回调
) {
    companion object {
        private const val TAG = "ChatHistoryDelegate"
        // 聊天总结的消息数量阈值和间隔
        private const val SUMMARY_CHUNK_SIZE = 5
    }

    // 聊天历史管理器
    private val chatHistoryManager = ChatHistoryManager(context)

    // API设置，用于获取内存优化设置
    private val apiPreferences = ApiPreferences(context)

    // 加载状态跟踪
    private var historiesLoaded = false
    private var currentChatIdLoaded = false

    // 防止重复创建聊天
    private var initialChatCreated = false

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
        // 初始化数据收集
        initializeDataCollection()
    }

    private fun initializeDataCollection() {
        // 收集聊天历史列表
        viewModelScope.launch {
            chatHistoryManager.chatHistoriesFlow.collect { histories ->
                _chatHistories.value = histories
                historiesLoaded = true
                checkIfShouldCreateNewChat()
            }
        }

        // 收集当前聊天ID
        viewModelScope.launch {
            chatHistoryManager.currentChatIdFlow.collect { chatId ->
                _currentChatId.value = chatId

                if (currentChatIdLoaded) {
                    return@collect
                }

                currentChatIdLoaded = true
                checkIfShouldCreateNewChat()

                if (chatId != null) {
                    val selectedChat = _chatHistories.value.find { it.id == chatId }
                    if (selectedChat != null) {
                        // 更新聊天历史
                        _chatHistory.value = selectedChat.messages
                        // 通知ViewModel聊天历史已加载
                        onChatHistoryLoaded(selectedChat.messages)

                        // 通知ViewModel恢复token统计
                        onTokenStatisticsLoaded(selectedChat.inputTokens, selectedChat.outputTokens)

                        // 清空并重新提取计划项
                        resetPlanItems()
                    }
                }
            }
        }
    }

    /** 检查是否应该创建新聊天，确保同步 */
    fun checkIfShouldCreateNewChat(): Boolean {
        // 只有当历史记录和当前对话ID都已加载，且未创建过初始对话时才检查
        if (!historiesLoaded || !currentChatIdLoaded || initialChatCreated) {
            return false
        }

        // 如果没有当前对话ID，说明需要创建一个新对话
        if (_currentChatId.value == null) {
            initialChatCreated = true
            return true
        } else {
            // 即使有当前对话ID，也标记为已创建初始对话，避免重复创建
            initialChatCreated = true
            return false
        }
    }

    /** 创建新的聊天 */
    fun createNewChat() {
        viewModelScope.launch {
            try {
                // 保存当前聊天
                saveCurrentChat(0, 0)

                // 清空计划项
                resetPlanItems()

                // 创建新聊天
                val newChat = chatHistoryManager.createNewChat()

                // 更新UI状态
                _chatHistory.value = newChat.messages

                // 通知ViewModel聊天历史已加载
                onChatHistoryLoaded(newChat.messages)

                // 通知ViewModel重置token统计
                onTokenStatisticsLoaded(0, 0)

                // 确保计划项被清空
                resetPlanItems()

                Log.d(TAG, "新聊天创建完成")
            } catch (e: Exception) {
                Log.e(TAG, "创建新聊天失败", e)
            }
        }
    }

    /** 切换聊天 */
    fun switchChat(chatId: String) {
        viewModelScope.launch {
            try {
                // 保存当前聊天
                saveCurrentChat(0, 0)

                // 清空计划项
                resetPlanItems()

                // 切换到选定的聊天
                chatHistoryManager.setCurrentChatId(chatId)
                val selectedChat = _chatHistories.value.find { it.id == chatId }
                if (selectedChat != null) {
                    _chatHistory.value = selectedChat.messages

                    // 通知ViewModel聊天历史已加载
                    onChatHistoryLoaded(selectedChat.messages)

                    // 通知ViewModel恢复token统计
                    onTokenStatisticsLoaded(selectedChat.inputTokens, selectedChat.outputTokens)

                    // 从聊天历史中重新提取计划项
                    Log.d(TAG, "从聊天历史中重新提取计划项")

                    // 检查是否存在总结消息
                    val hasSummary = selectedChat.messages.any { it.sender == "summary" }
                    if (hasSummary) {
                        Log.d(TAG, "检测到聊天历史中已有总结消息")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "切换聊天失败", e)
            }
        }
    }

    /** 删除聊天历史 */
    fun deleteChatHistory(chatId: String) {
        viewModelScope.launch {
            try {
                // 如果要删除的是当前聊天，先创建一个新的聊天
                if (chatId == _currentChatId.value) {
                    val newChat = chatHistoryManager.createNewChat()
                    _chatHistory.value = newChat.messages

                    // 通知ViewModel聊天历史已加载
                    onChatHistoryLoaded(newChat.messages)

                    // 通知ViewModel重置token统计
                    onTokenStatisticsLoaded(0, 0)
                }

                // 删除聊天历史
                chatHistoryManager.deleteChatHistory(chatId)
            } catch (e: Exception) {
                Log.e(TAG, "删除聊天历史失败", e)
            }
        }
    }

    /** 清空当前聊天 */
    fun clearCurrentChat() {
        viewModelScope.launch {
            try {
                // 清空聊天历史
                _chatHistory.value = emptyList()

                // 通知ViewModel聊天历史已清空
                onChatHistoryLoaded(emptyList())

                Log.d(TAG, "清空聊天")

                // 通知ViewModel重置token统计
                onTokenStatisticsLoaded(0, 0)

                // 清空计划项
                resetPlanItems()

                // 创建新的聊天ID
                val newChatId = UUID.randomUUID().toString()
                chatHistoryManager.setCurrentChatId(newChatId)
                _currentChatId.value = newChatId

                Log.d(TAG, "聊天记录已清空")
            } catch (e: Exception) {
                Log.e(TAG, "清空聊天记录失败", e)
            }
        }
    }

    /** 保存当前聊天到持久存储 */
    fun saveCurrentChat(inputTokens: Int = 0, outputTokens: Int = 0) {
        viewModelScope.launch {
            try {
                val currentId = _currentChatId.value ?: UUID.randomUUID().toString()

                // 仅在有消息时保存
                if (_chatHistory.value.isNotEmpty()) {
                    // 查找是否已有该ID的聊天历史，以保留原始createdAt
                    val existingChat = _chatHistories.value.find { it.id == currentId }

                    val history =
                            if (existingChat != null) {
                                // 如果是更新现有聊天，则保留原始的createdAt，只更新updatedAt
                                existingChat.copy(
                                        title = generateChatTitle(),
                                        messages = _chatHistory.value,
                                        updatedAt = LocalDateTime.now(),
                                        inputTokens = inputTokens,
                                        outputTokens = outputTokens
                                )
                            } else {
                                // 如果是新聊天，则创建新的ChatHistory对象
                                ChatHistory(
                                        id = currentId,
                                        title = generateChatTitle(),
                                        messages = _chatHistory.value,
                                        inputTokens = inputTokens,
                                        outputTokens = outputTokens
                                )
                            }

                    chatHistoryManager.saveChatHistory(history)

                    // 设置当前聊天ID（如果尚未设置）
                    if (_currentChatId.value == null) {
                        chatHistoryManager.setCurrentChatId(currentId)
                        _currentChatId.value = currentId
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save chat history", e)
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
            try {
                // 获取当前消息列表
                val currentMessages = _chatHistory.value
                val currentTypeIndex = currentMessages.indexOfLast { it.sender == message.sender }
                val lastUserIndex = currentMessages.indexOfLast { it.sender == "user" }

                // 检查是否需要进行记忆优化
                val memoryOptimizationEnabled = apiPreferences.memoryOptimizationFlow.first()

                if (memoryOptimizationEnabled && message.sender == "user") {
                    // 先正常添加消息到历史记录中
                    val updatedMessages =
                            addMessageNormally(
                                    message,
                                    currentMessages,
                                    currentTypeIndex,
                                    lastUserIndex
                            )

                    // 检查是否需要生成总结
                    val shouldSummarize = shouldGenerateSummary(updatedMessages)

                    // 仅当应该总结时触发总结
                    if (shouldSummarize) {
                        Log.d(TAG, "触发记忆总结")
                        // 将总结任务放在单独的协程中执行，不阻塞消息添加流程
                        viewModelScope.launch { summarizeMemory(updatedMessages) }
                    }

                    // 无论是否触发总结，都立即通知ViewModel消息已更新，不等待总结完成
                    onChatHistoryLoaded(_chatHistory.value)
                } else {
                    // 不需要优化，正常添加消息
                    addMessageNormally(message, currentMessages, currentTypeIndex, lastUserIndex)

                    // 通知ViewModel聊天历史已更新
                    onChatHistoryLoaded(_chatHistory.value)
                }

                // 自动保存聊天历史
                saveCurrentChat(0, 0)
            } catch (e: Exception) {
                Log.e(TAG, "添加消息到聊天失败", e)
            }
        }
    }

    /** 检查是否应该生成总结 */
    private fun shouldGenerateSummary(messages: List<ChatMessage>): Boolean {
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

        // 找到最后一条总结消息的位置
        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }

        // 计算自上次总结后的新消息数量
        val newMessagesCount =
                if (lastSummaryIndex == -1) {
                    // 如果没有总结消息，则所有消息都是新消息
                    userAiMessages.size
                } else {
                    // 计算最后一条总结后的user/ai消息数量
                    var count = 0
                    for (i in (lastSummaryIndex + 1) until messages.size) {
                        if (messages[i].sender == "user" || messages[i].sender == "ai") {
                            count++
                        }
                    }
                    count
                }

        // 只有当新消息数量达到阈值时才生成总结
        val shouldSummarize = newMessagesCount >= SUMMARY_CHUNK_SIZE * 2

        if (shouldSummarize) {
            Log.d(TAG, "需要生成总结. 自上次总结后的新消息数量: $newMessagesCount")
        } else {
            Log.d(TAG, "未达到总结条件. 自上次总结后的新消息数量: $newMessagesCount")
        }

        return shouldSummarize
    }

    /** 正常添加消息到聊天 */
    private fun addMessageNormally(
            message: ChatMessage,
            currentMessages: List<ChatMessage>,
            currentTypeIndex: Int,
            lastUserIndex: Int
    ): List<ChatMessage> {
        // 使用原来的逻辑
        when (message.sender) {
            "think" -> {
                // 处理思考消息：如果已有思考消息且在最后一条用户消息之后，则更新它
                if (currentTypeIndex >= 0 && currentTypeIndex > lastUserIndex) {
                    val newMessages = currentMessages.toMutableList()
                    newMessages[currentTypeIndex] = message
                    _chatHistory.value = newMessages
                } else {
                    // 否则添加新消息
                    _chatHistory.value = currentMessages + message
                }
            }
            "ai" -> {
                // 处理AI消息：如果已有AI消息且在最后一条用户消息之后，则更新它
                if (currentTypeIndex >= 0 && currentTypeIndex > lastUserIndex) {
                    val newMessages = currentMessages.toMutableList()
                    newMessages[currentTypeIndex] = message
                    _chatHistory.value = newMessages
                } else {
                    // 否则添加新消息
                    _chatHistory.value = currentMessages + message
                }
            }
            else -> {
                // 对于其他消息类型（如用户消息），直接添加到列表末尾
                _chatHistory.value = currentMessages + message
            }
        }

        return _chatHistory.value
    }

    /** 生成记忆总结 */
    private suspend fun summarizeMemory(messages: List<ChatMessage>) {
        try {
            Log.d(TAG, "开始生成记忆总结...")

            // 只有用户和AI消息才会被总结，过滤其他类型的消息
            val messagesForSummary = messages.filter { it.sender == "user" || it.sender == "ai" }

            // 找到最后一条总结消息
            val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }

            // 获取上一条摘要内容（如果存在）
            val previousSummary =
                    if (lastSummaryIndex != -1) {
                        messages[lastSummaryIndex].content.trim()
                    } else {
                        null
                    }

            // 计算要总结的消息范围
            val messagesToSummarize =
                    if (lastSummaryIndex == -1) {
                        // 如果没有总结消息，则总结最前面的SUMMARY_CHUNK_SIZE条
                        messagesForSummary.take(SUMMARY_CHUNK_SIZE)
                    } else {
                        // 从最后一条总结后开始，选取最多SUMMARY_CHUNK_SIZE条消息
                        val messagesAfterLastSummary = mutableListOf<ChatMessage>()
                        for (i in (lastSummaryIndex + 1) until messages.size) {
                            if (messages[i].sender == "user" || messages[i].sender == "ai") {
                                messagesAfterLastSummary.add(messages[i])
                                if (messagesAfterLastSummary.size >= SUMMARY_CHUNK_SIZE) {
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
            kotlinx.coroutines.delay(600)

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

                // 完全重写总结插入位置逻辑，确保总结被插入到正确位置
                val insertPosition = findProperSummaryPosition(newMessages)
                Log.d(TAG, "计算出的总结插入位置: $insertPosition，总消息数量: ${newMessages.size}")

                // 在确定的位置插入总结消息
                newMessages.add(insertPosition, summaryMessage)
                Log.d(TAG, "在索引 $insertPosition 处添加总结消息，总消息数量: ${newMessages.size}")

                // 更新消息列表
                _chatHistory.value = newMessages

                // 通知ViewModel聊天历史已更新
                onChatHistoryLoaded(_chatHistory.value)

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
        // 找到最后一条总结消息的位置
        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }

        // 如果没有找到总结消息，说明是第一次总结
        if (lastSummaryIndex == -1) {
            // 找到第SUMMARY_CHUNK_SIZE条user/ai消息在完整消息列表中的位置
            var userAiCount = 0
            for (i in messages.indices) {
                if (messages[i].sender == "user" || messages[i].sender == "ai") {
                    userAiCount++
                    if (userAiCount == SUMMARY_CHUNK_SIZE) {
                        return i + 1 // 在第SUMMARY_CHUNK_SIZE条消息后插入
                    }
                }
            }
            // 如果没有足够的消息，插入到末尾
            return messages.size
        } else {
            // 在最后一条总结之后找SUMMARY_CHUNK_SIZE条user/ai消息
            var userAiCount = 0
            for (i in (lastSummaryIndex + 1) until messages.size) {
                if (messages[i].sender == "user" || messages[i].sender == "ai") {
                    userAiCount++
                    if (userAiCount == SUMMARY_CHUNK_SIZE) {
                        return i + 1 // 在最后一条总结后的第SUMMARY_CHUNK_SIZE条消息后插入
                    }
                }
            }
            // 如果没有足够的消息，插入到末尾
            return messages.size
        }
    }

    /** 更新聊天历史 */
    fun updateChatHistory(messages: List<ChatMessage>) {
        _chatHistory.value = messages
    }

    /** 切换聊天历史选择器的显示状态 */
    fun toggleChatHistorySelector() {
        _showChatHistorySelector.value = !_showChatHistorySelector.value
    }

    /** 显示或隐藏聊天历史选择器 */
    fun showChatHistorySelector(show: Boolean) {
        _showChatHistorySelector.value = show
    }

    /** 获取当前聊天历史的内存记录 只包含user和ai消息，不包含系统消息和思考消息 */
    fun getMemory(includePlanInfo: Boolean = true): List<Pair<String, String>> {
        // 获取所有消息
        val allMessages = _chatHistory.value

        // 构建最终返回的消息列表
        val result = mutableListOf<Pair<String, String>>()

        // 查找最新的总结消息位置
        val lastSummaryIndex = allMessages.indexOfLast { it.sender == "summary" }
        // val allSummaryMessage = allMessages.filter { it.sender == "summary" }

        // 如果找到总结消息
        if (lastSummaryIndex != -1) {
            var firstUserMessage = ""
            // for (summaryMessage in allSummaryMessage) {
            val summaryMessage = allMessages[lastSummaryIndex]
            val summaryContent = summaryMessage.content.replace(Regex("^#+\\s*对话摘要[：:]*\\s*"), "").trim()

            firstUserMessage += "【历史对话摘要】\n$summaryContent\n"
            Log.d(TAG, "添加最新总结消息到内存记录: ${summaryMessage.content.take(50)}...")
            // }

            firstUserMessage += "【用户本次对话】\n"

            var firstUserAsk = true

            for (i in (lastSummaryIndex + 1) until allMessages.size) {
                val message = allMessages[i]
                if (message.sender == "user" || message.sender == "ai") {
                    if (firstUserAsk && message.sender == "user") {
                        firstUserAsk = false
                        firstUserMessage += message.content
                        result.add(Pair("user", firstUserMessage))
                    } else {
                        val role = if (message.sender == "ai") "assistant" else "user"
                        result.add(Pair(role, message.content))
                    }
                }
            }
        } else {
            // 如果没有总结消息，则正常处理所有消息
            for (message in allMessages) {
                if (message.sender == "user" || message.sender == "ai") {
                    val role = if (message.sender == "ai") "assistant" else "user"
                    result.add(Pair(role, message.content))
                }
            }
        }

        Log.d(TAG, "获取内存记录完成，共 ${result.size} 条消息，包含总结：${lastSummaryIndex != -1}")
        return result
    }

    /** 获取EnhancedAIService实例 */
    private fun getEnhancedAiService(): EnhancedAIService? {
        // 使用构造函数中传入的callback获取EnhancedAIService实例
        return getEnhancedAiService.invoke()
    }
}
