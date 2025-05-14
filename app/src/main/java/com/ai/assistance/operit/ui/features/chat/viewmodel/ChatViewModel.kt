package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.EnhancedAIService
import com.ai.assistance.operit.data.model.AiReference
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.ToolExecutionProgress
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.ui.features.chat.attachments.AttachmentManager
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    // API服务
    private var enhancedAiService: EnhancedAIService? = null

    // 工具处理器
    private val toolHandler = AIToolHandler.getInstance(context)

    // 工具权限系统
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)

    // 附件管理器
    private val attachmentManager = AttachmentManager(context, toolHandler)

    // 委托类
    private val uiStateDelegate = UiStateDelegate()
    private val tokenStatsDelegate =
            TokenStatisticsDelegate(
                    getEnhancedAiService = { enhancedAiService },
                    updateUiStatistics = { contextSize, inputTokens, outputTokens ->
                        uiStateDelegate.updateChatStatistics(contextSize, inputTokens, outputTokens)
                    }
            )
    private val apiConfigDelegate =
            ApiConfigDelegate(
                    context = context,
                    viewModelScope = viewModelScope,
                    onConfigChanged = { service ->
                        enhancedAiService = service
                        // API配置变更后，异步设置服务收集器
                        viewModelScope.launch { setupServiceCollectors() }
                    }
            )
    private val planItemsDelegate =
            PlanItemsDelegate(
                    viewModelScope = viewModelScope,
                    getEnhancedAiService = { enhancedAiService }
            )

    // Break circular dependency with lateinit
    private lateinit var chatHistoryDelegate: ChatHistoryDelegate
    private lateinit var messageProcessingDelegate: MessageProcessingDelegate
    private lateinit var floatingWindowDelegate: FloatingWindowDelegate

    // Use lazy initialization for exposed properties to avoid circular reference issues
    // API配置相关
    val apiKey: StateFlow<String> by lazy { apiConfigDelegate.apiKey }
    val apiEndpoint: StateFlow<String> by lazy { apiConfigDelegate.apiEndpoint }
    val modelName: StateFlow<String> by lazy { apiConfigDelegate.modelName }
    val isConfigured: StateFlow<Boolean> by lazy { apiConfigDelegate.isConfigured }
    val showThinking: StateFlow<Boolean> by lazy { apiConfigDelegate.showThinking }
    val enableAiPlanning: StateFlow<Boolean> by lazy { apiConfigDelegate.enableAiPlanning }
    val memoryOptimization: StateFlow<Boolean> by lazy { apiConfigDelegate.memoryOptimization }
    val collapseExecution: StateFlow<Boolean> by lazy { apiConfigDelegate.collapseExecution }

    // 聊天历史相关
    val chatHistory: StateFlow<List<ChatMessage>> by lazy { chatHistoryDelegate.chatHistory }
    val showChatHistorySelector: StateFlow<Boolean> by lazy {
        chatHistoryDelegate.showChatHistorySelector
    }
    val chatHistories: StateFlow<List<ChatHistory>> by lazy { chatHistoryDelegate.chatHistories }
    val currentChatId: StateFlow<String?> by lazy { chatHistoryDelegate.currentChatId }

    // 消息处理相关
    val userMessage: StateFlow<String> by lazy { messageProcessingDelegate.userMessage }
    val isLoading: StateFlow<Boolean> by lazy { messageProcessingDelegate.isLoading }
    val isProcessingInput: StateFlow<Boolean> by lazy {
        messageProcessingDelegate.isProcessingInput
    }
    val inputProcessingMessage: StateFlow<String> by lazy {
        messageProcessingDelegate.inputProcessingMessage
    }

    // UI状态相关
    val errorMessage: StateFlow<String?> by lazy { uiStateDelegate.errorMessage }
    val popupMessage: StateFlow<String?> by lazy { uiStateDelegate.popupMessage }
    val toastEvent: StateFlow<String?> by lazy { uiStateDelegate.toastEvent }
    val toolProgress: StateFlow<ToolExecutionProgress> by lazy { uiStateDelegate.toolProgress }
    val aiReferences: StateFlow<List<AiReference>> by lazy { uiStateDelegate.aiReferences }
    val masterPermissionLevel: StateFlow<PermissionLevel> by lazy {
        uiStateDelegate.masterPermissionLevel
    }

    // 聊天统计相关
    val contextWindowSize: StateFlow<Int> by lazy { uiStateDelegate.contextWindowSize }
    val inputTokenCount: StateFlow<Int> by lazy { uiStateDelegate.inputTokenCount }
    val outputTokenCount: StateFlow<Int> by lazy { uiStateDelegate.outputTokenCount }

    // 计划项相关
    val planItems: StateFlow<List<PlanItem>> by lazy { planItemsDelegate.planItems }

    // 悬浮窗相关
    val isFloatingMode: StateFlow<Boolean> by lazy { floatingWindowDelegate.isFloatingMode }

    // 附件相关
    val attachments: StateFlow<List<AttachmentInfo>> by lazy { attachmentManager.attachments }

    init {
        // Initialize delegates in correct order to avoid circular references
        initializeDelegates()

        // Setup additional components
        setupPermissionSystemCollection()
        setupAttachmentManagerToastCollection()
        checkIfShouldCreateNewChat()
    }

    private fun initializeDelegates() {
        // First initialize chat history delegate
        chatHistoryDelegate =
                ChatHistoryDelegate(
                        context = context,
                        viewModelScope = viewModelScope,
                        onChatHistoryLoaded = { messages: List<ChatMessage> ->
                            // We'll update floating window messages after it's initialized
                            if (::floatingWindowDelegate.isInitialized) {
                                floatingWindowDelegate.updateFloatingWindowMessages(messages)
                            }
                        },
                        onTokenStatisticsLoaded = { inputTokens: Int, outputTokens: Int ->
                            tokenStatsDelegate.setTokenCounts(inputTokens, outputTokens)
                        },
                        resetPlanItems = { planItemsDelegate.clearPlanItems() },
                        getEnhancedAiService = { enhancedAiService },
                        ensureAiServiceAvailable = { ensureAiServiceAvailable() }
                )

        // Then initialize message processing delegate
        messageProcessingDelegate =
                MessageProcessingDelegate(
                        context = context,
                        viewModelScope = viewModelScope,
                        getEnhancedAiService = { enhancedAiService },
                        getShowThinking = { apiConfigDelegate.showThinking.value },
                        getChatHistory = { chatHistoryDelegate.chatHistory.value },
                        getMemory = { includePlanInfo ->
                            chatHistoryDelegate.getMemory(includePlanInfo)
                        },
                        addMessageToChat = { message ->
                            chatHistoryDelegate.addMessageToChat(message)
                        },
                        updateChatStatistics = {
                            val (inputTokens, outputTokens) =
                                    tokenStatsDelegate.updateChatStatistics()
                            chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens)
                        },
                        saveCurrentChat = {
                            val (inputTokens, outputTokens) =
                                    tokenStatsDelegate.getCurrentTokenCounts()
                            chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens)
                        },
                        showErrorMessage = { message -> uiStateDelegate.showErrorMessage(message) }
                )

        // Finally initialize floating window delegate
        floatingWindowDelegate =
                FloatingWindowDelegate(
                        context = context,
                        viewModelScope = viewModelScope,
                        onMessageReceived = { message ->
                            // 更新用户消息
                            messageProcessingDelegate.updateUserMessage(message)
                            // 发送消息时也要传递附件
                            sendUserMessage()
                        },
                        onAttachmentRequested = { request -> processAttachmentRequest(request) },
                        onAttachmentRemoveRequested = { filePath -> removeAttachment(filePath) }
                )

        // 确保所有委托都初始化好后，尝试重新设置服务收集器
        if (enhancedAiService != null) {
            viewModelScope.launch {
                Log.d(TAG, "委托初始化完成，重新设置服务收集器")
                setupServiceCollectors()
            }
        }
    }

    private fun setupPermissionSystemCollection() {
        viewModelScope.launch {
            toolPermissionSystem.masterSwitchFlow.collect { level ->
                uiStateDelegate.updateMasterPermissionLevel(level)
            }
        }
    }

    private fun setupAttachmentManagerToastCollection() {
        viewModelScope.launch {
            attachmentManager.toastEvent.collect { message -> uiStateDelegate.showToast(message) }
        }
    }

    private fun checkIfShouldCreateNewChat() {
        viewModelScope.launch {
            // 检查历史记录加载后是否需要创建新聊天
            if (chatHistoryDelegate.checkIfShouldCreateNewChat() && isConfigured.value) {
                chatHistoryDelegate.createNewChat()
            }
        }
    }

    /** 设置服务相关的流收集逻辑 */
    private fun setupServiceCollectors() {
        // 设置工具进度收集
        viewModelScope.launch {
            enhancedAiService?.getToolProgressFlow()?.collect { progress ->
                uiStateDelegate.updateToolProgress(progress)
            }
        }

        // 设置引用收集
        viewModelScope.launch {
            enhancedAiService?.references?.collect { refs ->
                uiStateDelegate.updateAiReferences(refs)
            }
        }

        // 设置输入处理状态收集和计划项收集
        // 添加确保设置完成的机制
        viewModelScope.launch {
            var inputProcessingSetupComplete = false
            var planItemsSetupComplete = false
            var retryCount = 0
            val maxRetries = 3

            while ((!inputProcessingSetupComplete || !planItemsSetupComplete) &&
                    retryCount < maxRetries) {
                // 如果委托类已初始化且输入处理尚未设置完成，则尝试设置
                if (::messageProcessingDelegate.isInitialized && !inputProcessingSetupComplete) {
                    try {
                        Log.d(TAG, "设置输入处理状态收集，尝试 ${retryCount + 1}/${maxRetries}")
                        messageProcessingDelegate.setupInputProcessingStateCollection()
                        inputProcessingSetupComplete = true
                        Log.d(TAG, "输入处理状态收集设置成功")
                    } catch (e: Exception) {
                        Log.e(TAG, "设置输入处理状态收集时出错: ${e.message}", e)
                    }
                }

                // planItemsDelegate 不是 lateinit 变量，所以直接尝试设置
                if (!planItemsSetupComplete) {
                    try {
                        Log.d(TAG, "设置计划项收集，尝试 ${retryCount + 1}/${maxRetries}")
                        planItemsDelegate.setupPlanItemsCollection()
                        planItemsSetupComplete = true
                        Log.d(TAG, "计划项收集设置成功")
                    } catch (e: Exception) {
                        Log.e(TAG, "设置计划项收集时出错")
                    }
                }

                // 如果还未完成设置，则等待一段时间后重试
                if (!inputProcessingSetupComplete || !planItemsSetupComplete) {
                    retryCount++
                    kotlinx.coroutines.delay(500) // 延迟300毫秒后重试
                }
            }

            // 记录最终设置状态
            if (!inputProcessingSetupComplete) {
                Log.e(TAG, "无法设置输入处理状态收集，已达到最大重试次数")
            }
            if (!planItemsSetupComplete) {
                Log.e(TAG, "无法设置计划项收集，已达到最大重试次数")
            }
        }
    }

    // API配置相关方法
    fun updateApiKey(key: String) = apiConfigDelegate.updateApiKey(key)
    fun updateApiEndpoint(endpoint: String) = apiConfigDelegate.updateApiEndpoint(endpoint)
    fun updateModelName(model: String) = apiConfigDelegate.updateModelName(model)
    fun saveApiSettings() = apiConfigDelegate.saveApiSettings()
    fun useDefaultConfig() {
        if (apiConfigDelegate.useDefaultConfig()) {
            uiStateDelegate.showToast("使用默认配置继续")
        } else {
            uiStateDelegate.showErrorMessage("默认配置不完整，请填写必要信息")
        }
    }
    fun toggleAiPlanning() {
        apiConfigDelegate.toggleAiPlanning()
        uiStateDelegate.showToast(if (enableAiPlanning.value) "AI计划模式已开启" else "AI计划模式已关闭")
    }
    fun toggleShowThinking() {
        apiConfigDelegate.toggleShowThinking()
        uiStateDelegate.showToast(if (showThinking.value) "思考过程显示已开启" else "思考过程显示已关闭")
    }
    fun toggleMemoryOptimization() {
        apiConfigDelegate.toggleMemoryOptimization()
        uiStateDelegate.showToast(if (memoryOptimization.value) "记忆优化已开启" else "记忆优化已关闭")
    }

    // 聊天历史相关方法
    fun createNewChat() {
        chatHistoryDelegate.createNewChat()
    }
    fun switchChat(chatId: String) = chatHistoryDelegate.switchChat(chatId)
    fun deleteChatHistory(chatId: String) = chatHistoryDelegate.deleteChatHistory(chatId)
    fun clearCurrentChat() {
        chatHistoryDelegate.clearCurrentChat()
        uiStateDelegate.showToast("聊天记录已清空")
    }
    fun toggleChatHistorySelector() = chatHistoryDelegate.toggleChatHistorySelector()
    fun showChatHistorySelector(show: Boolean) = chatHistoryDelegate.showChatHistorySelector(show)
    fun saveCurrentChat() {
        val (inputTokens, outputTokens) = tokenStatsDelegate.getCurrentTokenCounts()
        chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens)
    }

    // 消息处理相关方法
    fun updateUserMessage(message: String) = messageProcessingDelegate.updateUserMessage(message)

    fun sendUserMessage() {
        // 获取当前附件列表
        val currentAttachments = attachmentManager.attachments.value

        // 调用messageProcessingDelegate发送消息，并传递附件信息
        messageProcessingDelegate.sendUserMessage(currentAttachments)

        // 发送后清空附件列表
        if (currentAttachments.isNotEmpty()) {
            attachmentManager.clearAttachments()
            // 更新悬浮窗附件列表
            updateFloatingWindowAttachments()
        }
    }

    fun cancelCurrentMessage() {
        messageProcessingDelegate.cancelCurrentMessage()
        uiStateDelegate.showToast("已取消当前对话")
    }

    // UI状态相关方法
    fun showErrorMessage(message: String) = uiStateDelegate.showErrorMessage(message)
    fun clearError() = uiStateDelegate.clearError()
    fun popupMessage(message: String) = uiStateDelegate.showPopupMessage(message)
    fun clearPopupMessage() = uiStateDelegate.clearPopupMessage()
    fun showToast(message: String) = uiStateDelegate.showToast(message)
    fun clearToastEvent() = uiStateDelegate.clearToastEvent()

    // 悬浮窗相关方法
    fun toggleFloatingMode() {
        floatingWindowDelegate.toggleFloatingMode()
    }
    fun updateFloatingWindowMessages(messages: List<ChatMessage>) {
        floatingWindowDelegate.updateFloatingWindowMessages(messages)
    }
    fun updateFloatingWindowAttachments() {
        floatingWindowDelegate.updateFloatingWindowAttachments(attachments.value)
    }

    // 权限相关方法
    fun toggleMasterPermission() {
        viewModelScope.launch {
            val newLevel =
                    if (masterPermissionLevel.value == PermissionLevel.ASK) {
                        PermissionLevel.ALLOW
                    } else {
                        PermissionLevel.ASK
                    }
            toolPermissionSystem.saveMasterSwitch(newLevel)

            uiStateDelegate.showToast(
                    if (newLevel == PermissionLevel.ALLOW) {
                        "已开启自动批准，工具执行将不再询问"
                    } else {
                        "已恢复询问模式，工具执行将询问批准"
                    }
            )
        }
    }

    // 附件相关方法
    /** 处理从悬浮窗接收的附件请求 */
    private fun processAttachmentRequest(request: String) {
        viewModelScope.launch {
            try {
                // 显示附件请求处理进度
                messageProcessingDelegate.setInputProcessingState(true, "正在处理附件请求...")

                when {
                    request == "screen_capture" -> {
                        // 捕获屏幕内容
                        captureScreenContent()
                    }
                    request == "notifications_capture" -> {
                        // 捕获通知
                        captureNotifications()
                    }
                    request == "location_capture" -> {
                        // 捕获位置
                        captureLocation()
                    }
                    request == "problem_memory" -> {
                        // 查询问题记忆 - 使用当前消息作为查询
                        val userQuery = userMessage.value
                        if (userQuery.isNotBlank()) {
                            messageProcessingDelegate.setInputProcessingState(true, "正在搜索问题记忆...")
                            val result = attachmentManager.queryProblemMemory(userQuery)
                            attachProblemMemory(result.first, result.second)
                        } else {
                            uiStateDelegate.showToast("请先输入搜索问题的内容")
                            messageProcessingDelegate.setInputProcessingState(false, "")
                        }
                    }
                    else -> {
                        // 处理普通文件附件
                        handleAttachment(request)
                    }
                }

                // 在各子方法中都已经有设置进度条状态的代码，不需要在这里重复清除
            } catch (e: Exception) {
                Log.e(TAG, "Error processing attachment request", e)
                uiStateDelegate.showToast("处理附件失败: ${e.message}")
                // 确保出错时清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** Handles a file or image attachment selected by the user */
    fun handleAttachment(filePath: String) {
        viewModelScope.launch {
            try {
                // 显示附件处理进度
                messageProcessingDelegate.setInputProcessingState(true, "正在处理附件...")

                attachmentManager.handleAttachment(filePath)

                // 处理完附件后立即更新悬浮窗中的附件列表
                updateFloatingWindowAttachments()

                // 清除附件处理进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            } catch (e: Exception) {
                Log.e(TAG, "处理附件失败", e)
                uiStateDelegate.showToast("处理附件失败: ${e.message}")
                // 发生错误时也需要清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** Removes an attachment by its file path */
    fun removeAttachment(filePath: String) {
        attachmentManager.removeAttachment(filePath)
        // 移除附件后立即更新悬浮窗中的附件列表
        updateFloatingWindowAttachments()
    }

    /** Inserts a reference to an attachment at the current cursor position in the user's message */
    fun insertAttachmentReference(attachment: AttachmentInfo) {
        val currentMessage = userMessage.value
        val attachmentRef = attachmentManager.createAttachmentReference(attachment)

        // Insert at the end of the current message
        updateUserMessage("$currentMessage $attachmentRef ")

        // Show a toast to confirm insertion
        uiStateDelegate.showToast("已插入附件引用: ${attachment.fileName}")
    }

    /** Captures the current screen content and attaches it to the message */
    fun captureScreenContent() {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage("")
                // 显示屏幕内容获取进度
                messageProcessingDelegate.setInputProcessingState(true, "正在获取屏幕内容...")
                uiStateDelegate.showToast("正在获取屏幕内容...")

                attachmentManager.captureScreenContent()

                // 完成后立即更新悬浮窗中的附件列表
                updateFloatingWindowAttachments()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            } catch (e: Exception) {
                Log.e(TAG, "截取屏幕内容失败", e)
                uiStateDelegate.showToast("截取屏幕内容失败: ${e.message}")
                // 发生错误时也需要清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** 获取设备当前通知数据并添加为附件 */
    fun captureNotifications() {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage("")
                // 显示通知获取进度
                messageProcessingDelegate.setInputProcessingState(true, "正在获取当前通知...")
                uiStateDelegate.showToast("正在获取当前通知...")

                attachmentManager.captureNotifications()

                // 完成后立即更新悬浮窗中的附件列表
                updateFloatingWindowAttachments()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            } catch (e: Exception) {
                Log.e(TAG, "获取通知数据失败", e)
                uiStateDelegate.showToast("获取通知数据失败: ${e.message}")
                // 发生错误时也需要清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** 获取设备当前位置数据并添加为附件 */
    fun captureLocation() {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage("")
                // 显示位置获取进度
                messageProcessingDelegate.setInputProcessingState(true, "正在获取位置信息...")
                uiStateDelegate.showToast("正在获取位置信息...")

                attachmentManager.captureLocation()

                // 完成后立即更新悬浮窗中的附件列表
                updateFloatingWindowAttachments()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            } catch (e: Exception) {
                Log.e(TAG, "获取位置数据失败", e)
                uiStateDelegate.showToast("获取位置数据失败: ${e.message}")
                // 发生错误时也需要清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** 添加问题记忆附件 */
    fun attachProblemMemory(content: String, filename: String) {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage("")
                // 显示问题记忆添加进度
                messageProcessingDelegate.setInputProcessingState(true, "正在添加问题记忆...")
                uiStateDelegate.showToast("正在添加问题记忆...")

                // 将实际处理委托给AttachmentManager
                attachmentManager.attachProblemMemory(content, filename)

                // 完成后立即更新悬浮窗中的附件列表
                updateFloatingWindowAttachments()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            } catch (e: Exception) {
                Log.e(TAG, "添加问题记忆失败", e)
                uiStateDelegate.showToast("添加问题记忆失败: ${e.message}")
                // 发生错误时也需要清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** 搜索问题记忆 */
    fun searchProblemMemory() {
        // 此方法已被 attachProblemMemory 替代
        // 保留此方法以确保向后兼容性
        uiStateDelegate.showToast("请使用新的问题记忆功能")
    }

    /** 确保AI服务可用，如果当前实例为空则创建一个默认实例 */
    fun ensureAiServiceAvailable() {
        if (enhancedAiService == null) {
            viewModelScope.launch {
                try {
                    // 使用默认配置或保存的配置创建一个新实例
                    Log.d(TAG, "创建默认EnhancedAIService实例")
                    apiConfigDelegate.useDefaultConfig()

                    // 等待服务实例创建完成
                    var retryCount = 0
                    while (enhancedAiService == null && retryCount < 3) {
                        kotlinx.coroutines.delay(500)
                        retryCount++
                    }

                    if (enhancedAiService == null) {
                        Log.e(TAG, "无法创建EnhancedAIService实例")
                        uiStateDelegate.showToast("无法初始化AI服务，请检查网络和API设置")
                    } else {
                        Log.d(TAG, "成功创建EnhancedAIService实例")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "创建EnhancedAIService实例时出错", e)
                    uiStateDelegate.showToast("初始化AI服务失败: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 清理悬浮窗资源
        floatingWindowDelegate.cleanup()
    }
}
