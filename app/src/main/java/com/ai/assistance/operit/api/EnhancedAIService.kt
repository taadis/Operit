package com.ai.assistance.operit.api

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.enhance.ConversationMarkupManager
import com.ai.assistance.operit.api.enhance.ConversationRoundManager
import com.ai.assistance.operit.api.enhance.InputProcessor
import com.ai.assistance.operit.api.enhance.MultiServiceManager
import com.ai.assistance.operit.api.enhance.ToolExecutionManager
import com.ai.assistance.operit.api.library.ProblemLibrary
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.ToolExecutionProgress
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Enhanced AI service that provides advanced conversational capabilities by integrating various
 * components like tool execution, conversation management, user preferences, and problem library.
 */
class EnhancedAIService(private val context: Context) {
    companion object {
        private const val TAG = "EnhancedAIService"
    }

    // MultiServiceManager 管理不同功能的 AIService 实例
    private val multiServiceManager = MultiServiceManager(context)

    // AIService 实例 - 保留为兼容现有代码，但实际使用 MultiServiceManager
    private val aiService: AIService by lazy {
        runBlocking { multiServiceManager.getDefaultService() }
    }

    // Tool handler for executing tools
    private val toolHandler = AIToolHandler.getInstance(context)

    // 初始化问题库
    init {
        ProblemLibrary.initialize(context)
        // 初始化 MultiServiceManager
        runBlocking { multiServiceManager.initialize() }
    }

    // State flows for UI updates
    private val _inputProcessingState =
            MutableStateFlow<InputProcessingState>(InputProcessingState.Idle)
    val inputProcessingState = _inputProcessingState.asStateFlow()

    // Plan items tracking
    private val _planItems = MutableStateFlow<List<PlanItem>>(emptyList())
    val planItems = _planItems.asStateFlow()

    // Conversation management
    private val streamBuffer = StringBuilder()
    private val roundManager = ConversationRoundManager()
    private val isConversationActive = AtomicBoolean(false)

    // Api Preferences for settings
    private val apiPreferences = ApiPreferences(context)

    // Coroutine management
    private val toolProcessingScope = CoroutineScope(Dispatchers.IO)
    private val toolExecutionJobs = ConcurrentHashMap<String, Job>()
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val conversationMutex = Mutex()

    // Callbacks
    private var currentResponseCallback: ((content: String, thinking: String?) -> Unit)? = null
    private var currentCompleteCallback: (() -> Unit)? = null

    // Package manager for handling tool packages
    private val packageManager = PackageManager.getInstance(context, toolHandler)

    // Current chat ID for web workspace
    private var currentChatId: String? = null

    init {
        toolHandler.registerDefaultTools()
    }

    /**
     * 获取指定功能类型的 AIService 实例
     * @param functionType 功能类型
     * @return AIService 实例
     */
    suspend fun getAIServiceForFunction(functionType: FunctionType): AIService {
        return multiServiceManager.getServiceForFunction(functionType)
    }

    /**
     * 刷新指定功能类型的 AIService 实例 当配置发生更改时调用
     * @param functionType 功能类型
     */
    suspend fun refreshServiceForFunction(functionType: FunctionType) {
        multiServiceManager.refreshServiceForFunction(functionType)
    }

    /** 刷新所有 AIService 实例 当全局配置发生更改时调用 */
    suspend fun refreshAllServices() {
        multiServiceManager.refreshAllServices()
    }

    /** Get the tool progress flow for UI updates */
    fun getToolProgressFlow(): StateFlow<ToolExecutionProgress> {
        return toolHandler.toolProgress
    }

    /** Process user input with a delay for UI feedback */
    suspend fun processUserInput(input: String): String {
        _inputProcessingState.value = InputProcessingState.Processing("Processing input...")
        return InputProcessor.processUserInput(input)
    }

    /** Extract and update plan items from content */
    private fun extractPlanItems(content: String) {
        // Only extract plan items if planning is enabled
        toolProcessingScope.launch {
            try {
                val planningEnabled = apiPreferences.enableAiPlanningFlow.first()
                if (planningEnabled) {
                    Log.d(TAG, "计划模式已启用，开始提取计划项")

                    // 获取当前的计划项
                    val existingItems = _planItems.value

                    // 始终传入现有的计划项，让PlanItemParser处理合并和更新
                    val updatedPlanItems =
                            ConversationMarkupManager.extractPlanItems(content, existingItems)

                    if (updatedPlanItems.isNotEmpty()) {
                        // 检查计划项是否有变化
                        if (updatedPlanItems != existingItems) {
                            Log.d(TAG, "计划项有变化，更新状态流")
                            _planItems.value = updatedPlanItems
                        }
                    } else if (_planItems.value.isNotEmpty()) {
                        // 即使没有提取到新的计划项，也保留现有的计划项
                        Log.d(TAG, "保留现有计划项")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "提取计划项时发生错误: ${e.message}")
                // 错误时也保留现有计划项，避免因为异常导致UI丢失显示
            }
        }
    }

    /** Clear all plan items */
    fun clearPlanItems() {
        toolProcessingScope.launch {
            try {
                Log.d(TAG, "清除所有计划项")
                _planItems.value = emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "清除计划项时发生错误", e)
            }
        }
    }

