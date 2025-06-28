package com.ai.assistance.operit.ui.floating.ui.ball

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode

/**
 * 渲染Live2D悬浮球模式界面
 */
@Composable
fun FloatingLive2dBallMode(floatContext: FloatContext) {
    Box(
        modifier = Modifier
            .size(floatContext.ballSize)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        floatContext.saveWindowState?.invoke()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        floatContext.onMove(
                            dragAmount.x,
                            dragAmount.y,
                            floatContext.windowScale
                        )
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // 点击切换到Live2D模式
                        floatContext.onModeChange(FloatingMode.LIVE2D)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Live2D模式",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.Center)
            )
        }
    }
} 