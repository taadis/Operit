package com.ai.assistance.operit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ai.assistance.operit.AdbCommandExecutor.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import android.content.BroadcastReceiver
import android.os.Build
import android.app.PendingIntent
import android.os.Bundle
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException

/**
 * 用于执行Termux命令的工具类
 */
class TermuxCommandExecutor {
    companion object {
        private const val TAG = "TermuxCommandExecutor"
        private const val PACKAGE_NAME = "com.termux"
        private const val ACTIVITY_NAME = "com.termux.app.TermuxActivity"
        private const val EXTRA_COMMAND = "com.termux.app.execute_command_key"
        private const val EXTRA_RUNNER_SERVICE = "com.termux.run_command.RunCommandService"
        private const val EXTRA_SESSION_ACTION = "session_action"
        private const val DEFAULT_TIMEOUT = 30000L // 默认超时时间：30秒
        private const val INACTIVITY_TIMEOUT = 15000L // 无活动超时时间：15秒
        private var EXECUTION_ID = 1000
        
        /**
         * 命令输出接收器接口
         * 用于接收命令执行过程中的实时输出
         */
        interface CommandOutputReceiver {
            /**
             * 接收标准输出内容
             * @param output 输出内容
             * @param isComplete 是否是最后一次输出
             */
            fun onStdout(output: String, isComplete: Boolean)
            
            /**
             * 接收标准错误内容
             * @param error 错误内容
             * @param isComplete 是否是最后一次输出
             */
            fun onStderr(error: String, isComplete: Boolean)
            
            /**
             * 命令执行完成
             * @param result 最终执行结果
             */
            fun onComplete(result: CommandResult)
            
            /**
             * 命令执行出错
             * @param error 错误信息
             * @param exitCode 退出码
             */
            fun onError(error: String, exitCode: Int)
        }
        
        /**
         * 命令输出接收器的默认实现，用于转换为最终结果回调
         */
        private class DefaultCommandOutputReceiver(
            private val resultCallback: (CommandResult) -> Unit
        ) : CommandOutputReceiver {
            private val stdoutBuilder = StringBuilder()
            private val stderrBuilder = StringBuilder()
            
            override fun onStdout(output: String, isComplete: Boolean) {
                stdoutBuilder.append(output)
            }
            
            override fun onStderr(error: String, isComplete: Boolean) {
                stderrBuilder.append(error)
            }
            
            override fun onComplete(result: CommandResult) {
                resultCallback(result)
            }
            
            override fun onError(error: String, exitCode: Int) {
                resultCallback(CommandResult(false, stdoutBuilder.toString(), error, exitCode))
            }
        }
        
        /**
         * 获取下一个执行ID
         * 用于确保每个命令执行有唯一的ID
         */
        private fun getNextExecutionId(): Int {
            return EXECUTION_ID++
        }
        
        /**
         * 会话操作类型
         */
        object SessionAction {
            const val ACTION_NEW_SESSION = "0"
            const val ACTION_USE_CURRENT_SESSION = "1"
            const val ACTION_SWITCH_TO_NEW_SESSION = "2"
        }
        
        /**
         * 执行Termux命令的高级配置选项
         */
        data class CommandOptions(
            val executable: String = "/data/data/com.termux/files/usr/bin/bash",
            val arguments: Array<String> = arrayOf(),
            val workingDirectory: String = "/data/data/com.termux/files/home",
            val background: Boolean = true,
            val sessionAction: String = SessionAction.ACTION_NEW_SESSION,
            val label: String? = null,
            val description: String? = null,
            val stdin: String? = null,
            val user: String? = null,
            val timeout: Long = DEFAULT_TIMEOUT
            // 移除streamOutput选项，因为现在总是使用流式输出
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as CommandOptions

                if (executable != other.executable) return false
                if (!arguments.contentEquals(other.arguments)) return false
                if (workingDirectory != other.workingDirectory) return false
                if (background != other.background) return false
                if (sessionAction != other.sessionAction) return false
                if (label != other.label) return false
                if (description != other.description) return false
                if (stdin != other.stdin) return false
                if (user != other.user) return false
                if (timeout != other.timeout) return false

                return true
            }

            override fun hashCode(): Int {
                var result = executable.hashCode()
                result = 31 * result + arguments.contentHashCode()
                result = 31 * result + workingDirectory.hashCode()
                result = 31 * result + background.hashCode()
                result = 31 * result + sessionAction.hashCode()
                result = 31 * result + (label?.hashCode() ?: 0)
                result = 31 * result + (description?.hashCode() ?: 0)
                result = 31 * result + (stdin?.hashCode() ?: 0)
                result = 31 * result + (user?.hashCode() ?: 0)
                result = 31 * result + timeout.hashCode()
                return result
            }
        }
        
