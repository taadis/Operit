package com.ai.assistance.operit.core.tools.defaultTool.accessbility

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.UIActionResultData
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardUITools
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import java.io.StringReader
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** 无障碍级别的UI工具，使用Android无障碍服务API实现UI操作 */
open class AccessibilityUITools(context: Context) : StandardUITools(context) {

    companion object {
        private const val TAG = "AccessibilityUITools"
    }

    /** Gets the current UI page/window information */
    override suspend fun getPageInfo(tool: AITool): ToolResult {
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
            // 使用无障碍服务获取UI数据
            val uiXml = UIHierarchyManager.getUIHierarchy(context)
            if (uiXml.isEmpty()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to retrieve UI data via accessibility service."
                )
            }

            // 解析当前窗口信息
            val focusInfo = extractFocusInfoFromAccessibility()

            // 简化布局信息
            val simplifiedLayout = simplifyLayout(uiXml)

            // 创建结构化数据
            val resultData =
                    UIPageResultData(
                            packageName = focusInfo.packageName ?: "Unknown",
                            activityName = focusInfo.activityName ?: "Unknown",
                            uiElements = simplifiedLayout
                    )

            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
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

    /** 从无障碍服务获取焦点信息 */
    private fun extractFocusInfoFromAccessibility(): FocusInfo {
        val focusInfo = FocusInfo()

        try {
            val service = com.ai.assistance.operit.services.UIAccessibilityService.getInstance()
            if (service != null) {
                // 获取当前活动窗口
                val window = service.rootInActiveWindow
                if (window != null) {
                    // 从窗口获取包名
                    focusInfo.packageName = window.packageName?.toString()

                    // 尝试从窗口属性获取活动名称
                    if (window.className != null) {
                        focusInfo.activityName = window.className.toString().substringAfterLast('.')
                    }

                    window.recycle()
                }
            }

            // 如果没有获取到，使用默认值
            if (focusInfo.packageName == null) focusInfo.packageName = "android"
            if (focusInfo.activityName == null) focusInfo.activityName = "ForegroundActivity"
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting focus info from accessibility", e)
            // 设置默认值
            focusInfo.packageName = "android"
            focusInfo.activityName = "ForegroundActivity"
        }

        return focusInfo
    }

    /** 简化XML布局为节点树 */
    fun simplifyLayout(xml: String): SimplifiedUINode {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

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

        return rootNode?.toUINode()
                ?: SimplifiedUINode(
                        className = null,
                        text = null,
                        contentDesc = null,
                        resourceId = null,
                        bounds = null,
                        isClickable = false,
                        children = emptyList()
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

    /** 点击元素 */
    override suspend fun clickElement(tool: AITool): ToolResult {
        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0
        val bounds = tool.parameters.find { it.name == "bounds" }?.value

        if (resourceId == null && className == null && bounds == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "Missing element identifier. Provide at least one of: 'resourceId', 'className', or 'bounds'."
            )
        }

        try {
            // 获取无障碍服务实例
            val service = com.ai.assistance.operit.services.UIAccessibilityService.getInstance()
            if (service == null) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Accessibility service not running."
                )
            }

            // 获取根节点
            val rootNode =
                    service.rootInActiveWindow
                            ?: return ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = StringResultData(""),
                                    error = "Unable to get root accessibility node."
                            )

