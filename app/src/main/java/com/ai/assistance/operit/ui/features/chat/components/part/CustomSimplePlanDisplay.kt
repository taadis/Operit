package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 简化版计划显示组件，用于XML渲染器中展示计划项 支持不同状态的计划显示：todo, in_progress, completed, failed等 */
@Composable
fun CustomSimplePlanDisplay(
        id: String,
        status: String,
        content: String,
        isUpdate: Boolean,
        modifier: Modifier = Modifier
) {
    when (status.lowercase()) {
        "todo" -> {
            if (!isUpdate) { // 只为计划项显示待办项，不为更新显示
                // TODO计划：小尺寸显示
                Row(
                        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Filled.RadioButtonUnchecked,
                            contentDescription = "待办任务",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        "in_progress" -> {
            // 如果是计划项或有内容的更新，显示指示器
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // 靠左对齐的计划开始标记
                    Box(modifier = Modifier.padding(end = 16.dp)) {
                        Card(
                                shape = RoundedCornerShape(16.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.15f
                                                        )
                                        )
                        ) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "计划进行中",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // 当有内容时显示内容，否则显示"计划 #X 执行中"
                                Text(
                                        text = if (content.isNotBlank()) content else "计划 #$id 执行中",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        "completed", "done" -> {
            // 对于计划项，显示带有内容的水平线
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // 横线
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                thickness = 1.5.dp
                        )
                    }

                    // 直接在线上方显示内容（灰色文字）
                    if (content.isNotBlank() && content != "Completed" && content != "Done") {
                        Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier =
                                        Modifier.padding(
                                                top = 2.dp,
                                                start = 16.dp,
                                                bottom = if (isUpdate) 4.dp else 0.dp
                                        ),
                                fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
        "failed", "cancelled" -> {
            // 失败和取消的计划应该与已完成的计划有相同的视觉处理
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // 横线
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 确定线的颜色
                        val lineColor =
                                when (status.lowercase()) {
                                    "failed" -> Color(0xFFC62828).copy(alpha = 0.25f) // 深红色带透明度
                                    else -> Color(0xFF757575).copy(alpha = 0.25f) // 灰色带透明度
                                }

                        // 横线 - 横跨整个宽度
                        Divider(
                                modifier = Modifier.weight(1f),
                                color = lineColor,
                                thickness = 1.5.dp
                        )
                    }

                    // 状态文本
                    val statusText =
                            when (status.lowercase()) {
                                "failed" -> "计划失败"
                                else -> "计划取消"
                            }

                    val textColor =
                            when (status.lowercase()) {
                                "failed" -> Color(0xFFC62828).copy(alpha = 0.7f) // 深红色
                                else -> Color(0xFF757575).copy(alpha = 0.7f) // 灰色
                            }

                    Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor,
                            modifier = Modifier.padding(top = 2.dp, start = 16.dp),
                            fontWeight = FontWeight.Medium
                    )

                    // 显示内容（如果有）
                    if (content.isNotBlank()) {
                        Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier =
                                        Modifier.padding(
                                                top = 2.dp,
                                                start = 16.dp,
                                                bottom = if (isUpdate) 4.dp else 0.dp
                                        ),
                                fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
        else -> {
            // 对于未分类的其他状态，使用水平线
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // 横线代替状态卡片
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 横线 - 横跨整个宽度
                        Divider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                thickness = 1.5.dp
                        )
                    }

                    // 显示状态文本
                    Text(
                            text = "计划: ${status.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp, start = 16.dp),
                            fontWeight = FontWeight.Medium
                    )

                    // 显示内容（如果有）
                    if (content.isNotBlank()) {
                        Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier =
                                        Modifier.padding(
                                                top = 2.dp,
                                                start = 16.dp,
                                                bottom = if (isUpdate) 4.dp else 0.dp
                                        ),
                                fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
