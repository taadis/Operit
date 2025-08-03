
package com.ai.assistance.operit.ui.features.chat.components.style.bubble

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.SummaryMessageComposable
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.SystemMessageComposable
import com.ai.assistance.operit.util.stream.Stream

/**
 * A composable function that renders chat messages in a bubble chat style.
 * Delegates to specialized composables based on message type.
 */
@Composable
fun BubbleStyleChatMessage(
    message: ChatMessage,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color
) {
    when (message.sender) {
        "user" -> {
            BubbleUserMessageComposable(
                message = message,
                backgroundColor = userMessageColor,
                textColor = userTextColor
            )
        }
        "ai" -> {
            BubbleAiMessageComposable(
                message = message,
                backgroundColor = aiMessageColor,
                textColor = aiTextColor
            )
        }
        "system" -> {
            SystemMessageComposable(
                message = message,
                backgroundColor = systemMessageColor,
                textColor = systemTextColor
            )
        }
        "summary" -> {
            SummaryMessageComposable(
                message = message,
                backgroundColor = systemMessageColor.copy(alpha = 0.7f),
                textColor = systemTextColor
            )
        }
    }
}
