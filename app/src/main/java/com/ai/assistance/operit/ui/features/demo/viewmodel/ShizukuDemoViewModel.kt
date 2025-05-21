package com.ai.assistance.operit.ui.features.demo.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.ui.features.demo.model.ShizukuScreenState
import com.ai.assistance.operit.ui.features.demo.utils.getTermuxConfigStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the ShizukuDemoScreen Handles business logic and state management for Shizuku and
 * Termux related operations
 */
class ShizukuDemoViewModel : ViewModel() {
    // Main UI state holder
    private val _uiState = MutableStateFlow(ShizukuScreenState())
    val uiState: StateFlow<ShizukuScreenState> = _uiState.asStateFlow()

    // Termux configuration states
    var isTunaSourceEnabled by mutableStateOf(false)
        private set
    var isPythonInstalled by mutableStateOf(false)
        private set
    var isUvInstalled by mutableStateOf(false)
        private set
    var isNodeInstalled by mutableStateOf(false)
        private set
    var isTermuxConfiguring by mutableStateOf(false)
        private set
    var isTermuxRunning by mutableStateOf(false)
        private set
    var isTermuxBatteryOptimizationExempted by mutableStateOf(false)
        private set
    var isTermuxFullyConfigured by mutableStateOf(false)
        private set

    // Output text for commands and configuration operations
    var outputText by mutableStateOf("欢迎使用Termux配置工具\n点击对应按钮开始配置")
        private set
    var currentTask by mutableStateOf("")
        private set

    /** Initialize the ViewModel with context data */
    fun initialize(context: Context) {
        viewModelScope.launch {
            // Load termux config status
            isTermuxFullyConfigured = getTermuxConfigStatus(context)

            // Initialize state
            initializeState(context)
        }
    }

