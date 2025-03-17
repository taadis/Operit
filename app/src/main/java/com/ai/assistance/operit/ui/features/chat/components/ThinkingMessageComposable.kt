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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.model.ChatMessage
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.ui.common.displays.MessageContentParser.Companion.ContentSegment

/**
 * A composable function for rendering thinking/processing messages in a Cursor IDE style.
 */
@Composable
fun ThinkingMessageComposable(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    supportToolMarkup: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) } // Default collapsed
    
    // Parse message content to identify tool markup if supported
    val contentSegments = if (supportToolMarkup) {
        MessageContentParser.parseContent(message.content, true)
    } else {
        listOf(ContentSegment.Text(message.content))
    }
    
    // Extract plain text thinking content
    val plainTextSegments = contentSegments.filterIsInstance<ContentSegment.Text>()
    val thinkingContent = if (plainTextSegments.isNotEmpty()) {
        plainTextSegments.joinToString("\n") { it.content }
    } else {
        message.content
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "思考过程",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor
                )
                
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = textColor
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Display thinking text content
                    if (thinkingContent.isNotBlank()) {
                        Text(
                            text = thinkingContent,
                            color = textColor,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    // Display tool segments if any
                    if (supportToolMarkup) {
                        contentSegments.forEach { segment ->
                            when (segment) {
                                is ContentSegment.ToolExecution -> {
                                    // Tool execution box with hiding enabled
                                    ToolExecutionBox(
                                        toolName = segment.name,
                                        isProcessing = true,
                                        result = null,
                                        isError = false,
                                        enableCopy = true,
                                        hideToolRequest = true // Hide execution requests
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                is ContentSegment.ToolResult -> {
                                    // Tool result box with collapsible display
                                    ToolExecutionBox(
                                        toolName = segment.name,
                                        isProcessing = false,
                                        result = segment.content,
                                        isError = segment.isError,
                                        enableCopy = true,
                                        hideToolRequest = false // Show results
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                is ContentSegment.Status -> {
                                    when (segment.type) {
                                        "executing" -> {
                                            if (segment.toolName.isNotBlank()) {
                                                ToolExecutionBox(
                                                    toolName = segment.toolName,
                                                    isProcessing = true,
                                                    result = null,
                                                    isError = false,
                                                    enableCopy = true,
                                                    hideToolRequest = true // Hide execution requests
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                        "result" -> {
                                            if (segment.toolName.isNotBlank()) {
                                                ToolExecutionBox(
                                                    toolName = segment.toolName,
                                                    isProcessing = false,
                                                    result = segment.content,
                                                    isError = !segment.success,
                                                    enableCopy = true,
                                                    hideToolRequest = false // Show results
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                        // Other status types handled as plain text
                                    }
                                }
                                else -> {} // Text content already handled
                            }
                        }
                    }
                }
            }
        }
    }
} 