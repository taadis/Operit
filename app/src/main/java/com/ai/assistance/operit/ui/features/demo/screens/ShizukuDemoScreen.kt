package com.ai.assistance.operit.ui.features.demo.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ai.assistance.operit.core.tools.system.termux.TermuxAuthorizer
import com.ai.assistance.operit.core.tools.system.termux.TermuxInstaller
import com.ai.assistance.operit.core.tools.system.termux.TermuxUtils
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.tools.system.AdbCommandExecutor
import com.ai.assistance.operit.tools.system.ShizukuInstaller
import com.ai.assistance.operit.tools.system.TermuxCommandExecutor
import com.ai.assistance.operit.ui.features.demo.components.*
import com.ai.assistance.operit.ui.features.demo.model.ShizukuScreenState
import com.ai.assistance.operit.ui.features.demo.wizards.ShizukuWizardCard
import com.ai.assistance.operit.ui.features.demo.wizards.TermuxWizardCard
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ShizukuDemoScreen"
private const val TERMUX_CONFIG_PREFS = "termux_config_preferences"
private const val KEY_TERMUX_FULLY_CONFIGURED = "termux_fully_configured"

/**
 * 保存Termux配置状态到持久化存储
 * @param context 上下文
 * @param isFullyConfigured 是否已完全配置
 */
private fun saveTermuxConfigStatus(context: Context, isFullyConfigured: Boolean) {
    context.getSharedPreferences(TERMUX_CONFIG_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TERMUX_FULLY_CONFIGURED, isFullyConfigured)
            .apply()
    Log.d(TAG, "保存Termux配置状态: $isFullyConfigured")
}

/**
 * 获取Termux配置状态
 * @param context 上下文
 * @return 是否已完全配置
 */