        // 添加调试函数来检查权限
        private fun checkAdbPermissions(context: Context) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    Log.d(TAG, "=== 验证ADB权限及功能 ===")
                    // 检查是否能创建文件
                    val testFile = "/data/data/com.termux/files/home/.termux_test_file"
                    val createResult = AdbCommandExecutor.executeAdbCommand("run-as com.termux sh -c 'touch \"$testFile\" && echo \"Test content\" > \"$testFile\" && echo \"Created\"'")
                    Log.d(TAG, "创建测试文件结果: ${createResult.success}, 输出: ${createResult.stdout}")
                    
                    // 检查是否能读取文件
                    val readResult = AdbCommandExecutor.executeAdbCommand("run-as com.termux sh -c 'cat \"$testFile\"'")
                    Log.d(TAG, "读取测试文件结果: ${readResult.success}, 输出: ${readResult.stdout}")
                    
                    // 检查是否能获取文件大小
                    val sizeResult = AdbCommandExecutor.executeAdbCommand("run-as com.termux sh -c 'stat -c %s \"$testFile\" 2>/dev/null || stat -f %z \"$testFile\"'")
                    Log.d(TAG, "获取文件大小结果: ${sizeResult.success}, 输出: ${sizeResult.stdout}")
                    
                    // 删除测试文件
                    val deleteResult = AdbCommandExecutor.executeAdbCommand("run-as com.termux sh -c 'rm \"$testFile\"' && echo 'Deleted'")
                    Log.d(TAG, "删除测试文件结果: ${deleteResult.success}, 输出: ${deleteResult.stdout}")
                    
