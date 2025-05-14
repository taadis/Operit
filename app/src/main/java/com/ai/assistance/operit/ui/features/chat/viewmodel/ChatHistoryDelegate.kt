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
        // 触发内存优化的消息数量阈值
        private const val MEMORY_OPTIMIZATION_THRESHOLD = 10
        // 内存优化时保留的最近消息数量
        private const val KEPT_RECENT_MESSAGES = 5
        // 每次总结的消息数量
        private const val SUMMARY_INTERVAL = 10
        // 检查重复总结的消息范围
        private const val RECENT_MESSAGE_CHECK_COUNT = 15
    }

    // 聊天历史管理器
    private val chatHistoryManager = ChatHistoryManager(context)

    // API设置，用于获取内存优化设置
    private val apiPreferences = ApiPreferences(context)

    // 记录上次总结的位置
    private var lastSummarizedIndex = 0

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

                // 重置总结位置
                lastSummarizedIndex = 0

                Log.d(TAG, "新聊天创建完成，重置总结索引")
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

                    // 重置总结索引 - 计算当前已有的user/ai消息数量
                    val userAiMessages =
                            selectedChat.messages.filter {
                                it.sender == "user" || it.sender == "ai"
                            }
                    lastSummarizedIndex = userAiMessages.size
                    Log.d(TAG, "切换聊天，设置总结索引为 $lastSummarizedIndex")

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

                // 重置总结位置
                lastSummarizedIndex = 0
                Log.d(TAG, "清空聊天，重置总结索引")

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

                    // 检查最近的消息中是否已经有总结消息
                    val recentMessages =
                            updatedMessages.takeLast(
                                    minOf(RECENT_MESSAGE_CHECK_COUNT, updatedMessages.size)
                            )
                    val hasRecentSummary = recentMessages.any { it.sender == "summary" }

                    // 获取用户和AI消息的数量
                    val userAiMessages =
                            updatedMessages.filter { it.sender == "user" || it.sender == "ai" }

                    // 计算新消息数量 - 使用总数而不是相对索引，简化逻辑
                    val messageCount = userAiMessages.size
                    val shouldSummarize =
                            messageCount >= MEMORY_OPTIMIZATION_THRESHOLD &&
                                    (messageCount - lastSummarizedIndex) >= SUMMARY_INTERVAL

                    // 仅当应该总结且最近没有总结消息时触发总结
                    if (shouldSummarize && !hasRecentSummary) {
                        Log.d(
                                TAG,
                                "触发记忆总结: 总消息数量 $messageCount, 上次总结索引 $lastSummarizedIndex, 新消息数量 ${messageCount - lastSummarizedIndex}"
                        )
                        // 将总结任务放在单独的协程中执行，不阻塞消息添加流程
                        viewModelScope.launch { summarizeMemory(updatedMessages) }
                    } else if (hasRecentSummary) {
                        Log.d(TAG, "最近消息中已有总结，跳过总结生成")
                    } else if (!shouldSummarize) {
                        Log.d(
                                TAG,
                                "未达到总结条件，消息数量 $messageCount，新消息数量 ${messageCount - lastSummarizedIndex}"
                        )
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

            // 再次检查最近的消息中是否已经有总结消息
            val recentMessages = messages.takeLast(minOf(RECENT_MESSAGE_CHECK_COUNT, messages.size))
            val hasRecentSummary = recentMessages.any { it.sender == "summary" }

            if (hasRecentSummary) {
                Log.d(TAG, "总结过程中检测到最近消息已有总结，终止当前总结")
                return
            }

            // 只有用户和AI消息才会被总结，过滤其他类型的消息
            val messagesForSummary = messages.filter { it.sender == "user" || it.sender == "ai" }

            // 如果消息太少，不需要总结
            if (messagesForSummary.size < MEMORY_OPTIMIZATION_THRESHOLD) {
                Log.d(TAG, "消息数量不足，不生成总结")
                return
            }

            // 计算要总结的消息范围 - 从上次总结位置开始，最多SUMMARY_INTERVAL条
            val messagesToSummarize =
                    messagesForSummary.drop(lastSummarizedIndex).take(SUMMARY_INTERVAL)

            if (messagesToSummarize.isEmpty()) {
                Log.d(TAG, "没有新消息需要总结")
                return
            }

            Log.d(TAG, "将总结从索引 $lastSummarizedIndex 开始的 ${messagesToSummarize.size} 条消息")

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

            // 记录当前消息列表的快照
            val messagesSnapshot = ArrayList(messages)
            val originalMessageCount = messagesSnapshot.size

            try {
                // 将消息转换为AI可以处理的格式
                val conversationToSummarize =
                        messagesToSummarize.mapIndexed { index, message ->
                            val role = if (message.sender == "user") "user" else "assistant"
                            Pair(role, "#${index + 1}: ${message.content}")
                        }

                Log.d(TAG, "开始使用AI生成对话总结：总结 ${messagesToSummarize.size} 条消息")

                // 获取总结内容
                val summary = enhancedAiService.generateSummary(conversationToSummarize)
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

                // 简化后的插入位置查找逻辑 - 只使用明确的时间戳方法
                // 找到最后一条被总结的消息，并在其后插入总结
                val lastMessageTimestamp = messagesToSummarize.lastOrNull()?.timestamp
                val insertPosition =
                        if (lastMessageTimestamp != null) {
                            // 查找最后一条被总结消息的位置
                            val pos =
                                    newMessages.indexOfFirst {
                                        it.timestamp == lastMessageTimestamp
                                    }
                            // 在这条消息后插入总结
                            if (pos >= 0) pos + 1 else 0
                        } else {
                            // 如果找不到时间戳，则插入到最开始的位置
                            0
                        }

                // 在确定的位置插入总结消息
                newMessages.add(insertPosition, summaryMessage)
                Log.d(TAG, "在索引 $insertPosition 处添加总结消息")

                // 更新消息列表
                _chatHistory.value = newMessages

                // 通知ViewModel聊天历史已更新
                onChatHistoryLoaded(_chatHistory.value)

                // 关键修改: 只更新一次总结索引，而且只前进固定间隔
                // 使用当前用户和AI消息总数减去固定间隔作为新的总结索引
                // 这确保下一次总结会在积累了SUMMARY_INTERVAL条新消息后触发
                val totalMessages = newMessages.count { it.sender == "user" || it.sender == "ai" }
                lastSummarizedIndex = totalMessages - SUMMARY_INTERVAL / 2 // 减去一半间隔，提前触发下一次总结

                Log.d(TAG, "总结完成，更新lastSummarizedIndex为 $lastSummarizedIndex，总消息数 $totalMessages")
            } catch (e: Exception) {
                Log.e(TAG, "AI生成总结失败", e)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "记忆总结失败", e)
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

        // 如果找到总结消息
        if (lastSummaryIndex != -1) {
            // 添加总结消息作为用户消息，使用更清晰的前缀
            val summaryMessage = allMessages[lastSummaryIndex]
            // 去除可能存在的Markdown格式，确保AI能直接理解
            val summaryContent =
                    summaryMessage
                            .content
                            .replace(Regex("^#+\\s*对话摘要[：:]*\\s*"), "") // 移除可能的标题
                            .trim()

            result.add(Pair("user", "【历史对话摘要】\n$summaryContent"))
            Log.d(TAG, "添加最新总结消息到内存记录: ${summaryMessage.content.take(50)}...")

            // 只处理总结之后的消息（不包括总结消息本身）
            Log.d(TAG, "跳过总结之前的所有消息，只保留总结和总结之后的消息")
            for (i in (lastSummaryIndex + 1) until allMessages.size) {
                val message = allMessages[i]
                if (message.sender == "user" || message.sender == "ai") {
                    val role = if (message.sender == "ai") "assistant" else "user"
                    result.add(Pair(role, message.content))
                }
                // 忽略其他类型的消息（如系统消息、思考消息等）
            }
        } else {
            // 如果没有总结消息，则正常处理所有消息
            for (message in allMessages) {
                if (message.sender == "user" || message.sender == "ai") {
                    val role = if (message.sender == "ai") "assistant" else "user"
                    result.add(Pair(role, message.content))
                }
                // 忽略其他类型的消息
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
