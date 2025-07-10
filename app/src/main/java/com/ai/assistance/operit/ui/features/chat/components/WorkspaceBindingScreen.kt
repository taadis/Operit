package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.*
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceScreen
import java.io.File

/**
 * 工作区绑定屏幕
 * 该组件已被重构，现在仅作为兼容层重定向到新的工作区组件
 */
@Composable
fun WorkspaceScreen(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
    onExportClick: (workDir: File) -> Unit
) {
    // 重定向到新的工作区组件
    com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceScreen(
        actualViewModel = actualViewModel,
        currentChat = currentChat,
        onExportClick = onExportClick
    )
}

/**
 * 导出按钮
 * 该组件已被重构，现在仅作为兼容层重定向到新的导出按钮组件
 */
@Composable
fun ExportButton(onClick: () -> Unit) {
    // 重定向到新的导出按钮组件
    com.ai.assistance.operit.ui.features.chat.webview.workspace.ExportButton(onClick = onClick)
} 