                    Log.d(TAG, "=== ADB权限检查完成 ===")
                } catch (e: Exception) {
                    Log.e(TAG, "检查ADB权限时出错", e)
                }
            }
        }
        
        /**
         * 使用Termux执行命令
         */
        fun executeCommand(
            context: Context,
            command: String,
            autoAuthorize: Boolean = true,
            background: Boolean = false,
            options: CommandOptions = CommandOptions()
        ): CommandResult {
            // 启动权限检查
            checkAdbPermissions(context)
            
            // 简化的实现，仅用于兼容旧代码
            // 实际应该使用executeCommandStreaming
            return CommandResult(
                success = false,
                stdout = "",
                stderr = "请使用executeCommandStreaming替代此方法",
                exitCode = -1
            )
        }
        
        /**
         * 执行Termux命令
         * @param context 上下文
         * @param command 要执行的命令
         * @param autoAuthorize 如果Termux未授权，是否自动尝试授权
         * @param background 是否在后台执行命令
         * @param resultCallback 命令执行结果回调，如果为null则不接收结果
         * @param outputReceiver 流式输出接收器
         * @return 执行结果 (仅表示命令是否成功发送，不代表实际执行结果)
         */
        suspend fun executeCommand(
            context: Context, 
            command: String,
            autoAuthorize: Boolean = true,
            background: Boolean = true,
            resultCallback: ((CommandResult) -> Unit)? = null,
            outputReceiver: CommandOutputReceiver? = null
        ): CommandResult = withContext(Dispatchers.IO) {
            // 强制使用流式输出方法，不提供非流式选项
            executeCommandStreaming(
                context = context,
                command = command,
                autoAuthorize = autoAuthorize,
                background = background,
                outputReceiver = outputReceiver,
                resultCallback = resultCallback
            )
        }
        
        /**
         * 执行Termux命令 (使用流式输出)
         * 该方法会默认使用流式输出模式，替代旧的executeCommand方法
         * @param context 上下文
         * @param command 要执行的命令
         * @param autoAuthorize 如果Termux未授权，是否自动尝试授权
         * @param background 是否在后台执行命令
         * @param outputReceiver 命令输出接收器，用于接收实时输出
         * @param resultCallback 命令执行结果回调，在不提供outputReceiver时使用
         * @return 执行结果 (仅表示命令是否成功发送，不代表实际执行结果)
         */
        suspend fun executeCommandStreaming(
            context: Context, 
            command: String,
            autoAuthorize: Boolean = true,
            background: Boolean = true,
            outputReceiver: CommandOutputReceiver? = null,
            resultCallback: ((CommandResult) -> Unit)? = null
        ): CommandResult = withContext(Dispatchers.IO) {
            try {
                // 检查ADB权限
                GlobalScope.launch(Dispatchers.IO) {
                    checkAdbPermissions(context)
                }
                
                Log.d(TAG, "执行命令: $command")
                
                // 首先检查Termux是否已安装
                if (!TermuxInstaller.isTermuxInstalled(context)) {
                    val errorResult = CommandResult(
                        success = false,
                        stdout = "",
                        stderr = "Termux is not installed",
                        exitCode = -1
                    )
                    outputReceiver?.onError("Termux is not installed", -1)
                    return@withContext errorResult
                }
                
                // 检查是否已授权，如果需要则尝试授权
                if (autoAuthorize && !TermuxAuthorizer.isTermuxAuthorized(context)) {
                    val authorized = TermuxAuthorizer.authorizeTermux(context)
                    if (!authorized) {
                        val errorResult = CommandResult(
                            success = false,
                            stdout = "",
                            stderr = "Failed to authorize Termux",
                            exitCode = -1
                        )
                        outputReceiver?.onError("Failed to authorize Termux", -1)
                        return@withContext errorResult
                    }
                }
                
                // 内部执行逻辑块
                try {
                    // 生成一个唯一的执行ID
                    val executionId = getNextExecutionId()
                    
                    // 处理特殊用户权限
                    val finalCommand = if (command.contains("su ") && !command.startsWith("su ")) {
                        "su -c \"$command\""
                    } else {
                        command
                    }
                    
                    // 创建一个临时文件用于输出，使用ADB创建以确保权限正确
                    val tempOutputFile = "/data/data/com.termux/files/home/.termux_output_${executionId}.log"
                    
                    // 直接通过ADB创建临时文件，而不是依赖Termux创建
                    try {
                        val testCmd = "run-as com.termux sh -c 'touch \"$tempOutputFile\" && chmod 666 \"$tempOutputFile\" && echo \"INIT_TEST_${executionId}\" > \"$tempOutputFile\"'"
                        val testResult = AdbCommandExecutor.executeAdbCommand(testCmd)
                        Log.d(TAG, "ADB创建文件测试: $testResult")
                        
                        // 验证文件是否创建成功
                        val checkCmd = "run-as com.termux sh -c 'ls -la \"$tempOutputFile\" && cat \"$tempOutputFile\"'"
                        val checkResult = AdbCommandExecutor.executeAdbCommand(checkCmd)
                        Log.d(TAG, "验证文件创建: ${checkResult.stdout}")
                    } catch (e: Exception) {
                        Log.e(TAG, "ADB创建文件测试失败", e)
                    }
                    
                    // 创建一个不断回显的命令，使用stdbuf禁用缓冲
                    val wrappedCommand = """
                        {
                            # 文件应该已经通过ADB创建，检查文件是否存在
                            if [ -f "$tempOutputFile" ]; then
                                echo "DEBUG: Temp file already exists, proceeding" >&2
                            else
                                echo "DEBUG: Temp file does not exist yet, waiting..." >&2
                                # 尝试等待文件
                                for i in {1..5}; do
                                    sleep 1
                                    if [ -f "$tempOutputFile" ]; then
                                        echo "DEBUG: Temp file now exists after waiting" >&2
                                        break
                                    fi
                                    echo "DEBUG: Still waiting for temp file (attempt ${'$'}i)" >&2
                                done
                                
                                # 如果还是没有，尝试再次创建
                                if [ ! -f "$tempOutputFile" ]; then
                                    echo "DEBUG: File still doesn't exist, trying to create it" >&2
                                    touch "$tempOutputFile"
                                    chmod 666 "$tempOutputFile"
                                fi
                            fi
                            
                            # 清空文件内容，重新开始
                            echo "" > "$tempOutputFile"
                            echo "DEBUG: Prepared temp file for output" >&2
                            
                            # 执行命令并同时将输出重定向到文件
                            {
                                # 使用最简单的输出重定向，确保可靠
                                echo "DEBUG: Starting command execution" >&2
                                {
                                    # 添加更多输出, 以确保用户能看到命令实际执行情况
                                    echo "执行命令: $finalCommand"
                                    echo "执行时间: $(date)"
                                    echo "---开始执行---"
                                    
                                    $finalCommand
                                    
                                    echo "---执行完成---"
                                    echo "退出代码: ${'$'}?"
                                    echo "COMMAND_COMPLETE:${'$'}?" >> "$tempOutputFile"
                                } 2>&1 | tee -a "$tempOutputFile"
                                
                                echo "DEBUG: Command execution completed" >&2
                            }
                            
                            # 检查文件内容
                            echo "DEBUG: Final file size: $(stat -c %s "$tempOutputFile" 2>/dev/null || stat -f %z "$tempOutputFile")" >&2
                        }
                    """.trimIndent()
                
                // 构建Intent
                val intent = Intent()
                    .setClassName("com.termux", "com.termux.app.RunCommandService")
                    .setAction("com.termux.RUN_COMMAND")
                
                // 设置命令路径和参数
                intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", wrappedCommand))
                intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                    intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background) // 使用传入的background参数决定是否在后台执行
                intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                
                    // 创建一个CompletableDeferred以等待命令完成
                    val commandCompleted = CompletableDeferred<CommandResult>()
                    
                    // 初始化输出收集器
                    val stdoutBuilder = StringBuilder()
                    val stderrBuilder = StringBuilder()
                    
                    // 如果提供了resultCallback，创建一个能够实时转发输出的特殊接收器
                    val effectiveOutputReceiver = if (resultCallback != null && outputReceiver == null) {
                        object : CommandOutputReceiver {
                            override fun onStdout(output: String, isComplete: Boolean) {
                                stdoutBuilder.append(output)
                                // 实时回调每次收到的输出
                                if (!isComplete) {
                                    // 每次收到输出就立即回调一个临时结果
                                    Handler(Looper.getMainLooper()).post {
                                        resultCallback(CommandResult(
                                            success = true, 
                                            stdout = output,  // 只包含这次新收到的输出
                                            stderr = "",
                                            exitCode = 0
                                        ))
                                    }
                                }
                            }
                            
                            override fun onStderr(error: String, isComplete: Boolean) {
                                stderrBuilder.append(error)
                                // 实时回调每次收到的错误
                                if (!isComplete) {
                                    // 每次收到错误就立即回调一个临时结果
                                    Handler(Looper.getMainLooper()).post {
                                        resultCallback(CommandResult(
                                            success = true, 
                                            stdout = "",
                                            stderr = error,  // 只包含这次新收到的错误
                                            exitCode = 0
                                        ))
                                    }
                                }
                            }
                            
                            override fun onComplete(result: CommandResult) {
                                // 命令完成时，回调最终结果
                            Handler(Looper.getMainLooper()).post {
                                resultCallback(result)
                                }
                            }
                            
                            override fun onError(error: String, exitCode: Int) {
                                // 命令出错时，回调错误结果
                                Handler(Looper.getMainLooper()).post {
                                    resultCallback(CommandResult(
                                        success = false,
                                        stdout = stdoutBuilder.toString(),
                                        stderr = error,
                                        exitCode = exitCode
                                    ))
                                }
                            }
                        }
                    } else {
                        outputReceiver
                    }
                    
                    // 创建结果处理回调
                    TermuxCommandResultService.registerCallback(executionId) { result ->
                        Log.d(TAG, "收到命令执行结果: $result")
                        
                        // 命令已完成，使用AdbCommandExecutor清理临时文件
                        GlobalScope.launch(Dispatchers.IO) {
                            // 等待一会确保所有输出都被处理
                            delay(500)
                            
                            // 使用AdbCommandExecutor删除临时文件，避免再次调用Termux
                            try {
                                AdbCommandExecutor.executeAdbCommand("run-as com.termux sh -c 'rm -f \"$tempOutputFile\"'")
                                Log.d(TAG, "临时文件已通过ADB删除: $tempOutputFile")
                        } catch (e: Exception) {
                                Log.e(TAG, "删除临时文件失败: ${e.message}")
                            }
                        }
                        
                        // 触发完成回调
                        val finalResult = CommandResult(
                            success = result.success,
                            stdout = result.stdout,  // 确保使用原始结果中的stdout
                            stderr = result.stderr,  // 确保使用原始结果中的stderr
                            exitCode = result.exitCode
                        )
                        
                        // 确保stdout和stderr非空
                        if (result.stdout.isNotEmpty() && stdoutBuilder.toString().isEmpty()) {
                            stdoutBuilder.append(result.stdout)
                        }
                        
                        if (result.stderr.isNotEmpty() && stderrBuilder.toString().isEmpty()) {
                            stderrBuilder.append(result.stderr)
                        }
                        
                        Log.d(TAG, "传递给接收器的最终结果: stdout长度=${finalResult.stdout.length}, stderr长度=${finalResult.stderr.length}")
                        effectiveOutputReceiver?.onComplete(finalResult)
                        
                        // 完成延迟对象
                        commandCompleted.complete(finalResult)
                    }
                    
                    // 创建PendingIntent
                    val resultIntent = Intent(context, TermuxCommandResultService::class.java)
                    resultIntent.putExtra(TermuxCommandResultService.EXTRA_EXECUTION_ID, executionId)
                    
                        val finalFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        
                    val pendingIntent = PendingIntent.getService(
                            context.applicationContext,
                            executionId,
                            resultIntent,
                            finalFlags
                        )
                        
                        // 添加PendingIntent到Intent
                        intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent)
                        
                    // 为实现流式输出，需要创建一个输出更新接收器
                    val outputReceiveAction = "com.termux.app.OUTPUT_$executionId"
                    val outputResultFilter = IntentFilter(outputReceiveAction)
                    
                    // 创建输出更新的广播接收器
                    val broadcastReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent == null || intent.extras == null) return
                            
                            val resultBundle = intent.getBundleExtra("result")
                            if (resultBundle != null) {
                                val stdout = resultBundle.getString("stdout", "")
                                val stderr = resultBundle.getString("stderr", "")
                                
                                // 使用Handler.post确保回调在主线程上执行，但不引入延迟
                                val mainHandler = Handler(Looper.getMainLooper())
                                
                                if (stdout.isNotEmpty()) {
                                    Log.d(TAG, "流式输出 stdout: $stdout")
                                    stdoutBuilder.append(stdout)
                                    
                                    // 立即在主线程上回调，确保实时性
                                    mainHandler.post {
                                        effectiveOutputReceiver?.onStdout(stdout, false)
                                    }
                                }
                                
                                if (stderr.isNotEmpty()) {
                                    Log.d(TAG, "流式输出 stderr: $stderr")
                                    stderrBuilder.append(stderr)
                                    
                                    // 立即在主线程上回调，确保实时性
                                    mainHandler.post {
                                        effectiveOutputReceiver?.onStderr(stderr, false)
                                    }
                                }
                            }
                        }
                    }
                    
                    // 注册输出接收器
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(broadcastReceiver, outputResultFilter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        context.registerReceiver(broadcastReceiver, outputResultFilter)
                    }
                    
                    // 创建输出接收的PendingIntent
                    val outputIntent = Intent(outputReceiveAction)
                    outputIntent.setPackage(context.packageName)
                    
                    val outputFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    
                    val outputPendingIntent = PendingIntent.getBroadcast(
                        context.applicationContext,
                        executionId + 1,  // 使用不同的请求码
                        outputIntent,
                        outputFlags
                    )
                    
                    // 添加输出接收PendingIntent，设置更短的更新间隔以提高响应性
                    intent.putExtra("com.termux.RUN_COMMAND_OUTPUT_PENDING_INTENT", outputPendingIntent)
                    intent.putExtra("com.termux.RUN_COMMAND_OUTPUT_INTERVAL", 50L)  // 输出更新间隔设置为更短的50毫秒，提高实时性
                    
                    // 发送命令
                    context.startService(intent)
                    Log.d(TAG, "已发送命令到Termux: $command, 执行ID: $executionId")
                    
                    // 使用一个额外的标志表示命令是否已完成
                    val commandIsRunning = AtomicBoolean(true)
                    
                    // 添加文件监控活动时间跟踪
                    val lastActivityTime = AtomicLong(System.currentTimeMillis())
                    val isDataBeingRead = AtomicBoolean(false)
                    
                    // 创建命令结果变量，用于文件监控线程更新
                    val monitoredResult = object {
                        var exitCode: Int = -1
                        var success: Boolean = false
                    }
                    
                    // 启动使用ADB的文件监控线程
                    val fileMonitorThread = Thread {
                        try {
                            var lastSize = 0L
                            
                            // 添加日志，记录文件监控开始
                            Log.d(TAG, "开始文件监控: $tempOutputFile")
                            
                            // 循环检查文件变化，直到命令完成
                            while (commandIsRunning.get()) {
                                // 使用AdbCommandExecutor获取文件大小
                                val sizeCommand = "run-as com.termux sh -c 'if [ -f \"$tempOutputFile\" ]; then echo \"EXISTS=\"; stat -c %s \"$tempOutputFile\" 2>/dev/null || stat -f %z \"$tempOutputFile\"; else echo \"NOT_EXISTS\"; fi'"
                                Log.d(TAG, "检查文件状态: $sizeCommand")
                                
                                val sizeResult = runCatching {
                                    val latch = CountDownLatch(1)
                                    var result: CommandResult? = null
                                    
                                    GlobalScope.launch(Dispatchers.IO) {
                                        result = AdbCommandExecutor.executeAdbCommand(sizeCommand)
                                        latch.countDown()
                                    }
                                    
                                    latch.await(5, TimeUnit.SECONDS)
                                    result
                                }.getOrNull()
                                
                                // 解析文件大小结果
                                val sizeOutput = sizeResult?.stdout?.trim() ?: ""
                                Log.d(TAG, "文件大小检查结果: '$sizeOutput'")
                                
                                // 如果文件不存在，继续循环
                                if (sizeOutput.isEmpty() || sizeOutput.contains("NOT_EXISTS")) {
                                    Log.d(TAG, "文件不存在，等待创建...")
                                    Thread.sleep(50) // 短暂等待后重试
                                    continue
                                }
                                
                                // 提取数字部分作为文件大小
                                val sizePart = if (sizeOutput.startsWith("EXISTS=")) {
                                    sizeOutput.substringAfter("EXISTS=").trim()
                                } else {
                                    sizeOutput.trim()
                                }
                                
                                val newSize = try {
                                    sizePart.toLong()
                                } catch (e: NumberFormatException) {
                                    Log.e(TAG, "无法解析文件大小: $sizePart", e)
                                    Thread.sleep(50) // 短暂等待后重试
                                    continue
                                }
                                
                                // 如果文件大小没有变化，等待后继续
                                if (newSize <= lastSize) {
                                    Thread.sleep(50) // 短暂等待后重试
                                    continue
                                }
                                
                                // 使用AdbCommandExecutor读取新增的内容
                                val readCommand = if (newSize - lastSize > 1024) {
                                    // 对于大块数据，使用更高效的读取方式
                                    "run-as com.termux sh -c 'dd if=\"$tempOutputFile\" bs=1024 skip=${lastSize / 1024} count=${(newSize - lastSize + 1023) / 1024} 2>/dev/null | dd bs=1 skip=${lastSize % 1024} count=${newSize - lastSize} 2>/dev/null'"
                } else {
                                    // 对于小块数据，直接按字节读取
                                    "run-as com.termux sh -c 'dd if=\"$tempOutputFile\" bs=1 skip=$lastSize count=${newSize - lastSize} 2>/dev/null'"
                                }
                                Log.d(TAG, "读取命令: $readCommand, 从位置 $lastSize 读取 ${newSize - lastSize} 字节")
                                
                                val readResult = runCatching {
                                    val latch = CountDownLatch(1)
                                    var result: CommandResult? = null
                                    
                                    GlobalScope.launch(Dispatchers.IO) {
                                        result = AdbCommandExecutor.executeAdbCommand(readCommand)
                                        latch.countDown()
                                    }
                                    
                                    latch.await(5, TimeUnit.SECONDS)
                                    result
                                }.getOrNull()
                                
                                // 解析读取结果
                                var output = readResult?.stdout ?: ""
                                
                                // 如果读取失败，尝试备用读取方法
                                if (output.isEmpty() && readResult?.stderr?.isNotEmpty() == true) {
                                    Log.w(TAG, "使用dd读取失败: ${readResult.stderr}，尝试备用方法")
                                    
                                    // 备用方法：使用cat加head/tail
                                    val fallbackCommand = "run-as com.termux sh -c 'cat \"$tempOutputFile\" | head -c ${lastSize + (newSize - lastSize)} | tail -c ${newSize - lastSize}'"
                                    val fallbackResult = runCatching {
                                        val latch = CountDownLatch(1)
                                        var result: CommandResult? = null
                                        
                                        GlobalScope.launch(Dispatchers.IO) {
                                            result = AdbCommandExecutor.executeAdbCommand(fallbackCommand)
                                            latch.countDown()
                                        }
                                        
                                        latch.await(5, TimeUnit.SECONDS)
                                        result
                                    }.getOrNull()
                                    
                                    output = fallbackResult?.stdout ?: ""
                                    if (output.isNotEmpty()) {
                                        Log.d(TAG, "备用读取方法成功")
                                    }
                                }
                                
                                Log.d(TAG, "读取新增内容 (${output.length} 字节): ${if (output.length > 50) output.substring(0, 50) + "..." else output}")
                                
                                // 检查是否包含命令完成标记
                                if (output.contains("COMMAND_COMPLETE:")) {
                                    Log.d(TAG, "检测到命令完成标记")
                                    val exitCodePattern = "COMMAND_COMPLETE:(\\d+)".toRegex()
                                    val matchResult = exitCodePattern.find(output)
                                    val exitCode = matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                    
                                    // 设置命令结果并停止监控
                                    monitoredResult.exitCode = exitCode
                                    monitoredResult.success = exitCode == 0
                                    Log.d(TAG, "命令执行完成，退出码: $exitCode, 成功: ${monitoredResult.success}")
                                    commandIsRunning.set(false)
                                }
                                
                                // 处理获取到的内容
                                if (output.isNotEmpty()) {
                                    // 更新最后活动时间
                                    lastActivityTime.set(System.currentTimeMillis())
                                    isDataBeingRead.set(true)
                                    
                                    Handler(Looper.getMainLooper()).post {
                                        effectiveOutputReceiver?.onStdout(output, false)
                                    }
                                    stdoutBuilder.append(output)
                                }
                                
                                lastSize = newSize
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "文件监控异常: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    
                    // 启动文件监控
                    fileMonitorThread.start()
                    
                    // 等待命令完成或超时
                    try {
                        // 使用自定义超时逻辑
                        var waitingForResult = true
                        val startTime = System.currentTimeMillis()
                        
                        while (waitingForResult) {
                            // 尝试在短时间内等待命令完成
                            try {
                                val result = withTimeoutOrNull(100L) {
                                    commandCompleted.await()
                                }
                                
                                // 如果命令完成，直接返回结果
                                if (result != null) {
                                    return@withContext result
                }
            } catch (e: Exception) {
                                Log.e(TAG, "等待命令完成时出错: ${e.message}")
                            }
                            
                            // 检查是否有活动或是否已超过最大超时时间
                            val currentTime = System.currentTimeMillis()
                            val totalElapsed = currentTime - startTime
                            val inactivityTime = currentTime - lastActivityTime.get()
                            
                            // 日志记录当前状态
                            if (totalElapsed % 5000 < 100) {
                                // 每5秒记录一次状态
                                Log.d(TAG, "命令仍在执行 - 总时间: ${totalElapsed/1000}秒, 无活动时间: ${inactivityTime/1000}秒, 数据读取中: ${isDataBeingRead.get()}")
                            }
                            
                            // 判断是否应该超时:
                            // 1. 如果总时间超过DEFAULT_TIMEOUT且没有数据被读取，则超时
                            // 2. 如果无活动时间超过INACTIVITY_TIMEOUT，则超时
                            if ((totalElapsed > DEFAULT_TIMEOUT && !isDataBeingRead.get()) || 
                                (inactivityTime > INACTIVITY_TIMEOUT && totalElapsed > INACTIVITY_TIMEOUT)) {
                                Log.w(TAG, "命令执行超时 - 总时间: ${totalElapsed/1000}秒, 无活动时间: ${inactivityTime/1000}秒")
                                waitingForResult = false
                                throw TimeoutException("Command execution timed out")
                            }
                            
                            // 短暂休眠避免过度消耗CPU
                            delay(100)
                        }
                        
                        // 这里实际上不会执行到，因为循环只有在抛出异常时才会结束
                        throw IllegalStateException("Unexpected state in command execution")
                    } catch (e: TimeoutException) {
                        Log.e(TAG, "命令执行超时: ${e.message}")
                        
                        // 移除回调
                        TermuxCommandResultService.removeCallback(executionId)
                        
                        // 注销广播接收器
                        try {
                            context.unregisterReceiver(broadcastReceiver)
                        } catch (e: Exception) {
                            // 忽略注销失败
                        }
                        
                        // 停止文件监控
                        commandIsRunning.set(false)
                        fileMonitorThread.interrupt()
                        try {
                            fileMonitorThread.join(1000)
                        } catch (e: Exception) {
                            // 忽略线程终止失败
                        }
                        
                        // 通知接收器超时
                        effectiveOutputReceiver?.onError("Command execution timed out", -1)
                        
                        // 清理临时文件
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                AdbCommandExecutor.executeAdbCommand("run-as com.termux sh -c 'rm -f \"$tempOutputFile\"'")
                            } catch (e: Exception) {
                                Log.e(TAG, "删除临时文件失败: ${e.message}")
                            }
                        }
                        
                return@withContext CommandResult(
                    success = false,
                            stdout = stdoutBuilder.toString(),
                            stderr = "Command execution timed out",
                    exitCode = -1
                )
                    } catch (e: CancellationException) {
                        Log.e(TAG, "命令执行被取消: ${e.message}")
                        
                        // 移除回调
                        TermuxCommandResultService.removeCallback(executionId)
                        
                        // 注销广播接收器
                        try {
                            context.unregisterReceiver(broadcastReceiver)
                        } catch (e: Exception) {
                            // 忽略注销失败
                        }
                        
                        // 停止文件监控
                        commandIsRunning.set(false)
                        fileMonitorThread.interrupt()
                        try {
                            fileMonitorThread.join(1000)
                        } catch (e: Exception) {
                            // 忽略线程终止失败
                        }
                        
                        // 通知接收器取消
                        effectiveOutputReceiver?.onError("Command execution was cancelled", -1)
                        
                        // 清理临时文件
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                AdbCommandExecutor.executeAdbCommand("run-as com.termux sh -c 'rm -f \"$tempOutputFile\"'")
                            } catch (e: Exception) {
                                Log.e(TAG, "删除临时文件失败: ${e.message}")
                            }
                        }
                        
                        return@withContext CommandResult(
                            success = false,
                            stdout = stdoutBuilder.toString(),
                            stderr = "Command execution was cancelled: ${e.message}",
                            exitCode = -1
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "命令执行过程中出现异常: ${e.message}")
                        
                        // 移除回调
                        TermuxCommandResultService.removeCallback(executionId)
                        
                        // 注销广播接收器
                        try {
                            context.unregisterReceiver(broadcastReceiver)
                        } catch (e: Exception) {
                            // 忽略注销失败
                        }
                        
                        // 停止文件监控
                        commandIsRunning.set(false)
                        fileMonitorThread.interrupt()
                        try {
                            fileMonitorThread.join(1000)
                        } catch (e: Exception) {
                            // 忽略线程终止失败
                        }
                        
                        val errorResult = CommandResult(
                            success = false,
                            stdout = stdoutBuilder.toString(),
                            stderr = "Error: ${e.message}",
                            exitCode = -1
                        )
                        outputReceiver?.onError("Error: ${e.message}", -1)
                        return@withContext errorResult
                    } finally {
                        // 停止文件监控
                        commandIsRunning.set(false)
                        fileMonitorThread.interrupt()
                        
                        try {
                            fileMonitorThread.join(1000)
                        } catch (e: Exception) {
                            // 忽略线程终止失败
                        }
                        
                        // 注销广播接收器
                        try {
                            context.unregisterReceiver(broadcastReceiver)
                        } catch (e: Exception) {
                            // 忽略注销失败
                        }
                    }
                } catch (e: Exception) {
                    // 外部异常处理已经在内部处理过了，直接转发异常
                    throw e
                }
            } catch (e: Exception) {
                Log.e(TAG, "执行命令失败: ${e.message}")
                val errorResult = CommandResult(
                    success = false,
                    stdout = "",
                    stderr = "Error: ${e.message}",
                    exitCode = -1
                )
                outputReceiver?.onError("Error: ${e.message}", -1)
                return@withContext errorResult
            }
        }
        
        /**
         * 将Intent中的Bundle转换为CommandResult
         */
        private fun extractCommandResult(intent: Intent?): CommandResult {
            if (intent == null || intent.extras == null) {
                Log.e(TAG, "结果Intent为空或缺少extras")
                return CommandResult(
                    success = false,
                    stdout = "",
                    stderr = "No intent or extras",
                    exitCode = -1
                )
            }
            
            // 尝试获取结果Bundle
            val resultBundle = intent.getBundleExtra("result")
            
            if (resultBundle == null) {
                Log.e(TAG, "结果Bundle为空")
                return CommandResult(
                    success = false,
                    stdout = "",
                    stderr = "No result bundle",
                    exitCode = -1
                )
            }
            
            // 从Bundle中提取关键值
            val stdout = resultBundle.getString("stdout", "")
            val stderr = resultBundle.getString("stderr", "")
            val exitCode = resultBundle.getInt("exitCode", -1)
            val errmsg = resultBundle.getString("errmsg", "")
            
            Log.d(TAG, "原始结果: stdout=\"$stdout\", stderr=\"$stderr\", exitCode=$exitCode, errmsg=\"$errmsg\"")
            
            // 检查输出是否为空
            val finalStdout = if (stdout.isEmpty() && exitCode == 0 && stderr.isEmpty() && errmsg.isEmpty()) {
                "[命令执行成功，无输出]" // 命令成功执行但没有输出，显示一个提示
            } else {
                stdout
            }
            
            // 修改成功状态判断逻辑：
            // 1. 如果exitCode为0，一定成功
            // 2. 如果exitCode为1但有正常输出且无错误消息，也认为是成功
            // 3. 如果errmsg不为空，则无论exitCode如何，认为是失败
            val success = when {
                exitCode == 0 -> true
                exitCode == 1 && stdout.isNotEmpty() && stderr.isEmpty() && errmsg.isEmpty() -> true
                errmsg.isNotEmpty() -> false
                else -> false
            }
            
            return CommandResult(
                success = success,
                stdout = finalStdout,
                stderr = if (errmsg.isNotEmpty()) "$stderr\n$errmsg" else stderr,
                exitCode = exitCode
            ).also {
                Log.d(TAG, "最终处理结果: $it")
            }
        }
    }
} 