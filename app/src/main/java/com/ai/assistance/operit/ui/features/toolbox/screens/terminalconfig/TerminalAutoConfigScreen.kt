package com.ai.assistance.operit.ui.features.toolbox.screens.terminalconfig

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.assistance.operit.tools.system.TermuxCommandExecutor
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.components.InteractiveInputDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalLine
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TerminalAutoConfig"

/** 终端自动配置屏幕 提供自动化安装和配置常用命令行工具的功能 */
@Composable
fun TerminalAutoConfigScreen(navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // 状态变量
    var outputText by remember { mutableStateOf("欢迎使用终端自动配置工具\n选择需要安装的组件点击按钮开始安装") }
    var isExecuting by remember { mutableStateOf(false) }
    var currentTask by remember { mutableStateOf("") }

    // 软件包安装状态
    var pythonInstalled by remember { mutableStateOf(false) }
    var pipInstalled by remember { mutableStateOf(false) }
    var nodeInstalled by remember { mutableStateOf(false) }
    var gitInstalled by remember { mutableStateOf(false) }
    var tunaSourceEnabled by remember { mutableStateOf(false) }

    // 会话ID
    var sessionId by remember { mutableStateOf<String?>(null) }

    // 交互式输入对话框状态
    var showInputDialog by remember { mutableStateOf(false) }
    var interactivePrompt by remember { mutableStateOf("") }
    var interactiveInputText by remember { mutableStateOf("") }
    var currentExecutionId by remember { mutableStateOf(-1) }

    // 创建或获取会话
    LaunchedEffect(key1 = Unit) {
        // 如果没有已有会话，创建一个新会话
        if (TerminalSessionManager.getSessionCount() == 0) {
            val session = TerminalSessionManager.createSession("自动配置会话")
            sessionId = session.id
        } else {
            // 使用已存在的会话
            sessionId = TerminalSessionManager.activeSessionId.value
            if (sessionId == null && TerminalSessionManager.sessions.isNotEmpty()) {
                sessionId = TerminalSessionManager.sessions[0].id
            }
        }
    }

    // 检查已安装的组件
    LaunchedEffect(key1 = Unit) {
        checkInstalledComponents(
                context,
                onResult = { python, pip, node, git ->
                    pythonInstalled = python
                    pipInstalled = pip
                    nodeInstalled = node
                    gitInstalled = git
                }
        )

        // 检查是否已启用清华源
        checkTunaSourceEnabled(context) { enabled -> tunaSourceEnabled = enabled }
    }

    // 交互式输入对话框
    if (showInputDialog) {
        InteractiveInputDialog(
                prompt = interactivePrompt,
                initialInput = interactiveInputText,
                onDismissRequest = { showInputDialog = false },
                onInputSubmit = { input ->
                    showInputDialog = false
                    interactiveInputText = ""

                    // 处理用户输入
                    scope.launch {
                        try {
                            val success =
                                    TermuxCommandExecutor.sendInputToCommand(
                                            context = context,
                                            executionId = currentExecutionId,
                                            input = input
                                    )

                            if (success) {
                                sessionId?.let { id ->
                                    TerminalSessionManager.sessions.find { it.id == id }?.let {
                                            session ->
                                        session.commandHistory.add(
                                                TerminalLine.Input(input, "User Input: ")
                                        )
                                    }
                                }
                            } else {
                                outputText += "\n[输入发送失败]"
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "发送用户输入时出错: ${e.message}", e)
                        }
                    }
                }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 标题
        Text(
                text = "终端自动配置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
        )

        // 命令输出区域
        Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                Text(
                        text = outputText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)
                )

                if (isExecuting) {
                    LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    )

                    Text(
                            text = "正在执行: $currentTask",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                    Modifier.align(Alignment.BottomCenter)
                                            .padding(bottom = 16.dp)
                                            .background(
                                                    color =
                                                            MaterialTheme.colorScheme.surfaceVariant
                                                                    .copy(alpha = 0.8f),
                                                    shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 安装选项
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                    text = "安装选项",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
            )

            // 清华源设置选项
            InstallOptionCard(
                    title = "清华源镜像",
                    description = "切换至清华大学开源镜像站，提高下载速度",
                    icon = Icons.Default.Speed,
                    installed = tunaSourceEnabled,
                    isExecuting = isExecuting,
                    onClick = {
                        scope.launch {
                            isExecuting = true
                            currentTask = "切换软件源"
                            switchToTunaMirror(
                                    context,
                                    sessionId,
                                    onOutput = { output -> outputText += "\n$output" },
                                    onInteractivePrompt = { prompt, executionId ->
                                        interactivePrompt = prompt
                                        currentExecutionId = executionId
                                        showInputDialog = true
                                    },
                                    onComplete = { success ->
                                        isExecuting = false
                                        tunaSourceEnabled = success
                                        if (success) {
                                            Toast.makeText(context, "清华源设置成功", Toast.LENGTH_SHORT)
                                                    .show()
                                        } else {
                                            Toast.makeText(context, "清华源设置失败", Toast.LENGTH_SHORT)
                                                    .show()
                                        }
                                    }
                            )
                        }
                    }
            )

            InstallOptionCard(
                    title = "Python 环境",
                    description = "安装 Python 3 和相关依赖",
                    icon = Icons.Default.Code,
                    installed = pythonInstalled,
                    isExecuting = isExecuting,
                    onClick = {
                        scope.launch {
                            isExecuting = true
                            currentTask = "安装 Python"
                            installPython(
                                    context,
                                    sessionId,
                                    onOutput = { output -> outputText += "\n$output" },
                                    onInteractivePrompt = { prompt, executionId ->
                                        interactivePrompt = prompt
                                        currentExecutionId = executionId
                                        showInputDialog = true
                                    },
                                    onComplete = { success ->
                                        isExecuting = false
                                        pythonInstalled = success
                                        if (success) {
                                            Toast.makeText(
                                                            context,
                                                            "Python 安装成功",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        } else {
                                            Toast.makeText(
                                                            context,
                                                            "Python 安装失败",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                    }
                            )
                        }
                    }
            )

            InstallOptionCard(
                    title = "PIP 包管理器",
                    description = "安装 Python 包管理工具",
                    icon = Icons.Default.Settings,
                    installed = pipInstalled,
                    isExecuting = isExecuting,
                    onClick = {
                        scope.launch {
                            isExecuting = true
                            currentTask = "安装 PIP"
                            installPip(
                                    context,
                                    sessionId,
                                    onOutput = { output -> outputText += "\n$output" },
                                    onInteractivePrompt = { prompt, executionId ->
                                        interactivePrompt = prompt
                                        currentExecutionId = executionId
                                        showInputDialog = true
                                    },
                                    onComplete = { success ->
                                        isExecuting = false
                                        pipInstalled = success
                                        if (success) {
                                            Toast.makeText(context, "PIP 安装成功", Toast.LENGTH_SHORT)
                                                    .show()
                                        } else {
                                            Toast.makeText(context, "PIP 安装失败", Toast.LENGTH_SHORT)
                                                    .show()
                                        }
                                    }
                            )
                        }
                    }
            )

            InstallOptionCard(
                    title = "Node.js 环境",
                    description = "安装 Node.js 和 NPM",
                    icon = Icons.Default.Code,
                    installed = nodeInstalled,
                    isExecuting = isExecuting,
                    onClick = {
                        scope.launch {
                            isExecuting = true
                            currentTask = "安装 Node.js"
                            installNodejs(
                                    context,
                                    sessionId,
                                    onOutput = { output -> outputText += "\n$output" },
                                    onInteractivePrompt = { prompt, executionId ->
                                        interactivePrompt = prompt
                                        currentExecutionId = executionId
                                        showInputDialog = true
                                    },
                                    onComplete = { success ->
                                        isExecuting = false
                                        nodeInstalled = success
                                        if (success) {
                                            Toast.makeText(
                                                            context,
                                                            "Node.js 安装成功",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        } else {
                                            Toast.makeText(
                                                            context,
                                                            "Node.js 安装失败",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                    }
                            )
                        }
                    }
            )

            InstallOptionCard(
                    title = "Git 版本控制",
                    description = "安装 Git 版本控制系统",
                    icon = Icons.Default.Code,
                    installed = gitInstalled,
                    isExecuting = isExecuting,
                    onClick = {
                        scope.launch {
                            isExecuting = true
                            currentTask = "安装 Git"
                            installGit(
                                    context,
                                    sessionId,
                                    onOutput = { output -> outputText += "\n$output" },
                                    onInteractivePrompt = { prompt, executionId ->
                                        interactivePrompt = prompt
                                        currentExecutionId = executionId
                                        showInputDialog = true
                                    },
                                    onComplete = { success ->
                                        isExecuting = false
                                        gitInstalled = success
                                        if (success) {
                                            Toast.makeText(context, "Git 安装成功", Toast.LENGTH_SHORT)
                                                    .show()
                                        } else {
                                            Toast.makeText(context, "Git 安装失败", Toast.LENGTH_SHORT)
                                                    .show()
                                        }
                                    }
                            )
                        }
                    }
            )

            Button(
                    onClick = {
                        scope.launch {
                            isExecuting = true
                            currentTask = "更新软件包"
                            updatePackages(
                                    context,
                                    sessionId,
                                    onOutput = { output -> outputText += "\n$output" },
                                    onInteractivePrompt = { prompt, executionId ->
                                        interactivePrompt = prompt
                                        currentExecutionId = executionId
                                        showInputDialog = true
                                    },
                                    onComplete = { success ->
                                        isExecuting = false
                                        if (success) {
                                            Toast.makeText(context, "软件包更新成功", Toast.LENGTH_SHORT)
                                                    .show()
                                            // 更新后重新检查安装状态
                                            scope.launch {
                                                checkInstalledComponents(
                                                        context,
                                                        onResult = { python, pip, node, git ->
                                                            pythonInstalled = python
                                                            pipInstalled = pip
                                                            nodeInstalled = node
                                                            gitInstalled = git
                                                        }
                                                )
                                            }
                                        } else {
                                            Toast.makeText(context, "软件包更新失败", Toast.LENGTH_SHORT)
                                                    .show()
                                        }
                                    }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = !isExecuting
            ) {
                Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "更新",
                        modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("更新所有软件包")
            }
        }
    }
}

@Composable
fun InstallOptionCard(
        title: String,
        description: String,
        icon: ImageVector,
        installed: Boolean,
        isExecuting: Boolean,
        onClick: () -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (installed) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                    ),
            enabled = !isExecuting,
            onClick = onClick
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint =
                            if (installed) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color =
                                if (installed) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                )

                Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                if (installed)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                                alpha = 0.8f
                                        )
                                else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (installed) {
                Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已安装",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

// 检查已安装的组件
suspend fun checkInstalledComponents(
        context: Context,
        onResult:
                (
                        pythonInstalled: Boolean,
                        pipInstalled: Boolean,
                        nodeInstalled: Boolean,
                        gitInstalled: Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        var pythonInstalled = false
        var pipInstalled = false
        var nodeInstalled = false
        var gitInstalled = false

        // 检查Python
        val pythonResult =
                TermuxCommandExecutor.executeCommand(
                        context = context,
                        command = "command -v python3",
                        autoAuthorize = true
                )
        pythonInstalled = pythonResult.success && pythonResult.stdout.contains("python3")

        // 检查PIP
        val pipResult =
                TermuxCommandExecutor.executeCommand(
                        context = context,
                        command = "command -v pip",
                        autoAuthorize = true
                )
        pipInstalled = pipResult.success && pipResult.stdout.contains("pip")

        // 检查Node.js
        val nodeResult =
                TermuxCommandExecutor.executeCommand(
                        context = context,
                        command = "command -v node",
                        autoAuthorize = true
                )
        nodeInstalled = nodeResult.success && nodeResult.stdout.contains("node")

        // 检查Git
        val gitResult =
                TermuxCommandExecutor.executeCommand(
                        context = context,
                        command = "command -v git",
                        autoAuthorize = true
                )
        gitInstalled = gitResult.success && gitResult.stdout.contains("git")

        withContext(Dispatchers.Main) {
            onResult(pythonInstalled, pipInstalled, nodeInstalled, gitInstalled)
        }
    }
}

// 检查是否已启用清华源
suspend fun checkTunaSourceEnabled(context: Context, onResult: (Boolean) -> Unit) {
    withContext(Dispatchers.IO) {
        val result =
                TermuxCommandExecutor.executeCommand(
                        context = context,
                        command =
                                "grep -q 'mirrors.tuna.tsinghua.edu.cn' \$PREFIX/etc/apt/sources.list && echo 'ENABLED' || echo 'DISABLED'",
                        autoAuthorize = true
                )

        val isEnabled = result.success && result.stdout.contains("ENABLED")

        withContext(Dispatchers.Main) { onResult(isEnabled) }
    }
}

// 切换至清华源
suspend fun switchToTunaMirror(
        context: Context,
        sessionId: String?,
        onOutput: (String) -> Unit,
        onInteractivePrompt: (String, Int) -> Unit,
        onComplete: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        onOutput("开始切换到清华源镜像...")

        // 获取会话
        val session = sessionId?.let { TerminalSessionManager.sessions.find { s -> s.id == it } }

        // 修改源地址的命令 - 分步执行更可靠
        val backupCommand = "cp \$PREFIX/etc/apt/sources.list \$PREFIX/etc/apt/sources.list.bak"
        val sedCommand =
                "sed -i 's@^\\(deb.*stable main\\)$@#\\1\\ndeb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main@' \$PREFIX/etc/apt/sources.list"
        val updateCommand = "apt update"

        if (session != null) {
            // 分步执行命令更可靠
            var allCommandsSucceeded = true

            // 1. 备份原始sources.list
            onOutput("正在备份原始软件源配置...")
            TerminalSessionManager.executeSessionCommand(
                    context,
                    session,
                    backupCommand,
                    onOutput,
                    onInteractivePrompt,
                    onComplete = { exitCode, success ->
                        Log.d(TAG, "备份命令完成: 退出码=$exitCode, 成功=$success")
                        allCommandsSucceeded = allCommandsSucceeded && success

                        if (!success) {
                            onOutput("备份原始软件源失败，退出码: $exitCode")
                        }
                    }
            )

            // 2. 修改sources.list
            onOutput("正在修改软件源配置...")
            TerminalSessionManager.executeSessionCommand(
                    context,
                    session,
                    sedCommand,
                    onOutput,
                    onInteractivePrompt,
                    onComplete = { exitCode, success ->
                        Log.d(TAG, "修改软件源命令完成: 退出码=$exitCode, 成功=$success")
                        allCommandsSucceeded = allCommandsSucceeded && success

                        if (!success) {
                            onOutput("修改软件源配置失败，退出码: $exitCode")
                        }
                    }
            )

            // 3. 更新软件包信息
            onOutput("正在更新软件包信息...")
            TerminalSessionManager.executeSessionCommand(
                    context,
                    session,
                    updateCommand,
                    onOutput,
                    onInteractivePrompt,
                    onComplete = { exitCode, success ->
                        Log.d(TAG, "更新软件包命令完成: 退出码=$exitCode, 成功=$success")
                        allCommandsSucceeded = allCommandsSucceeded && success

                        if (!success) {
                            onOutput("更新软件包信息失败，退出码: $exitCode")
                        }
                    }
            )

            // 检查命令执行后是否成功切换
            val checkResult =
                    TermuxCommandExecutor.executeCommand(
                            context = context,
                            command =
                                    "grep -q 'mirrors.tuna.tsinghua.edu.cn' \$PREFIX/etc/apt/sources.list && echo 'SUCCESS'",
                            autoAuthorize = true
                    )

            withContext(Dispatchers.Main) {
                val success =
                        allCommandsSucceeded &&
                                checkResult.success &&
                                checkResult.stdout.contains("SUCCESS")
                onOutput("清华源设置" + if (success) "成功" else "失败")

                // 如果成功，给出一些有用的信息
                if (success) {
                    onOutput("清华源设置成功！软件包下载速度将大幅提升。")
                    onOutput(
                            "如果需要恢复默认源，可以执行: cp \$PREFIX/etc/apt/sources.list.bak \$PREFIX/etc/apt/sources.list"
                    )
                }

                onComplete(success)
            }
        } else {
            // 回退到直接执行命令的逻辑保持不变
            val fullCommand = "$backupCommand && $sedCommand && $updateCommand"
            val result =
                    TermuxCommandExecutor.executeCommand(
                            context = context,
                            command = fullCommand,
                            autoAuthorize = true,
                            resultCallback = { result ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    // 检查执行结果
                                    val checkResult =
                                            TermuxCommandExecutor.executeCommand(
                                                    context = context,
                                                    command =
                                                            "grep -q 'mirrors.tuna.tsinghua.edu.cn' \$PREFIX/etc/apt/sources.list && echo 'SUCCESS'",
                                                    autoAuthorize = true
                                            )

                                    val success =
                                            checkResult.success &&
                                                    checkResult.stdout.contains("SUCCESS")
                                    onOutput("清华源设置" + if (success) "成功" else "失败")

                                    // 如果成功，给出一些有用的信息
                                    if (success) {
                                        onOutput("清华源设置成功！软件包下载速度将大幅提升。")
                                        onOutput(
                                                "如果需要恢复默认源，可以执行: cp \$PREFIX/etc/apt/sources.list.bak \$PREFIX/etc/apt/sources.list"
                                        )
                                    }

                                    onComplete(success)
                                }
                            },
                            outputReceiver =
                                    object : TermuxCommandExecutor.Companion.CommandOutputReceiver {
                                        override fun onStdout(output: String, isComplete: Boolean) {
                                            CoroutineScope(Dispatchers.Main).launch {
                                                onOutput(output)
                                            }
                                        }

                                        override fun onStderr(error: String, isComplete: Boolean) {
                                            // 检查是否是交互式提示
                                            if (error.startsWith("INTERACTIVE_PROMPT:")) {
                                                val promptText =
                                                        error.substringAfter("INTERACTIVE_PROMPT:")
                                                                .trim()
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    onOutput("[需要输入] $promptText")
                                                }
                                            } else {
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    onOutput(error)
                                                }
                                            }
                                        }

                                        override fun onComplete(
                                                result:
                                                        com.ai.assistance.operit.tools.system.AdbCommandExecutor.CommandResult
                                        ) {
                                            // 已在resultCallback中处理
                                        }

                                        override fun onError(error: String, exitCode: Int) {
                                            CoroutineScope(Dispatchers.Main).launch {
                                                onOutput("错误: $error (退出码: $exitCode)")
                                            }
                                        }
                                    }
                    )

            if (!result.success) {
                CoroutineScope(Dispatchers.Main).launch {
                    onOutput("命令发送失败: ${result.stderr}")
                    onComplete(false)
                }
            }
        }
    }
}

// 安装Python
suspend fun installPython(
        context: Context,
        sessionId: String?,
        onOutput: (String) -> Unit,
        onInteractivePrompt: (String, Int) -> Unit,
        onComplete: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        onOutput("开始安装 Python...")

        // 获取会话
        val session = sessionId?.let { TerminalSessionManager.sessions.find { s -> s.id == it } }

        if (session != null) {
            // 使用终端会话执行命令
            TerminalSessionManager.executeSessionCommand(
                    context,
                    session,
                    "pkg update -y && pkg install python -y",
                    onOutput,
                    onInteractivePrompt,
                    onComplete = { exitCode, success ->
                        onOutput("Python 安装" + if (success) "成功" else "失败")
                        onComplete(success)
                    }
            )
        } else {
            // 回退到直接执行命令
            val result =
                    TermuxCommandExecutor.executeCommand(
                            context = context,
                            command = "pkg update -y && pkg install python -y",
                            autoAuthorize = true,
                            resultCallback = { result ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    onOutput("Python 安装" + if (result.success) "成功" else "失败")
                                    onComplete(result.success)
                                }
                            },
                            outputReceiver =
                                    object : TermuxCommandExecutor.Companion.CommandOutputReceiver {
                                        override fun onStdout(output: String, isComplete: Boolean) {
                                            CoroutineScope(Dispatchers.Main).launch {
                                                onOutput(output)
                                            }
                                        }

                                        override fun onStderr(error: String, isComplete: Boolean) {
                                            // 检查是否是交互式提示
                                            if (error.startsWith("INTERACTIVE_PROMPT:")) {
                                                val promptText =
                                                        error.substringAfter("INTERACTIVE_PROMPT:")
                                                                .trim()
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    onOutput("[需要输入] $promptText")
                                                }
                                            } else {
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    onOutput(error)
                                                }
                                            }
                                        }

                                        override fun onComplete(
                                                result:
                                                        com.ai.assistance.operit.tools.system.AdbCommandExecutor.CommandResult
                                        ) {
                                            // 已在resultCallback中处理
                                        }

                                        override fun onError(error: String, exitCode: Int) {
                                            CoroutineScope(Dispatchers.Main).launch {
                                                onOutput("错误: $error (退出码: $exitCode)")
                                            }
                                        }
                                    }
                    )

            if (!result.success) {
                CoroutineScope(Dispatchers.Main).launch {
                    onOutput("命令发送失败: ${result.stderr}")
                    onComplete(false)
                }
            }
        }
    }
}

