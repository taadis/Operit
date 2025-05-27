package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.system.shell.RootShellExecutor
import com.topjohnwu.superuser.Shell
import java.io.File
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
    
    // 静态初始化libsu
    init {
        // 确保libsu全局设置已配置
        try {
            // 配置Shell
            Shell.enableVerboseLogging = true
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
            )
            Log.d(TAG, "libsu Shell全局配置已初始化")
        } catch (e: Exception) {
            Log.e(TAG, "libsu Shell全局配置初始化失败", e)
        }
    }

    /** 初始化Root授权器 */
    fun initialize(context: Context) {
        try {
            Log.d(TAG, "初始化RootAuthorizer...")
            
            // 初始化Root执行器
            if (rootShellExecutor == null) {
                rootShellExecutor = RootShellExecutor(context)
            }
            rootShellExecutor?.initialize()

            // 检查Root状态
            checkRootStatus(context)
            
            Log.d(TAG, "RootAuthorizer初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "RootAuthorizer初始化失败", e)
            // 设置默认状态
            _isRooted.value = false
            _hasRootAccess.value = false
        }
    }

    /**
     * 检查设备是否已Root以及应用是否有Root访问权限
     * @param context 应用上下文
     * @return 是否已获取Root权限
     */
    fun checkRootStatus(context: Context): Boolean {
        try {
            Log.d(TAG, "检查Root状态...")
            
            // 确保Root执行器已初始化
            if (rootShellExecutor == null) {
                rootShellExecutor = RootShellExecutor(context)
                rootShellExecutor?.initialize()
            }
            
            // 检查设备是否已Root（基于文件系统检查，不依赖于Shell访问）
            val deviceRooted = isDeviceRooted()
            _isRooted.value = deviceRooted
            Log.d(TAG, "设备Root状态: $deviceRooted")

            // 如果设备没有Root，则应用肯定没有Root访问权限
            if (!deviceRooted) {
                _hasRootAccess.value = false
                notifyStateChanged()
                return false
            }

            // 检查应用是否有Root访问权限
            val hasAccess = rootShellExecutor?.isAvailable() ?: false
            _hasRootAccess.value = hasAccess
            Log.d(TAG, "应用Root访问权限: $hasAccess")

            // 通知状态变更
            notifyStateChanged()

            return hasAccess
        } catch (e: Exception) {
            Log.e(TAG, "检查Root状态时出错", e)
            _isRooted.value = false
            _hasRootAccess.value = false
            notifyStateChanged()
            return false
        }
    }

    /**
     * 判断设备是否已Root（不一定意味着应用有Root权限）
     * 使用多种方法检测设备是否Root
     * @return 设备是否已Root
     */
    fun isDeviceRooted(): Boolean {
        try {
            Log.d(TAG, "检查设备是否已Root...")
            
            // 方法1: 使用libsu检测
            try {
                val isRoot = Shell.isAppGrantedRoot() ?: false
                if (isRoot) {
                    Log.d(TAG, "libsu检测到设备已Root并授予应用权限")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "libsu检测Root失败: ${e.message}")
            }
            
            // 方法2: 检查常见的su路径
            val suPaths = arrayOf(
                "/system/bin/su", 
                "/system/xbin/su", 
                "/sbin/su", 
                "/system/app/Superuser.apk", 
                "/system/app/SuperSU.apk"
            )
            
            for (path in suPaths) {
                if (File(path).exists()) {
                    Log.d(TAG, "发现su文件: $path")
                    return true
                }
            }
            
            // 方法3: 检查是否可以执行su命令
            try {
                val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    Log.d(TAG, "su命令可用，设备已Root")
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "检查su命令失败: ${e.message}")
            }
            
            // 如果所有方法都失败，则认为设备未Root
            Log.d(TAG, "设备未检测到Root")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "检查设备Root状态时出错", e)
            return false
        }
    }

    /**
     * 请求Root权限（尝试获取su权限）
     * 注意：这将触发Root管理应用的权限授予弹窗（如Magisk、SuperSU等）
     * @param onResult 请求结果回调
     */
    fun requestRootPermission(onResult: (Boolean) -> Unit) {
        try {
            Log.d(TAG, "正在请求Root权限...")

            // 使用libsu直接请求root权限
            Shell.getShell { shell ->
                val granted = shell.isRoot
                Log.d(TAG, "Root权限请求结果: ${if (granted) "已授予" else "已拒绝"}")
                
                // 更新状态
                _hasRootAccess.value = granted
                if (granted) {
                    _isRooted.value = true
                }
                
                // 通知状态变更
                notifyStateChanged()
                
                // 回调结果
                onResult(granted)
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求Root权限时出错", e)
            onResult(false)
        }
    }

    /**
     * 执行Root命令
     * @param command 要执行的命令
     * @return 命令执行结果
     */
    suspend fun executeRootCommand(command: String): Pair<Boolean, String> {
        try {
            Log.d(TAG, "执行Root命令: $command")

            // 检查Root执行器是否可用
            if (rootShellExecutor == null || !_hasRootAccess.value) {
                return Pair(false, "Root执行器未初始化或无Root权限")
            }

            // 使用Root执行器执行命令
            val result = rootShellExecutor!!.executeCommand(command)
            return Pair(result.success, if (result.success) result.stdout else result.stderr)
        } catch (e: Exception) {
            Log.e(TAG, "执行Root命令时出错", e)
            return Pair(false, "执行出错: ${e.message}")
        }
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
