package com.ai.assistance.operit.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import com.ai.assistance.operit.permissions.ToolCategory

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
    val description: String = "",
    val category: ToolCategory? = null
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
 * Base interface for all tool result data
 * Each tool should implement its own version of this interface
 */
interface ToolResultData {
    /**
     * Converts the structured data to a string representation
     */
    override fun toString(): String
}

/**
 * Simple boolean result implementation
 */
@Serializable
data class BooleanResultData(val value: Boolean) : ToolResultData {
    override fun toString(): String = value.toString()
}

/**
 * Simple string result implementation
 */
@Serializable
data class StringResultData(val value: String) : ToolResultData {
    override fun toString(): String = value
}

/**
 * Simple integer result implementation
 */
@Serializable
data class IntResultData(val value: Int) : ToolResultData {
    override fun toString(): String = value.toString()
}

/**
 * Represents the result of a tool execution
 */
@Serializable
data class ToolResult(
    val toolName: String,
    val success: Boolean,
    val result: ToolResultData,
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