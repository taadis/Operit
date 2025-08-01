package com.ai.assistance.operit.ui.features.chat.webview

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.IOException

fun createAndGetDefaultWorkspace(context: Context, chatId: String): File {
    val workspacePath = getWorkspacePath(chatId)
    ensureWorkspaceDirExists(workspacePath)

    val webContentDir = File(workspacePath)

    // Reuse the createDefaultIndexHtmlIfNeeded logic from LocalWebServer
    LocalWebServer.createDefaultIndexHtmlIfNeeded(webContentDir)

    return webContentDir
}

fun getWorkspacePath(chatId: String): String {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return "$downloadDir/Operit/workspace/$chatId"
}

fun ensureWorkspaceDirExists(path: String): File {
    val workspaceDir = File(path)
    if (!workspaceDir.exists()) {
        workspaceDir.mkdirs()
    }
    return workspaceDir
}
 