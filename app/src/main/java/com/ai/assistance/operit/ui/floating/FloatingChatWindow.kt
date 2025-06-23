package com.ai.assistance.operit.ui.floating

import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.floating.ui.ball.FloatingChatBallMode
import com.ai.assistance.operit.ui.floating.ui.window.FloatingChatWindowMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 悬浮聊天窗口的主要UI组件 - 重构版
 *
 * @param messages 要显示的聊天消息列表
 * @param width 窗口宽度
 * @param height 窗口高度
 * @param onClose 关闭窗口的回调
 * @param onResize 调整窗口大小的回调
 * @param isBallMode 是否为球模式
 * @param ballSize 球的大小
 * @param windowScale 窗口缩放比例
 * @param onScaleChange 缩放比例变化的回调
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
        windowScale: Float = 1.0f,
        onScaleChange: (Float) -> Unit = {},
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
    val floatContext = rememberFloatContext(
        messages = messages,
        width = width,
        height = height,
        onClose = onClose,
        onResize = onResize,
        ballSize = ballSize,
        windowScale = windowScale,
        onScaleChange = onScaleChange,
        onToggleBallMode = onToggleBallMode,
        onMove = onMove,
        snapToEdge = snapToEdge,
        isAtEdge = isAtEdge,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        currentX = currentX,
        currentY = currentY,
        saveWindowState = saveWindowState,
        onSendMessage = onSendMessage,
        onAttachmentRequest = onAttachmentRequest,
        attachments = attachments,
        onRemoveAttachment = onRemoveAttachment,
        onInputFocusRequest = onInputFocusRequest
    )
    
    // 将窗口缩放限制在合理范围内 - 已通过回调和状态源头处理，不再需要
    // LaunchedEffect(initialWindowScale) {
    //     floatContext.windowScale = initialWindowScale.coerceIn(0.5f, 1.0f)
    // }

    // 监听输入状态变化
    LaunchedEffect(floatContext.showInputDialog) {
        // 通知服务需要切换焦点模式
        floatContext.onInputFocusRequest?.invoke(floatContext.showInputDialog)

        // 如果隐藏输入框，清空消息
        if (!floatContext.showInputDialog) {
            floatContext.userMessage = ""
        }
    }

    // 根据isBallMode参数渲染对应界面
    if (isBallMode) {
        FloatingChatBallMode(floatContext = floatContext)
    } else {
        FloatingChatWindowMode(floatContext = floatContext)
    }
}
