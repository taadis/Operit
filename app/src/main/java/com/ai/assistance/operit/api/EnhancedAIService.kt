package com.ai.assistance.operit.api

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.enhanced.EnhancedAIService as EnhancedImplementation
import com.ai.assistance.operit.model.AiReference
import com.ai.assistance.operit.model.InputProcessingState
import kotlinx.coroutines.flow.StateFlow

/**
 * Entry point for the EnhancedAIService
 * This class delegates to the implementation in the enhanced package
 */
class EnhancedAIService(
    apiEndpoint: String,
    apiKey: String,
    modelName: String,
    private val context: Context
) {
    private val implementation = EnhancedImplementation(apiEndpoint, apiKey, modelName, context)
    
    val inputProcessingState: StateFlow<InputProcessingState> = implementation.inputProcessingState
    val references: StateFlow<List<AiReference>> = implementation.references
    
    /**
     * Get the tool progress flow for UI updates
     */
    fun getToolProgressFlow(): StateFlow<com.ai.assistance.operit.model.ToolExecutionProgress> {
        return implementation.getToolProgressFlow()
    }
    
    /**
     * Process user input
     */
    suspend fun processUserInput(input: String): String {
        return implementation.processUserInput(input)
    }
    
    /**
     * Clear references
     */
    fun clearReferences() {
        implementation.clearReferences()
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
        implementation.sendMessage(message, onPartialResponse, chatHistory, onComplete)
    }
    
    /**
     * Cancel the current conversation
     */
    fun cancelConversation() {
        implementation.cancelConversation()
    }

}