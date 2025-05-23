package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.common.displays.MessageContentParser.Companion.ContentSegment

/** A component to manage and display plan-related content in AI messages */
@Composable
fun PlanManager(
        contentSegments: List<ContentSegment>,
        renderContent: @Composable (ContentSegment) -> Unit
) {
    // Track plan state
    val planStatus = remember {
        mutableStateOf(mutableMapOf<String, Boolean>())
    } // <planId, isActive>
    val currentActivePlanId = remember { mutableStateOf<String?>(null) }
    val planSegments = remember {
        mutableStateOf(mutableListOf<Pair<ContentSegment, @Composable () -> Unit>>())
    }

    // Process segments to find plan elements
    LaunchedEffect(contentSegments) {
        // Initialize plan statuses
        contentSegments.forEach { segment ->
            when (segment) {
                is ContentSegment.PlanItem -> {
                    val planId = segment.id
                    if (segment.status.lowercase() == "in_progress") {
                        planStatus.value[planId] = true
                    } else if (segment.status.lowercase() == "completed" ||
                                    segment.status.lowercase() == "done"
                    ) {
                        planStatus.value[planId] = false
                    }
                }
                is ContentSegment.PlanUpdate -> {
                    val planId = segment.id
                    if (segment.status.lowercase() == "in_progress") {
                        planStatus.value[planId] = true
                    } else if (segment.status.lowercase() == "completed" ||
                                    segment.status.lowercase() == "done"
                    ) {
                        planStatus.value[planId] = false
                    }
                }
                else -> {
                    /* Other segments don't affect plan status */
                }
            }
        }
    }

    // Process segments and handle plan grouping
    contentSegments.forEach { segment ->
        when (segment) {
            is ContentSegment.PlanItem, is ContentSegment.PlanUpdate -> {
                val planId =
                        when (segment) {
                            is ContentSegment.PlanItem -> segment.id
                            is ContentSegment.PlanUpdate -> segment.id
                            else -> ""
                        }

                val status =
                        when (segment) {
                            is ContentSegment.PlanItem -> segment.status
                            is ContentSegment.PlanUpdate -> segment.status
                            else -> ""
                        }

                if (status.lowercase() == "in_progress") {
                    // If there's already an active plan, finish it first
                    if (currentActivePlanId.value != null && currentActivePlanId.value != planId) {
                        if (planSegments.value.isNotEmpty()) {
                            RenderPlanWithTimeline(
                                    planId = currentActivePlanId.value!!,
                                    composables = planSegments.value.map { it.second }
                            )
                            planSegments.value.clear()
                        }
                    }

                    // Render plan start indicator
                    renderContent(segment)

                    // Set current active plan
                    currentActivePlanId.value = planId
                } else if (status.lowercase() == "completed" || status.lowercase() == "done") {
                    // If this is the active plan, render collected segments and finish it
                    if (currentActivePlanId.value == planId) {
                        if (planSegments.value.isNotEmpty()) {
                            RenderPlanWithTimeline(
                                    planId = planId,
                                    composables = planSegments.value.map { it.second }
                            )
                            planSegments.value.clear()
                        }

                        // Render plan completion indicator
                        renderContent(segment)

                        // Clear active plan
                        currentActivePlanId.value = null
                    } else {
                        // Not the active plan, render normally
                        renderContent(segment)
                    }
                } else {
                    // Other plan statuses, render normally
                    renderContent(segment)
                }
            }
            else -> {
                // For non-plan segments
                if (currentActivePlanId.value != null) {
                    // If there's an active plan, collect segment for later rendering
                    planSegments.value.add(Pair(segment) { renderContent(segment) })
                } else {
                    // Otherwise render directly
                    renderContent(segment)
                }
            }
        }
    }

    // Render any remaining active plan
    if (currentActivePlanId.value != null && planSegments.value.isNotEmpty()) {
        RenderPlanWithTimeline(
                planId = currentActivePlanId.value!!,
                composables = planSegments.value.map { it.second }
        )
    }
}

