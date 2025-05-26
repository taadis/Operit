package com.ai.assistance.operit.ui.features.toolbox.screens.logcat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/** 从字符串生成颜色 */
fun generateColorFromString(input: String): Color {
    val hash = abs(input.hashCode())

    // 使用固定的饱和度和亮度，只变化色相，确保颜色适合阅读
    val hue = (hash % 360).toFloat()
    val saturation = 0.6f // 中等饱和度
    val brightness = 0.85f // 较高亮度，保证可读性

    return Color.hsl(hue, saturation, brightness)
}

/** 日志记录项目 - 紧凑版带tag高亮 */
@Composable
fun LogRecordItem(record: LogRecord) {
    val dateFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val formattedDate = remember(record) { dateFormatter.format(Date(record.timestamp)) }

    // 为tag生成颜色 - 修复remember内部不能调用Composable的问题
    val tagColor =
            if (record.tag != null) {
                generateColorFromString(record.tag)
            } else {
                MaterialTheme.colorScheme.onSurface
            }

    Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                    CardDefaults.cardColors(
                            containerColor = record.level.color.copy(alpha = 0.04f)
                    ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, record.level.color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 日志头部 - 更紧凑的设计
            Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // 日志级别指示
                Box(
                        modifier =
                                Modifier.size(14.dp)
                                        .clip(CircleShape)
                                        .background(record.level.color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                            text = record.level.symbol,
                            style = MaterialTheme.typography.labelSmall,
                            color = record.level.color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                    )
                }

                // 标签 - 高亮显示
                if (record.tag != null) {
                    Box(
                            modifier =
                                    Modifier.padding(start = 6.dp)
                                            .background(
                                                    color = tagColor.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                                text = record.tag,
                                style =
                                        MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Medium
                                        ),
                                color = tagColor.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 时间戳
                Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 9.sp
                )
            }

            // 日志内容 - 更紧凑设计
            Text(
                    text = record.message,
                    style =
                            MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                            ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** 简单紧凑型搜索框 */
@Composable
fun CompactSearchField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        modifier: Modifier = Modifier,
        leadingIcon: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = null
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    // 自定义输入框布局
    Row(
            modifier =
                    modifier.height(36.dp)
                            .background(color = backgroundColor, shape = RoundedCornerShape(18.dp))
                            .border(
                                    width = 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // 前置图标
        if (leadingIcon != null) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                leadingIcon()
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // 文本输入区域
        Box(
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle =
                            TextStyle(
                                    color = textColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                            ),
                    modifier = Modifier.fillMaxWidth(),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
            )

            // 显示提示文本
            if (value.isEmpty()) {
                Text(
                        text = placeholder,
                        color = placeholderColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                )
            }
        }

        // 后置图标
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                trailingIcon()
            }
        }
    }
}

/** 标签过滤芯片 */
@Composable
fun TagFilterChip(
        tag: String,
        count: Int,
        isFiltered: Boolean = false,
        filterAction: FilterAction? = null,
        onClick: () -> Unit,
        onActionSelect: (FilterAction) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        FilterChip(
                selected = isFiltered,
                onClick = {
                    if (isFiltered) {
                        showMenu = true
                    } else {
                        onClick()
                    }
                },
                label = {
                    Text(
                            "$tag ($count)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                    )
                },
                leadingIcon =
                        if (filterAction != null) {
                            {
                                Icon(
                                        imageVector =
                                                when (filterAction) {
                                                    FilterAction.INCLUDE -> Icons.Default.Add
                                                    FilterAction.EXCLUDE -> Icons.Default.Remove
                                                    FilterAction.ONLY -> Icons.Default.FilterAlt
                                                },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null,
                colors =
                        FilterChipDefaults.filterChipColors(
                                selectedContainerColor =
                                        MaterialTheme.colorScheme.primaryContainer.copy(
                                                alpha = 0.6f
                                        ),
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
        )

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                    text = { Text("仅显示此标签") },
                    onClick = {
                        onActionSelect(FilterAction.ONLY)
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.FilterAlt, contentDescription = null)
                    }
            )
            DropdownMenuItem(
                    text = { Text("包含此标签") },
                    onClick = {
                        onActionSelect(FilterAction.INCLUDE)
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    }
            )
            DropdownMenuItem(
                    text = { Text("排除此标签") },
                    onClick = {
                        onActionSelect(FilterAction.EXCLUDE)
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = null)
                    }
            )
            Divider()
            DropdownMenuItem(
                    text = { Text("取消过滤") },
                    onClick = {
                        onClick()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
            )
        }
    }
}

/** 可折叠的分组标题 */
@Composable
fun CollapsibleSectionHeader(
        title: String,
        expanded: Boolean,
        onToggle: () -> Unit,
        modifier: Modifier = Modifier
) {
    Row(
            modifier =
                    modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable {
                        onToggle()
                    },
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                imageVector =
                        if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = if (expanded) "折叠" else "展开",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
        )

        Divider(
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    }
}
