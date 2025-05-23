package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch

/** UI调试工具主屏幕 用于显示和调试应用内的UI元素 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIDebuggerScreen(navController: NavController) {
    val viewModel: UIDebuggerViewModel = viewModel()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var showHighlightControls by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // 初始化ViewModel
    LaunchedEffect(Unit) { viewModel.initialize(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 状态指示
        Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧状态信息
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            imageVector =
                                    when {
                                        uiState.isLoading -> Icons.Default.Refresh
                                        uiState.elements.isEmpty() -> Icons.Default.Warning
                                        else -> Icons.Default.CheckCircle
                                    },
                            contentDescription = null,
                            tint =
                                    when {
                                        uiState.isLoading ->
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                        uiState.elements.isEmpty() ->
                                                MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                            text =
                                    when {
                                        uiState.isLoading -> "正在加载UI元素..."
                                        uiState.elements.isEmpty() -> "未检测到UI元素，请刷新或检查权限"
                                        else -> "已加载 ${uiState.elements.size} 个UI元素"
                                    },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // 右侧操作按钮
                Row {
                    IconButton(
                            onClick = { coroutineScope.launch { viewModel.refreshUI() } }
                    ) {
                        Icon(
                                Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                            onClick = { showHighlightControls = !showHighlightControls }
                    ) {
                        Icon(
                                Icons.Default.Visibility,
                                contentDescription = "高亮控制",
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 高亮控制区域
        AnimatedVisibility(
                visible = showHighlightControls,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
        ) {
            HighlightControlsSection(
                    isHighlightActive = uiState.highlightEnabled,
                    onToggleHighlight = { viewModel.toggleHighlight() },
                    onHighlightBounds = { viewModel.highlightAllElements() },
                    onClearHighlight = { viewModel.clearHighlights() }
            )
        }

        // 主内容
        if (uiState.isLoading) {
            UIDebuggerLoadingView()
        } else if (uiState.elements.isEmpty()) {
            EmptyStateView(onRefresh = { coroutineScope.launch { viewModel.refreshUI() } })
        } else {
            ElementTreeView(
                    elements = uiState.elements,
                    selectedElementId = uiState.selectedElementId,
                    onElementSelected = { elementId -> viewModel.selectElement(elementId) },
                    onElementAction = { elementId, action ->
                        coroutineScope.launch {
                            try {
                                viewModel.performAction(elementId, action)
                            } catch (e: Exception) {
                                errorMessage = "执行操作失败: ${e.message}"
                                showErrorDialog = true
                            }
                        }
                    }
            )
        }
    }

    // 错误对话框
    if (showErrorDialog) {
        AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = { Text("操作失败") },
                text = { Text(errorMessage) },
                confirmButton = { TextButton(onClick = { showErrorDialog = false }) { Text("确定") } }
        )
    }

    // 操作完成提示
    if (uiState.showActionFeedback) {
        LaunchedEffect(uiState.showActionFeedback) {
            launch {
                // 延迟一段时间后自动关闭反馈
                kotlinx.coroutines.delay(2000)
                viewModel.clearActionFeedback()
            }
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    modifier = Modifier.padding(16.dp)
            ) {
                Text(
                        text = uiState.actionFeedbackMessage,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/** 树状结构UI元素视图 */
@Composable
fun ElementTreeView(
        elements: List<UIElement>,
        selectedElementId: String?,
        onElementSelected: (String) -> Unit,
        onElementAction: (String, UIElementAction) -> Unit
) {
    val viewModel: UIDebuggerViewModel = viewModel()
    val parentChildrenMap by viewModel.parentChildrenMap.collectAsState()

    // 元素ID到元素的映射，方便查找
    val elementMap = remember(elements) { elements.associateBy { it.id } }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 渲染根元素的子元素
        parentChildrenMap[null]?.forEach { rootId ->
            item {
                ElementTreeNode(
                        element = elementMap[rootId]!!,
                        depth = 0,
                        elementMap = elementMap,
                        parentChildrenMap = parentChildrenMap,
                        selectedElementId = selectedElementId,
                        onElementSelected = onElementSelected,
                        onElementAction = onElementAction
                )
            }
        }
    }
}

