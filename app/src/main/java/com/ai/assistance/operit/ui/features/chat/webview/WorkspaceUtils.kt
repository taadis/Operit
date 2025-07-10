package com.ai.assistance.operit.ui.features.chat.webview

import android.content.Context
import java.io.File

fun createAndGetDefaultWorkspace(context: Context, chatId: String): File {
    // This uses the old logic of creating a fixed path workspace inside the app's data.
    // This will be replaced by a file picker in the future.
    val workspacePath = LocalWebServer.getWorkspacePath(chatId)
    LocalWebServer.ensureWorkspaceDirExists(workspacePath)

    // 创建并返回工作区目录
    val webContentDir = File(workspacePath)

    // 如果工作区为空，创建一个示例HTML文件
    LocalWebServer.createDefaultIndexHtmlIfNeeded(webContentDir)

    return webContentDir
}
 