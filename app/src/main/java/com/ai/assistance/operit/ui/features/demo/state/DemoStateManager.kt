package com.ai.assistance.operit.ui.features.demo.state

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.ai.assistance.operit.core.tools.system.RootAuthorizer
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.termux.TermuxAuthorizer
import com.ai.assistance.operit.core.tools.system.termux.TermuxInstaller
import com.ai.assistance.operit.ui.features.demo.utils.TermuxDemoUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DemoStateManager"

/**
 * Consolidated state management for the demo screens. Handles state initialization, updates, and
 * listeners for Shizuku and Termux features.
 */
class DemoStateManager(private val context: Context, private val coroutineScope: CoroutineScope) {
    // Main UI state holder
    private val _uiState = MutableStateFlow(DemoScreenState())
    val uiState: StateFlow<DemoScreenState> = _uiState.asStateFlow()

    // Termux configuration states
    var isTunaSourceEnabled = mutableStateOf(false)
    var isPythonInstalled = mutableStateOf(false)
    var isUvInstalled = mutableStateOf(false)
    var isNodeInstalled = mutableStateOf(false)
    var isTermuxBatteryOptimizationExempted = mutableStateOf(false)
    var isTermuxFullyConfigured = mutableStateOf(false)
    var isTermuxRunning = mutableStateOf(false)
    var isTermuxConfiguring = mutableStateOf(false)

    // Output text for commands and configuration operations
    var outputText = mutableStateOf("欢迎使用Termux配置工具\n点击对应按钮开始配置")
    var currentTask = mutableStateOf("")

    // Shizuku/Termux state change listeners
    private val shizukuListener: () -> Unit = {
        refreshStatus()
        checkTermuxAuthState()
    }

    private val termuxListener: () -> Unit = {
        refreshStatus()
        checkTermuxAuthState()
    }

    // Root state change listener
    private val rootListener: () -> Unit = { refreshStatus() }

    init {
        // Register listeners for Shizuku and Termux state changes
        ShizukuAuthorizer.addStateChangeListener(shizukuListener)
        TermuxInstaller.addStateChangeListener(termuxListener)
        RootAuthorizer.addStateChangeListener(rootListener)
    }

    /** Initialize state */
    fun initialize() {
        coroutineScope.launch {
            Log.d(TAG, "初始化状态...")

            // 读取Termux配置持久化状态
            isTermuxFullyConfigured.value = TermuxDemoUtil.getTermuxConfigStatus(context)

            // 注册状态变更监听器
            registerStateChangeListeners()

            // 刷新所有权限状态
            refreshStatus()
        }
    }

    /** Refresh permissions and component status */
    fun refreshStatus() {
        _uiState.update { currentState -> currentState.copy(isRefreshing = mutableStateOf(true)) }

        coroutineScope.launch {
            try {
                // Refresh permissions and status
                TermuxDemoUtil.refreshPermissionsAndStatus(
                        context = context,
                        updateShizukuInstalled = { _uiState.value.isShizukuInstalled.value = it },
                        updateShizukuRunning = { _uiState.value.isShizukuRunning.value = it },
                        updateShizukuPermission = {
                            _uiState.value.hasShizukuPermission.value = it
                        },
                        updateTermuxInstalled = { _uiState.value.isTermuxInstalled.value = it },
                        updateTermuxRunning = { isTermuxRunning.value = it },
                        updateStoragePermission = {
                            _uiState.value.hasStoragePermission.value = it
                        },
                        updateLocationPermission = {
                            _uiState.value.hasLocationPermission.value = it
                        },
                        updateOverlayPermission = {
                            _uiState.value.hasOverlayPermission.value = it
                        },
                        updateBatteryOptimizationExemption = {
                            _uiState.value.hasBatteryOptimizationExemption.value = it
                        },
                        updateAccessibilityServiceEnabled = {
                            _uiState.value.hasAccessibilityServiceEnabled.value = it
                        }
                )

                // Check Shizuku API_V23 permission
                if (_uiState.value.isShizukuInstalled.value && _uiState.value.isShizukuRunning.value
                ) {
                    _uiState.value.hasShizukuPermission.value =
                            ShizukuAuthorizer.hasShizukuPermission()

                    if (!_uiState.value.hasShizukuPermission.value) {
                        Log.d(TAG, "缺少Shizuku API_V23权限，显示Shizuku向导卡片")
                        _uiState.value.showShizukuWizard.value = true
                    }
                } else {
                    _uiState.value.hasShizukuPermission.value = false
                    _uiState.value.showShizukuWizard.value = true
                }

                // Check Termux authorization status
                checkTermuxAuthState()

                // Check if we're still refreshing to add a small delay for visibility
                delay(300)
            } catch (e: Exception) {
                Log.e(TAG, "刷新权限状态时出错: ${e.message}", e)
            } finally {
                _uiState.update { currentState ->
                    currentState.copy(isRefreshing = mutableStateOf(false))
                }
            }
        }
    }

