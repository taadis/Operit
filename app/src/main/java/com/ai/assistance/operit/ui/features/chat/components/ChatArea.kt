package com.ai.assistance.operit.ui.features.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.ai.assistance.operit.data.model.AiReference
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.ToolExecutionProgress

@Composable
fun ChatArea(
        chatHistory: List<ChatMessage>,
        scrollState: ScrollState,
        aiReferences: List<AiReference> = emptyList(),
        planItems: List<PlanItem> = emptyList(),
        enablePlanning: Boolean = false,
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
        hasBackgroundImage: Boolean = false,
        modifier: Modifier = Modifier,
        isEditMode: Boolean = false,
        onSelectMessageToEdit: ((Int, ChatMessage) -> Unit)? = null,
        onCopyMessage: ((ChatMessage) -> Unit)? = null,
        messagesPerPage: Int = 10 // 每页显示的消息数量
) {
    // 记住当前深度状态，但当chatHistory发生变化时重置为1
    var currentDepth = remember(chatHistory) { mutableStateOf(1) }

    Column(modifier = modifier) {
        // 移除References display

        // Plan display when planning is enabled and there are plan items
        if (enablePlanning && planItems.isNotEmpty()) {
            PlanGraphDisplay(planItems = planItems, modifier = Modifier.fillMaxWidth())
        } else {
            // 删除全部不必要的诊断日志
        }

        // Tool progress bar
        ToolProgressBar(toolProgress = toolProgress, modifier = Modifier.fillMaxWidth())

        // 改用普通Column替代LazyColumn，避免复杂的回收逻辑带来的性能问题
        Column(
                modifier =
                        Modifier.weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .verticalScroll(scrollState) // 使用从外部传入的scrollState
                                .background(Color.Transparent)
        ) {
            val messagesCount = chatHistory.size
            val maxVisibleIndex = messagesCount - 1
            val minVisibleIndex =
                    maxOf(0, maxVisibleIndex - currentDepth.value * messagesPerPage + 1)
            val hasMoreMessages = minVisibleIndex > 0

            // "加载更多"文本 - 改为灰色文本而非按钮
            if (hasMoreMessages) {
                Text(
                        text = "加载更多历史消息...",
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clickable { currentDepth.value += 1 }
                                        .padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 根据当前深度筛选显示的消息
            chatHistory.subList(minVisibleIndex, messagesCount).forEachIndexed {
                    relativeIndex,
                    message ->
                val actualIndex = minVisibleIndex + relativeIndex
                // 使用key组合函数为每个消息项设置单独的重组作用域
                key(message) {
                    MessageItem(
                            index = actualIndex,
                            message = message,
                            userMessageColor = userMessageColor,
                            aiMessageColor = aiMessageColor,
                            userTextColor = userTextColor,
                            aiTextColor = aiTextColor,
                            systemMessageColor = systemMessageColor,
                            systemTextColor = systemTextColor,
                            thinkingBackgroundColor = thinkingBackgroundColor,
                            thinkingTextColor = thinkingTextColor,
                            isEditMode = isEditMode,
                            onSelectMessageToEdit = onSelectMessageToEdit,
                            onCopyMessage = onCopyMessage
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // 当AI正在响应但尚未输出任何文本时，显示加载指示器
            val lastMessage = chatHistory.lastOrNull { it.sender != "think" }
            if (isLoading &&
                            (lastMessage?.sender == "user" ||
                                    (lastMessage?.sender == "ai" && lastMessage.content.isBlank()))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)) {
                    // 加载指示器放在左侧，与标签对齐
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        LoadingDotsIndicator(aiTextColor)
                    }
                }
            }
        }
    }
}

/** 单个消息项组件 将消息渲染逻辑提取到单独的组件，减少重组范围 */
@Composable
private fun MessageItem(
        index: Int,
        message: ChatMessage,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        isEditMode: Boolean,
        onSelectMessageToEdit: ((Int, ChatMessage) -> Unit)?,
        onCopyMessage: ((ChatMessage) -> Unit)?
) {
    // 添加Context以访问剪贴板服务
    val context = LocalContext.current

    // 编辑模式下为消息添加点击功能
    val messageModifier =
            if (isEditMode && message.sender != "think") {
                Modifier.clickable { onSelectMessageToEdit?.invoke(index, message) }
            } else {
                Modifier
            }

    Box(modifier = messageModifier) {
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
                supportToolMarkup = true,
                initialThinkingExpanded = true
        )

        // 在编辑模式下显示编辑指示器
        if (isEditMode && (message.sender == "user" || message.sender == "ai")) {
            // 复制按钮
            Box(
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .padding(
                                            end = 46.dp,
                                            top = if (message.sender == "user") 6.dp else 0.dp
                                    )
                                    .size(32.dp)
                                    .clickable(
                                            interactionSource =
                                                    remember { MutableInteractionSource() },
                                            indication =
                                                    rememberRipple(
                                                            bounded = false,
                                                            radius = 20.dp,
                                                            color =
                                                                    MaterialTheme.colorScheme
                                                                            .primary
                                                    )
                                    ) {
                                        // 实现复制功能
                                        val clipboardManager =
                                                context.getSystemService(
                                                        Context.CLIPBOARD_SERVICE
                                                ) as
                                                        ClipboardManager
                                        val clipData =
                                                ClipData.newPlainText("聊天消息", message.content)
                                        clipboardManager.setPrimaryClip(clipData)

                                        // 显示复制成功的提示
                                        Toast.makeText(context, "消息已复制到剪贴板", Toast.LENGTH_SHORT)
                                                .show()

                                        // 如果有外部回调，也调用它
                                        onCopyMessage?.invoke(message)
                                    }
            ) {
                Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制此消息",
                        modifier = Modifier.align(Alignment.Center).size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

            // 编辑按钮
            Box(
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .padding(
                                            end = 20.dp,
                                            top = if (message.sender == "user") 6.dp else 0.dp
                                    )
                                    .size(32.dp)
                                    .clickable(
                                            interactionSource =
                                                    remember { MutableInteractionSource() },
                                            indication =
                                                    rememberRipple(
                                                            bounded = false,
                                                            radius = 20.dp,
                                                            color =
                                                                    MaterialTheme.colorScheme
                                                                            .primary
                                                    )
                                    ) { onSelectMessageToEdit?.invoke(index, message) }
            ) {
                Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑此消息",
                        modifier = Modifier.align(Alignment.Center).size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun LoadingDotsIndicator(textColor: Color) {
    val infiniteTransition = rememberInfiniteTransition()

    Row(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        val jumpHeight = -5f
        val animationDelay = 160

        (0..2).forEach { index ->
            val offsetY by
                    infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = jumpHeight,
                            animationSpec =
                                    infiniteRepeatable(
                                            animation =
                                                    keyframes {
                                                        durationMillis = 600
                                                        0f at 0
                                                        jumpHeight * 0.4f at 100
                                                        jumpHeight * 0.8f at 200
                                                        jumpHeight at 300
                                                        jumpHeight * 0.8f at 400
                                                        jumpHeight * 0.4f at 500
                                                        0f at 600
                                                    },
                                            repeatMode = RepeatMode.Restart,
                                            initialStartOffset = StartOffset(index * animationDelay)
                                    ),
                            label = ""
                    )

            Box(
                    modifier =
                            Modifier.size(6.dp)
                                    .offset(y = offsetY.dp)
                                    .background(
                                            color = textColor.copy(alpha = 0.6f),
                                            shape = CircleShape
                                    )
            )
        }
    }
}
