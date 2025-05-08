package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ChatHeader(
        showChatHistorySelector: Boolean,
        onToggleChatHistorySelector: () -> Unit,
        currentChatTitle: String?,
        modifier: Modifier = Modifier,
        onLaunchFloatingWindow: () -> Unit = {},
        isFloatingMode: Boolean = false
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

                Box(
                        modifier =
                                Modifier.size(32.dp)
                                        .background(
                                                color =
                                                        if (isFloatingMode)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.15f)
                                                        else Color.Transparent,
                                                shape = CircleShape
                                        )
                ) {
                        IconButton(
                                onClick = onLaunchFloatingWindow,
                                modifier = Modifier.matchParentSize()
                        ) {
                                Icon(
                                        imageVector = Icons.Default.PictureInPicture,
                                        contentDescription =
                                                if (isFloatingMode) "关闭悬浮窗" else "开启悬浮窗",
                                        tint =
                                                if (isFloatingMode)
                                                        MaterialTheme.colorScheme.primary
                                                else
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.7f
                                                        ),
                                        modifier = Modifier.size(20.dp)
                                )
                        }
                }
        }
}
