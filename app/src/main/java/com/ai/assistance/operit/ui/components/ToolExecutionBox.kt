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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

// 工具执行状态框组件
@Composable
fun ToolExecutionBox(
    toolName: String,
    isProcessing: Boolean,
    result: String?,
    isError: Boolean
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 工具执行标题栏
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
                
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = textColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(textColor.copy(alpha = 0.1f))
                        .padding(8.dp)
                )
            }
        }
    }
} 