package com.ai.assistance.operit.tools.defaultTool

import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.model.ToolResultData
import com.ai.assistance.operit.model.StringResultData
import com.ai.assistance.operit.tools.ConnectionResultData
import com.ai.assistance.operit.tools.ToolExecutor
import kotlinx.serialization.Serializable

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
            
            // Return structured connection data
            val resultData = ConnectionResultData(
                connectionId = CONNECTION_ID,
                isActive = true
            )
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = resultData
            )
        } catch (e: Exception) {
            // Return error result
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Simulation error: ${e.message}"
            )
        }
    }
} 