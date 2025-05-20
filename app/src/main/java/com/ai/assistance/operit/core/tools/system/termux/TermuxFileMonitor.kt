package com.ai.assistance.operit.core.tools.system.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ai.assistance.operit.services.TermuxCommandResultService
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor.CommandResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Termux文件监控工具类 用于监控命令输出文件和处理命令执行过程中的文件操作 */
object TermuxFileMonitor {
    private const val TAG = "TermuxFileMonitor"

    /** 创建带有命令前缀的日志消息 */
    private fun createLogMessage(command: String, message: String): String {
        val shortCommand = if (command.length > 20) command.substring(0, 20) + "..." else command
        return "[cmd: $shortCommand] $message"
    }

    /** 检查临时文件是否创建成功 */
    private suspend fun checkFileCreation(
            command: String,
            tempOutputFile: String,
            commandCompleted: CompletableDeferred<CommandResult>,
            commandIsRunning: AtomicBoolean,
            outputReceiver: TermuxCommandOutputReceiver?,
            stdoutContent: String,
            stderrContent: String
    ) {
        // 检查文件是否在一段时间内创建
        val fileCheckTimeout = 10000L // 10秒
        val startTime = System.currentTimeMillis()
        var fileCreated = false

        while (System.currentTimeMillis() - startTime < fileCheckTimeout) {
            val checkFileCmd =
                    "run-as com.termux sh -c 'if [ -f \"$tempOutputFile\" ]; then echo \"EXISTS\"; else echo \"NOT_EXISTS\"; fi'"
            val checkResult = AndroidShellExecutor.executeAdbCommand(checkFileCmd)

            if (checkResult.stdout.contains("EXISTS")) {
                fileCreated = true
                // Log.d(TAG, createLogMessage(command, "检测到文件已创建: $tempOutputFile"))
                break
            }

            delay(500) // 等待500毫秒再检查
        }

        // 如果文件创建失败，通知错误并完成操作
        if (!fileCreated) {
            // Log.e(TAG, createLogMessage(command, "文件创建超时: $tempOutputFile"))
            outputReceiver?.onError("未能创建输出文件，请检查Termux权限", -1)

            commandCompleted.complete(
                    CommandResult(
                            success = false,
                            stdout = stdoutContent,
                            stderr = "未能创建输出文件，请检查Termux权限",
                            exitCode = -1
                    )
            )

            // 停止文件监控
            commandIsRunning.set(false)
        }
    }

