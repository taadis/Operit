package com.ai.assistance.operit.ui.floating.ui.live2d

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.floating.FloatContext

@Composable
fun FloatingLive2dMode(floatContext: FloatContext) {
    var scale by remember { mutableStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(floatContext.windowScale) {
        if (!isDragging) {
            scale = floatContext.windowScale
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        // 主内容区
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 可拖动的顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            floatContext.onMove(dragAmount.x, dragAmount.y, scale)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    floatContext.onModeChange(floatContext.previousMode)
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
                Text("Live2D 模式", style = MaterialTheme.typography.titleMedium)
                // 空白占位，让标题居中
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Live2D 人物占位符
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Cyan.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text("这里是Live2D人物")
            }
        }

        // 缩放手柄 - 右下角
        Box(
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.BottomEnd)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            floatContext.saveWindowState?.invoke()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val scaleDelta = (dragAmount.y * 0.005f)
                            scale = (scale + scaleDelta).coerceIn(0.3f, 2.0f)
                            floatContext.onScaleChange(scale)
                        }
                    )
                }
        )
    }
} 