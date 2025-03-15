package com.ai.assistance.operit.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

/**
 * Represents a tool parameter in an AI tool
 */
@Serializable
data class ToolParameter(
    val name: String,
    val value: String
)

/**
 * Represents a tool that can be used by the AI
 */
@Serializable
data class AITool(
    val name: String,
    val parameters: List<ToolParameter> = emptyList(),
    val description: String = ""
)

/**
 * Represents a tool invocation that was extracted from an AI response
 */
@Serializable
data class ToolInvocation(
    val tool: AITool,
    val rawText: String,
    @Contextual
    val responseLocation: IntRange // Where in the response this tool invocation was found
)

/**
 * Represents the result of a tool execution
 */
@Serializable
data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val result: String,
    val error: String? = null
)

/**
 * Represents possible states of tool execution
 */
enum class ToolExecutionState {
    IDLE,
    EXTRACTING,
    EXECUTING,
    COMPLETED,
    FAILED
}

/**
 * Represents a progress update during tool execution
 */
data class ToolExecutionProgress(
    val state: ToolExecutionState,
    val tool: AITool? = null,
    val progress: Float = 0f, // 0.0 to 1.0
    val message: String = "",
    val result: ToolResult? = null
)

/**
 * Represents the validation result for tool parameters
 */
@Serializable
data class ToolValidationResult(
    val valid: Boolean,
    val errorMessage: String = ""
) 