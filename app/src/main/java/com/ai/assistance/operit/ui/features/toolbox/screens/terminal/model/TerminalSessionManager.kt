package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

private const val TAG = "TerminalSessionManager"

/** 终端会话管理器 管理多个终端会话，全局通用单例，在应用内切换界面时保持状态 */
object TerminalSessionManager {
    // 所有会话列表
    private val _sessions = mutableStateListOf<TerminalSession>()
    val sessions: SnapshotStateList<TerminalSession> = _sessions

    // 当前活动会话ID
    private val _activeSessionId = mutableStateOf<String?>(null)
    val activeSessionId = _activeSessionId

    /** 创建新会话 */
    fun createSession(name: String = "Session ${_sessions.size + 1}"): TerminalSession {
        val session = TerminalSession(name = name)
        _sessions.add(session)

        // 如果是第一个会话，自动设为活动会话
        if (_sessions.size == 1) {
            _activeSessionId.value = session.id
        }

        return session
    }

    /** 关闭会话 */
    fun closeSession(sessionId: String) {
        val sessionIndex = _sessions.indexOfFirst { it.id == sessionId }
        if (sessionIndex != -1) {
            _sessions.removeAt(sessionIndex)

            // 如果关闭的是当前活动会话，选择新的活动会话
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value =
                        if (_sessions.isNotEmpty()) {
                            // 选择前一个会话，如果没有则选择第一个会话
                            val newIndex = (sessionIndex - 1).coerceAtLeast(0)
                            _sessions[newIndex].id
                        } else {
                            null
                        }
            }
        }
    }

    /** 切换活动会话 */
    fun switchSession(sessionId: String) {
        if (_sessions.any { it.id == sessionId }) {
            _activeSessionId.value = sessionId
        }
    }

    /** 获取活动会话 */
    fun getActiveSession(): TerminalSession? {
        return _activeSessionId.value?.let { id -> _sessions.find { it.id == id } }
    }

    /** 重命名会话 */
    fun renameSession(sessionId: String, newName: String) {
        val session = _sessions.find { it.id == sessionId }
        session?.let {
            val index = _sessions.indexOf(it)
            if (index != -1) {
                // 直接修改会话名称
                it.name = newName
            }
        }
    }

    /** 获取会话数量 */
    fun getSessionCount(): Int {
        return _sessions.size
    }

    /** 检查会话是否支持流式输出 在当前实现中，所有会话都支持流式输出 */
    fun isStreamingSupported(): Boolean {
        return true
    }

    /**
     * 使用会话执行命令的通用函数
     * @param context 上下文
     * @param session 终端会话
     * @param command 要执行的命令
     * @param onOutput 输出回调
     * @param onInteractivePrompt 交互式提示回调
     * @param onComplete 完成回调，提供退出码和成功状态
     */
    suspend fun executeSessionCommand(
            context: Context,
            session: TerminalSession,
            command: String,
            onOutput: (String) -> Unit,
            onInteractivePrompt: (String, Int) -> Unit,
            onComplete: ((exitCode: Int, success: Boolean) -> Unit)? = null
    ) {
        var executionId = -1
        var commandCompleted = false
        var exitCode = 0
        var isSuccess = true
        val completionLock = Mutex(locked = true)

        // 添加命令到会话历史
        session.commandHistory.add(TerminalLine.Input(command, session.getPrompt()))

        // 记录命令开始执行时间用于超时检测
        val startTime = System.currentTimeMillis()

        // 创建一个可由多个来源触发解锁的函数
        fun signalCompletion(code: Int, success: Boolean, source: String) {
            if (!commandCompleted) {
                commandCompleted = true
                exitCode = code
                isSuccess = success
                Log.d(TAG, "命令完成信号 (来源:$source) - 退出码=$code, 成功=$success")

                try {
                    completionLock.unlock()
                } catch (e: IllegalStateException) {
                    // 互斥锁可能已经被解锁，忽略异常
                    Log.w(TAG, "尝试解锁已解锁的互斥锁: ${e.message}")
                }
            }
        }

        // 跟踪捕获到的交互式提示
        val outputFlow =
                session.executeCommand(
                        context = context,
                        command = command,
                        scope = CoroutineScope(Dispatchers.IO),
                        onCompletion = { code, success ->
                            // 将命令完成状态传递给调用者
                            Log.d(TAG, "命令正式完成回调: 退出码=$code, 成功=$success")
                            signalCompletion(code, success, "onCompletion回调")
                        }
                )

        // 创建一个协程来收集输出
        val collectJob =
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        var noOutputTime = startTime

                        outputFlow.collect { output ->
                            // 每收到输出，重置无输出计时器
                            noOutputTime = System.currentTimeMillis()

                            withContext(Dispatchers.Main) {
                                when {
                                    // 检查是否为执行ID信息
                                    output.text.contains("EXECUTION_ID:") -> {
                                        val idMatch =
                                                "EXECUTION_ID:(\\d+)".toRegex().find(output.text)
                                        idMatch?.groupValues?.get(1)?.toIntOrNull()?.let { id ->
                                            executionId = id
                                            Log.d(TAG, "捕获命令执行ID: $executionId")
                                        }
                                    }
                                    // 检查输出中的完成标记
                                    output.text.contains("COMMAND_COMPLETE:") -> {
                                        val codeMatch =
                                                "COMMAND_COMPLETE:(\\d+)"
                                                        .toRegex()
                                                        .find(output.text)
                                        codeMatch?.groupValues?.get(1)?.toIntOrNull()?.let { code ->
                                            signalCompletion(code, code == 0, "COMMAND_COMPLETE标记")
                                        }
                                    }
                                    // 检查是否为交互式提示
                                    output.text.startsWith("INTERACTIVE_PROMPT:") -> {
                                        val promptText =
                                                output.text
                                                        .substringAfter("INTERACTIVE_PROMPT:")
                                                        .trim()
                                        Log.d(TAG, "捕获交互式提示: $promptText")

                                        if (executionId != -1) {
                                            onInteractivePrompt(promptText, executionId)
                                        } else {
                                            onOutput("[无法处理交互式提示] $promptText")
                                        }
                                    }
                                    // 过滤控制输出
                                    output.text.contains("COMMAND_START") ||
                                            output.text.contains("COMMAND_END") ||
                                            output.text.contains("COMMAND_EXIT_CODE:") ||
                                            output.text.startsWith("STARTED:") -> {
                                        // 这些是控制输出，不显示给用户
                                        Log.d(TAG, "过滤控制输出: ${output.text}")

                                        // 检查是否包含完成标记
                                        when {
                                            output.text.contains("COMMAND_COMPLETE:") -> {
                                                val codeMatch =
                                                        "COMMAND_COMPLETE:(\\d+)"
                                                                .toRegex()
                                                                .find(output.text)
                                                codeMatch?.let {
                                                    val code = it.groupValues[1].toIntOrNull() ?: 0
                                                    signalCompletion(
                                                            code,
                                                            code == 0,
                                                            "COMMAND_COMPLETE标记"
                                                    )
                                                }
                                            }
                                            // 退出码信息也可以作为备选完成信号
                                            output.text.contains("COMMAND_EXIT_CODE:") -> {
                                                val codeMatch =
                                                        "COMMAND_EXIT_CODE:(\\d+)"
                                                                .toRegex()
                                                                .find(output.text)
                                                codeMatch?.let {
                                                    val code = it.groupValues[1].toIntOrNull() ?: 0
                                                    signalCompletion(code, code == 0, "EXIT_CODE标记")
                                                }
                                            }
                                            // 确保when是完整的
                                            else -> {
                                                // 其他控制输出，仅记录不处理
                                                Log.d(TAG, "其他控制输出: ${output.text}")
                                            }
                                        }
                                    }
                                    else -> {
                                        // 普通输出
                                        onOutput(output.text)
                                    }
                                }
                            }

                            // 检查超时 - 如果已经很久没收到任何输出，可能命令已经完成但没有触发完成回调
                            val currentTime = System.currentTimeMillis()
                            if (!commandCompleted && (currentTime - noOutputTime > 10000)
                            ) { // 10秒无输出视为完成
                                signalCompletion(0, true, "输出流无活动超时")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "收集命令输出时出错: ${e.message}")
                        signalCompletion(-1, false, "输出流收集异常")
                    }
                }

        // 启动一个超时监控协程
        val timeoutJob =
                CoroutineScope(Dispatchers.IO).launch {
                    // 总超时时间2分钟
                    delay(120000)
                    if (!commandCompleted) {
                        Log.w(TAG, "命令执行总超时: $command")
                        signalCompletion(-1, false, "命令总执行超时")
                    }
                }

        try {
            // 等待命令完成信号
            completionLock.lock()
            Log.d(TAG, "命令执行完成，退出等待: $command")
        } catch (e: Exception) {
            Log.e(TAG, "等待命令完成时出错: ${e.message}")
        } finally {
            // 确保清理所有协程
            timeoutJob.cancel()
            // 给收集作业一点时间处理最终输出
            delay(500)
            collectJob.cancel()

            // 确保在主线程上调用完成回调
            withContext(Dispatchers.Main) { onComplete?.invoke(exitCode, isSuccess) }
        }
    }
}
