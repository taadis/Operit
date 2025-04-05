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
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.model.InputProcessingState
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
     * Clear all references
     */
    fun clearReferences() {
        _references.value = emptyList()
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

        // If it's a new conversation, reset all state
        if (chatHistory.isEmpty()) {
            Log.d(TAG, "Starting new conversation - resetting all state")
            conversationHistory.clear()
            clearReferences()
            streamBuffer.clear()
            roundManager.initializeNewConversation()
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
                val userPreferences = preferencesManager.userPreferencesFlow.first()
                if (userPreferences.preferences.isNotEmpty()) {
                    conversationHistory.add(0, Pair("system", "${SystemPromptConfig.getSystemPrompt(packageManager)}\n\nUser preference description: ${userPreferences.preferences}"))
                } else {
                    conversationHistory.add(0, Pair("system", SystemPromptConfig.getSystemPrompt(packageManager)))
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

        // Clear references
        clearReferences()

        // Clear callback references
        currentResponseCallback = null
        currentCompleteCallback = null

        Log.d(TAG, "Conversation cancellation complete - all state reset")
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
                onPartialResponse(displayContent, thinking)
                return
            }

            // Regular message processing
            streamBuffer.replace(0, streamBuffer.length, content)

            // Update content in round manager
            val displayContent = roundManager.updateContent(content)

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

                // Extract references in the complete stage
                extractReferences(displayContent)

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

                    // Still call completion callback if error occurs
                    onComplete()
                    return@launch
                }

                // Check again if conversation is active
                if (!isConversationActive.get()) {
                    Log.d(TAG, "Conversation canceled after processing history, stopping further processing")
                    onComplete()
                    return@launch
                }

                // Main flow: Detect and process tool invocations
                Log.d(TAG, "Starting tool invocation detection: content length = ${content.length}")

                // Add more detailed logging including first 50 chars of content for debugging
                val contentPreview = if (content.length > 50) content.substring(0, 50) + "..." else content
                Log.d(TAG, "Content preview: $contentPreview")

                // Try to detect tool invocations using both methods
                val toolInvocations = toolHandler.extractToolInvocations(content)

                if (toolInvocations.isNotEmpty() && isConversationActive.get()) {
                    // Tool invocation processing flow
                    Log.d(TAG, "Found ${toolInvocations.size} tool invocations: ${toolInvocations.map { it.tool.name }}")
                    handleToolInvocation(toolInvocations, displayContent, responseCallback, onComplete)
                } else {
                    // Double-check - use simple text matching to confirm if tool invocation tags might have been missed
                    val possibleToolTag = "<tool\\s+name=".toRegex().find(content)

                    if (possibleToolTag != null) {
                        // Possible tool invocation but not correctly parsed, do more lenient detection
                        Log.w(TAG, "Possible tool tag found but no valid tool invocations extracted, doing secondary check")

                        // Retry tool extraction - this time clean the string, removing some chars that might interfere with parsing
                        val cleanedContent = content
                            .replace("\r", " ")
                            .replace(Regex("\\s{2,}"), " ")

                        val retriedInvocations = toolHandler.extractToolInvocations(cleanedContent)

                        if (retriedInvocations.isNotEmpty() && isConversationActive.get()) {
                            // Second try successfully extracted tool invocations
                            Log.d(TAG, "Secondary check found ${retriedInvocations.size} tool invocations: ${retriedInvocations.map { it.tool.name }}")
                            handleToolInvocation(retriedInvocations, displayContent, responseCallback, onComplete)
                            return@launch
                        } else {
                            Log.w(TAG, "Secondary check still found no valid tool invocations, format might not match expectations")
                        }
                    }

                    // No tool invocations, mark conversation round as completed
                    Log.d(TAG, "No tool invocations found, completing current round")

                    // I dont know this will work or not
                    handleTaskCompletion(displayContent, null, responseCallback)

                    // if the handleTaskCompletion is need to be here, I think it should be here
                    // if (isConversationActive.get()) {
                    //     markConversationCompleted()
                    // }

                    onComplete()
                }
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
        // Clear content pool
        roundManager.clearContent()

        // Ensure input processing state is updated to completed
        _inputProcessingState.value = InputProcessingState.Completed

        Log.d(TAG, "Task completed - content pool cleared, input processing state updated to Completed")

        // Update UI
        onPartialResponse(cleanedContent, thinking)

        // Update conversation history
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
        // Clear content pool
        roundManager.clearContent()

        // Ensure input processing state is updated to completed
        _inputProcessingState.value = InputProcessingState.Completed

        Log.d(TAG, "Wait for user need - content pool cleared, input processing state updated to Completed")

        // Update UI
        onPartialResponse(cleanedContent, thinking)

        // Update conversation history without triggering problem analysis
        toolProcessingScope.launch {
            // Call completion callback
            currentCompleteCallback?.invoke()

            // No problem library saving here - this is the key difference
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

} 