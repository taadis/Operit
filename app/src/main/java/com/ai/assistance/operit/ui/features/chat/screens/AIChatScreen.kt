package com.ai.assistance.operit.ui.features.chat.screens

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.components.ErrorDialog
import com.ai.assistance.operit.ui.features.chat.components.*
import com.ai.assistance.operit.ui.features.chat.util.ConfigurationStateHolder
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModelFactory
import com.ai.assistance.operit.ui.main.screens.GestureStateHolder
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
        padding: PaddingValues,
        viewModel: ChatViewModel? = null,
        isFloatingMode: Boolean = false,
        onLoading: (Boolean) -> Unit = {},
        onError: (String) -> Unit = {},
        hasBackgroundImage: Boolean = false,
        onNavigateToTokenConfig: () -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
        onGestureConsumed: (Boolean) -> Unit = {}
) {
        val context = LocalContext.current
        val density = LocalDensity.current

        // Initialize ViewModel without using viewModel() function
        val factory = ChatViewModelFactory(context)
        val actualViewModel = viewModel ?: remember { factory.create(ChatViewModel::class.java) }

        // Get background image state
        val preferencesManager = remember { UserPreferencesManager(context) }
        val useBackgroundImage by
                preferencesManager.useBackgroundImage.collectAsState(initial = false)
        val backgroundImageUri by
                preferencesManager.backgroundImageUri.collectAsState(initial = null)
        val hasBackgroundImage = useBackgroundImage && backgroundImageUri != null

        // 添加编辑按钮和编辑状态
        val isEditMode = remember { mutableStateOf(false) }
        val editingMessageIndex = remember { mutableStateOf<Int?>(null) }
        val editingMessageContent = remember { mutableStateOf("") }

        // Collect state from ViewModel
        val apiKey by actualViewModel.apiKey.collectAsState()
        val isConfigured by actualViewModel.isConfigured.collectAsState()
        val chatHistory by actualViewModel.chatHistory.collectAsState()
        val userMessage by actualViewModel.userMessage.collectAsState()
        val isLoading by actualViewModel.isLoading.collectAsState()
        val errorMessage by actualViewModel.errorMessage.collectAsState()
        val toolProgress by actualViewModel.toolProgress.collectAsState()
        val isProcessingInput by actualViewModel.isProcessingInput.collectAsState()
        val inputProcessingMessage by actualViewModel.inputProcessingMessage.collectAsState()
        val planItems by actualViewModel.planItems.collectAsState()
        val enableAiPlanning by actualViewModel.enableAiPlanning.collectAsState()
        val showChatHistorySelector by actualViewModel.showChatHistorySelector.collectAsState()
        val chatHistories by actualViewModel.chatHistories.collectAsState()
        val currentChatId by actualViewModel.currentChatId.collectAsState()
        val popupMessage by actualViewModel.popupMessage.collectAsState()
        val attachments by actualViewModel.attachments.collectAsState()
        // 收集附件面板状态
        val attachmentPanelState by actualViewModel.attachmentPanelState.collectAsState()

        // 添加WebView刷新相关状态
        val webViewNeedsRefresh by actualViewModel.webViewNeedsRefresh.collectAsState()

        // Floating window mode state
        val isFloatingMode by actualViewModel.isFloatingMode.collectAsState()
        val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }

        // UI state
        val listState = rememberLazyListState()
        val focusManager = LocalFocusManager.current
        val coroutineScope = rememberCoroutineScope()

        // 确保每次应用启动时正确处理配置界面的显示逻辑
        LaunchedEffect(apiKey) {
                // 只有当apiKey有效值时才执行逻辑，防止初始化阶段的不正确判断
                if (apiKey.isNotBlank()) {
                        // 如果使用的是自定义配置，标记为已确认，不显示配置界面
                        if (apiKey != ApiPreferences.DEFAULT_API_KEY) {
                                ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                        }
                }
        }

        // Modern chat UI colors - Cursor风格
        val backgroundColor =
                if (hasBackgroundImage) Color.Transparent else MaterialTheme.colorScheme.background
        val userMessageColor = MaterialTheme.colorScheme.primaryContainer
        val aiMessageColor = MaterialTheme.colorScheme.surface
        val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
        val aiTextColor = MaterialTheme.colorScheme.onSurface
        val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
        val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

        // 只保留两个简单状态
        var autoScrollToBottom by remember { mutableStateOf(true) } // 是否自动滚动到底部
        var showScrollButton by remember { mutableStateOf(false) } // 是否显示滚动按钮
        // 添加一个防抖动标记，防止按钮频繁闪烁
        var isScrollStateChanging by remember { mutableStateOf(false) }
        // 跟踪上一次的滚动位置
        var lastScrollOffset by remember { mutableStateOf(0) }

        // 防抖动效果
        LaunchedEffect(isScrollStateChanging) {
                if (isScrollStateChanging) {
                        delay(300) // 短暂延迟后重置状态
                        isScrollStateChanging = false
                }
        }

        // 更简单直接的滚动状态监听 - 只监听用户主动向上滚动
        LaunchedEffect(Unit) {
                snapshotFlow {
                        Pair(listState.firstVisibleItemScrollOffset, listState.isScrollInProgress)
                }
                        .collect { (currentOffset, isScrolling) ->
                                // 只在用户主动滚动时判断
                                if (isScrolling && !isScrollStateChanging) {
                                        // 检测是否是向上滚动(手指向下滑)
                                        val isScrollingUp = currentOffset < lastScrollOffset

                                        // 更新上次滚动位置
                                        lastScrollOffset = currentOffset

                                        // 如果用户向上滚动，禁用自动滚动并显示按钮
                                        if (!isScrollingUp) {
                                                if (!showScrollButton) {
                                                        isScrollStateChanging = true
                                                        showScrollButton = true
                                                        autoScrollToBottom = false
                                                }
                                        }
                                }
                        }
        }

        // 监听用户滚动到底部的情况
        LaunchedEffect(Unit) {
                snapshotFlow { !listState.canScrollForward }.collect { isAtBottom ->
                        if (isAtBottom && !isScrollStateChanging && showScrollButton) {
                                // 用户手动滚动到底部时，重新启用自动滚动并隐藏按钮
                                isScrollStateChanging = true
                                showScrollButton = false
                                autoScrollToBottom = true
                        }
                }
        }

        // 内容变化时的自动滚动 - 不使用size或index
        LaunchedEffect(chatHistory) {
                if (autoScrollToBottom && chatHistory.isNotEmpty()) {
                        delay(50) // 短暂延迟确保布局完成
                        try {
                                // 直接使用极大值滚动，不关心具体位置
                                listState.dispatchRawDelta(100000f)
                        } catch (e: Exception) {
                                Log.e("AIChatScreen", "自动滚动失败", e)
                        }
                }
        }

        // 内容追加的自动滚动 - 不依赖于index或size
        LaunchedEffect(chatHistory.lastOrNull()?.content) {
                if (autoScrollToBottom && chatHistory.isNotEmpty()) {
                        delay(10)
                        try {
                                // 直接使用极大值滚动，不关心具体位置
                                listState.dispatchRawDelta(100000f)
                        } catch (e: Exception) {
                                Log.e("AIChatScreen", "内容追加滚动失败", e)
                        }
                }
        }

        // Launch floating window service when floating mode is enabled
        LaunchedEffect(isFloatingMode, chatHistory) {
                if (isFloatingMode && canDrawOverlays.value) {
                        try {
                                // Start floating chat service
                                val intent = Intent(context, FloatingChatService::class.java)

                                // Filter out "think" messages which are not needed in the floating
                                // window
                                val filteredMessages = chatHistory.filter { it.sender != "think" }

                                // Convert to array of parcelables if needed
                                val chatMessagesArray = filteredMessages.toTypedArray()
                                intent.putExtra("CHAT_MESSAGES", chatMessagesArray)

                                context.startService(intent)
                                Log.d(
                                        "AIChatScreen",
                                        "Started floating window service with ${filteredMessages.size} messages"
                                )

                                // Update the messages in the service when chatHistory changes
                                actualViewModel.updateFloatingWindowMessages(filteredMessages)
                        } catch (e: Exception) {
                                Log.e("AIChatScreen", "Error starting floating service", e)
                                actualViewModel
                                        .toggleFloatingMode() // Turn off floating mode if it fails
                                android.widget.Toast.makeText(
                                                context,
                                                "启动悬浮窗失败，请确保已授予悬浮窗权限",
                                                android.widget.Toast.LENGTH_SHORT
                                        )
                                        .show()
                        }
                } else if (!isFloatingMode) {
                        // Stop floating chat service when floating mode is disabled
                        context.stopService(Intent(context, FloatingChatService::class.java))
                }
        }

        // 移除原有的 snackbar 错误处理
        val snackbarHostState = remember { SnackbarHostState() }

        // 用新的错误弹窗替换原有的错误显示逻辑
        errorMessage?.let { message ->
                ErrorDialog(errorMessage = message, onDismiss = { actualViewModel.clearError() })
        }

        // 处理toast事件 (保留)
        val toastEvent by actualViewModel.toastEvent.collectAsState()

        toastEvent?.let { message ->
                LaunchedEffect(message) {
                        android.widget.Toast.makeText(
                                        context,
                                        message,
                                        android.widget.Toast.LENGTH_SHORT
                                )
                                .show()
                        actualViewModel.clearToastEvent()
                }
        }

        // Save chat on app exit
        DisposableEffect(Unit) {
                onDispose {
                        // This is handled by the ViewModel
                }
        }

        // Add overflow menu items
        val overflowMenuItems =
                listOf(
                        Triple("切换思考显示", "toggle_thinking") {
                                actualViewModel.toggleShowThinking()
                        },
                        Triple("切换记忆优化", "toggle_memory_optimization") {
                                actualViewModel.toggleMemoryOptimization()
                        },
                        Triple("切换AI计划模式", "toggle_ai_planning") {
                                actualViewModel.toggleAiPlanning()
                        },
                        Triple("清空聊天记录", "clear_chat") { actualViewModel.clearCurrentChat() },
                        Triple("管理历史记录", "manage_history") {
                                actualViewModel.showChatHistorySelector(true)
                        }
                )

        // 判断是否有默认配置可用
        val hasDefaultConfig =
                apiKey.isNotBlank()

        // 判断是否正在使用默认配置
        val isUsingDefaultConfig = actualViewModel.isUsingDefaultConfig()

        // 只有在使用默认配置且用户尚未在当前会话中确认使用默认配置时才显示配置界面
        val shouldShowConfig =
                isUsingDefaultConfig && !ConfigurationStateHolder.hasConfirmedDefaultInSession

        // 添加手势状态
        var chatScreenGestureConsumed by remember { mutableStateOf(false) }

        // 添加累计滑动距离变量
        var currentDrag by remember { mutableStateOf(0f) }
        var verticalDrag by remember { mutableStateOf(0f) }
        val dragThreshold = 40f // 与PhoneLayout保持一致

        // 收集WebView显示状态
        val showWebView by actualViewModel.showWebView.collectAsState()

        // 当手势状态改变时，通知父组件
        LaunchedEffect(chatScreenGestureConsumed, showWebView) {
                // 当WebView显示时，设置手势已消费状态为true，防止侧边栏滑出
                val finalGestureState = chatScreenGestureConsumed || showWebView
                // 同时更新全局状态持有者，确保PhoneLayout能够访问到状态
                GestureStateHolder.isChatScreenGestureConsumed = finalGestureState
                onGestureConsumed(finalGestureState)
        }

        Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                // Add WindowInsets to modify the entire screen when keyboard appears
                contentWindowInsets = WindowInsets.ime,
                bottomBar = {
                        // 只在不显示配置界面时显示底部输入框
                        if (!shouldShowConfig) {
                                Column {
                                        // 添加优化和计划模式开关到输入框上方
                                        ChatSettingsBar(
                                                actualViewModel = actualViewModel,
                                                memoryOptimization =
                                                        actualViewModel.memoryOptimization
                                                                .collectAsState()
                                                                .value,
                                                masterPermissionLevel =
                                                        actualViewModel.masterPermissionLevel
                                                                .collectAsState()
                                                                .value,
                                                enableAiPlanning = enableAiPlanning
                                        )

                                        // 原有输入框区域
                                        ChatInputSection(
                                                userMessage = userMessage,
                                                onUserMessageChange = {
                                                        actualViewModel.updateUserMessage(it)
                                                },
                                                onSendMessage = {
                                                        actualViewModel.sendUserMessage()
                                                        // 在发送消息后重置附件面板状态
                                                        actualViewModel.resetAttachmentPanelState()
                                                },
                                                onCancelMessage = {
                                                        actualViewModel.cancelCurrentMessage()
                                                },
                                                isLoading = isLoading,
                                                isProcessingInput = isProcessingInput,
                                                inputProcessingMessage = inputProcessingMessage,
                                                allowTextInputWhileProcessing = true,
                                                onAttachmentRequest = { filePath ->
                                                        // 处理附件 - 现在使用文件路径而不是Uri
                                                        actualViewModel.handleAttachment(filePath)
                                                },
                                                attachments = attachments,
                                                onRemoveAttachment = { filePath ->
                                                        // 删除附件 - 现在使用文件路径而不是Uri
                                                        actualViewModel.removeAttachment(filePath)
                                                },
                                                onInsertAttachment = { attachment: AttachmentInfo ->
                                                        // 在光标位置插入附件引用
                                                        actualViewModel.insertAttachmentReference(
                                                                attachment
                                                        )
                                                },
                                                onAttachScreenContent = {
                                                        // 添加屏幕内容附件
                                                        actualViewModel.captureScreenContent()
                                                },
                                                onAttachNotifications = {
                                                        // 添加当前通知附件
                                                        actualViewModel.captureNotifications()
                                                },
                                                onAttachLocation = {
                                                        // 添加当前位置附件
                                                        actualViewModel.captureLocation()
                                                },
                                                onAttachProblemMemory = { content, filename ->
                                                        // 添加问题记忆附件
                                                        actualViewModel.attachProblemMemory(
                                                                content,
                                                                filename
                                                        )
                                                },
                                                hasBackgroundImage = hasBackgroundImage,
                                                // 传递附件面板状态
                                                externalAttachmentPanelState = attachmentPanelState,
                                                onAttachmentPanelStateChange = { newState ->
                                                        actualViewModel.updateAttachmentPanelState(
                                                                newState
                                                        )
                                                }
                                        )
                                }
                        }
                },
                floatingActionButton = {
                        // Remove the existing FAB since we now have the button in the header
                }
        ) { paddingValues ->
                // 根据前面的逻辑条件决定是否显示配置界面
                if (shouldShowConfig) {
                        ConfigurationScreen(
                                apiEndpoint = "",
                                apiKey = apiKey,
                                modelName = "",
                                onApiEndpointChange = { },
                                onApiKeyChange = { actualViewModel.updateApiKey(it) },
                                onModelNameChange = { },
                                onSaveConfig = {
                                        actualViewModel.saveApiSettings()
                                        // 保存配置后导航到聊天界面
                                        ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                                },
                                onError = { error -> actualViewModel.showErrorMessage(error) },
                                coroutineScope = coroutineScope,
                                // 新增：使用默认配置的回调
                                onUseDefault = {
                                        actualViewModel.useDefaultConfig()
                                        // 确认使用默认配置后导航到聊天界面
                                        ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                                },
                                // 标识是否在使用默认配置
                                isUsingDefault = isUsingDefaultConfig,
                                // 添加导航到聊天界面的回调
                                onNavigateToChat = {
                                        // 当用户设置了自己的配置后保存
                                        actualViewModel.saveApiSettings()
                                        // 确认后导航到聊天界面
                                        ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                                },
                                // 添加导航到Token配置页面的回调
                                onNavigateToTokenConfig = onNavigateToTokenConfig,
                                // 添加导航到Settings页面的回调
                                onNavigateToSettings = onNavigateToSettings
                        )
                } else {
                        // 使用提取出来的聊天内容组件
                        ChatScreenContent(
                                paddingValues = paddingValues,
                                actualViewModel = actualViewModel,
                                showChatHistorySelector = showChatHistorySelector,
                                chatHistory = chatHistory,
                                listState = listState,
                                planItems = planItems,
                                enableAiPlanning = enableAiPlanning,
                                toolProgress = toolProgress,
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
                                isEditMode = isEditMode,
                                editingMessageIndex = editingMessageIndex,
                                editingMessageContent = editingMessageContent,
                                chatScreenGestureConsumed = chatScreenGestureConsumed,
                                onChatScreenGestureConsumed = { chatScreenGestureConsumed = it },
                                currentDrag = currentDrag,
                                onCurrentDragChange = { currentDrag = it },
                                verticalDrag = verticalDrag,
                                onVerticalDragChange = { verticalDrag = it },
                                dragThreshold = dragThreshold,
                                showScrollButton = showScrollButton,
                                onShowScrollButtonChange = { showScrollButton = it },
                                autoScrollToBottom = autoScrollToBottom,
                                onAutoScrollToBottomChange = { autoScrollToBottom = it },
                                coroutineScope = coroutineScope,
                                chatHistories = chatHistories,
                                currentChatId = currentChatId ?: "",
                                // 添加WebView刷新相关参数
                                webViewNeedsRefresh = webViewNeedsRefresh,
                                onWebViewRefreshed = { actualViewModel.resetWebViewRefreshState() }
                        )
                }
        }

        // Show popup message dialog when needed
        popupMessage?.let { message ->
                AlertDialog(
                        onDismissRequest = { actualViewModel.clearPopupMessage() },
                        title = { Text("提示") },
                        text = { Text(message ?: "") },
                        confirmButton = {
                                TextButton(onClick = { actualViewModel.clearPopupMessage() }) {
                                        Text("确定")
                                }
                        }
                )
        }

        // Check for overlay permission on resume
        LaunchedEffect(Unit) {
                canDrawOverlays.value = Settings.canDrawOverlays(context)

                // If floating mode is on but no permission, turn it off
                if (isFloatingMode && !canDrawOverlays.value) {
                        actualViewModel.toggleFloatingMode()
                        android.widget.Toast.makeText(
                                        context,
                                        "未获得悬浮窗权限，已关闭悬浮窗模式",
                                        android.widget.Toast.LENGTH_SHORT
                                )
                                .show()
                }
        }
}
