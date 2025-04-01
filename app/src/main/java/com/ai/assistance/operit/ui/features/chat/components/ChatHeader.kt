package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun ChatHeader(
    showChatHistorySelector: Boolean,
    onToggleChatHistorySelector: () -> Unit,
    currentChatTitle: String?,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onToggleChatHistorySelector,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.height(28.dp)
    ) {
        Text(
            text = if (showChatHistorySelector) "隐藏历史" else "显示历史",
            style = MaterialTheme.typography.labelMedium
        )
    }
} 