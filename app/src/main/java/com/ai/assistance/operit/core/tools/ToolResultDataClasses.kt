package com.ai.assistance.operit.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * This file contains all implementations of ToolResultData Centralized for easier maintenance and
 * integration
 */
@Serializable
sealed class ToolResultData {
    /** Converts the structured data to a string representation */
    abstract override fun toString(): String
    fun toJson(): String {
        val json = Json.encodeToString(this)
        return json
    }
}

// Basic result data types (moved from AITool.kt)
@Serializable
data class BooleanResultData(val value: Boolean) : ToolResultData() {
    override fun toString(): String = value.toString()
}

@Serializable
data class StringResultData(val value: String) : ToolResultData() {
    override fun toString(): String = value
}

@Serializable
data class IntResultData(val value: Int) : ToolResultData() {
    override fun toString(): String = value.toString()
}

/** ADB命令执行结果数据 */
@Serializable
data class ADBResultData(val command: String, val output: String, val exitCode: Int) :
        ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("ADB命令执行结果:")
        sb.appendLine("命令: $command")
        sb.appendLine("退出码: $exitCode")
        sb.appendLine("\n输出:")
        sb.appendLine(output)
        return sb.toString()
    }
}

/** 计算结果结构化数据 */
@Serializable
data class CalculationResultData(
        val expression: String,
        val result: Double,
        val formattedResult: String,
        val variables: Map<String, Double> = emptyMap()
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("表达式: $expression")
        sb.appendLine("结果: $formattedResult")

        if (variables.isNotEmpty()) {
            sb.appendLine("变量:")
            variables.forEach { (name, value) -> sb.appendLine("  $name = $value") }
        }

        return sb.toString()
    }
}

/** 日期结果结构化数据 */
@Serializable
data class DateResultData(val date: String, val format: String, val formattedDate: String) :
        ToolResultData() {
    override fun toString(): String {
        return formattedDate
    }
}

/** Connection result data */
@Serializable
data class ConnectionResultData(
        val connectionId: String,
        val isActive: Boolean,
        val timestamp: Long = System.currentTimeMillis()
) : ToolResultData() {
    override fun toString(): String {
        return "Simulated connection established. Demo connection ID: $connectionId"
    }
}

