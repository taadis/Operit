package com.ai.assistance.operit.tools

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.PermissionLevel
import com.ai.assistance.operit.data.ToolCategoryMapper
import com.ai.assistance.operit.data.ToolPermissionPreferences
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.ui.components.PermissionRequestResult
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
        private const val PERMISSION_REQUEST_TIMEOUT_MS = 60000L // Extend timeout to 60 seconds
    }
    
    private val toolPermissionPreferences = ToolPermissionPreferences(context)
    
    // 当前的权限请求回调
    private var currentPermissionCallback: ((PermissionRequestResult) -> Unit)? = null
    
    // 需要显示的权限请求信息
    private var permissionRequestInfo: Pair<AITool, String>? = null
    
    /**
     * 检查工具是否有执行权限
     * @param tool 要执行的工具
     * @return 权限检查结果
     */
    suspend fun checkToolPermission(tool: AITool): Boolean {
        Log.d(TAG, "Starting permission check process: ${tool.name}")
        
        // 获取全局权限开关设置
        val masterSwitch = toolPermissionPreferences.masterSwitchFlow.first()
        Log.d(TAG, "Global permission switch: $masterSwitch")
        
        // 如果全局禁止，所有工具都不允许执行
        if (masterSwitch == PermissionLevel.FORBID) {
            Log.d(TAG, "Global permission setting is forbidden, refusing to execute tool: ${tool.name}")
            return false
        }
        
        // 如果全局询问，则对所有工具进行询问
        if (masterSwitch == PermissionLevel.ASK) {
            Log.d(TAG, "Global permission setting is asking, requesting user confirmation: ${tool.name}")
            return requestPermission(tool)
        }
        
        // 获取工具类别 - 优先使用工具内置类别，如果没有则使用映射器
        val toolCategory = tool.category ?: ToolCategoryMapper.getToolCategory(tool.name)
        Log.d(TAG, "Tool category: ${tool.name} -> $toolCategory")
        
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
        Log.d(TAG, "Tool category permission level: $toolCategory -> $permissionLevel")
        
        return when (permissionLevel) {
            PermissionLevel.ALLOW -> {
                // 允许直接执行
                Log.d(TAG, "Permission setting is allowed, executing tool directly: ${tool.name}")
                true
            }
            PermissionLevel.CAUTION -> {
                // 警惕模式 - 检查是否危险操作
                val isDangerous = ToolCategoryMapper.isDangerousOperation(tool.name, tool.parameters)
                if (isDangerous) {
                    Log.d(TAG, "Dangerous operation in caution mode, requesting user confirmation: ${tool.name}")
                    requestPermission(tool)
                } else {
                    Log.d(TAG, "Safe operation in caution mode, executing tool directly: ${tool.name}")
                    true
                }
            }
            PermissionLevel.ASK -> {
                // 询问用户
                Log.d(TAG, "Permission setting is asking, requesting user confirmation: ${tool.name}")
                requestPermission(tool)
            }
            PermissionLevel.FORBID -> {
                // 禁止执行
                Log.d(TAG, "Permission setting is forbidden, refusing to execute tool: ${tool.name}")
                false
            }
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
        
        Log.d(TAG, "Starting permission request: ${tool.name}, description: $operationDescription")
        
        // Clear any existing request info first
        currentPermissionCallback = null
        permissionRequestInfo = null
        
        // 设置权限请求信息，用于UI显示
        permissionRequestInfo = Pair(tool, operationDescription)
        
        // Force a check immediately to ensure the UI detects the request
        refreshPermissionRequestState()
        
        // 添加额外日志，验证状态是否正确设置
        Log.d(TAG, "Permission request state set, current state: hasRequest=${hasActivePermissionRequest()}, info=$permissionRequestInfo")
        
        return withTimeoutOrNull(PERMISSION_REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                Log.d(TAG, "Permission request suspended, waiting for user response: ${tool.name}")
                
                // 设置回调
                currentPermissionCallback = { result ->
                    // 清空回调和请求信息
                    Log.d(TAG, "User response received, clearing permission request state")
                    currentPermissionCallback = null
                    permissionRequestInfo = null
                    
                    Log.d(TAG, "User responded to permission request: ${tool.name}, result: $result")
                    
                    // 根据用户选择继续协程
                    when (result) {
                        PermissionRequestResult.ALLOW -> continuation.resume(true)
                        PermissionRequestResult.DENY -> continuation.resume(false)
                        PermissionRequestResult.DISCONNECT -> {
                            // 断开连接 - 取消当前请求
                            if (continuation.isActive) continuation.cancel()
                            continuation.resume(false)
                        }
                    }
                }
                
                // Check state again after setting callback to ensure UI is updated
                refreshPermissionRequestState()
                
                // 请求超时时自动取消
                continuation.invokeOnCancellation {
                    Log.d(TAG, "Permission request cancelled: ${tool.name}")
                    currentPermissionCallback = null
                    permissionRequestInfo = null
                }
            }
        } ?: run {
            Log.d(TAG, "Permission request timed out for: ${tool.name}")
            // Clean up resources
            currentPermissionCallback = null
            permissionRequestInfo = null
            false // timeout default deny
        }
    }
    
    /**
     * 处理权限请求结果
     * @param result 权限请求结果
     */
    fun handlePermissionResult(result: PermissionRequestResult) {
        currentPermissionCallback?.invoke(result)
    }
    
    /**
     * 获取当前权限请求信息
     * @return 当前权限请求信息（工具和操作描述），如果没有则返回null
     */
    fun getCurrentPermissionRequest(): Pair<AITool, String>? {
        return permissionRequestInfo
    }
    
    /**
     * 检查是否有待处理的权限请求
     * @return 是否有待处理的权限请求
     */
    fun hasActivePermissionRequest(): Boolean {
        val hasRequest = permissionRequestInfo != null && currentPermissionCallback != null
        Log.d(TAG, "Checking for active permission request: $hasRequest (info=${permissionRequestInfo != null}, callback=${currentPermissionCallback != null})")
        return hasRequest
    }
    
    /**
     * 强制刷新当前的权限请求状态
     * 如果权限对话框未显示但存在有效请求，可调用此方法
     */
    fun refreshPermissionRequestState(): Boolean {
        val hasActiveRequest = hasActivePermissionRequest()
        Log.d(TAG, "Forced refresh of permission request state, active request: $hasActiveRequest")
        return hasActiveRequest
    }
} 