package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.model.ChatMessage
import com.ai.assistance.operit.ui.components.MessageContentParser.Companion.ContentSegment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.text.selection.SelectionContainer

/**
 * A composable function for rendering AI response messages in a Cursor IDE style.
 * Supports text selection and copy on long press for different segments.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiMessageComposable(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    supportToolMarkup: Boolean = true
) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Response",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Parse the message content to identify tool markup
            val contentSegments = MessageContentParser.parseContent(message.content, supportToolMarkup)
            
            // Render each segment
            contentSegments.forEach { segment ->
                when (segment) {
                    is ContentSegment.Text -> {
                        // Render regular text content
                        if (segment.content.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { /* Do nothing on normal click */ },
                                        onLongClick = {
                                            clipboardManager.setText(AnnotatedString(segment.content))
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    )
                            ) {
                                TextWithCodeBlocksComposable(
                                    text = segment.content,
                                    textColor = textColor
                                )
                            }
                        }
                    }
                    
                    is ContentSegment.ToolRequest -> {
                        // Render tool request UI
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { /* Do nothing on normal click */ },
                                    onLongClick = {
                                        val copyText = "Tool Request: ${segment.name}\nParameters: ${segment.params}"
                                        clipboardManager.setText(AnnotatedString(copyText))
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (segment.description.isNotBlank())
                                        "Requesting tool: ${segment.name} (${segment.description})"
                                    else
                                        "Requesting tool: ${segment.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                
                                if (segment.params.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Parameters:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                    
                                    // Display parameters
                                    SelectionContainer {
                                        Text(
                                            text = segment.params,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f))
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    is ContentSegment.ToolExecution -> {
                        // Use the standard ToolExecutionBox for executing tools
                        ToolExecutionBox(
                            toolName = segment.name,
                            isProcessing = true,
                            result = null,
                            isError = false,
                            enableCopy = true,  // Enable copy functionality 
                            hideToolRequest = true  // Hide tool execution request
                        )
                    }
                    
                    is ContentSegment.ToolResult -> {
                        // Use the standard ToolExecutionBox for tool results
                        ToolExecutionBox(
                            toolName = segment.name,
                            isProcessing = false,
                            result = segment.content,
                            isError = segment.isError,
                            enableCopy = true,  // Enable copy functionality
                            hideToolRequest = false  // Show tool results
                        )
                    }
                    
                    is ContentSegment.Status -> {
                        when (segment.type) {
                            "executing" -> {
                                // Tool execution status
                                if (segment.toolName.isNotBlank()) {
                                    ToolExecutionBox(
                                        toolName = segment.toolName,
                                        isProcessing = true,
                                        result = null,
                                        isError = false,
                                        enableCopy = true,  // Enable copy functionality
                                        hideToolRequest = true  // Hide tool request
                                    )
                                }
                            }
                            "result" -> {
                                // Tool result status
                                if (segment.toolName.isNotBlank()) {
                                    ToolExecutionBox(
                                        toolName = segment.toolName,
                                        isProcessing = false,
                                        result = segment.content,
                                        isError = !segment.success,
                                        enableCopy = true,  // Enable copy functionality
                                        hideToolRequest = false  // Show tool result
                                    )
                                }
                            }
                            "warning" -> {
                                // Warning information
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { /* Do nothing on normal click */ },
                                            onLongClick = {
                                                clipboardManager.setText(AnnotatedString(segment.content))
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = segment.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                            "complete" -> {
                                // Task completion indicator
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = "✓ Task completed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                            // Thinking status is handled by ThinkingMessageComposable
                        }
                    }
                }
                
                // Add spacing between segments
                if (segment != contentSegments.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
} 