package com.ai.assistance.operit.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ai.assistance.operit.data.PermissionLevel
import com.ai.assistance.operit.data.ToolCategoryMapper
import com.ai.assistance.operit.data.ToolPermissionPreferences
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.ui.components.PermissionRequestResult
import com.ai.assistance.operit.ui.features.permission.PermissionRequestOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 工具权限管理器，负责检查和请求工具执行权限
 */
class ToolPermissionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ToolPermissionManager"
        private const val PERMISSION_REQUEST_TIMEOUT_MS = 60000L // 60秒权限请求超时
    }
    
    private val toolPermissionPreferences = ToolPermissionPreferences(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionRequestOverlay = PermissionRequestOverlay(context)
    
    // 当前的权限请求回调
    private var currentPermissionCallback: ((PermissionRequestResult) -> Unit)? = null
    
    // 需要显示的权限请求信息
    private var permissionRequestInfo: Pair<AITool, String>? = null
    
    // 权限请求状态变化通知
    private val _permissionRequestState = MutableStateFlow<Pair<AITool, String>?>(null)
    val permissionRequestState = _permissionRequestState.asStateFlow()
    
    /**
     * 检查工具是否有执行权限
     * @param tool 要执行的工具
     * @return 权限检查结果
     */
    suspend fun checkToolPermission(tool: AITool): Boolean {
        Log.d(TAG, "Starting permission check: ${tool.name}")
        
        // 获取全局权限开关设置
        val masterSwitch = toolPermissionPreferences.masterSwitchFlow.first()
        
        // 如果全局禁止，所有工具都不允许执行
        if (masterSwitch == PermissionLevel.FORBID) {
            return false
        }
        
        // 如果全局询问，则对所有工具进行询问
        if (masterSwitch == PermissionLevel.ASK) {
            return requestPermission(tool)
        }
        
        // 获取工具类别
        val toolCategory = tool.category ?: ToolCategoryMapper.getToolCategory(tool.name)
        
        // 根据工具类别获取对应的权限级别
        val permissionLevel = when (toolCategory) {
            com.ai.assistance.operit.data.ToolCategory.SYSTEM_OPERATION -> 
                toolPermissionPreferences.systemOperationPermissionFlow.first()
            com.ai.assistance.operit.data.ToolCategory.NETWORK -> 
                toolPermissionPreferences.networkPermissionFlow.first()
            com.ai.assistance.operit.data.ToolCategory.UI_AUTOMATION -> 
                toolPermissionPreferences.uiAutomationPermissionFlow.first()
            com.ai.assistance.operit.data.ToolCategory.FILE_READ -> 
                toolPermissionPreferences.fileReadPermissionFlow.first()
            com.ai.assistance.operit.data.ToolCategory.FILE_WRITE -> 
                toolPermissionPreferences.fileWritePermissionFlow.first()
        }
        
        return when (permissionLevel) {
            PermissionLevel.ALLOW -> true
            PermissionLevel.CAUTION -> {
                val isDangerous = ToolCategoryMapper.isDangerousOperation(tool.name, tool.parameters)
                if (isDangerous) requestPermission(tool) else true
            }
            PermissionLevel.ASK -> requestPermission(tool)
            PermissionLevel.FORBID -> false
        }
    }
    
    /**
     * 请求用户确认执行权限
     * @param tool 要执行的工具
     * @return 用户是否允许执行
     */
    private suspend fun requestPermission(tool: AITool): Boolean {
        // 获取操作描述
        val operationDescription = ToolCategoryMapper.getOperationDescription(tool.name, tool.parameters)
        
        Log.d(TAG, "Requesting permission: ${tool.name}")
        
        // 清除现有请求
        currentPermissionCallback = null
        permissionRequestInfo = null
        _permissionRequestState.value = null
        
        // 设置新请求
        val requestInfo = Pair(tool, operationDescription)
        permissionRequestInfo = requestInfo
        _permissionRequestState.value = requestInfo
        
        Log.d(TAG, "Permission request state updated: ${tool.name}")
        
        return withTimeoutOrNull(PERMISSION_REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                // 设置回调
                currentPermissionCallback = { result ->
                    Log.d(TAG, "Permission result received: $result for ${tool.name}")
                    // 清理状态
                    currentPermissionCallback = null
                    permissionRequestInfo = null
                    _permissionRequestState.value = null
                    
                    // 处理结果
                    when (result) {
                        PermissionRequestResult.ALLOW -> continuation.resume(true)
                        PermissionRequestResult.DENY -> continuation.resume(false)
                        PermissionRequestResult.DISCONNECT -> {
                            if (continuation.isActive) continuation.cancel()
                            continuation.resume(false)
                        }
                    }
                }
                
                // 在主线程中启动权限请求Activity
                mainHandler.post {
                    // 使用悬浮窗显示权限请求
                    if (!permissionRequestOverlay.hasOverlayPermission()) {
                        Log.w(TAG, "No overlay permission, requesting...")
                        permissionRequestOverlay.requestOverlayPermission()
                        currentPermissionCallback?.invoke(PermissionRequestResult.DENY)
                    } else {
                        permissionRequestOverlay.show(tool, operationDescription) { result ->
                            handlePermissionResult(result)
                        }
                    }
                }
            }
        } ?: run {
            // 超时处理
            Log.d(TAG, "Permission request timed out: ${tool.name}")
            currentPermissionCallback = null
            permissionRequestInfo = null
            _permissionRequestState.value = null
            false
        }
    }
    
    /**
     * 处理权限请求结果
     */
    fun handlePermissionResult(result: PermissionRequestResult) {
        currentPermissionCallback?.invoke(result)
    }
    
    /**
     * 获取当前权限请求信息
     */
    fun getCurrentPermissionRequest(): Pair<AITool, String>? {
        return permissionRequestInfo
    }
    
    /**
     * 检查是否有待处理的权限请求
     */
    fun hasActivePermissionRequest(): Boolean {
        val hasRequest = permissionRequestInfo != null && currentPermissionCallback != null
        return hasRequest
    }
    
    /**
     * 刷新权限请求状态
     */
    fun refreshPermissionRequestState(): Boolean {
        return hasActivePermissionRequest()
    }
}