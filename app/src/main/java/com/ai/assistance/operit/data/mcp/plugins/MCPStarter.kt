package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPConfigPreferences
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.MCPVscodeConfig
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSessionManager
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * MCP 插件启动工具类
 *
 * 负责启动已部署的MCP插件，通过TerminalSession执行命令
 */
class MCPStarter(private val context: Context) {

    companion object {
        private const val TAG = "MCPStarter"
    }

    // 创建一个协程作用域，用于处理异步操作
    private val starterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 插件启动进度监听器接口 */
    interface PluginStartProgressListener {
        /**
         * 当插件开始启动时调用
         * @param pluginId 插件ID
         * @param index 当前插件索引（从1开始）
         * @param total 总插件数量
         */
        fun onPluginStarting(pluginId: String, index: Int, total: Int)

        /**
         * 当插件启动完成时调用
         * @param pluginId 插件ID
         * @param success 是否成功启动
         * @param index 当前插件索引（从1开始）
         * @param total 总插件数量
         */
        fun onPluginStarted(pluginId: String, success: Boolean, index: Int, total: Int)

        /**
         * 当所有插件启动完成时调用
         * @param successCount 成功启动的插件数量
         * @param totalCount 总插件数量
         */
        fun onAllPluginsStarted(successCount: Int, totalCount: Int)
    }

    /**
     * 启动插件
     *
     * @param pluginId 插件ID
     * @param statusCallback 状态回调
     * @return 是否成功启动
     */
    suspend fun startPlugin(pluginId: String, statusCallback: (StartStatus) -> Unit): Boolean {
        try {
            val mcpConfigPreferences = MCPConfigPreferences(context)

            // 检查插件是否已部署
            val isDeployed = mcpConfigPreferences.getDeploySuccessFlow(pluginId).first()
            if (!isDeployed) {
                statusCallback(StartStatus.Error("插件未部署: $pluginId"))
                return false
            }

            statusCallback(StartStatus.InProgress("正在启动插件: $pluginId"))

            // 获取和解析插件配置
            val mcpLocalServer =
                    com.ai.assistance.operit.data.mcp.MCPLocalServer.getInstance(context)
            val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
            val config = MCPVscodeConfig.fromJson(pluginConfig)
            val serverName =
                    extractServerNameFromConfig(pluginConfig)
                            ?: pluginId.split("/").last().lowercase()

            // 获取服务器命令和参数
            val serverConfig = config?.mcpServers?.get(serverName)
            if (serverConfig == null) {
                statusCallback(StartStatus.Error("插件配置无效: $pluginId"))
                return false
            }

            // 创建终端会话
            val session = TerminalSessionManager.createSession("MCP-$serverName")

            // 构建完整命令
            val command = serverConfig.command
            val args = serverConfig.args.joinToString(" ")

            // 定义插件目录路径
            val termuxPluginDir =
                    "/data/data/com.termux/files/home/mcp_plugins/${pluginId.split("/").last()}"

            statusCallback(StartStatus.InProgress("执行命令: $command $args"))

            // 执行启动命令
            var success = false

            TerminalSessionManager.executeSessionCommand(
                    context = context,
                    session = session,
                    command = "cd $termuxPluginDir && $command $args",
                    onOutput = { output ->
                        Log.d(TAG, "[$serverName] 输出: $output")
                        statusCallback(StartStatus.InProgress("输出: $output"))
                    },
                    onInteractivePrompt = { prompt, executionId ->
                        Log.d(TAG, "[$serverName] 交互提示: $prompt (ID: $executionId)")
                        statusCallback(StartStatus.InProgress("交互提示: $prompt"))
                        null // 不提供自动响应
                    },
                    onComplete = { exitCode, commandSuccess ->
                        success = commandSuccess
                        if (commandSuccess) {
                            Log.d(TAG, "[$serverName] 启动成功 (exitCode: $exitCode)")
                            statusCallback(StartStatus.Success("插件 $pluginId 已成功启动"))
                        } else {
                            Log.e(TAG, "[$serverName] 启动失败 (exitCode: $exitCode)")
                            statusCallback(StartStatus.Error("启动失败，退出码: $exitCode"))
                        }
                    }
            )

            // 确保注册服务器
            registerServerIfNeeded(serverName, serverConfig, pluginId)

            return success
        } catch (e: Exception) {
            Log.e(TAG, "启动插件时出错", e)
            statusCallback(StartStatus.Error("启动错误: ${e.message}"))
            return false
        }
    }

