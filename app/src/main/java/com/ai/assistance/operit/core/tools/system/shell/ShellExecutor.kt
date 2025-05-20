package com.ai.assistance.operit.core.tools.system.shell

import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel

/** 通用Shell命令执行接口 定义了不同权限级别Shell操作的统一接口 */
interface ShellExecutor {
    /**
     * 执行Shell命令
     * @param command 要执行的Shell命令
     * @return 命令执行结果
     */
    suspend fun executeCommand(command: String): CommandResult

    /**
     * 获取当前执行器的权限级别
     * @return 当前执行器的权限级别
     */
    fun getPermissionLevel(): AndroidPermissionLevel

    /**
     * 检查执行器是否可用
     * @return 执行器是否可用
     */
    fun isAvailable(): Boolean

    /**
     * 请求执行器所需的权限
     * @param onResult 权限请求结果回调
     */
    fun requestPermission(onResult: (Boolean) -> Unit)

    /**
     * 检查是否已有执行器所需的权限
     * @return 权限状态，包含是否有权限及详细的错误原因
     */
    fun hasPermission(): PermissionStatus

    /** 初始化执行器 */
    fun initialize()

    /** 命令执行结果数据类 */
    data class CommandResult(
            val success: Boolean,
            val stdout: String,
            val stderr: String = "",
            val exitCode: Int = -1
    )

    /** 权限状态数据类 包含权限检查结果和失败原因描述 */
    data class PermissionStatus(
            val granted: Boolean,
            val reason: String = if (granted) "Permission granted" else "Permission denied"
    ) {
        companion object {
            fun granted() = PermissionStatus(true)
            fun denied(reason: String) = PermissionStatus(false, reason)
        }
    }
}
