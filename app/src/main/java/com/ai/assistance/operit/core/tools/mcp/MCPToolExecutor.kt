package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient
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

        // 获取MCP桥接客户端
        val mcpClient = mcpManager.getOrCreateClient(serverName)
        if (mcpClient == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "无法连接到MCP服务器: $serverName"
            )
        }

        Log.d(TAG, "准备调用MCP工具: $serverName:$actualToolName")

        // 将AITool参数转换为Map
        val parameters = tool.parameters.associate { it.name to it.value }

        // 调用MCP工具 - 使用同步版本
        val result =
                try {
                    // 从MCPBridgeClient中调用工具
                    val jsonResult = mcpClient.callToolSync(actualToolName, parameters)
                    if (jsonResult != null) {
                        Log.d(TAG, "MCP工具调用成功: $serverName:$actualToolName")
                        // 重要：不要断开连接，保持活跃状态以便后续调用
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = StringResultData(jsonResult.toString()),
                                error = null
                        )
                    } else {
                        Log.w(TAG, "MCP工具调用失败: $serverName:$actualToolName - 未返回有效结果")
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "工具调用失败，未返回有效结果"
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "调用MCP工具时发生异常: ${e.message}", e)
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "调用工具时发生异常: ${e.message}"
                    )
                }

        return result
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
 * 管理MCP客户端的创建和缓存 注意：此版本使用MCPBridgeClient作为底层客户端，替代了原有的MCPClient
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

    // 缓存已创建的MCP桥接客户端，避免重复创建
    private val clientCache =
            ConcurrentHashMap<String, com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient>()

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
     * 获取或创建MCP桥接客户端
     *
     * @param serverName 服务器名称
     * @return MCP桥接客户端，如果服务器不存在或无法连接则返回null
     */
    fun getOrCreateClient(
            serverName: String
    ): com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient? {
        // 检查缓存中是否已有客户端
        val cachedClient = clientCache[serverName]
        if (cachedClient != null) {
            // 检查客户端连接状态 - 只做轻量检查，不要过早断开
            if (cachedClient.isConnected()) {
                Log.d(TAG, "使用已缓存的客户端: $serverName")
                return cachedClient
            } else {
                // 尝试重新连接现有客户端
                Log.d(TAG, "尝试重新连接缓存的客户端: $serverName")
                val reconnected = kotlinx.coroutines.runBlocking { cachedClient.connect() }
                if (reconnected) {
                    Log.d(TAG, "成功重新连接到服务: $serverName")
                    return cachedClient
                }
                // 客户端不再可用，从缓存移除
                Log.w(TAG, "无法重新连接到服务: $serverName，将创建新的连接")
                clientCache.remove(serverName)
            }
        }

        // 获取服务器配置
        val serverConfig = serverConfigCache[serverName] ?: return null

        try {
            // 创建新的桥接客户端
            val client =
                    com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient(context, serverName)

            // 尝试连接 - 带详细日志
            Log.d(TAG, "正在创建新的连接到服务: $serverName")
            val connectResult = kotlinx.coroutines.runBlocking { client.connect() }

            if (connectResult) {
                // 连接成功，在会话期间保持此连接
                Log.d(TAG, "成功连接到服务: $serverName，将在会话期间保持连接")
                clientCache[serverName] = client
                return client
            } else {
                Log.w(TAG, "无法连接到服务: $serverName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建桥接客户端时出错: ${e.message}", e)
        }

        return null
    }

    /**
     * 注册MCP服务器配置
     *
     * @param serverName 服务器名称
     * @param serverConfig 服务器配置
     */
    fun registerServer(serverName: String, serverConfig: MCPServerConfig) {
        serverConfigCache[serverName] = serverConfig

        // 如果已有缓存的客户端，需要更新或移除
        if (clientCache.containsKey(serverName)) {
            // 移除旧客户端，下次需要时会重新创建
            val oldClient = clientCache.remove(serverName)
            oldClient?.disconnect()
        }
    }

    /**
     * 注册MCP服务器（简化版）
     *
     * @param serverName 服务器名称
     * @param endpoint 服务器端点URL
     * @param description 服务器描述
     */
    fun registerServer(serverName: String, endpoint: String, description: String = "") {
        val serverConfig = MCPServerConfig(
            name = serverName,
            endpoint = endpoint,
            description = description,
            capabilities = listOf("tools"),
            extraData = emptyMap()
        )
        registerServer(serverName, serverConfig)
    }

    /** 关闭所有MCP客户端连接 */
    fun shutdown() {
        clientCache.values.forEach { it.disconnect() }
        clientCache.clear()
    }
}
