package com.ai.assistance.operit.tools

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.regex.Pattern

/**
 * Handles the extraction and execution of AI tools from responses
 */
class AIToolHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "AIToolHandler"
        
        // Regex patterns for tool extraction
        private val TOOL_PATTERN = Pattern.compile(
            "<tool>(.*?)</tool>", 
            Pattern.DOTALL
        )
        
        private val TOOL_NAME_PATTERN = Pattern.compile(
            "name=\"(.*?)\"", 
            Pattern.DOTALL
        )
        
        private val TOOL_PARAM_PATTERN = Pattern.compile(
            "<param\\s+name=\"(.*?)\">(.*?)</param>", 
            Pattern.DOTALL
        )
    }
    
    // Tool execution state
    private val _toolProgress = MutableStateFlow(
        ToolExecutionProgress(state = ToolExecutionState.IDLE)
    )
    val toolProgress: StateFlow<ToolExecutionProgress> = _toolProgress.asStateFlow()
    
    // Available tools registry
    private val availableTools = mutableMapOf<String, ToolExecutor>()
    
    // Register a tool executor
    fun registerTool(name: String, executor: ToolExecutor) {
        availableTools[name] = executor
    }
    
    // Register all default tools
    fun registerDefaultTools() {
        // Weather tool example
        registerTool("weather") { tool ->
            ToolResult(
                toolName = tool.name,
                success = true,
                result = "The current weather is sunny, 25°C"
            )
        }
        
        // Calculator tool example
        registerTool("calculate") { tool ->
            val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
            try {
                val result = evalExpression(expression)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Result: $result"
                )
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Error calculating: ${e.message}"
                )
            }
        }
        
        // Web search tool example
        registerTool("web_search") { tool ->
            val query = tool.parameters.find { it.name == "query" }?.value ?: ""
            ToolResult(
                toolName = tool.name,
                success = true,
                result = "Search results for '$query':\n- Result 1\n- Result 2\n- Result 3"
            )
        }
    }
    
    // Simple expression evaluator (for demonstration)
    private fun evalExpression(expression: String): Double {
        // This is a simplified example - in a real app you'd use a proper expression evaluator
        return when {
            "+" in expression -> {
                val parts = expression.split("+")
                parts[0].trim().toDouble() + parts[1].trim().toDouble()
            }
            "-" in expression -> {
                val parts = expression.split("-")
                parts[0].trim().toDouble() - parts[1].trim().toDouble()
            }
            "*" in expression -> {
                val parts = expression.split("*")
                parts[0].trim().toDouble() * parts[1].trim().toDouble()
            }
            "/" in expression -> {
                val parts = expression.split("/")
                parts[0].trim().toDouble() / parts[1].trim().toDouble()
            }
            else -> expression.trim().toDouble()
        }
    }
    
    /**
     * Process the AI response to extract and execute tools
     * @param response The AI response text
     * @return The processed response with tool results inserted
     */
    suspend fun processResponse(response: String): String {
        _toolProgress.value = ToolExecutionProgress(
            state = ToolExecutionState.EXTRACTING,
            message = "Extracting tools from response..."
        )
        
        try {
            // Extract tool invocations
            val toolInvocations = extractToolInvocations(response)
            
            if (toolInvocations.isEmpty()) {
                _toolProgress.value = ToolExecutionProgress(
                    state = ToolExecutionState.COMPLETED,
                    message = "No tools to execute"
                )
                return response
            }
            
            var processedResponse = response
            
            // Execute each tool and replace the invocation with the result
            toolInvocations.forEachIndexed { index, invocation ->
                _toolProgress.value = ToolExecutionProgress(
                    state = ToolExecutionState.EXECUTING,
                    tool = invocation.tool,
                    progress = index.toFloat() / toolInvocations.size,
                    message = "Executing tool: ${invocation.tool.name}..."
                )
                
                // Find the executor for this tool
                val executor = availableTools[invocation.tool.name]
                
                if (executor == null) {
                    val errorResult = "Tool '${invocation.tool.name}' is not available"
                    processedResponse = replaceToolInvocation(
                        processedResponse, 
                        invocation, 
                        errorResult
                    )
                } else {
                    try {
                        // Execute the tool
                        val result = executor.invoke(invocation.tool)
                        
                        // Replace the tool invocation with the result
                        processedResponse = replaceToolInvocation(
                            processedResponse, 
                            invocation, 
                            if (result.success) result.result else "Tool execution failed: ${result.error}"
                        )
                        
                        _toolProgress.value = ToolExecutionProgress(
                            state = ToolExecutionState.COMPLETED,
                            tool = invocation.tool,
                            progress = (index + 1).toFloat() / toolInvocations.size,
                            message = "Tool executed: ${invocation.tool.name}",
                            result = result
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing tool: ${invocation.tool.name}", e)
                        
                        processedResponse = replaceToolInvocation(
                            processedResponse, 
                            invocation, 
                            "Error executing tool: ${e.message}"
                        )
                        
                        _toolProgress.value = ToolExecutionProgress(
                            state = ToolExecutionState.FAILED,
                            tool = invocation.tool,
                            message = "Tool execution failed: ${e.message}",
                            result = ToolResult(
                                toolName = invocation.tool.name,
                                success = false,
                                result = "",
                                error = e.message
                            )
                        )
                    }
                }
            }
            
            _toolProgress.value = ToolExecutionProgress(
                state = ToolExecutionState.COMPLETED,
                progress = 1.0f,
                message = "All tools executed successfully"
            )
            
            return processedResponse
        } catch (e: Exception) {
            Log.e(TAG, "Error processing tools", e)
            
            _toolProgress.value = ToolExecutionProgress(
                state = ToolExecutionState.FAILED,
                message = "Failed to process tools: ${e.message}"
            )
            
            return "$response\n\nError processing tools: ${e.message}"
        }
    }
    
    /**
     * Extract tool invocations from the AI response
     */
    private fun extractToolInvocations(response: String): List<ToolInvocation> {
        val invocations = mutableListOf<ToolInvocation>()
        val matcher = TOOL_PATTERN.matcher(response)
        
        while (matcher.find()) {
            val toolContent = matcher.group(1) ?: continue
            val toolNameMatcher = TOOL_NAME_PATTERN.matcher(toolContent)
            
            if (toolNameMatcher.find()) {
                val toolName = toolNameMatcher.group(1) ?: continue
                
                // Extract parameters
                val parameters = mutableListOf<ToolParameter>()
                val paramMatcher = TOOL_PARAM_PATTERN.matcher(toolContent)
                
                while (paramMatcher.find()) {
                    val paramName = paramMatcher.group(1) ?: continue
                    val paramValue = paramMatcher.group(2) ?: continue
                    parameters.add(ToolParameter(paramName, paramValue))
                }
                
                val tool = AITool(
                    name = toolName,
                    parameters = parameters
                )
                
                invocations.add(
                    ToolInvocation(
                        tool = tool,
                        rawText = matcher.group(0) ?: "",
                        responseLocation = matcher.start()..matcher.end()
                    )
                )
            }
        }
        
        return invocations
    }
    
    /**
     * Replace a tool invocation in the response with its result
     */
    private fun replaceToolInvocation(
        response: String,
        invocation: ToolInvocation,
        result: String
    ): String {
        val before = response.substring(0, invocation.responseLocation.first)
        val after = response.substring(invocation.responseLocation.last + 1)
        
        return "$before\n\n**Tool Result:** $result\n\n$after"
    }
    
    /**
     * Reset the tool execution state
     */
    fun reset() {
        _toolProgress.value = ToolExecutionProgress(state = ToolExecutionState.IDLE)
    }
}

/**
 * Interface for tool executors
 */
fun interface ToolExecutor {
    operator fun invoke(tool: AITool): ToolResult
} 