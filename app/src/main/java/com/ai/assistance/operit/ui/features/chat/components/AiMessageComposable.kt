package com.ai.assistance.operit.ui.features.chat.components

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
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.displays.MessageContentParser.Companion.ContentSegment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.text.selection.SelectionContainer
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.ui.common.displays.TextWithCodeBlocksComposable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

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
            val contentSegments =
                MessageContentParser.parseContent(message.content, supportToolMarkup)
            
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
                                    // Regular tool execution
                                    ToolExecutionBox(
                                        toolName = segment.toolName,
                                        isProcessing = true,
                                        result = null,
                                        isError = false,
                                        enableCopy = true,
                                        hideToolRequest = true
                                    )
                                }
                            }
                            "result" -> {
                                // Tool result status
                                if (segment.toolName.isNotBlank()) {
                                    // Regular tool result
                                    ToolExecutionBox(
                                        toolName = segment.toolName,
                                        isProcessing = false,
                                        result = segment.content,
                                        isError = !segment.success,
                                        enableCopy = true,
                                        hideToolRequest = false
                                    )
                                }
                            }
                            "completion" -> {
                                // Task completion indicator (old name, keep for backward compatibility)
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
                            "complete" -> {
                                // Task completion indicator (new name)
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
                            "wait_for_user_need" -> {
                                // Wait for user need indicator
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                                ) {
                                    SelectionContainer {
                                        Text(
                                            text = "✓ Ready for further assistance",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
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
                        }
                    }
                    
                    // 新增：处理计划项显示
                    is ContentSegment.PlanItem -> {
                        PlanItemDisplay(segment.id, segment.status, segment.description)
                    }
                    
                    // 新增：处理计划更新显示
                    is ContentSegment.PlanUpdate -> {
                        PlanUpdateDisplay(segment.id, segment.status, segment.message)
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

/**
 * 计划项的显示组件
 */
@Composable
private fun PlanItemDisplay(id: String, status: String, description: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标根据状态显示不同的图标
            val (icon, color) = when (status.lowercase()) {
                "todo" -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurface
                "in_progress" -> Icons.Filled.PlayArrow to MaterialTheme.colorScheme.primary
                "completed" -> Icons.Filled.CheckCircle to Color(0xFF4CAF50) // Green
                "failed" -> Icons.Filled.Error to Color(0xFFF44336) // Red
                "cancelled" -> Icons.Filled.Cancel to Color(0xFF9E9E9E) // Gray
                else -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurface
            }
            
            Icon(
                imageVector = icon,
                contentDescription = "Plan item status: $status",
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // ID和状态信息
                Text(
                    text = "ID: $id • Status: ${status.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 计划更新的显示组件
 */
@Composable
private fun PlanUpdateDisplay(id: String, status: String, message: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (status.lowercase()) {
                "completed" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                "failed" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                "in_progress" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            }
        ),
        border = BorderStroke(1.dp, when (status.lowercase()) {
            "completed" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            "failed" -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            "in_progress" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标根据状态显示不同的图标
            val (icon, color) = when (status.lowercase()) {
                "todo" -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurface
                "in_progress" -> Icons.Filled.PlayArrow to MaterialTheme.colorScheme.primary
                "completed" -> Icons.Filled.CheckCircle to Color(0xFF4CAF50) // Green
                "failed" -> Icons.Filled.Error to Color(0xFFF44336) // Red
                "cancelled" -> Icons.Filled.Cancel to Color(0xFF9E9E9E) // Gray
                else -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurface
            }
            
            Icon(
                imageVector = icon,
                contentDescription = "Plan status update: $status",
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 状态更新信息
                Text(
                    text = "Plan #$id: Status updated to ${status.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = when (status.lowercase()) {
                        "completed" -> Color(0xFF2E7D32) // dark green
                        "failed" -> Color(0xFFC62828) // dark red
                        "in_progress" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                
                // 如果有消息，则显示
                if (!message.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
} 