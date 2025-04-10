package com.ai.assistance.operit.tools.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 工具类用于通过Shizuku执行ADB命令
 */
class AdbCommandExecutor {
    companion object {
        private const val TAG = "AdbCommandExecutor"
        private val serviceCache = ConcurrentHashMap<Int, IShizukuService>()
        private val mainHandler = Handler(Looper.getMainLooper())
        
        // 注册Shizuku权限请求监听器
        private var binderReceivedListenerRegistered = false
        private var permissionRequestListenerRegistered = false
        
        // 服务状态
        private var isServiceAvailable = false
        
        // 状态变更回调
        private val stateChangeListeners = mutableListOf<() -> Unit>()
        
        /**
         * 添加状态变更监听器
         */
        fun addStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) {
                if (!stateChangeListeners.contains(listener)) {
                    stateChangeListeners.add(listener)
                }
            }
        }
        
        /**
         * 移除状态变更监听器
         */
        fun removeStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) {
                stateChangeListeners.remove(listener)
            }
        }
        
        /**
         * 触发状态变更通知
         */
        private fun notifyStateChanged() {
            // 确保在主线程中执行UI相关回调
            mainHandler.post {
                synchronized(stateChangeListeners) {
                    Log.d(TAG, "Notifying ${stateChangeListeners.size} listeners about state change")
                    stateChangeListeners.forEach { it.invoke() }
                }
            }
        }
        
        /**
         * 检查Shizuku是否已安装
         * @param context Android上下文
         * @return 是否已安装Shizuku
         */
        fun isShizukuInstalled(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
        
        /**
         * 检查Shizuku服务是否正在运行
         * @return 服务是否运行
         */
        fun isShizukuServiceRunning(): Boolean {
            try {
                // 首先检查本地缓存的状态 - 如果已经知道服务可用，直接返回
                if (isServiceAvailable) {
                    // Log.d(TAG, "Shizuku service is available (cached)")
                    return true
                }
                
                // 方法1: 使用pingBinder - 这是最可靠的检测方法
                try {
                    if (Shizuku.pingBinder()) {
                        Log.d(TAG, "Shizuku pingBinder succeeded")
                        isServiceAvailable = true
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Shizuku pingBinder check failed", e)
                }
                
                // 方法2: 直接获取并检查binder存活状态
                try {
                    val binder = Shizuku.getBinder()
                    if (binder != null && binder.isBinderAlive) {
                        Log.d(TAG, "Shizuku binder is alive")
                        isServiceAvailable = true
                        return true
                    } else if (binder == null) {
                        // Log.d(TAG, "Shizuku binder is null")
                    } else {
                        // Log.d(TAG, "Shizuku binder is not alive")
                    }
                } catch (e: Exception) {
                    // Log.e(TAG, "Binder check failed", e)
                }
                
                // 方法3: 尝试获取Shizuku UID (如果可以获取有效UID，说明服务在运行)
                try {
                    val uid = Shizuku.getUid()
                    Log.d(TAG, "Shizuku UID: $uid")
                    if (uid > 0) {
                        isServiceAvailable = true
                        return true
                    }
                } catch (e: Exception) {
                //    Log.e(TAG, "UID check failed", e)
                }
                
                // 记录详细的检测结果
                // Log.d(TAG, "All Shizuku service checks failed")
                isServiceAvailable = false
                return false
            } catch (e: Throwable) {
                Log.e(TAG, "Critical error checking Shizuku service", e)
                isServiceAvailable = false
                return false
            }
        }
        
        /**
         * 检查应用是否有Shizuku权限
         * @return 是否有权限
         */
        fun hasShizukuPermission(): Boolean {
            try {
                if (!isShizukuServiceRunning()) {
                    Log.d(TAG, "Cannot check permission: Shizuku service not running")
                    return false
                }
                
                // 适用于Shizuku 13.x版本的权限检查
                val result = Shizuku.checkSelfPermission()
                // Log.d(TAG, "Shizuku permission check result: $result")
                return result == PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Shizuku permission", e)
                return false
            }
        }
        
        /**
         * 请求Shizuku权限
         * @param onResult 权限请求结果回调
         */
        fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
            if (!isShizukuServiceRunning()) {
                Log.e(TAG, "Cannot request permission: Shizuku service not running")
                onResult(false)
                return
            }
            
            if (hasShizukuPermission()) {
                Log.d(TAG, "Permission already granted")
                onResult(true)
                notifyStateChanged() // 确保已有权限时也通知状态变化
                return
            }
            
            Log.d(TAG, "Requesting Shizuku permission")
            
            // 移除之前的监听器避免重复
            try {
                if (permissionRequestListenerRegistered) {
                    Shizuku.removeRequestPermissionResultListener { _, _ -> }
                    permissionRequestListenerRegistered = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing existing permission listener", e)
            }
            
            try {
                val requestCode = 100
                
                // 为Shizuku 13.x添加日志
                Log.d(TAG, "Setting up permission result listener")
                
                Shizuku.addRequestPermissionResultListener { code, grantResult ->
                    Log.d(TAG, "Permission result received: code=$code, result=$grantResult")
                    if (code == requestCode) {
                        val granted = grantResult == PackageManager.PERMISSION_GRANTED
                        Log.d(TAG, "Shizuku permission request result: $granted")
                        onResult(granted)
                        if (granted) {
                            // 权限授予时触发状态变更通知
                            notifyStateChanged()
                        }
                        
                        // 权限请求完成后移除监听器
                        try {
                            Shizuku.removeRequestPermissionResultListener { _, _ -> }
                            permissionRequestListenerRegistered = false
                        } catch (e: Exception) {
                            Log.e(TAG, "Error removing permission listener", e)
                        }
                    }
                }
                permissionRequestListenerRegistered = true
                
                // 请求权限
                Log.d(TAG, "Calling Shizuku.requestPermission($requestCode)")
                Shizuku.requestPermission(requestCode)
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting Shizuku permission", e)
                onResult(false)
            }
        }
        
        /**
         * 初始化Shizuku绑定
         */
        fun initializeShizuku() {
            Log.d(TAG, "Initializing Shizuku")
            
            // 重置服务状态
            isServiceAvailable = false
            
            // 移除之前的监听器避免重复
            if (binderReceivedListenerRegistered) {
                try {
                    Shizuku.removeBinderReceivedListener { }
                    Shizuku.removeBinderDeadListener { }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing binder listeners", e)
                }
                binderReceivedListenerRegistered = false
            }
            
            try {
                // 设置绑定接收监听器
                Shizuku.addBinderReceivedListener {
                    Log.d(TAG, "Shizuku binder received")
                    isServiceAvailable = true
                    notifyStateChanged()
                    
                    // 当收到binder时主动检查权限状态
                    mainHandler.post {
                        try {
                            val hasPermission = hasShizukuPermission()
                            Log.d(TAG, "Checking permission after binder received: $hasPermission")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error checking permission after binder received", e)
                        }
                    }
                }
                
                // 设置绑定断开监听器
                Shizuku.addBinderDeadListener {
                    Log.d(TAG, "Shizuku binder dead")
                    isServiceAvailable = false
                    notifyStateChanged()
                }
                
                binderReceivedListenerRegistered = true
                
                // 立即检查服务是否已经在运行
                val isRunning = isShizukuServiceRunning()
                Log.d(TAG, "Initial Shizuku service status check: $isRunning")
                if (isRunning) {
                    // 如果服务正在运行，检查权限
                    mainHandler.post {
                        try {
                            val hasPermission = hasShizukuPermission()
                            Log.d(TAG, "Initial permission check: $hasPermission")
                            notifyStateChanged()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error during initial permission check", e)
                        }
                    }
                } else {
                    // 如果服务未运行，500毫秒后再次检查以防初始化延迟
                    mainHandler.postDelayed({
                        val retryCheck = isShizukuServiceRunning()
                        Log.d(TAG, "Delayed service status check: $retryCheck")
                        if (retryCheck) {
                            notifyStateChanged()
                        }
                    }, 500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Shizuku", e)
            }
        }
        
        /**
         * 获取Shizuku启动说明
         */
        fun getShizukuStartupInstructions(): String {
            return "要启动Shizuku服务：\n" +
                   "1. 确保已安装Shizuku应用\n" +
                   "2. 使用ADB命令启动Shizuku:\n" +
                   "   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh\n" +
                   "或者\n\n" + 
                   "2. 打开Shizuku应用并按照屏幕上的说明操作"
        }
        
        /**
         * 检测命令是否包含需要shell解释的特殊操作符
         * 这个方法更准确地检测操作符，避免误判命令参数中包含的特殊字符
         * @param command 要检查的命令
         * @return 是否包含shell操作符
         */
        private fun containsShellOperators(command: String): Boolean {
            // 预处理：标记引号内的内容，避免检测引号内的操作符
            var inSingleQuotes = false
            var inDoubleQuotes = false
            var escaped = false
            var i = 0
            
            while (i < command.length) {
                val c = command[i]
                
                // 处理转义字符
                if (c == '\\' && !escaped) {
                    escaped = true
                    i++
                    continue
                }
                
                // 处理引号
                if (c == '\'' && !escaped && !inDoubleQuotes) {
                    inSingleQuotes = !inSingleQuotes
                } else if (c == '"' && !escaped && !inSingleQuotes) {
                    inDoubleQuotes = !inDoubleQuotes
                } 
                // 只在不在引号内时检测操作符
                else if (!inSingleQuotes && !inDoubleQuotes && !escaped) {
                    // 检测管道
                    if (c == '|') {
                        // 检查是不是 || 操作符
                        if (i + 1 < command.length && command[i + 1] == '|') {
                            return true
                        }
                        // 单个 | 管道符
                        return true
                    }
                    
                    // 检测 && 操作符
                    if (c == '&') {
                        // 检查是不是 && 操作符
                        if (i + 1 < command.length && command[i + 1] == '&') {
                            return true
                        }
                        // 后台运行符号 &
                        return true
                    }
                    
                    // 检测重定向
                    if (c == '>' || c == '<') {
                        return true
                    }
                    
                    // 检测分号
                    if (c == ';') {
                        return true
                    }
                }
                
                escaped = false
                i++
            }
            
            return false
        }
        
        /**
         * 封装执行ADB命令的函数
         * @param command 要执行的ADB命令
         * @return 命令执行结果
         * 
         * 支持的shell特性:
         * - 管道操作符 (|) 用于命令链接
         * - 重定向操作符 (>, <, >>, 2>, &>, etc.) 用于输入输出重定向
         * - 命令连接符 (&&, ||, ;) 用于组合多条命令
         * - 后台运行 (&) 用于后台执行命令
         */
        suspend fun executeAdbCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
            if (!isShizukuServiceRunning()) {
                return@withContext CommandResult(false, "", "Shizuku service not running")
            }
            
            if (!hasShizukuPermission()) {
                return@withContext CommandResult(false, "", "Shizuku permission not granted")
            }

            Log.d(TAG, "Executing command: $command")
            
            // 使用更精确的方法检测shell操作符
            if (containsShellOperators(command)) {
                Log.d(TAG, "Command contains shell operators or redirections, executing with shell")
                return@withContext executeWithShell(command)
            }
            
            // 普通命令执行
            return@withContext executeCommandDirect(command)
        }
        
        /**
         * 封装重试逻辑的函数
         * @param maxRetries 最大重试次数
         * @param delayMs 每次重试前的延迟时间（毫秒）
         * @param operation 要执行的操作
         * @return 操作结果
         */
        private suspend fun <T> retryOperation(
            maxRetries: Int = 3,
            delayMs: Long = 500,
            operation: suspend () -> T
        ): T {
            var lastException: Exception? = null
            for (attempt in 0 until maxRetries) {
                try {
                    return operation()
                } catch (e: Exception) {
                    // 检查是否是 read interrupted 异常
                    val isInterruptedRead = e is InterruptedIOException && 
                                         e.message?.contains("read interrupted") == true
                    
                    if (isInterruptedRead) {
                        lastException = e
                        Log.w(TAG, "Read interrupted on attempt ${attempt + 1}/$maxRetries, retrying in $delayMs ms", e)
                        delay(delayMs)
                        continue
                    } else {
                        // 对于其他异常，直接抛出
                        throw e
                    }
                }
            }
            // 如果达到最大重试次数，抛出最后一个异常
            throw lastException ?: IllegalStateException("Unknown error in retry operation")
        }
        
        /**
         * 直接执行不包含特殊操作符的普通命令
         */
        private suspend fun executeCommandDirect(command: String): CommandResult = withContext(Dispatchers.IO) {
            var process: Any? = null
            
            try {
                val service = getShizukuService() ?: return@withContext CommandResult(
                    false,
                    "",
                    "Shizuku service not available"
                )
                
                // 拆分命令行参数 - 使用更智能的解析方法
                val commandParts = parseCommand(command)
                // Log.d(TAG, "Parsed command parts: ${commandParts.joinToString(", ", "[", "]")}")
                
                // 创建进程
                process = service.newProcess(commandParts, null, null)
                
                if (process == null) {
                    return@withContext CommandResult(false, "", "Failed to create process")
                }
                
                // 将ParcelFileDescriptor转换为InputStream
                val processClass = process::class.java
                val inputStream = processClass.getMethod("getInputStream").invoke(process) as ParcelFileDescriptor?
                val errorStream = processClass.getMethod("getErrorStream").invoke(process) as ParcelFileDescriptor?
                
                // 使用重试逻辑读取标准输出和错误输出
                val stdout = if (inputStream != null) {
                    retryOperation {
                        val stdoutStream = FileInputStream(inputStream.fileDescriptor)
                        BufferedReader(InputStreamReader(stdoutStream)).use { it.readText() }
                    }
                } else ""
                
                val stderr = if (errorStream != null) {
                    retryOperation {
                        val stderrStream = FileInputStream(errorStream.fileDescriptor)
                        BufferedReader(InputStreamReader(stderrStream)).use { it.readText() }
                    }
                } else ""
                
                // 等待进程结束并获取退出代码
                val exitCode = processClass.getMethod("waitFor").invoke(process) as Int
                
                // 返回结果
                return@withContext CommandResult(
                    exitCode == 0,
                    stdout,
                    stderr,
                    exitCode
                )
            } catch (e: RemoteException) {
                Log.e(TAG, "Remote exception while executing command", e)
                return@withContext CommandResult(false, "", "Remote exception: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command", e)
                return@withContext CommandResult(false, "", "Error: ${e.message}")
            } finally {
                // 安全关闭文件描述符
                try {
                    if (process != null) {
                        val processClass = process::class.java
                        try {
                            val inputStream = processClass.getMethod("getInputStream").invoke(process) as ParcelFileDescriptor?
                            inputStream?.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error closing input stream", e)
                        }
                        
                        try {
                            val errorStream = processClass.getMethod("getErrorStream").invoke(process) as ParcelFileDescriptor?
                            errorStream?.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error closing error stream", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in cleanup", e)
                }
            }
        }
        
        /**
         * 通过shell解释器执行包含特殊操作符的命令
         */
        private suspend fun executeWithShell(command: String): CommandResult = withContext(Dispatchers.IO) {
            Log.d(TAG, "Executing through shell: $command")
            
            try {
                val service = getShizukuService() ?: return@withContext CommandResult(
                    false,
                    "",
                    "Shizuku service not available"
                )
                
                // 检测是否包含重定向操作符进行写入操作
                val containsRedirection = command.contains(">") 
                
                // 处理命令，确保使用完整路径
                val processedCommand = if (command.contains("|") && command.contains("grep")) {
                    // 替换 'grep' 为 '/system/bin/grep'，确保使用系统grep命令
                    command.replace(" grep ", " /system/bin/grep ")
                } else {
                    command
                }
                
                // 构建增强的shell环境和命令
                // 添加umask设置确保文件权限较为宽松，避免权限问题
                // 添加PATH环境变量确保找到所有系统命令
                val enhancedCommand = if (containsRedirection) {
                    // 为重定向操作添加更多环境支持
                    "umask 0022 && PATH=\$PATH:/system/bin:/system/xbin:/vendor/bin:/vendor/xbin && $processedCommand"
                } else {
                    processedCommand
                }
                
                // 构建shell命令，使用-e确保出错时立即退出
                val shellArgs = arrayOf("sh", "-e", "-c", enhancedCommand)
                Log.d(TAG, "Enhanced shell command: ${shellArgs.joinToString(", ", "[", "]")}")
                
                // 创建进程
                val process = service.newProcess(shellArgs, null, null) ?: return@withContext CommandResult(
                    false,
                    "",
                    "Failed to create process"
                )
                
                // 处理输入输出流
                val processClass = process::class.java
                val inputStream = processClass.getMethod("getInputStream").invoke(process) as ParcelFileDescriptor?
                val errorStream = processClass.getMethod("getErrorStream").invoke(process) as ParcelFileDescriptor?
                
                // 使用重试逻辑读取标准输出和错误输出
                val stdout = if (inputStream != null) {
                    retryOperation {
                        val stdoutStream = FileInputStream(inputStream.fileDescriptor)
                        BufferedReader(InputStreamReader(stdoutStream)).use { it.readText() }
                    }
                } else ""
                
                val stderr = if (errorStream != null) {
                    retryOperation {
                        val stderrStream = FileInputStream(errorStream.fileDescriptor)
                        BufferedReader(InputStreamReader(stderrStream)).use { it.readText() }
                    }
                } else ""
                
                // 等待进程结束并获取退出代码
                val exitCode = processClass.getMethod("waitFor").invoke(process) as Int
                
                // 关闭文件描述符
                try {
                    inputStream?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing input stream in shell execution", e)
                }
                
                try {
                    errorStream?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing error stream in shell execution", e)
                }
                
                // 记录详细的结果日志以便调试
                Log.d(TAG, "Shell execution result: exitCode=$exitCode, success=${exitCode == 0}, stdout length=${stdout.length}, stderr length=${stderr.length}")
                if (stdout.isNotEmpty()) {
                    Log.d(TAG, "Shell stdout (first 100 chars): ${stdout.take(100)}")
                }
                if (stderr.isNotEmpty()) {
                    Log.e(TAG, "Shell stderr (first 100 chars): ${stderr.take(100)}")
                }
                
                // 确定命令是否成功
                val success = when {
                    // 如果命令包含grep，即使没有找到匹配也认为成功
                    command.contains("grep") -> exitCode == 0 || exitCode == 1
                    
                    // 对其他命令，只有exitCode=0才算成功
                    else -> exitCode == 0
                }
                
                return@withContext CommandResult(
                    success,
                    stdout,
                    stderr,
                    exitCode
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error executing shell command", e)
                return@withContext CommandResult(false, "", "Error: ${e.message}")
            }
        }
        
        /**
         * 获取Shizuku服务
         */
        @Throws(RemoteException::class)
        private fun getShizukuService(): IShizukuService? {
            try {
                val uid = Shizuku.getUid()
                if (uid <= 0) {
                    Log.d(TAG, "Invalid Shizuku UID: $uid")
                    return null
                }
                
                // 检查缓存的服务是否可用
                val cached = serviceCache[uid]
                if (cached != null) {
                    val isCachedAlive = try {
                        cached.asBinder().pingBinder()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pinging cached binder", e)
                        false
                    }
                    
                    if (isCachedAlive) {
                        // Log.d(TAG, "Using cached Shizuku service")
                        return cached
                    } else {
                        Log.d(TAG, "Cached Shizuku service is dead, removing from cache")
                        serviceCache.remove(uid)
                    }
                }
                
                // 获取新的Binder
                val binder = Shizuku.getBinder()
                if (binder == null) {
                    Log.d(TAG, "Shizuku binder is null")
                    return null
                }
                
                if (!binder.isBinderAlive) {
                    Log.d(TAG, "Shizuku binder is not alive")
                    return null
                }
                
                // 创建新的服务实例
                Log.d(TAG, "Creating new Shizuku service interface")
                val service = IShizukuService.Stub.asInterface(binder)
                serviceCache[uid] = service
                return service
            } catch (e: Exception) {
                Log.e(TAG, "Error getting Shizuku service", e)
                return null
            }
        }
        
        /**
         * 智能解析命令行，正确处理引号
         * @param command 完整命令行
         * @return 解析后的参数数组
         */
        private fun parseCommand(command: String): Array<String> {
            val result = mutableListOf<String>()
            val currentArg = StringBuilder()
            var i = 0
            var inSingleQuotes = false
            var inDoubleQuotes = false
            
            while (i < command.length) {
                val c = command[i]
                
                // 处理转义字符
                if (i < command.length - 1 && c == '\\') {
                    val nextChar = command[i + 1]
                    if (nextChar == '\'' || nextChar == '"') {
                        // 处理转义的引号
                        currentArg.append(nextChar)
                        i += 2
                        continue
                    }
                }
                
                // 处理单引号 (只有当不在双引号中时才处理单引号的开始和结束)
                if (c == '\'' && !inDoubleQuotes) {
                    inSingleQuotes = !inSingleQuotes
                    i++
                    continue
                }
                
                // 处理双引号 (只有当不在单引号中时才处理双引号的开始和结束)
                if (c == '"' && !inSingleQuotes) {
                    inDoubleQuotes = !inDoubleQuotes
                    i++
                    continue
                }
                
                // 处理空格 (只有当不在任何引号中时才分割参数)
                if (c == ' ' && !inSingleQuotes && !inDoubleQuotes) {
                    if (currentArg.isNotEmpty()) {
                        result.add(currentArg.toString())
                        currentArg.clear()
                    }
                    i++
                    continue
                }
                
                // 正常字符
                currentArg.append(c)
                i++
            }
            
            // 添加最后一个参数
            if (currentArg.isNotEmpty()) {
                result.add(currentArg.toString())
            }
            
            // 检查未闭合的引号
            if (inSingleQuotes || inDoubleQuotes) {
                Log.w(TAG, "Warning: Unclosed quotes in command: $command")
            }
            
            // 日志记录结果
            // Log.d(TAG, "Command parsing result: ${result.joinToString(", ", "[", "]")}")
            
            return result.toTypedArray()
        }
    }
    
    /**
     * 命令执行结果数据类
     */
    data class CommandResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String = "",
        val exitCode: Int = -1
    )
} 