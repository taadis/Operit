package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.graphics.Color
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
                                        .background(
                                                if (hasBackgroundImage) Color.Transparent
                                                else MaterialTheme.colorScheme.surface
                                        )
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
                                        modifier = Modifier
                                                .fillMaxWidth()
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
                                                onSelectMessageToEdit = onSelectMessageToEdit
                                        )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                        }
                }

                // Loading indicator
                if (isLoading) {
                        Box(
                                modifier =
                                        Modifier.align(Alignment.CenterHorizontally)
                                                .padding(vertical = 8.dp)
                        ) {
                                Card(
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.8f)
                                                ),
                                        elevation =
                                                CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                        Text(
                                                text = "正在处理...",
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 8.dp
                                                        ),
                                                style = MaterialTheme.typography.bodyMedium
                                        )
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
        onSelectMessageToEdit: ((Int, ChatMessage) -> Unit)?
) {
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
                        Box(
                                modifier =
                                        Modifier.align(Alignment.TopEnd)
                                                .padding(
                                                        end = 20.dp,
                                                        top =
                                                                if (message.sender == "user") 6.dp
                                                                else 0.dp
                                                )
                                                .size(32.dp)
                                                .clickable(
                                                        interactionSource =
                                                                remember {
                                                                        MutableInteractionSource()
                                                                },
                                                        indication =
                                                                rememberRipple(
                                                                        bounded = false,
                                                                        radius = 20.dp,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
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
