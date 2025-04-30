package com.ai.assistance.operit.core.tools.system.termux

import com.ai.assistance.operit.tools.system.AdbCommandExecutor.CommandResult

/** 命令输出接收器接口 用于接收命令执行过程中的实时输出 */
interface TermuxCommandOutputReceiver {
    /**
     * 接收标准输出内容
     * @param output 输出内容
     * @param isComplete 是否是最后一次输出
     */
    fun onStdout(output: String, isComplete: Boolean)

    /**
     * 接收标准错误内容
     * @param error 错误内容
     * @param isComplete 是否是最后一次输出
     */
    fun onStderr(error: String, isComplete: Boolean)

    /**
     * 命令执行完成
     * @param result 最终执行结果
     */
    fun onComplete(result: CommandResult)

    /**
     * 命令执行出错
     * @param error 错误信息
     * @param exitCode 退出码
     */
    fun onError(error: String, exitCode: Int)
}

/** 命令输出接收器的默认实现，用于转换为最终结果回调 */
class DefaultCommandOutputReceiver(private val resultCallback: (CommandResult) -> Unit) :
    TermuxCommandOutputReceiver {
    private val stdoutBuilder = StringBuilder()
    private val stderrBuilder = StringBuilder()

    override fun onStdout(output: String, isComplete: Boolean) {
        stdoutBuilder.append(output)
    }

    override fun onStderr(error: String, isComplete: Boolean) {
        stderrBuilder.append(error)
    }

    override fun onComplete(result: CommandResult) {
        resultCallback(result)
    }

    override fun onError(error: String, exitCode: Int) {
        resultCallback(CommandResult(false, stdoutBuilder.toString(), error, exitCode))
    }
}
