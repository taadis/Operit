package com.ai.assistance.operit.ui.floating.ui.window

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.components.CursorStyleChatMessage

/** 单个消息项组件 将消息渲染逻辑提取到单独的组件，减少重组范围 */
@Composable
fun MessageItem(
        index: Int,
        message: ChatMessage,
        allMessages: List<ChatMessage>,
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
                // 检查这是否是最后一条AI消息
                val isLastAiMessage =
                        message.sender == "ai" &&
                                index == allMessages.indexOfLast { it.sender == "ai" }
                val streamToRender = if (isLastAiMessage) message.contentStream else null

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
                        initialThinkingExpanded = true,
                        overrideStream = streamToRender
                )

                // 在编辑模式下显示编辑指示器
                if (isEditMode && (message.sender == "user" || message.sender == "ai")) {
                        // 复制按钮
                        Box(
                                modifier =
                                        Modifier.align(Alignment.TopEnd)
                                                .padding(
                                                        end = 46.dp,
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
                                                ) {
                                                        // 实现复制功能
                                                        val clipboardManager =
                                                                context.getSystemService(
                                                                        Context.CLIPBOARD_SERVICE
                                                                ) as
                                                                        ClipboardManager
                                                        val clipData =
                                                                ClipData.newPlainText(
                                                                        "聊天消息",
                                                                        message.content
                                                                )
                                                        clipboardManager.setPrimaryClip(clipData)

                                                        // 显示复制成功的提示
                                                        Toast.makeText(
                                                                        context,
                                                                        "消息已复制到剪贴板",
                                                                        Toast.LENGTH_SHORT
                                                                )
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
