package com.ai.assistance.operit.core.tools.system.termux

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ai.assistance.operit.services.TermuxCommandResultService
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor.CommandResult
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Termux命令执行实现类 包含执行Termux命令的核心逻辑实现 */
object TermuxCommandExecutionImpl {
    private const val TAG = "TermuxCmdExecImpl"
    private const val PACKAGE_NAME = "com.termux"
    private const val ACTIVITY_NAME = "com.termux.app.TermuxActivity"
    private const val EXTRA_COMMAND = "com.termux.app.execute_command_key"
    private const val EXTRA_RUNNER_SERVICE = "com.termux.run_command.RunCommandService"
    private const val EXTRA_SESSION_ACTION = "session_action"
    private var EXECUTION_ID = 1000

    /** 获取下一个执行ID 用于确保每个命令执行有唯一的ID */
    private fun getNextExecutionId(): Int {
        return EXECUTION_ID++
    }

    /** 检查是否有Termux运行命令权限 */
    private fun hasTermuxRunCommandPermission(context: Context): Boolean {
        return context.checkCallingOrSelfPermission("com.termux.permission.RUN_COMMAND") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** 创建带有命令前缀的日志消息 */
    private fun createLogMessage(command: String, message: String): String {
        val shortCommand = if (command.length > 20) command.substring(0, 20) + "..." else command
        return "[cmd: $shortCommand] $message"
    }

    /**
     * 执行Termux命令 (使用流式输出) 该方法会默认使用流式输出模式，替代旧的executeCommand方法
     * @param context 上下文
     * @param command 要执行的命令
     * @param background 是否在后台执行命令
     * @param outputReceiver 命令输出接收器，用于接收实时输出
     * @param resultCallback 命令执行结果回调，在不提供outputReceiver时使用
     * @param options 命令选项，包括超时设置和超时处理模式
     * @return 执行结果 (仅表示命令是否成功发送，不代表实际执行结果)
     */
    suspend fun executeCommandStreaming(
            context: Context,
            command: String,
            background: Boolean = true,
            outputReceiver: TermuxCommandOutputReceiver? = null,
            resultCallback: ((CommandResult) -> Unit)? = null,
            options: TermuxCommandOptions = TermuxCommandOptions()
    ): CommandResult =
            withContext(Dispatchers.IO) {
                try {
                    // 检查应用是否在前台运行
                    if (!TermuxUtils.isTermuxRunning(context)) {
                        Log.w(TAG, createLogMessage(command, "应用未启动，无法启动Termux服务"))
                        val errorResult =
                                CommandResult(
                                        success = false,
                                        stdout = "",
                                        stderr = "应用未启动，无法启动Termux服务。请确保应用在前台运行或使用ADB方式直接执行命令",
                                        exitCode = -1
                                )
                        outputReceiver?.onError("应用未启动，无法启动Termux服务", -1)
                        return@withContext errorResult
                    }

                    Log.d(TAG, createLogMessage(command, "执行命令: $command"))

                    // 首先检查Termux是否已安装
                    if (!TermuxInstaller.isTermuxInstalled(context)) {
                        Log.e(TAG, createLogMessage(command, "Termux未安装"))
                        val errorResult =
                                CommandResult(
                                        success = false,
                                        stdout = "",
                                        stderr = "Termux is not installed",
                                        exitCode = -1
                                )
                        outputReceiver?.onError("Termux is not installed", -1)
                        return@withContext errorResult
                    }

                    // 检查是否已授权，如果需要则尝试授权
                    if (!TermuxAuthorizer.isTermuxAuthorized(context)) {
                            Log.e(TAG, createLogMessage(command, "授权Termux失败"))
                            val errorResult =
                                    CommandResult(
                                            success = false,
                                            stdout = "",
                                            stderr = "Failed to authorize Termux",
                                            exitCode = -1
                                    )
                            outputReceiver?.onError("Failed to authorize Termux", -1)
                            return@withContext errorResult
                    }

                    // 内部执行逻辑块
                    try {
                        // 生成一个唯一的执行ID
                        val executionId = getNextExecutionId()
                        Log.d(TAG, createLogMessage(command, "分配执行ID: $executionId"))

                        // 处理特殊用户权限
                        val finalCommand =
                                if (command.contains("su ") && !command.startsWith("su ")) {
                                    "su -c \"$command\""
                                } else {
                                    command
                                }

                        if (finalCommand != command) {
                            Log.d(TAG, createLogMessage(command, "命令调整为: $finalCommand"))
                        }

                        // 创建一个临时文件用于输出，使用ADB创建以确保权限正确
                        val tempOutputFile =
                                "/data/data/com.termux/files/home/.termux_output_${executionId}.log"
                        Log.d(TAG, createLogMessage(command, "创建临时输出文件: $tempOutputFile"))

                        // 创建一个不断回显的命令，使用stdbuf禁用缓冲
                        val wrappedCommand =
                                """
                    {
                        # 创建输入FIFO管道
                        INPUT_FIFO="/data/data/com.termux/files/home/.termux_input_${executionId}.fifo"
                        rm -f "${'$'}INPUT_FIFO" 2>/dev/null
                        mkfifo "${'$'}INPUT_FIFO" 2>/dev/null
                        chmod 666 "${'$'}INPUT_FIFO" 2>/dev/null
                        
                        # 保持FIFO打开状态(防止读取方在没有写入方时退出)
                        (
                            while true; do
                                sleep 3600 > "${'$'}INPUT_FIFO" 2>/dev/null &
                                wait ${'$'}! 2>/dev/null
                            done
                        ) &
                        FIFO_KEEPER_PID=${'$'}!
                        trap "kill ${'$'}FIFO_KEEPER_PID 2>/dev/null; rm -f \"${'$'}INPUT_FIFO\" 2>/dev/null" EXIT

                        # 检查输出文件是否存在
                        if [ ! -f "$tempOutputFile" ]; then
                            touch "$tempOutputFile"
                            chmod 666 "$tempOutputFile"
                        fi
                        
                        # 清空文件内容，重新开始
                        echo "" > "$tempOutputFile"
                        
                        # 执行命令并同时将输出重定向到文件
                        {
                            {
                                # 添加基本输出信息
                                echo "Excute Time: $(date)"
                                
                                # 从FIFO读取输入并传递给命令
                                $finalCommand < "${'$'}INPUT_FIFO"
                                CMD_EXIT_CODE=${'$'}?
                                
                                echo "COMMAND_COMPLETE:${'$'}CMD_EXIT_CODE" >> "$tempOutputFile"
                            } 2>&1 | tee -a "$tempOutputFile"
                        }
                        
                        # 清理FIFO
                        kill ${'$'}FIFO_KEEPER_PID 2>/dev/null
                        rm -f "${'$'}INPUT_FIFO" 2>/dev/null
                    }
                """.trimIndent()

                        // 构建Intent
                        val intent =
                                Intent().setClassName(
                                                "com.termux",
                                                "com.termux.app.RunCommandService"
                                        )
                                        .setAction("com.termux.RUN_COMMAND")

                        // 设置命令路径和参数
                        intent.putExtra(
                                "com.termux.RUN_COMMAND_PATH",
                                "/data/data/com.termux/files/usr/bin/bash"
                        )
                        intent.putExtra(
                                "com.termux.RUN_COMMAND_ARGUMENTS",
                                arrayOf("-c", wrappedCommand)
                        )
                        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", options.workingDirectory)
                        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
                        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")

                        // 创建一个CompletableDeferred以等待命令完成
                        val commandCompleted = CompletableDeferred<CommandResult>()

                        // 使用一个额外的标志表示命令是否已完成
                        val commandIsRunning = AtomicBoolean(true)

                        // 添加文件监控活动时间跟踪
                        val lastActivityTime = AtomicLong(System.currentTimeMillis())
                        val isDataBeingRead = AtomicBoolean(false)

                        // 创建命令结果变量，用于文件监控线程更新
                        val monitoredResult =
                                object {
                                    var exitCode: Int = -1
                                    var success: Boolean = false
                                }

                        // 初始化输出收集器
                        val stdoutBuilder = StringBuilder()
                        val stderrBuilder = StringBuilder()

                        // 如果提供了resultCallback，创建一个能够实时转发输出的特殊接收器
                        val effectiveOutputReceiver =
                                if (resultCallback != null && outputReceiver == null) {
                                    object : TermuxCommandOutputReceiver {
                                        override fun onStdout(output: String, isComplete: Boolean) {
                                            stdoutBuilder.append(output)
                                            // 实时回调每次收到的输出
                                            if (!isComplete) {
                                                // 每次收到输出就立即回调一个临时结果
                                                Handler(Looper.getMainLooper()).post {
                                                    resultCallback(
                                                            CommandResult(
                                                                    success = true,
                                                                    stdout = output, // 只包含这次新收到的输出
                                                                    stderr = "",
                                                                    exitCode = 0
                                                            )
                                                    )
                                                }
                                            }
                                        }

                                        override fun onStderr(error: String, isComplete: Boolean) {
                                            stderrBuilder.append(error)
                                            // 实时回调每次收到的错误
                                            if (!isComplete) {
                                                // 每次收到错误就立即回调一个临时结果
                                                Handler(Looper.getMainLooper()).post {
                                                    resultCallback(
                                                            CommandResult(
                                                                    success = true,
                                                                    stdout = "",
                                                                    stderr = error, // 只包含这次新收到的错误
                                                                    exitCode = 0
                                                            )
                                                    )
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
                                                resultCallback(
                                                        CommandResult(
                                                                success = false,
                                                                stdout = stdoutBuilder.toString(),
                                                                stderr = error,
                                                                exitCode = exitCode
                                                        )
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    outputReceiver
                                }

                        // 创建结果处理回调
                        TermuxCommandResultService.registerCallback(executionId) { result ->
                            Log.d(TAG, createLogMessage(command, "收到命令执行结果回调: $result"))

                            // 命令已完成，使用AdbCommandExecutor清理临时文件
                            GlobalScope.launch(Dispatchers.IO) {
                                // 等待一会确保所有输出都被处理
                                delay(500)

                                // 使用AdbCommandExecutor删除临时文件，避免再次调用Termux
                                try {
                                    AndroidShellExecutor.executeShellCommand(
                                            "run-as com.termux sh -c 'rm -f \"$tempOutputFile\"'"
                                    )
                                    Log.d(
                                            TAG,
                                            createLogMessage(
                                                    command,
                                                    "临时文件已通过ADB删除: $tempOutputFile"
                                            )
                                    )

                                    // 尝试清理FIFO管道
                                    val fifoFile =
                                            "/data/data/com.termux/files/home/.termux_input_$executionId.fifo"
                                    AndroidShellExecutor.executeShellCommand(
                                            "run-as com.termux sh -c 'rm -f \"$fifoFile\" 2>/dev/null'"
                                    )

                                    // 清理资源
                                    TermuxCommandInteraction.unregisterCommandFile(executionId)
                                } catch (e: Exception) {
                                    Log.e(TAG, createLogMessage(command, "删除临时文件失败: ${e.message}"))
                                }
                            }

                            // 检查当前输出是否已经包含COMMAND_COMPLETE标记
                            // 如果已经通过文件监听获取了完整输出，则不再重复添加内容
                            val currentOutput = stdoutBuilder.toString()
                            val alreadyCompleted = currentOutput.contains("COMMAND_COMPLETE:")

                            if (alreadyCompleted) {
                                Log.d(
                                        TAG,
                                        createLogMessage(command, "已通过文件监听获取完整输出，不再添加Intent回调内容")
                                )
                                // 仅完成延迟对象，不更新输出
                                commandCompleted.complete(
                                        CommandResult(
                                                success = result.success,
                                                stdout = currentOutput,
                                                stderr = stderrBuilder.toString(),
                                                exitCode = result.exitCode
                                        )
                                )
                                return@registerCallback
                            }

                            // 触发完成回调
                            val finalResult =
                                    CommandResult(
                                            success = result.success,
                                            stdout = result.stdout, // 确保使用原始结果中的stdout
                                            stderr = result.stderr, // 确保使用原始结果中的stderr
                                            exitCode = result.exitCode
                                    )

                            // 确保stdout和stderr非空
                            if (result.stdout.isNotEmpty() && stdoutBuilder.toString().isEmpty()) {
                                // termux has some bug
                                // stdoutBuilder.append(result.stdout)
                            }

                            if (result.stderr.isNotEmpty() && stderrBuilder.toString().isEmpty()) {
                                stderrBuilder.append(result.stderr)
                            }

                            Log.d(
                                    TAG,
                                    createLogMessage(
                                            command,
                                            "传递给接收器的最终结果: stdout长度=${finalResult.stdout.length}, stderr长度=${finalResult.stderr.length}"
                                    )
                            )
                            effectiveOutputReceiver?.onComplete(finalResult)

                            // 完成延迟对象
                            commandCompleted.complete(finalResult)
                        }

                        // 创建PendingIntent
                        val resultIntent = Intent(context, TermuxCommandResultService::class.java)
                        resultIntent.putExtra(
                                TermuxCommandResultService.EXTRA_EXECUTION_ID,
                                executionId
                        )

                        val finalFlags =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                                } else {
                                    PendingIntent.FLAG_UPDATE_CURRENT
                                }

                        val pendingIntent =
                                PendingIntent.getService(
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
                        val broadcastReceiver =
                                object : BroadcastReceiver() {
                                    override fun onReceive(context: Context?, intent: Intent?) {
                                        if (intent == null || intent.extras == null) return

                                        val resultBundle = intent.getBundleExtra("result")
                                        if (resultBundle != null) {
                                            val stdout = resultBundle.getString("stdout", "")
                                            val stderr = resultBundle.getString("stderr", "")

                                            // 使用Handler.post确保回调在主线程上执行，但不引入延迟
                                            val mainHandler = Handler(Looper.getMainLooper())

                                            if (stdout.isNotEmpty()) {
                                                Log.d(
                                                        TAG,
                                                        createLogMessage(
                                                                command,
                                                                "流式输出 stdout: $stdout"
                                                        )
                                                )
                                                stdoutBuilder.append(stdout)

                                                // 立即在主线程上回调，确保实时性
                                                mainHandler.post {
                                                    effectiveOutputReceiver?.onStdout(stdout, false)
                                                }
                                            }

                                            if (stderr.isNotEmpty()) {
                                                Log.d(
                                                        TAG,
                                                        createLogMessage(
                                                                command,
                                                                "流式输出 stderr: $stderr"
                                                        )
                                                )
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
                            context.registerReceiver(
                                    broadcastReceiver,
                                    outputResultFilter,
                                    Context.RECEIVER_NOT_EXPORTED
                            )
                        } else {
                            context.registerReceiver(broadcastReceiver, outputResultFilter)
                        }

                        // 创建输出接收的PendingIntent
                        val outputIntent = Intent(outputReceiveAction)
                        outputIntent.setPackage(context.packageName)

                        val outputFlags =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                                } else {
                                    PendingIntent.FLAG_UPDATE_CURRENT
                                }

                        val outputPendingIntent =
                                PendingIntent.getBroadcast(
                                        context.applicationContext,
                                        executionId + 1, // 使用不同的请求码
                                        outputIntent,
                                        outputFlags
                                )

                        // 添加输出接收PendingIntent，设置更短的更新间隔以提高响应性
                        intent.putExtra(
                                "com.termux.RUN_COMMAND_OUTPUT_PENDING_INTENT",
                                outputPendingIntent
                        )
                        intent.putExtra(
                                "com.termux.RUN_COMMAND_OUTPUT_INTERVAL",
                                50L
                        ) // 输出更新间隔设置为更短的50毫秒，提高实时性

                        // 注册命令文件，以便于交互
                        TermuxCommandInteraction.registerCommandFile(executionId, tempOutputFile)

                        // 发送命令
                        try {
                            context.startService(intent)
                            Log.d(
                                    TAG,
                                    createLogMessage(
                                            command,
                                            "已发送命令到Termux: $command, 执行ID: $executionId"
                                    )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, createLogMessage(command, "启动Termux服务失败: ${e.message}"), e)

                            // 检查是否是后台服务启动限制异常
                            if (e.javaClass.name.contains(
                                            "BackgroundServiceStartNotAllowedException"
                                    )
                            ) {
                                Log.e(TAG, createLogMessage(command, "检测到后台服务启动限制，无法在后台启动Termux服务"))

                                // 移除之前注册的回调，避免回调落空
                                TermuxCommandResultService.removeCallback(executionId)

                                // 注销广播接收器
                                try {
                                    context.unregisterReceiver(broadcastReceiver)
                                } catch (e2: Exception) {
                                    // 忽略注销失败
                                    Log.e(
                                            TAG,
                                            createLogMessage(command, "注销广播接收器失败: ${e2.message}")
                                    )
                                }

                                // 通知接收器出错
                                effectiveOutputReceiver?.onError("无法在后台启动Termux服务，请确保应用在前台运行", -1)

                                // 直接完成延迟对象，避免永久等待
                                commandCompleted.complete(
                                        CommandResult(
                                                success = false,
                                                stdout = "",
                                                stderr = "无法在后台启动Termux服务: ${e.message}，请确保应用在前台运行",
                                                exitCode = -1
                                        )
                                )

                                // 返回错误结果
                                return@withContext CommandResult(
                                        success = false,
                                        stdout = "",
                                        stderr = "无法在后台启动Termux服务: ${e.message}，请确保应用在前台运行",
                                        exitCode = -1
                                )
                            }

                            // 检查是否缺少Termux运行命令权限
                            if (!hasTermuxRunCommandPermission(context)) {
                                Log.e(TAG, createLogMessage(command, "缺少Termux运行命令权限"))

                                // 移除之前注册的回调，避免回调落空
                                TermuxCommandResultService.removeCallback(executionId)

                                // 注销广播接收器
                                try {
                                    context.unregisterReceiver(broadcastReceiver)
                                } catch (e2: Exception) {
                                    // 忽略注销失败
                                }

                                // 通知接收器出错
                                effectiveOutputReceiver?.onError(
                                        "缺少Termux运行命令权限，请确保已授予RUN_COMMAND权限",
                                        -1
                                )

                                // 完成延迟对象
                                commandCompleted.complete(
                                        CommandResult(
                                                success = false,
                                                stdout = "",
                                                stderr = "缺少Termux运行命令权限，请确保已授予RUN_COMMAND权限",
                                                exitCode = -1
                                        )
                                )

                                // 清理资源
                                TermuxCommandInteraction.unregisterCommandFile(executionId)
                                return@withContext CommandResult(
                                        success = false,
                                        stdout = "",
                                        stderr = "缺少Termux运行命令权限，请确保已授予RUN_COMMAND权限",
                                        exitCode = -1
                                )
                            } else {
                                // 有权限但Termux未启动
                                Log.e(TAG, createLogMessage(command, "Termux服务未启动或未响应"))

                                // 移除之前注册的回调，避免回调落空
                                TermuxCommandResultService.removeCallback(executionId)

                                // 注销广播接收器
                                try {
                                    context.unregisterReceiver(broadcastReceiver)
                                } catch (e2: Exception) {
                                    // 忽略注销失败
                                }

                                // 通知接收器出错
                                effectiveOutputReceiver?.onError("Termux服务未启动或未响应，请先启动Termux应用", -1)

                                // 完成延迟对象
                                commandCompleted.complete(
                                        CommandResult(
                                                success = false,
                                                stdout = "",
                                                stderr = "Termux服务未启动或未响应，请先启动Termux应用",
                                                exitCode = -1
                                        )
                                )

                                // 清理资源
                                TermuxCommandInteraction.unregisterCommandFile(executionId)
                                return@withContext CommandResult(
                                        success = false,
                                        stdout = "",
                                        stderr = "Termux服务未启动或未响应，请先启动Termux应用",
                                        exitCode = -1
                                )
                            }
                        }

                        // 启动文件监控线程
                        TermuxFileMonitor.monitorCommandOutput(
                                command = finalCommand, // 传递命令到文件监控
                                tempOutputFile = tempOutputFile,
                                commandIsRunning = commandIsRunning,
                                monitoredResult = monitoredResult,
                                executionId = executionId,
                                lastActivityTime = lastActivityTime,
                                isDataBeingRead = isDataBeingRead,
                                effectiveOutputReceiver = effectiveOutputReceiver,
                                stdoutBuilder = stdoutBuilder
                        )

                        // 等待命令完成或超时
                        try {
                            // 使用自定义超时逻辑
                            var waitingForResult = true
                            val startTime = System.currentTimeMillis()

                            while (waitingForResult) {
                                // 尝试在短时间内等待命令完成
                                try {
                                    val result =
                                            withTimeoutOrNull(100L) { commandCompleted.await() }

                                    // 如果命令完成，直接返回结果
                                    if (result != null) {
                                        return@withContext result
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, createLogMessage(command, "等待命令完成时出错: ${e.message}"))
                                }

                                // 检查是否有活动或是否已超过最大超时时间
                                val currentTime = System.currentTimeMillis()
                                val totalElapsed = currentTime - startTime
                                val inactivityTime = currentTime - lastActivityTime.get()

                                // 日志记录当前状态
                                if (totalElapsed % 5000 < 100) {
                                    // 每5秒记录一次状态
                                    Log.d(
                                            TAG,
                                            createLogMessage(
                                                    command,
                                                    "命令仍在执行 - 总时间: ${totalElapsed/1000}秒, 无活动时间: ${inactivityTime/1000}秒, 数据读取中: ${isDataBeingRead.get()} (${if (isDataBeingRead.get()) "不会触发无活动超时" else "可能触发无活动超时"})"
                                            )
                                    )
                                }

                                // 判断是否应该超时:
                                // 1. 如果总时间超过超时时间且没有数据被读取，则超时
                                // 2. 如果无活动时间超过无活动超时时间且没有数据正在被读取，则超时
                                if ((totalElapsed > options.timeout && !isDataBeingRead.get()) ||
                                                (inactivityTime >
                                                        TermuxCommandOptions.INACTIVITY_TIMEOUT &&
                                                        !isDataBeingRead.get() &&
                                                        totalElapsed >
                                                                TermuxCommandOptions
                                                                        .INACTIVITY_TIMEOUT)
                                ) {
                                    Log.w(
                                            TAG,
                                            createLogMessage(
                                                    command,
                                                    "命令执行超时 - 总时间: ${totalElapsed/1000}秒, 无活动时间: ${inactivityTime/1000}秒, 数据读取中: ${isDataBeingRead.get()}"
                                            )
                                    )
                                    waitingForResult = false
                                    throw TimeoutException("Command execution timed out")
                                }

                                // 短暂休眠避免过度消耗CPU
                                delay(100)
                            }

                            // 这里实际上不会执行到，因为循环只有在抛出异常时才会结束
                            throw IllegalStateException("Unexpected state in command execution")
                        } catch (e: TimeoutException) {
                            return@withContext TermuxFileMonitor.handleTimeout(
                                    command = finalCommand, // 传递命令到异常处理
                                    e = e,
                                    executionId = executionId,
                                    context = context,
                                    broadcastReceiver = broadcastReceiver,
                                    commandIsRunning = commandIsRunning,
                                    tempOutputFile = tempOutputFile,
                                    effectiveOutputReceiver = effectiveOutputReceiver,
                                    stdoutBuilder = stdoutBuilder,
                                    stderrBuilder = stderrBuilder,
                                    timeoutAsError = options.timeoutAsError
                            )
                        } catch (e: CancellationException) {
                            return@withContext TermuxFileMonitor.handleCancellation(
                                    command = finalCommand, // 传递命令到异常处理
                                    e = e,
                                    executionId = executionId,
                                    context = context,
                                    broadcastReceiver = broadcastReceiver,
                                    commandIsRunning = commandIsRunning,
                                    tempOutputFile = tempOutputFile,
                                    effectiveOutputReceiver = effectiveOutputReceiver,
                                    stdoutBuilder = stdoutBuilder
                            )
                        } catch (e: Exception) {
                            return@withContext TermuxFileMonitor.handleException(
                                    command = finalCommand, // 传递命令到异常处理
                                    e = e,
                                    executionId = executionId,
                                    context = context,
                                    broadcastReceiver = broadcastReceiver,
                                    commandIsRunning = commandIsRunning,
                                    tempOutputFile = tempOutputFile,
                                    effectiveOutputReceiver = effectiveOutputReceiver,
                                    stdoutBuilder = stdoutBuilder
                            )
                        } finally {
                            // 停止文件监控
                            commandIsRunning.set(false)

                            // 注销广播接收器
                            try {
                                context.unregisterReceiver(broadcastReceiver)
                            } catch (e: Exception) {
                                // 忽略注销失败
                            }

                            // 取消命令文件注册
                            TermuxCommandInteraction.unregisterCommandFile(executionId)
                        }
                    } catch (e: Exception) {
                        // 外部异常处理已经在内部处理过了，直接转发异常
                        throw e
                    }
                } catch (e: Exception) {
                    Log.e(TAG, createLogMessage(command, "执行命令失败: ${e.message}"))
                    val errorResult =
                            CommandResult(
                                    success = false,
                                    stdout = "",
                                    stderr = "Error: ${e.message}",
                                    exitCode = -1
                            )
                    outputReceiver?.onError("Error: ${e.message}", -1)
                    return@withContext errorResult
                }
            }

    /** 将Intent中的Bundle转换为CommandResult */
    fun extractCommandResult(intent: Intent?): CommandResult {
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

        Log.d(
                TAG,
                "原始结果: stdout=\"$stdout\", stderr=\"$stderr\", exitCode=$exitCode, errmsg=\"$errmsg\""
        )

        // 检查输出是否为空
        val finalStdout =
                if (stdout.isEmpty() && exitCode == 0 && stderr.isEmpty() && errmsg.isEmpty()) {
                    "[命令执行成功，无输出]" // 命令成功执行但没有输出，显示一个提示
                } else {
                    stdout
                }

        // 修改成功状态判断逻辑：
        // 1. 如果exitCode为0，一定成功
        // 2. 如果exitCode为1但有正常输出且无错误消息，也认为是成功
        // 3. 如果errmsg不为空，则无论exitCode如何，认为是失败
        val success =
                when {
                    exitCode == 0 -> true
                    exitCode == 1 && stdout.isNotEmpty() && stderr.isEmpty() && errmsg.isEmpty() ->
                            true
                    errmsg.isNotEmpty() -> false
                    else -> false
                }

        return CommandResult(
                        success = success,
                        stdout = finalStdout,
                        stderr = if (errmsg.isNotEmpty()) "$stderr\n$errmsg" else stderr,
                        exitCode = exitCode
                )
                .also { Log.d(TAG, "最终处理结果: $it") }
    }
}
