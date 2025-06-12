package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage

/**
 * A composable function for rendering AI response messages in a Cursor IDE style. Supports text
 * selection and copy on long press for different segments. Always uses collapsed execution mode for
 * tool output display.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiMessageComposable(
        message: ChatMessage,
        backgroundColor: Color,
        textColor: Color
) {
    // Removed the Card background - Direct Column for AI response
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = "Response",
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )
    }
}