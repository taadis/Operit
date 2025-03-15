package com.ai.assistance.operit.api

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.model.ToolExecutionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enhanced AI service that uses composition with AIService for tool handling capabilities
 */
class EnhancedAIService(
    apiEndpoint: String,
    apiKey: String,
    modelName: String,
    private val context: Context
) {

    companion object {
        private const val TAG = "EnhancedAIService"
        private val SYSTEM_PROMPT_TOOLS = """
            You have access to the following tools. When you want to use a tool, output it in this specific format:
            
            <tool name="tool_name">
            <param name="parameter_name">parameter_value</param>
            </tool>
            
            Available tools:
            - weather: Get the current weather. No parameters needed.
            - calculate: Calculate a mathematical expression. Parameters: expression (e.g. "2+2")
            - web_search: Search the web for information. Parameters: query (the search term)
            
            Only use these tools when necessary. Use the exact format specified above.
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
     * Send message with tool handling
     */
    suspend fun sendMessage(
        message: String,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>> = emptyList(),
        onComplete: () -> Unit = {}
    ) {
        // Process input first
        val processedInput = processUserInput(message)
        
        // Add tools prompt to the chat history for proper context
        val enhancedChatHistory = chatHistory.toMutableList()
        
        // Only add the system prompt if it's not already there (to avoid duplication)
        if (enhancedChatHistory.none { it.first == "system" && it.second.contains("tools") }) {
            enhancedChatHistory.add(0, Pair("system", SYSTEM_PROMPT_TOOLS))
        }
        
        // Track the accumulated response for tool processing
        val accumulatedResponse = StringBuilder()
        var lastProcessedLength = 0
        
        // Create a collection point for the final response
        val processedResponse = MutableStateFlow<String>("")
        
        // Clear previous references
        clearReferences()
        
        // Send the message and handle the response
        var aiResponseComplete = false
        var toolProcessingComplete = true // Start as true to allow the first response
        
        withContext(Dispatchers.IO) {
            val scope = CoroutineScope(Dispatchers.IO)
            
            // Send the message to the AI using the composed AIService
            aiService.sendMessage(
                message = processedInput,
                onPartialResponse = { content, thinking ->
                    // Update the accumulated response - REMOVING THE RESET that was causing truncation
                    // accumulatedResponse.setLength(0) - This was causing truncation by clearing the text!
                    accumulatedResponse.replace(0, accumulatedResponse.length, content)
                    
                    // Extract references
                    extractReferences(content)
                    
                    // Only process tools when we have a significant content update
                    if (content.length > lastProcessedLength + 50 && toolProcessingComplete) {
                        lastProcessedLength = content.length
                        toolProcessingComplete = false
                        
                        // Process tools in a separate coroutine
                        scope.launch {
                            try {
                                val processed = toolHandler.processResponse(content)
                                processedResponse.value = processed
                                toolProcessingComplete = true
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing tools", e)
                                processedResponse.value = content
                                toolProcessingComplete = true
                            }
                        }
                    }
                    
                    // Determine what to show in the UI
                    val contentToShow = if (toolProcessingComplete) {
                        processedResponse.value.ifEmpty { content }
                    } else {
                        content
                    }
                    
                    // Call the original callback
                    onPartialResponse(contentToShow, thinking)
                },
                chatHistory = enhancedChatHistory,
                onComplete = {
                    aiResponseComplete = true
                    scope.launch {
                        // Ensure tool processing is completed
                        while (!toolProcessingComplete) {
                            kotlinx.coroutines.delay(100)
                        }
                        
                        // Final tool processing if needed
                        val finalContent = accumulatedResponse.toString()
                        if (finalContent.length > lastProcessedLength) {
                            try {
                                val processed = toolHandler.processResponse(finalContent)
                                processedResponse.value = processed
                                
                                // Send one final update with the complete content
                                val finalProcessed = processedResponse.value.ifEmpty { finalContent }
                                onPartialResponse(finalProcessed, null)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in final tool processing", e)
                                processedResponse.value = finalContent
                                
                                // Even with an error, send the complete content
                                onPartialResponse(finalContent, null)
                            }
                        } else {
                            // No new content, but still ensure we send the complete content one last time
                            val finalProcessed = processedResponse.value.ifEmpty { finalContent }
                            onPartialResponse(finalProcessed, null)
                        }
                        
                        // Call original onComplete
                        onComplete()
                    }
                }
            )
        }
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