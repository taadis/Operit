package com.ai.assistance.operit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.model.ChatMessage

/**
 * A composable function that renders chat messages in a Cursor IDE style.
 * Delegates to specialized composables based on message type.
 */
@Composable
fun CursorStyleChatMessage(
    message: ChatMessage,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color,
    supportToolMarkup: Boolean = true
) {
    when (message.sender) {
        "user" -> {
            UserMessageComposable(
                message = message,
                backgroundColor = userMessageColor,
                textColor = userTextColor
            )
        }
        "ai" -> {
            AiMessageComposable(
                message = message,
                backgroundColor = aiMessageColor,
                textColor = aiTextColor,
                supportToolMarkup = supportToolMarkup
            )
        }
        "think" -> {
            ThinkingMessageComposable(
                message = message,
                backgroundColor = thinkingBackgroundColor,
                textColor = thinkingTextColor,
                supportToolMarkup = supportToolMarkup
            )
        }
        "system" -> {
            SystemMessageComposable(
                message = message,
                backgroundColor = systemMessageColor,
                textColor = systemTextColor
            )
        }
    }
} 