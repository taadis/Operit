package com.ai.assistance.operit.tools

import kotlinx.serialization.Serializable
import com.ai.assistance.operit.model.ToolResultData
import kotlinx.serialization.Contextual
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * This file contains all implementations of ToolResultData
 * Centralized for easier maintenance and integration
 */

/**
 * Extension function to serialize ToolResultData objects to JSON
 */
fun ToolResultData.toJson(): String = when (this) {
    is BooleanResultData -> Json.encodeToString(this)
    is StringResultData -> Json.encodeToString(this)
    is IntResultData -> Json.encodeToString(this)
    is CalculationResultData -> Json.encodeToString(this)
    is DateResultData -> Json.encodeToString(this)
    is ConnectionResultData -> Json.encodeToString(this)
    is DirectoryListingData -> Json.encodeToString(this)
    is FileContentData -> Json.encodeToString(this)
    is FileExistsData -> Json.encodeToString(this)
    is FileOperationData -> Json.encodeToString(this)
    is HttpResponseData -> Json.encodeToString(this)
    is WebPageData -> Json.encodeToString(this)
    is SystemSettingData -> Json.encodeToString(this)
    is AppOperationData -> Json.encodeToString(this)
    is AppListData -> Json.encodeToString(this)
    is UIPageResultData -> Json.encodeToString(this)
    is UIActionResultData -> Json.encodeToString(this)
    is CombinedOperationResultData -> Json.encodeToString(this)
    is WebSearchResultData -> Json.encodeToString(this)
    is FindFilesResultData -> Json.encodeToString(this)
    else -> this.toString()
}

// Basic result data types (moved from AITool.kt)
@Serializable
data class BooleanResultData(val value: Boolean) : ToolResultData {
    override fun toString(): String = value.toString()
}

@Serializable
data class StringResultData(val value: String) : ToolResultData {
    override fun toString(): String = value
}

@Serializable
data class IntResultData(val value: Int) : ToolResultData {
    override fun toString(): String = value.toString()
}

/**
 * 计算结果结构化数据
 */
@Serializable
data class CalculationResultData(
    val expression: String,
    val result: Double,
    val formattedResult: String,
    val variables: Map<String, Double> = emptyMap()
) : ToolResultData {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("表达式: $expression")
        sb.appendLine("结果: $formattedResult")

        if (variables.isNotEmpty()) {
            sb.appendLine("变量:")
            variables.forEach { (name, value) ->
                sb.appendLine("  $name = $value")
            }
        }

        return sb.toString()
    }
}

/**
 * 日期结果结构化数据
 */
@Serializable
data class DateResultData(
    val date: String,
    val format: String,
    val formattedDate: String
) : ToolResultData {
    override fun toString(): String {
        return formattedDate
    }
}

/**
 * Connection result data
 */
@Serializable
data class ConnectionResultData(
    val connectionId: String,
    val isActive: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) : ToolResultData {
    override fun toString(): String {
        return "Simulated connection established. Demo connection ID: $connectionId"
    }
}

/**
 * Represents a directory listing result
 */
@Serializable
data class DirectoryListingData(
    val path: String,
    val entries: List<FileEntry>
) : ToolResultData {
    @Serializable
    data class FileEntry(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val permissions: String,
        val lastModified: String
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Directory listing for $path:")
        entries.forEach { entry ->
            val typeIndicator = if (entry.isDirectory) "d" else "-"
            sb.appendLine(
                "$typeIndicator${entry.permissions} ${
                    entry.size.toString().padStart(8)
                } ${entry.lastModified} ${entry.name}"
            )
        }
        return sb.toString()
    }
}

/**
 * Represents a file content result
 */
@Serializable
data class FileContentData(
    val path: String,
    val content: String,
    val size: Long
) : ToolResultData {
    override fun toString(): String {
        return "Content of $path:\n$content"
    }
}

/**
 * Represents file existence check result
 */
@Serializable
data class FileExistsData(
    val path: String,
    val exists: Boolean,
    val isDirectory: Boolean = false,
    val size: Long = 0
) : ToolResultData {
    override fun toString(): String {
        val fileType = if (isDirectory) "Directory" else "File"
        return if (exists) {
            "$fileType exists at path: $path (size: $size bytes)"
        } else {
            "No file or directory exists at path: $path"
        }
    }
}

/**
 * Represents a file operation result
 */
@Serializable
data class FileOperationData(
    val operation: String,
    val path: String,
    val successful: Boolean,
    val details: String
) : ToolResultData {
    override fun toString(): String {
        return details
    }
}

/**
 * HTTP响应结果结构化数据
 */
@Serializable
data class HttpResponseData(
    val url: String,
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, String>,
    val contentType: String,
    val content: String,
    val contentSummary: String,
    val size: Int
) : ToolResultData {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("HTTP Response:")
        sb.appendLine("URL: $url")
        sb.appendLine("Status: $statusCode $statusMessage")
        sb.appendLine("Content-Type: $contentType")
        sb.appendLine("Size: $size bytes")
        sb.appendLine()
        sb.appendLine("Content Summary:")
        sb.append(contentSummary)
        return sb.toString()
    }
}

/**
 * 网页内容结构化数据
 */
@Serializable
data class WebPageData(
    val url: String,
    val title: String,
    val contentType: String,
    val content: String,
    val textContent: String,
    val size: Int,
    val links: List<Link>
) : ToolResultData {
    @Serializable
    data class Link(
        val text: String,
        val url: String
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("网页内容:")
        if (title.isNotBlank()) {
            sb.appendLine("标题: $title")
        }
        sb.appendLine("类型: $contentType")
        sb.appendLine("大小: $size bytes")
        sb.appendLine()
        sb.append(textContent)
        return sb.toString()
    }
}

