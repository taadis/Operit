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
import kotlinx.coroutines.delay
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

            // 获取环境变量配置
            val envVars = serverConfig.env

            // 定义插件目录路径
            val termuxPluginDir =
                    "/data/data/com.termux/files/home/mcp_plugins/${pluginId.split("/").last()}"

            // 如果有环境变量，先检查并设置
            if (envVars.isNotEmpty()) {
                statusCallback(StartStatus.InProgress("设置环境变量..."))
                Log.d(TAG, "[$serverName] 设置环境变量: ${envVars.keys}")

                // 构建导出环境变量的命令
                val exportCommands =
                        envVars.entries.joinToString(" && ") { (key, value) ->
                            "export $key=\"$value\""
                        }

                // 执行cd命令并设置环境变量
                val envCommand = "cd $termuxPluginDir && $exportCommands"
                TerminalSessionManager.executeSessionCommand(
                        context = context,
                        session = session,
                        command = envCommand,
                        onOutput = { output -> Log.d(TAG, "[$serverName] 环境变量输出: $output") },
                        onInteractivePrompt = { prompt, executionId ->
                            Log.d(TAG, "[$serverName] 环境变量设置交互提示: $prompt (ID: $executionId)")
                            null
                        },
                        onComplete = { exitCode, commandSuccess ->
                            if (!commandSuccess) {
                                Log.e(TAG, "[$serverName] 环境变量设置失败 (exitCode: $exitCode)")
                            } else {
                                Log.d(TAG, "[$serverName] 环境变量设置成功")
                            }
                        }
                )
            }

            statusCallback(StartStatus.InProgress("执行命令: $command $args"))

            // 标记命令是否完成以及成功状态
            val commandCompletedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
            var success = false

            // 使用环境变量执行命令
            val fullCommand =
                    if (envVars.isNotEmpty()) {
                        // 如果有环境变量，一次性导出并执行命令
                        val exports =
                                envVars.entries.joinToString(" ") { (key, value) ->
                                    "$key=\"$value\""
                                }
                        "cd $termuxPluginDir && $exports $command $args"
                    } else {
                        // 如果没有环境变量，直接执行命令
                        "cd $termuxPluginDir && $command $args"
                    }

            // 创建一个定时器，在等待3秒后检查命令状态
            val timer = java.util.Timer()
            timer.schedule(object : java.util.TimerTask() {
                override fun run() {
                    // 如果命令仍在运行（没有触发onComplete），认为是成功的
                    if (!commandCompletedFlag.get()) {
                        Log.d(TAG, "[$serverName] 启动成功 - 命令仍在运行超过3秒")
                        statusCallback(StartStatus.Success("插件 $pluginId 已成功启动"))
                        success = true
                    }
                }
            }, 3000)

            // 执行命令
            TerminalSessionManager.executeSessionCommand(
                    context = context,
                    session = session,
                    command = fullCommand,
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
                        // 标记命令已完成
                        commandCompletedFlag.set(true)
                        
                        // 取消定时器，以免重复处理
                        timer.cancel()

                        // 对于长期运行的服务，如果命令完成了（返回了结果）通常意味着出错了
                        if (commandSuccess) {
                            Log.w(TAG, "[$serverName] 命令成功完成，但这可能意味着服务没有持续运行 (exitCode: $exitCode)")
                            statusCallback(StartStatus.Error("命令执行完成，可能未能保持运行，退出码: $exitCode"))
                            success = false
                        } else {
                            Log.e(TAG, "[$serverName] 命令执行失败 (exitCode: $exitCode)")
                            statusCallback(StartStatus.Error("启动失败，命令执行错误，退出码: $exitCode"))
                            success = false
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

                // 如果没有部署的插件，直接完成
                if (deployedPlugins.isEmpty()) {
                    progressListener.onAllPluginsStarted(0, 0)
                    return@launch
                }

                // 插件启动结果映射
                val pluginResults = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

                // 创建一个主定时器，确保整个启动过程不会卡住
                val masterTimer = java.util.Timer()
                masterTimer.schedule(object : java.util.TimerTask() {
                    override fun run() {
                        // 如果10秒后还有插件没有报告结果，将其标记为失败并继续
                        for (pluginId in deployedPlugins) {
                            if (!pluginResults.containsKey(pluginId)) {
                                Log.w(TAG, "插件 $pluginId 启动超时")
                                pluginResults[pluginId] = false
                                
                                // 通知插件启动完成（但失败）
                                val index = deployedPlugins.indexOf(pluginId) + 1
                                progressListener.onPluginStarted(
                                    pluginId,
                                    false,
                                    index,
                                    deployedPlugins.size
                                )
                            }
                        }
                        
                        // 检查是否所有插件都有了结果
                        if (pluginResults.size == deployedPlugins.size) {
                            // 报告最终结果
                            val successCount = pluginResults.values.count { it }
                            progressListener.onAllPluginsStarted(successCount, deployedPlugins.size)
                        }
                    }
                }, 10000) // 10秒超时
                
                // 并行启动所有已部署的插件
                deployedPlugins.forEachIndexed { index, pluginId ->
                    starterScope.launch {
                        Log.d(TAG, "异步启动插件: $pluginId")

                        // 通知开始启动插件
                        progressListener.onPluginStarting(
                                pluginId,
                                index + 1,
                                deployedPlugins.size
                        )

                        var success = false
                        var resultReported = false
                        
                        try {
                            // 创建一个插件启动状态回调，可以多次被调用
                            val statusCallback = { status: StartStatus ->
                                when (status) {
                                    is StartStatus.Success -> {
                                        Log.d(TAG, "插件 $pluginId 启动成功")
                                        if (!resultReported) {
                                            resultReported = true
                                            success = true
                                            startedCount.incrementAndGet()
                                            pluginResults[pluginId] = true
                                            
                                            // 通知插件启动完成
                                            progressListener.onPluginStarted(
                                                pluginId,
                                                true,
                                                index + 1,
                                                deployedPlugins.size
                                            )
                                            
                                            // 检查是否所有插件都完成了
                                            if (pluginResults.size == deployedPlugins.size) {
                                                // 取消主定时器
                                                masterTimer.cancel()
                                                
                                                // 报告最终结果
                                                val successCount = pluginResults.values.count { it }
                                                progressListener.onAllPluginsStarted(successCount, deployedPlugins.size)
                                            }
                                        }
                                    }
                                    is StartStatus.Error -> {
                                        Log.e(
                                                TAG,
                                                "插件 $pluginId 启动失败: ${status.message}"
                                        )
                                        if (!resultReported) {
                                            resultReported = true
                                            success = false
                                            pluginResults[pluginId] = false
                                            
                                            // 通知插件启动完成（但失败）
                                            progressListener.onPluginStarted(
                                                pluginId,
                                                false,
                                                index + 1,
                                                deployedPlugins.size
                                            )
                                            
                                            // 检查是否所有插件都完成了
                                            if (pluginResults.size == deployedPlugins.size) {
                                                // 取消主定时器
                                                masterTimer.cancel()
                                                
                                                // 报告最终结果
                                                val successCount = pluginResults.values.count { it }
                                                progressListener.onAllPluginsStarted(successCount, deployedPlugins.size)
                                            }
                                        }
                                    }
                                    is StartStatus.InProgress -> {
                                        // 处理进行中状态，不需要特别处理
                                    }
                                    is StartStatus.NotStarted -> {
                                        // 处理未开始状态，不需要特别处理
                                    }
                                }
                            }
                            
                            // 启动插件
                            startPlugin(pluginId = pluginId, statusCallback = statusCallback)
                            
                            // 注意：不再等待startPlugin返回，因为成功或失败会通过statusCallback通知
                            
                            // 设置一个安全定时器，确保即使statusCallback没有被调用，也会有结果
                            kotlinx.coroutines.delay(8000)
                            
                            // 如果8秒后仍未收到结果，标记为失败
                            if (!resultReported) {
                                Log.w(TAG, "插件 $pluginId 启动超时，未收到状态回调")
                                resultReported = true
                                success = false
                                pluginResults[pluginId] = false
                                
                                // 通知插件启动完成（但失败）
                                progressListener.onPluginStarted(
                                    pluginId,
                                    false,
                                    index + 1,
                                    deployedPlugins.size
                                )
                                
                                // 检查是否所有插件都完成了
                                if (pluginResults.size == deployedPlugins.size) {
                                    // 取消主定时器
                                    masterTimer.cancel()
                                    
                                    // 报告最终结果
                                    val successCount = pluginResults.values.count { it }
                                    progressListener.onAllPluginsStarted(successCount, deployedPlugins.size)
                                }
                            }
                        } catch (e: Exception) {
                            // 处理异常
                            Log.e(TAG, "插件 $pluginId 启动过程中出错", e)
                            if (!resultReported) {
                                resultReported = true
                                success = false
                                pluginResults[pluginId] = false
                                
                                // 通知插件启动完成（但失败）
                                progressListener.onPluginStarted(
                                    pluginId,
                                    false,
                                    index + 1,
                                    deployedPlugins.size
                                )
                                
                                // 检查是否所有插件都完成了
                                if (pluginResults.size == deployedPlugins.size) {
                                    // 取消主定时器
                                    masterTimer.cancel()
                                    
                                    // 报告最终结果
                                    val successCount = pluginResults.values.count { it }
                                    progressListener.onAllPluginsStarted(successCount, deployedPlugins.size)
                                }
                            }
                        }
                    }
                }
                
                // 设置一个全局超时，确保在非常极端的情况下也能完成
                delay(15000)
                
                // 如果15秒后还没有完成，强制完成
                masterTimer.cancel()
                
                // 计算最终成功数量
                val finalSuccess = pluginResults.values.count { it }
                
                // 通知所有插件启动完成
                if (pluginResults.size < deployedPlugins.size) {
                    Log.w(TAG, "在全局超时后强制完成插件启动，已处理: ${pluginResults.size}/${deployedPlugins.size}")
                    progressListener.onAllPluginsStarted(finalSuccess, deployedPlugins.size)
                }
                
                Log.d(TAG, "批量启动完成，成功启动: $finalSuccess 个插件")
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
            val extraDataMap = mutableMapOf<String, String>()
            extraDataMap["command"] = serverConfig.command
            extraDataMap["args"] = serverConfig.args.joinToString(",")

            // 添加环境变量信息
            serverConfig.env.forEach { (key, value) -> extraDataMap["env_$key"] = value }

            val mcpServerConfig =
                    com.ai.assistance.operit.core.tools.mcp.MCPServerConfig(
                            name = serverName,
                            endpoint = "mcp://plugin/$serverName",
                            description = "从插件启动的MCP服务器: $pluginId",
                            capabilities = listOf("tools", "resources"),
                            extraData = extraDataMap
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
