package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.system.shell.RootShellExecutor
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Root权限授权管理器 提供检查设备是否root、请求root权限等功能 */
object RootAuthorizer {
    private const val TAG = "RootAuthorizer"

    // Root执行器实例
    private var rootShellExecutor: RootShellExecutor? = null

    // 状态监听器列表
    private val stateChangeListeners = CopyOnWriteArrayList<() -> Unit>()

    // Root状态流
    private val _isRooted = MutableStateFlow(false)
    val isRooted: StateFlow<Boolean> = _isRooted.asStateFlow()

    // Root访问权限状态流
    private val _hasRootAccess = MutableStateFlow(false)
    val hasRootAccess: StateFlow<Boolean> = _hasRootAccess.asStateFlow()

    /** 初始化Root授权器 */
    fun initialize(context: Context) {
        // 初始化Root执行器
        rootShellExecutor = RootShellExecutor(context)
        rootShellExecutor?.initialize()

        // 检查Root状态
        checkRootStatus(context)
    }

    /**
     * 检查设备是否已Root以及应用是否有Root访问权限
     * @param context 应用上下文
     * @return 是否已获取Root权限
     */
    fun checkRootStatus(context: Context): Boolean {
        val executor = rootShellExecutor ?: RootShellExecutor(context)
        rootShellExecutor = executor

        // 检查设备是否已Root
        val deviceRooted = isDeviceRooted()
        _isRooted.value = deviceRooted

        // 检查应用是否有Root访问权限
        val hasAccess = executor.isAvailable()
        _hasRootAccess.value = hasAccess

        Log.d(TAG, "设备是否已Root: $deviceRooted, 应用是否有Root访问权限: $hasAccess")

        // 通知状态变更
        notifyStateChanged()

        return hasAccess
    }

    /**
     * 判断设备是否已Root（不一定意味着应用有Root权限）
     * @return 设备是否已Root
     */
    fun isDeviceRooted(): Boolean {
        // 直接使用RootShellExecutor的isAvailable方法，但是需要区分两种情况：
        // 1. 设备已Root但应用可能没有Root权限
        // 2. 设备已Root且应用有Root权限

        if (rootShellExecutor?.isAvailable() == true) {
            return true
        }

        // 如果应用没有Root权限，使用简单检查判断设备是否已Root
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 请求Root权限（尝试获取su权限） 注意：这将触发Root管理应用的权限授予弹窗（如Magisk、SuperSU等）
     * @param onResult 请求结果回调
     */
    fun requestRootPermission(onResult: (Boolean) -> Unit) {
        Log.d(TAG, "正在请求Root权限...")

        val executor = rootShellExecutor ?: return onResult(false)

        executor.requestPermission { granted ->
            // 更新状态并通知监听器
            _hasRootAccess.value = granted
            notifyStateChanged()

            Log.d(TAG, "Root权限请求结果: ${if (granted) "已授予" else "已拒绝"}")
            onResult(granted)
        }
    }

    /**
     * 执行Root命令
     * @param command 要执行的命令
     * @return 命令执行结果
     */
    suspend fun executeRootCommand(command: String): Pair<Boolean, String> {
        Log.d(TAG, "执行Root命令: $command")

        val executor = rootShellExecutor ?: return Pair(false, "Root执行器未初始化")

        val result = executor.executeCommand(command)
        return Pair(result.success, if (result.success) result.stdout else result.stderr)
    }

    /**
     * 添加状态变更监听器
     * @param listener 状态变更监听器
     */
    fun addStateChangeListener(listener: () -> Unit) {
        stateChangeListeners.add(listener)
    }

    /**
     * 移除状态变更监听器
     * @param listener 状态变更监听器
     */
    fun removeStateChangeListener(listener: () -> Unit) {
        stateChangeListeners.remove(listener)
    }

    /** 通知所有监听器状态已变更 */
    private fun notifyStateChanged() {
        stateChangeListeners.forEach { it.invoke() }
    }
}
