package com.ai.assistance.operit.ui.floating.ui.window

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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.chat.components.AttachmentChip
import com.ai.assistance.operit.ui.floating.FloatContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FloatingChatWindowMode(floatContext: FloatContext) {
    val currentCornerRadius =
            if (floatContext.useDirectCorners.value && !floatContext.isBallMode) {
                with(floatContext.density) { 12f.dp }
            } else {
                with(floatContext.density) { floatContext.cornerRadius.value.dp }
            }
    val borderThickness = 3.dp
    val edgeHighlightColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    val handleDrag = { dx: Float, dy: Float ->
        floatContext.onMove(dx, dy, floatContext.windowScale)
    }
    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
            modifier =
                    Modifier.width(floatContext.windowWidthState)
                            .height(floatContext.windowHeightState)
    ) {
        // 创建一个呈现边缘视觉反馈的覆盖层
        Box(
                modifier =
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = floatContext.windowScale
                            scaleY = floatContext.windowScale
                            transformOrigin = TransformOrigin(0f, 0f)
                            alpha = floatContext.animatedAlpha.value
                        }
        ) {
            // 主要内容区域，含边框
            Box(
                    modifier =
                            Modifier.fillMaxSize()
                                    .shadow(8.dp, RoundedCornerShape(currentCornerRadius))
                                    .border(
                                            width = borderThickness,
                                            color =
                                                    if (floatContext.isEdgeResizing)
                                                            edgeHighlightColor
                                                    else Color.Transparent,
                                            shape = RoundedCornerShape(currentCornerRadius)
                                    )
                                    .clip(RoundedCornerShape(currentCornerRadius))
                                    .background(backgroundColor)
                                    .onSizeChanged { size ->
                                        // 更新实际窗口大小（像素值）
                                        floatContext.initialWindowWidth = size.width.toFloat()
                                        floatContext.initialWindowHeight = size.height.toFloat()
                                    }
            ) {
                // 过渡时的波纹效果
                if (floatContext.transitionFeedback.value > 0) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val radius =
                                size.minDimension * 0.6f * floatContext.transitionFeedback.value

                        drawCircle(
                                color =
                                        primaryColor.copy(
                                                alpha =
                                                        0.1f *
                                                                (1f -
                                                                        floatContext
                                                                                .transitionFeedback
                                                                                .value)
                                        ),
                                radius = radius,
                                center = Offset(centerX, centerY)
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // 窗口模式下的顶部工具栏
                    val titleBarHover = remember { mutableStateOf(false) }
                    // 添加按钮事件状态
                    val closeButtonPressed = remember { mutableStateOf(false) }
                    val minimizeButtonPressed = remember { mutableStateOf(false) }

                    // 处理关闭按钮副作用
                    LaunchedEffect(closeButtonPressed.value) {
                        if (closeButtonPressed.value) {
                            floatContext.animatedAlpha.animateTo(0f, animationSpec = tween(200))
                            floatContext.onClose()
                            closeButtonPressed.value = false // 重置状态
                        }
                    }

                    // 处理最小化按钮副作用
                    LaunchedEffect(minimizeButtonPressed.value) {
                        if (minimizeButtonPressed.value) {
                            floatContext.onToggleBallMode()
                            minimizeButtonPressed.value = false // 重置状态
                        }
                    }

                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .height(48.dp)
                                            .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha =
                                                                    if (titleBarHover.value) 0.3f
                                                                    else 0.2f
                                                    )
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .pointerInput(Unit) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        titleBarHover.value =
                                                                event.changes.any { it.pressed }
                                                    }
                                                }
                                            }
                                            .pointerInput(Unit) {
                                                detectDragGestures(
                                                        onDragEnd = {
                                                            floatContext.saveWindowState?.invoke()
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            handleDrag(dragAmount.x, dragAmount.y)
                                                        }
                                                )
                                            }
                    ) {
                        // 显示内容（使用条件渲染而不是ContentWithFade）
                        if (floatContext.contentVisible) {
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                        text = "AI助手",
                                        style =
                                                MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Medium
                                                ),
                                        color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 最小化按钮
                                    val minimizeHover = remember { mutableStateOf(false) }

                                    IconButton(
                                            onClick = { minimizeButtonPressed.value = true },
                                            modifier =
                                                    Modifier.size(30.dp)
                                                            .background(
                                                                    color =
                                                                            if (minimizeHover.value)
                                                                                    primaryColor
                                                                                            .copy(
                                                                                                    alpha =
                                                                                                            0.1f
                                                                                            )
                                                                            else Color.Transparent,
                                                                    shape = CircleShape
                                                            )
                                                            .pointerInput(Unit) {
                                                                awaitPointerEventScope {
                                                                    while (true) {
                                                                        val event =
                                                                                awaitPointerEvent()
                                                                        minimizeHover.value =
                                                                                event.changes.any {
                                                                                    it.pressed
                                                                                }
                                                                    }
                                                                }
                                                            }
                                    ) {
                                        Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "最小化",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // 关闭按钮
                                    val closeHover = remember { mutableStateOf(false) }

                                    IconButton(
                                            onClick = { closeButtonPressed.value = true },
                                            modifier =
                                                    Modifier.size(30.dp)
                                                            .background(
                                                                    color =
                                                                            if (closeHover.value)
                                                                                    errorColor.copy(
                                                                                            alpha =
                                                                                                    0.1f
                                                                                    )
                                                                            else Color.Transparent,
                                                                    shape = CircleShape
                                                            )
                                                            .pointerInput(Unit) {
                                                                awaitPointerEventScope {
                                                                    while (true) {
                                                                        val event =
                                                                                awaitPointerEvent()
                                                                        closeHover.value =
                                                                                event.changes.any {
                                                                                    it.pressed
                                                                                }
                                                                    }
                                                                }
                                                            }
                                    ) {
                                        Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "关闭",
                                                tint =
                                                        if (closeHover.value) errorColor
                                                        else onSurfaceVariantColor,
                                                modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 聊天内容区域
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        // 条件性显示聊天内容或输入框
                        if (!floatContext.showInputDialog) {
                            val scrollState = rememberScrollState()

                            // Auto-scroll to bottom
                            LaunchedEffect(floatContext.messages.size) {
                                if (floatContext.messages.isNotEmpty()) {
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }
                            }

                            val isLoading = floatContext.messages.lastOrNull()?.sender == "think"

                            Column(
                                    modifier =
                                            Modifier.fillMaxSize()
                                                    .verticalScroll(scrollState)
                                                    .padding(horizontal = 16.dp, vertical = 16.dp)
                            ) {
                                floatContext.messages.forEachIndexed { index, message ->
                                    if (message.sender != "think") {
                                        key(message.timestamp) {
                                            MessageItem(
                                                    index = index,
                                                    message = message,
                                                    allMessages = floatContext.messages,
                                                    userMessageColor = userMessageColor,
                                                    aiMessageColor = aiMessageColor,
                                                    userTextColor = userTextColor,
                                                    aiTextColor = aiTextColor,
                                                    systemMessageColor = systemMessageColor,
                                                    systemTextColor = systemTextColor,
                                                    thinkingBackgroundColor =
                                                            thinkingBackgroundColor,
                                                    thinkingTextColor = thinkingTextColor,
                                                    isEditMode = false, // 悬浮窗不支持编辑模式
                                                    onSelectMessageToEdit = null,
                                                    onCopyMessage = null
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }

                                val lastMessage =
                                        floatContext.messages.lastOrNull { it.sender != "think" }
                                if (isLoading &&
                                                (lastMessage?.sender == "user" ||
                                                        (lastMessage?.sender == "ai" &&
                                                                lastMessage.content.isBlank()))
                                ) {
                                    Column(
                                            modifier =
                                                    Modifier.fillMaxWidth().padding(vertical = 0.dp)
                                    ) {
                                        Box(modifier = Modifier.padding(start = 16.dp)) {
                                            LoadingDotsIndicator(aiTextColor)
                                        }
                                    }
                                }
                            }
                        } else {
                            // 集成的输入区域
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                            text = "发送消息",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                    )

                                    IconButton(
                                            onClick = {
                                                floatContext.showInputDialog = false
                                                floatContext.showAttachmentPanel =
                                                        false // 确保关闭附件选择面板
                                            }
                                    ) {
                                        Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "关闭",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 显示已添加的附件
                                if (floatContext.attachments.isNotEmpty()) {
                                    LazyRow(
                                            modifier =
                                                    Modifier.fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(floatContext.attachments) { attachment ->
                                            AttachmentChip(
                                                    attachmentInfo = attachment,
                                                    onRemove = {
                                                        floatContext.onRemoveAttachment?.invoke(
                                                                attachment.filePath
                                                        )
                                                    },
                                                    onInsert = { /* 在悬浮窗中不支持插入操作 */}
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                val focusRequester = remember { FocusRequester() }
                                val keyboardController = LocalSoftwareKeyboardController.current
                                val focusManager = LocalFocusManager.current

                                // 添加一个当输入区域显示时的DisposableEffect
                                DisposableEffect(floatContext.showInputDialog) {
                                    if (floatContext.showInputDialog) {
                                        floatContext.coroutineScope.launch {
                                            // 增加延迟，确保视图完全渲染
                                            delay(300)

                                            // 请求焦点 - 但不主动显示键盘，让服务层处理键盘显示
                                            try {
                                                focusRequester.requestFocus()
                                                // 移除直接显示键盘的代码，避免重复显示
                                            } catch (e: Exception) {
                                                Log.e(
                                                        "FloatingChatWindow",
                                                        "Failed to request focus",
                                                        e
                                                )
                                            }
                                        }
                                    }

                                    // 清理
                                    onDispose {
                                        // 不主动隐藏键盘，让服务层处理
                                    }
                                }

                                // 改成Box包装输入框，使其填充剩余空间
                                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                                    // 输入框 - 占满可用空间
                                    OutlinedTextField(
                                            value = floatContext.userMessage,
                                            onValueChange = { floatContext.userMessage = it },
                                            placeholder = { Text("请输入您的问题...") },
                                            modifier =
                                                    Modifier.fillMaxSize()
                                                            .focusRequester(focusRequester),
                                            textStyle = TextStyle.Default,
                                            maxLines = Int.MAX_VALUE, // 允许多行
                                            keyboardOptions =
                                                    KeyboardOptions(
                                                            imeAction = ImeAction.Send,
                                                            autoCorrect = true
                                                    ),
                                            keyboardActions =
                                                    KeyboardActions(
                                                            onSend = {
                                                                if (floatContext.userMessage
                                                                                .isNotBlank() ||
                                                                                floatContext
                                                                                        .attachments
                                                                                        .isNotEmpty()
                                                                ) {
                                                                    floatContext.onSendMessage
                                                                            ?.invoke(
                                                                                    floatContext
                                                                                            .userMessage
                                                                            )
                                                                    floatContext.userMessage = ""
                                                                    floatContext.showInputDialog =
                                                                            false
                                                                    floatContext
                                                                            .showAttachmentPanel =
                                                                            false
                                                                }
                                                            }
                                                    ),
                                            colors =
                                                    OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor =
                                                                    MaterialTheme.colorScheme
                                                                            .primary,
                                                            unfocusedBorderColor =
                                                                    MaterialTheme.colorScheme
                                                                            .outline
                                                    ),
                                            shape = RoundedCornerShape(12.dp)
                                    )

                                    // 发送按钮 - 放在右下角
                                    FloatingActionButton(
                                            onClick = {
                                                if (floatContext.userMessage.isNotBlank() ||
                                                                floatContext.attachments
                                                                        .isNotEmpty()
                                                ) {
                                                    floatContext.onSendMessage?.invoke(
                                                            floatContext.userMessage
                                                    )
                                                    floatContext.userMessage = ""
                                                    floatContext.showInputDialog = false
                                                    floatContext.showAttachmentPanel = false
                                                }
                                            },
                                            modifier =
                                                    Modifier.align(Alignment.BottomEnd)
                                                            .padding(8.dp)
                                                            .size(46.dp),
                                            containerColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = "发送",
                                                tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // 缩放控制手柄 - 只在不显示输入框且处于窗口模式时显示
                        if (!floatContext.showInputDialog && !floatContext.isBallMode) {
                            // 添加缩放按钮悬停状态
                            val scaleButtonHover = remember { mutableStateOf(false) }

                            Box(
                                    modifier =
                                            Modifier.size(48.dp)
                                                    .padding(6.dp)
                                                    .align(Alignment.BottomEnd)
                                                    .offset(x = (-8).dp, y = (-8).dp)
                                                    // 移除阴影和背景
                                                    // 添加点击涟漪效果的背景
                                                    .background(
                                                            color =
                                                                    if (scaleButtonHover.value)
                                                                            primaryColor.copy(
                                                                                    alpha = 0.1f
                                                                            )
                                                                    else Color.Transparent,
                                                            shape = CircleShape
                                                    )
                                                    // 移除边框
                                                    .pointerInput(Unit) {
                                                        awaitPointerEventScope {
                                                            while (true) {
                                                                val event = awaitPointerEvent()
                                                                scaleButtonHover.value =
                                                                        event.changes.any {
                                                                            it.pressed
                                                                        }
                                                            }
                                                        }
                                                    }
                                                    .pointerInput(Unit) {
                                                        detectDragGestures(
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    val scaleDelta =
                                                                            dragAmount.y * 0.001f
                                                                    floatContext.windowScale =
                                                                            (floatContext
                                                                                            .windowScale +
                                                                                            scaleDelta)
                                                                                    .coerceIn(
                                                                                            0.5f,
                                                                                            1.0f
                                                                                    )
                                                                    floatContext.saveWindowState
                                                                            ?.invoke()
                                                                }
                                                        )
                                                    }
                                                    .pointerInput(Unit) {
                                                        detectTapGestures {
                                                            floatContext.windowScale =
                                                                    when {
                                                                        floatContext.windowScale >
                                                                                0.8f -> 0.7f
                                                                        floatContext.windowScale >
                                                                                0.7f -> 0.9f
                                                                        else -> 1.0f
                                                                    }
                                                            floatContext.saveWindowState?.invoke()
                                                        }
                                                    }
                            ) {
                                val lineColor =
                                        if (scaleButtonHover.value) primaryColor.copy(alpha = 1.0f)
                                        else primaryColor.copy(alpha = 0.7f)

                                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                    // 绘制缩放图标 -
                                    // 增加粗细使其在透明背景下更加明显
                                    drawLine(
                                            color = lineColor,
                                            start = Offset(size.width * 0.2f, size.height * 0.8f),
                                            end = Offset(size.width * 0.8f, size.height * 0.2f),
                                            strokeWidth = 3.5f // 略微增加线条粗细
                                    )
                                    drawLine(
                                            color = lineColor,
                                            start = Offset(size.width * 0.5f, size.height * 0.8f),
                                            end = Offset(size.width * 0.8f, size.height * 0.5f),
                                            strokeWidth = 3.5f // 略微增加线条粗细
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 移除了底部边框调整高度功能

            // 移除了右侧边框调整宽度功能
            // 右下角 - 调整宽度和高度
            Box(
                    modifier =
                            Modifier.size(25.dp).align(Alignment.BottomEnd).pointerInput(Unit) {
                                detectDragGestures(
                                        onDragStart = {
                                            floatContext.isEdgeResizing = true
                                            floatContext.activeEdge = ResizeEdge.BOTTOM_RIGHT
                                            floatContext.initialWindowWidth =
                                                    floatContext.windowWidthState.value *
                                                            floatContext.density.density
                                            floatContext.initialWindowHeight =
                                                    floatContext.windowHeightState.value *
                                                            floatContext.density.density
                                        },
                                        onDragEnd = {
                                            floatContext.isEdgeResizing = false
                                            floatContext.saveWindowState?.invoke()
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val newWidth =
                                                    (floatContext.initialWindowWidth + dragAmount.x)
                                                            .coerceAtLeast(150f)
                                            val newHeight =
                                                    (floatContext.initialWindowHeight +
                                                                    dragAmount.y)
                                                            .coerceAtLeast(200f)
                                            floatContext.windowWidthState =
                                                    with(floatContext.density) {
                                                        (newWidth / floatContext.density.density).dp
                                                                .coerceAtLeast(150.dp)
                                                    }
                                            floatContext.windowHeightState =
                                                    with(floatContext.density) {
                                                        (newHeight / floatContext.density.density)
                                                                .dp.coerceAtLeast(200.dp)
                                                    }
                                            floatContext.onResize(
                                                    floatContext.windowWidthState,
                                                    floatContext.windowHeightState
                                            )
                                        }
                                )
                            }
            )

            // 显示边缘缩放指示器 - 只保留右下角
            if (floatContext.isEdgeResizing && floatContext.activeEdge == ResizeEdge.BOTTOM_RIGHT) {
                // 右下角调整指示器
                Box(
                        modifier =
                                Modifier.size(8.dp)
                                        .background(color = edgeHighlightColor, shape = CircleShape)
                                        .align(Alignment.BottomEnd)
                )
            }
        }
    }

    // 输入按钮 - 只有在不显示输入框时才显示
    if (!floatContext.showInputDialog && floatContext.onSendMessage != null) {
        Box(
                modifier =
                        Modifier.width(
                                        floatContext.windowWidthState
                                ) // 使用窗口宽度而不是fillMaxSize，确保按钮在窗口内
                                .height(floatContext.windowHeightState) // 使用窗口高度
                                .graphicsLayer { // 应用与窗口相同的缩放
                                    scaleX = floatContext.windowScale
                                    scaleY = floatContext.windowScale
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
        ) {
            // 添加附件按钮 - 放在编辑按钮上方
            SmallFloatingActionButton(
                    onClick = {
                        // 修改为切换附件面板显示/隐藏
                        floatContext.showAttachmentPanel = !floatContext.showAttachmentPanel
                    },
                    modifier =
                            Modifier.align(Alignment.BottomStart)
                                    .padding(start = 12.dp, bottom = 60.dp) // 减小间距，使按钮更靠近
                                    .size(34.dp), // 减小按钮尺寸
                    containerColor =
                            if (floatContext.showAttachmentPanel)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) // 增加透明度
                            else
                                    MaterialTheme.colorScheme.secondaryContainer.copy(
                                            alpha = 0.75f
                                    ), // 增加透明度
                    contentColor =
                            if (floatContext.showAttachmentPanel)
                                    MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription =
                                if (floatContext.showAttachmentPanel) "关闭附件面板" else "添加附件",
                        modifier = Modifier.size(16.dp) // 减小图标尺寸
                )
            }

            // 编辑消息按钮 - 保持原有功能
            SmallFloatingActionButton(
                    onClick = {
                        if (floatContext.isBallMode) {
                            // 如果是球模式，先切换到窗口模式再显示输入框
                            floatContext.onToggleBallMode()
                            // 等待模式切换完成后再显示输入框
                            floatContext.coroutineScope.launch {
                                delay(500) // 等待模式切换动画完成
                                floatContext.showInputDialog = true
                            }
                        } else {
                            floatContext.showInputDialog = true
                        }
                    },
                    modifier =
                            Modifier.align(Alignment.BottomStart)
                                    .padding(12.dp) // 减小内边距
                                    .size(34.dp), // 减小按钮尺寸
                    containerColor =
                            MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.75f // 增加透明度
                            ),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "发送消息",
                        modifier = Modifier.size(16.dp) // 减小图标尺寸
                )
            }
        }
    }

    // 附件面板 - 在点击附件按钮时显示
    if (floatContext.showAttachmentPanel && !floatContext.showInputDialog) {
        Box(
                modifier =
                        Modifier.width(floatContext.windowWidthState)
                                .height(floatContext.windowHeightState)
                                .graphicsLayer {
                                    scaleX = floatContext.windowScale
                                    scaleY = floatContext.windowScale
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
                                // 修改pointerInput逻辑以检测附件按钮区域
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        // 计算附件面板的大概位置（底部区域）
                                        val panelHeight = 220.dp.toPx() // 附件面板的大致高度
                                        val screenHeight = size.height

                                        // 计算附件按钮的区域（左下角+按钮位置）- 使用与实际按钮一致的值
                                        val buttonSize = 34.dp.toPx() // 使用实际按钮尺寸34dp
                                        val buttonPaddingStart = 12.dp.toPx() // 使用实际的开始内边距12dp
                                        val buttonPaddingBottom = 60.dp.toPx() // 使用实际的底部内边距60dp
                                        val buttonLeft = buttonPaddingStart
                                        val buttonTop =
                                                screenHeight - buttonPaddingBottom - buttonSize
                                        val buttonRight = buttonLeft + buttonSize
                                        val buttonBottom = buttonTop + buttonSize

                                        // 增加一点点点击区域，使按钮更容易点到
                                        val expandedClickArea = 6.dp.toPx()
                                        val isButtonClicked =
                                                offset.x >= (buttonLeft - expandedClickArea) &&
                                                        offset.x <=
                                                                (buttonRight + expandedClickArea) &&
                                                        offset.y >=
                                                                (buttonTop - expandedClickArea) &&
                                                        offset.y <=
                                                                (buttonBottom + expandedClickArea)

                                        if (isButtonClicked) {
                                            // 点击了附件按钮，切换面板显示状态
                                            floatContext.showAttachmentPanel =
                                                    !floatContext.showAttachmentPanel
                                        }
                                        // 检查点击是否在面板外部区域
                                        else if (offset.y < screenHeight - panelHeight) {
                                            // 点击面板外部区域，关闭面板
                                            floatContext.showAttachmentPanel = false
                                        }
                                        // 点击在面板内部区域，不做处理
                                    }
                                }
        ) {
            // 使用自定义的悬浮窗专用附件面板
            FloatingAttachmentPanel(
                    visible = floatContext.showAttachmentPanel,
                    onAttachScreenContent = {
                        floatContext.coroutineScope.launch {
                            // 屏幕内容附件 - 在service层处理
                            floatContext.onAttachmentRequest?.invoke("screen_capture")
                            // 允许附件面板关闭，但稍后再刷新附件列表
                            delay(500) // 给Service一点时间处理附件
                            // 保持附件面板关闭状态，但内容已更新
                            floatContext.showAttachmentPanel = false
                        }
                    },
                    onAttachNotifications = {
                        floatContext.coroutineScope.launch {
                            // 通知附件 - 在service层处理
                            floatContext.onAttachmentRequest?.invoke("notifications_capture")
                            // 允许附件面板关闭，但稍后再刷新附件列表
                            delay(500) // 给Service一点时间处理附件
                            // 保持附件面板关闭状态，但内容已更新
                            floatContext.showAttachmentPanel = false
                        }
                    },
                    onAttachLocation = {
                        floatContext.coroutineScope.launch {
                            // 位置附件 - 在service层处理
                            floatContext.onAttachmentRequest?.invoke("location_capture")
                            // 允许附件面板关闭，但稍后再刷新附件列表
                            delay(500) // 给Service一点时间处理附件
                            // 保持附件面板关闭状态，但内容已更新
                            floatContext.showAttachmentPanel = false
                        }
                    },
                    onAttachProblemMemory = {
                        floatContext.coroutineScope.launch {
                            // 问题记忆附件 - 在service层处理
                            floatContext.onAttachmentRequest?.invoke("problem_memory")
                            // 允许附件面板关闭，但稍后再刷新附件列表
                            delay(500) // 给Service一点时间处理附件
                            // 保持附件面板关闭状态，但内容已更新
                            floatContext.showAttachmentPanel = false
                        }
                    },
                    onDismiss = { floatContext.showAttachmentPanel = false }
            )
        }
    }
}
