package com.ai.assistance.operit.tools.defaultTool

import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.ToolExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

/**
 * Demonstration tool that shows a synchronous delay in processing
 * This is a simulated tool for educational purposes to demonstrate blocking operations
 */
class BlockingSleepToolExecutor : ToolExecutor {
    
    companion object {
        private const val DEFAULT_DURATION_MS = 1000L // Default 1 second
        private const val MAX_DURATION_MS = 10000L // Maximum 10 seconds
    }
    
    override fun invoke(tool: AITool): ToolResult {
        // Get the duration parameter, or use default if not provided
        val durationParam = tool.parameters.find { it.name == "duration_ms" }?.value
        
        return try {
            // Parse the duration and ensure it's within allowed limits
            val durationMs = if (durationParam != null) {
                val parsed = durationParam.toLongOrNull() ?: DEFAULT_DURATION_MS
                parsed.coerceIn(0, MAX_DURATION_MS) // Limit to reasonable range
            } else {
                DEFAULT_DURATION_MS
            }
            
            // Execute the delay in a blocking manner (since invoke isn't suspend)
            runBlocking {
                delay(durationMs)
            }
            
            // Return success result
            ToolResult(
                toolName = tool.name,
                success = true,
                result = "Demonstration completed: Synchronous delay of $durationMs milliseconds"
            )
        } catch (e: Exception) {
            // Return error result
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Demonstration error: ${e.message}"
            )
        }
    }
} 