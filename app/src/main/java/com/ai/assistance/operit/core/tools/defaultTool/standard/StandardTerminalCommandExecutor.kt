package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSessionManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.*

/** 终端命令执行工具 - 非流式输出版本 执行终端命令并一次性收集全部输出后返回 */
class StandardTerminalCommandExecutor(private val context: Context) {

    private val TAG = "TerminalCommandExecutor"

    /** 执行指定的AI工具 */
    fun invoke(tool: AITool): ToolResult {
        return runBlocking {
            try {
                val command = tool.parameters.find { param -> param.name == "command" }?.value ?: ""
                val timeout =
                        tool.parameters
                                .find { param -> param.name == "timeout_ms" }
                                ?.value
                                ?.toLongOrNull()
                                ?: 30000L
                val withSessionId =
                        tool.parameters.find { param -> param.name == "session_id" }?.value

                // 获取会话
                val session =
                        if (!withSessionId.isNullOrEmpty()) {
                            TerminalSessionManager.sessions.find {
                                    s:
                                            com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSession
                                ->
                                s.id == withSessionId
                            }
                        } else {
                            // 如果没有指定会话ID，使用当前活动会话，或者创建一个新会话
                            TerminalSessionManager.getActiveSession()
                                    ?: TerminalSessionManager.createSession("命令执行会话")
                        }

                if (session == null) {
                    return@runBlocking ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "无法找到或创建终端会话"
                    )
                }

                // 收集输出
                val outputBuffer = StringBuilder()
                var exitCode = -1
                var success = false

                val result =
                        withTimeoutOrNull(timeout) {
                            suspendCoroutine { continuation ->
                                // 创建完成时的处理程序
                                val onComplete: (Int, Boolean) -> Unit = { code, isSuccess ->
                                    exitCode = code
                                    success = isSuccess
                                    continuation.resume(Unit)
                                }

                                // 使用会话管理器执行命令
                                GlobalScope.launch {
                                    TerminalSessionManager.executeSessionCommand(
                                            context = context,
                                            session = session,
                                            command = command,
                                            onOutput = { outputBuffer.appendLine(it) },
                                            onInteractivePrompt = { prompt, _ ->
                                                outputBuffer.appendLine("[交互式提示] $prompt (自动跳过)")
                                            },
                                            onComplete = onComplete
                                    )
                                }
                            }
                            Unit
                        }

                if (result == null) {
                    // 超时
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result =
                                    TerminalCommandResultData(
                                            command = command,
                                            output = outputBuffer.toString() + "\n[命令执行超时]",
                                            exitCode = -1,
                                            sessionId = session.id
                                    ),
                            error = "命令执行超时"
                    )
                } else {
                    // 成功执行
                    ToolResult(
                            toolName = tool.name,
                            success = success,
                            result =
                                    TerminalCommandResultData(
                                            command = command,
                                            output = outputBuffer.toString(),
                                            exitCode = exitCode,
                                            sessionId = session.id
                                    )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "执行终端命令时出错", e)
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "执行终端命令时出错: ${e.message}"
                )
            }
        }
    }
}
