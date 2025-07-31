package com.ai.assistance.operit.ui.features.chat.webview.computer

import androidx.compose.runtime.Composable
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import java.io.File

@Composable
fun ComputerScreen(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
) {
    // For now, we'll just show the ComputerManager directly.
    // Later we can add setup screens if needed.
    ComputerManager(
        actualViewModel = actualViewModel,
        currentChat = currentChat,
    )
} 