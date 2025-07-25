package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.MemoryQueryResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*
import com.ai.assistance.operit.data.preferences.preferencesManager

/**
 * Executes queries against the AI's memory graph.
 */
class MemoryQueryToolExecutor(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "MemoryQueryToolExecutor"
    }

    private val memoryRepository by lazy {
        val profileId = runBlocking { preferencesManager.activeProfileIdFlow.first() }
        MemoryRepository(context, profileId)
    }

    override fun invoke(tool: AITool): ToolResult = runBlocking {
        val query = tool.parameters.find { it.name == "query" }?.value ?: ""
        if (query.isBlank()) {
            return@runBlocking ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Query parameter cannot be empty.")
        }

        Log.d(TAG, "Executing memory query: $query")

        return@runBlocking try {
            val results =
                memoryRepository.searchMemoriesPrecise(query)
            
            val formattedResult = buildResultData(results.take(3)) // Take top 3 results
            ToolResult(toolName = tool.name, success = true, result = formattedResult)
        } catch (e: Exception) {
            Log.e(TAG, "Memory query failed", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Failed to execute memory query: ${e.message}")
        }
    }

    private fun buildResultData(memories: List<Memory>): MemoryQueryResultData {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val memoryInfos = memories.map { memory ->
            MemoryQueryResultData.MemoryInfo(
                title = memory.title,
                content = memory.content,
                source = memory.source,
                tags = memory.tags.map { it.name },
                createdAt = sdf.format(memory.createdAt)
            )
        }
        return MemoryQueryResultData(memories = memoryInfos)
    }


    override fun validateParameters(tool: AITool): ToolValidationResult {
        val query = tool.parameters.find { it.name == "query" }?.value
        if (query.isNullOrBlank()) {
            return ToolValidationResult(valid = false, errorMessage = "Missing or empty required parameter: query")
        }
        return ToolValidationResult(valid = true)
    }

    override fun getCategory(): ToolCategory {
        return ToolCategory.FILE_READ
    }
} 