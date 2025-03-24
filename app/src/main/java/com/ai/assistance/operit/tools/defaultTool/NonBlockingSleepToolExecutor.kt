package com.ai.assistance.operit.tools.defaultTool

import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.ToolExecutor
import kotlinx.coroutines.*

/**
 * Demonstration tool that shows an asynchronous delay in processing
 * This is a simulated tool for educational purposes to demonstrate non-blocking operations
 */
class NonBlockingSleepToolExecutor : ToolExecutor {
    
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
            
            // Create a deferred result that will be completed after the delay
            val result = CompletableDeferred<ToolResult>()
            
            // Create a new coroutine that doesn't block the caller
            CoroutineScope(Dispatchers.IO).launch {
                // Execute the non-blocking delay
                delay(durationMs)
                
                // Complete with the result when done
                result.complete(
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = "Demonstration completed: Asynchronous delay of $durationMs milliseconds (processing continued in background)"
                    )
                )
            }
            
            // Return an immediate result - do not wait for completion
            ToolResult(
                toolName = tool.name,
                success = true,
                result = "Demonstration started: Asynchronous delay of $durationMs milliseconds initiated."
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