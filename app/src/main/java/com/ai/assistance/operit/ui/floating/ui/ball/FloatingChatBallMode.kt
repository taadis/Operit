package com.ai.assistance.operit.ui.floating

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 渲染悬浮窗的球模式界面
 *
 * @param ballSize 球的大小
 * @param isAtEdge 是否处于屏幕边缘
 * @param windowScale 窗口缩放比例
 * @param animatedAlpha 淡入淡出动画值
 * @param transitionFeedback 过渡反馈动画
 * @param onToggleBallMode 切换到窗口模式的回调
 * @param onModeChangeRequest 请求模式切换的回调，会触发handleModeToggle
 * @param onInputRequest 请求显示输入框的回调
 * @param onMove 移动事件的回调
 * @param saveWindowState 保存窗口状态的回调
 * @param snapToEdge 靠边收起的回调
 * @param screenWidth 屏幕宽度
 * @param screenHeight 屏幕高度
 * @param currentX 当前X坐标
 * @param currentY 当前Y坐标
 */
@Composable
fun FloatingChatBallMode(
        ballSize: Dp = 48.dp,
        isAtEdge: Boolean = false,
        windowScale: Float = 0.8f,
        animatedAlpha: Float = 1f,
        transitionFeedback: Float = 0f,
        onToggleBallMode: () -> Unit = {},
        onModeChangeRequest: () -> Unit = {},
        onInputRequest: () -> Unit = {},
        onMove: (Float, Float) -> Unit = { _, _ -> },
        saveWindowState: (() -> Unit)? = null,
        snapToEdge: (Boolean) -> Unit = { _ -> },
        screenWidth: Dp = 1080.dp,
        screenHeight: Dp = 2340.dp,
        currentX: Float = 0f,
        currentY: Float = 0f
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val ballHoverState = remember { mutableStateOf(false) }
    val touchAnimationState = remember { mutableStateOf(false) }
    val touchAnimationValue = remember { Animatable(0f) }
    val showBallButtons by remember { mutableStateOf(true) }
    val inputButtonPressed = remember { mutableStateOf(false) }
    val ballTapState = remember { mutableStateOf(false) }

    // Pre-extract theme colors for Canvas usage
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val primaryColor = MaterialTheme.colorScheme.primary

    // Handle input button press
    LaunchedEffect(inputButtonPressed.value) {
        if (inputButtonPressed.value) {
            // 修改为先切换到窗口模式，再显示输入框
            onToggleBallMode()
            // 等待模式切换完成后再显示输入框
            delay(500) // 等待模式切换动画完成
            onInputRequest()
            inputButtonPressed.value = false
        }
    }

    // Handle click events
    LaunchedEffect(ballTapState.value) {
        if (ballTapState.value) {
            if (isAtEdge) {
                snapToEdge(false)
            } else {
                onModeChangeRequest()
            }
            // Reset state
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
    val pulseScale by
            pulseAnimation.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.04f,
                    animationSpec =
                            infiniteRepeatable(
                                    animation = tween(2000, easing = EaseInOutQuad),
                                    repeatMode = RepeatMode.Reverse
                            ),
                    label = "PulseScale"
            )

    // 当用户悬停或触摸时的效果
    val ballInteractionModifier =
            Modifier.pointerInput(Unit) {
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
            modifier =
                    Modifier.size(ballSize)
                            .shadow(
                                    elevation = if (ballHoverState.value) 6.dp else 4.dp,
                                    shape = CircleShape
                            )
                            .clip(CircleShape)
                            .background(
                                    if (ballHoverState.value) primaryColor.copy(alpha = 0.9f)
                                    else primaryColor
                            )
                            .alpha(if (isAtEdge) 0.8f else 1f)
                            .then(ballInteractionModifier)
                            .graphicsLayer {
                                // 确保球的缩放不会太小，最小保持0.8倍大小
                                val effectiveScale = maxOf(windowScale, 0.8f)
                                val hoverEffect = if (ballHoverState.value) 1.05f else 1f
                                scaleX =
                                        effectiveScale *
                                                (if (isAtEdge) 0.7f else 1f) *
                                                (if (!isAtEdge) pulseScale else 1f) *
                                                hoverEffect
                                scaleY =
                                        effectiveScale *
                                                (if (isAtEdge) 0.7f else 1f) *
                                                (if (!isAtEdge) pulseScale else 1f) *
                                                hoverEffect
                                alpha = animatedAlpha
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
                                                if (isAtEdge) snapToEdge(false)
                                            }
                                        },
                                        onDragEnd = {
                                            saveWindowState?.invoke()

                                            // 靠边检测
                                            val edgeThresholdPx = with(density) { 20.dp.toPx() }
                                            if (currentX < edgeThresholdPx ||
                                                            currentX >
                                                                    screenWidth.toPx() -
                                                                            edgeThresholdPx
                                            ) {
                                                snapToEdge(true)
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            onMove(dragAmount.x, dragAmount.y)
                                        }
                                )
                            }
    ) {
        // 过渡时的波纹效果
        if (transitionFeedback > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                        color = onPrimaryColor.copy(alpha = 0.15f * (1f - transitionFeedback)),
                        radius = size.minDimension * 0.6f * transitionFeedback,
                        center = Offset(size.width / 2, size.height / 2)
                )
            }
        }

        // 触摸反馈波纹
        if (touchAnimationValue.value > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                        color =
                                onPrimaryColor.copy(
                                        alpha = 0.2f * (1f - touchAnimationValue.value)
                                ),
                        radius = size.minDimension * 0.7f * touchAnimationValue.value,
                        center = Offset(size.width / 2, size.height / 2)
                )
            }
        }

        Box(
                modifier =
                        Modifier.fillMaxSize().padding(4.dp).pointerInput(Unit) {
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
                                            val centerX = screenWidth.value / 2
                                            val centerY = screenHeight.value / 2
                                            onMove(centerX - currentX, centerY - currentY)

                                            // 保存新位置
                                            saveWindowState?.invoke()
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
