package com.ai.assistance.operit.core.tools.system.termux

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer

/** Termux工具类 提供Termux相关的通用工具方法 */
object TermuxUtils {
    private const val TAG = "TermuxUtils"
    private const val TERMUX_PACKAGE_NAME = "com.termux"

    /**
     * 检查应用是否在前台运行
     * @param context 上下文
     * @return 是否在前台运行
     */
    fun isAppInForeground(context: Context): Boolean {
        val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName

        for (appProcess in appProcesses) {
            if (appProcess.importance ==
                            android.app.ActivityManager.RunningAppProcessInfo
                                    .IMPORTANCE_FOREGROUND && appProcess.processName == packageName
            ) {
                return true
            }
        }
        return false
    }

    /**
     * 检查Termux是否在运行
     * @param context 上下文
     * @return 是否在运行
     */
    fun isTermuxRunning(context: Context): Boolean {
        try {
                val psResult = kotlinx.coroutines.runBlocking {
                    AndroidShellExecutor.executeShellCommand("ps -ef | grep com.termux | grep -v grep")
                }
                
                if (psResult.success && psResult.stdout.isNotEmpty() && psResult.stdout.contains("com.termux")) {
                    Log.d(TAG, "通过ps命令检测到Termux进程运行中")
                    return true
                }
                
                // 如果ps命令没有找到，再尝试检查Termux服务
                val serviceCheckResult = kotlinx.coroutines.runBlocking {
                    AndroidShellExecutor.executeShellCommand(
                        "dumpsys activity services | grep -A2 'app.TermuxService'"
                    )
                }
                
                if (serviceCheckResult.success && serviceCheckResult.stdout.contains("app.TermuxService")) {
                    Log.d(TAG, "通过dumpsys检测到TermuxService正在运行")
                    return true
                }
            
            
            // 作为备选方案，检查ActivityManager中的进程
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningProcesses = activityManager.runningAppProcesses ?: return false
            
            // 只有当进程重要性是前台或可见时才认为它在运行
            val termuxProcess = runningProcesses.find { 
                it.processName == TERMUX_PACKAGE_NAME && 
                it.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE 
            }
            
            return termuxProcess != null
        } catch (e: Exception) {
            Log.e(TAG, "检测Termux运行状态时出错: ${e.message}")
            return false
        }
    }

    /**
     * 获取Termux数据目录路径
     * @return Termux数据目录路径
     */
    fun getTermuxDataDir(): String {
        return "/data/data/com.termux/files"
    }

    /**
     * 获取Termux主目录路径
     * @return Termux主目录路径
     */
    fun getTermuxHomeDir(): String {
        return "${getTermuxDataDir()}/home"
    }

    /**
     * 获取Termux二进制目录路径
     * @return Termux二进制目录路径
     */
    fun getTermuxBinDir(): String {
        return "${getTermuxDataDir()}/usr/bin"
    }

    /**
     * 构建Termux运行目录中的临时文件路径
     * @param prefix 前缀
     * @param suffix 后缀
     * @return 临时文件路径
     */
    fun buildTermuxTempFilePath(prefix: String, suffix: String): String {
        return "${getTermuxHomeDir()}/$prefix$suffix"
    }

    /**
     * 构建FIFO管道路径
     * @param executionId 执行ID
     * @return FIFO路径
     */
    fun buildFifoPath(executionId: Int): String {
        return "${getTermuxHomeDir()}/.termux_input_${executionId}.fifo"
    }

    /**
     * 构建输出文件路径
     * @param executionId 执行ID
     * @return 输出文件路径
     */
    fun buildOutputPath(executionId: Int): String {
        return "${getTermuxHomeDir()}/.termux_output_${executionId}.log"
    }
}
