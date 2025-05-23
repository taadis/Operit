package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.common.displays.MessageContentParser.Companion.ContentSegment

/**
 * Tool state management component that manages the display and state of tools used in AI responses
 */
class ToolStateManager(
        private val contentSegments: List<ContentSegment>,
        private val onShowResult: (title: String, content: String, params: String) -> Unit
) {
    // Track displayed tools
    private val displayedTools = mutableSetOf<String>()

    // Track each tool's final state
    private val toolFinalStates = mutableMapOf<String, Triple<Boolean, String?, Boolean>>()

    // Track tool params
    private val toolParams = mutableMapOf<String, String>()

    // Track completed tools
    private val completedTools = mutableSetOf<String>()

    // Track simulated tools
    private val simulatedTools = mutableSetOf<String>()

    init {
        // Initialize tool states
        preprocessToolState()
    }

    private fun preprocessToolState() {
        // Collect tool requests
        val toolRequests = mutableMapOf<String, Int>()
        contentSegments.forEachIndexed { index, segment ->
            if (segment is ContentSegment.ToolRequest) {
                toolRequests[segment.name] = index
                toolParams[segment.name] = segment.params
            }
        }

        // Check for completion status
        val hasCompletionStatus =
                contentSegments.any { segment ->
                    segment is ContentSegment.Status &&
                            (segment.type == "completion" ||
                                    segment.type == "complete" ||
                                    segment.type == "wait_for_user_need")
                }

        // Identify simulated tools
        if (hasCompletionStatus) {
            for ((toolName, position) in toolRequests) {
                // Check if this tool has execution or result after the request
                val hasExecutionOrResult =
                        contentSegments.drop(position + 1).any { segment ->
                            (segment is ContentSegment.ToolExecution && segment.name == toolName) ||
                                    (segment is ContentSegment.ToolResult &&
                                            segment.name == toolName) ||
                                    (segment is ContentSegment.Status &&
                                            segment.toolName == toolName &&
                                            (segment.type == "executing" ||
                                                    segment.type == "result"))
                        }

                // Mark as simulated if no execution or result
                if (!hasExecutionOrResult) {
                    simulatedTools.add(toolName)
                }
            }
        }

        // Initialize tool states based on all segments
        contentSegments.forEach { segment ->
            when (segment) {
                is ContentSegment.ToolRequest -> {
                    val toolName = segment.name
                    if (!completedTools.contains(toolName)) {
                        toolFinalStates[toolName] = Triple(true, null, false)
                    }
                }
                is ContentSegment.ToolResult -> {
                    val toolName = segment.name
                    toolFinalStates[toolName] = Triple(false, segment.content, segment.isError)
                    completedTools.add(toolName)
                }
                is ContentSegment.Status -> {
                    if (segment.toolName.isNotBlank()) {
                        val toolName = segment.toolName
                        when (segment.type) {
                            "executing" -> {
                                if (!completedTools.contains(toolName)) {
                                    toolFinalStates[toolName] = Triple(true, null, false)
                                }
                            }
                            "result" -> {
                                toolFinalStates[toolName] =
                                        Triple(false, segment.content, !segment.success)
                                completedTools.add(toolName)
                            }
                        }
                    }
                }
                else -> {
                    // Other segments don't affect tool state
                }
            }
        }
    }

    /** Renders a tool segment (request, execution, or result) */
    @Composable
    fun RenderToolSegment(segment: ContentSegment) {
        val clipboardManager = LocalClipboardManager.current
        val haptic = LocalHapticFeedback.current

        when (segment) {
            is ContentSegment.ToolRequest -> {
                val toolName = segment.name
                val isSimulated = simulatedTools.contains(toolName)

                // Skip if already displayed and completed without more results
                if (completedTools.contains(toolName) && displayedTools.contains(toolName)) {
                    var hasMoreResults = false
                    var foundCurrentRequest = false

                    for (s in contentSegments) {
                        if (s === segment) {
                            foundCurrentRequest = true
                            continue
                        }

                        if (foundCurrentRequest &&
                                        ((s is ContentSegment.ToolResult && s.name == toolName) ||
                                                (s is ContentSegment.Status &&
                                                        s.toolName == toolName &&
                                                        s.type == "result"))
                        ) {
                            hasMoreResults = true
                            break
                        }
                    }

                    if (!hasMoreResults) {
                        return
                    } else {
                        // Reset for a new round of display
                        displayedTools.remove(toolName)
                        completedTools.remove(toolName)
                    }
                } else if (displayedTools.contains(toolName) && !completedTools.contains(toolName)
                ) {
                    // Skip if already displayed but not completed
                    return
                }

                // Add to displayed tools
                displayedTools.add(toolName)

                // Get tool's final state
                val finalState = toolFinalStates[toolName]

                // Show tool status
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ToolStatusDisplay(
                            toolName = toolName,
                            isProcessing = finalState?.first ?: false,
                            isError = finalState?.third ?: false,
                            result = finalState?.second,
                            params = segment.params,
                            onShowResult = {
                                onShowResult(
                                        toolName,
                                        finalState?.second?.toString() ?: "",
                                        segment.params
                                )
                            },
                            onCopyResult = {
                                val result = finalState?.second
                                if (result != null) {
                                    clipboardManager.setText(AnnotatedString(result.toString()))
                                }
                            },
                            isSimulated = isSimulated
                    )
                }
            }
            is ContentSegment.ToolExecution -> {
                val toolName = segment.name
                val isSimulated = simulatedTools.contains(toolName)

                // Only show if not already displayed
                if (!displayedTools.contains(toolName)) {
                    displayedTools.add(toolName)
                    val finalState = toolFinalStates[toolName]

                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ToolStatusDisplay(
                                toolName = toolName,
                                isProcessing = finalState?.first ?: true,
                                isError = finalState?.third ?: false,
                                result = finalState?.second,
                                params = toolParams[toolName],
                                onShowResult = {
                                    val result = finalState?.second
                                    if (result != null) {
                                        onShowResult(
                                                toolName,
                                                result.toString(),
                                                toolParams[toolName] ?: ""
                                        )
                                    }
                                },
                                onCopyResult = {
                                    val result = finalState?.second
                                    if (result != null) {
                                        clipboardManager.setText(AnnotatedString(result.toString()))
                                    }
                                },
                                isSimulated = isSimulated
                        )
                    }
                }
            }
            is ContentSegment.ToolResult -> {
                val toolName = segment.name
                val isSimulated = simulatedTools.contains(toolName)

                // Only show if not already displayed
                if (displayedTools.contains(toolName)) {
                    completedTools.add(toolName)
                    return
                }

                displayedTools.add(toolName)
                completedTools.add(toolName)

                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ToolStatusDisplay(
                            toolName = toolName,
                            isProcessing = false,
                            isError = segment.isError,
                            result = segment.content,
                            params = toolParams[toolName],
                            onShowResult = {
                                onShowResult(toolName, segment.content, toolParams[toolName] ?: "")
                            },
                            onCopyResult = {
                                clipboardManager.setText(AnnotatedString(segment.content))
                            },
                            isSimulated = isSimulated
                    )
                }
            }
            is ContentSegment.Status -> {
                if (segment.toolName.isNotBlank()) {
                    val toolName = segment.toolName
                    val isSimulated = simulatedTools.contains(toolName)

                    if (segment.type == "executing") {
                        if (!displayedTools.contains(toolName)) {
                            displayedTools.add(toolName)
                            val finalState = toolFinalStates[toolName]

                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                ToolStatusDisplay(
                                        toolName = toolName,
                                        isProcessing = finalState?.first ?: true,
                                        isError = finalState?.third ?: false,
                                        result = finalState?.second,
                                        params = toolParams[toolName],
                                        onShowResult = {
                                            val result = finalState?.second
                                            if (result != null && result.toString().isNotBlank()) {
                                                onShowResult(
                                                        toolName,
                                                        result.toString(),
                                                        toolParams[toolName] ?: ""
                                                )
                                            }
                                        },
                                        onCopyResult = {
                                            val result = finalState?.second
                                            if (result != null) {
                                                clipboardManager.setText(
                                                        AnnotatedString(result.toString())
                                                )
                                            }
                                        },
                                        isSimulated = isSimulated
                                )
                            }
                        }
                    } else if (segment.type == "result") {
                        if (!displayedTools.contains(toolName)) {
                            displayedTools.add(toolName)
                        }
                        completedTools.add(toolName)
                    }
                }
            }
            else -> {
                /* Other segments are handled elsewhere */
            }
        }
    }
}
