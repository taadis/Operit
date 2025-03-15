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

class EnhancedAIService(
    apiEndpoint: String,
    apiKey: String,
    modelName: String,
    private val context: Context
) {
    companion object {
        private const val TAG = "EnhancedAIService"
        private val SYSTEM_PROMPT = """
            You are Operit, an all-capable AI assistant, aimed at solving any task presented by the user. You have various tools at your disposal that you can call upon to efficiently complete complex requests. 
            
            When calling a tool, the user will see your response, and then will automatically send the tool results back to you in a follow-up message.
            
            ⚠️ CRITICAL TOOL USAGE RESTRICTION ⚠️
            - YOU MUST ONLY INVOKE ONE TOOL AT A TIME. This is absolutely critical.
            - NEVER include more than one tool invocation in a single response. The system can only handle one at a time.
            - If you need multiple tools to complete a task, use them sequentially - call one, wait for its result, then call the next.
            - After receiving a tool result, you can decide to use another tool or complete the task.
            - Violating this restriction will cause significant errors and prevent task completion.
            
            IMPORTANT COMMUNICATION GUIDELINES:
            - Keep your responses concise and to the point. Avoid lengthy explanations unless specifically requested.
            - When responding after tool execution, be brief and focus on insights from the tool result.
            - Your responses should naturally flow with previous messages as if it's one continuous conversation.
            - Avoid phrases like "based on the tool result" or "as I mentioned earlier" that suggest separate interactions.
            - Don't repeat information that was already provided in previous messages.
            
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
    
    private val aiService = AIService(apiEndpoint, apiKey, modelName)
    private val toolHandler = AIToolHandler(context)
    
    private val _inputProcessingState = MutableStateFlow<InputProcessingState>(InputProcessingState.Idle)
    val inputProcessingState = _inputProcessingState.asStateFlow()
    
    private val _references = MutableStateFlow<List<AiReference>>(emptyList())
    val references = _references.asStateFlow()
    
    private val streamBuffer = StringBuilder()
    private val contentAccumulationPool = StringBuilder()
    private val isNewResponseStarted = AtomicBoolean(true)
    private val currentResponseRound = AtomicInteger(0)
    private var lastContentForCurrentRound = ""
    
    private val toolProcessingScope = CoroutineScope(Dispatchers.IO)
    private val toolExecutionJobs = ConcurrentHashMap<String, Job>()
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val conversationMutex = Mutex()
    private val isConversationActive = AtomicBoolean(false)
    
    private var currentResponseCallback: ((content: String, thinking: String?) -> Unit)? = null
    private var currentCompleteCallback: (() -> Unit)? = null
    
    init {
        toolHandler.registerDefaultTools()
    }
    
    fun getToolProgressFlow(): StateFlow<com.ai.assistance.operit.model.ToolExecutionProgress> {
        return toolHandler.toolProgress
    }
    
    fun registerTool(name: String, executor: com.ai.assistance.operit.tools.ToolExecutor) {
        toolHandler.registerTool(name, executor)
    }
    
    suspend fun processUserInput(input: String): String {
        _inputProcessingState.value = InputProcessingState.Processing("Processing input...")
        
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
        }
        
        _inputProcessingState.value = InputProcessingState.Completed
        return input
    }
    
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
    
    fun clearReferences() {
        _references.value = emptyList()
    }
    
    suspend fun sendMessage(
        message: String,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>> = emptyList(),
        onComplete: () -> Unit = {}
    ) {
        currentResponseCallback = onPartialResponse
        currentCompleteCallback = onComplete
        isConversationActive.set(true)
        
        if (chatHistory.isEmpty()) {
            conversationHistory.clear()
            clearReferences()
            cancelAllToolExecutions()
            streamBuffer.clear()
            isNewResponseStarted.set(true)
        }
        
        val processedInput = processUserInput(message)
        val enhancedChatHistory = mutableListOf<Pair<String, String>>()
        
        conversationMutex.withLock {
            if (chatHistory.isEmpty() && conversationHistory.isEmpty()) {
                enhancedChatHistory.add(Pair("system", SYSTEM_PROMPT))
                conversationHistory.clear()
                conversationHistory.addAll(enhancedChatHistory)
            } else if (chatHistory.isNotEmpty()) {
                enhancedChatHistory.addAll(chatHistory)
                
                if (!enhancedChatHistory.any { it.first == "system" }) {
                    enhancedChatHistory.add(0, Pair("system", SYSTEM_PROMPT))
                }
                
                conversationHistory.clear()
                conversationHistory.addAll(enhancedChatHistory)
            } else {
                enhancedChatHistory.addAll(conversationHistory)
            }
            
            conversationHistory.add(Pair("user", processedInput))
        }
        
        withContext(Dispatchers.IO) {
            aiService.sendMessage(
                message = processedInput,
                onPartialResponse = { content, thinking ->
                    processStreamingContent(
                        content,
                        thinking,
                        onPartialResponse,
                        conversationHistory
                    )
                },
                chatHistory = conversationHistory,
                onComplete = {
                    handleStreamingComplete(onComplete)
                }
            )
        }
    }
    
    fun cancelConversation() {
        isConversationActive.set(false)
        cancelAllToolExecutions()
        contentAccumulationPool.clear()
        currentResponseRound.set(0)
        Log.d(TAG, "Conversation canceled - content pool cleared")
        
        currentCompleteCallback?.invoke()
        currentResponseCallback = null
        currentCompleteCallback = null
    }
    
    private fun processStreamingContent(
        content: String, 
        thinking: String?,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: MutableList<Pair<String, String>>
    ) {
        streamBuffer.replace(0, streamBuffer.length, content)
        
        if (isNewResponseStarted.getAndSet(false)) {
            currentResponseRound.set(0)
            contentAccumulationPool.clear()
            contentAccumulationPool.append(content)
            lastContentForCurrentRound = content
            
            Log.d(TAG, "New conversation started - content pool initialized with first content")
        } else {
            if (content != lastContentForCurrentRound) {
                val poolContent = contentAccumulationPool.toString()
                val currentRoundStartIndex = poolContent.lastIndexOf("--- Round ${currentResponseRound.get()} ---")
                
                if (currentRoundStartIndex >= 0) {
                    val beforeCurrentRound = poolContent.substring(0, currentRoundStartIndex)
                    contentAccumulationPool.replace(0, contentAccumulationPool.length, 
                        beforeCurrentRound + "--- Round ${currentResponseRound.get()} ---\n" + content)
                } else {
                    contentAccumulationPool.append("\n\n--- Round ${currentResponseRound.get()} ---\n").append(content)
                }
                
                lastContentForCurrentRound = content
                
                Log.d(TAG, "Updated content for round ${currentResponseRound.get()}")
            }
        }
        
        val accumulatedContent = contentAccumulationPool.toString()
        
        if (content.contains("[TASK_COMPLETE]")) {
            handleTaskCompletion(accumulatedContent, thinking, onPartialResponse)
            return
        }
        
        val contentWithoutSeparators = accumulatedContent.replace(Regex("--- Round \\d+ ---\n"), "")
        extractReferences(contentWithoutSeparators)
        
        val toolInvocations = toolHandler.extractToolInvocations(content)
        
        if (toolInvocations.isNotEmpty()) {
            toolProcessingScope.launch {
                conversationMutex.withLock {
                    val contentWithoutSeparators = accumulatedContent.replace(Regex("--- Round \\d+ ---\n"), "")
                    conversationHistory.add(Pair("assistant", contentWithoutSeparators))
                }
            }
        }
        
        val displayContent = accumulatedContent.replace(Regex("--- Round \\d+ ---\n"), "")
        onPartialResponse(displayContent, thinking)
        
        // Only process the first tool invocation when multiple are found
        // This ensures we only execute one tool at a time, as stated in the system prompt
        if (toolInvocations.isNotEmpty() && toolExecutionJobs.isEmpty()) {
            val invocation = toolInvocations.first()
            val toolId = "${invocation.tool.name}_${invocation.responseLocation.first}"
            
            val newRound = currentResponseRound.incrementAndGet()
            Log.d(TAG, "Starting new tool execution round: $newRound")
            
            val job = toolProcessingScope.launch {
                executeToolWithFollowUp(invocation, displayContent, onPartialResponse)
            }
            
            toolExecutionJobs[toolId] = job
            
            // Log warning if multiple tools were detected but we're only processing the first one
            if (toolInvocations.size > 1) {
                Log.w(TAG, "Multiple tool invocations found (${toolInvocations.size}), but only processing the first one: ${invocation.tool.name}")
                
                // Append a notification to the content
                contentAccumulationPool.append("\n\n<status type=\"warning\">多个工具被同时调用，系统只能处理一个。现在仅处理: ${invocation.tool.name}</status>")
                val updatedDisplayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
                onPartialResponse(updatedDisplayContent, thinking)
            }
        }
    }
    
    private suspend fun executeToolWithFollowUp(
        invocation: ToolInvocation,
        currentContent: String,
        onPartialResponse: (content: String, thinking: String?) -> Unit
    ) {
        _inputProcessingState.value = InputProcessingState.Processing(
            "Executing tool: ${invocation.tool.name}"
        )
        
        contentAccumulationPool.append("\n\n<status type=\"executing\" tool=\"${invocation.tool.name}\"></status>")
        lastContentForCurrentRound = ""
        
        val displayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
        onPartialResponse(displayContent, null)
        
        val executor = toolHandler.getToolExecutor(invocation.tool.name)
        
        if (executor == null) {
            Log.w(TAG, "Tool not available: ${invocation.tool.name}")
            
            val errorMessage = "无法找到工具: ${invocation.tool.name}"
            contentAccumulationPool.append("\n\n<status type=\"error\" tool=\"${invocation.tool.name}\">$errorMessage</status>")
            
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
            val result = try {
                val validationResult = executor.validateParameters(invocation.tool)
                if (!validationResult.valid) {
                    ToolResult(
                        toolName = invocation.tool.name,
                        success = false,
                        result = "",
                        error = "Invalid tool parameters: ${validationResult.errorMessage}"
                    )
                } else {
                    executor.invoke(invocation.tool)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during tool execution: ${invocation.tool.name}", e)
                ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = "",
                    error = "Tool execution error: ${e.message}"
                )
            }
            
            val toolResultString = if (result.success) result.result else "${result.error}"
            contentAccumulationPool.append("\n\n<status type=\"result\" tool=\"${invocation.tool.name}\" success=\"${result.success}\">${toolResultString}</status>")
            
            val updatedDisplayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
            onPartialResponse(updatedDisplayContent, null)
            
            postToolResultAsUserMessage(result)
        }
        
        toolHandler.markToolInvocationProcessed(invocation)
        
        val toolId = "${invocation.tool.name}_${invocation.responseLocation.first}"
        toolExecutionJobs.remove(toolId)
    }
    
    private suspend fun postToolResultAsUserMessage(result: ToolResult) {
        if (!isConversationActive.get()) {
            Log.d(TAG, "Conversation is no longer active, not posting tool result")
            return
        }
        
        val toolResultMessage = formatToolResultForMessage(result)
        
        conversationMutex.withLock {
            conversationHistory.add(Pair("user", toolResultMessage))
        }
        
        val currentChatHistory = conversationMutex.withLock {
            conversationHistory.toList()
        }
        
        val responseCallback = currentResponseCallback ?: return
        val completeCallback = currentCompleteCallback ?: {}
        
        val currentDisplay = currentChatHistory
            .lastOrNull { it.first == "assistant" }?.second ?: ""
        
        if (currentDisplay.isNotEmpty()) {
            val updatedContent = "$currentDisplay\n\n<status type=\"thinking\"></status>"
            responseCallback(updatedContent, null)
        }
        
        aiService.sendMessage(
            message = toolResultMessage,
            onPartialResponse = { content, thinking ->
                processFollowUpResponse(content, thinking, responseCallback, currentChatHistory)
            },
            chatHistory = currentChatHistory,
            onComplete = {
                if (!isConversationActive.get()) {
                    completeCallback()
                }
            }
        )
    }
    
    private fun processFollowUpResponse(
        content: String,
        thinking: String?,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>>
    ) {
        if (isNewResponseStarted.getAndSet(false)) {
            val newRound = currentResponseRound.incrementAndGet()
            Log.d(TAG, "Starting new AI follow-up round: $newRound")
            
            contentAccumulationPool.append("\n\n--- Round $newRound ---\n").append(content)
            lastContentForCurrentRound = content
        } else {
            if (content != lastContentForCurrentRound) {
                val poolContent = contentAccumulationPool.toString()
                val currentRound = currentResponseRound.get()
                val currentRoundStartIndex = poolContent.lastIndexOf("--- Round $currentRound ---")
                
                if (currentRoundStartIndex >= 0) {
                    val beforeCurrentRound = poolContent.substring(0, currentRoundStartIndex)
                    contentAccumulationPool.replace(0, contentAccumulationPool.length, 
                        beforeCurrentRound + "--- Round $currentRound ---\n" + content)
                } else {
                    contentAccumulationPool.append("\n\n--- Round $currentRound ---\n").append(content)
                }
                
                lastContentForCurrentRound = content
                
                Log.d(TAG, "Updated content for follow-up round $currentRound")
            }
        }
        
        val displayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
        
        if (content.contains("[TASK_COMPLETE]")) {
            handleTaskCompletion(displayContent, thinking, onPartialResponse)
            return
        }
        
        onPartialResponse(displayContent, thinking)
        
        val toolInvocations = toolHandler.extractToolInvocations(content)
        
        if (toolInvocations.isNotEmpty()) {
            toolProcessingScope.launch {
                conversationMutex.withLock {
                    conversationHistory.add(Pair("assistant", displayContent))
                }
            }
            
            // Only process the first tool invocation when multiple are found
            // This ensures we only execute one tool at a time, as stated in the system prompt
            if (toolExecutionJobs.isEmpty()) {
                val invocation = toolInvocations.first()
                val toolId = "${invocation.tool.name}_${invocation.responseLocation.first}"
                
                val newRound = currentResponseRound.incrementAndGet()
                Log.d(TAG, "Starting new tool execution round from follow-up: $newRound")
                
                val job = toolProcessingScope.launch {
                    executeToolWithFollowUp(invocation, displayContent, onPartialResponse)
                }
                
                toolExecutionJobs[toolId] = job
                
                // Log warning if multiple tools were detected but we're only processing the first one
                if (toolInvocations.size > 1) {
                    Log.w(TAG, "Multiple tool invocations found in follow-up (${toolInvocations.size}), but only processing the first one: ${invocation.tool.name}")
                    
                    // Append a notification to the content
                    contentAccumulationPool.append("\n\n<status type=\"warning\">多个工具被同时调用，系统只能处理一个。现在仅处理: ${invocation.tool.name}</status>")
                    val updatedDisplayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
                    onPartialResponse(updatedDisplayContent, thinking)
                }
            }
        } else {
            toolProcessingScope.launch {
                conversationMutex.withLock {
                    conversationHistory.add(Pair("assistant", displayContent))
                }
            }
        }
    }
    
    private fun formatToolResultForMessage(result: ToolResult): String {
        return if (result.success) {
            """
            <tool_result name="${result.toolName}" status="success">
            <content>
            ${result.result}
            </content>
            </tool_result>
            """.trimIndent()
        } else {
            """
            <tool_result name="${result.toolName}" status="error">
            <error>${result.error ?: "Unknown error"}</error>
            </tool_result>
            """.trimIndent()
        }
    }
    
    private fun handleStreamingComplete(onComplete: () -> Unit) {
        toolProcessingScope.launch {
            val activeJobs = toolExecutionJobs.values.toList()
            val timeout = System.currentTimeMillis() + 10000 // 10 seconds timeout
            
            for (job in activeJobs) {
                if (System.currentTimeMillis() > timeout) {
                    Log.w(TAG, "Timed out waiting for tool executions to complete")
                    break
                }
                
                withContext(Dispatchers.Default) {
                    kotlinx.coroutines.withTimeoutOrNull(1000) {
                        job.join()
                    }
                }
            }
            
            _inputProcessingState.value = InputProcessingState.Completed
            
            if (isConversationActive.get() && toolExecutionJobs.isEmpty()) {
                val displayContent = contentAccumulationPool.toString().replace(Regex("--- Round \\d+ ---\n"), "")
                
                if (!displayContent.contains("[TASK_COMPLETE]")) {
                    val hasToolInvocations = toolHandler.extractToolInvocations(displayContent).isNotEmpty()
                    
                    if (!hasToolInvocations) {
                        Log.d(TAG, "No tool invocations found in non-streaming mode, assuming completion")
                        
                        val responseCallback = currentResponseCallback
                        if (responseCallback != null) {
                            handleTaskCompletion(displayContent, null, responseCallback)
                        } else {
                            markConversationCompleted()
                        }
                    }
                }
            }
            
            if (!isConversationActive.get()) {
                onComplete()
            }
        }
    }
    
    /**
     * Centralizes the logic for handling task completion across the different methods.
     * Cleans content, updates UI, and marks the conversation as completed.
     */
    private fun handleTaskCompletion(content: String, thinking: String?, onPartialResponse: (content: String, thinking: String?) -> Unit) {
        // Mark conversation as inactive
        isConversationActive.set(false)
        
        // Clean content by removing the task complete marker and any round separators
        val cleanedContent = content
            .replace("[TASK_COMPLETE]", "")
            .replace(Regex("--- Round \\d+ ---\n"), "")
            .trim() + "\n\n<status type=\"complete\"></status>"  // 在所有情况下统一添加任务完成的文本
        
        // Clear the content accumulation pool
        contentAccumulationPool.clear()
        Log.d(TAG, "Task complete - content pool cleared")
        
        // Update UI with the cleaned content
        onPartialResponse(cleanedContent, thinking)
        
        // Update conversation history
        toolProcessingScope.launch {
            conversationMutex.withLock {
                conversationHistory.add(Pair("assistant", cleanedContent))
            }
            
            // Invoke the complete callback if present
            currentCompleteCallback?.invoke()
        }
    }
    
    /**
     * Marks the conversation as completed and clears necessary state
     */
    private fun markConversationCompleted() {
        isConversationActive.set(false)
        contentAccumulationPool.clear()
        currentResponseRound.set(0)
        Log.d(TAG, "Assumed task complete - content pool cleared")
    }
    
    private fun cancelAllToolExecutions() {
        toolProcessingScope.coroutineContext.cancelChildren()
        toolExecutionJobs.clear()
    }
}

sealed class InputProcessingState {
    object Idle : InputProcessingState()
    data class Processing(val message: String) : InputProcessingState()
    object Completed : InputProcessingState()
}

data class AiReference(
    val text: String,
    val url: String
)