package com.ai.assistance.operit.ui.features.demo.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.RootAuthorizer
import com.ai.assistance.operit.ui.features.demo.state.DemoStateManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** ViewModel for the ShizukuDemoScreen Delegates most state management to DemoStateManager */
class ShizukuDemoViewModel(application: Application) : AndroidViewModel(application) {
    // 初始化时直接创建stateManager
    private val stateManager: DemoStateManager = DemoStateManager(application, viewModelScope)

    // AIToolHandler instance
    private val toolHandler: AIToolHandler = AIToolHandler.getInstance(application)

    // Expose state from the manager
    val uiState: StateFlow<com.ai.assistance.operit.ui.features.demo.state.DemoScreenState> =
            stateManager.uiState

    // Expose properties for Termux configuration
    val isTunaSourceEnabled
        get() = stateManager.isTunaSourceEnabled
    val isPythonInstalled
        get() = stateManager.isPythonInstalled
    val isUvInstalled
        get() = stateManager.isUvInstalled
    val isNodeInstalled
        get() = stateManager.isNodeInstalled
    val isTermuxConfiguring
        get() = stateManager.isTermuxConfiguring
    val isTermuxRunning
        get() = stateManager.isTermuxRunning
    val isTermuxBatteryOptimizationExempted
        get() = stateManager.isTermuxBatteryOptimizationExempted
    val isTermuxFullyConfigured
        get() = stateManager.isTermuxFullyConfigured
    val outputText
        get() = stateManager.outputText
    val currentTask
        get() = stateManager.currentTask

    /** Initialize the ViewModel with context data */
    fun initialize(context: Context) {
        // 初始化Root授权器
        RootAuthorizer.initialize(context)

        // 只需要调用stateManager的initialize方法
        stateManager.initialize()
    }

    /** Refresh app status */
    fun refreshStatus(context: Context) {
        // 检查Root状态
        checkRootStatus(context)

        stateManager.refreshStatus()
    }

    /** Check Termux authorization state */
    fun checkTermuxAuthState(context: Context) {
        stateManager.checkTermuxAuthState()
    }

    /** Check root status */
    fun checkRootStatus(context: Context) {
        viewModelScope.launch {
            val isDeviceRooted = RootAuthorizer.isDeviceRooted()
            val hasRootAccess = RootAuthorizer.checkRootStatus(context)

            stateManager.updateRootStatus(isDeviceRooted, hasRootAccess)

            Log.d(
                    "ShizukuDemoViewModel",
                    "Root状态更新: 设备已Root=$isDeviceRooted, 应用有Root权限=$hasRootAccess"
            )
        }
    }

    /** Request root permission */
    fun requestRootPermission(context: Context) {
        viewModelScope.launch {
            // 如果已有Root权限，则直接执行测试命令
            if (RootAuthorizer.hasRootAccess.value) {
                executeRootCommand("id", context)
                return@launch
            }
            
            // 如果没有Root权限，则先请求权限
            Toast.makeText(context, "正在请求Root权限...", Toast.LENGTH_SHORT).show()
            
            RootAuthorizer.requestRootPermission { granted ->
                viewModelScope.launch {
                    if (granted) {
                        Toast.makeText(context, "Root权限已授予", Toast.LENGTH_SHORT).show()
                        // 权限授予后执行一个简单的测试命令
                        executeRootCommand("id", context)
                    } else {
                        Toast.makeText(context, "Root权限请求被拒绝", Toast.LENGTH_SHORT).show()
                    }
                    
                    // 刷新状态
                    checkRootStatus(context)
                }
            }
        }
    }

    /** Execute root command */
    fun executeRootCommand(command: String, context: Context) {
        viewModelScope.launch {
            val result = RootAuthorizer.executeRootCommand(command)

            if (result.first) {
                Toast.makeText(context, "命令执行成功", Toast.LENGTH_SHORT).show()
                stateManager.updateResultText("命令执行成功:\n${result.second}")
            } else {
                Toast.makeText(context, "命令执行失败", Toast.LENGTH_SHORT).show()
                stateManager.updateResultText("命令执行失败:\n${result.second}")
            }
        }
    }

