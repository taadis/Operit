package com.ai.assistance.operit.api.enhanced.utils

import android.util.Log
import com.ai.assistance.operit.api.enhanced.models.ConversationMarkupManager
import com.ai.assistance.operit.model.ToolInvocation
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.StringResultData
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
                    result = StringResultData(""),
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
                result = StringResultData(""),
                error = "工具执行错误: ${e.message}"
            )
        }
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
                    result = StringResultData(""),
                    error = "Permission denied: Operation was not authorized"
                )
                
                return Pair(false, errorResult)
            }
        }
        
        // 有权限
        return Pair(true, null)
    }
} 