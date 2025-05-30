package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.ToolExecutionProgress
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ChatScreenContent(
    paddingValues: PaddingValues,
    actualViewModel: ChatViewModel,
    showChatHistorySelector: Boolean,
    chatHistory: List<ChatMessage>,
    listState: LazyListState,
    planItems: List<PlanItem>,
    enableAiPlanning: Boolean,
    toolProgress: ToolExecutionProgress,
    isLoading: Boolean,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color,
    hasBackgroundImage: Boolean,
    isEditMode: MutableState<Boolean>,
    editingMessageIndex: MutableState<Int?>,
    editingMessageContent: MutableState<String>,
    chatScreenGestureConsumed: Boolean,
    onChatScreenGestureConsumed: (Boolean) -> Unit,
    currentDrag: Float,
    onCurrentDragChange: (Float) -> Unit,
    verticalDrag: Float,
    onVerticalDragChange: (Float) -> Unit,
    dragThreshold: Float,
    showScrollButton: Boolean,
    onShowScrollButtonChange: (Boolean) -> Unit,
    autoScrollToBottom: Boolean,
    onAutoScrollToBottomChange: (Boolean) -> Unit,
    coroutineScope: CoroutineScope,
    chatHistories: List<ChatHistory>,
    currentChatId: String,
) {
    // 获取WebView状态
    val showWebView = actualViewModel.showWebView.collectAsState().value
    
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // 主聊天区域（包括顶部工具栏），确保它一直可见
        Column(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onChatScreenGestureConsumed(false)
                            onCurrentDragChange(0f)
                            onVerticalDragChange(0f)
                        },
                        onDragEnd = {
                            // 手势结束后重置累计值
                            onCurrentDragChange(0f)
                            onVerticalDragChange(0f)
                            // 延迟重置消费状态，确保事件不会传递到全局侧边栏
                            onChatScreenGestureConsumed(false)
                        },
                        onDragCancel = {
                            onCurrentDragChange(0f)
                            onVerticalDragChange(0f)
                            onChatScreenGestureConsumed(false)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            // 累加水平拖动距离
                            val newDrag = currentDrag + dragAmount
                            onCurrentDragChange(newDrag)

                            // 保持判定条件简单，与PhoneLayout类似
                            val dragRight = dragAmount > 0 // 即时判断方向，而不是累计方向
                            
                            // 添加日志记录手势状态
                            Log.d("ChatScreenContent", "手势状态: 方向=${if(dragRight) "右" else "左"}, 累计=${newDrag}, 垂直=${verticalDrag}, 显示历史=${showChatHistorySelector}")

                            // 简化判定条件：只要是向右滑动且累积量超过阈值就触发
                            if (!showChatHistorySelector &&
                                dragRight &&
                                newDrag > dragThreshold &&
                                Math.abs(newDrag) > Math.abs(verticalDrag)
                            ) {
                                Log.d("ChatScreenContent", "触发打开历史记录：累计=${newDrag}")
                                actualViewModel.showChatHistorySelector(true)
                                // 告知父组件手势已被消费
                                onChatScreenGestureConsumed(true)
                                change.consume()
                            }

                            // 如果是从右向左滑动，且历史选择器已显示，则关闭历史选择器
                            if (dragAmount < 0 &&
                                showChatHistorySelector &&
                                newDrag < -dragThreshold
                            ) {
                                Log.d("ChatScreenContent", "触发关闭历史记录：累计=${newDrag}")
                                actualViewModel.showChatHistorySelector(false)
                                onChatScreenGestureConsumed(true)
                                change.consume()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    // 添加垂直方向手势检测，用于记录垂直拖动距离
                    detectVerticalDragGestures { _, dragAmount ->
                        onVerticalDragChange(verticalDrag + dragAmount)
                    }
                }
        ) {
            // 聊天区域
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部工具栏 - 整合聊天历史按钮和统计信息 - 始终显示在顶部
                ChatScreenHeader(
                    actualViewModel = actualViewModel,
                    showChatHistorySelector = showChatHistorySelector,
                    chatHistories = chatHistories,
                    currentChatId = currentChatId,
                    isEditMode = isEditMode,
                    showWebView = showWebView,
                    onWebDevClick = { actualViewModel.toggleWebView() }
                )

                // 聊天对话区域
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .weight(1f)
                        .background(if (hasBackgroundImage) Color.Transparent else MaterialTheme.colorScheme.background)
                ) {
                    // 只有在不显示WebView时才显示聊天区域
                    if (!showWebView) {
                        ChatArea(
                            chatHistory = chatHistory,
                            listState = listState,
                            planItems = planItems,
                            enablePlanning = enableAiPlanning,
                            toolProgress = toolProgress,
                            isLoading = isLoading,
                            userMessageColor = userMessageColor,
                            aiMessageColor = aiMessageColor,
                            userTextColor = userTextColor,
                            aiTextColor = aiTextColor,
                            systemMessageColor = systemMessageColor,
                            systemTextColor = systemTextColor,
                            thinkingBackgroundColor = thinkingBackgroundColor,
                            thinkingTextColor = thinkingTextColor,
                            hasBackgroundImage = hasBackgroundImage,
                            modifier = Modifier.fillMaxSize(),
                            isEditMode = isEditMode.value,
                            onSelectMessageToEdit = { index, message ->
                                editingMessageIndex.value = index
                                editingMessageContent.value = message.content
                            }
                        )
                        
                        // 编辑模式下的操作面板
                        if (isEditMode.value && editingMessageIndex.value != null) {
                            MessageEditPanel(
                                editingMessageContent = editingMessageContent,
                                onCancel = {
                                    editingMessageIndex.value = null
                                    editingMessageContent.value = ""
                                },
                                onSave = {
                                    val index = editingMessageIndex.value
                                    if (index != null && index < chatHistory.size) {
                                        val editedMessage = chatHistory[index].copy(
                                            content = editingMessageContent.value
                                        )
                                        actualViewModel.updateMessage(index, editedMessage)

                                        // 重置编辑状态
                                        editingMessageIndex.value = null
                                        editingMessageContent.value = ""
                                    }
                                },
                                onResend = {
                                    val index = editingMessageIndex.value
                                    if (index != null && index < chatHistory.size) {
                                        actualViewModel.rewindAndResendMessage(
                                            index,
                                            editingMessageContent.value
                                        )

                                        // 重置编辑状态
                                        editingMessageIndex.value = null
                                        editingMessageContent.value = ""
                                        isEditMode.value = false
                                    }
                                }
                            )
                        }
                    }
                    
                    // 滚动到底部按钮 - 仅在聊天区域显示时显示
                    if (showScrollButton && !showWebView) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            ScrollToBottomButton(
                                onClick = {
                                    // 点击按钮：启用自动滚动，隐藏按钮，并立即滚动到底部
                                    onAutoScrollToBottomChange(true)
                                    onShowScrollButtonChange(false)

                                    coroutineScope.launch {
                                        if (chatHistory.isNotEmpty()) {
                                            try {
                                                // 不关心index，直接尝试滚动到底部
                                                // 使用最大可能的滚动量
                                                listState.dispatchRawDelta(100000f)
                                            } catch (e: Exception) {
                                                Log.e("ChatScreenContent", "滚动到底部失败", e)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // WebView作为独立层，避免与顶部Header冲突
        if (showWebView) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 添加一个空白区域，与ChatScreenHeader相同高度
                Spacer(modifier = Modifier.height(56.dp)) // 使用大致的ChatScreenHeader高度
                
                // WebView区域 - 只占用内容区域，不包括顶部工具栏
                AndroidView(
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            webViewClient = android.webkit.WebViewClient()
                            settings.javaScriptEnabled = true
                            loadUrl("https://www.baidu.com")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }

        // 历史选择器作为浮动层，使用AnimatedVisibility保持动画效果
        AnimatedVisibility(
            visible = showChatHistorySelector,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            ChatHistorySelectorPanel(
                actualViewModel = actualViewModel,
                chatHistories = chatHistories, 
                currentChatId = currentChatId,
                showChatHistorySelector = showChatHistorySelector
            )
        }
    }
}

@Composable
fun ScrollToBottomButton(
    onClick: () -> Unit
) {
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = Modifier.padding(end = 16.dp),
        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "滚动到底部",
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ChatHistorySelectorPanel(
    actualViewModel: ChatViewModel,
    chatHistories: List<ChatHistory>,
    currentChatId: String,
    showChatHistorySelector: Boolean
) {
    // 添加一个覆盖整个屏幕的半透明点击区域，用于关闭历史选择器
    Box(modifier = Modifier.fillMaxSize()) {
        // 透明遮罩层，点击右侧空白处关闭历史选择器 - 修改为不拦截滑动事件
        Box(
            modifier = Modifier.fillMaxSize()
                // 使用pointerInput替代clickable，以便只处理点击事件而不拦截滑动
                .pointerInput(Unit) {
                    detectTapGestures {
                        actualViewModel.toggleChatHistorySelector()
                    }
                }
                .background(Color.Black.copy(alpha = 0.1f))
        )

        // 历史选择器面板
        Box(
            modifier = Modifier.width(280.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                )
        ) {
            // 直接使用ChatHistorySelector
            ChatHistorySelector(
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                onNewChat = {
                    actualViewModel.createNewChat()
                    // 创建新对话后自动收起侧边框
                    actualViewModel.showChatHistorySelector(false)
                },
                onSelectChat = { chatId ->
                    actualViewModel.switchChat(chatId)
                    // 切换聊天后也自动收起侧边框
                    actualViewModel.showChatHistorySelector(false)
                },
                onDeleteChat = { chatId ->
                    actualViewModel.deleteChatHistory(chatId)
                },
                chatHistories = chatHistories.sortedByDescending { it.createdAt },
                currentId = currentChatId
            )

            // 在右侧添加浮动返回按钮
            OutlinedButton(
                onClick = { actualViewModel.toggleChatHistorySelector() },
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 8.dp)
                    .height(28.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("返回", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
} 