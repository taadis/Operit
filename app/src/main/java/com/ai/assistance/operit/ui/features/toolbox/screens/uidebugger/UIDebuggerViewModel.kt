package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI调试工具的ViewModel，负责处理与AITool的交互 */
class UIDebuggerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UIDebuggerState())
    val uiState: StateFlow<UIDebuggerState> = _uiState.asStateFlow()

    // 父子关系映射，用于构建树结构
    private val _parentChildrenMap = MutableStateFlow<Map<String?, List<String>>>(emptyMap())
    val parentChildrenMap: StateFlow<Map<String?, List<String>>> = _parentChildrenMap.asStateFlow()

    private lateinit var toolHandler: AIToolHandler
    private val TAG = "UIDebuggerViewModel"

    /** 初始化ViewModel */
    fun initialize(context: Context) {
        toolHandler = AIToolHandler.getInstance(context)
        refreshUI()
    }

    /** 刷新UI元素 */
    fun refreshUI() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                // 在IO线程中执行AITool操作
                val elements =
                        withContext(Dispatchers.IO) {
                            // 调用AITool的get_page_info获取UI层次结构
                            val pageInfoTool = AITool(name = "get_page_info", parameters = listOf())

                            val result = toolHandler.executeTool(pageInfoTool)
                            if (result.success) {
                                // 处理结构化返回数据
                                val resultData = result.result
                                if (resultData is UIPageResultData) {
                                    Log.d(
                                            TAG,
                                            "获取页面信息成功: ${resultData.packageName}, ${resultData.activityName}"
                                    )

                                    // 处理UI元素树
                                    convertToUIElements(resultData.uiElements)
                                } else {
                                    Log.e(TAG, "返回数据类型错误: ${resultData::class.java.simpleName}")
                                    throw Exception("返回数据类型错误")
                                }
                            } else {
                                throw Exception("获取UI信息失败: ${result.error}")
                            }
                        }

                _uiState.update { it.copy(isLoading = false, elements = elements) }

                // 构建树结构关系图
                buildParentChildrenMap(elements)
            } catch (e: Exception) {
                Log.e(TAG, "刷新UI元素失败", e)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "刷新UI元素失败: ${e.message}")
                }
            }
        }
    }

    /** 构建父子关系映射 */
    private fun buildParentChildrenMap(elements: List<UIElement>) {
        // 所有元素的ID-子元素ID列表映射
        val map = mutableMapOf<String?, MutableList<String>>()

        // 初始化根节点
        map[null] = mutableListOf()

        // 基于类名分组找出根元素
        val rootElements =
                elements.filter {
                    it.className.contains("Layout", ignoreCase = true) ||
                            it.className.contains("Root", ignoreCase = true) ||
                            it.className.contains("Screen", ignoreCase = true)
                }

        // 如果找不到明确的根元素，则使用第一个元素作为根
        if (rootElements.isEmpty() && elements.isNotEmpty()) {
            val root = elements.first()
            map[null]?.add(root.id)
        } else {
            rootElements.forEach { root -> map[null]?.add(root.id) }
        }

        // 构建其余元素的父子关系
        elements.forEach { element -> map[element.id] = mutableListOf() }

        // 为每个非根元素找到一个父元素
        elements.filter { !map[null]!!.contains(it.id) }.forEach { child ->
            // 寻找可能的父元素：包含Layout的元素
            val possibleParent =
                    elements.find { parent ->
                        parent.id != child.id &&
                                (parent.className.contains("Layout", ignoreCase = true) ||
                                        parent.className.contains("View", ignoreCase = true))
                    }

            if (possibleParent != null) {
                map[possibleParent.id]?.add(child.id)
            } else {
                // 如果找不到合适的父元素，则添加到根
                map[null]?.add(child.id)
            }
        }

        _parentChildrenMap.value = map
    }

    /** 将SimplifiedUINode转换为UIElement列表 */
    private fun convertToUIElements(
            node: com.ai.assistance.operit.core.tools.SimplifiedUINode
    ): List<UIElement> {
        val elements = mutableListOf<UIElement>()
        processNode(node, elements)
        return elements
    }

    /** 处理节点并添加到列表 */
    private fun processNode(
            node: com.ai.assistance.operit.core.tools.SimplifiedUINode,
            elements: MutableList<UIElement>
    ) {
        // 创建UI元素
        val element = createUiElement(node)
        elements.add(element)

        // 递归处理子节点
        node.children.forEach { childNode -> processNode(childNode, elements) }
    }

    /** 从SimplifiedUINode创建UIElement */
    private fun createUiElement(
            node: com.ai.assistance.operit.core.tools.SimplifiedUINode
    ): UIElement {
        // 解析边界
        val bounds =
                node.bounds?.let {
                    try {
                        val coordsPattern = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
                        val matchResult = coordsPattern.find(it)
                        if (matchResult != null) {
                            val (left, top, right, bottom) = matchResult.destructured
                            Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析边界失败: $it", e)
                        null
                    }
                }

        return UIElement(
                id = UUID.randomUUID().toString(),
                className = node.className ?: "Unknown",
                resourceId = node.resourceId,
                packageName = null, // 这个信息在SimplifiedUINode中不可用
                contentDesc = node.contentDesc,
                text = node.text ?: "",
                bounds = bounds,
                isClickable = node.isClickable,
                isVisible = true, // 默认可见
                isCheckable = false, // 这些属性在SimplifiedUINode中不可用
                isChecked = false,
                isEnabled = true,
                isFocused = false,
                isScrollable = false,
                isLongClickable = false,
                isSelected = false
        )
    }

    /** 选择元素 */
    fun selectElement(elementId: String) {
        _uiState.update { state -> state.copy(selectedElementId = elementId) }
    }

    /** 切换高亮显示 */
    fun toggleHighlight() {
        _uiState.update { state -> state.copy(highlightEnabled = !state.highlightEnabled) }
    }

    /** 在元素上执行操作 */
    suspend fun performAction(elementId: String, action: UIElementAction) {
        val element = _uiState.value.elements.find { it.id == elementId } ?: return
        val bounds = element.bounds ?: return

        withContext(Dispatchers.IO) {
            when (action) {
                UIElementAction.CLICK -> {
                    if (element.isClickable) {
                        // 计算元素中心点
                        val centerX = (bounds.left + bounds.right) / 2
                        val centerY = (bounds.top + bounds.bottom) / 2

                        // 使用AITool点击坐标
                        val tapTool =
                                AITool(
                                        name = "tap",
                                        parameters =
                                                listOf(
                                                        ToolParameter("x", centerX.toString()),
                                                        ToolParameter("y", centerY.toString())
                                                )
                                )

                        val result = toolHandler.executeTool(tapTool)
                        showActionFeedback(
                                if (result.success) "点击元素成功: ${element.typeDescription}"
                                else "点击元素失败: ${result.error}"
                        )
                    } else {
                        showActionFeedback("元素不可点击")
                    }
                }
                UIElementAction.LONG_CLICK -> {
                    if (element.isLongClickable) {
                        // 计算元素中心点
                        val centerX = (bounds.left + bounds.right) / 2
                        val centerY = (bounds.top + bounds.bottom) / 2

                        // 使用AITool swipe在同一位置进行长按
                        val swipeTool =
                                AITool(
                                        name = "swipe",
                                        parameters =
                                                listOf(
                                                        ToolParameter(
                                                                "start_x",
                                                                centerX.toString()
                                                        ),
                                                        ToolParameter(
                                                                "start_y",
                                                                centerY.toString()
                                                        ),
                                                        ToolParameter("end_x", centerX.toString()),
                                                        ToolParameter("end_y", centerY.toString()),
                                                        ToolParameter("duration", "1000")
                                                )
                                )

                        val result = toolHandler.executeTool(swipeTool)
                        showActionFeedback(
                                if (result.success) "长按元素成功: ${element.typeDescription}"
                                else "长按元素失败: ${result.error}"
                        )
                    } else {
                        showActionFeedback("元素不支持长按")
                    }
                }
                UIElementAction.HIGHLIGHT -> {
                    showActionFeedback("高亮元素: ${element.typeDescription}")
                    // 显示一个临时的反馈信息
                }
                UIElementAction.INSPECT -> {
                    showActionFeedback("正在检查元素: ${element.typeDescription}")
                }
            }
        }
    }

    /** 显示操作反馈 */
    private fun showActionFeedback(message: String) {
        _uiState.update { state ->
            state.copy(showActionFeedback = true, actionFeedbackMessage = message)
        }
    }

    /** 清除操作反馈 */
    fun clearActionFeedback() {
        _uiState.update { state ->
            state.copy(showActionFeedback = false, actionFeedbackMessage = "")
        }
    }

    /** 高亮所有元素 */
    fun highlightAllElements() {
        viewModelScope.launch {
            val message = "高亮功能需要SYSTEM_ALERT_WINDOW权限，请确保应用已获取该权限"
            showActionFeedback(message)
        }
    }

    /** 清除高亮 */
    fun clearHighlights() {
        viewModelScope.launch { showActionFeedback("高亮已清除") }
    }
}