    /**
     * Re-extract plan items from the entire chat history This helps recover plan items in case they
     * were lost due to UI state issues
     */
    private fun reExtractPlanItems(chatHistory: List<Pair<String, String>>) {
        if (chatHistory.isEmpty()) {
            Log.d(TAG, "空聊天历史，跳过计划项重新提取")
            return
        }

        toolProcessingScope.launch {
            try {
                val planningEnabled = apiPreferences.enableAiPlanningFlow.first()
                if (!planningEnabled) {
                    Log.d(TAG, "计划模式未启用，跳过计划项重新提取")
                    return@launch
                }

                // 如果当前已有计划项，不进行重新提取
                if (_planItems.value.isNotEmpty()) {
                    Log.d(
                            TAG,
                            "当前已有计划项，无需重新提取: ${_planItems.value.map { "${it.id}: ${it.status}" }}"
                    )
                    return@launch
                }

                Log.d(TAG, "开始从历史聊天中重新提取计划项")
                // 获取所有助手消息内容
                val assistantMessages =
                        chatHistory.filter { it.first == "assistant" }.map { it.second }

                // 合并提取所有计划项的逻辑
                var accumulatedItems = _planItems.value

                for (content in assistantMessages) {
                    // 使用新的带有现有计划项参数的方法
                    accumulatedItems =
                            ConversationMarkupManager.extractPlanItems(content, accumulatedItems)
                }

                // 只有在找到计划项时才更新状态流
                if (accumulatedItems.isNotEmpty() && accumulatedItems != _planItems.value) {
                    Log.d(
                            TAG,
                            "从历史聊天中重新提取到计划项: ${accumulatedItems.map { "${it.id}: ${it.status}" }}"
                    )
                    _planItems.value = accumulatedItems
                    Log.d(TAG, "已更新计划项状态流")
                } else if (accumulatedItems.isEmpty()) {
                    Log.d(TAG, "未从历史聊天中提取到任何计划项")
                }
            } catch (e: Exception) {
                Log.e(TAG, "重新提取计划项时发生错误: ${e.message}", e)
            }
        }
    }

