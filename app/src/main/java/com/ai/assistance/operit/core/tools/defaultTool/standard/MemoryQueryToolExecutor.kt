package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

/**
 * Executes queries against the AI's memory graph.
 */
class MemoryQueryToolExecutor(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "MemoryQueryToolExecutor"
    }

    private val memoryRepository = MemoryRepository()

    override fun invoke(tool: AITool): ToolResult {
        val query = tool.parameters.find { it.name == "query" }?.value ?: ""
        if (query.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Query parameter cannot be empty.")
        }

        Log.d(TAG, "Executing memory query: $query")

        return try {
            val results = runBlocking {
                memoryRepository.searchMemories(query)
            }
            val formattedResult = formatResults(results.take(5)) // Take top 5 results
            ToolResult(toolName = tool.name, success = true, result = StringResultData(formattedResult))
        } catch (e: Exception) {
            Log.e(TAG, "Memory query failed", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Failed to execute memory query: ${e.message}")
        }
    }

    private fun formatResults(memories: List<Memory>): String {
        if (memories.isEmpty()) {
            return "No relevant memories found."
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return memories.joinToString("\n---\n") { memory ->
            """
            Title: ${memory.title}
            Content: ${memory.content.take(200)}...
            Source: ${memory.source}
            Tags: ${memory.tags.joinToString(", ") { it.name }}
            Created: ${sdf.format(memory.createdAt)}
            """.trimIndent()
        }
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