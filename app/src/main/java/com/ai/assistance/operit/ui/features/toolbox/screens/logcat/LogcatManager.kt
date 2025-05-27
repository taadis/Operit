package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.Context
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** 日志管理器 */
class LogcatManager(private val context: Context) {
    private val TAG = "LogcatManager"
    private var isRunning = false
    private var captureJob: Job? = null
    
    // 日志解析正则表达式
    private val logPattern = Regex("^([VDIWEAF])/([^(]+)\\(\\s*(\\d+)\\):\\s+(.+)$")

    /** 开始捕获日志 */
    fun startLogcat(onNewLog: (LogRecord) -> Unit, filter: String = ""): Job? {
        if (isRunning) {
            // 使用同步方式停止之前的捕获，确保资源完全释放
            stopLogcatSync()
        }

        try {
            isRunning = true
            
            val command =
                    if (filter.isBlank()) {
                        "logcat -v threadtime" // 使用threadtime格式获取更完整的时间戳
                    } else {
                        "logcat -v threadtime $filter"
                    }

            // 清理日志缓冲区
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AndroidShellExecutor.executeShellCommand("logcat -c")
                } catch (e: Exception) {
                    Log.e(TAG, "清理日志缓冲区失败", e)
                }
            }
            
            // 使用flow来持续收集logcat输出
            captureJob =
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // 创建进程读取器通道
                            logcatProcessFlow(command)
                                .buffer(capacity = 100) // 缓冲区
                                .collect { line ->
                                    if (line.isNotBlank() && isRunning) {
                                        parseLogLine(line)?.let { record ->
                                            // 直接发送日志记录
                                            onNewLog(record)
                                        }
                                    }
                                }
                        } catch (e: Exception) {
                            Log.e(TAG, "日志捕获过程中发生异常", e)
                        } finally {
                            Log.d(TAG, "日志捕获已结束")
                        }
                    }
            
            return captureJob
        } catch (e: Exception) {
            Log.e(TAG, "启动日志捕获失败", e)
            isRunning = false
            return null
        }
    }

    /** 创建一个流，持续输出logcat进程的每一行 */
    private fun logcatProcessFlow(command: String): Flow<String> =
            flow {
                val bufferSize = 8192 // 缓冲区大小
                var process: Process? = null

                try {
                    Log.d(TAG, "启动logcat命令: $command")
                    
                    // 对于需要持续输出的logcat命令，仍然使用Runtime.exec
                    // AndroidShellExecutor不适合持续流式读取输出
                    process = Runtime.getRuntime().exec(command)
                    val reader =
                            BufferedReader(
                                    InputStreamReader(process.inputStream),
                                    bufferSize
                            )
                    
                    var line: String? = null
                    while (isRunning && reader.readLine().also { line = it } != null) {
                        line?.let { emit(it) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "读取logcat流时发生错误", e)
                    throw e
                } finally {
                    process?.destroy()
                }
            }
            .flowOn(Dispatchers.IO)
    
    /** 停止捕获日志（异步方式） */
    fun stopLogcat() {
        // 使用协程在IO线程中同步停止
        CoroutineScope(Dispatchers.IO).launch { stopLogcatSync() }
    }

    /** 停止捕获日志（同步方式） */
    private fun stopLogcatSync() {
        isRunning = false
        
        try {
            // 给协程一点时间来处理剩余日志
            captureJob?.let { job ->
                runBlocking {
                    withTimeoutOrNull(1000) { // 最多等待1秒
                        job.cancelAndJoin()
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略取消异常
        } finally {
            captureJob = null
        }
    }

    /** 解析日志行 */
    private fun parseLogLine(line: String): LogRecord? {
        // 尝试解析标准格式的logcat输出
        // threadtime格式例如: 05-12 11:22:33.444 1234 5678 D TAG: Message here
        try {
            // 处理多种可能的日志格式
            if (line.length > 18) { // 包含足够的字符
                val levelPosition = findLogLevelPosition(line)
                if (levelPosition >= 0) {
                    val levelChar = line[levelPosition]
                    val level =
                            when (levelChar) {
                                'V' -> LogLevel.VERBOSE
                                'D' -> LogLevel.DEBUG
                                'I' -> LogLevel.INFO
                                'W' -> LogLevel.WARNING
                                'E' -> LogLevel.ERROR
                                'F' -> LogLevel.FATAL
                                'S' -> LogLevel.SILENT
                                else -> LogLevel.UNKNOWN
                            }
                    
                    // 尝试提取tag和消息
                    val colonPosition = line.indexOf(':', levelPosition + 2)
                    if (colonPosition > levelPosition) {
                        val tag = line.substring(levelPosition + 2, colonPosition).trim()
                        val message = line.substring(colonPosition + 1).trim()
                        
                        return LogRecord(
                            message = message,
                            level = level,
                            timestamp = System.currentTimeMillis(),
                            tag = tag,
                            // 无法精确提取pid和tid
                            pid = extractPid(line),
                            tid = null
                        )
                    }
                }
            }

            // 无法解析为标准格式，返回整行作为消息
            return LogRecord(
                message = line, 
                level = LogLevel.UNKNOWN,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            // 解析异常，确保至少返回原始行
            return LogRecord(
                message = "解析错误: $line", 
                level = LogLevel.ERROR,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /** 查找日志级别字符的位置 */
    private fun findLogLevelPosition(line: String): Int {
        // 寻找常见的日志级别字符
        for (i in 20 until line.length) {
            if (line[i] in "VDIEFWS" && i + 1 < line.length && line[i + 1] == '/') {
                return i
            }
        }
        
        // 使用正则表达式搜索可能的日志级别
        val levelMatch = Regex("([VDIEFWS])\\s+\\w+:").find(line)
        return levelMatch?.range?.start ?: -1
    }

    /** 尝试从日志中提取进程ID */
    private fun extractPid(line: String): String? {
        // 尝试使用正则表达式匹配进程ID
        val pidMatch = Regex("\\((\\d+)\\)").find(line) ?: return null
        return pidMatch.groupValues[1]
    }

    /** 清除日志缓冲区 */
    fun clearLogcat() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AndroidShellExecutor.executeShellCommand("logcat -c")
            } catch (e: Exception) {
                Log.e(TAG, "清除日志缓冲区失败", e)
            }
        }
    }

    /** 获取预设过滤器列表 */
    fun getPresetFilters(): List<PresetFilter> {
        return listOf(
                PresetFilter(
                        name = "所有日志",
                        filter = "",
                        description = "显示所有日志记录",
                        category = FilterCategory.LEVEL,
                        icon = Icons.Default.List
                ),
                PresetFilter(
                        name = "错误日志",
                        filter = "*:E",
                        description = "只显示错误级别的日志",
                        category = FilterCategory.LEVEL,
                        icon = Icons.Default.Error
                ),
                PresetFilter(
                        name = "警告和错误",
                        filter = "*:W",
                        description = "显示警告和错误级别的日志",
                        category = FilterCategory.LEVEL,
                        icon = Icons.Default.Warning
                ),
                PresetFilter(
                        name = "信息及以上",
                        filter = "*:I",
                        description = "显示信息、警告和错误级别的日志",
                        category = FilterCategory.LEVEL,
                        icon = Icons.Default.Info
                ),
                PresetFilter(
                        name = "系统服务",
                        filter = "ActivityManager:* PackageManager:*",
                        description = "显示系统服务相关日志",
                        category = FilterCategory.SYSTEM,
                        icon = Icons.Default.Settings
                ),
                PresetFilter(
                        name = "崩溃日志",
                        filter = "AndroidRuntime:E CrashAnrDetector:*",
                        description = "显示应用崩溃相关日志",
                        category = FilterCategory.SYSTEM,
                        icon = Icons.Default.BugReport
                ),
                PresetFilter(
                        name = "网络活动",
                        filter = "ConnectivityService:* NetworkStats:* WifiService:*",
                        description = "显示网络活动相关日志",
                        category = FilterCategory.SYSTEM,
                        icon = Icons.Default.Wifi
                ),
                PresetFilter(
                        name = "当前应用",
                        filter = "--pid=${android.os.Process.myPid()}",
                        description = "只显示当前应用的日志",
                        category = FilterCategory.APP,
                        icon = Icons.Default.PhoneAndroid
                )
        )
    }
}
