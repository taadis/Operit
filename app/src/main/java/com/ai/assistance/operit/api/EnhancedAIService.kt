package com.ai.assistance.operit.api

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.tools.AIToolHandler
import com.ai.assistance.operit.model.ToolExecutionState
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.model.ToolInvocation
import com.ai.assistance.operit.model.ConversationRoundManager
import com.ai.assistance.operit.model.ConversationMarkupManager
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.data.preferencesManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ChatMessage(
    val role: String,
    val content: String
)

class EnhancedAIService(
    apiEndpoint: String,
    apiKey: String,
    modelName: String,
    private val context: Context
) {
    companion object {
        private const val TAG = "EnhancedAIService"
        private val SYSTEM_PROMPT = """
            You are Operit, an all-capable AI assistant, aimed at solving any task presented by the user. You have various tools at your disposal that you can call upon to efficiently complete complex requests. 
            
            When calling a tool, the user will see your response, and then will automatically send the tool results back to you in a follow-up message.
            
            CRITICAL TOOL USAGE RESTRICTION
            - YOU MUST ONLY INVOKE ONE TOOL AT A TIME. This is absolutely critical.
            - NEVER include more than one tool invocation in a single response. The system can only handle one at a time.
            - only call the tool at the end of your response.
            - Keep your responses concise and to the point. Avoid lengthy explanations unless specifically requested.
            - Please stop content output immediately after calling the tool
            - Only respond to the current step. Do NOT repeat all previous content in your new responses.
            - Maintain conversational context naturally without explicitly referencing previous interactions.
            - Do NOT predict or generate content beyond what is explicitly requested by the user.
            - Focus only on addressing the current request without speculating about future interactions.
            
            To use a tool, use this format in your response:
            
            <tool name="tool_name">
            <param name="parameter_name">parameter_value</param>
            </tool>
            
            Available tools:
            - calculate: Enhanced calculator for mathematical and data processing. Use this for:
              * Math operations (e.g., "2+2", "sqrt(16)", "sin(30)", "round(3.7)")
              * Date calculations supporting multiple formats:
                - ISO format: date("2023-01-01")
                - Slash format: date("2023/01/01")
                - US format: date("01/01/2023")
                - European format: date("01.01.2023")
                - With time: date("2023-01-01 12:30:45")
                Examples: 
                - "date_diff(date(\"2023-01-01\"), today())" - days between dates
                - "weekday(date(\"01/15/2023\"))" - get day of week (1=Sunday)
                - "month(date(\"2023.03.15\"))" - get month (3=March)
                - "date_add(today(), 30)" - date 30 days from today
              * Unit conversions (e.g., "convert(32, f, c)", "convert(150, km, mi)")
              * Statistical analysis (e.g., "stats.mean(1,2,3,4,5)", "stats.stdev(10,12,15,18)")
              * Variables and conditional logic (e.g., "x=10; y=20; if(x>y)then(x)else(y)")
              * Financial calculations (e.g., "principal=1000; rate=0.05; principal*(1+rate)^5")
              When users ask about calculations, dates, measurements, or need data processing, PRIORITIZE using this tool instead of explaining steps. It's a safe alternative to eval() with more capabilities.
            - sleep: Demonstration tool that pauses briefly. Parameters: duration_ms (milliseconds, default 1000, max 10000)
            - device_info: Returns basic device identifier for the current app session only. No parameters needed.
            
            File System Tools:
            - list_files: List files in a directory. Parameters: path (e.g. "/sdcard/Download")
            - read_file: Read the content of a file. Parameters: path (file path)
            - write_file: Write content to a file. Parameters: path (file path), content (text to write), append (boolean, default false)
            - delete_file: Delete a file or directory. Parameters: path (target path), recursive (boolean, default false)
            - file_exists: Check if a file or directory exists. Parameters: path (target path)
            - move_file: Move or rename a file or directory. Parameters: source (source path), destination (destination path)
            - copy_file: Copy a file or directory. Parameters: source (source path), destination (destination path), recursive (boolean, default false)
            - make_directory: Create a directory. Parameters: path (directory path), create_parents (boolean, default false)
            - find_files: Search for files matching a pattern. Parameters: path (search path, MUST start with /sdcard/ to avoid system issues), pattern (search pattern, e.g. "*.jpg"), max_depth (optional, controls depth of subdirectory search, -1=unlimited), use_path_pattern (boolean, default false), case_insensitive (boolean, default false)
            - file_info: Get information about a file or directory. Parameters: path (target path)
            - zip_files: Compress files or directories. Parameters: source (path to compress), destination (output zip file)
            - unzip_files: Extract a zip file. Parameters: source (zip file path), destination (extract path)
            - open_file: Open a file using the system's default application. Parameters: path (file path)
            - share_file: Share a file with other applications. Parameters: path (file path), title (optional share title, default "Share File")
            - download_file: Download a file from the internet. Parameters: url (file URL), destination (save path)
            
            HTTP Tools:
            - fetch_web_page: Retrieve web page content. Parameters: url (web page URL), format (return format, optional: "text" or "html", default "text")
            - http_request: Send an HTTP request. Parameters: url (request URL), method (request method, optional: GET/POST/PUT/DELETE, default GET), headers (request headers in JSON format, optional), body (request body, optional), body_type (request body type, optional: "json"/"form"/"text", default "json")
            - web_search: Returns search results for a query. Parameters: query (the search term)
            
            System Operation Tools (these tools require user authorization):
            - get_system_setting: Get the value of a system setting. Parameters: setting (setting name), namespace (namespace: system/secure/global, default system)
            - modify_system_setting: Modify the value of a system setting. Parameters: setting (setting name), value (setting value), namespace (namespace: system/secure/global, default system)
            - install_app: Install an application. Parameters: apk_path (APK file path)
            - uninstall_app: Uninstall an application. Parameters: package_name (app package name), keep_data (whether to keep data, default false)
            - list_installed_apps: Get a list of installed applications. Parameters: include_system_apps (whether to include system apps, default false)
            - start_app: Launch an application. Parameters: package_name (app package name), activity (optional activity name)
            - stop_app: Stop a running application. Parameters: package_name (app package name)
            
            UI Automation Tools:
            - get_page_info: Get information about the current UI screen, including the complete UI hierarchy. Parameters: format (format, optional: "xml" or "json", default "xml"), detail (detail level, optional: "minimal", "summary", or "full", default "summary")
            - tap: Simulate a tap at specific coordinates. Parameters: x (X coordinate), y (Y coordinate)
            - click_element: Click an element identified by resource ID or class name. Parameters: resourceId (element resource ID, optional), className (element class name, optional), index (which matching element to click, 0-based counting, default 0), partialMatch (whether to enable partial matching, default false), at least one identification parameter must be provided
            - set_input_text: Set text in an input field. Parameters: text (text to input)
            - press_key: Simulate a key press. Parameters: keyCode (key code, e.g., "KEYCODE_BACK", "KEYCODE_HOME", etc.)
            - swipe: Simulate a swipe gesture. Parameters: startX (start X coordinate), startY (start Y coordinate), endX (end X coordinate), endY (end Y coordinate), duration (duration in milliseconds, default 300)
            - launch_app: Launch an application. Parameters: packageName (app package name)
            - combined_operation: Execute a UI operation, wait for a specified time, then return the new UI state. Parameters: operation (operation to execute, e.g., "tap 500 800", "click_element resourceId buttonID [index] [partialMatch]", "swipe 500 1000 500 200"), delayMs (wait time in milliseconds, default 1000)
            
            IMPORTANT UI AUTOMATION ADVICE:
            - When dealing with UI interaction issues, prioritize using the combined_operation tool over individual operation tools
            - The combined_operation tool automatically waits for UI updates and returns the new state, solving the problem of needing manual delays and fetching the interface after operations
            - For scenarios like "what happens after clicking" or "how does the interface change after text input", combined_operation is the best choice
            - For example: use "combined_operation" with "operation=tap 500 800" instead of a standalone "tap" command plus delay
            - Or use "combined_operation" with "operation=click_element resourceId buttonID" instead of a standalone "click_element" command
            - When needing to click a specific item in a list, use the index parameter of "click_element", e.g., "click_element resourceId com.example.app:id/list_item 2" to click the 3rd item
            - When multiple elements share the same identifier (such as list items), you can use the "index" parameter to specify which specific element to click
            - When elements cannot be precisely located by ID, you can first use the "tap" tool to click directly using coordinates
            - When launching apps, prioritize using "combined_operation" as this allows you to immediately get interface information
            
            When you finish your task and no longer need any tools, end your response with: [TASK_COMPLETE]
            
            Based on user needs, proactively select the most appropriate tool or combination of tools. For complex tasks, you can break down the problem and use different tools step by step to solve it. After using each tool, clearly explain the execution results and suggest the next steps.

            Always maintain a helpful, informative tone throughout the interaction. If you encounter any limitations or need more details, clearly communicate this to the user before terminating.
        """.trimIndent()
    }
    
    private val aiService = AIService(apiEndpoint, apiKey, modelName)
    private val toolHandler = AIToolHandler(context)
    
    private val _inputProcessingState = MutableStateFlow<InputProcessingState>(InputProcessingState.Idle)
    val inputProcessingState = _inputProcessingState.asStateFlow()
    
    private val _references = MutableStateFlow<List<AiReference>>(emptyList())
    val references = _references.asStateFlow()
    
    private val streamBuffer = StringBuilder()
    private val roundManager = ConversationRoundManager()
    private val isConversationActive = AtomicBoolean(false)
    
    private val toolProcessingScope = CoroutineScope(Dispatchers.IO)
    private val toolExecutionJobs = ConcurrentHashMap<String, Job>()
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val conversationMutex = Mutex()
    
    private var currentResponseCallback: ((content: String, thinking: String?) -> Unit)? = null
    private var currentCompleteCallback: (() -> Unit)? = null
    
    init {
        toolHandler.registerDefaultTools()
    }
    
    fun getToolProgressFlow(): StateFlow<com.ai.assistance.operit.model.ToolExecutionProgress> {
        return toolHandler.toolProgress
    }
    
    fun registerTool(name: String, executor: com.ai.assistance.operit.tools.ToolExecutor) {
        toolHandler.registerTool(name, executor)
    }
    
    suspend fun processUserInput(input: String): String {
        _inputProcessingState.value = InputProcessingState.Processing("Processing input...")
        
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
        }
        
        _inputProcessingState.value = InputProcessingState.Completed
        return input
    }
    
    private fun extractReferences(content: String) {
        val referencePattern = "\\[([^\\]]+)\\]\\((https?://[^\\)]+)\\)".toRegex()
        val matches = referencePattern.findAll(content)
        
        val newReferences = matches.map { matchResult ->
            val (text, url) = matchResult.destructured
            AiReference(text, url)
        }.toList()
        
        if (newReferences.isNotEmpty()) {
            _references.value = newReferences
        }
    }
    
    fun clearReferences() {
        _references.value = emptyList()
    }
    
    suspend fun sendMessage(
        message: String,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>> = emptyList(),
        onComplete: () -> Unit = {}
    ) {
        // 清理任何现有状态
        cancelAllToolExecutions()
        
        // 设置新回调
        currentResponseCallback = onPartialResponse
        currentCompleteCallback = onComplete
        isConversationActive.set(true)
        
        // 如果是新对话，重置所有状态
        if (chatHistory.isEmpty()) {
            Log.d(TAG, "开始新对话 - 重置所有状态")
            conversationHistory.clear()
            clearReferences()
            streamBuffer.clear()
            roundManager.initializeNewConversation()
        }
        
        // 显示处理输入反馈
        val processedInput = processUserInput(message)
        val enhancedChatHistory = prepareConversationHistory(chatHistory, processedInput)
        
        // 显示连接到AI反馈
        _inputProcessingState.value = InputProcessingState.Connecting("Connecting to AI service...")
        
        withContext(Dispatchers.IO) {
            aiService.sendMessage(
                message = processedInput,
                onPartialResponse = { content, thinking ->
                    // 首个响应接收，更新为接收状态
                    if (streamBuffer.isEmpty()) {
                        _inputProcessingState.value = InputProcessingState.Receiving("Receiving AI response...")
                    }
                    
                    processContent(
                        content,
                        thinking,
                        onPartialResponse,
                        enhancedChatHistory,
                        isFollowUp = false
                    )
                },
                chatHistory = enhancedChatHistory,
                onComplete = {
                    handleStreamingComplete(onComplete)
                },
                onConnectionStatus = { status ->
                    // 更新UI中的连接状态
                    when {
                        status.contains("准备连接") || status.contains("正在建立连接") -> 
                            _inputProcessingState.value = InputProcessingState.Connecting(status)
                        status.contains("连接成功") ->
                            _inputProcessingState.value = InputProcessingState.Receiving(status)
                        status.contains("超时") || status.contains("失败") -> 
                            _inputProcessingState.value = InputProcessingState.Processing(status)
                    }
                }
            )
        }
    }
    
    private suspend fun prepareConversationHistory(
        chatHistory: List<Pair<String, String>>, 
        processedInput: String
    ): MutableList<Pair<String, String>> {
        val enhancedChatHistory = mutableListOf<Pair<String, String>>()
        
        conversationMutex.withLock {
            if (chatHistory.isEmpty() && conversationHistory.isEmpty()) {
                // 添加系统提示词
                var systemPrompt = SYSTEM_PROMPT


                
                val userPreferences = preferencesManager.userPreferencesFlow.first()
                if (userPreferences.preferences.isNotEmpty()) {
                    systemPrompt += "\n\nUser preference description: ${userPreferences.preferences}"
                }
                
                enhancedChatHistory.add(Pair("system", systemPrompt))

                
                conversationHistory.clear()
                conversationHistory.addAll(enhancedChatHistory)
            } else if (chatHistory.isNotEmpty()) {
                // 处理传入的聊天历史，确保角色映射正确
                enhancedChatHistory.addAll(ChatUtils.mapChatHistoryToStandardRoles(chatHistory))
                
                if (!enhancedChatHistory.any { it.first == "system" }) {
                    // 添加系统提示词
                    enhancedChatHistory.add(0, Pair("system", SYSTEM_PROMPT))
                    
                    // 添加用户偏好描述
                    val userPreferences = preferencesManager.userPreferencesFlow.first()
                    if (userPreferences.preferences.isNotEmpty()) {
                        enhancedChatHistory.add(1, Pair("system", "User preference description: ${userPreferences.preferences}"))
                    }
                }
                
                conversationHistory.clear()
                conversationHistory.addAll(enhancedChatHistory)
            } else {
                enhancedChatHistory.addAll(conversationHistory)
            }
            
            conversationHistory.add(Pair("user", processedInput))
        }
        return enhancedChatHistory
    }
    
    fun cancelConversation() {
        // 设置对话未激活状态
        isConversationActive.set(false)
        
        // 取消底层AIService的流式传输
        aiService.cancelStreaming()
        
        // 取消所有工具执行
        cancelAllToolExecutions()
        
        // 清理当前对话内容
        roundManager.clearContent()
        Log.d(TAG, "Conversation canceled - content pool cleared")
        
        // 安全地清理对话历史，使用协程以避免阻塞主线程
        toolProcessingScope.launch {
            try {
                // 使用withLock而不是tryLock，确保操作的线程安全性
                conversationMutex.withLock {
                    // 如果最后一条消息是AI的，则移除它，因为它可能是不完整的
                    if (conversationHistory.isNotEmpty() && conversationHistory.last().first == "assistant") {
                        Log.d(TAG, "从对话历史中移除不完整的AI消息")
                        conversationHistory.removeAt(conversationHistory.size - 1)
                    }
                }
                
                Log.d(TAG, "对话历史清理完成")
            } catch (e: Exception) {
                Log.e(TAG, "清理对话历史时出错", e)
            }
        }
        
        // 重置输入处理状态
        _inputProcessingState.value = InputProcessingState.Idle
        
        // 清理引用
        clearReferences()
        
        // 清理回调引用，防止重复触发
        currentResponseCallback = null
        currentCompleteCallback = null
        
        Log.d(TAG, "对话取消完成 - 所有状态已重置")
    }
    
    private fun processContent(
        content: String, 
        thinking: String?,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>>,
        isFollowUp: Boolean
    ) {
        // 先检查对话是否已取消
        if (!isConversationActive.get()) {
            Log.d(TAG, "对话已取消，跳过内容处理")
            return
        }
        
        try {
            // 如果是工具调用后的跟进，使用新的内容回合
            if (isFollowUp) {
                // 使用全新的内容处理
                streamBuffer.replace(0, streamBuffer.length, content)
                val displayContent = roundManager.updateContent(content)
                onPartialResponse(displayContent, thinking)
                return
            }
            
            // 常规消息处理
            streamBuffer.replace(0, streamBuffer.length, content)
            
            // 更新round manager中的内容
            val displayContent = roundManager.updateContent(content)
            
            // 再次检查对话是否活跃
            if (!isConversationActive.get()) {
                Log.d(TAG, "对话在处理过程中被取消，跳过回调")
                return
            }
            
            // 更新UI显示
            onPartialResponse(displayContent, thinking)
        } catch (e: Exception) {
            Log.e(TAG, "处理内容时出错", e)
            // 即使出错也不中断流程
        }
    }
    
    private fun handleStreamingComplete(onComplete: () -> Unit) {
        toolProcessingScope.launch {
            try {
                // 如果对话已经不活跃，直接返回
                if (!isConversationActive.get()) {
                    Log.d(TAG, "对话已取消，跳过流式传输完成处理")
                    onComplete()
                    return@launch
                }
                
                // 获取响应内容
                val content = streamBuffer.toString().trim()
                
                // 如果内容为空，直接结束
                if (content.isEmpty()) {
                    Log.d(TAG, "响应内容为空，跳过处理")
                    onComplete()
                    return@launch
                }
                
                val displayContent = roundManager.getDisplayContent()
                val responseCallback = currentResponseCallback ?: run {
                    Log.d(TAG, "回调已被清除，跳过处理")
                    onComplete()
                    return@launch
                }
                
                // 在complete阶段提取引用
                extractReferences(displayContent)
                
                // 处理任务完成标记
                if (ConversationMarkupManager.containsTaskCompletion(content)) {
                    handleTaskCompletion(displayContent, null, responseCallback)
                    
                    // 确保在任务完成后，状态被正确重置
                    _inputProcessingState.value = InputProcessingState.Completed
                    
                    onComplete()
                    return@launch
                }
                
                // 再次检查对话是否活跃
                if (!isConversationActive.get()) {
                    Log.d(TAG, "对话在提取内容后被取消，停止进一步处理")
                    onComplete()
                    return@launch
                }
                
                // 添加当前助手消息到对话历史
                // 使用try-catch捕获可能的锁异常
                try {
                    conversationMutex.withLock {
                        conversationHistory.add(Pair("assistant", displayContent))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "添加助手消息到历史时出错", e)
                    
                    // 发生错误时仍调用完成回调
                    onComplete()
                    return@launch
                }
                
                // 再次检查对话是否活跃
                if (!isConversationActive.get()) {
                    Log.d(TAG, "对话在处理历史后被取消，停止进一步处理")
                    onComplete()
                    return@launch
                }
                
                // 主流程：检测和处理工具调用 - 增强版
                Log.d(TAG, "开始检测工具调用: 内容长度 = ${content.length}")
                
                // 添加更详细的日志，包括内容的前50个字符，帮助调试
                val contentPreview = if (content.length > 50) content.substring(0, 50) + "..." else content
                Log.d(TAG, "内容预览: $contentPreview")
                
                // 尝试通过两种方式检测工具调用
                val toolInvocations = toolHandler.extractToolInvocations(content)
                
                if (toolInvocations.isNotEmpty() && isConversationActive.get()) {
                    // 工具调用处理流程
                    Log.d(TAG, "找到 ${toolInvocations.size} 个工具调用: ${toolInvocations.map { it.tool.name }}")
                    handleToolInvocation(toolInvocations, displayContent, responseCallback, onComplete)
                } else {
                    // 进行二次检查 - 使用简单的文本匹配来确认是否可能有工具调用标记被错过
                    val possibleToolTag = "<tool\\s+name=".toRegex().find(content)
                    
                    if (possibleToolTag != null) {
                        // 可能存在工具调用但没有被正确解析，进行更宽松的检测
                        Log.w(TAG, "发现可能的工具标签但未提取到有效工具调用，进行二次检查")
                        
                        // 重试工具提取 - 这次清理一下字符串，移除一些可能干扰解析的特殊字符
                        val cleanedContent = content
                            .replace("\r", " ")
                            .replace(Regex("\\s{2,}"), " ")
                        
                        val retriedInvocations = toolHandler.extractToolInvocations(cleanedContent)
                        
                        if (retriedInvocations.isNotEmpty() && isConversationActive.get()) {
                            // 第二次尝试成功提取到工具调用
                            Log.d(TAG, "二次检查找到 ${retriedInvocations.size} 个工具调用: ${retriedInvocations.map { it.tool.name }}")
                            handleToolInvocation(retriedInvocations, displayContent, responseCallback, onComplete)
                            return@launch
                        } else {
                            Log.w(TAG, "二次检查仍未提取到有效工具调用，可能是格式不符合预期")
                        }
                    }
                    
                    // 没有工具调用，标记对话回合结束
                    Log.d(TAG, "没有找到工具调用，完成当前回合")
                    _inputProcessingState.value = InputProcessingState.Completed
                    
                    if (isConversationActive.get()) {
                        markConversationCompleted()
                    }
                    
                    onComplete()
                }
            } catch (e: Exception) {
                // 捕获任何处理过程中的异常
                Log.e(TAG, "处理流式传输完成时出错", e)
                _inputProcessingState.value = InputProcessingState.Idle
                onComplete() // 确保即使发生错误也调用完成回调
            }
        }
    }
    
    /**
     * 处理工具调用并获取结果 - 作为handleStreamingComplete的子流程
     */
    private suspend fun handleToolInvocation(
        toolInvocations: List<ToolInvocation>,
        displayContent: String,
        responseCallback: (content: String, thinking: String?) -> Unit,
        onComplete: () -> Unit
    ) {
        Log.d(TAG, "完成内容中找到工具调用，开始处理")
        
        // 只处理第一个工具调用，如果有多个则显示警告
        val invocation = toolInvocations.first()
        
        if (toolInvocations.size > 1) {
            Log.w(TAG, "找到多个工具调用 (${toolInvocations.size})，但只处理第一个: ${invocation.tool.name}")
            
            val updatedDisplayContent = roundManager.appendContent(
                ConversationMarkupManager.createMultipleToolsWarning(invocation.tool.name)
            )
            responseCallback(updatedDisplayContent, null)
        }
        
        // 开始执行工具
        _inputProcessingState.value = InputProcessingState.Processing(
            "执行工具: ${invocation.tool.name}"
        )
        
        // 更新UI显示工具执行状态
        val updatedDisplayContent = roundManager.appendContent(
            ConversationMarkupManager.createExecutingToolStatus(invocation.tool.name)
        )
        responseCallback(updatedDisplayContent, null)
        
        // 获取工具执行器并执行
        val executor = toolHandler.getToolExecutor(invocation.tool.name)
        val result: ToolResult
        
        if (executor == null) {
            // 工具不可用处理
            Log.w(TAG, "工具不可用: ${invocation.tool.name}")
            
            val errorDisplayContent = roundManager.appendContent(
                ConversationMarkupManager.createToolNotAvailableError(invocation.tool.name)
            )
            responseCallback(errorDisplayContent, null)
            
            // 创建错误结果
            result = ToolResult(
                toolName = invocation.tool.name,
                success = false,
                result = "",
                error = "工具 '${invocation.tool.name}' 不可用"
            )
        } else {
            // 执行前检查权限
            val toolPermissionManager = toolHandler.getToolPermissionManager()
            val hasPermission = toolPermissionManager.checkToolPermission(invocation.tool)
            
            if (!hasPermission) {
                // 用户拒绝了权限
                Log.w(TAG, "工具权限被拒绝: ${invocation.tool.name}")
                
                val errorDisplayContent = roundManager.appendContent(
                    ConversationMarkupManager.createErrorStatus("权限被拒绝", "操作 '${invocation.tool.name}' 未获得授权")
                )
                responseCallback(errorDisplayContent, null)
                
                // 创建权限拒绝结果
                result = ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = "",
                    error = "Permission denied: Operation was not authorized"
                )
            } else {
                // 执行工具
                result = executeToolSafely(invocation, executor)
                
                // 显示工具执行结果
                val toolResultString = if (result.success) result.result else "${result.error}"
                val resultDisplayContent = roundManager.appendContent(
                    ConversationMarkupManager.createToolResultStatus(
                        invocation.tool.name, 
                        result.success, 
                        toolResultString
                    )
                )
                responseCallback(resultDisplayContent, null)
            }
        }
        
        // 添加过渡状态
        _inputProcessingState.value = InputProcessingState.Processing("工具执行完成，准备进一步处理...")

        
        // 处理工具结果 - 直接在这里继续处理，不调用单独的方法
        // 这是整个循环的关键部分，工具结果处理后直接继续下一轮AI请求
        if (!isConversationActive.get()) {
            Log.d(TAG, "对话不再活跃，不处理工具结果")
            onComplete()
            return
        }
        
        // 工具结果处理和后续AI请求 - 全都在complete回调的流程中
        val toolResultMessage = ConversationMarkupManager.formatToolResultForMessage(result)
        
        // 添加工具结果到对话历史
        conversationMutex.withLock {
            conversationHistory.add(Pair("user", toolResultMessage))
        }
        
        // 获取当前对话历史
        val currentChatHistory = conversationMutex.withLock {
            ChatUtils.mapChatHistoryToStandardRoles(conversationHistory)
        }
        
        // 更新UI显示AI思考状态
        val currentDisplay = currentChatHistory.lastOrNull { it.first == "assistant" }?.second ?: ""
        if (currentDisplay.isNotEmpty()) {
            val updatedContent = ConversationMarkupManager.appendThinkingStatus(currentDisplay)
            responseCallback(updatedContent, null)
        }
        
        // 开始新回合 - 确保处理工具执行后的回复会在新消息中显示
        roundManager.startNewRound()
        streamBuffer.clear() // 清空buffer，确保将创建新的消息
        Log.d(TAG, "开始AI响应工具结果回合")
        
        // 明确显示我们正在准备发送工具结果给AI
        _inputProcessingState.value = InputProcessingState.Processing("正在准备处理工具执行结果...")
        
        // 添加短暂延迟使状态变化更加明显
        kotlinx.coroutines.delay(300)
        
        // 直接在当前流程中请求AI响应，保持在complete的主循环中
        withContext(Dispatchers.IO) {
            aiService.sendMessage(
                message = toolResultMessage,
                onPartialResponse = { content, thinking ->
                    // 只处理显示，不执行任何工具逻辑
                    // 使用新的处理方式，确保这是一个新消息
                    // 注意清空buffer，使得processContent认为这是一个新的消息
                    // 注意清空buffer，使得processContent认为这是一个新的消息
                    processContent(content, thinking, responseCallback, currentChatHistory, isFollowUp = true)
                },
                chatHistory = currentChatHistory,
                onComplete = {
                    handleStreamingComplete {
                        if (!isConversationActive.get()) {
                            currentCompleteCallback?.invoke()
                        }
                    }
                },
                onConnectionStatus = { status ->
                    // 更新工具执行后请求的连接状态
                    when {
                        status.contains("准备连接") || status.contains("正在建立连接") -> 
                            _inputProcessingState.value = InputProcessingState.Connecting(status + " (工具执行后)")
                        status.contains("连接成功") ->
                            _inputProcessingState.value = InputProcessingState.Receiving(status + " (工具执行后)")
                        status.contains("超时") || status.contains("失败") -> 
                            _inputProcessingState.value = InputProcessingState.Processing(status + " (工具执行后)")
                    }
                }
            )
        }
    }
    
    private fun executeToolSafely(
        invocation: ToolInvocation,
        executor: com.ai.assistance.operit.tools.ToolExecutor
    ): ToolResult {
        return try {
            val validationResult = executor.validateParameters(invocation.tool)
            if (!validationResult.valid) {
                ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = "",
                    error = "参数无效: ${validationResult.errorMessage}"
                )
            } else {
                executor.invoke(invocation.tool)
            }
        } catch (e: Exception) {
            Log.e(TAG, "工具执行错误: ${invocation.tool.name}", e)
            ToolResult(
                toolName = invocation.tool.name,
                success = false,
                result = "",
                error = "工具执行错误: ${e.message}"
            )
        }
    }
    
    /**
     * 处理任务完成逻辑
     */
    private fun handleTaskCompletion(content: String, thinking: String?, onPartialResponse: (content: String, thinking: String?) -> Unit) {
        // 标记对话完成
        isConversationActive.set(false)
        
        // 清理任务完成标记
        val cleanedContent = ConversationMarkupManager.createTaskCompletionContent(content)
        
        // 清理内容池
        roundManager.clearContent()
        
        // 添加：确保更新输入处理状态为已完成
        _inputProcessingState.value = InputProcessingState.Completed
        
        Log.d(TAG, "任务完成 - 内容池已清理，输入处理状态已更新为Completed")
        
        // 更新UI
        onPartialResponse(cleanedContent, thinking)
        
        // 更新对话历史
        toolProcessingScope.launch {
            conversationMutex.withLock {
                conversationHistory.add(Pair("assistant", cleanedContent))
            }
            
            // 调用完成回调
            currentCompleteCallback?.invoke()
        }
    }
    
    /**
     * 标记对话完成
     */
    private fun markConversationCompleted() {
        isConversationActive.set(false)
        roundManager.clearContent()
        _inputProcessingState.value = InputProcessingState.Completed
        Log.d(TAG, "标记对话完成 - 内容池已清理")
    }
    
    /**
     * 取消所有工具执行
     */
    private fun cancelAllToolExecutions() {
        toolProcessingScope.coroutineContext.cancelChildren()
    }
    
    /**
     * 直接分析用户偏好，不通过 sendMessage，不显示在聊天界面
     * @param conversationHistory 用户的对话历史
     * @param onResult 分析结果回调
     */
    suspend fun analyzeUserPreferences(
        conversationHistory: List<Pair<String, String>>,
        onResult: (String) -> Unit
    ) {
        try {
            // 获取当前的用户偏好
            val currentPreferences = preferencesManager.userPreferencesFlow.first()
            
            val systemPrompt = """
                你是一个用户偏好分析专家。请基于用户的对话历史和当前的AI印象偏好，分析并更新用户的偏好描述。
                
                当前的AI印象偏好：
                ${if (currentPreferences.preferences.isNotEmpty()) currentPreferences.preferences else "暂无偏好信息"}
                
                请基于新的对话历史，对上述偏好进行更新和补充。更新时请注意：
                1. 保持已有偏好的合理部分
                2. 根据新的对话内容补充或修改相关描述
                3. 确保描述自然流畅，不超过100字
                4. 重点关注：
                   - 提问风格偏好
                   - 个人信息（如有提及）
                   - 家庭信息（如有提及）
                   - 社交信息（如有提及）
                   - 购物习惯（如有提及）
                
                请提供一个自然的、符合用户特点的总结。注意：请基于用户真实表现出的特点进行分析，而不是臆测或过度推断。
            """.trimIndent()
            
            // 构建请求体
            val messages = mutableListOf<Pair<String, String>>()
            messages.add(Pair("system", systemPrompt))
            
            // 添加用户对话历史作为分析材料
            val analysisMessage = StringBuilder("以下是用户的对话历史，请分析其中的偏好特点：\n\n")
            for ((role, content) in conversationHistory) {
                if (role == "user") {
                    analysisMessage.append("用户: $content\n")
                } else if (role == "assistant") {
                    analysisMessage.append("助手: $content\n")
                }
            }
            
            messages.add(Pair("user", analysisMessage.toString()))
            
            // 收集分析结果
            val result = StringBuilder()
            
            // 直接调用 aiService，不经过 sendMessage
            withContext(Dispatchers.IO) {
                aiService.sendMessage(
                    message = analysisMessage.toString(),
                    onPartialResponse = { content, _ ->
                        // 只保存内容，不处理思考过程
                        result.clear()
                        result.append(content)
                    },
                    chatHistory = messages,
                    onComplete = {
                        Log.d(TAG, "用户偏好分析完成")
                        // 返回最终分析结果
                        val finalResult = result.toString().trim()
                        onResult(finalResult)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "用户偏好分析失败", e)
            // 出错时返回空结果
            onResult("")
        }
    }
}

sealed class InputProcessingState {
    object Idle : InputProcessingState()
    data class Processing(val message: String) : InputProcessingState()
    data class Connecting(val message: String) : InputProcessingState()
    data class Receiving(val message: String) : InputProcessingState() 
    object Completed : InputProcessingState()
}

data class AiReference(
    val text: String,
    val url: String
)