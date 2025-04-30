package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.tools.StringResultData
import com.ai.assistance.operit.tools.ToolExecutor
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP工具执行器
 *
 * 处理MCP工具的调用，类似于已有的PackageToolExecutor
 */
class MCPToolExecutor(private val context: Context, private val mcpManager: MCPManager) :
        ToolExecutor {
    companion object {
        private const val TAG = "MCPToolExecutor"
    }

    override fun invoke(tool: AITool): ToolResult {
        // 从工具名称中提取服务器名称和工具名称
        // 格式：服务器名称:工具名称
        val toolNameParts = tool.name.split(":")
        if (toolNameParts.size < 2) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "无效的MCP工具名称格式，应为 '服务器名称:工具名称'"
            )
        }

        val serverName = toolNameParts[0]
        val actualToolName = toolNameParts.subList(1, toolNameParts.size).joinToString(":")

        // 获取MCP客户端
        val mcpClient = mcpManager.getOrCreateClient(serverName)
        if (mcpClient == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "无法连接到MCP服务器: $serverName"
            )
        }

        // 将AITool参数转换为Map
        val parameters = tool.parameters.associate { it.name to it.value }

        // 调用MCP工具
        val result = mcpClient.invokeTool(actualToolName, parameters)

        // 添加工具名称前缀，使其与调用的格式一致
        return result.copy(toolName = tool.name)
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        // 验证工具名称格式
        val toolNameParts = tool.name.split(":")
        if (toolNameParts.size < 2) {
            return ToolValidationResult(
                    valid = false,
                    errorMessage = "无效的MCP工具名称格式，应为 '服务器名称:工具名称'"
            )
        }

        // 这里可以添加更多验证逻辑，但目前简单返回成功
        return ToolValidationResult(valid = true)
    }
}

/**
 * MCP管理器
 *
 * 管理MCP客户端的创建和缓存
 */
class MCPManager(private val context: Context) {
    companion object {
        private const val TAG = "MCPManager"

        @Volatile private var INSTANCE: MCPManager? = null

        fun getInstance(context: Context): MCPManager {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE ?: MCPManager(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    // 缓存已创建的MCP客户端，避免重复创建
    private val clientCache = ConcurrentHashMap<String, MCPClient>()

    // 缓存服务器配置
    private val serverConfigCache = ConcurrentHashMap<String, MCPServerConfig>()

    /**
     * 检查服务器是否已注册
     *
     * @param serverName 服务器名称
     * @return 如果服务器已注册则返回true
     */
    fun isServerRegistered(serverName: String): Boolean {
        return serverConfigCache.containsKey(serverName)
    }

    /**
     * 获取所有已注册的服务器配置
     *
     * @return 服务器名称到服务器配置的映射
     */
    fun getRegisteredServers(): Map<String, MCPServerConfig> {
        return serverConfigCache.toMap()
    }

    /**
     * 获取或创建MCP客户端
     *
     * @param serverName 服务器名称
     * @return MCP客户端，如果服务器不存在则返回null
     */
    fun getOrCreateClient(serverName: String): MCPClient? {
        // 检查缓存中是否已有客户端
        val cachedClient = clientCache[serverName]
        if (cachedClient != null) {
            return cachedClient
        }

        // 获取服务器配置
        val serverConfig = serverConfigCache[serverName] ?: return null

        // 创建新客户端
        val client = MCPClient(context, serverConfig)
        val initResult = client.initialize()

        if (!initResult.startsWith("已成功初始化")) {
            Log.e(TAG, "MCP客户端初始化失败: $initResult")
            client.shutdown()
            return null
        }

        // 缓存客户端
        clientCache[serverName] = client
        return client
    }

    /**
     * 注册MCP服务器配置
     *
     * @param serverName 服务器名称
     * @param serverConfig 服务器配置
     */
    fun registerServer(serverName: String, serverConfig: MCPServerConfig) {
        serverConfigCache[serverName] = serverConfig
    }

    /**
     * 注册MCP服务器配置
     *
     * @param serverName 服务器名称
     * @param endpoint 服务器端点URL
     * @param description 服务器描述
     */
    fun registerServer(serverName: String, endpoint: String, description: String = "") {
        val serverConfig =
                MCPServerConfig(name = serverName, endpoint = endpoint, description = description)
        serverConfigCache[serverName] = serverConfig
    }

    /** 关闭所有MCP客户端连接 */
    fun shutdown() {
        clientCache.values.forEach { it.shutdown() }
        clientCache.clear()
    }
}
