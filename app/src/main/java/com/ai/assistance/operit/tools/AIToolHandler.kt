package com.ai.assistance.operit.tools

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolExecutionProgress
import com.ai.assistance.operit.data.model.ToolExecutionState
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import com.ai.assistance.operit.ui.permissions.ToolCategory
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
    
    // Tool permission system
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)
    
    /**
     * Get the tool permission system for UI use
     */
    fun getToolPermissionSystem(): ToolPermissionSystem {
        return toolPermissionSystem
    }
    
    /**
     * Force refresh permission request state
     * Can be called if permission dialog is not showing
     */
    fun refreshPermissionState(): Boolean {
        return toolPermissionSystem.refreshPermissionRequestState()
    }
    
    // 工具注册的唯一方法 - 提供完整信息的注册
    fun registerTool(
        name: String,
        category: ToolCategory,
        dangerCheck: ((AITool) -> Boolean)? = null,
        descriptionGenerator: ((AITool) -> String)? = null,
        executor: ToolExecutor
    ) {
        // 注册工具的类别
        val wrappedExecutor = object : ToolExecutor {
            override fun invoke(tool: AITool): ToolResult {
                // 创建一个带类别的工具实例（如果需要）
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
            
            override fun getCategory(): ToolCategory {
                return category
            }
        }
        
        availableTools[name] = wrappedExecutor
        
        // 注册危险操作检查（如果提供）
        if (dangerCheck != null) {
            toolPermissionSystem.registerDangerousOperation(name, dangerCheck)
        }
        
        // 注册描述生成器（如果提供）
        if (descriptionGenerator != null) {
            toolPermissionSystem.registerOperationDescription(name, descriptionGenerator)
        }
    }
    
    // 添加重载方法接受函数式接口作为executor的便捷写法
    fun registerTool(
        name: String,
        category: ToolCategory,
        dangerCheck: ((AITool) -> Boolean)? = null,
        descriptionGenerator: ((AITool) -> String)? = null,
        executor: (AITool) -> ToolResult
    ) {
        registerTool(
            name = name,
            category = category,
            dangerCheck = dangerCheck,
            descriptionGenerator = descriptionGenerator,
            executor = ToolExecutor { tool -> executor(tool) }
        )
    }
    
    // Register all default tools
    fun registerDefaultTools() {
        // Initialize the permission system with default rules
        toolPermissionSystem.initializeDefaultRules()

        registerAllTools(this,context)
    }
    
    
    
    // Package manager instance (lazy initialized)
    private var packageManagerInstance: PackageManager? = null
    
    /**
     * Gets or creates the package manager instance
     */
    fun getOrCreatePackageManager(): PackageManager {
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
                        // Add permission check
                        Log.d(TAG, "Starting permission check for tool: ${invocation.tool.name}")
                        
                        // Initialize permission variables
                        var permissionDenied = false
                        
                        try {
                            // Perform permission check
                            val hasPermission = kotlinx.coroutines.runBlocking {
                                toolPermissionSystem.checkToolPermission(invocation.tool)
                            }
                            Log.d(TAG, "Permission check result: ${invocation.tool.name}, result: $hasPermission")
                            
                            if (!hasPermission) {
                                // User denied permission
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
                                        result = StringResultData(""),
                                        error = "Permission denied"
                                    )
                                )
                                
                                // Mark permission as denied due to error
                                permissionDenied = true
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during permission check", e)
                            
                            // Handle errors in permission check
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
                                    result = StringResultData(""),
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
                                result.result.toString()
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
                                result = StringResultData(""),
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
        
        // Then check for regular tools
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
                result = StringResultData(""),
                error = "Tool not found: ${tool.name}"
                    )
                }
        
        // Validate parameters
        val validationResult = executor.validateParameters(tool)
        if (!validationResult.valid) {
            return ToolResult(
                toolName = tool.name,
                    success = false,
                result = StringResultData(""),
                error = validationResult.errorMessage
                )
            }
        
        // Execute the tool
        return executor.invoke(tool)
    }

    // Problem Library Management
    private val problemLibrary = mutableMapOf<String, ProblemRecord>()
    private val problemLibraryFile = "problem_library.json"

    // Make ProblemRecord public so it can be accessed from EnhancedAIService
    data class ProblemRecord(
        val uuid: String,
        val query: String,
        val solution: String,
        val tools: List<String>,
        val summary: String = "", // 添加问题总结字段
        val timestamp: Long = System.currentTimeMillis()
    )

    // Public method for saving problem records
    fun saveProblemRecord(record: ProblemRecord) {
        problemLibrary[record.uuid] = record
        saveProblemLibraryToFile()
        Log.d(TAG, "Problem record saved: ${record.uuid}")
    }

    // 保存问题库到文件
    private fun saveProblemLibraryToFile() {
        try {
            val json = org.json.JSONArray()
            
            problemLibrary.values.forEach { record ->
                val recordJson = org.json.JSONObject().apply {
                    put("uuid", record.uuid)
                    put("query", record.query)
                    put("solution", record.solution)
                    put("summary", record.summary)
                    put("tools", org.json.JSONArray(record.tools))
                    put("timestamp", record.timestamp)
                }
                json.put(recordJson)
            }
            
            val file = java.io.File(context.filesDir, problemLibraryFile)
            file.writeText(json.toString(2))
            Log.d(TAG, "Problem library saved to file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving problem library to file", e)
        }
    }

    // 从文件加载问题库
    private fun loadProblemLibraryFromFile() {
        try {
            val file = java.io.File(context.filesDir, problemLibraryFile)
            if (!file.exists()) {
                Log.d(TAG, "Problem library file does not exist, creating empty library")
                return
            }
            
            val json = org.json.JSONArray(file.readText())
            
            for (i in 0 until json.length()) {
                val recordJson = json.getJSONObject(i)
                val toolsArray = recordJson.getJSONArray("tools")
                val tools = mutableListOf<String>()
                
                for (j in 0 until toolsArray.length()) {
                    tools.add(toolsArray.getString(j))
                }
                
                val record = ProblemRecord(
                    uuid = recordJson.getString("uuid"),
                    query = recordJson.getString("query"),
                    solution = recordJson.getString("solution"),
                    summary = recordJson.optString("summary", ""),
                    tools = tools,
                    timestamp = recordJson.getLong("timestamp")
                )
                
                problemLibrary[record.uuid] = record
            }
            
            Log.d(TAG, "Problem library loaded from file: ${problemLibrary.size} records")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading problem library from file", e)
        }
    }

    // 初始化问题库
    init {
        loadProblemLibraryFromFile()
    }

    fun queryProblemLibrary(query: String): String {
        if (problemLibrary.isEmpty()) {
            return "问题库为空，尚无记录"
        }

        // 使用更智能的匹配方式，提取查询关键词
        val keywords = query.split(Regex("\\s+|,|，|\\.|。"))
            .filter { it.length > 1 }
            .map { it.lowercase() }

        // 如果没有有效关键词，返回所有记录
        if (keywords.isEmpty()) {
            return formatProblemLibraryResults(problemLibrary.values.toList())
        }

        // 按相关性排序的记录
        val scoredRecords = problemLibrary.values.map { record ->
            // 计算记录的相关性分数
            val queryScore = keywords.count { keyword ->
                record.query.lowercase().contains(keyword)
            }
            val summaryScore = keywords.count { keyword ->
                record.summary.lowercase().contains(keyword)
            }
            val toolsScore = keywords.count { keyword ->
                record.tools.any { tool -> tool.lowercase().contains(keyword) }
            }
            
            // 总分 = 查询分 * 2 + 摘要分 * 1.5 + 工具分 * 1
            val totalScore = queryScore * 2.0 + summaryScore * 1.5 + toolsScore
            
            Pair(record, totalScore)
        }
        .filter { it.second > 0 } // 只返回有相关性的记录
        .sortedByDescending { it.second } // 按相关性排序
        .map { it.first }
        .take(5) // 最多返回5条记录

        if (scoredRecords.isEmpty()) {
            return "未找到相关记录"
        }

        return formatProblemLibraryResults(scoredRecords)
    }

    // 格式化问题库查询结果
    private fun formatProblemLibraryResults(records: List<ProblemRecord>): String {
        val result = StringBuilder()
        result.appendLine("找到 ${records.size} 条相关记录:")
        
        records.forEach { record ->
            result.appendLine("\nUUID: ${record.uuid}")
            
            // 优先显示摘要，如果没有则显示原始查询
            if (record.summary.isNotEmpty()) {
                result.appendLine("摘要: ${record.summary}")
            } else {
                result.appendLine("问题: ${record.query}")
            }
            
            // 显示使用的工具
            result.appendLine("使用工具: ${record.tools.joinToString(", ")}")
            
            // 显示时间
            result.appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(record.timestamp))}")
        }

        return result.toString()
    }
    
    /**
     * 获取所有问题记录
     * @return 问题记录列表
     */
    fun getAllProblemRecords(): List<ProblemRecord> {
        return problemLibrary.values.toList()
    }
    
    /**
     * 搜索问题库
     * @param query 搜索关键词
     * @return 匹配的问题记录列表
     */
    fun searchProblemLibrary(query: String): List<ProblemRecord> {
        if (problemLibrary.isEmpty()) {
            return emptyList()
        }

        // 提取查询关键词
        val keywords = query.split(Regex("\\s+|,|，|\\.|。"))
            .filter { it.length > 1 }
            .map { it.lowercase() }

        // 如果没有有效关键词，返回所有记录
        if (keywords.isEmpty()) {
            return problemLibrary.values.toList()
        }

        // 按相关性排序的记录
        return problemLibrary.values.map { record ->
            // 计算记录的相关性分数
            val queryScore = keywords.count { keyword ->
                record.query.lowercase().contains(keyword)
            }
            val summaryScore = keywords.count { keyword ->
                record.summary.lowercase().contains(keyword)
            }
            val toolsScore = keywords.count { keyword ->
                record.tools.any { tool -> tool.lowercase().contains(keyword) }
            }
            
            // 总分 = 查询分 * 2 + 摘要分 * 1.5 + 工具分 * 1
            val totalScore = queryScore * 2.0 + summaryScore * 1.5 + toolsScore
            
            Pair(record, totalScore)
        }
        .filter { it.second > 0 } // 只返回有相关性的记录
        .sortedByDescending { it.second } // 按相关性排序
        .map { it.first }
    }
    
    /**
     * 删除问题记录
     * @param uuid 问题记录的UUID
     * @return 是否删除成功
     */
    fun deleteProblemRecord(uuid: String): Boolean {
        val removed = problemLibrary.remove(uuid) != null
        if (removed) {
            saveProblemLibraryToFile()
            Log.d(TAG, "Problem record deleted: $uuid")
        }
        return removed
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
     * Get the tool's category, default to UI_AUTOMATION as highest security level
     */
    fun getCategory(): ToolCategory {
        return ToolCategory.UI_AUTOMATION
    }
} 