package com.ai.assistance.operit.core.tools.system.termux

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.system.AdbCommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Termux命令交互工具类 处理与正在运行的Termux命令的交互，如发送输入 */
object TermuxCommandInteraction {
    private const val TAG = "TermuxCmdInteraction"

    // 正在运行的命令的临时文件映射
    private val activeCommandFiles = mutableMapOf<Int, String>()
    // 正在等待用户输入的命令
    private val waitingForInput =
            mutableMapOf<Int, kotlinx.coroutines.CompletableDeferred<String>>()

    /**
     * 向正在运行的命令发送输入
     * @param context 上下文
     * @param executionId 命令的执行ID
     * @param input 要发送的输入文本，自动添加换行符
     * @return 是否成功发送
     */
    suspend fun sendInputToCommand(context: Context, executionId: Int, input: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    // 清理输入文本，删除可能导致问题的多余空白字符
                    val cleanInput = input.trim()
                    Log.d(TAG, "向命令 $executionId 发送输入: '$cleanInput'")

                    // 通过命名管道(FIFO)发送输入
                    val inputWithNewline =
                            if (cleanInput.endsWith("\n")) cleanInput else "$cleanInput\n"
                    val fifoPath =
                            "/data/data/com.termux/files/home/.termux_input_${executionId}.fifo"

                    // 检查FIFO是否存在
                    val checkFifoCmd =
                            "run-as com.termux sh -c 'if [ -p \"$fifoPath\" ]; then echo \"EXISTS\"; else echo \"NOT_EXISTS\"; fi'"
                    val checkFifoResult = AdbCommandExecutor.executeAdbCommand(checkFifoCmd)

                    if (checkFifoResult.stdout.trim() == "EXISTS") {
                        Log.d(TAG, "FIFO存在，写入数据: '$inputWithNewline'")

                        // 使用多种方法向FIFO写入数据以提高成功率

                        // 方法1: 使用echo直接写入
                        val echoCmd =
                                "run-as com.termux sh -c 'echo \"$inputWithNewline\" > \"$fifoPath\"'"
                        AdbCommandExecutor.executeAdbCommand(echoCmd)

                        // 方法2: 使用printf写入原始数据(避免echo对特殊字符的处理)
                        val printfCmd =
                                "run-as com.termux sh -c 'printf \"$inputWithNewline\" > \"$fifoPath\"'"
                        AdbCommandExecutor.executeAdbCommand(printfCmd)

                        // 方法3: 字符一个一个地写入
                        val chars = inputWithNewline.toCharArray()
                        for (c in chars) {
                            val charCmd = "run-as com.termux sh -c 'printf \"$c\" > \"$fifoPath\"'"
                            AdbCommandExecutor.executeAdbCommand(charCmd)
                            delay(5) // 短暂延迟，避免写入太快
                        }

                        Log.d(TAG, "输入数据发送完成")
                        return@withContext true
                    } else {
                        Log.w(TAG, "FIFO不存在，无法发送输入")
                        return@withContext false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "发送输入时出错: ${e.message}", e)
                    return@withContext false
                }
            }

    /**
     * 注册命令文件路径
     * @param executionId 命令执行ID
     * @param filePath 输出文件路径
     */
    fun registerCommandFile(executionId: Int, filePath: String) {
        activeCommandFiles[executionId] = filePath
    }

    /**
     * 取消注册命令文件路径
     * @param executionId 命令执行ID
     */
    fun unregisterCommandFile(executionId: Int) {
        activeCommandFiles.remove(executionId)
    }
}