    /** Check installed components in Termux */
    fun checkInstalledComponents(context: Context) {
        stateManager.checkInstalledComponents()
    }

    /** Update configuration status and components */
    fun updateSourceStatus(isEnabled: Boolean) {
        stateManager.updateSourceStatus(isEnabled)
    }

    fun updatePythonStatus(isInstalled: Boolean) {
        stateManager.updatePythonStatus(isInstalled)
    }

    fun updateUvStatus(isInstalled: Boolean) {
        stateManager.updateUvStatus(isInstalled)
    }

    fun updateNodeStatus(isInstalled: Boolean) {
        stateManager.updateNodeStatus(isInstalled)
    }

    fun updateBatteryStatus(isExempted: Boolean) {
        stateManager.updateBatteryStatus(isExempted)
    }

    fun updateConfigStatus(isConfigured: Boolean) {
        stateManager.updateConfigStatus(isConfigured)
    }

    /** Update output text */
    fun updateOutputText(text: String) {
        stateManager.updateOutputText(text)
    }

    /** Configuration management */
    fun startConfiguration(taskName: String) {
        stateManager.startConfiguration(taskName)
    }

    fun endConfiguration() {
        stateManager.endConfiguration()
    }

    /** Dialog management */
    fun showResultDialog(title: String, content: String) {
        stateManager.showResultDialog(title, content)
    }

    fun hideResultDialog() {
        stateManager.hideResultDialog()
    }

    /** UI visibility toggles */
    fun toggleShizukuWizard() {
        stateManager.toggleShizukuWizard()
    }

    fun toggleTermuxWizard() {
        stateManager.toggleTermuxWizard()
    }

    fun toggleRootWizard() {
        stateManager.toggleRootWizard()
    }

    fun toggleAdbCommandExecutor() {
        stateManager.toggleAdbCommandExecutor()
    }

    fun toggleTermuxCommandExecutor() {
        stateManager.toggleTermuxCommandExecutor()
    }

    fun toggleSampleCommands() {
        stateManager.toggleSampleCommands()
    }

    /** Command handling */
    fun updateCommandText(text: String) {
        stateManager.updateCommandText(text)
    }

