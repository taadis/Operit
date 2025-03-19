package com.ai.assistance.operit.ui.features.demo.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.AdbCommandExecutor
import com.ai.assistance.operit.ShizukuInstaller
import com.ai.assistance.operit.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuDemoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    var commandText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("结果将显示在这里") }
    var isShizukuInstalled by remember { mutableStateOf(AdbCommandExecutor.isShizukuInstalled(context)) }
    var isShizukuRunning by remember { mutableStateOf(AdbCommandExecutor.isShizukuServiceRunning()) }
    var hasPermission by remember { mutableStateOf(AdbCommandExecutor.hasShizukuPermission()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var permissionErrorMessage by remember { mutableStateOf<String?>(null) }
    var showSampleCommands by remember { mutableStateOf(false) }
    
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
    val installFromStoreStr = stringResource(id = R.string.install_from_store)
    val installingBundledStr = stringResource(id = R.string.installing_bundled)
    val installFailedStr = stringResource(id = R.string.install_failed)
    val installBundledStr = stringResource(
        id = R.string.install_bundled, 
        ShizukuInstaller.getBundledShizukuVersion(context)
    )
    val watchTutorialStr = "观看视频教程"
    
    // 刷新状态函数
    val refreshStatus = {
        isShizukuInstalled = AdbCommandExecutor.isShizukuInstalled(context)
        isShizukuRunning = AdbCommandExecutor.isShizukuServiceRunning()
        hasPermission = AdbCommandExecutor.hasShizukuPermission()
        
        val sdkVersion = Build.VERSION.SDK_INT
        println("SDK Version: $sdkVersion, Installed: $isShizukuInstalled, Running: $isShizukuRunning, HasPermission: $hasPermission")
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
    
    // 定期检查Shizuku状态
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
            text = "Shizuku ADB 命令执行器",
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 16.sp,
            color = Color.White,
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
                    Text("Shizuku 状态", style = MaterialTheme.typography.titleMedium)
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
                
                Text("已安装: ${if (isShizukuInstalled) "是" else "否"}")
                Text("服务运行: ${if (isShizukuRunning) "是" else "否"}")
                Text("权限状态: ${if (hasPermission) "已授权" else "未授权"}")
                
                // 显示错误消息（如果有）
                permissionErrorMessage?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (!isShizukuInstalled) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // 如果没有安装应用市场，打开浏览器
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(installFromStoreStr)
                    }
                    
                    // 添加从内置APK安装的选项
                    OutlinedButton(
                        onClick = {
                            if (ShizukuInstaller.installBundledShizuku(context)) {
                                Toast.makeText(context, installingBundledStr, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, installFailedStr, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(installBundledStr)
                    }
                } else if (!isShizukuRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法启动Shizuku应用", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        ) {
                            Text("打开 Shizuku 应用")
                        }
                        
                        OutlinedButton(
                            onClick = { showHelp = !showHelp },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        ) {
                            Text(if (showHelp) "隐藏帮助" else "显示帮助")
                        }
                    }
                    
                    // 添加视频教程按钮
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val videoUrl = "https://www.bilibili.com/video/BV1B9e4enEwX"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "无法打开视频链接", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(watchTutorialStr)
                    }
                    
                    if (showHelp) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = AdbCommandExecutor.getShizukuStartupInstructions(),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else if (!hasPermission) {
                    // 服务运行但没有权限
                    OutlinedButton(
                        onClick = {
                            AdbCommandExecutor.requestShizukuPermission { granted ->
                                hasPermission = granted
                                if (granted) {
                                    Toast.makeText(context, "Shizuku权限已授予", Toast.LENGTH_SHORT).show()
                                    permissionErrorMessage = null
                                } else {
                                    val message = "Shizuku权限请求被拒绝"
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    permissionErrorMessage = message
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("请求Shizuku权限")
                    }
                }
            }
        }
        
        // 命令输入部分
        if (isShizukuRunning && hasPermission) {
            Text(
                text = "执行ADB命令",
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
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = if (!isShizukuRunning) 
                        "请先启动Shizuku服务" 
                    else if (!hasPermission) 
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