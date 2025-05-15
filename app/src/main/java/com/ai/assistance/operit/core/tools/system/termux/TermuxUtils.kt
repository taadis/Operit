package com.ai.assistance.operit.core.tools.system.termux

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.system.AdbCommandExecutor

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
        // 首先尝试使用Shizuku/ADB的更高权限检测Termux进程
        try {
            // 检查Shizuku服务是否可用
            if (AdbCommandExecutor.isShizukuServiceRunning() &&
                AdbCommandExecutor.hasShizukuPermission()) {

                // 使用更精确的dumpsys命令检查Termux是否真正运行中（非后台或已停止）
                val dumpsysResult = kotlinx.coroutines.runBlocking {
                    // 检查Termux是否在Recent任务中且状态为RESUMED或PAUSED
                    AdbCommandExecutor.executeAdbCommand(
                        "dumpsys activity recents | grep -E 'Recent #[0-9]+.*com.termux'"
                    )
                }

                if (dumpsysResult.success && dumpsysResult.stdout.isNotEmpty()) {
                    // 找到了Recent任务中的Termux，再检查是否是RESUMED或PAUSED状态
                    val activityStateResult = kotlinx.coroutines.runBlocking {
                        AdbCommandExecutor.executeAdbCommand(
                            "dumpsys activity activities | grep -A3 'com.termux/.app.TermuxActivity' | grep -E 'RESUMED|PAUSED'"
                        )
                    }
                    
                    if (activityStateResult.success && activityStateResult.stdout.isNotEmpty()) {
                        Log.d("TermuxUtils", "通过activity状态检测到Termux正在运行: ${activityStateResult.stdout.trim()}")
                        return true
                    }
                }
                
                // 作为备选方法，检查Termux是否有可见窗口
                val windowCheckResult = kotlinx.coroutines.runBlocking {
                    AdbCommandExecutor.executeAdbCommand(
                        "dumpsys window windows | grep -E 'Window #[0-9]+ Window\\{.*com.termux/com.termux.app.TermuxActivity'"
                    )
                }
                
                if (windowCheckResult.success && windowCheckResult.stdout.isNotEmpty()) {
                    Log.d("TermuxUtils", "通过window检测到Termux有活动窗口")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("TermuxUtils", "使用ADB检测Termux运行状态失败: ${e.message}")
            // 如果使用Shizuku/ADB方式失败，回退到使用ActivityManager
        }

        // 回退到ActivityManager检测，但更精确地检查是否在前台或可见
        Log.d("TermuxUtils", "回退到ActivityManager检测Termux运行状态")
        val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val processes = activityManager.runningAppProcesses ?: return false

        // 仅检查前台或可见的Termux进程
        for (process in processes) {
            if (process.processName == TERMUX_PACKAGE_NAME && 
                (process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                 process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE)) {
                
                Log.d("TermuxUtils", "通过ActivityManager检测到Termux正在前台或可见状态")
                return true
            }
        }
        
        return false
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