// 安装PIP
suspend fun installPip(
        context: Context,
        sessionId: String?,
        onOutput: (String) -> Unit,
        onInteractivePrompt: (String, Int) -> Unit,
        onComplete: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        onOutput("开始安装 PIP...")

        // 获取会话
        val session = sessionId?.let { TerminalSessionManager.sessions.find { s -> s.id == it } }

        if (session != null) {
            // 使用终端会话执行命令
            TerminalSessionManager.executeSessionCommand(
                    context,
                    session,
                    "pkg update -y && pip install --upgrade pip",
                    onOutput,
                    onInteractivePrompt,
                    onComplete = { exitCode, success ->
                        onOutput("PIP 安装" + if (success) "成功" else "失败")
                        onComplete(success)
                    }
            )
        } else {
            // 回退到直接执行命令
            val result =
                    TermuxCommandExecutor.executeCommand(
                            context = context,
                            command = "pkg update -y && pip install --upgrade pip",
                            autoAuthorize = true,
                            resultCallback = { result ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    onOutput("PIP 安装" + if (result.success) "成功" else "失败")
                                    onComplete(result.success)
                                }
                            }
                    )

            if (!result.success) {
                CoroutineScope(Dispatchers.Main).launch {
                    onOutput("命令发送失败: ${result.stderr}")
                    onComplete(false)
                }
            }
        }
    }
}