    /** 监控命令输出文件 */
    fun monitorCommandOutput(
            command: String,
            tempOutputFile: String,
            commandIsRunning: AtomicBoolean,
            monitoredResult: Any,
            executionId: Int,
            lastActivityTime: AtomicLong,
            isDataBeingRead: AtomicBoolean,
            effectiveOutputReceiver: TermuxCommandOutputReceiver?,
            stdoutBuilder: StringBuilder
    ) {
        val fileMonitorThread = Thread {
            try {
                var lastSize = 0L
                var hadOutputInLastCycle = false

                // 添加日志，记录文件监控开始
                // Log.d(TAG, createLogMessage(command, "开始文件监控: $tempOutputFile"))

                // 循环检查文件变化，直到命令完成
                while (commandIsRunning.get()) {
                    // 在每个监控周期开始时，设置数据正在读取状态为前一周期是否有输出
                    // 这样每当有新数据读取，isDataBeingRead就会保持为true至少一个完整周期
                    isDataBeingRead.set(hadOutputInLastCycle)

                    // 重置本周期的输出状态标识
                    hadOutputInLastCycle = false

                    // 使用AdbCommandExecutor获取文件大小
                    val sizeCommand =
                            "run-as com.termux sh -c 'if [ -f \"$tempOutputFile\" ]; then echo \"EXISTS=\"; stat -c %s \"$tempOutputFile\" 2>/dev/null || stat -f %z \"$tempOutputFile\"; else echo \"NOT_EXISTS\"; fi'"
                    // Log.d(TAG, createLogMessage(command, "检查文件状态: $sizeCommand"))

                    val sizeResult =
                            runCatching {
                                        val latch = CountDownLatch(1)
                                        var result: CommandResult? = null

                                        GlobalScope.launch(Dispatchers.IO) {
                                            result =
                                                    AndroidShellExecutor.executeAdbCommand(
                                                            sizeCommand
                                                    )
                                            latch.countDown()
                                        }

                                        latch.await(5, TimeUnit.SECONDS)
                                        result
                                    }
                                    .getOrNull()

                    // 解析文件大小结果
                    val sizeOutput = sizeResult?.stdout?.trim() ?: ""
                    // Log.d(TAG, createLogMessage(command, "文件大小检查结果: '$sizeOutput'"))

                    // 如果文件不存在，继续循环
                    if (sizeOutput.isEmpty() || sizeOutput.contains("NOT_EXISTS")) {
                        // Log.d(TAG, createLogMessage(command, "文件不存在，等待创建..."))
                        Thread.sleep(50) // 短暂等待后重试
                        continue
                    }

                    // 提取数字部分作为文件大小
                    val sizePart =
                            if (sizeOutput.startsWith("EXISTS=")) {
                                sizeOutput.substringAfter("EXISTS=").trim()
                            } else {
                                sizeOutput.trim()
                            }

                    val newSize =
                            try {
                                sizePart.toLong()
                            } catch (e: NumberFormatException) {
                                // Log.e(TAG, createLogMessage(command, "无法解析文件大小: $sizePart"), e)
                                Thread.sleep(50) // 短暂等待后重试
                                continue
                            }

                    // 如果文件大小没有变化，等待后继续
                    if (newSize <= lastSize) {
                        Thread.sleep(50) // 短暂等待后重试
                        continue
                    }

                    // 使用AdbCommandExecutor读取新增的内容
                    val readCommand =
                            if (newSize - lastSize > 1024) {
                                // 对于大块数据，使用更高效的读取方式
                                "run-as com.termux sh -c 'dd if=\"$tempOutputFile\" bs=1024 skip=${lastSize / 1024} count=${(newSize - lastSize + 1023) / 1024} 2>/dev/null | dd bs=1 skip=${lastSize % 1024} count=${newSize - lastSize} 2>/dev/null'"
                            } else {
                                // 对于小块数据，直接按字节读取
                                "run-as com.termux sh -c 'dd if=\"$tempOutputFile\" bs=1 skip=$lastSize count=${newSize - lastSize} 2>/dev/null'"
                            }
                    // Log.d(TAG, createLogMessage(command, "读取命令: $readCommand, 从位置 $lastSize 读取
                    // ${newSize - lastSize} 字节"))

                    val readResult =
                            runCatching {
                                        val latch = CountDownLatch(1)
                                        var result: CommandResult? = null

                                        GlobalScope.launch(Dispatchers.IO) {
                                            result =
                                                    AndroidShellExecutor.executeAdbCommand(
                                                            readCommand
                                                    )
                                            latch.countDown()
                                        }

                                        latch.await(5, TimeUnit.SECONDS)
                                        result
                                    }
                                    .getOrNull()

                    // 解析读取结果
                    var output = readResult?.stdout ?: ""

                    // 如果读取失败，尝试备用读取方法
                    if (output.isEmpty() && readResult?.stderr?.isNotEmpty() == true) {
                        // Log.w(TAG, createLogMessage(command, "使用dd读取失败:
                        // ${readResult.stderr}，尝试备用方法"))

                        // 备用方法：使用cat加head/tail
                        val fallbackCommand =
                                "run-as com.termux sh -c 'cat \"$tempOutputFile\" | head -c ${lastSize + (newSize - lastSize)} | tail -c ${newSize - lastSize}'"
                        val fallbackResult =
                                runCatching {
                                            val latch = CountDownLatch(1)
                                            var result: CommandResult? = null

                                            GlobalScope.launch(Dispatchers.IO) {
                                                result =
                                                        AndroidShellExecutor.executeAdbCommand(
                                                                fallbackCommand
                                                        )
                                                latch.countDown()
                                            }

                                            latch.await(5, TimeUnit.SECONDS)
                                            result
                                        }
                                        .getOrNull()

                        output = fallbackResult?.stdout ?: ""
                        if (output.isNotEmpty()) {
                            // Log.d(TAG, createLogMessage(command, "备用读取方法成功"))
                        }
                    }

                    // Log.d(TAG, createLogMessage(command, "读取新增内容 (${output.length} 字节): ${if
                    // (output.length > 50) output.substring(0, 50) + "..." else output}"))

                    // 检查是否包含命令完成标记
                    if (output.contains("COMMAND_COMPLETE:")) {
                        // Log.d(TAG, createLogMessage(command, "检测到命令完成标记"))
                        val exitCodePattern = "COMMAND_COMPLETE:(\\d+)".toRegex()
                        val matchResult = exitCodePattern.find(output)
                        val exitCode = matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0

                        // 设置命令结果
                        if (monitoredResult is Any) {
                            try {
                                val exitCodeField =
                                        monitoredResult::class.java.getDeclaredField("exitCode")
                                exitCodeField.isAccessible = true
                                exitCodeField.set(monitoredResult, exitCode)

                                val successField =
                                        monitoredResult::class.java.getDeclaredField("success")
                                successField.isAccessible = true
                                successField.set(monitoredResult, exitCode == 0)
                            } catch (e: Exception) {
                                // Log.e(TAG, createLogMessage(command, "设置监控结果字段失败: ${e.message}"))
                            }
                        }

                        // Log.d(TAG, createLogMessage(command, "命令执行完成，退出码: $exitCode, 成功:
                        // ${exitCode == 0}"))
                        commandIsRunning.set(false)
                    }

                    // 检查是否需要用户输入
                    if (output.contains("Do you want to continue") ||
                                    output.contains("[Y/n]") ||
                                    output.contains("[y/N]") ||
                                    output.contains("(yes/no)") ||
                                    output.contains("请按回车键继续") ||
                                    output.contains("Press Enter to continue")
                    ) {

                        Log.d(TAG, createLogMessage(command, "检测到命令等待用户输入"))

                        // 创建一个通知，让用户知道命令需要输入
                        Handler(Looper.getMainLooper()).post {
                            // 发送带有执行ID和提示信息的特殊消息
                            val promptMessage =
                                    if (output.contains("[Y/n]")) {
                                        "确认继续？默认为是 [Y/n]"
                                    } else if (output.contains("[y/N]")) {
                                        "确认继续？默认为否 [y/N]"
                                    } else if (output.contains("(yes/no)")) {
                                        "确认继续？(yes/no)"
                                    } else if (output.contains("请按回车键继续") ||
                                                    output.contains("Press Enter to continue")
                                    ) {
                                        "按回车键继续"
                                    } else {
                                        "请输入响应"
                                    }

                            val specialMarker =
                                    "INTERACTIVE_PROMPT:ID:$executionId:PROMPT:$promptMessage"
                            effectiveOutputReceiver?.onStderr(specialMarker, false)
                        }
                    }

                    // 处理获取到的内容
                    if (output.isNotEmpty()) {
                        // 更新最后活动时间和读取状态
                        lastActivityTime.set(System.currentTimeMillis())
                        // 记录本周期有输出
                        hadOutputInLastCycle = true

                        Log.d(
                                TAG,
                                createLogMessage(
                                        command,
                                        "检测到新输出: ${output.length}字节, 内容预览: ${output.take(100).replace("\n", "\\n")}${if (output.length > 100) "..." else ""}"
                                )
                        )

                        Handler(Looper.getMainLooper()).post {
                            effectiveOutputReceiver?.onStdout(output, false)
                        }
                        stdoutBuilder.append(output)
                    }

                    lastSize = newSize
                }
            } catch (e: Exception) {
                Log.e(TAG, createLogMessage(command, "文件监控异常: ${e.message}"), e)
                e.printStackTrace()
            }
        }

        // 启动文件监控
        fileMonitorThread.start()
    }

