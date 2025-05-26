package com.ai.assistance.operit.ui.features.demo.viewmodel

import android.app.Application
import android.content.Context
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
import com.ai.assistance.operit.ui.features.demo.utils.TermuxDemoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** Set loading state */
    fun setLoading(isLoading: Boolean) {
        stateManager.setLoading(isLoading)
    }

    /** Initialize the ViewModel with context data (Async version) */
    fun initializeAsync(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 初始化Root授权器 - 在IO线程进行初始化
                RootAuthorizer.initialize(context)

                // 先检查一次Root状态
                val isDeviceRooted = RootAuthorizer.isDeviceRooted()
                val hasRootAccess = RootAuthorizer.checkRootStatus(context)

                // 更新状态
                withContext(Dispatchers.Main) {
                    stateManager.updateRootStatus(isDeviceRooted, hasRootAccess)
                }

                // 调用stateManager的异步初始化方法
                stateManager.initializeAsync()
            } catch (e: Exception) {
                Log.e("ShizukuDemoViewModel", "初始化时出错: ${e.message}", e)
            } finally {
                // 完成后关闭加载指示器
                withContext(Dispatchers.Main) { setLoading(false) }
            }
        }
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

                // Call the actual implementation
                val result =
                        TermuxDemoUtil.configureTunaSource(
                                context = context,
                                updateOutputText = { text -> updateOutputText(text) },
                                updateSourceStatus = { enabled -> updateSourceStatus(enabled) },
                                updateConfigStatus = { configured ->
                                    updateConfigStatus(configured)
                                },
                                isTunaSourceEnabled = isTunaSourceEnabled.value,
                                isPythonInstalled = isPythonInstalled.value,
                                isUvInstalled = isUvInstalled.value,
                                isNodeInstalled = isNodeInstalled.value,
                                currentOutputText = outputText.value
                        )

                // Update output text with the result
                updateOutputText(result)
            } catch (e: Exception) {
                Log.e("ShizukuDemoViewModel", "配置清华源时出错: ${e.message}", e)
                updateOutputText("${outputText.value}\n配置清华源时出错: ${e.message}")
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

                // Call the actual implementation
                val result =
                        TermuxDemoUtil.installPython(
                                context = context,
                                updateOutputText = { text -> updateOutputText(text) },
                                updatePythonStatus = { installed -> updatePythonStatus(installed) },
                                updateConfigStatus = { configured ->
                                    updateConfigStatus(configured)
                                },
                                isTunaSourceEnabled = isTunaSourceEnabled.value,
                                isPythonInstalled = isPythonInstalled.value,
                                isUvInstalled = isUvInstalled.value,
                                isNodeInstalled = isNodeInstalled.value,
                                currentOutputText = outputText.value
                        )

                // Update output text with the result
                updateOutputText(result)
            } catch (e: Exception) {
                Log.e("ShizukuDemoViewModel", "安装Python时出错: ${e.message}", e)
                updateOutputText("${outputText.value}\n安装Python时出错: ${e.message}")
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

                // Call the actual implementation
                val result =
                        TermuxDemoUtil.installUv(
                                context = context,
                                updateOutputText = { text -> updateOutputText(text) },
                                updateUvStatus = { installed -> updateUvStatus(installed) },
                                updateConfigStatus = { configured ->
                                    updateConfigStatus(configured)
                                },
                                isTunaSourceEnabled = isTunaSourceEnabled.value,
                                isPythonInstalled = isPythonInstalled.value,
                                isUvInstalled = isUvInstalled.value,
                                isNodeInstalled = isNodeInstalled.value,
                                currentOutputText = outputText.value
                        )

                // Update output text with the result
                updateOutputText(result)
            } catch (e: Exception) {
                Log.e("ShizukuDemoViewModel", "安装UV时出错: ${e.message}", e)
                updateOutputText("${outputText.value}\n安装UV时出错: ${e.message}")
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

                // Call the actual implementation
                val result =
                        TermuxDemoUtil.installNode(
                                context = context,
                                updateOutputText = { text -> updateOutputText(text) },
                                updateNodeStatus = { installed -> updateNodeStatus(installed) },
                                updateConfigStatus = { configured ->
                                    updateConfigStatus(configured)
                                },
                                isTunaSourceEnabled = isTunaSourceEnabled.value,
                                isPythonInstalled = isPythonInstalled.value,
                                isUvInstalled = isUvInstalled.value,
                                isNodeInstalled = isNodeInstalled.value,
                                currentOutputText = outputText.value
                        )

                // Update output text with the result
                updateOutputText(result)
            } catch (e: Exception) {
                Log.e("ShizukuDemoViewModel", "安装Node.js时出错: ${e.message}", e)
                updateOutputText("${outputText.value}\n安装Node.js时出错: ${e.message}")
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

                // Call the actual battery optimization exemption request
                TermuxDemoUtil.requestTermuxBatteryOptimization(context)

                updateOutputText("${outputText.value}\n开始设置Termux电池优化豁免...")
                updateOutputText(
                        "${outputText.value}\n已打开Termux电池优化设置页面。请在系统设置中点击「允许」以豁免Termux的电池优化。"
                )
                updateOutputText("${outputText.value}\n完成设置后，请返回本应用并点击刷新按钮检查状态。")

                // After requesting the exemption, check the status
                val isExempted = TermuxDemoUtil.checkTermuxBatteryOptimization(context)
                if (isExempted) {
                    updateBatteryStatus(true)
                    updateOutputText("${outputText.value}\nTermux已成功获得电池优化豁免！")
                }
            } catch (e: Exception) {
                Log.e("ShizukuDemoViewModel", "设置Termux电池优化豁免时出错: ${e.message}", e)
                updateOutputText("${outputText.value}\n无法打开电池优化设置: ${e.message}")
            } finally {
                endConfiguration()
            }
        }
    }

    fun authorizeTermux(context: Context) {
        viewModelScope.launch {
            startConfiguration("授权Termux")

            try {
                // Call the actual authorization logic
                val result =
                        TermuxDemoUtil.authorizeTermux(
                                context = context,
                                updateOutputText = { text -> updateOutputText(text) },
                                updateTermuxAuthorized = { authorized ->
                                    stateManager.uiState.value.isTermuxAuthorized.value = authorized
                                },
                                updateTermuxRunning = { running ->
                                    stateManager.isTermuxRunning.value = running
                                },
                                currentOutputText = outputText.value
                        )

                // Update output text with the result from authorizeTermux
                updateOutputText(result)

                // Check installed components after authorization attempt
                checkInstalledComponents(context)
            } catch (e: Exception) {
                Log.e("ShizukuDemoViewModel", "授权Termux时出错: ${e.message}", e)
                updateOutputText("${outputText.value}\n授权Termux时出错: ${e.message}")
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
                    // Set Termux as running immediately to improve UI responsiveness
                    stateManager.isTermuxRunning.value = true

                    // Check if Termux was previously fully configured
                    val cachedFullyConfigured = TermuxDemoUtil.getTermuxConfigStatus(context)

                    if (cachedFullyConfigured) {
                        // Give Termux a moment to start up
                        delay(500)

                        // If it was previously fully configured, we can assume it's already
                        // authorized
                        // Just restore the fully configured state directly
                        stateManager.uiState.value.isTermuxAuthorized.value = true
                        stateManager.isTermuxFullyConfigured.value = true

                        // Check installed components in background to update the UI
                        checkInstalledComponents(context)
                    }
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

        // Show a toast notification for feedback - using Main dispatcher to ensure it runs on the
        // UI thread
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, "已重新注册所有工具", Toast.LENGTH_SHORT).show()
        }
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
