package com.ai.assistance.operit.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.ai.assistance.operit.model.ToolExecutionProgress
import com.ai.assistance.operit.model.ToolExecutionState
import com.ai.assistance.operit.ui.components.*
import com.ai.assistance.operit.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen() {
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
    val aiReferences by viewModel.aiReferences.collectAsState()
    val showChatHistorySelector by viewModel.showChatHistorySelector.collectAsState()
    val chatHistories by viewModel.chatHistories.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    
    // UI state
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val inputFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    
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
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(
                index = chatHistory.size - 1,
                scrollOffset = 0 // Ensure full visibility of the last item
            )
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
    
    // Request focus to the input field when configured
    LaunchedEffect(isConfigured) {
        if (isConfigured) {
            delay(300)
            inputFocusRequester.requestFocus()
        }
    }
    
    // Save chat on app exit
    DisposableEffect(Unit) {
        onDispose {
            // This is handled by the ViewModel
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isConfigured) {
                ChatInputSection(
                    userMessage = userMessage,
                    onUserMessageChange = { viewModel.updateUserMessage(it) },
                    onSendMessage = { viewModel.sendUserMessage() },
                    isLoading = isLoading,
                    isProcessingInput = isProcessingInput,
                    inputProcessingMessage = inputProcessingMessage,
                    focusRequester = inputFocusRequester
                )
            }
        }
    ) { paddingValues ->
        if (!isConfigured) {
            // Configuration screen
            ConfigurationScreen(
                apiEndpoint = apiEndpoint,
                apiKey = apiKey,
                modelName = modelName,
                onApiEndpointChange = { viewModel.updateApiEndpoint(it) },
                onApiKeyChange = { viewModel.updateApiKey(it) },
                onModelNameChange = { viewModel.updateModelName(it) },
                onSaveConfig = { viewModel.saveApiSettings() },
                onError = { viewModel.clearError() },
                coroutineScope = coroutineScope
            )
        } else {
            // Chat screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Chat history selector (collapsible)
                    AnimatedVisibility(
                        visible = showChatHistorySelector,
                        enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                        exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    ) {
                        ChatHistorySelector(
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(top = 8.dp),
                            onNewChat = { viewModel.createNewChat() },
                            onSelectChat = { chatId -> viewModel.switchChat(chatId) },
                            chatHistories = chatHistories.sortedByDescending { it.updatedAt },
                            currentId = currentChatId
                        )
                    }
                    
                    // Main chat area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Chat header
                        val currentChatTitle = chatHistories.find { it.id == currentChatId }?.title
                        
                        ChatHeader(
                            showChatHistorySelector = showChatHistorySelector,
                            onToggleChatHistorySelector = { viewModel.toggleChatHistorySelector() },
                            currentChatTitle = currentChatTitle
                        )
                        
                        // Chat area with messages
                        ChatArea(
                            chatHistory = chatHistory,
                            listState = listState,
                            aiReferences = aiReferences,
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
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ViewModel Factory
class ChatViewModelFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

