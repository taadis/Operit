package com.ai.assistance.operit.ui.features.demo.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.ShizukuInstaller
import com.ai.assistance.operit.ui.features.demo.components.*
import com.ai.assistance.operit.ui.features.demo.helpers.*
import com.ai.assistance.operit.ui.features.demo.model.ShizukuScreenState
import com.ai.assistance.operit.ui.features.demo.utils.getTermuxConfigStatus
import com.ai.assistance.operit.ui.features.demo.wizards.ShizukuWizardCard
import com.ai.assistance.operit.ui.features.demo.wizards.TermuxWizardCard
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val isUvInstalled = remember { mutableStateOf(false) }
    val isNodeInstalled = remember { mutableStateOf(false) }
    val isTermuxConfiguring = remember { mutableStateOf(false) }
    val isTermuxRunning = remember { mutableStateOf(false) }
    val isTermuxBatteryOptimizationExempted = remember { mutableStateOf(false) }

    // 添加Termux配置持久化状态 - 在初始化阶段就读取，避免界面闪烁
    val isTermuxFullyConfigured = remember {
        // 立即读取持久化配置，避免LaunchedEffect中读取导致的UI闪烁
        val savedStatus = getTermuxConfigStatus(context)
        mutableStateOf(savedStatus)
    }

    // 输出文本
    var outputText by remember { mutableStateOf("欢迎使用Termux配置工具\n点击对应按钮开始配置") }
    var currentTask by remember { mutableStateOf("") }

    // 检查已安装组件的函数
    val checkInstalledComponentsFunc: () -> Unit = {
        scope.launch {
            checkInstalledComponents(
                                context = context,
                isTermuxRunning = isTermuxRunning.value,
                isTermuxAuthorized = state.isTermuxAuthorized.value,
                isTunaSourceEnabled = isTunaSourceEnabled.value,
                isPythonInstalled = isPythonInstalled.value,
                isUvInstalled = isUvInstalled.value,
                isNodeInstalled = isNodeInstalled.value,
                isTermuxBatteryOptimizationExempted = isTermuxBatteryOptimizationExempted.value,
                updateConfigStatus = { isTermuxFullyConfigured.value = it },
                updateSourceStatus = { isTunaSourceEnabled.value = it },
                updatePythonStatus = { isPythonInstalled.value = it },
                updateUvStatus = { isUvInstalled.value = it },
                updateNodeStatus = { isNodeInstalled.value = it },
                updateBatteryStatus = { isTermuxBatteryOptimizationExempted.value = it }
            )
        }
    }

    // 刷新状态函数
    val refreshStatus: () -> Unit = {
        refreshAppStatus(
            context = context,
            state = state,
            scope = scope,
            isTermuxFullyConfigured = isTermuxFullyConfigured,
            checkInstalledComponentsFunc = checkInstalledComponentsFunc
        )
    }

    // 检查Termux授权状态
    val checkTermuxAuth: () -> Unit = {
        scope.launch {
            checkTermuxAuthState(
                context = context,
                state = state,
                scope = scope,
                isTermuxFullyConfigured = isTermuxFullyConfigured,
                checkInstalledComponentsFunc = checkInstalledComponentsFunc
            )
        }
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

    // 配置清华源
    val configureTunaSourceFunc: () -> Unit = {
        scope.launch {
            // 先检查Termux是否在运行
            val isRunning = checkTermuxRunning(context)
            isTermuxRunning.value = isRunning
            
            if (!isRunning) {
                outputText += "\nTermux未运行，请先启动Termux"
                Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                return@launch
            }

            isTermuxConfiguring.value = true
            currentTask = "配置清华源"
            outputText += "\n开始配置清华源..."
            
            try {
                outputText = configureTunaSource(
                    context = context,
                    updateOutputText = { outputText = it },
                    updateSourceStatus = { isTunaSourceEnabled.value = it },
                    updateConfigStatus = { isTermuxFullyConfigured.value = it },
                    isTunaSourceEnabled = isTunaSourceEnabled.value,
                    isPythonInstalled = isPythonInstalled.value,
                    isUvInstalled = isUvInstalled.value,
                    isNodeInstalled = isNodeInstalled.value,
                    currentOutputText = outputText
                )
            } finally {
                isTermuxConfiguring.value = false
                currentTask = ""
                // 自动关闭对话框
                state.showResultDialogState.value = false
            }
        }
    }

    // 安装Python
    val installPythonFunc: () -> Unit = {
        scope.launch {
            // 先检查Termux是否在运行
            val isRunning = checkTermuxRunning(context)
            isTermuxRunning.value = isRunning
            
            if (!isRunning) {
                outputText += "\nTermux未运行，请先启动Termux"
                Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                return@launch
            }

            isTermuxConfiguring.value = true
            currentTask = "安装Python"
            outputText += "\n开始安装Python..."
            
            try {
                outputText = installPython(
                    context = context,
                    updateOutputText = { outputText = it },
                    updatePythonStatus = { isPythonInstalled.value = it },
                    updateConfigStatus = { isTermuxFullyConfigured.value = it },
                    isTunaSourceEnabled = isTunaSourceEnabled.value,
                    isPythonInstalled = isPythonInstalled.value,
                    isUvInstalled = isUvInstalled.value,
                    isNodeInstalled = isNodeInstalled.value,
                    currentOutputText = outputText
                )
            } finally {
                isTermuxConfiguring.value = false
                currentTask = ""
                // 自动关闭对话框
                state.showResultDialogState.value = false
            }
        }
    }

    // 安装UV包管理工具
    val installUvFunc: () -> Unit = {
        scope.launch {
            // 先检查Termux是否在运行
            val isRunning = checkTermuxRunning(context)
            isTermuxRunning.value = isRunning
            
            if (!isRunning) {
                outputText += "\nTermux未运行，请先启动Termux"
                Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                return@launch
            }

            isTermuxConfiguring.value = true
            currentTask = "安装UV"
            outputText += "\n开始安装UV..."
            
            try {
                outputText = installUv(
                    context = context,
                    updateOutputText = { outputText = it },
                    updateUvStatus = { isUvInstalled.value = it },
                    updateConfigStatus = { isTermuxFullyConfigured.value = it },
                    isTunaSourceEnabled = isTunaSourceEnabled.value,
                    isPythonInstalled = isPythonInstalled.value,
                    isUvInstalled = isUvInstalled.value,
                    isNodeInstalled = isNodeInstalled.value,
                    currentOutputText = outputText
                )
            } finally {
                isTermuxConfiguring.value = false
                currentTask = ""
                // 自动关闭对话框
                state.showResultDialogState.value = false
            }
        }
    }

    // 安装Node.js
    val installNodeFunc: () -> Unit = {
        scope.launch {
            // 先检查Termux是否在运行
            val isRunning = checkTermuxRunning(context)
            isTermuxRunning.value = isRunning
            
            if (!isRunning) {
                outputText += "\nTermux未运行，请先启动Termux"
                Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                return@launch
            }

            isTermuxConfiguring.value = true
            currentTask = "安装Node.js"
            outputText += "\n开始安装Node.js..."
            
            try {
                outputText = installNode(
                    context = context,
                    updateOutputText = { outputText = it },
                    updateNodeStatus = { isNodeInstalled.value = it },
                    updateConfigStatus = { isTermuxFullyConfigured.value = it },
                    isTunaSourceEnabled = isTunaSourceEnabled.value,
                    isPythonInstalled = isPythonInstalled.value,
                    isUvInstalled = isUvInstalled.value, 
                    isNodeInstalled = isNodeInstalled.value,
                    currentOutputText = outputText
                )
            } finally {
                isTermuxConfiguring.value = false
                currentTask = ""
                // 自动关闭对话框
                state.showResultDialogState.value = false
            }
        }
    }

    // 请求Termux电池优化豁免
    val requestTermuxBatteryOptimizationFunc: () -> Unit = {
        scope.launch {
            // 先检查Termux是否在运行
            val isRunning = checkTermuxRunning(context)
            isTermuxRunning.value = isRunning
            
            if (!isRunning) {
                outputText += "\nTermux未运行，请先启动Termux"
                Toast.makeText(context, "请先启动Termux", Toast.LENGTH_SHORT).show()
                return@launch
            }

            isTermuxConfiguring.value = true
            currentTask = "设置Termux电池优化豁免"
            outputText += "\n开始设置Termux电池优化豁免..."
            try {
                requestTermuxBatteryOptimization(context)
                outputText += "\n已打开Termux电池优化设置页面。请在系统设置中点击「允许」以豁免Termux的电池优化。"
                outputText += "\n完成设置后，请返回本应用并点击刷新按钮检查状态。"
            } finally {
                isTermuxConfiguring.value = false
                currentTask = ""
                // 自动关闭对话框
                state.showResultDialogState.value = false
            }
        }
    }

    // 授权Termux
    val authorizeTermuxFunc: () -> Unit = {
        scope.launch {
            isTermuxConfiguring.value = true
            currentTask = "授权Termux"
            state.resultDialogTitle.value = "授权Termux"
            state.resultDialogContent.value = outputText
            state.showResultDialogState.value = true

            try {
                outputText = authorizeTermux(
                    context = context,
                    updateOutputText = { outputText = it },
                    updateTermuxAuthorized = { state.isTermuxAuthorized.value = it },
                    updateTermuxRunning = { isTermuxRunning.value = it },
                    currentOutputText = outputText
                )
                
                // 授权成功后进行组件检查
                if (state.isTermuxAuthorized.value) {
                    checkInstalledComponentsFunc()
                }
            } finally {
                isTermuxConfiguring.value = false
                currentTask = ""
                state.resultDialogContent.value = outputText
                // 自动关闭对话框
                state.showResultDialogState.value = false
            }
        }
    }

    // 启动Termux函数
    val startTermuxFunc: () -> Unit = {
        scope.launch {
            startTermux(context) { isRunning ->
                isTermuxRunning.value = isRunning
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
        ShizukuAuthorizer.addStateChangeListener(shizukuListener)

        val termuxListener: () -> Unit = {
            refreshStatus()
            // 在Termux状态变化时检查授权状态
            checkTermuxAuth()
        }
        
        initStateListeners(state, refreshStatus, checkTermuxAuth)

        onDispose {
            removeStateListeners(shizukuListener, termuxListener)
        }
    }

    // 初始状态加载
    LaunchedEffect(Unit) {
        initializeState(
            context = context,
            state = state,
            scope = scope,
            isTunaSourceEnabled = isTunaSourceEnabled,
            isPythonInstalled = isPythonInstalled,
            isUvInstalled = isUvInstalled,
            isNodeInstalled = isNodeInstalled,
            isTermuxBatteryOptimizationExempted = isTermuxBatteryOptimizationExempted,
            isTermuxFullyConfigured = isTermuxFullyConfigured,
            updateConfigStatus = { isTermuxFullyConfigured.value = it },
            checkInstalledComponentsFunc = checkInstalledComponentsFunc
        )
    }

    // 显示对话框函数
    val showDialogFunc: (String, String) -> Unit = { title, content ->
        state.resultDialogTitle.value = title
        state.resultDialogContent.value = content
        state.showResultDialogState.value = true
    }

    // 显示结果对话框函数
    val showResultDialog: () -> Unit = {
        state.resultDialogTitle.value = "配置结果"
        state.resultDialogContent.value = outputText
        state.showResultDialogState.value = true
    }

    // 关闭对话框
    val closeDialog: () -> Unit = {
        state.showResultDialogState.value = false
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
                        ShizukuAuthorizer.requestShizukuPermission { granted ->
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
                    isUvInstalled = isTermuxFullyConfigured.value || isUvInstalled.value,
                    isNodeInstalled = isTermuxFullyConfigured.value || isNodeInstalled.value,
                    isTermuxRunning = isTermuxRunning.value,
                    // 添加电池优化豁免状态
                    isTermuxBatteryOptimizationExempted =
                            isTermuxFullyConfigured.value ||
                                    isTermuxBatteryOptimizationExempted.value,
                    // 添加启动Termux的回调
                    onStartTermux = startTermuxFunc,
                    // 添加电池优化豁免的回调
                    onRequestTermuxBatteryOptimization = { requestTermuxBatteryOptimizationFunc() },
                    // 添加新的回调函数
                    onConfigureTunaSource = {
                        if (!isTermuxConfiguring.value) {
                            // 用户点击配置清华源
                            state.resultDialogTitle.value = "配置清华源"
                            state.resultDialogContent.value = outputText
                            state.showResultDialogState.value = true
                            configureTunaSourceFunc()
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
                            installPythonFunc()
                        } else {
                            Toast.makeText(context, "请等待当前配置完成", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onInstallUvEnv = {
                        if (!isTermuxConfiguring.value) {
                            // 用户点击安装UV包管理器
                            state.resultDialogTitle.value = "安装UV包管理器"
                            state.resultDialogContent.value = outputText
                            state.showResultDialogState.value = true
                            installUvFunc()
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
                            installNodeFunc()
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
                    onAuthorizeTermux = authorizeTermuxFunc
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 权限状态卡片
        PermissionStatusCard(
                isRefreshing = state.isRefreshing.value,
                onRefresh = refreshStatus,
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
                isUvInstalled = isUvInstalled.value || isTermuxFullyConfigured.value,
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

        // 命令输入部分的UI
        ShizukuDemoScreenCommandUI(
            state = state,
            scope = scope,
            isTermuxAuthorized = state.isTermuxAuthorized.value
        )
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