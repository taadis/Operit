package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import com.ai.assistance.operit.tools.PackageTool
import com.ai.assistance.operit.tools.PackageToolParameter
import com.ai.assistance.operit.tools.ToolPackage
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

/**
 * 表示MCP服务器作为工具包
 *
 * 该类将MCP服务器转换为标准ToolPackage格式，使其可以与现有的PackageManager无缝集成
 */
@Serializable
data class MCPPackage(
        val serverConfig: MCPServerConfig,
        val mcpTools: List<MCPTool> = emptyList()
) {
    companion object {
        private const val TAG = "MCPPackage"

        /**
         * 从服务器创建MCP包
         *
         * @param context 应用上下文
         * @param serverConfig 服务器配置
         * @return 创建的MCP包，如果连接失败则返回null
         */
        fun fromServer(context: Context, serverConfig: MCPServerConfig): MCPPackage? {
            val mcpClient = MCPClient(context, serverConfig)

            // 初始化连接
            val initResult = mcpClient.initialize()
            if (!initResult.startsWith("已成功初始化")) {
                mcpClient.shutdown()
                return null
            }

            // 获取工具列表
            val tools = mcpClient.getTools()

            // 关闭连接
            mcpClient.shutdown()

            return MCPPackage(serverConfig, tools)
        }
    }

    /** 转换为标准工具包格式 将MCP包转换为与现有PackageManager兼容的ToolPackage格式 */
    fun toToolPackage(): ToolPackage {
        // 将MCP工具转换为标准工具包工具
        val tools =
                mcpTools.map { mcpTool ->
                    // 将MCP工具参数转换为标准工具包参数
                    val params =
                            mcpTool.parameters.map { mcpParam ->
                                PackageToolParameter(
                                        name = mcpParam.name,
                                        description = mcpParam.description,
                                        required = mcpParam.required,
                                        type = mcpParam.type
                                )
                            }

                    // 创建工具包工具 - 只使用工具名称
                    PackageTool(
                            name = mcpTool.name, // 只使用工具名
                            description = mcpTool.description,
                            parameters = params,
                            // 注意：script字段用于存储MCP服务器和工具的信息，用于识别MCP服务器
                            script = generateScriptPlaceholder(serverConfig.name, mcpTool.name)
                    )
                }

        // 创建完整的工具包，使用服务器名称作为包名，不添加任何前缀
        return ToolPackage(
                name = serverConfig.name, // 直接使用服务器名称，不添加mcp:前缀
                description = serverConfig.description,
                tools = tools,
                // 使用标准文件操作类别，可以根据需要调整
                category = ToolCategory.FILE_READ
        )
    }

    /** 生成脚本占位符 用于在script字段中存储MCP服务器和工具的信息，便于后续识别 */
    private fun generateScriptPlaceholder(serverName: String, toolName: String): String {
        return """
            /* MCPJS
            {
                "serverName": "$serverName",
                "toolName": "$toolName",
                "endpoint": "${serverConfig.endpoint}"
            }
            */
            // MCP 工具 - 不是实际的JavaScript脚本
            // 这是一个占位符，用于存储MCP服务器和工具的信息
        """.trimIndent()
    }
}
