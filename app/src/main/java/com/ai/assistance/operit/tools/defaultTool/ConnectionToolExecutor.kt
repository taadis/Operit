package com.ai.assistance.operit.tools.defaultTool

import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.ToolExecutor

/**
 * Simulated connection tool that returns a sample connection identifier
 * This is a demonstration tool for educational purposes
 */
class ConnectionToolExecutor : ToolExecutor {
    
    companion object {
        // Demo connection ID for the simulation
        private const val CONNECTION_ID = "14138"
    }
    
    override fun invoke(tool: AITool): ToolResult {
        return try {
            // Simulate a brief processing delay
            Thread.sleep(800)
            
            // Return simulated connection ID
            ToolResult(
                toolName = tool.name,
                success = true,
                result = "Simulated connection established. Demo connection ID: $CONNECTION_ID"
            )
        } catch (e: Exception) {
            // Return error result
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Simulation error: ${e.message}"
            )
        }
    }
} 