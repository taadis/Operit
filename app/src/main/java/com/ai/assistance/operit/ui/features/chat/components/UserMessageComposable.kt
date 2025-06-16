package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable

/**
 * A composable function for rendering user messages in a Cursor IDE style. Supports text selection
 * and copy on long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserMessageComposable(message: ChatMessage, backgroundColor: Color, textColor: Color) {
        val clipboardManager = LocalClipboardManager.current
        val haptic = LocalHapticFeedback.current

        // 添加状态控制内容预览
        val showContentPreview = remember { mutableStateOf(false) }
        val selectedAttachmentContent = remember { mutableStateOf("") }
        val selectedAttachmentName = remember { mutableStateOf("") }

        // Parse message content to separate text and attachments
        val parseResult = remember(message.content) { parseMessageContent(message.content) }
        val textContent = parseResult.processedText
        val trailingAttachments = parseResult.trailingAttachments

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                // Display trailing attachments above the message bubble
                if (trailingAttachments.isNotEmpty()) {
                        // Display attachment row above the bubble
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                trailingAttachments.forEach { attachment ->
                                        AttachmentTag(
                                                filename = attachment.filename,
                                                type = attachment.type,
                                                textColor = textColor,
                                                backgroundColor = backgroundColor,
                                                content = attachment.content,
                                                onClick = { content, name ->
                                                        // 当点击附件标签时，显示内容预览
                                                        if (content.isNotEmpty()) {
                                                                selectedAttachmentContent.value =
                                                                        content
                                                                selectedAttachmentName.value = name
                                                                showContentPreview.value = true
                                                        }
                                                }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                }
                        }
                }

                // Message bubble
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .combinedClickable(
                                                interactionSource =
                                                        remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { /* Do nothing on normal click */},
                                                onLongClick = {
                                                        clipboardManager.setText(
                                                                AnnotatedString(message.content)
                                                        )
                                                        haptic.performHapticFeedback(
                                                                HapticFeedbackType.LongPress
                                                        )
                                                }
                                        ),
                        colors = CardDefaults.cardColors(containerColor = backgroundColor),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                        text = "Prompt",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textColor.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Display main text content with inline attachments
                                Text(text = textContent, color = textColor,style = MaterialTheme.typography.bodyMedium)
                        }
                }
        }

        // 内容预览对话框
        if (showContentPreview.value) {
                Dialog(onDismissRequest = { showContentPreview.value = false }) {
                        Surface(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp
                        ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                        // 头部
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Code,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                        Text(
                                                                text = selectedAttachmentName.value,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                        )
                                                }

                                                IconButton(
                                                        onClick = {
                                                                showContentPreview.value = false
                                                        }
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "关闭",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                        )
                                                }
                                        }

                                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                                        // 内容区域
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .wrapContentHeight(
                                                                        align = Alignment.Top
                                                                )
                                                                .weight(1f, fill = false)
                                                                .border(
                                                                        width = 1.dp,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceVariant,
                                                                        shape =
                                                                                RoundedCornerShape(
                                                                                        4.dp
                                                                                )
                                                                )
                                                                .padding(8.dp)
                                                                .verticalScroll(
                                                                        rememberScrollState()
                                                                )
                                        ) {
                                                Text(
                                                        text = selectedAttachmentContent.value,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace
                                                )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // 复制按钮
                                        Button(
                                                onClick = {
                                                        clipboardManager.setText(
                                                                AnnotatedString(
                                                                        selectedAttachmentContent
                                                                                .value
                                                                )
                                                        )
                                                        showContentPreview.value = false
                                                },
                                                modifier = Modifier.align(Alignment.End)
                                        ) { Text("复制内容") }
                                }
                        }
                }
        }
}

/** Result of parsing message content, containing processed text and trailing attachments */
data class MessageParseResult(
        val processedText: String,
        val trailingAttachments: List<AttachmentData>
)

/**
 * Parses the message content to extract text and attachments Keeps inline attachments as @filename
 * in the text Extracts trailing attachments that appear at the end of the message
 */
