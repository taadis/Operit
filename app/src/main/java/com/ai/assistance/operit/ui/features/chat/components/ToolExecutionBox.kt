package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// Maximum length for collapsed tool result display
private const val MAX_RESULT_LENGTH = 500

// 工具执行状态框组件
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolExecutionBox(
    toolName: String,
    isProcessing: Boolean,
    result: String?,
    isError: Boolean,
    params: String? = null,
    enableCopy: Boolean = false,
    hideToolRequest: Boolean = true, // 默认隐藏工具请求
    collapseExecution: Boolean = false // 是否启用折叠执行模式
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    
    // State for collapsible result
    var isResultExpanded by remember { mutableStateOf(false) }
    
    // State for full result dialog
    var showFullResultDialog by remember { mutableStateOf(false) }
    
    // 折叠执行模式下的请求和结果处理
    if (collapseExecution) {
        // 在折叠执行模式下，使用共享的ToolStatusDisplay组件
        ToolStatusDisplay(
            toolName = toolName,
            isProcessing = isProcessing,
            isError = isError,
            result = result,
            params = params,
            onShowResult = {
                showFullResultDialog = true
            },
            onCopyResult = {
                result?.let { clipboardManager.setText(AnnotatedString(it)) }
            }
        )
        
        // 结果对话框
        if (showFullResultDialog && result != null) {
            ResultDialog(
                title = toolName,
                content = result,
                params = params,
                onDismiss = { showFullResultDialog = false }
            )
        }
        
        return
    }
    
    // 非折叠模式下的完整显示
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
                if (enableCopy && result != null) {
                    it.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { 
                            if (!isProcessing && result.length > MAX_RESULT_LENGTH) {
                                showFullResultDialog = true
                            }
                        },
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(result))
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
                .padding(16.dp)
        ) {
            // 工具执行标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side with icon and tool name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 图标
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
                                .size(18.dp)
                                .rotate(rotation)
                        } else {
                            Modifier.size(18.dp)
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 工具名称
                    Column {
                        Text(
                            text = when {
                                isProcessing -> "正在执行工具: $toolName"
                                isError -> "工具执行失败: $toolName"
                                else -> "工具执行成功: $toolName"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isProcessing)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else if (isError)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        
                        // 显示参数值（如果有）
                        if (params != null && params.isNotBlank()) {
                            val paramValuesText = extractParamValues(params)
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
                
                // Expand/collapse button for results
                if (!isProcessing && result != null && result.length > MAX_RESULT_LENGTH) {
                    IconButton(onClick = { isResultExpanded = !isResultExpanded }) {
                        Icon(
                            imageVector = if (isResultExpanded) 
                                Icons.Default.KeyboardArrowUp 
                            else 
                                Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isResultExpanded) "收起" else "展开",
                            tint = if (isProcessing)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else if (isError)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            // 处理中状态显示进度条
            if (isProcessing) {
                Spacer(modifier = Modifier.height(12.dp))
                SimpleLinearProgressIndicator(
                    progress = 1f, // 使用无限循环进度条
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "工具正在执行中，请稍候...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            // 结果内容
            if (!isProcessing && result != null) {
                Spacer(modifier = Modifier.height(12.dp))
                
                val displayResult = if (result.length > MAX_RESULT_LENGTH && !isResultExpanded) {
                    result.take(MAX_RESULT_LENGTH) + "..."
                } else {
                    result
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp, max = if (isResultExpanded) 300.dp else 150.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isError)
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.1f)
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f)
                        )
                ) {
                    SelectionContainer {
                        Text(
                            text = displayResult,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = if (isError)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp)
                        )
                    }
                }
                
                if (result.length > MAX_RESULT_LENGTH && !isResultExpanded) {
                    TextButton(
                        onClick = { showFullResultDialog = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("查看完整内容")
                    }
                }
            }
        }
    }
    
    // Dialog for full result content
    if (showFullResultDialog && result != null) {
        ResultDialog(
            title = toolName,
            content = result,
            params = params,
            onDismiss = { showFullResultDialog = false }
        )
    }
}

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