// 安装Node.js
suspend fun installNodejs(
        context: Context,
        sessionId: String?,
        onOutput: (String) -> Unit,
        onInteractivePrompt: (String, Int) -> Unit,
        onComplete: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        onOutput("开始安装 Node.js...")

        // 获取会话
        val session = sessionId?.let { TerminalSessionManager.sessions.find { s -> s.id == it } }

        if (session != null) {
            // 使用终端会话执行命令
            TerminalSessionManager.executeSessionCommand(
                    context,
                    session,
                    "pkg update -y && pkg install nodejs -y",
                    onOutput,
                    onInteractivePrompt,
                    onComplete = { exitCode, success ->
                        onOutput("Node.js 安装" + if (success) "成功" else "失败")
                        onComplete(success)
                    }
            )
        } else {
            // 回退到直接执行命令
            val result =
                    TermuxCommandExecutor.executeCommand(
                            context = context,
                            command = "pkg update -y && pkg install nodejs -y",
                            autoAuthorize = true,
                            resultCallback = { result ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    onOutput("Node.js 安装" + if (result.success) "成功" else "失败")
                                    onComplete(result.success)
                                }
                            }
                    )

            if (!result.success) {
                CoroutineScope(Dispatchers.Main).launch {
                    onOutput("命令发送失败: ${result.stderr}")
                    onComplete(false)
                }
            }
        }
    }
}

