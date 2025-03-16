package com.ai.assistance.operit.tools

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.regex.Pattern
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles the extraction and execution of AI tools from responses
 * Supports real-time streaming extraction and execution of tools
 */
class AIToolHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "AIToolHandler"
        
        // Regex patterns for tool extraction
        private val TOOL_PATTERN = Pattern.compile(
            "<tool\\s+name=\"([^\"]+)\"[^>]*>(.*?)</tool>", 
            Pattern.DOTALL
        )
        
        private val TOOL_NAME_PATTERN = Pattern.compile(
            "name=\"([^\"]+)\"", 
            Pattern.DOTALL
        )
        
        private val TOOL_PARAM_PATTERN = Pattern.compile(
            "<param\\s+name=\"([^\"]+)\">(.*?)</param>", 
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
    
    // Track already processed tool invocations in streaming mode
    private val processedToolInvocations = ConcurrentHashMap<String, Boolean>()
    
    // Register a tool executor
    fun registerTool(name: String, executor: ToolExecutor) {
        availableTools[name] = executor
    }
    
    // Register all default tools
    fun registerDefaultTools() {
        // Simulated weather tool
        registerTool("weather") { tool ->
            ToolResult(
                toolName = tool.name,
                success = true,
                result = "Simulated weather data: Sunny, 25°C"
            )
        }
        
        // Demo calculator tool
        registerTool("calculate") { tool ->
            val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
            try {
                val result = evalExpression(expression)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Calculation result: $result"
                )
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Calculation error: ${e.message}"
                )
            }
        }
        
        // Simulated web search tool
        registerTool("web_search") { tool ->
            val query = tool.parameters.find { it.name == "query" }?.value ?: ""
            ToolResult(
                toolName = tool.name,
                success = true,
                result = "Simulated search results for '$query':\n- Demo result 1\n- Demo result 2\n- Demo result 3"
            )
        }
        
        // Demonstration synchronous delay tool
        registerTool("blocking_sleep") { tool ->
            val sleepExecutor = BlockingSleepToolExecutor()
            sleepExecutor.invoke(tool)
        }
        
        // Demonstration asynchronous delay tool
        registerTool("non_blocking_sleep") { tool ->
            val sleepExecutor = NonBlockingSleepToolExecutor()
            sleepExecutor.invoke(tool)
        }
        
        // Simulated connection ID tool
        registerTool("device_info") { tool ->
            val deviceInfoExecutor = ConnectionToolExecutor()
            deviceInfoExecutor.invoke(tool)
        }
    }
    
    // Simple expression evaluator (for demonstration)
    private fun evalExpression(expression: String): Double {
        // Using Android's built-in expressions evaluator
        // This is a simplified implementation for basic expressions
        try {
            // Clean and prepare the expression
            val preparedExpression = expression.trim()
                .replace("\\s+".toRegex(), "")  // Remove all whitespace
            
            // Handle basic mathematical functions
            return when {
                preparedExpression.contains("sqrt") -> {
                    val value = preparedExpression.substringAfter("sqrt").trim('(', ')')
                    Math.sqrt(evalExpression(value))
                }
                preparedExpression.contains("sin") -> {
                    val value = preparedExpression.substringAfter("sin").trim('(', ')')
                    Math.sin(Math.toRadians(evalExpression(value)))
                }
                preparedExpression.contains("cos") -> {
                    val value = preparedExpression.substringAfter("cos").trim('(', ')')
                    Math.cos(Math.toRadians(evalExpression(value)))
                }
                preparedExpression.contains("tan") -> {
                    val value = preparedExpression.substringAfter("tan").trim('(', ')')
                    Math.tan(Math.toRadians(evalExpression(value)))
                }
                preparedExpression.contains("log") -> {
                    val value = preparedExpression.substringAfter("log").trim('(', ')')
                    Math.log10(evalExpression(value))
                }
                preparedExpression.contains("ln") -> {
                    val value = preparedExpression.substringAfter("ln").trim('(', ')')
                    Math.log(evalExpression(value))
                }
                preparedExpression.equals("pi", ignoreCase = true) -> Math.PI
                preparedExpression.equals("e", ignoreCase = true) -> Math.E
                "+" in preparedExpression -> {
                    // Find the rightmost addition that's not inside parentheses
                    var parenthesesCount = 0
                    var splitIndex = -1
                    
                    for (i in preparedExpression.indices.reversed()) {
                        val char = preparedExpression[i]
                        if (char == ')') parenthesesCount++
                        else if (char == '(') parenthesesCount--
                        else if (char == '+' && parenthesesCount == 0) {
                            splitIndex = i
                            break
                        }
                    }
                    
                    if (splitIndex == -1) {
                        // No suitable '+' found, try to evaluate as is
                        preparedExpression.toDouble()
                    } else {
                        val left = preparedExpression.substring(0, splitIndex)
                        val right = preparedExpression.substring(splitIndex + 1)
                        evalExpression(left) + evalExpression(right)
                    }
                }
                "-" in preparedExpression && !preparedExpression.startsWith("-") -> {
                    // Find the rightmost subtraction that's not inside parentheses
                    var parenthesesCount = 0
                    var splitIndex = -1
                    
                    for (i in preparedExpression.indices.reversed()) {
                        val char = preparedExpression[i]
                        if (char == ')') parenthesesCount++
                        else if (char == '(') parenthesesCount--
                        else if (char == '-' && parenthesesCount == 0 && i > 0) {
                            splitIndex = i
                            break
                        }
                    }
                    
                    if (splitIndex == -1) {
                        // No suitable '-' found, try to evaluate as is
                        preparedExpression.toDouble()
                    } else {
                        val left = preparedExpression.substring(0, splitIndex)
                        val right = preparedExpression.substring(splitIndex + 1)
                        evalExpression(left) - evalExpression(right)
                    }
                }
                "*" in preparedExpression -> {
                    // Find the rightmost multiplication that's not inside parentheses
                    var parenthesesCount = 0
                    var splitIndex = -1
                    
                    for (i in preparedExpression.indices.reversed()) {
                        val char = preparedExpression[i]
                        if (char == ')') parenthesesCount++
                        else if (char == '(') parenthesesCount--
                        else if (char == '*' && parenthesesCount == 0) {
                            splitIndex = i
                            break
                        }
                    }
                    
                    if (splitIndex == -1) {
                        // No suitable '*' found, try to evaluate as is
                        preparedExpression.toDouble()
                    } else {
                        val left = preparedExpression.substring(0, splitIndex)
                        val right = preparedExpression.substring(splitIndex + 1)
                        evalExpression(left) * evalExpression(right)
                    }
                }
                "/" in preparedExpression -> {
                    // Find the rightmost division that's not inside parentheses
                    var parenthesesCount = 0
                    var splitIndex = -1
                    
                    for (i in preparedExpression.indices.reversed()) {
                        val char = preparedExpression[i]
                        if (char == ')') parenthesesCount++
                        else if (char == '(') parenthesesCount--
                        else if (char == '/' && parenthesesCount == 0) {
                            splitIndex = i
                            break
                        }
                    }
                    
                    if (splitIndex == -1) {
                        // No suitable '/' found, try to evaluate as is
                        preparedExpression.toDouble()
                    } else {
                        val left = preparedExpression.substring(0, splitIndex)
                        val right = preparedExpression.substring(splitIndex + 1)
                        evalExpression(left) / evalExpression(right)
                    }
                }
                preparedExpression.startsWith("(") && preparedExpression.endsWith(")") -> {
                    // Remove outer parentheses and evaluate inner expression
                    evalExpression(preparedExpression.substring(1, preparedExpression.length - 1))
                }
                else -> preparedExpression.toDouble() // Simple number
            }
        } catch (e: Exception) {
            // If parsing fails, fall back to basic arithmetic
            return when {
                "+" in expression -> {
                    val parts = expression.split("+")
                    parts[0].trim().toDouble() + parts[1].trim().toDouble()
                }
                "-" in expression && !expression.startsWith("-") -> {
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
            
            return "$response\nError processing tools: ${e.message}"
        }
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
        
        return "$before\n**Tool Result [${invocation.tool.name}]:** \n$result\n$after"
    }
    
    /**
     * Reset the tool execution state
     */
    fun reset() {
        _toolProgress.value = ToolExecutionProgress(state = ToolExecutionState.IDLE)
        processedToolInvocations.clear()
    }
    
    /**
     * Get a registered tool executor by name
     * @param toolName The name of the tool
     * @return The tool executor or null if not found
     */
    fun getToolExecutor(toolName: String): ToolExecutor? {
        return availableTools[toolName]
    }
    
    /**
     * Extract tool invocations from the AI response
     * Public method to be used by EnhancedAIService
     */
    fun extractToolInvocations(response: String): List<ToolInvocation> {
        val invocations = extractToolInvocationsInternal(response)
        return invocations
    }
    
    /**
     * Internal implementation of tool invocation extraction
     * Enhanced to better support streaming responses
     */
    private fun extractToolInvocationsInternal(response: String): List<ToolInvocation> {
        val invocations = mutableListOf<ToolInvocation>()
        val matcher = TOOL_PATTERN.matcher(response)
        
        while (matcher.find()) {
            val rawText = matcher.group(0) ?: continue
            val toolName = matcher.group(1) ?: continue
            val toolContent = matcher.group(2) ?: continue
            val start = matcher.start()
            val end = matcher.end()
            
            // Create a unique ID for this tool invocation based on its position and content
            val invocationId = "$toolName:$start:$end"
            
            // Skip if we've already processed this exact invocation (for streaming mode)
            if (processedToolInvocations.containsKey(invocationId)) {
                continue
            }
            
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
                    rawText = rawText,
                    responseLocation = start..end
                )
            )
        }
        
        return invocations
    }
    
    /**
     * Mark a tool invocation as processed (for streaming mode)
     */
    fun markToolInvocationProcessed(invocation: ToolInvocation) {
        val invocationId = "${invocation.tool.name}:${invocation.responseLocation.first}:${invocation.responseLocation.last}"
        processedToolInvocations[invocationId] = true
    }
}

/**
 * Interface for tool executors
 */
fun interface ToolExecutor {
    operator fun invoke(tool: AITool): ToolResult
    
    /**
     * Validates the parameters of a tool before execution
     * Default implementation always returns valid
     */
    fun validateParameters(tool: AITool): ToolValidationResult {
        return ToolValidationResult(valid = true)
    }
} 