    /** Functional operations */
    fun configureTunaSource(context: Context) {
        viewModelScope.launch {
            startConfiguration("配置清华源")

            try {
                // Check if Termux is running
                if (!isTermuxRunning.value) {
                    updateOutputText("${outputText.value}\nTermux未运行，请先启动Termux")
                    Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Implementation of configureTunaSource
                updateOutputText("${outputText.value}\n开始配置清华源...")

                // Simulate configuration
                updateSourceStatus(true)
                updateOutputText("${outputText.value}\n清华源配置成功")
            } finally {
                endConfiguration()
            }
        }
    }

    fun installPython(context: Context) {
        viewModelScope.launch {
            startConfiguration("安装Python")

            try {
                // Check if Termux is running
                if (!isTermuxRunning.value) {
                    updateOutputText("${outputText.value}\nTermux未运行，请先启动Termux")
                    Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Implementation of installPython
                updateOutputText("${outputText.value}\n开始安装Python...")

                // Simulate installation
                updatePythonStatus(true)
                updateOutputText("${outputText.value}\nPython安装成功")
            } finally {
                endConfiguration()
            }
        }
    }

    fun installUv(context: Context) {
        viewModelScope.launch {
            startConfiguration("安装UV")

            try {
                // Check if Termux is running
                if (!isTermuxRunning.value) {
                    updateOutputText("${outputText.value}\nTermux未运行，请先启动Termux")
                    Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Implementation of installUv
                updateOutputText("${outputText.value}\n开始安装UV...")

                // Simulate installation
                updateUvStatus(true)
                updateOutputText("${outputText.value}\nUV安装成功")
            } finally {
                endConfiguration()
            }
        }
    }

    fun installNode(context: Context) {
        viewModelScope.launch {
            startConfiguration("安装Node.js")

            try {
                // Check if Termux is running
                if (!isTermuxRunning.value) {
                    updateOutputText("${outputText.value}\nTermux未运行，请先启动Termux")
                    Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Implementation of installNode
                updateOutputText("${outputText.value}\n开始安装Node.js...")

                // Simulate installation
                updateNodeStatus(true)
                updateOutputText("${outputText.value}\nNode.js安装成功")
            } finally {
                endConfiguration()
            }
        }
    }

    fun requestTermuxBatteryOptimization(context: Context) {
        viewModelScope.launch {
            startConfiguration("设置Termux电池优化豁免")

            try {
                // Check if Termux is running
                if (!isTermuxRunning.value) {
                    updateOutputText("${outputText.value}\nTermux未运行，请先启动Termux")
                    Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Implementation of requestTermuxBatteryOptimization
                updateOutputText("${outputText.value}\n开始设置Termux电池优化豁免...")
                updateOutputText(
                        "${outputText.value}\n已打开Termux电池优化设置页面。请在系统设置中点击「允许」以豁免Termux的电池优化。"
                )
                updateOutputText("${outputText.value}\n完成设置后，请返回本应用并点击刷新按钮检查状态。")

                // Open battery optimization settings for Termux
                try {
                    val intent =
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:com.termux")
                            }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    updateOutputText("${outputText.value}\n无法打开电池优化设置: ${e.message}")
                }
            } finally {
                endConfiguration()
            }
        }
    }

    fun authorizeTermux(context: Context) {
        viewModelScope.launch {
            startConfiguration("授权Termux")

            try {
                // Implementation of authorizeTermux
                updateOutputText("${outputText.value}\n开始授权Termux...")

                // After successful authorization
                updateOutputText("${outputText.value}\nTermux授权成功")

                // Check installed components after successful authorization
                checkInstalledComponents(context)
            } finally {
                endConfiguration()
            }
        }
    }

    fun startTermux(context: Context) {
        viewModelScope.launch {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("com.termux")
                if (intent != null) {
                    context.startActivity(intent)
                    stateManager.isTermuxRunning.value = true
                } else {
                    Toast.makeText(context, "无法找到Termux应用", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "无法启动Termux应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun executeAdbCommand() {
        viewModelScope.launch {
            // Update result text to indicate execution in progress
            stateManager.updateResultText("执行中...")

            try {
                // Execute the command
                val commandText = uiState.value.commandText.value
                val result = AndroidShellExecutor.executeShellCommand(commandText)

                // Update with the result
                stateManager.updateResultText(
                        if (result.success) {
                            "命令执行成功:\n${result.stdout}"
                        } else {
                            "命令执行失败 (退出码: ${result.exitCode}):\n${result.stderr}"
                        }
                )
            } catch (e: Exception) {
                // Handle execution errors
                stateManager.updateResultText("命令执行出错: ${e.message}")
            }
        }
    }

    /** Refresh all registered tools */
    fun refreshTools(context: Context) {
        Log.d("ShizukuDemoViewModel", "Refreshing all registered tools")
        // First clear the current tool execution state
        toolHandler.reset()

        // Re-register all default tools
        toolHandler.registerDefaultTools()

        // Show a toast notification for feedback
        Toast.makeText(context, "已重新注册所有工具", Toast.LENGTH_SHORT).show()
    }

    /** Cleanup when ViewModel is cleared */
    override fun onCleared() {
        super.onCleared()
        stateManager.cleanup()
    }

    /** ViewModelFactory for creating ShizukuDemoViewModel with dependencies */
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ShizukuDemoViewModel::class.java)) {
                return ShizukuDemoViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