/** 树状结构中的单个节点 */
@Composable
fun ElementTreeNode(
        element: UIElement,
        depth: Int,
        elementMap: Map<String, UIElement>,
        parentChildrenMap: Map<String?, List<String>>,
        selectedElementId: String?,
        onElementSelected: (String) -> Unit,
        onElementAction: (String, UIElementAction) -> Unit
) {
    val isSelected = element.id == selectedElementId
    var isExpanded by remember { mutableStateOf(depth < 2) } // 默认展开前两层
    val hasChildren = parentChildrenMap[element.id]?.isNotEmpty() == true
    val coroutineScope = rememberCoroutineScope()

    val rotationState by
            animateFloatAsState(
                    targetValue = if (isExpanded) 90f else 0f,
                    label = "Rotation Animation"
            )

    // 节点本身
    Surface(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(start = (depth * 16).dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onElementSelected(element.id) },
            color =
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
            tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // 展开/折叠图标
            if (hasChildren) {
                IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.ArrowRight,
                            contentDescription = if (isExpanded) "折叠" else "展开",
                            modifier = Modifier.rotate(rotationState)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(24.dp))
            }

            // 元素类型图标
            Icon(
                    imageVector = getIconForElementType(element.className),
                    contentDescription = null,
                    tint = getColorForElement(element),
                    modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 元素信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = getElementTitle(element),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                if (element.text.isNotEmpty() || element.contentDesc?.isNotEmpty() == true) {
                    Text(
                            text = element.text.ifEmpty { element.contentDesc ?: "" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 操作按钮
            if (isSelected) {
                Row {
                    IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    onElementAction(element.id, UIElementAction.CLICK)
                                }
                            },
                            enabled = element.isClickable
                    ) {
                        Icon(
                                Icons.Default.TouchApp,
                                contentDescription = "点击",
                                tint =
                                        if (element.isClickable) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }

                    IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    onElementAction(element.id, UIElementAction.HIGHLIGHT)
                                }
                            }
                    ) {
                        Icon(
                                Icons.Default.Visibility,
                                contentDescription = "高亮",
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    onElementAction(element.id, UIElementAction.INSPECT)
                                }
                            }
                    ) {
                        Icon(
                                Icons.Default.Info,
                                contentDescription = "检查",
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // 垂直连接线
    if (isExpanded && hasChildren) {
        Box(
                modifier =
                        Modifier.padding(start = (depth * 16 + 12).dp)
                                .width(2.dp)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    }

    // 子节点
    if (isExpanded && hasChildren) {
        parentChildrenMap[element.id]?.forEach { childId ->
            elementMap[childId]?.let { childElement ->
                ElementTreeNode(
                        element = childElement,
                        depth = depth + 1,
                        elementMap = elementMap,
                        parentChildrenMap = parentChildrenMap,
                        selectedElementId = selectedElementId,
                        onElementSelected = onElementSelected,
                        onElementAction = onElementAction
                )
            }
        }
    }
}

/** 获取元素类型对应的图标 */
@Composable
fun getIconForElementType(className: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        className.contains("Button", ignoreCase = true) -> Icons.Default.TouchApp
        className.contains("Text", ignoreCase = true) -> Icons.Default.TextFields
        className.contains("Edit", ignoreCase = true) -> Icons.Default.Edit
        className.contains("Image", ignoreCase = true) -> Icons.Default.Image
        className.contains("View", ignoreCase = true) -> Icons.Default.Visibility
        className.contains("Layout", ignoreCase = true) -> Icons.Default.ViewModule
        className.contains("Recycler", ignoreCase = true) -> Icons.Default.List
        className.contains("Scroll", ignoreCase = true) -> Icons.Default.SwapVert
        else -> Icons.Default.Widgets
    }
}

/** 获取元素对应的颜色 */
@Composable
fun getColorForElement(element: UIElement): Color {
    return when {
        element.isClickable -> MaterialTheme.colorScheme.primary
        element.className.contains("Layout", ignoreCase = true) ->
                MaterialTheme.colorScheme.tertiary
        element.className.contains("Text", ignoreCase = true) -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }
}

/** 获取元素标题 */
fun getElementTitle(element: UIElement): String {
    val className = element.className
    val resourceId = element.resourceId?.substringAfterLast("/") ?: ""

    return if (resourceId.isNotEmpty()) {
        "$className ($resourceId)"
    } else {
        className
    }
}

/** 工具屏幕入口 */
@Composable
fun UIDebuggerToolScreen(navController: NavController) {
    UIDebuggerScreen(navController = navController)
}
