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

/**
 * 用于执行Termux命令的工具类
 */
class TermuxCommandExecutor {
    companion object {
        private const val TAG = "TermuxCommandExecutor"
        private const val DEFAULT_TIMEOUT = 30000L // 默认超时时间：30秒
        private var EXECUTION_ID = 1000
        
        /**
         * 获取下一个执行ID
         * 用于确保每个命令执行有唯一的ID
         */
        private fun getNextExecutionId(): Int {
            return EXECUTION_ID++
        }
        
        /**
         * 执行Termux命令
         * @param context 上下文
         * @param command 要执行的命令
         * @param autoAuthorize 如果Termux未授权，是否自动尝试授权
         * @param background 是否在后台执行命令
         * @param resultCallback 命令执行结果回调，如果为null则不接收结果
         * @return 执行结果 (仅表示命令是否成功发送，不代表实际执行结果)
         */
        suspend fun executeCommand(
            context: Context, 
            command: String,
            autoAuthorize: Boolean = true,
            background: Boolean = false,
            resultCallback: ((CommandResult) -> Unit)? = null
        ): CommandResult = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "执行命令: $command")
                
                // 首先检查Termux是否已安装
                if (!TermuxInstaller.isTermuxInstalled(context)) {
                    return@withContext CommandResult(
                        success = false,
                        stdout = "",
                        stderr = "Termux is not installed",
                        exitCode = -1
                    )
                }
                
                // 检查是否已授权，如果需要则尝试授权
                if (autoAuthorize && !TermuxAuthorizer.isTermuxAuthorized(context)) {
                    val authorized = TermuxAuthorizer.authorizeTermux(context)
                    if (!authorized) {
                        return@withContext CommandResult(
                            success = false,
                            stdout = "",
                            stderr = "Failed to authorize Termux",
                            exitCode = -1
                        )
                    }
                }
                
                // 构建Intent
                val intent = Intent()
                    .setClassName("com.termux", "com.termux.app.RunCommandService")
                    .setAction("com.termux.RUN_COMMAND")
                
                // 设置命令路径和参数
                intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
                intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                
                // 如果需要接收结果
                if (resultCallback != null) {
                    // 生成一个唯一的执行ID
                    val executionId = getNextExecutionId()
                    
                    // 创建BroadcastReceiver接收结果
                    val resultReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            // 提取和返回结果
                            val result = extractCommandResult(intent)
                            resultCallback(result)
                        }
                    }
                    
                    // 先声明wrapperReceiver变量，以便在timeoutRunnable中引用
                    lateinit var wrapperReceiver: BroadcastReceiver
                    
                    // 创建超时处理
                    val timeoutHandler = Handler(Looper.getMainLooper())
                    val timeoutRunnable = Runnable {
                        try {
                            context.unregisterReceiver(wrapperReceiver)
                            resultCallback(CommandResult(false, "", "Command execution timed out", -1))
                        } catch (e: Exception) {
                            // 忽略注销失败
                        }
                    }
                    
                    // 创建包装接收器
                    wrapperReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            // 取消超时
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            
                            // 调用原始接收器
                            resultReceiver.onReceive(context, intent)
                            
                            // 注销自身
                            try {
                                context?.unregisterReceiver(this)
                            } catch (e: Exception) {
                                // 忽略注销失败
                            }
                        }
                    }
                    
                    // 注册接收器，使用特定的Action和执行ID
                    val filter = IntentFilter("com.termux.app.RESULT_${executionId}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(wrapperReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        context.registerReceiver(wrapperReceiver, filter)
                    }
                    
                    // 创建PendingIntent
                    val resultIntent = Intent("com.termux.app.RESULT_${executionId}")
                    resultIntent.setPackage(context.packageName)
                    
                    // 设置标志
                    val finalFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_ONE_SHOT
                    }
                    
                    val pendingIntent = PendingIntent.getBroadcast(
                        context.applicationContext,
                        executionId,
                        resultIntent,
                        finalFlags
                    )
                    
                    // 添加PendingIntent到Intent
                    intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent)
                    
                    // 设置超时
                    timeoutHandler.postDelayed(timeoutRunnable, DEFAULT_TIMEOUT)
                    
                    // 发送命令
                    context.startService(intent)
                    
                    // 返回临时结果，表示命令已发送并等待结果
                    return@withContext CommandResult(
                        success = true,
                        stdout = "Command sent to Termux, waiting for result...",
                        stderr = "",
                        exitCode = 0
                    )
                } else {
                    // 如果没有回调，立即返回结果
                    context.startService(intent)
                    return@withContext CommandResult(
                        success = true,
                        stdout = "Command sent to Termux",
                        stderr = "",
                        exitCode = 0
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "执行命令失败: ${e.message}")
                return@withContext CommandResult(
                    success = false,
                    stdout = "",
                    stderr = "Error: ${e.message}",
                    exitCode = -1
                )
            }
        }
        
        /**
         * 执行Termux命令并获取结果
         * @param context 上下文
         * @param command 要执行的命令
         * @param timeoutMillis 等待结果的超时时间（毫秒）
         * @param autoAuthorize 如果Termux未授权，是否自动尝试授权
         * @return 执行结果
         */
        suspend fun executeCommandAndGetResult(
            context: Context,
            command: String,
            timeoutMillis: Long = DEFAULT_TIMEOUT,
            autoAuthorize: Boolean = true
        ): CommandResult = withContext(Dispatchers.IO) {
            // 使用CompletableDeferred来等待结果
            val deferred = CompletableDeferred<CommandResult>()
            
            // 使用executeCommand并提供回调
            executeCommand(
                context = context,
                command = command,
                autoAuthorize = autoAuthorize,
                background = true, // 在后台运行以获取结果
                resultCallback = { result ->
                    deferred.complete(result)
                }
            )
            
            // 等待结果，设置超时
            try {
                withTimeout(timeoutMillis) {
                    deferred.await()
                }
            } catch (e: Exception) {
                CommandResult(
                    success = false,
                    stdout = "",
                    stderr = "Command execution timed out after ${timeoutMillis}ms",
                    exitCode = -1
                )
            }
        }
        
        /**
         * 将Intent中的Bundle转换为CommandResult
         */
        private fun extractCommandResult(intent: Intent?): CommandResult {
            if (intent == null || intent.extras == null) {
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
            
            // 仅基于exitCode判断成功
            return CommandResult(
                success = exitCode == 0,
                stdout = stdout,
                stderr = if (errmsg.isNotEmpty()) "$stderr\n$errmsg" else stderr,
                exitCode = exitCode
            )
        }
    }
} 