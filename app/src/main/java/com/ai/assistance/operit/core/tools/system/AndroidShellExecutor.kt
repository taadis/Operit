package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.system.shell.ShellExecutorFactory
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences

/** 向后兼容的Shell命令执行工具类 通过权限级别委托到相应的Shell执行器 */
class AndroidShellExecutor {
    companion object {
        private const val TAG = "AndroidShellExecutor"
        private var context: Context? = null

        /**
         * 设置全局上下文引用
         * @param appContext 应用上下文
         */
        fun setContext(appContext: Context) {
            context = appContext.applicationContext
        }

        /**
         * 设置首选的权限级别
         * @param level 要设置的权限级别
         */
        suspend fun setPreferredPermissionLevel(level: AndroidPermissionLevel) {
            androidPermissionPreferences.savePreferredPermissionLevel(level)
            // 清除执行器缓存，确保下次使用新的权限级别
            ShellExecutorFactory.clearCache()
        }

        /**
         * 获取当前首选的权限级别
         * @return 当前配置的首选权限级别
         */
        fun getPreferredPermissionLevel(): AndroidPermissionLevel {
            return androidPermissionPreferences.getPreferredPermissionLevel()
        }

        /**
         * 封装执行ADB命令的函数
         * @param command 要执行的ADB命令
         * @return 命令执行结果
         */
        suspend fun executeAdbCommand(command: String): CommandResult {
            val ctx = context ?: return CommandResult(false, "", "Context not initialized")

            // 首先尝试使用用户首选的权限级别执行命令
            val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel()
            Log.d(TAG, "Using preferred permission level: $preferredLevel")

            val preferredExecutor = ShellExecutorFactory.getExecutor(ctx, preferredLevel)
            val permStatus = preferredExecutor.hasPermission()

            // 尝试使用首选权限级别执行命令
            if (preferredExecutor.isAvailable() && permStatus.granted) {
                // Log.d(TAG, "Executing command with preferred permission level: $preferredLevel")
                val result = preferredExecutor.executeCommand(command)
                return CommandResult(result.success, result.stdout, result.stderr, result.exitCode)
            }

                        Log.d(
                                TAG,
                    "Preferred executor not available (${permStatus.reason}), trying highest available executor"
            )

            // 如果首选执行器不可用，尝试获取最高可用级别的执行器
            val (executor, executorStatus) = ShellExecutorFactory.getHighestAvailableExecutor(ctx)

            if (!executorStatus.granted) {
                return CommandResult(
                                                false,
                                                "",
                        "No suitable shell executor available: ${executorStatus.reason}",
                        -1
                )
            }

            Log.d(TAG, "Using executor with permission level: ${executor.getPermissionLevel()}")

            val result = executor.executeCommand(command)
            return CommandResult(result.success, result.stdout, result.stderr, result.exitCode)
        }

        /**
         * 获取所有可用的执行器及其权限状态 这对于调试和显示给用户选择可用的执行方式很有用
         * @return 权限级别到执行器可用状态的映射
         */
        fun getAvailableShellMethods(): Map<AndroidPermissionLevel, Pair<Boolean, String>> {
            val ctx = context ?: return emptyMap()

            val availableExecutors = ShellExecutorFactory.getAvailableExecutors(ctx)
            return availableExecutors.mapValues { (_, pair) ->
                val (_, status) = pair
                Pair(status.granted, status.reason)
            }
        }
    }

    /** 命令执行结果数据类 */
    data class CommandResult(
            val success: Boolean,
            val stdout: String,
            val stderr: String = "",
            val exitCode: Int = -1
    )
}