    /** 处理命令执行超时 */
    suspend fun handleTimeout(
            command: String,
            e: TimeoutException,
            executionId: Int,
            context: Context,
            broadcastReceiver: BroadcastReceiver,
            commandIsRunning: AtomicBoolean,
            tempOutputFile: String,
            effectiveOutputReceiver: TermuxCommandOutputReceiver?,
            stdoutBuilder: StringBuilder,
            stderrBuilder: StringBuilder,
            timeoutAsError: Boolean
    ): CommandResult {
        Log.e(TAG, createLogMessage(command, "命令执行超时: ${e.message}"))

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

        // 根据选项决定超时是否视为错误
        val timeoutIsError = timeoutAsError

        // 修改：通知接收器超时，如果timeoutAsError为false，则不视为错误
        if (timeoutIsError) {
            effectiveOutputReceiver?.onError("Command execution timed out", -1)
        } else {
            val timeoutResult =
                    CommandResult(
                            success = true, // 视超时为成功
                            stdout = stdoutBuilder.toString(),
                            stderr = "Command timed out, but considered successful",
                            exitCode = 0 // 使用0作为成功的退出码
                    )
            effectiveOutputReceiver?.onComplete(timeoutResult)
        }

        // 清理临时文件
        GlobalScope.launch(Dispatchers.IO) {
            try {
                AndroidShellExecutor.executeAdbCommand(
                        "run-as com.termux sh -c 'rm -f \"$tempOutputFile\"'"
                )
            } catch (e: Exception) {
                Log.e(TAG, createLogMessage(command, "删除临时文件失败: ${e.message}"))
            }
        }

        return CommandResult(
                success = !timeoutIsError, // 根据选项决定是否视为成功
                stdout = stdoutBuilder.toString(),
                stderr =
                        if (timeoutIsError) "Command execution timed out"
                        else "Command timed out, but considered successful",
                exitCode = if (timeoutIsError) -1 else 0 // 根据是否视为错误设置不同的退出码
        )
    }

