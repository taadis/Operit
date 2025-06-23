package com.ai.assistance.operit.ui.floating.ui.ball

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.floating.FloatContext
import kotlinx.coroutines.delay

/**
 * 渲染悬浮窗的球模式界面 - 简化版
 */
@Composable
fun FloatingChatBallMode(floatContext: FloatContext) {
    val ballHoverState = remember { mutableStateOf(false) }
    val touchAnimationState = remember { mutableStateOf(false) }
    val touchAnimationValue = remember { Animatable(0f) }
    val ballTapState = remember { mutableStateOf(false) }

    // 预先提取主题颜色用于Canvas
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val primaryColor = MaterialTheme.colorScheme.primary

    // 处理点击事件
    LaunchedEffect(ballTapState.value) {
        if (ballTapState.value) {
            if (floatContext.isAtEdge) {
                floatContext.snapToEdge(false)
            } else {
                // 切换到窗口模式
                floatContext.onToggleBallMode()
            }
            // 重置状态
            ballTapState.value = false
        }
    }

    // 触摸动画效果
    LaunchedEffect(touchAnimationState.value) {
        if (touchAnimationState.value) {
            touchAnimationValue.snapTo(0f)
            touchAnimationValue.animateTo(1f, animationSpec = tween(500, easing = EaseOutQuart))
            touchAnimationState.value = false
        }
    }

    // 球模式动画
    val pulseAnimation = rememberInfiniteTransition(label = "BallPulse")
    val pulseScale by pulseAnimation.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    // 当用户悬停或触摸时的效果
    val ballInteractionModifier = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                // 检测是否有触摸点在球上
                ballHoverState.value = event.changes.any { it.pressed }

                if (event.type == PointerEventType.Press) {
                    touchAnimationState.value = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(floatContext.ballSize)
            .shadow(
                elevation = if (ballHoverState.value) 6.dp else 4.dp,
                shape = CircleShape
            )
            .clip(CircleShape)
            .background(
                if (ballHoverState.value) primaryColor.copy(alpha = 0.9f) else primaryColor
            )
            .alpha(if (floatContext.isAtEdge) 0.8f else 1f)
            .then(ballInteractionModifier)
            .graphicsLayer {
                // 确保球的缩放不会太小，最小保持0.8倍大小
                val effectiveScale = maxOf(floatContext.windowScale, 0.8f)
                val hoverEffect = if (ballHoverState.value) 1.05f else 1f
                scaleX = effectiveScale * 
                         (if (floatContext.isAtEdge) 0.7f else 1f) * 
                         (if (!floatContext.isAtEdge) pulseScale else 1f) * 
                         hoverEffect
                scaleY = effectiveScale * 
                         (if (floatContext.isAtEdge) 0.7f else 1f) * 
                         (if (!floatContext.isAtEdge) pulseScale else 1f) * 
                         hoverEffect
                alpha = floatContext.animatedAlpha.value
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // 检查点击是否在圆内
                        val radius = size.width / 2f
                        val center = Offset(radius, radius)
                        val distance = (offset - center).getDistance()

                        // 只有当点击在圆内才处理事件
                        if (distance <= radius) {
                            if (floatContext.isAtEdge)
                                floatContext.snapToEdge(false)
                        }
                    },
                    onDragEnd = {
                        floatContext.saveWindowState?.invoke()

                        // 靠边检测
                        val edgeThresholdPx = with(floatContext.density) { 20.dp.toPx() }
                        if (floatContext.currentX < edgeThresholdPx ||
                            floatContext.currentX > floatContext.screenWidth.toPx() - edgeThresholdPx
                        ) {
                            floatContext.snapToEdge(true)
                        }
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
    ) {
        // 触摸反馈波纹
        if (touchAnimationValue.value > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = onPrimaryColor.copy(
                        alpha = 0.2f * (1f - touchAnimationValue.value)
                    ),
                    radius = size.minDimension * 0.7f * touchAnimationValue.value,
                    center = Offset(size.width / 2, size.height / 2)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            // 检查点击是否在圆内
                            val radius = size.width / 2f
                            val center = Offset(radius, radius)
                            val distance = (offset - center).getDistance()

                            // 只有当点击在圆内才处理事件
                            if (distance <= radius) {
                                touchAnimationState.value = true
                                ballTapState.value = true
                            }
                        },
                        onLongPress = { offset ->
                            // 检查长按是否在圆内
                            val radius = size.width / 2f
                            val center = Offset(radius, radius)
                            val distance = (offset - center).getDistance()

                            // 只有当长按在圆内才处理事件
                            if (distance <= radius) {
                                // 长按重置位置到屏幕中心（安全恢复功能）
                                val centerX = floatContext.screenWidth.value / 2
                                val centerY = floatContext.screenHeight.value / 2
                                floatContext.onMove(
                                    centerX - floatContext.currentX,
                                    centerY - floatContext.currentY,
                                    floatContext.windowScale
                                )

                                // 保存新位置
                                floatContext.saveWindowState?.invoke()
                            }
                        }
                    )
                }
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "聊天窗口",
                tint = Color.White,
                modifier = Modifier.size(24.dp).align(Alignment.Center)
            )
        }
    }
}
