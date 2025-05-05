package com.ai.assistance.operit.data.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json

/** VSCode风格的MCP配置 用于解析和管理VSCode格式的MCP服务器配置 */
@Serializable
data class MCPVscodeConfig(val mcpServers: Map<String, ServerConfig> = emptyMap()) {
    /** 服务器配置 */
    @Serializable
    data class ServerConfig(
            val command: String,
            val args: List<String> = emptyList(),
            val disabled: Boolean = false,
            val autoApprove: List<String> = emptyList()
    )

    companion object {
        /**
         * 从JSON字符串解析配置
         *
         * @param json JSON字符串
         * @return MCPVscodeConfig 实例
         */
        fun fromJson(json: String): MCPVscodeConfig? {
            return try {
                val jsonFormat = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                }
                jsonFormat.decodeFromString<MCPVscodeConfig>(json)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 从配置中提取服务器名称
         *
         * @param configJson 配置JSON字符串
         * @return 第一个服务器名称，如果解析失败则返回null
         */
        fun extractServerName(configJson: String): String? {
            if (configJson.isBlank()) return null

            try {
                val config = fromJson(configJson)
                return config?.mcpServers?.keys?.firstOrNull()
            } catch (e: Exception) {
                return null
            }
        }

        /**
         * 生成类似VSCode的MCP配置
         *
         * @param serverName 服务器名称
         * @param command 命令
         * @param args 参数列表
         * @return JSON字符串
         */
        fun generateConfig(serverName: String, command: String, args: List<String>): String {
            val serverConfig = ServerConfig(command, args)
            val config = MCPVscodeConfig(mapOf(serverName to serverConfig))

            val jsonFormat = Json {
                prettyPrint = true
                encodeDefaults = true
            }

            return jsonFormat.encodeToString(MCPVscodeConfig.serializer(), config)
        }
    }
}