// 安装Git
suspend fun installGit(
        context: Context,
        sessionId: String?,
        onOutput: (String) -> Unit,
        onInteractivePrompt: (String, Int) -> Unit,
        onComplete: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        onOutput("开始安装 Git...")

        // 获取会话
        val session = sessionId?.let { TerminalSessionManager.sessions.find { s -> s.id == it } }

        if (session != null) {
            // 使用终端会话执行命令
            TerminalSessionManager.executeSessionCommand(
                    context,
                    session,
                    "pkg update -y && pkg install git -y",
                    onOutput,
                    onInteractivePrompt,
                    onComplete = { exitCode, success ->
                        onOutput("Git 安装" + if (success) "成功" else "失败")
                        onComplete(success)
                    }
            )
        } else {
            // 回退到直接执行命令
            val result =
                    TermuxCommandExecutor.executeCommand(
                            context = context,
                            command = "pkg update -y && pkg install git -y",
                            autoAuthorize = true,
                            resultCallback = { result ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    onOutput("Git 安装" + if (result.success) "成功" else "失败")
                                    onComplete(result.success)
                                }
                            }
                    )

            if (!result.success) {
                CoroutineScope(Dispatchers.Main).launch {
                    onOutput("命令发送失败: ${result.stderr}")
                    onComplete(false)
                }
            }
        }
    }
}

