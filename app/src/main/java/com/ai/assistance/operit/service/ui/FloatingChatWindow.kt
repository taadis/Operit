package com.ai.assistance.operit.service.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.FiberSmartRecord
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.components.CursorStyleChatMessage

/**
 * 悬浮聊天窗口的主要UI组件
 * 
 * @param messages 要显示的聊天消息列表
 * @param width 窗口宽度
 * @param height 窗口高度
 * @param onClose 关闭窗口的回调
 * @param onResize 调整窗口大小的回调
 * @param isBallMode 是否为球模式
 * @param ballSize 球的大小
 * @param onToggleBallMode 切换球模式的回调
 */
@Composable
fun FloatingChatWindow(
    messages: List<ChatMessage>,
    width: Dp,
    height: Dp,
    onClose: () -> Unit,
    onResize: (Dp, Dp) -> Unit,
    isBallMode: Boolean = false,
    ballSize: Dp = 56.dp,
    onToggleBallMode: () -> Unit = {}
) {
    // 使用标准状态代替collectAsState，避免兼容性问题
    val updatedMessages = remember(messages) { messages }
    
    // 主题颜色
    val backgroundColor = MaterialTheme.colorScheme.background
    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    var isDraggingResizeHandle by remember { mutableStateOf(false) }
    var localWidth by remember { mutableStateOf(width) }
    var localHeight by remember { mutableStateOf(height) }
    
    // 最小窗口尺寸
    val minWidth = 200.dp
    val minHeight = 200.dp
    
    if (isBallMode) {
        // 悬浮球模式
        Box(
            modifier = Modifier
                .size(ballSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            // 点击悬浮球展开为正常窗口
            IconButton(
                onClick = onToggleBallMode,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.FiberSmartRecord,
                    contentDescription = "展开",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    } else {
        // 正常窗口模式
        Box(
            modifier = Modifier
                .width(localWidth)
                .height(localHeight)
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 消息列表 (占据整个空间)
                val listState = rememberLazyListState()
                
                // 自动滚动到底部
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 32.dp, start = 8.dp, end = 8.dp, bottom = 8.dp)
                ) {
                    items(updatedMessages) { message ->
                        // 使用与主应用相同的消息组件
                        CursorStyleChatMessage(
                            message = message,
                            userMessageColor = userMessageColor,
                            aiMessageColor = aiMessageColor,
                            userTextColor = userTextColor,
                            aiTextColor = aiTextColor,
                            systemMessageColor = systemMessageColor,
                            systemTextColor = systemTextColor,
                            thinkingBackgroundColor = thinkingBackgroundColor,
                            thinkingTextColor = thinkingTextColor,
                            supportToolMarkup = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            
            // 标题栏风格的头部区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    .align(Alignment.TopCenter)
            ) {
                Text(
                    text = "AI助手悬浮窗",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                )
            }
            
            // 关闭按钮（右上角）
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // 添加最小化到球模式的按钮（左上角）
            IconButton(
                onClick = onToggleBallMode,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Minimize,
                    contentDescription = "缩小为悬浮球",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // 调整大小手柄 (在右下角)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                isDraggingResizeHandle = true
                                
                            },
                            onDragEnd = {
                                isDraggingResizeHandle = false
                                
                                // 应用最终尺寸
                                onResize(localWidth, localHeight)
                            },
                            onDragCancel = {
                                isDraggingResizeHandle = false
                                
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                
                                // 调整窗口大小
                                localWidth = (localWidth + dragAmount.x.toDp()).coerceAtLeast(minWidth)
                                localHeight = (localHeight + dragAmount.y.toDp()).coerceAtLeast(minHeight)
                                
                                // 实时更新
                                onResize(localWidth, localHeight)
                            }
                        )
                    }
            ) {
                // 调整大小手柄图标 (简单的对角线)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width * 0.3f, size.height * 0.7f),
                        end = Offset(size.width * 0.7f, size.height * 0.3f),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width * 0.5f, size.height * 0.7f),
                        end = Offset(size.width * 0.7f, size.height * 0.5f),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
} 