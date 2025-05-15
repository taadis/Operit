package com.ai.assistance.operit.ui.features.chat.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.features.chat.components.ChatArea
import com.ai.assistance.operit.ui.features.chat.components.ChatHeader
import com.ai.assistance.operit.ui.features.chat.components.ChatHistorySelector
import com.ai.assistance.operit.ui.features.chat.components.ChatInputSection
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    onNavigateToTokenConfig: () -> Unit = {} // 添加导航到Token配置页面的回调
) {
    val context = LocalContext.current

    // Initialize ViewModel without using viewModel() function
    val factory = ChatViewModelFactory(context)
    val viewModel = remember { factory.create(ChatViewModel::class.java) }

    // Collect state from ViewModel
    val apiKey by viewModel.apiKey.collectAsState()
    val apiEndpoint by viewModel.apiEndpoint.collectAsState()
    val modelName by viewModel.modelName.collectAsState()
    val isConfigured by viewModel.isConfigured.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val toolProgress by viewModel.toolProgress.collectAsState()
    val isProcessingInput by viewModel.isProcessingInput.collectAsState()
    val inputProcessingMessage by viewModel.inputProcessingMessage.collectAsState()
    val planItems by viewModel.planItems.collectAsState()
    val enableAiPlanning by viewModel.enableAiPlanning.collectAsState()
    val showChatHistorySelector by viewModel.showChatHistorySelector.collectAsState()
    val chatHistories by viewModel.chatHistories.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val popupMessage by viewModel.popupMessage.collectAsState()
    val collapseExecution by viewModel.collapseExecution.collectAsState()
    val attachments by viewModel.attachments.collectAsState()

    // Floating window mode state
    val isFloatingMode by viewModel.isFloatingMode.collectAsState()
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // UI state
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // 使用全局静态变量，确保在整个应用生命周期内保持状态
    // 这样即使组件重组，状态也不会丢失
    // 添加这个变量，用于跟踪该会话是否已经确认使用默认配置

    // 确保每次应用启动时正确处理配置界面的显示逻辑
    LaunchedEffect(apiKey) {
        // 只有当apiKey有效值时才执行逻辑，防止初始化阶段的不正确判断
        if (apiKey.isNotBlank()) {
            // 如果使用的是自定义配置，标记为已确认，不显示配置界面
            if (apiKey != ApiPreferences.DEFAULT_API_KEY) {
                ConfigurationStateHolder.hasConfirmedDefaultInSession = true
            }
            // 注意：对于默认配置，我们不在这里设置hasConfirmedDefaultInSession为false
            // 这样可以保持它的值，实现"只在首次进入时显示一次"的效果
        }
    }

    // Modern chat UI colors - Cursor风格
    val backgroundColor = MaterialTheme.colorScheme.background
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
                viewModel.updateFloatingWindowMessages(filteredMessages)
            } catch (e: Exception) {
                Log.e("AIChatScreen", "Error starting floating service", e)
                viewModel.toggleFloatingMode() // Turn off floating mode if it fails
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

    // Show error in snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            delay(3000)
            viewModel.clearError()
        }
    }

    // Handle toast event
    val toastEvent by viewModel.toastEvent.collectAsState()

    toastEvent?.let { message ->
        LaunchedEffect(message) {
            android.widget.Toast.makeText(
                context,
                message,
                android.widget.Toast.LENGTH_SHORT
            )
                .show()
            viewModel.clearToastEvent()
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
            Triple("切换思考显示", "toggle_thinking") { viewModel.toggleShowThinking() },
            Triple("切换记忆优化", "toggle_memory_optimization") {
                viewModel.toggleMemoryOptimization()
            },
            Triple("切换AI计划模式", "toggle_ai_planning") { viewModel.toggleAiPlanning() },
            Triple("清空聊天记录", "clear_chat") { viewModel.clearCurrentChat() },
            Triple("管理历史记录", "manage_history") {
                viewModel.showChatHistorySelector(true)
            }
        )

    // 判断是否有默认配置可用
    val hasDefaultConfig =
        apiEndpoint.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()

    // 判断是否正在使用默认配置
    val isUsingDefaultConfig = apiKey == ApiPreferences.DEFAULT_API_KEY

    // 只有在使用默认配置且用户尚未在当前会话中确认使用默认配置时才显示配置界面
    val shouldShowConfig =
        isUsingDefaultConfig && !ConfigurationStateHolder.hasConfirmedDefaultInSession

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // 只在不显示配置界面时显示底部输入框
            if (!shouldShowConfig) {
                Column {
                    // 添加优化和计划模式开关到输入框上方
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                color =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                                    .copy(
                                        alpha =
                                        0.1f
                                    )
                            )
                            .padding(
                                horizontal = 16.dp,
                                vertical = 4.dp
                            ),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val memoryOptimization by
                        viewModel.memoryOptimization
                            .collectAsState()
                        val masterPermissionLevel by
                        viewModel.masterPermissionLevel
                            .collectAsState()

                        // 自动批准开关 - 左侧第一个开关
                        Row(
                            modifier =
                            Modifier
                                .background(
                                    color =
                                    if (masterPermissionLevel ==
                                        PermissionLevel
                                            .ALLOW
                                    )
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                            .copy(
                                                alpha =
                                                0.2f
                                            )
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .surface,
                                    shape =
                                    RoundedCornerShape(
                                        4.dp
                                    )
                                )
                                .padding(
                                    horizontal = 4.dp,
                                    vertical = 2.dp
                                )
                                .clickable {
                                    viewModel
                                        .toggleMasterPermission()
                                },
                            verticalAlignment =
                            Alignment.CenterVertically,
                            horizontalArrangement =
                            Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "自动批准:",
                                style =
                                MaterialTheme.typography
                                    .labelSmall,
                                color =
                                MaterialTheme.colorScheme
                                    .onSurface.copy(
                                        alpha = 0.7f
                                    )
                            )
                            Text(
                                text =
                                if (masterPermissionLevel ==
                                    PermissionLevel
                                        .ALLOW
                                )
                                    "已开启"
                                else "询问",
                                style =
                                MaterialTheme.typography
                                    .labelSmall.copy(
                                        fontWeight =
                                        androidx.compose
                                            .ui
                                            .text
                                            .font
                                            .FontWeight
                                            .Bold
                                    ),
                                color =
                                if (masterPermissionLevel ==
                                    PermissionLevel
                                        .ALLOW
                                )
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // 记忆优化开关 - 添加背景色效果，与计划模式一致
                        Row(
                            modifier =
                            Modifier
                                .background(
                                    color =
                                    if (memoryOptimization
                                    )
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                            .copy(
                                                alpha =
                                                0.2f
                                            )
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .surface,
                                    shape =
                                    RoundedCornerShape(
                                        4.dp
                                    )
                                )
                                .padding(
                                    horizontal = 4.dp,
                                    vertical = 2.dp
                                )
                                .clickable {
                                    viewModel
                                        .toggleMemoryOptimization()
                                },
                            verticalAlignment =
                            Alignment.CenterVertically,
                            horizontalArrangement =
                            Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "记忆优化:",
                                style =
                                MaterialTheme.typography
                                    .labelSmall,
                                color =
                                MaterialTheme.colorScheme
                                    .onSurface.copy(
                                        alpha = 0.7f
                                    )
                            )
                            Text(
                                text =
                                if (memoryOptimization)
                                    "已开启"
                                else "已关闭",
                                style =
                                MaterialTheme.typography
                                    .labelSmall.copy(
                                        fontWeight =
                                        androidx.compose
                                            .ui
                                            .text
                                            .font
                                            .FontWeight
                                            .Bold
                                    ),
                                color =
                                if (memoryOptimization)
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // AI计划模式开关 - 更详细的文本
                        Row(
                            modifier =
                            Modifier
                                .background(
                                    color =
                                    if (enableAiPlanning
                                    )
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                            .copy(
                                                alpha =
                                                0.2f
                                            )
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .surface,
                                    shape =
                                    RoundedCornerShape(
                                        4.dp
                                    )
                                )
                                .padding(
                                    horizontal = 4.dp,
                                    vertical = 2.dp
                                )
                                .clickable {
                                    viewModel
                                        .toggleAiPlanning()
                                },
                            verticalAlignment =
                            Alignment.CenterVertically,
                            horizontalArrangement =
                            Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "AI计划模式:",
                                style =
                                MaterialTheme.typography
                                    .labelSmall,
                                color =
                                MaterialTheme.colorScheme
                                    .onSurface.copy(
                                        alpha = 0.7f
                                    )
                            )
                            Text(
                                text =
                                if (enableAiPlanning) "已开启"
                                else "已关闭",
                                style =
                                MaterialTheme.typography
                                    .labelSmall.copy(
                                        fontWeight =
                                        androidx.compose
                                            .ui
                                            .text
                                            .font
                                            .FontWeight
                                            .Bold
                                    ),
                                color =
                                if (enableAiPlanning)
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )
                        }
                    }

                    // 原有输入框区域
                    ChatInputSection(
                        userMessage = userMessage,
                        onUserMessageChange = {
                            viewModel.updateUserMessage(it)
                        },
                        onSendMessage = { viewModel.sendUserMessage() },
                        onCancelMessage = {
                            viewModel.cancelCurrentMessage()
                        },
                        isLoading = isLoading,
                        isProcessingInput = isProcessingInput,
                        inputProcessingMessage = inputProcessingMessage,
                        onAttachmentRequest = { filePath ->
                            // 处理附件 - 现在使用文件路径而不是Uri
                            viewModel.handleAttachment(filePath)
                        },
                        attachments = attachments,
                        onRemoveAttachment = { filePath ->
                            // 删除附件 - 现在使用文件路径而不是Uri
                            viewModel.removeAttachment(filePath)
                        },
                        onInsertAttachment = { attachment: AttachmentInfo ->
                            // 在光标位置插入附件引用
                            viewModel.insertAttachmentReference(
                                attachment
                            )
                        },
                        onAttachScreenContent = {
                            // 添加屏幕内容附件
                            viewModel.captureScreenContent()
                        },
                        onAttachNotifications = {
                            // 添加当前通知附件
                            viewModel.captureNotifications()
                        },
                        onAttachLocation = {
                            // 添加当前位置附件
                            viewModel.captureLocation()
                        },
                        onAttachProblemMemory = { content, filename ->
                            // 添加问题记忆附件
                            viewModel.attachProblemMemory(content, filename)
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            // Remove the existing FAB since we now have the button in the header
        }
    ) { paddingValues ->
        // 判断是否有默认配置可用
        val hasDefaultConfig =
            apiEndpoint.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()

        // 判断是否正在使用默认配置
        val isUsingDefaultConfig = apiKey == ApiPreferences.DEFAULT_API_KEY

        // 根据前面的逻辑条件决定是否显示配置界面
        if (shouldShowConfig) {
            ConfigurationScreen(
                apiEndpoint = apiEndpoint,
                apiKey = apiKey,
                modelName = modelName,
                onApiEndpointChange = { viewModel.updateApiEndpoint(it) },
                onApiKeyChange = { viewModel.updateApiKey(it) },
                onModelNameChange = { viewModel.updateModelName(it) },
                onSaveConfig = {
                    viewModel.saveApiSettings()
                    // 保存配置后导航到聊天界面
                    ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                },
                onError = { error -> viewModel.showErrorMessage(error) },
                coroutineScope = coroutineScope,
                // 新增：使用默认配置的回调
                onUseDefault = {
                    viewModel.useDefaultConfig()
                    // 确认使用默认配置后导航到聊天界面
                    ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                },
                // 标识是否在使用默认配置
                isUsingDefault = isUsingDefaultConfig,
                // 添加导航到聊天界面的回调
                onNavigateToChat = {
                    // 当用户设置了自己的配置后保存
                    viewModel.saveApiSettings()
                    // 确认后导航到聊天界面
                    ConfigurationStateHolder.hasConfirmedDefaultInSession = true
                },
                // 添加导航到Token配置页面的回调
                onNavigateToTokenConfig = onNavigateToTokenConfig
            )
        } else {
            // Chat screen
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)) {
                // 主聊天区域（包括顶部工具栏），确保它一直可见
                Column(modifier = Modifier.fillMaxSize()) {
                    // Chat header
                    val currentChatTitle =
                        chatHistories.find { it.id == currentChatId }?.title

                    // 聊天区域
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 顶部工具栏 - 整合聊天历史按钮和统计信息
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme
                                        .colorScheme
                                        .surfaceVariant
                                        .copy(
                                            alpha =
                                            0.2f
                                        )
                                )
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 6.dp
                                )
                        ) {
                            // 左侧：聊天历史按钮
                            ChatHeader(
                                showChatHistorySelector =
                                showChatHistorySelector,
                                onToggleChatHistorySelector = {
                                    viewModel
                                        .toggleChatHistorySelector()
                                },
                                currentChatTitle = currentChatTitle,
                                modifier =
                                Modifier.align(
                                    Alignment
                                        .CenterStart
                                ),
                                isFloatingMode = isFloatingMode,
                                onLaunchFloatingWindow = {
                                    // Check if we can draw
                                    // overlays first
                                    if (!Settings.canDrawOverlays(
                                            context
                                        )
                                    ) {
                                        // Show message to
                                        // user
                                        android.widget.Toast
                                            .makeText(
                                                context,
                                                "需要悬浮窗权限。请前往设置授予权限",
                                                android.widget
                                                    .Toast
                                                    .LENGTH_SHORT
                                            )
                                            .show()

                                        // Launch settings
                                        // to grant
                                        // permission
                                        val intent =
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse(
                                                    "package:${context.packageName}"
                                                )
                                            )
                                        context.startActivity(
                                            intent
                                        )
                                    } else {
                                        // Toggle floating
                                        // mode
                                        viewModel
                                            .toggleFloatingMode()

                                        // 根据当前悬浮窗状态显示不同的提示
                                        val isFloating =
                                            viewModel
                                                .isFloatingMode
                                                .value
                                        val message =
                                            if (isFloating
                                            )
                                                "悬浮窗已开启"
                                            else
                                                "悬浮窗已关闭"
                                        android.widget.Toast
                                            .makeText(
                                                context,
                                                message,
                                                android.widget
                                                    .Toast
                                                    .LENGTH_SHORT
                                            )
                                            .show()
                                    }
                                }
                            )

                            // 右侧：统计信息
                            val contextWindowSize by
                            viewModel.contextWindowSize
                                .collectAsState()
                            val inputTokenCount by
                            viewModel.inputTokenCount
                                .collectAsState()
                            val outputTokenCount by
                            viewModel.outputTokenCount
                                .collectAsState()

                            Row(
                                modifier =
                                Modifier
                                    .align(
                                        Alignment
                                            .CenterEnd
                                    )
                                    .background(
                                        color =
                                        MaterialTheme
                                            .colorScheme
                                            .surface
                                            .copy(
                                                alpha =
                                                0.8f
                                            ),
                                        shape =
                                        RoundedCornerShape(
                                            4.dp
                                        )
                                    )
                                    .padding(
                                        horizontal =
                                        8.dp,
                                        vertical =
                                        4.dp
                                    ),
                                verticalAlignment =
                                Alignment.CenterVertically,
                                horizontalArrangement =
                                Arrangement.spacedBy(6.dp)
                            ) {
                                // 统计项 - 使用水平排列的小标签
                                StatItem(
                                    label = "请求",
                                    value = "$contextWindowSize"
                                )

                                StatItem(
                                    label = "累计入",
                                    value = "$inputTokenCount"
                                )

                                Divider(
                                    modifier =
                                    Modifier
                                        .height(
                                            16.dp
                                        )
                                        .width(
                                            1.dp
                                        ),
                                    color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(
                                            alpha =
                                            0.2f
                                        )
                                )

                                StatItem(
                                    label = "累计出",
                                    value = "$outputTokenCount"
                                )

                                Divider(
                                    modifier =
                                    Modifier
                                        .height(
                                            16.dp
                                        )
                                        .width(
                                            1.dp
                                        ),
                                    color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(
                                            alpha =
                                            0.2f
                                        )
                                )

                                StatItem(
                                    label = "总计",
                                    value =
                                    "${inputTokenCount + outputTokenCount}",
                                    isHighlighted = true
                                )
                            }
                        }

                        // 聊天对话区域
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)) {
                            ChatArea(
                                chatHistory = chatHistory,
                                listState = listState,
                                planItems = planItems,
                                enablePlanning = enableAiPlanning,
                                toolProgress = toolProgress,
                                isLoading = isLoading,
                                userMessageColor = userMessageColor,
                                aiMessageColor = aiMessageColor,
                                userTextColor = userTextColor,
                                aiTextColor = aiTextColor,
                                systemMessageColor =
                                systemMessageColor,
                                systemTextColor = systemTextColor,
                                thinkingBackgroundColor =
                                thinkingBackgroundColor,
                                thinkingTextColor =
                                thinkingTextColor,
                                collapseExecution =
                                collapseExecution,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Scroll to bottom button - 简化
                            Box(
                                modifier =
                                Modifier
                                    .align(
                                        Alignment
                                            .CenterEnd
                                    )
                                    .padding(
                                        end = 16.dp
                                    )
                            ) {
                                if (showScrollButton) {
                                    SmallFloatingActionButton(
                                        onClick = {
                                            // 点击按钮：启用自动滚动，隐藏按钮，并立即滚动到底部
                                            autoScrollToBottom =
                                                true
                                            showScrollButton =
                                                false

                                            coroutineScope
                                                .launch {
                                                    if (chatHistory
                                                            .isNotEmpty()
                                                    ) {
                                                        try {
                                                            // 不关心index，直接尝试滚动到底部
                                                            // 使用最大可能的滚动量
                                                            listState
                                                                .dispatchRawDelta(
                                                                    100000f
                                                                )
                                                        } catch (
                                                            e:
                                                            Exception
                                                        ) {
                                                            Log.e(
                                                                "AIChatScreen",
                                                                "滚动到底部失败",
                                                                e
                                                            )
                                                        }
                                                    }
                                                }
                                        },
                                        containerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .secondary
                                            .copy(
                                                alpha =
                                                0.85f
                                            ),
                                        contentColor =
                                        MaterialTheme
                                            .colorScheme
                                            .onSecondary
                                    ) {
                                        Icon(
                                            imageVector =
                                            Icons.Default
                                                .KeyboardArrowDown,
                                            contentDescription =
                                            "滚动到底部",
                                            modifier =
                                            Modifier.size(
                                                18.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 历史选择器作为浮动层，使用AnimatedVisibility保持动画效果
                AnimatedVisibility(
                    visible = showChatHistorySelector,
                    enter =
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300)),
                    exit =
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300)),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Box(
                        modifier =
                        Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .background(
                                color =
                                MaterialTheme
                                    .colorScheme
                                    .surface
                                    .copy(
                                        alpha =
                                        0.95f
                                    ),
                                shape =
                                RoundedCornerShape(
                                    topEnd =
                                    4.dp,
                                    bottomEnd =
                                    4.dp
                                )
                            )
                    ) {
                        // 直接使用ChatHistorySelector
                        ChatHistorySelector(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(top = 8.dp),
                            onNewChat = {
                                viewModel.createNewChat()
                                // 创建新对话后自动收起侧边框
                                viewModel.showChatHistorySelector(false)
                            },
                            onSelectChat = { chatId ->
                                viewModel.switchChat(chatId)
                                // 切换聊天后也自动收起侧边框
                                viewModel.showChatHistorySelector(false)
                            },
                            onDeleteChat = { chatId ->
                                viewModel.deleteChatHistory(chatId)
                            },
                            chatHistories =
                            chatHistories.sortedByDescending {
                                it.createdAt
                            },
                            currentId = currentChatId
                        )

                        // 在右侧添加浮动返回按钮
                        OutlinedButton(
                            onClick = {
                                viewModel
                                    .toggleChatHistorySelector()
                            },
                            modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = 16.dp,
                                    end = 8.dp
                                )
                                .height(28.dp),
                            contentPadding =
                            PaddingValues(
                                horizontal = 10.dp,
                                vertical = 0.dp
                            ),
                            colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                            ),
                            border =
                            ButtonDefaults.outlinedButtonBorder
                                .copy(width = 1.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                verticalAlignment =
                                Alignment.CenterVertically,
                                horizontalArrangement =
                                Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector =
                                    Icons.Default
                                        .ArrowBack,
                                    contentDescription = null,
                                    modifier =
                                    Modifier.size(
                                        14.dp
                                    ),
                                    tint =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                )
                                Text(
                                    "返回",
                                    style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Show popup message dialog when needed
    popupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPopupMessage() },
            title = { Text("提示") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPopupMessage() }) {
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
            viewModel.toggleFloatingMode()
            android.widget.Toast.makeText(
                context,
                "未获得悬浮窗权限，已关闭悬浮窗模式",
                android.widget.Toast.LENGTH_SHORT
            )
                .show()
        }
    }
}

// ViewModel Factory
class ChatViewModelFactory(private val context: Context) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return ChatViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// 添加用于显示统计项的可复用组件
@Composable
private fun StatItem(label: String, value: String, isHighlighted: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style =
            MaterialTheme.typography.labelMedium.copy(
                fontWeight =
                if (isHighlighted)
                    androidx.compose.ui.text.font.FontWeight
                        .Bold
                else androidx.compose.ui.text.font.FontWeight.Normal
            ),
            color =
            if (isHighlighted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// 全局配置状态持有者，确保在整个应用生命周期内保持状态
object ConfigurationStateHolder {
    // 标记默认配置是否已确认过，默认为false
    // 注意：这个状态只在应用单次运行期间有效，应用关闭后将被重置
    var hasConfirmedDefaultInSession: Boolean = false
}
