package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.displays.EnhancedMarkdownText
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.ui.common.displays.MessageContentParser.Companion.ContentSegment

/**
 * A composable function for rendering AI response messages in a Cursor IDE style. This version has
 * been simplified and modularized with components extracted to separate files.
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

    // State for result dialog
    var showResultDialog by remember { mutableStateOf(false) }
    var resultDialogTitle by remember { mutableStateOf("") }
    var resultDialogContent by remember { mutableStateOf("") }
    var resultDialogParams by remember { mutableStateOf("") }

    // Show result dialog if needed
    if (showResultDialog) {
        ResultDialog(
                title = resultDialogTitle,
                content = resultDialogContent,
                params = resultDialogParams.takeIf { it.isNotBlank() },
                onDismiss = { showResultDialog = false }
        )
    }

    // Main layout
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        // Response header
        Text(
                text = "Response",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        // Parse message content to identify segments
        val contentSegments = MessageContentParser.parseContent(message.content, supportToolMarkup)

        // Create a composable function for rendering a content segment
        @Composable
        fun RenderContentSegment(segment: MessageContentParser.Companion.ContentSegment) {
            when (segment) {
                is MessageContentParser.Companion.ContentSegment.Text -> {
                        if (segment.content.isNotBlank()) {
                            Box(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .padding(horizontal = 16.dp)
                                                    .combinedClickable(
                                                            interactionSource =
                                                                    remember {
                                                                        MutableInteractionSource()
                                                                    },
                                                            indication = null,
                                                            onClick = { /* Do nothing on normal click */
                                                            },
                                                            onLongClick = {
                                                                clipboardManager.setText(
                                                                    AnnotatedString(segment.content)
                                                                )
                                                                haptic.performHapticFeedback(
                                                                        HapticFeedbackType.LongPress
                                                                )
                                                            }
                                                    )
                            ) {
                                EnhancedMarkdownText(
                                        text = segment.content,
                                        textColor = textColor,
                                        onCodeCopied = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                            )
                        }
                    }
                }
                is MessageContentParser.Companion.ContentSegment.PlanItem -> {
                    com.ai.assistance.operit.ui.features.chat.components.UnifiedPlanDisplay(
                                id = segment.id,
                                status = segment.status,
                                content = segment.description,
                                isUpdate = false
                        )
                    }
                is MessageContentParser.Companion.ContentSegment.PlanUpdate -> {
                    com.ai.assistance.operit.ui.features.chat.components.UnifiedPlanDisplay(
                                id = segment.id,
                                status = segment.status,
                                content = segment.message ?: "",
                            isUpdate = true
                    )
                }
                is MessageContentParser.Companion.ContentSegment.Status -> {
                    if (segment.toolName.isBlank()) {
                        StatusMessageDisplay(segment)
                    }
                    // Tool-related statuses are handled in ToolStateManager
                }
                else -> {
                    // Tool segments are handled in ToolStateManager
                }
                }

                // Add spacing between segments
                if (segment != contentSegments.last()) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

        // Create tool state manager instance
        val toolStateManager = remember {
            ToolStateManager(
                    contentSegments = contentSegments,
                    onShowResult = { title, content, params ->
                        resultDialogTitle = title
                        resultDialogContent = content
                        resultDialogParams = params
                        showResultDialog = true
                    }
            )
        }

        // Use the PlanManager for plan content handling
        PlanManager(
                contentSegments = contentSegments,
                renderContent = { segment ->
                    // For tool-related segments, use the tool manager
                    if (segment is MessageContentParser.Companion.ContentSegment.ToolRequest ||
                                    segment is
                                            MessageContentParser.Companion.ContentSegment.ToolExecution ||
                                    segment is
                                            MessageContentParser.Companion.ContentSegment.ToolResult ||
                                    (segment is
                                            MessageContentParser.Companion.ContentSegment.Status &&
                                            segment.toolName.isNotBlank())
                    ) {
                        // Render tool segment using the tool state manager
                        toolStateManager.RenderToolSegment(segment)
                                } else {
                        // For non-tool segments, use the regular renderer
                        RenderContentSegment(segment)
                    }
                }
        )
    }
}
