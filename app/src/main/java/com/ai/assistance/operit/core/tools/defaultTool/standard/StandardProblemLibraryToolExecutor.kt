package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.library.ProblemLibraryTool
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.coroutines.runBlocking

/** 问题库工具执行器 - 用于查询问题库 */
class StandardProblemLibraryToolExecutor(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "ProblemLibraryToolExecutor"
    }

    // 懒加载获取ProblemLibraryTool实例
    private val problemLibraryTool by lazy { ProblemLibraryTool.getInstance(context) }

    override fun invoke(tool: AITool): ToolResult {
        val query = tool.parameters.find { it.name == "query" }?.value ?: ""

        Log.d(TAG, "执行问题库查询: $query")

        val result = runBlocking { problemLibraryTool.queryProblemLibrary(query) }

        return ToolResult(toolName = tool.name, success = true, result = StringResultData(result))
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val query = tool.parameters.find { it.name == "query" }?.value
        if (query == null) {
            return ToolValidationResult(valid = false, errorMessage = "缺少必要参数: query")
        }
        return ToolValidationResult(valid = true)
    }

    override fun getCategory(): ToolCategory {
        return ToolCategory.FILE_READ
    }
}
