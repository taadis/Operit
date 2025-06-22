package com.ai.assistance.operit.ui.floating

import FloatingChatWindowMode
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.attachments.AttachmentManager
import com.ai.assistance.operit.ui.features.chat.components.AttachmentChip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 悬浮聊天窗口的主要UI组件
 *
 * @param messages 要显示的聊天消息列表
 * @param width 窗口宽度
 * @param height 窗口高度
 * @param onClose 关闭窗口的回调
 * @param onResize 调整窗口大小的回调
 * @param isBallMode 是否为球模式
 * @param ballSize 球的大小
 * @param initialWindowScale 初始窗口缩放比例
 * @param onToggleBallMode 切换球模式的回调
 * @param onMove 悬浮窗移动的回调，传递相对移动距离和当前缩放比例
 * @param snapToEdge 靠边收起的回调
 * @param isAtEdge 是否处于屏幕边缘
 * @param screenWidth 屏幕宽度参数，用于边界检测
 * @param screenHeight 屏幕高度参数，用于边界检测
 * @param currentX 当前窗口X坐标
 * @param currentY 当前窗口Y坐标
 * @param saveWindowState 保存窗口状态的回调
 * @param onSendMessage 发送消息的回调
 * @param onAttachmentRequest 附件请求回调
 * @param attachments 当前附件列表
 * @param onRemoveAttachment 删除附件回调
 * @param onInputFocusRequest 请求输入焦点的回调，参数为true时请求获取焦点，false时释放焦点
 */
