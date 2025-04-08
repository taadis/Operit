package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.core.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 从XML参数中提取实际内容（而不是属性值）
 */
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

/**
 * 工具状态显示组件，用于显示工具的不同状态（请求、执行中、完成、错误）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolStatusDisplay(
    toolName: String,
    isProcessing: Boolean,
    isError: Boolean,
    result: String?,
    params: String? = null,
    onShowResult: () -> Unit,
    onCopyResult: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    // 使用InfiniteTransition创建持续旋转动画
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = ""
    )

    // 提取参数值（不包括参数名）
    val paramValuesText = extractParamValues(params)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (isError) 
                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f) 
                else 
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                if (isProcessing)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else if (isError)
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
            .let {
                if (result != null && !isProcessing) {
                    // 只要有结果且不是处理中状态，就可以点击查看完整结果
                    it.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onShowResult()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onLongClick = {
                            onCopyResult()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                } else {
                    it
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标
                Icon(
                    imageVector = when {
                        isProcessing -> Icons.Default.Autorenew
                        isError -> Icons.Default.Close
                        else -> Icons.Default.Check
                    },
                    contentDescription = when {
                        isProcessing -> "执行中"
                        isError -> "执行错误"
                        else -> "执行成功"
                    },
                    tint = if (isError) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary,
                    modifier = if (isProcessing) {
                        Modifier
                            .size(16.dp)
                            .rotate(rotation)
                    } else {
                        Modifier.size(16.dp)
                    }
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 工具名称和状态描述
                Column {
                    Text(
                        text = when {
                            isProcessing -> "正在执行: $toolName"
                            isError -> "执行失败: $toolName"
                            else -> "执行完成: $toolName"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isProcessing)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else if (isError)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // 显示参数值（如果有）
                    if (paramValuesText.isNotBlank()) {
                        Text(
                            text = paramValuesText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isProcessing)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else if (isError)
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
} 