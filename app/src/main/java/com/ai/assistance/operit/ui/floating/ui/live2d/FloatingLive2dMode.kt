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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.Live2DConfig
import com.ai.assistance.operit.data.repository.Live2DRepository
import com.ai.assistance.operit.ui.floating.FloatContext
import com.chatwaifu.live2d.JniBridgeJava
import com.chatwaifu.live2d.Live2DViewCompose

@Composable
fun FloatingLive2dMode(floatContext: FloatContext) {
    var scale by remember { mutableStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(floatContext.windowScale) {
        if (!isDragging) {
            scale = floatContext.windowScale
        }
    }

    val context = LocalContext.current
    val repository = remember { Live2DRepository.getInstance(context) }
    val models by repository.models.collectAsState()
    val currentConfig by repository.currentConfig.collectAsState()
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

            // Live2D 显示区域
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (JniBridgeJava.isLibraryLoaded()) {
                    if (models.isEmpty()) {
                        Text(
                            "没有可用的Live2D模型", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    } else if (errorMessage != null) {
                        Text(
                            text = "加载失败: $errorMessage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        val currentModel = models.find { it.id == currentConfig?.modelId }
                        if (currentModel != null && currentConfig != null) {
                            Live2DViewCompose(
                                modifier = Modifier.fillMaxSize(),
                                model = currentModel,
                                config = currentConfig?.copy(renderBack = false) ?: currentConfig,
                                onError = { error -> errorMessage = "加载失败: $error" }
                            )
                        } else {
                            Text(
                                "未选择模型",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                } else {
                    Text(
                        "Live2D库未正确加载",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
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