/** Represents a directory listing result */
@Serializable
data class DirectoryListingData(val path: String, val entries: List<FileEntry>) : ToolResultData() {
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

/** Represents a file content result */
@Serializable
data class FileContentData(val path: String, val content: String, val size: Long) :
        ToolResultData() {
    override fun toString(): String {
        return "Content of $path:\n$content"
    }
}

/** Represents file existence check result */
@Serializable
data class FileExistsData(
        val path: String,
        val exists: Boolean,
        val isDirectory: Boolean = false,
        val size: Long = 0
) : ToolResultData() {
    override fun toString(): String {
        val fileType = if (isDirectory) "Directory" else "File"
        return if (exists) {
            "$fileType exists at path: $path (size: $size bytes)"
        } else {
            "No file or directory exists at path: $path"
        }
    }
}

/** Represents detailed file information */
@Serializable
data class FileInfoData(
        val path: String,
        val exists: Boolean,
        val fileType: String, // "file", "directory", or "other"
        val size: Long,
        val permissions: String,
        val owner: String,
        val group: String,
        val lastModified: String,
        val rawStatOutput: String
) : ToolResultData() {
    override fun toString(): String {
        if (!exists) {
            return "File or directory does not exist at path: $path"
        }

        val sb = StringBuilder()
        sb.appendLine("File information for $path:")
        sb.appendLine("Type: $fileType")
        sb.appendLine("Size: $size bytes")
        sb.appendLine("Permissions: $permissions")
        sb.appendLine("Owner: $owner")
        sb.appendLine("Group: $group")
        sb.appendLine("Last modified: $lastModified")
        return sb.toString()
    }
}

/** Represents a file operation result */
@Serializable
data class FileOperationData(
        val operation: String,
        val path: String,
        val successful: Boolean,
        val details: String
) : ToolResultData() {
    override fun toString(): String {
        return details
    }
}

/** HTTP响应结果结构化数据 */
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
) : ToolResultData() {
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

/** 网页内容结构化数据 */
@Serializable
data class WebPageData(
        val url: String,
        val title: String,
        val contentType: String,
        val content: String,
        val textContent: String,
        val size: Int,
        val links: List<Link>
) : ToolResultData() {
    @Serializable data class Link(val text: String, val url: String)

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

/** 系统设置数据 */
@Serializable
data class SystemSettingData(val namespace: String, val setting: String, val value: String) :
        ToolResultData() {
    override fun toString(): String {
        return "$namespace.$setting 的当前值是: $value"
    }
}

/** 应用操作结果数据 */
@Serializable
data class AppOperationData(
        val operationType: String,
        val packageName: String,
        val success: Boolean,
        val details: String = ""
) : ToolResultData() {
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

/** 应用列表数据 */
@Serializable
data class AppListData(val includesSystemApps: Boolean, val packages: List<String>) :
        ToolResultData() {
    override fun toString(): String {
        val appType = if (includesSystemApps) "所有应用" else "第三方应用"
        return "已安装${appType}列表:\n${packages.joinToString("\n")}"
    }
}

/** Represents UI node structure for hierarchical display */
@Serializable
data class SimplifiedUINode(
        val className: String?,
        val text: String?,
        val contentDesc: String?,
        val resourceId: String?,
        val bounds: String?,
        val isClickable: Boolean,
        val children: List<SimplifiedUINode>
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
        children.forEach { sb.append(it.toTreeString("$indent  ")) }

        return sb.toString()
    }

    private fun shouldKeepNode(): Boolean {
        // Keep conditions: key element types or has content or clickable or has children that
        // should be kept
        val isKeyElement =
                className in
                        setOf("Button", "TextView", "EditText", "ScrollView", "Switch", "ImageView")
        val hasContent = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()

        return isKeyElement || hasContent || isClickable || children.any { it.shouldKeepNode() }
    }
}

/** Represents UI page information result data */
@Serializable
data class UIPageResultData(
        val packageName: String,
        val activityName: String,
        val uiElements: SimplifiedUINode
) : ToolResultData() {
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

/** Represents a UI action result data */
@Serializable
data class UIActionResultData(
        val actionType: String,
        val actionDescription: String,
        val coordinates: Pair<Int, Int>? = null,
        val elementId: String? = null
) : ToolResultData() {
    override fun toString(): String {
        return actionDescription
    }
}

/** Represents a combined operation result data */
@Serializable
data class CombinedOperationResultData(
        val operationSummary: String,
        val waitTime: Int,
        val pageInfo: UIPageResultData
) : ToolResultData() {
    override fun toString(): String {
        return "$operationSummary (waited ${waitTime}ms)\n\n$pageInfo"
    }
}

/** Device information result data */
@Serializable
data class DeviceInfoResultData(
        val deviceId: String,
        val model: String,
        val manufacturer: String,
        val androidVersion: String,
        val sdkVersion: Int,
        val screenResolution: String,
        val screenDensity: Float,
        val totalMemory: String,
        val availableMemory: String,
        val totalStorage: String,
        val availableStorage: String,
        val batteryLevel: Int,
        val batteryCharging: Boolean,
        val cpuInfo: String,
        val networkType: String,
        val additionalInfo: Map<String, String> = emptyMap()
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("设备信息:")
        sb.appendLine("设备型号: $manufacturer $model")
        sb.appendLine("Android版本: $androidVersion (SDK $sdkVersion)")
        sb.appendLine("设备ID: $deviceId")
        sb.appendLine("屏幕: $screenResolution (${screenDensity}dp)")
        sb.appendLine("内存: 可用 $availableMemory / 总计 $totalMemory")
        sb.appendLine("存储: 可用 $availableStorage / 总计 $totalStorage")
        sb.appendLine("电池: ${batteryLevel}% ${if (batteryCharging) "(充电中)" else ""}")
        sb.appendLine("网络: $networkType")
        sb.appendLine("处理器: $cpuInfo")

        if (additionalInfo.isNotEmpty()) {
            sb.appendLine("\n其他信息:")
            additionalInfo.forEach { (key, value) -> sb.appendLine("$key: $value") }
        }

        return sb.toString()
    }
}

/** Web search result data */
@Serializable
data class WebSearchResultData(val query: String, val results: List<SearchResult>) :
        ToolResultData() {
    @Serializable
    data class SearchResult(
            val title: String,
            val url: String,
            val snippet: String,
            val extraInfo: String? = null
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Search results for: $query")
        sb.appendLine("")

        results.forEachIndexed { index, result ->
            sb.appendLine("${index + 1}. ${result.title}")
            // sb.appendLine("   URL: ${result.url}")
            // it's a link, so we don't need to show it
            sb.appendLine("   ${result.snippet}")

            // 如果有extraInfo，则显示它
            result.extraInfo?.let { extraInfo ->
                if (extraInfo.isNotEmpty()) {
                    sb.appendLine("")
                    sb.appendLine("   详细信息:")
                    sb.appendLine("   $extraInfo")
                }
            }

            sb.appendLine("")
        }

        if (results.isEmpty()) {
            sb.appendLine("No results found.")
        }

        return sb.toString()
    }
}

/** Intent execution result data */
@Serializable
data class IntentResultData(
        val action: String,
        val uri: String,
        val package_name: String,
        val component: String,
        val flags: Int,
        val extras_count: Int,
        val result: String
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Intent执行结果:")
        sb.appendLine("Action: $action")
        if (uri != "null") sb.appendLine("URI: $uri")
        if (package_name != "null") sb.appendLine("包名: $package_name")
        if (component != "null") sb.appendLine("组件: $component")
        sb.appendLine("Flags: $flags")
        sb.appendLine("附加数据数量: $extras_count")
        sb.appendLine("\n执行结果: $result")
        return sb.toString()
    }
}

/** 文件查找结果数据 */
@Serializable
data class FindFilesResultData(val path: String, val pattern: String, val files: List<String>) :
        ToolResultData() {
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

/** FFmpeg处理结果数据 */
@Serializable
data class FFmpegResultData(
        val command: String,
        val returnCode: Int,
        val output: String,
        val duration: Long,
        val outputFile: String? = null,
        val mediaInfo: MediaInfo? = null
) : ToolResultData() {
    @Serializable
    data class MediaInfo(
            val format: String,
            val duration: String,
            val bitrate: String,
            val videoStreams: List<StreamInfo>,
            val audioStreams: List<StreamInfo>
    )

    @Serializable
    data class StreamInfo(
            val index: Int,
            val codecType: String,
            val codecName: String,
            val resolution: String? = null,
            val frameRate: String? = null,
            val sampleRate: String? = null,
            val channels: Int? = null
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("FFmpeg执行结果:")
        sb.appendLine("命令: $command")
        sb.appendLine("返回码: $returnCode")
        sb.appendLine("执行时间: ${duration}ms")

        outputFile?.let { sb.appendLine("输出文件: $it") }

        mediaInfo?.let { info ->
            sb.appendLine("\n媒体信息:")
            sb.appendLine("格式: ${info.format}")
            sb.appendLine("时长: ${info.duration}")
            sb.appendLine("比特率: ${info.bitrate}")

            if (info.videoStreams.isNotEmpty()) {
                sb.appendLine("\n视频流:")
                info.videoStreams.forEach { stream ->
                    sb.appendLine("  索引: ${stream.index}")
                    sb.appendLine("  编解码器: ${stream.codecName}")
                    stream.resolution?.let { sb.appendLine("  分辨率: $it") }
                    stream.frameRate?.let { sb.appendLine("  帧率: $it") }
                    sb.appendLine()
                }
            }

            if (info.audioStreams.isNotEmpty()) {
                sb.appendLine("\n音频流:")
                info.audioStreams.forEach { stream ->
                    sb.appendLine("  索引: ${stream.index}")
                    sb.appendLine("  编解码器: ${stream.codecName}")
                    stream.sampleRate?.let { sb.appendLine("  采样率: $it") }
                    stream.channels?.let { sb.appendLine("  声道数: $it") }
                    sb.appendLine()
                }
            }
        }

        sb.appendLine("\n输出日志:")
        sb.append(output)

        return sb.toString()
    }
}

/** 文件转换结果数据 */
@Serializable
data class FileConversionResultData(
        val sourcePath: String,
        val targetPath: String,
        val sourceFormat: String,
        val targetFormat: String,
        val conversionType: String, // "document", "image", "audio", "video", "archive"
        val quality: String? = null,
        val fileSize: Long = 0, // Size of the output file in bytes
        val duration: Long = 0, // Time taken for conversion in ms
        val metadata: Map<String, String> =
                emptyMap() // Any additional metadata about the conversion
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("文件转换结果:")
        sb.appendLine("源文件: $sourcePath")
        sb.appendLine("目标文件: $targetPath")
        sb.appendLine("格式转换: .$sourceFormat → .$targetFormat")
        sb.appendLine("转换类型: $conversionType")

        quality?.let { sb.appendLine("质量设置: $it") }

        if (fileSize > 0) {
            val sizeInKB = fileSize / 1024.0
            val sizeInMB = sizeInKB / 1024.0

            val sizeStr =
                    when {
                        sizeInMB >= 1.0 -> String.format("%.2f MB", sizeInMB)
                        sizeInKB >= 1.0 -> String.format("%.2f KB", sizeInKB)
                        else -> "$fileSize bytes"
                    }
            sb.appendLine("文件大小: $sizeStr")
        }

        if (duration > 0) {
            sb.appendLine("处理时间: ${duration}ms")
        }

        if (metadata.isNotEmpty()) {
            sb.appendLine("\n元数据:")
            metadata.forEach { (key, value) -> sb.appendLine("  $key: $value") }
        }

        return sb.toString()
    }
}

/** 文件格式转换支持数据 */
@Serializable
data class FileFormatConversionsResultData(
        val formatType: String? = null, // null means all formats
        val conversions: Map<String, List<String>>, // source format -> list of target formats
        val fileTypes: Map<String, List<String>> = emptyMap() // category -> list of formats
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()

        if (formatType != null) {
            sb.appendLine("支持的$formatType 格式转换:")

            // Only show conversions for the specified format type
            val formatsForType = fileTypes[formatType] ?: emptyList()
            formatsForType.forEach { sourceFormat ->
                val targetFormats = conversions[sourceFormat]
                if (targetFormats != null && targetFormats.isNotEmpty()) {
                    sb.appendLine(".$sourceFormat → ${targetFormats.joinToString(", ") { ".$it" }}")
                }
            }
        } else {
            sb.appendLine("所有支持的格式转换:")
            conversions.forEach { (sourceFormat, targetFormats) ->
                sb.appendLine(".$sourceFormat → ${targetFormats.joinToString(", ") { ".$it" }}")
            }

            if (fileTypes.isNotEmpty()) {
                sb.appendLine("\n按类型分组:")
                fileTypes.forEach { (type, formats) ->
                    sb.appendLine("$type: ${formats.joinToString(", ") { ".$it" }}")
                }
            }
        }

        return sb.toString()
    }
}
