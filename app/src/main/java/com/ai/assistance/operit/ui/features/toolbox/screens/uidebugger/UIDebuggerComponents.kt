package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 高亮控制区域 */
@Composable
fun HighlightControlsSection(
        isHighlightActive: Boolean,
        onToggleHighlight: () -> Unit,
        onHighlightBounds: () -> Unit,
        onClearHighlight: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("高亮控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                        onClick = onToggleHighlight,
                        colors =
                                ButtonDefaults.outlinedButtonColors(
                                        containerColor =
                                                if (isHighlightActive)
                                                        MaterialTheme.colorScheme.primaryContainer
                                                else Color.Transparent
                                )
                ) {
                    Icon(
                            Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isHighlightActive) "关闭高亮" else "开启高亮")
                }

                OutlinedButton(onClick = onHighlightBounds) {
                    Icon(
                            Icons.Default.CropFree,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("显示边界")
                }

                OutlinedButton(onClick = onClearHighlight) {
                    Icon(
                            Icons.Default.ClearAll,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清除高亮")
                }
            }
        }
    }
}

/** 加载指示器组件 */
@Composable
fun UIDebuggerLoadingView() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("正在加载UI层次结构...")
        }
    }
}

/** 空状态视图组件 */
@Composable
fun EmptyStateView(onRefresh: () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                    Icons.Default.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("未检测到UI元素", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                    "请刷新或确保应用已获得所需权限",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("刷新")
            }
        }
    }
}

/** 元素列表视图 */
@Composable
fun ElementListView(
        elements: List<UIElement>,
        selectedElementId: String?,
        onElementSelected: (String) -> Unit,
        onElementAction: (String, UIElementAction) -> Unit
) {
    LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(elements) { element ->
            val isSelected = element.id == selectedElementId

            ElementCard(
                    element = element,
                    isSelected = isSelected,
                    onClick = { onElementSelected(element.id) },
                    onActionClick = { action -> onElementAction(element.id, action) }
            )
        }
    }
}

/** 元素卡片组件 */
@Composable
fun ElementCard(
        element: UIElement,
        isSelected: Boolean,
        onClick: () -> Unit,
        onActionClick: (UIElementAction) -> Unit
) {
    val elevation by
            animateFloatAsState(targetValue = if (isSelected) 8f else 1f, label = "elevation")

    Card(
            modifier =
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.7f
                                            )
                                    else MaterialTheme.colorScheme.surface
                    )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 元素标识信息
            Row(verticalAlignment = Alignment.CenterVertically) {
                ElementTypeIcon(element)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = element.typeDescription,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )

                    Text(
                            text = element.identifierInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 元素属性信息
            element.bounds?.let { bounds ->
                Text(
                        text =
                                "位置: [${bounds.left}, ${bounds.top}, ${bounds.right}, ${bounds.bottom}]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (element.text.isNotEmpty()) {
                Text(
                        text = "文本: ${element.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 交互属性
            Text(
                    text = "可交互: ${if (element.isClickable) "是" else "否"}",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                            if (element.isClickable) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 仅在元素被选中且可操作时显示操作按钮
            if (isSelected) {
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                            onClick = { onActionClick(UIElementAction.HIGHLIGHT) },
                            modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                                Icons.Default.BorderStyle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("高亮")
                    }

                    if (element.isClickable) {
                        OutlinedButton(
                                onClick = { onActionClick(UIElementAction.CLICK) },
                                modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                    Icons.Default.TouchApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("点击")
                        }
                    }

                    OutlinedButton(
                            onClick = { onActionClick(UIElementAction.INSPECT) },
                            modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("检查")
                    }
                }
            }
        }
    }
}

/** 元素类型图标 */
@Composable
fun ElementTypeIcon(element: UIElement) {
    val iconTint =
            when {
                element.isClickable -> MaterialTheme.colorScheme.primary
                element.isVisible -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            }

    val icon =
            when {
                element.className.contains("Button", ignoreCase = true) -> Icons.Default.SmartButton
                element.className.contains("Text", ignoreCase = true) -> Icons.Default.TextFields
                element.className.contains("Image", ignoreCase = true) -> Icons.Default.Image
                element.className.contains("Edit", ignoreCase = true) -> Icons.Default.Edit
                element.className.contains("Check", ignoreCase = true) -> Icons.Default.CheckBox
                element.className.contains("Switch", ignoreCase = true) -> Icons.Default.ToggleOn
                element.className.contains("Radio", ignoreCase = true) ->
                        Icons.Default.RadioButtonChecked
                element.className.contains("Layout", ignoreCase = true) -> Icons.Default.ViewModule
                element.className.contains("View", ignoreCase = true) -> Icons.Default.Widgets
                else -> Icons.Default.ViewInAr
            }

    Box(
            contentAlignment = Alignment.Center,
            modifier =
                    Modifier.size(40.dp)
                            .background(
                                    color =
                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.5f
                                            ),
                                    shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.alpha(if (element.isVisible) 1f else 0.5f)
        )
    }
}
