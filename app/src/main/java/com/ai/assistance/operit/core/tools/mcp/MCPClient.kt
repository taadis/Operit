package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.tools.StringResultData
import com.ai.assistance.operit.tools.ToolExecutor
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.PrintStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.kotlinx.mcp.*
import org.jetbrains.kotlinx.mcp.client.Client
import org.jetbrains.kotlinx.mcp.client.ClientOptions
import org.jetbrains.kotlinx.mcp.client.StdioClientTransport

/**
 * Model Context Protocol (MCP) Client
 *
 * 使用官方JetBrains MCP Kotlin SDK实现
 */
class MCPClient(private val context: Context, private val serverConfig: MCPServerConfig) {

    companion object {
        private const val TAG = "MCPClient"
    }

    // Coroutine scope for client operations
    private val clientScope = CoroutineScope(Dispatchers.IO)

    // MCP客户端
    private val client =
            Client(
                    clientInfo = Implementation(name = "operit-mcp-client", version = "1.0.0"),
                    options = ClientOptions()
            )

    // 通信管道
    private var inputStream: PipedInputStream? = null
    private var outputStream: PipedOutputStream? = null
    private var transport: StdioClientTransport? = null

    // 连接状态
    private var isConnected = false

    /** 初始化MCP连接 */
    suspend fun initialize(): String =
            withContext(Dispatchers.IO) {
                try {
                    if (isConnected) {
                        return@withContext "已连接到MCP服务器"
                    }

                    Log.d(TAG, "准备连接到MCP服务器: ${serverConfig.name} (${serverConfig.endpoint})")

                    // 检查是否是本地服务器，如果是则使用共享管道
                    if (serverConfig.endpoint == "mcp://local") {
                        // 获取本地服务器实例
                        val localServer =
                                com.ai.assistance.operit.data.mcp.MCPLocalServer.getInstance(
                                        context
                                )

                        // 确保本地服务器已启动
                        if (!localServer.isRunning.value) {
                            Log.d(TAG, "本地服务器未运行，尝试启动")
                            val started = localServer.startServer()
                            if (!started) {
                                return@withContext "无法启动本地MCP服务器"
                            }
                            // 给服务器一点时间初始化
                            delay(1000)
                        }

                        // 从本地服务器获取管道
                        Log.d(TAG, "获取本地服务器管道")
                        var retryCount = 0
                        var pipes: Pair<PipedInputStream, PipedOutputStream>? = null

                        // 尝试最多3次获取管道
                        while (pipes == null && retryCount < 3) {
                            pipes = localServer.getServerPipes()
                            if (pipes == null) {
                                Log.w(TAG, "获取管道失败，尝试重试 (${retryCount + 1}/3)")
                                retryCount++
                                // 稍微延迟一下再重试
                                delay(500)
                            }
                        }

                        if (pipes == null) {
                            return@withContext "无法获取服务器管道"
                        }

                        // 使用服务器提供的管道
                        inputStream = pipes.first
                        outputStream = pipes.second
                        Log.d(TAG, "已获取本地服务器管道")
                    } else {
                        // 非本地服务器，创建标准管道
                        inputStream = PipedInputStream(4096)
                        outputStream = PipedOutputStream(inputStream)
                    }

                    // 创建传输层
                    Log.d(TAG, "创建客户端传输层")
                    transport =
                            StdioClientTransport(
                                    input = inputStream!!,
                                    output = PrintStream(outputStream!!, true)
                            )

                    try {
                        // 尝试连接到服务器
                        Log.d(TAG, "开始连接到MCP服务器")
                        
                        // 添加连接超时保护
                        var connected = false
                        val connectJob = clientScope.launch {
                            try {
                                client.connect(transport!!)
                                connected = true
                                Log.d(TAG, "MCP连接成功建立")
                            } catch (e: Exception) {
                                Log.e(TAG, "MCP连接过程异常", e)
                                throw e
                            }
                        }
                        
                        // 等待连接完成或超时
                        val timeoutMs = 5000L // 5秒超时
                        val timeout = withTimeoutOrNull(timeoutMs) {
                            connectJob.join()
                            true
                        }
                        
                        if (timeout == null) {
                            // 连接超时
                            Log.e(TAG, "MCP连接超时 (${timeoutMs}ms)")
                            connectJob.cancel()
                            // 清理资源但不阻塞
                            clientScope.launch {
                                tryCleanupResources()
                            }
                            return@withContext "连接失败: 连接超时，可能是服务器未响应"
                        }
                        
                        if (!connected) {
                            // 清理资源但不阻塞
                            clientScope.launch {
                                tryCleanupResources()
                            }
                            return@withContext "连接失败: 连接过程中断"
                        }
                        
                        isConnected = true
                        Log.d(TAG, "成功连接到MCP服务器")

                        // 获取服务器版本信息
                        val versionJob = async {
                            try {
                                client.getServerVersion()
                            } catch (e: Exception) {
                                Log.w(TAG, "获取服务器版本失败", e)
                                null
                            }
                        }
                        
                        // 设置版本获取超时
                        val info = withTimeoutOrNull(3000L) {
                            versionJob.await()
                        } ?: null
                        
                        Log.d(TAG, "获取到服务器版本: ${info?.name ?: "未知"} ${info?.version ?: ""}")
                        
                        // 确保连接和传输层完全初始化
                        // 在主连接流程中获取工具列表，而不是创建新协程
                        try {
                            // 给传输层一点时间完全初始化
                            delay(500)
                            Log.d(TAG, "开始自动获取工具列表（主流程）")
                            val tools = getTools()
                            Log.d(TAG, "自动获取工具列表完成: ${tools.size} 个工具")
                            
                            // 构建并返回连接结果消息
                            val message = "已成功初始化MCP服务器: ${info?.name ?: "未知"} ${info?.version ?: ""}"
                            Log.d(TAG, "连接初始化完成: $message")
                            
                            // 在返回之前确保结果消息不被后续操作修改
                            val finalMessage = message
                            
                            // 如果配置为不保持连接，启动异步关闭过程
                            if (serverConfig.endpoint == "mcp://local" && !serverConfig.keepConnectionOpen) {
                                Log.d(TAG, "在initialize中不立即关闭连接，以便后续操作可以使用")
                                // 让后续操作（如getToolsAndClose）来关闭连接
                                
                                // 启动一个协程用于监控连接，如果长时间不活动则自动关闭
                                clientScope.launch {
                                    Log.d(TAG, "启动连接监控器，空闲超时时间: 10s")
                                    delay(10000) // 10秒超时，较短，因为我们预期后续操作会很快调用
                                    if (isConnected) {
                                        Log.d(TAG, "连接空闲超时，自动关闭")
                                        try {
                                            closeConnection()
                                        } catch (e: Exception) {
                                            Log.e(TAG, "关闭连接时发生异常", e)
                                        }
                                    }
                                }
                            } else if (!serverConfig.keepConnectionOpen) {
                                // 启动一个协程用于监控连接，如果长时间不活动则自动关闭
                                clientScope.launch {
                                    Log.d(TAG, "启动连接监控器，空闲超时时间: 30s")
                                    delay(30000) // 30秒超时
                                    if (isConnected) {
                                        Log.d(TAG, "连接空闲超时，自动关闭")
                                        try {
                                            closeConnection()
                                        } catch (e: Exception) {
                                            Log.e(TAG, "关闭连接时发生异常", e)
                                        }
                                    }
                                }
                            } else {
                                Log.d(TAG, "保持连接开放，供后续操作使用")
                            }
                            
                            // 特别记录最终返回的消息，以便诊断
                            Log.d(TAG, "MCPClient.initialize() 返回: '$finalMessage'")
                            
                            // 返回最终结果消息
                            finalMessage
                        } catch (e: Exception) {
                            Log.e(TAG, "自动获取工具列表失败：${e.message}", e)
                            // 返回连接成功但工具获取失败的消息
                            "已连接到MCP服务器，但工具列表获取失败: ${e.message}"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "连接过程中发生异常", e)
                        // 检查是否是不支持initialize方法的错误
                        if (e.message?.contains("Server does not support initialize") == true) {
                            Log.w(TAG, "服务器不支持initialize方法")

                            // 对于本地服务器，这是一个严重错误，因为我们控制了两端
                            if (serverConfig.endpoint == "mcp://local") {
                                Log.e(TAG, "本地服务器不支持initialize方法，这是不应该发生的")
                            }

                            // 清理资源，但不调用closeConnection()以避免可能的冲突
                            isConnected = false

                            // 由于无法使用标准协议，返回失败
                            "无法连接到MCP服务器: 协议不兼容 (${e.message})"
                        } else {
                            // 其他错误
                            Log.e(TAG, "连接服务器时发生其他错误", e)
                            isConnected = false
                            throw e
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "MCP连接失败", e)
                    // 确保资源已清理
                    if (isConnected) {
                        // 立即标记为已关闭
                        isConnected = false
                        // 异步关闭，避免阻塞
                        clientScope.launch {
                            try {
                                closeConnection()
                            } catch (ex: Exception) {
                                Log.e(TAG, "关闭连接失败", ex)
                            }
                        }
                    } else {
                        // 手动清理资源
                        Log.d(TAG, "清理未连接的资源")
                        // 异步清理，避免阻塞
                        clientScope.launch {
                            try {
                                tryCleanupResources()
                            } catch (ex: Exception) {
                                Log.e(TAG, "清理资源失败", ex)
                            }
                        }
                    }
                    val errorMessage = "连接失败: ${e.message}"
                    Log.e(TAG, "MCPClient.initialize() 返回错误: '$errorMessage'")
                    errorMessage
                }
            }

    /** 同步初始化 */
    fun initializeSync(): String = runBlocking { initialize() }

    /** 获取可用工具列表 */
    suspend fun getTools(): List<MCPTool> =
            withContext(Dispatchers.IO) {
                if (!isConnected) {
                    Log.e(TAG, "客户端未连接")
                    return@withContext emptyList()
                }

                try {
                    Log.d(TAG, "开始获取工具列表")
                    
                    // 尝试最多5次，每次使用较短的超时时间
                    var retryCount = 0
                    var result: ListToolsResult? = null
                    
                    while (result == null && retryCount < 5) {
                        // 添加额外检查确保连接就绪
                        if (!isConnected) {
                            Log.e(TAG, "连接已断开，无法获取工具列表")
                            return@withContext emptyList()
                        }
                        
                        // 添加超时保护 - 使用较短的超时时间，但允许重试
                        val toolsJob = async {
                            try {
                                Log.d(TAG, "发送工具列表请求 (尝试 ${retryCount + 1}/5)")
                                val response = client.request<ListToolsResult>(ListToolsRequest(cursor = null))
                                Log.d(TAG, "收到工具列表响应: ${response.tools.size} 个工具")
                                response
                            } catch (e: Exception) {
                                Log.e(TAG, "获取工具列表过程异常 (尝试 ${retryCount + 1}/5)", e)
                                
                                // 检查是否是传输层未启动的错误
                                if (e.message?.contains("Transport not started") == true) {
                                    Log.w(TAG, "传输层未就绪，等待更长时间后重试")
                                    // 给传输层更多时间初始化
                                    delay(1000)
                                }
                                
                                null
                            }
                        }
                        
                        // 设置获取工具超时（每次递增超时时间）
                        val timeoutMs = 3000L + (retryCount * 1000L)
                        result = withTimeoutOrNull(timeoutMs) {
                            Log.d(TAG, "等待工具列表响应，超时设置: ${timeoutMs}ms")
                            toolsJob.await()
                        }
                        
                        if (result == null) {
                            Log.w(TAG, "获取工具列表超时 (尝试 ${retryCount + 1}/5)")
                            retryCount++
                            
                            // 在重试前等待时间递增
                            val delayMs = 500L + (retryCount * 500L)
                            Log.d(TAG, "等待 ${delayMs}ms 后重试")
                            delay(delayMs)
                        }
                    }
                    
                    // 如果所有尝试都失败，使用空结果
                    if (result == null) {
                        Log.e(TAG, "获取工具列表所有尝试均失败")
                        result = ListToolsResult(tools = emptyList(), nextCursor = null)
                    }
                    
                    if (result.tools.isEmpty()) {
                        Log.w(TAG, "获取到空工具列表")
                    } else {
                        Log.d(TAG, "成功获取到 ${result.tools.size} 个工具")
                    }
                    
                    Log.d(TAG, "处理 ${result.tools.size} 个工具")

                    // 将获取到的工具转换为客户端格式
                    return@withContext result.tools.map { tool ->
                        // 将工具参数转为MCPToolParameter
                        val params =
                                tool.inputSchema.properties.map { (name, schema) ->
                                    when (schema) {
                                        is JsonObject -> {
                                            // 获取描述和类型
                                            val description =
                                                    when (val desc = schema["description"]) {
                                                        is JsonPrimitive -> desc.content
                                                        else -> "Parameter: $name"
                                                    }

                                            val type =
                                                    when (val typeValue = schema["type"]) {
                                                        is JsonPrimitive -> typeValue.content
                                                        else -> "string"
                                                    }

                                            MCPToolParameter(
                                                    name = name,
                                                    description = description,
                                                    type = type,
                                                    required = true
                                            )
                                        }
                                        else -> {
                                            // 如果不是JsonObject，使用默认值
                                    MCPToolParameter(
                                                    name = name,
                                                    description = "Parameter: $name",
                                                    type = "string",
                                                    required = true
                                            )
                                        }
                                    }
                                }

                        MCPTool(
                                name = tool.name,
                                description = tool.description ?: "",
                                parameters = params
                        )
                    }
        } catch (e: Exception) {
                    Log.e(TAG, "获取工具列表失败", e)
                    emptyList()
                }
            }

    /** 同步获取工具列表 */
    fun getToolsSync(): List<MCPTool> = runBlocking { getTools() }

    /** 调用工具 */
    suspend fun invokeTool(toolName: String, parameters: Map<String, String>): ToolResult =
            withContext(Dispatchers.IO) {
                if (!isConnected) {
                    return@withContext ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                            error = "MCP客户端未连接"
            )
        }

        try {
                    // 构建参数JSON对象
                    val argsMap = mutableMapOf<String, JsonElement>()
                    parameters.forEach { (key, value) -> argsMap[key] = JsonPrimitive(value) }
                    val args = JsonObject(argsMap)

                    // 调用工具
                    val response =
                            client.request<CallToolResult>(
                                    CallToolRequest(name = toolName, arguments = args)
                            )

                    // 解析响应
                    val content =
                            response.content.joinToString("\n") { content ->
                                when (content) {
                                    is TextContent -> content.text ?: ""
                                    is EmbeddedResource -> "资源: ${content.resource.uri}"
                                    is ImageContent -> "图片: ${content.mimeType}"
                                    else -> ""
                                }
                            }

                    if (response.isError != true) {
                        ToolResult(
                                toolName = toolName,
                                success = true,
                                result = StringResultData(content)
                        )
                    } else {
                        ToolResult(
                        toolName = toolName,
                        success = false,
                        result = StringResultData(""),
                                error = content
                        )
                    }
        } catch (e: Exception) {
                    Log.e(TAG, "调用工具失败: $toolName", e)
                    ToolResult(
                    toolName = toolName,
                    success = false,
                    result = StringResultData(""),
                            error = "调用失败: ${e.message}"
                    )
                }
            }

