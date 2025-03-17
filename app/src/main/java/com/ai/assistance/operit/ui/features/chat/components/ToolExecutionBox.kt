package com.ai.assistance.operit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextOverflow

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
    enableCopy: Boolean = false,
    hideToolRequest: Boolean = true // 默认隐藏工具请求
) {
    val accentColor = if (isError) 
        MaterialTheme.colorScheme.error 
    else 
        MaterialTheme.colorScheme.primary
    
    val backgroundColor = if (isProcessing)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    else if (isError)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    else
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    
    val textColor = if (isProcessing)
        MaterialTheme.colorScheme.onPrimaryContainer
    else if (isError)
        MaterialTheme.colorScheme.onErrorContainer
    else
        MaterialTheme.colorScheme.onSecondaryContainer
    
    // For clipboard functionality
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    
    // State for collapsible result
    var isResultExpanded by remember { mutableStateOf(false) }
    
    // State for full result dialog
    var showFullResultDialog by remember { mutableStateOf(false) }
    
    // If hideToolRequest is true and tool is processing, don't show anything
    if (hideToolRequest && isProcessing) {
        return
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(backgroundColor)
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
                        tint = accentColor,
                        modifier = if (isProcessing) {
                            Modifier
                                .size(18.dp)
                                .rotate(
                                    animateFloatAsState(
                                        targetValue = (System.currentTimeMillis() / 10 % 360).toFloat(),
                                        animationSpec = tween(0),
                                        label = "loading"
                                    ).value
                                )
                        } else {
                            Modifier.size(18.dp)
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // 工具名称
                    Text(
                        text = when {
                            isProcessing -> "正在执行工具: $toolName"
                            isError -> "工具执行失败: $toolName"
                            else -> "工具执行成功: $toolName"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor
                    )
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
                            tint = textColor
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
                    color = textColor.copy(alpha = 0.7f),
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
                
                SelectionContainer {
                    Text(
                        text = displayResult,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = textColor,
                        maxLines = if (isResultExpanded) Int.MAX_VALUE else 15,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(textColor.copy(alpha = 0.1f))
                            .padding(8.dp)
                    )
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
    if (showFullResultDialog) {
        Dialog(onDismissRequest = { showFullResultDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    // Dialog title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = toolName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        IconButton(onClick = { showFullResultDialog = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭"
                            )
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Result content in scrollable box
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        SelectionContainer {
                            Text(
                                text = result ?: "",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }
                    
                    // Copy button
                    Button(
                        onClick = {
                            result?.let { clipboardManager.setText(AnnotatedString(it)) }
                            showFullResultDialog = false
                        },
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .align(Alignment.End)
                    ) {
                        Text("复制内容")
                    }
                }
            }
        }
    }
} 