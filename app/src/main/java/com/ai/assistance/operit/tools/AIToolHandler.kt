package com.ai.assistance.operit.tools

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.regex.Pattern
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

/**
 * Handles the extraction and execution of AI tools from responses
 * Supports real-time streaming extraction and execution of tools
 */
class AIToolHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "AIToolHandler"
        
        // Updated regex patterns for tool extraction
        private val TOOL_PATTERN = Pattern.compile(
            "<tool\\s+name=\"([^\"]+)\"(?:\\s+description=\"([^\"]+)\")?[^>]*>([\\s\\S]*?)</tool>", 
            Pattern.DOTALL
        )
        
        private val TOOL_NAME_PATTERN = Pattern.compile(
            "name=\"([^\"]+)\"", 
            Pattern.DOTALL
        )
        
        private val TOOL_PARAM_PATTERN = Pattern.compile(
            "<param\\s+name=\"([^\"]+)\">([\\s\\S]*?)</param>", 
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
        // Weather Tool
        registerTool("weather") { tool ->
            val weatherTool = WeatherTool(context)
            weatherTool.invoke(tool)
        }
        
        // Demo calculator tool
        registerTool("calculate") { tool ->
            val expression = tool.parameters.find { it.name == "expression" }?.value ?: ""
            try {
                val result = Calculator.evalExpression(expression)
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
        
        // Web Search Tool
        registerTool("web_search") { tool ->
            val webSearchTool = WebSearchTool(context)
            webSearchTool.invoke(tool)
        }
        
        // Sleep tool
        registerTool("sleep") { tool ->
            val sleepExecutor = BlockingSleepToolExecutor()
            sleepExecutor.invoke(tool)
        }
        
        // Simulated connection ID tool
        registerTool("device_info") { tool ->
            val deviceInfoExecutor = ConnectionToolExecutor()
            deviceInfoExecutor.invoke(tool)
        }
        
        // File System Tools
        val fileSystemTools = FileSystemTools(context)
        
        // List directory contents
        registerTool("list_files") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.listFiles(tool)
            }
        }
        
        // Read file content
        registerTool("read_file") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.readFile(tool)
            }
        }
        
        // Write to file
        registerTool("write_file") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.writeFile(tool)
            }
        }
        
        // Delete file/directory
        registerTool("delete_file") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.deleteFile(tool)
            }
        }
        
        // Check if file exists
        registerTool("file_exists") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.fileExists(tool)
            }
        }
        
        // Move/rename file or directory
        registerTool("move_file") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.moveFile(tool)
            }
        }
        
        // Copy file or directory
        registerTool("copy_file") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.copyFile(tool)
            }
        }
        
        // Create directory
        registerTool("make_directory") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.makeDirectory(tool)
            }
        }
        
        // Search for files
        registerTool("find_files") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.findFiles(tool)
            }
        }
        
        // Get file information
        registerTool("file_info") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.fileInfo(tool)
            }
        }
        
        // Compress files/directories
        registerTool("zip_files") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.zipFiles(tool)
            }
        }
        
        // Extract zip files
        registerTool("unzip_files") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.unzipFiles(tool)
            }
        }
        
        // 打开文件
        registerTool("open_file") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.openFile(tool)
            }
        }
        
        // 分享文件
        registerTool("share_file") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.shareFile(tool)
            }
        }
        
        // 下载文件
        registerTool("download_file") { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.downloadFile(tool)
            }
        }
        
        // HTTP网络请求工具
        val httpTools = HttpTools(context)
        
        // 获取网页内容
        registerTool("fetch_web_page") { tool ->
            kotlinx.coroutines.runBlocking {
                httpTools.fetchWebPage(tool)
            }
        }
        
        // 发送HTTP请求
        registerTool("http_request") { tool ->
            kotlinx.coroutines.runBlocking {
                httpTools.httpRequest(tool)
            }
        }
        
        // 系统操作工具
        val systemOperationTools = SystemOperationTools(context)
        
        // 修改系统设置
        registerTool("modify_system_setting") { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.modifySystemSetting(tool)
            }
        }
        
        // 获取系统设置
        registerTool("get_system_setting") { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.getSystemSetting(tool)
            }
        }
        
        // 安装应用
        registerTool("install_app") { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.installApp(tool)
            }
        }
        
        // 卸载应用
        registerTool("uninstall_app") { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.uninstallApp(tool)
            }
        }
        
        // 获取已安装应用列表
        registerTool("list_installed_apps") { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.listInstalledApps(tool)
            }
        }
        
        // 启动应用
        registerTool("start_app") { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.startApp(tool)
            }
        }
        
        // 停止应用
        registerTool("stop_app") { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.stopApp(tool)
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
            val toolContent = matcher.group(3) ?: continue  // Updated to use group 3 since group 2 is now the optional description
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