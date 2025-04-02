package com.ai.assistance.operit.ui.features.terminal.screens

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.TermuxInstaller
import com.ai.assistance.operit.TermuxAuthorizer
import com.ai.assistance.operit.TermuxCommandExecutor
import kotlinx.coroutines.launch
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "TerminalScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    var commandText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("准备就绪...") }
    
    // 预定义命令
    val echoPathCommand = "echo \$PATH"
    val testScriptCommand = "sh /sdcard/Download/termux_test.sh"
    
    // Termux状态
    var isTermuxInstalled by remember { mutableStateOf(TermuxInstaller.isTermuxInstalled(context)) }
    var showTermuxWizard by remember { mutableStateOf(false) }
    
    // Termux授权状态
    var isTermuxAuthorized by remember { mutableStateOf(false) }
    var isCheckingAuth by remember { mutableStateOf(false) }
    
    // 初始状态加载
    LaunchedEffect(Unit) {
        isTermuxInstalled = TermuxInstaller.isTermuxInstalled(context)
        showTermuxWizard = !isTermuxInstalled
        
        if (isTermuxInstalled) {
            isCheckingAuth = true
            isTermuxAuthorized = TermuxAuthorizer.isTermuxAuthorized(context)
            isCheckingAuth = false
        }
        
        Log.d(TAG, "初始化检测 Termux安装状态: $isTermuxInstalled, 授权状态: $isTermuxAuthorized")
    }
    
    // 定期检查Termux是否安装
    LaunchedEffect(Unit) {
        while(true) {
            val wasInstalled = isTermuxInstalled
            isTermuxInstalled = TermuxInstaller.isTermuxInstalled(context)
            
            if (wasInstalled != isTermuxInstalled) {
                Log.d(TAG, "检测到Termux安装状态变化: $isTermuxInstalled")
                showTermuxWizard = !isTermuxInstalled
                
                // 如果Termux被安装，检查授权状态
                if (isTermuxInstalled && !isCheckingAuth) {
                    isCheckingAuth = true
                    isTermuxAuthorized = TermuxAuthorizer.isTermuxAuthorized(context)
                    isCheckingAuth = false
                }
            }
            
            // 如果已安装，定期检查授权状态
            if (isTermuxInstalled && !isCheckingAuth) {
                isCheckingAuth = true
                val wasAuthorized = isTermuxAuthorized
                isTermuxAuthorized = TermuxAuthorizer.isTermuxAuthorized(context)
                if (wasAuthorized != isTermuxAuthorized) {
                    Log.d(TAG, "检测到Termux授权状态变化: $isTermuxAuthorized")
                }
                isCheckingAuth = false
            }
            
            delay(2000)  // 每2秒检查一次
        }
    }
    
    // 监听应用安装/卸载广播
    DisposableEffect(context) {
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val packageName = intent.data?.schemeSpecificPart ?: return
                if (packageName == "com.termux") {
                    val wasInstalled = isTermuxInstalled
                    isTermuxInstalled = TermuxInstaller.isTermuxInstalled(context)
                    Log.d(TAG, "接收到应用变更广播: Termux安装状态: $isTermuxInstalled")
                    
                    if (wasInstalled != isTermuxInstalled) {
                        showTermuxWizard = !isTermuxInstalled
                    }
                }
            }
        }
        
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        
        context.registerReceiver(packageReceiver, intentFilter)
        
        onDispose {
            context.unregisterReceiver(packageReceiver)
        }
    }
    
    // 注册Termux状态变更监听器
    DisposableEffect(Unit) {
        val termuxListener: () -> Unit = {
            val wasInstalled = isTermuxInstalled
            isTermuxInstalled = TermuxInstaller.isTermuxInstalled(context)
            
            if (wasInstalled != isTermuxInstalled) {
                Log.d(TAG, "通过监听器检测到Termux安装状态变化: $isTermuxInstalled")
                showTermuxWizard = !isTermuxInstalled
            }
        }
        
        TermuxInstaller.addStateChangeListener(termuxListener)
        
        onDispose {
            TermuxInstaller.removeStateChangeListener(termuxListener)
        }
    }
    
    // 预加载字符串资源
    val installingBundledTermuxStr = "正在安装内置的Termux，请授予安装权限"
    val installTermuxFailedStr = "安装Termux失败"
    val termuxCommandStr = "Termux命令"
    val executeTermuxCommandStr = "执行命令"
    val openTermuxStr = "打开Termux应用"
    val termuxWizardTitleStr = "Termux设置向导"
    val installTermuxFromStoreStr = "从应用商店安装Termux"
    val installBundledTermuxStr = "安装内置版本"
    val authorizeTermuxStr = "授权Termux"
    val authorizeTermuxInProgressStr = "正在授权Termux..."
    val authorizeTermuxSuccessStr = "成功授权Termux"
    val authorizeTermuxFailedStr = "授权Termux失败"
    val getResultStr = "执行并获取结果"
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 如果Termux未安装，显示安装向导
        if (!isTermuxInstalled) {
            TermuxWizardCard(
                onInstallFromStore = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, 
                            Uri.parse("market://details?id=com.termux"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=com.termux"))
                        context.startActivity(intent)
                    }
                },
                onInstallBundled = {
                    if (TermuxInstaller.installBundledTermux(context)) {
                        Toast.makeText(context, installingBundledTermuxStr, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, installTermuxFailedStr, Toast.LENGTH_SHORT).show()
                    }
                },
                onToggleWizard = { showTermuxWizard = it },
                termuxWizardTitle = termuxWizardTitleStr,
                installTermuxFromStoreText = installTermuxFromStoreStr,
                installBundledTermuxText = installBundledTermuxStr
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // 终端卡片 - 只在Termux已安装时显示
        if (isTermuxInstalled) {
            // 显示Termux授权状态卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = if (isTermuxAuthorized) 1f else 0.9f
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Termux授权状态", 
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        if (isTermuxAuthorized) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已授权",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = "未授权",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        if (isTermuxAuthorized) 
                            "Termux已授权，可以执行命令" 
                        else 
                            "Termux尚未授权，需要授权后才能执行命令",
                        color = if (isTermuxAuthorized)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (!isTermuxAuthorized) {
                        var isAuthorizing by remember { mutableStateOf(false) }
                        
                        Button(
                            onClick = {
                                isAuthorizing = true
                                scope.launch {
                                    Toast.makeText(context, authorizeTermuxInProgressStr, Toast.LENGTH_SHORT).show()
                                    val success = TermuxAuthorizer.authorizeTermux(context)
                                    isAuthorizing = false
                                    if (success) {
                                        isTermuxAuthorized = true
                                        Toast.makeText(context, authorizeTermuxSuccessStr, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, authorizeTermuxFailedStr, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isAuthorizing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(authorizeTermuxStr)
                        }
                    }
                }
            }
        
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = termuxCommandStr,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    
                    // 命令输入框
                    OutlinedTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        label = { Text("输入命令") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = false,
                        minLines = 3
                    )
                    
                    // 执行按钮行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 正常执行按钮
                        Button(
                            onClick = {
                                // 更新结果文本为"执行中..."
                                resultText = "执行中..."

                                // 使用协程异步执行
                                scope.launch {
                                    try {
                                        // 检查Termux是否授权
                                        if (!TermuxAuthorizer.isTermuxAuthorized(context)) {
                                            // 尝试请求权限
                                            val permissionGranted = TermuxAuthorizer.requestRunCommandPermission(context)
                                            if (!permissionGranted) {
                                                resultText = "无法执行命令：Termux未授权或未正确配置。请确保:\n" +
                                                        "1. Termux已安装\n" +
                                                        "2. 已在Termux中设置allow-external-apps=true\n" +
                                                        "3. 应用已声明并获得com.termux.permission.RUN_COMMAND权限"
                                                Toast.makeText(context, "无法执行命令：Termux未授权", Toast.LENGTH_LONG).show()
                                                return@launch
                                            }
                                        }
                                        
                                        // 执行命令
                                        val command = commandText
                                        resultText = "命令 '$command' 已发送到Termux执行，等待结果..."
                                        
                                        // 使用带回调的executeCommand方法获取实际执行结果
                                        TermuxCommandExecutor.executeCommand(
                                            context = context,
                                            command = command,
                                            autoAuthorize = true,
                                            background = false, // 显示UI执行
                                            resultCallback = { result ->
                                                // 在主线程中更新UI
                                                scope.launch(Dispatchers.Main) {
                                                    if (result.success) {
                                                        resultText = "命令执行成功，退出码: ${result.exitCode}\n" +
                                                                     "输出:\n${result.stdout}"
                                                    } else {
                                                        resultText = "命令执行失败，退出码: ${result.exitCode}\n" +
                                                                     "错误:\n${result.stderr}"
                                                        Toast.makeText(context, "命令执行失败: ${result.stderr.take(50)}${if(result.stderr.length > 50) "..." else ""}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "执行命令时出错: ${e.message}", e)
                                        resultText = "执行命令时出错: ${e.message}"
                                        Toast.makeText(context, "执行命令失败: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        ) {
                            Text(executeTermuxCommandStr)
                        }
                        
                        // 执行并获取结果按钮
                        Button(
                            onClick = {
                                if (commandText.isNotBlank()) {
                                    scope.launch {
                                        resultText = "执行中并等待结果..."
                                        
                                        try {
                                            // 检查Termux是否授权
                                            if (!TermuxAuthorizer.isTermuxAuthorized(context)) {
                                                // 尝试请求权限
                                                val permissionGranted = TermuxAuthorizer.requestRunCommandPermission(context)
                                                if (!permissionGranted) {
                                                    resultText = "无法执行命令：Termux未授权或未正确配置。请确保:\n" +
                                                            "1. Termux已安装\n" +
                                                            "2. 已在Termux中设置allow-external-apps=true\n" +
                                                            "3. 应用已声明并获得com.termux.permission.RUN_COMMAND权限"
                                                    Toast.makeText(context, "无法执行命令：Termux未授权", Toast.LENGTH_LONG).show()
                                                    return@launch
                                                }
                                            }
                                            
                                            val result = TermuxCommandExecutor.executeCommandAndGetResult(
                                                context = context, 
                                                command = commandText,
                                                timeoutMillis = 15000  // 15秒超时
                                            )
                                            
                                            resultText = if (result.success) {
                                                "命令成功执行，退出码: ${result.exitCode}\n" +
                                                "输出:\n${result.stdout}"
                                            } else {
                                                "命令执行失败，退出码: ${result.exitCode}\n" +
                                                "错误:\n${result.stderr}"
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "执行并获取结果时出错: ${e.message}", e)
                                            resultText = "执行命令时出错: ${e.message}"
                                            Toast.makeText(context, "执行命令失败: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            enabled = commandText.isNotBlank() && isTermuxAuthorized,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        ) {
                            Text(getResultStr)
                        }
                    }
                    
                    // 结果显示区域
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = resultText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    // 快速示例命令按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 示例1
                        OutlinedButton(
                            onClick = { 
                                commandText = "ls -la" 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp)
                        ) {
                            Text("ls -la")
                        }
                        
                        // 示例2
                        OutlinedButton(
                            onClick = { 
                                commandText = echoPathCommand 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        ) {
                            Text("环境变量")
                        }
                        
                        // 示例3
                        OutlinedButton(
                            onClick = { 
                                commandText = testScriptCommand 
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        ) {
                            Text("测试脚本")
                        }
                    }
                    
                    // 打开Termux按钮
                    OutlinedButton(
                        onClick = {
                            if (!TermuxInstaller.openTermux(context)) {
                                Toast.makeText(context, "无法打开Termux应用", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        Text(openTermuxStr)
                    }
                    
                    // 终端使用说明
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "使用说明",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "1. 输入需要执行的命令\n" +
                                "2. 点击「执行命令」按钮运行命令，或点击「执行并获取结果」获取命令输出\n" +
                                "3. 如需更多功能，请点击「打开Termux应用」\n" +
                                "4. 第一次使用时请点击「授权Termux」按钮进行授权",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TermuxWizardCard(
    onInstallFromStore: () -> Unit,
    onInstallBundled: () -> Unit,
    onToggleWizard: (Boolean) -> Unit,
    termuxWizardTitle: String,
    installTermuxFromStoreText: String,
    installBundledTermuxText: String,
    showWizard: Boolean = true
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
                    termuxWizardTitle, 
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
            
            // 详细设置内容，仅在展开时显示
            if (showWizard) {
                Text(
                    "Termux是一个功能强大的终端模拟器，通过它您可以使用各种命令行工具。我们需要先安装这个应用。",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                OutlinedButton(
                    onClick = onInstallFromStore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(installTermuxFromStoreText)
                }
                
                OutlinedButton(
                    onClick = onInstallBundled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(installBundledTermuxText)
                }
            }
        }
    }
} 