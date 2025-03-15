package com.ai.assistance.operit.api

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.model.ToolExecutionState
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.model.ToolInvocation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enhanced AI service that uses composition with AIService for tool handling capabilities
 * with non-streaming support for tool execution
 */
class EnhancedAIService(
    apiEndpoint: String,
    apiKey: String,
    modelName: String,
    private val context: Context
) {

    companion object {
        private const val TAG = "EnhancedAIService"
        private val SYSTEM_PROMPT_NON_STREAMING = """
            You are Operit, an all-capable AI assistant, aimed at solving any task presented by the user. You have various tools at your disposal that you can call upon to efficiently complete complex requests. 
            
            When calling a tool, the user will see your response, and then will automatically send the tool results back to you in a follow-up message.
            
            IMPORTANT COMMUNICATION GUIDELINES:
            - Keep your responses concise and to the point. Avoid lengthy explanations unless specifically requested.
            - When responding after tool execution, be brief and focus on insights from the tool result.
            - Your responses should naturally flow with previous messages as if it's one continuous conversation.
            - Avoid phrases like "based on the tool result" or "as I mentioned earlier" that suggest separate interactions.
            - Don't repeat information that was already provided in previous messages.
            
            IMPORTANT TOOL USAGE RESTRICTIONS:
            - You can only use ONE tool with return value at a time. Wait for its result before using another tool.
            - After receiving a tool result, you can decide to use another tool or complete the task.
            - This restriction is critical for proper functioning.
            
            To use a tool, use this format in your response:
            
            <tool name="tool_name">
            <param name="parameter_name">parameter_value</param>
            </tool>
            
            Available tools:
            - weather: Provides simulated weather data only (no real weather API calls). No parameters needed.
            - calculate: Simple calculator that evaluates basic expressions locally. Parameters: expression (e.g. "2+2", "sqrt(16)")
            - web_search: Returns pre-defined simulated search results (no actual web access). Parameters: query (the search term)
            - blocking_sleep: Demonstration tool that pauses briefly. Parameters: duration_ms (milliseconds, default 1000, max 10000)
            - non_blocking_sleep: Demonstration tool for asynchronous operations. Parameters: duration_ms (milliseconds, default 1000, max 10000)
            - device_info: Returns basic device identifier for the current app session only. No parameters needed.
            
            When you finish your task and no longer need any tools, end your response with: [TASK_COMPLETE]
            
            Based on user needs, proactively select the most appropriate tool or combination of tools. For complex tasks, you can break down the problem and use different tools step by step to solve it. After using each tool, clearly explain the execution results and suggest the next steps.

            Always maintain a helpful, informative tone throughout the interaction. If you encounter any limitations or need more details, clearly communicate this to the user before terminating.
        """.trimIndent()
    }
    
    // Use composition instead of inheritance
    private val aiService = AIService(apiEndpoint, apiKey, modelName)
    
    // Tool handler
    private val toolHandler = AIToolHandler(context)
    
    // Input preprocessing state
    private val _inputProcessingState = MutableStateFlow<InputProcessingState>(InputProcessingState.Idle)
    val inputProcessingState = _inputProcessingState.asStateFlow()
    
    // AI response references
    private val _references = MutableStateFlow<List<AiReference>>(emptyList())
    val references = _references.asStateFlow()
    
    // Track streaming AI response - used for tool extraction only
    private val streamBuffer = StringBuilder()
    
    // Content accumulation pool - persists across partial responses until task complete
    private val contentAccumulationPool = StringBuilder()
    
    // Flag to track if content pool needs initialization for a new response
    private var isNewResponseStarted = AtomicBoolean(true)
    
    // Tag to identify the current response round - increments for each tool execution
    private var currentResponseRound = AtomicInteger(0)
    
    // The last content received for the current response round
    private var lastContentForCurrentRound = ""
    
    // Coroutine scope for tool processing
    private val toolProcessingScope = CoroutineScope(Dispatchers.IO)
    
    // Tool execution jobs
    private val toolExecutionJobs = ConcurrentHashMap<String, Job>()
    
    // Conversation context for non-streaming mode
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    
    // Mutex for conversation updates
    private val conversationMutex = Mutex()
    
    // Flag to indicate if a conversation is in progress
    private val isConversationActive = AtomicBoolean(false)
    
    // Current onPartialResponse callback
    private var currentResponseCallback: ((content: String, thinking: String?) -> Unit)? = null
    
    // Current onComplete callback
    private var currentCompleteCallback: (() -> Unit)? = null
    
    init {
        // Register default tools
        toolHandler.registerDefaultTools()
    }
    
    /**
     * Get the tool progress flow
     */
    fun getToolProgressFlow(): StateFlow<com.ai.assistance.operit.model.ToolExecutionProgress> {
        return toolHandler.toolProgress
    }
    
    /**
     * Register a custom tool
     */
    fun registerTool(name: String, executor: com.ai.assistance.operit.tools.ToolExecutor) {
        toolHandler.registerTool(name, executor)
    }
    
    /**
     * Process user input before sending to AI
     */
    suspend fun processUserInput(input: String): String {
        _inputProcessingState.value = InputProcessingState.Processing("Processing input...")
        
        // This is where you would implement any preprocessing of user input
        // For example, entity extraction, command detection, etc.
        
        // For now, just return the input unchanged with a small delay to simulate processing
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
        }
        
        _inputProcessingState.value = InputProcessingState.Completed
        return input
    }
    
    /**
     * Extract potential references from the AI response and store them
     */
    private fun extractReferences(content: String) {
        val referencePattern = "\\[([^\\]]+)\\]\\((https?://[^\\)]+)\\)".toRegex()
        val matches = referencePattern.findAll(content)
        
        val newReferences = matches.map { matchResult ->
            val (text, url) = matchResult.destructured
            AiReference(text, url)
        }.toList()
        
        if (newReferences.isNotEmpty()) {
            _references.value = newReferences
        }
    }
    
    /**
     * Clear references
     */
    fun clearReferences() {
        _references.value = emptyList()
    }
    
    /**
     * Send message with non-streaming tool handling
     */
    suspend fun sendMessage(
        message: String,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>> = emptyList(),
        onComplete: () -> Unit = {}
    ) {
        // Store the callbacks for non-streaming mode
        currentResponseCallback = onPartialResponse
        currentCompleteCallback = onComplete
        
        // Set conversation as active
        isConversationActive.set(true)
        
        // Reset tracking state for a new message
        if (chatHistory.isEmpty()) {
            // This is a new conversation, so clear any previous state
            conversationHistory.clear()
            clearReferences()
            cancelAllToolExecutions()
            streamBuffer.clear()
            
            // Mark as new response, but don't clear contentAccumulationPool here
            isNewResponseStarted.set(true)
        }
        
        // Process input first
        val processedInput = processUserInput(message)
        
        // Add tools prompt to the chat history for proper context
        val enhancedChatHistory = mutableListOf<Pair<String, String>>()
        
        // Initialize or update the conversation history with the system prompt
        conversationMutex.withLock {
            if (chatHistory.isEmpty() && conversationHistory.isEmpty()) {
                // First message in a new conversation
                enhancedChatHistory.add(Pair("system", SYSTEM_PROMPT_NON_STREAMING))
                
                // Initialize the conversation history
                conversationHistory.clear()
                conversationHistory.addAll(enhancedChatHistory)
            } else if (!chatHistory.isEmpty()) {
                // Use provided chat history
                enhancedChatHistory.addAll(chatHistory)
                
                // Make sure we have the right system prompt
                if (!enhancedChatHistory.any { it.first == "system" }) {
                    enhancedChatHistory.add(0, Pair("system", SYSTEM_PROMPT_NON_STREAMING))
                }
                
                // Update conversation history
                conversationHistory.clear()
                conversationHistory.addAll(enhancedChatHistory)
            } else {
                // Use existing conversation history
                enhancedChatHistory.addAll(conversationHistory)
            }
            
            // Add the user message to the conversation history
            conversationHistory.add(Pair("user", processedInput))
        }
        
        // Process the request in non-streaming mode with tool handling
        withContext(Dispatchers.IO) {
            // Send the message to the AI using the composed AIService with streaming support
            aiService.sendMessage(
                message = processedInput,
                onPartialResponse = { content, thinking ->
                    // Process the streaming content for tool calls in real-time
                    processStreamingContent(
                        content,
                        thinking,
                        onPartialResponse,
                        conversationHistory
                    )
                },
                chatHistory = conversationHistory,
                onComplete = {
                    // Handle streaming completion based on mode
                    handleStreamingComplete(onComplete)
                }
            )
        }
    }
    
    /**
     * Cancel the current conversation and clean up resources
     */
    fun cancelConversation() {
        isConversationActive.set(false)
        cancelAllToolExecutions()
        
        // Clear the content accumulation pool when canceling
        contentAccumulationPool.clear()
        currentResponseRound.set(0)
        Log.d(TAG, "Conversation canceled - content pool cleared")
        
        currentCompleteCallback?.invoke()
        currentResponseCallback = null
        currentCompleteCallback = null
    }
    
    /**
     * Process streaming content, detecting and executing tools
     */
    private fun processStreamingContent(
        content: String, 
        thinking: String?,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: MutableList<Pair<String, String>>
    ) {
        // Update the stream buffer for tool extraction only (this gets replaced)
        streamBuffer.replace(0, streamBuffer.length, content)
        
        // For a brand new conversation or after TASK_COMPLETE
        if (isNewResponseStarted.getAndSet(false)) {
            // Initialize a new round of responses
            currentResponseRound.set(0)
            
            // Clear the pool and set the first content
            contentAccumulationPool.clear()
            contentAccumulationPool.append(content)
            
            // Track the content for this round
            lastContentForCurrentRound = content
            
            Log.d(TAG, "New conversation started - content pool initialized with first content")
        } else {
            // In the same response round, just update with the latest content
            // (don't accumulate within the same round)
            
            // Check if this is the same content as before (to avoid unnecessary updates)
            if (content != lastContentForCurrentRound) {
                // Extract just the part of the pool for the current round
                val poolContent = contentAccumulationPool.toString()
                val currentRoundStartIndex = poolContent.lastIndexOf("--- Round ${currentResponseRound.get()} ---")
                
                if (currentRoundStartIndex >= 0) {
                    // Replace just the content for the current round
                    val beforeCurrentRound = poolContent.substring(0, currentRoundStartIndex)
                    contentAccumulationPool.replace(0, contentAccumulationPool.length, 
                        beforeCurrentRound + "--- Round ${currentResponseRound.get()} ---\n" + content)
                } else {
                    // First content for this round
                    contentAccumulationPool.append("\n\n--- Round ${currentResponseRound.get()} ---\n").append(content)
                }
                
                // Update the last content for this round
                lastContentForCurrentRound = content
                
                Log.d(TAG, "Updated content for round ${currentResponseRound.get()}")
            }
        }
        
        // Get the accumulated content
        val accumulatedContent = contentAccumulationPool.toString()
        
        // Check for task completion marker
        if (content.contains("[TASK_COMPLETE]")) {
            // Mark the conversation as complete
            isConversationActive.set(false)
            
            // Clean up the marker and round separators from the displayed content
            var cleanedPoolContent = accumulatedContent.replace("[TASK_COMPLETE]", "").trim()
            // Remove round separators
            cleanedPoolContent = cleanedPoolContent.replace(Regex("--- Round \\d+ ---\n"), "")
            
            // Clear the pool after task completion
            contentAccumulationPool.clear()
            Log.d(TAG, "Task complete - content pool cleared")
            
            // Call the callback with the accumulated content
            onPartialResponse(cleanedPoolContent, thinking)
            
            // Save the assistant's final response to history
            toolProcessingScope.launch {
                conversationMutex.withLock {
                    conversationHistory.add(Pair("assistant", cleanedPoolContent))
                }
                
                // Complete the conversation
                currentCompleteCallback?.invoke()
            }
            return
        }
        
        // Extract references from the accumulated content (without round separators)
        val contentWithoutSeparators = accumulatedContent.replace(Regex("--- Round \\d+ ---\n"), "")
        extractReferences(contentWithoutSeparators)
        
        // Safely extract tool invocations from the current content chunk
        val toolInvocations = toolHandler.extractToolInvocations(content)
        
        // Save the assistant's response to history if we detected tools
        if (toolInvocations.isNotEmpty()) {
            toolProcessingScope.launch {
                conversationMutex.withLock {
                    // Remove round separators for history
                    val contentWithoutSeparators = accumulatedContent.replace(Regex("--- Round \\d+ ---\n"), "")
                    conversationHistory.add(Pair("assistant", contentWithoutSeparators))
                }
            }
        }
        
        // Always update with the accumulated content (but remove round separators for display)
        val displayContent = accumulatedContent.replace(Regex("--- Round \\d+ ---\n"), "")
        onPartialResponse(displayContent, thinking)
        
        // Process any new complete tool invocations
        toolInvocations.forEach { invocation ->
            val toolId = "${invocation.tool.name}_${invocation.responseLocation.first}"
            
            // Only process this tool invocation if we haven't started it yet
            if (!toolExecutionJobs.containsKey(toolId)) {
                // When we detect a tool invocation, we're starting a new round
                // Increment the round counter
                val newRound = currentResponseRound.incrementAndGet()
                Log.d(TAG, "Starting new tool execution round: $newRound")
                
                // Start a new tool execution job
                val job = toolProcessingScope.launch {
                    // Pass the clean display content without round separators
                    executeToolWithFollowUp(invocation, displayContent, onPartialResponse)
                }
                
                // Register the job
                toolExecutionJobs[toolId] = job
            }
        }
    }
    
    /**
     * Execute a tool with follow-up message for non-streaming AI models
     */
    private suspend fun executeToolWithFollowUp(
        invocation: ToolInvocation,
        currentContent: String,
        onPartialResponse: (content: String, thinking: String?) -> Unit
    ) {
        _inputProcessingState.value = InputProcessingState.Processing(
            "Executing tool: ${invocation.tool.name}"
        )
        
        // Add the tool execution notification
        contentAccumulationPool.append("\n\n_执行中: ${invocation.tool.name}_")
        
        // Clear lastContentForCurrentRound to ensure we'll accept the next content update
        lastContentForCurrentRound = ""
        
        // Get the content without round separators for display
        val displayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
        onPartialResponse(displayContent, null)
        
        // Find the executor for this tool
        val executor = toolHandler.getToolExecutor(invocation.tool.name)
        
        if (executor == null) {
            // Tool not available
            Log.w(TAG, "Tool not available: ${invocation.tool.name}")
            
            // Add the error notification
            contentAccumulationPool.append("\n\n_无法找到工具: ${invocation.tool.name}_")
            
            // Display without round separators
            val updatedDisplayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
            onPartialResponse(updatedDisplayContent, null)
            
            postToolResultAsUserMessage(
                ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = "",
                    error = "Tool '${invocation.tool.name}' is not available"
                )
            )
        } else {
            // Execute the tool
            val result = try {
                executor.invoke(invocation.tool)
            } catch (e: Exception) {
                Log.e(TAG, "Error during tool execution: ${invocation.tool.name}", e)
                ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = "",
                    error = "Tool execution error: ${e.message}"
                )
            }
            
            // Add the result
            val toolResultString = if (result.success) result.result else "错误: ${result.error}"
            contentAccumulationPool.append("\n\n_结果: ${invocation.tool.name}_\n$toolResultString")
            
            // Display without round separators
            val updatedDisplayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
            onPartialResponse(updatedDisplayContent, null)
            
            // Post the result as a new user message in the conversation
            postToolResultAsUserMessage(result)
        }
        
        // Mark this tool invocation as processed
        toolHandler.markToolInvocationProcessed(invocation)
        
        // Clean up the job
        val toolId = "${invocation.tool.name}_${invocation.responseLocation.first}"
        toolExecutionJobs.remove(toolId)
    }
    
    /**
     * Post a tool result as a user message and trigger a new AI response
     */
    private suspend fun postToolResultAsUserMessage(result: ToolResult) {
        if (!isConversationActive.get()) {
            Log.d(TAG, "Conversation is no longer active, not posting tool result")
            return
        }
        
        // Format the tool result as a user message
        val toolResultMessage = formatToolResultForMessage(result)
        
        // Add the tool result to the conversation history
        conversationMutex.withLock {
            conversationHistory.add(Pair("user", toolResultMessage))
        }
        
        // Send the follow-up message to get the AI's next response
        val currentChatHistory = conversationMutex.withLock {
            conversationHistory.toList()
        }
        
        // Use the current callbacks
        val responseCallback = currentResponseCallback ?: return
        val completeCallback = currentCompleteCallback ?: {}
        
        // Notify the user that we're waiting for AI response
        // Get the latest content to append our status to
        val currentDisplay = currentChatHistory
            .lastOrNull { it.first == "assistant" }?.second ?: ""
        
        // Only append if there's existing content
        if (currentDisplay.isNotEmpty()) {
            val updatedContent = "$currentDisplay\n\n_思考中..._"
            responseCallback(updatedContent, null)
        }
        
        // Send a follow-up message
        aiService.sendMessage(
            message = toolResultMessage,
            onPartialResponse = { content, thinking ->
                processFollowUpResponse(content, thinking, responseCallback, currentChatHistory)
            },
            chatHistory = currentChatHistory,
            onComplete = {
                // Only complete the overall conversation if the AI indicated task completion
                // Otherwise, the onComplete is handled in processFollowUpResponse
                if (!isConversationActive.get()) {
                    completeCallback()
                }
            }
        )
    }
    
    /**
     * Process a follow-up response from the AI after tool execution
     */
    private fun processFollowUpResponse(
        content: String,
        thinking: String?,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>>
    ) {
        // This is the beginning of a new round response
        if (isNewResponseStarted.getAndSet(false)) {
            // This is a new round of responses, so increment the round counter
            val newRound = currentResponseRound.incrementAndGet()
            Log.d(TAG, "Starting new AI follow-up round: $newRound")
            
            // Add the new round marker and content
            contentAccumulationPool.append("\n\n--- Round $newRound ---\n").append(content)
            
            // Track the content for this round
            lastContentForCurrentRound = content
        } else {
            // In the same round, just update with latest content
            if (content != lastContentForCurrentRound) {
                // Extract just the part of the pool for the current round
                val poolContent = contentAccumulationPool.toString()
                val currentRound = currentResponseRound.get()
                val currentRoundStartIndex = poolContent.lastIndexOf("--- Round $currentRound ---")
                
                if (currentRoundStartIndex >= 0) {
                    // Replace just the content for the current round
                    val beforeCurrentRound = poolContent.substring(0, currentRoundStartIndex)
                    contentAccumulationPool.replace(0, contentAccumulationPool.length, 
                        beforeCurrentRound + "--- Round $currentRound ---\n" + content)
                } else {
                    // First content for this round
                    contentAccumulationPool.append("\n\n--- Round $currentRound ---\n").append(content)
                }
                
                // Update the last content for this round
                lastContentForCurrentRound = content
                
                Log.d(TAG, "Updated content for follow-up round $currentRound")
            }
        }
        
        // Get the accumulated content (but remove round separators for display)
        val displayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
        
        // Check for task completion marker
        if (content.contains("[TASK_COMPLETE]")) {
            // Mark the conversation as complete
            isConversationActive.set(false)
            
            // Clean up the marker from the displayed content
            val cleanedContent = displayContent.replace("[TASK_COMPLETE]", "").trim()
            
            // Clear the pool after task completion
            contentAccumulationPool.clear()
            Log.d(TAG, "Task complete - content pool cleared")
            
            // Update the user with the clean content
            onPartialResponse(cleanedContent, thinking)
            
            // Save the assistant's final response to history
            toolProcessingScope.launch {
                conversationMutex.withLock {
                    conversationHistory.add(Pair("assistant", cleanedContent))
                }
            }
            return
        }
        
        // Always update with the accumulated content (without round separators)
        onPartialResponse(displayContent, thinking)
        
        // Check for tool invocations in the follow-up response
        val toolInvocations = toolHandler.extractToolInvocations(content)
        
        // If we have new tool invocations, save this response to history
        if (toolInvocations.isNotEmpty()) {
            toolProcessingScope.launch {
                conversationMutex.withLock {
                    conversationHistory.add(Pair("assistant", displayContent))
                }
            }
            
            // Process the new tool invocations
            toolInvocations.forEach { invocation ->
                val toolId = "${invocation.tool.name}_${invocation.responseLocation.first}"
                
                // Only process this tool invocation if we haven't started it yet
                if (!toolExecutionJobs.containsKey(toolId)) {
                    // When we detect a tool invocation, we're starting a new round
                    // Increment the round counter for the next tool execution
                    val newRound = currentResponseRound.incrementAndGet()
                    Log.d(TAG, "Starting new tool execution round from follow-up: $newRound")
                    
                    val job = toolProcessingScope.launch {
                        executeToolWithFollowUp(invocation, displayContent, onPartialResponse)
                    }
                    
                    toolExecutionJobs[toolId] = job
                }
            }
        } else {
            // No new tool invocations, just store the response
            toolProcessingScope.launch {
                conversationMutex.withLock {
                    conversationHistory.add(Pair("assistant", displayContent))
                }
            }
        }
    }
    
    /**
     * Format a tool result for sending as a user message
     */
    private fun formatToolResultForMessage(result: ToolResult): String {
        return if (result.success) {
            """
            <tool_result name="${result.toolName}">
            <status>success</status>
            <content>
            ${result.result}
            </content>
            </tool_result>
            """.trimIndent()
        } else {
            """
            <tool_result name="${result.toolName}">
            <status>error</status>
            <error>${result.error ?: "Unknown error"}</error>
            </tool_result>
            """.trimIndent()
        }
    }
    
    /**
     * Handle completion of the streaming response
     */
    private fun handleStreamingComplete(onComplete: () -> Unit) {
        // Ensure all tool executions are finished
        toolProcessingScope.launch {
            // Wait for all tool executions to complete with a timeout
            val activeJobs = toolExecutionJobs.values.toList()
            val timeout = System.currentTimeMillis() + 10000 // 10 seconds timeout
            
            for (job in activeJobs) {
                // Check if we've exceeded our timeout
                if (System.currentTimeMillis() > timeout) {
                    Log.w(TAG, "Timed out waiting for tool executions to complete")
                    break
                }
                
                // Wait for this job with a short timeout
                withContext(Dispatchers.Default) {
                    kotlinx.coroutines.withTimeoutOrNull(1000) {
                        job.join()
                    }
                }
            }
            
            // Mark processing as completed
            _inputProcessingState.value = InputProcessingState.Completed
            
            // Check if there are no active tools and no [TASK_COMPLETE] marker was found
            if (isConversationActive.get() && toolExecutionJobs.isEmpty()) {
                // Get the accumulated content (without round separators)
                val displayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
                
                // Check if this response already has a [TASK_COMPLETE] marker
                if (!displayContent.contains("[TASK_COMPLETE]")) {
                    // Check if there are tool invocations in the response
                    val hasToolInvocations = toolHandler.extractToolInvocations(displayContent).isNotEmpty()
                    
                    // If no tool invocations and the conversation is still active, we can assume completion
                    if (!hasToolInvocations) {
                        Log.d(TAG, "No tool invocations found in non-streaming mode, assuming completion")
                        
                        // Update the callback that we're treating this as complete
                        val responseCallback = currentResponseCallback
                        if (responseCallback != null) {
                            // Use the accumulated content with completion marker
                            val completedContent = "$displayContent\n\n_任务完成_"
                            responseCallback(completedContent, null)
                        }
                        
                        // Mark as no longer active since we're treating this as complete
                        isConversationActive.set(false)
                        
                        // Clear the content pool
                        contentAccumulationPool.clear()
                        currentResponseRound.set(0)
                        Log.d(TAG, "Assumed task complete - content pool cleared")
                    }
                }
            }
            
            // Only complete if the conversation is no longer active
            if (!isConversationActive.get()) {
                onComplete()
            }
        }
    }
    
    /**
     * Cancel all ongoing tool executions
     */
    private fun cancelAllToolExecutions() {
        toolProcessingScope.coroutineContext.cancelChildren()
        toolExecutionJobs.clear()
    }
}

/**
 * States for input processing
 */
sealed class InputProcessingState {
    object Idle : InputProcessingState()
    data class Processing(val message: String) : InputProcessingState()
    object Completed : InputProcessingState()
}

/**
 * Represents a reference found in AI response
 */
data class AiReference(
    val text: String,
    val url: String
) 
