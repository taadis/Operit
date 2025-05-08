package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.common.animations.SimpleAnimatedVisibility
import com.ai.assistance.operit.ui.features.chat.viewmodel.AttachmentInfo

@Composable
fun ChatInputSection(
        userMessage: String,
        onUserMessageChange: (String) -> Unit,
        onSendMessage: () -> Unit,
        onCancelMessage: () -> Unit,
        isLoading: Boolean,
        isProcessingInput: Boolean,
        inputProcessingMessage: String,
        onAttachmentRequest: (android.net.Uri) -> Unit = {},
        attachments: List<AttachmentInfo> = emptyList(),
        onRemoveAttachment: (android.net.Uri) -> Unit = {},
        onInsertAttachment: (AttachmentInfo) -> Unit = {},
        modifier: Modifier = Modifier
) {
        val modernTextStyle = TextStyle(fontSize = 14.sp)

        val isProcessing = isLoading || isProcessingInput

        // 控制附件面板的展开状态
        val (showAttachmentPanel, setShowAttachmentPanel) =
                androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = modifier.shadow(4.dp)
        ) {
                Column {
                        // Input processing indicator
                        SimpleAnimatedVisibility(visible = isProcessingInput) {
                                val progressColor =
                                        when {
                                                inputProcessingMessage.contains("工具执行后") ->
                                                        MaterialTheme.colorScheme.tertiary.copy(
                                                                alpha = 0.8f
                                                        )
                                                inputProcessingMessage.contains("Connecting") ||
                                                        inputProcessingMessage.contains("连接") ->
                                                        MaterialTheme.colorScheme.tertiary
                                                inputProcessingMessage.contains("Receiving") ||
                                                        inputProcessingMessage.contains("响应") ->
                                                        MaterialTheme.colorScheme.secondary
                                                else -> MaterialTheme.colorScheme.primary
                                        }

                                val progressValue =
                                        if (inputProcessingMessage.contains("准备")) 0.3f
                                        else if (inputProcessingMessage.contains("连接")) 0.6f else 1f

                                SimpleLinearProgressIndicator(
                                        progress = progressValue,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = progressColor
                                )

                                if (inputProcessingMessage.isNotBlank()) {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 4.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Text(
                                                        text = inputProcessingMessage,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme.onSurface
                                                                        .copy(alpha = 0.8f),
                                                        modifier = Modifier.weight(1f)
                                                )
                                        }
                                }
                        }

                        // Attachment chips row - only show if there are attachments
                        if (attachments.isNotEmpty()) {
                                LazyRow(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 4.dp
                                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                        items(attachments) { attachment ->
                                                AttachmentChip(
                                                        attachmentInfo = attachment,
                                                        onRemove = {
                                                                onRemoveAttachment(attachment.uri)
                                                        },
                                                        onInsert = {
                                                                onInsertAttachment(attachment)
                                                        }
                                                )
                                        }
                                }
                        }

                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                // Input field (increased height)
                                OutlinedTextField(
                                        value = userMessage,
                                        onValueChange = onUserMessageChange,
                                        placeholder = {
                                                Text("请输入您的问题...", style = modernTextStyle)
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        textStyle = modernTextStyle,
                                        maxLines = 1, // Force single line
                                        singleLine = true,
                                        keyboardOptions =
                                                KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions =
                                                KeyboardActions(
                                                        onSend = {
                                                                if (!isProcessing) onSendMessage()
                                                        }
                                                ),
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor =
                                                                MaterialTheme.colorScheme.primary,
                                                        unfocusedBorderColor =
                                                                MaterialTheme.colorScheme.outline
                                                ),
                                        shape = RoundedCornerShape(20.dp),
                                        enabled = !isProcessing
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                // Send button using Box instead of IconButton to avoid padding
                                // issues
                                Box(
                                        modifier =
                                                Modifier.size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                when {
                                                                        isProcessing ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error
                                                                        userMessage.isNotBlank() ||
                                                                                attachments
                                                                                        .isNotEmpty() ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                        else ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.5f
                                                                                        )
                                                                }
                                                        )
                                                        .clickable(
                                                                enabled =
                                                                        if (isProcessing) true
                                                                        else
                                                                                userMessage
                                                                                        .isNotBlank() ||
                                                                                        attachments
                                                                                                .isNotEmpty(),
                                                                onClick = {
                                                                        if (isProcessing) {
                                                                                onCancelMessage()
                                                                        } else {
                                                                                onSendMessage()
                                                                        }
                                                                }
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector =
                                                        if (isProcessing) Icons.Default.Close
                                                        else Icons.Default.Send,
                                                contentDescription =
                                                        if (isProcessing) "取消" else "发送",
                                                tint =
                                                        if (isProcessing)
                                                                MaterialTheme.colorScheme.onError
                                                        else MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                        )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Attachment button using Box instead of IconButton
                                Box(
                                        modifier =
                                                Modifier.size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                if (showAttachmentPanel)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                        )
                                                        .clickable(
                                                                enabled = !isProcessing,
                                                                onClick = {
                                                                        setShowAttachmentPanel(
                                                                                !showAttachmentPanel
                                                                        )
                                                                }
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "添加附件",
                                                tint =
                                                        if (showAttachmentPanel)
                                                                MaterialTheme.colorScheme.onPrimary
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                        )
                                }
                        }

                        // 附件选择面板 - 移动到输入框下方
                        AttachmentSelectorPanel(
                                visible = showAttachmentPanel,
                                onAttachImage = { uri ->
                                        // 传递uri给外部处理函数，这里应该用处理添加附件的函数
                                        onAttachmentRequest(uri)
                                },
                                onAttachFile = { uri ->
                                        // 传递uri给外部处理函数，这里应该用处理添加附件的函数
                                        onAttachmentRequest(uri)
                                },
                                onDismiss = { setShowAttachmentPanel(false) }
                        )
                }
        }
}

@Composable
fun AttachmentChip(attachmentInfo: AttachmentInfo, onRemove: () -> Unit, onInsert: () -> Unit) {
        val isImage = attachmentInfo.mimeType.startsWith("image/")
        val icon: ImageVector = if (isImage) Icons.Default.Image else Icons.Default.Description

        Surface(
                modifier =
                        Modifier.height(26.dp)
                                .border(
                                        width = 1.dp,
                                        color =
                                                MaterialTheme.colorScheme.outline.copy(
                                                        alpha = 0.5f
                                                ),
                                        shape = RoundedCornerShape(13.dp)
                                ),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                                text = attachmentInfo.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 80.dp)
                        )

                        Spacer(modifier = Modifier.width(2.dp))

                        IconButton(onClick = onInsert, modifier = Modifier.size(14.dp)) {
                                Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "插入附件",
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                )
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        IconButton(onClick = onRemove, modifier = Modifier.size(14.dp)) {
                                Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "删除附件",
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                }
        }
}
