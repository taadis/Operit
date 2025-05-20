package com.ai.assistance.operit.core.tools.system

import android.content.Context
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor.CommandResult
import com.ai.assistance.operit.core.tools.system.termux.TermuxCommandExecutionImpl
import com.ai.assistance.operit.core.tools.system.termux.TermuxCommandInteraction
import com.ai.assistance.operit.core.tools.system.termux.TermuxCommandOptions
import com.ai.assistance.operit.core.tools.system.termux.TermuxCommandOutputReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 用于执行Termux命令的工具类。 此类作为所有Termux相关命令执行功能的入口点， 具体实现被拆分到多个类中以提高可维护性。 */
class TermuxCommandExecutor {
        companion object {
                private const val TAG = "TermuxCommandExecutor"

                /**
                 * 执行Termux命令
                 * @param context 上下文
                 * @param command 要执行的命令
                 * @param autoAuthorize 如果Termux未授权，是否自动尝试授权
                 * @param background 是否在后台执行命令
                 * @param resultCallback 命令执行结果回调，如果为null则不接收结果
                 * @param outputReceiver 流式输出接收器
                 * @return 执行结果 (仅表示命令是否成功发送，不代表实际执行结果)
                 */
                suspend fun executeCommand(
                        context: Context,
                        command: String,
                        autoAuthorize: Boolean = true,
                        background: Boolean = true,
                        resultCallback: ((CommandResult) -> Unit)? = null,
                        outputReceiver: TermuxCommandOutputReceiver? = null
                ): CommandResult =
                        withContext(Dispatchers.IO) {
                                // 强制使用流式输出方法，不提供非流式选项
                                TermuxCommandExecutionImpl.executeCommandStreaming(
                                        context = context,
                                        command = command,
                                        autoAuthorize = autoAuthorize,
                                        background = background,
                                        outputReceiver = outputReceiver,
                                        resultCallback = resultCallback
                                )
                        }

                /**
                 * 执行Termux命令 (使用流式输出) 该方法会默认使用流式输出模式，替代旧的executeCommand方法
                 * @param context 上下文
                 * @param command 要执行的命令
                 * @param autoAuthorize 如果Termux未授权，是否自动尝试授权
                 * @param background 是否在后台执行命令
                 * @param outputReceiver 命令输出接收器，用于接收实时输出
                 * @param resultCallback 命令执行结果回调，在不提供outputReceiver时使用
                 * @param options 命令选项，包括超时设置和超时处理模式
                 * @return 执行结果 (仅表示命令是否成功发送，不代表实际执行结果)
                 */
                suspend fun executeCommandStreaming(
                        context: Context,
                        command: String,
                        autoAuthorize: Boolean = true,
                        background: Boolean = true,
                        outputReceiver: TermuxCommandOutputReceiver? = null,
                        resultCallback: ((CommandResult) -> Unit)? = null,
                        options: TermuxCommandOptions = TermuxCommandOptions()
                ): CommandResult =
                        withContext(Dispatchers.IO) {
                                TermuxCommandExecutionImpl.executeCommandStreaming(
                                        context = context,
                                        command = command,
                                        autoAuthorize = autoAuthorize,
                                        background = background,
                                        outputReceiver = outputReceiver,
                                        resultCallback = resultCallback,
                                        options = options
                                )
                        }

                /**
                 * 向正在运行的命令发送输入
                 * @param context 上下文
                 * @param executionId 命令的执行ID
                 * @param input 要发送的输入文本，自动添加换行符
                 * @return 是否成功发送
                 */
                suspend fun sendInputToCommand(
                        context: Context,
                        executionId: Int,
                        input: String
                ): Boolean =
                        TermuxCommandInteraction.sendInputToCommand(context, executionId, input)
        }
}
