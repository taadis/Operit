package com.ai.assistance.operit.tools

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.AdbCommandExecutor
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolParameter
import com.ai.assistance.operit.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Tools for UI automation via ADB shell commands
 */
class ADBUITools(private val context: Context) {
    
    companion object {
        private const val TAG = "ADBUITools"
        private const val COMMAND_TIMEOUT_SECONDS = 10L
    }
    
    /**
     * Gets the current UI page/window information
     */
    suspend fun getPageInfo(tool: AITool): ToolResult {
        val format = tool.parameters.find { it.name == "format" }?.value ?: "xml"
        val detail = tool.parameters.find { it.name == "detail" }?.value ?: "summary"
        
        if (format !in listOf("xml", "json")) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Invalid format specified. Must be 'xml' or 'json'."
            )
        }
        
        return try {
            // 获取UI数据
            val uiData = getUIData() ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Failed to retrieve UI data."
            )
            
            // 解析当前窗口信息
            val focusInfo = extractFocusInfo(uiData.windowInfo)
            
            // 生成输出
            val result = when (detail) {
                "minimal" -> {
                    // 简化的输出，只返回基本信息
                    """
                    |Current Application: ${focusInfo.packageName ?: "Unknown"}
                    |Current Activity: ${focusInfo.activityName ?: "Unknown"}
                    |UI Preview: 
                    |${simplifyLayout(uiData.uiXml)}
                    """.trimMargin()
                }
                "full" -> {
                    // 完整的输出，包括完整的UI层次结构和窗口信息
                    """
                    |Current Application: ${focusInfo.packageName ?: "Unknown"}
                    |Current Activity: ${focusInfo.activityName ?: "Unknown"}
                    |
                    |UI Hierarchy:
                    |${simplifyLayout(uiData.uiXml)}
                    """.trimMargin()
                }
                else -> { // "summary"默认情况
                    // 标准输出，包括窗口信息和简化的UI层次结构
                    """
                    |Current Application: ${focusInfo.packageName ?: "Unknown"}
                    |Current Activity: ${focusInfo.activityName ?: "Unknown"}
                    |
                    |UI Elements:
                    |${simplifyLayout(uiData.uiXml)}
                    """.trimMargin()
                }
            }
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = result,
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting page info", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error getting page info: ${e.message}"
            )
        }
    }
    
    /**
     * UI数据类，保存XML和窗口信息
     */
    private data class UIData(val uiXml: String, val windowInfo: String)
    
    /**
     * 获取UI数据
     */
    private suspend fun getUIData(): UIData? {
        try {
            // 执行UI dump命令
            val dumpCommand = "uiautomator dump /sdcard/window_dump.xml"
            val dumpResult = AdbCommandExecutor.executeAdbCommand(dumpCommand)
            if (!dumpResult.success) return null
            
            // 读取dump文件内容
            val readCommand = "cat /sdcard/window_dump.xml"
            val readResult = AdbCommandExecutor.executeAdbCommand(readCommand)
            if (!readResult.success) return null
            
            // 获取窗口信息
            val windowCommand = "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'"
            val windowResult = AdbCommandExecutor.executeAdbCommand(windowCommand)
            if (!windowResult.success) return null
            
            return UIData(readResult.stdout, windowResult.stdout)
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving UI data", e)
            return null
        }
    }
    
    /**
     * 表示一个UI元素节点
     */
    private data class UINode(
        val className: String,    // 元素类名
        val id: String = "",      // 资源ID
        val text: String = "",    // 文本内容
        val contentDesc: String = "", // 内容描述
        val hint: String = "",    // 提示文本
        val isClickable: Boolean = false, // 是否可点击
        val isEnabled: Boolean = true,    // 是否启用
        val bounds: String = "",  // 元素边界
        val centerX: Int = 0,     // 中心X坐标
        val centerY: Int = 0,     // 中心Y坐标
        val children: MutableList<UINode> = mutableListOf() // 子元素
    ) {
        // 获取简短的显示名称
        fun getDisplayName(): String {
            val sb = StringBuilder()
            
            // 添加类名
            val shortClassName = className.split(".").lastOrNull() ?: className
            sb.append("[$shortClassName]")
            
            // 添加文本、描述或提示
            if (text.isNotBlank()) sb.append(" \"$text\"")
            else if (contentDesc.isNotBlank()) sb.append(" ($contentDesc)")
            else if (hint.isNotBlank()) sb.append(" hint:$hint")
            
            // 添加ID（如果有）
            if (id.isNotBlank()) {
                val shortId = id.split("/").lastOrNull() ?: id
                sb.append(" id=$shortId")
            }
            
            // 如果可点击，添加点击坐标
            if (isClickable) {
                sb.append(" 👆($centerX,$centerY)")
            }
            
            // 如果不可用，添加标记
            if (!isEnabled && isClickable) {
                sb.append(" [DISABLED]")
            }
            
            return sb.toString()
        }
        
        // 是否是重要元素（可交互或有文本）
        fun isImportant(): Boolean {
            return isClickable || 
                   text.isNotBlank() || 
                   contentDesc.isNotBlank() || 
                   hint.isNotBlank() ||
                   className.contains("EditText") ||
                   className.contains("Button") ||
                   className.contains("CheckBox") ||
                   className.contains("RadioButton") ||
                   className.contains("Switch")
        }
        
        // 获取元素类型优先级（用于排序）
        fun getTypePriority(): Int {
            return when {
                className.contains("Button") -> 10
                className.contains("EditText") -> 9
                className.contains("CheckBox") || className.contains("RadioButton") || className.contains("Switch") -> 8
                isClickable -> 7
                text.isNotBlank() -> 6
                contentDesc.isNotBlank() -> 5
                hint.isNotBlank() -> 4
                className.contains("TextView") -> 3
                className.contains("ImageView") -> 2
                else -> 1
            }
        }
    }
    
    data class SimplifiedNode(
        val className: String?,
        val text: String?,
        val contentDesc: String?,
        val resourceId: String?,
        val bounds: String?,
        val isClickable: Boolean,  // 新增点击状态
        val children: MutableList<SimplifiedNode> = mutableListOf()
    )
    
    fun simplifyLayout(xml: String): String {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser().apply {
            setInput(StringReader(xml))
        }
    
        val nodeStack = mutableListOf<SimplifiedNode>()
        var rootNode: SimplifiedNode? = null
    
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "node") {
                        val newNode = createNode(parser)
                        if (rootNode == null) {
                            rootNode = newNode
                            nodeStack.add(newNode)
                        } else {
                            nodeStack.lastOrNull()?.children?.add(newNode)
                            nodeStack.add(newNode)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "node") {
                        nodeStack.removeLastOrNull()
                    }
                }
            }
            parser.next()
        }
    
        return rootNode?.toTreeString() ?: ""
    }
    
    private fun createNode(parser: XmlPullParser): SimplifiedNode {
        // 解析关键属性
        val className = parser.getAttributeValue(null, "class")?.substringAfterLast('.')
        val text = parser.getAttributeValue(null, "text")?.replace("&#10;", "\n")
        val contentDesc = parser.getAttributeValue(null, "content-desc")
        val resourceId = parser.getAttributeValue(null, "resource-id")
        val bounds = parser.getAttributeValue(null, "bounds")
        val isClickable = parser.getAttributeValue(null, "clickable") == "true"
    
        return SimplifiedNode(
            className = className,
            text = text,
            contentDesc = contentDesc,
            resourceId = resourceId,
            bounds = bounds,
            isClickable = isClickable
        )
    }
    
    private fun SimplifiedNode.shouldKeepNode(): Boolean {
        // 保留条件：关键元素类型 或 有内容 或 可点击 或 包含需要保留的子节点
        val isKeyElement = className in setOf(
            "Button", "TextView", "EditText", 
            "ScrollView", "Switch", "ImageView"
        )
        val hasContent = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()
        
        return isKeyElement || hasContent || isClickable || children.any { it.shouldKeepNode() }
    }
    
    private fun SimplifiedNode.toTreeString(indent: String = ""): String {
        if (!shouldKeepNode()) return ""
    
        val sb = StringBuilder()
        
        // 节点标识
        sb.append(indent)
        if (isClickable) sb.append("▶ ") else sb.append("◢ ")
        
        // 类名
        className?.let { sb.append("[$it] ") }
        
        // 文本内容（最多显示30字符）
        text?.takeIf { it.isNotBlank() }?.let { 
            val displayText = if (it.length > 30) "${it.take(27)}..." else it
            sb.append("T: \"$displayText\" ")
        }
        
        // 内容描述
        contentDesc?.takeIf { it.isNotBlank() }?.let { sb.append("D: \"$it\" ") }
        
        // 资源ID
        resourceId?.takeIf { it.isNotBlank() }?.let { sb.append("ID: $it ") }
        
        // 坐标范围
        bounds?.let { sb.append("⮞ $it") }
        
        sb.append("\n")
    
        // 递归处理子节点
        children.forEach { 
            sb.append(it.toTreeString("$indent  ")) 
        }
    
        return sb.toString()
    }
    /**
     * Extracts package and activity information from window focus data
     */
    private fun extractFocusInfo(windowInfo: String): FocusInfo {
        val result = FocusInfo()
        
        try {
            // Extract package name
            val packageRegex = "\\s([a-zA-Z0-9.]+)/".toRegex()
            val packageMatch = packageRegex.find(windowInfo)
            result.packageName = packageMatch?.groupValues?.get(1)
            
            // Extract activity name
            val activityRegex = "/([a-zA-Z0-9.]+)".toRegex()
            val activityMatch = activityRegex.find(windowInfo)
            result.activityName = activityMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing window info", e)
        }
        
        return result
    }
    
    /**
     * Simple data class to hold focus information
     */
    private data class FocusInfo(
        var packageName: String? = null,
        var activityName: String? = null
    )
    
    /**
     * Simulates a tap/click at specific coordinates
     */
    suspend fun tap(tool: AITool): ToolResult {
        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull()
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull()
        
        if (x == null || y == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Missing or invalid coordinates. Both 'x' and 'y' must be valid integers."
            )
        }
        
        val command = "input tap $x $y"
        
        return try {
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Successfully tapped at coordinates ($x, $y)",
                    error = ""
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to tap at coordinates ($x, $y): ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error tapping at coordinates", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error tapping at coordinates: ${e.message}"
            )
        }
    }
    
    /**
     * Simulates a click on an element identified by resource ID or text
     */
    suspend fun clickElement(tool: AITool): ToolResult {
        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val text = tool.parameters.find { it.name == "text" }?.value
        val contentDesc = tool.parameters.find { it.name == "contentDesc" }?.value
        
        if (resourceId == null && text == null && contentDesc == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Missing element identifier. Provide at least one of: 'resourceId', 'text', or 'contentDesc'."
            )
        }
        
        return try {
            // First, dump the UI hierarchy
            val dumpCommand = "uiautomator dump /sdcard/window_dump.xml"
            val dumpResult = AdbCommandExecutor.executeAdbCommand(dumpCommand)
            
            if (!dumpResult.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to dump UI hierarchy: ${dumpResult.stderr}"
                )
            }
            
            // Then read the dumped file
            val readCommand = "cat /sdcard/window_dump.xml"
            val readResult = AdbCommandExecutor.executeAdbCommand(readCommand)
            
            if (!readResult.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to read UI hierarchy: ${readResult.stderr}"
                )
            }
            
            // Parse the XML to find the element
            val xml = readResult.stdout
            
            // Define regex patterns for matching element attributes
            val resourceIdPattern = "resource-id=\"${resourceId?.let { Regex.escape(it) } ?: ".*?"}\"".toRegex()
            val textPattern = "text=\"${text?.let { Regex.escape(it) } ?: ".*?"}\"".toRegex()
            val contentDescPattern = "content-desc=\"${contentDesc?.let { Regex.escape(it) } ?: ".*?"}\"".toRegex()
            val boundsPattern = "bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"".toRegex()
            
            // Find nodes in XML that match our criteria
            val nodeRegex = if (resourceId != null) {
                "<node[^>]*?$resourceIdPattern[^>]*?>".toRegex()
            } else if (text != null) {
                "<node[^>]*?$textPattern[^>]*?>".toRegex()
            } else {
                "<node[^>]*?$contentDescPattern[^>]*?>".toRegex()
            }
            
            val matchingNodes = nodeRegex.findAll(xml)
            val node = matchingNodes.firstOrNull()
            
            if (node == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "No matching element found with the specified criteria."
                )
            }
            
            // Extract bounds from the matching node
            val matchResult = boundsPattern.find(node.value)
            
            if (matchResult == null || matchResult.groupValues.size < 5) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to extract bounds from the element."
                )
            }
            
            // Extract coordinates
            val x1 = matchResult.groupValues[1].toInt()
            val y1 = matchResult.groupValues[2].toInt()
            val x2 = matchResult.groupValues[3].toInt()
            val y2 = matchResult.groupValues[4].toInt()
            
            // Calculate center point
            val centerX = (x1 + x2) / 2
            val centerY = (y1 + y2) / 2
            
            // Execute the tap command at the center point
            val tapCommand = "input tap $centerX $centerY"
            val tapResult = AdbCommandExecutor.executeAdbCommand(tapCommand)
            
            if (tapResult.success) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Successfully clicked element" + when {
                        resourceId != null -> " with resource ID: $resourceId"
                        text != null -> " with text: $text"
                        else -> " with content description: $contentDesc"
                    } + " at coordinates ($centerX, $centerY)",
                    error = ""
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to click element: ${tapResult.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking element", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error clicking element: ${e.message}"
            )
        }
    }
    
    /**
     * Sets text in an input field
     */
    suspend fun setInputText(tool: AITool): ToolResult {
        val text = tool.parameters.find { it.name == "text" }?.value
        
        if (text == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Missing 'text' parameter."
            )
        }
        
        return try {
            // Clear existing text first
            val clearCommand = "input keyevent KEYCODE_CTRL_A && input keyevent KEYCODE_DEL"
            val clearResult = AdbCommandExecutor.executeAdbCommand(clearCommand)
            
            if (!clearResult.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to clear existing text: ${clearResult.stderr}"
                )
            }
            
            // Now type the new text
            val inputCommand = "input text '${text.replace("'", "\\'")}'"
            val inputResult = AdbCommandExecutor.executeAdbCommand(inputCommand)
            
            if (inputResult.success) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Successfully set input text to: $text",
                    error = ""
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to set input text: ${inputResult.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting input text", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error setting input text: ${e.message}"
            )
        }
    }
    
    /**
     * Simulates pressing a specific key
     */
    suspend fun pressKey(tool: AITool): ToolResult {
        val keyCode = tool.parameters.find { it.name == "keyCode" }?.value
        
        if (keyCode == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Missing 'keyCode' parameter."
            )
        }
        
        val command = "input keyevent $keyCode"
        
        return try {
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Successfully pressed key: $keyCode",
                    error = ""
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to press key: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pressing key", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error pressing key: ${e.message}"
            )
        }
    }
    
    /**
     * Performs a swipe gesture
     */
    suspend fun swipe(tool: AITool): ToolResult {
        val startX = tool.parameters.find { it.name == "startX" }?.value?.toIntOrNull()
        val startY = tool.parameters.find { it.name == "startY" }?.value?.toIntOrNull()
        val endX = tool.parameters.find { it.name == "endX" }?.value?.toIntOrNull()
        val endY = tool.parameters.find { it.name == "endY" }?.value?.toIntOrNull()
        val duration = tool.parameters.find { it.name == "duration" }?.value?.toIntOrNull() ?: 300
        
        if (startX == null || startY == null || endX == null || endY == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Missing or invalid coordinates. 'startX', 'startY', 'endX', and 'endY' must be valid integers."
            )
        }
        
        val command = "input swipe $startX $startY $endX $endY $duration"
        
        return try {
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Successfully performed swipe from ($startX, $startY) to ($endX, $endY)",
                    error = ""
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to perform swipe: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing swipe", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error performing swipe: ${e.message}"
            )
        }
    }
    
    /**
     * Launches an app by package name
     */
    suspend fun launchApp(tool: AITool): ToolResult {
        val packageName = tool.parameters.find { it.name == "packageName" }?.value
        
        if (packageName == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Missing 'packageName' parameter."
            )
        }
        
        val command = "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
        
        return try {
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Successfully launched app: $packageName",
                    error = ""
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to launch app: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error launching app: ${e.message}"
            )
        }
    }
    
    /**
     * Performs a combined operation: execute an action, wait, then return the new UI state
     * This allows for common patterns like click-wait-get_page_info or swipe-wait-get_page_info
     */
    suspend fun combinedOperation(tool: AITool): ToolResult {
        val operation = tool.parameters.find { it.name == "operation" }?.value
        val delayMs = tool.parameters.find { it.name == "delayMs" }?.value?.toIntOrNull() ?: 1000
        
        if (operation == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Missing 'operation' parameter. Must specify which operation to perform."
            )
        }
        
        // Parse the operation to determine which tool to execute
        val operationParts = operation.trim().split(" ")
        if (operationParts.isEmpty()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Invalid operation format. Must specify operation type and parameters."
            )
        }
        
        val operationType = operationParts[0].lowercase()
        
        // Execute the specified operation
        val operationResult = when (operationType) {
            "tap" -> {
                if (operationParts.size < 3) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = "",
                        error = "Invalid tap operation. Format: tap x y"
                    )
                }
                
                val x = operationParts[1].toIntOrNull()
                val y = operationParts[2].toIntOrNull()
                
                if (x == null || y == null) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = "",
                        error = "Invalid coordinates for tap operation."
                    )
                }
                
                val tapTool = AITool(
                    name = "tap",
                    parameters = listOf(
                        ToolParameter("x", x.toString()),
                        ToolParameter("y", y.toString())
                    )
                )
                
                tap(tapTool)
            }
            "swipe" -> {
                if (operationParts.size < 5) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = "",
                        error = "Invalid swipe operation. Format: swipe startX startY endX endY [duration]"
                    )
                }
                
                val startX = operationParts[1].toIntOrNull()
                val startY = operationParts[2].toIntOrNull()
                val endX = operationParts[3].toIntOrNull()
                val endY = operationParts[4].toIntOrNull()
                val duration = if (operationParts.size > 5) operationParts[5].toIntOrNull() ?: 300 else 300
                
                if (startX == null || startY == null || endX == null || endY == null) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = "",
                        error = "Invalid coordinates for swipe operation."
                    )
                }
                
                val swipeTool = AITool(
                    name = "swipe",
                    parameters = listOf(
                        ToolParameter("startX", startX.toString()),
                        ToolParameter("startY", startY.toString()),
                        ToolParameter("endX", endX.toString()),
                        ToolParameter("endY", endY.toString()),
                        ToolParameter("duration", duration.toString())
                    )
                )
                
                swipe(swipeTool)
            }
            "click_element" -> {
                if (operationParts.size < 3) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = "",
                        error = "Invalid click_element operation. Format: click_element type value"
                    )
                }
                
                val identifierType = operationParts[1]
                val identifierValue = operationParts.drop(2).joinToString(" ")
                
                if (identifierType !in listOf("resourceId", "text", "contentDesc")) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = "",
                        error = "Invalid identifier type for click_element. Must be 'resourceId', 'text', or 'contentDesc'."
                    )
                }
                
                val parameters = mutableListOf<ToolParameter>()
                parameters.add(ToolParameter(identifierType, identifierValue))
                
                val clickTool = AITool(
                    name = "click_element",
                    parameters = parameters
                )
                
                clickElement(clickTool)
            }
            "press_key" -> {
                if (operationParts.size < 2) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = "",
                        error = "Invalid press_key operation. Format: press_key keyCode"
                    )
                }
                
                val keyCode = operationParts[1]
                
                val keyTool = AITool(
                    name = "press_key",
                    parameters = listOf(
                        ToolParameter("keyCode", keyCode)
                    )
                )
                
                pressKey(keyTool)
            }
            "set_input_text" -> {
                if (operationParts.size < 2) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = "",
                        error = "Invalid set_input_text operation. Format: set_input_text text"
                    )
                }
                
                val inputText = operationParts.drop(1).joinToString(" ")
                
                val inputTool = AITool(
                    name = "set_input_text",
                    parameters = listOf(
                        ToolParameter("text", inputText)
                    )
                )
                
                setInputText(inputTool)
            }
            "launch_app" -> {
                if (operationParts.size < 2) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = "",
                        error = "Invalid launch_app operation. Format: launch_app packageName"
                    )
                }
                
                val packageName = operationParts[1]
                
                val launchTool = AITool(
                    name = "launch_app",
                    parameters = listOf(
                        ToolParameter("packageName", packageName)
                    )
                )
                
                launchApp(launchTool)
            }
            else -> {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Unsupported operation: $operationType. Supported operations: tap, swipe, click_element, press_key, set_input_text, launch_app"
                )
            }
        }
        
        // If the operation failed, return the error
        if (!operationResult.success) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Operation failed: ${operationResult.error}"
            )
        }
        
        // Wait for the specified delay
        try {
            kotlinx.coroutines.delay(delayMs.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "Error during delay", e)
        }
        
        // 使用getPageInfo方法获取新的UI状态，而不是重复实现相同的逻辑
        try {
            // 创建适当的工具调用以获取页面信息
            val pageInfoTool = AITool(
                name = "get_page_info",
                parameters = listOf(
                    ToolParameter("format", "xml"),
                    ToolParameter("detail", "summary")
                )
            )
            
            // 调用getPageInfo获取UI状态
            val pageInfoResult = getPageInfo(pageInfoTool)
            
            if (!pageInfoResult.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Operation succeeded but failed to get UI state: ${pageInfoResult.error}"
                )
            }
            
            // 构建操作摘要
            val operationSummary = when (operationType) {
                "tap" -> "Tapped at (${operationParts[1]}, ${operationParts[2]})"
                "swipe" -> "Swiped from (${operationParts[1]}, ${operationParts[2]}) to (${operationParts[3]}, ${operationParts[4]})"
                "click_element" -> "Clicked element with ${operationParts[1]}: ${operationParts.drop(2).joinToString(" ")}"
                "press_key" -> "Pressed key: ${operationParts[1]}"
                "set_input_text" -> "Set input text to: ${operationParts.drop(1).joinToString(" ")}"
                "launch_app" -> "Launched app: ${operationParts[1]}"
                else -> "Executed operation: $operation"
            }
            
            // 组合操作摘要和UI状态
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = "$operationSummary (waited ${delayMs}ms)\n\n${pageInfoResult.result}",
                error = ""
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting UI state after operation", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "Operation was successful but failed to get new UI state: ${e.message}",
                error = "Error getting UI state after operation: ${e.message}"
            )
        }
    }
}