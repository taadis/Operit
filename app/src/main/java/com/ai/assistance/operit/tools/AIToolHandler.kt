package com.ai.assistance.operit.tools

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.model.*
import com.ai.assistance.operit.tools.defaultTool.BlockingSleepToolExecutor
import com.ai.assistance.operit.tools.defaultTool.Calculator
import com.ai.assistance.operit.tools.defaultTool.ConnectionToolExecutor
import com.ai.assistance.operit.tools.defaultTool.FileSystemTools
import com.ai.assistance.operit.tools.defaultTool.HttpTools
import com.ai.assistance.operit.tools.defaultTool.SystemOperationTools
import com.ai.assistance.operit.tools.defaultTool.UITools
import com.ai.assistance.operit.tools.defaultTool.WebSearchTool
import com.ai.assistance.operit.tools.packTool.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.regex.Pattern

/**
 * Handles the extraction and execution of AI tools from responses
 * Supports real-time streaming extraction and execution of tools
 */
class AIToolHandler private constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "AIToolHandler"
        
        @Volatile
        private var INSTANCE: AIToolHandler? = null
        
        fun getInstance(context: Context): AIToolHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AIToolHandler(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Updated and more robust regex patterns for tool extraction with improved robustness
        private val TOOL_PATTERN = Pattern.compile(
            "<tool\\s+name=\\s*\"([^\"]+)\"(?:\\s+description=\\s*\"([^\"]+)\")?[^>]*>([\\s\\S]*?)</tool>", 
            Pattern.DOTALL
        )
        
        private val TOOL_NAME_PATTERN = Pattern.compile(
            "name=\\s*\"([^\"]+)\"", 
            Pattern.DOTALL
        )
        
        private val TOOL_PARAM_PATTERN = Pattern.compile(
            "<param\\s+name=\\s*\"([^\"]+)\">([\\s\\S]*?)</param>", 
            Pattern.DOTALL
        )
        
        // Alternative pattern to match common edge cases with different spacing
        private val ALTERNATIVE_TOOL_PATTERN = Pattern.compile(
            "<tool[\\s\\n]+name\\s*=\\s*[\"']([^\"']+)[\"'](?:[\\s\\n]+description\\s*=\\s*[\"']([^\"']+)[\"'])?[^>]*>([\\s\\S]*?)</tool>", 
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
    
    // 工具权限管理器
    private val toolPermissionManager = ToolPermissionManager(context)
    
    /**
     * 获取工具权限管理器 - 供UI层使用
     */
    fun getToolPermissionManager(): ToolPermissionManager {
        return toolPermissionManager
    }
    
    /**
     * 强制刷新权限请求状态
     * 如果权限对话框未显示，可调用此方法
     */
    fun refreshPermissionState(): Boolean {
        return toolPermissionManager.refreshPermissionRequestState()
    }
    
    // Register a tool executor
    fun registerTool(name: String, executor: ToolExecutor) {
        availableTools[name] = executor
    }
    
    // Register a tool executor with category
    fun registerTool(name: String, category: com.ai.assistance.operit.data.ToolCategory, executor: ToolExecutor) {
        val wrappedExecutor = object : ToolExecutor {
            override fun invoke(tool: AITool): ToolResult {
                // 创建一个包含类别的新工具实例
                val toolWithCategory = if (tool.category == null) {
                    tool.copy(category = category)
                } else {
                    tool
                }
                return executor.invoke(toolWithCategory)
            }
            
            override fun validateParameters(tool: AITool): ToolValidationResult {
                return executor.validateParameters(tool)
            }
            
            override fun getCategory(): com.ai.assistance.operit.data.ToolCategory {
                return category
            }
        }
        availableTools[name] = wrappedExecutor
    }
    
    // Register all default tools
    fun registerDefaultTools() {
        // Register the use_package tool
        registerTool("use_package", com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION) { tool ->
            val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
            val result = getOrCreatePackageManager().usePackage(packageName)
            ToolResult(
                toolName = tool.name,
                success = true,
                result = result
            )
        }
        // Demo calculator tool
        registerTool("calculate", com.ai.assistance.operit.data.ToolCategory.FILE_READ) { tool ->
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
        registerTool("web_search", com.ai.assistance.operit.data.ToolCategory.NETWORK) { tool ->
            val webSearchTool = WebSearchTool(context)
            webSearchTool.invoke(tool)
        }
        
        // Sleep tool
        registerTool("sleep", com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION) { tool ->
            val durationMs = tool.parameters.find { it.name == "duration_ms" }?.value?.toIntOrNull() ?: 1000
            val limitedDuration = durationMs.coerceIn(0, 10000) // Limit to max 10 seconds
            
            Thread.sleep(limitedDuration.toLong())
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = "Slept for ${limitedDuration}ms"
            )
        }
        
        // Device info - returns basic device identifier
        registerTool("device_info", com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION) { tool ->
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = "Device ID: $deviceId"
            )
        }
        
        // File System Tools
        val fileSystemTools = FileSystemTools(context)
        
        // List directory contents
        registerTool("list_files", com.ai.assistance.operit.data.ToolCategory.FILE_READ) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.listFiles(tool)
            }
        }
        
        // Read file content
        registerTool("read_file", com.ai.assistance.operit.data.ToolCategory.FILE_READ) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.readFile(tool)
            }
        }
        
        // Write to file
        registerTool("write_file", com.ai.assistance.operit.data.ToolCategory.FILE_WRITE) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.writeFile(tool)
            }
        }
        
        // Delete file/directory
        registerTool("delete_file", com.ai.assistance.operit.data.ToolCategory.FILE_WRITE) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.deleteFile(tool)
            }
        }
        
        // Check if file exists
        registerTool("file_exists", com.ai.assistance.operit.data.ToolCategory.FILE_READ) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.fileExists(tool)
            }
        }
        
        // Move/rename file or directory
        registerTool("move_file", com.ai.assistance.operit.data.ToolCategory.FILE_WRITE) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.moveFile(tool)
            }
        }
        
        // Copy file or directory
        registerTool("copy_file", com.ai.assistance.operit.data.ToolCategory.FILE_WRITE) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.copyFile(tool)
            }
        }
        
        // Create directory
        registerTool("make_directory", com.ai.assistance.operit.data.ToolCategory.FILE_WRITE) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.makeDirectory(tool)
            }
        }
        
        // Search for files
        registerTool("find_files", com.ai.assistance.operit.data.ToolCategory.FILE_READ) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.findFiles(tool)
            }
        }
        
        // Get file information
        registerTool("file_info", com.ai.assistance.operit.data.ToolCategory.FILE_READ) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.fileInfo(tool)
            }
        }
        
        // Compress files/directories
        registerTool("zip_files", com.ai.assistance.operit.data.ToolCategory.FILE_WRITE) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.zipFiles(tool)
            }
        }
        
        // Extract zip files
        registerTool("unzip_files", com.ai.assistance.operit.data.ToolCategory.FILE_WRITE) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.unzipFiles(tool)
            }
        }
        
        // 打开文件
        registerTool("open_file", com.ai.assistance.operit.data.ToolCategory.FILE_READ) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.openFile(tool)
            }
        }
        
        // 分享文件
        registerTool("share_file", com.ai.assistance.operit.data.ToolCategory.FILE_WRITE) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.shareFile(tool)
            }
        }
        
        // 下载文件
        registerTool("download_file", com.ai.assistance.operit.data.ToolCategory.NETWORK) { tool ->
            kotlinx.coroutines.runBlocking {
                fileSystemTools.downloadFile(tool)
            }
        }
        
        // HTTP network request tool
        val httpTools = HttpTools(context)
        
        // 获取网页内容
        registerTool("fetch_web_page", com.ai.assistance.operit.data.ToolCategory.NETWORK) { tool ->
            kotlinx.coroutines.runBlocking {
                httpTools.fetchWebPage(tool)
            }
        }
        
        // 发送HTTP请求
        registerTool("http_request", com.ai.assistance.operit.data.ToolCategory.NETWORK) { tool ->
            kotlinx.coroutines.runBlocking {
                httpTools.httpRequest(tool)
            }
        }
        
        // 系统操作工具
        val systemOperationTools = SystemOperationTools(context)
        
        // 修改系统设置
        registerTool("modify_system_setting", com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION) { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.modifySystemSetting(tool)
            }
        }
        
        // 获取系统设置
        registerTool("get_system_setting", com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION) { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.getSystemSetting(tool)
            }
        }
        
        // 安装应用
        registerTool("install_app", com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION) { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.installApp(tool)
            }
        }
        
        // 卸载应用
        registerTool("uninstall_app", com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION) { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.uninstallApp(tool)
            }
        }
        
        // 获取已安装应用列表
        registerTool("list_installed_apps", com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION) { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.listInstalledApps(tool)
            }
        }
        
        // 启动应用
        registerTool("start_app", com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION) { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.startApp(tool)
            }
        }
        
        // 停止应用
        registerTool("stop_app", com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION) { tool ->
            kotlinx.coroutines.runBlocking {
                systemOperationTools.stopApp(tool)
            }
        }
        
        // UI Automation Tools via ADB
        val adbUITools = UITools(context)
        
        // Get current page/window information
        registerTool("get_page_info", com.ai.assistance.operit.data.ToolCategory.UI_AUTOMATION) { tool ->
            kotlinx.coroutines.runBlocking {
                adbUITools.getPageInfo(tool)
            }
        }
        
        // Tap at specific coordinates
        registerTool("tap", com.ai.assistance.operit.data.ToolCategory.UI_AUTOMATION) { tool ->
            kotlinx.coroutines.runBlocking {
                adbUITools.tap(tool)
            }
        }
        
        // Click on element by resource ID or class name
        registerTool("click_element", com.ai.assistance.operit.data.ToolCategory.UI_AUTOMATION) { tool ->
            kotlinx.coroutines.runBlocking {
                adbUITools.clickElement(tool)
            }
        }
        
        // Set text in input field
        registerTool("set_input_text", com.ai.assistance.operit.data.ToolCategory.UI_AUTOMATION) { tool ->
            kotlinx.coroutines.runBlocking {
                adbUITools.setInputText(tool)
            }
        }
        
        // Press a specific key
        registerTool("press_key", com.ai.assistance.operit.data.ToolCategory.UI_AUTOMATION) { tool ->
            kotlinx.coroutines.runBlocking {
                adbUITools.pressKey(tool)
            }
        }
        
        // Perform swipe gesture
        registerTool("swipe", com.ai.assistance.operit.data.ToolCategory.UI_AUTOMATION) { tool ->
            kotlinx.coroutines.runBlocking {
                adbUITools.swipe(tool)
            }
        }
        
        // Launch an app by package name
        registerTool("launch_app", com.ai.assistance.operit.data.ToolCategory.UI_AUTOMATION) { tool ->
            kotlinx.coroutines.runBlocking {
                adbUITools.launchApp(tool)
            }
        }
        
        // Perform a combined operation with delay and return the new UI state
        registerTool("combined_operation", com.ai.assistance.operit.data.ToolCategory.UI_AUTOMATION) { tool ->
            kotlinx.coroutines.runBlocking {
                adbUITools.combinedOperation(tool)
            }
        }
        
    }
    
    // Package manager instance (lazy initialized)
    private var packageManagerInstance: PackageManager? = null
    
    /**
     * Gets or creates the package manager instance
     */
    private fun getOrCreatePackageManager(): PackageManager {
        return packageManagerInstance ?: run {
            packageManagerInstance = PackageManager.getInstance(context, this)
            packageManagerInstance!!
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
                        // 添加权限检查
                        Log.d(TAG, "Starting permission check for tool: ${invocation.tool.name}")
                        
                        // 初始化权限变量
                        var permissionDenied = false
                        
                        try {
                            // 执行权限检查
                            val hasPermission = toolPermissionManager.checkToolPermission(invocation.tool)
                            Log.d(TAG, "Permission check result: ${invocation.tool.name}, result: $hasPermission")
                            
                            if (!hasPermission) {
                                // 用户拒绝了权限
                                val errorResult = "Permission denied: Operation '${invocation.tool.name}' was not authorized"
                                Log.d(TAG, "Permission denied: ${invocation.tool.name}, replacing invocation")
                                
                                processedResponse = replaceToolInvocation(
                                    processedResponse, 
                                    invocation, 
                                    errorResult
                                )
                                
                                _toolProgress.value = ToolExecutionProgress(
                                    state = ToolExecutionState.FAILED,
                                    tool = invocation.tool,
                                    message = "Permission denied for tool: ${invocation.tool.name}",
                                    result = ToolResult(
                                        toolName = invocation.tool.name,
                                        success = false,
                                        result = "",
                                        error = "Permission denied"
                                    )
                                )
                                
                                // Mark permission as denied due to error
                                permissionDenied = true
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during permission check", e)
                            
                            // 处理权限检查过程中的错误
                            val errorResult = "Error during permission check: ${e.message}"
                            processedResponse = replaceToolInvocation(
                                processedResponse, 
                                invocation, 
                                errorResult
                            )
                            
                            _toolProgress.value = ToolExecutionProgress(
                                state = ToolExecutionState.FAILED,
                                tool = invocation.tool,
                                message = "Permission check failed: ${e.message}",
                                result = ToolResult(
                                    toolName = invocation.tool.name,
                                    success = false,
                                    result = "",
                                    error = "Permission check error: ${e.message}"
                                )
                            )
                            
                            // Mark permission as denied due to error
                            permissionDenied = true
                        }
                        
                        // If permission not denied, execute the tool
                        if (!permissionDenied) {
                            // Execute the tool
                            val result = executor.invoke(invocation.tool)
                            
                            // Replace the tool invocation with the result
                            processedResponse = replaceToolInvocation(
                                processedResponse, 
                                invocation, 
                                result.result
                            )
                            
                            _toolProgress.value = ToolExecutionProgress(
                                state = ToolExecutionState.COMPLETED,
                                tool = invocation.tool,
                                progress = (index + 1).toFloat() / toolInvocations.size,
                                message = "Tool executed: ${invocation.tool.name}",
                                result = result
                            )
                        }
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
        // Add more comprehensive logging for debugging
        Log.d(TAG, "Extracting tool invocations from response of length: ${response.length}")
        
        val invocations = extractToolInvocationsInternal(response)
        
        Log.d(TAG, "Found ${invocations.size} tool invocations: ${invocations.map { it.tool.name }}")
        
        return invocations
    }
    
    /**
     * Internal implementation of tool invocation extraction
     * Enhanced to better support streaming responses and handle edge cases
     */
    private fun extractToolInvocationsInternal(response: String): List<ToolInvocation> {
        val invocations = mutableListOf<ToolInvocation>()
        
        // First try with the primary pattern
        val matcher = TOOL_PATTERN.matcher(response)
        var matchFound = false
        
        while (matcher.find()) {
            matchFound = true
            val rawText = matcher.group(0) ?: continue
            val toolName = matcher.group(1) ?: continue
            val toolContent = matcher.group(3) ?: continue  // Updated to use group 3 since group 2 is now the optional description
            val start = matcher.start()
            val end = matcher.end()
            
            // Create a unique ID for this tool invocation based on its position and content
            val invocationId = "$toolName:$start:$end"
            
            Log.d(TAG, "Found tool invocation primary pattern: $toolName at position $start-$end")
            
            
            
            // Extract parameters
            val parameters = mutableListOf<ToolParameter>()
            val paramMatcher = TOOL_PARAM_PATTERN.matcher(toolContent)
            
            while (paramMatcher.find()) {
                val paramName = paramMatcher.group(1) ?: continue
                val paramValue = paramMatcher.group(2) ?: continue
                parameters.add(ToolParameter(paramName, paramValue))
                Log.d(TAG, "  Parameter: $paramName = $paramValue")
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
        
        // If no matches found with primary pattern, try alternative pattern
        if (!matchFound) {
            Log.d(TAG, "No matches with primary pattern, trying alternative pattern")
            val altMatcher = ALTERNATIVE_TOOL_PATTERN.matcher(response)
            
            while (altMatcher.find()) {
                val rawText = altMatcher.group(0) ?: continue
                val toolName = altMatcher.group(1) ?: continue
                val toolContent = altMatcher.group(3) ?: continue
                val start = altMatcher.start()
                val end = altMatcher.end()
                
                // Create a unique ID for this tool invocation based on its position and content
                val invocationId = "$toolName:$start:$end"
                
                Log.d(TAG, "Found tool invocation with alternative pattern: $toolName at position $start-$end")
                
                
                
                // Extract parameters (using more flexible parameter pattern)
                val parameters = mutableListOf<ToolParameter>()
                val paramContent = toolContent.trim()
                val paramLines = paramContent.split(Regex("\\s*<param\\s+"))
                
                for (line in paramLines) {
                    if (line.isBlank() || !line.contains("name=")) continue
                    
                    val nameMatch = Regex("name\\s*=\\s*[\"']([^\"']+)[\"']").find(line)
                    val name = nameMatch?.groupValues?.get(1) ?: continue
                    
                    val valueMatch = Regex(">([\\s\\S]*?)</param>").find(line)
                    val value = valueMatch?.groupValues?.get(1)?.trim() ?: continue
                    
                    parameters.add(ToolParameter(name, value))
                    Log.d(TAG, "  Parameter (alt): $name = $value")
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
        }
        
        return invocations
    }
    
    /**
     * Executes a tool directly
     */
    fun executeTool(tool: AITool): ToolResult {
        val executor = availableTools[tool.name]
        
        if (executor == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Tool not found: ${tool.name}"
            )
        }
        
        // Validate parameters
        val validationResult = executor.validateParameters(tool)
        if (!validationResult.valid) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = validationResult.errorMessage
            )
        }
        
        // Execute the tool
        return executor.invoke(tool)
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
    
    /**
     * 获取工具的类别，默认返回UI_AUTOMATION作为最高安全级别
     */
    fun getCategory(): com.ai.assistance.operit.data.ToolCategory {
        return com.ai.assistance.operit.data.ToolCategory.UI_AUTOMATION
    }
} 