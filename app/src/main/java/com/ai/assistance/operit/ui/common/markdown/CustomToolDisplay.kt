package com.ai.assistance.operit.ui.common.markdown

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

/** 自定义工具显示组件，用于XML渲染器 基于原始ToolStatusDisplay功能但简化为我们需要的版本 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomToolDisplay(
        toolName: String,
        isProcessing: Boolean,
        isError: Boolean,
        result: String?,
        params: String? = null,
        onShowResult: () -> Unit,
        onCopyResult: () -> Unit,
        isSimulated: Boolean = false
) {
    val haptic = LocalHapticFeedback.current

    // 创建旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "rotate")
    val rotation by
            infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(2000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                            ),
                    label = "rotation"
            )

    // 处理参数显示
    val paramValuesText = extractParamValues(params)

    // 为模拟工具选择特殊颜色
    val simulatedBorderColor = Color(0xFF9575CD) // 使用柔和的紫色
    val simulatedBackgroundColor = Color(0xFFEDE7F6) // 非常淡的紫色背景
    val simulatedTextColor = Color(0xFF5E35B1) // 深紫色文字
    val simulatedIconColor = Color(0xFF7E57C2) // 中等紫色图标

    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                    width = 1.dp,
                                    color =
                                            when {
                                                isError ->
                                                        MaterialTheme.colorScheme.error.copy(
                                                                alpha = 0.5f
                                                        )
                                                isSimulated ->
                                                        simulatedBorderColor.copy(alpha = 0.5f)
                                                else ->
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.5f
                                                        )
                                            },
                                    shape = RoundedCornerShape(8.dp)
                            )
                            .background(
                                    when {
                                        isProcessing && !isSimulated ->
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.5f
                                                )
                                        isError ->
                                                MaterialTheme.colorScheme.errorContainer.copy(
                                                        alpha = 0.7f
                                                )
                                        isSimulated -> simulatedBackgroundColor.copy(alpha = 0.7f)
                                        else ->
                                                MaterialTheme.colorScheme.secondaryContainer.copy(
                                                        alpha = 0.5f
                                                )
                                    }
                            )
                            .let {
                                if ((result != null && !isProcessing) || isSimulated) {
                                    // 如果有结果且不是处理中，可以点击查看完整结果
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
            // 工具图标
            Icon(
                    imageVector =
                            when {
                                isSimulated -> Icons.Default.Code
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

            // 工具名称和参数
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

                // 显示工具名称和参数
                Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
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

/** 从XML参数中提取实际内容 */
private fun extractParamValues(params: String?): String {
    if (params.isNullOrBlank()) return ""

    // 处理标准的param标签格式
    val paramPattern =
            "<param\\s+name=\"([^\"]+)\">(.*?)</param>".toRegex(RegexOption.DOT_MATCHES_ALL)
    val matches = paramPattern.findAll(params)
    val paramsList =
            matches
                    .map {
                        val name = it.groupValues[1]
                        val value = it.groupValues[2].trim()
                        "$name: $value"
                    }
                    .toList()

    if (paramsList.isNotEmpty()) {
        val combinedParams = paramsList.joinToString(", ")
        return combinedParams.take(100) + if (combinedParams.length > 100) "..." else ""
    }

    // 如果没有找到标准格式参数，尝试处理纯文本内容
    val noTagsContent = params.replace(Regex("<[^>]*>|</[^>]*>"), "").trim()
    if (noTagsContent.isNotBlank()) {
        return noTagsContent.take(100) + if (noTagsContent.length > 100) "..." else ""
    }

    return params.take(100) + if (params.length > 100) "..." else ""
}
