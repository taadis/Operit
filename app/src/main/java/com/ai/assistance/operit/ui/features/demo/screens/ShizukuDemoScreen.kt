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
    viewModel: ShizukuDemoViewModel = viewModel(
        factory = ShizukuDemoViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
    )
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
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data =
                                    Uri.parse(
                                            "https://shizuku.rikka.app/zh-hans/download/"
                                    )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开下载链接", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onInstallBundled = {
                        try {
                            Toast.makeText(context, "安装内置版本功能暂未实现", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
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
                            viewModel.refreshStatus(context)
                        }
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Termux向导卡片 - 如果Termux未完全设置则显示
        if (!uiState.isTermuxInstalled.value ||
                !uiState.isTermuxAuthorized.value ||
                !viewModel.isTermuxFullyConfigured.value
        ) {
            TermuxWizardCard(
                    isTermuxInstalled = uiState.isTermuxInstalled.value,
                    isTermuxAuthorized = uiState.isTermuxAuthorized.value,
                    showWizard = uiState.showTermuxWizard.value,
                    onToggleWizard = { viewModel.toggleTermuxWizard() },
                    onInstallBundled = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data =
                                    Uri.parse(
                                            "https://f-droid.org/packages/com.termux/"
                                    )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开下载链接", Toast.LENGTH_SHORT).show()
                            try {
                                Toast.makeText(context, "正在尝试下载APK", Toast.LENGTH_SHORT).show()
                                val termuxDownloadUrl =
                                        "https://github.com/termux/termux-app/releases/download/v0.118.0/termux-app_v0.118.0+github-debug_universal.apk"
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.data = Uri.parse(termuxDownloadUrl)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                        context,
                                        "无法自动下载，请手动前往 Termux 官网下载",
                                        Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    onOpenTermux = { viewModel.startTermux(context) },
                    onAuthorizeTermux = { viewModel.authorizeTermux(context) },
                    isTunaSourceEnabled = viewModel.isTunaSourceEnabled.value,
                    isPythonInstalled = viewModel.isPythonInstalled.value,
                    isUvInstalled = viewModel.isUvInstalled.value,
                    isNodeInstalled = viewModel.isNodeInstalled.value,
                    isTermuxRunning = viewModel.isTermuxRunning.value,
                    isTermuxBatteryOptimizationExempted = viewModel.isTermuxBatteryOptimizationExempted.value,
                    onStartTermux = { viewModel.startTermux(context) },
                    onRequestTermuxBatteryOptimization = { viewModel.requestTermuxBatteryOptimization(context) },
                    onConfigureTunaSource = { viewModel.configureTunaSource(context) },
                    onInstallPythonEnv = { viewModel.installPython(context) },
                    onInstallUvEnv = { viewModel.installUv(context) },
                    onInstallNodeEnv = { viewModel.installNode(context) }
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
                isTermuxRunning = viewModel.isTermuxRunning.value,
                onStoragePermissionClick = {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Android 11+: Go to manage all files permission page
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        } else {
                            // Android 10-: Go to app settings page
                            val intent =
                                    Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + context.packageName)
                                    )
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        // Fall back to app settings
                        try {
                            val intent =
                                    Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + context.packageName)
                                    )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开权限设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOverlayPermissionClick = {
                    try {
                        val intent =
                                Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开悬浮窗设置", Toast.LENGTH_SHORT).show()
                    }
                },
                onBatteryOptimizationClick = {
                    try {
                        val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:" + context.packageName)
                                }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                    }
                },
                onAccessibilityClick = {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开无障碍服务设置", Toast.LENGTH_SHORT).show()
                    }
                },
                onLocationPermissionClick = {
                    // 请求位置权限
                    locationPermissionLauncher.launch(
                            arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                    )
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
                                    !viewModel.isTermuxFullyConfigured.value
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
                isTunaSourceEnabled = viewModel.isTunaSourceEnabled.value || viewModel.isTermuxFullyConfigured.value,
                isPythonInstalled = viewModel.isPythonInstalled.value || viewModel.isTermuxFullyConfigured.value,
                isUvInstalled = viewModel.isUvInstalled.value || viewModel.isTermuxFullyConfigured.value,
                isNodeInstalled = viewModel.isNodeInstalled.value || viewModel.isTermuxFullyConfigured.value
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
                if (!viewModel.isTermuxConfiguring.value) {
                    viewModel.hideResultDialog()
                    // 重置输出文本，准备下一次操作
                    viewModel.updateOutputText("欢迎使用Termux配置工具\n点击对应按钮开始配置")
                }
            },
            title = uiState.resultDialogTitle.value,
            content =
                    if (viewModel.isTermuxConfiguring.value) {
                        // 当正在配置时，显示实时输出
                        "${viewModel.outputText.value}\n\n${if (viewModel.currentTask.value.isNotEmpty()) "正在执行: ${viewModel.currentTask.value}..." else ""}"
                    } else {
                        // 当配置完成时，显示最终结果
                        viewModel.outputText.value
                    },
            context = context,
            // 当正在配置时不显示按钮，不可复制，配置完成后恢复按钮
            showButtons = !viewModel.isTermuxConfiguring.value,
            allowCopy = !viewModel.isTermuxConfiguring.value
    )
}