private fun parseMessageContent(content: String): MessageParseResult {
        val attachments = mutableListOf<AttachmentData>()
        val trailingAttachments = mutableListOf<AttachmentData>()
        val messageText = StringBuilder()

        // 先用简单的分割方式检测有没有附件标签
        if (!content.contains("<attachment")) {
                return MessageParseResult(content, emptyList())
        }

        try {
                // Enhanced regex pattern to find attachments with optional content attribute
                // 注意：由于content属性可能包含json数据，我们用非贪婪匹配确保正确解析
                val attachmentPattern =
                        "<attachment\\s+id=\"([^\"]+)\"\\s+filename=\"([^\"]+)\"\\s+type=\"([^\"]+)\"(?:\\s+size=\"([^\"]+)\")?(?:\\s+content=\"(.*?)\")?\\s*/>".toRegex(
                                RegexOption.DOT_MATCHES_ALL
                        )

                // Get all matches
                val matches = attachmentPattern.findAll(content).toList()
                if (matches.isEmpty()) {
                        return MessageParseResult(content, emptyList())
                }

                // Find the last non-whitespace character after the last attachment
                val lastMatch = matches.last()
                val contentAfterLastMatch = content.substring(lastMatch.range.last + 1).trim()

                // Process all attachments
                var lastIndex = 0
                matches.forEachIndexed { index, matchResult ->
                        // Add text before this attachment
                        val startIndex = matchResult.range.first
                        if (startIndex > lastIndex) {
                                messageText.append(content.substring(lastIndex, startIndex))
                        }

                        // Extract attachment data
                        val id = matchResult.groupValues[1]
                        val filename = matchResult.groupValues[2]
                        val type = matchResult.groupValues[3]
                        val size = matchResult.groupValues[4].toLongOrNull() ?: 0L
                        val attachmentContent = matchResult.groupValues[5]

                        // Create attachment data object, including content if available
                        val attachment =
                                AttachmentData(
                                        id = id,
                                        filename = filename,
                                        type = type,
                                        size = size,
                                        content = attachmentContent
                                )

                        // Determine if this is a trailing attachment
                        val isLastAttachment = index == matches.size - 1
                        val isTrailingAttachment =
                                isLastAttachment && contentAfterLastMatch.isEmpty()

                        // 特殊处理屏幕内容附件，始终将其作为trailing attachment
                        val isScreenContent =
                                (type == "text/json" && filename == "screen_content.json")

                        if (isTrailingAttachment || isScreenContent) {
                                // This is a trailing attachment, extract it
                                trailingAttachments.add(attachment)
                        } else {
                                // This is an inline attachment, keep it in the text as @filename
                                messageText.append("@${filename}")
                                // Also add to general attachments list for reference
                                attachments.add(attachment)
                        }

                        lastIndex = matchResult.range.last + 1
                }

                // Add any remaining text
                if (lastIndex < content.length) {
                        messageText.append(content.substring(lastIndex))
                }

                return MessageParseResult(messageText.toString(), trailingAttachments)
        } catch (e: Exception) {
                // 如果解析失败，返回原始内容
                android.util.Log.e("UserMessageComposable", "解析消息内容失败", e)
                return MessageParseResult(content, emptyList())
        }
}

/** Data class for attachment information */
data class AttachmentData(
        val id: String,
        val filename: String,
        val type: String,
        val size: Long = 0,
        val content: String = "" // Added content field
)

/** Compact attachment tag component for displaying in user messages */
@Composable
private fun AttachmentTag(
        filename: String,
        type: String,
        textColor: Color,
        backgroundColor: Color,
        content: String = "",
        onClick: (String, String) -> Unit = { _, _ -> }
) {
        // 根据附件类型选择图标
        val icon: ImageVector =
                when {
                        type.startsWith("image/") -> Icons.Default.Image
                        type == "text/json" && filename == "screen_content.json" ->
                                Icons.Default.ScreenshotMonitor
                        else -> Icons.Default.Description
                }

        // 根据附件类型调整显示标签
        val displayLabel =
                when {
                        type == "text/json" && filename == "screen_content.json" -> "屏幕内容"
                        else -> filename
                }

        Surface(
                modifier =
                        Modifier.height(24.dp)
                                .padding(vertical = 2.dp)
                                .clickable(
                                        enabled = content.isNotEmpty(),
                                        onClick = { onClick(content, filename) }
                                ),
                shape = RoundedCornerShape(12.dp),
                color = backgroundColor.copy(alpha = 0.5f)
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = textColor.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                                text = displayLabel,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = textColor,
                                modifier = Modifier.widthIn(max = 120.dp)
                        )
                }
        }
}