/**
 * 系统设置数据
 */
@Serializable
data class SystemSettingData(
    val namespace: String,
    val setting: String,
    val value: String
) : ToolResultData {
    override fun toString(): String {
        return "$namespace.$setting 的当前值是: $value"
    }
}

/**
 * 应用操作结果数据
 */
@Serializable
data class AppOperationData(
    val operationType: String,
    val packageName: String,
    val success: Boolean,
    val details: String = ""
) : ToolResultData {
    override fun toString(): String {
        return when (operationType) {
            "install" -> "成功安装应用: $packageName $details"
            "uninstall" -> "成功卸载应用: $packageName $details"
            "start" -> "成功启动应用: $packageName $details"
            "stop" -> "成功停止应用: $packageName $details"
            else -> details
        }
    }
}

/**
 * 应用列表数据
 */
@Serializable
data class AppListData(
    val includesSystemApps: Boolean,
    val packages: List<String>
) : ToolResultData {
    override fun toString(): String {
        val appType = if (includesSystemApps) "所有应用" else "第三方应用"
        return "已安装${appType}列表:\n${packages.joinToString("\n")}"
    }
}

/**
 * Represents UI node structure for hierarchical display
 */
@Serializable
data class SimplifiedUINode(
    val className: String? = null,
    val text: String? = null,
    val contentDesc: String? = null,
    val resourceId: String? = null,
    val bounds: String? = null,
    val isClickable: Boolean = false,
    val children: List<SimplifiedUINode> = emptyList()
) {
    fun toTreeString(indent: String = ""): String {
        if (!shouldKeepNode()) return ""
    
        val sb = StringBuilder()
        
        // Node identifier
        sb.append(indent)
        if (isClickable) sb.append("▶ ") else sb.append("◢ ")
        
        // Class name
        className?.let { sb.append("[$it] ") }
        
        // Text content (maximum 30 characters)
        text?.takeIf { it.isNotBlank() }?.let { 
            val displayText = if (it.length > 30) "${it.take(27)}..." else it
            sb.append("T: \"$displayText\" ")
        }
        
        // Content description
        contentDesc?.takeIf { it.isNotBlank() }?.let { sb.append("D: \"$it\" ") }
        
        // Resource ID
        resourceId?.takeIf { it.isNotBlank() }?.let { sb.append("ID: $it ") }
        
        // Bounds
        bounds?.let { sb.append("⮞ $it") }
        
        sb.append("\n")
    
        // Process children recursively
        children.forEach { 
            sb.append(it.toTreeString("$indent  ")) 
        }
    
        return sb.toString()
    }
    
    private fun shouldKeepNode(): Boolean {
        // Keep conditions: key element types or has content or clickable or has children that should be kept
        val isKeyElement = className in setOf(
            "Button", "TextView", "EditText", 
            "ScrollView", "Switch", "ImageView"
        )
        val hasContent = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()
        
        return isKeyElement || hasContent || isClickable || children.any { it.shouldKeepNode() }
    }
}

/**
 * Represents UI page information result data
 */
@Serializable
data class UIPageResultData(
    val packageName: String,
    val activityName: String,
    val uiElements: SimplifiedUINode
) : ToolResultData {
    override fun toString(): String {
        return """
            |Current Application: $packageName
            |Current Activity: $activityName
            |
            |UI Elements:
            |${uiElements.toTreeString()}
            """.trimMargin()
    }
}

/**
 * Represents a UI action result data
 */
@Serializable
data class UIActionResultData(
    val actionType: String,
    val actionDescription: String,
    val coordinates: Pair<Int, Int>? = null,
    val elementId: String? = null
) : ToolResultData {
    override fun toString(): String {
        return actionDescription
    }
}

/**
 * Represents a combined operation result data
 */
@Serializable
data class CombinedOperationResultData(
    val operationSummary: String,
    val waitTime: Int,
    val pageInfo: UIPageResultData
) : ToolResultData {
    override fun toString(): String {
        return "$operationSummary (waited ${waitTime}ms)\n\n$pageInfo"
    }
}

/**
 * Web search result data
 */
@Serializable
data class WebSearchResultData(
    val query: String,
    val results: List<SearchResult>
) : ToolResultData {
    @Serializable
    data class SearchResult(
        val title: String,
        val url: String,
        val snippet: String
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Search results for: $query")
        sb.appendLine("")

        results.forEachIndexed { index, result ->
            sb.appendLine("${index + 1}. ${result.title}")
            // sb.appendLine("   URL: ${result.url}")
            //it's a link, so we don't need to show it
            sb.appendLine("   ${result.snippet}")
            sb.appendLine("")
        }

        if (results.isEmpty()) {
            sb.appendLine("No results found.")
        }

        return sb.toString()
    }
}

/**
 * 文件查找结果数据
 */
@Serializable
data class FindFilesResultData(
    val path: String,
    val pattern: String,
    val files: List<String>
) : ToolResultData {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("文件查找结果:")
        sb.appendLine("搜索路径: $path")
        sb.appendLine("匹配模式: $pattern")
        
        sb.appendLine("找到 ${files.size} 个文件:")
        files.forEachIndexed { index, file ->
            if (index < 10 || files.size <= 20) {
                sb.appendLine("- $file")
            } else if (index == 10 && files.size > 20) {
                sb.appendLine("... 以及 ${files.size - 10} 个其他文件")
            }
        }
        
        return sb.toString()
    }
}