package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.common.animations.SimpleAnimatedVisibility

@Composable
fun ChatInputSection(
        userMessage: String,
        onUserMessageChange: (String) -> Unit,
        onSendMessage: () -> Unit,
        onCancelMessage: () -> Unit,
        isLoading: Boolean,
        isProcessingInput: Boolean,
        inputProcessingMessage: String,
        modifier: Modifier = Modifier
) {
        val modernTextStyle = TextStyle(fontSize = 14.sp)

        val isProcessing = isLoading || isProcessingInput

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

                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                OutlinedTextField(
                                        value = userMessage,
                                        onValueChange = onUserMessageChange,
                                        placeholder = { Text("请输入您的问题...") },
                                        modifier = Modifier.weight(1f),
                                        textStyle = modernTextStyle,
                                        maxLines = 3,
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
                                        shape = RoundedCornerShape(24.dp),
                                        enabled = !isProcessing
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                        onClick = {
                                                if (isProcessing) {
                                                        onCancelMessage()
                                                } else {
                                                        onSendMessage()
                                                }
                                        },
                                        enabled =
                                                if (isProcessing) true
                                                else userMessage.isNotBlank(),
                                        modifier =
                                                Modifier.size(48.dp)
                                                        .clip(RoundedCornerShape(50))
                                                        .background(
                                                                when {
                                                                        isProcessing ->
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .error
                                                                        userMessage.isNotBlank() ->
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
                                ) {
                                        if (isProcessing) {
                                                Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "取消",
                                                        tint = MaterialTheme.colorScheme.onError
                                                )
                                        } else {
                                                Icon(
                                                        Icons.Default.Send,
                                                        contentDescription = "发送",
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                        }
                                }
                        }
                }
        }
}
