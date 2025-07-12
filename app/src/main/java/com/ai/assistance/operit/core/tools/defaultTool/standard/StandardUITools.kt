package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.common.displays.UIOperationOverlay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** Base class for UI automation tools - standard version does not support UI operations */
open class StandardUITools(protected val context: Context) {

    companion object {
        private const val TAG = "UITools"
        private const val COMMAND_TIMEOUT_SECONDS = 10L
        private const val OPERATION_NOT_SUPPORTED =
                "This operation is not supported in the standard version. Please use the accessibility or debugger version."
    }

    // UI操作反馈覆盖层
    protected val operationOverlay = UIOperationOverlay(context)

    /** Gets the current UI page/window information */
    open suspend fun getPageInfo(tool: AITool): ToolResult {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    data class UINode(
            val className: String?,
            val text: String?,
            val contentDesc: String?,
            val resourceId: String?,
            val bounds: String?,
            val isClickable: Boolean,
            val children: MutableList<UINode> = mutableListOf()
    )

    protected fun UINode.toUINode(): SimplifiedUINode {
        return SimplifiedUINode(
                className = className,
                text = text,
                contentDesc = contentDesc,
                resourceId = resourceId,
                bounds = bounds,
                isClickable = isClickable,
                children = children.map { it.toUINode() }
        )
    }

    protected data class FocusInfo(
            var packageName: String? = null,
            var activityName: String? = null
    )

    /** Simulates a tap/click at specific coordinates */
    open suspend fun tap(tool: AITool): ToolResult {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /** Simulates a click on an element identified by resource ID or class name */
    open suspend fun clickElement(tool: AITool): ToolResult {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /** Sets text in an input field */
    open suspend fun setInputText(tool: AITool): ToolResult {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /** Simulates pressing a specific key */
    open suspend fun pressKey(tool: AITool): ToolResult {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /** Performs a swipe gesture */
    open suspend fun swipe(tool: AITool): ToolResult {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /** Finds UI elements matching specific criteria without clicking them */
    open suspend fun findElement(tool: AITool): ToolResult {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /**
     * Executes a full UI automation task to achieve a specific goal.
     * This is a high-level tool that orchestrates smaller UI actions.
     */
    open fun automateUiTask(tool: AITool): Flow<ToolResult> {
        val taskGoal = tool.parameters.find { it.name == "task_goal" }?.value
        if (taskGoal.isNullOrBlank()) {
            return flowOf(
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Parameter 'task_goal' is missing."
                )
            )
        }

        // We must use runBlocking here as we are in a non-suspend context but need to call a suspend function
        val initialPageInfoResult = runBlocking { getPageInfo(AITool("get_page_info", emptyList())) }

        if (!initialPageInfoResult.success) {
            return flowOf(
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get initial UI state: ${initialPageInfoResult.error}"
                )
            )
        }

        val initialUiState = (initialPageInfoResult.result as? UIPageResultData)?.toString()
            ?: initialPageInfoResult.result.toString()

        val service = EnhancedAIService.getInstance(context)
        
        return runBlocking {
            service.executeUiAutomationTask(initialUiState, taskGoal).map { uiCommand ->
                // An interrupt is a valid, successful outcome of a step, signaling a need for escalation.
                // The tool step itself did not fail, so success is always true.
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    // The result now clearly states the outcome of the step.
                    result = StringResultData("Step Result: ${uiCommand.type}. Details: ${uiCommand.arg}"),
                    error = null // No technical error occurred during the step.
                )
            }
        }
    }

    // 保留一些实用的辅助方法，供子类使用

    /** Helper method to extract attribute values from node text */
    protected fun extractAttribute(nodeText: String, attributeName: String): String {
        val pattern = "$attributeName=\"(.*?)\"".toRegex()
        val matchResult = pattern.find(nodeText)
        return matchResult?.groupValues?.get(1) ?: ""
    }
}
