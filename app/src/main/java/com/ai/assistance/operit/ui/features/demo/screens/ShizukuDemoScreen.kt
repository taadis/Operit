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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.ui.features.demo.components.*
import com.ai.assistance.operit.ui.features.demo.viewmodel.ShizukuDemoViewModel
import com.ai.assistance.operit.ui.features.demo.wizards.ShizukuWizardCard
import com.ai.assistance.operit.ui.features.demo.wizards.TermuxWizardCard
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuDemoScreen(
    viewModel: ShizukuDemoViewModel = viewModel(factory = ShizukuDemoViewModel.Factory())
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsState()
    
    // Location permission launcher
    val locationPermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineLocationGranted =
                        permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseLocationGranted =
                        permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                if (fineLocationGranted || coarseLocationGranted) {
                    viewModel.refreshStatus(context)
                }
    }

    // Register state change listeners
    DisposableEffect(Unit) {
        val shizukuListener: () -> Unit = {
            viewModel.refreshStatus(context)
            viewModel.checkTermuxAuthState(context)
        }
        ShizukuAuthorizer.addStateChangeListener(shizukuListener)
        
        onDispose {
            ShizukuAuthorizer.removeStateChangeListener(shizukuListener)
        }
    }

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
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
        if (!uiState.isShizukuInstalled.value ||
                        !uiState.isShizukuRunning.value ||
                        !uiState.hasShizukuPermission.value
        ) {
            ShizukuWizardCard(
                    isShizukuInstalled = uiState.isShizukuInstalled.value,
                    isShizukuRunning = uiState.isShizukuRunning.value,
                    hasShizukuPermission = uiState.hasShizukuPermission.value,
                    showWizard = uiState.showShizukuWizard.value,
                    onToggleWizard = { viewModel.toggleShizukuWizard() },
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
                        try {
                            if (com.ai.assistance.operit.core.tools.system.ShizukuInstaller.installBundledShizuku(context)) {
                                Toast.makeText(context, "正在安装内置版本...", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "安装失败", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
                            viewModel.refreshStatus(context)
                        }
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Termux向导卡片 - 只有在需要配置时才显示
        if (!uiState.isTermuxInstalled.value ||
                        !uiState.isTermuxAuthorized.value ||
                        !viewModel.isTermuxFullyConfigured
        ) {
            TermuxWizardCard(
                    isTermuxInstalled = uiState.isTermuxInstalled.value,
                    isTermuxAuthorized = uiState.isTermuxAuthorized.value,
                    showWizard = uiState.showTermuxWizard.value,
                    onToggleWizard = { viewModel.toggleTermuxWizard() },
                    // 添加新的状态参数 - 如果持久化记录显示已配置，则直接显示为已配置
                    isTunaSourceEnabled =
                            viewModel.isTermuxFullyConfigured || viewModel.isTunaSourceEnabled,
                    isPythonInstalled = viewModel.isTermuxFullyConfigured || viewModel.isPythonInstalled,
                    isUvInstalled = viewModel.isTermuxFullyConfigured || viewModel.isUvInstalled,
                    isNodeInstalled = viewModel.isTermuxFullyConfigured || viewModel.isNodeInstalled,
                    isTermuxRunning = viewModel.isTermuxRunning,
                    // 添加电池优化豁免状态
                    isTermuxBatteryOptimizationExempted =
                            viewModel.isTermuxFullyConfigured ||
                                    viewModel.isTermuxBatteryOptimizationExempted,
                    // 添加启动Termux的回调
                    onStartTermux = { viewModel.startTermux(context) },
                    // 添加电池优化豁免的回调
                    onRequestTermuxBatteryOptimization = { viewModel.requestTermuxBatteryOptimization(context) },
                    // 添加新的回调函数
                    onConfigureTunaSource = {
                        if (!viewModel.isTermuxConfiguring) {
                            viewModel.configureTunaSource(context)
                        } else {
                            Toast.makeText(context, "请等待当前配置完成", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onInstallPythonEnv = {
                        if (!viewModel.isTermuxConfiguring) {
                            viewModel.installPython(context)
                        } else {
                            Toast.makeText(context, "请等待当前配置完成", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onInstallUvEnv = {
                        if (!viewModel.isTermuxConfiguring) {
                            viewModel.installUv(context)
                        } else {
                            Toast.makeText(context, "请等待当前配置完成", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onInstallNodeEnv = {
                        if (!viewModel.isTermuxConfiguring) {
                            viewModel.installNode(context)
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
                        viewModel.startTermux(context)
                    },
                    onAuthorizeTermux = { viewModel.authorizeTermux(context) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 权限状态卡片
        PermissionStatusCard(
                isRefreshing = uiState.isRefreshing.value,
                onRefresh = { viewModel.refreshStatus(context) },
                hasStoragePermission = uiState.hasStoragePermission.value,
                hasOverlayPermission = uiState.hasOverlayPermission.value,
                hasBatteryOptimizationExemption = uiState.hasBatteryOptimizationExemption.value,
                hasAccessibilityServiceEnabled = uiState.hasAccessibilityServiceEnabled.value,
                hasLocationPermission = uiState.hasLocationPermission.value,
                isShizukuInstalled = uiState.isShizukuInstalled.value,
                isShizukuRunning = uiState.isShizukuRunning.value,
                hasShizukuPermission = uiState.hasShizukuPermission.value,
                isTermuxInstalled = uiState.isTermuxInstalled.value,
                isTermuxAuthorized = uiState.isTermuxAuthorized.value,
                isTermuxRunning = viewModel.isTermuxRunning,
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
                },
                onLocationPermissionClick = {
                    if (!uiState.hasLocationPermission.value) {
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
                    if (!uiState.isShizukuInstalled.value ||
                                    !uiState.isShizukuRunning.value ||
                                    !uiState.hasShizukuPermission.value
                    ) {
                        viewModel.toggleShizukuWizard()
                    }
                },
                onShizukuLongClick = {
                    // 长按时切换ADB命令执行器的显示状态
                    viewModel.toggleAdbCommandExecutor()
                },
                onTermuxClick = {
                    // 短按时处理Termux
                    if (!uiState.isTermuxInstalled.value ||
                                    !uiState.isTermuxAuthorized.value ||
                                    !viewModel.isTermuxFullyConfigured
                    ) {
                        // 如果未完全配置，显示向导
                        viewModel.toggleTermuxWizard()
                    } else {
                        // 如果已完全配置，尝试打开Termux
                        viewModel.startTermux(context)
                    }
                },
                onTermuxLongClick = {
                    // 长按时切换Termux命令执行器的显示状态
                    viewModel.toggleTermuxCommandExecutor()
                },
                permissionErrorMessage = uiState.permissionErrorMessage.value,
                // 添加Termux配置状态参数，将持久化状态融合进显示逻辑
                isTunaSourceEnabled = viewModel.isTunaSourceEnabled || viewModel.isTermuxFullyConfigured,
                isPythonInstalled = viewModel.isPythonInstalled || viewModel.isTermuxFullyConfigured,
                isUvInstalled = viewModel.isUvInstalled || viewModel.isTermuxFullyConfigured,
                isNodeInstalled = viewModel.isNodeInstalled || viewModel.isTermuxFullyConfigured
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
            viewModel = viewModel, 
            context = context
        )
    }

    // 显示命令结果对话框
    CommandResultDialog(
            showDialog = uiState.showResultDialogState.value,
            onDismiss = {
                // 只有当未在配置过程中时才允许手动关闭
                if (!viewModel.isTermuxConfiguring) {
                    viewModel.hideResultDialog()
                    // 重置输出文本，准备下一次操作
                    viewModel.updateOutputText("欢迎使用Termux配置工具\n点击对应按钮开始配置")
                }
            },
            title = uiState.resultDialogTitle.value,
            content =
                    if (viewModel.isTermuxConfiguring) {
                        // 当正在配置时，显示实时输出
                        "${viewModel.outputText}\n\n${if (viewModel.currentTask.isNotEmpty()) "正在执行: ${viewModel.currentTask}..." else ""}"
                    } else {
                        // 当配置完成时，显示最终结果
                        viewModel.outputText
                    },
            context = context,
            // 当正在配置时不显示按钮，不可复制，配置完成后恢复按钮
            showButtons = !viewModel.isTermuxConfiguring,
            allowCopy = !viewModel.isTermuxConfiguring
    )
}