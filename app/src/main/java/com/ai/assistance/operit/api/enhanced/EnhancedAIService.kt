package com.ai.assistance.operit.api.enhanced

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.AIService
import com.ai.assistance.operit.api.enhanced.config.SystemPromptConfig
import com.ai.assistance.operit.api.enhanced.utils.InputProcessor
import com.ai.assistance.operit.api.enhanced.utils.ProblemLibraryManager
import com.ai.assistance.operit.api.enhanced.utils.ReferenceManager
import com.ai.assistance.operit.api.enhanced.utils.ToolExecutionManager
import com.ai.assistance.operit.api.enhanced.utils.UserPreferenceAnalyzer
import com.ai.assistance.operit.data.preferencesManager
import com.ai.assistance.operit.model.AiReference
import com.ai.assistance.operit.api.enhanced.models.ConversationMarkupManager
import com.ai.assistance.operit.api.enhanced.models.ConversationRoundManager
import com.ai.assistance.operit.model.InputProcessingState
import com.ai.assistance.operit.model.ToolInvocation
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.tools.packTool.PackageManager
import com.ai.assistance.operit.util.ChatUtils
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

    // State flows for UI updates
    private val _inputProcessingState = MutableStateFlow<InputProcessingState>(InputProcessingState.Idle)
    val inputProcessingState = _inputProcessingState.asStateFlow()

    private val _references = MutableStateFlow<List<AiReference>>(emptyList())
    val references = _references.asStateFlow()

    // Conversation management
    private val streamBuffer = StringBuilder()
    private val roundManager = ConversationRoundManager()
    private val isConversationActive = AtomicBoolean(false)

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
    fun getToolProgressFlow(): StateFlow<com.ai.assistance.operit.model.ToolExecutionProgress> {
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
        val enhancedChatHistory = prepareConversationHistory(chatHistory, processedInput)

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
                        enhancedChatHistory,
                        isFollowUp = false
                    )
                },
                chatHistory = enhancedChatHistory,
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
        val enhancedChatHistory = mutableListOf<Pair<String, String>>()

        conversationMutex.withLock {
            if (chatHistory.isEmpty() && conversationHistory.isEmpty()) {
                // Add system prompt
                var systemPrompt = SystemPromptConfig.getSystemPrompt(packageManager)

                val userPreferences = preferencesManager.userPreferencesFlow.first()
                if (userPreferences.preferences.isNotEmpty()) {
                    systemPrompt += "\n\nUser preference description: ${userPreferences.preferences}"
                }

                enhancedChatHistory.add(Pair("system", systemPrompt))

                conversationHistory.clear()
                conversationHistory.addAll(enhancedChatHistory)
            } else if (chatHistory.isNotEmpty()) {
                // Process incoming chat history, ensuring roles are correctly mapped
                enhancedChatHistory.addAll(ChatUtils.mapChatHistoryToStandardRoles(chatHistory))

                if (!enhancedChatHistory.any { it.first == "system" }) {
                    // Add system prompt
                    enhancedChatHistory.add(0, Pair("system", SystemPromptConfig.getSystemPrompt(packageManager)))

                    // Add user preference description
                    val userPreferences = preferencesManager.userPreferencesFlow.first()
                    if (userPreferences.preferences.isNotEmpty()) {
                        enhancedChatHistory.add(1, Pair("system", "User preference description: ${userPreferences.preferences}"))
                    }
                }

                conversationHistory.clear()
                conversationHistory.addAll(enhancedChatHistory)
            } else {
                enhancedChatHistory.addAll(conversationHistory)
            }

            conversationHistory.add(Pair("user", processedInput))
        }
        return enhancedChatHistory
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

        // Safely clean up conversation history
        toolProcessingScope.launch {
            try {
                conversationMutex.withLock {
                    // If the last message is from AI, remove it as it might be incomplete
                    if (conversationHistory.isNotEmpty() && conversationHistory.last().first == "assistant") {
                        Log.d(TAG, "Removing incomplete AI message from conversation history")
                        conversationHistory.removeAt(conversationHistory.size - 1)
                    }
                }

                Log.d(TAG, "Conversation history cleanup complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up conversation history", e)
            }
        }

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

                    // Ensure state is properly reset after task completion
                    _inputProcessingState.value = InputProcessingState.Completed

                    // Save problem record to library
                    toolProcessingScope.launch {
                        ProblemLibraryManager.saveProblemToLibrary(
                            toolHandler,
                            conversationHistory,
                            displayContent,
                            aiService
                        )
                    }

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
                    _inputProcessingState.value = InputProcessingState.Completed

                    if (isConversationActive.get()) {
                        markConversationCompleted()
                    }

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

        // Special handling for sequence tool execution
        if (invocation.tool.name == "execute_sequence") {
            val executor = toolHandler.getToolExecutor(invocation.tool.name)
            val result = ToolExecutionManager.handleSequenceTool(
                invocation,
                executor
            ) { statusUpdate ->
                val updatedContent = roundManager.appendContent(statusUpdate)
                responseCallback(updatedContent, null)
            }

            // Process the tool result
            processToolResult(result, onComplete)
            return
        }

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
                result = "",
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
            val toolResultString = if (result.success) result.result else "${result.error}"
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

        // Clear content pool
        roundManager.clearContent()

        // Ensure input processing state is updated to completed
        _inputProcessingState.value = InputProcessingState.Completed

        Log.d(TAG, "Task completed - content pool cleared, input processing state updated to Completed")

        // Update UI
        onPartialResponse(cleanedContent, thinking)

        // Update conversation history
        toolProcessingScope.launch {
            conversationMutex.withLock {
                conversationHistory.add(Pair("assistant", cleanedContent))
            }

            // Call completion callback
            currentCompleteCallback?.invoke()

            // Analyze and save user preferences
            UserPreferenceAnalyzer.analyzeAndSaveUserPreferences(
                aiService,
                conversationHistory
            )
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
            conversationHistory.add(Pair("user", toolResultMessage))
        }

        // Get current conversation history
        val currentChatHistory = conversationMutex.withLock {
            ChatUtils.mapChatHistoryToStandardRoles(conversationHistory)
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
    
    /**
     * Analyze user preferences directly (not through sendMessage, not shown in chat UI)
     */
    suspend fun analyzeUserPreferences(
        conversationHistory: List<Pair<String, String>>,
        onResult: (String) -> Unit
    ) {
        UserPreferenceAnalyzer.analyzeUserPreferences(
            aiService,
            conversationHistory,
            onResult
        )
    }
} 