package com.ai.assistance.operit.ui.features.chat.components

// import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
// import androidx.compose.animation.fadeIn
// import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.model.ToolExecutionProgress
import com.ai.assistance.operit.model.ToolExecutionState
import com.ai.assistance.operit.ui.common.animations.SimpleAnimatedVisibility

/**
 * A progress bar that displays the current state of tool execution
 */
@Composable
fun ToolProgressBar(
    toolProgress: ToolExecutionProgress,
    modifier: Modifier = Modifier
) {
    // Only show if there is active tool processing
    val isVisible = toolProgress.state != ToolExecutionState.IDLE
    
    // Animate the progress value for smooth transitions
    val animatedProgress by animateFloatAsState(
        targetValue = toolProgress.progress,
        label = "Progress Animation"
    )
    
    SimpleAnimatedVisibility(
        visible = isVisible,
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Tool name and execution state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (toolProgress.state) {
                            ToolExecutionState.EXTRACTING -> "Detecting tools..."
                            ToolExecutionState.EXECUTING -> "Executing: ${toolProgress.tool?.name ?: "tool"}"
                            ToolExecutionState.COMPLETED -> "Tools completed"
                            ToolExecutionState.FAILED -> "Tool execution failed"
                            else -> "Processing tools"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Show a completion indicator if completed
                    if (toolProgress.state == ToolExecutionState.COMPLETED) {
                        Text(
                            text = "100%",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress message
                if (toolProgress.message.isNotBlank()) {
                    Text(
                        text = toolProgress.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                // Progress bar - using custom implementation to avoid animation issues
                SimpleLinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = when (toolProgress.state) {
                        ToolExecutionState.FAILED -> MaterialTheme.colorScheme.error
                        ToolExecutionState.COMPLETED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
                
                // Show tool result if available
                toolProgress.result?.let { result ->
                    if (result.success) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tool completed successfully",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Error: ${result.error ?: "Unknown error"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
} 