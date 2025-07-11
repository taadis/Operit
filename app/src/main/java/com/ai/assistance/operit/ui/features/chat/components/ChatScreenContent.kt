package com.ai.assistance.operit.ui.features.chat.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ChatScreenContent(
        paddingValues: PaddingValues,
        actualViewModel: ChatViewModel,
        showChatHistorySelector: Boolean,
        chatHistory: List<ChatMessage>,
        planItems: List<PlanItem>,
        enableAiPlanning: Boolean,
        isLoading: Boolean,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        hasBackgroundImage: Boolean,
        isEditMode: MutableState<Boolean>,
        editingMessageIndex: MutableState<Int?>,
        editingMessageContent: MutableState<String>,
        chatScreenGestureConsumed: Boolean,
        onChatScreenGestureConsumed: (Boolean) -> Unit,
        currentDrag: Float,
        onCurrentDragChange: (Float) -> Unit,
        verticalDrag: Float,
        onVerticalDragChange: (Float) -> Unit,
        dragThreshold: Float,
        scrollState: ScrollState,
        showScrollButton: Boolean,
        onShowScrollButtonChange: (Boolean) -> Unit,
        autoScrollToBottom: Boolean,
        onAutoScrollToBottomChange: (Boolean) -> Unit,
        coroutineScope: CoroutineScope,
        chatHistories: List<ChatHistory>,
        currentChatId: String
) {
    // 获取WebView状态
    val showWebView = actualViewModel.showWebView.collectAsState().value
    val currentChat = chatHistories.find { it.id == currentChatId }

    // 导出相关状态
    val context = LocalContext.current
    var showExportPlatformDialog by remember { mutableStateOf(false) }
    var showAndroidExportDialog by remember { mutableStateOf(false) }
    var showWindowsExportDialog by remember { mutableStateOf(false) }
    var showExportProgressDialog by remember { mutableStateOf(false) }
    var showExportCompleteDialog by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportStatus by remember { mutableStateOf("") }
    var exportSuccess by remember { mutableStateOf(false) }
    var exportFilePath by remember { mutableStateOf<String?>(null) }
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }
    var webContentDir by remember { mutableStateOf<File?>(null) }

    val onSelectMessageToEditCallback = remember(editingMessageIndex, editingMessageContent) {
        { index: Int, message: ChatMessage ->
            editingMessageIndex.value = index
            editingMessageContent.value = message.content
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // 主聊天区域（包括顶部工具栏），确保它一直可见
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                            onDragStart = {
                                                onChatScreenGestureConsumed(false)
                                                onCurrentDragChange(0f)
                                                onVerticalDragChange(0f)
                                            },
                                            onDragEnd = {
                                                onCurrentDragChange(0f)
                                                onVerticalDragChange(0f)
                                                onChatScreenGestureConsumed(false)
                                            },
                                            onDragCancel = {
                                                onCurrentDragChange(0f)
                                                onVerticalDragChange(0f)
                                                onChatScreenGestureConsumed(false)
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                val newDrag = currentDrag + dragAmount
                                                onCurrentDragChange(newDrag)
                                                val dragRight = dragAmount > 0
                                                if (!showChatHistorySelector &&
                                                                dragRight &&
                                                                newDrag > dragThreshold &&
                                                                Math.abs(newDrag) >
                                                                        Math.abs(verticalDrag)
                                                ) {
                                                    actualViewModel.showChatHistorySelector(true)
                                                    onChatScreenGestureConsumed(true)
                                                    change.consume()
                                                }
                                                if (dragAmount < 0 &&
                                                                showChatHistorySelector &&
                                                                newDrag < -dragThreshold
                                                ) {
                                                    actualViewModel.showChatHistorySelector(false)
                                                    onChatScreenGestureConsumed(true)
                                                    change.consume()
                                                }
                                            }
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { _, dragAmount ->
                                        onVerticalDragChange(verticalDrag + dragAmount)
                                    }
                                }
        ) {
            // 聊天区域
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部工具栏 - 整合聊天历史按钮和统计信息 - 始终显示在顶部
                ChatScreenHeader(
                        actualViewModel = actualViewModel,
                        showChatHistorySelector = showChatHistorySelector,
                        chatHistories = chatHistories,
                        currentChatId = currentChatId,
                        isEditMode = isEditMode
                )

                // 聊天对话区域
                Box(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
                    // 始终显示聊天区域，不再使用if条件判断
                    ChatArea(
                            chatHistory = chatHistory,
                            scrollState = scrollState,
                            planItems = planItems,
                            enablePlanning = enableAiPlanning,
                            isLoading = isLoading,
                            userMessageColor = userMessageColor,
                            aiMessageColor = aiMessageColor,
                            userTextColor = userTextColor,
                            aiTextColor = aiTextColor,
                            systemMessageColor = systemMessageColor,
                            systemTextColor = systemTextColor,
                            thinkingBackgroundColor = thinkingBackgroundColor,
                            thinkingTextColor = thinkingTextColor,
                            hasBackgroundImage = hasBackgroundImage,
                            modifier = Modifier.fillMaxSize(),
                            isEditMode = isEditMode.value,
                            onSelectMessageToEdit = onSelectMessageToEditCallback
                    )

                    // 编辑模式下的操作面板
                    if (isEditMode.value && editingMessageIndex.value != null) {
                        MessageEditPanel(
                                editingMessageContent = editingMessageContent,
                                onCancel = {
                                    editingMessageIndex.value = null
                                    editingMessageContent.value = ""
                                },
                                onSave = {
                                    val index = editingMessageIndex.value
                                    if (index != null && index < chatHistory.size) {
                                        val editedMessage =
                                                chatHistory[index].copy(
                                                        content = editingMessageContent.value
                                                )
                                        actualViewModel.updateMessage(index, editedMessage)

                                        // 重置编辑状态
                                        editingMessageIndex.value = null
                                        editingMessageContent.value = ""
                                        isEditMode.value = false
                                    }
                                },
                                onResend = {
                                    val index = editingMessageIndex.value
                                    if (index != null && index < chatHistory.size) {
                                        actualViewModel.rewindAndResendMessage(
                                                index,
                                                editingMessageContent.value
                                        )

                                        // 重置编辑状态
                                        editingMessageIndex.value = null
                                        editingMessageContent.value = ""
                                        isEditMode.value = false
                                    }
                                }
                        )
                    }

                    // 滚动到底部按钮 - 仅在可见区域需要时显示，不再受WebView显示状态影响
                    if (showScrollButton) {
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.BottomEnd
                        ) {
                            ScrollToBottomButton(
                                    onClick = {
                                        // 点击按钮：启用自动滚动，隐藏按钮，并立即滚动到底部
                                        onAutoScrollToBottomChange(true)
                                        onShowScrollButtonChange(false)

                                        coroutineScope.launch {
                                            if (chatHistory.isNotEmpty()) {
                                                try {
                                                    // 不关心index，直接尝试滚动到底部
                                                    // 使用最大可能的滚动量
                                                    scrollState.animateScrollTo(
                                                            scrollState.maxValue
                                                    )
                                                } catch (e: Exception) {
                                                    Log.e("ChatScreenContent", "滚动到底部失败", e)
                                                }
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        // 历史选择器作为浮动层，使用AnimatedVisibility保持动画效果
        AnimatedVisibility(
                visible = showChatHistorySelector,
                enter =
                        slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) +
                                fadeIn(animationSpec = tween(300)),
                exit =
                        slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) +
                                fadeOut(animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.TopStart)
        ) {
            ChatHistorySelectorPanel(
                    actualViewModel = actualViewModel,
                    chatHistories = chatHistories,
                    currentChatId = currentChatId,
                    showChatHistorySelector = showChatHistorySelector
            )
        }

        // 导出平台选择对话框
        if (showExportPlatformDialog) {
            ExportPlatformDialog(
                    onDismiss = { showExportPlatformDialog = false },
                    onSelectAndroid = { showAndroidExportDialog = true },
                    onSelectWindows = { showWindowsExportDialog = true }
            )
        }

        // Android导出设置对话框
        if (showAndroidExportDialog && webContentDir != null) {
            AndroidExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showAndroidExportDialog = false },
                    onExport = { packageName, appName, iconUri ->
                        showAndroidExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = "开始导出..."

                        // 启动导出过程
                        coroutineScope.launch {
                            exportAndroidApp(
                                    context = context,
                                    packageName = packageName,
                                    appName = appName,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // Windows导出设置对话框
        if (showWindowsExportDialog && webContentDir != null) {
            WindowsExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showWindowsExportDialog = false },
                    onExport = { appName, iconUri ->
                        showWindowsExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = "开始导出..."

                        // 启动导出过程
                        coroutineScope.launch {
                            exportWindowsApp(
                                    context = context,
                                    appName = appName,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // 导出进度对话框
        if (showExportProgressDialog) {
            ExportProgressDialog(
                    progress = exportProgress,
                    status = exportStatus,
                    onCancel = { showExportProgressDialog = false }
            )
        }

        // 导出完成对话框
        if (showExportCompleteDialog) {
            ExportCompleteDialog(
                    success = exportSuccess,
                    filePath = exportFilePath,
                    errorMessage = exportErrorMessage,
                    onDismiss = { showExportCompleteDialog = false },
                    onOpenFile = { filePath ->
                        try {
                            val file = File(filePath)
                            val fileUri =
                                    FileProvider.getUriForFile(
                                            context,
                                            context.applicationContext.packageName +
                                                    ".fileprovider",
                                            file
                                    )
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(
                                    fileUri,
                                    "application/vnd.android.package-archive"
                            )
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Log.e("ChatScreenContent", "无法打开文件: $filePath", e)
                        } catch (e: Exception) {
                            Log.e("ChatScreenContent", "文件操作错误: ${e.message}", e)
                        }
                    }
            )
        }
    }
}

@Composable
fun ScrollToBottomButton(onClick: () -> Unit) {
    SmallFloatingActionButton(
            onClick = onClick,
            modifier = Modifier.padding(end = 16.dp),
            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSecondary
    ) {
        Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "滚动到底部",
                modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ChatHistorySelectorPanel(
        actualViewModel: ChatViewModel,
        chatHistories: List<ChatHistory>,
        currentChatId: String,
        showChatHistorySelector: Boolean
) {
    // 添加一个覆盖整个屏幕的半透明点击区域，用于关闭历史选择器
    Box(modifier = Modifier.fillMaxSize()) {
        // 透明遮罩层，点击右侧空白处关闭历史选择器 - 修改为不拦截滑动事件
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                // 使用pointerInput替代clickable，以便只处理点击事件而不拦截滑动
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        actualViewModel.toggleChatHistorySelector()
                                    }
                                }
                                .background(Color.Black.copy(alpha = 0.1f))
        )

        // 历史选择器面板
        Box(
                modifier =
                        Modifier.width(280.dp)
                                .fillMaxHeight()
                                .background(
                                        color =
                                                MaterialTheme.colorScheme.surface.copy(
                                                        alpha = 0.95f
                                                ),
                                        shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                )
        ) {
            // 直接使用ChatHistorySelector
            ChatHistorySelector(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    onNewChat = {
                        actualViewModel.createNewChat()
                        // 创建新对话后自动收起侧边框
                        actualViewModel.showChatHistorySelector(false)
                    },
                    onSelectChat = { chatId ->
                        actualViewModel.switchChat(chatId)
                        // 切换聊天后也自动收起侧边框
                        actualViewModel.showChatHistorySelector(false)
                    },
                    onDeleteChat = { chatId -> actualViewModel.deleteChatHistory(chatId) },
                    onUpdateChatTitle = { chatId, newTitle ->
                        actualViewModel.updateChatTitle(chatId, newTitle)
                    },
                    onCreateGroup = { groupName -> actualViewModel.createGroup(groupName) },
                    onUpdateChatOrderAndGroup = { reorderedHistories, movedItem, targetGroup ->
                        actualViewModel.updateChatOrderAndGroup(
                                reorderedHistories,
                                movedItem,
                                targetGroup
                        )
                    },
                    onUpdateGroupName = { oldName, newName ->
                        actualViewModel.updateGroupName(oldName, newName)
                    },
                    onDeleteGroup = { groupName, deleteChats ->
                        actualViewModel.deleteGroup(groupName, deleteChats)
                    },
                    chatHistories = chatHistories,
                    currentId = currentChatId
            )

            // 在右侧添加浮动返回按钮
            OutlinedButton(
                    onClick = { actualViewModel.toggleChatHistorySelector() },
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .padding(top = 16.dp, end = 8.dp)
                                    .height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors =
                            ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                            ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                    shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Text("返回", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