    /** 处理命令执行取消 */
    suspend fun handleCancellation(
            command: String,
            e: CancellationException,
            executionId: Int,
            context: Context,
            broadcastReceiver: BroadcastReceiver,
            commandIsRunning: AtomicBoolean,
            tempOutputFile: String,
            effectiveOutputReceiver: TermuxCommandOutputReceiver?,
            stdoutBuilder: StringBuilder
    ): CommandResult {
        Log.e(TAG, createLogMessage(command, "命令执行被取消: ${e.message}"))

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

        // 通知接收器取消
        effectiveOutputReceiver?.onError("Command execution was cancelled", -1)

        // 清理临时文件
        GlobalScope.launch(Dispatchers.IO) {
            try {
                AndroidShellExecutor.executeAdbCommand(
                        "run-as com.termux sh -c 'rm -f \"$tempOutputFile\"'"
                )
            } catch (e: Exception) {
                Log.e(TAG, createLogMessage(command, "删除临时文件失败: ${e.message}"))
            }
        }

        return CommandResult(
                success = false,
                stdout = stdoutBuilder.toString(),
                stderr = "Command execution was cancelled: ${e.message}",
                exitCode = -1
        )
    }

    /** 处理命令执行异常 */
    suspend fun handleException(
            command: String,
            e: Exception,
            executionId: Int,
            context: Context,
            broadcastReceiver: BroadcastReceiver,
            commandIsRunning: AtomicBoolean,
            tempOutputFile: String,
            effectiveOutputReceiver: TermuxCommandOutputReceiver?,
            stdoutBuilder: StringBuilder
    ): CommandResult {
        // Log.e(TAG, createLogMessage(command, "命令执行过程中出现异常: ${e.message}"), e)

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

        val errorResult =
                CommandResult(
                        success = false,
                        stdout = stdoutBuilder.toString(),
                        stderr = "Error: ${e.message}",
                        exitCode = -1
                )
        effectiveOutputReceiver?.onError("Error: ${e.message}", -1)
        return errorResult
    }
}