// 更新软件包
suspend fun updatePackages(
        context: Context,
        sessionId: String?,
        onOutput: (String) -> Unit,
        onInteractivePrompt: (String, Int) -> Unit,
        onComplete: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        onOutput("开始更新软件包...")

        // 获取会话
        val session = sessionId?.let { TerminalSessionManager.sessions.find { s -> s.id == it } }

        if (session != null) {
            // 使用终端会话执行命令
            TerminalSessionManager.executeSessionCommand(
                    context,
                    session,
                    "pkg update -y && pkg upgrade -y",
                    onOutput,
                    onInteractivePrompt,
                    onComplete = { exitCode, success ->
                        onOutput("软件包更新完成，请检查结果")
                        onComplete(success)
                    }
            )
        } else {
            // 回退到直接执行命令
            val result =
                    TermuxCommandExecutor.executeCommand(
                            context = context,
                            command = "pkg update -y && pkg upgrade -y",
                            autoAuthorize = true,
                            resultCallback = { result ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    onOutput("软件包更新" + if (result.success) "成功" else "失败")
                                    onComplete(result.success)
                                }
                            }
                    )

            if (!result.success) {
                CoroutineScope(Dispatchers.Main).launch {
                    onOutput("命令发送失败: ${result.stderr}")
                    onComplete(false)
                }
            }
        }
    }
}