            // 如果提供了边界坐标，直接解析并点击中心点
            if (bounds != null) {
                try {
                    // 解析边界坐标格式 [left,top][right,bottom]
                    val boundsPattern = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
                    val matchResult = boundsPattern.find(bounds)

                    if (matchResult == null || matchResult.groupValues.size < 5) {
                        rootNode.recycle()
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Invalid bounds format. Should be: [left,top][right,bottom]"
                        )
                    }

                    // 提取坐标
                    val x1 = matchResult.groupValues[1].toInt()
                    val y1 = matchResult.groupValues[2].toInt()
                    val x2 = matchResult.groupValues[3].toInt()
                    val y2 = matchResult.groupValues[4].toInt()

                    // 计算中心点
                    val centerX = (x1 + x2) / 2
                    val centerY = (y1 + y2) / 2

                    // 显示点击反馈
                    operationOverlay.showTap(centerX, centerY)

                    // 使用无障碍服务的自定义点击功能
                    val clickSuccess = performAccessibilityClick(centerX, centerY)

                    rootNode.recycle()
                    return if (clickSuccess) {
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        UIActionResultData(
                                                actionType = "click",
                                                actionDescription =
                                                        "Successfully clicked at bounds $bounds",
                                                coordinates = Pair(centerX, centerY),
                                                elementId = null
                                        ),
                                error = ""
                        )
                    } else {
                        operationOverlay.hide()
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error =
                                        "Failed to click at bounds $bounds via accessibility service."
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error clicking at bounds", e)
                    rootNode.recycle()
                    operationOverlay.hide()
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Error clicking at bounds: ${e.message}"
                    )
                }
            }

            // 寻找匹配的节点
            val nodes = ArrayList<android.view.accessibility.AccessibilityNodeInfo>()

            // 根据resourceId查找
            if (resourceId != null) {
                val matchingNodes = rootNode.findAccessibilityNodeInfosByViewId(resourceId)
                if (matchingNodes != null && matchingNodes.size > 0) {
                    nodes.addAll(matchingNodes)
                }
            }

            // 如果通过resourceId没有找到，尝试通过className查找
            if (nodes.isEmpty() && className != null) {
                findNodesByClassName(rootNode, className, nodes)
            }

            // 检查是否找到匹配节点
            if (nodes.isEmpty()) {
                rootNode.recycle()
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "No matching element found."
                )
            }

            // 检查索引是否有效
            if (index < 0 || index >= nodes.size) {
                // 释放所有节点
                nodes.forEach { it.recycle() }
                rootNode.recycle()

                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "Index out of range. Found ${nodes.size} elements, but requested index $index."
                )
            }

            // 获取指定索引的节点
            val nodeToClick = nodes[index]

            // 获取节点的边界
            val rect = android.graphics.Rect()
            nodeToClick.getBoundsInScreen(rect)

            // 计算中心点
            val centerX = rect.centerX()
            val centerY = rect.centerY()

            // 显示点击反馈
            operationOverlay.showTap(centerX, centerY)

            // 尝试直接点击元素
            var clickSuccess =
                    nodeToClick.performAction(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
                    )

            // 如果直接点击失败，尝试通过位置点击
            if (!clickSuccess) {
                clickSuccess = performAccessibilityClick(centerX, centerY)
            }

            // 释放所有节点
            nodes.forEach { it.recycle() }
            rootNode.recycle()

            return if (clickSuccess) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "click",
                                        actionDescription =
                                                "Successfully clicked element via accessibility service",
                                        coordinates = Pair(centerX, centerY),
                                        elementId = resourceId ?: className
                                ),
                        error = ""
                )
            } else {
                operationOverlay.hide()
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to click element via accessibility service."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking element", e)
            operationOverlay.hide()
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error clicking element: ${e.message}"
            )
        }
    }

    /** 递归查找指定类名的节点 */
    private fun findNodesByClassName(
            node: android.view.accessibility.AccessibilityNodeInfo,
            className: String,
            result: ArrayList<android.view.accessibility.AccessibilityNodeInfo>
    ) {
        if (node.className != null && node.className.toString().contains(className)) {
            result.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node))
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByClassName(child, className, result)
            child.recycle()
        }
    }

    /** 设置输入文本 */
    override suspend fun setInputText(tool: AITool): ToolResult {
        val text = tool.parameters.find { it.name == "text" }?.value ?: ""

        try {
            // 获取无障碍服务实例
            val service = com.ai.assistance.operit.services.UIAccessibilityService.getInstance()
            if (service == null) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Accessibility service not running."
                )
            }

            // 获取根节点
            val rootNode =
                    service.rootInActiveWindow
                            ?: return ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = StringResultData(""),
                                    error = "Unable to get root accessibility node."
                            )

            // 查找已有焦点的可编辑节点
            val focusedNode = findFocusedEditableNode(rootNode)
            if (focusedNode == null) {
                rootNode.recycle()
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "No focused editable field found."
                )
            }

            // 显示反馈
            val rect = android.graphics.Rect()
            focusedNode.getBoundsInScreen(rect)
            operationOverlay.showTextInput(rect.centerX(), rect.centerY(), text)

            // 创建设置文本的Bundle
            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                    android.view.accessibility.AccessibilityNodeInfo
                            .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
            )

            // 执行设置文本操作
            val result =
                    focusedNode.performAction(
                            android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                            arguments
                    )

            // 清理资源
            focusedNode.recycle()
            rootNode.recycle()

            return if (result) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "textInput",
                                        actionDescription =
                                                "Successfully set input text via accessibility service"
                                ),
                        error = ""
                )
            } else {
                operationOverlay.hide()
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to set text via accessibility service."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting input text", e)
            operationOverlay.hide()
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error setting input text: ${e.message}"
            )
        }
    }

    /** 查找有焦点的可编辑节点 */
    private fun findFocusedEditableNode(
            rootNode: android.view.accessibility.AccessibilityNodeInfo
    ): android.view.accessibility.AccessibilityNodeInfo? {
        // 首先检查根节点本身是否有焦点且可编辑
        if (rootNode.isFocused && rootNode.isEditable) {
            return android.view.accessibility.AccessibilityNodeInfo.obtain(rootNode)
        }

        // 检查是否可以找到有焦点的节点
        val focusedNode =
                rootNode.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            return focusedNode
        }

        // 如果没有找到有焦点的节点，尝试找到任何可编辑字段
        val editableNodes = ArrayList<android.view.accessibility.AccessibilityNodeInfo>()
        findEditableNodes(rootNode, editableNodes)

        if (editableNodes.isNotEmpty()) {
            // 返回第一个可编辑节点
            val result = editableNodes[0]
            // 清理其他节点
            for (i in 1 until editableNodes.size) {
                editableNodes[i].recycle()
            }
            return result
        }

        return null
    }

    /** 递归查找所有可编辑节点 */
    private fun findEditableNodes(
            node: android.view.accessibility.AccessibilityNodeInfo,
            result: ArrayList<android.view.accessibility.AccessibilityNodeInfo>
    ) {
        if (node.isEditable) {
            // 复制节点添加到结果列表
            result.add(android.view.accessibility.AccessibilityNodeInfo.obtain(node))
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findEditableNodes(child, result)
            child.recycle()
        }
    }

    /** 执行轻触操作 */
    override suspend fun tap(tool: AITool): ToolResult {
        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull()
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull()

        if (x == null || y == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "Missing or invalid coordinates. Both 'x' and 'y' must be valid integers."
            )
        }

        try {
            // 显示点击反馈
            operationOverlay.showTap(x, y)

            // 使用无障碍服务执行点击
            val result = performAccessibilityClick(x, y)

            return if (result) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "tap",
                                        actionDescription =
                                                "Successfully tapped at coordinates ($x, $y) via accessibility service",
                                        coordinates = Pair(x, y)
                                ),
                        error = ""
                )
            } else {
                operationOverlay.hide()
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to tap at coordinates via accessibility service."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error tapping at coordinates", e)
            operationOverlay.hide()
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error tapping at coordinates: ${e.message}"
            )
        }
    }

    /** 执行滑动操作 */
    override suspend fun swipe(tool: AITool): ToolResult {
        val startX = tool.parameters.find { it.name == "start_x" }?.value?.toIntOrNull()
        val startY = tool.parameters.find { it.name == "start_y" }?.value?.toIntOrNull()
        val endX = tool.parameters.find { it.name == "end_x" }?.value?.toIntOrNull()
        val endY = tool.parameters.find { it.name == "end_y" }?.value?.toIntOrNull()
        val duration = tool.parameters.find { it.name == "duration" }?.value?.toIntOrNull() ?: 300

        if (startX == null || startY == null || endX == null || endY == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "Missing or invalid coordinates. 'start_x', 'start_y', 'end_x', and 'end_y' must be valid integers."
            )
        }

        try {
            // 显示滑动反馈
            operationOverlay.showSwipe(startX, startY, endX, endY)

            // 使用无障碍服务执行滑动
            val result = performAccessibilitySwipe(startX, startY, endX, endY, duration)

            return if (result) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "swipe",
                                        actionDescription =
                                                "Successfully performed swipe from ($startX, $startY) to ($endX, $endY) via accessibility service"
                                ),
                        error = ""
                )
            } else {
                operationOverlay.hide()
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to perform swipe via accessibility service."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing swipe", e)
            operationOverlay.hide()
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error performing swipe: ${e.message}"
            )
        }
    }

    // 使用无障碍服务执行点击的辅助方法
    private fun performAccessibilityClick(x: Int, y: Int): Boolean {
        try {
            val service =
                    com.ai.assistance.operit.services.UIAccessibilityService.getInstance()
                            ?: return false

            // 使用Android标准的GestureDescription API实现点击
            val clickPath = android.graphics.Path()
            clickPath.moveTo(x.toFloat(), y.toFloat())

            // 创建手势描述构建器
            val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
            // 添加点击轨迹：从起点到终点的时间为50ms
            gestureBuilder.addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(
                            clickPath,
                            0,
                            50
                    )
            )

            // 分发手势
            return service.dispatchGesture(
                    gestureBuilder.build(),
                    null, // 不需要回调
                    null // 不需要处理程序
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error performing accessibility click", e)
            return false
        }
    }

    // 使用无障碍服务执行滑动的辅助方法
    private fun performAccessibilitySwipe(
            startX: Int,
            startY: Int,
            endX: Int,
            endY: Int,
            duration: Int
    ): Boolean {
        try {
            val service =
                    com.ai.assistance.operit.services.UIAccessibilityService.getInstance()
                            ?: return false

            // 创建滑动路径
            val swipePath = android.graphics.Path()
            swipePath.moveTo(startX.toFloat(), startY.toFloat())
            swipePath.lineTo(endX.toFloat(), endY.toFloat())

            // 创建手势描述构建器
            val gestureBuilder = android.accessibilityservice.GestureDescription.Builder()
            // 添加滑动轨迹：从起点到终点的时间为duration毫秒
            gestureBuilder.addStroke(
                    android.accessibilityservice.GestureDescription.StrokeDescription(
                            swipePath,
                            0,
                            duration.toLong()
                    )
            )

            // 分发手势
            return service.dispatchGesture(
                    gestureBuilder.build(),
                    null, // 不需要回调
                    null // 不需要处理程序
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error performing accessibility swipe", e)
            return false
        }
    }

    /** 模拟按键操作 */
    override suspend fun pressKey(tool: AITool): ToolResult {
        val keyCode = tool.parameters.find { it.name == "key_code" }?.value

        if (keyCode == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Missing 'key_code' parameter."
            )
        }

        try {
            // 获取无障碍服务实例
            val service = com.ai.assistance.operit.services.UIAccessibilityService.getInstance()
            if (service == null) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Accessibility service not running."
                )
            }

            // 尝试通过无障碍服务执行按键操作
            // 注意：标准AccessibilityService不直接支持键盘事件，但我们可以尝试使用ACTION_GLOBAL_ACTION
            val keyAction =
                    when (keyCode) {
                        "KEYCODE_BACK" ->
                                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                        "KEYCODE_HOME" ->
                                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                        "KEYCODE_RECENTS" ->
                                android.accessibilityservice.AccessibilityService
                                        .GLOBAL_ACTION_RECENTS
                        "KEYCODE_NOTIFICATIONS" ->
                                android.accessibilityservice.AccessibilityService
                                        .GLOBAL_ACTION_NOTIFICATIONS
                        "KEYCODE_QUICK_SETTINGS" ->
                                android.accessibilityservice.AccessibilityService
                                        .GLOBAL_ACTION_QUICK_SETTINGS
                        "KEYCODE_POWER_DIALOG" ->
                                android.accessibilityservice.AccessibilityService
                                        .GLOBAL_ACTION_POWER_DIALOG
                        else -> null
                    }

            if (keyAction != null) {
                val success = service.performGlobalAction(keyAction)
                return if (success) {
                    ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                                    UIActionResultData(
                                            actionType = "keyPress",
                                            actionDescription =
                                                    "Successfully pressed key: $keyCode via accessibility service"
                                    ),
                            error = ""
                    )
                } else {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error =
                                    "Failed to press key: $keyCode via accessibility service. Not all keys are supported."
                    )
                }
            } else {
                // 如果不是标准全局操作，返回不支持的错误
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "Key: $keyCode is not supported via accessibility service. Only system keys like BACK, HOME, etc. are supported."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pressing key", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error pressing key: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** 查找UI元素 */
    override suspend fun findElement(tool: AITool): ToolResult {
        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val text = tool.parameters.find { it.name == "text" }?.value
        val contentDesc = tool.parameters.find { it.name == "contentDesc" }?.value
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull()

        if (resourceId == null && className == null && text == null && contentDesc == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "At least one of resourceId, className, text, or contentDesc must be provided."
            )
        }

        try {
            // 获取当前UI层次结构
            val uiHierarchy = UIHierarchyManager.getUIHierarchy(context)
            if (uiHierarchy.isNullOrEmpty()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to get UI hierarchy."
                )
            }

            // 解析XML获取所有匹配的元素
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(StringReader(uiHierarchy))

            val matchedElements = mutableListOf<SimplifiedUINode>()
            var currentNode: SimplifiedUINode? = null
            var depth = 0

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        val nodeName = parser.name
                        if (nodeName == "node") {
                            val nodeResourceId = parser.getAttributeValue(null, "resource-id") ?: ""
                            val nodeClassName = parser.getAttributeValue(null, "class") ?: ""
                            val nodeText = parser.getAttributeValue(null, "text") ?: ""
                            val nodeContentDesc =
                                    parser.getAttributeValue(null, "content-desc") ?: ""
                            val nodeBounds = parser.getAttributeValue(null, "bounds") ?: ""
                            val nodeEnabled = parser.getAttributeValue(null, "enabled") == "true"
                            val nodeClickable =
                                    parser.getAttributeValue(null, "clickable") == "true"

                            // 检查是否匹配搜索条件
                            var matches = true
                            if (resourceId != null && !nodeResourceId.contains(resourceId)) {
                                matches = false
                            }
                            if (matches && className != null && !nodeClassName.contains(className)
                            ) {
                                matches = false
                            }
                            if (matches && text != null && !nodeText.contains(text)) {
                                matches = false
                            }
                            if (matches &&
                                            contentDesc != null &&
                                            !nodeContentDesc.contains(contentDesc)
                            ) {
                                matches = false
                            }

                            if (matches) {
                                // 解析bounds字符串，格式为
                                // "[left,top][right,bottom]"
                                val bounds = parseBounds(nodeBounds)

                                currentNode =
                                        SimplifiedUINode(
                                                resourceId = nodeResourceId,
                                                className = nodeClassName,
                                                text = nodeText,
                                                contentDesc = nodeContentDesc,
                                                bounds = nodeBounds,
                                                isClickable = nodeClickable,
                                                children = emptyList()
                                        )
                                matchedElements.add(currentNode)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        depth--
                    }
                }
                eventType = parser.next()
            }

            // 如果找到了匹配的元素
            if (matchedElements.isNotEmpty()) {
                // 如果指定了索引，则返回该索引处的元素
                val targetElement =
                        if (index != null && index >= 0 && index < matchedElements.size) {
                            matchedElements[index]
                        } else {
                            // 否则返回第一个匹配的元素
                            matchedElements[0]
                        }

                // 将元素转换为JSON
                val elementJson = JSONObject()
                elementJson.put("resourceId", targetElement.resourceId)
                elementJson.put("className", targetElement.className)
                elementJson.put("text", targetElement.text)
                elementJson.put("contentDesc", targetElement.contentDesc)
                elementJson.put("bounds", targetElement.bounds)
                elementJson.put("isClickable", targetElement.isClickable)

                // 还要返回找到的所有元素数量
                val resultJson = JSONObject()
                resultJson.put("element", elementJson)
                resultJson.put("totalMatches", matchedElements.size)

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData(resultJson.toString()),
                        error = ""
                )
            } else {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "No matching elements found."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding element", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error finding element: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    private fun parseBounds(boundsString: String): android.graphics.Rect {
        // 解析 "[left,top][right,bottom]" 格式的边界字符串
        val rect = android.graphics.Rect()
        try {
            val parts = boundsString.replace("[", "").replace("]", ",").split(",")
            if (parts.size >= 4) {
                rect.left = parts[0].toInt()
                rect.top = parts[1].toInt()
                rect.right = parts[2].toInt()
                rect.bottom = parts[3].toInt()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing bounds: $boundsString", e)
        }
        return rect
    }
}
