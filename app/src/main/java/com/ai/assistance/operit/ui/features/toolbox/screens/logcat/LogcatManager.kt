package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.cancelAndJoin

/** 日志管理器 */
class LogcatManager(private val context: Context) {
    private var logcatProcess: Process? = null
    private var reader: BufferedReader? = null
    private var isRunning = false
    private var captureJob: Job? = null
    
    // 标签统计
    private val _tagStats = CopyOnWriteArrayList<TagStats>()
    val tagStats: List<TagStats> get() = _tagStats.sortedByDescending { it.count }
    
    // 当前过滤配置
    private var currentTagFilters = mutableMapOf<String, FilterAction>()

    // 日志解析正则表达式
    private val logPattern = Regex("^([VDIWEAF])/([^(]+)\\(\\s*(\\d+)\\):\\s+(.+)$")

    /** 开始捕获日志 */
    fun startLogcat(onNewLog: (LogRecord) -> Unit, filter: String = ""): Job? {
        if (isRunning) {
            // 使用同步方式停止之前的捕获，确保资源完全释放
            stopLogcatSync()
        }

        return try {
            isRunning = true
            _tagStats.clear()
            
            val command =
                    if (filter.isBlank()) {
                        "logcat -v threadtime"  // 使用threadtime格式获取更完整的时间戳
                    } else {
                        "logcat -v threadtime $filter"
                    }

            try {
                Runtime.getRuntime().exec("logcat -c")
            } catch (e: Exception) {
                // 忽略清理错误
            }
            
            val process = Runtime.getRuntime().exec(command)
            logcatProcess = process
            reader = BufferedReader(InputStreamReader(process.inputStream))

            captureJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    reader?.use { reader ->
                        var line: String? = null
                        val buffer = mutableListOf<String>()
                        
                        while (isRunning && isActive && reader.readLine().also { line = it } != null) {
                            if (!line.isNullOrBlank()) {
                                buffer.add(line!!)
                                
                                // 每50条日志或者缓冲区大小超过10KB时批量处理
                                if (buffer.size >= 50 || buffer.sumOf { it.length } > 10 * 1024) {
                                    processBatchedLogs(buffer, onNewLog)
                                    buffer.clear()
                                }
                            }
                        }
                        
                        // 处理剩余的日志
                        if (buffer.isNotEmpty()) {
                            processBatchedLogs(buffer, onNewLog)
                        }
                    }
                } catch (e: Exception) {
                    // 记录异常但不中断
                } finally {
                    closeResources()
                }
            }
            
            captureJob
        } catch (e: Exception) {
            isRunning = false
            null
        }
    }

    /** 批量处理日志 */
    private fun processBatchedLogs(logs: List<String>, onNewLog: (LogRecord) -> Unit) {
        for (line in logs) {
            parseLogLine(line)?.let { record ->
                // 根据标签过滤
                val shouldShow = shouldShowLog(record)
                if (shouldShow) {
                    onNewLog(record)
                }
                // 更新标签统计
                record.tag?.let { tag ->
                    updateTagStats(tag)
                }
            }
        }
    }

    /** 是否应该显示该日志（根据标签过滤） */
    private fun shouldShowLog(record: LogRecord): Boolean {
        if (currentTagFilters.isEmpty()) return true
        
        val tag = record.tag ?: return true
        
        // 如果有任何ONLY过滤器，则必须匹配其中一个
        val onlyFilters = currentTagFilters.filter { it.value == FilterAction.ONLY }
        if (onlyFilters.isNotEmpty()) {
            return onlyFilters.keys.contains(tag)
        }
        
        // 如果被排除，则不显示
        if (currentTagFilters[tag] == FilterAction.EXCLUDE) {
            return false
        }
        
        return true
    }
    
    /** 更新标签统计 */
    private fun updateTagStats(tag: String) {
        val existingTag = _tagStats.find { it.tag == tag }
        if (existingTag != null) {
            existingTag.count++
        } else {
            _tagStats.add(TagStats(tag = tag, count = 1, isFiltered = currentTagFilters.containsKey(tag)))
        }
    }
    
    /** 添加标签过滤器 */
    fun addTagFilter(tag: String, action: FilterAction) {
        currentTagFilters[tag] = action
        // 更新标签过滤状态
        _tagStats.find { it.tag == tag }?.let { stats ->
            val index = _tagStats.indexOf(stats)
            if (index >= 0) {
                _tagStats.removeAt(index)
                _tagStats.add(index, stats.copy(isFiltered = true))
            }
        }
    }
    
    /** 移除标签过滤器 */
    fun removeTagFilter(tag: String) {
        currentTagFilters.remove(tag)
        // 更新标签过滤状态
        _tagStats.find { it.tag == tag }?.let { stats ->
            val index = _tagStats.indexOf(stats)
            if (index >= 0) {
                _tagStats.removeAt(index)
                _tagStats.add(index, stats.copy(isFiltered = false))
            }
        }
    }
    
    /** 清除所有标签过滤器 */
    fun clearTagFilters() {
        currentTagFilters.clear()
        // 重置所有标签过滤状态
        val updatedStats = _tagStats.map { it.copy(isFiltered = false) }
        _tagStats.clear()
        _tagStats.addAll(updatedStats)
    }
    
    /** 获取热门标签（出现频率最高的前N个） */
    fun getTopTags(limit: Int = 10): List<TagStats> {
        return tagStats.take(limit)
    }

    /** 停止捕获日志（异步方式） */
    fun stopLogcat() {
        // 使用协程在IO线程中同步停止
        CoroutineScope(Dispatchers.IO).launch {
            stopLogcatSync()
        }
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
            closeResources()
        }
    }

    /** 关闭资源 */
    private fun closeResources() {
        try {
            reader?.close()
            reader = null
            
            logcatProcess?.let { process ->
                try {
                    // 确保进程正确终止
                    process.inputStream?.close()
                    process.outputStream?.close()
                    process.errorStream?.close()
                    process.destroyForcibly()
                } catch (e: Exception) {
                    // 忽略关闭异常
                }
            }
            logcatProcess = null
        } catch (e: Exception) {
            // 忽略异常
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
                    val level = when (levelChar) {
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
            if (line[i] in "VDIEFWS" && i + 1 < line.length && line[i+1] == '/') {
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
        try {
            Runtime.getRuntime().exec("logcat -c")
        } catch (e: Exception) {
            // 处理异常
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