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

/**
 * 用于执行Termux命令的工具类
 */
class TermuxCommandExecutor {
    companion object {
        private const val TAG = "TermuxCommandExecutor"
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
                Log.d(TAG, "executeCommand开始执行: command=$command, background=$background, hasCallback=${resultCallback != null}")
                
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
                
                // 按照Termux官方文档，正确配置执行Termux命令的Intent
                // 参考: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
                
                // 构建Intent
                val intent = Intent()
                    .setClassName("com.termux", "com.termux.app.RunCommandService")
                    .setAction("com.termux.RUN_COMMAND")
                
                // 设置命令路径 - 使用bash作为解释器
                intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                
                // 设置命令参数 - 使用-c表示执行后面的命令字符串
                intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                
                // 设置工作目录
                intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                
                // 设置是否在后台运行 (true=不显示UI, false=显示终端UI)
                intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
                
                // 设置会话操作 - 0表示创建新会话
                intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                
                // 可选: 添加命令标签和描述，便于调试
                intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Command execution")
                intent.putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION", "Running command: ${command.take(50)}${if(command.length > 50) "..." else ""}")
                
                // 如果需要接收结果，创建PendingIntent
                if (resultCallback != null) {
                    // 生成一个唯一的执行ID
                    val executionId = getNextExecutionId()
                    
                    // 创建BroadcastReceiver接收结果
                    val resultReceiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent == null) {
                                Log.e(TAG, "收到空的Intent回调")
                                return
                            }
                            
                            Log.d(TAG, "收到命令执行回调: ${intent.action}，Intent内容: ${intent.extras?.keySet()?.joinToString()}")
                            
                            // 从Intent中获取结果Bundle
                            val resultBundle = intent.getBundleExtra("com.termux.RUN_COMMAND_RESULT_BUNDLE")
                            if (resultBundle == null) {
                                Log.e(TAG, "回调中没有包含结果Bundle，检查所有Intent extras: ${intent.extras?.keySet()?.joinToString()}")
                                // 尝试查找其他可能包含结果的extras
                                val allExtras = StringBuilder()
                                intent.extras?.keySet()?.forEach { key ->
                                    allExtras.append("$key: ${intent.extras?.get(key)}\n")
                                }
                                Log.d(TAG, "所有Intent extras内容:\n$allExtras")
                                
                                resultCallback(CommandResult(
                                    success = false,
                                    stdout = "",
                                    stderr = "No result bundle returned from Termux",
                                    exitCode = -1
                                ))
                                return
                            }
                            
                            // 解析结果
                            val stdout = resultBundle.getString("stdout", "")
                            val stderr = resultBundle.getString("stderr", "")
                            val exitCode = resultBundle.getInt("exitCode", -1)
                            val errCode = resultBundle.getInt("err", 0)
                            val errmsg = resultBundle.getString("errmsg", "")
                            
                            // 创建命令结果并通过回调返回
                            val result = CommandResult(
                                success = exitCode == 0 && errCode == 0,
                                stdout = stdout,
                                stderr = stderr,
                                exitCode = exitCode
                            )
                            
                            resultCallback(result)
                            
                            // 注销广播接收器
                            try {
                                context?.unregisterReceiver(this)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error unregistering receiver", e)
                            }
                        }
                    }
                    
                    // 注册BroadcastReceiver
                    val filter = IntentFilter("com.termux.app.RESULT_${executionId}")
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        // Android 13+需要指定RECEIVER_EXPORTED或RECEIVER_NOT_EXPORTED标志
                        context.registerReceiver(resultReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        context.registerReceiver(resultReceiver, filter)
                    }
                    
                    // 创建PendingIntent
                    val resultIntent = Intent("com.termux.app.RESULT_${executionId}")
                    // 将隐式Intent转换为显式Intent (指定接收广播的组件)
                    resultIntent.setPackage(context.packageName)
                    
                    val pendingIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                            // 对于Android 14+，使用FLAG_IMMUTABLE
                            android.app.PendingIntent.getBroadcast(
                                context.applicationContext,
                                executionId,
                                resultIntent,
                                android.app.PendingIntent.FLAG_IMMUTABLE
                            )
                        } else {
                            // 对于Android 12-13，可以使用FLAG_MUTABLE
                            android.app.PendingIntent.getBroadcast(
                                context.applicationContext,
                                executionId,
                                resultIntent,
                                android.app.PendingIntent.FLAG_MUTABLE
                            )
                        }
                    } else {
                        // 对于Android 11及以下
                        android.app.PendingIntent.getBroadcast(
                            context.applicationContext,
                            executionId,
                            resultIntent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    }
                    
                    // 添加PendingIntent到运行命令的Intent
                    intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pendingIntent)
                    
                    // 设置超时
                    val timeoutHandler = Handler(Looper.getMainLooper())
                    val timeoutRunnable = Runnable {
                        try {
                            Log.d(TAG, "Command execution timed out after 30 seconds")
                            try {
                                context.unregisterReceiver(resultReceiver)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error unregistering receiver during timeout", e)
                            }
                            
                            resultCallback(CommandResult(
                                success = false,
                                stdout = "",
                                stderr = "Command execution timed out after 30 seconds",
                                exitCode = -1
                            ))
                        } catch (e: Exception) {
                            // 接收器可能已经被注销
                            Log.e(TAG, "Error during timeout handling", e)
                        }
                    }
                    timeoutHandler.postDelayed(timeoutRunnable, 30000) // 30秒超时
                    
                    // 修改onReceive方法，在接收到结果时取消超时
                    val originalOnReceive = resultReceiver.javaClass.getDeclaredMethod("onReceive", Context::class.java, Intent::class.java)
                    originalOnReceive.isAccessible = true
                    
                    // 创建一个新的onReceive方法，将在原方法执行前取消超时
                    resultReceiver.javaClass.getDeclaredField("onReceive").apply {
                        isAccessible = true
                        set(resultReceiver, { context: Context?, intent: Intent? ->
                            // 取消超时
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            // 调用原始onReceive
                            originalOnReceive.invoke(resultReceiver, context, intent)
                        })
                    }
                }
                
                try {
                    // 使用startService启动RunCommandService
                    Log.d(TAG, "Starting Termux RunCommandService with command: $command")
                    Log.d(TAG, "Intent详情: action=${intent.action}, component=${intent.component}, " +
                                "extras=${intent.extras?.keySet()?.joinToString()}")
                    
                    if (resultCallback != null) {
                        Log.d(TAG, "带回调执行，使用PendingIntent")
                    }
                    
                    context.startService(intent)
                    
                    // 如果没有回调，立即返回一个表示命令已发送的结果
                    if (resultCallback == null) {
                        return@withContext CommandResult(
                            success = true,
                            stdout = "Command sent to Termux",
                            stderr = "",
                            exitCode = 0
                        )
                    } else {
                        // 如果有回调，返回一个临时结果
                        return@withContext CommandResult(
                            success = true,
                            stdout = "Command sent to Termux, waiting for result...",
                            stderr = "",
                            exitCode = 0
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing Termux command via Intent: ${e.message}", e)
                    Log.e(TAG, "Stack trace: ", e)
                    
                    // 提供更详细的错误信息
                    val errorDetails = when (e) {
                        is android.content.ActivityNotFoundException -> 
                            "没有找到可处理此Intent的Service或Activity (ActivityNotFoundException). " +
                            "请确认Termux是否正确安装，并且package名称和组件名称正确。"
                        is SecurityException -> 
                            "安全权限问题 (SecurityException). " + 
                            "请确认AndroidManifest.xml中已添加com.termux.permission.RUN_COMMAND权限，" +
                            "并在Termux中设置允许外部应用。"
                        else -> "未知错误: ${e.javaClass.simpleName} - ${e.message}"
                    }
                    
                    Log.e(TAG, "Error details: $errorDetails")
                    
                    // 尝试使用ADB方式执行命令
                    Log.d(TAG, "Falling back to ADB method")
                    return@withContext executeViaAdb(context, command)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing Termux command: ${e.message}", e)
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
         * 直接使用executeCommand方法并提供回调
         * @param context 上下文
         * @param command 要执行的命令
         * @param timeoutMillis 等待结果的超时时间（毫秒）
         * @param autoAuthorize 如果Termux未授权，是否自动尝试授权
         * @return 执行结果
         */
        suspend fun executeCommandAndGetResult(
            context: Context,
            command: String,
            timeoutMillis: Long = 10000,
            autoAuthorize: Boolean = true
        ): CommandResult = withContext(Dispatchers.IO) {
            // 使用CompletableDeferred来等待结果
            val deferred = kotlinx.coroutines.CompletableDeferred<CommandResult>()
            
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
         * 检查文件是否存在于Termux目录中
         */
        private suspend fun checkFileExists(context: Context, filePath: String): Boolean {
            val result = AdbCommandExecutor.executeAdbCommand(
                "run-as com.termux [ -f \"$filePath\" ] && echo \"exists\" || echo \"not exists\""
            )
            return result.success && result.stdout.contains("exists")
        }
        
        /**
         * 读取Termux中的文件内容
         */
        private suspend fun readTermuxFile(context: Context, filePath: String): CommandResult {
            return AdbCommandExecutor.executeAdbCommand(
                "run-as com.termux cat \"$filePath\" 2>/dev/null"
            )
        }
        
        /**
         * 检查Termux是否正在运行
         * @param context 上下文
         * @return Termux是否正在运行
         */
        suspend fun isTermuxRunning(context: Context): Boolean = withContext(Dispatchers.IO) {
            try {
                val result = AdbCommandExecutor.executeAdbCommand(
                    "pidof com.termux"
                )
                
                // 如果命令成功且输出不为空，则Termux正在运行
                return@withContext result.success && result.stdout.trim().isNotEmpty()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if Termux is running", e)
                return@withContext false
            }
        }
        
        /**
         * 通过ADB执行Termux命令
         * 这种方式更可靠，但可能需要root或Shizuku权限
         */
        private suspend fun executeViaAdb(
            context: Context,
            command: String
        ): CommandResult = withContext(Dispatchers.IO) {
            try {
                // 检查Shizuku是否可用
                if (!AdbCommandExecutor.isShizukuInstalled(context) || 
                    !AdbCommandExecutor.isShizukuServiceRunning() || 
                    !AdbCommandExecutor.hasShizukuPermission()) {
                    return@withContext CommandResult(
                        success = false,
                        stdout = "",
                        stderr = "Shizuku not available or not authorized",
                        exitCode = -1
                    )
                }
                
                // 检查Termux是否安装
                if (!TermuxInstaller.isTermuxInstalled(context)) {
                    return@withContext CommandResult(
                        success = false,
                        stdout = "",
                        stderr = "Termux is not installed",
                        exitCode = -1
                    )
                }
                
                // 格式化命令字符串，确保特殊字符被正确处理
                val escapedCommand = command.replace("\"", "\\\"")
                
                // 构建ADB命令以启动Termux的RunCommandService
                // 参考: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
                val termuxCommand = "am startservice " +
                        "--user 0 " +
                        "-n com.termux/com.termux.app.RunCommandService " +
                        "-a com.termux.RUN_COMMAND " +
                        "--es com.termux.RUN_COMMAND_PATH \"/data/data/com.termux/files/usr/bin/bash\" " +
                        "--esa com.termux.RUN_COMMAND_ARGUMENTS \"-c,$escapedCommand\" " +
                        "--es com.termux.RUN_COMMAND_WORKDIR \"/data/data/com.termux/files/home\" " +
                        "--ez com.termux.RUN_COMMAND_BACKGROUND true " +
                        "--es com.termux.RUN_COMMAND_SESSION_ACTION \"0\" " +
                        "--es com.termux.RUN_COMMAND_COMMAND_LABEL \"ADB Command\" " +
                        "--es com.termux.RUN_COMMAND_COMMAND_DESCRIPTION \"Running command via ADB\""
                
                Log.d(TAG, "Executing Termux command via ADB: $termuxCommand")
                
                // 执行ADB命令
                val result = AdbCommandExecutor.executeAdbCommand(termuxCommand)
                
                if (!result.success) {
                    Log.e(TAG, "ADB command execution failed: ${result.stderr}")
                }
                
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Error executing Termux command via ADB: ${e.message}", e)
                return@withContext CommandResult(
                    success = false,
                    stdout = "",
                    stderr = "Error via ADB: ${e.message}",
                    exitCode = -1
                )
            }
        }
    }
} 