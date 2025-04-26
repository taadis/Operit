package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatHeader(
        showChatHistorySelector: Boolean,
        onToggleChatHistorySelector: () -> Unit,
        currentChatTitle: String?,
        modifier: Modifier = Modifier,
        onLaunchFloatingWindow: () -> Unit = {}
) {
    Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
    ) {
        Button(
                onClick = onToggleChatHistorySelector,
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(28.dp)
        ) {
            Text(
                    text = if (showChatHistorySelector) "隐藏历史" else "显示历史",
                    style = MaterialTheme.typography.labelMedium
            )
        }

        IconButton(onClick = onLaunchFloatingWindow, modifier = Modifier.size(32.dp)) {
            Icon(
                    imageVector = Icons.Default.PictureInPicture,
                    contentDescription = "开启悬浮窗",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
            )
        }
    }
}
