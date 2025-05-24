package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable

/** A composable function for rendering thinking/processing messages in a Cursor IDE style. */
@Composable
fun ThinkingMessageComposable(
        message: ChatMessage,
        backgroundColor: Color,
        textColor: Color,
        initialExpanded: Boolean = false // 添加初始展开状态参数
) {
        var expanded by rememberSaveable { mutableStateOf(initialExpanded) }
        val haptic = LocalHapticFeedback.current

        // Extract plain text thinking content
        val thinkingContent = message.content

        Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clickable { expanded = !expanded }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Text(
                                        text = stringResource(id = R.string.thinking_process),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = textColor
                                )

                                Icon(
                                        imageVector =
                                                if (expanded) Icons.Default.ExpandLess
                                                else Icons.Default.ExpandMore,
                                        contentDescription = if (expanded) "收起" else "展开",
                                        tint = textColor
                                )
                        }

                        AnimatedVisibility(visible = expanded) {
                                Column(
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 16.dp,
                                                        vertical = 8.dp
                                                )
                                ) {
                                        // Display thinking text content
                                        if (thinkingContent.isNotBlank()) {
                                                MarkdownTextComposable(
                                                        text = thinkingContent,
                                                        textColor = textColor,
                                                        fontSize =
                                                                MaterialTheme.typography
                                                                        .bodySmall
                                                                        .fontSize,
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                )
                                        }
                                }
                        }
                }
        }
}
