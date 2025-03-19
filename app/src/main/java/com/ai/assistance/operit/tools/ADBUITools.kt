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
        
        // Log tap attempt
        Log.d(TAG, "Attempting to tap at coordinates: ($x, $y)")
        
        val command = "input tap $x $y"
        
        return try {
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (result.success) {
                Log.d(TAG, "Tap successful at coordinates: ($x, $y)")
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Successfully tapped at coordinates ($x, $y)",
                    error = ""
                )
            } else {
                Log.e(TAG, "Tap failed at coordinates: ($x, $y), error: ${result.stderr}")
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to tap at coordinates ($x, $y): ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error tapping at coordinates ($x, $y)", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error tapping at coordinates: ${e.message ?: "Unknown exception"}"
            )
        }
    }
    
    /**
     * Simulates a click on an element identified by resource ID, text, content description, or class name
     */
    suspend fun clickElement(tool: AITool): ToolResult {
        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val text = tool.parameters.find { it.name == "text" }?.value
        val contentDesc = tool.parameters.find { it.name == "contentDesc" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0
        val partialMatch = tool.parameters.find { it.name == "partialMatch" }?.value?.toBoolean() ?: false
        
        if (resourceId == null && text == null && contentDesc == null && className == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Missing element identifier. Provide at least one of: 'resourceId', 'text', 'contentDesc', or 'className'."
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
                    error = "Failed to dump UI hierarchy: ${dumpResult.stderr ?: "Unknown error"}"
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
                    error = "Failed to read UI hierarchy: ${readResult.stderr ?: "Unknown error"}"
                )
            }
            
            // Parse the XML to find the element
            val xml = readResult.stdout
            
            // Define regex patterns for matching element attributes
            // For resource IDs, we need to be more precise to match complete IDs
            val resourceIdPattern = if (resourceId != null) {
                if (partialMatch) {
                    "resource-id=\".*?${Regex.escape(resourceId)}.*?\"".toRegex()
                } else {
                    // More precise matching for exact resource IDs (must end with the ID)
                    // This helps with cases where one ID is a subset of another
                    "resource-id=\"(?:.*?:id/)?${Regex.escape(resourceId)}\"".toRegex()
                }
            } else {
                "resource-id=\".*?\"".toRegex()
            }
            
            val textPattern = if (text != null) {
                if (partialMatch) {
                    "text=\".*?${Regex.escape(text)}.*?\"".toRegex()
                } else {
                    "text=\"${Regex.escape(text)}\"".toRegex()
                }
            } else {
                "text=\".*?\"".toRegex()
            }
            
            val contentDescPattern = if (contentDesc != null) {
                if (partialMatch) {
                    "content-desc=\".*?${Regex.escape(contentDesc)}.*?\"".toRegex()
                } else {
                    "content-desc=\"${Regex.escape(contentDesc)}\"".toRegex()
                }
            } else {
                "content-desc=\".*?\"".toRegex()
            }
            
            val classNamePattern = if (className != null) {
                "class=\".*?${Regex.escape(className)}.*?\"".toRegex()
            } else {
                "class=\".*?\"".toRegex()
            }
            
            val boundsPattern = "bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"".toRegex()
            
            // Find nodes in XML that match our criteria
            // First, try to build a more precise regex based on which criteria are provided
            val matchingNodes = if (resourceId != null) {
                // For resourceId, extract complete node elements to ensure we're matching correctly
                val nodePattern = if (partialMatch) {
                    "<node[^>]*?resource-id=\".*?${Regex.escape(resourceId)}.*?\"[^>]*?>".toRegex()
                } else {
                    // More precise matching for resourceIds
                    "<node[^>]*?resource-id=\"(?:.*?:id/)?${Regex.escape(resourceId)}\"[^>]*?>".toRegex()
                }
                nodePattern.findAll(xml).toList()
            } else {
                // Build pattern for other criteria
                val nodeRegexPattern = StringBuilder("<node[^>]*?")
                
                if (text != null) nodeRegexPattern.append(".*?$textPattern")
                if (contentDesc != null) nodeRegexPattern.append(".*?$contentDescPattern")
                if (className != null) nodeRegexPattern.append(".*?$classNamePattern")
                
                nodeRegexPattern.append("[^>]*?>")
                
                val nodeRegex = nodeRegexPattern.toString().toRegex()
                nodeRegex.findAll(xml).toList()
            }
            
            if (matchingNodes.isEmpty()) {
                // If no nodes found, provide a helpful error message based on what we were searching for
                val criteria = when {
                    resourceId != null -> "resource ID: $resourceId"
                    text != null -> "text: $text"
                    contentDesc != null -> "content description: $contentDesc"
                    else -> "class name: $className"
                }
                val matchType = if (partialMatch) "partial match" else "exact match"
                
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "No element found with $criteria ($matchType)."
                )
            }
            
            // Log how many matching nodes we found
            Log.d(TAG, "Found ${matchingNodes.size} matching elements for clickElement")
            
            // Check if index is within range
            if (index < 0 || index >= matchingNodes.size) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Index out of range. Found ${matchingNodes.size} matching elements, but requested index $index."
                )
            }
            
            // Get the node at the specified index
            val node = matchingNodes[index]
            
            // Debug log the matched node to help with troubleshooting
            Log.d(TAG, "Selected node: ${node.value.take(200)}${if (node.value.length > 200) "..." else ""}")
            
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
            
            // Log the tap coordinates
            Log.d(TAG, "Tapping element at coordinates: ($centerX, $centerY)")
            
            // Execute the tap command at the center point
            val tapCommand = "input tap $centerX $centerY"
            val tapResult = AdbCommandExecutor.executeAdbCommand(tapCommand)
            
            if (tapResult.success) {
                val identifierDescription = when {
                    resourceId != null -> " with resource ID: $resourceId"
                    text != null -> " with text: $text"
                    contentDesc != null -> " with content description: $contentDesc"
                    else -> " with class name: $className"
                }
                
                val matchCount = if (matchingNodes.size > 1) {
                    " (index $index of ${matchingNodes.size} matches)"
                } else {
                    ""
                }
                
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Successfully clicked element$identifierDescription$matchCount at coordinates ($centerX, $centerY)",
                    error = ""
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = "",
                    error = "Failed to click element: ${tapResult.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking element", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error clicking element: ${e.message ?: "Unknown exception"}"
            )
        }
    }
    
    /**
     * Sets text in an input field
     */
    suspend fun setInputText(tool: AITool): ToolResult {
        val text = tool.parameters.find { it.name == "text" }?.value ?: ""
        
        // If no text parameter is provided, use an empty string but still log the issue
        if (text.isEmpty()) {
            Log.w(TAG, "Empty text provided to setInputText, will clear field only")
        }
        
        return try {
            // First clear the field by sending DEL key events
            Log.d(TAG, "Clearing text field with DEL keyevents")
            
            // First try select all (CTRL+A) then delete
            val selectAllCommand = "input keyevent KEYCODE_CTRL_A"
            AdbCommandExecutor.executeAdbCommand(selectAllCommand)
            
            // Then press delete - do this a few times to ensure the field is clear
            val deleteCommand = "input keyevent KEYCODE_DEL"
            repeat(5) { // Send delete a few times to make sure the field is clear
                AdbCommandExecutor.executeAdbCommand(deleteCommand)
            }
            
            // Short delay before typing
            kotlinx.coroutines.delay(300)
            
            // If text is empty, we're done (just wanted to clear the field)
            if (text.isEmpty()) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = "Successfully cleared input field",
                    error = ""
                )
            }
            
            Log.d(TAG, "Setting text to: $text")
            
            // Try multiple approaches to input text
            var success = false
            var errorMessage = ""
            
            // Approach 1: Use keyevent mapping (most reliable but limited to ASCII)
            try {
                success = typeUsingKeyEvents(text)
                
                if (success) {
                    Log.d(TAG, "Successfully input text using keyevents")
                } else {
                    Log.w(TAG, "Failed to input text using keyevents, will try alternative methods")
                    errorMessage = "Failed to input text using keyevents"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error using keyevents for text input", e)
                errorMessage = "Error using keyevents: ${e.message ?: "Unknown error"}"
            }
            
            // Approach 2: Try direct text input if keyevents failed
            if (!success) {
                try {
                    // Try without any quotes or escaping first - this works in some cases
                    val noQuotesCommand = "input text $text"
                    val noQuotesResult = AdbCommandExecutor.executeAdbCommand(noQuotesCommand)
                    
                    if (noQuotesResult.success) {
                        success = true
                        Log.d(TAG, "Successfully input text using direct command without quotes")
                    } else {
                        // Try with double quotes and basic escaping
                        val safeText = text.replace("\"", "\\\"").replace("$", "\\$")
                        val quotedCommand = "input text \"$safeText\""
                        val quotedResult = AdbCommandExecutor.executeAdbCommand(quotedCommand)
                        
                        if (quotedResult.success) {
                            success = true
                            Log.d(TAG, "Successfully input text using quoted command")
                        } else {
                            Log.w(TAG, "Failed to input text using quoted command: ${quotedResult.stderr}")
                            errorMessage = "Failed to input text with quotes: ${quotedResult.stderr ?: "Unknown error"}"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error with direct text input commands", e)
                    errorMessage = "Error with direct input: ${e.message ?: "Unknown error"}"
                }
            }
            
            // Approach 3: Try character by character with fallback to keycode map
            if (!success) {
                try {
                    Log.d(TAG, "Trying character-by-character with mixed strategy")
                    success = typeCharByCharMixed(text)
                    
                    if (success) {
                        Log.d(TAG, "Successfully input text using character-by-character mixed approach")
                    } else {
                        Log.e(TAG, "All text input methods failed")
                        errorMessage = "All text input methods failed"
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in character-by-character mixed approach", e)
                    errorMessage = "Error in mixed character approach: ${e.message ?: "Unknown error"}"
                }
            }
            
            if (success) {
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
                    error = "Failed to set input text: $errorMessage"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting input text", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = "",
                error = "Error setting input text: ${e.message ?: "Unknown exception"}"
            )
        }
    }
    
    /**
     * Types text character by character using a mix of strategies
     * Tries different approaches for each character
     */
    private suspend fun typeCharByCharMixed(text: String): Boolean {
        Log.d(TAG, "Typing text using mixed strategy: $text")
        try {
            for (char in text) {
                // Try several methods for each character until one works
                
                // Method 1: Try input text with double quotes for this single character
                val charCommand = "input text \"$char\""
                val result = AdbCommandExecutor.executeAdbCommand(charCommand)
                
                if (result.success) {
                    // This method worked, continue to next character
                    kotlinx.coroutines.delay(50)
                    continue
                }
                
                // Method 2: Try input text without quotes for this character
                val plainCommand = "input text $char"
                val plainResult = AdbCommandExecutor.executeAdbCommand(plainCommand)
                
                if (plainResult.success) {
                    // This method worked, continue to next character
                    kotlinx.coroutines.delay(50)
                    continue
                }
                
                // Method 3: Try using keyevent for this character
                val keycodeSuccess = typeCharUsingKeycode(char)
                
                if (keycodeSuccess) {
                    // Keyevent method worked, continue to next character
                    kotlinx.coroutines.delay(50)
                    continue
                }
                
                // If we reach here, all methods failed for this character
                Log.e(TAG, "Failed to type character '$char' using all available methods")
                return false
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error in mixed character typing", e)
            return false
        }
    }
    
    /**
     * Map of characters to Android key codes for common characters
     */
    private val keyCodeMap = mapOf(
        'a' to "KEYCODE_A",
        'b' to "KEYCODE_B", 
        'c' to "KEYCODE_C",
        'd' to "KEYCODE_D",
        'e' to "KEYCODE_E",
        'f' to "KEYCODE_F",
        'g' to "KEYCODE_G",
        'h' to "KEYCODE_H",
        'i' to "KEYCODE_I",
        'j' to "KEYCODE_J",
        'k' to "KEYCODE_K",
        'l' to "KEYCODE_L",
        'm' to "KEYCODE_M",
        'n' to "KEYCODE_N",
        'o' to "KEYCODE_O",
        'p' to "KEYCODE_P",
        'q' to "KEYCODE_Q",
        'r' to "KEYCODE_R",
        's' to "KEYCODE_S",
        't' to "KEYCODE_T",
        'u' to "KEYCODE_U",
        'v' to "KEYCODE_V",
        'w' to "KEYCODE_W",
        'x' to "KEYCODE_X",
        'y' to "KEYCODE_Y",
        'z' to "KEYCODE_Z",
        'A' to "KEYCODE_SHIFT_LEFT KEYCODE_A",
        'B' to "KEYCODE_SHIFT_LEFT KEYCODE_B",
        'C' to "KEYCODE_SHIFT_LEFT KEYCODE_C",
        'D' to "KEYCODE_SHIFT_LEFT KEYCODE_D",
        'E' to "KEYCODE_SHIFT_LEFT KEYCODE_E",
        'F' to "KEYCODE_SHIFT_LEFT KEYCODE_F",
        'G' to "KEYCODE_SHIFT_LEFT KEYCODE_G",
        'H' to "KEYCODE_SHIFT_LEFT KEYCODE_H",
        'I' to "KEYCODE_SHIFT_LEFT KEYCODE_I",
        'J' to "KEYCODE_SHIFT_LEFT KEYCODE_J",
        'K' to "KEYCODE_SHIFT_LEFT KEYCODE_K",
        'L' to "KEYCODE_SHIFT_LEFT KEYCODE_L",
        'M' to "KEYCODE_SHIFT_LEFT KEYCODE_M",
        'N' to "KEYCODE_SHIFT_LEFT KEYCODE_N",
        'O' to "KEYCODE_SHIFT_LEFT KEYCODE_O",
        'P' to "KEYCODE_SHIFT_LEFT KEYCODE_P",
        'Q' to "KEYCODE_SHIFT_LEFT KEYCODE_Q",
        'R' to "KEYCODE_SHIFT_LEFT KEYCODE_R",
        'S' to "KEYCODE_SHIFT_LEFT KEYCODE_S",
        'T' to "KEYCODE_SHIFT_LEFT KEYCODE_T",
        'U' to "KEYCODE_SHIFT_LEFT KEYCODE_U",
        'V' to "KEYCODE_SHIFT_LEFT KEYCODE_V",
        'W' to "KEYCODE_SHIFT_LEFT KEYCODE_W",
        'X' to "KEYCODE_SHIFT_LEFT KEYCODE_X",
        'Y' to "KEYCODE_SHIFT_LEFT KEYCODE_Y",
        'Z' to "KEYCODE_SHIFT_LEFT KEYCODE_Z",
        '0' to "KEYCODE_0",
        '1' to "KEYCODE_1",
        '2' to "KEYCODE_2",
        '3' to "KEYCODE_3",
        '4' to "KEYCODE_4",
        '5' to "KEYCODE_5",
        '6' to "KEYCODE_6",
        '7' to "KEYCODE_7",
        '8' to "KEYCODE_8",
        '9' to "KEYCODE_9",
        ' ' to "KEYCODE_SPACE",
        '.' to "KEYCODE_PERIOD",
        ',' to "KEYCODE_COMMA",
        '\n' to "KEYCODE_ENTER",
        '!' to "KEYCODE_SHIFT_LEFT KEYCODE_1",
        '@' to "KEYCODE_SHIFT_LEFT KEYCODE_2",
        '#' to "KEYCODE_SHIFT_LEFT KEYCODE_3",
        '$' to "KEYCODE_SHIFT_LEFT KEYCODE_4",
        '%' to "KEYCODE_SHIFT_LEFT KEYCODE_5",
        '^' to "KEYCODE_SHIFT_LEFT KEYCODE_6",
        '&' to "KEYCODE_SHIFT_LEFT KEYCODE_7",
        '*' to "KEYCODE_SHIFT_LEFT KEYCODE_8",
        '(' to "KEYCODE_SHIFT_LEFT KEYCODE_9",
        ')' to "KEYCODE_SHIFT_LEFT KEYCODE_0",
        '-' to "KEYCODE_MINUS",
        '_' to "KEYCODE_SHIFT_LEFT KEYCODE_MINUS",
        '=' to "KEYCODE_EQUALS",
        '+' to "KEYCODE_SHIFT_LEFT KEYCODE_EQUALS",
        '[' to "KEYCODE_LEFT_BRACKET",
        '{' to "KEYCODE_SHIFT_LEFT KEYCODE_LEFT_BRACKET",
        ']' to "KEYCODE_RIGHT_BRACKET",
        '}' to "KEYCODE_SHIFT_LEFT KEYCODE_RIGHT_BRACKET",
        ';' to "KEYCODE_SEMICOLON",
        ':' to "KEYCODE_SHIFT_LEFT KEYCODE_SEMICOLON",
        '\'' to "KEYCODE_APOSTROPHE",
        '\"' to "KEYCODE_SHIFT_LEFT KEYCODE_APOSTROPHE",
        '\\' to "KEYCODE_BACKSLASH",
        '|' to "KEYCODE_SHIFT_LEFT KEYCODE_BACKSLASH",
        '/' to "KEYCODE_SLASH",
        '?' to "KEYCODE_SHIFT_LEFT KEYCODE_SLASH",
        '<' to "KEYCODE_SHIFT_LEFT KEYCODE_COMMA",
        '>' to "KEYCODE_SHIFT_LEFT KEYCODE_PERIOD",
        '`' to "KEYCODE_GRAVE",
        '~' to "KEYCODE_SHIFT_LEFT KEYCODE_GRAVE"
    )
    
    /**
     * Types a character using keyevents
     */
    private suspend fun typeCharUsingKeycode(char: Char): Boolean {
        val keycode = keyCodeMap[char]
        if (keycode == null) {
            Log.d(TAG, "No keycode mapping found for character: $char")
            return false
        }
        
        // Handle composite keycodes (like shift + key)
        val keycodes = keycode.split(" ")
        
        for (code in keycodes) {
            val command = "input keyevent $code"
            val result = AdbCommandExecutor.executeAdbCommand(command)
            
            if (!result.success) {
                Log.e(TAG, "Failed to input keyevent $code for character $char: ${result.stderr}")
                return false
            }
            
            // Small delay between keypresses in a sequence
            if (keycodes.size > 1) {
                kotlinx.coroutines.delay(50)
            }
        }
        
        return true
    }
    
    /**
     * Types text entirely using keyevents
     */
    private suspend fun typeUsingKeyEvents(text: String): Boolean {
        Log.d(TAG, "Typing text using keyevents: $text")
        
        try {
            for (char in text) {
                val success = typeCharUsingKeycode(char)
                
                if (!success) {
                    // If we can't type this character with keyevents, inform caller
                    // so they can try other methods
                    return false
                }
                
                // Small delay between characters
                kotlinx.coroutines.delay(50)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error typing using keyevents", e)
            return false
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
                        error = "Invalid click_element operation. Format: click_element type value [index] [partialMatch]"
                    )
                }
                
                val identifierType = operationParts[1]
                val identifierValue = operationParts[2]
                
                // Handle potential index parameter (optional)
                val index = if (operationParts.size > 3 && operationParts[3].toIntOrNull() != null) {
                    operationParts[3].toInt()
                } else {
                    0 // Default to first element
                }
                
                // Handle potential partialMatch parameter (optional)
                val partialMatch = if (operationParts.size > 4) {
                    operationParts[4].toBoolean()
                } else {
                    false // Default to exact match
                }
                
                if (identifierType !in listOf("resourceId", "text", "contentDesc", "className")) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = "",
                        error = "Invalid identifier type for click_element. Must be 'resourceId', 'text', 'contentDesc', or 'className'."
                    )
                }
                
                // Log the click_element operation details for debugging
                Log.d(TAG, "click_element operation: type=$identifierType, value=$identifierValue, index=$index, partialMatch=$partialMatch")
                
                val parameters = mutableListOf<ToolParameter>()
                parameters.add(ToolParameter(identifierType, identifierValue))
                parameters.add(ToolParameter("index", index.toString()))
                parameters.add(ToolParameter("partialMatch", partialMatch.toString()))
                
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
                "click_element" -> {
                    val indexInfo = if (operationParts.size > 3 && operationParts[3].toIntOrNull() != null) {
                        " at index ${operationParts[3]}"
                    } else {
                        ""
                    }
                    val partialMatchInfo = if (operationParts.size > 4 && operationParts[4] == "true") {
                        " (partial match)"
                    } else {
                        ""
                    }
                    "Clicked element with ${operationParts[1]}: ${operationParts[2]}$indexInfo$partialMatchInfo"
                }
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