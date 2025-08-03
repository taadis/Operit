package com.ai.assistance.operit.ui.features.chat.components.style.cursor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable

/** A composable function for rendering system messages in a Cursor IDE style. */
@Composable
fun SystemMessageComposable(message: ChatMessage, backgroundColor: Color, textColor: Color) {
    val haptic = LocalHapticFeedback.current

    Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            MarkdownTextComposable(
                    text = message.content,
                    textColor = textColor,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
            )
        }
    }
}
