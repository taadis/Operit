package com.ai.assistance.operit.ui.floating

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.floating.ui.window.ResizeEdge
import kotlinx.coroutines.CoroutineScope

@Stable
class FloatContext(
        // From parameters
        val messages: List<ChatMessage>,
        val width: Dp,
        val height: Dp,
        val isBallMode: Boolean,
        val ballSize: Dp,
        val initialWindowScale: Float,
        val isAtEdge: Boolean,
        val screenWidth: Dp,
        val screenHeight: Dp,
        val currentX: Float,
        val currentY: Float,
        val attachments: List<AttachmentInfo>,
        val onClose: () -> Unit,
        val onResize: (Dp, Dp) -> Unit,
        val onToggleBallMode: () -> Unit,
        val onMove: (Float, Float, Float) -> Unit,
        val snapToEdge: (Boolean) -> Unit,
        val saveWindowState: (() -> Unit)?,
        val onSendMessage: ((String) -> Unit)?,
        val onAttachmentRequest: ((String) -> Unit)?,
        val onRemoveAttachment: ((String) -> Unit)?,
        val onInputFocusRequest: ((Boolean) -> Unit)?,

        // Internal states
        val coroutineScope: CoroutineScope,
        val density: Density,
) {
    val animatedAlpha = Animatable(1f)
    val isInTransition = mutableStateOf(false)
    val ballToWindowTransition = Animatable(if (isBallMode) 0f else 1f)
    val cornerRadius = Animatable(if (isBallMode) 100f else 12f)
    val useDirectCorners = mutableStateOf(false)

    var windowScale by mutableFloatStateOf(initialWindowScale.coerceIn(0.5f, 1.0f))
    var windowWidthState by mutableStateOf(width.coerceAtLeast(200.dp))
    var windowHeightState by mutableStateOf(height.coerceAtLeast(250.dp))

    var activeEdge by mutableStateOf(ResizeEdge.NONE)
    var isEdgeResizing by mutableStateOf(false)
    var initialWindowWidth by mutableStateOf(0f)
    var initialWindowHeight by mutableStateOf(0f)

    val transitionFeedback = Animatable(0f)
    val contentVisible by derivedStateOf { !isInTransition.value }

    var showInputDialog by mutableStateOf(false)
    var userMessage by mutableStateOf("")
    var showAttachmentPanel by mutableStateOf(false)
}

@Composable
fun rememberFloatContext(
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
): FloatContext {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    return remember(
            messages,
            width,
            height,
            isBallMode,
            initialWindowScale,
            isAtEdge,
            screenWidth,
            screenHeight,
            currentX,
            currentY,
            attachments
    ) {
        FloatContext(
                messages = messages,
                width = width,
                height = height,
                isBallMode = isBallMode,
                ballSize = ballSize,
                initialWindowScale = initialWindowScale,
                isAtEdge = isAtEdge,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                currentX = currentX,
                currentY = currentY,
                attachments = attachments,
                onClose = onClose,
                onResize = onResize,
                onToggleBallMode = onToggleBallMode,
                onMove = onMove,
                snapToEdge = snapToEdge,
                saveWindowState = saveWindowState,
                onSendMessage = onSendMessage,
                onAttachmentRequest = onAttachmentRequest,
                onRemoveAttachment = onRemoveAttachment,
                onInputFocusRequest = onInputFocusRequest,
                coroutineScope = coroutineScope,
                density = density
        )
    }
}