    /** 同步调用工具 */
    fun invokeToolSync(toolName: String, parameters: Map<String, String>): ToolResult =
            runBlocking {
                invokeTool(toolName, parameters)
            }

    /** 检查连接状态 */
    suspend fun ping(): Boolean =
            withContext(Dispatchers.IO) {
                if (!isConnected) return@withContext false

                try {
                    client.request<EmptyRequestResult>(PingRequest())
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Ping失败", e)
                    false
                }
            }

    /** 同步检查连接状态 */
    fun pingSync(): Boolean = runBlocking { ping() }

    /** 关闭连接 */
    suspend fun shutdown() =
            withContext(Dispatchers.IO) {
                if (!isConnected) return@withContext
                closeConnection()
            }

    /** 同步关闭连接 */
    fun shutdownSync() = runBlocking { if (isConnected) shutdown() }

    /** 关闭连接的内部方法 */
    private suspend fun closeConnection() {
        // 立即标记连接为已关闭，这样其他线程就不会尝试使用它
        val wasConnected = isConnected
        isConnected = false
        
        Log.d(TAG, "正在关闭MCP连接...")
        
        try {
            if (wasConnected) {
                try {
                    Log.d(TAG, "正在关闭MCP客户端...")
                    // 带超时的客户端关闭
                    withTimeoutOrNull(2000L) {
                        client.close()
                    }
                    Log.d(TAG, "MCP客户端连接已关闭")
                } catch (e: Exception) {
                    Log.e(TAG, "关闭客户端失败", e)
                }
            }

            // 关闭输出流 - 先关闭输出流防止管道死锁
            try {
                Log.d(TAG, "关闭输出流...")
                outputStream?.close()
                outputStream = null
                Log.d(TAG, "输出流已关闭")
            } catch (e: Exception) {
                Log.e(TAG, "关闭输出流失败", e)
            }
            
            // 短暂等待确保输出流关闭消息传播
            delay(50)

            // 关闭输入流
            try {
                Log.d(TAG, "关闭输入流...")
                inputStream?.close()
                inputStream = null
                Log.d(TAG, "输入流已关闭") 
            } catch (e: Exception) {
                Log.e(TAG, "关闭输入流失败", e)
            }
            
            // 只有在完全中断与服务器的连接后，才放弃传输层的引用
            transport = null

            Log.d(TAG, "已关闭MCP连接")
        } catch (e: Exception) {
            Log.e(TAG, "关闭连接失败", e)
            
            // 强制清除所有引用
            transport = null
            inputStream = null
            outputStream = null
        }
    }

