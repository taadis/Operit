package com.ai.assistance.operit.tools.defaultTool

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.AdbCommandExecutor
import com.ai.assistance.operit.model.AITool
import com.ai.assistance.operit.model.ToolParameter
import com.ai.assistance.operit.model.ToolResult
import com.ai.assistance.operit.tools.CombinedOperationResultData
import com.ai.assistance.operit.tools.SimplifiedUINode
import com.ai.assistance.operit.tools.StringResultData
import com.ai.assistance.operit.tools.UIActionResultData
import com.ai.assistance.operit.tools.UIPageResultData
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Tools for UI automation via ADB shell commands
 */
class UITools(private val context: Context) {
    
    companion object {
        private const val TAG = "UITools"
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
                result = StringResultData(""),
                error = "Invalid format specified. Must be 'xml' or 'json'."
            )
        }
        
        return try {
            // 获取UI数据
            val uiData = getUIData() ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to retrieve UI data."
            )
            
            // 解析当前窗口信息
            val focusInfo = extractFocusInfo(uiData.windowInfo)
            
            // 简化布局信息
            val simplifiedLayout = simplifyLayout(uiData.uiXml)
            
            // 创建结构化数据
            val resultData = UIPageResultData(
                packageName = focusInfo.packageName ?: "Unknown",
                activityName = focusInfo.activityName ?: "Unknown",
                uiElements = simplifiedLayout
            )
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = resultData,
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting page info", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
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
            // 1. 首先尝试使用无障碍服务获取UI层次结构
            val uiXml = com.ai.assistance.operit.data.UIHierarchyManager.getUIHierarchy(context)
            
            // 如果成功获取到UI层次结构
            if (uiXml.isNotEmpty()) {
                Log.d(TAG, "使用无障碍服务获取UI层次结构成功")
                
                // 仍然需要获取窗口信息（使用ADB命令）
                val windowCommand = "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'"
                val windowResult = AdbCommandExecutor.executeAdbCommand(windowCommand)
                
                if (windowResult.success) {
                    return UIData(uiXml, windowResult.stdout)
                }
            }
            
            // 2. 如果无障碍服务获取失败，回退到使用ADB命令
            Log.d(TAG, "回退到使用ADB命令获取UI数据")
            
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

    
    data class UINode(
        val className: String?,
        val text: String?,
        val contentDesc: String?,
        val resourceId: String?,
        val bounds: String?,
        val isClickable: Boolean,  // 新增点击状态
        val children: MutableList<UINode> = mutableListOf()
    )
    
    fun simplifyLayout(xml: String): SimplifiedUINode {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser().apply {
            setInput(StringReader(xml))
        }
    
        val nodeStack = mutableListOf<UINode>()
        var rootNode: UINode? = null
    
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
    
        // Convert SimplifiedNode to SimplifiedUINode
        return rootNode?.toUINode() ?: SimplifiedUINode(
            className = null,
            text = null,
            contentDesc = null,
            resourceId = null,
            bounds = null,
            isClickable = false,
            children = emptyList()
        )
    }
    
    // Extension function to convert SimplifiedNode to SimplifiedUINode
    private fun UINode.toUINode(): SimplifiedUINode {
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
    
    private fun createNode(parser: XmlPullParser): UINode {
        // 解析关键属性
        val className = parser.getAttributeValue(null, "class")?.substringAfterLast('.')
        val text = parser.getAttributeValue(null, "text")?.replace("&#10;", "\n")
        val contentDesc = parser.getAttributeValue(null, "content-desc")
        val resourceId = parser.getAttributeValue(null, "resource-id")
        val bounds = parser.getAttributeValue(null, "bounds")
        val isClickable = parser.getAttributeValue(null, "clickable") == "true"
    
        return UINode(
            className = className,
            text = text,
            contentDesc = contentDesc,
            resourceId = resourceId,
            bounds = bounds,
            isClickable = isClickable
        )
    }
    
    private fun UINode.shouldKeepNode(): Boolean {
        // 保留条件：关键元素类型 或 有内容 或 可点击 或 包含需要保留的子节点
        val isKeyElement = className in setOf(
            "Button", "TextView", "EditText", 
            "ScrollView", "Switch", "ImageView"
        )
        val hasContent = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()
        
        return isKeyElement || hasContent || isClickable || children.any { it.shouldKeepNode() }
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
                result = StringResultData(""),
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
                    result = UIActionResultData(
                        actionType = "tap",
                        actionDescription = "Successfully tapped at coordinates ($x, $y)",
                        coordinates = Pair(x, y)
                    ),
                    error = ""
                )
            } else {
                Log.e(TAG, "Tap failed at coordinates: ($x, $y), error: ${result.stderr}")
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to tap at coordinates ($x, $y): ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error tapping at coordinates ($x, $y)", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error tapping at coordinates: ${e.message ?: "Unknown exception"}"
            )
        }
    }
    
    /**
     * Simulates a click on an element identified by resource ID or class name
     */
    suspend fun clickElement(tool: AITool): ToolResult {
        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0
        val partialMatch = tool.parameters.find { it.name == "partialMatch" }?.value?.toBoolean() ?: false
        
        if (resourceId == null && className == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing element identifier. Provide at least one of: 'resourceId' or 'className'."
            )
        }
        
        return try {
            // 优先使用无障碍服务尝试点击元素
            val uiXml = com.ai.assistance.operit.data.UIHierarchyManager.getUIHierarchy(context)
            if (uiXml.isNotEmpty()) {
                // 如果通过无障碍服务获取到了UI层次结构，则尝试直接使用这个数据而不再调用ADB dump
                Log.d(TAG, "使用无障碍服务获取的UI层次结构")
                
                // 使用已经获取的UI XML数据处理点击
                // 解析XML，定位元素并获取坐标
                val matchingNodes = findMatchingNodesInXml(uiXml, resourceId, className, partialMatch)
                
                if (matchingNodes.isNotEmpty()) {
                    // 处理点击
                    return processClickOnFoundNodes(matchingNodes, index, tool, resourceId, className)
                }
            }
            
            // 如果无障碍服务不可用或未找到匹配元素，回退到使用ADB命令
            Log.d(TAG, "回退到使用ADB命令获取UI数据和点击元素")
            
            // First, dump the UI hierarchy
            val dumpCommand = "uiautomator dump /sdcard/window_dump.xml"
            val dumpResult = AdbCommandExecutor.executeAdbCommand(dumpCommand)
            
            if (!dumpResult.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
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
                    result = StringResultData(""),
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
                // Build pattern for class name
                val nodeRegexPattern = StringBuilder("<node[^>]*?")
                
                if (className != null) nodeRegexPattern.append(".*?$classNamePattern")
                
                nodeRegexPattern.append("[^>]*?>")
                
                val nodeRegex = nodeRegexPattern.toString().toRegex()
                nodeRegex.findAll(xml).toList()
            }
            
            if (matchingNodes.isEmpty()) {
                // If no nodes found, provide a helpful error message based on what we were searching for
                val criteria = when {
                    resourceId != null -> "resource ID: $resourceId"
                    else -> "class name: $className"
                }
                val matchType = if (partialMatch) "partial match" else "exact match"
                
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
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
                    result = StringResultData(""),
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
                    result = StringResultData(""),
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
                    else -> " with class name: $className"
                }
                
                val matchCount = if (matchingNodes.size > 1) {
                    " (index $index of ${matchingNodes.size} matches)"
                } else {
                    ""
                }
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = UIActionResultData(
                        actionType = "click",
                        actionDescription = "Successfully clicked element$identifierDescription$matchCount at coordinates ($centerX, $centerY)",
                        coordinates = Pair(centerX, centerY),
                        elementId = resourceId ?: className
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to click element: ${tapResult.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking element", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
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
                    result = UIActionResultData(
                        actionType = "textInput",
                        actionDescription = "Successfully cleared input field"
                    ),
                    error = ""
                )
            }
            
            Log.d(TAG, "Setting text to: $text")
            
            // First try to set text using accessibility service
            var success = false
            
            // Try to use accessibility service first
            if (com.ai.assistance.operit.service.UIAccessibilityService.isRunning()) {
                success = setTextWithAccessibility(text)
                if (success) {
                    Log.d(TAG, "Successfully set text using accessibility service")
                } else {
                    Log.w(TAG, "Failed to set text using accessibility service, falling back to clipboard method")
                }
            }
            
            // If accessibility method failed or isn't available, use clipboard method as fallback
            if (!success) {
                success = setTextViaClipboard(text)
            }
            
            if (success) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = UIActionResultData(
                        actionType = "textInput",
                        actionDescription = "Successfully set input text to: $text"
                    ),
                    error = ""
                )
            } else {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to set input text via all available methods"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting input text", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error setting input text: ${e.message ?: "Unknown exception"}"
            )
        }
    }
    
    /**
     * Sets text using the accessibility service
     * This is the preferred method when accessible
     */
    private fun setTextWithAccessibility(text: String): Boolean {
        try {
            val service = com.ai.assistance.operit.service.UIAccessibilityService.getInstance() ?: return false
            
            // Get the root node
            val rootNode = service.rootInActiveWindow ?: return false
            
            // Find the focused node, which is likely the input field
            val focusedNode = findFocusedEditableNode(rootNode)
            if (focusedNode == null) {
                Log.w(TAG, "No focused editable node found for text input")
                return false
            }
            
            // Create a bundle with the text to set
            val arguments = android.os.Bundle()
            arguments.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            
            // Perform the action to set the text
            val result = focusedNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            // Clean up
            focusedNode.recycle()
            rootNode.recycle()
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error setting text with accessibility service", e)
            return false
        }
    }
    
    /**
     * Finds a focused node that can accept text input
     */
    private fun findFocusedEditableNode(rootNode: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
        // First check if the root node itself is focused and editable
        if (rootNode.isFocused && rootNode.isEditable) {
            return rootNode
        }
        
        // Check if we can find a focused node
        val focusedNode = rootNode.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            return focusedNode
        }
        
        // If no focused node, try to find any editable field
        val editableNodes = ArrayList<android.view.accessibility.AccessibilityNodeInfo>()
        findEditableNodes(rootNode, editableNodes)
        
        if (editableNodes.isNotEmpty()) {
            // Return the first editable node
            val result = editableNodes[0]
            // Clean up the other nodes
            for (i in 1 until editableNodes.size) {
                editableNodes[i].recycle()
            }
            return result
        }
        
        return null
    }
    
    /**
     * Recursively finds all editable nodes in the hierarchy
     */
    private fun findEditableNodes(node: android.view.accessibility.AccessibilityNodeInfo, result: ArrayList<android.view.accessibility.AccessibilityNodeInfo>) {
        if (node.isEditable) {
            // Make a copy of the node to add to our result list
            result.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node))
        }
        
        // Recursively check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditableNodes(child, result)
            child.recycle()
        }
    }
    
    /**
     * Sets text via clipboard and paste operation
     * This method is used as a fallback when accessibility method isn't available
     */
    private suspend fun setTextViaClipboard(text: String): Boolean {
        try {
            Log.d(TAG, "Setting clipboard text: $text")
            
            // Set clipboard text using native Android API
            if (!setClipboardNatively(text)) {
                Log.e(TAG, "Failed to set clipboard using native Android API")
                return false
            }
            
            // Give the system time to update the clipboard
            kotlinx.coroutines.delay(300)
            
            // Try to paste the text using CTRL+V
            val pasteCommand = "input keyevent 279 47"  // KEYCODE_CTRL_LEFT KEYCODE_V
            val pasteResult = AdbCommandExecutor.executeAdbCommand(pasteCommand)
            
            if (pasteResult.success) {
                Log.d(TAG, "Pasted text using CTRL+V method")
                return true
            }
            
            // If that fails, try the named keycode version
            val namedPasteCommand = "input keyevent KEYCODE_CTRL_LEFT KEYCODE_V"
            val namedPasteResult = AdbCommandExecutor.executeAdbCommand(namedPasteCommand)
            
            if (namedPasteResult.success) {
                Log.d(TAG, "Pasted text using named keycode method")
                return true
            }
            
            Log.e(TAG, "All paste methods failed")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error setting text via clipboard", e)
            return false
        }
    }
    
    /**
     * 使用Android原生API设置剪贴板
     */
    private fun setClipboardNatively(text: String): Boolean {
        return try {
            // 获取剪贴板管理器服务
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            
            // 创建一个新的剪贴板数据
            val clipData = android.content.ClipData.newPlainText("text", text)
            
            // 设置剪贴板内容
            clipboardManager.setPrimaryClip(clipData)
            
            // 给系统一点时间更新剪贴板
            Thread.sleep(200)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting clipboard using native API", e)
            false
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
                result = StringResultData(""),
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
                    result = UIActionResultData(
                        actionType = "keyPress",
                        actionDescription = "Successfully pressed key: $keyCode"
                    ),
                    error = ""
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to press key: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pressing key", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
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
                result = StringResultData(""),
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
                    result = UIActionResultData(
                        actionType = "swipe",
                        actionDescription = "Successfully performed swipe from ($startX, $startY) to ($endX, $endY)"
                    ),
                    error = ""
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to perform swipe: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing swipe", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
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
                result = StringResultData(""),
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
                    result = UIActionResultData(
                        actionType = "launchApp",
                        actionDescription = "Successfully launched app: $packageName"
                    ),
                    error = ""
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to launch app: ${result.stderr}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error launching app: ${e.message}"
            )
        }
    }
    
    /**
     * Performs a combined operation: execute an action, wait, then return the new UI state
     */
    suspend fun combinedOperation(tool: AITool): ToolResult {
        val operation = tool.parameters.find { it.name == "operation" }?.value
        val delayMs = tool.parameters.find { it.name == "delayMs" }?.value?.toIntOrNull() ?: 1000
        
        if (operation == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing 'operation' parameter. Must specify which operation to perform."
            )
        }
        
        // Parse the operation to determine which tool to execute
        val operationParts = operation.trim().split(" ")
        if (operationParts.isEmpty()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
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
                        result = StringResultData(""),
                        error = "Invalid tap operation. Format: tap x y"
                    )
                }
                
                val x = operationParts[1].toIntOrNull()
                val y = operationParts[2].toIntOrNull()
                
                if (x == null || y == null) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
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
                        result = StringResultData(""),
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
                        result = StringResultData(""),
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
                        result = StringResultData(""),
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
                
                if (identifierType !in listOf("resourceId", "className")) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Invalid identifier type for click_element. Must be 'resourceId' or 'className'."
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
                        result = StringResultData(""),
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
                        result = StringResultData(""),
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
                        result = StringResultData(""),
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
                    result = StringResultData(""),
                    error = "Unsupported operation: $operationType. Supported operations: tap, swipe, click_element, press_key, set_input_text, launch_app"
                )
            }
        }
        
        // If the operation failed, return the error
        if (!operationResult.success) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Operation failed: ${operationResult.error}"
            )
        }
        
        // Wait for the specified delay
        try {
            kotlinx.coroutines.delay(delayMs.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "Error during delay", e)
        }
        
        // Get UI state after operation
        try {
            // Create page info tool with appropriate parameters
            val pageInfoTool = AITool(
                name = "get_page_info",
                parameters = listOf(
                    ToolParameter("format", "xml"),
                    ToolParameter("detail", "summary")
                )
            )
            
            // Call getPageInfo to get UI state
            val pageInfoResult = getPageInfo(pageInfoTool)
            
            if (!pageInfoResult.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Operation succeeded but failed to get UI state: ${pageInfoResult.error}"
                )
            }
            
            // Build operation summary
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
            
            // Ensure we have a UIPageResultData
            if (pageInfoResult.result is UIPageResultData) {
                val pageInfo = pageInfoResult.result
                
                // Combine operation summary and UI state
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = CombinedOperationResultData(
                        operationSummary = operationSummary,
                        waitTime = delayMs,
                        pageInfo = pageInfo
                    ),
                    error = ""
                )
            } else {
                // Convert string result to UIPageResultData if needed
                val defaultPageInfo = UIPageResultData(
                    packageName = "Unknown",
                    activityName = "Unknown",
                    uiElements = com.ai.assistance.operit.tools.SimplifiedUINode(
                        className = "Root",
                        text = pageInfoResult.result.toString(),
                        contentDesc = null,
                        resourceId = null,
                        bounds = null,
                        isClickable = false,
                        children = emptyList()
                    )
                )
                
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = CombinedOperationResultData(
                        operationSummary = operationSummary,
                        waitTime = delayMs,
                        pageInfo = defaultPageInfo
                    ),
                    error = ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting UI state after operation", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData("Error getting UI state: ${e.message}"),
                error = "Error getting UI state after operation: ${e.message}"
            )
        }
    }
    
    /**
     * 从XML字符串中查找匹配的节点
     */
    private fun findMatchingNodesInXml(xml: String, resourceId: String?, className: String?, partialMatch: Boolean): List<Pair<String, List<String>>> {
        val matchingNodes = mutableListOf<Pair<String, List<String>>>()
        
        // 定义regex模式以匹配节点属性
        val resourceIdPattern = if (resourceId != null) {
            if (partialMatch) {
                "resource-id=\".*?${Regex.escape(resourceId)}.*?\"".toRegex()
            } else {
                "resource-id=\"(?:.*?:id/)?${Regex.escape(resourceId)}\"".toRegex()
            }
        } else {
            "resource-id=\".*?\"".toRegex()
        }
        
        val classNamePattern = if (className != null) {
            "class=\".*?${Regex.escape(className)}.*?\"".toRegex()
        } else {
            "class=\".*?\"".toRegex()
        }
        
        // 找到XML中匹配条件的节点
        if (resourceId != null) {
            val nodePattern = if (partialMatch) {
                "<node[^>]*?resource-id=\".*?${Regex.escape(resourceId)}.*?\"[^>]*?>".toRegex()
            } else {
                "<node[^>]*?resource-id=\"(?:.*?:id/)?${Regex.escape(resourceId)}\"[^>]*?>".toRegex()
            }
            nodePattern.findAll(xml).forEach { match ->
                // 提取bounds和其他需要的属性
                val nodeText = match.value
                val bounds = extractBounds(nodeText)
                if (bounds.isNotEmpty()) {
                    matchingNodes.add(Pair(nodeText, bounds))
                }
            }
        } else if (className != null) {
            val nodePattern = "<node[^>]*?class=\".*?${Regex.escape(className)}.*?\"[^>]*?>".toRegex()
            nodePattern.findAll(xml).forEach { match ->
                val nodeText = match.value
                val bounds = extractBounds(nodeText)
                if (bounds.isNotEmpty()) {
                    matchingNodes.add(Pair(nodeText, bounds))
                }
            }
        }
        
        return matchingNodes
    }
    
    /**
     * 从节点文本中提取bounds坐标
     */
    private fun extractBounds(nodeText: String): List<String> {
        val boundsPattern = "bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"".toRegex()
        val matchResult = boundsPattern.find(nodeText) ?: return emptyList()
        
        if (matchResult.groupValues.size < 5) return emptyList()
        
        return matchResult.groupValues.drop(1) // 返回四个坐标值
    }
    
    /**
     * 处理找到的节点并执行点击
     */
    private suspend fun processClickOnFoundNodes(
        matchingNodes: List<Pair<String, List<String>>>,
        index: Int,
        tool: AITool,
        resourceId: String?,
        className: String?
    ): ToolResult {
        // 检查索引是否在范围内
        if (index < 0 || index >= matchingNodes.size) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "索引超出范围。找到${matchingNodes.size}个匹配元素，但请求的索引为$index。"
            )
        }
        
        // 获取指定索引的节点
        val (nodeText, boundsValues) = matchingNodes[index]
        
        if (boundsValues.size < 4) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "无法从元素中提取边界坐标。"
            )
        }
        
        // 提取坐标并计算中心点
        val x1 = boundsValues[0].toInt()
        val y1 = boundsValues[1].toInt()
        val x2 = boundsValues[2].toInt()
        val y2 = boundsValues[3].toInt()
        
        // 计算中心点坐标
        val centerX = (x1 + x2) / 2
        val centerY = (y1 + y2) / 2
        
        // 记录点击坐标
        Log.d(TAG, "点击元素坐标: ($centerX, $centerY)")
        
        // 执行点击命令
        val tapCommand = "input tap $centerX $centerY"
        val tapResult = AdbCommandExecutor.executeAdbCommand(tapCommand)
        
        if (tapResult.success) {
            val identifierDescription = when {
                resourceId != null -> " with resource ID: $resourceId"
                else -> " with class name: $className"
            }
            
            val matchCount = if (matchingNodes.size > 1) {
                " (index $index of ${matchingNodes.size} matches)"
            } else {
                ""
            }
            
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = UIActionResultData(
                    actionType = "click",
                    actionDescription = "Successfully clicked element$identifierDescription$matchCount at coordinates ($centerX, $centerY)",
                    coordinates = Pair(centerX, centerY),
                    elementId = resourceId ?: className
                ),
                error = ""
            )
        } else {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to click element: ${tapResult.stderr ?: "Unknown error"}"
            )
        }
    }
}