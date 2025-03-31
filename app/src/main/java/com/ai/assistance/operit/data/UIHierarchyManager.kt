package com.ai.assistance.operit.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.ai.assistance.operit.service.UIAccessibilityService
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

/**
 * UI层次结构管理器
 * 负责管理UI层次结构的获取、缓存和服务状态
 */
object UIHierarchyManager {
    private const val TAG = "UIHierarchyManager"
    
    // 服务运行状态
    private val isServiceRunning = AtomicBoolean(false)
    
    // 最后获取的UI层次结构及其时间戳
    private var lastUiHierarchy: String = ""
    private var lastUiTimestamp: Long = 0
    
    // 缓存有效期（毫秒）
    private const val CACHE_TTL = 500L
    
    /**
     * 检查无障碍服务是否已启用
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceString = "${context.packageName}/${UIAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, 0
        ) == 1
        
        return accessibilityEnabled && enabledServices.contains(serviceString)
    }
    
    /**
     * 打开无障碍服务设置页面
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    /**
     * 设置无障碍服务运行状态
     */
    fun setAccessibilityServiceRunning(running: Boolean) {
        isServiceRunning.set(running)
        Log.d(TAG, "无障碍服务状态更新: $running")
    }
    
    /**
     * 获取UI层次结构（优先使用无障碍服务，失败时返回空字符串）
     */
    fun getUIHierarchy(context: Context, forceFresh: Boolean = false): String {
        // 检查缓存是否可用
        val now = System.currentTimeMillis()
        if (!forceFresh && now - lastUiTimestamp < CACHE_TTL && lastUiHierarchy.isNotEmpty()) {
            Log.d(TAG, "使用缓存的UI层次结构")
            return lastUiHierarchy
        }
        
        // 尝试从无障碍服务获取
        if (isServiceRunning.get()) {
            val service = UIAccessibilityService.getInstance()
            if (service != null) {
                try {
                    val hierarchy = service.getUIHierarchy()
                    if (hierarchy.isNotEmpty()) {
                        // 更新缓存
                        lastUiHierarchy = hierarchy
                        lastUiTimestamp = now
                        return hierarchy
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "从无障碍服务获取UI层次结构失败", e)
                }
            }
        }
        
        // 如果无障碍服务不可用或获取失败，返回空字符串
        return ""
    }
    
    /**
     * 清除UI层次结构缓存
     */
    fun clearCache() {
        lastUiHierarchy = ""
        lastUiTimestamp = 0
        Log.d(TAG, "UI层次结构缓存已清除")
    }

    /**
     * 通过ADB/Shizuku启用无障碍服务
     * @param context Android上下文
     * @return 操作是否成功
     */
    suspend fun enableAccessibilityServiceViaAdb(context: Context): Boolean {
        try {
            // 检查Shizuku服务是否正常运行并有权限
            if (!com.ai.assistance.operit.AdbCommandExecutor.isShizukuServiceRunning() || 
                !com.ai.assistance.operit.AdbCommandExecutor.hasShizukuPermission()) {
                Log.d(TAG, "Shizuku服务未运行或无权限，无法启用无障碍服务")
                return false
            }
            
            val serviceString = "${context.packageName}/${UIAccessibilityService::class.java.canonicalName}"
            
            // 获取当前已启用的服务列表
            val currentServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            // 检查服务是否已经在列表中
            if (currentServices.contains(serviceString)) {
                Log.d(TAG, "无障碍服务已在启用列表中")
                
                // 确保无障碍总开关已启用
                if (Settings.Secure.getInt(
                        context.contentResolver,
                        Settings.Secure.ACCESSIBILITY_ENABLED, 0
                    ) != 1) {
                    // 启用无障碍总开关
                    return executeAdbCommand("settings put secure accessibility_enabled 1")
                }
                
                return true
            }
            
            // 构建新的服务列表
            val newServices = if (currentServices.isEmpty()) {
                serviceString
            } else {
                "$currentServices:$serviceString"
            }
            
            // 通过ADB命令启用服务
            val result1 = executeAdbCommand("settings put secure enabled_accessibility_services $newServices")
            val result2 = executeAdbCommand("settings put secure accessibility_enabled 1")
            
            Log.d(TAG, "启用无障碍服务的ADB命令执行结果: $result1, $result2")
            
            // 短暂延迟，等待设置生效
            kotlinx.coroutines.delay(500)
            
            // 验证设置是否生效
            return isAccessibilityServiceEnabled(context)
        } catch (e: Exception) {
            Log.e(TAG, "通过ADB启用无障碍服务出错", e)
            return false
        }
    }

    /**
     * 执行ADB命令
     */
    private suspend fun executeAdbCommand(command: String): Boolean {
        return try {
            val result = com.ai.assistance.operit.AdbCommandExecutor.executeAdbCommand(command)
            Log.d(TAG, "执行命令结果: ${result.stdout}")
            !result.stdout.contains("error", ignoreCase = true) && 
            !result.stdout.contains("Exception", ignoreCase = true) &&
            result.exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "执行命令出错", e)
            false
        }
    }
} 