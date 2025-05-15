package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.system.AdbCommandExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 使用高效方式监控Bridge日志文件 该实现基于增量读取，仅读取文件中新增的内容，大大减少资源占用 */
object BridgeLogMonitor {
    private const val TAG = "BridgeLogMonitor"
    private const val BRIDGE_LOG_FILE = "/data/data/com.termux/files/home/bridge/bridge.log"
    private const val OUTPUT_LOG_TAG = "BridgeJS"

    private var monitorJob: Job? = null
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private val lastPosition = AtomicLong(0)

    /** 开始监控Bridge日志文件 */
    fun startMonitoring(context: Context) {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "Bridge日志监控已在运行")
            return
        }

        monitorJob =
                monitorScope.launch {
                    Log.i(TAG, "开始高效监控Bridge日志")

                    try {
                        // 如果文件不存在，从头开始监控
                        if (!fileExists()) {
                            lastPosition.set(0)
                            Log.d(TAG, "Bridge日志文件不存在，将从创建时开始监控")
                        } else if (lastPosition.get() == 0L) {
                            // 如果是首次监控，对于大文件从末尾开始，小文件从头开始
                            val fileSize = getFileSize()
                            if (fileSize > 1024 * 10) { // 大于10KB
                                lastPosition.set(fileSize)
                                Log.d(TAG, "首次监控，从文件末尾开始: $fileSize 字节")
                            } else {
                                lastPosition.set(0)
                                Log.d(TAG, "首次监控，从文件头开始读取小文件: $fileSize 字节")
                            }
                        }

                        // 开始监控循环
                        while (isActive && isRunning.get()) {
                            try {
                                val currentFileSize = getFileSize()

                                // 如果文件大小变化，读取新增内容
                                if (currentFileSize > lastPosition.get()) {
                                    readAndLogNewContent(lastPosition.get(), currentFileSize)
                                    lastPosition.set(currentFileSize)
                                }

                                delay(300) // 300毫秒间隔，避免过于频繁的IO操作
                            } catch (e: Exception) {
                                Log.e(TAG, "监控文件时发生异常: ${e.message}", e)
                                delay(1000) // 错误后等待时间长一些
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Bridge日志监控失败: ${e.message}", e)
                    } finally {
                        Log.i(TAG, "Bridge日志监控已停止")
                    }
                }
    }

    /** 停止监控Bridge日志文件 */
    fun stopMonitoring() {
        isRunning.set(false)
        monitorJob?.cancel()
        monitorJob = null
        Log.d(TAG, "Bridge日志监控已停止")
    }

    /** 检查文件是否存在 */
    private suspend fun fileExists(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val command =
                            "run-as com.termux sh -c '[ -f \"$BRIDGE_LOG_FILE\" ] && echo \"EXISTS\" || echo \"NOT_EXISTS\"'"
                    val result = AdbCommandExecutor.executeAdbCommand(command)
                    result.stdout.trim() == "EXISTS"
                } catch (e: Exception) {
                    Log.e(TAG, "检查文件是否存在时出错: ${e.message}")
                    false
                }
            }

    /** 获取文件大小 */
    private suspend fun getFileSize(): Long =
            withContext(Dispatchers.IO) {
                try {
                    val command =
                            "run-as com.termux sh -c 'if [ -f \"$BRIDGE_LOG_FILE\" ]; then stat -c %s \"$BRIDGE_LOG_FILE\" 2>/dev/null || stat -f %z \"$BRIDGE_LOG_FILE\"; else echo \"0\"; fi'"
                    val result = AdbCommandExecutor.executeAdbCommand(command)
                    result.stdout.trim().toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    Log.e(TAG, "获取文件大小失败: ${e.message}")
                    0L
                }
            }

    /** 读取并记录新增内容 */
    private suspend fun readAndLogNewContent(startPosition: Long, endPosition: Long) =
            withContext(Dispatchers.IO) {
                if (endPosition <= startPosition) return@withContext

                val bytesToRead = endPosition - startPosition
                val readCommandBody =
                        if (bytesToRead > 10 * 1024) { // 大于10KB使用更高效的读取
                            // 读取新增部分，按块读取大文件
                            "dd if=\"$BRIDGE_LOG_FILE\" bs=1024 skip=${startPosition / 1024} count=${(bytesToRead + 1023) / 1024} 2>/dev/null | dd bs=1 skip=${startPosition % 1024} count=$bytesToRead 2>/dev/null"
                        } else {
                            // 对小块数据使用更简单的命令
                            "dd if=\"$BRIDGE_LOG_FILE\" bs=1 skip=$startPosition count=$bytesToRead 2>/dev/null"
                        }

                val readCommand = "run-as com.termux sh -c '$readCommandBody'"

                try {
                    val result = AdbCommandExecutor.executeAdbCommand(readCommand)
                    val newContent = result.stdout

                    if (newContent.isNotEmpty()) {
                        // 分行输出到日志，避免过长的日志被截断
                        newContent.split("\n").forEach { line ->
                            if (line.isNotEmpty()) {
                                Log.d(OUTPUT_LOG_TAG, line)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "读取文件新增内容时出错: ${e.message}")

                    // 尝试备用方法
                    try {
                        val fallbackCommandBody =
                                "tail -c +${startPosition + 1} \"$BRIDGE_LOG_FILE\" | head -c ${endPosition - startPosition}"
                        val fallbackCommand = "run-as com.termux sh -c '$fallbackCommandBody'"
                        val result = AdbCommandExecutor.executeAdbCommand(fallbackCommand)
                        val newContent = result.stdout

                        if (newContent.isNotEmpty()) {
                            newContent.split("\n").forEach { line ->
                                if (line.isNotEmpty()) {
                                    Log.d(OUTPUT_LOG_TAG, line)
                                }
                            }
                        }
                    } catch (fallbackE: Exception) {
                        Log.e(TAG, "备用读取方法也失败: ${fallbackE.message}")
                    }
                }
            }

    /** 清理Bridge日志文件 */
    suspend fun clearLogFile() =
            withContext(Dispatchers.IO) {
                try {
                    val clearCommand =
                            "run-as com.termux sh -c 'rm -f \"$BRIDGE_LOG_FILE\" || true'"
                    AdbCommandExecutor.executeAdbCommand(clearCommand)
                    lastPosition.set(0)
                    Log.d(TAG, "Bridge日志文件已清理")
                } catch (e: Exception) {
                    Log.e(TAG, "清理Bridge日志文件失败: ${e.message}")
                }
            }
}