    /** 启动所有已部署的插件 */
    fun startAllDeployedPlugins() {
        // 使用空监听器调用带监听器的方法
        startAllDeployedPlugins(
                object : PluginStartProgressListener {
                    override fun onPluginStarting(pluginId: String, index: Int, total: Int) {}
                    override fun onPluginStarted(
                            pluginId: String,
                            success: Boolean,
                            index: Int,
                            total: Int
                    ) {}
                    override fun onAllPluginsStarted(successCount: Int, totalCount: Int) {}
                }
        )
    }

    /**
     * 启动所有已部署的插件（带进度监听器）
     *
     * @param progressListener 进度监听器
     */
    fun startAllDeployedPlugins(progressListener: PluginStartProgressListener) {
        Log.d(TAG, "开始启动所有已部署的插件")

        starterScope.launch {
            try {
                val mcpRepository = MCPRepository(context)
                val mcpConfigPreferences = MCPConfigPreferences(context)

                // 获取已安装的插件列表
                val pluginList = mcpRepository.installedPluginIds.first()
                Log.d(TAG, "已安装插件数量: ${pluginList.size}")

                // 使用原子计数器追踪成功启动的插件数量
                val startedCount = AtomicInteger(0)

                // 过滤出已部署的插件
                val deployedPlugins =
                        pluginList.filter { pluginId ->
                            mcpConfigPreferences.getDeploySuccessFlow(pluginId).first()
                        }

                Log.d(TAG, "已部署插件数量: ${deployedPlugins.size}")

                // 并行启动所有已部署的插件
                val startJobs =
                        deployedPlugins.mapIndexed { index, pluginId ->
                            starterScope.async {
                                Log.d(TAG, "异步启动插件: $pluginId")

                                // 通知开始启动插件
                                progressListener.onPluginStarting(
                                        pluginId,
                                        index + 1,
                                        deployedPlugins.size
                                )

                                val success =
                                        startPlugin(
                                                pluginId = pluginId,
                                                statusCallback = { status ->
                                                    when (status) {
                                                        is StartStatus.Success -> {
                                                            Log.d(TAG, "插件 $pluginId 启动成功")
                                                            startedCount.incrementAndGet()
                                                        }
                                                        is StartStatus.Error -> {
                                                            Log.e(
                                                                    TAG,
                                                                    "插件 $pluginId 启动失败: ${status.message}"
                                                            )
                                                        }
                                                        is StartStatus.InProgress -> {
                                                            // 处理进行中状态
                                                        }
                                                        is StartStatus.NotStarted -> {
                                                            // 处理未开始状态
                                                        }
                                                    }
                                                }
                                        )

                                // 通知插件启动完成
                                progressListener.onPluginStarted(
                                        pluginId,
                                        success,
                                        index + 1,
                                        deployedPlugins.size
                                )

                                success
                            }
                        }

                // 等待所有插件启动完成
                startJobs.awaitAll()

                // 通知所有插件启动完成
                progressListener.onAllPluginsStarted(startedCount.get(), deployedPlugins.size)

                Log.d(TAG, "批量启动完成，成功启动: ${startedCount.get()} 个插件")
            } catch (e: Exception) {
                Log.e(TAG, "批量启动插件时出错", e)
                progressListener.onAllPluginsStarted(0, 0)
            }
        }
    }

    /** 从配置中提取服务器名称 */
    private fun extractServerNameFromConfig(configJson: String): String? {
        return MCPVscodeConfig.extractServerName(configJson)
    }

    /** 如果需要，注册服务器到MCPManager */
    private fun registerServerIfNeeded(
            serverName: String,
            serverConfig: MCPVscodeConfig.ServerConfig,
            pluginId: String
    ) {
        try {
            // 获取MCP管理器实例
            val mcpManager = com.ai.assistance.operit.core.tools.mcp.MCPManager.getInstance(context)

            // 构建服务器配置
            val mcpServerConfig =
                    com.ai.assistance.operit.core.tools.mcp.MCPServerConfig(
                            name = serverName,
                            endpoint = "mcp://plugin/$serverName",
                            description = "从插件启动的MCP服务器: $pluginId",
                            capabilities = listOf("tools", "resources"),
                            extraData =
                                    mapOf(
                                            "command" to serverConfig.command,
                                            "args" to serverConfig.args.joinToString(",")
                                    )
                    )

            // 注册服务器
            mcpManager.registerServer(serverName, mcpServerConfig)
            Log.d(TAG, "已注册插件 $pluginId 为服务器: $serverName")
        } catch (e: Exception) {
            Log.e(TAG, "注册服务器失败: ${e.message}")
        }
    }

    /** 启动状态 */
    sealed class StartStatus {
        object NotStarted : StartStatus()
        data class InProgress(val message: String) : StartStatus()
        data class Success(val message: String) : StartStatus()
        data class Error(val message: String) : StartStatus()
    }
}
