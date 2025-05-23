package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import android.graphics.Rect

/** UI调试工具的状态类 */
data class UIDebuggerState(
        val isLoading: Boolean = false,
        val elements: List<UIElement> = emptyList(),
        val selectedElementId: String? = null,
        val errorMessage: String? = null,
        val showActionFeedback: Boolean = false,
        val actionFeedbackMessage: String = "",
        val highlightEnabled: Boolean = false
)

/** UI元素数据模型 */
data class UIElement(
        val id: String,
        val className: String,
        val resourceId: String? = null,
        val packageName: String? = null,
        val contentDesc: String? = null,
        val text: String = "",
        val bounds: Rect? = null,
        val isClickable: Boolean = false,
        val isVisible: Boolean = true,
        val isCheckable: Boolean = false,
        val isChecked: Boolean = false,
        val isEnabled: Boolean = true,
        val isFocused: Boolean = false,
        val isScrollable: Boolean = false,
        val isLongClickable: Boolean = false,
        val isSelected: Boolean = false
) {
    val typeDescription: String
        get() =
                when {
                    className.contains("Button", ignoreCase = true) -> "按钮"
                    className.contains("Text", ignoreCase = true) -> "文本"
                    className.contains("Edit", ignoreCase = true) -> "输入框"
                    className.contains("Image", ignoreCase = true) -> "图片"
                    className.contains("Check", ignoreCase = true) -> "复选框"
                    className.contains("Switch", ignoreCase = true) -> "开关"
                    className.contains("Radio", ignoreCase = true) -> "单选框"
                    className.contains("Layout", ignoreCase = true) -> "布局容器"
                    className.contains("View", ignoreCase = true) -> "视图"
                    else -> "UI元素"
                }

    val identifierInfo: String
        get() = resourceId ?: packageName ?: className

    fun getFullDetails(): String {
        return buildString {
            append("类名: $className\n")
            if (resourceId != null) append("资源ID: $resourceId\n")
            if (packageName != null) append("包名: $packageName\n")
            if (contentDesc != null) append("内容描述: $contentDesc\n")
            if (text.isNotEmpty()) append("文本: $text\n")
            if (bounds != null)
                    append(
                            "边界: [${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom}]\n"
                    )
            append("可点击: ${if (isClickable) "是" else "否"}\n")
            append("可见: ${if (isVisible) "是" else "否"}\n")
            if (isCheckable) append("可选中: ${if (isChecked) "已选中" else "未选中"}\n")
            if (!isEnabled) append("已禁用\n")
            if (isFocused) append("已获取焦点\n")
            if (isScrollable) append("可滚动\n")
            if (isLongClickable) append("可长按\n")
            if (isSelected) append("已选择\n")
        }
    }
}

/** UI元素操作类型 */
enum class UIElementAction {
    CLICK,
    LONG_CLICK,
    HIGHLIGHT,
    INSPECT
}

/** UI解析结果的密封类 */
sealed class UiParsingResult {
    data class Success(val elements: List<UIElement>) : UiParsingResult()
    data class Error(val message: String) : UiParsingResult()
}
