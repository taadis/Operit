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
            if (isConnected) {
                return@withContext "已连接到MCP服务器"
            }

            try {
                // 准备管道
                if (serverConfig.endpoint == "mcp://local") {
                    val localServer =
                        com.ai.assistance.operit.data.mcp.MCPLocalServer.getInstance(context)

                    // 确保本地服务器已启动
                    if (!localServer.isRunning.value) {
                        val started = localServer.startServer()
                        if (!started) {
                            return@withContext "无法启动本地MCP服务器"
                        }
                        delay(500)
                    }

                    // 获取管道
                    var retryCount = 0
                    var pipes: Pair<PipedInputStream, PipedOutputStream>? = null
                    while (pipes == null && retryCount < 3) {
                        pipes = localServer.getServerPipes()
                        if (pipes == null) {
                            retryCount++
                            delay(500)
                        }
                    }

                    if (pipes == null) {
                        return@withContext "无法获取服务器管道"
                    }

                    inputStream = pipes.first
                    outputStream = pipes.second
                } else {
                    inputStream = PipedInputStream(4096)
                    outputStream = PipedOutputStream(inputStream)
                }

                // 创建传输层
                transport = StdioClientTransport(
                    input = inputStream!!,
                    output = PrintStream(outputStream!!, true)
                )

                // 连接到服务器
                var connected = false
                val connectJob = clientScope.launch {
                    try {
                        client.connect(transport!!)
                        connected = true
                    } catch (e: Exception) {
                        Log.e(TAG, "连接异常", e)
                        throw e
                    }
                }

                // 等待连接完成或超时
                if (withTimeoutOrNull(5000L) { connectJob.join(); true } == null) {
                    connectJob.cancel()
                    clientScope.launch { tryCleanupResources() }
                    return@withContext "连接失败: 连接超时"
                }

                if (!connected) {
                    clientScope.launch { tryCleanupResources() }
                    return@withContext "连接失败: 连接过程中断"
                }

                isConnected = true

                // 获取服务器版本信息
                val info = withTimeoutOrNull(3000L) {
                    try {
                        client.getServerVersion()
                    } catch (e: Exception) {
                        null
                    }
                }

                // 获取工具列表并设置超时监控
                delay(500)
                val tools = getTools()

                // 响应消息
                val message =
                    "已成功初始化MCP服务器: ${info?.name ?: "未知"} ${info?.version ?: ""}"

                // 设置连接监控
                if (!serverConfig.keepConnectionOpen) {
                    clientScope.launch {
                        delay(if (serverConfig.endpoint == "mcp://local") 10000L else 30000L)
                        if (isConnected) closeConnection()
                    }
                }

                return@withContext message

            } catch (e: Exception) {
                // 特殊处理不兼容的服务器
                if (e.message?.contains("Server does not support initialize") == true) {
                    isConnected = false
                    return@withContext "无法连接到MCP服务器: 协议不兼容 (${e.message})"
                }

                // 确保资源被清理
                isConnected = false
                clientScope.launch { tryCleanupResources() }

                return@withContext "连接失败: ${e.message}"
            }
        }

    /** 同步初始化 */
    fun initializeSync(): String = runBlocking { initialize() }

    /** 获取可用工具列表 */
    suspend fun getTools(): List<MCPTool> =
        withContext(Dispatchers.IO) {
            if (!isConnected) {
                return@withContext emptyList()
            }

            try {
                // 尝试最多5次，每次使用较短的超时时间
                var retryCount = 0
                var result: ListToolsResult? = null

                while (result == null && retryCount < 5) {
                    // 如果连接已断开，立即返回
                    if (!isConnected) return@withContext emptyList()

                    // 带超时地尝试获取工具列表
                    val timeoutMs = 3000L + (retryCount * 1000L)
                    result = withTimeoutOrNull(timeoutMs) {
                        try {
                            client.request<ListToolsResult>(ListToolsRequest(cursor = null))
                        } catch (e: Exception) {
                            if (e.message?.contains("Transport not started") == true) {
                                delay(1000)
                            }
                            null
                        }
                    }

                    if (result == null) {
                        retryCount++
                        delay(500L + (retryCount * 500L))
                    }
                }

                // 如果所有尝试都失败，使用空结果
                result = result ?: ListToolsResult(tools = emptyList(), nextCursor = null)

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
                return@withContext emptyList()
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
        // 立即标记连接为已关闭
        val wasConnected = isConnected
        isConnected = false

        Log.d(TAG, "正在关闭MCP连接...")

        try {
            if (wasConnected) {
                withTimeoutOrNull(2000L) { client.close() }
            }

            // 关闭输出流 - 先关闭输出流防止管道死锁
            outputStream?.close()
            outputStream = null

            // 短暂等待确保输出流关闭消息传播
            delay(50)

            // 关闭输入流
            inputStream?.close()
            inputStream = null

            // 放弃传输层的引用
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
            if (!isConnected) {
                return@withContext emptyList()
            }

            val tools = getTools()

            try {
                closeConnection()
            } catch (e: Exception) {
                Log.e(TAG, "关闭连接失败", e)
            }

            tools
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
                withTimeoutOrNull(2000L) { client.close() }
            }

            // 先关闭输出流，防止管道死锁
            outputStream?.close()
            outputStream = null

            // 短暂延迟确保输出流关闭消息传播
            delay(50)

            // 然后关闭输入流
            inputStream?.close()
            inputStream = null

            // 清除传输层引用
            transport = null
        } catch (e: Exception) {
            Log.e(TAG, "清理资源失败", e)

            // 强制清除所有引用
            transport = null
            inputStream = null
            outputStream = null
        }
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