/** Renders a plan with timeline visualization */
@Composable
fun RenderPlanWithTimeline(planId: String, composables: List<@Composable () -> Unit>) {
    // Don't render if no content
    if (composables.isEmpty()) return

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.Top
        ) {
            // Timeline visualization
            Box(modifier = Modifier.width(30.dp).fillMaxHeight()) {
                // Vertical line
                Box(
                        modifier =
                                Modifier.width(3.dp)
                                        .fillMaxHeight()
                                        .align(Alignment.Center)
                                        .background(
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.25f
                                                        ),
                                                shape = RoundedCornerShape(1.5.dp)
                                        )
                )

                // Top node
                Box(
                        modifier =
                                Modifier.size(6.dp)
                                        .align(Alignment.TopCenter)
                                        .background(
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.3f
                                                        ),
                                                shape = CircleShape
                                        )
                )

                // Bottom node
                Box(
                        modifier =
                                Modifier.size(6.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.3f
                                                        ),
                                                shape = CircleShape
                                        )
                                        .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = CircleShape
                                        )
                )
            }

            // Plan content
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                composables.forEach { composable -> composable() }
            }
        }
    }
}

/** Renders a unified plan display for both plan items and updates */
@Composable
fun UnifiedPlanDisplay(id: String, status: String, content: String, isUpdate: Boolean) {
    when (status.lowercase()) {
        "todo" -> {
            if (!isUpdate) {
                // TODO plans
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // Simplified visualization for TODO items
                    Box(
                            modifier =
                                    Modifier.size(8.dp)
                                            .background(
                                                    color =
                                                            MaterialTheme.colorScheme.onSurface
                                                                    .copy(alpha = 0.3f),
                                                    shape = CircleShape
                                            )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
        "in_progress" -> {
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.padding(start = 8.dp, end = 16.dp)) {
                        Card(
                                shape = RoundedCornerShape(16.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.15f
                                                        )
                                        )
                        ) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Plan in progress",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                if (content.isNotBlank()) {
                                    Text(
                                            text = content,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                            text = "计划 #$id 执行中",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        "completed", "done" -> {
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // Horizontal line instead of card
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                thickness = 1.5.dp
                        )
                    }

                    if (content.isNotBlank() && content != "Completed" && content != "Done") {
                        Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier =
                                        Modifier.padding(
                                                top = 2.dp,
                                                start = 16.dp,
                                                bottom = if (isUpdate) 4.dp else 0.dp
                                        ),
                                fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
        "failed", "cancelled" -> {
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    // Horizontal line
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lineColor =
                                when (status.lowercase()) {
                                    "failed" -> Color(0xFFC62828).copy(alpha = 0.25f) // Dark red
                                    else -> Color(0xFF757575).copy(alpha = 0.25f) // Gray
                                }

                        Divider(
                                modifier = Modifier.weight(1f),
                                color = lineColor,
                                thickness = 1.5.dp
                        )
                    }

                    // Status text
                    val statusText =
                            when (status.lowercase()) {
                                "failed" -> "计划失败"
                                else -> "计划取消"
                            }

                    val textColor =
                            when (status.lowercase()) {
                                "failed" -> Color(0xFFC62828).copy(alpha = 0.7f) // Dark red
                                else -> Color(0xFF757575).copy(alpha = 0.7f) // Gray
                            }

                    Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor,
                            modifier = Modifier.padding(top = 2.dp, start = 16.dp),
                            fontWeight = FontWeight.Medium
                    )

                    if (content.isNotBlank()) {
                        Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier =
                                        Modifier.padding(
                                                top = 2.dp,
                                                start = 16.dp,
                                                bottom = if (isUpdate) 4.dp else 0.dp
                                        ),
                                fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
        else -> {
            if (!isUpdate || content.isNotBlank()) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                thickness = 1.5.dp
                        )
                    }

                    Text(
                            text = "计划: ${status.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp, start = 16.dp),
                            fontWeight = FontWeight.Medium
                    )

                    if (content.isNotBlank()) {
                        Text(
                                text = content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier =
                                        Modifier.padding(
                                                top = 2.dp,
                                                start = 16.dp,
                                                bottom = if (isUpdate) 4.dp else 0.dp
                                        ),
                                fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/** Renders status information such as completion or warning messages */
@Composable
fun StatusMessageDisplay(segment: ContentSegment.Status) {
    // Only handle non-tool status messages here
    if (segment.toolName.isBlank()) {
        when (segment.type) {
            "completion", "complete" -> {
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(vertical = 4.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.3f
                                                )
                                ),
                        border =
                                BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
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
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(vertical = 4.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.tertiaryContainer.copy(
                                                        alpha = 0.3f
                                                )
                                ),
                        border =
                                BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                                )
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
                Card(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(vertical = 4.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.errorContainer.copy(
                                                        alpha = 0.3f
                                                )
                                ),
                        border =
                                BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                )
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
}
