package com.ai.assistance.operit.api

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.api.enhance.InputProcessor
import com.ai.assistance.operit.api.library.ProblemLibrary
import com.ai.assistance.operit.api.enhance.ReferenceManager
import com.ai.assistance.operit.api.enhance.ToolExecutionManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.model.AiReference
import com.ai.assistance.operit.api.enhance.ConversationMarkupManager
import com.ai.assistance.operit.api.enhance.ConversationRoundManager
import com.ai.assistance.operit.api.enhance.PlanItemParser
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.PlanItemStatus
import com.ai.assistance.operit.data.model.ToolExecutionProgress
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.StringResultData
import com.ai.assistance.operit.tools.packTool.PackageManager
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Calendar

/**
 * Enhanced AI service that provides advanced conversational capabilities
 * by integrating various components like tool execution, conversation management,
 * user preferences, and problem library.
 */
class EnhancedAIService(
    apiEndpoint: String,
    apiKey: String,
    modelName: String,
    private val context: Context
) {
    companion object {
        private const val TAG = "EnhancedAIService"
    }

    // Use composition for the base AIService
    private val aiService = AIService(apiEndpoint, apiKey, modelName)

    // Tool handler for executing tools
    private val toolHandler = AIToolHandler.getInstance(context)

    // 初始化问题库
    init {
        ProblemLibrary.initialize(context)
    }

    // State flows for UI updates
    private val _inputProcessingState = MutableStateFlow<InputProcessingState>(InputProcessingState.Idle)
    val inputProcessingState = _inputProcessingState.asStateFlow()

    private val _references = MutableStateFlow<List<AiReference>>(emptyList())
    val references = _references.asStateFlow()
    
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

    init {
        toolHandler.registerDefaultTools()
    }

    /**
     * Get the tool progress flow for UI updates
     */
    fun getToolProgressFlow(): StateFlow<ToolExecutionProgress> {
        return toolHandler.toolProgress
    }

    /**
     * Process user input with a delay for UI feedback
     */
    suspend fun processUserInput(input: String): String {
        _inputProcessingState.value = InputProcessingState.Processing("Processing input...")
        return InputProcessor.processUserInput(input)
    }

    /**
     * Extract and update references from content
     */
    private fun extractReferences(content: String) {
        val newReferences = ReferenceManager.extractReferences(content)
        if (newReferences.isNotEmpty()) {
            _references.value = newReferences
        }
    }
    
    /**
     * Extract and update plan items from content
     */
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
                    val updatedPlanItems = ConversationMarkupManager.extractPlanItems(content, existingItems)
                    
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

    /**
     * Clear all references
     */
    fun clearReferences() {
        _references.value = emptyList()
    }
    
    /**
     * Clear all plan items
     */
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
     * Re-extract plan items from the entire chat history
     * This helps recover plan items in case they were lost due to UI state issues
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
                    Log.d(TAG, "当前已有计划项，无需重新提取: ${_planItems.value.map { "${it.id}: ${it.status}" }}")
                    return@launch
                }
                
                Log.d(TAG, "开始从历史聊天中重新提取计划项")
                // 获取所有助手消息内容
                val assistantMessages = chatHistory.filter { it.first == "assistant" }.map { it.second }
                
                // 合并提取所有计划项的逻辑
                var accumulatedItems = _planItems.value
                
                for (content in assistantMessages) {
                    // 使用新的带有现有计划项参数的方法
                    accumulatedItems = ConversationMarkupManager.extractPlanItems(content, accumulatedItems)
                }
                
                // 只有在找到计划项时才更新状态流
                if (accumulatedItems.isNotEmpty() && accumulatedItems != _planItems.value) {
                    Log.d(TAG, "从历史聊天中重新提取到计划项: ${accumulatedItems.map { "${it.id}: ${it.status}" }}")
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

    /**
     * Send a message to the AI service
     */
    suspend fun sendMessage(
        message: String,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>> = emptyList(),
        onComplete: () -> Unit = {}
    ) {
        // Clean up any existing state
        cancelAllToolExecutions()

        // Set new callbacks
        currentResponseCallback = onPartialResponse
        currentCompleteCallback = onComplete
        isConversationActive.set(true)
        
        // 尝试从历史聊天中重新提取计划项，确保不会丢失
        reExtractPlanItems(chatHistory)

        // If it's a new conversation, reset all state
        if (chatHistory.isEmpty()) {
            Log.d(TAG, "Starting new conversation - resetting all state")
            conversationHistory.clear()
            clearReferences()
            // 修改：只有在没有计划项或强制清除状态下才清除计划项
            // 这确保了在对话完成或等待用户输入后，计划项不会被清除
            if (_planItems.value.isEmpty()) {
                Log.d(TAG, "无现有计划项，无需保留")
            } else {
                Log.d(TAG, "发现现有计划项，保留计划项: ${_planItems.value.map { "${it.id}: ${it.status}" }}")
                // clearPlanItems() - 不再清除现有计划项
            }
            streamBuffer.clear()
            roundManager.initializeNewConversation()
        } else {
            // 如果有聊天历史，说明是在继续之前的对话，确保会话标记为活跃
            Log.d(TAG, "继续现有对话，确保计划项保留: ${_planItems.value.map { "${it.id}: ${it.status}" }}")
        }

        // Show processing input feedback
        val processedInput = processUserInput(message)
        prepareConversationHistory(chatHistory, processedInput)

        Log.d(TAG, "Conversation history: ${conversationHistory.map { it.first }}")

        // Show connecting to AI feedback
        _inputProcessingState.value = InputProcessingState.Connecting("Connecting to AI service...")

        withContext(Dispatchers.IO) {
            aiService.sendMessage(
                message = processedInput,
                onPartialResponse = { content, thinking ->
                    // First response received, update to receiving state
                    if (streamBuffer.isEmpty()) {
                        _inputProcessingState.value = InputProcessingState.Receiving("Receiving AI response...")
                    }

                    processContent(
                        content,
                        thinking,
                        onPartialResponse,
                        conversationHistory,
                        isFollowUp = false
                    )
                },
                chatHistory = conversationHistory,
                onComplete = {
                    handleStreamingComplete(onComplete)
                },
                onConnectionStatus = { status ->
                    // Update connection status in UI
                    when {
                        status.contains("准备连接") || status.contains("正在建立连接") ->
                            _inputProcessingState.value = InputProcessingState.Connecting(status)
                        status.contains("连接成功") ->
                            _inputProcessingState.value = InputProcessingState.Receiving(status)
                        status.contains("超时") || status.contains("失败") ->
                            _inputProcessingState.value = InputProcessingState.Processing(status)
                    }
                }
            )
        }
    }

    /**
     * Prepare the conversation history with system prompt
     */
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
                
                if (preferencesText.isNotEmpty()) {
                    conversationHistory.add(0, Pair("system", "${SystemPromptConfig.getSystemPrompt(packageManager, planningEnabled)}\n\nUser preference description: $preferencesText"))
                } else {
                    conversationHistory.add(0, Pair("system", SystemPromptConfig.getSystemPrompt(packageManager, planningEnabled)))
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
     * Extract tool results from message content using both xmlToolResultPattern and xmlStatusPattern
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
        
        // Also extract using status pattern for result type
        val xmlStatusPattern = MessageContentParser.Companion.xmlStatusPattern
        xmlStatusPattern.findAll(content).forEach { match ->
            val toolName = match.groupValues[2]
            val toolResult = match.groupValues[0]
            
            // Include status of type "result" or "executing" as tool result
            if (toolName.isNotEmpty()) {
                results.add(Triple(toolName, toolResult, match.range))
            }
        }
        
        return results
    }

    /**
     * Process a chat message that contains tool results, splitting it into
     * assistant message segments and tool result segments
     */
    private suspend fun processChatMessageWithTools(content: String, toolResults: List<Triple<String, String, IntRange>>) {
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
                Log.d(TAG, "Memory optimization applied to tool result for $toolName, reduced length from ${result.second.length} to ${toolResult.length}")
            }
            
            if(conversationHistory.last().first == "tool") {
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
     * Optimize tool result by selecting the most important parts
     * This helps with memory management for long tool outputs
     */
    private fun optimizeToolResult(toolName: String, toolResult: String): String {
        // For excessive long tool results, keep only essential parts
        if (toolName == "use_package") {
            return toolResult
        }
        if (toolResult.length <= 1000) return toolResult
        
        // Extract content within XML tags
        val tagContent = Regex("<[^>]*>(.*?)</[^>]*>", RegexOption.DOT_MATCHES_ALL)
            .find(toolResult)?.groupValues?.getOrNull(1)
        
        val sb = StringBuilder()
        
        // Add prefix based on tool name
        sb.append("<tool_result name=\"$toolName\">")
        
        // If xml content was extracted, use it, otherwise use the first 300 and last 300 chars
        if (!tagContent.isNullOrEmpty()) {
            // For XML content, take up to 800 chars
            val maxContentLength = 800
            val content = if (tagContent.length > maxContentLength) {
                tagContent.substring(0, 400) + "\n... [content truncated for memory optimization] ...\n" + 
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

    /**
     * Build a formatted preferences text string from a PreferenceProfile
     */
    private fun buildPreferencesText(profile: com.ai.assistance.operit.data.model.PreferenceProfile): String {
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
                 today.get(Calendar.DAY_OF_MONTH) < birthCal.get(Calendar.DAY_OF_MONTH))) {
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

    /**
     * Cancel the current conversation
     */
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

        // Clear references but not plan items
        clearReferences()
        // 记录取消对话时的计划项状态，确保不会意外清除
        if (_planItems.value.isNotEmpty()) {
            Log.d(TAG, "取消对话时的计划项状态: ${_planItems.value.map { "${it.id}: ${it.status}" }}")
            Log.d(TAG, "取消对话保留计划项，不清除计划项状态")
        }
        // clearPlanItems()  // 不再自动清除计划项

        // Clear callback references
        currentResponseCallback = null
        currentCompleteCallback = null

        Log.d(TAG, "Conversation cancellation complete - all state reset except plan items")
    }

    /**
     * Process content received from the AI service
     */
    private fun processContent(
        content: String,
        thinking: String?,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>>,
        isFollowUp: Boolean
    ) {
        // First check if conversation is canceled
        if (!isConversationActive.get()) {
            Log.d(TAG, "Conversation canceled, skipping content processing")
            return
        }

        try {
            // If this is a follow-up after tool execution, use a new content round
            if (isFollowUp) {
                // Use completely new content processing
                streamBuffer.replace(0, streamBuffer.length, content)
                val displayContent = roundManager.updateContent(content)
                
                // Extract references and plan items
                extractReferences(displayContent)
                // 移除此处的计划项处理，只在流结束时处理
                // extractPlanItems(displayContent)
                
                onPartialResponse(displayContent, thinking)
                return
            }

            // Regular message processing
            streamBuffer.replace(0, streamBuffer.length, content)

            // Update content in round manager
            val displayContent = roundManager.updateContent(content)
            
            // Extract references and plan items as the content is received
            extractReferences(displayContent)

            // Check again if conversation is active
            if (!isConversationActive.get()) {
                Log.d(TAG, "Conversation canceled during processing, skipping callback")
                return
            }

            // Execute callback if not null
            onPartialResponse(displayContent, thinking)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing content", e)
            // Don't interrupt the flow even if there's an error
        }
    }

    /**
     * Handle the completion of streaming response
     */
    private fun handleStreamingComplete(onComplete: () -> Unit) {
        toolProcessingScope.launch {
            try {
                // If conversation is no longer active, return immediately
                if (!isConversationActive.get()) {
                    Log.d(TAG, "Conversation canceled, skipping streaming complete handling")
                    onComplete()
                    return@launch
                }

                // Get response content
                val content = streamBuffer.toString().trim()

                // If content is empty, finish immediately
                if (content.isEmpty()) {
                    Log.d(TAG, "Response content is empty, skipping processing")
                    onComplete()
                    return@launch
                }

                val displayContent = roundManager.getDisplayContent()
                val responseCallback = currentResponseCallback ?: run {
                    Log.d(TAG, "Callback cleared, skipping processing")
                    onComplete()
                    return@launch
                }

                // 首先提取引用和计划项，确保在任何状态变更前完成
                extractReferences(displayContent)
                
                // 提取计划项
                Log.d(TAG, "流传输完成，处理计划项")
                extractPlanItems(content)
                
                // Handle task completion marker
                if (ConversationMarkupManager.containsTaskCompletion(content)) {
                    handleTaskCompletion(displayContent, null, responseCallback)
                    onComplete()
                    return@launch
                }
                
                // Handle wait for user need marker
                if (ConversationMarkupManager.containsWaitForUserNeed(content)) {
                    handleWaitForUserNeed(displayContent, null, responseCallback)
                    onComplete()
                    return@launch
                }

                // Check again if conversation is active
                if (!isConversationActive.get()) {
                    Log.d(TAG, "Conversation canceled after extracting content, stopping further processing")
                    onComplete()
                    return@launch
                }

                // Add current assistant message to conversation history
                try {
                    conversationMutex.withLock {
                        conversationHistory.add(Pair("assistant", displayContent))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding assistant message to history", e)
                    onComplete()
                    return@launch
                }

                // Check again if conversation is active
                if (!isConversationActive.get()) {
                    Log.d(TAG, "Conversation canceled after processing history")
                    onComplete()
                    return@launch
                }

                // Main flow: Detect and process tool invocations
                Log.d(TAG, "Starting tool invocation detection")

                // Try to detect tool invocations
                val toolInvocations = toolHandler.extractToolInvocations(content)

                if (toolInvocations.isNotEmpty()) {
                    handleToolInvocation(toolInvocations, displayContent, responseCallback, onComplete)
                    return@launch
                }

                // 修改默认行为：如果没有特殊标记或工具调用，默认等待用户输入
                // 而不是直接标记为完成
                Log.d(TAG, "未检测到特殊标记或工具调用，默认使用等待用户输入模式")
                
                // 创建等待用户输入的内容
                val userNeedContent = ConversationMarkupManager.createWaitForUserNeedContent(displayContent)
                
                // 处理为等待用户输入模式
                handleWaitForUserNeed(userNeedContent, null, responseCallback)
                
                // 完成回调
                onComplete()
            } catch (e: Exception) {
                // Catch any exceptions in the processing flow
                Log.e(TAG, "Error handling streaming complete", e)
                _inputProcessingState.value = InputProcessingState.Idle
                onComplete() // Ensure completion callback is called even if error occurs
            }
        }
    }

    /**
     * Handle tool invocation processing
     */
    private suspend fun handleToolInvocation(
        toolInvocations: List<ToolInvocation>,
        displayContent: String,
        responseCallback: (content: String, thinking: String?) -> Unit,
        onComplete: () -> Unit
    ) {
        Log.d(TAG, "Found tool invocation in completed content, starting processing")

        // Only process the first tool invocation, show warning if there are multiple
        val invocation = toolInvocations.first()

        if (toolInvocations.size > 1) {
            Log.w(TAG, "Found multiple tool invocations (${toolInvocations.size}), but only processing the first: ${invocation.tool.name}")

            val updatedDisplayContent = roundManager.appendContent(
                ConversationMarkupManager.createMultipleToolsWarning(invocation.tool.name)
            )
            responseCallback(updatedDisplayContent, null)
        }

        // Start executing the tool
        _inputProcessingState.value = InputProcessingState.Processing(
            "Executing tool: ${invocation.tool.name}"
        )

        // Update UI to show tool execution status
        val updatedDisplayContent = roundManager.appendContent(
            ConversationMarkupManager.createExecutingToolStatus(invocation.tool.name)
        )
        responseCallback(updatedDisplayContent, null)

        // Get tool executor and execute
        val executor = toolHandler.getToolExecutor(invocation.tool.name)
        val result: ToolResult

        if (executor == null) {
            // Tool not available handling
            Log.w(TAG, "Tool not available: ${invocation.tool.name}")

            val errorDisplayContent = roundManager.appendContent(
                ConversationMarkupManager.createToolNotAvailableError(invocation.tool.name)
            )
            responseCallback(errorDisplayContent, null)

            // Create error result
            result = ToolResult(
                toolName = invocation.tool.name,
                success = false,
                result = StringResultData(""),
                error = "Tool '${invocation.tool.name}' not available"
            )
        } else {
            // Check permissions before execution
            val (hasPermission, errorResult) = ToolExecutionManager.checkToolPermission(
                toolHandler,
                invocation
            )

            // If permission denied, add result and exit function
            if (!hasPermission) {
                val errorDisplayContent = roundManager.appendContent(
                    ConversationMarkupManager.createErrorStatus("Permission denied", "Operation '${invocation.tool.name}' was not authorized")
                )
                responseCallback(errorDisplayContent, null)

                // Process error result and exit
                if (errorResult != null) {
                    processToolResult(errorResult, onComplete)
                }
                return
            }

            // Execute the tool
            result = ToolExecutionManager.executeToolSafely(invocation, executor)

            // Display tool execution result
            val toolResultString = if (result.success) result.result.toString() else "${result.error}"
            val resultDisplayContent = roundManager.appendContent(
                ConversationMarkupManager.createToolResultStatus(
                    invocation.tool.name,
                    result.success,
                    toolResultString
                )
            )
            responseCallback(resultDisplayContent, null)
        }

        // Process the tool result
        processToolResult(result, onComplete)
    }

    /**
     * Handle task completion logic
     */
    private fun handleTaskCompletion(content: String, thinking: String?, onPartialResponse: (content: String, thinking: String?) -> Unit) {
        // Mark conversation as complete
        isConversationActive.set(false)

        // Clean up task completion marker
        val cleanedContent = ConversationMarkupManager.createTaskCompletionContent(content)

        val displayContent = roundManager.getDisplayContent()
        
        
        // 清除内容池
        roundManager.clearContent()

        // Ensure input processing state is updated to completed
        _inputProcessingState.value = InputProcessingState.Completed

        // Update UI
        onPartialResponse(cleanedContent, thinking)

        // Save problem in the background
        toolProcessingScope.launch {
            // Call completion callback
            currentCompleteCallback?.invoke()

            // 保存问题记录到库
            toolProcessingScope.launch {
                ProblemLibrary.saveProblemAsync(
                    context,
                    toolHandler,
                    conversationHistory,
                    displayContent,
                    aiService
                )
            }
        }
    }

    /**
     * Handle wait for user need logic - similar to task completion but without problem summary
     */
    private fun handleWaitForUserNeed(content: String, thinking: String?, onPartialResponse: (content: String, thinking: String?) -> Unit) {
        // Mark conversation as complete
        isConversationActive.set(false)

        // Clean up wait for user need marker
        val cleanedContent = ConversationMarkupManager.createWaitForUserNeedContent(content)

        val displayContent = roundManager.getDisplayContent()
        
        // 清除内容池
        roundManager.clearContent()

        // Ensure input processing state is updated to completed
        _inputProcessingState.value = InputProcessingState.Completed

        // Update UI
        onPartialResponse(cleanedContent, thinking)

        // Update conversation history without triggering problem analysis
        toolProcessingScope.launch {
            // Call completion callback
            currentCompleteCallback?.invoke()
            
            Log.d(TAG, "Wait for user need - skipping problem library analysis")
        }
    }

    /**
     * Mark conversation as completed
     */
    private fun markConversationCompleted() {
        isConversationActive.set(false)
        roundManager.clearContent()
        _inputProcessingState.value = InputProcessingState.Completed
        Log.d(TAG, "Conversation marked as completed - content pool cleared")
    }

    /**
     * Cancel all tool executions
     */
    private fun cancelAllToolExecutions() {
        toolProcessingScope.coroutineContext.cancelChildren()
    }

    /**
     * Process tool execution result and continue conversation
     */
    private suspend fun processToolResult(result: ToolResult, onComplete: () -> Unit) {
        // Add transition state
        _inputProcessingState.value = InputProcessingState.Processing("Tool execution completed, preparing further processing...")

        // Check if conversation is still active
        if (!isConversationActive.get()) {
            Log.d(TAG, "Conversation no longer active, not processing tool result")
            onComplete()
            return
        }

        // Tool result processing and subsequent AI request
        val toolResultMessage = ConversationMarkupManager.formatToolResultForMessage(result)

        // Add tool result to conversation history
        conversationMutex.withLock {
            conversationHistory.add(Pair("tool", toolResultMessage))
        }

        // Get current conversation history
        val currentChatHistory = conversationMutex.withLock {
            conversationHistory
        }

        // Save callback to local variable
        val responseCallback = currentResponseCallback

        // Update UI to show AI thinking status
        val currentDisplay = currentChatHistory.lastOrNull { it.first == "assistant" }?.second ?: ""
        if (currentDisplay.isNotEmpty() && responseCallback != null) {
            val updatedContent = ConversationMarkupManager.appendThinkingStatus(currentDisplay)
            responseCallback.invoke(updatedContent, null)
        }

        // Start new round - ensure tool execution response will be shown in a new message
        roundManager.startNewRound()
        streamBuffer.clear() // Clear buffer to ensure a new message will be created
        Log.d(TAG, "Starting new round for AI response to tool result")

        // Clearly show we're preparing to send tool result to AI
        _inputProcessingState.value = InputProcessingState.Processing("Preparing to process tool execution result...")

        // Add short delay to make state change more visible
        delay(300)

        // Direct request AI response in current flow, keeping in complete main loop
        withContext(Dispatchers.IO) {
            aiService.sendMessage(
                message = toolResultMessage,
                onPartialResponse = { content, thinking ->
                    // Only handle display, not any tool logic
                    // Use new processing approach to ensure this is a new message
                    if (responseCallback != null) {
                        processContent(content, thinking, responseCallback, currentChatHistory, isFollowUp = true)
                    }
                },
                chatHistory = currentChatHistory,
                onComplete = {
                    handleStreamingComplete {
                        if (!isConversationActive.get()) {
                            currentCompleteCallback?.invoke()
                        }
                    }
                },
                onConnectionStatus = { status ->
                    // Update connection status for post-tool execution request
                    when {
                        status.contains("准备连接") || status.contains("正在建立连接") ->
                            _inputProcessingState.value = InputProcessingState.Connecting(status + " (after tool execution)")
                        status.contains("连接成功") ->
                            _inputProcessingState.value = InputProcessingState.Receiving(status + " (after tool execution)")
                        status.contains("超时") || status.contains("失败") ->
                            _inputProcessingState.value = InputProcessingState.Processing(status + " (after tool execution)")
                    }
                }
            )
        }
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
    
    /**
     * Reset token counters to zero
     * Use this when starting a new conversation
     */
    fun resetTokenCounters() {
        aiService.resetTokenCounts()
    }

    /**
     * Extract plan items from chat history without sending a message
     * This is used by ChatViewModel to restore plan items when switching chats or on app startup
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
                    accumulatedItems = ConversationMarkupManager.extractPlanItems(content, accumulatedItems)
                }
                
                // 只有找到计划项时才更新状态流
                if (accumulatedItems.isNotEmpty()) {
                    Log.d(TAG, "extractPlansFromHistory: 从历史聊天中提取到计划项: ${accumulatedItems.map { "${it.id}: ${it.status}" }}")
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

} 