    /**
     * Initialize the state with current system status
     */
    private fun initializeState(context: Context) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isRefreshing = mutableStateOf(true)
                )
            }
            
            // Check Shizuku status
            val isShizukuInstalled = ShizukuAuthorizer.isShizukuInstalled(context)
            val isShizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning()
            val hasShizukuPermission = ShizukuAuthorizer.hasShizukuPermission()
            
            // Check Termux status
            val isTermuxInstalled = isPackageInstalled(context, "com.termux")
            
            // Check Storage Permission
            val hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == 
                    PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                    PackageManager.PERMISSION_GRANTED
            }
            
            // Check Location Permission
            val hasLocationPermission = context.checkSelfPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == 
                    PackageManager.PERMISSION_GRANTED
            
            // Check Overlay Permission
            val hasOverlayPermission = Settings.canDrawOverlays(context)
            
            // Check Battery Optimization Exemption
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val hasBatteryOptimizationExemption = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            
            // Check Accessibility Service
            val hasAccessibilityServiceEnabled = UIHierarchyManager.isAccessibilityServiceEnabled(context)
            
            // Update UI state
            _uiState.update { currentState ->
                currentState.copy(
                    isShizukuInstalled = mutableStateOf(isShizukuInstalled),
                    isShizukuRunning = mutableStateOf(isShizukuRunning),
                    hasShizukuPermission = mutableStateOf(hasShizukuPermission),
                    isTermuxInstalled = mutableStateOf(isTermuxInstalled),
                    hasStoragePermission = mutableStateOf(hasStoragePermission),
                    hasLocationPermission = mutableStateOf(hasLocationPermission),
                    hasOverlayPermission = mutableStateOf(hasOverlayPermission),
                    hasBatteryOptimizationExemption = mutableStateOf(hasBatteryOptimizationExemption),
                    hasAccessibilityServiceEnabled = mutableStateOf(hasAccessibilityServiceEnabled),
                    isRefreshing = mutableStateOf(false)
                )
            }
            
            // If Shizuku is running and has permission, check Termux authorization
            if (isShizukuRunning && hasShizukuPermission) {
                checkTermuxAuthState(context)
            }
            
            // Check installed components if Termux is authorized
            if (_uiState.value.isTermuxAuthorized.value) {
                checkInstalledComponents(context)
            }
        }
    }

    /** Refresh app status */
    fun refreshStatus(context: Context) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isRefreshing = mutableStateOf(true))
            }

            initializeState(context)
        }
    }

    /** Check Termux authorization state */
    fun checkTermuxAuthState(context: Context) {
        viewModelScope.launch {
            // Check if Termux is authorized
            // Implementation depends on your specific requirements

            // Placeholder for actual implementation
            val isAuthorized = false // Replace with actual check

            _uiState.update { currentState ->
                currentState.copy(isTermuxAuthorized = mutableStateOf(isAuthorized))
            }

            if (isAuthorized) {
                checkInstalledComponents(context)
            }
        }
    }

    /** Check installed components in Termux */
    fun checkInstalledComponents(context: Context) {
        viewModelScope.launch {
            // Check for installed components in Termux
            // Implementation depends on your specific requirements

            // Placeholder for actual implementation
            updateConfigStatus(getTermuxConfigStatus(context))
        }
    }

    /** Update component status */
    fun updateSourceStatus(isEnabled: Boolean) {
        isTunaSourceEnabled = isEnabled
    }

    fun updatePythonStatus(isInstalled: Boolean) {
        isPythonInstalled = isInstalled
    }

    fun updateUvStatus(isInstalled: Boolean) {
        isUvInstalled = isInstalled
    }

    fun updateNodeStatus(isInstalled: Boolean) {
        isNodeInstalled = isInstalled
    }

    fun updateBatteryStatus(isExempted: Boolean) {
        isTermuxBatteryOptimizationExempted = isExempted
    }

    fun updateConfigStatus(isConfigured: Boolean) {
        isTermuxFullyConfigured = isConfigured
    }

    /** Update output text */
    fun updateOutputText(text: String) {
        outputText = text
    }

    /** Start configuration process */
    fun startConfiguration(taskName: String) {
        isTermuxConfiguring = true
        currentTask = taskName
        showResultDialog(taskName, outputText)
    }

    /** End configuration process */
    fun endConfiguration() {
        isTermuxConfiguring = false
        currentTask = ""
        hideResultDialog()
    }

    /** Show result dialog */
    fun showResultDialog(title: String, content: String) {
        _uiState.update { currentState ->
            currentState.copy(
                    resultDialogTitle = mutableStateOf(title),
                    resultDialogContent = mutableStateOf(content),
                    showResultDialogState = mutableStateOf(true)
            )
        }
    }

    /** Hide result dialog */
    fun hideResultDialog() {
        _uiState.update { currentState ->
            currentState.copy(showResultDialogState = mutableStateOf(false))
        }
    }

    /** Toggle wizard visibility */
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

    /** Toggle command executor visibility */
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

    /** Functional operations */
    fun configureTunaSource(context: Context) {
        viewModelScope.launch {
            startConfiguration("配置清华源")

            try {
                // Check if Termux is running
                val isRunning = checkTermuxRunning(context)
                isTermuxRunning = isRunning

                if (!isRunning) {
                    updateOutputText("${outputText}\nTermux未运行，请先启动Termux")
                    Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Implementation of configureTunaSource
                // This is a placeholder for the actual implementation
                updateOutputText("${outputText}\n开始配置清华源...")

                // Simulate configuration
                updateSourceStatus(true)
                updateOutputText("${outputText}\n清华源配置成功")
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
                val isRunning = checkTermuxRunning(context)
                isTermuxRunning = isRunning

                if (!isRunning) {
                    updateOutputText("${outputText}\nTermux未运行，请先启动Termux")
                    Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Implementation of installPython
                // This is a placeholder for the actual implementation
                updateOutputText("${outputText}\n开始安装Python...")

                // Simulate installation
                updatePythonStatus(true)
                updateOutputText("${outputText}\nPython安装成功")
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
                val isRunning = checkTermuxRunning(context)
                isTermuxRunning = isRunning

                if (!isRunning) {
                    updateOutputText("${outputText}\nTermux未运行，请先启动Termux")
                    Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Implementation of installUv
                // This is a placeholder for the actual implementation
                updateOutputText("${outputText}\n开始安装UV...")

                // Simulate installation
                updateUvStatus(true)
                updateOutputText("${outputText}\nUV安装成功")
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
                val isRunning = checkTermuxRunning(context)
                isTermuxRunning = isRunning

                if (!isRunning) {
                    updateOutputText("${outputText}\nTermux未运行，请先启动Termux")
                    Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Implementation of installNode
                // This is a placeholder for the actual implementation
                updateOutputText("${outputText}\n开始安装Node.js...")

                // Simulate installation
                updateNodeStatus(true)
                updateOutputText("${outputText}\nNode.js安装成功")
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
                val isRunning = checkTermuxRunning(context)
                isTermuxRunning = isRunning

                if (!isRunning) {
                    updateOutputText("${outputText}\nTermux未运行，请先启动Termux")
                    Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Implementation of requestTermuxBatteryOptimization
                // This would typically open the battery optimization settings
                updateOutputText("${outputText}\n开始设置Termux电池优化豁免...")
                updateOutputText("${outputText}\n已打开Termux电池优化设置页面。请在系统设置中点击「允许」以豁免Termux的电池优化。")
                updateOutputText("${outputText}\n完成设置后，请返回本应用并点击刷新按钮检查状态。")

                // Open battery optimization settings for Termux
                try {
                    val intent =
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:com.termux")
                            }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    updateOutputText("${outputText}\n无法打开电池优化设置: ${e.message}")
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
                // This is a placeholder for the actual implementation
                updateOutputText("${outputText}\n开始授权Termux...")

                // Simulate authorization
                _uiState.update { currentState ->
                    currentState.copy(isTermuxAuthorized = mutableStateOf(true))
                }

                updateOutputText("${outputText}\nTermux授权成功")

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
                    isTermuxRunning = true
                } else {
                    Toast.makeText(context, "无法找到Termux应用", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "无法启动Termux应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Utility functions */
    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkTermuxRunning(context: Context): Boolean {
        // Check if Termux is running
        // This is a placeholder for the actual implementation
        return isPackageInstalled(context, "com.termux") // Replace with actual check
    }

    /** Cleanup when ViewModel is cleared */
    override fun onCleared() {
        super.onCleared()
        // Cleanup resources if needed
    }

    /** ViewModelFactory for creating ShizukuDemoViewModel with dependencies */
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ShizukuDemoViewModel::class.java)) {
                return ShizukuDemoViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    /** Command handling methods */
    fun updateCommandText(text: String) {
        _uiState.update { currentState -> currentState.copy(commandText = mutableStateOf(text)) }
    }

    fun toggleSampleCommands() {
        _uiState.update { currentState ->
            currentState.copy(
                    showSampleCommands = mutableStateOf(!currentState.showSampleCommands.value)
            )
        }
    }

    fun executeAdbCommand() {
        viewModelScope.launch {
            // Update result text to indicate execution in progress
            _uiState.update { currentState ->
                currentState.copy(resultText = mutableStateOf("执行中..."))
            }

            try {
                // Execute the command
                val result =
                        AndroidShellExecutor.executeAdbCommand(_uiState.value.commandText.value)

                // Update with the result
                _uiState.update { currentState ->
                    currentState.copy(
                            resultText =
                                    mutableStateOf(
                                            if (result.success) {
                                                "命令执行成功:\n${result.stdout}"
                                            } else {
                                                "命令执行失败 (退出码: ${result.exitCode}):\n${result.stderr}"
                                            }
                                    )
                    )
                }
            } catch (e: Exception) {
                // Handle execution errors
                _uiState.update { currentState ->
                    currentState.copy(resultText = mutableStateOf("命令执行出错: ${e.message}"))
                }
            }
        }
    }
}
