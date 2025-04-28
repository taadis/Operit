package com.ai.assistance.operit.ui.features.mcp.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** A composable function that displays a refreshing indicator with animation. */
@Composable
fun RefreshingIndicator() {
        val rotation by
                rememberInfiniteTransition(label = "refreshRotation")
                        .animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec =
                                        infiniteRepeatable(
                                                animation = tween(1000, easing = LinearEasing),
                                                repeatMode = RepeatMode.Restart
                                        ),
                                label = "refreshRotation"
                        )

        Box(
                modifier =
                        Modifier.size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
        ) {
                Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refreshing",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }
                )
        }
}

/**
 * A composable function that displays a loading item, typically shown at the bottom of a list when
 * more items are being loaded.
 *
 * @param isLoading Whether the loading is in progress
 */
@Composable
fun LoadingItem(isLoading: Boolean) {
        if (isLoading) {
                Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                ) {
                        val animatedAlpha by
                                rememberInfiniteTransition(label = "loadingAlpha")
                                        .animateFloat(
                                                initialValue = 0.4f,
                                                targetValue = 0.8f,
                                                animationSpec =
                                                        infiniteRepeatable(
                                                                animation =
                                                                        tween(
                                                                                800,
                                                                                easing =
                                                                                        LinearEasing
                                                                        ),
                                                                repeatMode = RepeatMode.Reverse
                                                        ),
                                                label = "loadingAlpha"
                                        )

                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                        ) {
                                // Custom loading animation with three dots
                                Row(
                                        modifier =
                                                Modifier.background(
                                                                MaterialTheme.colorScheme
                                                                        .primaryContainer.copy(
                                                                        alpha = 0.3f
                                                                ),
                                                                RoundedCornerShape(12.dp)
                                                        )
                                                        .padding(
                                                                horizontal = 12.dp,
                                                                vertical = 8.dp
                                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // Three animated dots
                                        for (i in 0..2) {
                                                val dotDelay = i * 200
                                                val dotAlpha by
                                                        rememberInfiniteTransition(label = "dot$i")
                                                                .animateFloat(
                                                                        initialValue = 0.4f,
                                                                        targetValue = 1f,
                                                                        animationSpec =
                                                                                infiniteRepeatable(
                                                                                        animation =
                                                                                                tween(
                                                                                                        600,
                                                                                                        easing =
                                                                                                                FastOutSlowInEasing,
                                                                                                        delayMillis =
                                                                                                                dotDelay
                                                                                                ),
                                                                                        repeatMode =
                                                                                                RepeatMode
                                                                                                        .Reverse
                                                                                ),
                                                                        label = "dot$i"
                                                                )

                                                Box(
                                                        modifier =
                                                                Modifier.size(8.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        dotAlpha
                                                                                        )
                                                                        )
                                                )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                                text = "加载更多内容中",
                                                style = MaterialTheme.typography.bodySmall,
                                                color =
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                                .copy(alpha = animatedAlpha)
                                        )
                                }
                        }
                }
        } else {
                Spacer(modifier = Modifier.height(8.dp))
        }
}