    /** Update root status */
    fun updateRootStatus(isDeviceRooted: Boolean, hasRootAccess: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(
                    isDeviceRooted = mutableStateOf(isDeviceRooted),
                    hasRootAccess = mutableStateOf(hasRootAccess)
            )
        }

        // 如果设备已Root但未获取权限，则显示Root向导
        if (isDeviceRooted && !hasRootAccess) {
            _uiState.update { currentState ->
                currentState.copy(showRootWizard = mutableStateOf(true))
            }
        }
    }

    /** Check Termux authorization state */
    fun checkTermuxAuthState() {
        coroutineScope.launch {
            try {
                // 检查是否存在一个运行中的Termux实例
                val isRunning = TermuxDemoUtil.checkTermuxRunning(context)
                isTermuxRunning.value = isRunning

                // Update Termux authorization status
                val hasTermuxPermission = TermuxAuthorizer.isTermuxAuthorized(context)
                _uiState.value.isTermuxAuthorized.value = hasTermuxPermission

                // 首先检查是否有缓存数据
                val cachedFullyConfigured = TermuxDemoUtil.getTermuxConfigStatus(context)

                // If Termux is not authorized, ensure the wizard card is shown
                if (!_uiState.value.isTermuxAuthorized.value) {
                    _uiState.value.showTermuxWizard.value = true
                    isTermuxFullyConfigured.value = false
                } else if (!isRunning) {
                    // 如果已授权但未运行，显示向导卡片并设置为未完全配置
                    _uiState.value.showTermuxWizard.value = true
                    isTermuxFullyConfigured.value = false
                } else {
                    // Only check configurations if authorized and running
                    isTermuxFullyConfigured.value = cachedFullyConfigured
                    checkInstalledComponents()
                }

                Log.d(
                        TAG,
                        "Termux授权状态: $hasTermuxPermission, 配置状态: ${isTermuxFullyConfigured.value}, 运行状态: $isRunning"
                )
            } catch (e: Exception) {
                Log.e(TAG, "检查Termux授权状态时出错: ${e.message}", e)
            }
        }
    }

    /** Check installed components */
    fun checkInstalledComponents() {
        coroutineScope.launch {
            try {
                // 读取持久化状态
                isTermuxFullyConfigured.value = TermuxDemoUtil.getTermuxConfigStatus(context)

                // 如果Termux未运行或未授权，不进行检查
                if (!isTermuxRunning.value || !_uiState.value.isTermuxAuthorized.value) {
                    return@launch
                }

                // 逐个检查各个组件状态
                val batteryExemption = TermuxDemoUtil.checkTermuxBatteryOptimization(context)
                val tunaEnabled = TermuxDemoUtil.checkTunaSourceEnabled(context)
                val pythonInstalled = TermuxDemoUtil.checkPythonInstalled(context)
                val uvInstalled = TermuxDemoUtil.checkUvInstalled(context)
                val nodeInstalled = TermuxDemoUtil.checkNodeInstalled(context)

                // 更新状态
                isTermuxBatteryOptimizationExempted.value = batteryExemption
                isTunaSourceEnabled.value = tunaEnabled
                isPythonInstalled.value = pythonInstalled
                isUvInstalled.value = uvInstalled
                isNodeInstalled.value = nodeInstalled

                // 检查是否所有组件都已配置
                val allConfigured =
                        batteryExemption &&
                                tunaEnabled &&
                                pythonInstalled &&
                                uvInstalled &&
                                nodeInstalled

                // 更新缓存状态
                if (allConfigured && !isTermuxFullyConfigured.value) {
                    Log.d(TAG, "所有Termux组件已配置完成")
                    TermuxDemoUtil.saveTermuxConfigStatus(context, true)
                    isTermuxFullyConfigured.value = true
                } else if (!allConfigured && isTermuxFullyConfigured.value) {
                    Log.d(
                            TAG,
                            "Termux组件配置不完整（电池优化: $batteryExemption, 清华源: $tunaEnabled, " +
                                    "Python: $pythonInstalled, UV: $uvInstalled, Node: $nodeInstalled）"
                    )
                    TermuxDemoUtil.saveTermuxConfigStatus(context, false)
                    isTermuxFullyConfigured.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查已安装组件时出错: ${e.message}", e)
            }
        }
    }

    /** Update configuration status */
    fun updateConfigStatus(isConfigured: Boolean) {
        isTermuxFullyConfigured.value = isConfigured
    }

    /** Update component status */
    fun updateSourceStatus(isEnabled: Boolean) {
        isTunaSourceEnabled.value = isEnabled
    }

    fun updatePythonStatus(isInstalled: Boolean) {
        isPythonInstalled.value = isInstalled
    }

    fun updateUvStatus(isInstalled: Boolean) {
        isUvInstalled.value = isInstalled
    }

    fun updateNodeStatus(isInstalled: Boolean) {
        isNodeInstalled.value = isInstalled
    }

    fun updateBatteryStatus(isExempted: Boolean) {
        isTermuxBatteryOptimizationExempted.value = isExempted
    }

    /** Update UI state */
    fun updateOutputText(text: String) {
        outputText.value = text
    }

    fun startConfiguration(taskName: String) {
        isTermuxConfiguring.value = true
        currentTask.value = taskName
        showResultDialog(taskName, outputText.value)
    }

    fun endConfiguration() {
        isTermuxConfiguring.value = false
        currentTask.value = ""
        hideResultDialog()
    }

    /** Dialog management */
    fun showResultDialog(title: String, content: String) {
        _uiState.update { currentState ->
            currentState.copy(
                    resultDialogTitle = mutableStateOf(title),
                    resultDialogContent = mutableStateOf(content),
                    showResultDialogState = mutableStateOf(true)
            )
        }
    }

    fun hideResultDialog() {
        _uiState.update { currentState ->
            currentState.copy(showResultDialogState = mutableStateOf(false))
        }
    }

    /** Toggle UI visibility */
    fun toggleShizukuWizard() {
        _uiState.update { currentState ->
            currentState.copy(
                    showShizukuWizard = mutableStateOf(!currentState.showShizukuWizard.value)
            )
        }
    }

    fun toggleTermuxWizard() {
        _uiState.update { currentState ->
            currentState.copy(
                    showTermuxWizard = mutableStateOf(!currentState.showTermuxWizard.value)
            )
        }
    }

    fun toggleRootWizard() {
        _uiState.update { currentState ->
            currentState.copy(showRootWizard = mutableStateOf(!currentState.showRootWizard.value))
        }
    }

    fun toggleAdbCommandExecutor() {
        _uiState.update { currentState ->
            currentState.copy(
                    showAdbCommandExecutor =
                            mutableStateOf(!currentState.showAdbCommandExecutor.value)
            )
        }
    }

    fun toggleTermuxCommandExecutor() {
        _uiState.update { currentState ->
            currentState.copy(
                    showTermuxCommandExecutor =
                            mutableStateOf(!currentState.showTermuxCommandExecutor.value)
            )
        }
    }

    fun toggleSampleCommands() {
        _uiState.update { currentState ->
            currentState.copy(
                    showSampleCommands = mutableStateOf(!currentState.showSampleCommands.value)
            )
        }
    }

    /** Command handling */
    fun updateCommandText(text: String) {
        _uiState.update { currentState -> currentState.copy(commandText = mutableStateOf(text)) }
    }

    fun updateResultText(text: String) {
        _uiState.update { currentState -> currentState.copy(resultText = mutableStateOf(text)) }
    }

    /** Clean up resources */
    fun cleanup() {
        // Remove listeners
        ShizukuAuthorizer.removeStateChangeListener(shizukuListener)
        TermuxInstaller.removeStateChangeListener(termuxListener)
        RootAuthorizer.removeStateChangeListener(rootListener)
    }

    private fun registerStateChangeListeners() {
        // Implementation of registerStateChangeListeners method
    }

    /** Set loading state */
    fun setLoading(isLoading: Boolean) {
        _uiState.update { currentState -> currentState.copy(isLoading = mutableStateOf(isLoading)) }
    }

    /** Initialize state asynchronously */
    suspend fun initializeAsync() {
        Log.d(TAG, "异步初始化状态...")

        // 读取Termux配置持久化状态
        isTermuxFullyConfigured.value = TermuxDemoUtil.getTermuxConfigStatus(context)

        // 注册状态变更监听器
        registerStateChangeListeners()

        // 刷新所有权限状态（在IO线程上）
        refreshStatusAsync()
    }

    /** Refresh permissions and component status asynchronously */
    private suspend fun refreshStatusAsync() {
        _uiState.update { currentState -> currentState.copy(isRefreshing = mutableStateOf(true)) }

        try {
            // 在IO线程上执行所有刷新操作
            // Refresh permissions and status
            TermuxDemoUtil.refreshPermissionsAndStatus(
                    context = context,
                    updateShizukuInstalled = { _uiState.value.isShizukuInstalled.value = it },
                    updateShizukuRunning = { _uiState.value.isShizukuRunning.value = it },
                    updateShizukuPermission = { _uiState.value.hasShizukuPermission.value = it },
                    updateTermuxInstalled = { _uiState.value.isTermuxInstalled.value = it },
                    updateTermuxRunning = { isTermuxRunning.value = it },
                    updateStoragePermission = { _uiState.value.hasStoragePermission.value = it },
                    updateLocationPermission = { _uiState.value.hasLocationPermission.value = it },
                    updateOverlayPermission = { _uiState.value.hasOverlayPermission.value = it },
                    updateBatteryOptimizationExemption = {
                        _uiState.value.hasBatteryOptimizationExemption.value = it
                    },
                    updateAccessibilityServiceEnabled = {
                        _uiState.value.hasAccessibilityServiceEnabled.value = it
                    }
            )

            // Check Shizuku API_V23 permission
            if (_uiState.value.isShizukuInstalled.value && _uiState.value.isShizukuRunning.value) {
                _uiState.value.hasShizukuPermission.value = ShizukuAuthorizer.hasShizukuPermission()

                if (!_uiState.value.hasShizukuPermission.value) {
                    Log.d(TAG, "缺少Shizuku API_V23权限，显示Shizuku向导卡片")
                    _uiState.value.showShizukuWizard.value = true
                }
            } else {
                _uiState.value.hasShizukuPermission.value = false
                _uiState.value.showShizukuWizard.value = true
            }

            // Check Termux authorization status
            checkTermuxAuthStateAsync()

            // 延迟300ms以确保UI能够刷新
            delay(300)
        } catch (e: Exception) {
            Log.e(TAG, "刷新权限状态时出错: ${e.message}", e)
        } finally {
            _uiState.update { currentState ->
                currentState.copy(isRefreshing = mutableStateOf(false))
            }
        }
    }

    /** Check Termux authorization state asynchronously */
    private suspend fun checkTermuxAuthStateAsync() {
        try {
            // 检查是否存在一个运行中的Termux实例
            val isRunning = TermuxDemoUtil.checkTermuxRunning(context)
            isTermuxRunning.value = isRunning

            // 检查是否安装了Termux
            val isInstalled = TermuxInstaller.isTermuxInstalled(context)
            _uiState.value.isTermuxInstalled.value = isInstalled

            // 如果未安装，就不需要检查权限
            if (!isInstalled) {
                _uiState.value.isTermuxAuthorized.value = false
                _uiState.value.showTermuxWizard.value = true
                isTermuxFullyConfigured.value = false
                return
            }

            // 首先检查是否有缓存数据
            val cachedFullyConfigured = TermuxDemoUtil.getTermuxConfigStatus(context)

            // 检查授权状态 - 无论是否运行
            val hasTermuxPermission = TermuxAuthorizer.isTermuxAuthorized(context)
            _uiState.value.isTermuxAuthorized.value = hasTermuxPermission

            // 如果Termux没有运行，则始终显示向导卡片
            if (!isRunning) {
                _uiState.value.showTermuxWizard.value = true

                // 如果缓存显示已配置，但Termux未运行，需要显示启动向导
                if (cachedFullyConfigured) {
                    Log.d(TAG, "Termux已授权但未运行，显示向导卡片")
                    // 保留授权状态，只需显示向导
                }
            }

            // 检查是否已完全配置（缓存检查）
            isTermuxFullyConfigured.value =
                    cachedFullyConfigured && hasTermuxPermission && isRunning

            Log.d(
                    TAG,
                    "Termux授权状态: $hasTermuxPermission, 配置状态: ${isTermuxFullyConfigured.value}, 运行状态: $isRunning"
            )
        } catch (e: Exception) {
            Log.e(TAG, "检查Termux授权状态时出错: ${e.message}", e)
        }
    }

    /** Check installed components asynchronously */
    private suspend fun checkInstalledComponentsAsync() {
        try {
            // 检查各项配置状态
            val batteryExemption = TermuxDemoUtil.checkTermuxBatteryOptimization(context)
            val tunaEnabled = TermuxDemoUtil.checkTunaSourceEnabled(context)
            val pythonInstalled = TermuxDemoUtil.checkPythonInstalled(context)
            val uvInstalled = TermuxDemoUtil.checkUvInstalled(context)
            val nodeInstalled = TermuxDemoUtil.checkNodeInstalled(context)

            // 更新状态
            isTermuxBatteryOptimizationExempted.value = batteryExemption
            isTunaSourceEnabled.value = tunaEnabled
            isPythonInstalled.value = pythonInstalled
            isUvInstalled.value = uvInstalled
            isNodeInstalled.value = nodeInstalled

            // 检查是否所有组件都已配置
            val allConfigured =
                    batteryExemption &&
                            tunaEnabled &&
                            pythonInstalled &&
                            uvInstalled &&
                            nodeInstalled

            // 更新缓存状态
            if (allConfigured) {
                Log.d(TAG, "所有Termux组件已配置完成")
                TermuxDemoUtil.saveTermuxConfigStatus(context, true)
                isTermuxFullyConfigured.value = true
            } else {
                Log.d(TAG, "Termux组件配置不完整")
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查已安装组件时出错: ${e.message}", e)
        }
    }
}

/** Data class to hold all UI state */
data class DemoScreenState(
        // Permission states
        val isShizukuInstalled: MutableState<Boolean> = mutableStateOf(false),
        val isShizukuRunning: MutableState<Boolean> = mutableStateOf(false),
        val hasShizukuPermission: MutableState<Boolean> = mutableStateOf(false),
        val isTermuxInstalled: MutableState<Boolean> = mutableStateOf(false),
        val isTermuxAuthorized: MutableState<Boolean> = mutableStateOf(false),
        val hasStoragePermission: MutableState<Boolean> = mutableStateOf(false),
        val hasOverlayPermission: MutableState<Boolean> = mutableStateOf(false),
        val hasBatteryOptimizationExemption: MutableState<Boolean> = mutableStateOf(false),
        val hasAccessibilityServiceEnabled: MutableState<Boolean> = mutableStateOf(false),
        val hasLocationPermission: MutableState<Boolean> = mutableStateOf(false),
        val isDeviceRooted: MutableState<Boolean> = mutableStateOf(false),
        val hasRootAccess: MutableState<Boolean> = mutableStateOf(false),

        // UI states
        val isRefreshing: MutableState<Boolean> = mutableStateOf(false),
        val showHelp: MutableState<Boolean> = mutableStateOf(false),
        val permissionErrorMessage: MutableState<String?> = mutableStateOf(null),
        val showSampleCommands: MutableState<Boolean> = mutableStateOf(false),
        val showAdbCommandExecutor: MutableState<Boolean> = mutableStateOf(false),
        val showTermuxCommandExecutor: MutableState<Boolean> = mutableStateOf(false),
        val showShizukuWizard: MutableState<Boolean> = mutableStateOf(false),
        val showTermuxWizard: MutableState<Boolean> = mutableStateOf(false),
        val showRootWizard: MutableState<Boolean> = mutableStateOf(false),
        val showResultDialogState: MutableState<Boolean> = mutableStateOf(false),

        // Command execution
        val commandText: MutableState<String> = mutableStateOf(""),
        val resultText: MutableState<String> = mutableStateOf("结果将显示在这里"),
        val resultDialogTitle: MutableState<String> = mutableStateOf(""),
        val resultDialogContent: MutableState<String> = mutableStateOf(""),
        val isLoading: MutableState<Boolean> = mutableStateOf(false)
)

// Sample command lists that can be reused
val sampleAdbCommands =
        listOf(
                "getprop ro.build.version.release" to "获取Android版本",
                "pm list packages" to "列出已安装的应用包名",
                "dumpsys battery" to "查看电池状态",
                "settings list system" to "列出系统设置",
                "am start -a android.intent.action.VIEW -d https://www.example.com" to "打开网页",
                "dumpsys activity activities" to "查看活动的Activity",
                "service list" to "列出系统服务",
                "wm size" to "查看屏幕分辨率"
        )

// Predefined Termux commands
val termuxSampleCommands =
        listOf(
                "echo 'Hello Termux'" to "打印Hello Termux",
                "ls -la" to "列出文件和目录",
                "whoami" to "显示当前用户",
                "pkg update" to "更新包管理器",
                "pkg install python" to "安装Python",
                "termux-info" to "显示Termux信息",
                "termux-notification -t '测试通知' -c '这是一条测试通知'" to "发送通知",
                "termux-clipboard-get" to "获取剪贴板内容"
        )

// Root命令示例
val rootSampleCommands =
        listOf(
                "mount -o rw,remount /system" to "重新挂载系统分区为可写",
                "cat /proc/version" to "查看内核版本信息",
                "ls -la /data" to "查看/data目录内容",
                "getenforce" to "查看SELinux状态",
                "ps -A" to "列出所有进程",
                "cat /proc/meminfo" to "查看内存信息",
                "pm list features" to "列出系统功能",
                "dumpsys power" to "查看电源管理状态"
        )
