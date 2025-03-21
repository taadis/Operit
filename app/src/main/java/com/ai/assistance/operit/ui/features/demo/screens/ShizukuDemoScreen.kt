package com.ai.assistance.operit.ui.features.demo.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.ai.assistance.operit.AdbCommandExecutor
import com.ai.assistance.operit.ShizukuInstaller
import com.ai.assistance.operit.data.UIHierarchyManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShizukuDemoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    var commandText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("结果将显示在这里") }
    
    // 权限状态
    var isShizukuInstalled by remember { mutableStateOf(AdbCommandExecutor.isShizukuInstalled(context)) }
    var isShizukuRunning by remember { mutableStateOf(AdbCommandExecutor.isShizukuServiceRunning()) }
    var hasShizukuPermission by remember { mutableStateOf(AdbCommandExecutor.hasShizukuPermission()) }
    
    var hasStoragePermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasBatteryOptimizationExemption by remember { mutableStateOf(false) }
    var hasAccessibilityServiceEnabled by remember { mutableStateOf(UIHierarchyManager.isAccessibilityServiceEnabled(context)) }
    
    var isRefreshing by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var permissionErrorMessage by remember { mutableStateOf<String?>(null) }
    var showSampleCommands by remember { mutableStateOf(false) }
    
    // ADB命令执行功能的显示状态
    var showAdbCommandExecutor by remember { mutableStateOf(false) }
    
    // Shizuku向导状态
    var showShizukuWizard by remember { mutableStateOf(false) }
    
    // 预定义的示例命令
    val sampleCommands = listOf(
        "getprop ro.build.version.release" to "获取Android版本",
        "pm list packages" to "列出已安装的应用包名",
        "dumpsys battery" to "查看电池状态",
        "settings list system" to "列出系统设置",
        "am start -a android.intent.action.VIEW -d https://www.example.com" to "打开网页",
        "dumpsys activity activities" to "查看活动的Activity",
        "service list" to "列出系统服务",
        "wm size" to "查看屏幕分辨率"
    )
    
    // 预加载字符串资源
    val installFromStoreStr = "从应用商店安装"
    val installingBundledStr = "正在安装内置版本..."
    val installFailedStr = "安装失败"
    val installBundledStr = "安装内置版本 (${ShizukuInstaller.getBundledShizukuVersion(context)})"
    val watchTutorialStr = "观看视频教程"
    
    // 刷新状态函数
    val refreshStatus = {
        isShizukuInstalled = AdbCommandExecutor.isShizukuInstalled(context)
        isShizukuRunning = AdbCommandExecutor.isShizukuServiceRunning()
        hasShizukuPermission = AdbCommandExecutor.hasShizukuPermission()
        
        // 检查存储权限
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        // 检查悬浮窗权限
        hasOverlayPermission = Settings.canDrawOverlays(context)
        
        // 检查电池优化豁免
        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        hasBatteryOptimizationExemption = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        
        // 检查无障碍服务状态
        hasAccessibilityServiceEnabled = UIHierarchyManager.isAccessibilityServiceEnabled(context)
        
        val sdkVersion = Build.VERSION.SDK_INT
        println("SDK Version: $sdkVersion, Storage: $hasStoragePermission, Overlay: $hasOverlayPermission, " +
                "Battery: $hasBatteryOptimizationExemption, A11y: $hasAccessibilityServiceEnabled, " +
                "Shizuku: [Installed: $isShizukuInstalled, Running: $isShizukuRunning, HasPermission: $hasShizukuPermission]")
    }
    
    // 注册状态变更监听器
    DisposableEffect(Unit) {
        val listener: () -> Unit = {
            refreshStatus()
        }
        AdbCommandExecutor.addStateChangeListener(listener)
        onDispose {
            AdbCommandExecutor.removeStateChangeListener(listener)
        }
    }
    
    // 初始状态加载
    LaunchedEffect(Unit) {
        refreshStatus()
    }
    
    // 定期检查权限状态
    LaunchedEffect(Unit) {
        while(true) {
            delay(3000) // 每3秒检查一次
            refreshStatus()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("设备信息", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Android版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                Text("设备型号: ${Build.MANUFACTURER} ${Build.MODEL}")
            }
        }
        
        // Shizuku向导卡片 - 如果Shizuku未完全设置则显示
        if (!isShizukuInstalled || !isShizukuRunning || !hasShizukuPermission) {
            ShizukuWizardCard(
                isShizukuInstalled = isShizukuInstalled,
                isShizukuRunning = isShizukuRunning,
                hasShizukuPermission = hasShizukuPermission,
                showWizard = showShizukuWizard,
                onToggleWizard = { showShizukuWizard = it },
                onInstallFromStore = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // 如果没有安装应用市场，打开浏览器
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
                        context.startActivity(intent)
                    }
                },
                onInstallBundled = {
                    if (ShizukuInstaller.installBundledShizuku(context)) {
                        Toast.makeText(context, installingBundledStr, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, installFailedStr, Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenShizuku = {
                    try {
                        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
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
                        val videoUrl = "https://www.bilibili.com/video/BV1B9e4enEwX"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开视频链接", Toast.LENGTH_SHORT).show()
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
        
        // 状态信息
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("权限状态", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            isRefreshing = true
                            refreshStatus()
                            isRefreshing = false
                        },
                        enabled = !isRefreshing
                    ) {
                        Text("刷新")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 存储权限状态
                PermissionStatusItem(
                    title = "存储权限",
                    isGranted = hasStoragePermission,
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    addCategory("android.intent.category.DEFAULT")
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开存储权限设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                
                // 悬浮窗权限状态
                PermissionStatusItem(
                    title = "悬浮窗权限",
                    isGranted = hasOverlayPermission,
                    onClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开悬浮窗权限设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                
                // 电池优化豁免状态
                PermissionStatusItem(
                    title = "电池优化豁免",
                    isGranted = hasBatteryOptimizationExemption,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "无法打开电池优化设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                
                // 无障碍服务状态
                PermissionStatusItem(
                    title = "无障碍服务",
                    isGranted = hasAccessibilityServiceEnabled,
                    onClick = {
                        UIHierarchyManager.openAccessibilitySettings(context)
                    }
                )
                
                // Shizuku状态 - 长按时显示ADB命令执行器
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .combinedClickable(
                            onClick = {
                                // 短按时如果Shizuku未完全设置，则显示向导
                                if (!isShizukuInstalled || !isShizukuRunning || !hasShizukuPermission) {
                                    showShizukuWizard = !showShizukuWizard
                                }
                            },
                            onLongClick = {
                                // 长按时切换ADB命令执行器的显示状态
                                showAdbCommandExecutor = !showAdbCommandExecutor
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Shizuku服务",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    val statusText = when {
                        !isShizukuInstalled -> "未安装"
                        !isShizukuRunning -> "未运行"
                        !hasShizukuPermission -> "未授权"
                        else -> "已启用"
                    }
                    
                    val statusColor = when {
                        !isShizukuInstalled || !isShizukuRunning || !hasShizukuPermission -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // 显示错误消息（如果有）
                permissionErrorMessage?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // 显示长按提示
            Text(
            text = "提示：点击Shizuku服务可查看设置向导，长按可显示ADB命令执行器",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // 命令输入部分 - 只在用户长按Shizuku权限状态时显示
        if (showAdbCommandExecutor && isShizukuRunning && hasShizukuPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "ADB命令执行器",
                        style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            
            // 命令输入
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                label = { Text("输入ADB命令") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            
            // 示例命令按钮
            OutlinedButton(
                onClick = { showSampleCommands = !showSampleCommands },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(if (showSampleCommands) "隐藏示例命令" else "显示示例命令")
            }
            
            // 示例命令列表
            if (showSampleCommands) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("选择一个示例命令:", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        sampleCommands.forEach { (command, description) ->
                            OutlinedButton(
                                onClick = { commandText = command },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(description, style = MaterialTheme.typography.bodyMedium)
                                    Text(command, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            
            // 执行按钮
            Button(
                onClick = {
                    if (commandText.isNotBlank()) {
                        scope.launch {
                            resultText = "执行中..."
                            val result = AdbCommandExecutor.executeAdbCommand(commandText)
                            resultText = if (result.success) {
                                "命令执行成功:\n${result.stdout}"
                            } else {
                                "命令执行失败 (退出码: ${result.exitCode}):\n${result.stderr}"
                            }
                        }
                    }
                },
                enabled = commandText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("执行命令")
            }
            
            // 结果显示
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = resultText,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
                }
            }
        } else if (showAdbCommandExecutor) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = if (!isShizukuRunning) 
                        "请先启动Shizuku服务" 
                    else if (!hasShizukuPermission) 
                        "请授予Shizuku权限" 
                    else 
                        "无法使用ADB命令功能",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ShizukuWizardCard(
    isShizukuInstalled: Boolean,
    isShizukuRunning: Boolean,
    hasShizukuPermission: Boolean,
    showWizard: Boolean,
    onToggleWizard: (Boolean) -> Unit,
    onInstallFromStore: () -> Unit,
    onInstallBundled: () -> Unit,
    onOpenShizuku: () -> Unit,
    onWatchTutorial: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 向导标题与折叠/展开按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Shizuku 设置向导", 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                OutlinedButton(
                    onClick = { onToggleWizard(!showWizard) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(if (showWizard) "收起" else "展开")
                }
            }
            
            // 显示当前进度
            LinearProgressIndicator(
                progress = when {
                    !isShizukuInstalled -> 0f
                    !isShizukuRunning -> 0.33f
                    !hasShizukuPermission -> 0.66f
                    else -> 1f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
            
            // 当前状态文字
            val statusText = when {
                !isShizukuInstalled -> "步骤1：安装 Shizuku 应用"
                !isShizukuRunning -> "步骤2：启动 Shizuku 服务"
                !hasShizukuPermission -> "步骤3：授予 Shizuku 权限"
                else -> "Shizuku 已完全设置"
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // 详细设置内容，仅在展开时显示
            if (showWizard) {
                when {
                    // 第一步：安装Shizuku
                    !isShizukuInstalled -> {
                        Text(
                            "Shizuku是一个授予应用高级权限的工具，不需要root。我们需要先安装这个应用。",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        OutlinedButton(
                            onClick = onInstallFromStore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text("从应用商店安装")
                        }
                        
                        OutlinedButton(
                            onClick = onInstallBundled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("安装内置版本")
                        }
                    }
                    
                    // 第二步：启动Shizuku服务
                    !isShizukuRunning -> {
                        Text(
                            "Shizuku已安装，现在需要启动Shizuku服务。请按照以下方法之一操作：",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "方法1：使用应用内启动（推荐）",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = "1. 点击下方\"打开Shizuku\"按钮\n" +
                                           "2. 在Shizuku应用中选择\"通过无线调试启动\"\n" +
                                           "3. 根据提示开启\"开发者选项\"和\"无线调试\"\n" +
                                           "4. 允许Shizuku获取无线调试权限",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "方法2：使用USB启动",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = "1. 在电脑上安装ADB工具\n" +
                                           "2. 通过USB连接手机和电脑\n" +
                                           "3. 在电脑命令行执行：\n" +
                                           "   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/files/start.sh",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = onOpenShizuku,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 4.dp)
                            ) {
                                Text("打开Shizuku")
                            }
                            
                            OutlinedButton(
                                onClick = onWatchTutorial,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp)
                            ) {
                                Text("视频教程")
                            }
                        }
                    }
                    
                    // 第三步：授予权限
                    !hasShizukuPermission -> {
                        Text(
                            "Shizuku服务已启动，现在需要授予权限给本应用。点击下方按钮，在弹出的对话框中选择\"允许\"。",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "注意：如果没有看到授权弹窗，请检查：\n" +
                                       "1. Shizuku服务是否正在运行\n" +
                                       "2. 重启Shizuku服务后再尝试\n" +
                                       "3. 确认允许显示悬浮窗权限",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        OutlinedButton(
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("授予Shizuku权限")
                        }
                    }
                    
                    // 全部完成
                    else -> {
                        Text(
                            "恭喜！Shizuku已完全设置，您现在可以使用全部功能。",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionStatusItem(
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        
        Text(
            text = if (isGranted) "已授权" else "未授权",
            color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }
} 