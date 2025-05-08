package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable

/**
 * A composable function for rendering user messages in a Cursor IDE style. Supports text selection
 * and copy on long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserMessageComposable(message: ChatMessage, backgroundColor: Color, textColor: Color) {
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    // Parse message content to separate text and attachments
    val parseResult = parseMessageContent(message.content)
    val textContent = parseResult.processedText
    val trailingAttachments = parseResult.trailingAttachments

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Display trailing attachments above the message bubble 
        if (trailingAttachments.isNotEmpty()) {
            // Display attachment row above the bubble
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                trailingAttachments.forEach { attachment ->
                    AttachmentTag(
                        filename = attachment.filename,
                        type = attachment.type,
                        textColor = textColor,
                        backgroundColor = backgroundColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }

        // Message bubble
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Do nothing on normal click */ },
                    onLongClick = {
                        clipboardManager.setText(AnnotatedString(message.content))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                ),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = "Prompt",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Display main text content with inline attachments
                MarkdownTextComposable(text = textContent, textColor = textColor)
            }
        }
    }
}

/**
 * Result of parsing message content, containing processed text and trailing attachments
 */
data class MessageParseResult(
    val processedText: String,
    val trailingAttachments: List<AttachmentData>
)

/**
 * Parses the message content to extract text and attachments
 * Keeps inline attachments as @filename in the text
 * Extracts trailing attachments that appear at the end of the message
 */
private fun parseMessageContent(content: String): MessageParseResult {
    val attachments = mutableListOf<AttachmentData>()
    val trailingAttachments = mutableListOf<AttachmentData>()
    val messageText = StringBuilder()

    // Simple regex pattern to find attachments
    val attachmentPattern =
        "<attachment\\s+id=\"([^\"]+)\"\\s+filename=\"([^\"]+)\"\\s+type=\"([^\"]+)\"\\s+(?:size=\"([^\"]+)\"\\s+)?/>".toRegex()

    // Get all matches
    val matches = attachmentPattern.findAll(content).toList()
    if (matches.isEmpty()) {
        return MessageParseResult(content, emptyList())
    }

    // Find the last non-whitespace character after the last attachment
    val lastMatch = matches.last()
    val contentAfterLastMatch = content.substring(lastMatch.range.last + 1).trim()

    // Process all attachments
    var lastIndex = 0
    matches.forEachIndexed { index, matchResult ->
        // Add text before this attachment
        val startIndex = matchResult.range.first
        if (startIndex > lastIndex) {
            messageText.append(content.substring(lastIndex, startIndex))
        }

        // Extract attachment data
        val id = matchResult.groupValues[1]
        val filename = matchResult.groupValues[2]
        val type = matchResult.groupValues[3]
        val size = matchResult.groupValues[4].toLongOrNull() ?: 0L
        val attachment = AttachmentData(id, filename, type, size)
        
        // Determine if this is a trailing attachment
        val isLastAttachment = index == matches.size - 1
        val isTrailingAttachment = isLastAttachment && contentAfterLastMatch.isEmpty()
        
        if (isTrailingAttachment) {
            // This is a trailing attachment, extract it
            trailingAttachments.add(attachment)
        } else {
            // This is an inline attachment, keep it in the text as @filename
            messageText.append("@${filename}")
            // Also add to general attachments list for reference
            attachments.add(attachment)
        }

        lastIndex = matchResult.range.last + 1
    }

    // Add any remaining text
    if (lastIndex < content.length) {
        messageText.append(content.substring(lastIndex))
    }

    return MessageParseResult(messageText.toString(), trailingAttachments)
}

/** Data class for attachment information */
data class AttachmentData(
    val id: String,
    val filename: String,
    val type: String,
    val size: Long = 0
)

/** Compact attachment tag component for displaying in user messages */
@Composable
private fun AttachmentTag(
    filename: String,
    type: String,
    textColor: Color,
    backgroundColor: Color
) {
    val isImage = type.startsWith("image/")
    val icon: ImageVector = if (isImage) Icons.Default.Image else Icons.Default.Description

    Surface(
        modifier = Modifier.height(24.dp).padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = textColor.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = filename,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
                modifier = Modifier.widthIn(max = 120.dp)
            )
        }
    }
}
