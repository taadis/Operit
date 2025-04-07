package com.ai.assistance.operit.ui.features.chat.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.PlanItemStatus

/**
 * Component to display an AI's plan in a collapsible card.
 */
@Composable
fun PlanDisplay(
    planItems: List<PlanItem>,
    modifier: Modifier = Modifier
) {
    if (planItems.isEmpty()) return
    
    var expanded by remember { mutableStateOf(true) }
    val completedCount = planItems.count { it.status == PlanItemStatus.COMPLETED }
    val totalCount = planItems.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        // Header with progress and expand/collapse button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Plan (${completedCount}/${totalCount})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        
        // Progress indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(horizontal = 12.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(2.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
        
        // Plan items
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                planItems.forEach { planItem ->
                    PlanItemRow(planItem)
                    if (planItem != planItems.last()) {
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Row component to display a single plan item with its status.
 */
@Composable
private fun PlanItemRow(planItem: PlanItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        StatusDot(status = planItem.status)
        Spacer(modifier = Modifier.width(8.dp))
        
        // Description and status
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = planItem.description,
                style = MaterialTheme.typography.bodyMedium,
                color = when (planItem.status) {
                    PlanItemStatus.COMPLETED -> Color(0xFF2E7D32) // dark green
                    PlanItemStatus.FAILED -> Color(0xFFC62828) // dark red
                    PlanItemStatus.CANCELLED -> Color(0xFF616161) // gray
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            
            if (planItem.status == PlanItemStatus.IN_PROGRESS) {
                Spacer(modifier = Modifier.height(4.dp))
                // 使用无动画的进度条，避免版本不兼容问题
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(2.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(1.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f) // 固定进度为30%
                            .fillMaxHeight()
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }
        
        // Status text
        Text(
            text = when (planItem.status) {
                PlanItemStatus.TODO -> "TODO"
                PlanItemStatus.IN_PROGRESS -> "IN PROGRESS"
                PlanItemStatus.COMPLETED -> "COMPLETED"
                PlanItemStatus.FAILED -> "FAILED"
                PlanItemStatus.CANCELLED -> "CANCELLED"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when (planItem.status) {
                PlanItemStatus.COMPLETED -> Color(0xFF2E7D32) // dark green
                PlanItemStatus.FAILED -> Color(0xFFC62828) // dark red
                PlanItemStatus.CANCELLED -> Color(0xFF616161) // gray
                PlanItemStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Colored dot indicating the status of a plan item.
 */
@Composable
private fun StatusDot(status: PlanItemStatus) {
    val color = when (status) {
        PlanItemStatus.TODO -> Color(0xFF9E9E9E) // gray
        PlanItemStatus.IN_PROGRESS -> Color(0xFF1976D2) // blue
        PlanItemStatus.COMPLETED -> Color(0xFF4CAF50) // green
        PlanItemStatus.FAILED -> Color(0xFFF44336) // red
        PlanItemStatus.CANCELLED -> Color(0xFF616161) // dark gray
    }
    
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
    )
} 