private fun getTermuxConfigStatus(context: Context): Boolean {
    val status =
            context.getSharedPreferences(TERMUX_CONFIG_PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_TERMUX_FULLY_CONFIGURED, false)
    Log.d(TAG, "获取Termux配置状态: $status")
    return status
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuDemoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Create screen state
    val state = remember { ShizukuScreenState() }

    // Add new state variables for Termux configurations
    val isTunaSourceEnabled = remember { mutableStateOf(false) }
    val isPythonInstalled = remember { mutableStateOf(false) }
    val isNodeInstalled = remember { mutableStateOf(false) }
    val isTermuxConfiguring = remember { mutableStateOf(false) }
    val isTermuxRunning = remember { mutableStateOf(false) }

    // 添加Termux配置持久化状态 - 在初始化阶段就读取，避免界面闪烁
    val isTermuxFullyConfigured = remember {
        // 立即读取持久化配置，避免LaunchedEffect中读取导致的UI闪烁
        val savedStatus = getTermuxConfigStatus(context)
        Log.d(TAG, "初始化时 - Termux持久化配置状态: $savedStatus")
        mutableStateOf(savedStatus)
    }

    // 输出文本
    var outputText by remember { mutableStateOf("欢迎使用Termux配置工具\n点击对应按钮开始配置") }
    var currentTask by remember { mutableStateOf("") }

    // 执行Termux命令的辅助函数
    val executeTermuxCommand: suspend (Context, String) -> AdbCommandExecutor.CommandResult =
            { context, command ->
                val result =
                        TermuxCommandExecutor.executeCommand(
                                context = context,
                                command = command,
                                autoAuthorize = true
                        )

                // 给命令一些执行时间
                delay(500)

                result
            }

    // Location permission launcher
    val locationPermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineLocationGranted =
                        permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseLocationGranted =
                        permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                state.hasLocationPermission.value = fineLocationGranted || coarseLocationGranted
            }

    // 检查Termux是否在运行 - 单独函数
    fun checkTermuxRunning() {
        val wasRunning = isTermuxRunning.value
        isTermuxRunning.value = TermuxUtils.isTermuxRunning(context)
        // 只在状态变化时打印日志，避免过多日志
        if (wasRunning != isTermuxRunning.value) {
            Log.d(TAG, "Termux运行状态变更: ${isTermuxRunning.value}")
        }
    }

    // 启动Termux函数
    val startTermux: () -> Unit = {
        scope.launch {
            // 先检查Termux是否运行
            checkTermuxRunning()

            if (!isTermuxRunning.value) {
                // 简单地尝试启动Termux
                if (TermuxInstaller.openTermux(context)) {
                    Toast.makeText(context, "已发送启动Termux命令", Toast.LENGTH_SHORT).show()
                    // 给Termux一点启动时间
                    delay(1000)
                    // 再次检查状态，更新UI
                    checkTermuxRunning()
                } else {
                    Toast.makeText(context, "启动Termux失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Termux已经在运行中，显示通知
                Toast.makeText(context, "Termux已在运行中", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 检查Tuna源是否启用 - 只在初始加载时调用一次
    suspend fun checkTunaSourceEnabled() {
        try {
            // 更可靠的检测方式: 直接查看文件内容
            val result = executeTermuxCommand(context, "cat ${'$'}PREFIX/etc/apt/sources.list")

            // 检查文件内容中是否包含清华源URL
            val containsTuna =
                    result.success &&
                            (result.stdout.contains("mirrors.tuna.tsinghua.edu.cn") ||
                                    result.stdout.contains("tsinghua.edu.cn"))

            Log.d(TAG, "源文件内容: ${result.stdout}")
            Log.d(TAG, "检查清华源: $containsTuna")

            isTunaSourceEnabled.value = containsTuna
        } catch (e: Exception) {
            Log.e(TAG, "检查清华源错误: ${e.message}")
            isTunaSourceEnabled.value = false
        }
    }

    // 检查Python是否安装 - 只在初始加载时调用一次
    suspend fun checkPythonInstalled() {
        try {
            val result = executeTermuxCommand(context, "command -v python3")
            isPythonInstalled.value = result.success && result.stdout.contains("python3")
            Log.d(TAG, "检查Python: ${isPythonInstalled.value}")
        } catch (e: Exception) {
            Log.e(TAG, "检查Python错误: ${e.message}")
            isPythonInstalled.value = false
        }
    }

    // 检查Node.js是否安装 - 只在初始加载时调用一次
    suspend fun checkNodeInstalled() {
        try {
            val result = executeTermuxCommand(context, "command -v node")
            isNodeInstalled.value = result.success && result.stdout.contains("node")
            Log.d(TAG, "检查Node.js: ${isNodeInstalled.value}")
        } catch (e: Exception) {
            Log.e(TAG, "检查Node.js错误: ${e.message}")
            isNodeInstalled.value = false
        }
    }

    // 检查已安装的组件 - 只在初始加载和授权成功后调用一次
    suspend fun checkInstalledComponents() {
        withContext(Dispatchers.IO) {
            // 只在Termux已安装且已授权时才检查
            if (state.isTermuxInstalled.value && state.isTermuxAuthorized.value) {
                // 检查Termux是否在运行
                checkTermuxRunning()

                checkTunaSourceEnabled()
                checkPythonInstalled()
                checkNodeInstalled()

                // 检查完成后，更新持久化状态
                val allConfigured =
                        isTunaSourceEnabled.value &&
                                isPythonInstalled.value &&
                                isNodeInstalled.value

                if (allConfigured) {
                    // 如果所有组件都已配置好，保存持久化状态
                    if (!isTermuxFullyConfigured.value) {
                        saveTermuxConfigStatus(context, true)
                        isTermuxFullyConfigured.value = true
                        Log.d(TAG, "所有Termux组件配置完成，保存持久化状态")
                    }
                } else if (isTermuxFullyConfigured.value) {
                    // 如果之前认为配置完成，但现在检测到有组件未配置，重置状态
                    saveTermuxConfigStatus(context, false)
                    isTermuxFullyConfigured.value = false
                    Log.d(
                            TAG,
                            "检测到Termux组件配置不完整（清华源: ${isTunaSourceEnabled.value}, Python: ${isPythonInstalled.value}, Node: ${isNodeInstalled.value}），重置持久化状态"
                    )
                }
            }
        }
    }

    // 配置清华源
    val configureTunaSource = {
        scope.launch {
            // 先检查Termux是否在运行
            checkTermuxRunning()
            if (!isTermuxRunning.value) {
                outputText += "\nTermux未运行，请先启动Termux"
                Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                return@launch
            }

            isTermuxConfiguring.value = true
            currentTask = "配置清华源"
            outputText += "\n开始配置清华源..."
            try {
                // 备份原有sources.list
                val backupResult =
                        executeTermuxCommand(
                                context,
                                "cp ${'$'}PREFIX/etc/apt/sources.list ${'$'}PREFIX/etc/apt/sources.list.bak"
                        )
                outputText += "\n备份原始软件源配置${if (backupResult.success) "成功" else "失败"}"

                // 设置清华源 - 使用更明确的方式
                val setTunaSourceResult =
                        executeTermuxCommand(
                                context,
                                """
                    echo "# 清华大学开源软件镜像站 Termux 镜像源
deb https://mirrors.tuna.tsinghua.edu.cn/termux/termux-packages-24 stable main" > ${'$'}PREFIX/etc/apt/sources.list
                    """
                        )
                outputText += "\n设置清华源${if (setTunaSourceResult.success) "成功" else "失败"}"

                // 更新软件包列表
                outputText += "\n正在更新软件包列表..."
                val updateResult = executeTermuxCommand(context, "apt update -y")
                outputText += "\n软件包列表更新${if (updateResult.success) "成功" else "失败"}"

                if (setTunaSourceResult.success && updateResult.success) {
                    isTunaSourceEnabled.value = true
                    outputText += "\n清华源配置成功！"
                    // 重新检查，确保状态正确
                    checkTunaSourceEnabled()

                    // 检查是否所有组件都已配置完成
                    if (isTunaSourceEnabled.value &&
                                    isPythonInstalled.value &&
                                    isNodeInstalled.value
                    ) {
                        saveTermuxConfigStatus(context, true)
                        isTermuxFullyConfigured.value = true
                        outputText += "\n所有Termux组件已配置完成，配置状态已保存！"
                    }

                    Toast.makeText(context, "清华源设置成功", Toast.LENGTH_SHORT).show()
                } else {
                    outputText += "\n清华源配置失败，正在恢复备份..."
                    // 恢复备份
                    executeTermuxCommand(
                            context,
                            "cp ${'$'}PREFIX/etc/apt/sources.list.bak ${'$'}PREFIX/etc/apt/sources.list"
                    )
                    executeTermuxCommand(context, "apt update -y")
                    Toast.makeText(context, "清华源设置失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "配置清华源错误: ${e.message}")
                outputText += "\n配置清华源出错: ${e.message}"
                Toast.makeText(context, "配置清华源出错", Toast.LENGTH_SHORT).show()
            } finally {
                isTermuxConfiguring.value = false
                currentTask = ""
                // 自动关闭对话框
                state.showResultDialogState.value = false
            }
        }
    }

    // 安装Python环境
    val installPython = {
        scope.launch {
            // 简单地检查Termux是否在运行
            checkTermuxRunning()
            if (!isTermuxRunning.value) {
                outputText += "\nTermux未运行，请先启动Termux"
                Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                return@launch
            }

            isTermuxConfiguring.value = true
            currentTask = "安装Python"
            outputText += "\n开始安装Python环境..."
            try {
                // 安装Python和pip
                outputText += "\n正在安装Python..."
                val installPythonResult = executeTermuxCommand(context, "apt install -y python")
                outputText += "\nPython安装${if (installPythonResult.success) "成功" else "失败"}"

                if (installPythonResult.success) {
                    // 安装常用的pip包
                    outputText += "\n正在配置pip..."
                    val upgradeResult = executeTermuxCommand(context, "pip install --upgrade pip")
                    outputText += "\npip升级${if (upgradeResult.success) "成功" else "失败"}"

                    isPythonInstalled.value = true
                    outputText += "\nPython环境安装成功！"

                    // 检查是否所有组件都已配置完成
                    if (isTunaSourceEnabled.value &&
                                    isPythonInstalled.value &&
                                    isNodeInstalled.value
                    ) {
                        saveTermuxConfigStatus(context, true)
                        isTermuxFullyConfigured.value = true
                        outputText += "\n所有Termux组件已配置完成，配置状态已保存！"
                    }

                    Toast.makeText(context, "Python安装成功", Toast.LENGTH_SHORT).show()
                } else {
                    outputText += "\nPython安装失败"
                    Toast.makeText(context, "Python安装失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "安装Python环境错误: ${e.message}")
                outputText += "\n安装Python环境出错: ${e.message}"
                Toast.makeText(context, "安装Python环境出错", Toast.LENGTH_SHORT).show()
            } finally {
                isTermuxConfiguring.value = false
                currentTask = ""
                // 自动关闭对话框
                state.showResultDialogState.value = false
            }
        }
    }

    // 安装Node.js环境
    val installNode = {
        scope.launch {
            // 简单地检查Termux是否在运行
            checkTermuxRunning()
            if (!isTermuxRunning.value) {
                outputText += "\nTermux未运行，请先启动Termux"
                Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                return@launch
            }

            isTermuxConfiguring.value = true
            currentTask = "安装Node.js"
            outputText += "\n开始安装Node.js环境..."
            try {
                // 安装Node.js
                outputText += "\n正在安装Node.js..."
                val installNodeResult = executeTermuxCommand(context, "apt install -y nodejs")
                outputText += "\nNode.js安装${if (installNodeResult.success) "成功" else "失败"}"

                if (installNodeResult.success) {
                    isNodeInstalled.value = true
                    outputText += "\nNode.js环境安装成功！"

                    // 检查是否所有组件都已配置完成
                    if (isTunaSourceEnabled.value &&
                                    isPythonInstalled.value &&
                                    isNodeInstalled.value
                    ) {
                        saveTermuxConfigStatus(context, true)
                        isTermuxFullyConfigured.value = true
                        outputText += "\n所有Termux组件已配置完成，配置状态已保存！"
                    }

                    Toast.makeText(context, "Node.js安装成功", Toast.LENGTH_SHORT).show()
                } else {
                    outputText += "\nNode.js安装失败"
                    Toast.makeText(context, "Node.js安装失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "安装Node.js环境错误: ${e.message}")
                outputText += "\n安装Node.js环境出错: ${e.message}"
                Toast.makeText(context, "安装Node.js环境出错", Toast.LENGTH_SHORT).show()
            } finally {
                isTermuxConfiguring.value = false
                currentTask = ""
                // 自动关闭对话框
                state.showResultDialogState.value = false
            }
        }
    }

    // 刷新状态函数 - 不要重复检查Termux配置，减少不必要的跳转
    val refreshStatus = {
        Log.d(TAG, "刷新应用权限状态...")

        // 检查Shizuku安装、运行和权限状态
        state.isShizukuInstalled.value = AdbCommandExecutor.isShizukuInstalled(context)
        state.isShizukuRunning.value = AdbCommandExecutor.isShizukuServiceRunning()

        // 更明确地检查 moe.shizuku.manager.permission.API_V23 权限
        val hasShizukuPermission =
                if (state.isShizukuInstalled.value && state.isShizukuRunning.value) {
                    AdbCommandExecutor.hasShizukuPermission()
                } else {
                    false
                }
        state.hasShizukuPermission.value = hasShizukuPermission

        // 如果Shizuku权限检查失败，确保Shizuku向导卡片显示
        if (!state.hasShizukuPermission.value) {
            Log.d(TAG, "缺少Shizuku权限，显示Shizuku向导卡片")
            // 强制显示Shizuku向导卡片
            state.showShizukuWizard.value = true
        }

        // 检查Termux是否安装及检查清单中是否声明了RUN_COMMAND权限
        state.isTermuxInstalled.value = TermuxInstaller.isTermuxInstalled(context)

        // 检查Termux是否在运行 - 确保每次刷新状态时都检查Termux运行状态
        checkTermuxRunning()

        // 检查应用清单中是否声明了RUN_COMMAND权限
        var hasTermuxPermissionDeclared = false
        try {
            val packageInfo =
                    context.packageManager.getPackageInfo(
                            context.packageName,
                            PackageManager.GET_PERMISSIONS
                    )
            val declaredPermissions = packageInfo.requestedPermissions ?: emptyArray()
            hasTermuxPermissionDeclared =
                    declaredPermissions.any { it == "com.termux.permission.RUN_COMMAND" }

            if (!hasTermuxPermissionDeclared) {
                Log.w(TAG, "应用清单中未声明Termux RUN_COMMAND权限，Termux功能将无法正常运行")
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查应用权限声明时出错: ${e.message}")
        }

        // 检查存储权限
        state.hasStoragePermission.value =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    context.checkSelfPermission(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                            context.checkSelfPermission(
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }

        // 检查位置权限
        state.hasLocationPermission.value =
                context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        context.checkSelfPermission(
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // 检查悬浮窗权限
        state.hasOverlayPermission.value = Settings.canDrawOverlays(context)

        // 检查电池优化豁免
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        state.hasBatteryOptimizationExemption.value =
                powerManager.isIgnoringBatteryOptimizations(context.packageName)

        // 检查无障碍服务状态
        state.hasAccessibilityServiceEnabled.value =
                UIHierarchyManager.isAccessibilityServiceEnabled(context)
    }

    // 检查Termux授权状态 - 单独优化
    fun checkTermuxAuth() {
        scope.launch {
            // 这里只在特定情况下检查，而不是无条件检查
            if (state.isTermuxInstalled.value) {
                // 检查Termux是否在运行
                checkTermuxRunning()

                // 更明确地检查Termux RUN_COMMAND权限
                state.isTermuxAuthorized.value = TermuxAuthorizer.isTermuxAuthorized(context)

                // 如果Termux未授权，确保Termux向导卡片显示
                if (!state.isTermuxAuthorized.value) {
                    // 强制显示Termux向导卡片
                    state.showTermuxWizard.value = true
                }

                // 只有在授权通过后才检查各项配置
                if (state.isTermuxAuthorized.value) {
                    // 即使isTermuxFullyConfigured为true也执行检查，确保状态是最新的
                    // 但不会影响初始UI显示，因为isTermuxFullyConfigured已经在初始化时设置
                    checkInstalledComponents()
                }
            }
        }
    }

    // 注册状态变更监听器
    DisposableEffect(Unit) {
        val shizukuListener: () -> Unit = {
            refreshStatus()
            // 在Shizuku状态变化时检查Termux授权状态
            checkTermuxAuth()
        }
        AdbCommandExecutor.addStateChangeListener(shizukuListener)

        val termuxListener: () -> Unit = {
            refreshStatus()
            // 在Termux状态变化时检查授权状态
            checkTermuxAuth()
        }
        TermuxInstaller.addStateChangeListener(termuxListener)

        onDispose {
            AdbCommandExecutor.removeStateChangeListener(shizukuListener)
            TermuxInstaller.removeStateChangeListener(termuxListener)
        }
    }

    // 初始状态加载
    LaunchedEffect(Unit) {
        Log.d(TAG, "初始化时加载各项权限和配置状态")

        // 先刷新基本状态（包括检查Shizuku是否安装、运行）
        refreshStatus()

        // 专门检查Shizuku API_V23权限
        if (state.isShizukuInstalled.value && state.isShizukuRunning.value) {
            // 明确检查 moe.shizuku.manager.permission.API_V23 权限
            state.hasShizukuPermission.value = AdbCommandExecutor.hasShizukuPermission()

            // 如果没有Shizuku权限，强制显示Shizuku向导卡片
            if (!state.hasShizukuPermission.value) {
                Log.d(TAG, "缺少Shizuku API_V23权限，显示Shizuku向导卡片")
                state.showShizukuWizard.value = true
            }
        } else {
            state.hasShizukuPermission.value = false
            state.showShizukuWizard.value = true
        }

        // 检查Termux安装和权限状态
        // com.termux.permission.RUN_COMMAND权限检查包含在isTermuxAuthorized中
        if (state.isTermuxInstalled.value) {
            // 检查Termux是否在运行
            checkTermuxRunning()

            state.isTermuxAuthorized.value = TermuxAuthorizer.isTermuxAuthorized(context)

            // 如果Termux未授权，显示Termux向导卡片
            if (!state.isTermuxAuthorized.value) {
                Log.d(TAG, "缺少Termux RUN_COMMAND权限或配置，显示Termux向导卡片")
                state.showTermuxWizard.value = true
            } else {
                // 如果Termux已获得权限，进行组件检查
                Log.d(TAG, "Termux权限验证通过，验证组件配置状态")
                checkInstalledComponents() // 该函数会自动更新持久化状态
            }
        } else {
            state.isTermuxAuthorized.value = false
            state.showTermuxWizard.value = true
        }
    }

    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                text = "系统权限状态",
                style = MaterialTheme.typography.headlineSmall,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
        )

        // 设备信息卡片
        DeviceInfoCard(
                androidVersion = Build.VERSION.RELEASE,
                apiLevel = Build.VERSION.SDK_INT,
                deviceModel = Build.MODEL,
                manufacturer = Build.MANUFACTURER
        )

        // Shizuku向导卡片 - 如果Shizuku未完全设置则显示
        if (!state.isShizukuInstalled.value ||
                        !state.isShizukuRunning.value ||
                        !state.hasShizukuPermission.value
        ) {
            ShizukuWizardCard(
                    isShizukuInstalled = state.isShizukuInstalled.value,
                    isShizukuRunning = state.isShizukuRunning.value,
                    hasShizukuPermission = state.hasShizukuPermission.value,
                    showWizard = state.showShizukuWizard.value,
                    onToggleWizard = { state.showShizukuWizard.value = it },
                    onInstallFromStore = {
                        try {
                            val intent =
                                    Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(
                                                    "market://details?id=moe.shizuku.privileged.api"
                                            )
                                    )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // 如果没有安装应用市场，打开浏览器
                            val intent =
                                    Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(
                                                    "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"
                                            )
                                    )
                            context.startActivity(intent)
                        }
                    },
                    onInstallBundled = {
                        if (ShizukuInstaller.installBundledShizuku(context)) {
                            Toast.makeText(context, "正在安装内置版本...", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "安装失败", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenShizuku = {
                        try {
                            val intent =
                                    context.packageManager.getLaunchIntentForPackage(
                                            "moe.shizuku.privileged.api"
                                    )
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "无法找到Shizuku应用", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法启动Shizuku应用", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onWatchTutorial = {
                        try {
                            val videoUrl = "https://shizuku.rikka.app/zh-hans/guide/setup/"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开文档链接", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRequestPermission = {
                        AdbCommandExecutor.requestShizukuPermission { granted ->
                            if (granted) {
                                Toast.makeText(context, "Shizuku权限已授予", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Shizuku权限请求被拒绝", Toast.LENGTH_SHORT).show()
                            }
                            refreshStatus()
                        }
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Termux向导卡片 - 只有在需要配置时才显示
        if (!state.isTermuxInstalled.value ||
                        !state.isTermuxAuthorized.value ||
                        !isTermuxFullyConfigured.value
        ) {
            TermuxWizardCard(
                    isTermuxInstalled = state.isTermuxInstalled.value,
                    isTermuxAuthorized = state.isTermuxAuthorized.value,
                    showWizard = state.showTermuxWizard.value,
                    onToggleWizard = { state.showTermuxWizard.value = it },
                    // 添加新的状态参数 - 如果持久化记录显示已配置，则直接显示为已配置
                    isTunaSourceEnabled =
                            isTermuxFullyConfigured.value || isTunaSourceEnabled.value,
                    isPythonInstalled = isTermuxFullyConfigured.value || isPythonInstalled.value,
                    isNodeInstalled = isTermuxFullyConfigured.value || isNodeInstalled.value,
                    isTermuxRunning = isTermuxRunning.value,
                    // 添加启动Termux的回调
                    onStartTermux = startTermux,
                    // 添加新的回调函数
                    onConfigureTunaSource = {
                        if (!isTermuxConfiguring.value) {
                            // 用户点击配置清华源
                            state.resultDialogTitle.value = "配置清华源"
                            state.resultDialogContent.value = outputText
                            state.showResultDialogState.value = true
                            configureTunaSource()
                        } else {
                            Toast.makeText(context, "请等待当前配置完成", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onInstallPythonEnv = {
                        if (!isTermuxConfiguring.value) {
                            // 用户点击安装Python环境
                            state.resultDialogTitle.value = "安装Python环境"
                            state.resultDialogContent.value = outputText
                            state.showResultDialogState.value = true
                            installPython()
                        } else {
                            Toast.makeText(context, "请等待当前配置完成", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onInstallNodeEnv = {
                        if (!isTermuxConfiguring.value) {
                            // 用户点击安装Node.js环境
                            state.resultDialogTitle.value = "安装Node.js环境"
                            state.resultDialogContent.value = outputText
                            state.showResultDialogState.value = true
                            installNode()
                        } else {
                            Toast.makeText(context, "请等待当前配置完成", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onInstallBundled = {
                        try {
                            // 从assets目录提取Termux APK并安装
                            val apkFile = File(context.cacheDir, "termux.apk")

                            context.assets.open("termux.apk").use { inputStream ->
                                FileOutputStream(apkFile).use { outputStream ->
                                    val buffer = ByteArray(4 * 1024)
                                    var read: Int
                                    while (inputStream.read(buffer).also { read = it } != -1) {
                                        outputStream.write(buffer, 0, read)
                                    }
                                    outputStream.flush()
                                }
                            }

                            // 生成APK的URI，考虑文件提供者权限
                            val apkUri =
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                apkFile
                                        )
                                    } else {
                                        Uri.fromFile(apkFile)
                                    }

                            // 创建安装意图
                            val installIntent =
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(
                                                apkUri,
                                                "application/vnd.android.package-archive"
                                        )
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    }

                            // 启动安装界面
                            context.startActivity(installIntent)
                            Toast.makeText(context, "正在安装内置的Termux，请授予安装权限", Toast.LENGTH_LONG)
                                    .show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "安装内置Termux失败，请尝试从应用商店安装", Toast.LENGTH_SHORT)
                                    .show()
                        }
                    },
                    onOpenTermux = {
                        try {
                            val intent =
                                    context.packageManager.getLaunchIntentForPackage("com.termux")
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "无法找到Termux应用", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法启动Termux应用", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onAuthorizeTermux = {
                        // 授权Termux
                        scope.launch {
                            outputText = "欢迎使用Termux配置工具\n开始授权Termux..."

                            // 先检查Termux是否在运行
                            checkTermuxRunning()
                            if (!isTermuxRunning.value) {
                                outputText += "\nTermux未运行，将尝试启动Termux..."
                                if (TermuxInstaller.openTermux(context)) {
                                    outputText += "\n已启动Termux，请稍等..."
                                    // 简单等待1秒钟
                                    delay(1000)
                                    // 再次检查状态
                                    checkTermuxRunning()
                                    if (!isTermuxRunning.value) {
                                        outputText += "\nTermux似乎未成功启动，请手动启动Termux后再尝试"
                                        Toast.makeText(context, "请手动启动Termux", Toast.LENGTH_SHORT)
                                                .show()
                                        return@launch
                                    }
                                    outputText += "\nTermux已启动"
                                } else {
                                    outputText += "\nTermux启动失败，请确保Termux已正确安装"
                                    Toast.makeText(context, "启动Termux失败", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                            }

                            isTermuxConfiguring.value = true
                            currentTask = "授权Termux"
                            state.resultDialogTitle.value = "授权Termux"
                            state.resultDialogContent.value = outputText
                            state.showResultDialogState.value = true

                            try {
                                // 先检查Shizuku权限
                                if (!AdbCommandExecutor.hasShizukuPermission()) {
                                    outputText += "\n缺少Shizuku API_V23权限，无法执行授权操作"
                                    outputText += "\n请先点击Shizuku卡片，完成Shizuku设置和授权"
                                    currentTask = "Termux授权失败"
                                    state.resultDialogContent.value = outputText
                                    delay(3000) // 给用户时间阅读错误信息
                                    return@launch
                                }

                                // 再检查应用清单中是否声明了RUN_COMMAND权限
                                val packageInfo =
                                        context.packageManager.getPackageInfo(
                                                context.packageName,
                                                PackageManager.GET_PERMISSIONS
                                        )
                                val declaredPermissions =
                                        packageInfo.requestedPermissions ?: emptyArray()
                                val hasPermissionDeclared =
                                        declaredPermissions.any {
                                            it == "com.termux.permission.RUN_COMMAND"
                                        }

                                if (!hasPermissionDeclared) {
                                    outputText += "\n应用清单中未声明Termux RUN_COMMAND权限"
                                    outputText += "\n请联系开发者修复此问题"
                                    currentTask = "Termux授权失败"
                                    state.resultDialogContent.value = outputText
                                    delay(3000) // 给用户时间阅读错误信息
                                    return@launch
                                }

                                outputText += "\n开始授予Termux权限..."

                                val success = TermuxAuthorizer.grantAllTermuxPermissions(context)

                                if (success) {
                                    outputText += "\nTermux授权成功！"
                                    state.isTermuxAuthorized.value = true
                                    Toast.makeText(context, "成功授权Termux", Toast.LENGTH_SHORT).show()

                                    // 授权成功后检查各项配置状态
                                    outputText += "\n正在检查Termux配置状态..."
                                    delay(1000) // 给授权一点时间完成

                                    // 先读取持久化状态
                                    isTermuxFullyConfigured.value = getTermuxConfigStatus(context)

                                    if (isTermuxFullyConfigured.value) {
                                        outputText += "\n检测到Termux已完成所有配置（从持久化记录）"
                                        // 即使有持久化记录，还是检查一下实际状态
                                        checkInstalledComponents()
                                    } else {
                                        // 没有持久化记录，正常检查组件状态
                                        checkInstalledComponents()
                                        outputText += "\n检查完成，请点击相应按钮进行配置"
                                    }
                                } else {
                                    outputText += "\nTermux授权失败，请确认以下事项:"
                                    outputText += "\n1. Shizuku服务是否正常运行"
                                    outputText +=
                                            "\n2. 应用是否在AndroidManifest中声明了com.termux.permission.RUN_COMMAND权限"
                                    outputText += "\n3. Termux应用是否已正确安装"
                                    Toast.makeText(
                                                    context,
                                                    "授权Termux失败，请检查Termux设置",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "授权Termux出错: ${e.message}")
                                outputText += "\n授权Termux出错: ${e.message}"
                            } finally {
                                isTermuxConfiguring.value = false
                                currentTask = ""
                                state.resultDialogContent.value = outputText
                                // 自动关闭对话框
                                state.showResultDialogState.value = false
                            }
                        }
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 权限状态卡片
        PermissionStatusCard(
                isRefreshing = state.isRefreshing.value,
                onRefresh = {
                    state.isRefreshing.value = true
                    scope.launch {
                        try {
                            // 使用与初始页面加载相同的逻辑进行刷新
                            refreshStatus()

                            // 与初始加载保持一致，检查Termux授权状态
                            if (state.isTermuxInstalled.value) {
                                state.isTermuxAuthorized.value =
                                        TermuxAuthorizer.isTermuxAuthorized(context)

                                // 读取持久化配置状态
                                isTermuxFullyConfigured.value = getTermuxConfigStatus(context)

                                // 仅在授权成功时进行组件检查
                                if (state.isTermuxAuthorized.value) {
                                    // 检查组件状态，该函数会自动更新持久化状态
                                    checkInstalledComponents()
                                }
                            } else {
                                state.isTermuxAuthorized.value = false

                                // 如果Termux未安装，重置配置状态
                                if (isTermuxFullyConfigured.value) {
                                    saveTermuxConfigStatus(context, false)
                                    isTermuxFullyConfigured.value = false
                                }

                                // 重置组件状态
                                isTunaSourceEnabled.value = false
                                isPythonInstalled.value = false
                                isNodeInstalled.value = false
                            }

                            // 给UI更新一些时间
                            delay(300)
                        } catch (e: Exception) {
                            Log.e(TAG, "刷新状态时出错: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "刷新状态时出错: ${e.message}", Toast.LENGTH_SHORT)
                                        .show()
                            }
                        } finally {
                            // 刷新完成
                            state.isRefreshing.value = false
                        }
                    }
                },
                hasStoragePermission = state.hasStoragePermission.value,
                hasOverlayPermission = state.hasOverlayPermission.value,
                hasBatteryOptimizationExemption = state.hasBatteryOptimizationExemption.value,
                hasAccessibilityServiceEnabled = state.hasAccessibilityServiceEnabled.value,
                hasLocationPermission = state.hasLocationPermission.value,
                isShizukuInstalled = state.isShizukuInstalled.value,
                isShizukuRunning = state.isShizukuRunning.value,
                hasShizukuPermission = state.hasShizukuPermission.value,
                isTermuxInstalled = state.isTermuxInstalled.value,
                isTermuxAuthorized = state.isTermuxAuthorized.value,
                isTermuxRunning = isTermuxRunning.value,
                onStoragePermissionClick = {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent =
                                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                            .apply {
                                                addCategory("android.intent.category.DEFAULT")
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                            context.startActivity(intent)
                        } else {
                            val intent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开存储权限设置", Toast.LENGTH_SHORT).show()
                    }
                },
                onOverlayPermissionClick = {
                    try {
                        val intent =
                                Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开悬浮窗权限设置", Toast.LENGTH_SHORT).show()
                    }
                },
                onBatteryOptimizationClick = {
                    try {
                        val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                    }
                },
                onAccessibilityClick = {
                    // 不鼓励使用
                    // UIHierarchyManager.openAccessibilitySettings(context)
                },
                onLocationPermissionClick = {
                    if (!state.hasLocationPermission.value) {
                        // 直接请求位置权限，而不是跳转到设置页面
                        locationPermissionLauncher.launch(
                                arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                        )
                    } else {
                        // 如果已有权限，可以跳转到设置页面进行管理
                        try {
                            val intent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开位置权限设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onShizukuClick = {
                    // 短按时如果Shizuku未完全设置，则显示向导
                    if (!state.isShizukuInstalled.value ||
                                    !state.isShizukuRunning.value ||
                                    !state.hasShizukuPermission.value
                    ) {
                        state.showShizukuWizard.value = !state.showShizukuWizard.value
                    }
                },
                onShizukuLongClick = {
                    // 长按时切换ADB命令执行器的显示状态
                    state.showAdbCommandExecutor.value = !state.showAdbCommandExecutor.value
                },
                onTermuxClick = {
                    // 短按时处理Termux
                    if (!state.isTermuxInstalled.value ||
                                    !state.isTermuxAuthorized.value ||
                                    !isTermuxFullyConfigured.value
                    ) {
                        // 如果未完全配置，显示向导
                        state.showTermuxWizard.value = !state.showTermuxWizard.value
                    } else {
                        // 如果已完全配置，尝试打开Termux
                        try {
                            val intent =
                                    context.packageManager.getLaunchIntentForPackage("com.termux")
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "无法打开Termux应用", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法启动Termux应用", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onTermuxLongClick = {
                    // 长按时切换Termux命令执行器的显示状态
                    state.showTermuxCommandExecutor.value = !state.showTermuxCommandExecutor.value
                },
                permissionErrorMessage = state.permissionErrorMessage.value,
                // 添加Termux配置状态参数，将持久化状态融合进显示逻辑
                isTunaSourceEnabled = isTunaSourceEnabled.value || isTermuxFullyConfigured.value,
                isPythonInstalled = isPythonInstalled.value || isTermuxFullyConfigured.value,
                isNodeInstalled = isNodeInstalled.value || isTermuxFullyConfigured.value
        )

        // 显示长按提示
        Text(
                text =
                        "提示：无障碍启动时，ui操作可以进行加速，但是手机部分软件可能会扫开启时长然后风控账号（是哪一系列抽象软件我就不说了，各位心里应该有数）。\n\n" +
                                "点击权限状态可设置权限，\n" +
                                "长按Shizuku服务可显示ADB命令执行器，\n" +
                                "长按Termux终端可显示Termux命令执行器",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
        )

        // 命令输入部分 - 只在用户长按Shizuku权限状态时显示
        if (state.showAdbCommandExecutor.value &&
                        state.isShizukuRunning.value &&
                        state.hasShizukuPermission.value
        ) {
            AdbCommandExecutor(
                    commandText = state.commandText.value,
                    onCommandTextChange = { state.commandText.value = it },
                    resultText = state.resultText.value,
                    showSampleCommands = state.showSampleCommands.value,
                    onToggleSampleCommands = {
                        state.showSampleCommands.value = !state.showSampleCommands.value
                    },
                    onExecuteCommand = {
                        if (state.commandText.value.isNotBlank()) {
                            scope.launch {
                                state.resultText.value = "执行中..."
                                val result =
                                        AdbCommandExecutor.executeAdbCommand(
                                                state.commandText.value
                                        )
                                state.resultText.value =
                                        if (result.success) {
                                            "命令执行成功:\n${result.stdout}"
                                        } else {
                                            "命令执行失败 (退出码: ${result.exitCode}):\n${result.stderr}"
                                        }
                            }
                        }
                    },
                    onSampleCommandSelected = { state.commandText.value = it }
            )
        } else if (state.showAdbCommandExecutor.value) {
            FeatureErrorCard(
                    message =
                            if (!state.isShizukuRunning.value) "请先启动Shizuku服务"
                            else if (!state.hasShizukuPermission.value) "请授予Shizuku权限"
                            else "无法使用ADB命令功能"
            )
        }

        // Termux命令执行器 - 只在用户长按Termux状态时显示
        if (state.showTermuxCommandExecutor.value && state.isTermuxInstalled.value) {
            TermuxCommandExecutor(
                    isTermuxAuthorized = state.isTermuxAuthorized.value,
                    commandText = state.commandText.value,
                    onCommandTextChange = { state.commandText.value = it },
                    showSampleCommands = state.showSampleCommands.value,
                    onToggleSampleCommands = {
                        state.showSampleCommands.value = !state.showSampleCommands.value
                    },
                    onSampleCommandSelected = { state.commandText.value = it },
                    onAuthorizeTermux = {
                        scope.launch {
                            state.resultText.value = "正在授权Termux..."
                            val authorized = TermuxAuthorizer.grantAllTermuxPermissions(context)
                            if (authorized) {
                                state.resultText.value = "Termux授权成功！"
                                state.isTermuxAuthorized.value = true

                                // 授权成功后检查各项配置状态
                                checkInstalledComponents()
                            } else {
                                state.resultText.value = "Termux授权失败，请检查Shizuku权限和应用权限"
                            }
                        }
                    }
            )
        } else if (state.showTermuxCommandExecutor.value && !state.isTermuxInstalled.value) {
            FeatureErrorCard(message = "请先安装Termux应用")
        }
    }

    // 显示命令结果对话框
    CommandResultDialog(
            showDialog = state.showResultDialogState.value,
            onDismiss = {
                // 只有当未在配置过程中时才允许手动关闭
                if (!isTermuxConfiguring.value) {
                    state.showResultDialogState.value = false
                    // 重置输出文本，准备下一次操作
                    outputText = "欢迎使用Termux配置工具\n点击对应按钮开始配置"
                }
            },
            title = state.resultDialogTitle.value,
            content =
                    if (isTermuxConfiguring.value) {
                        // 当正在配置时，显示实时输出
                        "$outputText\n\n${if (currentTask.isNotEmpty()) "正在执行: $currentTask..." else ""}"
                    } else {
                        // 当配置完成时，显示最终结果
                        outputText
                    },
            context = context,
            // 当正在配置时不显示按钮，不可复制，配置完成后恢复按钮
            showButtons = !isTermuxConfiguring.value,
            allowCopy = !isTermuxConfiguring.value
    )
}
