package com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.mcp.InstallProgress
import com.ai.assistance.operit.data.mcp.InstallResult
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel for MCP 服务器管理，包括安装、卸载等功能 */
class MCPViewModel(private val repository: MCPRepository) : ViewModel() {

    // 当前安装进度
    private val _installProgress = MutableStateFlow<InstallProgress?>(null)
    val installProgress: StateFlow<InstallProgress?> = _installProgress.asStateFlow()

    // 安装结果
    private val _installResult = MutableStateFlow<InstallResult?>(null)
    val installResult: StateFlow<InstallResult?> = _installResult.asStateFlow()

    // 当前正在操作的服务器
    private val _currentServer = MutableStateFlow<MCPServer?>(null)
    val currentServer: StateFlow<MCPServer?> = _currentServer.asStateFlow()

    // 插件已安装路径缓存
    private val installedPathsCache = mutableMapOf<String, String?>()

    init {
        // 同步已安装状态
        viewModelScope.launch { repository.syncInstalledStatus() }
    }

    /** 安装服务器插件 */
    fun installServer(server: MCPServer) {
        viewModelScope.launch {
            _currentServer.value = server
            _installProgress.value = InstallProgress.Preparing
            _installResult.value = null

            val result =
                    repository.installMCPServer(server.id) { progress ->
                        _installProgress.value = progress
                    }

            _installResult.value = result

            // 清除缓存
            installedPathsCache.remove(server.id)
        }
    }

    /** 卸载服务器插件 */
    fun uninstallServer(server: MCPServer) {
        viewModelScope.launch {
            _currentServer.value = server
            _installProgress.value = InstallProgress.Preparing
            _installResult.value = null

            val success = repository.uninstallMCPServer(server.id)

            _installResult.value =
                    if (success) {
                        InstallResult.Success("")
                    } else {
                        InstallResult.Error("卸载失败")
                    }

            _installProgress.value = InstallProgress.Finished

            // 清除缓存
            installedPathsCache.remove(server.id)
        }
    }

    /** 重置安装状态 */
    fun resetInstallState() {
        _installProgress.value = null
        _installResult.value = null
        _currentServer.value = null
    }

    /** 获取已安装插件的路径 */
    fun getInstalledPath(serverId: String): String? {
        // 先查缓存
        if (installedPathsCache.containsKey(serverId)) {
            return installedPathsCache[serverId]
        }

        // 从存储库查询
        val path = repository.getInstalledPluginPath(serverId)
        installedPathsCache[serverId] = path
        return path
    }

    /** 获取本地插件信息，无需网络请求 */
    fun getLocalPluginDetails(serverId: String): MCPServer? {
        // 如果插件没有安装，返回null
        if (!repository.isPluginInstalled(serverId)) {
            return null
        }

        // 从当前列表中查找，这里的信息已经通过updateInstalledStatus更新过，
        // 包含了本地元数据
        return repository.mcpServers.value.find { it.id == serverId }
    }

    /** 刷新本地插件列表 */
    fun refreshLocalPlugins() {
        viewModelScope.launch {
            repository.syncInstalledStatus()
            // 清除路径缓存，强制重新读取
            installedPathsCache.clear()
        }
    }

    /** 刷新插件列表 */
    fun refreshPluginList() {
        viewModelScope.launch {
            repository.refresh()

            // 同步安装状态
            repository.syncInstalledStatus()
        }
    }

    /** 检查插件是否已安装 */
    fun isPluginInstalled(serverId: String): Boolean {
        return repository.isPluginInstalled(serverId)
    }

    /** 同步所有插件的安装状态 */
    fun syncInstalledStatus() {
        viewModelScope.launch { repository.syncInstalledStatus() }
    }

    /** ViewModel Factory */
    class Factory(private val repository: MCPRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MCPViewModel::class.java)) {
                return MCPViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
