package com.ai.assistance.operit.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A simple custom linear progress indicator that avoids using the problematic Material3 animation features.
 */
@Composable
fun SimpleLinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
) {
    if (progress in 0.0f..1.0f) {
        // 确定性进度条 - 有特定进度值
        LinearProgressIndicator(
            progress = progress,
            modifier = modifier.height(4.dp),
            color = color,
            trackColor = trackColor
        )
    } else {
        // 不确定性进度条 - 无限循环动画
        LinearProgressIndicator(
            modifier = modifier.height(4.dp),
            color = color,
            trackColor = trackColor
        )
    }
} 