    /** 创建工具执行器 */
    fun createToolExecutor(toolName: String): ToolExecutor =
            object : ToolExecutor {
            override fun invoke(tool: AITool): ToolResult {
                val params = tool.parameters.associate { it.name to it.value }
                    return invokeToolSync(toolName, params)
            }

            override fun validateParameters(tool: AITool): ToolValidationResult {
                return ToolValidationResult(valid = true)
        }
    }

    /** 获取工具列表并关闭连接 */
    suspend fun getToolsAndClose(): List<MCPTool> =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "获取工具列表并自动关闭连接")
                    
                    // 确保连接是开放的
                    if (!isConnected) {
                        Log.e(TAG, "尝试获取工具列表时客户端未连接")
                        return@withContext emptyList()
                    }
                    
                    // 获取工具列表
                    val tools = try {
                        // 存储工具而不是重新获取
                        val toolsList = getTools()
                        if (toolsList.isEmpty()) {
                            Log.w(TAG, "获取到空工具列表")
                        } else {
                            Log.d(TAG, "成功获取到 ${toolsList.size} 个工具")
                        }
                        toolsList
                    } catch (e: Exception) {
                        Log.e(TAG, "获取工具列表失败", e)
                        emptyList()
                    }
                    
                    // 获取完工具后关闭连接
                    Log.d(TAG, "工具列表获取完成，正在关闭连接")
                    try {
                        closeConnection()
                    } catch (e: Exception) {
                        Log.e(TAG, "关闭连接失败", e)
                    }
                    
                    // 返回工具列表
                    tools
                } catch (e: Exception) {
                    Log.e(TAG, "获取工具列表并关闭连接时发生异常", e)
                    // 确保连接关闭
                    try {
                        closeConnection()
                    } catch (ex: Exception) {
                        Log.e(TAG, "关闭连接失败", ex)
                    }
                    emptyList()
                }
            }

    /** 同步获取工具列表并关闭连接 */
    fun getToolsAndCloseSync(): List<MCPTool> = runBlocking { getToolsAndClose() }

    /** 尝试清理资源，不抛出异常 */
    private suspend fun tryCleanupResources() {
        Log.d(TAG, "尝试清理MCP客户端资源")
        
        // 立即标记连接为已关闭
        isConnected = false
        
        try {
            // 关闭客户端
            if (client != null) {
                try {
                    Log.d(TAG, "正在关闭MCP客户端...")
                    // 带超时的客户端关闭
                    withTimeoutOrNull(2000L) {
                        client.close()
                    }
                    Log.d(TAG, "MCP客户端连接已关闭")
                } catch (e: Exception) {
                    Log.e(TAG, "关闭客户端失败", e)
                }
            }
            
            // 先关闭输出流，防止管道死锁
            try {
                Log.d(TAG, "关闭输出流...")
                outputStream?.close()
                outputStream = null
                Log.d(TAG, "输出流已关闭")
            } catch (e: Exception) {
                Log.e(TAG, "关闭输出流失败", e)
            }
            
            // 短暂延迟确保输出流关闭消息传播
            delay(50)
            
            // 然后关闭输入流
            try {
                Log.d(TAG, "关闭输入流...")
                inputStream?.close()
                inputStream = null
                Log.d(TAG, "输入流已关闭")
            } catch (e: Exception) {
                Log.e(TAG, "关闭输入流失败", e)
            }
            
            // 只有在完全中断与服务器的连接后，才放弃传输层的引用
            transport = null
            
        } catch (e: Exception) {
            Log.e(TAG, "清理资源失败", e)
            
            // 强制清除所有引用
            transport = null
            inputStream = null
            outputStream = null
        }
        
        Log.d(TAG, "MCP客户端资源清理完成")
    }
}

/** MCP服务器配置 */
@Serializable
data class MCPServerConfig(
        val name: String,
        val endpoint: String,
        val description: String = "",
        val capabilities: List<String> = emptyList(),
        val keepConnectionOpen: Boolean = false
)

/** MCP工具参数 */
@Serializable
data class MCPToolParameter(
        val name: String,
        val description: String,
        val type: String = "string",
        val required: Boolean = false
)

/** MCP工具 */
@Serializable
data class MCPTool(
        val name: String,
        val description: String,
        val parameters: List<MCPToolParameter> = emptyList()
)