@Composable
fun FloatingChatWindow(
        messages: List<ChatMessage>,
        width: Dp,
        height: Dp,
        onClose: () -> Unit,
        onResize: (Dp, Dp) -> Unit,
        isBallMode: Boolean = false,
        ballSize: Dp = 48.dp,
        initialWindowScale: Float = 1.0f,
        onToggleBallMode: () -> Unit = {},
        onMove: (Float, Float, Float) -> Unit = { _, _, _ -> },
        snapToEdge: (Boolean) -> Unit = { _ -> },
        isAtEdge: Boolean = false,
        screenWidth: Dp = 1080.dp,
        screenHeight: Dp = 2340.dp,
        currentX: Float = 0f,
        currentY: Float = 0f,
        saveWindowState: (() -> Unit)? = null,
        onSendMessage: ((String) -> Unit)? = null,
        onAttachmentRequest: ((String) -> Unit)? = null,
        attachments: List<AttachmentInfo> = emptyList(),
        onRemoveAttachment: ((String) -> Unit)? = null,
        onInputFocusRequest: ((Boolean) -> Unit)? = null
) {
    val updatedMessages = remember(messages) { messages }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 主题和颜色
    val backgroundColor = MaterialTheme.colorScheme.background
    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 淡入淡出动画
    val animatedAlpha = remember { Animatable(1f) }

    // 模式过渡动画状态
    val isInTransition = remember { mutableStateOf(false) }

    // 模式切换动画
    val ballToWindowTransition = remember { Animatable(if (isBallMode) 0f else 1f) }

    // 窗口形状圆角动画 - 添加一个硬切换标志
    val cornerRadius = remember { Animatable(if (isBallMode) 100f else 12f) }
    // 添加一个标志用来决定是否显示当前的圆角过渡动画
    val useDirectCorners = remember { mutableStateOf(false) }

    // 窗口缩放状态 - 初始值来自服务，限制最大为1.0
    var windowScale by remember { mutableFloatStateOf(initialWindowScale.coerceIn(0.5f, 1.0f)) }

    // 当初始缩放值更新时应用它，限制最大缩放为1.0
    LaunchedEffect(initialWindowScale) { windowScale = initialWindowScale.coerceIn(0.5f, 1.0f) }

    // 窗口尺寸状态（基于传入的width和height）- 确保尺寸有效
    var windowWidthState by remember { mutableStateOf(width.coerceAtLeast(200.dp)) }
    var windowHeightState by remember { mutableStateOf(height.coerceAtLeast(250.dp)) }

    // 边缘检测相关的状态
    var activeEdge by remember { mutableStateOf(ResizeEdge.NONE) }
    var isEdgeResizing by remember { mutableStateOf(false) }
    val edgeHighlightColor = MaterialTheme.colorScheme.primary
    val borderThickness = 3.dp // 高亮边框厚度

    // 记录拖动开始时的尺寸，用于计算调整
    var initialWindowWidth by remember { mutableStateOf(0f) }
    var initialWindowHeight by remember { mutableStateOf(0f) }

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

    // 当模式变化时更新缩放和动画
    LaunchedEffect(isBallMode) {
        // 设置转换状态开始
        isInTransition.value = true

        if (isBallMode) {
            // 向球模式过渡 - 保持现有逻辑，球模式需要圆角
            useDirectCorners.value = false
            coroutineScope.launch {
                // 同时处理圆角和大小变化，无需先淡出再淡入
                animatedAlpha.animateTo(
                        0.9f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing)
                )

                // 并行执行这些动画以加快过渡
                coroutineScope.launch {
                    cornerRadius.animateTo(
                            100f,
                            animationSpec = tween(150, easing = FastOutSlowInEasing)
                    )
                }

                coroutineScope.launch {
                    ballToWindowTransition.animateTo(
                            0f,
                            animationSpec = tween(150, easing = FastOutSlowInEasing)
                    )
                }

                // 快速恢复透明度
                animatedAlpha.animateTo(1f, animationSpec = tween(100))
            }

            // 切换到球模式时，保存当前缩放的90%
            val targetScale = (windowScale * 0.9f).coerceAtLeast(0.5f)
            windowScale = targetScale
        } else {
            // 从球模式切换到窗口模式 - 使用直接切换，避免圆角过渡
            useDirectCorners.value = true

            // 立即设置窗口圆角 - 在动画开始前
            cornerRadius.snapTo(12f)

            coroutineScope.launch {
                // 轻微的淡出效果
                animatedAlpha.animateTo(
                        0.85f,
                        animationSpec = tween(80, easing = FastOutSlowInEasing)
                )

                // 只做大小过渡动画，直接从球扩展到窗口，无需圆角过渡
                ballToWindowTransition.animateTo(
                        1f,
                        animationSpec = tween(160, easing = FastOutSlowInEasing)
                )

                // 快速恢复透明度
                animatedAlpha.animateTo(1f, animationSpec = tween(60))
            }

            // 从球模式切换回窗口模式时确保窗口可见
            if (windowScale < 0.6f) {
                windowScale = 0.6f
            }
            snapToEdge(false)
        }

        // 短暂延迟后完成转换
        delay(200)
        isInTransition.value = false
        // 转换完成后重置直接切换标志
        useDirectCorners.value = false
    }

    // 处理拖动事件的函数
    val handleDrag = { dx: Float, dy: Float -> onMove(dx, dy, windowScale) }

    // 模式切换处理函数 - 添加防抖动
    val lastToggleTime = remember { mutableStateOf(0L) }
    val handleModeToggle = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToggleTime.value > 500 && !isInTransition.value) {
            lastToggleTime.value = currentTime
            onToggleBallMode()
        }
    }

    // 计算动画过渡中的尺寸
    val transitionBallSize = with(density) { ballSize.toPx() }
    val transitionWindowWidth = with(density) { windowWidthState.toPx() }
    val transitionWindowHeight = with(density) { windowHeightState.toPx() }

    // 计算过渡中的尺寸
    val currentWidth = lerp(transitionBallSize, transitionWindowWidth, ballToWindowTransition.value)
    val currentHeight =
            lerp(transitionBallSize, transitionWindowHeight, ballToWindowTransition.value)
    // 修改圆角计算，从球模式到窗口模式时直接使用窗口圆角
    val currentCornerRadius =
            if (useDirectCorners.value && !isBallMode) {
                with(density) { 12f.dp }
            } else {
                with(density) { cornerRadius.value.dp }
            }

    // 添加过渡期间的视觉反馈
    val transitionFeedback = remember { Animatable(0f) }

    // 当状态变化时提供视觉反馈
    LaunchedEffect(isInTransition.value) {
        if (isInTransition.value) {
            // 启动一个短暂的波纹动画 - 减少持续时间
            transitionFeedback.snapTo(0f)
            transitionFeedback.animateTo(
                    1f,
                    animationSpec = tween(200, easing = FastOutSlowInEasing)
            )
        } else {
            transitionFeedback.snapTo(0f)
        }
    }

    // 添加淡入淡出内容动画
    val contentVisible = !isInTransition.value

    // Add state for the input dialog
    var showInputDialog by remember { mutableStateOf(false) }
    var userMessage by remember { mutableStateOf("") }

    // 添加附件选择器相关状态
    var showAttachmentPanel by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val attachmentManager = remember {
        AttachmentManager(context, AIToolHandler.getInstance(context))
    }

    // 监听显示状态变化并通知焦点变化
    LaunchedEffect(showInputDialog) {
        // 通知服务需要切换焦点模式
        onInputFocusRequest?.invoke(showInputDialog)

        // 如果隐藏输入框，清空消息
        if (!showInputDialog) {
            userMessage = ""
        }
    }

    if (isBallMode) {
        FloatingChatBallMode(
                ballSize = ballSize,
                isAtEdge = isAtEdge,
                windowScale = windowScale,
                animatedAlpha = animatedAlpha.value,
                transitionFeedback = transitionFeedback.value,
                onToggleBallMode = onToggleBallMode,
                onModeChangeRequest = { onToggleBallMode() },
                onInputRequest = { onInputFocusRequest?.invoke(true) },
                onMove = { dx, dy -> onMove(dx, dy, windowScale) },
                saveWindowState = saveWindowState,
                snapToEdge = snapToEdge,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                currentX = currentX,
                currentY = currentY
        )
    } else {
        // 窗口模式
        // Pre-extract theme colors for Canvas usage
        FloatingChatWindowMode(
            windowWidthState,
            windowHeightState,
            windowScale,
            animatedAlpha,
            currentCornerRadius,
            borderThickness,
            isEdgeResizing,
            edgeHighlightColor,
            backgroundColor,
            initialWindowWidth,
            initialWindowHeight,
            transitionFeedback,
            onClose,
            onToggleBallMode,
            saveWindowState,
            handleDrag,
            contentVisible,
            showInputDialog,
            messages,
            userMessageColor,
            aiMessageColor,
            userTextColor,
            aiTextColor,
            systemMessageColor,
            systemTextColor,
            thinkingBackgroundColor,
            thinkingTextColor,
            showAttachmentPanel,
            attachments,
            onRemoveAttachment,
            coroutineScope,
            userMessage,
            onSendMessage,
            isBallMode,
            activeEdge,
            density,
            onResize,
            onAttachmentRequest
        )
    }
}
