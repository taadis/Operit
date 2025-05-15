package com.ai.assistance.operit.data.model

/**
 * Represents different states of input processing in the EnhancedAIService
 * This is a public model class that can be used by UI components
 */
sealed class InputProcessingState {
    /**
     * System is idle, not processing any input
     */
    object Idle : InputProcessingState()
    
    /**
     * System is processing input
     * @param message Message describing the current processing action
     */
    data class Processing(val message: String) : InputProcessingState()
    
    /**
     * System is connecting to the AI service
     * @param message Message describing the connection status
     */
    data class Connecting(val message: String) : InputProcessingState()
    
    /**
     * System is receiving response from the AI service
     * @param message Message describing the receiving status
     */
    data class Receiving(val message: String) : InputProcessingState()
    
    /**
     * Processing has completed
     */
    object Completed : InputProcessingState()
    
    /**
     * Processing encountered an error
     * @param message Error message describing what went wrong
     */
    data class Error(val message: String) : InputProcessingState()
} 