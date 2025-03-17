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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
            - calculate: Simple calculator that evaluates basic expressions locally. Parameters: expression (e.g. "2+2", "sqrt(16)")
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
            - open_file: 使用系统默认应用打开文件. Parameters: path (文件路径)
            - share_file: 分享文件. Parameters: path (文件路径), title (分享标题，可选，默认为"分享文件")
            - download_file: 从网络下载文件. Parameters: url (文件URL), destination (保存路径)
            
            HTTP Tools:
            - fetch_web_page: 获取网页内容. Parameters: url (网页URL), format (返回格式，可选: "text"或"html"，默认为"text")
            - http_request: 发送HTTP请求. Parameters: url (请求URL), method (请求方法，可选: GET/POST/PUT/DELETE，默认GET), headers (请求头，JSON格式，可选), body (请求体，可选), body_type (请求体类型，可选: "json"/"form"/"text"，默认"json")
            - web_search: Returns pre-defined simulated search results (no actual web access). Parameters: query (the search term)
            

            System Operation Tools (这些工具需要用户授权):
            - get_system_setting: 获取系统设置的值. Parameters: setting (设置名称), namespace (命名空间: system/secure/global, 默认system)
            - modify_system_setting: 修改系统设置的值. Parameters: setting (设置名称), value (设置值), namespace (命名空间: system/secure/global, 默认system)
            - install_app: 安装应用程序. Parameters: apk_path (APK文件路径)
            - uninstall_app: 卸载应用程序. Parameters: package_name (应用包名), keep_data (是否保留数据, 默认false)
            - list_installed_apps: 获取已安装应用列表. Parameters: include_system_apps (是否包含系统应用, 默认false)
            - start_app: 启动应用程序. Parameters: package_name (应用包名), activity (可选的活动名称)
            - stop_app: 停止应用程序. Parameters: package_name (应用包名)
            
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
        currentResponseCallback = onPartialResponse
        currentCompleteCallback = onComplete
        isConversationActive.set(true)
        
        if (chatHistory.isEmpty()) {
            conversationHistory.clear()
            clearReferences()
            cancelAllToolExecutions()
            streamBuffer.clear()
            roundManager.initializeNewConversation()
        }
        
        // Show processing input feedback
        val processedInput = processUserInput(message)
        val enhancedChatHistory = prepareConversationHistory(chatHistory, processedInput)
        
        // Show connecting to AI feedback
        _inputProcessingState.value = InputProcessingState.Connecting("Connecting to AI service...")
        
        withContext(Dispatchers.IO) {
            aiService.sendMessage(
                message = processedInput,
                onPartialResponse = { content, thinking ->
                    // First response received, update to receiving state
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
                    // Update the connection status in the UI
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
                enhancedChatHistory.add(Pair("system", SYSTEM_PROMPT))
                conversationHistory.clear()
                conversationHistory.addAll(enhancedChatHistory)
            } else if (chatHistory.isNotEmpty()) {
                // 处理传入的聊天历史，确保角色映射正确
                enhancedChatHistory.addAll(ChatUtils.mapChatHistoryToStandardRoles(chatHistory))
                
                if (!enhancedChatHistory.any { it.first == "system" }) {
                    enhancedChatHistory.add(0, Pair("system", SYSTEM_PROMPT))
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
        isConversationActive.set(false)
        cancelAllToolExecutions()
        roundManager.clearContent()
        Log.d(TAG, "Conversation canceled - content pool cleared")
        
        currentCompleteCallback?.invoke()
        currentResponseCallback = null
        currentCompleteCallback = null
    }
    
    private fun processContent(
        content: String, 
        thinking: String?,
        onPartialResponse: (content: String, thinking: String?) -> Unit,
        chatHistory: List<Pair<String, String>>,
        isFollowUp: Boolean
    ) {
        // 更新缓冲区内容
        streamBuffer.replace(0, streamBuffer.length, content)
        
        // 检测是否存在完整的工具调用
        
        // 更新round manager中的内容
        val displayContent = roundManager.updateContent(content)
        
        // 更新UI显示
        onPartialResponse(displayContent, thinking)
        
        // val toolInvocations = toolHandler.extractToolInvocations(content)
        // 如果检测到工具调用，且工具调用请求已经完整输出，则立即停止响应，直接调用onComplete
        // if (toolInvocations.isNotEmpty()) {
        //     // 验证工具调用是否完整
        //     val lastToolInvocation = toolInvocations.last()
        //     // 检查最后一个工具调用的结束位置是否接近content的结尾，表示工具调用已经完整输出
        //     val isToolCallComplete = content.length - lastToolInvocation.responseLocation.last < 20
            
        //     if (isToolCallComplete) {
        //         Log.d(TAG, "检测到完整的工具调用，立即停止响应，进入onComplete")
        //         // 如果工具调用完整，则触发aiService中断流式传输
        //         aiService.cancelStreaming()
        //         // 而且直接跳转到onComplete回调
        //         handleStreamingComplete { /* 这里不需要调用外部的onComplete */ }
        //     }
        // }
    }
    
    private fun handleStreamingComplete(onComplete: () -> Unit) {
        toolProcessingScope.launch {
            val content = streamBuffer.toString()
            val displayContent = roundManager.getDisplayContent()
            val responseCallback = currentResponseCallback ?: return@launch
            
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
            
            // 添加当前助手消息到对话历史
            conversationMutex.withLock {
                conversationHistory.add(Pair("assistant", displayContent))
            }
            
            // 主流程：检测和处理工具调用
            val toolInvocations = toolHandler.extractToolInvocations(content)
            
            if (toolInvocations.isNotEmpty() && isConversationActive.get()) {
                // 工具调用处理流程
                handleToolInvocation(toolInvocations, displayContent, responseCallback, onComplete)
            } else {
                // 没有工具调用，标记对话回合结束
                Log.d(TAG, "没有找到工具调用，完成当前回合")
                _inputProcessingState.value = InputProcessingState.Completed
                
                if (isConversationActive.get()) {
                    markConversationCompleted()
                }
                
                onComplete()
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
        
        // 添加过渡状态
        _inputProcessingState.value = InputProcessingState.Processing("工具执行完成，准备进一步处理...")
        
        toolHandler.markToolInvocationProcessed(invocation)
        
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
        
        // 开始新回合
        roundManager.startNewRound()
        Log.d(TAG, "开始AI响应工具结果回合")
        
        // 明确显示我们正在准备发送工具结果给AI
        _inputProcessingState.value = InputProcessingState.Processing("正在准备处理工具执行结果...")
        
        // 添加短暂延迟使状态变化更加明显
        kotlinx.coroutines.delay(300)
        
        // 清空buffer，准备接收新内容
        streamBuffer.clear()
        
        // 直接在当前流程中请求AI响应，保持在complete的主循环中
        withContext(Dispatchers.IO) {
            aiService.sendMessage(
                message = toolResultMessage,
                onPartialResponse = { content, thinking ->
                    // 只处理显示，不执行任何工具逻辑
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