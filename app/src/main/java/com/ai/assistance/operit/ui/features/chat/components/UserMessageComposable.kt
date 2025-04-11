package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
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

    Card(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* Do nothing on normal click */},
                                    onLongClick = {
                                        clipboardManager.setText(AnnotatedString(message.content))
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
            MarkdownTextComposable(text = message.content, textColor = textColor)
        }
    }
}