    /** Send a message to the AI service */
    suspend fun sendMessage(
            message: String,
            chatHistory: List<Pair<String, String>> = emptyList(),
            chatId: String? = null,
            functionType: FunctionType = FunctionType.CHAT
    ): Stream<String> {
        Log.d(TAG, "sendMessage调用开始: 功能类型=$functionType")
        return stream {
            try {
                // 确保所有操作都在IO线程上执行
                withContext(Dispatchers.IO) {
                    // Store the chat ID for web workspace
                    currentChatId = chatId
                    
                    // Mark conversation as active
                    isConversationActive.set(true)
                    
                    // Process the input message for any conversation markup (e.g., for AI planning)
                    val startTime = System.currentTimeMillis()
                    val processedInput = InputProcessor.processUserInput(message)
                    
                    // Update state to show we're processing
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value =
                                InputProcessingState.Processing("Processing message...")
                    }

                    // Prepare conversation history with system prompt
                    val preparedHistory = prepareConversationHistory(chatHistory, processedInput)
                    
                    // Update UI state to connecting
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value =
                                InputProcessingState.Connecting("Connecting to AI service...")
                    }

                    // Get all model parameters from preferences (with enabled state)
                    val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }
                    
                    // 获取对应功能类型的AIService实例
                    val serviceForFunction = multiServiceManager.getServiceForFunction(functionType)
                    
                    // 使用新的Stream API
                    Log.d(TAG, "调用AI服务，处理时间: ${System.currentTimeMillis() - startTime}ms")
                    val stream =
                            serviceForFunction.sendMessage(
                                    message = processedInput,
                                    chatHistory = preparedHistory,
                                    modelParameters = modelParameters
                            )

                    // 收到第一个响应，更新状态
                    var isFirstChunk = true

                    // 创建一个新的轮次来管理内容
                    roundManager.startNewRound()
                    streamBuffer.clear()

                    // 从原始stream收集内容并处理
                    var chunkCount = 0
                    var totalChars = 0
                    var lastLogTime = System.currentTimeMillis()
                    val streamStartTime = System.currentTimeMillis()

                    stream.collect { content ->
                        // 第一次收到响应，更新状态
                        if (isFirstChunk) {
                            withContext(Dispatchers.Main) {
                                _inputProcessingState.value =
                                        InputProcessingState.Receiving("Receiving AI response...")
                            }
                            isFirstChunk = false
                            Log.d(TAG, "首次响应耗时: ${System.currentTimeMillis() - streamStartTime}ms")
                        }

                        // 累计统计
                        chunkCount++
                        totalChars += content.length

                        // 周期性日志
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastLogTime > 5000) { // 每5秒记录一次
                            Log.d(TAG, "已接收 $chunkCount 个内容块，总计 $totalChars 个字符")
                            lastLogTime = currentTime
                        }

                        // 更新streamBuffer，保持与原有逻辑一致
                        streamBuffer.append(content)

                        // 更新内容到轮次管理器
                        roundManager.updateContent(streamBuffer.toString())

                        // 发射当前内容片段
                        emit(content)
                    }
                    Log.d(TAG, "流收集完成，总计 $totalChars 字符，耗时: ${System.currentTimeMillis() - streamStartTime}ms")
                }
            } catch (e: Exception) {
                // 对于协程取消异常，这是正常流程，应当向上抛出以停止流
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "sendMessage流被取消")
                    throw e
                }
                // Handle any exceptions
                Log.e(TAG, "发送消息时发生错误: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _inputProcessingState.value =
                            InputProcessingState.Error(message = "Error: ${e.message}")
                }
            } finally {
                // 确保流处理完成后调用
                val collector = this
                withContext(Dispatchers.IO) {
                    processStreamCompletion(functionType, collector)
                }
            }
        }
    }

    /**
     * 使用流处理技术增强工具调用检测能力 这个方法通过流式XML解析来辅助识别工具调用，比单纯依赖正则表达式更可靠
     * @param content 需要检测工具调用的内容
     * @return 经过增强检测的内容，可能会修复格式问题
     */
    private suspend fun enhanceToolDetection(content: String): String {
        try {
            // 检查内容是否包含可能的工具调用标记
            if (!content.contains("<tool") && !content.contains("</tool>")) {
                return content
            }

            // 创建字符流以应用流处理，使用 stream() 替代 asCharStream()
            val charStream = content.stream()

            // 使用XML插件来拆分流
            val plugins = listOf(StreamXmlPlugin())

            // 保存增强后的内容
            val enhancedContent = StringBuilder()

            // 追踪是否发现了工具标签
            var foundToolTag = false

            // 处理拆分的结果
            charStream.splitBy(plugins).collect { group ->
                when (val tag = group.tag) {
                    // 匹配到XML标签
                    is StreamXmlPlugin -> {
                        val xmlContent = StringBuilder()
                        group.stream.collect { char -> xmlContent.append(char) }

                        val xml = xmlContent.toString()
                        // 检查是否是工具标签
                        if (xml.contains("<tool") && xml.contains("</tool>")) {
                            foundToolTag = true
                            // 格式标准化，使其符合工具调用的正则表达式预期格式
                            val normalizedXml = normalizeToolXml(xml)
                            enhancedContent.append(normalizedXml)
                            Log.d(TAG, "工具调用XML被增强流处理检测到并标准化")
                        } else {
                            // 保留其他XML标签
                            enhancedContent.append(xml)
                        }
                    }
                    // 纯文本内容
                    null -> {
                        val textContent = StringBuilder()
                        group.stream.collect { char -> textContent.append(char) }
                        enhancedContent.append(textContent.toString())
                    }
                    // 添加必要的else分支
                    else -> {
                        val textContent = StringBuilder()
                        group.stream.collect { char -> textContent.append(char) }
                        enhancedContent.append(textContent.toString())
                        Log.w(TAG, "未知标签类型: ${tag::class.java.simpleName}")
                    }
                }
            }

            // 如果找到了工具标签，返回增强的内容；否则返回原始内容
            return if (foundToolTag) {
                Log.d(TAG, "增强的XML工具检测完成")
                enhancedContent.toString()
            } else {
                content
            }
        } catch (e: Exception) {
            // 如果流处理失败，返回原始内容并记录错误
            Log.e(TAG, "增强工具检测失败: ${e.message}", e)
            return content
        }
    }

    /**
     * 规范化工具XML以符合正则表达式预期
     * @param xml 原始XML文本
     * @return 标准化后的XML
     */
    private fun normalizeToolXml(xml: String): String {
        var result = xml.trim()

        // 确保工具名称格式正确
        result = result.replace(Regex("<tool\\s+name\\s*="), "<tool name=")

        // 确保参数格式正确
        result = result.replace(Regex("<param\\s+name\\s*="), "<param name=")

        return result
    }

    /** 在处理完流后调用，使用增强的工具检测功能 */
    private suspend fun processStreamCompletion(
            functionType: FunctionType = FunctionType.CHAT,
            collector: StreamCollector<String>
    ) {
        try {
            val startTime = System.currentTimeMillis()
            // If conversation is no longer active, return immediately
            if (!isConversationActive.get()) {
                return
            }

            // Get response content
            val content = streamBuffer.toString().trim()

            // If content is empty, finish immediately
            if (content.isEmpty()) {
                return
            }

            // 使用增强的工具检测功能处理内容
            val enhancedContent = enhanceToolDetection(content)
            // 如果内容被增强修改了，更新到streamBuffer
            if (enhancedContent != content) {
                streamBuffer.setLength(0)
                streamBuffer.append(enhancedContent)
                // 更新轮次管理器显示内容
                roundManager.updateContent(enhancedContent)
            }

            // 提取计划项
            extractPlanItems(enhancedContent)

            // Handle task completion marker
            if (ConversationMarkupManager.containsTaskCompletion(enhancedContent)) {
                handleTaskCompletion(enhancedContent)
                return
            }

            // Handle wait for user need marker
            if (ConversationMarkupManager.containsWaitForUserNeed(enhancedContent)) {
                handleWaitForUserNeed(enhancedContent)
                return
            }

            // Check again if conversation is active
            if (!isConversationActive.get()) {
                return
            }

            // Add current assistant message to conversation history
            try {
                conversationMutex.withLock {
                    conversationHistory.add(
                            Pair("assistant", roundManager.getCurrentRoundContent())
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "添加助手消息到历史记录失败", e)
                return
            }

            // Check again if conversation is active
            if (!isConversationActive.get()) {
                return
            }

            // Main flow: Detect and process tool invocations
            // Try to detect tool invocations
            val toolInvocations = toolHandler.extractToolInvocations(enhancedContent)

            if (toolInvocations.isNotEmpty()) {
                Log.d(TAG, "检测到 ${toolInvocations.size} 个工具调用，处理时间: ${System.currentTimeMillis() - startTime}ms")
                handleToolInvocation(
                        toolInvocations,
                        roundManager.getDisplayContent(),
                        functionType,
                        collector
                )
                return
            }

            // 修改默认行为：如果没有特殊标记或工具调用，默认等待用户输入
            // 而不是直接标记为完成

            // 创建等待用户输入的内容
            val userNeedContent =
                    ConversationMarkupManager.createWaitForUserNeedContent(
                            roundManager.getDisplayContent()
                    )

            // 处理为等待用户输入模式
            handleWaitForUserNeed(userNeedContent)
            Log.d(TAG, "流完成处理耗时: ${System.currentTimeMillis() - startTime}ms")
        } catch (e: Exception) {
            // Catch any exceptions in the processing flow
            Log.e(TAG, "处理流完成时发生错误", e)
            withContext(Dispatchers.Main) {
                _inputProcessingState.value = InputProcessingState.Idle
            }
        }
    }

    /** Handle task completion logic - simplified version without callbacks */
    private suspend fun handleTaskCompletion(content: String) {
        // Mark conversation as complete
        isConversationActive.set(false)

        // 清除内容池
        // roundManager.clearContent()

        // Ensure input processing state is updated to completed
        withContext(Dispatchers.Main) {
            _inputProcessingState.value = InputProcessingState.Completed
        }

        // 保存问题记录到库
        toolProcessingScope.launch {
            ProblemLibrary.saveProblemAsync(
                    context,
                    toolHandler,
                    conversationHistory,
                    content,
                    multiServiceManager.getServiceForFunction(FunctionType.PROBLEM_LIBRARY)
            )
        }
    }

    /** Handle wait for user need logic - simplified version without callbacks */
    private suspend fun handleWaitForUserNeed(content: String) {
        // Mark conversation as complete
        isConversationActive.set(false)

        // 清除内容池
        // roundManager.clearContent()

        // Ensure input processing state is updated to completed
        withContext(Dispatchers.Main) {
            _inputProcessingState.value = InputProcessingState.Completed
        }

        Log.d(TAG, "Wait for user need - skipping problem library analysis")
    }

    /** Handle tool invocation processing - simplified version without callbacks */
    private suspend fun handleToolInvocation(
            toolInvocations: List<ToolInvocation>,
            displayContent: String,
            functionType: FunctionType = FunctionType.CHAT,
            collector: StreamCollector<String>
    ) {
        val startTime = System.currentTimeMillis()
        // Only process the first tool invocation, show warning if there are multiple
        val invocation = toolInvocations.first()

        if (toolInvocations.size > 1) {
            Log.w(TAG, "发现多个工具调用(${toolInvocations.size})，但只处理第一个: ${invocation.tool.name}")
            val warningContent =
                    ConversationMarkupManager.createMultipleToolsWarning(invocation.tool.name)
            roundManager.appendContent(warningContent)
            collector.emit(warningContent)
        }

        // Start executing the tool
        withContext(Dispatchers.Main) {
            _inputProcessingState.value =
                    InputProcessingState.Processing("Executing tool: ${invocation.tool.name}")
        }

        // Get tool executor and execute
        val executor = toolHandler.getToolExecutor(invocation.tool.name)
        val result: ToolResult

        if (executor == null) {
            // Tool not available handling
            val notAvailableContent =
                    ConversationMarkupManager.createToolNotAvailableError(invocation.tool.name)
            roundManager.appendContent(notAvailableContent)
            collector.emit(notAvailableContent)

            // Create error result
            result =
                    ToolResult(
                            toolName = invocation.tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Tool '${invocation.tool.name}' not available"
                    )
        } else {
            // Check permissions before execution
            val (hasPermission, errorResult) =
                    ToolExecutionManager.checkToolPermission(toolHandler, invocation)

            // If permission denied, add result and exit function
            if (!hasPermission) {
                // Add both error status and a tool result status to ensure the UI shows the error
                // properly
                val errorStatusContent =
                        ConversationMarkupManager.createErrorStatus(
                                "Permission denied",
                                "Operation '${invocation.tool.name}' was not authorized"
                        )
                roundManager.appendContent(errorStatusContent)
                collector.emit(errorStatusContent)

                // Also add a proper tool result status with error=true to ensure it shows up
                // correctly in the UI
                val toolResultStatusContent =
                        ConversationMarkupManager.formatToolResultForMessage(
                                ToolResult(
                                        toolName = invocation.tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "Permission denied: Operation '${invocation.tool.name}' was not authorized"
                                )
                        )
                roundManager.appendContent(toolResultStatusContent)
                collector.emit(toolResultStatusContent)

                // Process error result and exit
                if (errorResult != null) {
                    processToolResult(errorResult, functionType, collector)
                }
                return
            }

            // Execute the tool
            val toolStartTime = System.currentTimeMillis()
            result = ToolExecutionManager.executeToolSafely(invocation, executor)
            val executionTime = System.currentTimeMillis() - toolStartTime
            Log.d(TAG, "工具执行完成，耗时: ${executionTime}ms，结果: ${if (result.success) "成功" else "失败"}")

            // Display tool execution result
            val toolResultString =
                    if (result.success) result.result.toString() else "${result.error}"
            val toolResultStatusContent =
                    ConversationMarkupManager.formatToolResultForMessage(result)
            roundManager.appendContent(toolResultStatusContent)
            collector.emit(toolResultStatusContent)
        }

        // Process the tool result
        Log.d(TAG, "工具调用处理耗时: ${System.currentTimeMillis() - startTime}ms")
        processToolResult(result, functionType, collector)
    }

    /** Process tool execution result - simplified version without callbacks */
    private suspend fun processToolResult(
            result: ToolResult,
            functionType: FunctionType = FunctionType.CHAT,
            collector: StreamCollector<String>
    ) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "开始处理工具结果: ${result.toolName}, 成功: ${result.success}")
        
        // Add transition state
        withContext(Dispatchers.Main) {
            _inputProcessingState.value =
                    InputProcessingState.Processing(
                            "Tool execution completed, preparing further processing..."
                    )
        }

        // Check if conversation is still active
        if (!isConversationActive.get()) {
            return
        }

        // Tool result processing and subsequent AI request
        val toolResultMessage = ConversationMarkupManager.formatToolResultForMessage(result)

        // Add tool result to conversation history
        conversationMutex.withLock { conversationHistory.add(Pair("tool", toolResultMessage)) }

        // Get current conversation history
        val currentChatHistory = conversationMutex.withLock { conversationHistory }

        // Append tool result to current round
        roundManager.appendContent(toolResultMessage)

        // Start new round - ensure tool execution response will be shown in a new message
        roundManager.startNewRound()
        streamBuffer.clear() // Clear buffer to ensure a new message will be created

        // Clearly show we're preparing to send tool result to AI
        withContext(Dispatchers.Main) {
            _inputProcessingState.value =
                    InputProcessingState.Processing("Preparing to process tool execution result...")
        }

        // Add short delay to make state change more visible
        delay(300)

        // Get all model parameters from preferences (with enabled state)
        val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }

        // 获取对应功能类型的AIService实例
        val serviceForFunction = multiServiceManager.getServiceForFunction(functionType)

        // 使用新的Stream API处理工具执行结果
        withContext(Dispatchers.IO) {
            try {
                // 发送消息并获取响应流
                val aiStartTime = System.currentTimeMillis()
                val stream =
                        serviceForFunction.sendMessage(
                                message = toolResultMessage,
                                chatHistory = currentChatHistory,
                                modelParameters = modelParameters
                        )

                // 更新状态为接收中
                withContext(Dispatchers.Main) {
                    _inputProcessingState.value =
                            InputProcessingState.Receiving(
                                    "Receiving AI response after tool execution..."
                            )
                }

                // 处理流
                var chunkCount = 0
                var totalChars = 0
                var lastLogTime = System.currentTimeMillis()

                stream.collect { content ->
                    // 更新streamBuffer
                    streamBuffer.append(content)

                    // 更新内容到轮次管理器
                    roundManager.updateContent(streamBuffer.toString())

                    // 累计统计
                    chunkCount++
                    totalChars += content.length

                    // 定期记录日志
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLogTime > 5000) { // 每5秒记录一次
                        lastLogTime = currentTime
                    }

                    // 通过收集器将内容发射出去，让UI可以接收到
                    collector.emit(content)
                }

                val processingTime = System.currentTimeMillis() - aiStartTime
                Log.d(TAG, "工具结果AI处理完成，收到 $totalChars 字符，耗时: ${processingTime}ms")

                // 流处理完成，处理完成逻辑
                processStreamCompletion(functionType, collector)
            } catch (e: Exception) {
                Log.e(TAG, "处理工具执行结果时出错", e)
                withContext(Dispatchers.Main) {
                    _inputProcessingState.value =
                            InputProcessingState.Error("处理工具执行结果失败: ${e.message}")
                }
            }
        }
        Log.d(TAG, "工具结果处理总耗时: ${System.currentTimeMillis() - startTime}ms")
    }

    // Expose base AIService for token counting
    fun getBaseAIService(): AIService = aiService

    /**
     * Get the current input token count from the last API call
     * @return The number of input tokens used in the most recent request
     */
    fun getCurrentInputTokenCount(): Int {
        return aiService.inputTokenCount
    }

    /**
     * Get the current output token count from the last API call
     * @return The number of output tokens generated in the most recent response
     */
    fun getCurrentOutputTokenCount(): Int {
        return aiService.outputTokenCount
    }

    /** Reset token counters to zero Use this when starting a new conversation */
    fun resetTokenCounters() {
        aiService.resetTokenCounts()
    }

    /**
     * 重置指定功能类型或所有功能类型的token计数器
     * @param functionType 功能类型，如果为null则重置所有功能类型
     */
    suspend fun resetTokenCountersForFunction(functionType: FunctionType? = null) {
        if (functionType == null) {
            // 重置所有服务实例的token计数
            FunctionType.values().forEach { type ->
                try {
                    val service = multiServiceManager.getServiceForFunction(type)
                    service.resetTokenCounts()
                } catch (e: Exception) {
                    Log.e(TAG, "重置${type}功能的token计数失败", e)
                }
            }
        } else {
            // 只重置指定功能类型的token计数
            val service = multiServiceManager.getServiceForFunction(functionType)
            service.resetTokenCounts()
        }
    }

    /**
     * Extract plan items from chat history without sending a message This is used by ChatViewModel
     * to restore plan items when switching chats or on app startup
     */
    fun extractPlansFromHistory(chatHistory: List<Pair<String, String>>) {
        if (chatHistory.isEmpty()) {
            Log.d(TAG, "extractPlansFromHistory: 空聊天历史，跳过计划项提取")
            return
        }

        Log.d(TAG, "extractPlansFromHistory: 从 ${chatHistory.size} 条聊天记录中提取计划项")

        // 确保计划项状态流为空，以便重新填充
        _planItems.value = emptyList()

        // 提取所有助手消息
        val assistantMessages = chatHistory.filter { it.first == "assistant" }.map { it.second }
        Log.d(TAG, "extractPlansFromHistory: 找到 ${assistantMessages.size} 条助手消息")

        // 从每条助手消息中提取计划项
        var accumulatedItems = emptyList<PlanItem>()

        toolProcessingScope.launch {
            try {
                val planningEnabled = apiPreferences.enableAiPlanningFlow.first()
                if (!planningEnabled) {
                    Log.d(TAG, "extractPlansFromHistory: 计划模式未启用，跳过提取")
                    return@launch
                }

                // 按时间顺序处理每条消息
                for (content in assistantMessages) {
                    // 使用带有现有计划项参数的方法提取计划项
                    accumulatedItems =
                            ConversationMarkupManager.extractPlanItems(content, accumulatedItems)
                }

                // 只有找到计划项时才更新状态流
                if (accumulatedItems.isNotEmpty()) {
                    Log.d(
                            TAG,
                            "extractPlansFromHistory: 从历史聊天中提取到计划项: ${accumulatedItems.map { "${it.id}: ${it.status}" }}"
                    )
                    _planItems.value = accumulatedItems
                    Log.d(TAG, "extractPlansFromHistory: 已更新计划项状态流")
                } else {
                    Log.d(TAG, "extractPlansFromHistory: 未从历史聊天中提取到任何计划项")
                }
            } catch (e: Exception) {
                Log.e(TAG, "extractPlansFromHistory: 提取计划项时发生错误: ${e.message}", e)
            }
        }
    }

    /**
     * 生成对话总结
     * @param messages 要总结的消息列表
     * @return 生成的总结文本
     */
    suspend fun generateSummary(messages: List<Pair<String, String>>): String {
        return generateSummary(messages, null)
    }

    /**
     * 生成对话总结，并且包含上一次的总结内容
     * @param messages 要总结的消息列表
     * @param previousSummary 上一次的总结内容，可以为null
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            previousSummary: String?
    ): String {
        try {
            // 使用更结构化、更详细的提示词
            var systemPrompt =
                    """
            请对以下对话内容进行简洁但全面的总结。遵循以下格式：
            
            1. 以"对话摘要"作为标题
            2. 用1-2个简短段落概述主要内容和交互
            3. 明确列出对理解后续对话至关重要的关键信息点（如用户提到的具体问题、需求、限制条件等）
            4. 特别标注用户明确表达的意图或情感，如有
            5. 避免使用复杂的标题结构和Markdown格式，使用简单段落
            
            总结应该尽量保留对后续对话有价值的上下文信息，但不要包含无关细节。内容应该简洁明了，便于AI快速理解历史对话的要点。
            """

            // 如果存在上一次的摘要，将其添加到系统提示中
            if (previousSummary != null && previousSummary.isNotBlank()) {
                systemPrompt +=
                        """
                
                以下是之前对话的摘要，请参考它来生成新的总结，确保新总结融合之前的要点，并包含新的信息：
                
                ${previousSummary.trim()}
                """
                Log.d(TAG, "添加上一条摘要内容到系统提示")
            }

            val finalMessages = listOf(Pair("system", systemPrompt)) + messages

            // Get all model parameters from preferences (with enabled state)
            val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }

            // 获取SUMMARY功能类型的AIService实例
            val summaryService = multiServiceManager.getServiceForFunction(FunctionType.SUMMARY)

            // 使用summaryService发送请求，收集完整响应
            val contentBuilder = StringBuilder()

            // 使用新的Stream API
            val stream =
                    summaryService.sendMessage(
                            message = "请按照要求总结对话内容",
                            chatHistory = finalMessages,
                            modelParameters = modelParameters
                    )

            // 收集流中的所有内容
            stream.collect { content -> contentBuilder.append(content) }

            // 获取完整的总结内容
            val summaryContent = contentBuilder.toString().trim()

            // 如果内容为空，返回默认消息
            if (summaryContent.isBlank()) {
                return "对话摘要：未能生成有效摘要。"
            }

            // 获取本次总结生成的token统计
            val inputTokens = summaryService.inputTokenCount
            val outputTokens = summaryService.outputTokenCount

            // 将总结token计数添加到用户偏好分析的token统计中
            try {
                Log.d(TAG, "总结生成使用了输入token: $inputTokens, 输出token: $outputTokens")
                apiPreferences.updatePreferenceAnalysisTokens(inputTokens, outputTokens)
                Log.d(TAG, "已将总结token统计添加到用户偏好分析token计数中")
            } catch (e: Exception) {
                Log.e(TAG, "更新token统计失败", e)
            }

            return summaryContent
        } catch (e: Exception) {
            Log.e(TAG, "生成总结时出错", e)
            return "对话摘要：生成摘要时出错，但对话仍在继续。"
        }
    }

    /**
     * 获取指定功能类型的当前输入token计数
     * @param functionType 功能类型
     * @return 输入token计数
     */
    suspend fun getCurrentInputTokenCountForFunction(functionType: FunctionType): Int {
        return multiServiceManager.getServiceForFunction(functionType).inputTokenCount
    }

    /**
     * 获取指定功能类型的当前输出token计数
     * @param functionType 功能类型
     * @return 输出token计数
     */
    suspend fun getCurrentOutputTokenCountForFunction(functionType: FunctionType): Int {
        return multiServiceManager.getServiceForFunction(functionType).outputTokenCount
    }

    /** Prepare the conversation history with system prompt */
    private suspend fun prepareConversationHistory(
            chatHistory: List<Pair<String, String>>,
            processedInput: String
    ): MutableList<Pair<String, String>> {
        conversationMutex.withLock {
            conversationHistory.clear()

            // Add system prompt if not already present
            if (!chatHistory.any { it.first == "system" }) {
                val activeProfile = preferencesManager.getUserPreferencesFlow().first()
                val preferencesText = buildPreferencesText(activeProfile)

                // Check if planning is enabled
                val planningEnabled = apiPreferences.enableAiPlanningFlow.first()

                // Get custom prompts if available
                val customIntroPrompt = apiPreferences.customIntroPromptFlow.first()
                val customTonePrompt = apiPreferences.customTonePromptFlow.first()

                // 获取系统提示词，并替换{CHAT_ID}为当前聊天ID
                var systemPrompt =
                        if (customIntroPrompt.isNotEmpty() || customTonePrompt.isNotEmpty()) {
                            // Use custom prompts if they are set
                            SystemPromptConfig.getSystemPromptWithCustomPrompts(
                                    packageManager,
                                    planningEnabled,
                                    customIntroPrompt,
                                    customTonePrompt
                            )
                        } else {
                            // Use default system prompt
                            SystemPromptConfig.getSystemPrompt(packageManager, planningEnabled)
                        }

                // 替换{CHAT_ID}为当前聊天ID
                if (currentChatId != null) {
                    systemPrompt = systemPrompt.replace("{CHAT_ID}", currentChatId!!)
                } else {
                    // 如果没有聊天ID，使用一个默认值
                    systemPrompt = systemPrompt.replace("{CHAT_ID}", "default")
                }

                if (preferencesText.isNotEmpty()) {
                    conversationHistory.add(
                            0,
                            Pair(
                                    "system",
                                    "$systemPrompt\n\nUser preference description: $preferencesText"
                            )
                    )
                } else {
                    conversationHistory.add(0, Pair("system", systemPrompt))
                }
            }

            // Process each message in chat history
            for (message in chatHistory) {
                val role = message.first
                val content = message.second

                // If it's an assistant message, check for tool results
                if (role == "assistant") {
                    val toolResults = extractToolResults(content)
                    if (toolResults.isNotEmpty()) {
                        // Process the message with tool results
                        processChatMessageWithTools(content, toolResults)
                    } else {
                        // Add the message as is
                        conversationHistory.add(message)
                    }
                } else {
                    // Add user or system messages as is
                    conversationHistory.add(message)
                }
            }
        }
        return conversationHistory
    }

    /**
     * Extract tool results from message content using xmlToolResultPattern
     */
    private fun extractToolResults(content: String): List<Triple<String, String, IntRange>> {
        val results = mutableListOf<Triple<String, String, IntRange>>()

        // Extract using tool_result pattern
        val xmlToolResultPattern = MessageContentParser.Companion.xmlToolResultPattern
        xmlToolResultPattern.findAll(content).forEach { match ->
            val toolName = match.groupValues[1]
            val toolResult = match.groupValues[0]
            results.add(Triple(toolName, toolResult, match.range))
        }

        return results
    }

    /**
     * Process a chat message that contains tool results, splitting it into assistant message
     * segments and tool result segments
     */
    private suspend fun processChatMessageWithTools(
            content: String,
            toolResults: List<Triple<String, String, IntRange>>
    ) {
        var lastEnd = 0
        val sortedResults = toolResults.sortedBy { it.third.first }

        // Check memory optimization setting once for all tool results
        val memoryOptimizationEnabled = apiPreferences.memoryOptimizationFlow.first()

        for (result in sortedResults) {
            val toolName = result.first
            var toolResult = result.second
            val range = result.third

            // Add assistant message before the tool result (if any)
            if (range.first > lastEnd) {
                val assistantContent = content.substring(lastEnd, range.first)
                if (assistantContent.isNotBlank()) {
                    conversationHistory.add(Pair("assistant", assistantContent))
                }
            }

            // Apply memory optimization for long tool results if enabled
            if (memoryOptimizationEnabled && toolResult.length > 1000) {
                // Optimize tool result by extracting the most important parts
                toolResult = optimizeToolResult(toolName, toolResult)
                Log.d(
                        TAG,
                        "Memory optimization applied to tool result for $toolName, reduced length from ${result.second.length} to ${toolResult.length}"
                )
            }

            if (conversationHistory.last().first == "tool") {
                conversationHistory[conversationHistory.size - 1] = Pair("tool", toolResult)
            } else {
                conversationHistory.add(Pair("tool", toolResult))
            }

            // Update lastEnd
            lastEnd = range.last + 1
        }

        // Add any remaining assistant content after the last tool result
        if (lastEnd < content.length) {
            val assistantContent = content.substring(lastEnd)
            if (assistantContent.isNotBlank()) {
                conversationHistory.add(Pair("assistant", assistantContent))
            }
        }
    }

    /**
     * Optimize tool result by selecting the most important parts This helps with memory management
     * for long tool outputs
     */
    private fun optimizeToolResult(toolName: String, toolResult: String): String {
        // For excessive long tool results, keep only essential parts
        if (toolName == "use_package") {
            return toolResult
        }
        if (toolResult.length <= 1000) return toolResult

        // Extract content within XML tags
        val tagContent =
                Regex("<[^>]*>(.*?)</[^>]*>", RegexOption.DOT_MATCHES_ALL)
                        .find(toolResult)
                        ?.groupValues
                        ?.getOrNull(1)

        val sb = StringBuilder()

        // Add prefix based on tool name
        sb.append("<tool_result name=\"$toolName\">")

        // If xml content was extracted, use it, otherwise use the first 300 and last 300 chars
        if (!tagContent.isNullOrEmpty()) {
            // For XML content, take up to 800 chars
            val maxContentLength = 800
            val content =
                    if (tagContent.length > maxContentLength) {
                        tagContent.substring(0, 400) +
                                "\n... [content truncated for memory optimization] ...\n" +
                                tagContent.substring(tagContent.length - 400)
                    } else {
                        tagContent
                    }
            sb.append(content)
        } else {
            // For non-XML content, take important parts from beginning and end
            sb.append(toolResult.substring(0, 400))
            sb.append("\n... [content truncated for memory optimization] ...\n")
            sb.append(toolResult.substring(toolResult.length - 400))
        }

        // Add closing tag
        sb.append("</tool_result>")

        return sb.toString()
    }

    /** Build a formatted preferences text string from a PreferenceProfile */
    private fun buildPreferencesText(
            profile: com.ai.assistance.operit.data.model.PreferenceProfile
    ): String {
        val parts = mutableListOf<String>()

        if (profile.gender.isNotEmpty()) {
            parts.add("性别: ${profile.gender}")
        }

        if (profile.birthDate > 0) {
            // Convert timestamp to age and format as text
            val today = Calendar.getInstance()
            val birthCal = Calendar.getInstance().apply { timeInMillis = profile.birthDate }
            var age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
            // Adjust age if birthday hasn't occurred yet this year
            if (today.get(Calendar.MONTH) < birthCal.get(Calendar.MONTH) ||
                            (today.get(Calendar.MONTH) == birthCal.get(Calendar.MONTH) &&
                                    today.get(Calendar.DAY_OF_MONTH) <
                                            birthCal.get(Calendar.DAY_OF_MONTH))
            ) {
                age--
            }
            parts.add("年龄: ${age}岁")

            // Also add birth date for more precise information
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            parts.add("出生日期: ${dateFormat.format(java.util.Date(profile.birthDate))}")
        }

        if (profile.personality.isNotEmpty()) {
            parts.add("性格特点: ${profile.personality}")
        }

        if (profile.identity.isNotEmpty()) {
            parts.add("身份认同: ${profile.identity}")
        }

        if (profile.occupation.isNotEmpty()) {
            parts.add("职业: ${profile.occupation}")
        }

        if (profile.aiStyle.isNotEmpty()) {
            parts.add("期待的AI风格: ${profile.aiStyle}")
        }

        return parts.joinToString("; ")
    }

    /** Cancel the current conversation */
    fun cancelConversation() {
        // Set conversation inactive
        isConversationActive.set(false)

        // Cancel underlying AIService streaming
        aiService.cancelStreaming()

        // Cancel all tool executions
        cancelAllToolExecutions()

        // Clean up current conversation content
        roundManager.clearContent()
        Log.d(TAG, "Conversation canceled - content pool cleared")

        // Reset input processing state
        _inputProcessingState.value = InputProcessingState.Idle

        // Clear callback references
        currentResponseCallback = null
        currentCompleteCallback = null

        Log.d(TAG, "Conversation cancellation complete - all state reset except plan items")
    }

    /** Cancel all tool executions */
    private fun cancelAllToolExecutions() {
        toolProcessingScope.coroutineContext.cancelChildren()
    }

    /** Mark conversation as completed */
    private fun markConversationCompleted() {
        isConversationActive.set(false)
        roundManager.clearContent()
        _inputProcessingState.value = InputProcessingState.Completed
        Log.d(TAG, "Conversation marked as completed - content pool cleared")
    }
}
