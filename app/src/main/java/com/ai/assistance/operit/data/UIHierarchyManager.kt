package com.ai.assistance.operit.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.ai.assistance.operit.service.UIAccessibilityService
import java.util.concurrent.atomic.AtomicBoolean

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
} 