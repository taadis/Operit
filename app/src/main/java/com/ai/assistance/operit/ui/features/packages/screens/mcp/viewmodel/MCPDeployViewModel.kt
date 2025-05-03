package com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.mcp.MCPDeployer
import com.ai.assistance.operit.data.mcp.MCPRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MCP部署ViewModel
 *
 * 负责处理插件部署业务逻辑
 */
class MCPDeployViewModel(private val context: Context, private val mcpRepository: MCPRepository) :
        ViewModel() {

    private val mcpDeployer = MCPDeployer(context)

    // 部署状态
    private val _deploymentStatus =
            MutableStateFlow<MCPDeployer.DeploymentStatus>(MCPDeployer.DeploymentStatus.NotStarted)
    val deploymentStatus: StateFlow<MCPDeployer.DeploymentStatus> = _deploymentStatus.asStateFlow()

    // 当前正在部署的插件ID
    private val _currentDeployingPlugin = MutableStateFlow<String?>(null)
    val currentDeployingPlugin: StateFlow<String?> = _currentDeployingPlugin.asStateFlow()

    // 部署输出消息
    private val _outputMessages = MutableStateFlow<List<String>>(emptyList())
    val outputMessages: StateFlow<List<String>> = _outputMessages.asStateFlow()

    /**
     * 部署插件
     *
     * @param pluginId 要部署的插件ID
     */
    fun deployPlugin(pluginId: String) {
        // 获取插件路径
        val pluginPath = mcpRepository.getInstalledPluginPath(pluginId)
        if (pluginPath == null) {
            _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error("无法获取插件路径: $pluginId")
            return
        }

        // 清除之前的输出
        _outputMessages.value = emptyList()

        // 设置当前部署的插件
        _currentDeployingPlugin.value = pluginId

        // 开始部署
        viewModelScope.launch {
            // 更新状态
            _deploymentStatus.value = MCPDeployer.DeploymentStatus.InProgress("正在准备部署插件")

            // 执行部署
            mcpDeployer.deployPlugin(
                    pluginId = pluginId,
                    pluginPath = pluginPath,
                    statusCallback = { status ->
                        _deploymentStatus.value = status

                        // 收集输出消息
                        if (status is MCPDeployer.DeploymentStatus.InProgress) {
                            if (status.message.startsWith("输出:")) {
                                val newMessage = status.message.removePrefix("输出:").trim()
                                if (newMessage.isNotEmpty()) {
                                    _outputMessages.value = _outputMessages.value + newMessage
                                }
                            }
                        }
                    }
            )
        }
    }

    /** 重置部署状态 */
    fun resetDeploymentState() {
        _deploymentStatus.value = MCPDeployer.DeploymentStatus.NotStarted
        _currentDeployingPlugin.value = null
        _outputMessages.value = emptyList()
    }

    /** 工厂类 */
    class Factory(private val context: Context, private val mcpRepository: MCPRepository) :
            ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MCPDeployViewModel::class.java)) {
                return MCPDeployViewModel(context, mcpRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
