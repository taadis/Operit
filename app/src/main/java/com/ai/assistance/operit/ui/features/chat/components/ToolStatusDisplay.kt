package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 从XML参数中提取实际内容（而不是属性值） */
private fun extractParamValues(params: String?): String {
    if (params.isNullOrBlank()) return ""

    // 直接提取XML标签之间的内容
    // 注意：这里不再需要提取属性，而是直接获取内容
    val noTagsContent = params.replace(Regex("<[^>]*>|</[^>]*>"), "").trim()
    if (noTagsContent.isNotBlank()) {
        return noTagsContent.take(100) + if (noTagsContent.length > 100) "..." else ""
    }

    // 如果直接清除标签方法失败，尝试特定模式匹配
    val toolContentPattern = "<tool[^>]*>(.*?)</tool>".toRegex(RegexOption.DOT_MATCHES_ALL)
    val match = toolContentPattern.find(params)
    if (match != null && match.groupValues.size > 1) {
        val content = match.groupValues[1].trim()
        if (content.isNotBlank()) {
            return content.take(100) + if (content.length > 100) "..." else ""
        }
    }

    // 如果都失败，则返回原始参数
    return params.take(100) + if (params.length > 100) "..." else ""
}

/** 工具状态显示组件，用于显示工具的不同状态（请求、执行中、完成、错误） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolStatusDisplay(
        toolName: String,
        isProcessing: Boolean,
        isError: Boolean,
        result: String?,
        params: String? = null,
        onShowResult: () -> Unit,
        onCopyResult: () -> Unit,
        isSimulated: Boolean = false // 标识是否为AI模拟的工具调用
) {
    val haptic = LocalHapticFeedback.current

    // 使用InfiniteTransition创建持续旋转动画
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by
            infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(2000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = ""
            )

    // 提取参数值（不包括参数名）
    val paramValuesText = extractParamValues(params)

    // 使用相同的文本样式确保对齐
    val textStyle = MaterialTheme.typography.bodyMedium

    // 为模拟工具选择更优雅的颜色
    val simulatedBorderColor = Color(0xFF9575CD) // 使用柔和的紫色
    val simulatedBackgroundColor = Color(0xFFEDE7F6) // 非常淡的紫色背景
    val simulatedTextColor = Color(0xFF5E35B1) // 深紫色文字
    val simulatedIconColor = Color(0xFF7E57C2) // 中等紫色图标

    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                    width = 1.dp,
                                    color =
                                            if (isError)
                                                    MaterialTheme.colorScheme.error.copy(
                                                            alpha = 0.5f
                                                    )
                                            else if (isSimulated)
                                                    simulatedBorderColor.copy(alpha = 0.5f)
                                            else
                                                    MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.5f
                                                    ),
                                    shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                    if (isProcessing && !isSimulated)
                                            MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.5f
                                            )
                                    else if (isError)
                                            MaterialTheme.colorScheme.errorContainer.copy(
                                                    alpha = 0.7f
                                            )
                                    else if (isSimulated)
                                            simulatedBackgroundColor.copy(alpha = 0.7f)
                                    else
                                            MaterialTheme.colorScheme.secondaryContainer.copy(
                                                    alpha = 0.5f
                                            )
                            )
                            .let {
                                if ((result != null && !isProcessing) || isSimulated) {
                                    // 只要有结果且不是处理中状态，就可以点击查看完整结果
                                    it.combinedClickable(
                                            interactionSource =
                                                    remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                onShowResult()
                                                haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                )
                                            },
                                            onLongClick = {
                                                onCopyResult()
                                                haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                )
                                            }
                                    )
                                } else {
                                    it
                                }
                            }
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标 - 为模拟工具显示固定图标，没有旋转效果
            Icon(
                    imageVector =
                            when {
                                isSimulated -> Icons.Default.Code // 使用Code图标表示模拟工具
                                isProcessing -> Icons.Default.Autorenew
                                isError -> Icons.Default.Close
                                else -> Icons.Default.Check
                            },
                    contentDescription =
                            when {
                                isSimulated -> "模拟工具"
                                isProcessing -> "执行中"
                                isError -> "执行错误"
                                else -> "执行成功"
                            },
                    tint =
                            when {
                                isSimulated -> simulatedIconColor
                                isError -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                    modifier =
                            if (isProcessing && !isSimulated) {
                                Modifier.size(16.dp).rotate(rotation)
                            } else {
                                Modifier.size(16.dp)
                            }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 文本内容 - 不再使用硬编码前缀
            val displayText =
                    if (paramValuesText.isNotBlank()) "$toolName: $paramValuesText" else toolName

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 如果是模拟工具，显示小标签
                if (isSimulated) {
                    Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = simulatedIconColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, simulatedIconColor.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                                text = "模拟",
                                style =
                                        MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                letterSpacing = 0.sp
                                        ),
                                color = simulatedTextColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // 工具名称和参数
                Text(
                        text = displayText,
                        style = textStyle,
                        color =
                                when {
                                    isSimulated -> simulatedTextColor
                                    isProcessing -> MaterialTheme.colorScheme.onPrimaryContainer
                                    isError -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = true)
                )
            }
        }
    }
}
