package com.ai.assistance.operit.api.enhanced.utils

import android.util.Log
import com.ai.assistance.operit.api.enhanced.models.ConversationMarkupManager
import com.ai.assistance.operit.model.ToolInvocation
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.AIToolHandler

/**
 * Utility class for managing tool executions
 */
object ToolExecutionManager {
    private const val TAG = "ToolExecutionManager"

    /**
     * Execute a tool safely, with parameter validation
     * 
     * @param invocation The tool invocation to execute
     * @param executor The tool executor to use
     * @return The result of the tool execution
     */
    fun executeToolSafely(
        invocation: ToolInvocation,
        executor: com.ai.assistance.operit.tools.ToolExecutor
    ): ToolResult {
        return try {
            val validationResult = executor.validateParameters(invocation.tool)
            if (!validationResult.valid) {
                ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = "",
                    error = "参数无效: ${validationResult.errorMessage}"
                )
            } else {
                executor.invoke(invocation.tool)
            }
        } catch (e: Exception) {
            Log.e(TAG, "工具执行错误: ${invocation.tool.name}", e)
            ToolResult(
                toolName = invocation.tool.name,
                success = false,
                result = "",
                error = "工具执行错误: ${e.message}"
            )
        }
    }
    
    /**
     * Handle a sequence tool execution
     * 
     * @param invocation The sequence tool invocation
     * @param executor The sequence tool executor
     * @param onStatusUpdate Callback function that receives status updates
     * @return The result of the sequence execution
     */
    suspend fun handleSequenceTool(
        invocation: ToolInvocation,
        executor: com.ai.assistance.operit.tools.ToolExecutor?,
        onStatusUpdate: (String) -> Unit
    ): ToolResult {
        // 提取UUID
        val uuid = invocation.tool.parameters.find { it.name == "uuid" }?.value ?: ""
        
        // 显示序列执行状态
        onStatusUpdate(ConversationMarkupManager.createExecutingSequenceStatus(uuid))
        
        // 如果执行器为空，返回错误
        if (executor == null) {
            onStatusUpdate(ConversationMarkupManager.createErrorStatus("序列执行失败", "序列执行工具不可用"))
            
            // 创建错误结果并返回
            return ToolResult(
                toolName = "execute_sequence",
                success = false,
                result = "",
                error = "序列执行工具不可用"
            )
        }
        
        // 执行序列工具
        val result = executor.invoke(invocation.tool)
        
        // 显示序列执行结果
        onStatusUpdate(ConversationMarkupManager.createSequenceResultStatus(uuid, result.result))
        
        // 返回结果
        return result
    }
    
    /**
     * Check if a tool requires permission and verify if it has permission
     * 
     * @param toolHandler The AIToolHandler instance to use for permission checks
     * @param invocation The tool invocation to check permissions for
     * @return A pair containing (has permission, error result if no permission)
     */
    suspend fun checkToolPermission(
        toolHandler: AIToolHandler,
        invocation: ToolInvocation
    ): Pair<Boolean, ToolResult?> {
        // 检查是否强制拒绝权限（deny_tool标记）
        val hasPromptForPermission = !invocation.rawText.contains("deny_tool")
        
        if (hasPromptForPermission) {
            // 检查权限，如果需要则弹出权限请求界面
            val toolPermissionSystem = toolHandler.getToolPermissionSystem()
            val hasPermission = toolPermissionSystem.checkToolPermission(invocation.tool)
            
            // 如果权限被拒绝，创建错误结果
            if (!hasPermission) {
                val errorResult = ToolResult(
                    toolName = invocation.tool.name,
                    success = false,
                    result = "",
                    error = "Permission denied: Operation was not authorized"
                )
                
                return Pair(false, errorResult)
            }
        }
        
        // 有权限
        return Pair(true, null)
    }
} 