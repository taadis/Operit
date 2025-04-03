package com.ai.assistance.operit.ui.features.terminal.model

// Updated to use streaming commands exclusively
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ai.assistance.operit.AdbCommandExecutor.CommandResult
import com.ai.assistance.operit.TermuxCommandExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 终端会话
 * 管理一个独立的终端会话，包括命令历史、工作目录等
 */
class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    var workingDirectory: String = "/data/data/com.termux/files/home",
    val commandHistory: SnapshotStateList<TerminalLine> = mutableStateListOf(),
    var commandHistoryIndex: Int = -1,
    var termuxSessionId: String? = null
) {
    
    /**
     * 保存用户输入的命令历史
     */
    private val userInputHistory = mutableListOf<String>()
    
    /**
     * 默认命令提示符
     */
    private val defaultPrompt = "$ "
    
    /**
     * 当前用户 (如果使用su命令切换了用户)
     */
    var currentUser: String? = null
    
    /**
     * 执行命令
     * 通过Termux运行命令并处理特殊命令
     * 返回Flow以实时获取输出
     */
    fun executeCommand(
        context: Context,
        command: String,
        scope: CoroutineScope
    ): Flow<TerminalLine.Output> {
        // 创建输出流
        val outputFlow = MutableSharedFlow<TerminalLine.Output>(replay = 0)
        
        // 在协程中执行命令
        scope.launch(Dispatchers.IO) {
            // 特殊命令处理
            when {
                command.trim() == "clear" -> {
                    commandHistory.clear()
                    return@launch
                }
                command.trim() == "help" -> {
                    outputFlow.emit(TerminalLine.Output(
                        "Available commands:\n" +
                        "  help - Display this help information\n" +
                        "  clear - Clear the terminal\n" +
                        "  cd [directory] - Change directory\n" +
                        "  su [user] - Switch user\n" +
                        "  pwd - Display current working directory\n" +
                        "  exit - Close this terminal session"
                    ))
                    return@launch
                }
                command.trim().startsWith("cd ") -> {
                    // 处理cd命令，更新工作目录
                    handleCdCommand(context, command.substring(3).trim(), outputFlow)
                    return@launch
                }
                command.trim() == "pwd" -> {
                    // 显示当前工作目录
                    outputFlow.emit(TerminalLine.Output(workingDirectory))
                    return@launch
                }
                command.trim().startsWith("su ") || command.trim() == "su" -> {
                    // 处理su命令，切换用户
                    handleSuCommand(context, command, scope, outputFlow)
                    return@launch
                }
                command.trim() == "exit" && currentUser != null -> {
                    // 如果当前是su状态，则退出su而不是终端
                    currentUser = null
                    outputFlow.emit(TerminalLine.Output("Returned to normal user"))
                    return@launch
                }
                else -> {
                    // 将命令添加到用户输入历史
                    if (command.isNotEmpty()) {
                        userInputHistory.add(command)
                    }
                    
                    // 创建命令选项
                    val options = createCommandOptions(command)
                    
                    // 创建输出接收器，以便实时更新终端
                    val outputReceiver = object : TermuxCommandExecutor.Companion.CommandOutputReceiver {
                        private val stdoutBuffer = StringBuilder()
                        private val stderrBuffer = StringBuilder()
                        private var lastOutputLine = ""
                        
                        override fun onStdout(output: String, isComplete: Boolean) {
                            if (output.isEmpty()) return
                            
                            // 调试输出
                            Log.d("TerminalSession", "收到标准输出: $output")
                            
                            // 将输出按行分割并处理
                            val lines = output.split("\n")
                            for (line in lines) {
                                if (line.isEmpty()) continue
                                
                                // 过滤掉控制输出
                                if (line.contains("COMMAND_START") || 
                                    line.contains("COMMAND_END") || 
                                    line.contains("COMMAND_EXIT_CODE:") ||
                                    line.startsWith("STARTED: ")) {
                                    Log.d("TerminalSession", "过滤控制输出: $line")
                                    continue
                                }
                                
                                // 添加到输出缓冲区并显示
                                scope.launch {
                                    outputFlow.emit(TerminalLine.Output(line))
                                }
                                stdoutBuffer.append(line).append("\n")
                                lastOutputLine = line
                            }
                        }
                        
                        override fun onStderr(error: String, isComplete: Boolean) {
                            if (error.isEmpty()) return
                            
                            // 调试输出
                            Log.d("TerminalSession", "收到错误输出: $error")
                            
                            // 检查是否是交互式提示特殊标记
                            if (error.startsWith("INTERACTIVE_PROMPT:")) {
                                // 直接转发交互式提示消息，不做处理
                                scope.launch {
                                    outputFlow.emit(TerminalLine.Output(error))
                                }
                                return
                            }
                            
                            // 将错误输出按行分割并处理
                            val lines = error.split("\n")
                            for (line in lines) {
                                if (line.isEmpty()) continue
                                
                                // 添加到错误缓冲区并显示
                                scope.launch {
                                    outputFlow.emit(TerminalLine.Output(line))
                                }
                                stderrBuffer.append(line).append("\n")
                                lastOutputLine = line
                            }
                        }
                        
                        override fun onComplete(result: CommandResult) {
                            // 调试输出
                            Log.d("TerminalSession", "命令完成: $result")
                            Log.d("TerminalSession", "收到的stdout长度: ${result.stdout.length}, stderr长度: ${result.stderr.length}")
                            
                            // 命令完成，检查是否有未显示的输出
                            scope.launch {
                                try {
                                    // 修改: 确保完整结果显示
                                    if (result.stdout.isNotEmpty()) {
                                        Log.d("TerminalSession", "处理最终stdout: ${result.stdout.length}字节")
                                        // 处理最终输出，按行显示确保格式正确
                                        val finalLines = result.stdout.split("\n")
                                            .filter { it.isNotEmpty() }
                                        
                                        for (line in finalLines) {
                                            // 过滤掉控制输出
                                            if (line.contains("COMMAND_START") || 
                                                line.contains("COMMAND_END") || 
                                                line.contains("COMMAND_EXIT_CODE:") ||
                                                line.startsWith("STARTED: ")) {
                                                continue
                                            }
                                            
                                            // 检查是否已有此行避免重复
                                            if (!stdoutBuffer.toString().contains(line)) {
                                                outputFlow.emit(TerminalLine.Output(line))
                                                Log.d("TerminalSession", "发送输出行: $line")
                                            }
                                        }
                                    }
                                    
                                    if (result.stderr.isNotEmpty()) {
                                        Log.d("TerminalSession", "处理最终stderr: ${result.stderr.length}字节")
                                        // 处理最终错误，按行显示确保格式正确
                                        val finalErrorLines = result.stderr.split("\n")
                                            .filter { it.isNotEmpty() }
                                        
                                        for (line in finalErrorLines) {
                                            if (!stderrBuffer.toString().contains(line)) {
                                                outputFlow.emit(TerminalLine.Output(line))
                                                Log.d("TerminalSession", "发送错误行: $line")
                                            }
                                        }
                                    }
                                    
                                    // 添加命令完成标记，帮助调试
                                    if (!result.success) {
                                        outputFlow.emit(TerminalLine.Output("[命令失败，退出码: ${result.exitCode}]"))
                                    }
                                } catch (e: Exception) {
                                    Log.e("TerminalSession", "处理命令结果时出错: ${e.message}", e)
                                    outputFlow.emit(TerminalLine.Output("[处理输出时出错: ${e.message}]"))
                                }
                            }
                        }
                        
                        override fun onError(error: String, exitCode: Int) {
                            // 调试输出
                            Log.e("TerminalSession", "命令出错: $error (code: $exitCode)")
                            
                            // 显示错误信息
                            scope.launch {
                                outputFlow.emit(TerminalLine.Output("Error: $error (code: $exitCode)"))
                            }
                        }
                    }
                    
                    // 使用TermuxCommandExecutor执行命令并确保结果立即处理
                    try {
                        TermuxCommandExecutor.executeCommandStreaming(
                            context = context,
                            command = command,
                            autoAuthorize = true,
                            background = true,
                            outputReceiver = outputReceiver
                        )
                        // 注意：结果会通过outputReceiver传递
                    } catch (e: Exception) {
                        // 处理异常情况
                        scope.launch {
                            outputFlow.emit(TerminalLine.Output("Error executing command: ${e.message}"))
                        }
                    }
                }
            }
        }
        
        return outputFlow
    }
    
    /**
     * 处理CD命令
     */
    private suspend fun handleCdCommand(
        context: Context,
        directory: String,
        outputFlow: MutableSharedFlow<TerminalLine.Output>
    ) {
        val cdCommand = if (directory.startsWith("/")) {
            "cd \"$directory\" && pwd"
        } else {
            "cd \"$workingDirectory\" && cd \"$directory\" && pwd"
        }
        
        // 创建命令选项，默认使用当前用户
        val options = TermuxCommandExecutor.Companion.CommandOptions(
            workingDirectory = workingDirectory,
            background = true,
            sessionAction = TermuxCommandExecutor.Companion.SessionAction.ACTION_NEW_SESSION,
            user = currentUser
        )
        
        // 创建输出接收器
        val outputReceiver = object : TermuxCommandExecutor.Companion.CommandOutputReceiver {
            private var result = ""
            
            override fun onStdout(output: String, isComplete: Boolean) {
                if (output.isNotEmpty()) {
                    result = output.trim()
                }
            }
            
            override fun onStderr(error: String, isComplete: Boolean) {
                if (error.isNotEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        outputFlow.emit(TerminalLine.Output("cd: $error"))
                    }
                }
            }
            
            override fun onComplete(finalResult: CommandResult) {
                CoroutineScope(Dispatchers.IO).launch {
                    if (finalResult.success && result.isNotEmpty()) {
                        // 更新工作目录
                        workingDirectory = result
                        outputFlow.emit(TerminalLine.Output(""))
                    } else if (!finalResult.success) {
                        outputFlow.emit(TerminalLine.Output("cd: ${finalResult.stderr}"))
                    }
                }
            }
            
            override fun onError(error: String, exitCode: Int) {
                CoroutineScope(Dispatchers.IO).launch {
                    outputFlow.emit(TerminalLine.Output("cd: $error"))
                }
            }
        }
        
        TermuxCommandExecutor.executeCommandStreaming(
            context = context,
            command = cdCommand,
            autoAuthorize = true,
            background = true,
            outputReceiver = outputReceiver
        )
    }
    
    /**
     * 处理su命令
     */
    private suspend fun handleSuCommand(
        context: Context,
        command: String,
        scope: CoroutineScope,
        outputFlow: MutableSharedFlow<TerminalLine.Output>
    ) {
        // 提取用户名，如果没有提供则默认为root
        val parts = command.trim().split("\\s+".toRegex())
        val user = if (parts.size > 1) parts[1] else "root"
        
        // 执行su命令检查是否成功
        val checkCmd = "su $user -c 'whoami'"
        val options = TermuxCommandExecutor.Companion.CommandOptions(
            workingDirectory = workingDirectory,
            background = true,
            sessionAction = TermuxCommandExecutor.Companion.SessionAction.ACTION_NEW_SESSION
        )
        
        // 创建输出接收器
        val outputReceiver = object : TermuxCommandExecutor.Companion.CommandOutputReceiver {
            override fun onStdout(output: String, isComplete: Boolean) {
                // 不需要处理中间输出
            }
            
            override fun onStderr(error: String, isComplete: Boolean) {
                if (error.isNotEmpty()) {
                    scope.launch {
                        outputFlow.emit(TerminalLine.Output("Error: $error"))
                    }
                }
            }
            
            override fun onComplete(result: CommandResult) {
                scope.launch {
                    if (result.success) {
                        // 切换用户成功
                        currentUser = user
                        outputFlow.emit(TerminalLine.Output("Switched to user: $user"))
                    } else {
                        // 切换失败
                        outputFlow.emit(TerminalLine.Output("Failed to switch user: ${result.stderr}"))
                    }
                }
            }
            
            override fun onError(error: String, exitCode: Int) {
                scope.launch {
                    outputFlow.emit(TerminalLine.Output("Failed to switch user: $error"))
                }
            }
        }
        
        TermuxCommandExecutor.executeCommandStreaming(
            context = context,
            command = checkCmd,
            autoAuthorize = true,
            background = true,
            outputReceiver = outputReceiver
        )
    }
    
    /**
     * 创建命令选项
     */
    private fun createCommandOptions(command: String): TermuxCommandExecutor.Companion.CommandOptions {
        // 确定会话操作模式
        val sessionAction = if (termuxSessionId != null) {
            TermuxCommandExecutor.Companion.SessionAction.ACTION_USE_CURRENT_SESSION 
        } else {
            TermuxCommandExecutor.Companion.SessionAction.ACTION_NEW_SESSION
        }
        
        // 创建命令选项
        return TermuxCommandExecutor.Companion.CommandOptions(
            workingDirectory = workingDirectory,
            background = true,
            sessionAction = sessionAction,
            label = "Session command: $name",
            description = "Running: $command",
            user = currentUser
        )
    }
    
    /**
     * 获取命令提示符
     */
    fun getPrompt(): String {
        // 提取工作目录最后一部分作为当前目录
        val currentDir = workingDirectory.split("/").last().ifEmpty { "/" }
        
        // 如果是切换用户状态，则显示不同的提示符
        return if (currentUser != null) {
            if (currentUser == "root") {
                "[$currentDir]# "
            } else {
                "[$currentUser@$currentDir]$ "
            }
        } else {
            "[$currentDir]$ "
        }
    }
    
    /**
     * 获取上一条命令历史
     */
    fun getPreviousCommand(): String? {
        if (userInputHistory.isEmpty()) return null
        
        if (commandHistoryIndex == -1) {
            commandHistoryIndex = userInputHistory.size - 1
        } else if (commandHistoryIndex > 0) {
            commandHistoryIndex--
        }
        
        return if (commandHistoryIndex >= 0 && commandHistoryIndex < userInputHistory.size) {
            userInputHistory[commandHistoryIndex]
        } else {
            null
        }
    }
    
    /**
     * 获取下一条命令历史
     */
    fun getNextCommand(): String? {
        if (userInputHistory.isEmpty() || commandHistoryIndex == -1) return null
        
        if (commandHistoryIndex < userInputHistory.size - 1) {
            commandHistoryIndex++
            return userInputHistory[commandHistoryIndex]
        } else {
            commandHistoryIndex = userInputHistory.size
            return ""
        }
    }
    
    /**
     * 重置命令历史索引
     */
    fun resetCommandHistoryIndex() {
        commandHistoryIndex = -1
    }
}

/**
 * 终端行类型
 */
sealed class TerminalLine {
    data class Input(val text: String, val prompt: String = "$ ") : TerminalLine()
    data class Output(val text: String) : TerminalLine()
} 